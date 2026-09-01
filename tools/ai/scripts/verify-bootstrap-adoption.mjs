#!/usr/bin/env node
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {access, lstat, mkdir, mkdtemp, readFile, realpath, rm, symlink} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createDistribution} from './package-distribution.mjs';

const aiRoot = fileURLToPath(new URL('../', import.meta.url));
const expectedClients = Object.freeze({
  codex: {configPath: '.codex/config.toml', skillRoot: '.agents/skills'},
  'claude-code': {configPath: '.mcp.json', skillRoot: '.claude/skills'},
  cursor: {configPath: '.cursor/mcp.json', skillRoot: '.agents/skills'},
});
const expectedPackage = Object.freeze({name: '@viewcompose/ai-tooling', version: '0.5.0'});

function contained(root, candidate) {
  const path = relative(resolve(root), resolve(candidate));
  return path === '' || (!path.startsWith(`..${sep}`) && path !== '..');
}

function npmInvocation() {
  return process.env.npm_execpath
    ? {executable: process.execPath, arguments: [process.env.npm_execpath]}
    : {executable: 'npm', arguments: []};
}

function run(executable, arguments_, {cwd, env = {}, input, expectedExitCode = 0} = {}) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(executable, arguments_, {
      cwd,
      env: {...process.env, ...env},
      shell: false,
      stdio: ['pipe', 'pipe', 'pipe'],
      windowsHide: true,
    });
    const stdout = [];
    const stderr = [];
    let settled = false;
    const finish = (callback, value) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      callback(value);
    };
    const timer = setTimeout(() => {
      child.kill('SIGKILL');
      finish(reject, new Error(`${executable} exceeded the 180 second adoption bound.`));
    }, 180_000);
    child.stdout.on('data', (chunk) => stdout.push(chunk));
    child.stderr.on('data', (chunk) => stderr.push(chunk));
    child.once('error', (error) => finish(reject, error));
    child.once('close', (exitCode) => {
      const result = {
        exitCode,
        stdout: Buffer.concat(stdout).toString('utf8'),
        stderr: Buffer.concat(stderr).toString('utf8'),
      };
      if (exitCode !== expectedExitCode) {
        finish(reject, new Error(
          `${executable} exited ${exitCode}; stdout=${result.stdout.slice(0, 4096)}; ` +
          `stderr=${result.stderr.slice(0, 4096)}`,
        ));
      } else {
        finish(resolvePromise, result);
      }
    });
    child.stdin.end(input);
  });
}

function parseJsonOutput(result, label) {
  try {
    return JSON.parse(result.stdout.trim());
  } catch (error) {
    throw new Error(`${label} did not return one JSON result: ${error.message}`);
  }
}

function adoptionEnvironment(root, npmCache) {
  const userCache = resolve(root, 'durable user cache');
  return {
    HOME: resolve(root, 'home'),
    USERPROFILE: resolve(root, 'home'),
    XDG_CACHE_HOME: userCache,
    LOCALAPPDATA: resolve(userCache, 'local app data'),
    npm_config_audit: 'false',
    npm_config_cache: npmCache,
    npm_config_fund: 'false',
    npm_config_ignore_scripts: 'true',
    npm_config_update_notifier: 'false',
  };
}

async function runNpxAgent(archivePath, projectRoot, npmCache, arguments_) {
  const npm = npmInvocation();
  const packageSpec = `file:${archivePath}`;
  return run(npm.executable, [
    ...npm.arguments,
    'exec',
    '--yes',
    '--',
    packageSpec,
    ...arguments_,
  ], {
    cwd: projectRoot,
    env: adoptionEnvironment(resolve(projectRoot, '..'), npmCache),
  });
}

async function runDurableAgent(durableRoot, projectRoot, environment, arguments_, expectedExitCode = 0) {
  return run(process.execPath, [
    resolve(durableRoot, 'scripts/agent-client-integration.mjs'),
    ...arguments_,
  ], {cwd: projectRoot, env: environment, expectedExitCode});
}

