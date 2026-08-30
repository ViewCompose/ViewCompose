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
    proposalFingerprint: 'a4e9a7462b3d7bbef163984f915444f3efce4586fd272573a4f30afcc28f7545',
    real: null,
  });
});
