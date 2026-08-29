import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {
  fingerprintRepairValue,
  orchestrateScreenshotRepair,
  sealRepairPatch,
} from './repair-orchestrator.mjs';
import {
  createScreenshotRepairCandidateEvaluator,
  createScreenshotRepairCandidateSession,
  evaluateScreenshotRepairCandidate,
  evaluateScreenshotRepairCandidateWithEvidence,
  ScreenshotRepairCandidateEvaluationError,
} from './screenshot-repair-candidate-evaluator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';

const fixtureRoot = new URL('../evaluation/fixtures/visual/', import.meta.url);
const [resolutionResult, baseGenerationRequest, previewRequest, referenceRequest, referenceResult] =
  await Promise.all([
    'screenshot-resolution/wireframe.result.json',
    'screenshot-generation/wireframe.request.json',
    'screenshot-render/wireframe.preview-request.json',
    'screenshot-pixel/pixel-reference.request.json',
    'screenshot-pixel/pixel-reference.result.json',
  ].map((path) => readFile(new URL(path, fixtureRoot), 'utf8').then(JSON.parse)));
const evidenceSchema = JSON.parse(await readFile(new URL(
  '../contracts/screenshot-repair-candidate-evidence.schema.json',
  import.meta.url,
), 'utf8'));

const fingerprints = Object.freeze({
  compilation: '1'.repeat(64),
  kotlin: '2'.repeat(64),
  render: '3'.repeat(64),
  layout: '4'.repeat(64),
  pixels: '5'.repeat(64),
});

function input(patch) {
  return {
    resolutionResult: structuredClone(resolutionResult),
    generationRequest: structuredClone(baseGenerationRequest),
    previewBindings: structuredClone(previewRequest.bindings),
    pixelReference: {
      request: structuredClone(referenceRequest),
      result: structuredClone(referenceResult),
    },
    ...(patch === undefined ? {} : {patch}),
  };
}

function compileResult(status = 'success') {
  return {
    status,
    evidence: {
      level: 'compiled',
      outputFingerprint: status === 'success' ? fingerprints.compilation : undefined,
    },
    diagnostics: status === 'success' ? [] : [{code: 'VC-AI-COMPILER-ERROR'}],
    data: {kotlinFingerprint: fingerprints.kotlin},
  };
}

function renderResult(status = 'success') {
  return {
    status,
    evidence: {
      level: 'rendered',
      outputFingerprint: status === 'success' ? fingerprints.render : undefined,
    },
    diagnostics: status === 'success' ? [] : [{code: 'VC-AI-PREVIEW-BUILD-FAILED'}],
    data: status === 'success' ? {preview: {targetId: 'test-preview'}} : {},
  };
}

function check(id, category, status = 'passed') {
  return {id, category, status};
}

function layoutResult({semantic = 'passed', structure = 'passed'} = {}) {
  const checks = [
    check('semantic.visible', 'semantic', semantic),
    check('semantic.text', 'semantic', semantic),
    check('identity.key', 'identity'),
    check('structure.parent', 'structure'),
    check('geometry.width', 'geometry', structure),
  ];
  const failed = checks.filter((item) => item.status === 'failed');
  return {
    status: failed.length === 0 ? 'success' : 'failed',
    evidenceLevel: failed.length === 0 ? 'compared' : 'rendered',
    diagnostics: [],
    comparison: {
      status: failed.length === 0 ? 'passed' : 'failed',
      comparisonFingerprint: fingerprints.layout,
      nodes: [{checks}],
      findings: failed.map((item) => ({checkId: item.id})),
    },
  };
}

function pixelResult(mismatchedPixels = 0) {
  return {
    status: mismatchedPixels === 0 ? 'success' : 'failed',
    evidenceLevel: mismatchedPixels === 0 ? 'compared' : 'rendered',
    diagnostics: [],
    comparison: {
      status: mismatchedPixels === 0 ? 'passed' : 'failed',
      comparisonFingerprint: fingerprints.pixels,
      metrics: {
        comparedPixels: 100,
        mismatchedPixels,
        maxChannelDelta: mismatchedPixels === 0 ? 0 : 7,
      },
    },
  };
}

function adapters(overrides = {}) {
  return {
    generate: async ({generationRequest}) => generationRequest.mode === 'compile'
      ? compileResult()
      : renderResult(),
    compare: async () => layoutResult(),
    comparePixels: async () => pixelResult(),
    ...overrides,
  };
}

