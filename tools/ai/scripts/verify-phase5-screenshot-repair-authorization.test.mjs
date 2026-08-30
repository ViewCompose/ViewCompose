import assert from 'node:assert/strict';
import test from 'node:test';
import {
  verifyPhase5ScreenshotRepairAuthorization,
} from './verify-phase5-screenshot-repair-authorization.mjs';

test('freezes implemented human authorization validation before repair activation', async () => {
  assert.deepEqual(await verifyPhase5ScreenshotRepairAuthorization({evaluateReal: false}), {
    implementation: true,
    authorizedFixtures: 1,
    invalidDenominators: 10,
    cancelledDenominators: 1,
    authorizationFingerprint: 'adbffdf1900072ad257969d8ac00d4d0706990040ba893c6ac09e910d5cb6633',
    real: null,
  });
});
