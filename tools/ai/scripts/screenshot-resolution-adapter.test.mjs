import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {validateScreenshotInference} from './screenshot-inference-validator.mjs';
import {resolveScreenshotInference} from './screenshot-resolution-adapter.mjs';

const fixtureRoot = new URL('../evaluation/fixtures/visual/', import.meta.url);

async function readJson(path) {
  return JSON.parse(await readFile(new URL(path, fixtureRoot), 'utf8'));
}

async function goldenArguments() {
  const [preprocessingRequest, inferenceRequest, inferenceResult, resolutionRequest] =
    await Promise.all([
      readJson('screenshot/inference-wireframe.request.json'),
      readJson('screenshot-inference/wireframe.request.json'),
      readJson('screenshot-inference/wireframe.result.json'),
      readJson('screenshot-resolution/wireframe.request.json'),
    ]);
  const {interpretation, intent, policy, authorization} = inferenceRequest;
  const imported = await validateScreenshotInference({
    preprocessingRequest,
    inferenceDeclaration: {interpretation, intent, policy, authorization},
    inferenceResult,
  });
  assert.equal(imported.status, 'success');
  return {validatedInference: imported.data, resolutionRequest};
}

test('applies the exact typed screenshot resolution deterministically', async () => {
  const [arguments_, expected] = await Promise.all([
    goldenArguments(),
    readJson('screenshot-resolution/wireframe.result.json'),
  ]);
  const [first, second] = await Promise.all([
    resolveScreenshotInference(arguments_, {requestId: 'resolution-first'}),
    resolveScreenshotInference(arguments_, {requestId: 'resolution-second'}),
  ]);
  assert.equal(first.status, 'success');
  assert.equal(second.status, 'success');
  assert.deepEqual(first.data, expected);
  assert.deepEqual(second.data, expected);
  assert.equal(first.evidence.outputFingerprint, expected.resultFingerprint);
});

test('requires complete exact question coverage', async () => {
  const arguments_ = await goldenArguments();
  arguments_.resolutionRequest.answers.pop();
  const result = await resolveScreenshotInference(arguments_);
  assert.equal(result.status, 'invalid');
  assert.equal(result.diagnostics[0].code, 'VC-AI-SCREENSHOT-RESOLUTION-COVERAGE-INCOMPLETE');
});

test('rejects executable expressions before patching Design IR', async () => {
  const arguments_ = await goldenArguments();
  arguments_.resolutionRequest.answers[0].decision.fields[0].value = {
    kind: 'expression',
    language: 'kotlin',
    source: 'loadTitle()',
  };
  const result = await resolveScreenshotInference(arguments_);
  assert.equal(result.status, 'invalid');
  assert.equal(result.diagnostics[0].code, 'VC-AI-SCREENSHOT-RESOLUTION-EXECUTABLE-DENIED');
});

test('rejects changed validated-import lineage', async () => {
  const arguments_ = await goldenArguments();
  arguments_.resolutionRequest.input.validationFingerprint = '0'.repeat(64);
  const result = await resolveScreenshotInference(arguments_);
  assert.equal(result.status, 'invalid');
  assert.equal(result.diagnostics[0].code, 'VC-AI-SCREENSHOT-RESOLUTION-LINEAGE-MISMATCH');
});

test('rejects a question answer moved to another pixel region', async () => {
  const arguments_ = await goldenArguments();
  arguments_.resolutionRequest.answers[0].sourceRegion.x += 1;
  const result = await resolveScreenshotInference(arguments_);
  assert.equal(result.status, 'invalid');
  assert.equal(result.diagnostics[0].code, 'VC-AI-SCREENSHOT-RESOLUTION-QUESTION-MISMATCH');
});

test('rejects fields outside the component-specific typed patch surface', async () => {
  const arguments_ = await goldenArguments();
  arguments_.resolutionRequest.answers[0].decision.fields[0].name = 'hint';
  const result = await resolveScreenshotInference(arguments_);
  assert.equal(result.status, 'invalid');
  assert.equal(result.diagnostics[0].code, 'VC-AI-SCREENSHOT-RESOLUTION-DECISION-UNSUPPORTED');
});

test('requires full accessibility coverage and honors cancellation', async () => {
  const arguments_ = await goldenArguments();
  arguments_.resolutionRequest.answers.at(-1).decision.nodes.pop();
  const incomplete = await resolveScreenshotInference(arguments_);
  assert.equal(incomplete.status, 'invalid');
  assert.equal(
    incomplete.diagnostics[0].code,
    'VC-AI-SCREENSHOT-RESOLUTION-COVERAGE-INCOMPLETE',
  );

  const cancelledArguments = await goldenArguments();
  const controller = new AbortController();
  controller.abort();
  const cancelled = await resolveScreenshotInference(cancelledArguments, {
    signal: controller.signal,
  });
  assert.equal(cancelled.status, 'cancelled');
  assert.equal(cancelled.diagnostics[0].code, 'VC-AI-SCREENSHOT-RESOLUTION-CANCELLED');
});
