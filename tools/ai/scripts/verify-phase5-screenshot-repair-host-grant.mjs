#!/usr/bin/env node
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {
  createTrustedScreenshotRepairHost,
  requestScreenshotRepairHostGrant,
} from './screenshot-repair-host-grant-adapter.mjs';

const visualRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));
const contractPath = resolve(visualRoot, 'screenshot-repair-host-grant-contract.json');
const schemaPath = fileURLToPath(
  new URL('../contracts/screenshot-repair-host-grant.schema.json', import.meta.url),
);

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function same(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

function fingerprintWithout(value, key) {
  const copy = structuredClone(value);
  delete copy[key];
  return fingerprintRepairValue(copy);
}

function resealRequest(request) {
  const result = structuredClone(request);
  delete result.requestFingerprint;
  result.requestFingerprint = fingerprintRepairValue(result);
  return result;
}

function resealDecision(decision) {
  const result = structuredClone(decision);
  delete result.decisionFingerprint;
  result.decisionFingerprint = fingerprintRepairValue(result);
  return result;
}

function assertContract(contract, schema) {
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-screenshot-repair-host-grant-v1' ||
    !same(contract.requiresContracts, [
      'screenshot-repair-authorization-v1',
      'screenshot-repair-host-grant-v1',
    ]) ||
    contract.activation?.tool !== 'generate_screenshot_viewcompose' ||
    contract.activation?.status !== 'implemented-internal' ||
    contract.activation?.publicRepairMode !== false ||
    contract.activation?.implementation !== true ||
    contract.activation?.executionAuthorized !== false
  ) {
    throw new Error('Screenshot repair host-grant activation boundary changed');
  }
  if (
    contract.boundary?.decision !==
      'trusted host callback only; serialized caller decisions have no authority' ||
    contract.boundary?.consumption !==
      'host durably reserves exactly one application attempt atomically' ||
    contract.boundary?.credentials !== 'out-of-band host transport only' ||
    contract.boundary?.providerCalls !== false ||
    contract.boundary?.toolNetworkAccess !== false ||
    contract.boundary?.unattendedExecution !== false ||
    contract.boundary?.logs !== 'fingerprints-only' ||
    contract.policy?.maxAttempts !== 1 ||
    contract.policy?.authorizationReuse !== false ||
    contract.policy?.retryAfterReservationFailure !== false ||
    contract.policy?.decisionTransport !== 'trusted-host-callback-only' ||
    contract.policy?.callerSuppliedDecision !== false ||
    contract.policy?.hostState !== 'durable' ||
    contract.policy?.publicActivation !== false
  ) {
    throw new Error('Screenshot repair host-grant policy boundary changed');
  }
  if (
    !contract.claims?.checked?.includes(
      'both review receipts are active immediately before one durable atomic reservation',
    ) ||
    !contract.claims?.notClaimed?.includes(
      'authority for a decision loaded from a file, stdin, CLI argument, MCP argument, or network payload',
    ) ||
    !contract.claims?.notClaimed?.includes(
      'a production durable host store, authenticated host integration, patch executor, or recovery workflow',
    ) ||
    schema.$id !==
      'https://schemas.viewcompose.com/ai/screenshot-repair-host-grant-v1.schema.json' ||
    schema.$defs?.request?.properties?.schemaVersion?.const !== 1 ||
    schema.$defs?.request?.properties?.policy?.properties?.publicToolMode?.const !== false ||
    schema.$defs?.grantedDecision?.properties?.grant?.$ref !== '#/$defs/grant' ||
    schema.$defs?.baselineAuthentication?.properties?.purpose?.const !==
      'baseline-acceptance' ||
    schema.$defs?.repairAuthentication?.properties?.purpose?.const !== 'repair-approval' ||
    schema.$defs?.baselineRevocationCheck?.properties?.purpose?.const !==
      'baseline-acceptance' ||
    schema.$defs?.repairRevocationCheck?.properties?.purpose?.const !== 'repair-approval' ||
    schema.$defs?.grant?.properties?.executionAuthorized?.const !== true ||
    schema.$defs?.grant?.properties?.unattendedExecution?.const !== false ||
    schema.$defs?.reservation?.properties?.maxAttempts?.const !== 1 ||
    schema.$defs?.reservation?.properties?.reuseAllowed?.const !== false ||
    schema.$defs?.decisionPolicy?.properties?.callerSuppliedDecision?.const !== false
  ) {
    throw new Error('Screenshot repair host-grant claim or schema boundary changed');
  }
  if (!same(contract.diagnosticCodes, [
    'VC-AI-REPAIR-HOST-GRANT-INPUT-INVALID',
    'VC-AI-REPAIR-HOST-GRANT-INTEGRITY-MISMATCH',
    'VC-AI-REPAIR-HOST-GRANT-LINEAGE-MISMATCH',
    'VC-AI-REPAIR-HOST-GRANT-AUTHENTICATION-FAILED',
    'VC-AI-REPAIR-HOST-GRANT-REVOKED',
    'VC-AI-REPAIR-HOST-GRANT-ALREADY-CONSUMED',
    'VC-AI-REPAIR-HOST-GRANT-POLICY-DENIED',
    'VC-AI-REPAIR-HOST-GRANT-HOST-FAILED',
    'VC-AI-REPAIR-HOST-GRANT-CANCELLED',
  ])) {
    throw new Error('Screenshot repair host-grant diagnostic boundary changed');
  }
}

