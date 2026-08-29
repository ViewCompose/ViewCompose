import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdtemp, mkdir, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {compareScreenshotPixels} from './pixel-comparator.mjs';
import {decodeScreenshotPng, encodeScreenshotPng} from './screenshot-preprocessor.mjs';

const fixtureRoot = new URL('../evaluation/fixtures/visual/screenshot-pixel/', import.meta.url);

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

async function readJson(url) {
  return JSON.parse(await readFile(url, 'utf8'));
}

function semanticComparison(preview, evidence) {
  const result = {
    schemaVersion: 1,
    status: 'passed',
    designIr: {
      documentId: 'pixel-test',
      sourceFingerprint: '1'.repeat(64),
      irFingerprint: '2'.repeat(64),
    },
    render: {
      requestFingerprint: preview.generatedPreview.requestFingerprint,
      outputFingerprint: evidence.outputFingerprint,
      renderTreeFingerprint: preview.renderTree.sha256,
      viewport: {widthPx: preview.image.widthPx, heightPx: preview.image.heightPx},
      density: preview.configuration.density,
      fontScale: preview.configuration.fontScale,
      localeTag: preview.configuration.localeTags[0],
      layoutDirection: preview.configuration.layoutDirection,
    },
    policy: {
      version: 1,
      nodeIdentity: 'exact-normalized-key',
      semanticHost: 'identity-or-allowlisted-single-child-wrapper',
      resourceValues: 'exact-preview-binding-source',
      dpRounding: 'nearest-integer-px',
      geometryTolerancePx: 0,
      hiddenGeometry: 'not-applicable-only-for-gone',
    },
    summary: {
      designNodes: 1,
      mappedNodes: 1,
      requiredChecks: 1,
      passedChecks: 1,
      failedChecks: 0,
      notApplicableChecks: 0,
    },
    nodes: [{
      designNodeId: 'pixel-root',
      designPath: ['pixel-root'],
      identityKey: 'pixel-root',
      identityRenderNodeId: 'pixel-root',
      semanticRenderNodeId: 'pixel-root',
      expectedKind: 'column',
      actualKind: 'column',
      wrapperDepth: 0,
      bounds: {left: 0, top: 0, right: preview.image.widthPx, bottom: preview.image.heightPx},
      checks: [{
        id: 'identity.key',
        category: 'identity',
        status: 'passed',
        expected: 'pixel-root',
        actual: 'pixel-root',
      }],
    }],
    findings: [],
  };
  result.comparisonFingerprint = sha256(JSON.stringify(result));
  return result;
}

async function testInput(repository, imageBytes) {
  const imagePath = resolve(repository, 'artifacts/render.png');
  await mkdir(resolve(repository, 'artifacts'), {recursive: true});
  await writeFile(imagePath, imageBytes);
  const referenceRequest = await readJson(new URL('pixel-reference.request.json', fixtureRoot));
  const referenceResult = await readJson(new URL('pixel-reference.result.json', fixtureRoot));
  const imageFingerprint = sha256(imageBytes);
  const outputFingerprint = sha256(`render:${imageFingerprint}`);
  const preview = {
    image: {
      path: 'artifacts/render.png',
      mediaType: 'image/png',
      bytes: imageBytes.length,
      sha256: imageFingerprint,
      widthPx: 1079,
      heightPx: 2339,
    },
    renderTree: {sha256: '3'.repeat(64)},
    generatedPreview: {
      requestFingerprint: '4'.repeat(64),
      pngSha256: imageFingerprint,
      renderTreeSha256: '3'.repeat(64),
    },
    configuration: {
      density: 2.625,
      fontScale: 1,
      localeTags: ['en-US'],
      layoutDirection: 'Ltr',
    },
  };
  const previewEvidence = {level: 'rendered', outputFingerprint};
  return {
    referenceRequest,
    referenceResult,
    preview,
    previewEvidence,
    semanticComparison: semanticComparison(preview, previewEvidence),
  };
}

