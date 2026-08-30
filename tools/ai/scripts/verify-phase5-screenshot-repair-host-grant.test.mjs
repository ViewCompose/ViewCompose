import assert from 'node:assert/strict';
import test from 'node:test';
import {
  verifyPhase5ScreenshotRepairHostGrant,
} from './verify-phase5-screenshot-repair-host-grant.mjs';

test('freezes host authentication, revocation, and atomic single-use before repair execution', async () => {
  assert.deepEqual(await verifyPhase5ScreenshotRepairHostGrant(), {
    implementation: true,
    publicRepairMode: false,
    executionAuthorized: false,
    supportedGrants: 1,
    invalidDenominators: 17,
    deniedDenominators: 5,
    cancelledDenominators: 1,
    requestFingerprint: '3765e61fcffdeada154d8cfd028aca3ff6ecdea1e1b94a7834e9cc06cc00cbd1',
    decisionFingerprint: 'df7a62497a1dce84a1461b16aa1d7481e0feb5e9fc0e7154026851f3bd1bb51e',
    adapter: {
      directCallbackGrants: 1,
      replayedGrants: 0,
      serializedDecisionsAccepted: 0,
    },
  });
});