async function verifyMcpHandshake(durableRoot, projectRoot, frameworkProfile, environment) {
  const mcpPath = resolve(durableRoot, 'scripts/mcp-server.mjs');
  const messages = [
    {
      jsonrpc: '2.0',
      id: 1,
      method: 'initialize',
      params: {
        protocolVersion: '2025-11-25',
        capabilities: {},
        clientInfo: {name: 'viewcompose-bootstrap-adoption', version: '1.0.0'},
      },
    },
    {jsonrpc: '2.0', method: 'notifications/initialized'},
    {jsonrpc: '2.0', id: 2, method: 'tools/list', params: {}},
  ];
  const result = await run(process.execPath, [mcpPath], {
    cwd: projectRoot,
    env: {
      ...environment,
      VIEWCOMPOSE_PROJECT_ROOT: projectRoot,
      VIEWCOMPOSE_FRAMEWORK_PROFILE: frameworkProfile,
    },
    input: `${messages.map(JSON.stringify).join('\n')}\n`,
  });
  assert.equal(result.stderr, '');
  const responses = result.stdout.trim().split('\n').filter(Boolean).map(JSON.parse);
  assert.equal(responses.find((response) => response.id === 1)?.result?.protocolVersion, '2025-11-25');
  assert.equal(responses.find((response) => response.id === 2)?.result?.tools?.length, 13);
}

async function assertSymlinkRejected(durableRoot, projectRoot, environment, client) {
  const alias = resolve(projectRoot, '..', `${client} logical alias`);
  await symlink(projectRoot, alias, process.platform === 'win32' ? 'junction' : 'dir');
  try {
    const rejected = await runDurableAgent(
      durableRoot,
      projectRoot,
      environment,
      ['init', '--client', client, '--project-root', alias],
      2,
    );
    assert.match(rejected.stderr, /symbolic link|physical absolute path/u);
  } finally {
    await rm(alias, {recursive: true, force: true});
  }
}

async function verifyClient({archivePath, root, client, expectedDurableRoot}) {
  const projectRoot = await realpath(resolve(root, `fresh ${client} project 路径`));
  const npmCache = resolve(root, `ephemeral npx ${client}`);
  const environment = adoptionEnvironment(root, npmCache);
  const first = parseJsonOutput(
    await runNpxAgent(archivePath, projectRoot, npmCache, ['init', '--client', client]),
    `${client} init`,
  );
  assert.equal(first.projectRoot, projectRoot);
  assert.equal(first.client, client);
  assert.equal(first.mode, 'project-bound');
  assert.equal(first.config.status, 'installed');
  assert.equal(first.skills.installed.length, 6);
  assert.equal(first.readiness.tooling.version, expectedPackage.version);
  assert.equal(first.readiness.framework.status, 'compatible');
  assert.ok(['project-bound-ready', 'host-prerequisites-required'].includes(first.readiness.status));
  if (process.env.VIEWCOMPOSE_REQUIRE_READY === 'true') {
    assert.equal(first.readiness.status, 'project-bound-ready');
  }
  assert.ok(first.durableInstallRoot);
  assert.equal(contained(npmCache, first.durableInstallRoot), false);
  if (expectedDurableRoot !== undefined) assert.equal(first.durableInstallRoot, expectedDurableRoot);
  await access(resolve(first.durableInstallRoot, 'scripts/mcp-server.mjs'));

  const second = parseJsonOutput(
    await runNpxAgent(archivePath, projectRoot, npmCache, ['init', '--client', client]),
    `${client} idempotent init`,
  );
  assert.equal(second.config.status, 'unchanged');
  assert.equal(second.skills.installed.length, 0);
  assert.equal(second.durableInstallRoot, first.durableInstallRoot);

  await rm(npmCache, {recursive: true, force: true});
  const doctor = parseJsonOutput(
    await runDurableAgent(first.durableInstallRoot, projectRoot, environment, [
      'doctor', '--client', client,
    ], first.readiness.status.endsWith('-ready') ? 0 : 1),
    `${client} durable doctor`,
  );
  assert.equal(doctor.projectRoot, projectRoot);
  assert.equal(doctor.tooling.version, expectedPackage.version);
  assert.equal(doctor.tooling.packageRoot, first.durableInstallRoot);
  assert.equal(doctor.config.status, 'ready');
  assert.equal(doctor.skills.ready, 6);

  const configText = await readFile(resolve(projectRoot, expectedClients[client].configPath), 'utf8');
  assert.equal(configText.includes('viewcompose'), true);
  assert.equal(configText.includes(npmCache), false);
  await verifyMcpHandshake(
    first.durableInstallRoot,
    projectRoot,
    first.framework.profileId,
    environment,
  );
  await assertSymlinkRejected(first.durableInstallRoot, projectRoot, environment, client);

  const removed = parseJsonOutput(
    await runDurableAgent(first.durableInstallRoot, projectRoot, environment, [
      'uninstall', '--client', client,
    ]),
    `${client} uninstall`,
  );
  assert.equal(removed.config.status, 'removed');
  assert.equal(removed.skills.removed.length, 6);
  const remainingConfig = await readFile(resolve(projectRoot, expectedClients[client].configPath), 'utf8');
  assert.equal(remainingConfig.includes('viewcompose'), false);
  const skillRoot = resolve(projectRoot, expectedClients[client].skillRoot);
  const skillRootMetadata = await lstat(skillRoot).catch((error) => {
    if (error?.code === 'ENOENT') return null;
    throw error;
  });
  assert.equal(skillRootMetadata, null);
  return first.durableInstallRoot;
}

