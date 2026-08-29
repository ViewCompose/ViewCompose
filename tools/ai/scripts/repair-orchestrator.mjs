import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {validateSchemaValue} from './schema-validator.mjs';
import {canonicalJson} from './screenshot-contract.mjs';

const repairSchemaPath = new URL('../contracts/screenshot-repair.schema.json', import.meta.url);
const designIrSchemaPath = new URL('../contracts/design-ir.schema.json', import.meta.url);
const GATE_ORDER = Object.freeze([
  'safety',
  'compilation',
  'semantics',
  'structure',
  'exact-pixels',
]);
const MAX_ITERATIONS = 5;
const MAX_PATCH_OPERATIONS = 64;
const MAX_PATCH_BYTES = 262_144;
const MAX_CANDIDATE_BYTES = 262_144;
const MAX_EVALUATION_CHECKS = 10_000;
const SHA256 = /^[a-f0-9]{64}$/u;
const STABLE_ID = /^[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}$/u;
const POLICY = Object.freeze({
  version: 1,
  maxIterations: MAX_ITERATIONS,
  gateOrder: GATE_ORDER,
  candidateMutation: 'typed-design-ir-patches-only',
  noOscillation: true,
  noRegression: true,
  strictImprovement: true,
  aggregateScore: false,
  networkAccess: false,
  providerCalls: false,
  executeInspectedProjectBuildLogic: false,
});

let schemasPromise;

function loadSchemas() {
  schemasPromise ??= Promise.all([
    readFile(repairSchemaPath, 'utf8').then(JSON.parse),
    readFile(designIrSchemaPath, 'utf8').then(JSON.parse),
  ]);
  return schemasPromise;
}

function fingerprint(value) {
  return createHash('sha256').update(JSON.stringify(value)).digest('hex');
}

export function fingerprintRepairValue(value) {
  return createHash('sha256').update(canonicalJson(value)).digest('hex');
}

function fingerprintWithout(value, key) {
  const copy = structuredClone(value);
  delete copy[key];
  return fingerprint(copy);
}

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function exactKeys(value, keys) {
  return isObject(value) &&
    JSON.stringify(Object.keys(value).sort()) === JSON.stringify([...keys].sort());
}

function gateAt(evaluation, name) {
  return evaluation.gates[GATE_ORDER.indexOf(name)];
}

function firstFailedGate(evaluation) {
  return evaluation.gates.find((gate) => gate.status !== 'passed');
}

function allPassed(evaluation) {
  return firstFailedGate(evaluation) === undefined;
}

function validateGateSequence(evaluation) {
  if (
    !same(evaluation.gates.map((gate) => gate.name), GATE_ORDER) ||
    Buffer.byteLength(JSON.stringify(evaluation), 'utf8') > MAX_CANDIDATE_BYTES ||
    evaluation.gates.slice(0, 4).reduce((sum, gate) => sum + gate.totalChecks, 0) >
      MAX_EVALUATION_CHECKS ||
    fingerprintWithout(evaluation, 'evaluationFingerprint') !== evaluation.evaluationFingerprint
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
        (gate.status === 'failed' && (
          gate.comparedPixels < 1 || gate.mismatchedPixels < 1 || gate.maxChannelDelta < 1
        )) ||
        (gate.status === 'not-run' && (
          gate.comparedPixels !== 0 || gate.mismatchedPixels !== 0 || gate.maxChannelDelta !== 0
        ))
      ) return false;
    } else if (
      gate.passedChecks > gate.totalChecks ||
      (gate.status === 'passed' && (gate.totalChecks < 1 || gate.passedChecks !== gate.totalChecks)) ||
      (gate.status === 'failed' && (gate.totalChecks < 1 || gate.passedChecks >= gate.totalChecks)) ||
      (gate.status === 'not-run' && (gate.totalChecks !== 0 || gate.passedChecks !== 0))
    ) return false;
    if (gate.status !== 'passed') stopped = true;
  }
  return true;
}

