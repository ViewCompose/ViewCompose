import test from 'node:test';
import assert from 'node:assert/strict';
import {resolveFixturePath, verifyPhase0} from './verify-phase0.mjs';

test('accepts the checked-in Phase 0 contracts and evaluation corpus', async () => {
  const summary = await verifyPhase0();
  assert.equal(summary.schemas, 8);
  assert.equal(summary.reservedCapabilities, 5);
  assert.equal(summary.metrics, 35);
  assert.equal(summary.cases, 34);
  assert.equal(summary.fixtures, 31);
  assert.equal(summary.xmlFixtures, 4);
  assert.equal(summary.xmlV2Fixtures, 2);
  assert.equal(summary.xmlProjectContextFixtures, 3);
  assert.equal(summary.xmlLayoutDependencyFixtures, 3);
  assert.equal(summary.generatedPreviewFixtures, 4);
});

test('rejects a fixture that resolves outside the repository', async () => {
  await assert.rejects(resolveFixturePath('../../../../../../etc/passwd'), /escapes repository root/u);
});
