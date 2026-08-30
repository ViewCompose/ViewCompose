import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {applyDesignIrRepairPatch} from './design-ir-repair-patch.mjs';
import {
  fingerprintRepairValue,
  sealRepairPatch,
  validateRepairPatch,
} from './repair-orchestrator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {canonicalJson} from './screenshot-contract.mjs';

const contractRoot = new URL('../contracts/', import.meta.url);
const schemaPaths = Object.freeze({
  proposal: new URL('screenshot-repair-proposal.schema.json', contractRoot),
  evidence: new URL('screenshot-repair-candidate-evidence.schema.json', contractRoot),
  repair: new URL('screenshot-repair.schema.json', contractRoot),
  designIr: new URL('design-ir.schema.json', contractRoot),
  layout: new URL('layout-comparison.schema.json', contractRoot),
  pixels: new URL('screenshot-pixel-comparison.schema.json', contractRoot),
  localization: new URL('screenshot-pixel-localization.schema.json', contractRoot),
});
const SHA256 = /^[a-f0-9]{64}$/u;
const GATE_ORDER = Object.freeze([
  'safety',
  'compilation',
  'render',
  'semantics',
  'structure',
  'exact-pixels',
]);
const MAX_DESIGN_NODES = 1000;
const MAX_DEPTH = 64;
const MAX_EVIDENCE_BYTES = 16 * 1024 * 1024;
const POLICY = Object.freeze({
  version: 1,
  mode: 'single-property-regression-rollback',
  targetValueSource: 'accepted-baseline-design-ir',
  currentGate: 'exact-pixels',
  requiredPriorGates: GATE_ORDER.slice(0, 5),
  baselineImprovement: 'same-denominator-strictly-fewer-mismatched-pixels',
  localization: 'changed-node-must-be-attributed',
  maxOperations: 1,
  eligibleCollections: ['properties'],
  valueInference: false,
  providerCalls: false,
  networkAccess: false,
});

let schemasPromise;

