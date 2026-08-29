import test from 'node:test';
import assert from 'node:assert/strict';
import {verifyPhase5ScreenshotRepair} from './verify-phase5-screenshot-repair.mjs';

test('freezes bounded screenshot repair convergence and fail-closed stops', async () => {
  const summary = await verifyPhase5ScreenshotRepair();
  assert.deepEqual(summary, {
    supportedGoldens: 1,
    patchGoldens: 1,
    failClosedDenominators: 5,
    repairFingerprint: '54e68f7a8129bcf1da26053917a6ad769f71e32729ac416ea792f3d5fec610cb',
  });
});
