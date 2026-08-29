import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase5ScreenshotPixelComparison} from './verify-phase5-screenshot-pixel-comparison.mjs';

test('freezes pixel-reference eligibility before exposing pixel comparison', async () => {
  const result = await verifyPhase5ScreenshotPixelComparison();
  assert.equal(result.supportedGoldens, 1);
  assert.equal(result.failClosedDenominators, 3);
  assert.equal(result.implementation, false);
});