function loadSchemas() {
  schemasPromise ??= Promise.all(
    Object.entries(schemaPaths).map(async ([name, path]) => [
      name,
      JSON.parse(await readFile(path, 'utf8')),
    ]),
  ).then(Object.fromEntries);
  return schemasPromise;
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function compactFingerprint(value) {
  return sha256(JSON.stringify(value));
}

function compactFingerprintWithout(value, key) {
  const copy = structuredClone(value);
  delete copy[key];
  return compactFingerprint(copy);
}

function suppliedEvidenceFingerprint(value) {
  return SHA256.test(value?.evidenceFingerprint ?? '') ? value.evidenceFingerprint : null;
}

function diagnostic(code, severity, message, nextAction) {
  return {code, severity, message, nextAction};
}

function sealProposal({
  status,
  reason,
  baselineEvidence,
  candidateEvidence,
  target = null,
  patch = null,
}) {
  let diagnostics = [];
  if (status === 'no-eligible-change') {
    diagnostics = [diagnostic(
      'VC-AI-REPAIR-PROPOSAL-NONE',
      'warning',
      `No bounded screenshot rollback is eligible: ${reason}.`,
      'Retain the current evidence and use an explicitly reviewed typed repair when needed.',
    )];
  } else if (status === 'invalid') {
    diagnostics = [diagnostic(
      'VC-AI-REPAIR-PROPOSAL-INPUT-INVALID',
      'error',
      `Screenshot repair proposal input is invalid: ${reason}.`,
      'Regenerate complete content-addressed candidate evidence from the same accepted input.',
    )];
  } else if (status === 'cancelled') {
    diagnostics = [diagnostic(
      'VC-AI-REPAIR-PROPOSAL-CANCELLED',
      'warning',
      'Screenshot repair proposal was cancelled before an eligible patch was emitted.',
      'Retry with the same immutable evidence if the rollback is still required.',
    )];
  }
  const result = {
    schemaVersion: 1,
    status,
    policy: structuredClone(POLICY),
    input: {
      baselineEvidenceFingerprint: suppliedEvidenceFingerprint(baselineEvidence),
      candidateEvidenceFingerprint: suppliedEvidenceFingerprint(candidateEvidence),
    },
    reason,
    target: target === null ? null : structuredClone(target),
    patch: patch === null ? null : structuredClone(patch),
    diagnostics,
  };
  result.proposalFingerprint = fingerprintRepairValue(result);
  return result;
}

function cancelled(baselineEvidence, candidateEvidence) {
  return sealProposal({
    status: 'cancelled',
    reason: 'cancelled',
    baselineEvidence,
    candidateEvidence,
  });
}

function invalid(reason, baselineEvidence, candidateEvidence) {
  return sealProposal({
    status: 'invalid',
    reason,
    baselineEvidence,
    candidateEvidence,
  });
}

function noEligible(reason, baselineEvidence, candidateEvidence) {
  return sealProposal({
    status: 'no-eligible-change',
    reason,
    baselineEvidence,
    candidateEvidence,
  });
}

function throwIfCancelled(signal) {
  if (signal?.aborted) {
    const error = new Error('Screenshot repair proposal was cancelled.');
    error.code = 'VC-AI-REPAIR-PROPOSAL-CANCELLED';
    throw error;
  }
}

function assertSchema(value, schema, rootSchema = schema) {
  if (validateSchemaValue(value, schema, rootSchema).length > 0) throw new Error('SCHEMA');
}

function validGateSequence(evaluation) {
  if (
    evaluation.gates.map((gate) => gate.name).join('\0') !== GATE_ORDER.join('\0') ||
    compactFingerprintWithout(evaluation, 'evaluationFingerprint') !==
      evaluation.evaluationFingerprint
  ) {
    return false;
  }
  let stopped = false;
  for (const gate of evaluation.gates) {
    if (stopped && gate.status !== 'not-run') return false;
    if (gate.name === 'exact-pixels') {
      if (
        (gate.status === 'passed' && (
          gate.comparedPixels < 1 || gate.mismatchedPixels !== 0 || gate.maxChannelDelta !== 0
        )) ||
        (gate.status === 'failed' && !(
          gate.comparedPixels >= 1 && gate.mismatchedPixels >= 1 && gate.maxChannelDelta >= 1
        )) ||
        (gate.status === 'not-run' && (
          gate.comparedPixels !== 0 || gate.mismatchedPixels !== 0 || gate.maxChannelDelta !== 0
        ))
      ) {
        return false;
      }
    } else if (
      (gate.status === 'passed' && (
        gate.totalChecks < 1 || gate.passedChecks !== gate.totalChecks
      )) ||
      (gate.status === 'failed' && (
        gate.totalChecks < 1 || gate.passedChecks >= gate.totalChecks
      )) ||
      (gate.status === 'not-run' && (
        gate.totalChecks !== 0 || gate.passedChecks !== 0
      ))
    ) {
      return false;
    }
    if (gate.status !== 'passed') stopped = true;
  }
  return true;
}

function indexDesignIr(designIr, signal) {
  const nodeById = new Map();
  const visit = (node, path) => {
    throwIfCancelled(signal);
    if (path.length > MAX_DEPTH || nodeById.size >= MAX_DESIGN_NODES || nodeById.has(node.id)) {
      throw new Error('DESIGN');
    }
    for (const fields of [node.properties, node.semantics, node.state]) {
      if (new Set(fields.map((field) => field.name)).size !== fields.length) {
        throw new Error('DESIGN');
      }
    }
    nodeById.set(node.id, {node, path});
    for (const child of node.children) visit(child, [...path, child.id]);
  };
  for (const root of designIr.roots) visit(root, [root.id]);
  return nodeById;
}

function clippedBounds(bounds, viewport) {
  const left = Math.max(0, Math.min(viewport.widthPx, bounds.left));
  const top = Math.max(0, Math.min(viewport.heightPx, bounds.top));
  const right = Math.max(0, Math.min(viewport.widthPx, bounds.right));
  const bottom = Math.max(0, Math.min(viewport.heightPx, bounds.bottom));
  return {x: left, y: top, width: Math.max(0, right - left), height: Math.max(0, bottom - top)};
}

function same(left, right) {
  return canonicalJson(left) === canonicalJson(right);
}

function validateFullPixelEvidence(evidence, designIndex) {
  const {candidateEvaluation: evaluation, layoutComparison, pixelComparison, pixelLocalization} =
    evidence;
  const unsignedLocalization = structuredClone(pixelLocalization);
  delete unsignedLocalization.localizationFingerprint;
  const semanticGate = evaluation.gates[3];
  const structureGate = evaluation.gates[4];
  const pixelGate = evaluation.gates[5];
  if (
    layoutComparison === null ||
    pixelComparison === null ||
    pixelLocalization === null ||
    compactFingerprintWithout(layoutComparison, 'comparisonFingerprint') !==
      layoutComparison.comparisonFingerprint ||
    compactFingerprintWithout(pixelComparison, 'comparisonFingerprint') !==
      pixelComparison.comparisonFingerprint ||
    layoutComparison.status !== 'passed' ||
    compactFingerprint(evidence.designIr) !==
      layoutComparison.designIr.irFingerprint ||
    layoutComparison.designIr.documentId !== evidence.designIr.documentId ||
    layoutComparison.designIr.sourceFingerprint !== evidence.designIr.source.fingerprint ||
    semanticGate.evidenceFingerprint !== layoutComparison.comparisonFingerprint ||
    structureGate.evidenceFingerprint !== layoutComparison.comparisonFingerprint ||
    pixelGate.evidenceFingerprint !== pixelComparison.comparisonFingerprint ||
    pixelComparison.render.semanticComparisonFingerprint !== layoutComparison.comparisonFingerprint ||
    pixelLocalization.pixelComparisonFingerprint !== pixelComparison.comparisonFingerprint ||
    fingerprintRepairValue(unsignedLocalization) !== pixelLocalization.localizationFingerprint
  ) {
    throw new Error('PIXEL-INTEGRITY');
  }
  const metrics = pixelComparison.metrics;
  if (
    metrics.totalPixels !== metrics.comparedPixels ||
    metrics.comparedPixels !== pixelGate.comparedPixels ||
    metrics.mismatchedPixels !== pixelGate.mismatchedPixels ||
    metrics.maxChannelDelta !== pixelGate.maxChannelDelta ||
    metrics.comparedPixels !==
      pixelComparison.reference.widthPx * pixelComparison.reference.heightPx ||
    pixelComparison.reference.widthPx !== pixelComparison.render.widthPx ||
    pixelComparison.reference.heightPx !== pixelComparison.render.heightPx ||
    pixelLocalization.viewport.widthPx !== pixelComparison.render.widthPx ||
    pixelLocalization.viewport.heightPx !== pixelComparison.render.heightPx ||
    pixelLocalization.mismatchedPixels !== metrics.mismatchedPixels ||
    pixelLocalization.status !== (metrics.mismatchedPixels === 0 ? 'exact' : 'mismatch') ||
    pixelComparison.status !== (metrics.mismatchedPixels === 0 ? 'passed' : 'failed') ||
    pixelGate.status !== (metrics.mismatchedPixels === 0 ? 'passed' : 'failed') ||
    !same(pixelComparison.reference.configuration, pixelComparison.render.configuration)
  ) {
    throw new Error('PIXEL-BINDING');
  }
  const layoutNodes = new Map(layoutComparison.nodes.map((node) => [node.designNodeId, node]));
  const allChecks = layoutComparison.nodes.flatMap((node) => node.checks);
  const requiredChecks = allChecks.filter((check) => check.status !== 'not-applicable');
  const passedChecks = allChecks.filter((check) => check.status === 'passed');
  const failedChecks = allChecks.filter((check) => check.status === 'failed');
  const notApplicableChecks = allChecks.filter((check) => check.status === 'not-applicable');
  const semanticChecks = requiredChecks.filter((check) => check.category === 'semantic');
  const structureChecks = requiredChecks.filter((check) =>
    ['identity', 'structure', 'geometry'].includes(check.category));
  if (
    layoutNodes.size !== layoutComparison.nodes.length ||
    layoutNodes.size !== designIndex.size ||
    layoutComparison.summary.designNodes !== designIndex.size ||
    layoutComparison.summary.mappedNodes !== designIndex.size ||
    layoutComparison.summary.requiredChecks !== requiredChecks.length ||
    layoutComparison.summary.passedChecks !== passedChecks.length ||
    layoutComparison.summary.failedChecks !== failedChecks.length ||
    layoutComparison.summary.notApplicableChecks !== notApplicableChecks.length ||
    failedChecks.length !== 0 ||
    layoutComparison.findings.length !== 0 ||
    semanticGate.totalChecks !== semanticChecks.length ||
    semanticGate.passedChecks !== semanticChecks.length ||
    structureGate.totalChecks !== structureChecks.length ||
    structureGate.passedChecks !== structureChecks.length
  ) {
    throw new Error('LAYOUT-BINDING');
  }
  for (const layout of layoutComparison.nodes) {
    const design = designIndex.get(layout.designNodeId);
    if (!design || !same(layout.designPath, design.path)) throw new Error('LAYOUT-BINDING');
  }
  let attributed = 0;
  const attributionIds = new Set();
  for (const attribution of pixelLocalization.attributions) {
    const design = designIndex.get(attribution.designNodeId);
    const layout = layoutNodes.get(attribution.designNodeId);
    if (
      !design ||
      !layout ||
      attributionIds.has(attribution.designNodeId) ||
      !same(attribution.designPath, design.path) ||
      !same(layout.designPath, design.path) ||
      layout.bounds === null ||
      !same(attribution.nodeBounds, clippedBounds(layout.bounds, pixelLocalization.viewport))
    ) {
      throw new Error('LOCALIZATION-BINDING');
    }
    attributionIds.add(attribution.designNodeId);
    attributed += attribution.mismatchedPixels;
  }
  if (
    attributed + pixelLocalization.unassignedMismatchedPixels !==
      pixelLocalization.mismatchedPixels ||
    (pixelLocalization.mismatchedPixels === 0) !==
      (pixelLocalization.mismatchBounds === null)
  ) {
    throw new Error('LOCALIZATION-TOTAL');
  }
}

function validateEvidence(value, schemas, signal) {
  throwIfCancelled(signal);
  let encoded;
  try {
    encoded = JSON.stringify(value);
  } catch {
    throw new Error('ENCODING');
  }
  if (Buffer.byteLength(encoded, 'utf8') > MAX_EVIDENCE_BYTES) throw new Error('SIZE');
  assertSchema(value, schemas.evidence);
  assertSchema(value.candidateEvaluation, schemas.repair.$defs.candidateEvaluation, schemas.repair);
  assertSchema(value.designIr, schemas.designIr);
  if (value.layoutComparison !== null) assertSchema(value.layoutComparison, schemas.layout);
  if (value.pixelComparison !== null) assertSchema(value.pixelComparison, schemas.pixels);
  if (value.pixelLocalization !== null) assertSchema(value.pixelLocalization, schemas.localization);
  const unsigned = structuredClone(value);
  delete unsigned.evidenceFingerprint;
  if (
    fingerprintRepairValue(unsigned) !== value.evidenceFingerprint ||
    !validGateSequence(value.candidateEvaluation) ||
    value.candidateEvaluation.designIrFingerprint !== fingerprintRepairValue(value.designIr) ||
    value.lineage.candidateDesignIrFingerprint !== value.candidateEvaluation.designIrFingerprint ||
    value.diagnostics.map((item) => item.gate).join('\0') !== GATE_ORDER.join('\0')
  ) {
    throw new Error('INTEGRITY');
  }
  const designIndex = indexDesignIr(value.designIr, signal);
  if (value.status === 'complete') {
    validateFullPixelEvidence(value, designIndex);
  } else if (value.candidateEvaluation.gates[5].status !== 'not-run') {
    throw new Error('STATUS');
  }
  return {evidence: value, designIndex};
}

function exactReferenceIdentity(evidence) {
  const reference = evidence.pixelComparison?.reference;
  if (!reference) return null;
  return {
    requestFingerprint: reference.requestFingerprint,
    outputFingerprint: reference.outputFingerprint,
    pngFingerprint: reference.pngFingerprint,
    widthPx: reference.widthPx,
    heightPx: reference.heightPx,
    configuration: reference.configuration,
  };
}

function candidateHasRequiredPixelFailure(candidate) {
  const gates = candidate.candidateEvaluation.gates;
  return candidate.status === 'complete' &&
    gates.slice(0, 5).every((gate) => gate.status === 'passed') &&
    gates[5].status === 'failed';
}

function baselineStrictlyBetter(baseline, candidate) {
  const baselineGates = baseline.candidateEvaluation.gates;
  if (
    baseline.status !== 'complete' ||
    !baselineGates.slice(0, 5).every((gate) => gate.status === 'passed') ||
    !['passed', 'failed'].includes(baselineGates[5].status)
  ) {
    return false;
  }
  const before = baselineGates[5];
  const current = candidate.candidateEvaluation.gates[5];
  return before.comparedPixels === current.comparedPixels &&
    before.mismatchedPixels < current.mismatchedPixels &&
    before.maxChannelDelta <= current.maxChannelDelta;
}

function locateSinglePropertyDifference(baseline, candidate, baselineIndex, candidateIndex) {
  const differences = [];
  for (const [nodeId, current] of candidateIndex) {
    const before = baselineIndex.get(nodeId);
    if (!before) return null;
    const baselineFields = new Map(before.node.properties.map((field) => [field.name, field]));
    for (const field of current.node.properties) {
      const baselineField = baselineFields.get(field.name);
      if (
        baselineField &&
        fingerprintRepairValue(baselineField.value) !== fingerprintRepairValue(field.value)
      ) {
        differences.push({
          nodeId,
          designPath: current.path,
          name: field.name,
          candidateValue: field.value,
          baselineValue: baselineField.value,
        });
        if (differences.length > 1) return null;
      }
    }
  }
  if (differences.length !== 1) return null;
  const difference = differences[0];
  if (
    difference.candidateValue?.kind === 'expression' ||
    difference.baselineValue?.kind === 'expression'
  ) {
    return null;
  }
  const restored = structuredClone(candidate);
  const restoredIndex = indexDesignIr(restored);
  const fields = restoredIndex.get(difference.nodeId).node.properties;
  const index = fields.findIndex((field) => field.name === difference.name);
  fields[index] = {name: difference.name, value: structuredClone(difference.baselineValue)};
  return canonicalJson(restored) === canonicalJson(baseline) ? difference : null;
}

async function eligibleRollback(baselineRecord, candidateRecord, signal) {
  const {evidence: baseline, designIndex: baselineIndex} = baselineRecord;
  const {evidence: candidate, designIndex: candidateIndex} = candidateRecord;
  const difference = locateSinglePropertyDifference(
    baseline.designIr,
    candidate.designIr,
    baselineIndex,
    candidateIndex,
  );
  if (difference === null) return null;
  const attribution = candidate.pixelLocalization.attributions.find((item) =>
    item.designNodeId === difference.nodeId && same(item.designPath, difference.designPath));
  if (!attribution || attribution.mismatchedPixels < 1) return null;
  const patch = sealRepairPatch([{
    op: 'replace-field',
    nodeId: difference.nodeId,
    collection: 'properties',
    name: difference.name,
    value: structuredClone(difference.baselineValue),
  }]);
  if (!await validateRepairPatch(patch)) return null;
  const applied = await applyDesignIrRepairPatch({
    designIr: candidate.designIr,
    expectedDesignIrFingerprint: candidate.lineage.candidateDesignIrFingerprint,
    patch,
  }, {signal});
  if (
    applied.designIrFingerprint !== baseline.lineage.candidateDesignIrFingerprint ||
    canonicalJson(applied.designIr) !== canonicalJson(baseline.designIr)
  ) {
    return null;
  }
  return {
    target: {
      nodeId: difference.nodeId,
      designPath: [...difference.designPath],
      collection: 'properties',
      name: difference.name,
      candidateValueFingerprint: fingerprintRepairValue(difference.candidateValue),
      baselineValueFingerprint: fingerprintRepairValue(difference.baselineValue),
      localizationFingerprint: candidate.pixelLocalization.localizationFingerprint,
      attributedMismatchedPixels: attribution.mismatchedPixels,
    },
    patch,
  };
}

export async function proposeScreenshotRepair({baselineEvidence, candidateEvidence} = {}, {signal} = {}) {
  if (signal?.aborted) return cancelled(baselineEvidence, candidateEvidence);
  const schemas = await loadSchemas();
  let baselineRecord;
  let candidateRecord;
  try {
    baselineRecord = validateEvidence(baselineEvidence, schemas, signal);
    candidateRecord = validateEvidence(candidateEvidence, schemas, signal);
  } catch (error) {
    if (signal?.aborted || error?.code === 'VC-AI-REPAIR-PROPOSAL-CANCELLED') {
      return cancelled(baselineEvidence, candidateEvidence);
    }
    return invalid('input-invalid', baselineEvidence, candidateEvidence);
  }
  const baseline = baselineRecord.evidence;
  const candidate = candidateRecord.evidence;
  if (
    baseline.lineage.baseResolutionResultFingerprint !==
      candidate.lineage.baseResolutionResultFingerprint ||
    baseline.lineage.inputDesignIrFingerprint !== candidate.lineage.inputDesignIrFingerprint
  ) {
    return invalid('evidence-lineage-mismatch', baselineEvidence, candidateEvidence);
  }
  if (candidate.candidateEvaluation.gates[5].status === 'passed') {
    return noEligible('candidate-already-exact', baselineEvidence, candidateEvidence);
  }
  if (!candidateHasRequiredPixelFailure(candidate)) {
    return noEligible('earlier-gate-failed', baselineEvidence, candidateEvidence);
  }
  if (!baselineStrictlyBetter(baseline, candidate)) {
    return noEligible('baseline-not-strictly-better', baselineEvidence, candidateEvidence);
  }
  if (!same(exactReferenceIdentity(baseline), exactReferenceIdentity(candidate))) {
    return invalid('evidence-lineage-mismatch', baselineEvidence, candidateEvidence);
  }
  try {
    throwIfCancelled(signal);
    const rollback = await eligibleRollback(baselineRecord, candidateRecord, signal);
    if (rollback === null) {
      return noEligible(
        'no-single-localized-property-difference',
        baselineEvidence,
        candidateEvidence,
      );
    }
    const proposal = sealProposal({
      status: 'proposed',
      reason: 'strict-pixel-regression-rollback',
      baselineEvidence,
      candidateEvidence,
      ...rollback,
    });
    if (
      validateSchemaValue(proposal, schemas.proposal).length > 0 ||
      !await validateRepairPatch(proposal.patch)
    ) {
      return invalid('input-invalid', baselineEvidence, candidateEvidence);
    }
    return proposal;
  } catch (error) {
    if (signal?.aborted || error?.code?.includes('CANCELLED')) {
      return cancelled(baselineEvidence, candidateEvidence);
    }
    return noEligible(
      'no-single-localized-property-difference',
      baselineEvidence,
      candidateEvidence,
    );
  }
}
