#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {validateSchemaValue} from './schema-validator.mjs';

const visualRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));
const contractPath = resolve(visualRoot, 'screenshot-repair-contract.json');
const schemaPath = fileURLToPath(new URL('../contracts/screenshot-repair.schema.json', import.meta.url));
const GATE_ORDER = Object.freeze([
  'safety',
  'compilation',
  'semantics',
  'structure',
  'exact-pixels',
]);

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function same(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

function fingerprintWithout(value, key) {
  const copy = structuredClone(value);
  delete copy[key];
  return createHash('sha256').update(JSON.stringify(copy)).digest('hex');
}

function assertUnique(values, label) {
  if (new Set(values).size !== values.length) throw new Error(`${label} are not unique`);
}

function assertEvaluation(evaluation, label) {
  if (!same(evaluation.gates.map((gate) => gate.name), GATE_ORDER)) {
    throw new Error(`${label}: gate order changed`);
  }
  if (fingerprintWithout(evaluation, 'evaluationFingerprint') !== evaluation.evaluationFingerprint) {
    throw new Error(`${label}: evaluation fingerprint changed`);
  }
  let stopped = false;
  for (const gate of evaluation.gates) {
    if (stopped && gate.status !== 'not-run') {
      throw new Error(`${label}: ${gate.name} ran after an earlier deterministic gate stopped`);
    }
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
      ) {
        throw new Error(`${label}: exact-pixel disposition is internally inconsistent`);
      }
    } else if (
      gate.passedChecks > gate.totalChecks ||
      (gate.status === 'passed' && (gate.totalChecks < 1 || gate.passedChecks !== gate.totalChecks)) ||
      (gate.status === 'failed' && (gate.totalChecks < 1 || gate.passedChecks >= gate.totalChecks)) ||
      (gate.status === 'not-run' && (gate.totalChecks !== 0 || gate.passedChecks !== 0))
    ) {
      throw new Error(`${label}: ${gate.name} disposition is internally inconsistent`);
    }
    if (gate.status !== 'passed') stopped = true;
  }
}

function assertResult(result, schema, fixture) {
  const violations = validateSchemaValue(result, schema);
  if (violations.length > 0) {
    throw new Error(`Screenshot repair golden violates schema v1: ${violations.join('; ')}`);
  }
  assertEvaluation(result.initial, 'initial candidate');
  assertEvaluation(result.final, 'final candidate');
  if (
    result.status !== fixture.expectedStatus ||
    result.termination.reason !== fixture.expectedTermination ||
    result.termination.iterationCount !== fixture.expectedIterations ||
    result.iterations.length !== fixture.expectedIterations ||
    result.repairFingerprint !== fixture.expectedRepairFingerprint ||
    fingerprintWithout(result, 'repairFingerprint') !== result.repairFingerprint
  ) {
    throw new Error('Screenshot repair initial-pass golden changed');
  }
  const candidates = [
    result.initial.candidateFingerprint,
    ...result.iterations.map((iteration) => iteration.candidate.candidateFingerprint),
  ];
  const changes = result.iterations.map((iteration) => iteration.changeFingerprint);
  assertUnique(candidates, 'Accepted screenshot repair candidates');
  assertUnique(changes, 'Screenshot repair changes');
  if (
    result.status !== 'converged' ||
    result.initial.candidateFingerprint !== result.final.candidateFingerprint ||
    result.initial.evaluationFingerprint !== result.final.evaluationFingerprint ||
    result.termination.acceptedCandidates !== 0 ||
    result.termination.rejectedCandidates !== 0 ||
    result.findings.length !== 0 ||
    result.final.gates.some((gate) => gate.status !== 'passed')
  ) {
    throw new Error('Screenshot repair zero-iteration convergence boundary changed');
  }
}

