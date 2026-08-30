import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase5ScreenshotRepairProposer} from './verify-phase5-screenshot-repair-proposer.mjs';

test('freezes rollback-only screenshot repair proposal before implementation', async () => {
  const summary = await verifyPhase5ScreenshotRepairProposer();
  assert.deepEqual(summary, {
    implementation: false,
    supportedRollbacks: 1,
    noEligibleDenominators: 6,
    invalidDenominators: 2,
    cancelledDenominators: 1,
    proposalFingerprint: '47bffb223b1503cb603f77840ea46ec9ae375bc7efa5637c5a3635adbcecce68',
  });
});
