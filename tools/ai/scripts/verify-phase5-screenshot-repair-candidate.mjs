#!/usr/bin/env node
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {validateSchemaValue} from './schema-validator.mjs';
import {evaluateScreenshotRepairCandidate} from './screenshot-repair-candidate-evaluator.mjs';

const visualRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));
const contractPath = resolve(visualRoot, 'screenshot-repair-contract.json');
const schemaPath = fileURLToPath(new URL('../contracts/screenshot-repair.schema.json', import.meta.url));
const gateOrder = Object.freeze([
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

export async function verifyPhase5ScreenshotRepairCandidate() {
  const [contract, schema] = await Promise.all([readJson(contractPath), readJson(schemaPath)]);
  const fixtures = contract.candidateEvaluatorFixtures;
  const [resolutionResult, generationRequest, previewRequest, referenceRequest, referenceResult] =
    await Promise.all([
      fixtures.resolutionResult,
      fixtures.generationRequest,
      fixtures.previewRequest,
      fixtures.pixelReferenceRequest,
      fixtures.pixelReferenceResult,
    ].map((path) => readJson(resolve(visualRoot, path))));
  const initialGolden = await readJson(resolve(
    visualRoot,
    contract.supportedFixtures[0].result,
  ));
  const results = [];
  for (const fixture of fixtures.cases) {
    const patch = fixture.patch ? await readJson(resolve(visualRoot, fixture.patch)) : undefined;
    const evaluation = await evaluateScreenshotRepairCandidate({
      resolutionResult,
      generationRequest,
      previewBindings: previewRequest.bindings,
      pixelReference: {request: referenceRequest, result: referenceResult},
      ...(patch === undefined ? {} : {patch}),
    }, {
      requestId: `verify-screenshot-repair-${fixture.id}`,
      limits: {
        maxSourceBytes: 262144,
        timeoutMs: 120000,
        maxOutputBytes: 1048576,
      },
    });
    const violations = validateSchemaValue(
      evaluation,
      schema.$defs.candidateEvaluation,
      schema,
    );
    if (violations.length > 0) {
      throw new Error(`${fixture.id}: candidate evaluation violates repair schema v2`);
    }
    if (
      evaluation.candidateFingerprint !== fixture.expectedCandidateFingerprint ||
      evaluation.designIrFingerprint !== fixture.expectedDesignIrFingerprint ||
      evaluation.evaluationFingerprint !== fixture.expectedEvaluationFingerprint ||
      !same(evaluation.gates.map((gate) => gate.name), gateOrder) ||
      !same(evaluation.gates.map((gate) => gate.status), fixture.expectedGateStatuses) ||
      !same(
        evaluation.gates.map((gate) => gate.evidenceFingerprint),
        fixture.expectedGateEvidence,
      ) ||
      !same(evaluation.gates.slice(0, 5).map((gate) => gate.totalChecks), fixture.expectedChecks) ||
      !same({
        comparedPixels: evaluation.gates[5].comparedPixels,
        mismatchedPixels: evaluation.gates[5].mismatchedPixels,
        maxChannelDelta: evaluation.gates[5].maxChannelDelta,
      }, fixture.expectedPixels)
    ) {
      throw new Error(`${fixture.id}: source-bound candidate evaluation changed`);
    }
    if (fixture.id === 'initial-exact' && !same(evaluation, initialGolden.initial)) {
      throw new Error('Initial source-bound candidate evaluation differs from repair golden');
    }
    results.push(evaluation);
  }
  return {
    evaluatedCandidates: results.length,
    exactCandidates: results.filter((result) => result.gates[5].status === 'passed').length,
    mismatchedCandidates: results.filter((result) => result.gates[5].status === 'failed').length,
    patchedMismatchedPixels: results[1].gates[5].mismatchedPixels,
    patchedEvaluationFingerprint: results[1].evaluationFingerprint,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase5ScreenshotRepairCandidate()
    .then((summary) => {
      console.log(
        `Verified source-bound screenshot repair candidates: ${summary.evaluatedCandidates}/` +
          `${summary.evaluatedCandidates} evaluated, ${summary.exactCandidates}/1 exact, and ` +
          `${summary.mismatchedCandidates}/1 bounded pixel mismatch with ` +
          `${summary.patchedMismatchedPixels} changed pixels; patched evaluation ` +
          `${summary.patchedEvaluationFingerprint}.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
