import assert from 'node:assert/strict';
import test from 'node:test';
import {
  verifyPhase5ScreenshotRepairAppliedResultHandoff,
} from './verify-phase5-screenshot-repair-applied-result-handoff.mjs';

test('freezes and verifies the internal applied-result handoff', async () => {
  assert.deepEqual(await verifyPhase5ScreenshotRepairAppliedResultHandoff(), {
    implementation: true,
    publicRepairMode: false,
    persistentSourceWrite: false,
    successfulHandoffs: 1,
    durableReceiptReads: 1,
    exactObjectDeliveries: 1,
    serializedAuthoritiesAccepted: 0,
    nonAppliedResultsDelivered: 0,
    mismatchedReceiptsAccepted: 0,
    replayedDeliveries: 0,
    concurrentDeliveries: 1,
  });
});
