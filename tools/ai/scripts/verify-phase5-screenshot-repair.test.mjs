import test from 'node:test';
import assert from 'node:assert/strict';
import {verifyPhase5ScreenshotRepair} from './verify-phase5-screenshot-repair.mjs';

test('freezes bounded screenshot repair convergence and fail-closed stops', async () => {
  const summary = await verifyPhase5ScreenshotRepair();
  assert.deepEqual(summary, {
    supportedGoldens: 1,
    failClosedDenominators: 5,
    repairFingerprint: 'b20ce414923dee7d9953b6d79e3e093778d1be17d41adffcdeb7da94a9cac18d',
  });
});
