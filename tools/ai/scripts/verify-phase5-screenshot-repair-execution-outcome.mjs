import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {
  createTrustedScreenshotRepairOutcomeHost,
  executeTrustedScreenshotRepair,
} from './screenshot-repair-execution-adapter.mjs';
import {
  createTrustedScreenshotRepairHost,
  requestScreenshotRepairHostGrant,
} from './screenshot-repair-host-grant-adapter.mjs';

const visualRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));
const contractPath = resolve(visualRoot, 'screenshot-repair-execution-outcome-contract.json');
const schemaPath = fileURLToPath(new URL(
  '../contracts/screenshot-repair-execution-outcome.schema.json',
  import.meta.url,
));
const hostGrantPath = resolve(
  visualRoot,
  'screenshot-repair/rollback.host-grant-decision.json',
);
const MAX_RECORD_BYTES = 65_536;
const INTEGRITY = 'VC-AI-REPAIR-EXECUTION-INTEGRITY-MISMATCH';
const LINEAGE = 'VC-AI-REPAIR-EXECUTION-LINEAGE-MISMATCH';
const POLICY = 'VC-AI-REPAIR-EXECUTION-ATTEMPT-POLICY-VIOLATION';
const INPUT = 'VC-AI-REPAIR-EXECUTION-INPUT-INVALID';
const OUTCOME = 'VC-AI-REPAIR-EXECUTION-OUTCOME-INVALID';
const RECEIPT = 'VC-AI-REPAIR-EXECUTION-RECEIPT-INVALID';

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

function assertContract(contract, schema) {
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-screenshot-repair-execution-outcome-v1' ||
    !same(contract.requiresContracts, [
      'screenshot-repair-authorization-v1',
      'screenshot-repair-host-grant-v1',
      'screenshot-repair-execution-outcome-v1',
    ]) ||
    contract.activation?.tool !== 'generate_screenshot_viewcompose' ||
    contract.activation?.status !== 'implemented-internal' ||
    contract.activation?.publicRepairMode !== false ||
    contract.activation?.implementation !== true ||
    contract.activation?.executionAuthorized !== false
  ) {
    throw new Error('Screenshot repair execution-outcome activation boundary changed');
  }
  if (
    contract.boundary?.attempt !==
      'the reserved attempt is consumed exactly once and is terminal for every outcome' ||
    contract.boundary?.patchMode !==
      'typed Design IR in memory only; persistent source writes remain forbidden' ||
    contract.boundary?.providerCalls !== false ||
    contract.boundary?.toolNetworkAccess !== false ||
    contract.boundary?.unattendedExecution !== false ||
    contract.policy?.maxAttempts !== 1 ||
    contract.policy?.grantReuse !== false ||
    contract.policy?.retryAfterAnyOutcome !== false ||
    contract.policy?.outcomeTransport !== 'trusted-host-callback-only' ||
    contract.policy?.callerSuppliedOutcome !== false ||
    contract.policy?.indeterminateHandling !== 'terminal-no-reexecution' ||
    contract.policy?.publicActivation !== false
  ) {
    throw new Error('Screenshot repair execution-outcome policy changed');
  }
  const checked = [
    'every outcome consumes attempt one of one and forbids reuse or retry',
    'only an applied committed outcome exposes content-addressed output identities',
    'failed and cancelled outcomes prove no committed output while indeterminate outcomes make no effect claim',
  ];
  const notClaimed = [
    'a production durable terminal store, source-writing executor, or recovery workflow',
    'atomic persistence across patch effect and terminal receipt',
    'automatic rollback or recovery after an indeterminate effect',
    'persistent application source changes or public repair execution',
  ];
  if (
    checked.some((claim) => !contract.claims?.checked?.includes(claim)) ||
    notClaimed.some((claim) => !contract.claims?.notClaimed?.includes(claim))
  ) {
    throw new Error('Screenshot repair execution-outcome claims changed');
  }
  const diagnostics = [
    INPUT,
    INTEGRITY,
    LINEAGE,
    POLICY,
    OUTCOME,
    RECEIPT,
    'VC-AI-REPAIR-EXECUTION-PATCH-FAILED',
    'VC-AI-REPAIR-EXECUTION-CANCELLED',
    'VC-AI-REPAIR-EXECUTION-EFFECT-UNKNOWN',
  ];
  if (!same(contract.diagnosticCodes, diagnostics)) {
    throw new Error('Screenshot repair execution-outcome diagnostics changed');
  }
  if (
    schema.$id !==
      'https://schemas.viewcompose.com/ai/screenshot-repair-execution-outcome-v1.schema.json' ||
    schema.oneOf?.length !== 4 ||
    schema.$defs?.terminalAttempt?.properties?.maxAttempts?.const !== 1 ||
    schema.$defs?.terminalAttempt?.properties?.terminal?.const !== true ||
    schema.$defs?.terminalAttempt?.properties?.reuseAllowed?.const !== false ||
    schema.$defs?.terminalAttempt?.properties?.retryAllowed?.const !== false ||
    schema.$defs?.executor?.properties?.patchMode?.const !== 'typed-design-ir-in-memory' ||
    schema.$defs?.executor?.properties?.persistentSourceWrite?.const !== false ||
    schema.$defs?.executor?.properties?.publicToolMode?.const !== false ||
    schema.$defs?.executor?.properties?.callerSuppliedOutcome?.const !== false ||
    schema.$defs?.unrecordedResult?.properties?.retryAllowed?.const !== false ||
    schema.$defs?.unrecordedResult?.properties?.effect?.$ref !== '#/$defs/unknownEffect' ||
    schema.$defs?.terminalReceipt?.properties?.outcomeTransport?.const !==
      'trusted-host-callback-only'
  ) {
    throw new Error('Screenshot repair execution-outcome schema boundary changed');
  }
}

