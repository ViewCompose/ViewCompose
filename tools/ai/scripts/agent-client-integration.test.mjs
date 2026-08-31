import assert from 'node:assert/strict';
import {cp, mkdtemp, mkdir, readFile, realpath, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {
  AGENT_CLIENT_PROFILES,
  commitDurablePackageIntegrity,
  diagnoseAgentClient,
  initializeAgentClient,
  installAgentClientSkills,
  isAbsoluteProjectRoot,
  migrateAgentClient,
  renderAgentClientConfig,
  resolveConsumerProjectRoot,
  uninstallAgentClient,
} from './agent-client-integration.mjs';

const aiRoot = await realpath(new URL('../', import.meta.url));
const sourceRoot = resolve(aiRoot, '../..');
const releasedProfile = '895ed1e52e5a9735f87e6d996e77ea43ca34cc2e496854408c40772419129064';

async function createAiPackage(root, version, skillSuffix) {
  await mkdir(resolve(root, 'contracts'), {recursive: true});
  await mkdir(resolve(root, 'scripts'), {recursive: true});
  await mkdir(resolve(root, 'skills'), {recursive: true});
  await mkdir(resolve(root, 'generated/released'), {recursive: true});
  for (const name of [
    'framework-profile-index.schema.json',
    'framework-compatibility-profile.schema.json',
  ]) {
    await cp(resolve(aiRoot, 'contracts', name), resolve(root, 'contracts', name));
  }
  await cp(
    resolve(aiRoot, 'generated/released/index.json'),
    resolve(root, 'generated/released/index.json'),
  );
  await cp(
    resolve(aiRoot, 'generated/released', releasedProfile),
    resolve(root, 'generated/released', releasedProfile),
    {recursive: true},
  );
  const manifest = JSON.parse(await readFile(resolve(aiRoot, 'skills/manifest.json'), 'utf8'));
  await writeFile(resolve(root, 'skills/manifest.json'), `${JSON.stringify(manifest)}\n`);
  for (const skill of manifest.skills) {
    await mkdir(resolve(root, 'skills', skill.id));
    await writeFile(resolve(root, skill.path), `${skill.id}-${skillSuffix}\n`);
  }
  await writeFile(resolve(root, 'scripts/mcp-server.mjs'), '// test MCP server\n');
  await writeFile(
    resolve(root, 'distribution.json'),
    `${JSON.stringify({package: {name: '@viewcompose/ai-tooling', version}})}\n`,
  );
  return manifest;
}

test('renders deterministic standalone and source-bound configuration for every client', () => {
  const nodeExecutable = '/opt/viewcompose/node';
  const mcpServerPath = '/opt/viewcompose/mcp-server.mjs';
  const projectRoot = '/workspace/app';
  const standaloneCodex = renderAgentClientConfig('codex', projectRoot, {
    frameworkProfile: releasedProfile,
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
    `VIEWCOMPOSE_FRAMEWORK_PROFILE = "${releasedProfile}"`,
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
    'VIEWCOMPOSE_FRAMEWORK_PROFILE = "current-source"',
    `VIEWCOMPOSE_SOURCE_ROOT = "${sourceRoot}"`,
    '',
  ].join('\n'));

  for (const client of ['claude-code', 'cursor']) {
    const standalone = JSON.parse(renderAgentClientConfig(client, projectRoot, {
      frameworkProfile: releasedProfile,
      nodeExecutable,
      mcpServerPath,
    }));
    assert.deepEqual(standalone.mcpServers.viewcompose, {
      command: nodeExecutable,
      args: [mcpServerPath],
      env: {
        VIEWCOMPOSE_PROJECT_ROOT: projectRoot,
        VIEWCOMPOSE_FRAMEWORK_PROFILE: releasedProfile,
      },
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
            VIEWCOMPOSE_FRAMEWORK_PROFILE: 'current-source',
            VIEWCOMPOSE_SOURCE_ROOT: sourceRoot,
          },
        },
      },
    });
  }
  assert.throws(() => renderAgentClientConfig('unknown', projectRoot), /Unknown client/u);
});

