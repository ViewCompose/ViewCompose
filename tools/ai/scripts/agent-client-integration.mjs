#!/usr/bin/env node
import {createHash, randomUUID} from 'node:crypto';
import {realpathSync} from 'node:fs';
import {
  copyFile,
  lstat,
  mkdir,
  readFile,
  readdir,
  realpath,
  rename,
  rm,
  rmdir,
  writeFile,
} from 'node:fs/promises';
import {homedir, platform} from 'node:os';
import {basename, dirname, isAbsolute, parse, posix, relative, resolve, sep, win32} from 'node:path';
import {fileURLToPath} from 'node:url';
import {detectAndroidSdk, detectJavaRuntime} from './tool-core.mjs';
import {detectFrameworkProjectProfile} from './framework-project-profile.mjs';
import {
  CURRENT_SOURCE_PROFILE,
  FRAMEWORK_PROFILE_ENVIRONMENT_VARIABLE,
  frameworkProfileMatchesProject,
  loadReleasedFrameworkProfiles,
  selectReleasedFrameworkProfile,
} from './framework-profile-selection.mjs';

const defaultAiRoot = fileURLToPath(new URL('../', import.meta.url));
const defaultMcpServerPath = fileURLToPath(new URL('./mcp-server.mjs', import.meta.url));
const manifestRelativePath = 'skills/manifest.json';
const managedTomlStart = '# ViewCompose AI managed configuration — start';
const managedTomlEnd = '# ViewCompose AI managed configuration — end';
const upgradeJournalPath = '.viewcompose/ai-upgrade-v1.json';
const bootstrapJournalName = '.viewcompose-ai-bootstrap-v1.json';

function durableIntegrityError(message) {
  const error = new Error(message);
  error.code = 'VC_AI_DURABLE_INTEGRITY';
  return error;
}

export const AGENT_CLIENT_PROFILES = Object.freeze({
  codex: Object.freeze({
    id: 'codex',
    displayName: 'Codex',
    configFormat: 'toml',
    configPath: '.codex/config.toml',
    skillRoot: '.agents/skills',
  }),
  'claude-code': Object.freeze({
    id: 'claude-code',
    displayName: 'Claude Code',
    configFormat: 'json',
    configPath: '.mcp.json',
    skillRoot: '.claude/skills',
  }),
  cursor: Object.freeze({
    id: 'cursor',
    displayName: 'Cursor',
    configFormat: 'json',
    configPath: '.cursor/mcp.json',
    skillRoot: '.agents/skills',
  }),
});

function profileFor(client) {
  const profile = AGENT_CLIENT_PROFILES[client];
  if (!profile) {
    throw new Error(
      `Unknown client ${JSON.stringify(client)}; expected codex, claude-code, or cursor.`,
    );
  }
  return profile;
}

function contained(root, candidate) {
  const path = relative(resolve(root), resolve(candidate));
  return path === '' || (!path.startsWith(`..${sep}`) && path !== '..' && !isAbsolute(path));
}

function tomlString(value) {
  return JSON.stringify(value);
}

function expectedServerDefinition(projectRoot, {
  sourceRoot,
  frameworkProfile,
  nodeExecutable = process.execPath,
  mcpServerPath = defaultMcpServerPath,
} = {}) {
  if (!isAbsolute(nodeExecutable) || !isAbsolute(mcpServerPath) || !isAbsolute(projectRoot)) {
    throw new Error('Configuration paths must be absolute.');
  }
  if (sourceRoot !== undefined && !isAbsolute(sourceRoot)) {
    throw new Error('Configuration paths must be absolute.');
  }
  const selectedProfile = frameworkProfile ?? (sourceRoot === undefined ? undefined : CURRENT_SOURCE_PROFILE);
  if (
    selectedProfile === undefined ||
    (selectedProfile !== CURRENT_SOURCE_PROFILE && !/^[a-f0-9]{64}$/u.test(selectedProfile))
  ) {
    throw new Error('Project-bound configuration requires one exact released framework profile.');
  }
  if (sourceRoot !== undefined && selectedProfile !== CURRENT_SOURCE_PROFILE) {
    throw new Error('Source-bound configuration requires the current-source framework profile.');
  }
  return {
    command: nodeExecutable,
    args: [mcpServerPath],
    env: {
      VIEWCOMPOSE_PROJECT_ROOT: projectRoot,
      [FRAMEWORK_PROFILE_ENVIRONMENT_VARIABLE]: selectedProfile,
      ...(sourceRoot === undefined ? {} : {VIEWCOMPOSE_SOURCE_ROOT: sourceRoot}),
    },
  };
}

export function renderAgentClientConfig(client, projectRoot, options = {}) {
  const profile = profileFor(client);
  const server = expectedServerDefinition(projectRoot, options);
  if (profile.configFormat === 'toml') {
    const lines = [
      '[mcp_servers.viewcompose]',
      `command = ${tomlString(server.command)}`,
      `args = [${tomlString(server.args[0])}]`,
      '',
    ];
    lines.push(
      '[mcp_servers.viewcompose.env]',
      `VIEWCOMPOSE_PROJECT_ROOT = ${tomlString(server.env.VIEWCOMPOSE_PROJECT_ROOT)}`,
      `${FRAMEWORK_PROFILE_ENVIRONMENT_VARIABLE} = ${tomlString(
        server.env[FRAMEWORK_PROFILE_ENVIRONMENT_VARIABLE],
      )}`,
      ...(server.env.VIEWCOMPOSE_SOURCE_ROOT === undefined ? [] : [
        `VIEWCOMPOSE_SOURCE_ROOT = ${tomlString(server.env.VIEWCOMPOSE_SOURCE_ROOT)}`,
      ]),
      '',
    );
    return lines.join('\n');
  }
  return `${JSON.stringify({mcpServers: {viewcompose: server}}, null, 2)}\n`;
}

async function metadataOrNull(path) {
  return lstat(path).catch((error) => {
    if (error?.code === 'ENOENT') return null;
    throw error;
  });
}

export async function resolveConsumerProjectRoot(projectRoot = process.cwd()) {
  return requireCanonicalDirectory(projectRoot, 'Consumer project root');
}

export function isAbsoluteProjectRoot(path, targetPlatform = platform()) {
  if (typeof path !== 'string' || path.length === 0) return false;
  return targetPlatform === 'win32' ? win32.isAbsolute(path) : posix.isAbsolute(path);
}

function defaultDurableCacheRoot() {
  if (platform() === 'win32') {
    return resolve(process.env.LOCALAPPDATA ?? resolve(homedir(), 'AppData', 'Local'), 'ViewCompose', 'ai-tooling');
  }
  if (platform() === 'darwin') return resolve(homedir(), 'Library', 'Caches', 'ViewCompose', 'ai-tooling');
  return resolve(process.env.XDG_CACHE_HOME ?? resolve(homedir(), '.cache'), 'viewcompose', 'ai-tooling');
}

function pathPrefixes(path) {
  const absolute = resolve(path);
  const root = parse(absolute).root;
  const suffix = relative(root, absolute);
  const prefixes = [root];
  let current = root;
  for (const component of suffix === '' ? [] : suffix.split(sep)) {
    current = resolve(current, component);
    prefixes.push(current);
  }
  return prefixes;
}