function same(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

async function validEvaluation(evaluation) {
  const [repairSchema] = await loadSchemas();
  return validateSchemaValue(
    evaluation,
    repairSchema.$defs.candidateEvaluation,
    repairSchema,
  ).length === 0 && validateGateSequence(evaluation);
}

function operationValid(operation, designIrSchema) {
  if (!isObject(operation) || !STABLE_ID.test(operation.nodeId ?? '')) return false;
  if (operation.op === 'replace-field') {
    return exactKeys(operation, ['op', 'nodeId', 'collection', 'name', 'value']) &&
      ['properties', 'semantics', 'state'].includes(operation.collection) &&
      STABLE_ID.test(operation.name ?? '') &&
      operation.value?.kind !== 'expression' &&
      validateSchemaValue(operation.value, designIrSchema.$defs.value, designIrSchema).length === 0;
  }
  if (operation.op === 'replace-modifier-argument') {
    return exactKeys(
      operation,
      ['op', 'nodeId', 'modifierIndex', 'name', 'value'],
    ) && Number.isInteger(operation.modifierIndex) &&
      operation.modifierIndex >= 0 && operation.modifierIndex < 128 &&
      STABLE_ID.test(operation.name ?? '') && operation.value?.kind !== 'expression' &&
      validateSchemaValue(operation.value, designIrSchema.$defs.value, designIrSchema).length === 0;
  }
  if (operation.op === 'replace-node-kind') {
    return exactKeys(operation, ['op', 'nodeId', 'kind']) &&
      ['box', 'button', 'column', 'image', 'row', 'text', 'text-field'].includes(operation.kind);
  }
  if (operation.op === 'reorder-children') {
    return exactKeys(operation, ['op', 'nodeId', 'orderedChildIds']) &&
      Array.isArray(operation.orderedChildIds) && operation.orderedChildIds.length <= 1000 &&
      operation.orderedChildIds.every((id) => STABLE_ID.test(id)) &&
      new Set(operation.orderedChildIds).size === operation.orderedChildIds.length;
  }
  return false;
}

export async function validateRepairPatch(patch) {
  const [, designIrSchema] = await loadSchemas();
  const operationKeys = patch?.operations?.map((operation, index) => {
    if (!isObject(operation)) return `invalid:${index}`;
    if (operation.op === 'replace-field') {
      return `${operation.op}:${operation.nodeId}:${operation.collection}:${operation.name}`;
    }
    if (operation.op === 'replace-modifier-argument') {
      return `${operation.op}:${operation.nodeId}:${operation.modifierIndex}:${operation.name}`;
    }
    return `${operation.op}:${operation.nodeId}`;
  }) ?? [];
  return exactKeys(patch, ['schemaVersion', 'operations', 'changeFingerprint']) &&
    patch.schemaVersion === 1 && Array.isArray(patch.operations) &&
    patch.operations.length >= 1 && patch.operations.length <= MAX_PATCH_OPERATIONS &&
    Buffer.byteLength(JSON.stringify(patch), 'utf8') <= MAX_PATCH_BYTES &&
    SHA256.test(patch.changeFingerprint ?? '') &&
    fingerprintWithout(patch, 'changeFingerprint') === patch.changeFingerprint &&
    new Set(operationKeys).size === operationKeys.length &&
    patch.operations.every((operation) => operationValid(operation, designIrSchema));
}

export function sealRepairPatch(operations) {
  const patch = {schemaVersion: 1, operations: structuredClone(operations)};
  patch.changeFingerprint = fingerprint(patch);
  return patch;
}

export function sealRepairEvaluation({candidateFingerprint, designIrFingerprint, gates}) {
  const evaluation = {
    candidateFingerprint,
    designIrFingerprint,
    gates: structuredClone(gates),
  };
  evaluation.evaluationFingerprint = fingerprint(evaluation);
  return evaluation;
}

function regression(current, candidate) {
  return current.gates.find((gate, index) =>
    gate.status === 'passed' && candidate.gates[index].status !== 'passed');
}

function strictlyImproves(currentGate, candidateGate) {
  if (currentGate.status === 'passed') return false;
  if (candidateGate.status === 'passed') return true;
  if (candidateGate.status !== 'failed' || currentGate.status !== 'failed') return false;
  if (currentGate.name === 'exact-pixels') {
    return candidateGate.comparedPixels === currentGate.comparedPixels &&
      candidateGate.mismatchedPixels < currentGate.mismatchedPixels &&
      candidateGate.maxChannelDelta <= currentGate.maxChannelDelta;
  }
  return candidateGate.totalChecks === currentGate.totalChecks &&
    candidateGate.passedChecks > currentGate.passedChecks;
}

function finding(code, message, nextAction, severity = 'error') {
  return {code, severity, message, nextAction};
}

function sealResult({status, initial, iterations, final, reason, findings}) {
  const result = {
    schemaVersion: 1,
    status,
    policy: structuredClone(POLICY),
    initial,
    iterations,
    final,
    termination: {
      reason,
      iterationCount: iterations.length,
      acceptedCandidates: iterations.filter((item) => item.disposition === 'accepted').length,
      rejectedCandidates: iterations.filter((item) => item.disposition !== 'accepted').length,
    },
    findings,
  };
  result.repairFingerprint = fingerprint(result);
  return result;
}

function cancelled(initial, iterations, final) {
  return sealResult({
    status: 'cancelled',
    initial,
    iterations,
    final,
    reason: 'cancelled',
    findings: [finding(
      'VC-AI-REPAIR-CANCELLED',
      'Bounded screenshot repair was cancelled before convergence was established.',
      'Retry the same immutable initial candidate and deterministic repair inputs if still required.',
      'warning',
    )],
  });
}

function inputInvalid(initial, iterations, final, message) {
  return sealResult({
    status: 'blocked',
    initial,
    iterations,
    final,
    reason: 'input-invalid',
    findings: [finding(
      'VC-AI-REPAIR-INPUT-INVALID',
      message,
      'Use one schema-valid candidate, one typed Design IR patch, and ordered gate evidence.',
    )],
  });
}

export async function orchestrateScreenshotRepair({
  initial,
  proposePatch,
  evaluatePatch,
} = {}, {signal} = {}) {
  const iterations = [];
  if (!await validEvaluation(initial)) {
    const fallback = sealRepairEvaluation({
      candidateFingerprint: '0'.repeat(64),
      designIrFingerprint: '0'.repeat(64),
      gates: [
        {
          name: 'safety',
          status: 'failed',
          passedChecks: 0,
          totalChecks: 1,
          evidenceFingerprint: '0'.repeat(64),
        },
        ...['compilation', 'semantics', 'structure'].map((name) => ({
          name,
          status: 'not-run',
          passedChecks: 0,
          totalChecks: 0,
          evidenceFingerprint: '0'.repeat(64),
        })),
        {
          name: 'exact-pixels',
          status: 'not-run',
          comparedPixels: 0,
          mismatchedPixels: 0,
          maxChannelDelta: 0,
          evidenceFingerprint: '0'.repeat(64),
        },
      ],
    });
    return inputInvalid(fallback, iterations, fallback, 'The initial repair evaluation is invalid.');
  }
  const initialCopy = structuredClone(initial);
  let current = initialCopy;
  if (signal?.aborted) return cancelled(initialCopy, iterations, current);
  const initialFailure = firstFailedGate(current);
  if (!initialFailure) {
    return sealResult({
      status: 'converged',
      initial: initialCopy,
      iterations,
      final: current,
      reason: 'initial-pass',
      findings: [],
    });
  }
  if (initialFailure.name === 'safety') {
    return sealResult({
      status: 'blocked',
      initial: initialCopy,
      iterations,
      final: current,
      reason: 'safety-failure',
      findings: [finding(
        'VC-AI-REPAIR-SAFETY-FAILURE',
        'The initial candidate failed the first safety gate; no repair proposal was evaluated.',
        'Resolve the safety finding outside automatic repair and submit a new reviewed candidate.',
      )],
    });
  }
  if (typeof proposePatch !== 'function' || typeof evaluatePatch !== 'function') {
    return inputInvalid(
      initialCopy,
      iterations,
      current,
      'Repair callbacks are required while a deterministic gate is failing.',
    );
  }

  const candidateFingerprints = new Set([current.candidateFingerprint]);
  const designIrFingerprints = new Set([current.designIrFingerprint]);
  const changeFingerprints = new Set();
  for (let iteration = 1; iteration <= MAX_ITERATIONS; iteration += 1) {
    if (signal?.aborted) return cancelled(initialCopy, iterations, current);
    const failed = firstFailedGate(current);
    let patch;
    try {
      patch = await proposePatch({
        iteration,
        reasonCode: failed.name,
        candidate: structuredClone(current),
      }, {signal});
    } catch {
      return inputInvalid(initialCopy, iterations, current, 'The typed repair proposer failed.');
    }
    if (signal?.aborted) return cancelled(initialCopy, iterations, current);
    if (patch === null || patch === undefined) {
      return sealResult({
        status: 'incomplete',
        initial: initialCopy,
        iterations,
        final: current,
        reason: 'no-eligible-change',
        findings: [finding(
          'VC-AI-REPAIR-NO-ELIGIBLE-CHANGE',
          `No eligible typed Design IR change was available for the ${failed.name} gate.`,
          'Return the retained evidence for human review without weakening the failed gate.',
          'warning',
        )],
      });
    }
    if (!await validateRepairPatch(patch)) {
      return inputInvalid(initialCopy, iterations, current, 'The proposed Design IR patch is invalid.');
    }
    if (changeFingerprints.has(patch.changeFingerprint)) {
      iterations.push({
        iteration,
        reasonCode: failed.name,
        changeFingerprint: patch.changeFingerprint,
        beforeCandidateFingerprint: current.candidateFingerprint,
        candidate: current,
        disposition: 'rejected-oscillation',
      });
      return sealResult({
        status: 'blocked',
        initial: initialCopy,
        iterations,
        final: current,
        reason: 'oscillation',
        findings: [finding(
          'VC-AI-REPAIR-OSCILLATION',
          'A typed repair change fingerprint repeated within the same bounded run.',
          'Stop automatic repair and inspect the retained candidate history.',
        )],
      });
    }
    changeFingerprints.add(patch.changeFingerprint);

    let candidate;
    try {
      candidate = await evaluatePatch({
        iteration,
        reasonCode: failed.name,
        candidate: structuredClone(current),
        patch: structuredClone(patch),
      }, {signal});
    } catch {
      return inputInvalid(initialCopy, iterations, current, 'The deterministic candidate evaluator failed.');
    }
    if (signal?.aborted) return cancelled(initialCopy, iterations, current);
    if (!await validEvaluation(candidate)) {
      return inputInvalid(initialCopy, iterations, current, 'The candidate gate evaluation is invalid.');
    }
    const record = {
      iteration,
      reasonCode: failed.name,
      changeFingerprint: patch.changeFingerprint,
      beforeCandidateFingerprint: current.candidateFingerprint,
      candidate: structuredClone(candidate),
      disposition: 'accepted',
    };
    if (
      candidateFingerprints.has(candidate.candidateFingerprint) ||
      designIrFingerprints.has(candidate.designIrFingerprint)
    ) {
      record.disposition = 'rejected-oscillation';
      iterations.push(record);
      return sealResult({
        status: 'blocked',
        initial: initialCopy,
        iterations,
        final: current,
        reason: 'oscillation',
        findings: [finding(
          'VC-AI-REPAIR-OSCILLATION',
          'A candidate fingerprint repeated within the same bounded run.',
          'Stop automatic repair and inspect the retained candidate history.',
        )],
      });
    }
    candidateFingerprints.add(candidate.candidateFingerprint);
    designIrFingerprints.add(candidate.designIrFingerprint);
    const regressed = regression(current, candidate);
    if (regressed) {
      record.disposition = 'rejected-regression';
      iterations.push(record);
      return sealResult({
        status: 'blocked',
        initial: initialCopy,
        iterations,
        final: current,
        reason: 'regression',
        findings: [finding(
          'VC-AI-REPAIR-REGRESSION',
          `The proposed candidate regressed the previously passed ${regressed.name} gate.`,
          'Keep the previous candidate and stop automatic repair for human review.',
        )],
      });
    }
    if (!strictlyImproves(failed, gateAt(candidate, failed.name))) {
      record.disposition = 'rejected-no-improvement';
      iterations.push(record);
      return sealResult({
        status: 'incomplete',
        initial: initialCopy,
        iterations,
        final: current,
        reason: 'no-eligible-change',
        findings: [finding(
          'VC-AI-REPAIR-NO-ELIGIBLE-CHANGE',
          `The proposed candidate did not strictly improve the ${failed.name} gate.`,
          'Keep the previous candidate and return the separate gate evidence for human review.',
          'warning',
        )],
      });
    }
    iterations.push(record);
    current = structuredClone(candidate);
    if (allPassed(current)) {
      return sealResult({
        status: 'converged',
        initial: initialCopy,
        iterations,
        final: current,
        reason: 'exact-pass',
        findings: [],
      });
    }
  }
  return sealResult({
    status: 'incomplete',
    initial: initialCopy,
    iterations,
    final: current,
    reason: 'max-iterations',
    findings: [finding(
      'VC-AI-REPAIR-ITERATION-LIMIT',
      'Bounded screenshot repair exhausted five accepted iterations without convergence.',
      'Return the retained candidate and every gate delta for human review.',
      'warning',
    )],
  });
}
