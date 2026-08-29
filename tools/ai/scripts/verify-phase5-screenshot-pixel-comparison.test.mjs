import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase5ScreenshotPixelComparison} from './verify-phase5-screenshot-pixel-comparison.mjs';

test('freezes exact pixel comparison inputs without running Gradle in unit tests', async () => {
  const result = await verifyPhase5ScreenshotPixelComparison({compareGolden: false});
  assert.equal(result.supportedGoldens, 1);
  assert.equal(result.failClosedDenominators, 4);
  assert.equal(result.implementation, true);
  assert.equal(result.compared, 0);
});
