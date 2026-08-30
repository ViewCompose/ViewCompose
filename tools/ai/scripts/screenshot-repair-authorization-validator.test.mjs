import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {
  validateScreenshotRepairAuthorization,
} from './screenshot-repair-authorization-validator.mjs';

const visualRoot = new URL('../evaluation/fixtures/visual/', import.meta.url);
const [contract, authorization, schema] = await Promise.all([
  'screenshot-repair-proposer-contract.json',
  'screenshot-repair/rollback.authorization.json',
].map((path) => readFile(new URL(path, visualRoot), 'utf8').then(JSON.parse)).concat([
  readFile(new URL('../contracts/screenshot-repair-authorization.schema.json', import.meta.url),
    'utf8').then(JSON.parse),
]));
const fixture = contract.supportedFixtures[0];
const proposal = {
  schemaVersion: 1,
  status: 'proposed',
  policy: {
    version: 1,
    mode: 'single-property-regression-rollback',
    targetValueSource: 'accepted-baseline-design-ir',
    currentGate: 'exact-pixels',
    requiredPriorGates: ['safety', 'compilation', 'render', 'semantics', 'structure'],
    baselineImprovement: 'same-denominator-strictly-fewer-mismatched-pixels',
    localization: 'changed-node-must-be-attributed',
    maxOperations: 1,
    eligibleCollections: ['properties'],
    valueInference: false,
    providerCalls: false,
    networkAccess: false,
  },
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
const reference = {
  requestFingerprint: '06ded39bf3588193305ba1574c43ca3a6b6d0ff9c4cd19ec3e12eb75afdefefd',
  outputFingerprint: 'e874a198d57e64645472dc11dac8e82df35e11117869dd616d33c93a311eb091',
  pngFingerprint: '69ac5adde66e6f5725a0258987f7f635cb7be333839536f06c0ae6a2ff0596e2',
  widthPx: 1079,
  heightPx: 2339,
  configuration: {
    density: 2.625,
    fontScale: 1,
    localeTag: 'en-US',
    layoutDirection: 'Ltr',
    colorSpace: 'sRGB',
    alphaMode: 'straight',
    orientation: 'upright',
    systemBars: {leftPx: 0, topPx: 0, rightPx: 0, bottomPx: 0},
    crop: {x: 0, y: 0, width: 1079, height: 2339},
  },
};
const baselineEvidence = {
  evidenceFingerprint: authorization.input.baselineEvidenceFingerprint,
  lineage: {candidateDesignIrFingerprint: authorization.input.baselineDesignIrFingerprint},
  pixelComparison: {reference},
};
const candidateEvidence = {
  evidenceFingerprint: authorization.input.candidateEvidenceFingerprint,
  lineage: {candidateDesignIrFingerprint: authorization.input.candidateDesignIrFingerprint},
  pixelComparison: {reference},
};
const propose = async () => structuredClone(proposal);

function assertValidationResult(value) {
  assert.deepEqual(validateSchemaValue(value, schema.$defs.validationResult, schema), []);
}

function resealAuthorization(value) {
  const result = structuredClone(value);
  delete result.authorizationFingerprint;
  result.authorizationFingerprint = fingerprintRepairValue(result);
  return result;
}

function input(overrides = {}) {
  return {
    baselineEvidence: structuredClone(baselineEvidence),
    candidateEvidence: structuredClone(candidateEvidence),
    proposal: structuredClone(proposal),
    authorization: structuredClone(authorization),
    ...overrides,
  };
}

test('validates exact human attestations without authorizing execution', async () => {
  const first = await validateScreenshotRepairAuthorization(input(), {propose});
  const second = await validateScreenshotRepairAuthorization(input(), {propose});
  assert.deepEqual(first, second);
  assert.equal(first.status, 'validated');
  assert.equal(first.reason, 'exact-attestation-bindings');
  assert.equal(first.policy.executionAuthorized, false);
  assert.equal(first.policy.reviewerTrust, 'external-host-responsibility');
  assert.equal(first.policy.receiptAuthentication, 'not-claimed');
  assert.equal(first.authorization.applicationCount, 1);
  assert.equal(first.authorization.unattendedExecution, false);
  assertValidationResult(first);
  const unsigned = structuredClone(first);
  delete unsigned.validationFingerprint;
  assert.equal(first.validationFingerprint, fingerprintRepairValue(unsigned));
});

test('rejects authorization integrity and exact lineage drift separately', async () => {
  const changedFingerprint = structuredClone(authorization);
  changedFingerprint.authorizationFingerprint = 'f'.repeat(64);
  const integrity = await validateScreenshotRepairAuthorization(input({
    authorization: changedFingerprint,
  }), {propose});
  assert.equal(integrity.status, 'invalid');
  assert.equal(integrity.reason, 'integrity-mismatch');
  assert.equal(
    integrity.diagnostics[0].code,
    'VC-AI-REPAIR-AUTHORIZATION-INTEGRITY-MISMATCH',
  );
  assertValidationResult(integrity);

  const changedLineage = structuredClone(authorization);
  changedLineage.input.candidateEvidenceFingerprint = 'f'.repeat(64);
  changedLineage.repairApproval.approvedCandidateEvidenceFingerprint = 'f'.repeat(64);
  const lineage = await validateScreenshotRepairAuthorization(input({
    authorization: resealAuthorization(changedLineage),
  }), {propose});
  assert.equal(lineage.status, 'invalid');
  assert.equal(lineage.reason, 'lineage-mismatch');
  assert.equal(
    lineage.diagnostics[0].code,
    'VC-AI-REPAIR-AUTHORIZATION-LINEAGE-MISMATCH',
  );
  assertValidationResult(lineage);
});

test('rejects malformed attestations and a proposal that cannot be reproduced', async () => {
  const unattended = structuredClone(authorization);
  unattended.repairApproval.unattendedExecution = true;
  const malformed = await validateScreenshotRepairAuthorization(input({
    authorization: resealAuthorization(unattended),
  }), {propose});
  assert.equal(malformed.reason, 'input-invalid');
  assertValidationResult(malformed);

  const missing = await validateScreenshotRepairAuthorization(input({authorization: undefined}), {
    propose,
  });
  assert.equal(missing.status, 'invalid');
  assert.equal(missing.reason, 'input-invalid');
  assertValidationResult(missing);

  const ineligible = await validateScreenshotRepairAuthorization(input(), {
    propose: async () => ({status: 'no-eligible-change', reason: 'candidate-already-exact'}),
  });
  assert.equal(ineligible.status, 'invalid');
  assert.equal(ineligible.reason, 'proposal-not-eligible');
  assertValidationResult(ineligible);
});

test('cancels before schema validation or proposal reproduction', async () => {
  const controller = new AbortController();
  controller.abort();
  let called = false;
  const result = await validateScreenshotRepairAuthorization({malformed: true}, {
    signal: controller.signal,
    propose: async () => {
      called = true;
    },
  });
  assert.equal(called, false);
  assert.equal(result.status, 'cancelled');
  assert.equal(result.reason, 'cancelled');
  assert.equal(result.policy.executionAuthorized, false);
  assertValidationResult(result);
});
