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
    authorizationFingerprint: 'edae2324520e22dd61d5e16eeddd48973fd1ba3c1e7d4cae60b6c9b126746b61',
    real: null,
  });
});
