import assert from 'node:assert/strict';
import {
  chmod,
  lstat,
  mkdtemp,
  readdir,
  readFile,
  rm,
  symlink,
} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {executeTrustedScreenshotRepair} from './screenshot-repair-execution-adapter.mjs';
import {
  createTrustedScreenshotRepairHost,
  requestScreenshotRepairHostGrant,
} from './screenshot-repair-host-grant-adapter.mjs';
import {
  createFileBackedScreenshotRepairTerminalStore,
  ScreenshotRepairTerminalStoreError,
} from './screenshot-repair-terminal-store.mjs';

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

async function temporaryRoot(t) {
  const root = await mkdtemp(join(tmpdir(), 'viewcompose-terminal-store-'));
  t.after(() => rm(root, {recursive: true, force: true}));
  return root;
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

async function createStore(root) {
  return createFileBackedScreenshotRepairTerminalStore({
    storeRoot: root,
    storeId: 'fixture-terminal-store',
    trustDomainId: grantedDecision.trustDomainId,
  });
}

function assertOutcome(value) {
  assert.deepEqual(validateSchemaValue(value, outcomeSchema), []);
  const unsigned = structuredClone(value);
  delete unsigned.outcomeFingerprint;
  assert.equal(value.outcomeFingerprint, fingerprintRepairValue(unsigned));
}

test('publishes one private durable outcome and reopens it without execution', async (t) => {
  const root = await temporaryRoot(t);
  const firstStore = await createStore(root);
  const outcome = await executeTrustedScreenshotRepair(executionInput(await directGrant()), {
    host: firstStore.host,
  });
  assert.equal(outcome.status, 'applied');
  assertOutcome(outcome);

  const reopened = await createStore(root);
  const reconciled = await reopened.readTerminalOutcome(
    grantedDecision.reservation.reservationReceipt,
  );
  assert.deepEqual(reconciled, outcome);
  assertOutcome(reconciled);

  const names = await readdir(root);
  assert.deepEqual(names, [
    `${grantedDecision.reservation.reservationReceipt}.terminal.json`,
  ]);
  const info = await lstat(join(root, names[0]));
  assert.equal(info.isFile(), true);
  assert.equal(info.mode & 0o077, 0);
});

test('returns the exact existing receipt for an idempotent cross-instance draft', async (t) => {
  const root = await temporaryRoot(t);
  const first = await createStore(root);
  const second = await createStore(root);
  const firstOutcome = await executeTrustedScreenshotRepair(executionInput(await directGrant()), {
    host: first.host,
  });
  const secondOutcome = await executeTrustedScreenshotRepair(executionInput(await directGrant()), {
    host: second.host,
  });
  assert.deepEqual(secondOutcome, firstOutcome);
  assert.deepEqual(
    await second.readTerminalOutcome(grantedDecision.reservation.reservationReceipt),
    firstOutcome,
  );
});

test('atomically preserves one of two conflicting terminal drafts without overwrite', async (t) => {
  const root = await temporaryRoot(t);
  const store = await createStore(root);
  const cancelled = new AbortController();
  cancelled.abort();
  const [applied, cancelledResult] = await Promise.all([
    executeTrustedScreenshotRepair(executionInput(await directGrant()), {host: store.host}),
    executeTrustedScreenshotRepair(executionInput(await directGrant()), {
      host: store.host,
      signal: cancelled.signal,
    }),
  ]);
  const results = [applied, cancelledResult];
  assert.equal(results.filter((result) => result.kind ===
    'screenshot-repair-execution-outcome').length, 1);
  assert.equal(results.filter((result) => result.kind ===
    'screenshot-repair-execution-recording-failure').length, 1);
  const recorded = results.find((result) => result.kind ===
    'screenshot-repair-execution-outcome');
  const blocked = results.find((result) => result.kind ===
    'screenshot-repair-execution-recording-failure');
  assert.equal(blocked.effect.state, 'unknown');
  assert.equal(blocked.effect.outputExposed, false);
  assert.equal(blocked.retryAllowed, false);
  assert.deepEqual(
    await store.readTerminalOutcome(grantedDecision.reservation.reservationReceipt),
    recorded,
  );
});

test('fails closed on an unsafe root and a corrupted terminal record', async (t) => {
  const parent = await temporaryRoot(t);
  const privateRoot = join(parent, 'private-store');
  const linkRoot = join(parent, 'linked-store');
  const store = await createStore(privateRoot);
  await executeTrustedScreenshotRepair(executionInput(await directGrant()), {host: store.host});
  const recordPath = join(
    privateRoot,
    `${grantedDecision.reservation.reservationReceipt}.terminal.json`,
  );
  await chmod(recordPath, 0o644);
  await assert.rejects(
    store.readTerminalOutcome(grantedDecision.reservation.reservationReceipt),
    (error) => error instanceof ScreenshotRepairTerminalStoreError &&
      error.code === 'VC-AI-REPAIR-TERMINAL-STORE-RECORD-CORRUPT' &&
      error.retryExecution === false,
  );

  await symlink(privateRoot, linkRoot, 'dir');
  await assert.rejects(
    createStore(linkRoot),
    (error) => error instanceof ScreenshotRepairTerminalStoreError &&
      error.code === 'VC-AI-REPAIR-TERMINAL-STORE-ROOT-UNSAFE',
  );
});