async function canonicalizeDurableCacheRoot(cacheRoot) {
  const lexicalRoot = resolve(cacheRoot);
  await mkdir(lexicalRoot, {recursive: true});
  const canonicalRoot = await realpath(lexicalRoot);
  const lexicalPrefixes = pathPrefixes(lexicalRoot);
  const canonicalPrefixes = pathPrefixes(canonicalRoot);
  if (lexicalPrefixes.length !== canonicalPrefixes.length) {
    throw new Error('AI tooling cache root must not traverse a symbolic link.');
  }
  for (let index = 0; index < lexicalPrefixes.length; index += 1) {
    const [lexical, canonical] = await Promise.all([
      lstat(lexicalPrefixes[index]),
      lstat(canonicalPrefixes[index]),
    ]);
    if (
      lexical.isSymbolicLink() ||
      canonical.isSymbolicLink() ||
      lexical.dev !== canonical.dev ||
      lexical.ino !== canonical.ino
    ) {
      throw new Error('AI tooling cache root must not traverse a symbolic link.');
    }
  }
  return canonicalRoot;
}

async function packageFingerprint(root, {excludeBootstrapMarker = false} = {}) {
  const files = [];
  async function visit(current) {
    for (const name of (await readdir(current)).sort()) {
      const path = resolve(current, name);
      if (excludeBootstrapMarker && relative(root, path) === bootstrapJournalName) continue;
      const metadata = await lstat(path);
      if (metadata.isSymbolicLink()) throw new Error(`AI package contains a symbolic link: ${path}`);
      if (metadata.isDirectory()) await visit(path);
      else if (metadata.isFile()) files.push({path: relative(root, path).split(sep).join('/'), bytes: await readFile(path)});
      else throw new Error(`AI package contains a non-regular entry: ${path}`);
    }
  }
  await visit(root);
  const identity = Buffer.concat(files.map(({path, bytes}) => Buffer.concat([
    Buffer.from(`${path}\0`, 'utf8'), bytes, Buffer.from('\0', 'utf8'),
  ])));
  return sha256(identity);
}

async function copyPackageTree(source, target) {
  await mkdir(target, {recursive: true});
  for (const name of (await readdir(source)).sort()) {
    const from = resolve(source, name);
    const to = resolve(target, name);
    const metadata = await lstat(from);
    if (metadata.isSymbolicLink()) throw new Error(`AI package contains a symbolic link: ${from}`);
    if (metadata.isDirectory()) await copyPackageTree(from, to);
    else if (metadata.isFile()) await copyFile(from, to);
    else throw new Error(`AI package contains a non-regular entry: ${from}`);
  }
}

export async function commitDurablePackageIntegrity({aiRoot, frameworkProfile}) {
  const distribution = JSON.parse(await readFile(resolve(aiRoot, 'distribution.json'), 'utf8'));
  if (
    distribution.package?.name !== '@viewcompose/ai-tooling' ||
    !/^\d+\.\d+\.\d+$/u.test(distribution.package?.version ?? '') ||
    !/^[a-f0-9]{64}$/u.test(frameworkProfile ?? '')
  ) {
    throw new Error('Cannot commit integrity for an invalid ViewCompose AI package identity.');
  }
  const contentFingerprint = await packageFingerprint(aiRoot, {excludeBootstrapMarker: true});
  const record = {
    schemaVersion: 1,
    status: 'committed',
    cacheKey: sha256(`${contentFingerprint}\0${frameworkProfile}`),
    packageVersion: distribution.package.version,
    frameworkProfile,
    contentFingerprint,
  };
  await writeFile(
    resolve(aiRoot, bootstrapJournalName),
    `${JSON.stringify(record, null, 2)}\n`,
    {flag: 'wx', mode: 0o600},
  );
  return record;
}

export async function verifyDurablePackageIntegrity({aiRoot, frameworkProfile, packageVersion}) {
  try {
    const markerPath = resolve(aiRoot, bootstrapJournalName);
    const markerMetadata = await metadataOrNull(markerPath);
    if (!markerMetadata?.isFile() || markerMetadata.isSymbolicLink()) {
      throw durableIntegrityError(
        'Active ViewCompose AI package has no safe durable-cache integrity marker.',
      );
    }
    const marker = JSON.parse(await readFile(markerPath, 'utf8'));
    const contentFingerprint = await packageFingerprint(aiRoot, {excludeBootstrapMarker: true});
    const expectedCacheKey = sha256(`${contentFingerprint}\0${frameworkProfile}`);
    const pathCacheKey = basename(aiRoot);
    if (
      marker.schemaVersion !== 1 ||
      marker.status !== 'committed' ||
      marker.packageVersion !== packageVersion ||
      marker.frameworkProfile !== frameworkProfile ||
      marker.contentFingerprint !== contentFingerprint ||
      marker.cacheKey !== expectedCacheKey ||
      (/^[a-f0-9]{64}$/u.test(pathCacheKey) && pathCacheKey !== expectedCacheKey)
    ) {
      throw durableIntegrityError(
        'Active ViewCompose AI durable package failed its integrity check.',
      );
    }
    return marker;
  } catch (error) {
    if (error?.code === 'VC_AI_DURABLE_INTEGRITY') throw error;
    throw durableIntegrityError(
      `Active ViewCompose AI durable package could not be verified: ${error.message}`,
    );
  }
}

function stablePackageVersion(packageVersion) {
  const match = /^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/u.exec(packageVersion);
  return match ? match.slice(1).map(Number) : null;
}

function requiresDurablePackageIntegrity(packageVersion) {
  const parts = stablePackageVersion(packageVersion);
  if (!parts) return false;
  const [major, minor] = parts;
  return major > 0 || minor >= 4;
}