function lineageMatches(outcome, grant) {
  return outcome.lineage.hostGrantDecisionFingerprint === grant.decisionFingerprint &&
    outcome.lineage.hostGrantRequestFingerprint === grant.requestFingerprint &&
    outcome.lineage.authorizationFingerprint === grant.grant.authorizationFingerprint &&
    outcome.lineage.proposalFingerprint === grant.grant.proposalFingerprint &&
    outcome.lineage.changeFingerprint === grant.grant.changeFingerprint &&
    outcome.lineage.inputDesignIrFingerprint === grant.grant.targetDesignIrFingerprint &&
    outcome.lineage.reservationReceipt === grant.reservation.reservationReceipt &&
    outcome.lineage.hostTrustDomainId === grant.trustDomainId;
}

function attemptIsTerminal(outcome) {
  return outcome.attempt?.reservationState === 'consumed' &&
    outcome.attempt?.attemptNumber === 1 &&
    outcome.attempt?.maxAttempts === 1 &&
    outcome.attempt?.terminal === true &&
    outcome.attempt?.reuseAllowed === false &&
    outcome.attempt?.retryAllowed === false &&
    outcome.attempt?.attendedExecution === true;
}

function executorIsIsolated(outcome) {
  return outcome.executor?.patchMode === 'typed-design-ir-in-memory' &&
    outcome.executor?.persistentSourceWrite === false &&
    outcome.executor?.publicToolMode === false &&
    outcome.executor?.callerSuppliedOutcome === false &&
    outcome.executor?.credentialInput === false &&
    outcome.executor?.providerCalls === false &&
    outcome.executor?.toolNetworkAccess === false &&
    outcome.executor?.logs === 'fingerprints-only';
}

function effectMatchesStatus(outcome) {
  if (outcome.status === 'applied') {
    return outcome.effect?.state === 'committed' &&
      typeof outcome.effect?.resultDesignIrFingerprint === 'string' &&
      typeof outcome.effect?.patchOutputFingerprint === 'string' &&
      outcome.effect?.outputExposed === true &&
      outcome.diagnostics?.length === 0;
  }
  if (outcome.status === 'failed' || outcome.status === 'cancelled') {
    return outcome.effect?.state === 'not-committed' &&
      outcome.effect?.resultDesignIrFingerprint === null &&
      outcome.effect?.patchOutputFingerprint === null &&
      outcome.effect?.outputExposed === false &&
      outcome.diagnostics?.length > 0;
  }
  return outcome.status === 'indeterminate' &&
    outcome.effect?.state === 'unknown' &&
    outcome.effect?.resultDesignIrFingerprint === null &&
    outcome.effect?.patchOutputFingerprint === null &&
    outcome.effect?.outputExposed === false &&
    outcome.diagnostics?.length > 0;
}

