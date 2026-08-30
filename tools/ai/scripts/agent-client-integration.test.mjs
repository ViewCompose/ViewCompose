import assert from 'node:assert/strict';
import {mkdtemp, mkdir, readFile, realpath, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {
  AGENT_CLIENT_PROFILES,
  diagnoseAgentClient,
  initializeAgentClient,
  installAgentClientSkills,
  renderAgentClientConfig,
  uninstallAgentClient,
} from './agent-client-integration.mjs';

const aiRoot = await realpath(new URL('../', import.meta.url));
const sourceRoot = resolve(aiRoot, '../..');

test('renders deterministic standalone and source-bound configuration for every client', () => {
  const nodeExecutable = '/opt/viewcompose/node';
  const mcpServerPath = '/opt/viewcompose/mcp-server.mjs';
  const projectRoot = '/workspace/app';
  const standaloneCodex = renderAgentClientConfig('codex', projectRoot, {
    nodeExecutable,
    mcpServerPath,
  });
  assert.equal(standaloneCodex, [
    '[mcp_servers.viewcompose]',
    `command = "${nodeExecutable}"`,
    `args = ["${mcpServerPath}"]`,
    '',
    '[mcp_servers.viewcompose.env]',
    `VIEWCOMPOSE_PROJECT_ROOT = "${projectRoot}"`,
    '',
  ].join('\n'));
  const codex = renderAgentClientConfig('codex', projectRoot, {
    sourceRoot,
    nodeExecutable,
    mcpServerPath,
  });
  assert.equal(codex, [
    '[mcp_servers.viewcompose]',
    `command = "${nodeExecutable}"`,
    `args = ["${mcpServerPath}"]`,
    '',
    '[mcp_servers.viewcompose.env]',
    `VIEWCOMPOSE_PROJECT_ROOT = "${projectRoot}"`,
    `VIEWCOMPOSE_SOURCE_ROOT = "${sourceRoot}"`,
    '',
  ].join('\n'));

  for (const client of ['claude-code', 'cursor']) {
    const standalone = JSON.parse(renderAgentClientConfig(client, projectRoot, {
      nodeExecutable,
      mcpServerPath,
    }));
    assert.deepEqual(standalone.mcpServers.viewcompose, {
      command: nodeExecutable,
      args: [mcpServerPath],
      env: {VIEWCOMPOSE_PROJECT_ROOT: projectRoot},
    });
    const config = JSON.parse(renderAgentClientConfig(client, projectRoot, {
      sourceRoot,
      nodeExecutable,
      mcpServerPath,
    }));
    assert.deepEqual(config, {
      mcpServers: {
        viewcompose: {
          command: nodeExecutable,
          args: [mcpServerPath],
          env: {
            VIEWCOMPOSE_PROJECT_ROOT: projectRoot,
            VIEWCOMPOSE_SOURCE_ROOT: sourceRoot,
          },
        },
      },
    });
  }
  assert.throws(() => renderAgentClientConfig('unknown', projectRoot), /Unknown client/u);
});

test('initializes, diagnoses, and uninstalls standalone integrations transactionally', async () => {
  const temporary = await realpath(await mkdtemp(resolve(tmpdir(), 'viewcompose-agent-lifecycle-')));
  const nodeExecutable = '/opt/viewcompose/node';
  const mcpServerPath = '/opt/viewcompose/mcp-server.mjs';
  try {
    for (const client of Object.keys(AGENT_CLIENT_PROFILES)) {
      const profile = AGENT_CLIENT_PROFILES[client];
      const projectRoot = resolve(temporary, client);
      await mkdir(projectRoot);
      const configPath = resolve(projectRoot, profile.configPath);
      await mkdir(resolve(configPath, '..'), {recursive: true});
      if (profile.configFormat === 'json') {
        await writeFile(configPath, `${JSON.stringify({
          unrelated: {preserved: true},
          mcpServers: {other: {command: '/opt/other'}},
        }, null, 2)}\n`);
      } else {
        await writeFile(configPath, '[features]\nexperimental = true\n');
      }

      const first = await initializeAgentClient({
        client,
        projectRoot,
        aiRoot,
        nodeExecutable,
        mcpServerPath,
      });
      assert.equal(first.mode, 'project-bound');
      assert.equal(first.config.status, 'installed');
      assert.ok(first.skills.installed.length > 0);

      const doctor = await diagnoseAgentClient({
        client,
        projectRoot,
        aiRoot,
        nodeExecutable,
        mcpServerPath,
      });
      assert.equal(doctor.status, 'project-bound-ready');
      assert.equal(doctor.capabilities.knowledgeAndGeneration, 'ready');
      assert.equal(doctor.capabilities.compilationPreviewAndLayout, 'project-bound-ready');
      assert.equal(doctor.host.status, 'ready');
      if (client === 'codex') {
        const missingHost = await diagnoseAgentClient({
          client,
          projectRoot,
          aiRoot,
          nodeExecutable,
          mcpServerPath,
          detectJava: () => null,
          detectSdk: () => null,
        });
        assert.equal(missingHost.status, 'host-prerequisites-required');
        assert.equal(missingHost.capabilities.knowledgeAndGeneration, 'ready');
        assert.equal(
          missingHost.capabilities.compilationPreviewAndLayout,
          'host-prerequisites-required',
        );
      }

      const second = await initializeAgentClient({
        client,
        projectRoot,
        aiRoot,
        nodeExecutable,
        mcpServerPath,
      });
      assert.equal(second.config.status, 'unchanged');
      assert.equal(second.skills.installed.length, 0);

      const removed = await uninstallAgentClient({
        client,
        projectRoot,
        aiRoot,
        nodeExecutable,
        mcpServerPath,
      });
      assert.equal(removed.config.status, 'removed');
      assert.ok(removed.skills.removed.length > 0);
      const remaining = await readFile(configPath, 'utf8');
      if (profile.configFormat === 'json') {
        const parsed = JSON.parse(remaining);
        assert.deepEqual(parsed.unrelated, {preserved: true});
        assert.deepEqual(parsed.mcpServers.other, {command: '/opt/other'});
        assert.equal(parsed.mcpServers.viewcompose, undefined);
      } else {
        assert.equal(remaining, '[features]\nexperimental = true\n');
      }

      const after = await diagnoseAgentClient({
        client,
        projectRoot,
        aiRoot,
        nodeExecutable,
        mcpServerPath,
      });
      assert.equal(after.status, 'repair-required');
      assert.equal(after.config.status, 'missing');
      assert.equal(after.skills.status, 'missing');
    }
  } finally {
    await rm(temporary, {recursive: true, force: true});
  }
});

test('installs exact canonical Skill bytes idempotently for every client layout', async () => {
  const temporary = await realpath(await mkdtemp(resolve(tmpdir(), 'viewcompose-agent-skills-')));
  try {
    const manifest = JSON.parse(await readFile(resolve(aiRoot, 'skills/manifest.json'), 'utf8'));
    for (const client of Object.keys(AGENT_CLIENT_PROFILES)) {
      const projectRoot = resolve(temporary, client);
      await mkdir(projectRoot);
      const first = await installAgentClientSkills({client, projectRoot, aiRoot});
      assert.deepEqual(first.installed, manifest.skills.map((skill) => skill.id));
      assert.deepEqual(first.unchanged, []);
      for (const skill of manifest.skills) {
        const installed = await readFile(resolve(projectRoot, first.skillRoot, skill.id, 'SKILL.md'));
        const canonical = await readFile(resolve(aiRoot, skill.path));
        assert.equal(installed.equals(canonical), true);
      }
      const second = await installAgentClientSkills({client, projectRoot, aiRoot});
      assert.deepEqual(second.installed, []);
      assert.deepEqual(second.unchanged, manifest.skills.map((skill) => skill.id));
    }
  } finally {
    await rm(temporary, {recursive: true, force: true});
  }
});

test('rejects conflicts, relative roots, symbolic-link roots, and unknown clients', async () => {
  const temporary = await realpath(await mkdtemp(resolve(tmpdir(), 'viewcompose-agent-safety-')));
  try {
    const projectRoot = resolve(temporary, 'project');
    await mkdir(projectRoot);
    const result = await installAgentClientSkills({client: 'codex', projectRoot, aiRoot});
    await writeFile(
      resolve(projectRoot, result.skillRoot, result.installed[0], 'SKILL.md'),
      'conflicting bytes\n',
    );
    await assert.rejects(
      installAgentClientSkills({client: 'codex', projectRoot, aiRoot}),
      /Refusing to overwrite conflicting Skill bytes/u,
    );
    await assert.rejects(
      initializeAgentClient({client: 'codex', projectRoot, aiRoot}),
      /Refusing to overwrite conflicting Skill bytes/u,
    );
    await assert.rejects(readFile(resolve(projectRoot, '.codex/config.toml')), /ENOENT/u);

    const configConflictRoot = resolve(temporary, 'config-conflict');
    await mkdir(resolve(configConflictRoot, '.cursor'), {recursive: true});
    await writeFile(
      resolve(configConflictRoot, '.cursor/mcp.json'),
      `${JSON.stringify({mcpServers: {viewcompose: {command: '/conflict'}}})}\n`,
    );
    await assert.rejects(
      initializeAgentClient({client: 'cursor', projectRoot: configConflictRoot, aiRoot}),
      /Refusing to overwrite conflicting MCP configuration/u,
    );
    await assert.rejects(readFile(resolve(configConflictRoot, '.agents/skills')), /ENOENT|EISDIR/u);
    await assert.rejects(
      installAgentClientSkills({client: 'codex', projectRoot: 'relative', aiRoot}),
      /absolute path/u,
    );
    const physicalRoot = resolve(temporary, 'physical');
    const linkedRoot = resolve(temporary, 'linked');
    await mkdir(physicalRoot);
    await symlink(physicalRoot, linkedRoot, 'dir');
    await assert.rejects(
      installAgentClientSkills({client: 'cursor', projectRoot: linkedRoot, aiRoot}),
      /symbolic link/u,
    );
    await assert.rejects(
      installAgentClientSkills({client: 'other', projectRoot, aiRoot}),
      /Unknown client/u,
    );
  } finally {
    await rm(temporary, {recursive: true, force: true});
  }
});
