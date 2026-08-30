import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {generateScreenshotKotlin} from './screenshot-design-ir-to-kotlin.mjs';
import {canonicalJson} from './screenshot-contract.mjs';

const resolutionPath = new URL(
  '../evaluation/fixtures/visual/screenshot-resolution/wireframe.result.json',
  import.meta.url,
);
const requestPath = new URL(
  '../evaluation/fixtures/visual/screenshot-generation/wireframe.request.json',
  import.meta.url,
);
const kotlinPath = new URL(
  '../evaluation/fixtures/visual/screenshot-generation/wireframe.generated.kt',
  import.meta.url,
);
const reportPath = new URL(
  '../evaluation/fixtures/visual/screenshot-generation/wireframe.report.json',
  import.meta.url,
);

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function fingerprint(value, omittedKey) {
  const copy = structuredClone(value);
  if (omittedKey) delete copy[omittedKey];
  return createHash('sha256').update(canonicalJson(copy)).digest('hex');
}

async function arguments_() {
  const [resolutionResult, generationRequest] = await Promise.all([
    readJson(resolutionPath),
    readJson(requestPath),
  ]);
  return {resolutionResult, generationRequest};
}

function rebind(arguments_) {
  arguments_.resolutionResult.designIrFingerprint = fingerprint(arguments_.resolutionResult.designIr);
  arguments_.resolutionResult.resultFingerprint = fingerprint(arguments_.resolutionResult, 'resultFingerprint');
  arguments_.generationRequest.input.resolvedDesignIrFingerprint =
    arguments_.resolutionResult.designIrFingerprint;
  arguments_.generationRequest.input.resolutionResultFingerprint =
    arguments_.resolutionResult.resultFingerprint;
}

test('generates the exact screenshot Kotlin and accessibility report twice', async () => {
  const input = await arguments_();
  const [first, second, goldenKotlin, goldenReport] = await Promise.all([
    generateScreenshotKotlin(input),
    generateScreenshotKotlin(input),
    readFile(kotlinPath, 'utf8'),
    readJson(reportPath),
  ]);
  assert.equal(first.status, 'success');
  assert.equal(second.status, 'success');
  assert.equal(first.kotlin, goldenKotlin);
  assert.deepEqual(first, second);
  assert.deepEqual(first.report, goldenReport);
  assert.equal(first.report.bindings.states[0].type, 'TextFieldState');
  assert.deepEqual(first.report.bindings.events.map((binding) => binding.type), [
    '(TextFieldImeAction) -> Boolean',
    '() -> Unit',
  ]);
  assert.equal(first.report.accessibility.traversal.explicitModifier, false);
});

test('rejects a mechanically ineligible resolution before generation', async () => {
  const input = await arguments_();
  input.resolutionResult.summary.codeGenerationAllowed = false;
  rebind(input);
  const result = await generateScreenshotKotlin(input);
  assert.equal(result.status, 'invalid');
  assert.equal(result.diagnostics[0].code, 'VC-AI-SCREENSHOT-GENERATION-NOT-ELIGIBLE');
});

test('rejects changed generation lineage', async () => {
  const input = await arguments_();
  input.generationRequest.input.resolvedDesignIrFingerprint = 'a'.repeat(64);
  const result = await generateScreenshotKotlin(input);
  assert.equal(result.status, 'invalid');
  assert.equal(result.diagnostics[0].code, 'VC-AI-SCREENSHOT-GENERATION-LINEAGE-MISMATCH');
});

test('rejects an unsupported callback kind without treating it as source', async () => {
  const input = await arguments_();
  const button = input.resolutionResult.designIr.roots[0].children.find((node) =>
    node.id === 'wireframe-button');
  button.events[0].kind = 'long-click';
  rebind(input);
  const result = await generateScreenshotKotlin(input);
  assert.equal(result.status, 'unsupported');
  assert.equal(result.diagnostics[0].code, 'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED');
});

test('rejects accessibility order that cannot be emitted honestly', async () => {
  const input = await arguments_();
  const title = input.resolutionResult.designIr.roots[0].children[0];
  const index = title.semantics.find((field) => field.name === 'traversalIndex');
  index.value.value = 3;
  rebind(input);
  const result = await generateScreenshotKotlin(input);
  assert.equal(result.status, 'unsupported');
  assert.equal(result.diagnostics[0].code, 'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED');
});
