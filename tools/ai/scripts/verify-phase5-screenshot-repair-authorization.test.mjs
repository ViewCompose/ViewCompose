import assert from 'node:assert/strict';
import test from 'node:test';
import {
  verifyPhase5ScreenshotRepairAuthorization,
} from './verify-phase5-screenshot-repair-authorization.mjs';

test('freezes human authorization before screenshot rollback activation', async () => {
  assert.deepEqual(await verifyPhase5ScreenshotRepairAuthorization(), {
    implementation: false,
    authorizedFixtures: 1,
    invalidDenominators: 10,
    cancelledDenominators: 1,
    authorizationFingerprint: 'ba359be06ef055db9ca32d7724dfe256b2d53a44aacbdec0f781d5825343cb46',
  });
});
