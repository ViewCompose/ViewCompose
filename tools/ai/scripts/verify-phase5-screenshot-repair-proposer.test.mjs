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
    proposalFingerprint: '2d77def7c5582719c648797d4aaaf3ae551e92a0c4cb7f1d7eb60dbdaba2aeee',
    real: null,
  });
});
