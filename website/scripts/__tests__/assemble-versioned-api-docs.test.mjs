import assert from 'node:assert/strict';
import {mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {
  CURRENT_DOCUMENTATION_TOOLING_PATHS,
  ensureRevisionAvailable,
  installCurrentDocumentationTooling,
  projectDependencyContractsForPublishingMetadata,
} from '../assemble-versioned-api-docs.mjs';

const frozenRevision = '1234567890abcdef1234567890abcdef12345678';

test('frozen revisions reject movable Git references before running a command', async () => {
  let commandCount = 0;
  await assert.rejects(
    ensureRevisionAvailable('release/source', {
      captureCommand: async () => {
        commandCount += 1;
      },
    }),
    /must be a full lowercase Git SHA/u,
  );
  assert.equal(commandCount, 0);
});

test('available frozen revisions do not fetch from the remote', async () => {
  const commands = [];
  const fetched = await ensureRevisionAvailable(frozenRevision, {
    root: '/test/repository',
    captureCommand: async (command, args, options) => {
      commands.push({command, args, options});
      return '';
    },
  });

  assert.equal(fetched, false);
  assert.deepEqual(commands, [
    {
      command: 'git',
      args: ['cat-file', '-e', `${frozenRevision}^{commit}`],
      options: {cwd: '/test/repository'},
    },
  ]);
});

test('missing frozen revisions fetch the exact full SHA once and verify the result', async () => {
  const commands = [];
  let available = false;
  const fetched = await ensureRevisionAvailable(frozenRevision, {
    root: '/test/repository',
    captureCommand: async (command, args, options) => {
      commands.push({command, args, options});
      if (args[0] === 'cat-file' && !available) throw new Error('missing commit');
      if (args[0] === 'fetch') available = true;
      return '';
    },
  });

  assert.equal(fetched, true);
  assert.deepEqual(commands, [
    {
      command: 'git',
      args: ['cat-file', '-e', `${frozenRevision}^{commit}`],
      options: {cwd: '/test/repository'},
    },
    {
      command: 'git',
      args: ['fetch', '--no-tags', '--depth=1', 'origin', frozenRevision],
      options: {cwd: '/test/repository'},
    },
    {
      command: 'git',
      args: ['cat-file', '-e', `${frozenRevision}^{commit}`],
      options: {cwd: '/test/repository'},
    },
  ]);
});

test('current documentation tooling replaces the complete publishing main source tree', async () => {
  const root = await mkdtemp(resolve(tmpdir(), 'viewcompose-documentation-tooling-test-'));
  const sourceRoot = resolve(root, 'source');
  const workspace = resolve(root, 'workspace');
  const sourceDirectory = resolve(sourceRoot, 'tool/src/main');
  const destinationDirectory = resolve(workspace, 'tool/src/main');
  try {
    await mkdir(resolve(sourceDirectory, 'nested'), {recursive: true});
    await mkdir(destinationDirectory, {recursive: true});
    await writeFile(resolve(sourceDirectory, 'Entry.kt'), 'entry', 'utf8');
    await writeFile(resolve(sourceDirectory, 'nested/Support.kt'), 'support', 'utf8');
    await writeFile(resolve(destinationDirectory, 'HistoricalOnly.kt'), 'stale', 'utf8');

    await installCurrentDocumentationTooling(workspace, sourceRoot, [
      {relativePath: 'tool/src/main', replaceDirectory: true},
    ]);

    assert.equal(await readFile(resolve(destinationDirectory, 'Entry.kt'), 'utf8'), 'entry');
    assert.equal(
      await readFile(resolve(destinationDirectory, 'nested/Support.kt'), 'utf8'),
      'support',
    );
    await assert.rejects(readFile(resolve(destinationDirectory, 'HistoricalOnly.kt'), 'utf8'));
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('versioned API generation overlays the publishing build as one current toolchain', () => {
  const paths = CURRENT_DOCUMENTATION_TOOLING_PATHS.map(({relativePath}) => relativePath);
  assert.ok(paths.includes('tools/viewcompose-publishing-build/build.gradle.kts'));
  assert.ok(paths.includes('tools/viewcompose-publishing-build/settings.gradle.kts'));
  assert.ok(paths.includes('tools/viewcompose-publishing-build/src/main'));
  assert.ok(paths.includes('gradle/viewcompose-dependency-contracts.properties'));
  assert.equal(
    paths.some((path) => path.endsWith('/ViewComposePublishingPlugin.kt')),
    false,
  );
});

test('historical API workspaces receive only contracts for their registered artifacts', () => {
  const projected = projectDependencyContractsForPublishingMetadata(
    [
      'schema.version=1',
      'module.viewcompose-runtime=api=;implementation=;compileOnly=;runtimeOnly=',
      'module.viewcompose-host-android=api=viewcompose-runtime,viewcompose-image-glide;implementation=;compileOnly=;runtimeOnly=',
      'module.viewcompose-image-glide=api=viewcompose-runtime;implementation=;compileOnly=;runtimeOnly=',
      '',
    ].join('\n'),
    [
      'module.viewcompose-runtime.version=0.1.0-alpha01',
      'module.viewcompose-host-android.version=0.1.0-alpha01',
      '',
    ].join('\n'),
  );

  assert.match(
    projected,
    /^module\.viewcompose-host-android=api=viewcompose-runtime;implementation=;compileOnly=;runtimeOnly=$/mu,
  );
  assert.doesNotMatch(projected, /module\.viewcompose-image-glide=/u);
  assert.doesNotMatch(projected, /api=.*viewcompose-image-glide/u);
});

test('historical API workspaces recover retired contracts from their source revision', () => {
  const projected = projectDependencyContractsForPublishingMetadata(
    [
      'schema.version=1',
      'module.viewcompose-ui-foundation=api=viewcompose-runtime;implementation=;compileOnly=;runtimeOnly=',
      '',
    ].join('\n'),
    [
      'module.viewcompose-runtime.version=0.1.0-alpha01',
      'module.viewcompose-widget-core.version=0.1.0-alpha01',
      '',
    ].join('\n'),
    [
      'schema.version=1',
      'module.viewcompose-runtime=api=;implementation=;compileOnly=;runtimeOnly=',
      'module.viewcompose-widget-core=api=viewcompose-runtime;implementation=;compileOnly=;runtimeOnly=',
      '',
    ].join('\n'),
  );

  assert.match(
    projected,
    /^module\.viewcompose-widget-core=api=viewcompose-runtime;implementation=;compileOnly=;runtimeOnly=$/mu,
  );
  assert.doesNotMatch(projected, /module\.viewcompose-ui-foundation=/u);
});

test('historical API workspaces synthesize documentation-only contracts before the registry existed', () => {
  const projected = projectDependencyContractsForPublishingMetadata(
    'schema.version=1\n',
    [
      'module.viewcompose-runtime.version=0.1.0-alpha01',
      'module.viewcompose-widget-core.version=0.1.0-alpha01',
      '',
    ].join('\n'),
    '',
  );

  assert.match(
    projected,
    /^module\.viewcompose-widget-core=api=;implementation=;compileOnly=;runtimeOnly=$/mu,
  );
});