test('evaluates the six deterministic gates in order and preserves separate evidence', async () => {
  const modes = [];
  const result = await evaluateScreenshotRepairCandidate(input(), adapters({
    generate: async ({generationRequest}) => {
      modes.push(generationRequest.mode);
      return generationRequest.mode === 'compile' ? compileResult() : renderResult();
    },
  }));
  assert.deepEqual(modes, ['compile', 'render']);
  assert.deepEqual(result.gates.map((gate) => [gate.name, gate.status]), [
    ['safety', 'passed'],
    ['compilation', 'passed'],
    ['render', 'passed'],
    ['semantics', 'passed'],
    ['structure', 'passed'],
    ['exact-pixels', 'passed'],
  ]);
  assert.equal(result.gates[1].evidenceFingerprint, fingerprints.compilation);
  assert.equal(result.gates[2].evidenceFingerprint, fingerprints.render);
  assert.equal(result.gates[3].passedChecks, 2);
  assert.equal(result.gates[4].passedChecks, 3);
  assert.equal(result.gates[5].comparedPixels, 100);
});

test('returns one bounded content-addressed evidence record without source or image bytes', async () => {
  const result = await evaluateScreenshotRepairCandidateWithEvidence(input(), adapters());
  assert.equal(result.evidence.status, 'complete');
  assert.equal(result.evidence.candidateEvaluation.evaluationFingerprint,
    result.evaluation.evaluationFingerprint);
  assert.equal(result.evidence.lineage.inputDesignIrFingerprint,
    resolutionResult.designIrFingerprint);
  assert.equal(result.evidence.lineage.changeFingerprint, null);
  assert.equal(result.evidence.diagnostics.length, 6);
  assert.equal(result.evidence.layoutComparison.comparisonFingerprint, fingerprints.layout);
  assert.equal(result.evidence.pixelComparison.comparisonFingerprint, fingerprints.pixels);
  const copy = structuredClone(result.evidence);
  delete copy.evidenceFingerprint;
  assert.equal(result.evidence.evidenceFingerprint, fingerprintRepairValue(copy));
  assert.deepEqual(validateSchemaValue(result.evidence, evidenceSchema), []);
  const encoded = JSON.stringify(result.evidence);
  assert.doesNotMatch(encoded, /generatedKotlin|image\/png|base64/u);
});

test('keeps immutable candidate evidence addressable inside one evaluator session', async () => {
  const session = createScreenshotRepairCandidateSession(input(), adapters());
  const initial = await session.evaluateInitial();
  const first = session.readEvidence(initial.candidateFingerprint);
  assert.equal(first.candidateEvaluation.candidateFingerprint, initial.candidateFingerprint);
  first.designIr.documentId = 'mutated-outside-session';
  assert.equal(
    session.readEvidence(initial.candidateFingerprint).designIr.documentId,
    resolutionResult.designIr.documentId,
  );
  assert.equal(session.readEvidence('f'.repeat(64)), null);
});

test('applies a typed patch and rebinds compile and render lineage to the candidate', async () => {
  const patch = sealRepairPatch([{
    op: 'replace-field',
    nodeId: 'wireframe-title',
    collection: 'properties',
    name: 'text',
    value: {kind: 'literal', value: 'Hello'},
  }]);
  const invocations = [];
  const result = await evaluateScreenshotRepairCandidate(input(patch), adapters({
    generate: async (arguments_) => {
      invocations.push(structuredClone(arguments_));
      return arguments_.generationRequest.mode === 'compile' ? compileResult() : renderResult();
    },
  }));
  assert.notEqual(result.designIrFingerprint, resolutionResult.designIrFingerprint);
  assert.equal(invocations.length, 2);
  for (const invocation of invocations) {
    assert.equal(
      invocation.resolutionResult.designIr.roots[0].children[0].properties[0].value.value,
      'Hello',
    );
    assert.equal(
      invocation.generationRequest.input.resolvedDesignIrFingerprint,
      result.designIrFingerprint,
    );
    assert.equal(
      invocation.generationRequest.input.resolutionResultFingerprint,
      invocation.resolutionResult.resultFingerprint,
    );
  }
});

test('binds the real candidate evaluator into one bounded orchestration iteration', async () => {
  const patch = sealRepairPatch([{
    op: 'replace-field',
    nodeId: 'wireframe-title',
    collection: 'properties',
    name: 'text',
    value: {kind: 'literal', value: 'Hello'},
  }]);
  const stageAdapters = adapters({
    compare: async ({designIr}) => {
      const result = layoutResult();
      result.comparison.patched =
        designIr.roots[0].children[0].properties[0].value.value === 'Hello';
      return result;
    },
    comparePixels: async ({semanticComparison}) =>
      pixelResult(semanticComparison.patched ? 0 : 1),
  });
  const initial = await evaluateScreenshotRepairCandidate(input(), stageAdapters);
  const evaluatePatch = createScreenshotRepairCandidateEvaluator(input(), stageAdapters);
  const result = await orchestrateScreenshotRepair({
    initial,
    proposePatch: async () => patch,
    evaluatePatch,
  });
  assert.equal(result.status, 'converged');
  assert.equal(result.termination.reason, 'exact-pass');
  assert.equal(result.iterations.length, 1);
  assert.equal(result.final.designIrFingerprint, result.iterations[0].candidate.designIrFingerprint);
  assert.equal(result.final.gates[5].mismatchedPixels, 0);
});