test('resolves an omitted project root from the physical current directory', async () => {
  const temporary = await realpath(await mkdtemp(resolve(tmpdir(), 'viewcompose-agent-root-')));
  const linked = `${temporary}-link`;
  await mkdir(resolve(temporary, 'project'));
  await symlink(resolve(temporary, 'project'), linked, 'dir');
  const previous = process.cwd();
  try {
    process.chdir(resolve(temporary, 'project'));
    assert.equal(await resolveConsumerProjectRoot(), resolve(temporary, 'project'));
    await assert.rejects(resolveConsumerProjectRoot(linked), /must not be a symbolic link/u);
  } finally {
    process.chdir(previous);
    await rm(linked, {force: true});
    await rm(temporary, {recursive: true, force: true});
  }
});

test('recognizes POSIX, Windows drive, and Windows UNC absolute project-root syntax', () => {
  assert.equal(isAbsoluteProjectRoot('/workspace/app', 'linux'), true);
  assert.equal(isAbsoluteProjectRoot('workspace/app', 'linux'), false);
  assert.equal(isAbsoluteProjectRoot('C:\\workspace\\app', 'win32'), true);
  assert.equal(isAbsoluteProjectRoot('\\\\server\\share\\app', 'win32'), true);
  assert.equal(isAbsoluteProjectRoot('C:workspace\\app', 'win32'), false);
});

test('binds MCP to a durable content-addressed package that survives source removal', async () => {
  const temporary = await realpath(await mkdtemp(resolve(tmpdir(), 'viewcompose-agent-cache-')));
  const packageRoot = resolve(temporary, 'package');
  const projectRoot = resolve(temporary, 'project');
  const cacheRoot = resolve(temporary, 'cache');
  try {
    await createAiPackage(packageRoot, '0.4.0', 'cached');
    await mkdir(projectRoot);
    const initialized = await initializeAgentClient({
      client: 'cursor', projectRoot, aiRoot: packageRoot, cacheRoot,
    });
    assert.ok(initialized.durableInstallRoot?.startsWith(cacheRoot));
    const config = await readFile(resolve(projectRoot, '.cursor/mcp.json'), 'utf8');
    assert.match(config, new RegExp(initialized.durableInstallRoot.replaceAll('/', '\\/'), 'u'));
    await rm(packageRoot, {recursive: true, force: true});
    const diagnosed = await diagnoseAgentClient({client: 'cursor', projectRoot});
    assert.equal(diagnosed.tooling.version, '0.4.0');
    assert.equal(diagnosed.tooling.packageRoot, initialized.durableInstallRoot);
  } finally {
    await rm(temporary, {recursive: true, force: true});
  }
});

test('rejects a tampered durable package on re-entry and reports repair-required diagnosis', async () => {
  const temporary = await realpath(await mkdtemp(resolve(tmpdir(), 'viewcompose-agent-tamper-')));
  const packageRoot = resolve(temporary, 'package');
  const projectRoot = resolve(temporary, 'project');
  const cacheRoot = resolve(temporary, 'cache');
  try {
    await createAiPackage(packageRoot, '0.4.0', 'integrity');
    await mkdir(projectRoot);
    const initialized = await initializeAgentClient({
      client: 'codex', projectRoot, aiRoot: packageRoot, cacheRoot,
    });
    await writeFile(resolve(initialized.durableInstallRoot, 'scripts/mcp-server.mjs'), '// tampered\n');
    await assert.rejects(
      initializeAgentClient({client: 'codex', projectRoot, aiRoot: packageRoot, cacheRoot}),
      /integrity check/u,
    );
    const diagnosed = await diagnoseAgentClient({client: 'codex', projectRoot});
    assert.equal(diagnosed.status, 'repair-required');
    assert.match(diagnosed.config.detail, /integrity check/u);
  } finally {
    await rm(temporary, {recursive: true, force: true});
  }
});

