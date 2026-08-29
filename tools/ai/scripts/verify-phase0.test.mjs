import test from 'node:test';
import assert from 'node:assert/strict';
import {resolveFixturePath, verifyPhase0} from './verify-phase0.mjs';

test('accepts the checked-in Phase 0 contracts and evaluation corpus', async () => {
  const summary = await verifyPhase0();
  assert.equal(summary.schemas, 5);
  assert.equal(summary.reservedCapabilities, 5);
  assert.equal(summary.metrics, 27);
  assert.equal(summary.cases, 22);
  assert.equal(summary.fixtures, 19);
  assert.equal(summary.xmlFixtures, 4);
});

test('rejects a fixture that resolves outside the repository', async () => {
  await assert.rejects(resolveFixturePath('../../../../../../etc/passwd'), /escapes repository root/u);
});
