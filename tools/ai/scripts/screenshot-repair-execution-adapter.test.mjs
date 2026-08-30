import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {fingerprintRepairValue, sealRepairPatch} from './repair-orchestrator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {
  createTrustedScreenshotRepairOutcomeHost,
  executeTrustedScreenshotRepair,
  ScreenshotRepairExecutionBoundaryError,
} from './screenshot-repair-execution-adapter.mjs';
import {
  createTrustedScreenshotRepairHost,
  requestScreenshotRepairHostGrant,
} from './screenshot-repair-host-grant-adapter.mjs';

const visualRoot = new URL('../evaluation/fixtures/visual/', import.meta.url);
const repairRoot = new URL('screenshot-repair/', visualRoot);
const [authorization, validationResult, grantedDecision, resolutionResult, proposerContract,
  outcomeSchema] = await Promise.all([
  ...[
    'rollback.authorization.json',
    'rollback.authorization-validation.json',
    'rollback.host-grant-decision.json',
  ].map((name) => readFile(new URL(name, repairRoot), 'utf8').then(JSON.parse)),
  readFile(new URL('screenshot-resolution/wireframe.result.json', visualRoot), 'utf8')
    .then(JSON.parse),
  readFile(new URL('screenshot-repair-proposer-contract.json', visualRoot), 'utf8')
    .then(JSON.parse),
  readFile(new URL('../contracts/screenshot-repair-execution-outcome.schema.json', import.meta.url),
    'utf8').then(JSON.parse),
]);

function directGrant() {
  const host = createTrustedScreenshotRepairHost({
    trustDomainId: 'fixture-repair-host',
    reserve: async () => structuredClone(grantedDecision),
  });
  return requestScreenshotRepairHostGrant({validationResult, authorization}, {host});
}

function findNode(nodes, id) {
  for (const node of nodes) {
    if (node.id === id) return node;
    const child = findNode(node.children, id);
    if (child) return child;
  }
  return undefined;
}

function executionInput(grantDecision) {
  const designIr = structuredClone(resolutionResult.designIr);
  findNode(designIr.roots, 'wireframe-title')
    .properties.find((field) => field.name === 'text').value.value = 'Hello';
  assert.equal(fingerprintRepairValue(designIr), grantDecision.grant.targetDesignIrFingerprint);
  return {
    grantDecision,
    designIr,
    patch: structuredClone(proposerContract.supportedFixtures[0].expectedPatch),
  };
}

function sealOutcome(value) {
  const result = structuredClone(value);
  delete result.outcomeFingerprint;
  result.outcomeFingerprint = fingerprintRepairValue(result);
  return result;
}

function terminalHost({onDraft = () => {}, fail = false, invalid = false,
  indeterminate = false} = {}) {
  return createTrustedScreenshotRepairOutcomeHost({
    trustDomainId: 'fixture-repair-host',
    record: async (draft) => {
      onDraft(structuredClone(draft));
      if (fail) throw new Error('synthetic terminal store failure');
      let outcome = {
        ...structuredClone(draft),
        receipt: {
          issuerTrustDomainId: draft.lineage.hostTrustDomainId,
          reservationReceipt: draft.lineage.reservationReceipt,
          terminalState: 'recorded',
          outcomeTransport: 'trusted-host-callback-only',
          outcomeReceipt: fingerprintRepairValue({draft, purpose: 'terminal-outcome'}),
        },
      };
      if (indeterminate) {
        outcome.status = 'indeterminate';
        outcome.reason = 'effect-unknown';
        outcome.effect = {
          state: 'unknown',
          resultDesignIrFingerprint: null,
          patchOutputFingerprint: null,
          outputExposed: false,
        };
        outcome.diagnostics = [{
          code: 'VC-AI-REPAIR-EXECUTION-EFFECT-UNKNOWN',
          severity: 'error',
          message: 'The trusted host cannot prove whether the in-memory effect was committed.',
          nextAction: 'Reconcile without executing the consumed authorization again.',
        }];
      }
      outcome = sealOutcome(outcome);
      if (invalid) {
        outcome.receipt.reservationReceipt = 'f'.repeat(64);
        outcome = sealOutcome(outcome);
      }
      return outcome;
    },
  });
}

function assertOutcome(value) {
  assert.deepEqual(validateSchemaValue(value, outcomeSchema), []);
  const unsigned = structuredClone(value);
  delete unsigned.outcomeFingerprint;
  assert.equal(value.outcomeFingerprint, fingerprintRepairValue(unsigned));
}

function assertUnrecorded(value) {
  assert.deepEqual(
    validateSchemaValue(value, outcomeSchema.$defs.unrecordedResult, outcomeSchema),
    [],
  );
  const unsigned = structuredClone(value);
  delete unsigned.failureFingerprint;
  assert.equal(value.failureFingerprint, fingerprintRepairValue(unsigned));
}

function boundaryCode(code) {
  return (error) => {
    assert.ok(error instanceof ScreenshotRepairExecutionBoundaryError);
    assert.equal(error.code, code);
    assert.equal(error.retryAllowed, false);
    return true;
  };
}

