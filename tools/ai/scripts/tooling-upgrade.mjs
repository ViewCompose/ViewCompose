import {execFile} from 'node:child_process';
import {createHash, randomUUID} from 'node:crypto';
import {lstat, mkdir, readFile, readdir, realpath, rename, rm, writeFile} from 'node:fs/promises';
import {homedir, platform, tmpdir} from 'node:os';
import {isAbsolute, relative, resolve, sep} from 'node:path';
import {promisify} from 'node:util';
import {detectFrameworkProjectProfile} from './framework-project-profile.mjs';
import {frameworkProfileMatchesProject} from './framework-profile-selection.mjs';
import {
  commitDurablePackageIntegrity,
  inspectAgentClientInstallation,
  migrateAgentClient,
  verifyDurablePackageIntegrity,
} from './agent-client-integration.mjs';

const execFileAsync = promisify(execFile);
const releaseApi = 'https://api.github.com/repos/ViewCompose/ViewCompose/releases?per_page=100';
const archiveLimit = 128 * 1024 * 1024;
const manifestLimit = 4 * 1024 * 1024;
const checksumLimit = 64 * 1024;
const packageFileLimit = 2048;
const bootstrapIntegrityMarker = '.viewcompose-ai-bootstrap-v1.json';
const githubReleaseHosts = new Set([
  'api.github.com',
  'github.com',
  'objects.githubusercontent.com',
  'release-assets.githubusercontent.com',
]);
const supportedContracts = Object.freeze({
  agentClientIntegration: 5,
  frameworkCompatibilityProfile: 1,
  frameworkProfileIndex: 1,
});