test('requires valid durable integrity for 0.4.0 and every later stable version', async () => {
  const temporary = await realpath(await mkdtemp(resolve(tmpdir(), 'viewcompose-agent-version-integrity-')));
  const cases = [
    {version: '0.4.0', mutation: 'missing-marker'},
    {version: '0.4.1', mutation: 'malformed-marker'},
    {version: '1.0.0', mutation: 'tampered-package'},
  ];
  try {
    for (const {version, mutation} of cases) {
      const suffix = version.replaceAll('.', '-');
      const packageRoot = resolve(temporary, `package-${suffix}`);
      const projectRoot = resolve(temporary, `project-${suffix}`);
      const cacheRoot = resolve(temporary, `cache-${suffix}`);
      await createAiPackage(packageRoot, version, suffix);
      await mkdir(projectRoot);
      const initialized = await initializeAgentClient({
        client: 'cursor', projectRoot, aiRoot: packageRoot, cacheRoot,
      });
      const marker = resolve(
        initialized.durableInstallRoot,
        '.viewcompose-ai-bootstrap-v1.json',
      );
      if (mutation === 'missing-marker') await rm(marker);
      if (mutation === 'malformed-marker') await writeFile(marker, '{invalid json\n');
      if (mutation === 'tampered-package') {
        await writeFile(resolve(initialized.durableInstallRoot, 'scripts/mcp-server.mjs'), '// tampered\n');
      }
      const diagnosed = await diagnoseAgentClient({client: 'cursor', projectRoot});
      assert.equal(diagnosed.status, 'repair-required');
      assert.match(diagnosed.config.detail, /durable-cache integrity|could not be verified|integrity check/u);
    }
  } finally {
    await rm(temporary, {recursive: true, force: true});
  }
});

test('rejects prerelease, build-metadata, and malformed active package versions', async () => {
  const temporary = await realpath(await mkdtemp(resolve(tmpdir(), 'viewcompose-agent-invalid-version-')));
  const invalidVersions = [
    '0.4.1-beta.1',
    '1.0.0-rc.1',
    '1.0.0+build.1',
    '00.4.0',
    '0.04.0',
    '0.4.00',
    'not-a-version',
  ];
  try {
    for (const [index, invalidVersion] of invalidVersions.entries()) {
      const packageRoot = resolve(temporary, `package-${index}`);
      const projectRoot = resolve(temporary, `project-${index}`);
      const cacheRoot = resolve(temporary, `cache-${index}`);
      await createAiPackage(packageRoot, '0.4.1', `invalid-${index}`);
      await mkdir(projectRoot);
      const initialized = await initializeAgentClient({
        client: 'cursor', projectRoot, aiRoot: packageRoot, cacheRoot,
      });
      const distributionPath = resolve(initialized.durableInstallRoot, 'distribution.json');
      await writeFile(
        distributionPath,
        `${JSON.stringify({package: {
          name: '@viewcompose/ai-tooling',
          version: invalidVersion,
        }})}\n`,
      );
      const diagnosed = await diagnoseAgentClient({client: 'cursor', projectRoot});
      assert.equal(diagnosed.status, 'repair-required');
      assert.match(diagnosed.config.detail, /stable released/u);
    }
  } finally {
    await rm(temporary, {recursive: true, force: true});
  }
});

