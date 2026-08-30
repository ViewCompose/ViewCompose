#!/usr/bin/env node
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {validateSchemaValue} from './schema-validator.mjs';
import {
  fingerprintRepairValue,
  validateRepairPatch,
} from './repair-orchestrator.mjs';

const visualRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));
const contractPath = resolve(visualRoot, 'screenshot-repair-proposer-contract.json');
const schemaPath = fileURLToPath(
  new URL('../contracts/screenshot-repair-proposal.schema.json', import.meta.url),
);
const requiredPriorGates = Object.freeze([
  'safety',
  'compilation',
  'render',
  'semantics',
  'structure',
]);
const proposalPolicy = Object.freeze({
  version: 1,
  mode: 'single-property-regression-rollback',
  targetValueSource: 'accepted-baseline-design-ir',
  currentGate: 'exact-pixels',
  requiredPriorGates,
  baselineImprovement: 'same-denominator-strictly-fewer-mismatched-pixels',
  localization: 'changed-node-must-be-attributed',
  maxOperations: 1,
  eligibleCollections: ['properties'],
  valueInference: false,
  providerCalls: false,
  networkAccess: false,
});

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function same(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

function assertUnique(values, label) {
  if (new Set(values).size !== values.length) throw new Error(`${label} are not unique`);
}

function assertContract(contract, schema) {
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-screenshot-repair-proposer-v1' ||
    !same(contract.requiresContracts, [
      'design-ir-v1',
      'screenshot-pixel-localization-v1',
      'screenshot-repair-v2',
      'screenshot-repair-candidate-evidence-v1',
      'screenshot-repair-proposal-v1',
    ]) ||
    contract.activation?.tool !== 'generate_screenshot_viewcompose' ||
    contract.activation?.status !== 'contract-frozen' ||
    contract.activation?.publicRepairMode !== false ||
    contract.activation?.implementation !== false ||
    contract.activation?.mode !== 'single-property-regression-rollback'
  ) {
    throw new Error('Screenshot repair proposer activation boundary changed');
  }
  if (
    contract.input?.callerSuppliedPatch !== false ||
    contract.input?.callerSuppliedTargetValue !== false ||
    contract.input?.pixelOrVisionInferredValue !== false ||
    !same(contract.eligibility?.sharedLineage, [
      'baseResolutionResultFingerprint',
      'inputDesignIrFingerprint',
    ]) ||
    contract.eligibility?.difference !==
      'exactly one existing properties field has a different non-expression typed value' ||
    contract.eligibility?.targetValue !==
      'the exact typed value retained by baseline Design IR' ||
    contract.policy?.maxOperations !== 1 ||
    !same(contract.policy?.eligibleOperations, ['replace-field']) ||
    !same(contract.policy?.eligibleCollections, ['properties']) ||
    contract.policy?.valueInference !== false ||
    contract.policy?.providerCalls !== false ||
    contract.policy?.networkAccess !== false ||
    contract.policy?.executeInspectedProjectBuildLogic !== false ||
    contract.policy?.aggregateScore !== false
  ) {
    throw new Error('Screenshot repair proposer eligibility or safety boundary changed');
  }
  if (
    !contract.claims?.checked?.includes(
      'rollback target values come only from an integrity-verified better baseline',
    ) ||
    !contract.claims?.checked?.includes(
      'no inferred value from pixel location, OCR, vision, or layout findings',
    ) ||
    !contract.claims?.notClaimed?.includes(
      'repair of a novel screenshot mismatch without a better baseline',
    ) ||
    !contract.claims?.notClaimed?.includes('public automatic repair mode')
  ) {
    throw new Error('Screenshot repair proposer claim boundary changed');
  }
  assertUnique(contract.diagnosticCodes, 'Screenshot repair proposer diagnostic codes');
  if (
    !same(contract.diagnosticCodes, [
      'VC-AI-REPAIR-PROPOSAL-NONE',
      'VC-AI-REPAIR-PROPOSAL-INPUT-INVALID',
      'VC-AI-REPAIR-PROPOSAL-CANCELLED',
    ]) ||
    schema.$id !==
      'https://schemas.viewcompose.com/ai/screenshot-repair-proposal-v1.schema.json' ||
    schema.properties?.schemaVersion?.const !== 1 ||
    schema.properties?.policy?.properties?.mode?.const !==
      'single-property-regression-rollback' ||
    schema.properties?.policy?.properties?.valueInference?.const !== false ||
    schema.properties?.policy?.properties?.maxOperations?.const !== 1
  ) {
    throw new Error('Screenshot repair proposer result schema boundary changed');
  }
}