function receiptMatches(outcome) {
  return outcome.receipt?.issuerTrustDomainId === outcome.lineage?.hostTrustDomainId &&
    outcome.receipt?.reservationReceipt === outcome.lineage?.reservationReceipt &&
    outcome.receipt?.terminalState === 'recorded' &&
    outcome.receipt?.outcomeTransport === 'trusted-host-callback-only' &&
    outcome.receipt?.outcomeReceipt !== outcome.lineage?.reservationReceipt;
}

function classifyOutcome(outcome, schema, grant) {
  let encoded;
  try {
    encoded = JSON.stringify(outcome);
  } catch {
    return INPUT;
  }
  if (typeof encoded !== 'string' || Buffer.byteLength(encoded, 'utf8') > MAX_RECORD_BYTES) {
    return INPUT;
  }
  if (outcome?.outcomeFingerprint !== fingerprintWithout(outcome, 'outcomeFingerprint')) {
    return INTEGRITY;
  }
  if (!attemptIsTerminal(outcome)) return POLICY;
  if (!executorIsIsolated(outcome)) return INPUT;
  if (!lineageMatches(outcome, grant)) return LINEAGE;
  if (!receiptMatches(outcome)) return RECEIPT;
  if (!effectMatchesStatus(outcome)) return OUTCOME;
  if (validateSchemaValue(outcome, schema).length > 0) return INPUT;
  return null;
}

function seal(value) {
  const result = structuredClone(value);
  delete result.outcomeFingerprint;
  result.outcomeFingerprint = fingerprintRepairValue(result);
  return result;
}

function findNode(nodes, id) {
  for (const node of nodes) {
    if (node.id === id) return node;
    const child = findNode(node.children, id);
    if (child) return child;
  }
  return undefined;
}

async function directGrant(hostGrant, authorization, validationResult) {
  const host = createTrustedScreenshotRepairHost({
    trustDomainId: hostGrant.trustDomainId,
    reserve: async () => structuredClone(hostGrant),
  });
  return requestScreenshotRepairHostGrant({authorization, validationResult}, {host});
}

function executionInput(grantDecision, resolutionResult, proposerContract) {
  const designIr = structuredClone(resolutionResult.designIr);
  const title = findNode(designIr.roots, 'wireframe-title');
  title.properties.find((field) => field.name === 'text').value.value = 'Hello';
  if (fingerprintRepairValue(designIr) !== grantDecision.grant.targetDesignIrFingerprint) {
    throw new Error('Screenshot repair execution candidate identity changed');
  }
  return {
    grantDecision,
    designIr,
    patch: structuredClone(proposerContract.supportedFixtures[0].expectedPatch),
  };
}

function outcomeHost(hostGrant, {fail = false} = {}) {
  return createTrustedScreenshotRepairOutcomeHost({
    trustDomainId: hostGrant.trustDomainId,
    record: async (draft) => {
      if (fail) throw new Error('synthetic terminal store failure');
      const outcome = {
        ...structuredClone(draft),
        receipt: {
          issuerTrustDomainId: draft.lineage.hostTrustDomainId,
          reservationReceipt: draft.lineage.reservationReceipt,
          terminalState: 'recorded',
          outcomeTransport: 'trusted-host-callback-only',
          outcomeReceipt: fingerprintRepairValue({draft, purpose: 'terminal-outcome'}),
        },
      };
      outcome.outcomeFingerprint = fingerprintRepairValue(outcome);
      return outcome;
    },
  });
}

