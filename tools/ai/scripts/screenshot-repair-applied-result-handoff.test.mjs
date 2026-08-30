import assert from 'node:assert/strict';
import {mkdtemp, readFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {
  createTrustedScreenshotRepairOutcomeHost,
  executeTrustedScreenshotRepair,
  handoffTrustedScreenshotRepairAppliedResult,
  ScreenshotRepairAppliedResultHandoffError,
} from './screenshot-repair-execution-adapter.mjs';
import {
  createTrustedScreenshotRepairHost,
  requestScreenshotRepairHostGrant,
} from './screenshot-repair-host-grant-adapter.mjs';
import {
  createFileBackedScreenshotRepairTerminalStore,
} from './screenshot-repair-terminal-store.mjs';

const visualRoot = new URL('../evaluation/fixtures/visual/', import.meta.url);
const repairRoot = new URL('screenshot-repair/', visualRoot);
const [authorization, validationResult, grantedDecision, resolutionResult, proposerContract,
  designIrSchema, handoffSchema] = await Promise.all([
  ...[
    'rollback.authorization.json',
    'rollback.authorization-validation.json',
    'rollback.host-grant-decision.json',
  ].map((name) => readFile(new URL(name, repairRoot), 'utf8').then(JSON.parse)),
  readFile(new URL('screenshot-resolution/wireframe.result.json', visualRoot), 'utf8')
    .then(JSON.parse),
  readFile(new URL('screenshot-repair-proposer-contract.json', visualRoot), 'utf8')
    .then(JSON.parse),
  readFile(new URL('../contracts/design-ir.schema.json', import.meta.url), 'utf8')
    .then(JSON.parse),
  readFile(new URL(
    '../contracts/screenshot-repair-applied-result-handoff.schema.json',
    import.meta.url,
  ), 'utf8').then(JSON.parse),
]);

async function temporaryStore(t) {
  const root = await mkdtemp(join(tmpdir(), 'viewcompose-applied-handoff-'));
  t.after(() => rm(root, {recursive: true, force: true}));
  return createFileBackedScreenshotRepairTerminalStore({
    storeRoot: root,
    storeId: 'fixture-applied-result-store',
    trustDomainId: grantedDecision.trustDomainId,
  });
}

function directGrant() {
  const host = createTrustedScreenshotRepairHost({
    trustDomainId: grantedDecision.trustDomainId,
    reserve: async () => structuredClone(grantedDecision),
  });
  return requestScreenshotRepairHostGrant({authorization, validationResult}, {host});
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

function handoffCode(code, retryHandoff = false) {
  return (error) => {
    assert.ok(error instanceof ScreenshotRepairAppliedResultHandoffError);
    assert.equal(error.code, code);
    assert.equal(error.retryExecution, false);
    assert.equal(error.retryHandoff, retryHandoff);
    return true;
  };
}

function assertHandoff(result, outcome) {
  assert.deepEqual(validateSchemaValue(result.receipt, handoffSchema), []);
  assert.deepEqual(validateSchemaValue(result.designIr, designIrSchema), []);
  const unsigned = structuredClone(result.receipt);
  delete unsigned.handoffFingerprint;
  assert.equal(result.receipt.handoffFingerprint, fingerprintRepairValue(unsigned));
  assert.equal(fingerprintRepairValue(result.designIr), outcome.effect.resultDesignIrFingerprint);
  assert.equal(Object.isFrozen(result), true);
  assert.equal(Object.isFrozen(result.receipt), true);
  assert.equal(Object.isFrozen(result.designIr), true);
  assert.equal(Object.isFrozen(result.designIr.roots[0]), true);
}

function sealOutcome(draft) {
  const outcome = {
    ...structuredClone(draft),
    receipt: {
      issuerTrustDomainId: draft.lineage.hostTrustDomainId,
      reservationReceipt: draft.lineage.reservationReceipt,
      terminalState: 'recorded',
      outcomeTransport: 'trusted-host-callback-only',
      outcomeReceipt: fingerprintRepairValue({draft, purpose: 'handoff-test-terminal'}),
    },
  };
  outcome.outcomeFingerprint = fingerprintRepairValue(outcome);
  return outcome;
}

test('delivers one frozen applied Design IR only after durable terminal reconciliation', async (t) => {
  const store = await temporaryStore(t);
  const outcome = await executeTrustedScreenshotRepair(executionInput(await directGrant()), {
    host: store.host,
  });
  const result = await handoffTrustedScreenshotRepairAppliedResult(outcome, {host: store.host});
  assertHandoff(result, outcome);
  assert.deepEqual(result.designIr, resolutionResult.designIr);
  assert.equal(result.receipt.delivery.terminalReceiptRevalidated, true);
  assert.equal(result.receipt.delivery.persistentSourceWrite, false);

  await assert.rejects(
    handoffTrustedScreenshotRepairAppliedResult(outcome, {host: store.host}),
    handoffCode('VC-AI-REPAIR-HANDOFF-ALREADY-DELIVERED'),
  );
});

test('rejects serialized authority and a reconstructed host before delivery', async (t) => {
  const store = await temporaryStore(t);
  const outcome = await executeTrustedScreenshotRepair(executionInput(await directGrant()), {
    host: store.host,
  });
  await assert.rejects(
    handoffTrustedScreenshotRepairAppliedResult(structuredClone(outcome), {host: store.host}),
    handoffCode('VC-AI-REPAIR-HANDOFF-AUTHORITY-INVALID'),
  );
  await assert.rejects(
    handoffTrustedScreenshotRepairAppliedResult(outcome, {host: structuredClone(store.host)}),
    handoffCode('VC-AI-REPAIR-HANDOFF-HOST-UNTRUSTED'),
  );
  assertHandoff(
    await handoffTrustedScreenshotRepairAppliedResult(outcome, {host: store.host}),
    outcome,
  );
});

test('does not create handoff authority for non-applied or non-durable outcomes', async (t) => {
  const store = await temporaryStore(t);
  const cancelled = new AbortController();
  cancelled.abort();
  const cancelledOutcome = await executeTrustedScreenshotRepair(
    executionInput(await directGrant()),
    {host: store.host, signal: cancelled.signal},
  );
  await assert.rejects(
    handoffTrustedScreenshotRepairAppliedResult(cancelledOutcome, {host: store.host}),
    handoffCode('VC-AI-REPAIR-HANDOFF-AUTHORITY-INVALID'),
  );

  const memoryHost = createTrustedScreenshotRepairOutcomeHost({
    trustDomainId: grantedDecision.trustDomainId,
    record: async (draft) => sealOutcome(draft),
  });
  const memoryOutcome = await executeTrustedScreenshotRepair(executionInput(await directGrant()), {
    host: memoryHost,
  });
  await assert.rejects(
    handoffTrustedScreenshotRepairAppliedResult(memoryOutcome, {host: memoryHost}),
    handoffCode('VC-AI-REPAIR-HANDOFF-AUTHORITY-INVALID'),
  );
});

test('permits only one concurrent handoff of the retained result', async (t) => {
  const store = await temporaryStore(t);
  const outcome = await executeTrustedScreenshotRepair(executionInput(await directGrant()), {
    host: store.host,
  });
  const settled = await Promise.allSettled([
    handoffTrustedScreenshotRepairAppliedResult(outcome, {host: store.host}),
    handoffTrustedScreenshotRepairAppliedResult(outcome, {host: store.host}),
  ]);
  assert.equal(settled.filter((entry) => entry.status === 'fulfilled').length, 1);
  const rejected = settled.find((entry) => entry.status === 'rejected');
  assert.equal(rejected.reason.code, 'VC-AI-REPAIR-HANDOFF-IN-PROGRESS');
});

test('fails closed on missing or changed durable receipts and permits a read retry', async () => {
  let recorded;
  let mode = 'missing';
  const host = createTrustedScreenshotRepairOutcomeHost({
    trustDomainId: grantedDecision.trustDomainId,
    record: async (draft) => {
      recorded = sealOutcome(draft);
      return structuredClone(recorded);
    },
    reconcile: async () => {
      if (mode === 'missing') return null;
      if (mode === 'mismatch') {
        const changed = structuredClone(recorded);
        changed.receipt.outcomeReceipt = 'f'.repeat(64);
        delete changed.outcomeFingerprint;
        changed.outcomeFingerprint = fingerprintRepairValue(changed);
        return changed;
      }
      return structuredClone(recorded);
    },
  });
  const outcome = await executeTrustedScreenshotRepair(executionInput(await directGrant()), {host});
  await assert.rejects(
    handoffTrustedScreenshotRepairAppliedResult(outcome, {host}),
    handoffCode('VC-AI-REPAIR-HANDOFF-DURABLE-RECEIPT-UNAVAILABLE', true),
  );
  mode = 'mismatch';
  await assert.rejects(
    handoffTrustedScreenshotRepairAppliedResult(outcome, {host}),
    handoffCode('VC-AI-REPAIR-HANDOFF-DURABLE-RECEIPT-MISMATCH'),
  );
  mode = 'exact';
  assertHandoff(await handoffTrustedScreenshotRepairAppliedResult(outcome, {host}), outcome);
});
