import assert from 'node:assert/strict';
import test from 'node:test';
import {
  verifyPhase5ScreenshotRepairAppliedResultHandoffContract,
} from './verify-phase5-screenshot-repair-applied-result-handoff.mjs';

test('freezes applied-result handoff before retaining repair output', async () => {
  assert.deepEqual(await verifyPhase5ScreenshotRepairAppliedResultHandoffContract(), {
    implementation: false,
    publicRepairMode: false,
    persistentSourceWrite: false,
    successfulHandoffsRequired: 1,
    invalidAuthoritiesAccepted: 0,
    replayedDeliveries: 0,
  });
});
