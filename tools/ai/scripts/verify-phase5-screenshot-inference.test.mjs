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
  assert.equal(summary.deterministicValidations, 2);
  assert.equal(summary.providerImports, 1);
  assert.equal(summary.providerExecutions, 0);
  assert.equal(summary.networkRequests, 0);
  assert.equal(
    summary.requestFingerprint,
    '083ac8d466d75c617184b6961e35228f9b53c117e381320fb265c3b97ee2dc2d',
  );
  assert.equal(
    summary.designIrFingerprint,
    '585b3d1761cc47f9718ff48e09216899faa470ca662e4e98ad705c8686109b5a',
  );
  assert.equal(
    summary.resultFingerprint,
    '62694a1787a531bf68f9794c1a54630082e5d8f8fe7ee5becf3214c2ab107a09',
  );
  assert.equal(
    summary.validationFingerprint,
    'a9ebb9732105d35eab22ed56f67a6b1f02396985a5b19344b6be21b9f59e48ab',
  );
});
