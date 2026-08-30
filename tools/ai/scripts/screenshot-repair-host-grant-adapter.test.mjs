import assert from 'node:assert/strict';
import {mkdtemp, mkdir, open, readFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import test from 'node:test';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {
  createTrustedScreenshotRepairHost,
  requestScreenshotRepairHostGrant,
} from './screenshot-repair-host-grant-adapter.mjs';

const fixtureRoot = new URL('../evaluation/fixtures/visual/screenshot-repair/', import.meta.url);
const [authorization, validationResult, grantedDecision, expectedRequest, hostGrantSchema] =
  await Promise.all([
    ...[
      'rollback.authorization.json',
      'rollback.authorization-validation.json',
      'rollback.host-grant-decision.json',
      'rollback.host-grant-request.json',
    ].map((name) => readFile(new URL(name, fixtureRoot), 'utf8').then(JSON.parse)),
    readFile(new URL('../contracts/screenshot-repair-host-grant.schema.json', import.meta.url),
      'utf8').then(JSON.parse),
  ]);

function adapterInput(overrides = {}) {
  return {
    validationResult: structuredClone(validationResult),
    authorization: structuredClone(authorization),
    ...overrides,
  };
}

function sealDecision(value) {
  const result = structuredClone(value);
  delete result.decisionFingerprint;
  result.decisionFingerprint = fingerprintRepairValue(result);
  return result;
}

function assertDecision(value) {
  assert.deepEqual(validateSchemaValue(value, hostGrantSchema), []);
  const unsigned = structuredClone(value);
  delete unsigned.decisionFingerprint;
  assert.equal(value.decisionFingerprint, fingerprintRepairValue(unsigned));
}

function deniedDecision(request, reason, code) {
  return sealDecision({
    schemaVersion: 1,
    kind: 'screenshot-repair-host-grant-decision',
    status: 'denied',
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
      severity: 'error',
      message: `Host denied the screenshot repair grant: ${reason}.`,
      nextAction: 'Create a new authorization; the reserved attempt cannot be reused.',
    }],
  });
}

function durableTestHost(storeRoot, onRequest = () => {}) {
  return createTrustedScreenshotRepairHost({
    trustDomainId: 'fixture-repair-host',
    reserve: async (request) => {
      onRequest(structuredClone(request));
      await mkdir(storeRoot, {recursive: true});
      let handle;
      try {
        handle = await open(join(storeRoot, `${request.requestFingerprint}.reserved`), 'wx', 0o600);
        await handle.writeFile(`${request.requestFingerprint}\n`, 'utf8');
        await handle.sync();
      } catch (error) {
        if (error?.code === 'EEXIST') {
          return deniedDecision(
            request,
            'already-consumed',
            'VC-AI-REPAIR-HOST-GRANT-ALREADY-CONSUMED',
          );
        }
        throw error;
      } finally {
        await handle?.close();
      }
      const directoryHandle = await open(storeRoot, 'r');
      try {
        await directoryHandle.sync();
      } finally {
        await directoryHandle.close();
      }
      return structuredClone(grantedDecision);
    },
  });
}

async function temporaryStore(t) {
  const root = await mkdtemp(join(tmpdir(), 'viewcompose-host-grant-'));
  t.after(() => rm(root, {recursive: true, force: true}));
  return root;
}

test('builds the exact request and durably reserves one attended grant across host instances', async (t) => {
  const root = await temporaryStore(t);
  let observedRequest;
  const firstHost = durableTestHost(root, (request) => {
    observedRequest = request;
  });
  const granted = await requestScreenshotRepairHostGrant(adapterInput(), {host: firstHost});
  assert.deepEqual(observedRequest, expectedRequest);
  assert.equal(granted.status, 'granted');
  assert.equal(granted.grant.executionAuthorized, true);
  assert.equal(granted.grant.unattendedExecution, false);

  const reloaded = durableTestHost(root);
  const denied = await requestScreenshotRepairHostGrant(adapterInput(), {host: reloaded});
  assert.equal(denied.status, 'denied');
  assert.equal(denied.reason, 'already-consumed');
  assert.equal(denied.executionAuthorized, false);
  assertDecision(denied);
});

test('permits exactly one of two concurrent reservations for the same authorization', async (t) => {
  const host = durableTestHost(await temporaryStore(t));
  const results = await Promise.all([
    requestScreenshotRepairHostGrant(adapterInput(), {host}),
    requestScreenshotRepairHostGrant(adapterInput(), {host}),
  ]);
  assert.deepEqual(results.map((result) => result.status).sort(), ['denied', 'granted']);
  assert.equal(results.find((result) => result.status === 'denied').reason, 'already-consumed');
});

