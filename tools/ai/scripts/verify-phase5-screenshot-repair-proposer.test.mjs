import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase5ScreenshotRepairProposer} from './verify-phase5-screenshot-repair-proposer.mjs';

test('freezes the implemented internal rollback-only screenshot repair proposal', async () => {
  const summary = await verifyPhase5ScreenshotRepairProposer({evaluateReal: false});
  assert.deepEqual(summary, {
    implementation: true,
    supportedRollbacks: 1,
    noEligibleDenominators: 6,
    invalidDenominators: 2,
    cancelledDenominators: 1,
    proposalFingerprint: 'f407aa353cb219f36e98922d83bd178196402ac93fac47088cff1685ef7cf44f',
    real: null,
  });
});
