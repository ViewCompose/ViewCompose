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
    requestFingerprint: '983360c6e158ebe62d475c3a21e29e9762cfd3c474cb2c1c7a64ac59d7e4fa71',
    decisionFingerprint: '48b7f7e222d87c76b59b464e4f34b10422631c31b5a302b18b3c30e93f44baaf',
    adapter: {
      directCallbackGrants: 1,
      replayedGrants: 0,
      serializedDecisionsAccepted: 0,
    },
  });
});
