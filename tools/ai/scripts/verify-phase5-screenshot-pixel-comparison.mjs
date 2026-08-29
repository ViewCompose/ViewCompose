#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {validateSchemaValue} from './schema-validator.mjs';
import {canonicalJson, SCREENSHOT_PREPROCESSING_SCHEMA} from './screenshot-contract.mjs';
import {prepareScreenshot} from './screenshot-preprocessor.mjs';

const visualRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));
const contractPath = resolve(visualRoot, 'screenshot-pixel-comparison-contract.json');
const schemaPath = fileURLToPath(
  new URL('../contracts/screenshot-pixel-comparison.schema.json', import.meta.url),
);

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function assertUnique(values, label) {
  if (new Set(values).size !== values.length) throw new Error(`${label} are not unique`);
}

function same(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

function assertContract(contract, schema) {
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-screenshot-pixel-comparison-v1' ||
    !same(contract.requiresContracts, [
      'screenshot-preprocessing-v1',
      'viewcompose-screenshot-layout-comparison-v1',
      'screenshot-pixel-comparison-v1',
    ]) ||
    contract.activation?.tool !== 'generate_screenshot_viewcompose' ||
    contract.activation?.status !== 'contract-frozen' ||
    contract.activation?.publicPixelCompareMode !== false ||
    contract.activation?.implementation !== false ||
    contract.activation?.successEvidence !== 'compared' ||
    contract.activation?.failureEvidence !== 'rendered'
  ) {
    throw new Error('Screenshot pixel comparison activation boundary changed');
  }
  if (
    contract.input?.callerSuppliedPolicy !== false ||
    contract.input?.callerSuppliedArtifactPath !== false ||
    contract.eligibility?.semanticEvidence !==
      'passed exact semantic comparison from the same render is required' ||
    contract.eligibility?.redactions !== 'none' ||
    contract.eligibility?.crop !== 'full rendered viewport' ||
    contract.eligibility?.dimensionTolerancePx !== 0 ||
    contract.policy?.channelTolerance !== 0 ||
    contract.policy?.aggregateScore !== false
  ) {
    throw new Error('Screenshot pixel comparison eligibility or exactness boundary changed');
  }
  if (
    !contract.claims?.checked?.includes('exact decoded RGBA equality after strict eligibility checks') ||
    !contract.claims?.notClaimed?.includes('perceptual similarity') ||
    !contract.claims?.notClaimed?.includes('automatic source repair') ||
    !contract.claims?.notClaimed?.includes(
      'pixel parity for redacted or configuration-mismatched references',
    )
  ) {
    throw new Error('Screenshot pixel comparison claim boundary changed');
  }
  for (const [name, ceiling] of Object.entries({
    maxCompressedBytesPerPng: 1_310_720,
    maxDecodedBytesPerPng: 67_108_864,
    maxDimensionPx: 4096,
    maxPixels: 16_777_216,
    maxPngChunks: 128,
    maxFindings: 1000,
  })) {
    const value = contract.limits?.[name];
    if (!Number.isInteger(value) || value <= 0 || value > ceiling) {
      throw new Error(`Screenshot pixel comparison limit ${name} exceeds its ceiling`);
    }
  }
  assertUnique(contract.diagnosticCodes, 'Screenshot pixel comparison diagnostic codes');
  if (contract.diagnosticCodes.some((code) => !/^VC-AI-PIXEL-[A-Z0-9-]+$/u.test(code))) {
    throw new Error('Screenshot pixel comparison diagnostic namespace changed');
  }
  if (
    schema.$id !==
      'https://schemas.viewcompose.com/ai/screenshot-pixel-comparison-v1.schema.json' ||
    schema.properties?.schemaVersion?.const !== 1 ||
    schema.properties?.policy?.properties?.aggregateScore?.const !== false ||
    schema.properties?.policy?.properties?.channelTolerance?.const !== 0
  ) {
    throw new Error('Screenshot pixel comparison result schema boundary changed');
  }
}

async function reproduceReference(request, result, requestId) {
  const requestViolations = validateSchemaValue(request, SCREENSHOT_PREPROCESSING_SCHEMA);
  const resultViolations = validateSchemaValue(result, SCREENSHOT_PREPROCESSING_SCHEMA);
  if (requestViolations.length > 0 || resultViolations.length > 0) {
    return false;
  }
  const reproduced = await prepareScreenshot(request, {requestId});
  return reproduced.status === 'success' && same(reproduced.data, result);
}

function eligibilityDiagnostics({
  request,
  result,
  referenceIntegrity,
  semanticComparisonFingerprint,
  expectedSemanticFingerprint,
  previewConfiguration,
  viewport,
}) {
  const codes = [];
  if (!referenceIntegrity) codes.push('VC-AI-PIXEL-REFERENCE-INTEGRITY-MISMATCH');
  if (semanticComparisonFingerprint !== expectedSemanticFingerprint) {
    codes.push('VC-AI-PIXEL-SEMANTIC-EVIDENCE-REQUIRED');
  }
  const expected = {
    density: previewConfiguration.density,
    fontScale: previewConfiguration.fontScale,
    localeTag: previewConfiguration.localeTag,
    layoutDirection: previewConfiguration.layoutDirection,
    colorSpace: 'sRGB',
    alphaMode: 'straight',
    orientation: 'upright',
    systemBars: {leftPx: 0, topPx: 0, rightPx: 0, bottomPx: 0},
    crop: {x: 0, y: 0, width: viewport.widthPx, height: viewport.heightPx},
  };
  if (
    !same(request.interpretation, expected) ||
    result.output?.widthPx !== viewport.widthPx ||
    result.output?.heightPx !== viewport.heightPx
  ) {
    codes.push('VC-AI-PIXEL-CONFIGURATION-MISMATCH');
  }
  if (request.privacy?.redactions?.length !== 0 || result.privacy?.redactionsApplied !== 0) {
    codes.push('VC-AI-PIXEL-REDACTION-UNSUPPORTED');
  }
  return [...new Set(codes)];
}

