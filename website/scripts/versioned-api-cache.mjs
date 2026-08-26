import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import {createReadStream} from 'node:fs';
import {
  appendFile,
  lstat,
  mkdir,
  readFile,
  readdir,
  rename,
  rm,
  writeFile,
} from 'node:fs/promises';
import {dirname, relative, resolve, sep} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {loadDocumentationReleases} from './documentation-releases.mjs';

const websiteRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = resolve(websiteRoot, '..');

export const VERSIONED_API_CACHE_SCHEMA_VERSION = 1;
export const VERSIONED_API_INTEGRITY_MANIFEST = 'integrity-manifest.json';
export const VERSIONED_API_CACHE_STATE_ROOT = resolve(
  repositoryRoot,
  'build/versioned-api-cache',
);
export const VERSIONED_API_CACHE_REPORT = resolve(
  repositoryRoot,
  'build/reports/documentation/versioned-api-cache-report.json',
);
export const VERSIONED_API_CACHE_INPUTS = Object.freeze([
  'tools/viewcompose-publishing-build/build.gradle.kts',
  'tools/viewcompose-publishing-build/settings.gradle.kts',
  'tools/viewcompose-publishing-build/src/main',
  'gradle/viewcompose-dependency-contracts.properties',
  'website/scripts/assemble-versioned-api-docs.mjs',
  'website/scripts/documentation-releases.mjs',
  'website/scripts/versioned-api-cache.mjs',
]);

function digestText(value) {
  return createHash('sha256').update(value, 'utf8').digest('hex');
}

async function javaRuntimeIdentity() {
  return new Promise((accept, reject) => {
    const child = spawn('java', ['-version'], {stdio: ['ignore', 'pipe', 'pipe']});
    const output = [];
    child.stdout.on('data', (chunk) => output.push(chunk));
    child.stderr.on('data', (chunk) => output.push(chunk));
    child.on('error', reject);
    child.on('exit', (code, signal) => {
      if (code === 0) accept(Buffer.concat(output).toString('utf8').trim());
      else reject(new Error(`java -version exited with ${code ?? signal}`));
    });
  });
}

async function digestFile(path) {
  const hash = createHash('sha256');
  await new Promise((accept, reject) => {
    const input = createReadStream(path);
    input.on('data', (chunk) => hash.update(chunk));
    input.on('error', reject);
    input.on('end', accept);
  });
  return hash.digest('hex');
}

function portablePath(path) {
  return path.split(sep).join('/');
}

function safeOutputPath(path) {
  if (!path || path.startsWith('/') || path.includes('\\')) return false;
  const segments = path.split('/');
  return segments.every((segment) => segment && segment !== '.' && segment !== '..');
}

async function pathExists(path) {
  try {
    await lstat(path);
    return true;
  } catch (error) {
    if (error.code === 'ENOENT') return false;
    throw error;
  }
}

async function collectRegularFiles(root, path = root) {
  const metadata = await lstat(path);
  if (metadata.isSymbolicLink()) {
    throw new Error(`Versioned API cache input must not be a symbolic link: ${path}`);
  }
  if (metadata.isFile()) return [{path, size: metadata.size}];
  if (!metadata.isDirectory()) {
    throw new Error(`Versioned API cache input must be a file or directory: ${path}`);
  }
  const entries = await readdir(path, {withFileTypes: true});
  const files = [];
  for (const entry of entries.sort((left, right) => left.name.localeCompare(right.name))) {
    files.push(...await collectRegularFiles(root, resolve(path, entry.name)));
  }
  return files;
}

async function mapLimit(values, limit, operation) {
  if (!Number.isInteger(limit) || limit < 1) throw new Error(`Invalid concurrency limit: ${limit}`);
  const result = new Array(values.length);
  let next = 0;
  const workers = Array.from({length: Math.min(limit, values.length)}, async () => {
    while (next < values.length) {
      const index = next;
      next += 1;
      result[index] = await operation(values[index], index);
    }
  });
  await Promise.all(workers);
  return result;
}

function normalizedEntries(entries) {
  return entries
    .map(({artifact, version, sourceRevision}) => ({artifact, version, sourceRevision}))
    .sort((left, right) =>
      left.artifact.localeCompare(right.artifact) ||
      left.version.localeCompare(right.version) ||
      left.sourceRevision.localeCompare(right.sourceRevision));
}

function sameEntries(left, right) {
  return JSON.stringify(normalizedEntries(left)) === JSON.stringify(normalizedEntries(right));
}

