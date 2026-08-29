import {createHash} from 'node:crypto';
import {lstat, readFile} from 'node:fs/promises';
import {isAbsolute, relative, resolve, sep} from 'node:path';
import {validateSchemaValue} from './schema-validator.mjs';
import {canonicalJson, SCREENSHOT_PREPROCESSING_SCHEMA} from './screenshot-contract.mjs';
import {decodeScreenshotPng, prepareScreenshot} from './screenshot-preprocessor.mjs';
import {diagnostic, repositoryRoot} from './tool-core.mjs';

const pixelSchemaPath = new URL('../contracts/screenshot-pixel-comparison.schema.json', import.meta.url);
const layoutSchemaPath = new URL('../contracts/layout-comparison.schema.json', import.meta.url);
const MAX_COMPRESSED_BYTES = 1_310_720;
const MAX_PIXELS = 4_194_304;
const SHA256 = /^[a-f0-9]{64}$/u;
const POLICY = Object.freeze({
  version: 1,
  colorSpace: 'sRGB',
  alphaMode: 'straight',
  coordinateSpace: 'exact-cropped-viewport',
  redactions: 'none',
  semanticEvidenceRequired: true,
  dimensionTolerancePx: 0,
  channelTolerance: 0,
  aggregateScore: false,
});

let schemasPromise;

