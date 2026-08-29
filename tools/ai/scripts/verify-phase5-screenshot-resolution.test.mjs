import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase5ScreenshotResolution} from './verify-phase5-screenshot-resolution.mjs';

test('freezes typed human resolution before screenshot code generation', async () => {
  const result = await verifyPhase5ScreenshotResolution();
  assert.equal(result.supportedGoldens, 1);
  assert.equal(result.answers, 6);
  assert.equal(result.resolvedUnsupportedSemantics, 6);
  assert.equal(result.resolvedEvents, 2);
  assert.equal(result.resolvedSemanticRoles, 2);
  assert.equal(result.failClosedDenominators, 3);
  assert.equal(result.remainingQuestions, 0);
  assert.equal(result.remainingUnsupportedSemantics, 0);
  assert.equal(result.placeholders, 0);
  assert.equal(result.codeGenerationAllowed, true);
  assert.equal(result.providerExecutions, 0);
  assert.equal(result.networkRequests, 0);
  assert.equal(
    result.requestFingerprint,
    'c2712d96b7f1e821e18c0952dcd31becafb48eea0df848e2983efb319dd3fea6',
  );
  assert.equal(
    result.resultFingerprint,
    'b6466ed78cfe2386e4b1a77238758e1c01d50a4fe374b0b36efae419e22e7f88',
  );
  assert.equal(
    result.resolvedDesignIrFingerprint,
    '0ae93f54fea2dfee3f3ea4c9a712bf3dcf4ce21143c00e3488a159a8551f6821',
  );
});
