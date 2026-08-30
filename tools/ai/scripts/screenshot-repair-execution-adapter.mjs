import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {applyDesignIrRepairPatch} from './design-ir-repair-patch.mjs';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {
  consumeTrustedScreenshotRepairGrant,
} from './screenshot-repair-host-grant-adapter.mjs';

const hostGrantSchemaPath = new URL(
  '../contracts/screenshot-repair-host-grant.schema.json',
  import.meta.url,
);
const outcomeSchemaPath = new URL(
  '../contracts/screenshot-repair-execution-outcome.schema.json',
  import.meta.url,
);
const handoffSchemaPath = new URL(
  '../contracts/screenshot-repair-applied-result-handoff.schema.json',
  import.meta.url,
);
const designIrSchemaPath = new URL('../contracts/design-ir.schema.json', import.meta.url);
const MAX_INPUT_BYTES = 1_048_576;
const MAX_OUTCOME_BYTES = 65_536;
const SHA256 = /^[a-f0-9]{64}$/u;
const STABLE_ID = /^[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}$/u;
const EXECUTOR_ID = 'viewcompose-internal-typed-design-ir-v1';
const trustedOutcomeHosts = new WeakMap();
const retainedAppliedResults = new WeakMap();

let schemasPromise;
let executorBuildFingerprintPromise;

function loadSchemas() {
  schemasPromise ??= Promise.all([
    readFile(hostGrantSchemaPath, 'utf8').then(JSON.parse),
    readFile(outcomeSchemaPath, 'utf8').then(JSON.parse),
    readFile(handoffSchemaPath, 'utf8').then(JSON.parse),
    readFile(designIrSchemaPath, 'utf8').then(JSON.parse),
  ]).then(([hostGrant, outcome, handoff, designIr]) => ({
    hostGrant,
    outcome,
    handoff,
    designIr,
  }));
  return schemasPromise;
}

function executorBuildFingerprint() {
  executorBuildFingerprintPromise ??= readFile(new URL(import.meta.url))
    .then((bytes) => createHash('sha256').update(bytes).digest('hex'));
  return executorBuildFingerprintPromise;
}

function exactKeys(value, expected) {
  return value !== null &&
    typeof value === 'object' &&
    !Array.isArray(value) &&
    JSON.stringify(Object.keys(value).sort()) === JSON.stringify([...expected].sort());
}

function encodedWithinLimit(value, limit) {
  try {
    const encoded = JSON.stringify(value);
    return typeof encoded === 'string' && Buffer.byteLength(encoded, 'utf8') <= limit;
  } catch {
    return false;
  }
}

function fingerprintWithout(value, key) {
  const copy = structuredClone(value);
  delete copy[key];
  return fingerprintRepairValue(copy);
}