export async function computeVersionedApiGeneratorFingerprint(
  root = repositoryRoot,
  inputs = VERSIONED_API_CACHE_INPUTS,
) {
  const files = [];
  for (const input of inputs) {
    const absolute = resolve(root, input);
    const discovered = await collectRegularFiles(root, absolute);
    files.push(...discovered.map((file) => ({
      ...file,
      relativePath: portablePath(relative(root, file.path)),
    })));
  }
  const records = await mapLimit(
    files.sort((left, right) => left.relativePath.localeCompare(right.relativePath)),
    16,
    async (file) => ({
      path: file.relativePath,
      size: file.size,
      sha256: await digestFile(file.path),
    }),
  );
  return digestText(JSON.stringify({
    schemaVersion: VERSIONED_API_CACHE_SCHEMA_VERSION,
    runtimeContract: {
      requiredJdkMajor: 17,
      requiredNodeMajor: 24,
      node: process.version,
      java: await javaRuntimeIdentity(),
      androidPlatform: 36,
      androidBuildTools: '36.0.0',
      output: 'dokka-html',
    },
    files: records,
  }));
}

export async function createVersionedApiCachePlan({
  entries,
  root = repositoryRoot,
  generatorFingerprint,
} = {}) {
  if (!entries || entries.length === 0) {
    throw new Error('Versioned API cache planning requires immutable release entries');
  }
  const generator = generatorFingerprint ?? await computeVersionedApiGeneratorFingerprint(root);
  const grouped = new Map();
  for (const entry of normalizedEntries(entries)) {
    const group = grouped.get(entry.sourceRevision) ?? [];
    group.push(entry);
    grouped.set(entry.sourceRevision, group);
  }
  const revisions = [...grouped]
    .map(([revision, groupEntries]) => {
      const normalized = normalizedEntries(groupEntries);
      return {
        revision,
        entries: normalized,
        fingerprint: digestText(JSON.stringify({
          schemaVersion: VERSIONED_API_CACHE_SCHEMA_VERSION,
          generatorFingerprint: generator,
          revision,
          entries: normalized,
        })),
      };
    })
    .sort((left, right) => left.revision.localeCompare(right.revision));
  const completeFingerprint = digestText(JSON.stringify({
    schemaVersion: VERSIONED_API_CACHE_SCHEMA_VERSION,
    generatorFingerprint: generator,
    revisions: revisions.map(({revision, fingerprint}) => ({revision, fingerprint})),
  }));
  return {
    schemaVersion: VERSIONED_API_CACHE_SCHEMA_VERSION,
    generatorFingerprint: generator,
    completeFingerprint,
    revisions,
  };
}

export async function readVersionedApiIntegrityManifest(
  stateRoot = VERSIONED_API_CACHE_STATE_ROOT,
) {
  const path = resolve(stateRoot, VERSIONED_API_INTEGRITY_MANIFEST);
  try {
    const manifest = JSON.parse(await readFile(path, 'utf8'));
    if (manifest.schemaVersion !== VERSIONED_API_CACHE_SCHEMA_VERSION) {
      return {manifest: null, problem: `unsupported schema ${manifest.schemaVersion}`};
    }
    if (!Array.isArray(manifest.revisions)) {
      return {manifest: null, problem: 'revisions must be an array'};
    }
    return {manifest, problem: null};
  } catch (error) {
    if (error.code === 'ENOENT') return {manifest: null, problem: 'manifest is missing'};
    return {manifest: null, problem: `manifest is unreadable: ${error.message}`};
  }
}

async function collectRevisionOutputFiles(outputRoot, revisionPlan) {
  const discovered = [];
  for (const entry of revisionPlan.entries) {
    const entryRoot = resolve(outputRoot, entry.artifact, entry.version);
    if (!await pathExists(entryRoot)) continue;
    const files = await collectRegularFiles(outputRoot, entryRoot);
    discovered.push(...files.map((file) => ({
      ...file,
      relativePath: portablePath(relative(outputRoot, file.path)),
    })));
  }
  return discovered.sort((left, right) => left.relativePath.localeCompare(right.relativePath));
}

async function revisionOutputExists(outputRoot, revisionPlan) {
  for (const entry of revisionPlan.entries) {
    if (await pathExists(resolve(outputRoot, entry.artifact, entry.version))) return true;
  }
  return false;
}

export async function createRevisionIntegrityRecord(outputRoot, revisionPlan) {
  const files = await collectRevisionOutputFiles(outputRoot, revisionPlan);
  const expectedRoots = revisionPlan.entries.length;
  const presentRoots = await Promise.all(
    revisionPlan.entries.map(({artifact, version}) =>
      pathExists(resolve(outputRoot, artifact, version))),
  );
  if (presentRoots.filter(Boolean).length !== expectedRoots) {
    const missing = revisionPlan.entries
      .filter((_, index) => !presentRoots[index])
      .map(({artifact, version}) => `${artifact}/${version}`);
    throw new Error(`Generated API revision ${revisionPlan.revision} is missing: ${missing.join(', ')}`);
  }
  const hashed = await mapLimit(files, 16, async (file) => ({
    path: file.relativePath,
    size: file.size,
    sha256: await digestFile(file.path),
  }));
  return {
    revision: revisionPlan.revision,
    fingerprint: revisionPlan.fingerprint,
    entries: normalizedEntries(revisionPlan.entries),
    files: hashed,
  };
}

