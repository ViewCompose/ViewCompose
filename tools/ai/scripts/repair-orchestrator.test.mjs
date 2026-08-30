import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import assert from 'node:assert/strict';
import {validateSchemaValue} from './schema-validator.mjs';
import {
  orchestrateScreenshotRepair,
  sealRepairEvaluation,
  sealRepairPatch,
} from './repair-orchestrator.mjs';

const schema = JSON.parse(await readFile(
  new URL('../contracts/screenshot-repair.schema.json', import.meta.url),
));
const exactGolden = JSON.parse(await readFile(
  new URL('../evaluation/fixtures/visual/screenshot-repair/initial-exact.result.json', import.meta.url),
));

function hash(value) {
  return createHash('sha256').update(String(value)).digest('hex');
}

function baseGate(name, status = 'passed', passedChecks = 1, totalChecks = 1, seed = name) {
  return {name, status, passedChecks, totalChecks, evidenceFingerprint: hash(seed)};
}

function pixelGate(mismatchedPixels, seed = `pixels-${mismatchedPixels}`) {
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
    gates[
      ['safety', 'compilation', 'render', 'semantics', 'structure', 'exact-pixels'].indexOf(name)
    ] = gate;
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

function assertValid(result) {
  assert.deepEqual(validateSchemaValue(result, schema), []);
  const copy = structuredClone(result);
  delete copy.repairFingerprint;
  assert.equal(result.repairFingerprint, hash(JSON.stringify(copy)));
}

test('returns the exact zero-iteration convergence golden', async () => {
  const result = await orchestrateScreenshotRepair({initial: exactGolden.initial});
  assert.deepEqual(result, exactGolden);
  assertValid(result);
});

test('returns incomplete when no typed change can improve the first failed gate', async () => {
  const initial = evaluation('no-change', 1);
  let evaluated = false;
  const result = await orchestrateScreenshotRepair({
    initial,
    proposePatch: async () => null,
    evaluatePatch: async () => {
      evaluated = true;
    },
  });
  assert.equal(evaluated, false);
  assert.equal(result.status, 'incomplete');
  assert.equal(result.termination.reason, 'no-eligible-change');
  assert.deepEqual(result.findings.map((item) => item.code), [
    'VC-AI-REPAIR-NO-ELIGIBLE-CHANGE',
  ]);
  assertValid(result);
});

test('rejects a candidate that regresses a passed semantic gate', async () => {
  const initial = evaluation('regression-initial', 2);
  const semanticsFailed = baseGate('semantics', 'failed', 13, 14, 'semantics-failed');
  const notRunStructure = baseGate('structure', 'not-run', 0, 0, 'structure-not-run');
  const notRunPixels = {
    name: 'exact-pixels',
    status: 'not-run',
    comparedPixels: 0,
    mismatchedPixels: 0,
    maxChannelDelta: 0,
    evidenceFingerprint: hash('pixels-not-run'),
  };
  const candidate = evaluation('regression-candidate', 0, {
    semantics: semanticsFailed,
    structure: notRunStructure,
    'exact-pixels': notRunPixels,
  });
  const result = await orchestrateScreenshotRepair({
    initial,
    proposePatch: async () => patch('regression'),
    evaluatePatch: async () => candidate,
  });
  assert.equal(result.status, 'blocked');
  assert.equal(result.termination.reason, 'regression');
  assert.equal(result.iterations[0].disposition, 'rejected-regression');
  assert.equal(result.final.candidateFingerprint, initial.candidateFingerprint);
  assertValid(result);
});

test('stops oscillation on a repeated candidate without accepting it', async () => {
  const initial = evaluation('oscillation', 2);
  const result = await orchestrateScreenshotRepair({
    initial,
    proposePatch: async () => patch('oscillation'),
    evaluatePatch: async () => structuredClone(initial),
  });
  assert.equal(result.status, 'blocked');
  assert.equal(result.termination.reason, 'oscillation');
  assert.equal(result.iterations[0].disposition, 'rejected-oscillation');
  assert.equal(result.termination.acceptedCandidates, 0);
  assertValid(result);
});

test('accepts only strict pixel improvements and stops at five iterations', async () => {
  const initial = evaluation('limit-initial', 6);
  let proposed = 0;
  let evaluated = 0;
  const result = await orchestrateScreenshotRepair({
    initial,
    proposePatch: async ({iteration, reasonCode}) => {
      assert.equal(reasonCode, 'exact-pixels');
      proposed += 1;
      return patch(`limit-${iteration}`);
    },
    evaluatePatch: async ({iteration}) => {
      evaluated += 1;
      return evaluation(`limit-${iteration}`, 6 - iteration);
    },
  });
  assert.equal(proposed, 5);
  assert.equal(evaluated, 5);
  assert.equal(result.status, 'incomplete');
  assert.equal(result.termination.reason, 'max-iterations');
  assert.equal(result.termination.acceptedCandidates, 5);
  assert.equal(result.final.gates[5].mismatchedPixels, 1);
  assertValid(result);
});

test('converges after an exact candidate and preserves before-after evidence', async () => {
  const initial = evaluation('converge-initial', 1);
  const candidate = evaluation('converge-final', 0);
  const result = await orchestrateScreenshotRepair({
    initial,
    proposePatch: async () => patch('converged'),
    evaluatePatch: async () => candidate,
  });
  assert.equal(result.status, 'converged');
  assert.equal(result.termination.reason, 'exact-pass');
  assert.equal(result.termination.iterationCount, 1);
  assert.equal(result.iterations[0].beforeCandidateFingerprint, initial.candidateFingerprint);
  assert.equal(result.final.candidateFingerprint, candidate.candidateFingerprint);
  assertValid(result);
});

test('rejects a non-improving candidate and retains the previous one', async () => {
  const initial = evaluation('no-improvement-initial', 2);
  const candidate = evaluation('no-improvement-candidate', 2);
  const result = await orchestrateScreenshotRepair({
    initial,
    proposePatch: async () => patch('no-improvement'),
    evaluatePatch: async () => candidate,
  });
  assert.equal(result.status, 'incomplete');
  assert.equal(result.termination.reason, 'no-eligible-change');
  assert.equal(result.iterations[0].disposition, 'rejected-no-improvement');
  assert.equal(result.final.candidateFingerprint, initial.candidateFingerprint);
  assertValid(result);
});

test('retains unavailable exact-pixel evidence as a failed non-improving gate', async () => {
  const unavailable = {
    name: 'exact-pixels',
    status: 'failed',
    comparedPixels: 0,
    mismatchedPixels: 0,
    maxChannelDelta: 0,
    evidenceFingerprint: hash('pixel-evidence-unavailable'),
  };
  const initial = evaluation('pixel-evidence-unavailable-initial', 1, {
    'exact-pixels': unavailable,
  });
  const candidate = evaluation('pixel-evidence-unavailable-candidate', 1, {
    'exact-pixels': unavailable,
  });
  const result = await orchestrateScreenshotRepair({
    initial,
    proposePatch: async () => patch('pixel-evidence-unavailable'),
    evaluatePatch: async () => candidate,
  });
  assert.equal(result.status, 'incomplete');
  assert.equal(result.termination.reason, 'no-eligible-change');
  assert.equal(result.final.candidateFingerprint, initial.candidateFingerprint);
  assertValid(result);
});

test('short-circuits an initial safety failure before repair callbacks', async () => {
  const safetyFailed = baseGate('safety', 'failed', 0, 1, 'unsafe');
  const notRun = (name) => baseGate(name, 'not-run', 0, 0, `${name}-not-run`);
  const initial = sealRepairEvaluation({
    candidateFingerprint: hash('unsafe-candidate'),
    designIrFingerprint: hash('unsafe-design'),
    gates: [
      safetyFailed,
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
        evidenceFingerprint: hash('unsafe-pixels'),
      },
    ],
  });
  let callbacks = 0;
  const result = await orchestrateScreenshotRepair({
    initial,
    proposePatch: async () => { callbacks += 1; },
    evaluatePatch: async () => { callbacks += 1; },
  });
  assert.equal(callbacks, 0);
  assert.equal(result.status, 'blocked');
  assert.equal(result.termination.reason, 'safety-failure');
  assertValid(result);
});

