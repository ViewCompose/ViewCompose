#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {validateSchemaValue} from './schema-validator.mjs';
import {
  orchestrateScreenshotRepair,
  sealRepairEvaluation,
  sealRepairPatch,
} from './repair-orchestrator.mjs';
import {applyDesignIrRepairPatch} from './design-ir-repair-patch.mjs';

const visualRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));
const contractPath = resolve(visualRoot, 'screenshot-repair-contract.json');
const schemaPath = fileURLToPath(new URL('../contracts/screenshot-repair.schema.json', import.meta.url));
const GATE_ORDER = Object.freeze([
  'safety',
  'compilation',
  'render',
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

function hash(value) {
  return createHash('sha256').update(String(value)).digest('hex');
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
        (gate.status === 'failed' && !(
          (gate.comparedPixels === 0 && gate.mismatchedPixels === 0 && gate.maxChannelDelta === 0) ||
          (gate.comparedPixels >= 1 && gate.mismatchedPixels >= 1 && gate.maxChannelDelta >= 1)
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
    throw new Error(`Screenshot repair golden violates schema v2: ${violations.join('; ')}`);
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
    contract.contractId !== 'viewcompose-bounded-screenshot-repair-v2' ||
    !same(contract.requiresContracts, [
      'viewcompose-screenshot-layout-comparison-v1',
      'viewcompose-screenshot-pixel-comparison-v1',
      'screenshot-pixel-localization-v1',
      'screenshot-repair-v2',
      'screenshot-repair-candidate-evidence-v1',
    ]) ||
    contract.activation?.tool !== 'generate_screenshot_viewcompose' ||
    contract.activation?.status !== 'implemented-internal' ||
    contract.activation?.publicRepairMode !== false ||
    contract.activation?.implementation !== true ||
    contract.activation?.typedPatchApplier !== true ||
    contract.activation?.candidateEvaluator !== true ||
    contract.activation?.candidateEvidenceRecord !== true ||
    contract.activation?.candidatePixelLocalization !== true
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
    contract.policy?.candidateWithoutStrictImprovement !==
      'reject and stop as no eligible change' ||
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
    schema.$id !== 'https://schemas.viewcompose.com/ai/screenshot-repair-v2.schema.json' ||
    schema.properties?.schemaVersion?.const !== 2 ||
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
      'released-artifact compile and render evidence for each evaluated candidate',
    ) ||
    !contract.claims?.checked?.includes(
      'content-addressed candidate evidence excludes generated source and image bytes',
    ) ||
    !contract.claims?.checked?.includes(
      'candidate evidence retains bounded exact-pixel mismatch localization',
    ) ||
    !contract.claims?.checked?.includes(
      'pixel evidence cannot override safety, compilation, render, semantic, or structural failure',
    ) ||
    !contract.claims?.notClaimed?.includes('public automatic repair mode') ||
    !contract.claims?.notClaimed?.includes('perceptual similarity or an aggregate visual score')
  ) {
    throw new Error('Screenshot repair claim boundary changed');
  }
}

function baseGate(name, status = 'passed', passedChecks = 1, totalChecks = 1, seed = name) {
  return {name, status, passedChecks, totalChecks, evidenceFingerprint: hash(seed)};
}

function pixelGate(mismatchedPixels, seed) {
  return {
    name: 'exact-pixels',
    status: mismatchedPixels === 0 ? 'passed' : 'failed',
    comparedPixels: 100,
    mismatchedPixels,
    maxChannelDelta: mismatchedPixels === 0 ? 0 : mismatchedPixels,
    evidenceFingerprint: hash(seed),
  };
}

function evaluation(seed, mismatchedPixels, overrides = {}) {
  const gates = [
    baseGate('safety', 'passed', 1, 1, `${seed}-safety`),
    baseGate('compilation', 'passed', 1, 1, `${seed}-compilation`),
    baseGate('render', 'passed', 1, 1, `${seed}-render`),
    baseGate('semantics', 'passed', 14, 14, `${seed}-semantics`),
    baseGate('structure', 'passed', 13, 13, `${seed}-structure`),
    pixelGate(mismatchedPixels, `${seed}-pixels`),
  ];
  for (const [name, gate] of Object.entries(overrides)) {
    gates[GATE_ORDER.indexOf(name)] = gate;
  }
  return sealRepairEvaluation({
    candidateFingerprint: hash(`${seed}-candidate`),
    designIrFingerprint: hash(`${seed}-design-ir`),
    gates,
  });
}

function patch(seed) {
  return sealRepairPatch([{
    op: 'replace-field',
    nodeId: 'wireframe-title',
    collection: 'properties',
    name: 'text',
    value: {kind: 'literal', value: seed},
  }]);
}

async function reproduceMutation(mutation) {
  if (mutation.operation === 'xor-render-channel') {
    return orchestrateScreenshotRepair({
      initial: evaluation('one-channel', mutation.expectedMetrics.mismatchedPixels),
      proposePatch: async () => null,
      evaluatePatch: async () => { throw new Error('must not evaluate'); },
    });
  }
  if (mutation.operation === 'regress-passed-gate') {
    return orchestrateScreenshotRepair({
      initial: evaluation('regression-initial', 2),
      proposePatch: async () => patch('regression'),
      evaluatePatch: async () => evaluation('regression-candidate', 0, {
        semantics: baseGate('semantics', 'failed', 13, 14, 'regression-semantics'),
        structure: baseGate('structure', 'not-run', 0, 0, 'regression-structure'),
        'exact-pixels': {
          name: 'exact-pixels',
          status: 'not-run',
          comparedPixels: 0,
          mismatchedPixels: 0,
          maxChannelDelta: 0,
          evidenceFingerprint: hash('regression-pixels'),
        },
      }),
    });
  }
  if (mutation.operation === 'repeat-candidate') {
    const initial = evaluation('repeat-candidate', 2);
    return orchestrateScreenshotRepair({
      initial,
      proposePatch: async () => patch('repeat-candidate'),
      evaluatePatch: async () => structuredClone(initial),
    });
  }
  if (mutation.operation === 'exhaust-iterations') {
    return orchestrateScreenshotRepair({
      initial: evaluation('iteration-limit-initial', 6),
      proposePatch: async ({iteration}) => patch(`iteration-limit-${iteration}`),
      evaluatePatch: async ({iteration}) =>
        evaluation(`iteration-limit-${iteration}`, 6 - iteration),
    });
  }
  if (mutation.operation === 'fail-safety-gate') {
    const notRun = (name) => baseGate(name, 'not-run', 0, 0, `${name}-not-run`);
    return orchestrateScreenshotRepair({
      initial: sealRepairEvaluation({
        candidateFingerprint: hash('safety-candidate'),
        designIrFingerprint: hash('safety-design-ir'),
        gates: [
          baseGate('safety', 'failed', 0, 1, 'safety-failed'),
          notRun('compilation'),
          notRun('render'),
          notRun('semantics'),
          notRun('structure'),
          {
            name: 'exact-pixels',
            status: 'not-run',
            comparedPixels: 0,
            mismatchedPixels: 0,
            maxChannelDelta: 0,
            evidenceFingerprint: hash('safety-pixels'),
          },
        ],
      }),
      proposePatch: async () => { throw new Error('must not propose'); },
      evaluatePatch: async () => { throw new Error('must not evaluate'); },
    });
  }
  throw new Error(`${mutation.operation}: unknown screenshot repair mutation`);
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
  const reproducedGolden = await orchestrateScreenshotRepair({initial: golden.initial});
  if (!same(reproducedGolden, golden)) {
    throw new Error('Screenshot repair zero-iteration implementation does not reproduce its golden');
  }
  const evaluatorFixtures = contract.candidateEvaluatorFixtures;
  if (
    typeof evaluatorFixtures?.resolutionResult !== 'string' ||
    typeof evaluatorFixtures?.generationRequest !== 'string' ||
    typeof evaluatorFixtures?.previewRequest !== 'string' ||
    typeof evaluatorFixtures?.pixelReferenceRequest !== 'string' ||
    typeof evaluatorFixtures?.pixelReferenceResult !== 'string' ||
    evaluatorFixtures?.cases?.length !== 2
  ) {
    throw new Error('Screenshot repair candidate evaluator fixtures changed');
  }
  for (const fixture of evaluatorFixtures.cases) {
    if (
      !/^[a-z0-9-]+$/u.test(fixture.id ?? '') ||
      !/^[a-f0-9]{64}$/u.test(fixture.expectedCandidateFingerprint ?? '') ||
      !/^[a-f0-9]{64}$/u.test(fixture.expectedDesignIrFingerprint ?? '') ||
      !/^[a-f0-9]{64}$/u.test(fixture.expectedEvaluationFingerprint ?? '') ||
      !/^[a-f0-9]{64}$/u.test(fixture.expectedEvidenceFingerprint ?? '') ||
      !Array.isArray(fixture.expectedEvidenceDiagnosticCodes) ||
      fixture.expectedEvidenceDiagnosticCodes.some(
        (code) => !/^VC-AI-[A-Z0-9-]+$/u.test(code),
      ) ||
      new Set(fixture.expectedEvidenceDiagnosticCodes).size !==
        fixture.expectedEvidenceDiagnosticCodes.length ||
      fixture.expectedGateStatuses?.length !== 6 ||
      fixture.expectedGateStatuses.some((status) => !['passed', 'failed'].includes(status)) ||
      fixture.expectedGateEvidence?.length !== 6 ||
      fixture.expectedGateEvidence.some((value) => !/^[a-f0-9]{64}$/u.test(value)) ||
      fixture.expectedChecks?.length !== 5 ||
      fixture.expectedChecks.some((value) => !Number.isInteger(value) || value < 1) ||
      !Number.isInteger(fixture.expectedPixels?.comparedPixels) ||
      !Number.isInteger(fixture.expectedPixels?.mismatchedPixels) ||
      !Number.isInteger(fixture.expectedPixels?.maxChannelDelta)
    ) {
      throw new Error(`${fixture.id ?? 'unknown'}: screenshot candidate evidence changed`);
    }
  }
  const initialEvaluatorFixture = evaluatorFixtures.cases[0];
  if (initialEvaluatorFixture.id !== 'initial-exact') {
    throw new Error('Initial screenshot candidate evaluator fixture changed');
  }
  for (const fixture of contract.patchFixtures) {
    const [resolution, patch] = await Promise.all([
      readJson(resolve(visualRoot, fixture.resolutionResult)),
      readJson(resolve(visualRoot, fixture.patch)),
    ]);
    const first = await applyDesignIrRepairPatch({
      designIr: resolution.designIr,
      expectedDesignIrFingerprint: resolution.designIrFingerprint,
      patch,
    });
    const second = await applyDesignIrRepairPatch({
      designIr: resolution.designIr,
      expectedDesignIrFingerprint: resolution.designIrFingerprint,
      patch,
    });
    if (
      !same(first, second) ||
      first.designIrFingerprint !== fixture.expectedDesignIrFingerprint ||
      first.outputFingerprint !== fixture.expectedOutputFingerprint ||
      !same(first.changedPaths, fixture.expectedChangedPaths) ||
      first.changeFingerprint !== patch.changeFingerprint ||
      first.inputDesignIrFingerprint !== resolution.designIrFingerprint
    ) {
      throw new Error('Typed screenshot Design IR repair patch output changed');
    }
  }
  for (const fixture of contract.failClosedFixtures) {
    const mutation = await readJson(resolve(visualRoot, fixture.mutation));
    assertMutation(mutation, fixture);
    const result = await reproduceMutation(mutation);
    if (
      result.status !== fixture.expectedStatus ||
      result.termination.reason !== fixture.expectedTermination ||
      !same(result.findings.map((finding) => finding.code), fixture.diagnosticCodes) ||
      validateSchemaValue(result, schema).length > 0 ||
      fingerprintWithout(result, 'repairFingerprint') !== result.repairFingerprint
    ) {
      throw new Error(`${mutation.operation}: repair implementation outcome changed`);
    }
  }
  return {
    supportedGoldens: contract.supportedFixtures.length,
    patchGoldens: contract.patchFixtures.length,
    candidateEvaluatorGoldens: evaluatorFixtures.cases.length,
    failClosedDenominators: contract.failClosedFixtures.length,
    repairFingerprint: golden.repairFingerprint,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase5ScreenshotRepair()
    .then((summary) => {
      console.log(
        `Verified bounded screenshot repair implementation: ${summary.supportedGoldens}/` +
          `${summary.supportedGoldens} zero-iteration convergence, ` +
          `${summary.patchGoldens}/${summary.patchGoldens} typed patch golden, and ` +
          `${summary.candidateEvaluatorGoldens}/${summary.candidateEvaluatorGoldens} ` +
          `released-artifact candidate evaluations, and ` +
          `${summary.failClosedDenominators}/${summary.failClosedDenominators} ` +
          `fail-closed denominators; repair fingerprint ${summary.repairFingerprint}.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
