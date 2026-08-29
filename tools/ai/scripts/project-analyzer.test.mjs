import assert from 'node:assert/strict';
import {mkdir, mkdtemp, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {analyzeProject, inspectProjectRequest} from './project-analyzer.mjs';

test('accepts a bounded read-only declared inventory', async () => {
  const result = await inspectProjectRequest({
    projectRoot: '/workspace/sample',
    readOnly: true,
    files: ['settings.gradle.kts', 'app/src/main/java/example/LoginScreen.kt'],
    limits: {maxFiles: 1000, maxBytes: 4194304, timeoutMs: 10000},
    excluded: ['.git', 'build', '*.jks'],
  });
  assert.equal(result.status, 'success');
  assert.equal(result.data.readOnly, true);
});

test('rejects traversal before reading a path', async () => {
  const result = await inspectProjectRequest({
    projectRoot: '/workspace/sample',
    requestedPath: '../../.ssh/id_ed25519',
  });
  assert.equal(result.status, 'invalid');
  assert.equal(result.diagnostics[0].code, 'VC-AI-PATH-ESCAPE');
});

test('rejects inspected-project build execution', async () => {
  const result = await inspectProjectRequest({
    projectRoot: '/workspace/sample',
    requestedOperation: 'execute-project-gradle',
    requestedCommand: './gradlew tasks',
  });
  assert.equal(result.status, 'invalid');
  assert.equal(result.diagnostics[0].code, 'VC-AI-BUILD-EXECUTION-DENIED');
});

test('rejects limits outside the fixed analyzer hard caps', async () => {
  const result = await inspectProjectRequest({
    projectRoot: '/workspace/sample',
    limits: {maxBytes: Number.MAX_SAFE_INTEGER},
  });
  assert.equal(result.status, 'invalid');
  assert.equal(result.diagnostics[0].code, 'VC-AI-PROJECT-LIMIT-INVALID');
});

test('rejects direct secret paths and malformed exclusions before traversal', async () => {
  const secret = await inspectProjectRequest({
    projectRoot: '/workspace/sample',
    requestedPath: 'local.properties',
  });
  assert.equal(secret.status, 'invalid');
  assert.equal(secret.diagnostics[0].code, 'VC-AI-SECRET-PATH-DENIED');

  const exclusions = await inspectProjectRequest({
    projectRoot: '/workspace/sample',
    excluded: '*.generated.kt',
  });
  assert.equal(exclusions.status, 'invalid');
  assert.equal(exclusions.diagnostics[0].code, 'VC-AI-PROJECT-EXCLUSION-INVALID');
});

test('inventories regular files, excludes secrets, and never follows symlinks', async () => {
  const root = await mkdtemp(join(tmpdir(), 'viewcompose-ai-project-'));
  try {
    await mkdir(join(root, 'app'), {recursive: true});
    await writeFile(join(root, 'settings.gradle.kts'), 'pluginManagement {}\n');
    await writeFile(
      join(root, 'app', 'Screen.kt'),
      'import com.viewcompose.ui.foundation.Column\n',
    );
    await writeFile(join(root, 'local.properties'), 'secret=value\n');
    const accepted = await analyzeProject({projectRoot: root});
    assert.equal(accepted.status, 'success');
    assert.deepEqual(
      accepted.data.files.map(({path}) => path),
      ['settings.gradle.kts', 'app/Screen.kt'].sort(),
    );
    assert.equal(accepted.data.signals.viewComposeImports, 1);
    await symlink(join(root, 'app', 'Screen.kt'), join(root, 'linked.kt'));
    const rejected = await analyzeProject({projectRoot: root});
    assert.equal(rejected.status, 'invalid');
    assert.equal(rejected.diagnostics[0].code, 'VC-AI-SYMLINK-DENIED');
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('omits inventory data when the bounded output limit is reached', async () => {
  const root = await mkdtemp(join(tmpdir(), 'viewcompose-ai-output-limit-'));
  try {
    await writeFile(join(root, 'settings.gradle.kts'), 'pluginManagement {}\n');
    const result = await analyzeProject({
      projectRoot: root,
      limits: {maxOutputBytes: 1},
    });
    assert.equal(result.status, 'limited');
    assert.equal(result.diagnostics[0].code, 'VC-AI-OUTPUT-LIMIT');
    assert.equal(result.data, undefined);
    assert.equal(result.truncated, true);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('derives artifacts, capabilities, versions, configuration, and migration findings', async () => {
  const root = await mkdtemp(join(tmpdir(), 'viewcompose-ai-findings-'));
  try {
    await mkdir(join(root, 'app', 'src', 'main', 'java'), {recursive: true});
    await mkdir(join(root, 'app', 'src', 'main', 'res', 'layout'), {recursive: true});
    await writeFile(
      join(root, 'app', 'build.gradle.kts'),
      `
        android { compileSdk = 36; defaultConfig { minSdk = 24; targetSdk = 36 } }
        dependencies {
          implementation("com.viewcompose:viewcompose-ui-foundation:0.1.0-alpha02")
        }
      `,
    );
    await writeFile(
      join(root, 'app', 'src', 'main', 'java', 'Screen.kt'),
      `
        import com.viewcompose.ui.foundation.Column
        import com.viewcompose.ui.foundation.TextDefaults
        import com.viewcompose.ghost.Ghost
        import androidx.compose.foundation.layout.Row
        @ViewComposePreview fun preview() = Unit
      `,
    );
    await writeFile(
      join(root, 'app', 'src', 'main', 'res', 'layout', 'screen.xml'),
      '<LinearLayout><TextView /></LinearLayout>',
    );
    const result = await analyzeProject({projectRoot: root});
    assert.equal(result.status, 'success');
    assert.deepEqual(
      result.data.findings.dependencies.map(({artifactId, state}) => ({artifactId, state})),
      [{artifactId: 'viewcompose-ui-foundation', state: 'current-bundle'}],
    );
    assert.ok(result.data.findings.capabilities.some(
      ({capabilityId}) => capabilityId === 'foundation.components',
    ));
    assert.equal(
      result.data.findings.imports.find(({importName}) => importName.endsWith('TextDefaults')).resolution,
      'supporting-symbol',
    );
    assert.deepEqual(result.data.findings.migrations.androidXml[0].widgets, [
      'LinearLayout',
      'TextView',
    ]);
    assert.equal(result.data.findings.migrations.jetpackCompose.length, 1);
    assert.equal(result.data.findings.previewSources.length, 1);
    assert.deepEqual(
      result.data.findings.configuration.map(({field, value}) => `${field}:${value}`).sort(),
      ['compileSdk:36', 'minSdk:24', 'targetSdk:36'],
    );
    assert.ok(result.diagnostics.some(({code}) => code === 'VC-AI-PROJECT-UNKNOWN-IMPORT'));
    assert.equal(result.diagnostics.some(({code}) => code === 'VC-AI-ARTIFACT-REQUIRED'), false);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('reports unknown artifacts and versions outside the current bundle without executing Gradle', async () => {
  const root = await mkdtemp(join(tmpdir(), 'viewcompose-ai-versions-'));
  try {
    await writeFile(
      join(root, 'build.gradle.kts'),
      `
        dependencies {
          implementation("com.viewcompose:viewcompose-ui-foundation:0.0.1")
          implementation("com.viewcompose:viewcompose-imaginary:1.0.0")
        }
      `,
    );
    const result = await analyzeProject({projectRoot: root});
    assert.equal(result.status, 'success');
    assert.ok(result.diagnostics.some(({code}) => code === 'VC-AI-PROJECT-VERSION-LANE'));
    assert.ok(result.diagnostics.some(({code}) => code === 'VC-AI-PROJECT-UNKNOWN-ARTIFACT'));
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});