async function materializeDurablePackage(aiRoot, frameworkProfile, cacheRoot = defaultDurableCacheRoot()) {
  const distributionPath = resolve(aiRoot, 'distribution.json');
  if (!(await metadataOrNull(distributionPath))) return {aiRoot, durableInstallRoot: null};
  const distribution = JSON.parse(await readFile(distributionPath, 'utf8'));
  if (distribution.package?.name !== '@viewcompose/ai-tooling') {
    throw new Error('AI package identity is invalid.');
  }
  const contentFingerprint = await packageFingerprint(aiRoot);
  const cacheKey = sha256(`${contentFingerprint}\0${frameworkProfile}`);
  const canonicalCacheRoot = await canonicalizeDurableCacheRoot(cacheRoot);
  const durableInstallRoot = resolve(canonicalCacheRoot, cacheKey);
  const marker = resolve(durableInstallRoot, bootstrapJournalName);
  const existing = await metadataOrNull(marker);
  if (existing) {
    const canonicalDurableInstallRoot = await requireCanonicalDirectory(
      durableInstallRoot,
      'Active AI package root',
    );
    const record = await verifyDurablePackageIntegrity({
      aiRoot: canonicalDurableInstallRoot,
      frameworkProfile,
      packageVersion: distribution.package.version,
    });
    if (record.cacheKey !== cacheKey || record.contentFingerprint !== contentFingerprint) {
      throw durableIntegrityError('Durable AI package cache marker is invalid.');
    }
    return {
      aiRoot: canonicalDurableInstallRoot,
      durableInstallRoot: canonicalDurableInstallRoot,
    };
  }
  const staging = resolve(canonicalCacheRoot, `.${cacheKey}.${randomUUID()}.staging`);
  try {
    await mkdir(staging);
    const canonicalStaging = await requireCanonicalDirectory(staging, 'Staged AI package root');
    await copyPackageTree(aiRoot, canonicalStaging);
    const record = await commitDurablePackageIntegrity({
      aiRoot: canonicalStaging,
      frameworkProfile,
    });
    if (record.cacheKey !== cacheKey || record.contentFingerprint !== contentFingerprint) {
      throw durableIntegrityError('Staged durable AI package integrity identity drifted.');
    }
    try {
      await rename(canonicalStaging, durableInstallRoot);
    } catch (error) {
      if (!['EEXIST', 'ENOTEMPTY'].includes(error?.code)) throw error;
      const canonicalDurableInstallRoot = await requireCanonicalDirectory(
        durableInstallRoot,
        'Active AI package root',
      );
      const concurrent = await verifyDurablePackageIntegrity({
        aiRoot: canonicalDurableInstallRoot,
        frameworkProfile,
        packageVersion: distribution.package.version,
      });
      if (
        concurrent.cacheKey !== cacheKey ||
        concurrent.contentFingerprint !== contentFingerprint
      ) {
        throw durableIntegrityError('Concurrent durable AI package materialization diverged.');
      }
    }
  } catch (error) {
    await rm(staging, {recursive: true, force: true}).catch(() => {});
    throw error;
  }
  const canonicalDurableInstallRoot = await requireCanonicalDirectory(
    durableInstallRoot,
    'Active AI package root',
  );
  return {
    aiRoot: canonicalDurableInstallRoot,
    durableInstallRoot: canonicalDurableInstallRoot,
  };
}

async function requireCanonicalDirectory(path, label) {
  if (!isAbsoluteProjectRoot(path)) throw new Error(`${label} must be an absolute path.`);
  const absolute = resolve(path);
  const canonical = await realpath(absolute).catch((error) => {
    if (error?.code === 'ENOENT') throw new Error(`${label} does not exist: ${absolute}`);
    throw error;
  });
  const metadata = await lstat(absolute);
  if (metadata.isSymbolicLink()) throw new Error(`${label} must not be a symbolic link.`);
  if (!metadata.isDirectory()) throw new Error(`${label} must be a regular directory.`);
  if (canonical !== absolute) {
    throw new Error(`${label} must not traverse a symbolic link; use its physical absolute path.`);
  }
  return absolute;
}

async function canonicalSourceRootOrUndefined(sourceRoot) {
  return sourceRoot === undefined
    ? undefined
    : requireCanonicalDirectory(sourceRoot, 'ViewCompose source root');
}

async function resolveFrameworkBinding({root, sourceRoot, aiRoot}) {
  if (sourceRoot !== undefined) {
    return Object.freeze({
      profileId: CURRENT_SOURCE_PROFILE,
      versionLane: 'current-source',
      projectProfile: null,
    });
  }
  const projectProfile = await detectFrameworkProjectProfile({projectRoot: root});
  return selectReleasedFrameworkProfile(projectProfile, {aiRoot});
}

async function assertSafePath(root, relativePath, {leafKind = 'any'} = {}) {
  let current = root;
  const segments = relativePath.split('/').filter(Boolean);
  for (let index = 0; index < segments.length; index += 1) {
    current = resolve(current, segments[index]);
    if (!contained(root, current)) throw new Error(`Agent target escapes the project root: ${relativePath}`);
    const metadata = await metadataOrNull(current);
    if (!metadata) return;
    if (metadata.isSymbolicLink()) throw new Error(`Agent target traverses a symbolic link: ${relativePath}`);
    const isLeaf = index === segments.length - 1;
    if (!isLeaf && !metadata.isDirectory()) {
      throw new Error(`Agent target parent is not a directory: ${relativePath}`);
    }
    if (isLeaf && leafKind === 'directory' && !metadata.isDirectory()) {
      throw new Error(`Agent target is not a directory: ${relativePath}`);
    }
    if (isLeaf && leafKind === 'file' && !metadata.isFile()) {
      throw new Error(`Agent target is not a regular file: ${relativePath}`);
    }
  }
}

async function ensureSafeDirectory(root, relativePath) {
  let current = root;
  for (const segment of relativePath.split('/').filter(Boolean)) {
    current = resolve(current, segment);
    if (!contained(root, current)) throw new Error(`Agent directory escapes the project root: ${relativePath}`);
    const metadata = await metadataOrNull(current);
    if (!metadata) {
      await mkdir(current);
    } else if (metadata.isSymbolicLink() || !metadata.isDirectory()) {
      throw new Error(`Agent directory is not a safe regular directory: ${relativePath}`);
    }
  }
}

async function atomicWrite(root, relativePath, bytes, mode = 0o644) {
  const parentRelative = dirname(relativePath) === '.' ? '' : dirname(relativePath);
  if (parentRelative) await ensureSafeDirectory(root, parentRelative);
  await assertSafePath(root, relativePath, {leafKind: 'file'});
  const target = resolve(root, relativePath);
  const temporary = resolve(dirname(target), `.${randomUUID()}.viewcompose.tmp`);
  if (!contained(root, temporary)) throw new Error(`Temporary target escapes project root: ${relativePath}`);
  try {
    await writeFile(temporary, bytes, {flag: 'wx', mode});
    await rename(temporary, target);
  } finally {
    await rm(temporary, {force: true}).catch(() => {});
  }
}

async function loadCanonicalSkills(aiRoot) {
  const absoluteAiRoot = await requireCanonicalDirectory(aiRoot, 'AI package root');
  const manifestPath = resolve(absoluteAiRoot, manifestRelativePath);
  if (!contained(absoluteAiRoot, manifestPath)) throw new Error('Skill manifest escapes the AI package.');
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
  if (manifest?.schemaVersion !== 1 || !Array.isArray(manifest.skills)) {
    throw new Error('Canonical Skill manifest is invalid.');
  }
  const ids = new Set();
  const skills = [];
  for (const entry of manifest.skills) {
    if (!/^[a-z][a-z0-9-]{2,63}$/u.test(entry?.id ?? '') || ids.has(entry.id)) {
      throw new Error(`Canonical Skill ID is invalid or duplicated: ${entry?.id ?? '<missing>'}.`);
    }
    if (entry.path !== `skills/${entry.id}/SKILL.md`) {
      throw new Error(`Canonical Skill path drifted for ${entry.id}.`);
    }
    const source = resolve(absoluteAiRoot, entry.path);
    const canonicalSource = await realpath(source);
    const metadata = await lstat(source);
    if (
      canonicalSource !== source ||
      !contained(resolve(absoluteAiRoot, 'skills'), source) ||
      !metadata.isFile() ||
      metadata.isSymbolicLink()
    ) {
      throw new Error(`Canonical Skill source is unsafe: ${entry.path}.`);
    }
    ids.add(entry.id);
    skills.push({id: entry.id, bytes: await readFile(source)});
  }
  return skills;
}