test('rejects serialized decisions and unregistered host handles before any callback', async () => {
  let calls = 0;
  const host = createTrustedScreenshotRepairHost({
    trustDomainId: 'fixture-repair-host',
    reserve: async () => {
      calls += 1;
      return structuredClone(grantedDecision);
    },
  });
  const injected = await requestScreenshotRepairHostGrant({
    ...adapterInput(),
    decision: structuredClone(grantedDecision),
  }, {host});
  assert.equal(injected.status, 'denied');
  assert.equal(injected.reason, 'input-invalid');
  assert.equal(calls, 0);
  assertDecision(injected);

  const serializedHandle = structuredClone(host);
  const untrusted = await requestScreenshotRepairHostGrant(adapterInput(), {
    host: serializedHandle,
  });
  assert.equal(untrusted.status, 'denied');
  assert.equal(untrusted.executionAuthorized, false);
  assert.equal(calls, 0);
  assertDecision(untrusted);
});

test('rejects a validly rehashed host decision with changed repair lineage', async () => {
  const changed = structuredClone(grantedDecision);
  changed.grant.changeFingerprint = 'f'.repeat(64);
  const host = createTrustedScreenshotRepairHost({
    trustDomainId: 'fixture-repair-host',
    reserve: async () => sealDecision(changed),
  });
  const result = await requestScreenshotRepairHostGrant(adapterInput(), {host});
  assert.equal(result.status, 'denied');
  assert.equal(result.reason, 'input-invalid');
  assert.equal(
    result.diagnostics[0].code,
    'VC-AI-REPAIR-HOST-GRANT-LINEAGE-MISMATCH',
  );
  assert.equal(result.executionAuthorized, false);
  assertDecision(result);
});

test('normalizes host failure without accepting a grant', async () => {
  const host = createTrustedScreenshotRepairHost({
    trustDomainId: 'fixture-repair-host',
    reserve: async () => {
      throw new Error('synthetic host failure');
    },
  });
  const result = await requestScreenshotRepairHostGrant(adapterInput(), {host});
  assert.equal(result.status, 'denied');
  assert.equal(result.reason, 'host-failed');
  assert.equal(result.diagnostics[0].code, 'VC-AI-REPAIR-HOST-GRANT-HOST-FAILED');
  assert.equal(result.executionAuthorized, false);
  assertDecision(result);
});

test('cancels before and after the direct host callback without retaining its grant', async () => {
  const before = new AbortController();
  before.abort();
  let calls = 0;
  const neverCalled = createTrustedScreenshotRepairHost({
    trustDomainId: 'fixture-repair-host',
    reserve: async () => {
      calls += 1;
      return structuredClone(grantedDecision);
    },
  });
  const preCancelled = await requestScreenshotRepairHostGrant(adapterInput(), {
    host: neverCalled,
    signal: before.signal,
  });
  assert.equal(preCancelled.status, 'cancelled');
  assert.equal(calls, 0);
  assertDecision(preCancelled);

  const during = new AbortController();
  const cancellingHost = createTrustedScreenshotRepairHost({
    trustDomainId: 'fixture-repair-host',
    reserve: async () => {
      during.abort();
      return structuredClone(grantedDecision);
    },
  });
  const cancelledAfterCallback = await requestScreenshotRepairHostGrant(adapterInput(), {
    host: cancellingHost,
    signal: during.signal,
  });
  assert.equal(cancelledAfterCallback.status, 'cancelled');
  assert.equal(cancelledAfterCallback.executionAuthorized, false);
  assertDecision(cancelledAfterCallback);
});

test('rejects a changed validation identity before invoking the trusted host', async () => {
  let calls = 0;
  const host = createTrustedScreenshotRepairHost({
    trustDomainId: 'fixture-repair-host',
    reserve: async () => {
      calls += 1;
      return structuredClone(grantedDecision);
    },
  });
  const changed = structuredClone(validationResult);
  changed.validationFingerprint = 'f'.repeat(64);
  const result = await requestScreenshotRepairHostGrant(adapterInput({
    validationResult: changed,
  }), {host});
  assert.equal(result.status, 'denied');
  assert.equal(result.reason, 'input-invalid');
  assert.equal(calls, 0);
  assertDecision(result);
});