async function verifyImplementedAdapter({
  hostGrant,
  authorization,
  validationResult,
  resolutionResult,
  proposerContract,
  schema,
}) {
  const host = outcomeHost(hostGrant);
  const grant = await directGrant(hostGrant, authorization, validationResult);
  const outcome = await executeTrustedScreenshotRepair(
    executionInput(grant, resolutionResult, proposerContract),
    {host},
  );
  let replayCode;
  try {
    await executeTrustedScreenshotRepair(
      executionInput(grant, resolutionResult, proposerContract),
      {host},
    );
  } catch (error) {
    replayCode = error?.code;
  }

  const serializableGrant = await directGrant(hostGrant, authorization, validationResult);
  let serializedCode;
  try {
    await executeTrustedScreenshotRepair(
      executionInput(structuredClone(serializableGrant), resolutionResult, proposerContract),
      {host},
    );
  } catch (error) {
    serializedCode = error?.code;
  }

  const unrecordedGrant = await directGrant(hostGrant, authorization, validationResult);
  const unrecorded = await executeTrustedScreenshotRepair(
    executionInput(unrecordedGrant, resolutionResult, proposerContract),
    {host: outcomeHost(hostGrant, {fail: true})},
  );
  if (
    outcome.status !== 'applied' ||
    outcome.effect.outputExposed !== true ||
    validateSchemaValue(outcome, schema).length > 0 ||
    replayCode !== 'VC-AI-REPAIR-EXECUTION-GRANT-ALREADY-CONSUMED' ||
    serializedCode !== 'VC-AI-REPAIR-EXECUTION-GRANT-UNTRUSTED' ||
    unrecorded.status !== 'blocked' ||
    unrecorded.effect.outputExposed !== false ||
    unrecorded.retryAllowed !== false ||
    validateSchemaValue(unrecorded, schema.$defs.unrecordedResult, schema).length > 0
  ) {
    throw new Error('Screenshot repair execution adapter boundary changed');
  }
  return {
    directApplications: 1,
    replayedApplications: 0,
    serializedGrantsAccepted: 0,
    unrecordedOutputsExposed: 0,
  };
}

function mutateFixture(mutation, fixtures) {
  const [applied, failed, cancelled, indeterminate] = fixtures;
  const result = structuredClone(applied);
  switch (mutation) {
    case 'changed-outcome-fingerprint':
      result.outcomeFingerprint = 'f'.repeat(64);
      return result;
    case 'host-grant-decision-mismatch':
      result.lineage.hostGrantDecisionFingerprint = 'f'.repeat(64);
      break;
    case 'host-grant-request-mismatch':
      result.lineage.hostGrantRequestFingerprint = 'f'.repeat(64);
      break;
    case 'authorization-mismatch':
      result.lineage.authorizationFingerprint = 'f'.repeat(64);
      break;
    case 'proposal-mismatch':
      result.lineage.proposalFingerprint = 'f'.repeat(64);
      break;
    case 'change-mismatch':
      result.lineage.changeFingerprint = 'f'.repeat(64);
      break;
    case 'input-design-ir-mismatch':
      result.lineage.inputDesignIrFingerprint = 'f'.repeat(64);
      break;
    case 'reservation-lineage-mismatch':
      result.lineage.reservationReceipt = 'f'.repeat(64);
      break;
    case 'trust-domain-mismatch':
      result.lineage.hostTrustDomainId = 'other-trust-domain';
      break;
    case 'second-attempt':
      result.attempt.attemptNumber = 2;
      break;
    case 'non-terminal-attempt':
      result.attempt.terminal = false;
      break;
    case 'retry-allowed':
      result.attempt.retryAllowed = true;
      break;
    case 'unattended-execution':
      result.attempt.attendedExecution = false;
      break;
    case 'persistent-source-write':
      result.executor.persistentSourceWrite = true;
      break;
    case 'public-tool-mode':
      result.executor.publicToolMode = true;
      break;
    case 'caller-supplied-outcome':
      result.executor.callerSuppliedOutcome = true;
      break;
    case 'applied-without-output':
      result.effect = structuredClone(failed.effect);
      break;
    case 'failed-exposes-output':
      Object.assign(result, {status: failed.status, reason: failed.reason});
      result.effect = structuredClone(applied.effect);
      result.diagnostics = structuredClone(failed.diagnostics);
      break;
    case 'cancelled-committed':
      Object.assign(result, {status: cancelled.status, reason: cancelled.reason});
      result.effect = structuredClone(applied.effect);
      result.diagnostics = structuredClone(cancelled.diagnostics);
      break;
    case 'indeterminate-claims-output':
      Object.assign(result, {status: indeterminate.status, reason: indeterminate.reason});
      result.effect = structuredClone(applied.effect);
      result.diagnostics = structuredClone(indeterminate.diagnostics);
      break;
    case 'receipt-issuer-mismatch':
      result.receipt.issuerTrustDomainId = 'other-trust-domain';
      break;
    case 'receipt-reservation-mismatch':
      result.receipt.reservationReceipt = 'f'.repeat(64);
      break;
    case 'receipt-reuses-reservation':
      result.receipt.outcomeReceipt = result.lineage.reservationReceipt;
      break;
    case 'raw-design-ir-output':
      result.effect.designIr = {};
      break;
    default:
      throw new Error(`Unknown screenshot repair execution-outcome mutation: ${mutation}`);
  }
  return seal(result);
}

