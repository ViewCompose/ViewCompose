import {readFile} from 'node:fs/promises';
import {
  fingerprintRepairValue,
  validateRepairPatch,
} from './repair-orchestrator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {canonicalJson} from './screenshot-contract.mjs';
import {proposeScreenshotRepair} from './screenshot-repair-proposer.mjs';

const authorizationSchemaPath = new URL(
  '../contracts/screenshot-repair-authorization.schema.json',
  import.meta.url,
);
const proposalSchemaPath = new URL(
  '../contracts/screenshot-repair-proposal.schema.json',
  import.meta.url,
);
const SHA256 = /^[a-f0-9]{64}$/u;
const MAX_AUTHORIZATION_BYTES = 65_536;
const POLICY = Object.freeze({
  version: 1,
  validation: 'exact-content-address-only',
  executionAuthorized: false,
  reviewerTrust: 'external-host-responsibility',
  receiptAuthentication: 'not-claimed',
  providerCalls: false,
  networkAccess: false,
});

let schemasPromise;

function loadSchemas() {
  schemasPromise ??= Promise.all([
    readFile(authorizationSchemaPath, 'utf8').then(JSON.parse),
    readFile(proposalSchemaPath, 'utf8').then(JSON.parse),
  ]).then(([authorization, proposal]) => ({authorization, proposal}));
  return schemasPromise;
}

function suppliedFingerprint(value, name) {
  return SHA256.test(value?.[name] ?? '') ? value[name] : null;
}

function resultInput(baselineEvidence, candidateEvidence, proposal, authorization) {
  return {
    baselineEvidenceFingerprint: suppliedFingerprint(baselineEvidence, 'evidenceFingerprint'),
    candidateEvidenceFingerprint: suppliedFingerprint(candidateEvidence, 'evidenceFingerprint'),
    proposalFingerprint: suppliedFingerprint(proposal, 'proposalFingerprint'),
    authorizationFingerprint: suppliedFingerprint(authorization, 'authorizationFingerprint'),
  };
}

function finding(code, severity, message, nextAction) {
  return {code, severity, message, nextAction};
}

function diagnosticFor(status, reason) {
  if (status === 'validated') return [];
  if (status === 'cancelled') {
    return [finding(
      'VC-AI-REPAIR-AUTHORIZATION-CANCELLED',
      'warning',
      'Screenshot repair authorization validation was cancelled before acceptance.',
      'Retry with the same immutable inputs if authorization validation is still required.',
    )];
  }
  const code = {
    'integrity-mismatch': 'VC-AI-REPAIR-AUTHORIZATION-INTEGRITY-MISMATCH',
    'lineage-mismatch': 'VC-AI-REPAIR-AUTHORIZATION-LINEAGE-MISMATCH',
  }[reason] ?? 'VC-AI-REPAIR-AUTHORIZATION-INPUT-INVALID';
  return [finding(
    code,
    'error',
    `Screenshot repair authorization validation failed: ${reason}.`,
    'Regenerate exact evidence and proposal identities, then obtain purpose-bound host attestations.',
  )];
}

function authorizationSummary(authorization) {
  return {
    baselineReviewerId: authorization.baselineAcceptance.reviewerId,
    baselineReviewReceipt: authorization.baselineAcceptance.reviewReceipt,
    sourceRevision: authorization.baselineAcceptance.sourceRevision,
    repairReviewerId: authorization.repairApproval.reviewerId,
    repairReviewReceipt: authorization.repairApproval.reviewReceipt,
    applicationCount: authorization.repairApproval.applicationCount,
    unattendedExecution: authorization.repairApproval.unattendedExecution,
  };
}

function sealResult({
  status,
  reason,
  baselineEvidence,
  candidateEvidence,
  proposal,
  authorization,
}) {
  const result = {
    schemaVersion: 1,
    status,
    reason,
    policy: structuredClone(POLICY),
    input: resultInput(baselineEvidence, candidateEvidence, proposal, authorization),
    authorization: status === 'validated' ? authorizationSummary(authorization) : null,
    diagnostics: diagnosticFor(status, reason),
  };
  result.validationFingerprint = fingerprintRepairValue(result);
  return result;
}

function invalid(reason, input) {
  return sealResult({status: 'invalid', reason, ...input});
}

function cancelled(input) {
  return sealResult({status: 'cancelled', reason: 'cancelled', ...input});
}

