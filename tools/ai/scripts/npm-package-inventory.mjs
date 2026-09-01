import {createHash} from 'node:crypto';
import {execFile} from 'node:child_process';
import {mkdtemp, readFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import {promisify} from 'node:util';
import {gunzipSync} from 'node:zlib';

const execFileAsync = promisify(execFile);

function npmEnvironment() {
  return {
    ...process.env,
    npm_config_audit: 'false',
    npm_config_fund: 'false',
    npm_config_update_notifier: 'false',
  };
}

function isMissingRegistryVersion(error) {
  const detail = `${error?.stderr ?? ''}\n${error?.message ?? ''}`;
  return /(?:\bE404\b|404 Not Found|is not in this registry)/u.test(detail);
}

function inventoryResult(stdout, mode) {
  const parsed = JSON.parse(stdout);
  if (mode === 'published-payload') {
    if (!Array.isArray(parsed) || parsed.length !== 1) {
      throw new Error('npm pack did not return exactly one package inventory.');
    }
    return parsed[0];
  }
  if (Array.isArray(parsed) || parsed === null || typeof parsed !== 'object') {
    throw new Error('npm publish dry-run did not return one package inventory.');
  }
  return parsed;
}

export async function verifyNpmPackageInventory(
  distribution,
  contract,
  {
    cwd = process.cwd(),
    execute = execFileAsync,
    readArchive = readFile,
    createTemporaryDirectory = mkdtemp,
    removeTemporaryDirectory = (path) => rm(path, {recursive: true, force: true}),
  } = {},
) {
  const packageIdentity = `${contract.package.name}@${contract.package.version}`;
  const options = {
    cwd,
    encoding: 'utf8',
    maxBuffer: 4 * 1024 * 1024,
    env: npmEnvironment(),
  };
  let publishedIntegrity = null;
  try {
    const {stdout} = await execute(
      'npm',
      ['view', packageIdentity, 'dist.integrity', '--json'],
      options,
    );
    publishedIntegrity = JSON.parse(stdout);
    if (typeof publishedIntegrity !== 'string' || !publishedIntegrity.startsWith('sha512-')) {
      throw new Error('Published npm version did not expose one SHA-512 integrity identity.');
    }
  } catch (error) {
    if (!isMissingRegistryVersion(error)) throw error;
  }

  let mode;
  let stdout;
  if (publishedIntegrity === null) {
    mode = 'publish-dry-run';
    ({stdout} = await execute(
      'npm',
      [
        'publish',
        distribution.archivePath,
        '--dry-run',
        '--ignore-scripts',
        '--json',
        '--access',
        'public',
      ],
      options,
    ));
  } else {
    mode = 'published-payload';
    const downloadRoot = await createTemporaryDirectory(
      resolve(tmpdir(), 'viewcompose-ai-published-package-'),
    );
    try {
      ({stdout} = await execute(
        'npm',
        ['pack', packageIdentity, '--ignore-scripts', '--json', '--pack-destination', downloadRoot],
        options,
      ));
      const downloaded = inventoryResult(stdout, mode);
      if (downloaded.filename !== distribution.manifest.archive.path) {
        throw new Error('Published npm archive filename differs from the frozen package manifest.');
      }
      const publishedArchive = await readArchive(resolve(downloadRoot, downloaded.filename));
      const downloadedIntegrity =
        `sha512-${createHash('sha512').update(publishedArchive).digest('base64')}`;
      if (downloaded.integrity !== publishedIntegrity || downloadedIntegrity !== publishedIntegrity) {
        throw new Error('Downloaded npm archive does not match the registry integrity identity.');
      }
      const candidateArchive = await readArchive(distribution.archivePath);
      let candidatePayload;
      let publishedPayload;
      try {
        candidatePayload = gunzipSync(candidateArchive);
        publishedPayload = gunzipSync(publishedArchive);
      } catch {
        throw new Error('Candidate or published npm archive is not a valid gzip payload.');
      }
      if (!candidatePayload.equals(publishedPayload)) {
        throw new Error('Published npm payload differs from the frozen package archive.');
      }
    } finally {
      await removeTemporaryDirectory(downloadRoot);
    }
  }

  const result = inventoryResult(stdout, mode);
  const expectedPaths = distribution.manifest.files.map((file) => file.path).sort();
  const actualPaths = result.files?.map((file) => file.path).sort() ?? [];
  if (
    result.id !== packageIdentity ||
    result.name !== contract.package.name ||
    result.version !== contract.package.version ||
    result.filename !== distribution.manifest.archive.path ||
    JSON.stringify(actualPaths) !== JSON.stringify(expectedPaths)
  ) {
    throw new Error(`npm ${mode} inventory differs from the frozen package manifest.`);
  }
  return mode;
}