async function prepareSkillOperations({client, projectRoot, aiRoot = defaultAiRoot}) {
  const profile = profileFor(client);
  const root = await requireCanonicalDirectory(projectRoot, 'Consumer project root');
  const skills = await loadCanonicalSkills(aiRoot);
  const operations = [];
  await assertSafePath(root, profile.skillRoot, {leafKind: 'directory'});
  for (const skill of skills) {
    const skillDirectory = `${profile.skillRoot}/${skill.id}`;
    const targetRelative = `${skillDirectory}/SKILL.md`;
    await assertSafePath(root, skillDirectory, {leafKind: 'directory'});
    await assertSafePath(root, targetRelative, {leafKind: 'file'});
    const target = resolve(root, targetRelative);
    const metadata = await metadataOrNull(target);
    let status = 'missing';
    if (metadata) {
      const existing = await readFile(target);
      status = existing.equals(skill.bytes) ? 'ready' : 'conflict';
    }
    operations.push({...skill, skillDirectory, targetRelative, target, status});
  }
  return {profile, root, operations};
}

function assertNoSkillConflicts(operations) {
  const conflict = operations.find((operation) => operation.status === 'conflict');
  if (conflict) throw new Error(`Refusing to overwrite conflicting Skill bytes: ${conflict.targetRelative}`);
}

async function applySkillInstall(root, operations) {
  const created = [];
  try {
    for (const operation of operations.filter((item) => item.status === 'missing')) {
      await ensureSafeDirectory(root, operation.skillDirectory);
      await writeFile(operation.target, operation.bytes, {flag: 'wx', mode: 0o644});
      created.push(operation);
      operation.status = 'installed';
    }
  } catch (error) {
    for (const operation of created.reverse()) {
      await rm(operation.target, {force: true}).catch(() => {});
      operation.status = 'missing';
    }
    throw error;
  }
  return created;
}

async function rollbackSkillInstall(created) {
  for (const operation of [...created].reverse()) {
    await rm(operation.target, {force: true}).catch(() => {});
    operation.status = 'missing';
  }
}

export async function installAgentClientSkills({client, projectRoot, aiRoot = defaultAiRoot} = {}) {
  const prepared = await prepareSkillOperations({client, projectRoot, aiRoot});
  assertNoSkillConflicts(prepared.operations);
  const created = await applySkillInstall(prepared.root, prepared.operations);
  return {
    schemaVersion: 1,
    client: prepared.profile.id,
    projectRoot: prepared.root,
    skillRoot: prepared.profile.skillRoot,
    installed: created.map((item) => item.id),
    unchanged: prepared.operations.filter((item) => item.status === 'ready').map((item) => item.id),
  };
}