test('stops after a compilation failure without invoking render or comparison', async () => {
  let calls = 0;
  const result = await evaluateScreenshotRepairCandidate(input(), adapters({
    generate: async () => {
      calls += 1;
      return compileResult('failed');
    },
    compare: async () => { throw new Error('must not compare'); },
    comparePixels: async () => { throw new Error('must not compare pixels'); },
  }));
  assert.equal(calls, 1);
  assert.equal(result.gates[1].status, 'failed');
  assert.ok(result.gates.slice(2).every((gate) => gate.status === 'not-run'));
});

test('keeps Preview failure distinct from successful compilation', async () => {
  let calls = 0;
  const result = await evaluateScreenshotRepairCandidate(input(), adapters({
    generate: async ({generationRequest}) => {
      calls += 1;
      return generationRequest.mode === 'compile' ? compileResult() : renderResult('failed');
    },
  }));
  assert.equal(calls, 2);
  assert.equal(result.gates[1].status, 'passed');
  assert.equal(result.gates[2].status, 'failed');
  assert.ok(result.gates.slice(3).every((gate) => gate.status === 'not-run'));
});

test('short-circuits structure and pixels after a semantic failure', async () => {
  let pixels = 0;
  const result = await evaluateScreenshotRepairCandidate(input(), adapters({
    compare: async () => layoutResult({semantic: 'failed'}),
    comparePixels: async () => {
      pixels += 1;
      return pixelResult();
    },
  }));
  assert.equal(pixels, 0);
  assert.equal(result.gates[3].status, 'failed');
  assert.equal(result.gates[3].passedChecks, 0);
  assert.equal(result.gates[3].totalChecks, 2);
  assert.equal(result.gates[4].status, 'not-run');
  assert.equal(result.gates[5].status, 'not-run');
});

test('short-circuits exact pixels after structural or geometry failure', async () => {
  let pixels = 0;
  const result = await evaluateScreenshotRepairCandidate(input(), adapters({
    compare: async () => layoutResult({structure: 'failed'}),
    comparePixels: async () => {
      pixels += 1;
      return pixelResult();
    },
  }));
  assert.equal(pixels, 0);
  assert.equal(result.gates[3].status, 'passed');
  assert.equal(result.gates[4].status, 'failed');
  assert.equal(result.gates[4].passedChecks, 2);
  assert.equal(result.gates[4].totalChecks, 3);
  assert.equal(result.gates[5].status, 'not-run');
});

test('retains an exact pixel mismatch without turning it into an aggregate score', async () => {
  const result = await evaluateScreenshotRepairCandidate(input(), adapters({
    comparePixels: async () => pixelResult(4),
  }));
  assert.deepEqual(result.gates[5], {
    name: 'exact-pixels',
    status: 'failed',
    comparedPixels: 100,
    mismatchedPixels: 4,
    maxChannelDelta: 7,
    evidenceFingerprint: fingerprints.pixels,
  });
});

test('represents unavailable pixel evidence as a failed zero-denominator gate', async () => {
  const result = await evaluateScreenshotRepairCandidate(input(), adapters({
    comparePixels: async () => ({
      status: 'unsupported',
      evidenceLevel: 'rendered',
      diagnostics: [{code: 'VC-AI-PIXEL-CONFIGURATION-MISMATCH'}],
    }),
  }));
  assert.deepEqual(result.gates[5], {
    name: 'exact-pixels',
    status: 'failed',
    comparedPixels: 0,
    mismatchedPixels: 0,
    maxChannelDelta: 0,
    evidenceFingerprint: result.gates[5].evidenceFingerprint,
  });
  assert.match(result.gates[5].evidenceFingerprint, /^[a-f0-9]{64}$/u);
});

test('fails the safety gate for a no-op patch before source generation', async () => {
  const patch = sealRepairPatch([{
    op: 'replace-field',
    nodeId: 'wireframe-title',
    collection: 'properties',
    name: 'text',
    value: {kind: 'literal', value: 'Welcome'},
  }]);
  let generations = 0;
  const result = await evaluateScreenshotRepairCandidate(input(patch), adapters({
    generate: async () => {
      generations += 1;
      return compileResult();
    },
  }));
  assert.equal(generations, 0);
  assert.equal(result.gates[0].status, 'failed');
  assert.ok(result.gates.slice(1).every((gate) => gate.status === 'not-run'));
});

test('honors cancellation before any candidate evidence is evaluated', async () => {
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(
    evaluateScreenshotRepairCandidate(input(), {...adapters(), signal: controller.signal}),
    (error) => {
      assert.ok(error instanceof ScreenshotRepairCandidateEvaluationError);
      assert.equal(error.code, 'VC-AI-REPAIR-CANCELLED');
      return true;
    },
  );
});
