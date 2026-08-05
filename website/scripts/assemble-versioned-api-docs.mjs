import {spawn} from 'node:child_process';
import {chmod, cp, mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, resolve} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {isStableRelease, loadDocumentationReleases} from './documentation-releases.mjs';

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
    relativePath: 'gradle/viewcompose-documentation-releases.properties',
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
    const child = spawn(command, args, {stdio: ['ignore', 'pipe', 'inherit'], ...options});
    const chunks = [];
    child.stdout.on('data', (chunk) => chunks.push(chunk));
    child.on('error', reject);
    child.on('exit', (code, signal) => {
      if (code === 0) accept(Buffer.concat(chunks).toString('utf8'));
      else reject(new Error(`${command} exited with ${code ?? signal}`));
    });
  });
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
) {
  const registeredArtifacts = new Set(
    [...publishingContent.matchAll(/^module\.(viewcompose-[a-z0-9-]+)\.version=/gmu)]
      .map((match) => match[1]),
  );
  if (registeredArtifacts.size === 0) {
    throw new Error('Cannot project dependency contracts without registered Maven artifacts');
  }

  const contractArtifactPattern = /^module\.(viewcompose-[a-z0-9-]+)=/u;
  const contractArtifacts = new Set(
    contractContent
      .split(/\r?\n/u)
      .map((line) => contractArtifactPattern.exec(line)?.[1])
      .filter(Boolean),
  );
  const missing = [...registeredArtifacts].filter((artifact) => !contractArtifacts.has(artifact));
  if (missing.length > 0) {
    throw new Error(`Dependency contracts are missing registered artifacts: ${missing.sort().join(', ')}`);
  }

  const projected = contractContent.split(/\r?\n/u).flatMap((line) => {
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

async function generateRevision(revision, entries, releases) {
  await capture('git', ['cat-file', '-e', `${revision}^{commit}`], {cwd: repositoryRoot});
  const workspace = await mkdtemp(resolve(tmpdir(), 'viewcompose-versioned-api-'));
  try {
    await extractRevision(revision, workspace);
    await installCurrentDocumentationTooling(workspace);
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
    const dependencyContractsPath = resolve(
      workspace,
      'gradle/viewcompose-dependency-contracts.properties',
    );
    const dependencyContracts = await readFile(dependencyContractsPath, 'utf8');
    await writeFile(
      dependencyContractsPath,
      projectDependencyContractsForPublishingMetadata(dependencyContracts, metadata),
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

async function main(argumentsList) {
  const releases = await loadDocumentationReleases(repositoryRoot);
  const modules = selectedModules(argumentsList, new Set(releases.current.keys()));
  const selected = releases.entries.filter((entry) => modules.includes(entry.artifact));
  await rm(outputRoot, {recursive: true, force: true});
  await mkdir(outputRoot, {recursive: true});

  const byRevision = Map.groupBy(selected, (entry) => entry.sourceRevision);
  for (const [revision, entries] of byRevision) {
    await generateRevision(revision, entries, releases);
  }
  for (const artifact of modules) {
    const entries = selected.filter((entry) => entry.artifact === artifact);
    await writeAliases(artifact, entries, releases.current.get(artifact));
  }
  const manifest = selected.map(({artifact, version, sourceRevision}) => ({
    artifact,
    version,
    sourceRevision,
  }));
  await writeFile(resolve(outputRoot, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  await main(process.argv.slice(2));
}