function equalJson(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function parseManagedTomlServer(text, relativePath) {
  const start = text.indexOf(`${managedTomlStart}\n`);
  const end = text.indexOf(`${managedTomlEnd}\n`);
  if (start < 0 || end < start || text.indexOf(managedTomlStart, start + 1) >= 0) {
    throw new Error(`Refusing to migrate an unknown MCP configuration: ${relativePath}`);
  }
  const block = text.slice(start, end + managedTomlEnd.length + 1);
  const stringValue = (name) => {
    const match = new RegExp(`^${name} = (.+)$`, 'mu').exec(block);
    if (!match) return undefined;
    try {
      const value = JSON.parse(match[1]);
      return typeof value === 'string' ? value : undefined;
    } catch {
      return undefined;
    }
  };
  const argsMatch = /^args = \[(.+)\]$/mu.exec(block);
  let argument;
  try {
    argument = argsMatch ? JSON.parse(argsMatch[1]) : undefined;
  } catch {
    argument = undefined;
  }
  const command = stringValue('command');
  const projectRoot = stringValue('VIEWCOMPOSE_PROJECT_ROOT');
  const frameworkProfile = stringValue(FRAMEWORK_PROFILE_ENVIRONMENT_VARIABLE);
  const sourceRoot = stringValue('VIEWCOMPOSE_SOURCE_ROOT');
  if (!command || !argument || !projectRoot || !frameworkProfile) {
    throw new Error(`Refusing to migrate malformed managed MCP configuration: ${relativePath}`);
  }
  return {
    server: {
      command,
      args: [argument],
      env: {
        VIEWCOMPOSE_PROJECT_ROOT: projectRoot,
        [FRAMEWORK_PROFILE_ENVIRONMENT_VARIABLE]: frameworkProfile,
        ...(sourceRoot === undefined ? {} : {VIEWCOMPOSE_SOURCE_ROOT: sourceRoot}),
      },
    },
    block,
  };
}

async function inspectManagedIntegration(profile, root) {
  await assertSafePath(root, profile.configPath, {leafKind: 'file'});
  const target = resolve(root, profile.configPath);
  const metadata = await metadataOrNull(target);
  if (!metadata?.isFile() || metadata.isSymbolicLink()) {
    throw new Error(`Managed MCP configuration is missing or unsafe: ${profile.configPath}`);
  }
  const original = await readFile(target, 'utf8');
  if (profile.configFormat === 'json') {
    const document = parseJsonObject(original, profile.configPath);
    const server = document.mcpServers?.viewcompose;
    if (!server || typeof server !== 'object' || Array.isArray(server)) {
      throw new Error(`Managed MCP configuration is missing: ${profile.configPath}`);
    }
    return {target, original, mode: metadata.mode & 0o777, document, server};
  }
  const parsed = parseManagedTomlServer(original, profile.configPath);
  const active = aiRootForManagedServer(parsed.server, root);
  const expectedBlock = managedTomlBlock(root, undefined, {
    frameworkProfile: active.frameworkProfile,
    nodeExecutable: parsed.server.command,
    mcpServerPath: parsed.server.args[0],
  });
  if (parsed.block !== expectedBlock) {
    throw new Error('Refusing to migrate user-edited managed MCP configuration.');
  }
  return {target, original, mode: metadata.mode & 0o777, ...parsed};
}

export async function inspectAgentClientInstallation({client, projectRoot} = {}) {
  const profile = profileFor(client);
  const root = await requireCanonicalDirectory(projectRoot, 'Consumer project root');
  const inspected = await inspectManagedIntegration(profile, root);
  const active = aiRootForManagedServer(inspected.server, root);
  const aiRoot = await requireCanonicalDirectory(active.aiRoot, 'Active AI package root');
  const distribution = JSON.parse(await readFile(resolve(aiRoot, 'distribution.json'), 'utf8'));
  if (
    distribution.package?.name !== '@viewcompose/ai-tooling' ||
    !stablePackageVersion(distribution.package?.version ?? '')
  ) {
    throw durableIntegrityError(
      'Active MCP entry does not point to a stable released ViewCompose AI package.',
    );
  }
  if (requiresDurablePackageIntegrity(distribution.package.version)) {
    await verifyDurablePackageIntegrity({
      aiRoot,
      frameworkProfile: active.frameworkProfile,
      packageVersion: distribution.package.version,
    });
  }
  return Object.freeze({
    client: profile.id,
    projectRoot: root,
    aiRoot,
    mcpServerPath: inspected.server.args[0],
    nodeExecutable: inspected.server.command,
    frameworkProfile: active.frameworkProfile,
    version: distribution.package.version,
  });
}

function aiRootForManagedServer(server, projectRoot) {
  const frameworkProfile = server.env?.[FRAMEWORK_PROFILE_ENVIRONMENT_VARIABLE];
  if (
    server.env?.VIEWCOMPOSE_PROJECT_ROOT !== projectRoot ||
    server.env?.VIEWCOMPOSE_SOURCE_ROOT !== undefined ||
    !/^[a-f0-9]{64}$/u.test(frameworkProfile ?? '') ||
    !isAbsolute(server.command) ||
    !Array.isArray(server.args) ||
    server.args.length !== 1 ||
    !isAbsolute(server.args[0]) ||
    basename(server.args[0]) !== 'mcp-server.mjs' ||
    basename(dirname(server.args[0])) !== 'scripts'
  ) {
    throw new Error('Refusing to migrate an MCP entry outside the version-bound managed shape.');
  }
  const aiRoot = resolve(server.args[0], '../..');
  const expected = expectedServerDefinition(projectRoot, {
    frameworkProfile,
    nodeExecutable: server.command,
    mcpServerPath: server.args[0],
  });
  if (!equalJson(server, expected)) {
    throw new Error('Refusing to migrate an MCP entry with unknown fields or ownership.');
  }
  return {aiRoot, frameworkProfile};
}

async function fileFingerprint(path) {
  const bytes = await readFile(path);
  return {bytes, sha256: sha256(bytes)};
}

async function recoverUpgradeTransaction(root) {
  await assertSafePath(root, upgradeJournalPath, {leafKind: 'file'});
  const journalFile = resolve(root, upgradeJournalPath);
  const metadata = await metadataOrNull(journalFile);
  if (!metadata) return {status: 'none'};
  if (!metadata.isFile() || metadata.isSymbolicLink()) {
    throw new Error('Upgrade recovery journal is unsafe.');
  }
  const journal = parseJsonObject(await readFile(journalFile, 'utf8'), upgradeJournalPath);
  if (
    journal.schemaVersion !== 1 ||
    !['prepared', 'committed'].includes(journal.status) ||
    !Array.isArray(journal.entries) ||
    journal.entries.length < 1 ||
    journal.entries.length > 32
  ) throw new Error('Upgrade recovery journal is invalid.');
  for (const entry of journal.entries) {
    for (const path of [entry.target, entry.backup, entry.staged]) {
      if (typeof path !== 'string' || path.length === 0) {
        throw new Error('Upgrade recovery journal contains an invalid path.');
      }
      await assertSafePath(root, path, {leafKind: 'file'});
    }
    const target = resolve(root, entry.target);
    const backup = resolve(root, entry.backup);
    const staged = resolve(root, entry.staged);
    if (journal.status === 'prepared') {
      const backupMetadata = await metadataOrNull(backup);
      if (backupMetadata) {
        if (!backupMetadata.isFile() || backupMetadata.isSymbolicLink()) {
          throw new Error(`Upgrade backup is unsafe: ${entry.backup}`);
        }
        await rm(target, {force: true});
        await rename(backup, target);
      } else {
        const targetMetadata = await metadataOrNull(target);
        if (!targetMetadata?.isFile() || targetMetadata.isSymbolicLink()) {
          throw new Error(`Upgrade recovery cannot restore ${entry.target}.`);
        }
        const actual = await fileFingerprint(target);
        if (actual.sha256 !== entry.originalSha256) {
          throw new Error(`Upgrade recovery found changed bytes at ${entry.target}.`);
        }
      }
    } else {
      const actual = await fileFingerprint(target);
      if (actual.sha256 !== entry.desiredSha256) {
        throw new Error(`Committed upgrade target changed before cleanup: ${entry.target}.`);
      }
      await rm(backup, {force: true});
    }
    await rm(staged, {force: true});
  }
  await rm(journalFile, {force: true});
  await rmdir(resolve(root, '.viewcompose')).catch((error) => {
    if (!['ENOENT', 'ENOTEMPTY'].includes(error?.code)) throw error;
  });
  return {status: journal.status === 'prepared' ? 'rolled-back' : 'committed-cleanup'};
}

async function applyUpgradeTransaction(root, changes, {afterWrite} = {}) {
  await recoverUpgradeTransaction(root);
  const transactionId = randomUUID();
  const entries = [];
  for (const [index, change] of changes.entries()) {
    const original = await fileFingerprint(change.target);
    if (original.sha256 !== change.expectedSha256) {
      throw new Error(`Managed bytes changed before upgrade: ${change.relativePath}`);
    }
    const suffix = `${transactionId}-${index}`;
    const directory = dirname(change.relativePath);
    const name = basename(change.relativePath);
    const staged = `${directory}/${name}.${suffix}.viewcompose-new`;
    const backup = `${directory}/${name}.${suffix}.viewcompose-old`;
    await writeFile(resolve(root, staged), change.next, {flag: 'wx', mode: change.mode});
    entries.push({
      target: change.relativePath,
      staged,
      backup,
      originalSha256: original.sha256,
      desiredSha256: sha256(change.next),
    });
  }
  const journal = {schemaVersion: 1, status: 'prepared', entries};
  await atomicWrite(root, upgradeJournalPath, `${JSON.stringify(journal, null, 2)}\n`, 0o600);
  try {
    for (let index = 0; index < entries.length; index += 1) {
      const entry = entries[index];
      await rename(resolve(root, entry.target), resolve(root, entry.backup));
      await rename(resolve(root, entry.staged), resolve(root, entry.target));
      await afterWrite?.(index, entry);
    }
    journal.status = 'committed';
    await atomicWrite(root, upgradeJournalPath, `${JSON.stringify(journal, null, 2)}\n`, 0o600);
    await recoverUpgradeTransaction(root);
  } catch (error) {
    await recoverUpgradeTransaction(root);
    throw error;
  }
}

export async function migrateAgentClient({
  client,
  projectRoot,
  newAiRoot,
  frameworkProfile,
  nodeExecutable = process.execPath,
  afterWrite,
} = {}) {
  const profile = profileFor(client);
  const root = await requireCanonicalDirectory(projectRoot, 'Consumer project root');
  const canonicalNewAiRoot = await requireCanonicalDirectory(newAiRoot, 'Candidate AI package root');
  const projectProfile = await detectFrameworkProjectProfile({projectRoot: root});
  if (!['empty', 'resolved'].includes(projectProfile.status)) {
    throw new Error(`Cannot migrate while project framework status is ${projectProfile.status}.`);
  }
  const candidateProfiles = await loadReleasedFrameworkProfiles({aiRoot: canonicalNewAiRoot});
  const candidate = candidateProfiles.profiles.find(({profile: candidateProfile}) =>
    candidateProfile.profileId === frameworkProfile);
  if (
    !candidate ||
    (projectProfile.status === 'resolved' &&
      !frameworkProfileMatchesProject(projectProfile.artifacts, candidate.profile))
  ) {
    throw new Error('Candidate framework profile no longer matches the consumer project.');
  }
  const inspected = await inspectManagedIntegration(profile, root);
  const active = aiRootForManagedServer(inspected.server, root);
  const canonicalOldAiRoot = await requireCanonicalDirectory(active.aiRoot, 'Active AI package root');
  const oldServerPath = resolve(canonicalOldAiRoot, 'scripts/mcp-server.mjs');
  if (oldServerPath !== inspected.server.args[0]) {
    throw new Error('Managed MCP server path differs from the active AI package root.');
  }
  const [oldSkills, newSkills, oldDistribution, newDistribution] = await Promise.all([
    loadCanonicalSkills(canonicalOldAiRoot),
    loadCanonicalSkills(canonicalNewAiRoot),
    readFile(resolve(canonicalOldAiRoot, 'distribution.json'), 'utf8').then(JSON.parse),
    readFile(resolve(canonicalNewAiRoot, 'distribution.json'), 'utf8').then(JSON.parse),
  ]);
  if (
    oldDistribution.package?.name !== '@viewcompose/ai-tooling' ||
    newDistribution.package?.name !== '@viewcompose/ai-tooling' ||
    JSON.stringify(oldSkills.map((skill) => skill.id)) !== JSON.stringify(newSkills.map((skill) => skill.id))
  ) {
    throw new Error('AI package identity or Skill set requires an unsupported migration contract.');
  }
  const newServer = expectedServerDefinition(root, {
    frameworkProfile,
    nodeExecutable,
    mcpServerPath: resolve(canonicalNewAiRoot, 'scripts/mcp-server.mjs'),
  });
  let nextConfig;
  if (profile.configFormat === 'json') {
    const document = {...inspected.document, mcpServers: {...inspected.document.mcpServers}};
    document.mcpServers.viewcompose = newServer;
    nextConfig = `${JSON.stringify(document, null, 2)}\n`;
  } else {
    const nextBlock = managedTomlBlock(root, undefined, {
      frameworkProfile,
      nodeExecutable,
      mcpServerPath: newServer.args[0],
    });
    nextConfig = inspected.original.replace(inspected.block, nextBlock);
  }
  const changes = [{
    relativePath: profile.configPath,
    target: inspected.target,
    expectedSha256: sha256(inspected.original),
    next: Buffer.from(nextConfig),
    mode: inspected.mode,
  }];
  for (let index = 0; index < oldSkills.length; index += 1) {
    const oldSkill = oldSkills[index];
    const newSkill = newSkills[index];
    const relativePath = `${profile.skillRoot}/${oldSkill.id}/SKILL.md`;
    await assertSafePath(root, relativePath, {leafKind: 'file'});
    changes.push({
      relativePath,
      target: resolve(root, relativePath),
      expectedSha256: sha256(oldSkill.bytes),
      next: newSkill.bytes,
      mode: 0o644,
    });
  }
  await applyUpgradeTransaction(root, changes, {afterWrite});
  return {
    schemaVersion: 1,
    client: profile.id,
    projectRoot: root,
    previousVersion: oldDistribution.package.version,
    installedVersion: newDistribution.package.version,
    previousFrameworkProfile: active.frameworkProfile,
    frameworkProfile,
    config: {path: profile.configPath, status: 'migrated'},
    skills: {path: profile.skillRoot, migrated: newSkills.map((skill) => skill.id)},
  };
}

function managedTomlBlock(projectRoot, sourceRoot, options) {
  return `${managedTomlStart}\n${renderAgentClientConfig('codex', projectRoot, {
    ...options,
    sourceRoot,
  })}${managedTomlEnd}\n`;
}

function parseJsonObject(text, relativePath) {
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch (error) {
    throw new Error(`Refusing to modify invalid JSON configuration ${relativePath}: ${error.message}`);
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`Refusing to modify non-object JSON configuration: ${relativePath}`);
  }
  if (
    Object.hasOwn(parsed, 'mcpServers') &&
    (!parsed.mcpServers || typeof parsed.mcpServers !== 'object' || Array.isArray(parsed.mcpServers))
  ) {
    throw new Error(`Refusing to replace non-object mcpServers configuration: ${relativePath}`);
  }
  return parsed;
}

