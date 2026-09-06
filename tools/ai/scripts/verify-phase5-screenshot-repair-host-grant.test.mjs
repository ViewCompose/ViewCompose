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
    requestFingerprint: '7a658a7bacaa6941b076e2135f31101484ba186d32352bc5e00d3ee4c848c17c',
    decisionFingerprint: '309445115d6dd0428016e5833bea375484a194f527c044ec1d3731f539351ff7',
    adapter: {
      directCallbackGrants: 1,
      replayedGrants: 0,
      serializedDecisionsAccepted: 0,
    },
  });
});