test('compares every eligible RGBA pixel with separate exact metrics', async () => {
  const repository = await mkdtemp(resolve(tmpdir(), 'viewcompose-pixel-comparator-'));
  try {
    const referenceRequest = await readJson(new URL('pixel-reference.request.json', fixtureRoot));
    const imageBytes = Buffer.from(referenceRequest.screenshot.data, 'base64');
    const input = await testInput(repository, imageBytes);
    const first = await compareScreenshotPixels(input, {repository});
    const second = await compareScreenshotPixels(input, {repository});
    assert.equal(first.status, 'success');
    assert.equal(first.evidenceLevel, 'compared');
    assert.deepEqual(first, second);
    assert.deepEqual(first.comparison.metrics, {
      totalPixels: 2523781,
      comparedPixels: 2523781,
      mismatchedPixels: 0,
      exactPixelRatio: 1,
      meanAbsoluteErrorRgba: 0,
      rootMeanSquareErrorRgba: 0,
      maxChannelDelta: 0,
    });
  } finally {
    await rm(repository, {recursive: true, force: true});
  }
});

test('reports a one-pixel RGBA mismatch without an aggregate score', async () => {
  const repository = await mkdtemp(resolve(tmpdir(), 'viewcompose-pixel-mismatch-'));
  try {
    const referenceRequest = await readJson(new URL('pixel-reference.request.json', fixtureRoot));
    const pixels = decodeScreenshotPng(referenceRequest.screenshot);
    pixels[0] ^= 1;
    const imageBytes = encodeScreenshotPng(pixels, 1079, 2339);
    const input = await testInput(repository, imageBytes);
    const result = await compareScreenshotPixels(input, {repository});
    assert.equal(result.status, 'failed');
    assert.equal(result.evidenceLevel, 'rendered');
    assert.equal(result.diagnostics[0].code, 'VC-AI-PIXEL-MISMATCH');
    assert.equal(result.comparison.metrics.mismatchedPixels, 1);
    assert.equal(result.comparison.metrics.maxChannelDelta, 1);
    assert.equal(Object.hasOwn(result.comparison.metrics, 'score'), false);
  } finally {
    await rm(repository, {recursive: true, force: true});
  }
});

test('rejects missing semantic evidence and a changed canonical reference', async () => {
  const repository = await mkdtemp(resolve(tmpdir(), 'viewcompose-pixel-integrity-'));
  try {
    const referenceRequest = await readJson(new URL('pixel-reference.request.json', fixtureRoot));
    const imageBytes = Buffer.from(referenceRequest.screenshot.data, 'base64');
    const input = await testInput(repository, imageBytes);
    const missingSemantic = await compareScreenshotPixels({
      ...input,
      semanticComparison: undefined,
    }, {repository});
    assert.equal(missingSemantic.diagnostics[0].code, 'VC-AI-PIXEL-SEMANTIC-EVIDENCE-REQUIRED');

    const changed = structuredClone(input);
    changed.referenceResult.output.sha256 = '0'.repeat(64);
    const integrity = await compareScreenshotPixels(changed, {repository});
    assert.equal(integrity.diagnostics[0].code, 'VC-AI-PIXEL-REFERENCE-INTEGRITY-MISMATCH');
  } finally {
    await rm(repository, {recursive: true, force: true});
  }
});

test('keeps the original redacted wireframe ineligible for pixel scoring', async () => {
  const repository = await mkdtemp(resolve(tmpdir(), 'viewcompose-pixel-ineligible-'));
  try {
    const pixelRequest = await readJson(new URL('pixel-reference.request.json', fixtureRoot));
    const imageBytes = Buffer.from(pixelRequest.screenshot.data, 'base64');
    const input = await testInput(repository, imageBytes);
    input.referenceRequest = await readJson(new URL(
      '../screenshot/inference-wireframe.request.json',
      fixtureRoot,
    ));
    input.referenceResult = await readJson(new URL(
      '../screenshot/inference-wireframe.result.json',
      fixtureRoot,
    ));
    const result = await compareScreenshotPixels(input, {repository});
    assert.equal(result.status, 'unsupported');
    assert.deepEqual(result.diagnostics.map((item) => item.code), [
      'VC-AI-PIXEL-CONFIGURATION-MISMATCH',
      'VC-AI-PIXEL-REDACTION-UNSUPPORTED',
    ]);
    assert.equal(result.comparison, undefined);
  } finally {
    await rm(repository, {recursive: true, force: true});
  }
});

test('honors cancellation before decoding pixel evidence', async () => {
  const controller = new AbortController();
  controller.abort();
  const result = await compareScreenshotPixels({}, {signal: controller.signal});
  assert.equal(result.status, 'cancelled');
  assert.equal(result.diagnostics[0].code, 'VC-AI-PIXEL-CANCELLED');
});
