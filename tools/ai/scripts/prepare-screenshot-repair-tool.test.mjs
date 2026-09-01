import assert from 'node:assert/strict';
import test from 'node:test';
import {prepareScreenshotRepairTool} from './prepare-screenshot-repair-tool.mjs';

const evidence = {
  schemaVersion: 1,
  status: 'complete',
  lineage: {candidateDesignIrFingerprint: 'a'.repeat(64)},
  candidateEvaluation: {},
  designIr: {},
  evidenceFingerprint: 'b'.repeat(64),
};

test('exposes evaluation and proposal without storing source-application state', async () => {
  let stored = 0;
  const evaluated = await prepareScreenshotRepairTool({
    operation: 'evaluate',
    evaluationInput: {},
  }, {
    evaluate: async () => ({evaluation: {gates: []}, evidence}),
    store: async () => { stored += 1; },
  });
  assert.equal(evaluated.status, 'success');
  const proposed = await prepareScreenshotRepairTool({
    operation: 'propose',
    baselineEvidence: evidence,
    candidateEvidence: evidence,
  }, {
    propose: async () => ({
      status: 'proposed',
      proposalFingerprint: 'c'.repeat(64),
      diagnostics: [],
    }),
    store: async () => { stored += 1; },
  });
  assert.equal(proposed.status, 'success');
  assert.equal(stored, 0);
});

test('stores only an inert prepared bundle and returns separate attended commands', async () => {
  const fingerprint = 'd'.repeat(64);
  let stored;
  const result = await prepareScreenshotRepairTool({
    operation: 'prepare',
    projectRoot: '/project',
  }, {
    prepare: async () => ({
      request: {requestFingerprint: fingerprint},
      preApplyEvidence: {evidenceFingerprint: 'e'.repeat(64)},
    }),
    store: async (bundle, options) => {
      stored = {bundle, options};
      return {requestFingerprint: fingerprint};
    },
  });
  assert.equal(result.status, 'success');
  assert.equal(result.data.sourceWritePerformed, false);
  assert.equal(result.data.nextCommands.apply, `viewcompose-repair apply ${fingerprint} --pretty`);
  assert.equal(stored.options.projectRoot, '/project');
});
