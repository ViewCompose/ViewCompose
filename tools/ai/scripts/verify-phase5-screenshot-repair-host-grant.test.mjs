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
    requestFingerprint: '0fe314890a50871229e3fc221cb0bad0fc8a84e4f7fe0e76916a9294cd2aa47a',
    decisionFingerprint: 'eeda387f022b57254f73abcd22eda6181ef60fd4963d5bee9b64e9ea07a2969d',
    adapter: {
      directCallbackGrants: 1,
      replayedGrants: 0,
      serializedDecisionsAccepted: 0,
    },
  });
});