export async function validateRevisionIntegrity(
  outputRoot,
  revisionPlan,
  manifestState,
) {
  const outputExists = await revisionOutputExists(outputRoot, revisionPlan);
  if (!manifestState.manifest) {
    return {
      status: outputExists ? 'invalid' : 'miss',
      reason: manifestState.problem,
      record: null,
    };
  }
  const records = manifestState.manifest.revisions
    .filter((record) => record?.revision === revisionPlan.revision);
  if (records.length !== 1) {
    return {
      status: outputExists ? 'invalid' : 'miss',
      reason: records.length === 0 ? 'revision record is missing' : 'duplicate revision records',
      record: null,
    };
  }
  const record = records[0];
  if (record.fingerprint !== revisionPlan.fingerprint) {
    return {status: 'stale', reason: 'revision fingerprint changed', record: null};
  }
  if (!Array.isArray(record.entries) || !sameEntries(record.entries, revisionPlan.entries)) {
    return {status: 'invalid', reason: 'revision entries do not match the fingerprint', record: null};
  }
  if (!Array.isArray(record.files)) {
    return {status: 'invalid', reason: 'revision files must be an array', record: null};
  }
  const expectedPrefixes = revisionPlan.entries.map(
    ({artifact, version}) => `${artifact}/${version}/`,
  );
  const declaredPaths = record.files.map((file) => file?.path);
  if (
    new Set(declaredPaths).size !== declaredPaths.length ||
    record.files.some((file) =>
      !file ||
      !safeOutputPath(file.path) ||
      !expectedPrefixes.some((prefix) => file.path.startsWith(prefix)) ||
      !Number.isSafeInteger(file.size) ||
      file.size < 0 ||
      !/^[a-f0-9]{64}$/u.test(file.sha256))
  ) {
    return {status: 'invalid', reason: 'revision file records are malformed', record: null};
  }
  let actualFiles;
  try {
    actualFiles = await collectRevisionOutputFiles(outputRoot, revisionPlan);
  } catch (error) {
    return {status: 'invalid', reason: error.message, record: null};
  }
  const actualPaths = actualFiles.map((file) => file.relativePath);
  const sortedDeclaredPaths = [...declaredPaths].sort((left, right) => left.localeCompare(right));
  if (JSON.stringify(actualPaths) !== JSON.stringify(sortedDeclaredPaths)) {
    return {status: 'invalid', reason: 'revision file set changed', record: null};
  }
  const declared = new Map(record.files.map((file) => [file.path, file]));
  const failures = await mapLimit(actualFiles, 16, async (file) => {
    const expected = declared.get(file.relativePath);
    if (expected.size !== file.size) return `${file.relativePath}: size changed`;
    const sha256 = await digestFile(file.path);
    return sha256 === expected.sha256 ? null : `${file.relativePath}: digest changed`;
  });
  const failure = failures.find(Boolean);
  if (failure) return {status: 'invalid', reason: failure, record: null};
  return {status: 'hit', reason: 'integrity verified', record};
}

export async function removeRevisionOutput(outputRoot, revisionPlan) {
  for (const {artifact, version} of revisionPlan.entries) {
    await rm(resolve(outputRoot, artifact, version), {recursive: true, force: true});
  }
}

export async function pruneVersionedApiOutput(outputRoot, entries, activeArtifacts) {
  await mkdir(outputRoot, {recursive: true});
  const versionsByArtifact = new Map();
  for (const {artifact, version} of entries) {
    const versions = versionsByArtifact.get(artifact) ?? new Set();
    versions.add(version);
    versionsByArtifact.set(artifact, versions);
  }
  const allowedArtifacts = new Set([...versionsByArtifact.keys(), ...activeArtifacts]);
  const roots = await readdir(outputRoot, {withFileTypes: true});
  for (const root of roots) {
    if (!root.isDirectory() || !allowedArtifacts.has(root.name)) {
      await rm(resolve(outputRoot, root.name), {recursive: true, force: true});
      continue;
    }
    const allowed = versionsByArtifact.get(root.name) ?? new Set();
    const children = await readdir(resolve(outputRoot, root.name), {withFileTypes: true});
    for (const child of children) {
      if (!child.isDirectory() || !allowed.has(child.name)) {
        await rm(resolve(outputRoot, root.name, child.name), {recursive: true, force: true});
      }
    }
  }
}

