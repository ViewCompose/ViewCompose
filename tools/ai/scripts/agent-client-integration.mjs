#!/usr/bin/env node
import {realpathSync} from 'node:fs';
import {lstat, mkdir, readFile, realpath, writeFile} from 'node:fs/promises';
import {isAbsolute, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';

const defaultAiRoot = fileURLToPath(new URL('../', import.meta.url));
const defaultMcpServerPath = fileURLToPath(new URL('./mcp-server.mjs', import.meta.url));
const manifestRelativePath = 'skills/manifest.json';

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

export function renderAgentClientConfig(client, sourceRoot, {
  nodeExecutable = process.execPath,
  mcpServerPath = defaultMcpServerPath,
} = {}) {
  const profile = profileFor(client);
  if (!isAbsolute(sourceRoot) || !isAbsolute(nodeExecutable) || !isAbsolute(mcpServerPath)) {
    throw new Error('Configuration paths must be absolute.');
  }
  if (profile.configFormat === 'toml') {
    return [
      '[mcp_servers.viewcompose]',
      `command = ${tomlString(nodeExecutable)}`,
      `args = [${tomlString(mcpServerPath)}]`,
      '',
      '[mcp_servers.viewcompose.env]',
      `VIEWCOMPOSE_SOURCE_ROOT = ${tomlString(sourceRoot)}`,
      '',
    ].join('\n');
  }
  return `${JSON.stringify({
    mcpServers: {
      viewcompose: {
        command: nodeExecutable,
        args: [mcpServerPath],
        env: {VIEWCOMPOSE_SOURCE_ROOT: sourceRoot},
      },
    },
  }, null, 2)}\n`;
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
  if (metadata.isSymbolicLink()) {
    throw new Error(`${label} must not be a symbolic link.`);
  }
  if (!metadata.isDirectory()) {
    throw new Error(`${label} must be a regular directory.`);
  }
  if (canonical !== absolute) {
    throw new Error(`${label} must not traverse a symbolic link; use its physical absolute path.`);
  }
  return absolute;
}

async function assertSafePath(root, relativePath, {leafKind = 'any'} = {}) {
  let current = root;
  const segments = relativePath.split('/').filter(Boolean);
  for (let index = 0; index < segments.length; index += 1) {
    current = resolve(current, segments[index]);
    if (!contained(root, current)) throw new Error(`Skill target escapes the project root: ${relativePath}`);
    const metadata = await metadataOrNull(current);
    if (!metadata) return;
    if (metadata.isSymbolicLink()) {
      throw new Error(`Skill target traverses a symbolic link: ${relativePath}`);
    }
    const isLeaf = index === segments.length - 1;
    if (!isLeaf && !metadata.isDirectory()) {
      throw new Error(`Skill target parent is not a directory: ${relativePath}`);
    }
    if (isLeaf && leafKind === 'directory' && !metadata.isDirectory()) {
      throw new Error(`Skill target is not a directory: ${relativePath}`);
    }
    if (isLeaf && leafKind === 'file' && !metadata.isFile()) {
      throw new Error(`Skill target is not a regular file: ${relativePath}`);
    }
  }
}

async function ensureSafeDirectory(root, relativePath) {
  let current = root;
  for (const segment of relativePath.split('/').filter(Boolean)) {
    current = resolve(current, segment);
    if (!contained(root, current)) throw new Error(`Skill directory escapes the project root: ${relativePath}`);
    const metadata = await metadataOrNull(current);
    if (!metadata) {
      await mkdir(current);
    } else if (metadata.isSymbolicLink() || !metadata.isDirectory()) {
      throw new Error(`Skill directory is not a safe regular directory: ${relativePath}`);
    }
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

export async function installAgentClientSkills({
  client,
  projectRoot,
  aiRoot = defaultAiRoot,
} = {}) {
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
    if (metadata) {
      const existing = await readFile(target);
      if (!existing.equals(skill.bytes)) {
        throw new Error(`Refusing to overwrite conflicting Skill bytes: ${targetRelative}`);
      }
      operations.push({...skill, skillDirectory, targetRelative, target, status: 'unchanged'});
    } else {
      operations.push({...skill, skillDirectory, targetRelative, target, status: 'installed'});
    }
  }

  for (const operation of operations.filter((item) => item.status === 'installed')) {
    await ensureSafeDirectory(root, operation.skillDirectory);
    await writeFile(operation.target, operation.bytes, {flag: 'wx', mode: 0o644}).catch(async (error) => {
      if (error?.code !== 'EEXIST') throw error;
      await assertSafePath(root, operation.targetRelative, {leafKind: 'file'});
      const existing = await readFile(operation.target);
      if (!existing.equals(operation.bytes)) {
        throw new Error(`Refusing to overwrite conflicting Skill bytes: ${operation.targetRelative}`);
      }
      operation.status = 'unchanged';
    });
  }

  const installed = operations.filter((item) => item.status === 'installed').map((item) => item.id);
  const unchanged = operations.filter((item) => item.status === 'unchanged').map((item) => item.id);
  return {
    schemaVersion: 1,
    client: profile.id,
    projectRoot: root,
    skillRoot: profile.skillRoot,
    installed,
    unchanged,
  };
}

function parseCommandLine(arguments_) {
  const [command, ...tokens] = arguments_;
  if (!['config', 'install-skills'].includes(command)) {
    throw new Error(
      'Usage: viewcompose-agent config --client <codex|claude-code|cursor> ' +
      '--source-root <absolute-path> | install-skills --client <client> ' +
      '--project-root <absolute-path>',
    );
  }
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
  const expected = command === 'config'
    ? ['client', 'source-root']
    : ['client', 'project-root'];
  if (
    Object.keys(values).length !== expected.length ||
    expected.some((key) => !Object.hasOwn(values, key))
  ) {
    throw new Error(`Invalid ${command} options; expected ${expected.map((key) => `--${key}`).join(' and ')}.`);
  }
  return {command, values};
}

async function main() {
  const {command, values} = parseCommandLine(process.argv.slice(2));
  if (command === 'config') {
    const sourceRoot = await requireCanonicalDirectory(values['source-root'], 'ViewCompose source root');
    process.stdout.write(renderAgentClientConfig(values.client, sourceRoot));
    return;
  }
  const result = await installAgentClientSkills({
    client: values.client,
    projectRoot: values['project-root'],
  });
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
}

const entryPath = process.argv[1] ? realpathSync(resolve(process.argv[1])) : '';
if (entryPath === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`ViewCompose agent integration failed: ${error.message}\n`);
    process.exitCode = 2;
  });
}
