#!/usr/bin/env node
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';

const visualRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));
const contractPath = resolve(visualRoot, 'screenshot-repair-authorization-contract.json');
const schemaPath = fileURLToPath(
  new URL('../contracts/screenshot-repair-authorization.schema.json', import.meta.url),
);

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function same(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

function assertContract(contract, schema) {
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-screenshot-repair-authorization-v1' ||
    !same(contract.requiresContracts, [
      'screenshot-repair-candidate-evidence-v1',
      'screenshot-repair-proposal-v1',
      'screenshot-repair-authorization-v1',
    ]) ||
    contract.activation?.tool !== 'generate_screenshot_viewcompose' ||
    contract.activation?.status !== 'contract-frozen' ||
    contract.activation?.publicRepairMode !== false ||
    contract.activation?.implementation !== false ||
    contract.activation?.executionAuthorized !== false
  ) {
    throw new Error('Screenshot repair authorization activation boundary changed');
  }
  if (
    contract.boundary?.credentials !== false ||
    contract.boundary?.providerCalls !== false ||
    contract.boundary?.networkAccess !== false ||
    contract.boundary?.unattendedExecution !== false ||
    contract.boundary?.logs !== 'metadata-only' ||
    contract.policy?.maxApplications !== 1 ||
    contract.policy?.baselineAcceptanceScope !== 'exact-evidence-only' ||
    contract.policy?.proposalBinding !== 'exact-content-address' ||
    contract.policy?.sourceRevision !== 'immutable-git-commit' ||
    contract.policy?.reviewerTrust !== 'external-host-responsibility' ||
    contract.policy?.receiptAuthentication !== 'not-claimed' ||
    contract.policy?.authorizationReuse !== false ||
    contract.policy?.authorizationRevocation !== 'host-owned-before-application'
  ) {
    throw new Error('Screenshot repair authorization policy boundary changed');
  }
  if (
    !contract.claims?.checked?.includes(
      'repair approval binds reviewer, receipt, proposal, current evidence, and change identity',
    ) ||
    !contract.claims?.notClaimed?.includes(
      'cryptographic authentication of reviewer identity or receipt',
    ) ||
    !contract.claims?.notClaimed?.includes(
      'automatic authorization from a successful proposal',
    ) ||
    schema.$id !==
      'https://schemas.viewcompose.com/ai/screenshot-repair-authorization-v1.schema.json' ||
    schema.properties?.schemaVersion?.const !== 1 ||
    schema.properties?.repairApproval?.properties?.applicationCount?.const !== 1 ||
    schema.properties?.repairApproval?.properties?.unattendedExecution?.const !== false ||
    schema.properties?.policy?.properties?.receiptAuthentication?.const !== 'not-claimed'
  ) {
    throw new Error('Screenshot repair authorization claim or schema boundary changed');
  }
  if (!same(contract.diagnosticCodes, [
    'VC-AI-REPAIR-AUTHORIZATION-INPUT-INVALID',
    'VC-AI-REPAIR-AUTHORIZATION-INTEGRITY-MISMATCH',
    'VC-AI-REPAIR-AUTHORIZATION-LINEAGE-MISMATCH',
    'VC-AI-REPAIR-AUTHORIZATION-CANCELLED',
  ])) {
    throw new Error('Screenshot repair authorization diagnostic boundary changed');
  }
}

function assertAuthorization(authorization, schema, expectedFingerprint) {
  const unsigned = structuredClone(authorization);
  delete unsigned.authorizationFingerprint;
  if (
    validateSchemaValue(authorization, schema).length > 0 ||
    authorization.authorizationFingerprint !== expectedFingerprint ||
    authorization.authorizationFingerprint !== fingerprintRepairValue(unsigned) ||
    authorization.baselineAcceptance.approvedEvidenceFingerprint !==
      authorization.input.baselineEvidenceFingerprint ||
    authorization.repairApproval.approvedProposalFingerprint !==
      authorization.input.proposalFingerprint ||
    authorization.repairApproval.approvedCandidateEvidenceFingerprint !==
      authorization.input.candidateEvidenceFingerprint ||
    authorization.repairApproval.approvedChangeFingerprint !==
      authorization.input.changeFingerprint ||
    authorization.baselineAcceptance.reviewerId.length === 0 ||
    authorization.repairApproval.reviewerId.length === 0 ||
    authorization.baselineAcceptance.reviewReceipt ===
      authorization.repairApproval.reviewReceipt
  ) {
    throw new Error('Screenshot repair authorization supported fixture changed');
  }
}

export async function verifyPhase5ScreenshotRepairAuthorization() {
  const [contract, schema] = await Promise.all([readJson(contractPath), readJson(schemaPath)]);
  assertContract(contract, schema);
  if (
    contract.supportedFixtures?.length !== 1 ||
    contract.invalidFixtures?.length !== 10 ||
    contract.cancelledFixtures?.length !== 1
  ) {
    throw new Error('Screenshot repair authorization denominator counts changed');
  }
  const fixture = contract.supportedFixtures[0];
  const authorization = await readJson(resolve(visualRoot, fixture.authorization));
  assertAuthorization(authorization, schema, fixture.expectedAuthorizationFingerprint);
  const declaredCodes = new Set(contract.diagnosticCodes);
  if (
    contract.invalidFixtures.some((item) => !declaredCodes.has(item.expectedCode)) ||
    contract.cancelledFixtures.some((item) => !declaredCodes.has(item.expectedCode)) ||
    contract.invalidFixtures.filter((item) =>
      item.expectedCode === 'VC-AI-REPAIR-AUTHORIZATION-LINEAGE-MISMATCH').length !== 5
  ) {
    throw new Error('Screenshot repair authorization fail-closed classifications changed');
  }
  return {
    implementation: false,
    authorizedFixtures: 1,
    invalidDenominators: 10,
    cancelledDenominators: 1,
    authorizationFingerprint: authorization.authorizationFingerprint,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase5ScreenshotRepairAuthorization()
    .then((summary) => {
      console.log(
        `Verified screenshot repair authorization contract: ${summary.authorizedFixtures}/1 ` +
          `human-attested authorization, ${summary.invalidDenominators}/10 invalid, and ` +
          `${summary.cancelledDenominators}/1 cancelled denominators; implementation remains off.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