function bindingsMatch(request, decision, authorization, expectedValidationFingerprint) {
  const baselineAuthentication = decision.authentication.baselineAcceptance;
  const repairAuthentication = decision.authentication.repairApproval;
  const baselineRevocation = decision.revocationChecks.baselineAcceptance;
  const repairRevocation = decision.revocationChecks.repairApproval;
  return request.input.validationFingerprint === expectedValidationFingerprint &&
    request.input.authorizationFingerprint === authorization.authorizationFingerprint &&
    request.input.baselineEvidenceFingerprint ===
      authorization.input.baselineEvidenceFingerprint &&
    request.input.candidateEvidenceFingerprint ===
      authorization.input.candidateEvidenceFingerprint &&
    request.input.candidateDesignIrFingerprint ===
      authorization.input.candidateDesignIrFingerprint &&
    request.input.pixelReferenceFingerprint === authorization.input.pixelReferenceFingerprint &&
    request.input.proposalFingerprint === authorization.input.proposalFingerprint &&
    request.input.changeFingerprint === authorization.input.changeFingerprint &&
    request.input.baselineSourceRevision === authorization.baselineAcceptance.sourceRevision &&
    decision.requestFingerprint === request.requestFingerprint &&
    decision.trustDomainId === request.hostBoundary.trustDomainId &&
    baselineAuthentication?.principalId === authorization.baselineAcceptance.reviewerId &&
    baselineAuthentication?.reviewReceipt === authorization.baselineAcceptance.reviewReceipt &&
    repairAuthentication?.principalId === authorization.repairApproval.reviewerId &&
    repairAuthentication?.reviewReceipt === authorization.repairApproval.reviewReceipt &&
    baselineRevocation?.reviewReceipt === authorization.baselineAcceptance.reviewReceipt &&
    repairRevocation?.reviewReceipt === authorization.repairApproval.reviewReceipt &&
    decision.grant.validationFingerprint === request.input.validationFingerprint &&
    decision.grant.authorizationFingerprint === request.input.authorizationFingerprint &&
    decision.grant.candidateEvidenceFingerprint === request.input.candidateEvidenceFingerprint &&
    decision.grant.proposalFingerprint === request.input.proposalFingerprint &&
    decision.grant.changeFingerprint === request.input.changeFingerprint &&
    decision.grant.targetDesignIrFingerprint === request.input.candidateDesignIrFingerprint;
}

function classify(request, decision, authorization, schema, expectedValidationFingerprint) {
  if (
    validateSchemaValue(request, schema).length > 0 ||
    validateSchemaValue(decision, schema).length > 0
  ) {
    return 'VC-AI-REPAIR-HOST-GRANT-INPUT-INVALID';
  }
  if (
    request.requestFingerprint !== fingerprintWithout(request, 'requestFingerprint') ||
    decision.decisionFingerprint !== fingerprintWithout(decision, 'decisionFingerprint')
  ) {
    return 'VC-AI-REPAIR-HOST-GRANT-INTEGRITY-MISMATCH';
  }
  return bindingsMatch(request, decision, authorization, expectedValidationFingerprint)
    ? null
    : 'VC-AI-REPAIR-HOST-GRANT-LINEAGE-MISMATCH';
}

