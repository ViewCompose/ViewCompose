import test from 'node:test';
import assert from 'node:assert/strict';
import {verifyPhase5ScreenshotRepair} from './verify-phase5-screenshot-repair.mjs';

test('freezes bounded screenshot repair convergence and fail-closed stops', async () => {
  const summary = await verifyPhase5ScreenshotRepair();
  assert.deepEqual(summary, {
    supportedGoldens: 1,
    patchGoldens: 1,
    candidateEvaluatorGoldens: 2,
    failClosedDenominators: 5,
    repairFingerprint: 'f56383c6a7e598b59534720677b68b83ef3015712cc14f85ce340468f7f50079',
  });
});
