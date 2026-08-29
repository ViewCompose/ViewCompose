import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {convertXmlToViewCompose} from './xml-migration.mjs';

const projectContextRoot = fileURLToPath(
  new URL('../evaluation/fixtures/xml/project-context/supported/', import.meta.url),
);

async function fixture(name) {
  return readFile(fileURLToPath(new URL(`../evaluation/fixtures/xml/${name}`, import.meta.url)), 'utf8');
}

test('returns standalone deterministic XML migration data without invoking compilation', async () => {
  let compiled = 0;
  const result = await convertXmlToViewCompose({
    source: await fixture('login.xml'),
    path: 'res/layout/login.xml',
    mode: 'generate',
    requestId: 'xml-generate',
    compile: async () => {
      compiled += 1;
    },
  });

  assert.equal(result.status, 'success');
  assert.equal(result.tool, 'convert_xml_to_viewcompose');
  assert.equal(result.evidence.level, 'static');
  assert.ok(result.data.kotlin.includes('fun UiTreeBuilder.LoginView('));
  assert.equal(result.data.migrationReport.callSiteReview.required, true);
  assert.equal(result.data.designIr.schemaVersion, 1);
  assert.equal(compiled, 0);
});

test('returns standalone XML v2 image and accessibility bindings', async () => {
  const result = await convertXmlToViewCompose({
    source: await fixture('profile-card.xml'),
    path: 'res/layout/profile-card.xml',
    mode: 'generate',
    requestId: 'xml-v2-generate',
  });

  assert.equal(result.status, 'success');
  assert.ok(result.data.kotlin.includes('fun UiTreeBuilder.ProfileCardView('));
  assert.ok(result.data.kotlin.includes('contentDescription = profilePhoto'));
  assert.deepEqual(
    result.data.migrationReport.bindings.resources.map(({source, type}) => ({source, type})),
    [
      {source: '@drawable/profile_avatar', type: 'ImageSource'},
      {source: '@string/profile_photo', type: 'String'},
      {source: '@string/status_label', type: 'String'},
    ],
  );
});

test('returns compiled conversion evidence through the same tool envelope', async () => {
  const result = await convertXmlToViewCompose({
    source: await fixture('login.xml'),
    path: 'res/layout/login.xml',
    mode: 'compile',
    requestId: 'xml-compile',
    limits: {maxSourceBytes: 262144, timeoutMs: 10000, maxOutputBytes: 1048576},
    compile: async (request) => ({
      status: 'success',
      evidence: {
        level: 'compiled',
        cache: 'miss',
        compilerLane: 'test-compiler-lane',
        outputFingerprint: 'b'.repeat(64),
      },
      diagnostics: [],
      data: {sourceBytes: Buffer.byteLength(request.source)},
      truncated: false,
    }),
  });

  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'compiled');
  assert.equal(result.evidence.compilerLane, 'test-compiler-lane');
  assert.equal(result.evidence.outputFingerprint, 'b'.repeat(64));
  assert.ok(result.data.compilation.sourceBytes > 0);
  assert.match(result.data.kotlinFingerprint, /^[a-f0-9]{64}$/u);
});

test('resolves project resources, styles, and call sites before generation', async () => {
  const result = await convertXmlToViewCompose({
    projectRoot: projectContextRoot,
    layoutPath: 'app/src/main/res/layout/styled_login.xml',
    resourceRoots: ['app/src/main/res'],
    sourceRoots: ['app/src/main/java'],
    mode: 'generate',
    requestId: 'xml-project-generate',
    limits: {maxSourceBytes: 4 * 1024 * 1024, timeoutMs: 120_000},
  });

  assert.equal(result.status, 'success');
  assert.equal(result.data.projectContext.resources.length, 4);
  assert.equal(result.data.projectContext.styles.length, 2);
  assert.equal(result.data.projectContext.callSites.length, 7);
  assert.equal(result.data.migrationReport.callSiteReview.inventory.length, 7);
  assert.equal(result.data.migrationReport.projectEvidence.completeness, 'not-proven');
  assert.ok(result.data.kotlin.includes('fun UiTreeBuilder.StyledLoginView('));
  assert.ok(result.data.kotlin.includes('padding(16.dp)'));
});