function mutate(request, decision, mutation) {
  let changedRequest = structuredClone(request);
  let changedDecision = structuredClone(decision);
  const other = 'f'.repeat(64);
  if (mutation === 'validation-fingerprint-mismatch') {
    changedRequest.input.validationFingerprint = other;
    changedRequest = resealRequest(changedRequest);
    changedDecision.requestFingerprint = changedRequest.requestFingerprint;
    changedDecision.grant.validationFingerprint = other;
    changedDecision = resealDecision(changedDecision);
  } else if (mutation === 'changed-request-fingerprint') {
    changedRequest.requestFingerprint = other;
  } else if (mutation === 'decision-request-mismatch') {
    changedDecision.requestFingerprint = other;
    changedDecision = resealDecision(changedDecision);
  } else if (mutation === 'trust-domain-mismatch') {
    changedDecision.trustDomainId = 'other-repair-host';
    changedDecision = resealDecision(changedDecision);
  } else if (mutation === 'baseline-principal-mismatch') {
    changedDecision.authentication.baselineAcceptance.principalId = 'other-baseline-reviewer';
    changedDecision = resealDecision(changedDecision);
  } else if (mutation === 'repair-receipt-mismatch') {
    changedDecision.authentication.repairApproval.reviewReceipt = other;
    changedDecision.revocationChecks.repairApproval.reviewReceipt = other;
    changedDecision = resealDecision(changedDecision);
  } else if (mutation === 'duplicate-baseline-purpose') {
    changedDecision.authentication.repairApproval.purpose = 'baseline-acceptance';
    changedDecision = resealDecision(changedDecision);
  } else if (mutation === 'revoked-receipt-in-grant') {
    changedDecision.revocationChecks.baselineAcceptance.status = 'revoked';
    changedDecision = resealDecision(changedDecision);
  } else if (mutation === 'non-atomic-reservation') {
    changedDecision.reservation.mode = 'caller-check-then-write';
    changedDecision = resealDecision(changedDecision);
  } else if (mutation === 'second-attempt') {
    changedDecision.reservation.attemptNumber = 2;
    changedDecision = resealDecision(changedDecision);
  } else if (mutation === 'authorization-reuse') {
    changedDecision.reservation.maxAttempts = 2;
    changedDecision.reservation.reuseAllowed = true;
    changedDecision = resealDecision(changedDecision);
  } else if (mutation === 'retry-after-failure') {
    changedDecision.reservation.retryAfterFailure = true;
    changedDecision = resealDecision(changedDecision);
  } else if (mutation === 'unattended-execution') {
    changedDecision.grant.unattendedExecution = true;
    changedDecision = resealDecision(changedDecision);
  } else if (mutation === 'caller-supplied-decision') {
    changedDecision.policy.callerSuppliedDecision = true;
    changedDecision = resealDecision(changedDecision);
  } else if (mutation === 'credential-shaped-field') {
    changedDecision.apiKey = 'forbidden-not-a-real-secret';
    changedDecision = resealDecision(changedDecision);
  } else if (mutation === 'change-mismatch') {
    changedDecision.grant.changeFingerprint = other;
    changedDecision = resealDecision(changedDecision);
  } else if (mutation === 'changed-decision-fingerprint') {
    changedDecision.decisionFingerprint = other;
  } else {
    throw new Error(`Unknown screenshot repair host-grant mutation: ${mutation}`);
  }
  return {request: changedRequest, decision: changedDecision};
}

function nonGrantDecision(request, status, reason, code) {
  const decision = {
    schemaVersion: 1,
    kind: 'screenshot-repair-host-grant-decision',
    status,
    reason,
    requestFingerprint: request.requestFingerprint,
    trustDomainId: request.hostBoundary.trustDomainId,
    executionAuthorized: false,
    policy: {
      decisionTransport: 'trusted-host-callback-only',
      callerSuppliedDecision: false,
      credentialInput: false,
      providerCalls: false,
      toolNetworkAccess: false,
      logs: 'fingerprints-only',
    },
    diagnostics: [{
      code,
      severity: status === 'cancelled' ? 'warning' : 'error',
      message: `Screenshot repair host grant was ${status}: ${reason}.`,
      nextAction: 'Resolve the host trust decision and submit a new exact grant request if needed.',
    }],
  };
  decision.decisionFingerprint = fingerprintRepairValue(decision);
  return decision;
}

