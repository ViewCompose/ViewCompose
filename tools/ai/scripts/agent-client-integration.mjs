#!/usr/bin/env node
import {randomUUID} from 'node:crypto';
import {realpathSync} from 'node:fs';
import {
  lstat,
  mkdir,
  readFile,
  realpath,
  rename,
  rm,
  rmdir,
  writeFile,
} from 'node:fs/promises';
import {dirname, isAbsolute, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';

const defaultAiRoot = fileURLToPath(new URL('../', import.meta.url));
const defaultMcpServerPath = fileURLToPath(new URL('./mcp-server.mjs', import.meta.url));
const manifestRelativePath = 'skills/manifest.json';
const managedTomlStart = '# ViewCompose AI managed configuration — start';
const managedTomlEnd = '# ViewCompose AI managed configuration — end';

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

function expectedServerDefinition(sourceRoot, {
  nodeExecutable = process.execPath,
  mcpServerPath = defaultMcpServerPath,
} = {}) {
  if (!isAbsolute(nodeExecutable) || !isAbsolute(mcpServerPath)) {
    throw new Error('Configuration paths must be absolute.');
  }
  if (sourceRoot !== undefined && !isAbsolute(sourceRoot)) {
    throw new Error('Configuration paths must be absolute.');
  }
  return {
    command: nodeExecutable,
    args: [mcpServerPath],
    ...(sourceRoot === undefined ? {} : {env: {VIEWCOMPOSE_SOURCE_ROOT: sourceRoot}}),
  };
}

export function renderAgentClientConfig(client, sourceRoot, options = {}) {
  const profile = profileFor(client);
  const server = expectedServerDefinition(sourceRoot, options);
  if (profile.configFormat === 'toml') {
    const lines = [
      '[mcp_servers.viewcompose]',
      `command = ${tomlString(server.command)}`,
      `args = [${tomlString(server.args[0])}]`,
      '',
    ];
    if (server.env) {
      lines.push(
        '[mcp_servers.viewcompose.env]',
        `VIEWCOMPOSE_SOURCE_ROOT = ${tomlString(server.env.VIEWCOMPOSE_SOURCE_ROOT)}`,
        '',
      );
    }
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

async function requireCanonicalDirectory(path, label) {
  if (!isAbsolute(path)) throw new Error(`${label} must be an absolute path.`);
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

function managedTomlBlock(sourceRoot, options) {
  return `${managedTomlStart}\n${renderAgentClientConfig('codex', sourceRoot, options)}${managedTomlEnd}\n`;
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

async function prepareConfigOperation({profile, root, sourceRoot, options = {}, action = 'install'}) {
  await assertSafePath(root, profile.configPath, {leafKind: 'file'});
  const target = resolve(root, profile.configPath);
  const metadata = await metadataOrNull(target);
  const original = metadata ? await readFile(target, 'utf8') : undefined;
  const mode = metadata ? metadata.mode & 0o777 : 0o644;
  if (profile.configFormat === 'json') {
    const server = expectedServerDefinition(sourceRoot, options);
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

  const block = managedTomlBlock(sourceRoot, options);
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
  projectRoot,
  sourceRoot,
  aiRoot = defaultAiRoot,
  nodeExecutable = process.execPath,
  mcpServerPath = defaultMcpServerPath,
} = {}) {
  const canonicalSourceRoot = await canonicalSourceRootOrUndefined(sourceRoot);
  const prepared = await prepareSkillOperations({client, projectRoot, aiRoot});
  assertNoSkillConflicts(prepared.operations);
  const config = await prepareConfigOperation({
    profile: prepared.profile,
    root: prepared.root,
    sourceRoot: canonicalSourceRoot,
    options: {nodeExecutable, mcpServerPath},
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
    mode: canonicalSourceRoot === undefined ? 'standalone' : 'source-bound',
    config: {path: prepared.profile.configPath, status: configChanged ? 'installed' : 'unchanged'},
    skills: {
      path: prepared.profile.skillRoot,
      installed: created.map((item) => item.id),
      unchanged: prepared.operations.filter((item) => item.status === 'ready').map((item) => item.id),
    },
  };
}

export async function diagnoseAgentClient({
  client,
  projectRoot,
  sourceRoot,
  aiRoot = defaultAiRoot,
  nodeExecutable = process.execPath,
  mcpServerPath = defaultMcpServerPath,
} = {}) {
  const canonicalSourceRoot = await canonicalSourceRootOrUndefined(sourceRoot);
  const prepared = await prepareSkillOperations({client, projectRoot, aiRoot});
  let configStatus = 'conflict';
  let configDetail;
  try {
    const config = await prepareConfigOperation({
      profile: prepared.profile,
      root: prepared.root,
      sourceRoot: canonicalSourceRoot,
      options: {nodeExecutable, mcpServerPath},
    });
    configStatus = config.status === 'ready' ? 'ready' : 'missing';
  } catch (error) {
    configDetail = error.message;
  }
  const missing = prepared.operations.filter((item) => item.status === 'missing').map((item) => item.id);
  const conflicts = prepared.operations.filter((item) => item.status === 'conflict').map((item) => item.id);
  const skillsStatus = conflicts.length > 0 ? 'conflict' : missing.length > 0 ? 'missing' : 'ready';
  const ready = configStatus === 'ready' && skillsStatus === 'ready';
  return {
    schemaVersion: 1,
    client: prepared.profile.id,
    projectRoot: prepared.root,
    mode: canonicalSourceRoot === undefined ? 'standalone' : 'source-bound',
    status: ready
      ? canonicalSourceRoot === undefined ? 'standalone-ready' : 'source-bound-ready'
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
      knowledgeAndGeneration: ready ? 'ready' : 'repair-required',
      compilationPreviewAndLayout: canonicalSourceRoot === undefined
        ? 'source-root-required'
        : ready ? 'source-bound-ready' : 'repair-required',
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
  projectRoot,
  sourceRoot,
  aiRoot = defaultAiRoot,
  nodeExecutable = process.execPath,
  mcpServerPath = defaultMcpServerPath,
} = {}) {
  const canonicalSourceRoot = await canonicalSourceRootOrUndefined(sourceRoot);
  const prepared = await prepareSkillOperations({client, projectRoot, aiRoot});
  assertNoSkillConflicts(prepared.operations);
  const config = await prepareConfigOperation({
    profile: prepared.profile,
    root: prepared.root,
    sourceRoot: canonicalSourceRoot,
    options: {nodeExecutable, mcpServerPath},
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
    mode: canonicalSourceRoot === undefined ? 'standalone' : 'source-bound',
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
  if (!['config', 'install-skills', 'init', 'doctor', 'uninstall'].includes(command)) {
    throw new Error(
      'Usage: viewcompose-agent <config|install-skills|init|doctor|uninstall> ' +
      '--client <codex|claude-code|cursor> [--project-root <absolute-path>] ' +
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
  const required = command === 'config' ? ['client'] : ['client', 'project-root'];
  const allowed = command === 'install-skills'
    ? new Set(required)
    : new Set([...required, 'source-root']);
  const unexpected = Object.keys(values).filter((key) => !allowed.has(key));
  if (required.some((key) => !Object.hasOwn(values, key)) || unexpected.length > 0) {
    throw new Error(`Invalid ${command} options.`);
  }
  return {command, values};
}

async function main() {
  const {command, values} = parseCommandLine(process.argv.slice(2));
  if (command === 'config') {
    const sourceRoot = await canonicalSourceRootOrUndefined(values['source-root']);
    process.stdout.write(renderAgentClientConfig(values.client, sourceRoot));
    return;
  }
  const arguments_ = {
    client: values.client,
    projectRoot: values['project-root'],
    sourceRoot: values['source-root'],
  };
  if (command === 'install-skills') {
    const result = await installAgentClientSkills(arguments_);
    process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
    return;
  }
  const operation = command === 'init'
    ? initializeAgentClient
    : command === 'doctor' ? diagnoseAgentClient : uninstallAgentClient;
  const result = await operation(arguments_);
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
  if (command === 'doctor' && result.status === 'repair-required') process.exitCode = 1;
}

const entryPath = process.argv[1] ? realpathSync(resolve(process.argv[1])) : '';
if (entryPath === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`ViewCompose agent integration failed: ${error.message}\n`);
    process.exitCode = 2;
  });
}
