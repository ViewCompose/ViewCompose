import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {applyDesignIrRepairPatch} from './design-ir-repair-patch.mjs';
import {
  fingerprintRepairValue,
  sealRepairEvaluation,
  validateRepairPatch,
} from './repair-orchestrator.mjs';
import {proposeScreenshotRepair} from './screenshot-repair-proposer.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {canonicalJson} from './screenshot-contract.mjs';

const resolutionResult = JSON.parse(await readFile(new URL(
  '../evaluation/fixtures/visual/screenshot-resolution/wireframe.result.json',
  import.meta.url,
), 'utf8'));
const proposalSchema = JSON.parse(await readFile(new URL(
  '../contracts/screenshot-repair-proposal.schema.json',
  import.meta.url,
), 'utf8'));
const BASE_RESOLUTION = resolutionResult.resultFingerprint;
const INPUT_DESIGN_IR = resolutionResult.designIrFingerprint;
const SHA = Object.freeze({
  compilation: '1'.repeat(64),
  render: '2'.repeat(64),
  request: '3'.repeat(64),
  output: '4'.repeat(64),
  png: '5'.repeat(64),
  renderRequest: '6'.repeat(64),
  renderOutput: '7'.repeat(64),
  renderPng: '8'.repeat(64),
  renderTree: '9'.repeat(64),
  safety: 'a'.repeat(64),
});
const GATES = Object.freeze([
  'safety',
  'compilation',
  'render',
  'semantics',
  'structure',
  'exact-pixels',
]);
const CONFIGURATION = Object.freeze({
  density: 1,
  fontScale: 1,
  localeTag: 'en-US',
  layoutDirection: 'Ltr',
  colorSpace: 'sRGB',
  alphaMode: 'straight',
  orientation: 'upright',
  systemBars: {leftPx: 0, topPx: 0, rightPx: 0, bottomPx: 0},
  crop: {x: 0, y: 0, width: 10, height: 10},
});
const BOUNDS = Object.freeze({
  'wireframe-root': {left: 0, top: 0, right: 10, bottom: 10},
  'wireframe-title': {left: 0, top: 0, right: 5, bottom: 5},
  'wireframe-field': {left: 0, top: 5, right: 5, bottom: 8},
  'wireframe-button': {left: 5, top: 5, right: 10, bottom: 10},
});

function compactFingerprint(value) {
  return createHash('sha256').update(JSON.stringify(value)).digest('hex');
}

function compactSeal(value, key) {
  const result = structuredClone(value);
  result[key] = compactFingerprint(result);
  return result;
}

function designIndex(designIr) {
  const entries = [];
  const visit = (node, path) => {
    entries.push({node, path});
    node.children.forEach((child) => visit(child, [...path, child.id]));
  };
  designIr.roots.forEach((root) => visit(root, [root.id]));
  return entries;
}

function withText(designIr, nodeId, value) {
  const changed = structuredClone(designIr);
  const entry = designIndex(changed).find((item) => item.node.id === nodeId);
  entry.node.properties.find((field) => field.name === 'text').value.value = value;
  return changed;
}

function layoutComparison(designIr) {
  const entries = designIndex(designIr);
  const nodes = entries.map(({node, path}) => ({
    designNodeId: node.id,
    designPath: path,
    identityKey: node.id,
    identityRenderNodeId: `render:${node.id}`,
    semanticRenderNodeId: `render:${node.id}`,
    expectedKind: node.kind,
    actualKind: node.kind,
    wrapperDepth: 0,
    bounds: BOUNDS[node.id],
    checks: [
      {
        id: `semantic:${node.id}`,
        category: 'semantic',
        status: 'passed',
        expected: 'matched',
        actual: 'matched',
      },
      {
        id: `geometry:${node.id}`,
        category: 'geometry',
        status: 'passed',
        expected: 'matched',
        actual: 'matched',
      },
    ],
  }));
  return compactSeal({
    schemaVersion: 1,
    status: 'passed',
    designIr: {
      documentId: designIr.documentId,
      sourceFingerprint: designIr.source.fingerprint,
      irFingerprint: compactFingerprint(designIr),
    },
    render: {
      requestFingerprint: SHA.renderRequest,
      outputFingerprint: SHA.renderOutput,
      renderTreeFingerprint: SHA.renderTree,
      viewport: {widthPx: 10, heightPx: 10},
      density: 1,
      fontScale: 1,
      localeTag: 'en-US',
      layoutDirection: 'Ltr',
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
      designNodes: nodes.length,
      mappedNodes: nodes.length,
      requiredChecks: nodes.length * 2,
      passedChecks: nodes.length * 2,
      failedChecks: 0,
      notApplicableChecks: 0,
    },
    nodes,
    findings: [],
  }, 'comparisonFingerprint');
}

