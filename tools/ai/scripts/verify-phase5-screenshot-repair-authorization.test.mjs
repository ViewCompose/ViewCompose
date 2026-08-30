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
    authorizationFingerprint: 'b85294c2284c3e42d495507e5223981c72c3251235ffa0a7be7b24664190d7f3',
    real: null,
  });
});
