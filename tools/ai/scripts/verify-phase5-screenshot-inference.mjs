#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {verifyScreenshotDesignInferenceContracts} from './verify-phase0.mjs';
import {canonicalJson} from './screenshot-contract.mjs';
import {validateScreenshotInference} from './screenshot-inference-validator.mjs';

const contractPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-inference-contract.json', import.meta.url),
);
const preprocessingRequestPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot/inference-wireframe.request.json', import.meta.url),
);
const preprocessingResultPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot/inference-wireframe.result.json', import.meta.url),
);
const inferenceRequestPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-inference/wireframe.request.json', import.meta.url),
);
const inferenceResultPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-inference/wireframe.result.json', import.meta.url),
);
const missingConsentPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-inference/provider-without-consent.mutation.json', import.meta.url),
);

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function fingerprint(value, omittedKey) {
  const copy = structuredClone(value);
  if (omittedKey) delete copy[omittedKey];
  return createHash('sha256').update(canonicalJson(copy)).digest('hex');
}

function declaration(request) {
  const {interpretation, intent, policy, authorization} = request;
  return {interpretation, intent, policy, authorization};
}

export async function verifyPhase5ScreenshotInference() {
  const [
    verification,
    contract,
    preprocessingRequest,
    preprocessingResult,
    inferenceRequest,
    inferenceResult,
    missingConsent,
  ] = await Promise.all([
    verifyScreenshotDesignInferenceContracts(),
    readJson(contractPath),
    readJson(preprocessingRequestPath),
    readJson(preprocessingResultPath),
    readJson(inferenceRequestPath),
    readJson(inferenceResultPath),
    readJson(missingConsentPath),
  ]);
  const golden = contract.supportedFixtures[0];
  if (
    verification.screenshotPreprocessing.supportedFixtures.length !== 2 ||
    verification.screenshotPreprocessing.unsupportedFixtures.length !== 2 ||
    verification.screenshotDesignInference.supportedFixtures.length !== 1 ||
    verification.screenshotDesignInference.unsupportedFixtures.length !== 3 ||
    contract.supportedFixtures.length !== 1 ||
    contract.unsupportedFixtures.length !== 3 ||
    golden.expectedNodes !== golden.expectedEvidenceRecords ||
    golden.expectedBlockingQuestions === 0 ||
    golden.expectedCodeGenerationAllowed !== false ||
    contract.execution.providerExecution !== false ||
    contract.execution.networkAccess !== false ||
    contract.execution.providerSelected !== false
  ) {
    throw new Error('Screenshot inference acceptance denominators changed');
  }
  const arguments_ = {
    preprocessingRequest,
    inferenceDeclaration: declaration(inferenceRequest),
    inferenceResult,
  };
  const [first, second] = await Promise.all([
    validateScreenshotInference(arguments_, {requestId: 'phase5-inference-first'}),
    validateScreenshotInference(arguments_, {requestId: 'phase5-inference-second'}),
  ]);
  if (
    first.status !== 'success' ||
    second.status !== 'success' ||
    first.data?.validationFingerprint !== golden.expectedValidationFingerprint ||
    second.data?.validationFingerprint !== golden.expectedValidationFingerprint ||
    JSON.stringify(first.data) !== JSON.stringify(second.data)
  ) {
    throw new Error('Screenshot inference validator did not reproduce the exact import twice');
  }

  const credentialArguments = structuredClone(arguments_);
  credentialArguments.inferenceDeclaration.authorization.apiKey = 'forbidden-not-a-real-secret';
  const consentArguments = structuredClone(arguments_);
  consentArguments.inferenceDeclaration.authorization = missingConsent.value;
  const lineageArguments = structuredClone(arguments_);
  lineageArguments.inferenceResult.requestFingerprint = '0'.repeat(64);
  lineageArguments.inferenceResult.resultFingerprint = fingerprint(
    lineageArguments.inferenceResult,
    'resultFingerprint',
  );
  const [credentialDenied, consentDenied, lineageDenied] = await Promise.all([
    validateScreenshotInference(credentialArguments, {requestId: 'phase5-credential'}),
    validateScreenshotInference(consentArguments, {requestId: 'phase5-consent'}),
    validateScreenshotInference(lineageArguments, {requestId: 'phase5-lineage'}),
  ]);
  const failures = [
    [credentialDenied, 'VC-AI-SCREENSHOT-INFERENCE-CREDENTIAL-DENIED'],
    [consentDenied, 'VC-AI-SCREENSHOT-INFERENCE-CONSENT-REQUIRED'],
    [lineageDenied, 'VC-AI-SCREENSHOT-INFERENCE-LINEAGE-MISMATCH'],
  ];
  if (failures.some(([result, code]) =>
    result.status !== 'invalid' || result.diagnostics?.[0]?.code !== code)) {
    throw new Error('Screenshot inference validator did not fail closed on its three denominators');
  }

  const providerAuthorization = {
    mode: 'provider-adapter',
    privacyReview: 'complete',
    redactionsVerified: true,
    approvedInputFingerprint: preprocessingResult.outputFingerprint,
    providerTransfer: true,
    networkAccess: true,
    providerId: 'example-provider',
    consentReceipt: '1'.repeat(64),
    consentInputFingerprint: preprocessingResult.outputFingerprint,
    approvedPurpose: 'screenshot-to-design-ir',
    retentionReview: 'complete',
    inputPersistence: false,
    outputPersistence: false,
    logs: 'metadata-only',
  };
  const providerRequest = {...inferenceRequest, authorization: providerAuthorization};
  const providerArguments = structuredClone(arguments_);
  providerArguments.inferenceDeclaration.authorization = providerAuthorization;
  providerArguments.inferenceResult.requestFingerprint = fingerprint(providerRequest);
  providerArguments.inferenceResult.producer = {
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
  providerArguments.inferenceResult.resultFingerprint = fingerprint(
    providerArguments.inferenceResult,
    'resultFingerprint',
  );
  const providerImport = await validateScreenshotInference(providerArguments, {
    requestId: 'phase5-provider-import',
  });
  if (
    providerImport.status !== 'success' ||
    providerImport.data?.authorization?.providerId !== 'example-provider'
  ) {
    throw new Error('Screenshot inference validator rejected consent-bound external provenance');
  }
  return {
    supportedGoldens: contract.supportedFixtures.length,
    failClosedDenominators: contract.unsupportedFixtures.length,
    nodes: golden.expectedNodes,
    evidenceRecords: golden.expectedEvidenceRecords,
    unresolvedQuestions: golden.expectedUnresolvedQuestions,
    blockingQuestions: golden.expectedBlockingQuestions,
    requestFingerprint: golden.expectedRequestFingerprint,
    designIrFingerprint: golden.expectedDesignIrFingerprint,
    resultFingerprint: golden.expectedResultFingerprint,
    validationFingerprint: first.data.validationFingerprint,
    deterministicValidations: 2,
    providerImports: 1,
    providerExecutions: 0,
    networkRequests: 0,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase5ScreenshotInference()
    .then((summary) => {
      process.stdout.write(
        `Verified Phase 5 screenshot inference contract: ` +
        `${summary.supportedGoldens}/1 human golden, ` +
        `${summary.nodes}/${summary.evidenceRecords} node/evidence records, ` +
        `${summary.unresolvedQuestions} unresolved and ${summary.blockingQuestions} blocking questions, ` +
        `${summary.failClosedDenominators}/3 fail-closed denominators, ` +
        `${summary.deterministicValidations}/2 deterministic offline validations, ` +
        `${summary.providerImports}/1 consent-bound external provider import, ` +
        `${summary.providerExecutions} provider executions, and ${summary.networkRequests} network requests; ` +
        `Design IR ${summary.designIrFingerprint}, result ${summary.resultFingerprint}, ` +
        `validation ${summary.validationFingerprint}.\n`,
      );
    })
    .catch((error) => {
      process.stderr.write(`ViewCompose screenshot inference verification failed: ${error.message}\n`);
      process.exitCode = 1;
    });
}
