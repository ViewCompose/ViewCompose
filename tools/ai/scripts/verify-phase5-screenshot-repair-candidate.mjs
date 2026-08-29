#!/usr/bin/env node
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {validateSchemaValue} from './schema-validator.mjs';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {
  evaluateScreenshotRepairCandidateWithEvidence,
} from './screenshot-repair-candidate-evaluator.mjs';

const visualRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));
const contractPath = resolve(visualRoot, 'screenshot-repair-contract.json');
const contractRoot = new URL('../contracts/', import.meta.url);
const schemaPaths = Object.freeze({
  repair: new URL('screenshot-repair.schema.json', contractRoot),
  evidence: new URL('screenshot-repair-candidate-evidence.schema.json', contractRoot),
  designIr: new URL('design-ir.schema.json', contractRoot),
  layout: new URL('layout-comparison.schema.json', contractRoot),
  pixels: new URL('screenshot-pixel-comparison.schema.json', contractRoot),
});
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

function assertValid(value, schema, rootSchema, label) {
  const violations = validateSchemaValue(value, schema, rootSchema ?? schema);
  if (violations.length > 0) {
    throw new Error(`${label} violates its schema: ${violations[0].path}`);
  }
}

function evidenceDiagnosticCodes(evidence) {
  return [...new Set(evidence.diagnostics.flatMap((item) => item.codes))].sort();
}

function assertCandidateEvidence({fixture, evidence, evaluation, resolutionResult, patch}, schemas) {
  if (evidence === null) {
    throw new Error(`${fixture.id}: accepted candidate did not retain evidence`);
  }
  assertValid(evidence, schemas.evidence, undefined, `${fixture.id}: candidate evidence`);
  assertValid(
    evidence.candidateEvaluation,
    schemas.repair.$defs.candidateEvaluation,
    schemas.repair,
    `${fixture.id}: retained candidate evaluation`,
  );
  assertValid(evidence.designIr, schemas.designIr, undefined, `${fixture.id}: retained Design IR`);
  assertValid(
    evidence.layoutComparison,
    schemas.layout,
    undefined,
    `${fixture.id}: retained layout comparison`,
  );
  assertValid(
    evidence.pixelComparison,
    schemas.pixels,
    undefined,
    `${fixture.id}: retained pixel comparison`,
  );
  const unsigned = structuredClone(evidence);
  delete unsigned.evidenceFingerprint;
  if (
    evidence.evidenceFingerprint !== fingerprintRepairValue(unsigned) ||
    evidence.evidenceFingerprint !== fixture.expectedEvidenceFingerprint ||
    !same(evidence.candidateEvaluation, evaluation) ||
    evidence.status !== 'complete' ||
    evidence.lineage.baseResolutionResultFingerprint !== resolutionResult.resultFingerprint ||
    evidence.lineage.inputDesignIrFingerprint !== resolutionResult.designIrFingerprint ||
    evidence.lineage.candidateDesignIrFingerprint !== evaluation.designIrFingerprint ||
    evidence.lineage.changeFingerprint !== (patch?.changeFingerprint ?? null) ||
    !same(evidence.diagnostics.map((item) => item.gate), gateOrder) ||
    !same(evidenceDiagnosticCodes(evidence), fixture.expectedEvidenceDiagnosticCodes)
  ) {
    throw new Error(`${fixture.id}: retained candidate evidence changed`);
  }
  const encoded = JSON.stringify(evidence);
  if (/generatedKotlin|image\/png|pngBase64|imageBase64|data:image/u.test(encoded)) {
    throw new Error(`${fixture.id}: candidate evidence retained generated source or image bytes`);
  }
}

export async function verifyPhase5ScreenshotRepairCandidate() {
  const [contract, schemas] = await Promise.all([
    readJson(contractPath),
    Promise.all(Object.entries(schemaPaths).map(async ([name, path]) => [name, await readJson(path)]))
      .then(Object.fromEntries),
  ]);
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
    const result = await evaluateScreenshotRepairCandidateWithEvidence({
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
    const {evaluation, evidence} = result;
    assertValid(
      evaluation,
      schemas.repair.$defs.candidateEvaluation,
      schemas.repair,
      `${fixture.id}: candidate evaluation`,
    );
    const actual = {
      candidateFingerprint: evaluation.candidateFingerprint,
      designIrFingerprint: evaluation.designIrFingerprint,
      evaluationFingerprint: evaluation.evaluationFingerprint,
      gateNames: evaluation.gates.map((gate) => gate.name),
      gateStatuses: evaluation.gates.map((gate) => gate.status),
      gateEvidence: evaluation.gates.map((gate) => gate.evidenceFingerprint),
      checks: evaluation.gates.slice(0, 5).map((gate) => gate.totalChecks),
      pixels: {
        comparedPixels: evaluation.gates[5].comparedPixels,
        mismatchedPixels: evaluation.gates[5].mismatchedPixels,
        maxChannelDelta: evaluation.gates[5].maxChannelDelta,
      },
    };
    const expected = {
      candidateFingerprint: fixture.expectedCandidateFingerprint,
      designIrFingerprint: fixture.expectedDesignIrFingerprint,
      evaluationFingerprint: fixture.expectedEvaluationFingerprint,
      gateNames: gateOrder,
      gateStatuses: fixture.expectedGateStatuses,
      gateEvidence: fixture.expectedGateEvidence,
      checks: fixture.expectedChecks,
      pixels: fixture.expectedPixels,
    };
    const changed = Object.keys(expected).filter((key) => !same(actual[key], expected[key]));
    if (changed.length > 0) {
      throw new Error(
        `${fixture.id}: source-bound candidate evaluation changed: ${changed.join(', ')}`,
      );
    }
    if (fixture.id === 'initial-exact' && !same(evaluation, initialGolden.initial)) {
      throw new Error('Initial source-bound candidate evaluation differs from repair golden');
    }
    assertCandidateEvidence(
      {fixture, evidence, evaluation, resolutionResult, patch},
      schemas,
    );
    results.push({evaluation, evidence});
  }
  return {
    evaluatedCandidates: results.length,
    exactCandidates: results.filter((result) => result.evaluation.gates[5].status === 'passed').length,
    mismatchedCandidates:
      results.filter((result) => result.evaluation.gates[5].status === 'failed').length,
    patchedMismatchedPixels: results[1].evaluation.gates[5].mismatchedPixels,
    patchedEvaluationFingerprint: results[1].evaluation.evaluationFingerprint,
    evidenceFingerprints: results.map((result) => result.evidence.evidenceFingerprint),
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
