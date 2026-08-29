import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {canonicalJson} from './screenshot-contract.mjs';
import {prepareScreenshot} from './screenshot-preprocessor.mjs';
import {validateScreenshotInference} from './screenshot-inference-validator.mjs';

const preprocessingRequestPath = new URL(
  '../evaluation/fixtures/visual/screenshot/inference-wireframe.request.json',
  import.meta.url,
);
const inferenceRequestPath = new URL(
  '../evaluation/fixtures/visual/screenshot-inference/wireframe.request.json',
  import.meta.url,
);
const inferenceResultPath = new URL(
  '../evaluation/fixtures/visual/screenshot-inference/wireframe.result.json',
  import.meta.url,
);

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function fingerprint(value, omittedKey) {
  const copy = structuredClone(value);
  if (omittedKey) delete copy[omittedKey];
  return sha256(canonicalJson(copy));
}

function declaration(request) {
  const {interpretation, intent, policy, authorization} = request;
  return {interpretation, intent, policy, authorization};
}

async function fixtures() {
  const [preprocessingRequest, inferenceRequest, inferenceResult] = await Promise.all([
    readJson(preprocessingRequestPath),
    readJson(inferenceRequestPath),
    readJson(inferenceResultPath),
  ]);
  return {
    preprocessingRequest,
    inferenceRequest,
    inferenceResult,
    arguments: {
      preprocessingRequest,
      inferenceDeclaration: declaration(inferenceRequest),
      inferenceResult,
    },
  };
}

function refreshResultFingerprint(result) {
  result.resultFingerprint = fingerprint(result, 'resultFingerprint');
  return result;
}

test('reproduces preprocessing and imports the exact incomplete human golden deterministically', async () => {
  const {arguments: arguments_} = await fixtures();
  const [first, second] = await Promise.all([
    validateScreenshotInference(arguments_, {requestId: 'inference-first'}),
    validateScreenshotInference(arguments_, {requestId: 'inference-second'}),
  ]);
  assert.equal(first.status, 'success');
  assert.equal(second.status, 'success');
  assert.equal(first.data.status, 'incomplete');
  assert.equal(first.data.summary.codeGenerationAllowed, false);
  assert.equal(first.data.designIr.roots.length, 1);
  assert.equal(first.data.nodeEvidence.length, 4);
  assert.equal(first.data.unresolvedQuestions.length, 6);
  assert.equal(first.data.validationFingerprint, second.data.validationFingerprint);
  assert.equal(
    first.data.validationFingerprint,
    '556c13d133d63e34fa81d1c04df3bee938509c5ced1d244ccf2366d48cb6e845',
  );
});

test('rejects changed request lineage even when the result fingerprint is internally consistent', async () => {
  const {arguments: arguments_} = await fixtures();
  arguments_.inferenceResult.requestFingerprint = '0'.repeat(64);
  refreshResultFingerprint(arguments_.inferenceResult);
  const result = await validateScreenshotInference(arguments_);
  assert.equal(result.status, 'invalid');
  assert.equal(result.diagnostics[0].code, 'VC-AI-SCREENSHOT-INFERENCE-LINEAGE-MISMATCH');
});

test('rejects out-of-bounds evidence and missing node evidence separately', async () => {
  const outside = await fixtures();
  outside.arguments.inferenceResult.nodeEvidence[1].sourceRegion.x = 15;
  refreshResultFingerprint(outside.arguments.inferenceResult);
  const outsideResult = await validateScreenshotInference(outside.arguments);
  assert.equal(outsideResult.status, 'invalid');
  assert.equal(outsideResult.diagnostics[0].code, 'VC-AI-SCREENSHOT-INFERENCE-REGION-INVALID');

  const missing = await fixtures();
  missing.arguments.inferenceResult.nodeEvidence.splice(1, 1);
  missing.arguments.inferenceResult.summary.evidenceRecords = 3;
  refreshResultFingerprint(missing.arguments.inferenceResult);
  const missingResult = await validateScreenshotInference(missing.arguments);
  assert.equal(missingResult.status, 'invalid');
  assert.equal(
    missingResult.diagnostics[0].code,
    'VC-AI-SCREENSHOT-INFERENCE-EVIDENCE-INCOMPLETE',
  );
});

test('requires a node question for below-threshold evidence', async () => {
  const {arguments: arguments_} = await fixtures();
  arguments_.inferenceResult.unresolvedQuestions.splice(0, 1);
  arguments_.inferenceResult.summary.unresolvedQuestions = 5;
  arguments_.inferenceResult.summary.blockingQuestions = 5;
  refreshResultFingerprint(arguments_.inferenceResult);
  const result = await validateScreenshotInference(arguments_);
  assert.equal(result.status, 'invalid');
  assert.equal(
    result.diagnostics[0].code,
    'VC-AI-SCREENSHOT-INFERENCE-CONFIDENCE-UNRESOLVED',
  );
});