export async function writeVersionedApiIntegrityManifest(stateRoot, plan, records) {
  const manifest = {
    schemaVersion: VERSIONED_API_CACHE_SCHEMA_VERSION,
    generatorFingerprint: plan.generatorFingerprint,
    completeFingerprint: plan.completeFingerprint,
    revisions: [...records].sort((left, right) => left.revision.localeCompare(right.revision)),
  };
  await mkdir(stateRoot, {recursive: true});
  const destination = resolve(stateRoot, VERSIONED_API_INTEGRITY_MANIFEST);
  const temporary = `${destination}.tmp`;
  await writeFile(temporary, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  await rename(temporary, destination);
  return manifest;
}

export async function writeVersionedApiCacheReport(report, path = VERSIONED_API_CACHE_REPORT) {
  await mkdir(dirname(path), {recursive: true});
  await writeFile(path, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
}

function parseOptions(argumentsList) {
  if (argumentsList.length % 2 !== 0) throw new Error('Every cache option requires a value');
  return Object.fromEntries(Array.from({length: argumentsList.length / 2}, (_, index) => {
    const key = argumentsList[index * 2];
    if (!key.startsWith('--')) throw new Error(`Unknown cache option: ${key}`);
    return [key.slice(2), argumentsList[index * 2 + 1]];
  }));
}

async function appendGitHubOutputs(path, values) {
  if (!path) return;
  const content = Object.entries(values).map(([key, value]) => `${key}=${value}\n`).join('');
  await appendFile(path, content, 'utf8');
}

async function planCommand(options) {
  const root = resolve(options.repository ?? repositoryRoot);
  const releases = await loadDocumentationReleases(root);
  const plan = await createVersionedApiCachePlan({entries: releases.entries, root});
  if (options['json-output']) {
    await mkdir(dirname(resolve(options['json-output'])), {recursive: true});
    await writeFile(resolve(options['json-output']), `${JSON.stringify(plan, null, 2)}\n`, 'utf8');
  }
  await appendGitHubOutputs(options['github-output'], {
    generator_fingerprint: plan.generatorFingerprint,
    complete_fingerprint: plan.completeFingerprint,
    revision_count: plan.revisions.length,
  });
  console.log(
    `Versioned API cache plan: ${plan.revisions.length} revision groups, ` +
      `generator ${plan.generatorFingerprint.slice(0, 12)}, ` +
      `complete ${plan.completeFingerprint.slice(0, 12)}.`,
  );
}

function cacheStatus(report) {
  if (report.invalidGroups > 0) return 'recovered';
  if (report.generatedGroups === 0) return 'hit';
  if (report.reusedGroups > 0) return 'partial';
  return 'miss';
}

export function summarizeVersionedApiCacheRestore(report, matchedKey, desiredPrefix) {
  const trustedExactRestore =
    matchedKey.startsWith(desiredPrefix) &&
    report.generatedGroups === 0 &&
    report.invalidGroups === 0;
  return {
    status: cacheStatus(report),
    saveRequired: !trustedExactRestore,
  };
}

async function reportCommand(options) {
  const report = JSON.parse(await readFile(resolve(options.report), 'utf8'));
  const matchedKey = options['matched-key'] ?? '';
  const desiredPrefix = options['desired-prefix'];
  if (!desiredPrefix) throw new Error('--desired-prefix is required');
  const result = summarizeVersionedApiCacheRestore(report, matchedKey, desiredPrefix);
  await appendGitHubOutputs(options['github-output'], {
    cache_status: result.status,
    save_required: result.saveRequired,
    reused_groups: report.reusedGroups,
    generated_groups: report.generatedGroups,
    invalid_groups: report.invalidGroups,
  });
  if (options['summary-output']) {
    const lines = [
      '## Versioned API cache',
      '',
      `- Status: \`${result.status}\``,
      `- Restored key: ${matchedKey ? `\`${matchedKey}\`` : 'none'}`,
      `- Revision groups: ${report.totalGroups}`,
      `- Reused: ${report.reusedGroups}`,
      `- Generated: ${report.generatedGroups}`,
      `- Invalid/corrupt: ${report.invalidGroups}`,
      `- Maximum parallel generators: ${report.maxParallelRevisions}`,
      `- Assembly duration: ${report.durationSeconds.toFixed(1)} s`,
      '',
    ];
    await writeFile(resolve(options['summary-output']), lines.join('\n'), 'utf8');
  }
}

async function main(argumentsList) {
  const [command, ...optionArguments] = argumentsList;
  const options = parseOptions(optionArguments);
  if (command === 'plan') return planCommand(options);
  if (command === 'report') return reportCommand(options);
  throw new Error(`Unknown versioned API cache command: ${command}`);
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  await main(process.argv.slice(2));
}
