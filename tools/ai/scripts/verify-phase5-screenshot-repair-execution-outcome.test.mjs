import assert from 'node:assert/strict';
import test from 'node:test';
import {
  verifyPhase5ScreenshotRepairExecutionOutcome,
} from './verify-phase5-screenshot-repair-execution-outcome.mjs';

test('freezes and verifies terminal outcomes for attended screenshot repair execution', async () => {
  assert.deepEqual(await verifyPhase5ScreenshotRepairExecutionOutcome(), {
    implementation: true,
    publicRepairMode: false,
    executionAuthorized: false,
    terminalOutcomes: 4,
    invalidDenominators: 24,
    statuses: ['applied', 'failed', 'cancelled', 'indeterminate'],
    outputBearingOutcomes: 1,
    retryableOutcomes: 0,
    adapter: {
      directApplications: 1,
      replayedApplications: 0,
      serializedGrantsAccepted: 0,
      unrecordedOutputsExposed: 0,
    },
  });
});