function same(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

function deepFreeze(value) {
  if (value === null || typeof value !== 'object' || Object.isFrozen(value)) return value;
  for (const child of Object.values(value)) deepFreeze(child);
  return Object.freeze(value);
}

function diagnostic(code, severity, message, nextAction) {
  return {code, severity, message, nextAction};
}

export class ScreenshotRepairExecutionBoundaryError extends Error {
  constructor(code, message, {grantConsumed = false} = {}) {
    super(message);
    this.name = 'ScreenshotRepairExecutionBoundaryError';
    this.code = code;
    this.grantConsumed = grantConsumed;
    this.retryAllowed = false;
  }
}

export class ScreenshotRepairAppliedResultHandoffError extends Error {
  constructor(code, message, {retryHandoff = false} = {}) {
    super(message);
    this.name = 'ScreenshotRepairAppliedResultHandoffError';
    this.code = code;
    this.retryExecution = false;
    this.retryHandoff = retryHandoff;
  }
}

function rejectBoundary(code, message, options) {
  throw new ScreenshotRepairExecutionBoundaryError(code, message, options);
}

function rejectHandoff(code, message, options) {
  throw new ScreenshotRepairAppliedResultHandoffError(code, message, options);
}

function grantValid(grant, schema, trustDomainId) {
  return encodedWithinLimit(grant, MAX_OUTCOME_BYTES) &&
    validateSchemaValue(grant, schema).length === 0 &&
    grant.status === 'granted' &&
    grant.decisionFingerprint === fingerprintWithout(grant, 'decisionFingerprint') &&
    grant.trustDomainId === trustDomainId &&
    grant.grant.executionAuthorized === true &&
    grant.grant.unattendedExecution === false;
}

function commonOutcome(grant, buildFingerprint) {
  return {
    lineage: {
      hostGrantDecisionFingerprint: grant.decisionFingerprint,
      hostGrantRequestFingerprint: grant.requestFingerprint,
      authorizationFingerprint: grant.grant.authorizationFingerprint,
      proposalFingerprint: grant.grant.proposalFingerprint,
      changeFingerprint: grant.grant.changeFingerprint,
      inputDesignIrFingerprint: grant.grant.targetDesignIrFingerprint,
      reservationReceipt: grant.reservation.reservationReceipt,
      hostTrustDomainId: grant.trustDomainId,
    },
    attempt: {
      reservationState: 'consumed',
      attemptNumber: 1,
      maxAttempts: 1,
      terminal: true,
      reuseAllowed: false,
      retryAllowed: false,
      attendedExecution: true,
    },
    executor: {
      executorId: EXECUTOR_ID,
      executorBuildFingerprint: buildFingerprint,
      patchMode: 'typed-design-ir-in-memory',
      persistentSourceWrite: false,
      publicToolMode: false,
      callerSuppliedOutcome: false,
      credentialInput: false,
      providerCalls: false,
      toolNetworkAccess: false,
      logs: 'fingerprints-only',
    },
  };
}

function outcomeDraft(common, status, reason, effect, diagnostics) {
  return {
    schemaVersion: 1,
    kind: 'screenshot-repair-execution-outcome',
    status,
    reason,
    lineage: structuredClone(common.lineage),
    attempt: structuredClone(common.attempt),
    executor: structuredClone(common.executor),
    effect,
    diagnostics,
  };
}

function cancelledDraft(common) {
  return outcomeDraft(
    common,
    'cancelled',
    'cancelled',
    {
      state: 'not-committed',
      resultDesignIrFingerprint: null,
      patchOutputFingerprint: null,
      outputExposed: false,
    },
    [diagnostic(
      'VC-AI-REPAIR-EXECUTION-CANCELLED',
      'warning',
      'The reserved attended attempt was cancelled without exposing an output.',
      'Do not retry this authorization; request a new review if repair is still required.',
    )],
  );
}

function failedDraft(common, reason) {
  const inputInvalid = reason === 'input-invalid';
  return outcomeDraft(
    common,
    'failed',
    reason,
    {
      state: 'not-committed',
      resultDesignIrFingerprint: null,
      patchOutputFingerprint: null,
      outputExposed: false,
    },
    [diagnostic(
      inputInvalid
        ? 'VC-AI-REPAIR-EXECUTION-INPUT-INVALID'
        : 'VC-AI-REPAIR-EXECUTION-PATCH-FAILED',
      'error',
      inputInvalid
        ? 'The reserved attempt did not contain the exact authorized Design IR and typed patch.'
        : 'The reserved typed Design IR patch failed without exposing an output.',
      'Inspect the bounded diagnostic; this authorization is terminal and cannot be retried.',
    )],
  );
}

function appliedDraft(common, result) {
  return outcomeDraft(
    common,
    'applied',
    'patch-applied',
    {
      state: 'committed',
      resultDesignIrFingerprint: result.designIrFingerprint,
      patchOutputFingerprint: result.outputFingerprint,
      outputExposed: true,
    },
    [],
  );
}

function unrecordedResult(common, reason) {
  const result = {
    schemaVersion: 1,
    kind: 'screenshot-repair-execution-recording-failure',
    status: 'blocked',
    reason,
    lineage: structuredClone(common.lineage),
    attempt: structuredClone(common.attempt),
    executor: structuredClone(common.executor),
    effect: {
      state: 'unknown',
      resultDesignIrFingerprint: null,
      patchOutputFingerprint: null,
      outputExposed: false,
    },
    executionAuthorized: false,
    retryAllowed: false,
    diagnostics: [diagnostic(
      'VC-AI-REPAIR-EXECUTION-EFFECT-UNKNOWN',
      'error',
      'The consumed grant has no validated trusted-host terminal receipt.',
      'Reconcile the reservation without executing again; this grant cannot be reused.',
    )],
  };
  result.failureFingerprint = fingerprintRepairValue(result);
  return result;
}

function receiptValid(outcome, draft) {
  return outcome.receipt?.issuerTrustDomainId === draft.lineage.hostTrustDomainId &&
    outcome.receipt?.reservationReceipt === draft.lineage.reservationReceipt &&
    outcome.receipt?.terminalState === 'recorded' &&
    outcome.receipt?.outcomeTransport === 'trusted-host-callback-only' &&
    SHA256.test(outcome.receipt?.outcomeReceipt ?? '') &&
    outcome.receipt.outcomeReceipt !== draft.lineage.reservationReceipt;
}

function commonMatches(outcome, draft) {
  return same(outcome.lineage, draft.lineage) &&
    same(outcome.attempt, draft.attempt) &&
    same(outcome.executor, draft.executor);
}

function hostOutcomeMatches(outcome, draft, schema) {
  if (
    !encodedWithinLimit(outcome, MAX_OUTCOME_BYTES) ||
    validateSchemaValue(outcome, schema).length > 0 ||
    outcome.outcomeFingerprint !== fingerprintWithout(outcome, 'outcomeFingerprint') ||
    !commonMatches(outcome, draft) ||
    !receiptValid(outcome, draft)
  ) {
    return false;
  }
  if (outcome.status === 'indeterminate') {
    return outcome.effect.state === 'unknown' && outcome.effect.outputExposed === false;
  }
  const withoutReceipt = structuredClone(outcome);
  delete withoutReceipt.receipt;
  delete withoutReceipt.outcomeFingerprint;
  return same(withoutReceipt, draft);
}

export function createTrustedScreenshotRepairOutcomeHost({
  trustDomainId,
  record,
  reconcile,
} = {}) {
  if (
    !STABLE_ID.test(trustDomainId ?? '') ||
    typeof record !== 'function' ||
    (reconcile !== undefined && typeof reconcile !== 'function')
  ) {
    throw new TypeError('A stable trust domain and direct terminal-record callback are required.');
  }
  const handle = Object.freeze({trustDomainId});
  trustedOutcomeHosts.set(handle, {record, reconcile});
  return handle;
}

export async function executeTrustedScreenshotRepair(input, {host, signal} = {}) {
  const hostImplementation = host !== null && typeof host === 'object'
    ? trustedOutcomeHosts.get(host)
    : undefined;
  if (!hostImplementation) {
    rejectBoundary(
      'VC-AI-REPAIR-EXECUTION-HOST-UNTRUSTED',
      'Execution requires a direct trusted-host terminal-record callback.',
    );
  }
  if (
    !exactKeys(input, ['grantDecision', 'designIr', 'patch']) ||
    !encodedWithinLimit(input, MAX_INPUT_BYTES)
  ) {
    rejectBoundary(
      'VC-AI-REPAIR-EXECUTION-INPUT-INVALID',
      'Execution accepts only one exact grant, Design IR, and typed patch.',
    );
  }
  const schemas = await loadSchemas();
  if (!grantValid(input.grantDecision, schemas.hostGrant, host.trustDomainId)) {
    rejectBoundary(
      'VC-AI-REPAIR-EXECUTION-GRANT-INVALID',
      'The host grant is malformed or does not bind this trust domain.',
    );
  }
  const consumption = consumeTrustedScreenshotRepairGrant(input.grantDecision, {
    trustDomainId: host.trustDomainId,
  });
  if (consumption.status !== 'consumed') {
    rejectBoundary(
      consumption.status === 'already-consumed'
        ? 'VC-AI-REPAIR-EXECUTION-GRANT-ALREADY-CONSUMED'
        : 'VC-AI-REPAIR-EXECUTION-GRANT-UNTRUSTED',
      consumption.status === 'already-consumed'
        ? 'The trusted grant capability has already been consumed.'
        : 'A serialized or reconstructed host grant has no execution authority.',
      {grantConsumed: consumption.status === 'already-consumed'},
    );
  }
  const common = commonOutcome(input.grantDecision, await executorBuildFingerprint());
  let draft;
  let appliedResult;
  if (signal?.aborted) {
    draft = cancelledDraft(common);
  } else if (input.patch?.changeFingerprint !== input.grantDecision.grant.changeFingerprint) {
    draft = failedDraft(common, 'input-invalid');
  } else {
    try {
      const result = await applyDesignIrRepairPatch({
        designIr: input.designIr,
        expectedDesignIrFingerprint: input.grantDecision.grant.targetDesignIrFingerprint,
        patch: input.patch,
      }, {signal});
      appliedResult = result;
      draft = signal?.aborted ? cancelledDraft(common) : appliedDraft(common, result);
    } catch (error) {
      if (signal?.aborted || error?.code === 'VC-AI-REPAIR-CANCELLED') {
        draft = cancelledDraft(common);
      } else {
        draft = failedDraft(
          common,
          error?.code === 'VC-AI-REPAIR-INPUT-INVALID' ? 'input-invalid' : 'patch-failed',
        );
      }
    }
  }
  let outcome;
  try {
    outcome = await hostImplementation.record(structuredClone(draft));
  } catch {
    return unrecordedResult(common, 'terminal-receipt-unavailable');
  }
  if (!hostOutcomeMatches(outcome, draft, schemas.outcome)) {
    return unrecordedResult(common, 'terminal-receipt-invalid');
  }
  const returnedOutcome = deepFreeze(structuredClone(outcome));
  if (
    returnedOutcome.status === 'applied' &&
    appliedResult !== undefined &&
    typeof hostImplementation.reconcile === 'function' &&
    appliedResult.designIrFingerprint === returnedOutcome.effect.resultDesignIrFingerprint &&
    appliedResult.outputFingerprint === returnedOutcome.effect.patchOutputFingerprint
  ) {
    retainedAppliedResults.set(returnedOutcome, {
      state: 'available',
      host,
      designIr: deepFreeze(appliedResult.designIr),
      designIrFingerprint: appliedResult.designIrFingerprint,
      patchOutputFingerprint: appliedResult.outputFingerprint,
    });
  }
  return returnedOutcome;
}

function handoffReceipt(outcome) {
  const receipt = {
    schemaVersion: 1,
    kind: 'screenshot-repair-applied-result-handoff',
    status: 'delivered',
    lineage: {
      outcomeFingerprint: outcome.outcomeFingerprint,
      outcomeReceipt: outcome.receipt.outcomeReceipt,
      reservationReceipt: outcome.lineage.reservationReceipt,
      hostTrustDomainId: outcome.lineage.hostTrustDomainId,
      inputDesignIrFingerprint: outcome.lineage.inputDesignIrFingerprint,
      changeFingerprint: outcome.lineage.changeFingerprint,
      resultDesignIrFingerprint: outcome.effect.resultDesignIrFingerprint,
      patchOutputFingerprint: outcome.effect.patchOutputFingerprint,
    },
    delivery: {
      source: 'retained-in-memory-applied-result',
      terminalOutcome: 'durable-reconciled',
      terminalReceiptRevalidated: true,
      exactInMemoryObject: true,
      singleUse: true,
      persistentResultStorage: false,
      persistentSourceWrite: false,
      publicToolMode: false,
      providerCalls: false,
      toolNetworkAccess: false,
      logs: 'fingerprints-only',
    },
  };
  receipt.handoffFingerprint = fingerprintRepairValue(receipt);
  return receipt;
}

function appliedOutcomeValid(outcome, schema, retained, trustDomainId) {
  return encodedWithinLimit(outcome, MAX_OUTCOME_BYTES) &&
    validateSchemaValue(outcome, schema).length === 0 &&
    outcome.status === 'applied' &&
    outcome.effect.state === 'committed' &&
    outcome.outcomeFingerprint === fingerprintWithout(outcome, 'outcomeFingerprint') &&
    outcome.lineage.hostTrustDomainId === trustDomainId &&
    outcome.effect.resultDesignIrFingerprint === retained.designIrFingerprint &&
    outcome.effect.patchOutputFingerprint === retained.patchOutputFingerprint &&
    receiptValid(outcome, outcome);
}

export async function handoffTrustedScreenshotRepairAppliedResult(outcome, {host} = {}) {
  const retained = outcome !== null && typeof outcome === 'object'
    ? retainedAppliedResults.get(outcome)
    : undefined;
  if (!retained) {
    rejectHandoff(
      'VC-AI-REPAIR-HANDOFF-AUTHORITY-INVALID',
      'Handoff requires the exact live applied-outcome object returned by attended execution.',
    );
  }
  if (retained.state === 'delivered') {
    rejectHandoff(
      'VC-AI-REPAIR-HANDOFF-ALREADY-DELIVERED',
      'The exact applied Design IR has already been delivered.',
    );
  }
  if (retained.state === 'in-progress') {
    rejectHandoff(
      'VC-AI-REPAIR-HANDOFF-IN-PROGRESS',
      'The exact applied Design IR already has an in-progress handoff.',
    );
  }
  const hostImplementation = host !== null && typeof host === 'object'
    ? trustedOutcomeHosts.get(host)
    : undefined;
  if (
    host !== retained.host ||
    !hostImplementation ||
    typeof hostImplementation.reconcile !== 'function'
  ) {
    rejectHandoff(
      'VC-AI-REPAIR-HANDOFF-HOST-UNTRUSTED',
      'Handoff requires the original trusted host and its direct reconciliation callback.',
    );
  }

  retained.state = 'in-progress';
  try {
    const schemas = await loadSchemas();
    if (!appliedOutcomeValid(outcome, schemas.outcome, retained, host.trustDomainId)) {
      rejectHandoff(
        'VC-AI-REPAIR-HANDOFF-TERMINAL-OUTCOME-INVALID',
        'The live terminal outcome no longer proves the exact committed result.',
      );
    }
    let durableOutcome;
    try {
      durableOutcome = await hostImplementation.reconcile(
        outcome.lineage.reservationReceipt,
      );
    } catch {
      rejectHandoff(
        'VC-AI-REPAIR-HANDOFF-DURABLE-RECEIPT-UNAVAILABLE',
        'The trusted host could not reopen the terminal record.',
        {retryHandoff: true},
      );
    }
    if (durableOutcome === null || durableOutcome === undefined) {
      rejectHandoff(
        'VC-AI-REPAIR-HANDOFF-DURABLE-RECEIPT-UNAVAILABLE',
        'The trusted host has no durable terminal record for this reservation.',
        {retryHandoff: true},
      );
    }
    if (
      !appliedOutcomeValid(durableOutcome, schemas.outcome, retained, host.trustDomainId) ||
      !same(durableOutcome, outcome)
    ) {
      rejectHandoff(
        'VC-AI-REPAIR-HANDOFF-DURABLE-RECEIPT-MISMATCH',
        'The reopened terminal record does not exactly match the accepted applied outcome.',
      );
    }
    if (
      validateSchemaValue(retained.designIr, schemas.designIr).length > 0 ||
      fingerprintRepairValue(retained.designIr) !== retained.designIrFingerprint
    ) {
      rejectHandoff(
        'VC-AI-REPAIR-HANDOFF-RESULT-INTEGRITY-MISMATCH',
        'The retained in-memory Design IR no longer matches the committed result identity.',
      );
    }
    const receipt = handoffReceipt(outcome);
    if (
      !encodedWithinLimit(receipt, MAX_OUTCOME_BYTES) ||
      validateSchemaValue(receipt, schemas.handoff).length > 0 ||
      receipt.handoffFingerprint !== fingerprintWithout(receipt, 'handoffFingerprint')
    ) {
      rejectHandoff(
        'VC-AI-REPAIR-HANDOFF-RESULT-INTEGRITY-MISMATCH',
        'The applied-result handoff receipt could not reproduce its contract identity.',
      );
    }
    const designIr = retained.designIr;
    retained.state = 'delivered';
    retained.designIr = undefined;
    return deepFreeze({receipt, designIr});
  } catch (error) {
    if (retained.state === 'in-progress') retained.state = 'available';
    throw error;
  }
}