function pixelEvidence(layout, mismatchedPixels, attributionNodeId, referenceRequest = SHA.request) {
  const exact = mismatchedPixels === 0;
  const comparison = compactSeal({
    schemaVersion: 1,
    status: exact ? 'passed' : 'failed',
    reference: {
      requestFingerprint: referenceRequest,
      outputFingerprint: SHA.output,
      pngFingerprint: SHA.png,
      widthPx: 10,
      heightPx: 10,
      configuration: structuredClone(CONFIGURATION),
    },
    render: {
      semanticComparisonFingerprint: layout.comparisonFingerprint,
      requestFingerprint: SHA.renderRequest,
      outputFingerprint: SHA.renderOutput,
      pngFingerprint: SHA.renderPng,
      widthPx: 10,
      heightPx: 10,
      configuration: structuredClone(CONFIGURATION),
    },
    policy: {
      version: 1,
      colorSpace: 'sRGB',
      alphaMode: 'straight',
      coordinateSpace: 'exact-cropped-viewport',
      redactions: 'none',
      semanticEvidenceRequired: true,
      dimensionTolerancePx: 0,
      channelTolerance: 0,
      aggregateScore: false,
    },
    metrics: {
      totalPixels: 100,
      comparedPixels: 100,
      mismatchedPixels,
      exactPixelRatio: exact ? 1 : (100 - mismatchedPixels) / 100,
      meanAbsoluteErrorRgba: exact ? 0 : 0.1,
      rootMeanSquareErrorRgba: exact ? 0 : 0.2,
      maxChannelDelta: exact ? 0 : 7,
    },
    findings: exact ? [] : [{
      code: 'VC-AI-PIXEL-MISMATCH',
      severity: 'error',
      category: 'pixel',
      message: 'Synthetic exact-pixel mismatch.',
      nextAction: 'Inspect the localized mismatch.',
    }],
  }, 'comparisonFingerprint');
  const layoutNode = layout.nodes.find((node) => node.designNodeId === attributionNodeId);
  const attributions = exact || !layoutNode ? [] : [{
    designNodeId: attributionNodeId,
    designPath: layoutNode.designPath,
    nodeBounds: {
      x: layoutNode.bounds.left,
      y: layoutNode.bounds.top,
      width: layoutNode.bounds.right - layoutNode.bounds.left,
      height: layoutNode.bounds.bottom - layoutNode.bounds.top,
    },
    mismatchedPixels,
    mismatchBounds: {x: 1, y: 1, width: 2, height: 2},
  }];
  const localization = {
    schemaVersion: 1,
    status: exact ? 'exact' : 'mismatch',
    pixelComparisonFingerprint: comparison.comparisonFingerprint,
    viewport: {widthPx: 10, heightPx: 10},
    policy: {
      version: 1,
      ownership: 'deepest-containing-design-node',
      bounds: 'left-top-inclusive-right-bottom-exclusive',
      tieBreak: 'deepest-path-then-design-node-id',
      unassigned: 'retained-separately',
      aggregateScore: false,
    },
    mismatchedPixels,
    mismatchBounds: exact ? null : {x: 1, y: 1, width: 2, height: 2},
    attributions,
    unassignedMismatchedPixels: exact || layoutNode ? 0 : mismatchedPixels,
  };
  localization.localizationFingerprint = fingerprintRepairValue(localization);
  return {comparison, localization};
}

