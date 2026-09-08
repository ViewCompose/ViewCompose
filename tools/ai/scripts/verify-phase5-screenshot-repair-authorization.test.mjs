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
    authorizationFingerprint: '6bacb1629e95c64e47b93dbb4490090c664728f43be3855c70bddfd52183da59',
    real: null,
  });
});