function exactReferenceIdentity(evidence) {
  const reference = evidence.pixelComparison.reference;
  return {
    requestFingerprint: reference.requestFingerprint,
    outputFingerprint: reference.outputFingerprint,
    pngFingerprint: reference.pngFingerprint,
    widthPx: reference.widthPx,
    heightPx: reference.heightPx,
    configuration: reference.configuration,
  };
}

function authorizationBindingsMatch({baselineEvidence, candidateEvidence, proposal, authorization}) {
  return authorization.input.baselineEvidenceFingerprint ===
      baselineEvidence.evidenceFingerprint &&
    authorization.input.candidateEvidenceFingerprint === candidateEvidence.evidenceFingerprint &&
    authorization.input.baselineDesignIrFingerprint ===
      baselineEvidence.lineage.candidateDesignIrFingerprint &&
    authorization.input.candidateDesignIrFingerprint ===
      candidateEvidence.lineage.candidateDesignIrFingerprint &&
    authorization.input.pixelReferenceFingerprint ===
      fingerprintRepairValue(exactReferenceIdentity(candidateEvidence)) &&
    authorization.input.proposalFingerprint === proposal.proposalFingerprint &&
    authorization.input.changeFingerprint === proposal.patch.changeFingerprint &&
    authorization.baselineAcceptance.approvedEvidenceFingerprint ===
      baselineEvidence.evidenceFingerprint &&
    authorization.repairApproval.approvedProposalFingerprint === proposal.proposalFingerprint &&
    authorization.repairApproval.approvedCandidateEvidenceFingerprint ===
      candidateEvidence.evidenceFingerprint &&
    authorization.repairApproval.approvedChangeFingerprint === proposal.patch.changeFingerprint &&
    authorization.baselineAcceptance.reviewReceipt !==
      authorization.repairApproval.reviewReceipt;
}

function schemaValid(value, schema, rootSchema = schema) {
  return validateSchemaValue(value, schema, rootSchema).length === 0;
}

export async function validateScreenshotRepairAuthorization({
  baselineEvidence,
  candidateEvidence,
  proposal,
  authorization,
} = {}, {
  signal,
  propose = proposeScreenshotRepair,
} = {}) {
  const input = {baselineEvidence, candidateEvidence, proposal, authorization};
  if (signal?.aborted) return cancelled(input);
  const schemas = await loadSchemas();
  let encodedAuthorization;
  try {
    encodedAuthorization = JSON.stringify(authorization);
  } catch {
    return invalid('input-invalid', input);
  }
  if (
    typeof encodedAuthorization !== 'string' ||
    Buffer.byteLength(encodedAuthorization, 'utf8') > MAX_AUTHORIZATION_BYTES ||
    !schemaValid(authorization, schemas.authorization) ||
    !schemaValid(proposal, schemas.proposal) ||
    !await validateRepairPatch(proposal?.patch)
  ) {
    return invalid('input-invalid', input);
  }
  const unsignedAuthorization = structuredClone(authorization);
  delete unsignedAuthorization.authorizationFingerprint;
  const unsignedProposal = structuredClone(proposal);
  delete unsignedProposal.proposalFingerprint;
  if (
    fingerprintRepairValue(unsignedAuthorization) !== authorization.authorizationFingerprint ||
    fingerprintRepairValue(unsignedProposal) !== proposal.proposalFingerprint
  ) {
    return invalid('integrity-mismatch', input);
  }
  const reproduced = await propose({baselineEvidence, candidateEvidence}, {signal});
  if (signal?.aborted || reproduced?.status === 'cancelled') return cancelled(input);
  if (reproduced?.status !== 'proposed') {
    return invalid(
      reproduced?.reason === 'input-invalid' ? 'input-invalid' :
        reproduced?.reason === 'evidence-lineage-mismatch' ? 'lineage-mismatch' :
          'proposal-not-eligible',
      input,
    );
  }
  if (
    canonicalJson(reproduced) !== canonicalJson(proposal) ||
    !authorizationBindingsMatch(input)
  ) {
    return invalid('lineage-mismatch', input);
  }
  const result = sealResult({
    status: 'validated',
    reason: 'exact-attestation-bindings',
    ...input,
  });
  if (!schemaValid(
    result,
    schemas.authorization.$defs.validationResult,
    schemas.authorization,
  )) {
    throw new Error('Screenshot repair authorization validator emitted an invalid result.');
  }
  return result;
}