function evaluation(designIr, layout, pixels, {earlierFailure = false} = {}) {
  const designIrFingerprint = fingerprintRepairValue(designIr);
  const standard = (name, status, fingerprint, totalChecks = 1) => ({
    name,
    status,
    passedChecks: status === 'passed' ? totalChecks : 0,
    totalChecks: status === 'not-run' ? 0 : totalChecks,
    evidenceFingerprint: fingerprint,
  });
  const gates = earlierFailure ? [
    standard('safety', 'passed', SHA.safety),
    standard('compilation', 'failed', SHA.compilation),
    standard('render', 'not-run', '0'.repeat(64)),
    standard('semantics', 'not-run', '0'.repeat(64)),
    standard('structure', 'not-run', '0'.repeat(64)),
    {
      name: 'exact-pixels',
      status: 'not-run',
      comparedPixels: 0,
      mismatchedPixels: 0,
      maxChannelDelta: 0,
      evidenceFingerprint: '0'.repeat(64),
    },
  ] : [
    standard('safety', 'passed', SHA.safety),
    standard('compilation', 'passed', SHA.compilation),
    standard('render', 'passed', SHA.render),
    standard('semantics', 'passed', layout.comparisonFingerprint, layout.nodes.length),
    standard('structure', 'passed', layout.comparisonFingerprint, layout.nodes.length),
    {
      name: 'exact-pixels',
      status: pixels.comparison.status === 'passed' ? 'passed' : 'failed',
      comparedPixels: pixels.comparison.metrics.comparedPixels,
      mismatchedPixels: pixels.comparison.metrics.mismatchedPixels,
      maxChannelDelta: pixels.comparison.metrics.maxChannelDelta,
      evidenceFingerprint: pixels.comparison.comparisonFingerprint,
    },
  ];
  return sealRepairEvaluation({
    candidateFingerprint: fingerprintRepairValue({designIrFingerprint, gates}),
    designIrFingerprint,
    gates,
  });
}

function evidence({
  designIr = resolutionResult.designIr,
  mismatchedPixels = 0,
  attributionNodeId = 'wireframe-title',
  earlierFailure = false,
  baseResolutionResultFingerprint = BASE_RESOLUTION,
  inputDesignIrFingerprint = INPUT_DESIGN_IR,
  referenceRequest = SHA.request,
} = {}) {
  const layout = earlierFailure ? null : layoutComparison(designIr);
  const pixels = earlierFailure ? null : pixelEvidence(
    layout,
    mismatchedPixels,
    attributionNodeId,
    referenceRequest,
  );
  const candidateEvaluation = evaluation(designIr, layout, pixels, {earlierFailure});
  const value = {
    schemaVersion: 1,
    status: earlierFailure ? 'short-circuited' : 'complete',
    lineage: {
      baseResolutionResultFingerprint,
      candidateResolutionResultFingerprint: fingerprintRepairValue({
        designIrFingerprint: candidateEvaluation.designIrFingerprint,
      }),
      inputDesignIrFingerprint,
      candidateDesignIrFingerprint: candidateEvaluation.designIrFingerprint,
      changeFingerprint: mismatchedPixels === 0 ? null : 'b'.repeat(64),
    },
    candidateEvaluation,
    designIr: structuredClone(designIr),
    diagnostics: GATES.map((gate) => ({
      gate,
      codes: gate === 'compilation' && earlierFailure ? ['VC-AI-COMPILER-ERROR'] : [],
    })),
    layoutComparison: layout,
    pixelComparison: pixels?.comparison ?? null,
    pixelLocalization: pixels?.localization ?? null,
  };
  value.evidenceFingerprint = fingerprintRepairValue(value);
  return value;
}

function resealEvidence(value) {
  const result = structuredClone(value);
  delete result.evidenceFingerprint;
  result.evidenceFingerprint = fingerprintRepairValue(result);
  return result;
}

const baseline = evidence();
const changedDesignIr = withText(resolutionResult.designIr, 'wireframe-title', 'Hello');
const candidate = evidence({designIr: changedDesignIr, mismatchedPixels: 10});

test('proposes only the baseline value for one localized properties regression', async () => {
  const first = await proposeScreenshotRepair({baselineEvidence: baseline, candidateEvidence: candidate});
  const second = await proposeScreenshotRepair({baselineEvidence: baseline, candidateEvidence: candidate});
  assert.deepEqual(first, second);
  assert.equal(first.status, 'proposed');
  assert.equal(first.reason, 'strict-pixel-regression-rollback');
  assert.equal(first.target.nodeId, 'wireframe-title');
  assert.equal(first.target.name, 'text');
  assert.equal(first.target.attributedMismatchedPixels, 10);
  assert.deepEqual(first.patch.operations, [{
    op: 'replace-field',
    nodeId: 'wireframe-title',
    collection: 'properties',
    name: 'text',
    value: {kind: 'literal', value: 'Welcome'},
  }]);
  assert.equal(await validateRepairPatch(first.patch), true);
  const applied = await applyDesignIrRepairPatch({
    designIr: candidate.designIr,
    expectedDesignIrFingerprint: candidate.lineage.candidateDesignIrFingerprint,
    patch: first.patch,
  });
  assert.equal(canonicalJson(applied.designIr), canonicalJson(baseline.designIr));
  assert.equal(first.proposalFingerprint, fingerprintRepairValue((() => {
    const copy = structuredClone(first);
    delete copy.proposalFingerprint;
    return copy;
  })()));
  assert.deepEqual(validateSchemaValue(first, proposalSchema), []);
});

