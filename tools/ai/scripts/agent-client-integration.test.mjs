import assert from 'node:assert/strict';
import {mkdtemp, mkdir, readFile, realpath, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {
  AGENT_CLIENT_PROFILES,
  installAgentClientSkills,
  renderAgentClientConfig,
} from './agent-client-integration.mjs';

const aiRoot = await realpath(new URL('../', import.meta.url));
const sourceRoot = resolve(aiRoot, '../..');

test('renders deterministic project-scoped configuration for all supported clients', () => {
  const nodeExecutable = '/opt/viewcompose/node';
  const mcpServerPath = '/opt/viewcompose/mcp-server.mjs';
  const codex = renderAgentClientConfig('codex', sourceRoot, {nodeExecutable, mcpServerPath});
  assert.equal(codex, [
    '[mcp_servers.viewcompose]',
    `command = "${nodeExecutable}"`,
    `args = ["${mcpServerPath}"]`,
    '',
    '[mcp_servers.viewcompose.env]',
    `VIEWCOMPOSE_SOURCE_ROOT = "${sourceRoot}"`,
    '',
  ].join('\n'));

  for (const client of ['claude-code', 'cursor']) {
    const config = JSON.parse(renderAgentClientConfig(client, sourceRoot, {
      nodeExecutable,
      mcpServerPath,
    }));
    assert.deepEqual(config, {
      mcpServers: {
        viewcompose: {
          command: nodeExecutable,
          args: [mcpServerPath],
          env: {VIEWCOMPOSE_SOURCE_ROOT: sourceRoot},
        },
      },
    });
  }
  assert.throws(() => renderAgentClientConfig('unknown', sourceRoot), /Unknown client/u);
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
