import {readFile} from 'node:fs/promises';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';

const authorizationSchemaPath = new URL(
  '../contracts/screenshot-repair-authorization.schema.json',
  import.meta.url,
);
const hostGrantSchemaPath = new URL(
  '../contracts/screenshot-repair-host-grant.schema.json',
  import.meta.url,
);
const SHA256 = /^[a-f0-9]{64}$/u;
const STABLE_ID = /^[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}$/u;
const MAX_RECORD_BYTES = 65_536;
const trustedHosts = new WeakMap();
const DECISION_POLICY = Object.freeze({
  decisionTransport: 'trusted-host-callback-only',
  callerSuppliedDecision: false,
  credentialInput: false,
  providerCalls: false,
  toolNetworkAccess: false,
  logs: 'fingerprints-only',
});

let schemasPromise;

function loadSchemas() {
  schemasPromise ??= Promise.all([
    readFile(authorizationSchemaPath, 'utf8').then(JSON.parse),
    readFile(hostGrantSchemaPath, 'utf8').then(JSON.parse),
  ]).then(([authorization, hostGrant]) => ({authorization, hostGrant}));
  return schemasPromise;
}

function exactKeys(value, expected) {
  return value !== null &&
    typeof value === 'object' &&
    !Array.isArray(value) &&
    JSON.stringify(Object.keys(value).sort()) === JSON.stringify([...expected].sort());
}

function schemaValid(value, schema, rootSchema = schema) {
  return validateSchemaValue(value, schema, rootSchema).length === 0;
}

function encodedWithinLimit(value) {
  try {
    const encoded = JSON.stringify(value);
    return typeof encoded === 'string' &&
      Buffer.byteLength(encoded, 'utf8') <= MAX_RECORD_BYTES;
  } catch {
    return false;
  }
}

function fingerprintWithout(value, key) {
  const copy = structuredClone(value);
  delete copy[key];
  return fingerprintRepairValue(copy);
}

function diagnostic(code, severity, message, nextAction) {
  return {code, severity, message, nextAction};
}

function rejectedDecision({status = 'denied', reason, code, request, trustDomainId}) {
  const decision = {
    schemaVersion: 1,
    kind: 'screenshot-repair-host-grant-decision',
    status,
    reason,
    requestFingerprint: SHA256.test(request?.requestFingerprint ?? '')
      ? request.requestFingerprint
      : null,
    trustDomainId: STABLE_ID.test(trustDomainId ?? '') ? trustDomainId : 'untrusted-host',
    executionAuthorized: false,
    policy: structuredClone(DECISION_POLICY),
    diagnostics: [diagnostic(
      code,
      status === 'cancelled' ? 'warning' : 'error',
      `Screenshot repair host-grant request was ${status}: ${reason}.`,
      status === 'cancelled'
        ? 'Retry the same immutable request through the trusted host callback if it is still needed.'
        : 'Resolve the host trust failure and create a new exact request; never reuse a reservation.',
    )],
  };
  decision.decisionFingerprint = fingerprintRepairValue(decision);
  return decision;
}

function inputInvalid(request, trustDomainId, code = 'VC-AI-REPAIR-HOST-GRANT-INPUT-INVALID') {
  return rejectedDecision({
    reason: 'input-invalid',
    code,
    request,
    trustDomainId,
  });
}

function cancelled(request, trustDomainId) {
  return rejectedDecision({
    status: 'cancelled',
    reason: 'cancelled',
    code: 'VC-AI-REPAIR-HOST-GRANT-CANCELLED',
    request,
    trustDomainId,
  });
}

function hostFailed(request, trustDomainId) {
  return rejectedDecision({
    reason: 'host-failed',
    code: 'VC-AI-REPAIR-HOST-GRANT-HOST-FAILED',
    request,
    trustDomainId,
  });
}