test('composes explicit project context with the XML v2 image subset', async (context) => {
  const projectRoot = await mkdtemp(resolve(tmpdir(), 'viewcompose-xml-v2-project-'));
  context.after(() => rm(projectRoot, {recursive: true, force: true}));
  const layoutDirectory = resolve(projectRoot, 'app/src/main/res/layout');
  const valuesDirectory = resolve(projectRoot, 'app/src/main/res/values');
  await Promise.all([
    mkdir(layoutDirectory, {recursive: true}),
    mkdir(valuesDirectory, {recursive: true}),
  ]);
  await Promise.all([
    writeFile(resolve(layoutDirectory, 'profile_card.xml'), await fixture('profile-card.xml')),
    writeFile(resolve(valuesDirectory, 'strings.xml'), `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="profile_photo">Profile photo</string>
    <string name="status_label">Online</string>
</resources>
`),
  ]);

  const result = await convertXmlToViewCompose({
    projectRoot,
    layoutPath: 'app/src/main/res/layout/profile_card.xml',
    resourceRoots: ['app/src/main/res'],
    sourceRoots: [],
    mode: 'generate',
    requestId: 'xml-v2-project-generate',
  });

  assert.equal(result.status, 'success');
  assert.equal(result.data.projectContext.resources.length, 2);
  assert.equal(result.data.projectContext.callSites.length, 0);
  assert.ok(result.data.kotlin.includes('profileAvatar: ImageSource'));
  assert.equal(result.data.migrationReport.projectEvidence.completeness, 'not-proven');
});

test('compiles project-aware generated Kotlin through the hermetic adapter', async () => {
  let compiledSource;
  const result = await convertXmlToViewCompose({
    projectRoot: projectContextRoot,
    layoutPath: 'app/src/main/res/layout/styled_login.xml',
    resourceRoots: ['app/src/main/res'],
    sourceRoots: ['app/src/main/java'],
    mode: 'compile',
    requestId: 'xml-project-compile',
    compile: async (request) => {
      compiledSource = request.source;
      return {
        status: 'success',
        evidence: {
          level: 'compiled',
          cache: 'miss',
          compilerLane: 'test-compiler-lane',
          outputFingerprint: 'c'.repeat(64),
        },
        diagnostics: [],
        data: {sourceBytes: Buffer.byteLength(request.source)},
        truncated: false,
      };
    },
  });

  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'compiled');
  assert.ok(compiledSource.includes('fun UiTreeBuilder.StyledLoginView('));
  assert.equal(result.data.projectContext.callSites.length, 7);
});

test('fails closed when project context uses unsupported style semantics', async () => {
  const result = await convertXmlToViewCompose({
    projectRoot: fileURLToPath(
      new URL('../evaluation/fixtures/xml/project-context/style-cycle/', import.meta.url),
    ),
    layoutPath: 'app/src/main/res/layout/cycle.xml',
    resourceRoots: ['app/src/main/res'],
    sourceRoots: [],
    mode: 'generate',
    requestId: 'xml-project-unsupported',
  });

  assert.equal(result.status, 'unsupported');
  assert.ok(result.diagnostics.some((entry) => entry.code === 'VC-AI-XML-STYLE-CYCLE'));
  assert.equal(result.data, undefined);
});

test('preserves unsupported XML diagnostics and never emits Kotlin', async () => {
  const result = await convertXmlToViewCompose({
    source: await fixture('custom-view.xml'),
    path: 'res/layout/custom-view.xml',
    mode: 'generate',
    requestId: 'xml-unsupported',
  });

  assert.equal(result.status, 'unsupported');
  assert.equal(result.diagnostics[0].code, 'VC-AI-XML-CUSTOM-VIEW-UNSUPPORTED');
  assert.equal(Object.hasOwn(result.data, 'kotlin'), false);
  assert.equal(result.data.unsupported[0].preservedSource.includes('AvatarView'), true);
});

test('does not emit Kotlin when an image accessibility decision is missing', async () => {
  const result = await convertXmlToViewCompose({
    source: await fixture('image-missing-description.xml'),
    path: 'res/layout/image-missing-description.xml',
    mode: 'generate',
    requestId: 'xml-v2-accessibility',
  });

  assert.equal(result.status, 'unsupported');
  assert.equal(result.diagnostics[0].code, 'VC-AI-XML-ACCESSIBILITY-REQUIRED');
  assert.equal(Object.hasOwn(result.data, 'kotlin'), false);
});

test('honors cancellation before conversion', async () => {
  const controller = new AbortController();
  controller.abort();
  const result = await convertXmlToViewCompose({
    source: await fixture('login.xml'),
    path: 'res/layout/login.xml',
    mode: 'generate',
    requestId: 'xml-cancelled',
    signal: controller.signal,
  });
  assert.equal(result.status, 'cancelled');
  assert.equal(result.data, undefined);
});
