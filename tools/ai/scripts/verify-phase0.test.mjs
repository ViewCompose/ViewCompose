import test from 'node:test';
import assert from 'node:assert/strict';
import {resolveFixturePath, verifyPhase0} from './verify-phase0.mjs';

test('accepts the checked-in Phase 0 contracts and evaluation corpus', async () => {
  const summary = await verifyPhase0();
  assert.equal(summary.schemas, 6);
  assert.equal(summary.reservedCapabilities, 5);
  assert.equal(summary.metrics, 30);
  assert.equal(summary.cases, 25);
  assert.equal(summary.fixtures, 22);
  assert.equal(summary.xmlFixtures, 4);
  assert.equal(summary.xmlProjectContextFixtures, 3);
});

test('rejects a fixture that resolves outside the repository', async () => {
  await assert.rejects(resolveFixturePath('../../../../../../etc/passwd'), /escapes repository root/u);
});
