import {spawn} from 'node:child_process';
import {chmod, cp, mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, resolve} from 'node:path';
import {performance} from 'node:perf_hooks';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {isStableRelease, loadDocumentationReleases} from './documentation-releases.mjs';
import {
  VERSIONED_API_CACHE_REPORT,
  VERSIONED_API_CACHE_STATE_ROOT,
  createRevisionIntegrityRecord,
  createVersionedApiCachePlan,
  pruneVersionedApiOutput,
  readVersionedApiIntegrityManifest,
  removeRevisionOutput,
  validateRevisionIntegrity,
  writeVersionedApiCacheReport,
  writeVersionedApiIntegrityManifest,
} from './versioned-api-cache.mjs';

const websiteRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = resolve(websiteRoot, '..');
const outputRoot = resolve(websiteRoot, 'generated/api');

export const CURRENT_DOCUMENTATION_TOOLING_PATHS = Object.freeze([
  Object.freeze({
    relativePath: 'tools/viewcompose-publishing-build/build.gradle.kts',
    replaceDirectory: false,
  }),
  Object.freeze({
    relativePath: 'tools/viewcompose-publishing-build/settings.gradle.kts',
    replaceDirectory: false,
  }),
  Object.freeze({
    relativePath: 'tools/viewcompose-publishing-build/src/main',
    replaceDirectory: true,
  }),
  Object.freeze({
    relativePath: 'gradle/viewcompose-dependency-contracts.properties',
    replaceDirectory: false,
  }),
  Object.freeze({
    relativePath: 'website/scripts/assemble-versioned-api-docs.mjs',
    replaceDirectory: false,
  }),
  Object.freeze({
    relativePath: 'website/scripts/documentation-releases.mjs',
    replaceDirectory: false,
  }),
]);

function selectedModules(argumentsList, available) {
  const index = argumentsList.indexOf('--modules');
  const selected = index >= 0
    ? argumentsList[index + 1]?.split(',').map((value) => value.trim()).filter(Boolean)
    : [...available];
  if (!selected || selected.length === 0) throw new Error('Select at least one documentation module');
  const unknown = selected.filter((module) => !available.has(module));
  if (unknown.length > 0) throw new Error(`Unknown documentation modules: ${unknown.sort().join(', ')}`);
  return [...new Set(selected)].sort();
}

function run(command, args, options = {}) {
  return new Promise((accept, reject) => {
    const child = spawn(command, args, {stdio: 'inherit', ...options});
    child.on('error', reject);
    child.on('exit', (code, signal) => {
      if (code === 0) accept();
      else reject(new Error(`${command} exited with ${code ?? signal}`));
    });
  });
}

function capture(command, args, options = {}) {
  return new Promise((accept, reject) => {
    const child = spawn(command, args, {stdio: ['ignore', 'pipe', 'pipe'], ...options});
    const chunks = [];
    const errors = [];
    child.stdout.on('data', (chunk) => chunks.push(chunk));
    child.stderr.on('data', (chunk) => errors.push(chunk));
    child.on('error', reject);
    child.on('exit', (code, signal) => {
      if (code === 0) accept(Buffer.concat(chunks).toString('utf8'));
      else {
        const detail = Buffer.concat(errors).toString('utf8').trim();
        reject(new Error(`${command} exited with ${code ?? signal}${detail ? `: ${detail}` : ''}`));
      }
    });
  });
}

export async function ensureRevisionAvailable(
  revision,
  {root = repositoryRoot, captureCommand = capture} = {},
) {
  if (!/^[a-f0-9]{40}$/u.test(revision)) {
    throw new Error(`Frozen source revision must be a full lowercase Git SHA: ${revision}`);
  }
  const verify = () =>
    captureCommand('git', ['cat-file', '-e', `${revision}^{commit}`], {cwd: root});
  try {
    await verify();
    return false;
  } catch {
    // A source revision may belong to a squashed release PR whose temporary branch was deleted.
    // Fetch the immutable full SHA instead of resolving through a movable branch or tag.
  }
  try {
    await captureCommand(
      'git',
      ['fetch', '--no-tags', '--depth=1', 'origin', revision],
      {cwd: root},
    );
    await verify();
    return true;
  } catch (error) {
    throw new Error(
      `Unable to resolve frozen source revision ${revision} locally or fetch it from origin`,
      {cause: error},
    );
  }
}

