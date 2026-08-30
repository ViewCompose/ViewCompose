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
const MAX_INPUT_BYTES = 1_048_576;
const MAX_OUTCOME_BYTES = 65_536;
const SHA256 = /^[a-f0-9]{64}$/u;
const STABLE_ID = /^[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}$/u;
const EXECUTOR_ID = 'viewcompose-internal-typed-design-ir-v1';
const trustedOutcomeHosts = new WeakMap();

let schemasPromise;
let executorBuildFingerprintPromise;

function loadSchemas() {
  schemasPromise ??= Promise.all([
    readFile(hostGrantSchemaPath, 'utf8').then(JSON.parse),
    readFile(outcomeSchemaPath, 'utf8').then(JSON.parse),
  ]).then(([hostGrant, outcome]) => ({hostGrant, outcome}));
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

function rejectBoundary(code, message, options) {
  throw new ScreenshotRepairExecutionBoundaryError(code, message, options);
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

export function createTrustedScreenshotRepairOutcomeHost({trustDomainId, record} = {}) {
  if (!STABLE_ID.test(trustDomainId ?? '') || typeof record !== 'function') {
    throw new TypeError('A stable trust domain and direct terminal-record callback are required.');
  }
  const handle = Object.freeze({trustDomainId});
  trustedOutcomeHosts.set(handle, {record});
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
  return structuredClone(outcome);
}
