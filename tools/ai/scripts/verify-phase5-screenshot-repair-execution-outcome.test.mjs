import assert from 'node:assert/strict';
import test from 'node:test';
import {
  verifyPhase5ScreenshotRepairExecutionOutcome,
} from './verify-phase5-screenshot-repair-execution-outcome.mjs';

test('freezes terminal screenshot repair outcomes before patch execution', async () => {
  assert.deepEqual(await verifyPhase5ScreenshotRepairExecutionOutcome(), {
    implementation: false,
    publicRepairMode: false,
    executionAuthorized: false,
    terminalOutcomes: 4,
    invalidDenominators: 24,
    statuses: ['applied', 'failed', 'cancelled', 'indeterminate'],
    outputBearingOutcomes: 1,
    retryableOutcomes: 0,
  });
});
