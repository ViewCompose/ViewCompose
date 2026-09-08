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
  assert.equal(result.resolvedAccessibilityFields, 14);
  assert.equal(result.failClosedDenominators, 3);
  assert.equal(result.remainingQuestions, 0);
  assert.equal(result.remainingUnsupportedSemantics, 0);
  assert.equal(result.placeholders, 0);
  assert.equal(result.codeGenerationAllowed, true);
  assert.equal(result.deterministicResolutions, 2);
  assert.equal(result.providerExecutions, 0);
  assert.equal(result.networkRequests, 0);
  assert.equal(
    result.requestFingerprint,
    '986d2992ccf4b45cc21dfa7d1d7995cf9e9efed29633b8b985b9db75e269b66b',
  );
  assert.equal(
    result.resultFingerprint,
    'f3619ac91a4dfa658466823ff0e031812d2ddeff902e1e4a997734b365b219db',
  );
  assert.equal(
    result.resolvedDesignIrFingerprint,
    '6137e04205c3bec89b5e4480e0448e45c6ab55905a0ea3d9fdc55ef2b3e52603',
  );
});
