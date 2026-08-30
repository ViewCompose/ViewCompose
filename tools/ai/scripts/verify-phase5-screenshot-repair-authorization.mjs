#!/usr/bin/env node
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {
  evaluateScreenshotRepairCandidateWithEvidence,
} from './screenshot-repair-candidate-evaluator.mjs';
import {
  validateScreenshotRepairAuthorization,
} from './screenshot-repair-authorization-validator.mjs';
import {proposeScreenshotRepair} from './screenshot-repair-proposer.mjs';

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
    contract.activation?.status !== 'implemented-internal' ||
    contract.activation?.publicRepairMode !== false ||
    contract.activation?.implementation !== true ||
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
    schema.properties?.policy?.properties?.receiptAuthentication?.const !== 'not-claimed' ||
    schema.$defs?.validationResult?.properties?.policy?.properties?.executionAuthorized?.const !==
      false
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

function resealAuthorization(value) {
  const result = structuredClone(value);
  delete result.authorizationFingerprint;
  result.authorizationFingerprint = fingerprintRepairValue(result);
  return result;
}

function mutateAuthorization(authorization, mutation) {
  const changed = structuredClone(authorization);
  const other = 'f'.repeat(64);
  if (mutation === 'baseline-evidence-mismatch') {
    changed.input.baselineEvidenceFingerprint = other;
    changed.baselineAcceptance.approvedEvidenceFingerprint = other;
  } else if (mutation === 'candidate-evidence-mismatch') {
    changed.input.candidateEvidenceFingerprint = other;
    changed.repairApproval.approvedCandidateEvidenceFingerprint = other;
  } else if (mutation === 'proposal-mismatch') {
    changed.input.proposalFingerprint = other;
    changed.repairApproval.approvedProposalFingerprint = other;
  } else if (mutation === 'change-mismatch') {
    changed.input.changeFingerprint = other;
    changed.repairApproval.approvedChangeFingerprint = other;
  } else if (mutation === 'pixel-reference-mismatch') {
    changed.input.pixelReferenceFingerprint = other;
  } else if (mutation === 'movable-source-revision') {
    changed.baselineAcceptance.sourceRevision = 'main';
  } else if (mutation === 'missing-reviewer') {
    delete changed.baselineAcceptance.reviewerId;
  } else if (mutation === 'unattended-execution') {
    changed.repairApproval.unattendedExecution = true;
  } else if (mutation === 'changed-authorization-fingerprint') {
    changed.authorizationFingerprint = other;
    return changed;
  } else if (mutation === 'credential-shaped-field') {
    changed.apiKey = 'forbidden-not-a-real-secret';
  } else {
    throw new Error(`Unknown authorization mutation: ${mutation}`);
  }
  return resealAuthorization(changed);
}

async function verifyRealAuthorization(contract, schema, authorization) {
  const repairContract = await readJson(resolve(visualRoot, 'screenshot-repair-contract.json'));
  const fixtures = repairContract.candidateEvaluatorFixtures;
  const [resolutionResult, generationRequest, previewRequest, referenceRequest, referenceResult] =
    await Promise.all([
      fixtures.resolutionResult,
      fixtures.generationRequest,
      fixtures.previewRequest,
      fixtures.pixelReferenceRequest,
      fixtures.pixelReferenceResult,
    ].map((path) => readJson(resolve(visualRoot, path))));
  const patch = await readJson(resolve(
    visualRoot,
    fixtures.cases.find((item) => item.id === 'title-text-pixel-mismatch').patch,
  ));
  const input = {
    resolutionResult,
    generationRequest,
    previewBindings: previewRequest.bindings,
    pixelReference: {request: referenceRequest, result: referenceResult},
  };
  const limits = {maxSourceBytes: 262144, timeoutMs: 120000, maxOutputBytes: 1048576};
  const baseline = await evaluateScreenshotRepairCandidateWithEvidence(input, {
    requestId: 'verify-screenshot-authorization-baseline',
    limits,
  });
  const candidate = await evaluateScreenshotRepairCandidateWithEvidence({...input, patch}, {
    requestId: 'verify-screenshot-authorization-candidate',
    limits,
  });
  const proposal = await proposeScreenshotRepair({
    baselineEvidence: baseline.evidence,
    candidateEvidence: candidate.evidence,
  });
  const arguments_ = {
    baselineEvidence: baseline.evidence,
    candidateEvidence: candidate.evidence,
    proposal,
    authorization,
  };
  const validated = await validateScreenshotRepairAuthorization(arguments_);
  const fixture = contract.supportedFixtures[0];
  if (
    validateSchemaValue(validated, schema.$defs.validationResult, schema).length > 0 ||
    validated.status !== 'validated' ||
    validated.reason !== 'exact-attestation-bindings' ||
    validated.policy.executionAuthorized !== false ||
    validated.validationFingerprint !== fixture.expectedValidationFingerprint
  ) {
    throw new Error(
      'Real screenshot repair authorization validation changed: ' + JSON.stringify({
        status: validated.status,
        reason: validated.reason,
        input: validated.input,
        proposal: {status: proposal.status, reason: proposal.reason},
        baselineGates: baseline.evaluation.gates,
        baselineDiagnostics: baseline.evidence?.diagnostics,
        candidateGates: candidate.evaluation.gates,
        candidateDiagnostics: candidate.evidence?.diagnostics,
        validationFingerprint: validated.validationFingerprint,
      }),
    );
  }
  const invalidCodes = [];
  for (const denominator of contract.invalidFixtures) {
    const result = await validateScreenshotRepairAuthorization({
      ...arguments_,
      authorization: mutateAuthorization(authorization, denominator.mutation),
    });
    if (
      result.status !== 'invalid' ||
      result.diagnostics.length !== 1 ||
      result.diagnostics[0].code !== denominator.expectedCode ||
      result.policy.executionAuthorized !== false
    ) {
      throw new Error(`Authorization denominator changed: ${denominator.mutation}`);
    }
    invalidCodes.push(result.diagnostics[0].code);
  }
  const controller = new AbortController();
  controller.abort();
  const cancelled = await validateScreenshotRepairAuthorization(arguments_, {
    signal: controller.signal,
  });
  if (
    cancelled.status !== 'cancelled' ||
    cancelled.diagnostics[0]?.code !== contract.cancelledFixtures[0].expectedCode ||
    cancelled.policy.executionAuthorized !== false
  ) {
    throw new Error('Screenshot repair authorization cancellation changed');
  }
  return {
    evaluatedCandidates: 2,
    validatedAttestations: 1,
    invalidAttestations: invalidCodes.length,
    cancelledAttestations: 1,
    validationFingerprint: validated.validationFingerprint,
  };
}

export async function verifyPhase5ScreenshotRepairAuthorization({evaluateReal = true} = {}) {
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
  const real = evaluateReal ? await verifyRealAuthorization(contract, schema, authorization) : null;
  return {
    implementation: true,
    authorizedFixtures: 1,
    invalidDenominators: 10,
    cancelledDenominators: 1,
    authorizationFingerprint: authorization.authorizationFingerprint,
    real,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase5ScreenshotRepairAuthorization()
    .then((summary) => {
      console.log(
        `Verified screenshot repair authorization contract: ${summary.authorizedFixtures}/1 ` +
          `human-attested authorization, ${summary.invalidDenominators}/10 invalid, and ` +
          `${summary.cancelledDenominators}/1 cancelled denominators; real validation reproduced ` +
          `${summary.real.invalidAttestations}/10 fail-closed outcomes with execution disabled.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