function authorizationValidationMatches(validationResult, authorization) {
  return validationResult.status === 'validated' &&
    validationResult.reason === 'exact-attestation-bindings' &&
    validationResult.policy.executionAuthorized === false &&
    validationResult.validationFingerprint ===
      fingerprintWithout(validationResult, 'validationFingerprint') &&
    authorization.authorizationFingerprint ===
      fingerprintWithout(authorization, 'authorizationFingerprint') &&
    validationResult.input.authorizationFingerprint === authorization.authorizationFingerprint &&
    validationResult.input.baselineEvidenceFingerprint ===
      authorization.input.baselineEvidenceFingerprint &&
    validationResult.input.candidateEvidenceFingerprint ===
      authorization.input.candidateEvidenceFingerprint &&
    validationResult.input.proposalFingerprint === authorization.input.proposalFingerprint &&
    validationResult.authorization.baselineReviewerId ===
      authorization.baselineAcceptance.reviewerId &&
    validationResult.authorization.baselineReviewReceipt ===
      authorization.baselineAcceptance.reviewReceipt &&
    validationResult.authorization.sourceRevision ===
      authorization.baselineAcceptance.sourceRevision &&
    validationResult.authorization.repairReviewerId === authorization.repairApproval.reviewerId &&
    validationResult.authorization.repairReviewReceipt ===
      authorization.repairApproval.reviewReceipt &&
    validationResult.authorization.applicationCount === 1 &&
    validationResult.authorization.unattendedExecution === false;
}

function buildRequest(validationResult, authorization, trustDomainId) {
  const request = {
    schemaVersion: 1,
    kind: 'screenshot-repair-host-grant-request',
    input: {
      validationFingerprint: validationResult.validationFingerprint,
      authorizationFingerprint: authorization.authorizationFingerprint,
      baselineEvidenceFingerprint: authorization.input.baselineEvidenceFingerprint,
      candidateEvidenceFingerprint: authorization.input.candidateEvidenceFingerprint,
      candidateDesignIrFingerprint: authorization.input.candidateDesignIrFingerprint,
      pixelReferenceFingerprint: authorization.input.pixelReferenceFingerprint,
      proposalFingerprint: authorization.input.proposalFingerprint,
      changeFingerprint: authorization.input.changeFingerprint,
      baselineSourceRevision: authorization.baselineAcceptance.sourceRevision,
    },
    hostBoundary: {
      trustDomainId,
      decisionTransport: 'trusted-host-callback-only',
      identityAuthentication: 'host-authenticates-both-reviewers',
      receiptAuthentication: 'host-authenticates-both-review-receipts',
      revocationCheck: 'immediately-before-atomic-reservation',
      consumption: 'atomic-single-use-reservation',
      durableState: 'host-owned',
      credentialTransport: 'out-of-band',
    },
    policy: {
      maxAttempts: 1,
      reuseAllowed: false,
      retryAfterReservationFailure: false,
      unattendedExecution: false,
      publicToolMode: false,
      callerSuppliedDecision: false,
      credentialInput: false,
      providerCalls: false,
      toolNetworkAccess: false,
      logs: 'fingerprints-only',
    },
  };
  request.requestFingerprint = fingerprintRepairValue(request);
  return request;
}

