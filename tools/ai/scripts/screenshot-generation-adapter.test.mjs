import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {generateScreenshotViewCompose} from './screenshot-generation-adapter.mjs';

const resolutionPath = new URL(
  '../evaluation/fixtures/visual/screenshot-resolution/wireframe.result.json',
  import.meta.url,
);
const requestPath = new URL(
  '../evaluation/fixtures/visual/screenshot-generation/wireframe.request.json',
  import.meta.url,
);
const previewRequestPath = new URL(
  '../evaluation/fixtures/visual/screenshot-render/wireframe.preview-request.json',
  import.meta.url,
);

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

async function arguments_(mode = 'compile') {
  const [resolutionResult, generationRequest] = await Promise.all([
    readJson(resolutionPath),
    readJson(requestPath),
  ]);
  generationRequest.mode = mode;
  return {resolutionResult, generationRequest};
}

test('returns static screenshot Kotlin without invoking compilation in generate mode', async () => {
  let compiles = 0;
  const result = await generateScreenshotViewCompose(await arguments_('generate'), {
    requestId: 'screenshot-generate',
    compile: async () => {
      compiles += 1;
      throw new Error('compile must not run');
    },
  });
  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'static');
  assert.equal(result.data.kotlinFingerprint,
    '5812c3ccbd0a6f30a0cc4c3ff4e71453006745d5dd76e63e153b2501131252e9');
  assert.equal(result.data.generationReport.verification.compilation, 'required');
  assert.equal(compiles, 0);
});

test('passes only generated source and fixed selections to the compiler', async () => {
  let invocation;
  const result = await generateScreenshotViewCompose(await arguments_(), {
    requestId: 'screenshot-compile',
    limits: {maxSourceBytes: 262144, timeoutMs: 120000, maxOutputBytes: 1048576},
    compile: async (value) => {
      invocation = value;
      return {
        status: 'success',
        evidence: {
          level: 'compiled',
          cache: 'miss',
          compilerLane: 'jdk21-kotlin-2.3.10-source',
          outputFingerprint: 'b'.repeat(64),
        },
        diagnostics: [],
        data: {classes: 1},
        truncated: false,
      };
    },
  });
  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'compiled');
  assert.equal(result.evidence.outputFingerprint, 'b'.repeat(64));
  assert.deepEqual(invocation.artifactIds, ['viewcompose-ui-foundation']);
  assert.deepEqual(invocation.capabilityIds, ['foundation.components']);
  assert.match(invocation.source, /onKeyboardAction = onEmailSubmit/u);
  assert.equal(invocation.path, 'generated/viewcompose/ScreenshotWireframeView.kt');
});

test('retains generated evidence when hermetic compilation fails', async () => {
  const result = await generateScreenshotViewCompose(await arguments_(), {
    compile: async () => ({
      status: 'failed',
      evidence: {level: 'compiled', cache: 'bypassed', compilerLane: 'test'},
      diagnostics: [{code: 'VC-AI-COMPILER-ERROR', severity: 'error', message: 'failed'}],
      data: {classes: 0},
      truncated: false,
    }),
  });
  assert.equal(result.status, 'failed');
  assert.equal(result.evidence.level, 'compiled');
  assert.ok(result.data.kotlin.includes('ScreenshotWireframeView'));
  assert.equal(result.diagnostics[0].code, 'VC-AI-COMPILER-ERROR');
});

test('source-binds generated screenshot Kotlin before returning rendered evidence', async () => {
  const input = await arguments_('render');
  input.previewBindings = (await readJson(previewRequestPath)).bindings;
  let invocation;
  const result = await generateScreenshotViewCompose(input, {
    requestId: 'screenshot-render',
    limits: {timeoutMs: 120000, maxOutputBytes: 1048576},
    render: async (value) => {
      invocation = value;
      return {
        status: 'success',
        evidence: {
          level: 'rendered',
          cache: 'miss',
          compilerLane: 'preview-compiler',
          renderLane: 'preview-renderer',
          outputFingerprint: 'd'.repeat(64),
        },
        diagnostics: [],
        data: {
          targetId: 'tools.ai.GeneratedScreenshotPreview',
          generatedPreview: {requestFingerprint: 'e'.repeat(64)},
        },
        truncated: false,
      };
    },
  });

  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'rendered');
  assert.equal(result.evidence.renderLane, 'preview-renderer');
  assert.equal(result.data.preview.targetId, 'tools.ai.GeneratedScreenshotPreview');
  assert.equal(invocation.generationReport.target.functionName, 'ScreenshotWireframeView');
  assert.deepEqual(invocation.previewBindings, input.previewBindings);
  assert.match(invocation.generatedKotlin, /onKeyboardAction = onEmailSubmit/u);
});

test('honors cancellation before screenshot generation', async () => {
  const controller = new AbortController();
  controller.abort();
  const result = await generateScreenshotViewCompose(await arguments_('generate'), {
    signal: controller.signal,
  });
  assert.equal(result.status, 'cancelled');
  assert.equal(result.diagnostics[0].code, 'VC-AI-SCREENSHOT-GENERATION-CANCELLED');
});
