import assert from 'node:assert/strict';
import {mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {
  CURRENT_DOCUMENTATION_TOOLING_PATHS,
  installCurrentDocumentationTooling,
  projectDependencyContractsForPublishingMetadata,
} from '../assemble-versioned-api-docs.mjs';

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