function purposeBindingsMatch(decision, request, authorization) {
  const baselineAuthentication = decision.authentication.baselineAcceptance;
  const repairAuthentication = decision.authentication.repairApproval;
  const baselineRevocation = decision.revocationChecks.baselineAcceptance;
  const repairRevocation = decision.revocationChecks.repairApproval;
  const receipts = [
    baselineAuthentication.authenticationReceipt,
    repairAuthentication.authenticationReceipt,
    baselineRevocation.checkReceipt,
    repairRevocation.checkReceipt,
    decision.reservation.reservationReceipt,
  ];
  return decision.requestFingerprint === request.requestFingerprint &&
    decision.trustDomainId === request.hostBoundary.trustDomainId &&
    baselineAuthentication.principalId === authorization.baselineAcceptance.reviewerId &&
    baselineAuthentication.reviewReceipt === authorization.baselineAcceptance.reviewReceipt &&
    repairAuthentication.principalId === authorization.repairApproval.reviewerId &&
    repairAuthentication.reviewReceipt === authorization.repairApproval.reviewReceipt &&
    baselineRevocation.reviewReceipt === authorization.baselineAcceptance.reviewReceipt &&
    repairRevocation.reviewReceipt === authorization.repairApproval.reviewReceipt &&
    new Set(receipts).size === receipts.length &&
    decision.grant.validationFingerprint === request.input.validationFingerprint &&
    decision.grant.authorizationFingerprint === request.input.authorizationFingerprint &&
    decision.grant.candidateEvidenceFingerprint === request.input.candidateEvidenceFingerprint &&
    decision.grant.proposalFingerprint === request.input.proposalFingerprint &&
    decision.grant.changeFingerprint === request.input.changeFingerprint &&
    decision.grant.targetDesignIrFingerprint === request.input.candidateDesignIrFingerprint;
}

function acceptedHostDecision(decision, request, authorization, schema) {
  if (
    !encodedWithinLimit(decision) ||
    !schemaValid(decision, schema) ||
    decision.decisionFingerprint !== fingerprintWithout(decision, 'decisionFingerprint') ||
    decision.requestFingerprint !== request.requestFingerprint ||
    decision.trustDomainId !== request.hostBoundary.trustDomainId
  ) {
    return false;
  }
  if (decision.status === 'granted') {
    return purposeBindingsMatch(decision, request, authorization);
  }
  return decision.executionAuthorized === false &&
    ((decision.status === 'cancelled' && decision.reason === 'cancelled') ||
      (decision.status === 'denied' && decision.reason !== 'cancelled'));
}

export function createTrustedScreenshotRepairHost({trustDomainId, reserve} = {}) {
  if (!STABLE_ID.test(trustDomainId ?? '') || typeof reserve !== 'function') {
    throw new TypeError('A stable trust domain and direct host reservation callback are required.');
  }
  const handle = Object.freeze({trustDomainId});
  trustedHosts.set(handle, {reserve});
  return handle;
}

export async function requestScreenshotRepairHostGrant(input, {host, signal} = {}) {
  const hostImplementation = host !== null && typeof host === 'object'
    ? trustedHosts.get(host)
    : undefined;
  const trustDomainId = hostImplementation ? host.trustDomainId : 'untrusted-host';
  if (signal?.aborted) return cancelled(null, trustDomainId);
  if (
    !hostImplementation ||
    !exactKeys(input, ['validationResult', 'authorization']) ||
    !encodedWithinLimit(input.validationResult) ||
    !encodedWithinLimit(input.authorization)
  ) {
    return inputInvalid(null, trustDomainId);
  }
  const schemas = await loadSchemas();
  if (
    !schemaValid(
      input.validationResult,
      schemas.authorization.$defs.validationResult,
      schemas.authorization,
    ) ||
    !schemaValid(input.authorization, schemas.authorization) ||
    !authorizationValidationMatches(input.validationResult, input.authorization)
  ) {
    return inputInvalid(null, trustDomainId);
  }
  const request = buildRequest(input.validationResult, input.authorization, trustDomainId);
  if (!schemaValid(request, schemas.hostGrant)) return inputInvalid(request, trustDomainId);
  let decision;
  try {
    decision = await hostImplementation.reserve(structuredClone(request), {signal});
  } catch {
    if (signal?.aborted) return cancelled(request, trustDomainId);
    return hostFailed(request, trustDomainId);
  }
  if (signal?.aborted) return cancelled(request, trustDomainId);
  if (!acceptedHostDecision(decision, request, input.authorization, schemas.hostGrant)) {
    return inputInvalid(
      request,
      trustDomainId,
      'VC-AI-REPAIR-HOST-GRANT-LINEAGE-MISMATCH',
    );
  }
  return structuredClone(decision);
}