function assertContract(contract, schema) {
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-bounded-screenshot-repair-v1' ||
    !same(contract.requiresContracts, [
      'viewcompose-screenshot-layout-comparison-v1',
      'viewcompose-screenshot-pixel-comparison-v1',
      'screenshot-repair-v1',
    ]) ||
    contract.activation?.tool !== 'generate_screenshot_viewcompose' ||
    contract.activation?.status !== 'contract-only' ||
    contract.activation?.publicRepairMode !== false ||
    contract.activation?.implementation !== false
  ) {
    throw new Error('Screenshot repair activation boundary changed');
  }
  if (
    contract.candidateBoundary?.mutation !==
      'typed Design IR patches over the accepted resolved result only' ||
    contract.candidateBoundary?.callerSuppliedKotlin !== false ||
    contract.candidateBoundary?.arbitrarySourceEdits !== false ||
    contract.candidateBoundary?.providerCalls !== false ||
    contract.candidateBoundary?.networkAccess !== false ||
    contract.candidateBoundary?.executeInspectedProjectBuildLogic !== false ||
    contract.candidateBoundary?.followSymbolicLinks !== false
  ) {
    throw new Error('Screenshot repair source or execution boundary changed');
  }
  if (
    contract.policy?.maxIterations !== 5 ||
    !same(contract.policy?.gateOrder, GATE_ORDER) ||
    contract.policy?.firstFailedGateOwnsReason !== true ||
    contract.policy?.passedGateRegression !== 'reject candidate and stop' ||
    contract.policy?.duplicateCandidateOrChange !== 'reject as oscillation and stop' ||
    contract.policy?.acceptedCandidate !==
      'must strictly improve the first previously failing gate' ||
    contract.policy?.aggregateScore !== false ||
    contract.policy?.automaticThresholdRelaxation !== false ||
    contract.policy?.automaticReferenceMutation !== false
  ) {
    throw new Error('Screenshot repair gate or convergence policy changed');
  }
  for (const [name, ceiling] of Object.entries({
    maxIterations: 5,
    maxFindings: 100,
    maxDesignIrPatchOperations: 64,
    maxCandidateBytes: 262144,
    maxEvaluationChecks: 10000,
    maxPixels: 4194304,
    timeoutMs: 600000,
  })) {
    const value = contract.limits?.[name];
    if (!Number.isInteger(value) || value < 1 || value > ceiling) {
      throw new Error(`Screenshot repair limit ${name} exceeds its ceiling`);
    }
  }
  assertUnique(contract.diagnosticCodes, 'Screenshot repair diagnostic codes');
  if (contract.diagnosticCodes.some((code) => !/^VC-AI-REPAIR-[A-Z0-9-]+$/u.test(code))) {
    throw new Error('Screenshot repair diagnostic namespace changed');
  }
  if (
    schema.$id !== 'https://schemas.viewcompose.com/ai/screenshot-repair-v1.schema.json' ||
    schema.properties?.schemaVersion?.const !== 1 ||
    schema.$defs?.policy?.properties?.maxIterations?.const !== 5 ||
    !same(schema.$defs?.policy?.properties?.gateOrder?.const, GATE_ORDER) ||
    schema.$defs?.policy?.properties?.aggregateScore?.const !== false
  ) {
    throw new Error('Screenshot repair result schema boundary changed');
  }
  if (
    !contract.claims?.checked?.includes(
      'no regression of a previously passed deterministic gate',
    ) ||
    !contract.claims?.checked?.includes(
      'pixel evidence cannot override safety, compilation, semantic, or structural failure',
    ) ||
    !contract.claims?.notClaimed?.includes('automatic repair implementation') ||
    !contract.claims?.notClaimed?.includes('perceptual similarity or an aggregate visual score')
  ) {
    throw new Error('Screenshot repair claim boundary changed');
  }
}

function assertMutation(mutation, expected) {
  const expectedByOperation = {
    'xor-render-channel': {
      termination: 'no-eligible-change',
      status: 'incomplete',
      code: 'VC-AI-REPAIR-NO-ELIGIBLE-CHANGE',
      valid: mutation.pixelIndex === 0 && mutation.channel === 'red' && mutation.value === 1,
    },
    'regress-passed-gate': {
      termination: 'regression',
      status: 'blocked',
      code: 'VC-AI-REPAIR-REGRESSION',
      valid: mutation.regressedGate === 'semantics' &&
        mutation.previousStatus === 'passed' && mutation.candidateStatus === 'failed',
    },
    'repeat-candidate': {
      termination: 'oscillation',
      status: 'blocked',
      code: 'VC-AI-REPAIR-OSCILLATION',
      valid: mutation.firstSeenIteration === 0 && mutation.repeatedAtIteration === 1 &&
        /^[a-f0-9]{64}$/u.test(mutation.candidateFingerprint ?? ''),
    },
    'exhaust-iterations': {
      termination: 'max-iterations',
      status: 'incomplete',
      code: 'VC-AI-REPAIR-ITERATION-LIMIT',
      valid: mutation.attemptedIterations === 5 && mutation.remainingMismatchedPixels > 0 &&
        mutation.candidateFingerprints?.length === 5 &&
        new Set(mutation.candidateFingerprints).size === 5,
    },
    'fail-safety-gate': {
      termination: 'safety-failure',
      status: 'blocked',
      code: 'VC-AI-REPAIR-SAFETY-FAILURE',
      valid: mutation.reasonCode === 'safety' && mutation.laterGatesRun === false,
    },
  }[mutation.operation];
  if (
    !expectedByOperation?.valid ||
    expected.expectedStatus !== expectedByOperation.status ||
    expected.expectedTermination !== expectedByOperation.termination ||
    !same(expected.diagnosticCodes, [expectedByOperation.code])
  ) {
    throw new Error(`${mutation.operation}: screenshot repair fail-closed denominator changed`);
  }
}

export async function verifyPhase5ScreenshotRepair() {
  const [contract, schema] = await Promise.all([readJson(contractPath), readJson(schemaPath)]);
  assertContract(contract, schema);
  const supported = contract.supportedFixtures[0];
  const golden = await readJson(resolve(visualRoot, supported.result));
  assertResult(golden, schema, supported);
  for (const fixture of contract.failClosedFixtures) {
    const mutation = await readJson(resolve(visualRoot, fixture.mutation));
    assertMutation(mutation, fixture);
  }
  return {
    supportedGoldens: contract.supportedFixtures.length,
    failClosedDenominators: contract.failClosedFixtures.length,
    repairFingerprint: golden.repairFingerprint,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase5ScreenshotRepair()
    .then((summary) => {
      console.log(
        `Verified bounded screenshot repair contract: ${summary.supportedGoldens}/` +
          `${summary.supportedGoldens} zero-iteration convergence and ` +
          `${summary.failClosedDenominators}/${summary.failClosedDenominators} ` +
          `fail-closed denominators; repair fingerprint ${summary.repairFingerprint}.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