async function prepareConfigOperation({
  profile,
  root,
  sourceRoot,
  frameworkProfile,
  options = {},
  action = 'install',
}) {
  await assertSafePath(root, profile.configPath, {leafKind: 'file'});
  const target = resolve(root, profile.configPath);
  const metadata = await metadataOrNull(target);
  const original = metadata ? await readFile(target, 'utf8') : undefined;
  const mode = metadata ? metadata.mode & 0o777 : 0o644;
  if (profile.configFormat === 'json') {
    const server = expectedServerDefinition(root, {...options, sourceRoot, frameworkProfile});
    const document = original === undefined ? {} : parseJsonObject(original, profile.configPath);
    const existing = document.mcpServers?.viewcompose;
    if (action === 'install') {
      if (existing !== undefined && !equalJson(existing, server)) {
        throw new Error(`Refusing to overwrite conflicting MCP configuration: ${profile.configPath}`);
      }
      if (existing !== undefined) {
        return {target, relativePath: profile.configPath, original, next: original, mode, status: 'ready'};
      }
      const nextDocument = {...document, mcpServers: {...(document.mcpServers ?? {}), viewcompose: server}};
      return {
        target,
        relativePath: profile.configPath,
        original,
        next: `${JSON.stringify(nextDocument, null, 2)}\n`,
        mode,
        status: 'missing',
      };
    }
    if (existing === undefined) {
      return {target, relativePath: profile.configPath, original, next: original, mode, status: 'missing'};
    }
    if (!equalJson(existing, server)) {
      throw new Error(`Refusing to remove conflicting MCP configuration: ${profile.configPath}`);
    }
    const nextDocument = {...document, mcpServers: {...document.mcpServers}};
    delete nextDocument.mcpServers.viewcompose;
    return {
      target,
      relativePath: profile.configPath,
      original,
      next: `${JSON.stringify(nextDocument, null, 2)}\n`,
      mode,
      status: 'ready',
    };
  }

  const block = managedTomlBlock(root, sourceRoot, {...options, frameworkProfile});
  const text = original ?? '';
  const hasSection = /^\s*\[mcp_servers\.viewcompose(?:\.env)?\]\s*$/mu.test(text);
  const occurrences = text.split(block).length - 1;
  if (action === 'install') {
    if (occurrences === 1) {
      return {target, relativePath: profile.configPath, original, next: original, mode, status: 'ready'};
    }
    if (occurrences > 1 || hasSection) {
      throw new Error(`Refusing to overwrite conflicting MCP configuration: ${profile.configPath}`);
    }
    const separator = text.length === 0 || text.endsWith('\n') ? '' : '\n';
    return {
      target,
      relativePath: profile.configPath,
      original,
      next: `${text}${separator}${block}`,
      mode,
      status: 'missing',
    };
  }
  if (occurrences === 0) {
    if (hasSection) throw new Error(`Refusing to remove conflicting MCP configuration: ${profile.configPath}`);
    return {target, relativePath: profile.configPath, original, next: original, mode, status: 'missing'};
  }
  if (occurrences > 1) {
    throw new Error(`Refusing to remove duplicated MCP configuration: ${profile.configPath}`);
  }
  return {
    target,
    relativePath: profile.configPath,
    original,
    next: text.replace(block, ''),
    mode,
    status: 'ready',
  };
}

async function applyConfigOperation(root, operation) {
  if (operation.next === operation.original) return false;
  await atomicWrite(root, operation.relativePath, operation.next, operation.mode);
  return true;
}