async function verifyImplementedAdapter(request, decision, authorization, validationResult) {
  let reserved = false;
  let calls = 0;
  const host = createTrustedScreenshotRepairHost({
    trustDomainId: request.hostBoundary.trustDomainId,
    reserve: async (actualRequest) => {
      calls += 1;
      if (!same(actualRequest, request)) throw new Error('Adapter request drifted');
      if (reserved) {
        return nonGrantDecision(
          actualRequest,
          'denied',
          'already-consumed',
          'VC-AI-REPAIR-HOST-GRANT-ALREADY-CONSUMED',
        );
      }
      reserved = true;
      return structuredClone(decision);
    },
  });
  const input = {validationResult, authorization};
  const granted = await requestScreenshotRepairHostGrant(input, {host});
  const replayed = await requestScreenshotRepairHostGrant(input, {host});
  const serializedHandle = structuredClone(host);
  const serialized = await requestScreenshotRepairHostGrant({
    ...input,
    decision,
  }, {host: serializedHandle});
  if (
    granted.status !== 'granted' ||
    granted.decisionFingerprint !== decision.decisionFingerprint ||
    replayed.status !== 'denied' ||
    replayed.reason !== 'already-consumed' ||
    replayed.executionAuthorized !== false ||
    serialized.status !== 'denied' ||
    serialized.executionAuthorized !== false ||
    calls !== 2
  ) {
    throw new Error('Screenshot repair trusted-host adapter boundary changed');
  }
  return {
    directCallbackGrants: 1,
    replayedGrants: 0,
    serializedDecisionsAccepted: 0,
  };
}

export async function verifyPhase5ScreenshotRepairHostGrant() {
  const [contract, schema] = await Promise.all([readJson(contractPath), readJson(schemaPath)]);
  assertContract(contract, schema);
  if (
    contract.supportedFixtures?.length !== 1 ||
    contract.invalidFixtures?.length !== 17 ||
    contract.deniedFixtures?.length !== 5 ||
    contract.cancelledFixtures?.length !== 1
  ) {
    throw new Error('Screenshot repair host-grant denominator changed');
  }
  const fixture = contract.supportedFixtures[0];
  const [request, decision, authorization, validationResult] = await Promise.all([
    fixture.request,
    fixture.decision,
    fixture.authorization,
    fixture.validation,
  ].map((path) => readJson(resolve(visualRoot, path))));
  if (
    request.requestFingerprint !== fixture.expectedRequestFingerprint ||
    decision.decisionFingerprint !== fixture.expectedDecisionFingerprint ||
    classify(
      request,
      decision,
      authorization,
      schema,
      request.input.validationFingerprint,
    ) !== null
  ) {
    throw new Error('Screenshot repair host-grant supported fixture changed');
  }
  for (const denominator of contract.invalidFixtures) {
    const changed = mutate(request, decision, denominator.mutation);
    const actual = classify(
      changed.request,
      changed.decision,
      authorization,
      schema,
      request.input.validationFingerprint,
    );
    if (actual !== denominator.expectedCode) {
      throw new Error(`Host-grant denominator changed: ${denominator.mutation}: ${actual}`);
    }
  }
  for (const denied of contract.deniedFixtures) {
    const value = nonGrantDecision(request, 'denied', denied.reason, denied.expectedCode);
    if (
      validateSchemaValue(value, schema).length > 0 ||
      value.executionAuthorized !== false ||
      value.diagnostics[0]?.code !== denied.expectedCode ||
      value.decisionFingerprint !== fingerprintWithout(value, 'decisionFingerprint')
    ) {
      throw new Error(`Host-grant denied result changed: ${denied.reason}`);
    }
  }
  const cancelledFixture = contract.cancelledFixtures[0];
  const cancelled = nonGrantDecision(
    request,
    'cancelled',
    cancelledFixture.reason,
    cancelledFixture.expectedCode,
  );
  if (
    validateSchemaValue(cancelled, schema).length > 0 ||
    cancelled.executionAuthorized !== false ||
    cancelled.diagnostics[0]?.code !== cancelledFixture.expectedCode ||
    cancelled.decisionFingerprint !== fingerprintWithout(cancelled, 'decisionFingerprint')
  ) {
    throw new Error('Screenshot repair host-grant cancelled result changed');
  }
  const adapter = await verifyImplementedAdapter(
    request,
    decision,
    authorization,
    validationResult,
  );
  return {
    implementation: true,
    publicRepairMode: false,
    executionAuthorized: false,
    supportedGrants: 1,
    invalidDenominators: 17,
    deniedDenominators: 5,
    cancelledDenominators: 1,
    requestFingerprint: request.requestFingerprint,
    decisionFingerprint: decision.decisionFingerprint,
    adapter,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase5ScreenshotRepairHostGrant()
    .then((summary) => {
      console.log(
        `Verified screenshot repair host-grant contract: ${summary.supportedGrants}/1 ` +
          `synthetic trusted-host grant, ${summary.invalidDenominators}/17 invalid, ` +
          `${summary.deniedDenominators}/5 denied, and ${summary.cancelledDenominators}/1 ` +
          'cancelled denominators; the internal callback is implemented and repair execution remains off.',
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