test('rejects executable and duplicate typed patch operations before evaluation', async () => {
  const initial = evaluation('invalid-patch', 1);
  let evaluated = false;
  const executable = sealRepairPatch([{
    op: 'replace-field',
    nodeId: 'wireframe-title',
    collection: 'properties',
    name: 'text',
    value: {kind: 'expression', language: 'kotlin', source: 'runProject()'},
  }]);
  const result = await orchestrateScreenshotRepair({
    initial,
    proposePatch: async () => executable,
    evaluatePatch: async () => { evaluated = true; },
  });
  assert.equal(evaluated, false);
  assert.equal(result.status, 'blocked');
  assert.equal(result.termination.reason, 'input-invalid');
  assertValid(result);
  const operation = {
    op: 'replace-field',
    nodeId: 'wireframe-title',
    collection: 'properties',
    name: 'text',
    value: {kind: 'literal', value: 'Duplicate'},
  };
  const duplicateResult = await orchestrateScreenshotRepair({
    initial,
    proposePatch: async () => sealRepairPatch([operation, operation]),
    evaluatePatch: async () => { evaluated = true; },
  });
  assert.equal(evaluated, false);
  assert.equal(duplicateResult.termination.reason, 'input-invalid');
  assertValid(duplicateResult);
});

test('honors cancellation before invoking either repair callback', async () => {
  const initial = evaluation('cancelled', 1);
  const controller = new AbortController();
  controller.abort();
  let callbacks = 0;
  const result = await orchestrateScreenshotRepair({
    initial,
    proposePatch: async () => { callbacks += 1; },
    evaluatePatch: async () => { callbacks += 1; },
  }, {signal: controller.signal});
  assert.equal(callbacks, 0);
  assert.equal(result.status, 'cancelled');
  assert.equal(result.termination.reason, 'cancelled');
  assertValid(result);
});

test('maps cancellation thrown inside an injected boundary to a cancelled result', async () => {
  const controller = new AbortController();
  const initial = evaluation('cancel-inside-boundary', 1);
  const result = await orchestrateScreenshotRepair({
    initial,
    proposePatch: async () => {
      controller.abort();
      throw new Error('cancelled');
    },
    evaluatePatch: async () => { throw new Error('must not evaluate'); },
  }, {signal: controller.signal});
  assert.equal(result.status, 'cancelled');
  assert.equal(result.termination.reason, 'cancelled');
  assertValid(result);
});

test('returns a schema-valid blocked result for an invalid initial candidate', async () => {
  const result = await orchestrateScreenshotRepair({initial: {candidateFingerprint: 'invalid'}});
  assert.equal(result.status, 'blocked');
  assert.equal(result.termination.reason, 'input-invalid');
  assert.deepEqual(result.findings.map((item) => item.code), ['VC-AI-REPAIR-INPUT-INVALID']);
  assertValid(result);
});