async function rollbackConfigOperation(root, operation) {
  if (operation.next === operation.original) return;
  if (operation.original === undefined) {
    await rm(operation.target, {force: true});
  } else {
    await atomicWrite(root, operation.relativePath, operation.original, operation.mode);
  }
}

export async function initializeAgentClient({
  client,
  projectRoot = process.cwd(),
  sourceRoot,
  aiRoot = defaultAiRoot,
  nodeExecutable = process.execPath,
  mcpServerPath = defaultMcpServerPath,
  cacheRoot,
} = {}) {
  const canonicalSourceRoot = await canonicalSourceRootOrUndefined(sourceRoot);
  const prepared = await prepareSkillOperations({client, projectRoot, aiRoot});
  const framework = await resolveFrameworkBinding({
    root: prepared.root,
    sourceRoot: canonicalSourceRoot,
    aiRoot,
  });
  const durable = canonicalSourceRoot === undefined
    ? await materializeDurablePackage(aiRoot, framework.profileId, cacheRoot)
    : {aiRoot, durableInstallRoot: null};
  const effectiveMcpServerPath = durable.durableInstallRoot
    ? resolve(durable.aiRoot, 'scripts/mcp-server.mjs')
    : mcpServerPath;
  assertNoSkillConflicts(prepared.operations);
  const config = await prepareConfigOperation({
    profile: prepared.profile,
    root: prepared.root,
    sourceRoot: canonicalSourceRoot,
    frameworkProfile: framework.profileId,
    options: {nodeExecutable, mcpServerPath: effectiveMcpServerPath},
  });
  let configChanged = false;
  let created = [];
  try {
    configChanged = await applyConfigOperation(prepared.root, config);
    created = await applySkillInstall(prepared.root, prepared.operations);
  } catch (error) {
    await rollbackSkillInstall(created);
    if (configChanged) await rollbackConfigOperation(prepared.root, config);
    throw error;
  }
  return {
    schemaVersion: 1,
    client: prepared.profile.id,
    projectRoot: prepared.root,
    mode: canonicalSourceRoot === undefined ? 'project-bound' : 'source-bound',
    framework: {
      versionLane: framework.versionLane,
      profileId: framework.profileId,
      projectStatus: framework.projectProfile?.status ?? 'source-bound',
    },
    config: {path: prepared.profile.configPath, status: configChanged ? 'installed' : 'unchanged'},
    skills: {
      path: prepared.profile.skillRoot,
      installed: created.map((item) => item.id),
      unchanged: prepared.operations.filter((item) => item.status === 'ready').map((item) => item.id),
    },
    durableInstallRoot: durable.durableInstallRoot,
  };
}

export async function diagnoseAgentClient({
  client,
  projectRoot = process.cwd(),
  sourceRoot,
  aiRoot = defaultAiRoot,
  nodeExecutable = process.execPath,
  mcpServerPath = defaultMcpServerPath,
  detectJava = detectJavaRuntime,
  detectSdk = detectAndroidSdk,
} = {}) {
  const canonicalSourceRoot = await canonicalSourceRootOrUndefined(sourceRoot);
  let activeInstallation;
  let activeInstallationError;
  if (canonicalSourceRoot === undefined) {
    try {
      activeInstallation = await inspectAgentClientInstallation({client, projectRoot});
    } catch (error) {
      if (error?.code === 'VC_AI_DURABLE_INTEGRITY') activeInstallationError = error;
    }
  }
  const effectiveAiRoot = activeInstallation?.aiRoot ?? aiRoot;
  const effectiveNodeExecutable = activeInstallation?.nodeExecutable ?? nodeExecutable;
  const effectiveMcpServerPath = activeInstallation?.mcpServerPath ?? mcpServerPath;
  const prepared = await prepareSkillOperations({client, projectRoot, aiRoot: effectiveAiRoot});
  let configStatus = 'conflict';
  let configDetail;
  let framework;
  try {
    if (activeInstallationError) throw activeInstallationError;
    if (activeInstallation) {
      const projectProfile = await detectFrameworkProjectProfile({projectRoot: prepared.root});
      const inventory = await loadReleasedFrameworkProfiles({aiRoot: effectiveAiRoot});
      const activeProfile = inventory.profiles.find(({profile: candidateProfile}) =>
        candidateProfile.profileId === activeInstallation.frameworkProfile)?.profile;
      if (
        !activeProfile ||
        !['empty', 'resolved'].includes(projectProfile.status) ||
        (projectProfile.status === 'resolved' &&
          !frameworkProfileMatchesProject(projectProfile.artifacts, activeProfile))
      ) {
        throw new Error('Installed framework profile no longer matches the consumer project.');
      }
      framework = {
        profileId: activeInstallation.frameworkProfile,
        versionLane: 'released',
        projectProfile,
      };
    } else {
      framework = await resolveFrameworkBinding({
        root: prepared.root,
        sourceRoot: canonicalSourceRoot,
        aiRoot: effectiveAiRoot,
      });
    }
    const config = await prepareConfigOperation({
      profile: prepared.profile,
      root: prepared.root,
      sourceRoot: canonicalSourceRoot,
      frameworkProfile: framework.profileId,
      options: {
        nodeExecutable: effectiveNodeExecutable,
        mcpServerPath: effectiveMcpServerPath,
      },
    });
    configStatus = config.status === 'ready' ? 'ready' : 'missing';
  } catch (error) {
    configDetail = error.message;
  }
  const missing = prepared.operations.filter((item) => item.status === 'missing').map((item) => item.id);
  const conflicts = prepared.operations.filter((item) => item.status === 'conflict').map((item) => item.id);
  const skillsStatus = conflicts.length > 0 ? 'conflict' : missing.length > 0 ? 'missing' : 'ready';
  const configurationReady = configStatus === 'ready' && skillsStatus === 'ready';
  const java = detectJava();
  const androidSdk = detectSdk(36);
  const hostReady = [17, 21].includes(java?.feature) && androidSdk?.apiLevel === 36;
  const ready = configurationReady && hostReady;
  const readyStatus = canonicalSourceRoot === undefined
    ? 'project-bound-ready'
    : 'source-bound-ready';
  return {
    schemaVersion: 1,
    client: prepared.profile.id,
    projectRoot: prepared.root,
    mode: canonicalSourceRoot === undefined ? 'project-bound' : 'source-bound',
    tooling: activeInstallation ? {
      version: activeInstallation.version,
      packageRoot: activeInstallation.aiRoot,
    } : {version: null, packageRoot: effectiveAiRoot},
    framework: framework ? {
      status: 'compatible',
      versionLane: framework.versionLane,
      profileId: framework.profileId,
      projectStatus: framework.projectProfile?.status ?? 'source-bound',
    } : {status: 'incompatible', detail: configDetail},
    status: ready ? readyStatus : configurationReady
      ? 'host-prerequisites-required'
      : 'repair-required',
    config: {
      path: prepared.profile.configPath,
      status: configStatus,
      ...(configDetail ? {detail: configDetail} : {}),
    },
    skills: {
      path: prepared.profile.skillRoot,
      status: skillsStatus,
      expected: prepared.operations.length,
      ready: prepared.operations.length - missing.length - conflicts.length,
      missing,
      conflicts,
    },
    capabilities: {
      knowledgeAndGeneration: configurationReady ? 'ready' : 'repair-required',
      compilationPreviewAndLayout: ready ? readyStatus : configurationReady
        ? 'host-prerequisites-required'
        : 'repair-required',
    },
    host: {
      status: hostReady ? 'ready' : 'prerequisites-required',
      java: java && [17, 21].includes(java.feature)
        ? {status: 'ready', feature: java.feature, home: java.javaHome}
        : {status: 'required', acceptedFeatures: [17, 21]},
      androidSdk: androidSdk?.apiLevel === 36
        ? {status: 'ready', apiLevel: 36, root: androidSdk.root}
        : {status: 'required', apiLevel: 36},
    },
  };
}