export async function verifyPhase5ScreenshotRepairExecutionOutcome() {
  const [contract, schema, hostGrant, authorization, validationResult, resolutionResult,
    proposerContract] = await Promise.all([
    readJson(contractPath),
    readJson(schemaPath),
    readJson(hostGrantPath),
    readJson(resolve(visualRoot, 'screenshot-repair/rollback.authorization.json')),
    readJson(resolve(visualRoot, 'screenshot-repair/rollback.authorization-validation.json')),
    readJson(resolve(visualRoot, 'screenshot-resolution/wireframe.result.json')),
    readJson(resolve(visualRoot, 'screenshot-repair-proposer-contract.json')),
  ]);
  assertContract(contract, schema);
  if (contract.supportedFixtures?.length !== 4 || contract.invalidFixtures?.length !== 24) {
    throw new Error('Screenshot repair execution-outcome denominator changed');
  }
  const fixtures = await Promise.all(
    contract.supportedFixtures.map((fixture) => readJson(resolve(visualRoot, fixture.file))),
  );
  for (const [index, outcome] of fixtures.entries()) {
    const expected = contract.supportedFixtures[index];
    if (
      outcome.status !== expected.expectedStatus ||
      outcome.effect?.state !== expected.expectedEffectState ||
      outcome.outcomeFingerprint !== expected.expectedOutcomeFingerprint ||
      classifyOutcome(outcome, schema, hostGrant) !== null
    ) {
      throw new Error(`Screenshot repair execution outcome ${expected.id} changed`);
    }
  }
  for (const invalid of contract.invalidFixtures) {
    const actual = classifyOutcome(mutateFixture(invalid.mutation, fixtures), schema, hostGrant);
    if (actual !== invalid.expectedCode) {
      throw new Error(
        `${invalid.mutation}: expected ${invalid.expectedCode}, received ${actual ?? 'accepted'}`,
      );
    }
  }
  const adapter = await verifyImplementedAdapter({
    hostGrant,
    authorization,
    validationResult,
    resolutionResult,
    proposerContract,
    schema,
  });
  return {
    implementation: true,
    publicRepairMode: false,
    executionAuthorized: false,
    terminalOutcomes: fixtures.length,
    invalidDenominators: contract.invalidFixtures.length,
    statuses: fixtures.map((outcome) => outcome.status),
    outputBearingOutcomes: fixtures.filter((outcome) => outcome.effect.outputExposed).length,
    retryableOutcomes: fixtures.filter((outcome) => outcome.attempt.retryAllowed).length,
    adapter,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase5ScreenshotRepairExecutionOutcome()
    .then((summary) => {
      console.log(
        `Verified screenshot repair execution-outcome contract: ${summary.terminalOutcomes}/4 ` +
          `terminal outcomes and ${summary.invalidDenominators}/24 invalid denominators; ` +
          `${summary.outputBearingOutcomes}/1 outcomes expose output, ` +
          `${summary.retryableOutcomes}/0 are retryable; the internal attended adapter is ` +
          'implemented and public execution remains off.',
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