function extractRevision(revision, destination) {
  return new Promise((accept, reject) => {
    const archive = spawn('git', ['archive', '--format=tar', revision], {
      cwd: repositoryRoot,
      stdio: ['ignore', 'pipe', 'inherit'],
    });
    const extract = spawn('tar', ['-x', '-C', destination], {
      stdio: ['pipe', 'inherit', 'inherit'],
    });
    archive.stdout.pipe(extract.stdin);
    let archiveCode;
    let extractCode;
    const finish = () => {
      if (archiveCode === undefined || extractCode === undefined) return;
      if (archiveCode === 0 && extractCode === 0) accept();
      else reject(new Error(`Unable to extract Git revision ${revision}`));
    };
    archive.on('error', reject);
    extract.on('error', reject);
    archive.on('exit', (code) => {
      archiveCode = code;
      finish();
    });
    extract.on('exit', (code) => {
      extractCode = code;
      finish();
    });
  });
}

function setProperty(content, key, value) {
  const escaped = key.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&');
  const pattern = new RegExp(`^${escaped}=.*$`, 'mu');
  if (pattern.test(content)) return content.replace(pattern, `${key}=${value}`);
  return `${content.trimEnd()}\n${key}=${value}\n`;
}

export async function installCurrentDocumentationTooling(
  workspace,
  sourceRoot = repositoryRoot,
  paths = CURRENT_DOCUMENTATION_TOOLING_PATHS,
) {
  for (const {relativePath, replaceDirectory} of paths) {
    const source = resolve(sourceRoot, relativePath);
    const destination = resolve(workspace, relativePath);
    if (replaceDirectory) await rm(destination, {recursive: true, force: true});
    await mkdir(dirname(destination), {recursive: true});
    await cp(source, destination, {recursive: replaceDirectory, force: true});
  }
}

export function projectDependencyContractsForPublishingMetadata(
  contractContent,
  publishingContent,
  historicalContractContent,
) {
  const registeredArtifacts = new Set(
    [...publishingContent.matchAll(/^module\.(viewcompose-[a-z0-9-]+)\.version=/gmu)]
      .map((match) => match[1]),
  );
  if (registeredArtifacts.size === 0) {
    throw new Error('Cannot project dependency contracts without registered Maven artifacts');
  }

  const contractArtifactPattern = /^module\.(viewcompose-[a-z0-9-]+)=/u;
  const allowHistoricalFallback = historicalContractContent !== undefined;
  const historicalContractLines = new Map(
    (historicalContractContent ?? '')
      .split(/\r?\n/u)
      .map((line) => [contractArtifactPattern.exec(line)?.[1], line])
      .filter(([artifact]) => artifact),
  );
  const augmentedContractContent = [
    contractContent.trimEnd(),
    ...[...registeredArtifacts]
      .filter((artifact) => !contractContent.includes(`module.${artifact}=`))
      .map((artifact) =>
        historicalContractLines.get(artifact) ??
          (allowHistoricalFallback
            ? `module.${artifact}=api=;implementation=;compileOnly=;runtimeOnly=`
            : undefined),
      )
      .filter(Boolean),
    '',
  ].join('\n');
  const contractArtifacts = new Set(
    augmentedContractContent
      .split(/\r?\n/u)
      .map((line) => contractArtifactPattern.exec(line)?.[1])
      .filter(Boolean),
  );
  const missing = [...registeredArtifacts].filter((artifact) => !contractArtifacts.has(artifact));
  if (missing.length > 0) {
    throw new Error(`Dependency contracts are missing registered artifacts: ${missing.sort().join(', ')}`);
  }

  const projected = augmentedContractContent.split(/\r?\n/u).flatMap((line) => {
    const artifact = contractArtifactPattern.exec(line)?.[1];
    if (!artifact) return [line];
    if (!registeredArtifacts.has(artifact)) return [];
    const separator = line.indexOf('=');
    const declarations = line.slice(separator + 1).split(';').map((declaration) => {
      const configurationSeparator = declaration.indexOf('=');
      const configuration = declaration.slice(0, configurationSeparator);
      const dependencies = declaration.slice(configurationSeparator + 1)
        .split(',')
        .filter((dependency) => registeredArtifacts.has(dependency));
      return `${configuration}=${dependencies.join(',')}`;
    });
    return [`${line.slice(0, separator)}=${declarations.join(';')}`];
  });
  return projected.join('\n');
}