async function removeEmptySkillDirectories(root, operations, skillRoot) {
  for (const operation of [...operations].reverse()) {
    await rmdir(resolve(root, operation.skillDirectory)).catch((error) => {
      if (!['ENOENT', 'ENOTEMPTY'].includes(error?.code)) throw error;
    });
  }
  await rmdir(resolve(root, skillRoot)).catch((error) => {
    if (!['ENOENT', 'ENOTEMPTY'].includes(error?.code)) throw error;
  });
}

export async function uninstallAgentClient({
  client,
  projectRoot = process.cwd(),
  sourceRoot,
  aiRoot = defaultAiRoot,
  nodeExecutable = process.execPath,
  mcpServerPath = defaultMcpServerPath,
} = {}) {
  const canonicalSourceRoot = await canonicalSourceRootOrUndefined(sourceRoot);
  const activeInstallation = canonicalSourceRoot === undefined
    ? await inspectAgentClientInstallation({client, projectRoot}).catch(() => null)
    : null;
  const effectiveAiRoot = activeInstallation?.aiRoot ?? aiRoot;
  const prepared = await prepareSkillOperations({client, projectRoot, aiRoot: effectiveAiRoot});
  const framework = activeInstallation
    ? {
      profileId: activeInstallation.frameworkProfile,
      versionLane: 'released',
    }
    : await resolveFrameworkBinding({
      root: prepared.root,
      sourceRoot: canonicalSourceRoot,
      aiRoot: effectiveAiRoot,
    });
  assertNoSkillConflicts(prepared.operations);
  const config = await prepareConfigOperation({
    profile: prepared.profile,
    root: prepared.root,
    sourceRoot: canonicalSourceRoot,
    frameworkProfile: framework.profileId,
    options: {
      nodeExecutable: activeInstallation?.nodeExecutable ?? nodeExecutable,
      mcpServerPath: activeInstallation?.mcpServerPath ?? mcpServerPath,
    },
    action: 'uninstall',
  });
  const removable = prepared.operations.filter((item) => item.status === 'ready');
  const staged = [];
  let configChanged = false;
  try {
    for (const operation of removable) {
      const staging = `${operation.target}.${randomUUID()}.viewcompose-remove`;
      await rename(operation.target, staging);
      staged.push({operation, staging});
    }
    configChanged = await applyConfigOperation(prepared.root, config);
  } catch (error) {
    if (configChanged) await rollbackConfigOperation(prepared.root, config);
    for (const item of staged.reverse()) await rename(item.staging, item.operation.target).catch(() => {});
    throw error;
  }
  for (const item of staged) await rm(item.staging, {force: true});
  await removeEmptySkillDirectories(prepared.root, removable, prepared.profile.skillRoot);
  return {
    schemaVersion: 1,
    client: prepared.profile.id,
    projectRoot: prepared.root,
    mode: canonicalSourceRoot === undefined ? 'project-bound' : 'source-bound',
    framework: {versionLane: framework.versionLane, profileId: framework.profileId},
    config: {path: prepared.profile.configPath, status: configChanged ? 'removed' : 'absent'},
    skills: {
      path: prepared.profile.skillRoot,
      removed: removable.map((item) => item.id),
      absent: prepared.operations.filter((item) => item.status === 'missing').map((item) => item.id),
    },
  };
}

function parseCommandLine(arguments_) {
  const [command, ...tokens] = arguments_;
  if (!['config', 'install-skills', 'init', 'doctor', 'upgrade', 'uninstall'].includes(command)) {
    throw new Error(
      'Usage: viewcompose-agent <config|install-skills|init|doctor|upgrade|uninstall> ' +
      '--client <codex|claude-code|cursor> --project-root <absolute-path> ' +
      '[--source-root <absolute-path>]',
    );
  }
  if (tokens.length % 2 !== 0) throw new Error(`Invalid ${command} arguments.`);
  const values = {};
  for (let index = 0; index < tokens.length; index += 2) {
    const option = tokens[index];
    const value = tokens[index + 1];
    if (!option?.startsWith('--') || value === undefined || value.startsWith('--')) {
      throw new Error(`Invalid ${command} arguments.`);
    }
    const key = option.slice(2);
    if (Object.hasOwn(values, key)) throw new Error(`Duplicate option ${option}.`);
    values[key] = value;
  }
  const required = ['client'];
  const allowed = command === 'config' || command === 'install-skills'
    ? new Set([...required, 'project-root', ...(command === 'config' ? ['source-root'] : [])])
    : new Set([...required, 'project-root', 'source-root']);
  const unexpected = Object.keys(values).filter((key) => !allowed.has(key));
  if (required.some((key) => !Object.hasOwn(values, key)) || unexpected.length > 0 ||
      (command === 'config' && !Object.hasOwn(values, 'project-root'))) {
    throw new Error(`Invalid ${command} options.`);
  }
  return {command, values};
}

async function main() {
  const {command, values} = parseCommandLine(process.argv.slice(2));
  if (command === 'config') {
    const sourceRoot = await canonicalSourceRootOrUndefined(values['source-root']);
    const projectRoot = await requireCanonicalDirectory(
      values['project-root'],
      'Consumer project root',
    );
    const framework = await resolveFrameworkBinding({
      root: projectRoot,
      sourceRoot,
      aiRoot: defaultAiRoot,
    });
    process.stdout.write(renderAgentClientConfig(values.client, projectRoot, {
      sourceRoot,
      frameworkProfile: framework.profileId,
    }));
    return;
  }
  const arguments_ = {
    client: values.client,
    ...(values['project-root'] === undefined ? {} : {projectRoot: values['project-root']}),
    sourceRoot: values['source-root'],
  };
  if (command === 'install-skills') {
    const result = await installAgentClientSkills(arguments_);
    process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
    return;
  }
  if (command === 'upgrade') {
    const {upgradeAgentClient} = await import('./tooling-upgrade.mjs');
    const result = await upgradeAgentClient(arguments_);
    process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
    return;
  }
  const operation = command === 'init'
    ? initializeAgentClient
    : command === 'doctor' ? diagnoseAgentClient : uninstallAgentClient;
  const result = await operation(arguments_);
  if (command === 'init') {
    result.readiness = await diagnoseAgentClient(arguments_);
  }
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
  if (command === 'doctor' && !result.status.endsWith('-ready')) process.exitCode = 1;
}

const entryPath = process.argv[1] ? realpathSync(resolve(process.argv[1])) : '';
if (entryPath === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`ViewCompose agent integration failed: ${error.message}\n`);
    process.exitCode = 2;
  });
}
