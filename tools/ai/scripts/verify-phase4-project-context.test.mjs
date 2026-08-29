import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase4XmlProjectContext} from './verify-phase4-project-context.mjs';

test('requires exact project resources, styles, call sites, and unsupported diagnostics', async () => {
  const summary = await verifyPhase4XmlProjectContext();
  assert.deepEqual(summary, {
    deterministic: 1,
    supported: 1,
    unsupported: 2,
    unsupportedFixtures: 2,
    resources: 4,
    styles: 2,
    callSites: 7,
  });
});

test('rejects resolver output that upgrades unsupported input', async () => {
  await assert.rejects(
    verifyPhase4XmlProjectContext({
      resolveContext: async () => ({status: 'success', context: {}, resolvedSource: ''}),
    }),
    /deterministic golden|unsupported project-context diagnostics/u,
  );
});
