import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase5ScreenshotInference} from './verify-phase5-screenshot-inference.mjs';

test('freezes screenshot inference lineage, evidence, uncertainty, and consent without a provider', async () => {
  const summary = await verifyPhase5ScreenshotInference();
  assert.equal(summary.supportedGoldens, 1);
  assert.equal(summary.failClosedDenominators, 3);
  assert.equal(summary.nodes, 4);
  assert.equal(summary.evidenceRecords, 4);
  assert.equal(summary.unresolvedQuestions, 6);
  assert.equal(summary.blockingQuestions, 6);
  assert.equal(summary.providerExecutions, 0);
  assert.equal(summary.networkRequests, 0);
  assert.equal(
    summary.requestFingerprint,
    'f789490fa61fa8d6a74e546b8defa536a78c9cebc83a123ba70da9967030a62b',
  );
  assert.equal(
    summary.designIrFingerprint,
    '585b3d1761cc47f9718ff48e09216899faa470ca662e4e98ad705c8686109b5a',
  );
  assert.equal(
    summary.resultFingerprint,
    '4bd30960cccdfe3b9a4402293b3739a3238a25fcef12fb2911c595a3df7a66c0',
  );
});
