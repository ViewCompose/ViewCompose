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
    proposalFingerprint: '8313b5eab8e050e5f5f16a251c4de7f8034e686aafd2d6ba10b89f13f8068ce1',
    real: null,
  });
});
