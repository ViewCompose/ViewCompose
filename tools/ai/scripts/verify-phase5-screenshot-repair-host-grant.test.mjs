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
    requestFingerprint: '1f47b20bbd1d7f300b906146978fbad84d690e04c44317327f8a5c7629d63faa',
    decisionFingerprint: '7503f19297663da468e481d319180e67fc7dd9815920013b542cdb9241ba203d',
    adapter: {
      directCallbackGrants: 1,
      replayedGrants: 0,
      serializedDecisionsAccepted: 0,
    },
  });
});