test('consumes one direct grant and records only the committed output identities', async () => {
  const grant = await directGrant();
  let observedDraft;
  const host = terminalHost({onDraft: (draft) => {
    observedDraft = draft;
  }});
  const outcome = await executeTrustedScreenshotRepair(executionInput(grant), {host});
  assert.equal(outcome.status, 'applied');
  assert.equal(
    outcome.effect.resultDesignIrFingerprint,
    resolutionResult.designIrFingerprint,
  );
  assert.equal(outcome.effect.outputExposed, true);
  assert.equal(Object.hasOwn(observedDraft, 'receipt'), false);
  assert.equal(JSON.stringify(observedDraft).includes('"designIr"'), false);
  assertOutcome(outcome);

  await assert.rejects(
    executeTrustedScreenshotRepair(executionInput(grant), {host}),
    boundaryCode('VC-AI-REPAIR-EXECUTION-GRANT-ALREADY-CONSUMED'),
  );
});

test('rejects serialized grants and hosts without consuming the original capability', async () => {
  const grant = await directGrant();
  const host = terminalHost();
  await assert.rejects(
    executeTrustedScreenshotRepair(executionInput(structuredClone(grant)), {host}),
    boundaryCode('VC-AI-REPAIR-EXECUTION-GRANT-UNTRUSTED'),
  );
  await assert.rejects(
    executeTrustedScreenshotRepair(executionInput(grant), {host: structuredClone(host)}),
    boundaryCode('VC-AI-REPAIR-EXECUTION-HOST-UNTRUSTED'),
  );
  const outcome = await executeTrustedScreenshotRepair(executionInput(grant), {host});
  assert.equal(outcome.status, 'applied');
  assertOutcome(outcome);
});

test('records invalid typed input as a terminal non-committed failure', async () => {
  const grant = await directGrant();
  const input = executionInput(grant);
  input.patch.changeFingerprint = 'f'.repeat(64);
  const outcome = await executeTrustedScreenshotRepair(input, {host: terminalHost()});
  assert.equal(outcome.status, 'failed');
  assert.equal(outcome.reason, 'input-invalid');
  assert.equal(outcome.effect.state, 'not-committed');
  assert.equal(outcome.effect.outputExposed, false);
  assertOutcome(outcome);
});

test('does not apply a valid typed patch outside the authorized change identity', async () => {
  const grant = await directGrant();
  const input = executionInput(grant);
  input.patch = sealRepairPatch([{
    op: 'replace-field',
    nodeId: 'wireframe-title',
    collection: 'properties',
    name: 'text',
    value: {kind: 'literal', value: 'Unauthorized'},
  }]);
  const outcome = await executeTrustedScreenshotRepair(input, {host: terminalHost()});
  assert.equal(outcome.status, 'failed');
  assert.equal(outcome.reason, 'input-invalid');
  assert.equal(outcome.effect.outputExposed, false);
  assertOutcome(outcome);
});

test('records cancellation after reservation as terminal and non-retryable', async () => {
  const grant = await directGrant();
  const controller = new AbortController();
  controller.abort();
  const outcome = await executeTrustedScreenshotRepair(executionInput(grant), {
    host: terminalHost(),
    signal: controller.signal,
  });
  assert.equal(outcome.status, 'cancelled');
  assert.equal(outcome.attempt.retryAllowed, false);
  assert.equal(outcome.effect.outputExposed, false);
  assertOutcome(outcome);
});

test('accepts a trusted-host downgrade to an indeterminate terminal receipt', async () => {
  const grant = await directGrant();
  const outcome = await executeTrustedScreenshotRepair(executionInput(grant), {
    host: terminalHost({indeterminate: true}),
  });
  assert.equal(outcome.status, 'indeterminate');
  assert.equal(outcome.effect.state, 'unknown');
  assert.equal(outcome.effect.outputExposed, false);
  assertOutcome(outcome);
});

test('blocks output when the consumed grant has no valid terminal receipt', async () => {
  for (const [options, expectedReason] of [
    [{fail: true}, 'terminal-receipt-unavailable'],
    [{invalid: true}, 'terminal-receipt-invalid'],
  ]) {
    const grant = await directGrant();
    const host = terminalHost(options);
    const result = await executeTrustedScreenshotRepair(executionInput(grant), {host});
    assert.equal(result.status, 'blocked');
    assert.equal(result.reason, expectedReason);
    assert.equal(result.effect.state, 'unknown');
    assert.equal(result.retryAllowed, false);
    assertUnrecorded(result);
    await assert.rejects(
      executeTrustedScreenshotRepair(executionInput(grant), {host}),
      boundaryCode('VC-AI-REPAIR-EXECUTION-GRANT-ALREADY-CONSUMED'),
    );
  }
});

test('permits exactly one concurrent consumer of the process-local grant capability', async () => {
  const grant = await directGrant();
  const host = terminalHost();
  const settled = await Promise.allSettled([
    executeTrustedScreenshotRepair(executionInput(grant), {host}),
    executeTrustedScreenshotRepair(executionInput(grant), {host}),
  ]);
  assert.equal(settled.filter((result) => result.status === 'fulfilled').length, 1);
  assert.equal(settled.filter((result) => result.status === 'rejected').length, 1);
  assert.equal(
    settled.find((result) => result.status === 'rejected').reason.code,
    'VC-AI-REPAIR-EXECUTION-GRANT-ALREADY-CONSUMED',
  );
});
