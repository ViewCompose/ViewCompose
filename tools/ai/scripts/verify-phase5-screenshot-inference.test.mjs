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
    '60df429550daff7f9e61494a4ba051dbdc0b19a18a8366dda7542d29962933ea',
  );
  assert.equal(
    summary.designIrFingerprint,
    '585b3d1761cc47f9718ff48e09216899faa470ca662e4e98ad705c8686109b5a',
  );
  assert.equal(
    summary.resultFingerprint,
    '5e4fe63958acdd0e6e9dbcf090c8ef511c93664473e490e0f17f38d176218c77',
  );
  assert.equal(
    summary.validationFingerprint,
    '7c14edc232f74463fa02ab6d3dffe215c887c7a1053522a551302351097a4a68',
  );
});