export function npmInvocation(npmExecPath = process.env.npm_execpath) {
  return npmExecPath
    ? {executable: process.execPath, arguments: [npmExecPath]}
    : {executable: 'npm', arguments: []};
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function semver(value) {
  const match = /^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/u.exec(value);
  return match ? match.slice(1).map(Number) : null;
}

export function compareToolingVersions(left, right) {
  const leftParts = semver(left);
  const rightParts = semver(right);
  if (!leftParts || !rightParts) throw new Error('AI tooling versions must be stable semantic versions.');
  for (let index = 0; index < 3; index += 1) {
    if (leftParts[index] !== rightParts[index]) return leftParts[index] - rightParts[index];
  }
  return 0;
}

async function responseBytes(response, maximum, label) {
  if (!response?.ok) throw new Error(`${label} request failed with HTTP ${response?.status ?? 'unknown'}.`);
  const declared = Number(response.headers?.get?.('content-length'));
  if (Number.isFinite(declared) && declared > maximum) throw new Error(`${label} exceeds its byte limit.`);
  const bytes = new Uint8Array(await response.arrayBuffer());
  if (bytes.length > maximum) throw new Error(`${label} exceeds its byte limit.`);
  return bytes;
}

export async function boundedReleaseRequest(url, maximum, label) {
  const parsed = new URL(url);
  if (
    parsed.protocol !== 'https:' ||
    !githubReleaseHosts.has(parsed.hostname)
  ) {
    throw new Error(`${label} URL is outside the GitHub Release allowlist.`);
  }
  const response = await fetch(parsed, {
    headers: {
      accept: 'application/vnd.github+json',
      'user-agent': 'viewcompose-ai-tooling-upgrader',
      'x-github-api-version': '2022-11-28',
    },
    redirect: 'follow',
    signal: AbortSignal.timeout(30_000),
  });
  const finalUrl = new URL(response.url || parsed);
  if (
    finalUrl.protocol !== 'https:' ||
    !githubReleaseHosts.has(finalUrl.hostname)
  ) {
    throw new Error(`${label} redirect left the GitHub Release allowlist.`);
  }
  return responseBytes(response, maximum, label);
}

function parseJson(bytes, label) {
  try {
    return JSON.parse(Buffer.from(bytes).toString('utf8'));
  } catch (error) {
    throw new Error(`${label} is not valid JSON: ${error.message}`);
  }
}

function exactReleaseAssets(release, version) {
  const expected = [
    `viewcompose-ai-tooling-${version}.tgz`,
    'manifest.json',
    'SHA256SUMS',
  ];
  if (!Array.isArray(release.assets) || release.assets.length !== expected.length) return null;
  const assets = new Map(release.assets.map((asset) => [asset.name, asset.browser_download_url]));
  if (
    assets.size !== expected.length ||
    expected.some((name) => typeof assets.get(name) !== 'string')
  ) return null;
  return Object.fromEntries(expected.map((name) => [name, assets.get(name)]));
}

function safePackagePath(path) {
  return typeof path === 'string' &&
    path.length > 0 &&
    path.length <= 4096 &&
    !isAbsolute(path) &&
    !path.includes('\\') &&
    path.split('/').every((segment) => segment.length > 0 && segment !== '.' && segment !== '..');
}

function verifyCandidateManifest(manifest, release, version) {
  if (
    manifest?.schemaVersion !== 2 ||
    manifest.package?.name !== '@viewcompose/ai-tooling' ||
    manifest.package?.version !== version ||
    release.tag_name !== `ai-tooling-v${version}` ||
    manifest.archive?.path !== `viewcompose-ai-tooling-${version}.tgz` ||
    !/^[a-f0-9]{64}$/u.test(manifest.archive?.sha256 ?? '') ||
    !Number.isInteger(manifest.archive?.bytes) ||
    manifest.archive.bytes < 1 ||
    manifest.archive.bytes > archiveLimit ||
    !Array.isArray(manifest.frameworkProfiles) ||
    manifest.frameworkProfiles.length < 1 ||
    manifest.frameworkProfiles.length > 128 ||
    manifest.frameworkProfileIndex?.schemaVersion !== 1 ||
    !Array.isArray(manifest.frameworkProfileIndex?.profiles) ||
    !Array.isArray(manifest.files) ||
    manifest.files.length < 1 ||
    manifest.files.length > packageFileLimit
  ) {
    throw new Error(`AI tooling Release ${release.tag_name} has an invalid sidecar manifest.`);
  }
  for (const [name, supported] of Object.entries(supportedContracts)) {
    if (manifest.compatibility?.[name] !== supported) {
      throw new Error(`AI tooling Release ${release.tag_name} requires unsupported ${name}.`);
    }
  }
  const indexed = manifest.frameworkProfileIndex.profiles.map((profile) => profile.profileId);
  const declared = manifest.frameworkProfiles.map((profile) => profile.profileId);
  const packagePaths = manifest.files.map((file) => file.path);
  if (
    new Set(indexed).size !== indexed.length ||
    JSON.stringify(indexed) !== JSON.stringify(declared) ||
    !indexed.includes(manifest.frameworkProfileIndex.defaultProfileId) ||
    manifest.frameworkProfileIndex.profiles.some((entry) =>
      entry.bundlePath !== entry.profileId ||
      entry.profilePath !== `${entry.profileId}/profile.json`) ||
    manifest.frameworkProfiles.some((profile) =>
      profile.schemaVersion !== 1 ||
      profile.consumerSelectable !== true ||
      profile.knowledge?.versionLane !== 'released' ||
      !/^[a-f0-9]{64}$/u.test(profile.profileId ?? '') ||
      !Array.isArray(profile.artifacts)) ||
    new Set(packagePaths).size !== packagePaths.length ||
    manifest.files.some((file) =>
      !safePackagePath(file?.path) ||
      !Number.isInteger(file?.bytes) ||
      file.bytes < 1 ||
      file.bytes > archiveLimit ||
      !/^[a-f0-9]{64}$/u.test(file?.sha256 ?? ''))
  ) {
    throw new Error(`AI tooling Release ${release.tag_name} has inconsistent framework profiles.`);
  }
}

export async function selectCompatibleUpgradeCandidate({
  releases,
  projectProfile,
  currentVersion,
  request = boundedReleaseRequest,
} = {}) {
  if (projectProfile?.status === 'unresolved' || projectProfile?.status === 'conflict') {
    throw new Error(`Cannot check upgrades while project framework status is ${projectProfile.status}.`);
  }
  if (!['empty', 'resolved'].includes(projectProfile?.status)) {
    throw new Error('Upgrade selection requires a resolved or empty project framework profile.');
  }
  const candidates = (releases ?? []).flatMap((release) => {
    const match = /^ai-tooling-v(.+)$/u.exec(release?.tag_name ?? '');
    const version = match?.[1];
    if (
      release?.draft === true ||
      release?.prerelease === true ||
      !semver(version ?? '') ||
      compareToolingVersions(version, currentVersion) <= 0
    ) return [];
    const assets = exactReleaseAssets(release, version);
    return assets ? [{release, version, assets}] : [];
  }).sort((left, right) => compareToolingVersions(right.version, left.version));

  for (const candidate of candidates.slice(0, 30)) {
    const manifestBytes = await request(candidate.assets['manifest.json'], manifestLimit, 'Release manifest');
    const manifest = parseJson(manifestBytes, 'Release manifest');
    verifyCandidateManifest(manifest, candidate.release, candidate.version);
    const profiles = projectProfile.status === 'empty'
      ? manifest.frameworkProfiles.filter((profile) =>
        profile.profileId === manifest.frameworkProfileIndex.defaultProfileId)
      : manifest.frameworkProfiles.filter((profile) =>
        frameworkProfileMatchesProject(projectProfile.artifacts, profile));
    if (profiles.length === 0) continue;
    const selectedProfile = profiles.find((profile) =>
      profile.profileId === manifest.frameworkProfileIndex.defaultProfileId) ?? profiles[0];
    return Object.freeze({...candidate, manifest, manifestBytes, selectedProfile});
  }
  return null;
}

export async function discoverCompatibleUpgrade({
  projectRoot,
  currentVersion,
  request = boundedReleaseRequest,
} = {}) {
  const projectProfile = await detectFrameworkProjectProfile({projectRoot});
  const releaseBytes = await request(releaseApi, manifestLimit, 'GitHub Release inventory');
  const releases = parseJson(releaseBytes, 'GitHub Release inventory');
  if (!Array.isArray(releases)) throw new Error('GitHub Release inventory must be an array.');
  const candidate = await selectCompatibleUpgradeCandidate({
    releases,
    projectProfile,
    currentVersion,
    request,
  });
  return {projectProfile, candidate};
}

function parseChecksums(bytes) {
  const entries = new Map();
  for (const line of Buffer.from(bytes).toString('utf8').trim().split('\n')) {
    const match = /^([a-f0-9]{64})  ([^/]+)$/u.exec(line);
    if (!match || entries.has(match[2])) throw new Error('Release checksum inventory is malformed.');
    entries.set(match[2], match[1]);
  }
  return entries;
}

export async function downloadCompatibleCandidate(candidate, {
  directory,
  request = boundedReleaseRequest,
} = {}) {
  if (!candidate || !isAbsolute(directory)) {
    throw new Error('Candidate download requires one absolute staging directory.');
  }
  await mkdir(directory, {recursive: true});
  const checksumBytes = await request(candidate.assets.SHA256SUMS, checksumLimit, 'Release checksums');
  const checksums = parseChecksums(checksumBytes);
  if (
    checksums.size !== 2 ||
    checksums.get('manifest.json') !== sha256(candidate.manifestBytes) ||
    checksums.get(candidate.manifest.archive.path) !== candidate.manifest.archive.sha256
  ) {
    throw new Error('Release checksum inventory does not bind the selected manifest and archive.');
  }
  const archive = await request(
    candidate.assets[candidate.manifest.archive.path],
    archiveLimit,
    'Release archive',
  );
  if (
    archive.length !== candidate.manifest.archive.bytes ||
    sha256(archive) !== candidate.manifest.archive.sha256
  ) {
    throw new Error('Downloaded AI tooling archive differs from the selected Release manifest.');
  }
  const archivePath = resolve(directory, candidate.manifest.archive.path);
  await writeFile(archivePath, archive, {flag: 'wx', mode: 0o600});
  return {archivePath, archiveSha256: candidate.manifest.archive.sha256};
}

function defaultUpgradeRoot() {
  const base = platform() === 'darwin'
    ? resolve(homedir(), 'Library/Caches')
    : process.env.XDG_CACHE_HOME && isAbsolute(process.env.XDG_CACHE_HOME)
      ? resolve(process.env.XDG_CACHE_HOME)
      : resolve(homedir(), '.cache');
  return resolve(base, 'viewcompose/ai-tooling/packages');
}

export async function verifyInstalledCandidate(packageRoot, manifest) {
  const canonicalRoot = await realpath(packageRoot);
  if (canonicalRoot !== packageRoot) throw new Error('Installed candidate package root is unsafe.');
  const installedFiles = [];
  const visit = async (directory) => {
    for (const entry of (await readdir(directory, {withFileTypes: true}))
      .sort((left, right) => left.name.localeCompare(right.name))) {
      const path = resolve(directory, entry.name);
      if (relative(packageRoot, path) === bootstrapIntegrityMarker) continue;
      if (entry.isSymbolicLink()) throw new Error('Installed candidate contains a symbolic link.');
      if (entry.isDirectory()) {
        await visit(path);
      } else if (entry.isFile()) {
        installedFiles.push(relative(packageRoot, path).split(sep).join('/'));
        if (installedFiles.length > packageFileLimit) {
          throw new Error('Installed candidate exceeds its file-count limit.');
        }
      } else {
        throw new Error('Installed candidate contains an unsupported filesystem entry.');
      }
    }
  };
  await visit(packageRoot);
  const declaredFiles = manifest.files.map((file) => file.path).sort();
  if (JSON.stringify(installedFiles.sort()) !== JSON.stringify(declaredFiles)) {
    throw new Error('Installed candidate file inventory differs from its Release manifest.');
  }
  for (const file of manifest.files) {
    const path = resolve(packageRoot, file.path);
    const metadata = await lstat(path);
    if (!metadata.isFile() || metadata.isSymbolicLink()) {
      throw new Error(`Installed candidate file is unsafe: ${file.path}.`);
    }
    const bytes = await readFile(path);
    if (bytes.length !== file.bytes || sha256(bytes) !== file.sha256) {
      throw new Error(`Installed candidate file failed integrity: ${file.path}.`);
    }
  }
  const distribution = parseJson(
    await readFile(resolve(packageRoot, 'distribution.json')),
    'Installed distribution metadata',
  );
  const installedProfileIndex = parseJson(
    await readFile(resolve(packageRoot, 'generated/released/index.json')),
    'Installed framework profile index',
  );
  const installedProfiles = await Promise.all(manifest.frameworkProfiles.map((profile) =>
    readFile(
      resolve(packageRoot, 'generated/released', profile.profileId, 'profile.json'),
    ).then((bytes) => parseJson(bytes, `Installed framework profile ${profile.profileId}`))));
  if (
    distribution.package?.name !== manifest.package.name ||
    distribution.package?.version !== manifest.package.version ||
    !distribution.frameworkProfile ||
    !manifest.frameworkProfiles.some((profile) =>
      profile.profileId === distribution.frameworkProfile.profileId) ||
    JSON.stringify(installedProfileIndex) !== JSON.stringify(manifest.frameworkProfileIndex) ||
    JSON.stringify(installedProfiles) !== JSON.stringify(manifest.frameworkProfiles)
  ) {
    throw new Error('Installed candidate metadata or framework profiles differ from its Release manifest.');
  }
}

async function installedPackageRoot(prefix, npmExecutable, npmArguments) {
  const {stdout} = await execFileAsync(npmExecutable, [
    ...npmArguments,
    'root',
    '--global',
    '--prefix',
    prefix,
  ], {
    encoding: 'utf8',
    maxBuffer: 1024 * 1024,
  });
  return resolve(stdout.trim(), '@viewcompose/ai-tooling');
}

export async function installCompatibleCandidate(candidate, archivePath, {
  installRoot = defaultUpgradeRoot(),
  npmExecutable,
  npmArguments,
} = {}) {
  if (!isAbsolute(installRoot) || !isAbsolute(archivePath)) {
    throw new Error('Candidate installation paths must be absolute.');
  }
  const finalPrefix = resolve(
    installRoot,
    `${candidate.version}-${candidate.manifest.archive.sha256.slice(0, 16)}`,
  );
  const stagingPrefix = resolve(installRoot, `.staging-${randomUUID()}`);
  const npm = npmInvocation();
  const effectiveNpmExecutable = npmExecutable ?? npm.executable;
  const effectiveNpmArguments = npmArguments ?? npm.arguments;
  await mkdir(installRoot, {recursive: true});
  try {
    const existing = await lstat(finalPrefix).catch((error) => {
      if (error?.code === 'ENOENT') return null;
      throw error;
    });
    if (existing) {
      if (!existing.isDirectory() || existing.isSymbolicLink()) {
        throw new Error('Existing content-addressed candidate prefix is unsafe.');
      }
      const packageRoot = await installedPackageRoot(
        finalPrefix,
        effectiveNpmExecutable,
        effectiveNpmArguments,
      );
      await verifyInstalledCandidate(packageRoot, candidate.manifest);
      await verifyDurablePackageIntegrity({
        aiRoot: packageRoot,
        frameworkProfile: candidate.selectedProfile.profileId,
        packageVersion: candidate.version,
      });
      return {
        prefix: finalPrefix,
        packageRoot,
        mcpServerPath: resolve(packageRoot, 'scripts/mcp-server.mjs'),
        cache: 'verified-hit',
      };
    }
    await execFileAsync(effectiveNpmExecutable, [
      ...effectiveNpmArguments,
      'install',
      '--global',
      '--prefix',
      stagingPrefix,
      '--ignore-scripts',
      '--offline',
      '--no-audit',
      '--no-fund',
      archivePath,
    ], {
      cwd: tmpdir(),
      encoding: 'utf8',
      maxBuffer: 4 * 1024 * 1024,
      env: {...process.env, npm_config_audit: 'false', npm_config_fund: 'false'},
    });
    const stagingPackage = await installedPackageRoot(
      stagingPrefix,
      effectiveNpmExecutable,
      effectiveNpmArguments,
    );
    await verifyInstalledCandidate(stagingPackage, candidate.manifest);
    await commitDurablePackageIntegrity({
      aiRoot: stagingPackage,
      frameworkProfile: candidate.selectedProfile.profileId,
    });
    await rename(stagingPrefix, finalPrefix);
    const finalPackage = resolve(finalPrefix, stagingPackage.slice(stagingPrefix.length + 1));
    return {
      prefix: finalPrefix,
      packageRoot: finalPackage,
      mcpServerPath: resolve(finalPackage, 'scripts/mcp-server.mjs'),
      cache: 'installed',
    };
  } catch (error) {
    await rm(stagingPrefix, {recursive: true, force: true});
    throw error;
  }
}

export async function upgradeAgentClient({
  client,
  projectRoot = process.cwd(),
  request = boundedReleaseRequest,
  installRoot = defaultUpgradeRoot(),
  npmExecutable,
  npmArguments,
  discover = discoverCompatibleUpgrade,
  download = downloadCompatibleCandidate,
  install = installCompatibleCandidate,
  migrate = migrateAgentClient,
  inspect = inspectAgentClientInstallation,
} = {}) {
  const active = await inspect({client, projectRoot});
  const discovery = await discover({
    projectRoot: active.projectRoot,
    currentVersion: active.version,
    request,
  });
  if (!discovery.candidate) {
    return {
      schemaVersion: 1,
      status: 'no-compatible-update',
      client: active.client,
      projectRoot: active.projectRoot,
      installedVersion: active.version,
      frameworkProfile: active.frameworkProfile,
    };
  }
  const downloadRoot = resolve(installRoot, `.download-${randomUUID()}`);
  try {
    const downloaded = await download(discovery.candidate, {directory: downloadRoot, request});
    const installed = await install(discovery.candidate, downloaded.archivePath, {
      installRoot,
      npmExecutable,
      npmArguments,
    });
    const migration = await migrate({
      client: active.client,
      projectRoot: active.projectRoot,
      newAiRoot: installed.packageRoot,
      frameworkProfile: discovery.candidate.selectedProfile.profileId,
    });
    return {
      schemaVersion: 1,
      status: 'upgraded',
      client: active.client,
      projectRoot: active.projectRoot,
      previousVersion: active.version,
      installedVersion: discovery.candidate.version,
      previousFrameworkProfile: active.frameworkProfile,
      frameworkProfile: discovery.candidate.selectedProfile.profileId,
      packageRoot: installed.packageRoot,
      migration,
    };
  } finally {
    await rm(downloadRoot, {recursive: true, force: true});
  }
}