export function projectDocumentationReleases(entries) {
  const normalized = [...entries].sort((left, right) =>
    left.version.localeCompare(right.version) || left.artifact.localeCompare(right.artifact));
  return [
    'schema.version=1',
    `release.count=${normalized.length}`,
    ...normalized.flatMap((entry, index) => [
      `release.${index}.version=${entry.version}`,
      `release.${index}.sourceRevision=${entry.sourceRevision}`,
      `release.${index}.modules=${entry.artifact}`,
    ]),
    '',
  ].join('\n');
}

async function generateRevision(revision, entries, releases) {
  await ensureRevisionAvailable(revision);
  const workspace = await mkdtemp(resolve(tmpdir(), 'viewcompose-versioned-api-'));
  try {
    await extractRevision(revision, workspace);
    const dependencyContractsPath = resolve(
      workspace,
      'gradle/viewcompose-dependency-contracts.properties',
    );
    const historicalDependencyContracts = await readFile(dependencyContractsPath, 'utf8').catch(
      () => '',
    );
    await installCurrentDocumentationTooling(workspace);
    await writeFile(
      resolve(workspace, 'gradle/viewcompose-documentation-releases.properties'),
      projectDocumentationReleases(entries),
      'utf8',
    );
    const metadataPath = resolve(workspace, 'gradle/viewcompose-publishing.properties');
    let metadata = await readFile(metadataPath, 'utf8');
    for (const artifact of releases.current.keys()) {
      metadata = setProperty(metadata, `module.${artifact}.sourceRevision`, revision);
    }
    for (const entry of entries) {
      metadata = setProperty(metadata, `module.${entry.artifact}.version`, entry.version);
      metadata = setProperty(
        metadata,
        `module.${entry.artifact}.sourceRevision`,
        entry.sourceRevision,
      );
    }
    const dependencyContracts = await readFile(dependencyContractsPath, 'utf8');
    await writeFile(
      dependencyContractsPath,
      projectDependencyContractsForPublishingMetadata(
        dependencyContracts,
        metadata,
        historicalDependencyContracts,
      ),
      'utf8',
    );
    await writeFile(metadataPath, metadata, 'utf8');
    const wrapper = resolve(workspace, 'gradlew');
    await chmod(wrapper, 0o755);
    const tasks = entries.map((entry) => `:${entry.artifact}:dokkaGeneratePublicationHtml`);
    await run(wrapper, ['--no-daemon', '--stacktrace', ...tasks], {cwd: workspace});
    for (const entry of entries) {
      const source = resolve(workspace, entry.artifact, 'build/dokka/html');
      const destination = resolve(outputRoot, entry.artifact, entry.version);
      await mkdir(dirname(destination), {recursive: true});
      await cp(source, destination, {recursive: true, force: true});
    }
  } finally {
    await rm(workspace, {recursive: true, force: true});
  }
}

export const MAX_PARALLEL_API_REVISIONS = 2;

export function maximumParallelApiRevisions(value = process.env.VIEWCOMPOSE_API_DOCS_MAX_PARALLEL_REVISIONS) {
  if (value === undefined || value === '') return 1;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1 || parsed > MAX_PARALLEL_API_REVISIONS) {
    throw new Error(
      `VIEWCOMPOSE_API_DOCS_MAX_PARALLEL_REVISIONS must be between 1 and ` +
        `${MAX_PARALLEL_API_REVISIONS}, but was '${value}'`,
    );
  }
  return parsed;
}

async function generateMissingRevisions(revisions, releases, concurrency) {
  let next = 0;
  const results = new Map();
  const workers = Array.from({length: Math.min(concurrency, revisions.length)}, async () => {
    while (next < revisions.length) {
      const index = next;
      next += 1;
      const revision = revisions[index];
      const startedAt = performance.now();
      await generateRevision(revision.revision, revision.entries, releases);
      results.set(revision.revision, {
        durationSeconds: (performance.now() - startedAt) / 1000,
      });
    }
  });
  await Promise.all(workers);
  return results;
}

function redirectDocument(artifact, version) {
  return `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta http-equiv="refresh" content="0; url=../${version}/">
    <link rel="canonical" href="../${version}/">
    <title>ViewCompose ${artifact} API</title>
  </head>
  <body>
    <p><a href="../${version}/">Open ${artifact} ${version} API reference</a></p>
  </body>
</html>
`;
}

async function writeAliases(artifact, entries, current) {
  const currentDirectory = resolve(outputRoot, artifact, 'current');
  await mkdir(currentDirectory, {recursive: true});
  await writeFile(resolve(currentDirectory, 'index.html'), redirectDocument(artifact, current.version));
  const latest = entries.filter((entry) => isStableRelease(entry.version)).at(-1);
  if (latest) {
    const latestDirectory = resolve(outputRoot, artifact, 'latest');
    await mkdir(latestDirectory, {recursive: true});
    await writeFile(resolve(latestDirectory, 'index.html'), redirectDocument(artifact, latest.version));
  }
}