function loadSchemas() {
  schemasPromise ??= Promise.all([
    readFile(pixelSchemaPath, 'utf8').then(JSON.parse),
    readFile(layoutSchemaPath, 'utf8').then(JSON.parse),
  ]);
  return schemasPromise;
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function compactFingerprint(value) {
  return sha256(JSON.stringify(value));
}

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function contained(parent, child) {
  const path = relative(resolve(parent), resolve(child));
  return path === '' || (!path.startsWith(`..${sep}`) && path !== '..' && !isAbsolute(path));
}

function pixelFailure(findings, status = 'failed') {
  return {
    status,
    evidenceLevel: 'rendered',
    diagnostics: findings.map((finding) => diagnostic({
      code: finding.code,
      severity: 'error',
      message: finding.message,
      nextAction: finding.nextAction,
    })),
  };
}

function oneFailure(code, message, nextAction, status) {
  return pixelFailure([{code, message, nextAction}], status);
}

function fingerprintWithout(value, key) {
  const copy = structuredClone(value);
  delete copy[key];
  return compactFingerprint(copy);
}

function exactConfiguration(referenceRequest, preview) {
  return {
    density: preview.configuration.density,
    fontScale: preview.configuration.fontScale,
    localeTag: preview.configuration.localeTags[0],
    layoutDirection: preview.configuration.layoutDirection,
    colorSpace: 'sRGB',
    alphaMode: 'straight',
    orientation: 'upright',
    systemBars: {leftPx: 0, topPx: 0, rightPx: 0, bottomPx: 0},
    crop: {x: 0, y: 0, width: preview.image.widthPx, height: preview.image.heightPx},
  };
}

async function readImageArtifact(repository, artifact) {
  if (
    !isObject(artifact) ||
    artifact.mediaType !== 'image/png' ||
    typeof artifact.path !== 'string' ||
    artifact.path.length === 0 ||
    artifact.path.length > 4096 ||
    isAbsolute(artifact.path) ||
    !Number.isInteger(artifact.bytes) ||
    artifact.bytes < 33 ||
    artifact.bytes > MAX_COMPRESSED_BYTES ||
    !SHA256.test(artifact.sha256 ?? '') ||
    !Number.isInteger(artifact.widthPx) ||
    !Number.isInteger(artifact.heightPx) ||
    artifact.widthPx < 1 ||
    artifact.heightPx < 1 ||
    artifact.widthPx * artifact.heightPx > MAX_PIXELS
  ) {
    throw new Error('EVIDENCE');
  }
  const path = resolve(repository, artifact.path);
  if (!contained(repository, path)) throw new Error('EVIDENCE');
  let current = resolve(repository);
  for (const segment of relative(repository, path).split(sep).filter(Boolean)) {
    current = resolve(current, segment);
    const metadata = await lstat(current);
    if (metadata.isSymbolicLink()) throw new Error('EVIDENCE');
  }
  const metadata = await lstat(path);
  if (!metadata.isFile() || metadata.isSymbolicLink() || metadata.size !== artifact.bytes) {
    throw new Error('EVIDENCE');
  }
  const bytes = await readFile(path);
  if (bytes.length !== artifact.bytes || sha256(bytes) !== artifact.sha256) {
    throw new Error('EVIDENCE');
  }
  return {
    mediaType: 'image/png',
    encoding: 'base64',
    data: bytes.toString('base64'),
    bytes: bytes.length,
    sha256: artifact.sha256,
    widthPx: artifact.widthPx,
    heightPx: artifact.heightPx,
  };
}

function semanticEvidenceAccepted(semanticComparison, preview, previewEvidence, layoutSchema) {
  if (
    !isObject(semanticComparison) ||
    semanticComparison.status !== 'passed' ||
    validateSchemaValue(semanticComparison, layoutSchema).length > 0 ||
    fingerprintWithout(semanticComparison, 'comparisonFingerprint') !==
      semanticComparison.comparisonFingerprint ||
    semanticComparison.render.requestFingerprint !==
      preview.generatedPreview.requestFingerprint ||
    semanticComparison.render.outputFingerprint !== previewEvidence.outputFingerprint ||
    semanticComparison.render.renderTreeFingerprint !== preview.renderTree.sha256 ||
    semanticComparison.render.viewport.widthPx !== preview.image.widthPx ||
    semanticComparison.render.viewport.heightPx !== preview.image.heightPx ||
    semanticComparison.render.density !== preview.configuration.density ||
    semanticComparison.render.fontScale !== preview.configuration.fontScale ||
    semanticComparison.render.localeTag !== preview.configuration.localeTags[0] ||
    semanticComparison.render.layoutDirection !== preview.configuration.layoutDirection
  ) {
    return false;
  }
  return true;
}

function rounded(value) {
  return Number(value.toFixed(12));
}

function comparePixels(referencePixels, renderPixels, signal) {
  if (referencePixels.length !== renderPixels.length || referencePixels.length % 4 !== 0) {
    throw new Error('DIMENSION');
  }
  const totalPixels = referencePixels.length / 4;
  if (totalPixels < 1 || totalPixels > MAX_PIXELS) throw new Error('LIMIT');
  let mismatchedPixels = 0;
  let absoluteError = 0;
  let squaredError = 0;
  let maxChannelDelta = 0;
  for (let offset = 0; offset < referencePixels.length; offset += 4) {
    if ((offset & 0x3fff) === 0 && signal?.aborted) throw new Error('CANCELLED');
    let mismatch = false;
    for (let channel = 0; channel < 4; channel += 1) {
      const delta = Math.abs(referencePixels[offset + channel] - renderPixels[offset + channel]);
      if (delta !== 0) mismatch = true;
      absoluteError += delta;
      squaredError += delta * delta;
      if (delta > maxChannelDelta) maxChannelDelta = delta;
    }
    if (mismatch) mismatchedPixels += 1;
  }
  const channels = totalPixels * 4;
  return {
    totalPixels,
    comparedPixels: totalPixels,
    mismatchedPixels,
    exactPixelRatio: rounded((totalPixels - mismatchedPixels) / totalPixels),
    meanAbsoluteErrorRgba: rounded(absoluteError / channels),
    rootMeanSquareErrorRgba: rounded(Math.sqrt(squaredError / channels)),
    maxChannelDelta,
  };
}

export async function compareScreenshotPixels({
  referenceRequest,
  referenceResult,
  semanticComparison,
  preview,
  previewEvidence,
} = {}, {
  repository = repositoryRoot(),
  signal,
  prepare = prepareScreenshot,
} = {}) {
  const [pixelSchema, layoutSchema] = await loadSchemas();
  if (signal?.aborted) {
    return oneFailure(
      'VC-AI-PIXEL-CANCELLED',
      'Screenshot pixel comparison was cancelled before reference evidence was accepted.',
      'Retry the same immutable comparison when pixel evidence is still required.',
      'cancelled',
    );
  }
  if (
    validateSchemaValue(referenceRequest, SCREENSHOT_PREPROCESSING_SCHEMA).length > 0 ||
    validateSchemaValue(referenceResult, SCREENSHOT_PREPROCESSING_SCHEMA).length > 0 ||
    !isObject(preview) ||
    !isObject(previewEvidence) ||
    previewEvidence.level !== 'rendered' ||
    !SHA256.test(previewEvidence.outputFingerprint ?? '') ||
    !isObject(preview.generatedPreview) ||
    !SHA256.test(preview.generatedPreview.requestFingerprint ?? '') ||
    !SHA256.test(preview.generatedPreview.pngSha256 ?? '') ||
    !SHA256.test(preview.generatedPreview.renderTreeSha256 ?? '') ||
    !isObject(preview.image) ||
    preview.generatedPreview.pngSha256 !== preview.image?.sha256 ||
    preview.generatedPreview.renderTreeSha256 !== preview.renderTree?.sha256 ||
    !isObject(preview.renderTree) ||
    !SHA256.test(preview.renderTree.sha256 ?? '') ||
    !isObject(preview.configuration) ||
    typeof preview.configuration.density !== 'number' ||
    typeof preview.configuration.fontScale !== 'number' ||
    !Array.isArray(preview.configuration.localeTags) ||
    preview.configuration.localeTags.length !== 1 ||
    !['Ltr', 'Rtl'].includes(preview.configuration.layoutDirection)
  ) {
    return oneFailure(
      'VC-AI-PIXEL-INPUT-INVALID',
      'Screenshot pixel comparison input is incomplete or violates a frozen schema.',
      'Use one canonical preprocessing pair and evidence returned by the same screenshot render.',
      'invalid',
    );
  }
  const reproduced = await prepare(referenceRequest, {
    requestId: 'compare-screenshot-pixels-reference',
    signal,
  });
  if (reproduced.status === 'cancelled' || signal?.aborted) {
    return oneFailure(
      'VC-AI-PIXEL-CANCELLED',
      'Screenshot pixel comparison was cancelled while reproducing reference evidence.',
      'Retry the same immutable comparison when pixel evidence is still required.',
      'cancelled',
    );
  }
  if (
    reproduced.status !== 'success' ||
    canonicalJson(reproduced.data) !== canonicalJson(referenceResult)
  ) {
    return oneFailure(
      'VC-AI-PIXEL-REFERENCE-INTEGRITY-MISMATCH',
      'The screenshot reference request and canonical preprocessing result do not reproduce exactly.',
      'Regenerate the immutable preprocessing result from the reviewed reference request.',
    );
  }
  if (!semanticEvidenceAccepted(semanticComparison, preview, previewEvidence, layoutSchema)) {
    return oneFailure(
      'VC-AI-PIXEL-SEMANTIC-EVIDENCE-REQUIRED',
      'Pixel comparison requires a passing semantic and structural result from the same render.',
      'Pass the exact accepted layout comparison before requesting pixel evidence.',
    );
  }

  const eligibilityFindings = [];
  const expectedConfiguration = exactConfiguration(referenceRequest, preview);
  if (
    canonicalJson(referenceRequest.interpretation) !== canonicalJson(expectedConfiguration) ||
    referenceResult.output.widthPx !== preview.image?.widthPx ||
    referenceResult.output.heightPx !== preview.image?.heightPx
  ) {
    eligibilityFindings.push({
      code: 'VC-AI-PIXEL-CONFIGURATION-MISMATCH',
      message: 'Reference viewport or interpretation does not exactly match the accepted render.',
      nextAction: 'Use a full-viewport reference with the same dimensions and device configuration.',
    });
  }
  if (
    referenceRequest.privacy.redactions.length !== 0 ||
    referenceResult.privacy.redactionsApplied !== 0
  ) {
    eligibilityFindings.push({
      code: 'VC-AI-PIXEL-REDACTION-UNSUPPORTED',
      message: 'A redacted screenshot cannot enter exact full-viewport pixel comparison.',
      nextAction: 'Use semantic checks only, or provide a reviewed unredacted pixel reference.',
    });
  }
  if (eligibilityFindings.length > 0) return pixelFailure(eligibilityFindings, 'unsupported');

  let referencePixels;
  let renderAsset;
  let renderPixels;
  try {
    referencePixels = decodeScreenshotPng(referenceResult.output, {signal});
  } catch {
    if (signal?.aborted) {
      return oneFailure(
        'VC-AI-PIXEL-CANCELLED',
        'Screenshot pixel comparison was cancelled while decoding reference RGBA pixels.',
        'Retry the same immutable comparison when pixel evidence is still required.',
        'cancelled',
      );
    }
    return oneFailure(
      'VC-AI-PIXEL-REFERENCE-INTEGRITY-MISMATCH',
      'The canonical reference PNG failed strict bounded RGBA decoding.',
      'Reproduce the reference with screenshot preprocessing v1.',
    );
  }
  try {
    renderAsset = await readImageArtifact(repository, preview.image);
    renderPixels = decodeScreenshotPng(renderAsset, {signal});
  } catch {
    if (signal?.aborted) {
      return oneFailure(
        'VC-AI-PIXEL-CANCELLED',
        'Screenshot pixel comparison was cancelled while reading rendered RGBA evidence.',
        'Retry the same immutable comparison when pixel evidence is still required.',
        'cancelled',
      );
    }
    return oneFailure(
      'VC-AI-PIXEL-IMAGE-INTEGRITY-MISMATCH',
      'The accepted render PNG failed containment, identity, or strict bounded RGBA decoding.',
      'Reject the comparison and regenerate the content-addressed Preview evidence.',
    );
  }

  let metrics;
  try {
    metrics = comparePixels(referencePixels, renderPixels, signal);
  } catch (error) {
    if (error.message === 'CANCELLED') {
      return oneFailure(
        'VC-AI-PIXEL-CANCELLED',
        'Screenshot pixel comparison was cancelled during bounded RGBA comparison.',
        'Retry the same immutable comparison when pixel evidence is still required.',
        'cancelled',
      );
    }
    return oneFailure(
      error.message === 'LIMIT' ? 'VC-AI-PIXEL-LIMIT' : 'VC-AI-PIXEL-DIMENSION-MISMATCH',
      'Reference and render RGBA buffers cannot be compared within the frozen limits.',
      'Use equal bounded dimensions and regenerate both immutable PNG identities.',
      error.message === 'LIMIT' ? 'limited' : 'failed',
    );
  }

  const findings = metrics.mismatchedPixels === 0 ? [] : [{
    code: 'VC-AI-PIXEL-MISMATCH',
    severity: 'error',
    category: 'pixel',
    message: `${metrics.mismatchedPixels} of ${metrics.totalPixels} pixels differ at zero tolerance.`,
    nextAction: 'Inspect separate semantic, style, typography, asset, and geometry causes before repair.',
  }];
  const comparison = {
    schemaVersion: 1,
    status: findings.length === 0 ? 'passed' : 'failed',
    reference: {
      requestFingerprint: referenceResult.requestFingerprint,
      outputFingerprint: referenceResult.outputFingerprint,
      pngFingerprint: referenceResult.output.sha256,
      widthPx: referenceResult.output.widthPx,
      heightPx: referenceResult.output.heightPx,
      configuration: referenceRequest.interpretation,
    },
    render: {
      semanticComparisonFingerprint: semanticComparison.comparisonFingerprint,
      requestFingerprint: preview.generatedPreview.requestFingerprint,
      outputFingerprint: previewEvidence.outputFingerprint,
      pngFingerprint: renderAsset.sha256,
      widthPx: preview.image.widthPx,
      heightPx: preview.image.heightPx,
      configuration: expectedConfiguration,
    },
    policy: {...POLICY},
    metrics,
    findings,
  };
  comparison.comparisonFingerprint = compactFingerprint(comparison);
  const violations = validateSchemaValue(comparison, pixelSchema);
  if (violations.length > 0) {
    return oneFailure(
      'VC-AI-PIXEL-INPUT-INVALID',
      `Screenshot pixel comparison result violates schema v1: ${violations.slice(0, 3).join('; ')}`,
      'Repair the deterministic pixel comparator before accepting its output.',
    );
  }
  return {
    status: comparison.status === 'passed' ? 'success' : 'failed',
    evidenceLevel: comparison.status === 'passed' ? 'compared' : 'rendered',
    diagnostics: findings.map((finding) => diagnostic(finding)),
    comparison,
  };
}
