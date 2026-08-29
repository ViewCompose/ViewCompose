import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase5ScreenshotPreprocessing} from './verify-phase5-screenshot-preprocessing.mjs';

test('meets every frozen screenshot preprocessing and privacy denominator', async () => {
  const summary = await verifyPhase5ScreenshotPreprocessing();
  assert.equal(summary.supportedGoldens, 1);
  assert.equal(summary.deterministicRuns, 3);
  assert.equal(summary.privacyDenials, 2);
  assert.equal(summary.integrityDenials, 1);
  assert.equal(summary.cancellations, 1);
  assert.equal(
    summary.inputSha256,
    'ff96bfc58337301e15ff1515d39a2653a855a46ef74e50f8884889cd28f21cc0',
  );
  assert.equal(
    summary.outputSha256,
    '201c08259fb2891c57c3f85e0f9e1157ad9df9ae8303c4f8d679735cf2850b99',
  );
  assert.equal(
    summary.outputFingerprint,
    '74d3e3190dca4157d07cefd51f9a3a809094dad93785cef3c327f566a6e832b1',
  );
});