export async function verifyBootstrapAdoption() {
  const temporaryRoot = await realpath(await mkdtemp(resolve(tmpdir(), 'ViewCompose adoption 测试 ')));
  try {
    const packageRoot = resolve(temporaryRoot, 'candidate package');
    const projectsRoot = resolve(temporaryRoot, 'projects with spaces 空间');
    await mkdir(projectsRoot, {recursive: true});
    await mkdir(resolve(projectsRoot, 'home'), {recursive: true});
    for (const client of Object.keys(expectedClients)) {
      await mkdir(resolve(projectsRoot, `fresh ${client} project 路径`), {recursive: true});
    }
    const distribution = await createDistribution({outputRoot: packageRoot});
    assert.equal(distribution.manifest.package.name, expectedPackage.name);
    assert.equal(distribution.manifest.package.version, expectedPackage.version);
    let durableRoot;
    for (const client of Object.keys(expectedClients)) {
      durableRoot = await verifyClient({
        archivePath: distribution.archivePath,
        root: projectsRoot,
        client,
        expectedDurableRoot: durableRoot,
      });
    }
    return {
      platform: process.platform,
      clients: Object.keys(expectedClients).length,
      pathCases: ['spaces', 'non-ascii', 'physical-root', 'symlink-rejection'],
      mcpHandshakes: Object.keys(expectedClients).length,
      durableInstallRoot: durableRoot,
    };
  } finally {
    await rm(temporaryRoot, {recursive: true, force: true});
  }
}

const entryPath = process.argv[1] ? resolve(process.argv[1]) : '';
if (entryPath === fileURLToPath(import.meta.url)) {
  verifyBootstrapAdoption().then((result) => {
    process.stdout.write(
      `Verified ViewCompose ${expectedPackage.version} bootstrap adoption on ${result.platform}: ` +
      `${result.clients}/${result.clients} clients, ${result.mcpHandshakes}/${result.mcpHandshakes} ` +
      `MCP handshakes, and ${result.pathCases.length}/${result.pathCases.length} native path cases.\n`,
    );
  }).catch((error) => {
    process.stderr.write(`ViewCompose bootstrap adoption verification failed: ${error.stack ?? error.message}\n`);
    process.exitCode = 1;
  });
}
