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
    repairFingerprint: 'a6f92b031f387d30eea9d52ed84b91182149751dfb72e8603d5a4de1ba99d9ee',
  });
});
