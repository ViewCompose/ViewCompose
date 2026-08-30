import assert from 'node:assert/strict';
import test from 'node:test';
import {
  verifyPhase5ScreenshotRepairHostGrant,
} from './verify-phase5-screenshot-repair-host-grant.mjs';

test('freezes host authentication, revocation, and atomic single-use before repair execution', async () => {
  assert.deepEqual(await verifyPhase5ScreenshotRepairHostGrant(), {
    implementation: false,
    publicRepairMode: false,
    executionAuthorized: false,
    supportedGrants: 1,
    invalidDenominators: 17,
    deniedDenominators: 4,
    cancelledDenominators: 1,
    requestFingerprint: 'ab8134e2be383dbe8c2b376aceb172d2132f0268e0c4870999a682c9fc660dbd',
    decisionFingerprint: '8f5953ee7fec99c15d446d3adb1877ef1dd95a2ff5dbffbab27de119d6974c2e',
  });
});