test('rejects inferred behavior and executable expressions from a still screenshot', async () => {
  const behavior = await fixtures();
  behavior.arguments.inferenceResult.designIr.roots[0].children[2].events.push({
    kind: 'click',
    binding: 'onClick',
    status: 'placeholder',
  });
  refreshResultFingerprint(behavior.arguments.inferenceResult);
  const behaviorResult = await validateScreenshotInference(behavior.arguments);
  assert.equal(behaviorResult.status, 'invalid');
  assert.equal(
    behaviorResult.diagnostics[0].code,
    'VC-AI-SCREENSHOT-INFERENCE-BEHAVIOR-FORBIDDEN',
  );

  const expression = await fixtures();
  expression.arguments.inferenceResult.designIr.roots[0].children[0].properties[0].value = {
    kind: 'expression',
    language: 'kotlin',
    source: 'danger()',
  };
  refreshResultFingerprint(expression.arguments.inferenceResult);
  const expressionResult = await validateScreenshotInference(expression.arguments);
  assert.equal(expressionResult.status, 'invalid');
  assert.equal(
    expressionResult.diagnostics[0].code,
    'VC-AI-SCREENSHOT-INFERENCE-EVIDENCE-INCOMPLETE',
  );
});

test('imports externally produced provider provenance only with exact-input consent', async () => {
  const fixture = await fixtures();
  const prepared = await prepareScreenshot(fixture.preprocessingRequest, {requestId: 'provider-prep'});
  const authorization = {
    mode: 'provider-adapter',
    privacyReview: 'complete',
    redactionsVerified: true,
    approvedInputFingerprint: prepared.data.outputFingerprint,
    providerTransfer: true,
    networkAccess: true,
    providerId: 'example-provider',
    consentReceipt: '1'.repeat(64),
    consentInputFingerprint: prepared.data.outputFingerprint,
    approvedPurpose: 'screenshot-to-design-ir',
    retentionReview: 'complete',
    inputPersistence: false,
    outputPersistence: false,
    logs: 'metadata-only',
  };
  fixture.arguments.inferenceDeclaration.authorization = authorization;
  const providerRequest = {
    ...fixture.inferenceRequest,
    authorization,
  };
  fixture.arguments.inferenceResult.requestFingerprint = fingerprint(providerRequest);
  fixture.arguments.inferenceResult.producer = {
    kind: 'provider-adapter',
    providerId: 'example-provider',
    adapterVersion: 'fixture-v1',
    modelId: 'fixture-model',
    modelVersion: 'fixture-model-v1',
    providerRequestFingerprint: '2'.repeat(64),
    providerResponseFingerprint: '3'.repeat(64),
    networkAccess: true,
    providerTransfer: true,
  };
  refreshResultFingerprint(fixture.arguments.inferenceResult);
  const result = await validateScreenshotInference(fixture.arguments);
  assert.equal(result.status, 'success');
  assert.equal(result.data.authorization.mode, 'provider-adapter');
  assert.equal(result.data.authorization.providerId, 'example-provider');
  assert.equal(result.data.producer.modelVersion, 'fixture-model-v1');
});

test('fails closed on credential input, missing provider consent, and cancellation', async () => {
  const credential = await fixtures();
  credential.arguments.inferenceDeclaration.authorization.apiKey = 'forbidden-not-a-real-secret';
  const credentialResult = await validateScreenshotInference(credential.arguments);
  assert.equal(credentialResult.status, 'invalid');
  assert.equal(
    credentialResult.diagnostics[0].code,
    'VC-AI-SCREENSHOT-INFERENCE-CREDENTIAL-DENIED',
  );

  const missingConsent = await fixtures();
  missingConsent.arguments.inferenceDeclaration.authorization = {
    mode: 'provider-adapter',
    privacyReview: 'complete',
    redactionsVerified: true,
    approvedInputFingerprint: '1'.repeat(64),
    providerTransfer: true,
    networkAccess: true,
    providerId: 'example-provider',
    approvedPurpose: 'screenshot-to-design-ir',
    retentionReview: 'complete',
    inputPersistence: false,
    outputPersistence: false,
    logs: 'metadata-only',
  };
  const missingConsentResult = await validateScreenshotInference(missingConsent.arguments);
  assert.equal(missingConsentResult.status, 'invalid');
  assert.equal(
    missingConsentResult.diagnostics[0].code,
    'VC-AI-SCREENSHOT-INFERENCE-CONSENT-REQUIRED',
  );

  const cancelled = await fixtures();
  const controller = new AbortController();
  controller.abort('test cancellation');
  const cancelledResult = await validateScreenshotInference(cancelled.arguments, {
    signal: controller.signal,
  });
  assert.equal(cancelledResult.status, 'cancelled');
  assert.equal(
    cancelledResult.diagnostics[0].code,
    'VC-AI-SCREENSHOT-INFERENCE-CANCELLED',
  );
});
