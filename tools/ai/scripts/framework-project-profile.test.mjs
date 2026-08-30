import assert from 'node:assert/strict';
import {mkdtemp, mkdir, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {detectFrameworkProjectProfile} from './framework-project-profile.mjs';

async function project(files) {
  const root = await mkdtemp(resolve(tmpdir(), 'viewcompose-framework-profile-'));
  for (const [path, content] of Object.entries(files)) {
    const target = resolve(root, path);
    await mkdir(resolve(target, '..'), {recursive: true});
    await writeFile(target, content);
  }
  return root;
}

test('detects exact independently versioned literal ViewCompose dependencies', async () => {
  const root = await project({
    'app/build.gradle.kts': `dependencies {
      implementation("com.viewcompose:viewcompose-ui-foundation:0.1.0-alpha02")
      implementation("com.viewcompose:viewcompose-material3-android:0.1.0-alpha02")
    }`,
  });
  try {
    const result = await detectFrameworkProjectProfile({projectRoot: root});
    assert.equal(result.status, 'resolved');
    assert.deepEqual(result.artifacts.map(({coordinate, version}) => [coordinate, version]), [
      ['com.viewcompose:viewcompose-material3-android', '0.1.0-alpha02'],
      ['com.viewcompose:viewcompose-ui-foundation', '0.1.0-alpha02'],
    ]);
    assert.match(result.profileFingerprint, /^[a-f0-9]{64}$/u);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('resolves used standard version-catalog aliases and ignores unused entries', async () => {
  const root = await project({
    'gradle/libs.versions.toml': `[versions]
viewcompose = "0.1.0-alpha02"

[libraries]
viewcompose-foundation = { module = "com.viewcompose:viewcompose-ui-foundation", version.ref = "viewcompose" }
unused-viewcompose = "com.viewcompose:viewcompose-animation:0.1.0-alpha05"
`,
    'app/build.gradle.kts': 'dependencies { implementation(libs.viewcompose.foundation) }',
  });
  try {
    const result = await detectFrameworkProjectProfile({projectRoot: root});
    assert.equal(result.status, 'resolved');
    assert.deepEqual(result.artifacts.map(({coordinate}) => coordinate), [
      'com.viewcompose:viewcompose-ui-foundation',
    ]);
    assert.equal(result.artifacts[0].evidence[0].kind, 'version-catalog');
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('resolves ViewCompose libraries used through a standard version-catalog bundle', async () => {
  const root = await project({
    'gradle/libs.versions.toml': `[versions]
viewcompose = "0.1.0-alpha02"

[libraries]
viewcompose-foundation = { module = "com.viewcompose:viewcompose-ui-foundation", version.ref = "viewcompose" }
viewcompose-material = { module = "com.viewcompose:viewcompose-material3-android", version.ref = "viewcompose" }

[bundles]
viewcompose = ["viewcompose-foundation", "viewcompose-material"]
`,
    'app/build.gradle.kts': 'dependencies { implementation(libs.bundles.viewcompose) }',
  });
  try {
    const result = await detectFrameworkProjectProfile({projectRoot: root});
    assert.equal(result.status, 'resolved');
    assert.equal(result.artifacts.length, 2);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('uses an exact dependency lock to resolve a dynamic declaration', async () => {
  const root = await project({
    'app/build.gradle.kts': 'dependencies { implementation("com.viewcompose:viewcompose-ui-foundation:0.1.+") }',
    'gradle/dependency-locks/runtimeClasspath.lockfile':
      'com.viewcompose:viewcompose-ui-foundation:0.1.0-alpha02=runtimeClasspath\n',
  });
  try {
    const result = await detectFrameworkProjectProfile({projectRoot: root});
    assert.equal(result.status, 'resolved');
    assert.equal(result.artifacts[0].version, '0.1.0-alpha02');
    assert.equal(result.artifacts[0].evidence.some((item) => item.kind === 'dependency-lock'), true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('rejects a dependency lock that conflicts with an exact declaration', async () => {
  const root = await project({
    'app/build.gradle.kts':
      'dependencies { implementation("com.viewcompose:viewcompose-ui-foundation:0.1.0-alpha01") }',
    'gradle.lockfile': 'com.viewcompose:viewcompose-ui-foundation:0.1.0-alpha02=runtimeClasspath\n',
  });
  try {
    const result = await detectFrameworkProjectProfile({projectRoot: root});
    assert.equal(result.status, 'conflict');
    assert.deepEqual(result.conflicts[0].versions, ['0.1.0-alpha01', '0.1.0-alpha02']);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('fails closed for unresolved and conflicting versions', async () => {
  const unresolvedRoot = await project({
    'build.gradle.kts': 'dependencies { implementation("com.viewcompose:viewcompose-ui-foundation:$viewcomposeVersion") }',
  });
  const conflictingRoot = await project({
    'a/build.gradle.kts': 'dependencies { implementation("com.viewcompose:viewcompose-ui-foundation:0.1.0-alpha01") }',
    'b/build.gradle.kts': 'dependencies { implementation("com.viewcompose:viewcompose-ui-foundation:0.1.0-alpha02") }',
  });
  try {
    assert.equal((await detectFrameworkProjectProfile({projectRoot: unresolvedRoot})).status, 'unresolved');
    const conflict = await detectFrameworkProjectProfile({projectRoot: conflictingRoot});
    assert.equal(conflict.status, 'conflict');
    assert.deepEqual(conflict.conflicts[0].versions, ['0.1.0-alpha01', '0.1.0-alpha02']);
  } finally {
    await rm(unresolvedRoot, {recursive: true, force: true});
    await rm(conflictingRoot, {recursive: true, force: true});
  }
});

test('distinguishes an empty new project from imports without dependency identity', async () => {
  const emptyRoot = await project({'settings.gradle.kts': 'rootProject.name = "Empty"'});
  const importedRoot = await project({
    'app/src/main/java/example/Screen.kt': 'package example\nimport com.viewcompose.ui.foundation.UiTreeBuilder\n',
  });
  try {
    const empty = await detectFrameworkProjectProfile({projectRoot: emptyRoot});
    assert.equal(empty.status, 'empty');
    assert.equal(empty.profileFingerprint, 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
    assert.equal((await detectFrameworkProjectProfile({projectRoot: importedRoot})).status, 'unresolved');
  } finally {
    await rm(emptyRoot, {recursive: true, force: true});
    await rm(importedRoot, {recursive: true, force: true});
  }
});

test('rejects symbolic links without following them', async () => {
  const root = await project({'settings.gradle.kts': 'rootProject.name = "Linked"'});
  try {
    await symlink(resolve(root, 'settings.gradle.kts'), resolve(root, 'linked.gradle.kts'));
    await assert.rejects(
      detectFrameworkProjectProfile({projectRoot: root}),
      /rejects symbolic link/u,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});
