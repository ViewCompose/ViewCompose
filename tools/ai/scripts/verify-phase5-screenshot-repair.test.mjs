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
    repairFingerprint: 'e851cb37943ce42df9fa91a30cf36f73a99e2b59e5fd74cd0b061a5a4c444858',
  });
});