export async function verifyPhase5ScreenshotRepairProposer() {
  const [contract, schema] = await Promise.all([
    readJson(contractPath),
    readJson(schemaPath),
  ]);
  assertContract(contract, schema);
  if (
    contract.supportedFixtures?.length !== 1 ||
    contract.noEligibleFixtures?.length !== 6 ||
    contract.invalidFixtures?.length !== 2 ||
    contract.cancelledFixtures?.length !== 1
  ) {
    throw new Error('Screenshot repair proposer denominator counts changed');
  }
  const fixture = contract.supportedFixtures[0];
  const proposed = {
    schemaVersion: 1,
    status: 'proposed',
    policy: structuredClone(proposalPolicy),
    input: {
      baselineEvidenceFingerprint: fixture.baselineEvidenceFingerprint,
      candidateEvidenceFingerprint: fixture.candidateEvidenceFingerprint,
    },
    reason: fixture.expectedReason,
    target: fixture.expectedTarget,
    patch: fixture.expectedPatch,
    diagnostics: [],
    proposalFingerprint: fixture.expectedProposalFingerprint,
  };
  const unsigned = structuredClone(proposed);
  delete unsigned.proposalFingerprint;
  if (
    validateSchemaValue(proposed, schema).length > 0 ||
    !await validateRepairPatch(proposed.patch) ||
    proposed.patch.operations.length !== 1 ||
    proposed.patch.operations[0].collection !== 'properties' ||
    proposed.target.nodeId !== proposed.patch.operations[0].nodeId ||
    proposed.target.name !== proposed.patch.operations[0].name ||
    proposed.proposalFingerprint !== fingerprintRepairValue(unsigned)
  ) {
    throw new Error('Screenshot repair proposer supported contract fixture changed');
  }
  const reasons = schema.properties.reason.enum;
  const expectedNoEligible = [
    'candidate-already-exact',
    'earlier-gate-failed',
    'baseline-not-strictly-better',
    'no-single-localized-property-difference',
    'no-single-localized-property-difference',
    'no-single-localized-property-difference',
  ];
  if (
    !same(contract.noEligibleFixtures.map((item) => item.expectedReason), expectedNoEligible) ||
    !same(contract.invalidFixtures.map((item) => item.expectedReason), [
      'input-invalid',
      'evidence-lineage-mismatch',
    ]) ||
    !same(contract.cancelledFixtures.map((item) => item.expectedReason), ['cancelled']) ||
    [...contract.noEligibleFixtures, ...contract.invalidFixtures, ...contract.cancelledFixtures]
      .some((item) => !reasons.includes(item.expectedReason))
  ) {
    throw new Error('Screenshot repair proposer fail-closed reasons changed');
  }
  return {
    implementation: false,
    supportedRollbacks: 1,
    noEligibleDenominators: 6,
    invalidDenominators: 2,
    cancelledDenominators: 1,
    proposalFingerprint: fixture.expectedProposalFingerprint,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase5ScreenshotRepairProposer()
    .then((summary) => {
      console.log(
        `Verified screenshot repair proposer contract: ${summary.supportedRollbacks}/1 bounded ` +
          `rollback, ${summary.noEligibleDenominators}/6 no-change, ` +
          `${summary.invalidDenominators}/2 invalid, and ` +
          `${summary.cancelledDenominators}/1 cancelled denominators; implementation remains off.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
