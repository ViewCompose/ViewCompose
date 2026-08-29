import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {convertXmlToViewCompose} from './xml-migration.mjs';

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