function assertCodes(actual, expected, label) {
  if (!same(actual, expected)) {
    throw new Error(`${label}: expected ${expected.join(', ')}, received ${actual.join(', ')}`);
  }
}

export async function verifyPhase5ScreenshotPixelComparison() {
  const [contract, schema, layoutContract, previewRequest] = await Promise.all([
    readJson(contractPath),
    readJson(schemaPath),
    readJson(resolve(visualRoot, 'screenshot-layout-comparison-contract.json')),
    readJson(resolve(visualRoot, 'screenshot-render/wireframe.preview-request.json')),
  ]);
  assertContract(contract, schema);
  const [referenceRequest, referenceResult] = await Promise.all([
    readJson(resolve(visualRoot, contract.lineage.referenceRequest)),
    readJson(resolve(visualRoot, contract.lineage.referenceResult)),
  ]);
  const referenceIntegrity = await reproduceReference(
    referenceRequest,
    referenceResult,
    'phase5-pixel-reference',
  );
  const fixture = contract.supportedFixtures[0];
  if (
    !referenceIntegrity ||
    sha256(canonicalJson(referenceRequest)) !== contract.lineage.referenceRequestFingerprint ||
    referenceResult.requestFingerprint !== contract.lineage.referenceRequestFingerprint ||
    referenceResult.outputFingerprint !== contract.lineage.referenceOutputFingerprint ||
    referenceResult.output.sha256 !== contract.lineage.referencePngFingerprint ||
    layoutContract.supportedFixtures?.[0]?.expectedComparisonFingerprint !==
      contract.lineage.semanticComparisonFingerprint ||
    layoutContract.lineage?.previewRequestFingerprint !==
      contract.lineage.previewRequestFingerprint ||
    layoutContract.lineage?.acceptedOutputFingerprint !==
      contract.lineage.renderOutputFingerprint ||
    fixture.viewport.widthPx * fixture.viewport.heightPx !== fixture.expectedMetrics.totalPixels ||
    fixture.expectedMetrics.comparedPixels !== fixture.expectedMetrics.totalPixels ||
    fixture.expectedMetrics.mismatchedPixels !== 0 ||
    fixture.expectedMetrics.exactPixelRatio !== 1 ||
    fixture.expectedMetrics.meanAbsoluteErrorRgba !== 0 ||
    fixture.expectedMetrics.rootMeanSquareErrorRgba !== 0 ||
    fixture.expectedMetrics.maxChannelDelta !== 0
  ) {
    throw new Error('Screenshot pixel reference lineage or exact denominator changed');
  }
  const accepted = eligibilityDiagnostics({
    request: referenceRequest,
    result: referenceResult,
    referenceIntegrity,
    semanticComparisonFingerprint: contract.lineage.semanticComparisonFingerprint,
    expectedSemanticFingerprint: contract.lineage.semanticComparisonFingerprint,
    previewConfiguration: previewRequest.configuration,
    viewport: fixture.viewport,
  });
  assertCodes(accepted, [], 'eligible pixel reference');

  let blocked = 0;
  for (const unsupported of contract.unsupportedFixtures) {
    const mutation = await readJson(resolve(visualRoot, unsupported.mutation));
    let request = referenceRequest;
    let result = referenceResult;
    let semanticFingerprint = contract.lineage.semanticComparisonFingerprint;
    let integrity = referenceIntegrity;
    if (mutation.operation === 'replace-reference-pair') {
      [request, result] = await Promise.all([
        readJson(resolve(visualRoot, 'screenshot-pixel', mutation.request)),
        readJson(resolve(visualRoot, 'screenshot-pixel', mutation.result)),
      ]);
      integrity = await reproduceReference(request, result, 'phase5-pixel-ineligible-configuration');
    } else if (mutation.operation === 'remove-semantic-comparison') {
      semanticFingerprint = undefined;
    } else if (mutation.operation === 'replace-reference-output-sha256') {
      result = structuredClone(referenceResult);
      result.output.sha256 = mutation.value;
      integrity = await reproduceReference(request, result, 'phase5-pixel-ineligible-integrity');
    } else {
      throw new Error(`${mutation.operation}: unknown screenshot pixel mutation`);
    }
    const actualCodes = eligibilityDiagnostics({
      request,
      result,
      referenceIntegrity: integrity,
      semanticComparisonFingerprint: semanticFingerprint,
      expectedSemanticFingerprint: contract.lineage.semanticComparisonFingerprint,
      previewConfiguration: previewRequest.configuration,
      viewport: fixture.viewport,
    });
    assertCodes(actualCodes, mutation.expectedDiagnosticCodes, mutation.operation);
    assertCodes(actualCodes, unsupported.diagnosticCodes, `${mutation.operation} contract`);
    blocked += 1;
  }
  return {
    supportedGoldens: 1,
    failClosedDenominators: blocked,
    implementation: false,
    referenceOutputFingerprint: referenceResult.outputFingerprint,
  };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  verifyPhase5ScreenshotPixelComparison()
    .then((result) => {
      process.stdout.write(
        `Verified Phase 5 screenshot pixel contract: ${result.supportedGoldens}/1 eligible ` +
          `reference and ${result.failClosedDenominators}/3 ineligible denominators; ` +
          `pixel execution remains intentionally disabled.\n`,
      );
    })
    .catch((error) => {
      process.stderr.write(
        `Phase 5 screenshot pixel contract verification failed: ${error.message}\n`,
      );
      process.exitCode = 1;
    });
}
