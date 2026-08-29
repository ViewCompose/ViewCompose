#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {validateSchemaValue} from './schema-validator.mjs';
import {canonicalJson, SCREENSHOT_PREPROCESSING_SCHEMA} from './screenshot-contract.mjs';
import {
  decodeScreenshotPng,
  encodeScreenshotPng,
  prepareScreenshot,
} from './screenshot-preprocessor.mjs';
import {compareScreenshotPixels} from './pixel-comparator.mjs';
import {generateScreenshotViewCompose} from './screenshot-generation-adapter.mjs';

const visualRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));
const contractPath = resolve(visualRoot, 'screenshot-pixel-comparison-contract.json');
const schemaPath = fileURLToPath(
  new URL('../contracts/screenshot-pixel-comparison.schema.json', import.meta.url),
);
const localizationSchemaPath = fileURLToPath(
  new URL('../contracts/screenshot-pixel-localization.schema.json', import.meta.url),
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

function assertContract(contract, schema, localizationSchema) {
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-screenshot-pixel-comparison-v1' ||
    !same(contract.requiresContracts, [
      'screenshot-preprocessing-v1',
      'viewcompose-screenshot-layout-comparison-v1',
      'screenshot-pixel-comparison-v1',
      'screenshot-pixel-localization-v1',
    ]) ||
    contract.activation?.tool !== 'generate_screenshot_viewcompose' ||
    contract.activation?.status !== 'implemented' ||
    contract.activation?.publicPixelCompareMode !== true ||
    contract.activation?.implementation !== true ||
    contract.activation?.localizationStatus !== 'contract-frozen' ||
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
    contract.policy?.aggregateScore !== false ||
    !same(contract.localizationPolicy, {
      ownership: 'deepest-containing-design-node',
      bounds: 'left-top-inclusive-right-bottom-exclusive',
      tieBreak: 'deepest-path-then-design-node-id',
      unassigned: 'retained-separately',
      aggregateScore: false,
    })
  ) {
    throw new Error('Screenshot pixel comparison eligibility or exactness boundary changed');
  }
  if (
    !contract.claims?.checked?.includes('exact decoded RGBA equality after strict eligibility checks') ||
    !contract.claims?.notClaimed?.includes('perceptual similarity') ||
    !contract.claims?.notClaimed?.includes('automatic source repair') ||
    !contract.claims?.checked?.includes(
      'bounded mismatch bounds and deepest-node attribution without patch inference',
    ) ||
    !contract.claims?.notClaimed?.includes('repair values inferred from mismatch location') ||
    !contract.claims?.notClaimed?.includes(
      'pixel parity for redacted or configuration-mismatched references',
    )
  ) {
    throw new Error('Screenshot pixel comparison claim boundary changed');
  }
  for (const [name, ceiling] of Object.entries({
    maxCompressedBytesPerPng: 1_310_720,
    maxDecodedBytesPerPng: 16_777_216,
    maxDimensionPx: 4096,
    maxPixels: 4_194_304,
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
    contract.limits.maxCompressedBytesPerPng !== 1_310_720 ||
    contract.limits.maxDecodedBytesPerPng !== 16_777_216 ||
    contract.limits.maxDimensionPx !== 4096 ||
    contract.limits.maxPixels !== 4_194_304
  ) {
    throw new Error('Screenshot pixel comparison limits diverge from screenshot preprocessing');
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
  if (
    localizationSchema.$id !==
      'https://schemas.viewcompose.com/ai/screenshot-pixel-localization-v1.schema.json' ||
    localizationSchema.properties?.schemaVersion?.const !== 1 ||
    localizationSchema.properties?.policy?.properties?.ownership?.const !==
      'deepest-containing-design-node' ||
    localizationSchema.properties?.policy?.properties?.bounds?.const !==
      'left-top-inclusive-right-bottom-exclusive' ||
    localizationSchema.properties?.policy?.properties?.tieBreak?.const !==
      'deepest-path-then-design-node-id' ||
    localizationSchema.properties?.policy?.properties?.unassigned?.const !==
      'retained-separately' ||
    localizationSchema.properties?.policy?.properties?.aggregateScore?.const !== false ||
    localizationSchema.properties?.attributions?.maxItems !== contract.limits.maxFindings
  ) {
    throw new Error('Screenshot pixel localization schema boundary changed');
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

export async function verifyPhase5ScreenshotPixelComparison({compareGolden = true} = {}) {
  const [contract, schema, localizationSchema, layoutContract, previewRequest] = await Promise.all([
    readJson(contractPath),
    readJson(schemaPath),
    readJson(localizationSchemaPath),
    readJson(resolve(visualRoot, 'screenshot-layout-comparison-contract.json')),
    readJson(resolve(visualRoot, 'screenshot-render/wireframe.preview-request.json')),
  ]);
  assertContract(contract, schema, localizationSchema);
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
    fixture.expectedMetrics.maxChannelDelta !== 0 ||
    !same(fixture.expectedLocalization, {
      status: 'exact',
      mismatchedPixels: 0,
      mismatchBounds: null,
      attributions: 0,
      unassignedMismatchedPixels: 0,
    })
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
    } else if (mutation.operation === 'xor-render-channel') {
      if (
        mutation.pixelIndex !== 0 ||
        mutation.channel !== 'red' ||
        mutation.value !== 1 ||
        mutation.expectedMetrics?.mismatchedPixels !== 1 ||
        mutation.expectedMetrics?.maxChannelDelta !== 1 ||
        !same(mutation.expectedLocalization, {
          status: 'mismatch',
          mismatchBounds: {x: 0, y: 0, width: 1, height: 1},
          designNodeId: 'pixel-root',
          attributedMismatchedPixels: 1,
          unassignedMismatchedPixels: 0,
        })
      ) {
        throw new Error('Screenshot pixel mismatch denominator changed');
      }
      assertCodes(mutation.expectedDiagnosticCodes, unsupported.diagnosticCodes, mutation.operation);
      blocked += 1;
      continue;
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
  let compared = 0;
  let cacheHits = 0;
  if (compareGolden) {
    const [resolutionResult, generationRequest] = await Promise.all([
      readJson(resolve(visualRoot, 'screenshot-resolution/wireframe.result.json')),
      readJson(resolve(visualRoot, contract.lineage.pixelGenerationRequest)),
    ]);
    const input = {
      resolutionResult,
      generationRequest,
      previewBindings: previewRequest.bindings,
      pixelReference: {request: referenceRequest, result: referenceResult},
    };
    const first = await generateScreenshotViewCompose(input, {
      requestId: 'phase5-screenshot-pixel-first',
      limits: {maxSourceBytes: 2_000_000, timeoutMs: 120_000, maxOutputBytes: 2_000_000},
    });
    const comparison = first.data?.pixelComparison;
    if (
      first.status !== 'success' ||
      first.evidence?.level !== 'compared' ||
      first.evidence?.outputFingerprint !== fixture.expectedComparisonFingerprint ||
      first.data?.generationReport?.requestFingerprint !==
        contract.lineage.pixelGenerationRequestFingerprint ||
      first.data?.generationReport?.reportFingerprint !==
        contract.lineage.pixelGenerationReportFingerprint ||
      first.data?.comparison?.comparisonFingerprint !==
        contract.lineage.semanticComparisonFingerprint ||
      comparison?.comparisonFingerprint !== fixture.expectedComparisonFingerprint ||
      comparison?.reference?.outputFingerprint !== contract.lineage.referenceOutputFingerprint ||
      comparison?.render?.outputFingerprint !== contract.lineage.renderOutputFingerprint ||
      !same(comparison?.metrics, fixture.expectedMetrics) ||
      comparison?.findings?.length !== 0 ||
      first.diagnostics?.length !== 0
    ) {
      throw new Error('Screenshot exact pixel comparison or accepted lineage changed');
    }
    compared += 1;

    const second = await generateScreenshotViewCompose(input, {
      requestId: 'phase5-screenshot-pixel-cache',
      limits: {maxSourceBytes: 2_000_000, timeoutMs: 120_000, maxOutputBytes: 2_000_000},
    });
    if (
      second.status !== 'success' ||
      second.evidence?.cache !== 'hit' ||
      second.evidence?.outputFingerprint !== fixture.expectedComparisonFingerprint ||
      !same(second.data?.pixelComparison, comparison)
    ) {
      throw new Error('Screenshot pixel comparison cache replay changed');
    }
    cacheHits += 1;

    const renderedEvidence = {
      ...first.evidence,
      level: 'rendered',
      outputFingerprint: contract.lineage.renderOutputFingerprint,
    };
    for (const unsupported of contract.unsupportedFixtures.slice(0, 3)) {
      const mutation = await readJson(resolve(visualRoot, unsupported.mutation));
      const arguments_ = {
        referenceRequest,
        referenceResult,
        semanticComparison: first.data.comparison,
        preview: first.data.preview,
        previewEvidence: renderedEvidence,
      };
      if (mutation.operation === 'replace-reference-pair') {
        arguments_.referenceRequest = await readJson(
          resolve(visualRoot, 'screenshot-pixel', mutation.request),
        );
        arguments_.referenceResult = await readJson(
          resolve(visualRoot, 'screenshot-pixel', mutation.result),
        );
      } else if (mutation.operation === 'remove-semantic-comparison') {
        arguments_.semanticComparison = undefined;
      } else if (mutation.operation === 'replace-reference-output-sha256') {
        arguments_.referenceResult = structuredClone(referenceResult);
        arguments_.referenceResult.output.sha256 = mutation.value;
      }
      const denied = await compareScreenshotPixels(arguments_);
      assertCodes(
        denied.diagnostics.map((item) => item.code),
        unsupported.diagnosticCodes,
        `${mutation.operation} implementation`,
      );
    }

    const mismatchMutation = await readJson(resolve(
      visualRoot,
      contract.unsupportedFixtures[3].mutation,
    ));
    const temporaryRepository = await mkdtemp(resolve(tmpdir(), 'viewcompose-pixel-gate-'));
    try {
      const renderPath = resolve(temporaryRepository, 'render.png');
      const pixels = decodeScreenshotPng(referenceRequest.screenshot);
      pixels[mismatchMutation.pixelIndex * 4] ^= mismatchMutation.value;
      const bytes = encodeScreenshotPng(pixels, fixture.viewport.widthPx, fixture.viewport.heightPx);
      await mkdir(temporaryRepository, {recursive: true});
      await writeFile(renderPath, bytes);
      const preview = structuredClone(first.data.preview);
      preview.image = {
        ...preview.image,
        path: 'render.png',
        bytes: bytes.length,
        sha256: sha256(bytes),
      };
      preview.generatedPreview.pngSha256 = preview.image.sha256;
      const previewEvidence = {
        ...renderedEvidence,
        outputFingerprint: sha256(`mutated-render:${preview.image.sha256}`),
      };
      const semanticComparison = structuredClone(first.data.comparison);
      semanticComparison.render.outputFingerprint = previewEvidence.outputFingerprint;
      delete semanticComparison.comparisonFingerprint;
      semanticComparison.comparisonFingerprint = sha256(JSON.stringify(semanticComparison));
      const mismatch = await compareScreenshotPixels({
        referenceRequest,
        referenceResult,
        semanticComparison,
        preview,
        previewEvidence,
      }, {repository: temporaryRepository});
      if (
        mismatch.status !== 'failed' ||
        mismatch.evidenceLevel !== 'rendered' ||
        !same(mismatch.diagnostics.map((item) => item.code), mismatchMutation.expectedDiagnosticCodes) ||
        mismatch.comparison?.metrics?.mismatchedPixels !==
          mismatchMutation.expectedMetrics.mismatchedPixels ||
        mismatch.comparison?.metrics?.maxChannelDelta !==
          mismatchMutation.expectedMetrics.maxChannelDelta
      ) {
        throw new Error('Screenshot one-channel pixel mismatch did not fail exactly');
      }
    } finally {
      await rm(temporaryRepository, {recursive: true, force: true});
    }
  }
  return {
    supportedGoldens: 1,
    failClosedDenominators: blocked,
    implementation: true,
    compared,
    cacheHits,
    comparisonFingerprint: fixture.expectedComparisonFingerprint,
    referenceOutputFingerprint: referenceResult.outputFingerprint,
  };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  verifyPhase5ScreenshotPixelComparison()
    .then((result) => {
      process.stdout.write(
        `Verified Phase 5 screenshot pixel comparison: ${result.compared}/1 exact RGBA result, ` +
          `${result.cacheHits}/1 stable cache hit, and ` +
          `${result.failClosedDenominators}/4 fail-closed denominators; accepted ` +
          `${result.comparisonFingerprint}.\n`,
      );
    })
    .catch((error) => {
      process.stderr.write(
        `Phase 5 screenshot pixel contract verification failed: ${error.message}\n`,
      );
      process.exitCode = 1;
    });
}