test('concurrent durable package materialization converges on one verified cache entry', async () => {
  const temporary = await realpath(await mkdtemp(resolve(tmpdir(), 'viewcompose-agent-concurrent-cache-')));
  const packageRoot = resolve(temporary, 'package');
  const cacheRoot = resolve(temporary, 'cache');
  const projectRoots = [resolve(temporary, 'project-one'), resolve(temporary, 'project-two')];
  try {
    await createAiPackage(packageRoot, '0.4.1', 'concurrent');
    await Promise.all(projectRoots.map((projectRoot) => mkdir(projectRoot)));
    const initialized = await Promise.all(projectRoots.map((projectRoot) => initializeAgentClient({
      client: 'codex', projectRoot, aiRoot: packageRoot, cacheRoot,
    })));
    assert.equal(initialized[0].durableInstallRoot, initialized[1].durableInstallRoot);
    for (const projectRoot of projectRoots) {
      const diagnosed = await diagnoseAgentClient({client: 'codex', projectRoot});
      assert.equal(diagnosed.status, 'project-bound-ready');
      assert.equal(diagnosed.tooling.version, '0.4.1');
    }
  } finally {
    await rm(temporary, {recursive: true, force: true});
  }
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
    const incompatibleRoot = resolve(temporary, 'incompatible-framework');
    await mkdir(incompatibleRoot);
    await writeFile(
      resolve(incompatibleRoot, 'build.gradle.kts'),
      'dependencies { implementation("com.viewcompose:viewcompose-ui-foundation:0.0.0-unsupported") }\n',
    );
    await assert.rejects(
      initializeAgentClient({client: 'codex', projectRoot: incompatibleRoot, aiRoot}),
      /No released framework profile matches/u,
    );
    await assert.rejects(readFile(resolve(incompatibleRoot, '.codex/config.toml')), /ENOENT/u);
    await assert.rejects(readFile(resolve(incompatibleRoot, '.agents/skills')), /ENOENT|EISDIR/u);
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

test('migrates config and exact Skill bytes as one recoverable version-bound transaction', async () => {
  const temporary = await realpath(await mkdtemp(resolve(tmpdir(), 'viewcompose-agent-upgrade-')));
  try {
    const oldAiRoot = resolve(temporary, 'old-package');
    const newAiRoot = resolve(temporary, 'new-package');
    const manifest = await createAiPackage(oldAiRoot, '0.3.0', 'old');
    await createAiPackage(newAiRoot, '0.4.0', 'new');
    await commitDurablePackageIntegrity({aiRoot: newAiRoot, frameworkProfile: releasedProfile});
    const dependency = JSON.parse(await readFile(
      resolve(oldAiRoot, 'generated/released', releasedProfile, 'profile.json'),
      'utf8',
    )).artifacts.find((artifact) => artifact.knowledgeIncluded);
    for (const client of Object.keys(AGENT_CLIENT_PROFILES)) {
      const profile = AGENT_CLIENT_PROFILES[client];
      const projectRoot = resolve(temporary, `project-${client}`);
      await mkdir(projectRoot);
      await writeFile(
        resolve(projectRoot, 'build.gradle.kts'),
        `dependencies { implementation("${dependency.coordinate}:${dependency.version}") }\n`,
      );
      const oldMcp = resolve(oldAiRoot, 'scripts/mcp-server.mjs');
      await initializeAgentClient({
        client,
        projectRoot,
        aiRoot: oldAiRoot,
        mcpServerPath: oldMcp,
        nodeExecutable: process.execPath,
        cacheRoot: resolve(temporary, 'cache'),
      });
      if (client === 'codex') {
        const initialConfig = await readFile(resolve(projectRoot, profile.configPath), 'utf8');
        await assert.rejects(migrateAgentClient({
          client,
          projectRoot,
          newAiRoot,
          frameworkProfile: releasedProfile,
          afterWrite: () => {
            throw new Error('injected interruption');
          },
        }), /injected interruption/u);
        const rolledBack = await readFile(resolve(projectRoot, profile.configPath), 'utf8');
        assert.equal(rolledBack, initialConfig);
        await assert.rejects(readFile(resolve(projectRoot, '.viewcompose/ai-upgrade-v1.json')), /ENOENT/u);
        const edited = rolledBack.replace(
          '# ViewCompose AI managed configuration — end',
          '# user-managed addition\n# ViewCompose AI managed configuration — end',
        );
        await writeFile(resolve(projectRoot, profile.configPath), edited);
        await assert.rejects(migrateAgentClient({
          client,
          projectRoot,
          newAiRoot,
          frameworkProfile: releasedProfile,
        }), /user-edited managed MCP configuration/u);
        await writeFile(resolve(projectRoot, profile.configPath), rolledBack);
      }
      const migrated = await migrateAgentClient({
        client,
        projectRoot,
        newAiRoot,
        frameworkProfile: releasedProfile,
      });
      assert.equal(migrated.previousVersion, '0.3.0');
      assert.equal(migrated.installedVersion, '0.4.0');
      const config = await readFile(resolve(projectRoot, profile.configPath), 'utf8');
      assert.match(config, new RegExp(resolve(newAiRoot, 'scripts/mcp-server.mjs').replaceAll('/', '\\/'), 'u'));
      for (const skill of manifest.skills) {
        assert.equal(
          await readFile(resolve(projectRoot, profile.skillRoot, skill.id, 'SKILL.md'), 'utf8'),
          `${skill.id}-new\n`,
        );
      }
      const doctor = await diagnoseAgentClient({
        client,
        projectRoot,
        aiRoot: oldAiRoot,
        mcpServerPath: oldMcp,
      });
      assert.equal(doctor.status, 'project-bound-ready');
      assert.equal(doctor.tooling.version, '0.4.0');
      assert.equal(doctor.framework.profileId, releasedProfile);
      const removed = await uninstallAgentClient({
        client,
        projectRoot,
        aiRoot: oldAiRoot,
        mcpServerPath: oldMcp,
      });
      assert.equal(removed.config.status, 'removed');
      assert.equal(removed.skills.removed.length, manifest.skills.length);
    }
  } finally {
    await rm(temporary, {recursive: true, force: true});
  }
});