async function copyUnpublishedCurrentApi(artifact) {
  const source = resolve(repositoryRoot, artifact, 'build/dokka/html');
  const destination = resolve(outputRoot, artifact, 'current');
  try {
    await cp(source, destination, {recursive: true, force: true});
  } catch (error) {
    throw new Error(
      `Unable to copy current API for unpublished artifact ${artifact}. ` +
        `Run :${artifact}:dokkaGeneratePublicationHtml first: ${error.message}`,
    );
  }
}

async function main(argumentsList) {
  const startedAt = performance.now();
  const releases = await loadDocumentationReleases(repositoryRoot);
  const modules = selectedModules(argumentsList, new Set(releases.current.keys()));
  const completeSelection = modules.length === releases.current.size;
  const historyArtifacts = new Set([
    ...modules,
    ...(completeSelection ? releases.retired : []),
  ]);
  const selected = releases.entries.filter((entry) => historyArtifacts.has(entry.artifact));
  const cachePlan = await createVersionedApiCachePlan({entries: selected, root: repositoryRoot});
  const manifestState = await readVersionedApiIntegrityManifest(VERSIONED_API_CACHE_STATE_ROOT);
  await pruneVersionedApiOutput(outputRoot, selected, modules);

  const records = new Map();
  const groupResults = [];
  for (const revision of cachePlan.revisions) {
    const validationStartedAt = performance.now();
    const validation = await validateRevisionIntegrity(outputRoot, revision, manifestState);
    groupResults.push({
      revision: revision.revision,
      entryCount: revision.entries.length,
      status: validation.status,
      reason: validation.reason,
      validationSeconds: (performance.now() - validationStartedAt) / 1000,
    });
    if (validation.status === 'hit') {
      records.set(revision.revision, validation.record);
    } else {
      await removeRevisionOutput(outputRoot, revision);
    }
  }

  const missingRevisions = cachePlan.revisions.filter(
    (revision) => !records.has(revision.revision),
  );
  const maxParallelRevisions = maximumParallelApiRevisions();
  const generationResults = await generateMissingRevisions(
    missingRevisions,
    releases,
    maxParallelRevisions,
  );
  for (const revision of missingRevisions) {
    records.set(
      revision.revision,
      await createRevisionIntegrityRecord(outputRoot, revision),
    );
    const group = groupResults.find((result) => result.revision === revision.revision);
    group.generationSeconds = generationResults.get(revision.revision).durationSeconds;
  }
  for (const artifact of modules) {
    const entries = selected.filter((entry) => entry.artifact === artifact);
    if (releases.unpublished.has(artifact)) {
      await copyUnpublishedCurrentApi(artifact);
    } else {
      await writeAliases(artifact, entries, releases.current.get(artifact));
    }
  }
  const manifest = selected.map(({artifact, version, sourceRevision}) => ({
    artifact,
    version,
    sourceRevision,
  }));
  await writeFile(resolve(outputRoot, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  await writeVersionedApiIntegrityManifest(
    VERSIONED_API_CACHE_STATE_ROOT,
    cachePlan,
    records.values(),
  );

  const report = {
    schemaVersion: 1,
    generatorFingerprint: cachePlan.generatorFingerprint,
    completeFingerprint: cachePlan.completeFingerprint,
    totalGroups: cachePlan.revisions.length,
    reusedGroups: groupResults.filter(({status}) => status === 'hit').length,
    generatedGroups: missingRevisions.length,
    invalidGroups: groupResults.filter(({status}) => status === 'invalid').length,
    staleGroups: groupResults.filter(({status}) => status === 'stale').length,
    maxParallelRevisions,
    durationSeconds: (performance.now() - startedAt) / 1000,
    groups: groupResults,
  };
  await writeVersionedApiCacheReport(report, VERSIONED_API_CACHE_REPORT);
  console.log(
    `Versioned API cache: reused ${report.reusedGroups}/${report.totalGroups}, ` +
      `generated ${report.generatedGroups}, invalid ${report.invalidGroups}, ` +
      `parallelism ${maxParallelRevisions}, ${report.durationSeconds.toFixed(1)} s.`,
  );
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  await main(process.argv.slice(2));
}