test('declines an already exact candidate and an earlier-gate failure', async () => {
  const exact = await proposeScreenshotRepair({
    baselineEvidence: baseline,
    candidateEvidence: baseline,
  });
  assert.equal(exact.status, 'no-eligible-change');
  assert.equal(exact.reason, 'candidate-already-exact');
  const shortCircuited = evidence({
    designIr: changedDesignIr,
    mismatchedPixels: 10,
    earlierFailure: true,
  });
  const earlier = await proposeScreenshotRepair({
    baselineEvidence: baseline,
    candidateEvidence: shortCircuited,
  });
  assert.equal(earlier.status, 'no-eligible-change');
  assert.equal(earlier.reason, 'earlier-gate-failed');
});

test('requires a strictly better baseline on the same exact-pixel denominator', async () => {
  const notBetter = evidence({designIr: resolutionResult.designIr, mismatchedPixels: 10});
  const result = await proposeScreenshotRepair({
    baselineEvidence: notBetter,
    candidateEvidence: candidate,
  });
  assert.equal(result.status, 'no-eligible-change');
  assert.equal(result.reason, 'baseline-not-strictly-better');
});

test('declines multiple field changes or a field outside localized ownership', async () => {
  const twoChanges = withText(changedDesignIr, 'wireframe-button', 'Submit');
  const multiple = await proposeScreenshotRepair({
    baselineEvidence: baseline,
    candidateEvidence: evidence({designIr: twoChanges, mismatchedPixels: 10}),
  });
  assert.equal(multiple.reason, 'no-single-localized-property-difference');
  const notLocalized = await proposeScreenshotRepair({
    baselineEvidence: baseline,
    candidateEvidence: evidence({
      designIr: changedDesignIr,
      mismatchedPixels: 10,
      attributionNodeId: 'wireframe-root',
    }),
  });
  assert.equal(notLocalized.reason, 'no-single-localized-property-difference');
});

test('does not invent a value when localization has no baseline property difference', async () => {
  const changedBaseline = evidence({designIr: changedDesignIr});
  const result = await proposeScreenshotRepair({
    baselineEvidence: changedBaseline,
    candidateEvidence: candidate,
  });
  assert.equal(result.status, 'no-eligible-change');
  assert.equal(result.reason, 'no-single-localized-property-difference');
  assert.equal(result.patch, null);
});

test('fails closed on evidence integrity, lineage, or reference identity changes', async () => {
  const corrupted = structuredClone(candidate);
  corrupted.pixelLocalization.attributions[0].mismatchedPixels = 9;
  const integrity = await proposeScreenshotRepair({
    baselineEvidence: baseline,
    candidateEvidence: corrupted,
  });
  assert.equal(integrity.status, 'invalid');
  assert.equal(integrity.reason, 'input-invalid');

  const changedLineage = resealEvidence({
    ...structuredClone(candidate),
    lineage: {
      ...candidate.lineage,
      baseResolutionResultFingerprint: 'c'.repeat(64),
    },
  });
  const lineage = await proposeScreenshotRepair({
    baselineEvidence: baseline,
    candidateEvidence: changedLineage,
  });
  assert.equal(lineage.status, 'invalid');
  assert.equal(lineage.reason, 'evidence-lineage-mismatch');

  const otherReference = evidence({
    designIr: changedDesignIr,
    mismatchedPixels: 10,
    referenceRequest: 'd'.repeat(64),
  });
  const reference = await proposeScreenshotRepair({
    baselineEvidence: baseline,
    candidateEvidence: otherReference,
  });
  assert.equal(reference.status, 'invalid');
  assert.equal(reference.reason, 'evidence-lineage-mismatch');
});

test('cancels before validation and emits no patch', async () => {
  const controller = new AbortController();
  controller.abort();
  const result = await proposeScreenshotRepair({
    baselineEvidence: {malformed: true},
    candidateEvidence: {malformed: true},
  }, {signal: controller.signal});
  assert.equal(result.status, 'cancelled');
  assert.equal(result.reason, 'cancelled');
  assert.equal(result.patch, null);
});
