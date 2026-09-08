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
    repairFingerprint: '25333bc53582ad8a2990d4d08016191a5aac7ef5164741587a17caaa02fa4341',
  });
});
