import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase5ScreenshotComparison} from './verify-phase5-screenshot-comparison.mjs';

test('freezes the screenshot layout comparison evidence boundary', async () => {
  const result = await verifyPhase5ScreenshotComparison({compareGolden: false});
  assert.equal(result.supportedGoldens, 1);
  assert.equal(result.failClosedDenominators, 2);
  assert.equal(result.compared, 0);
});
