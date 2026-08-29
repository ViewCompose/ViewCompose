import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase4XmlProjectContext} from './verify-phase4-project-context.mjs';

test('requires exact project resources, styles, call sites, and unsupported diagnostics', async () => {
  const requests = [];
  const summary = await verifyPhase4XmlProjectContext({
    compile: async (request) => {
      requests.push(request);
      return {
        status: 'success',
        evidence: {level: 'compiled', outputFingerprint: 'd'.repeat(64)},
        diagnostics: [],
      };
    },
  });
  assert.equal(summary.deterministic, 1);
  assert.equal(summary.supported, 1);
  assert.equal(summary.unsupported, 2);
  assert.equal(summary.unsupportedFixtures, 2);
  assert.equal(summary.resources, 4);
  assert.equal(summary.styles, 2);
  assert.equal(summary.callSites, 7);
  assert.equal(summary.compiled, 1);
  assert.equal(summary.fingerprints.length, 1);
  assert.equal(summary.fingerprints[0].classes, 'd'.repeat(64));
  assert.equal(requests.length, 1);
  assert.ok(requests[0].source.includes('fun UiTreeBuilder.StyledLoginView('));
});

test('rejects resolver output that upgrades unsupported input', async () => {
  await assert.rejects(
    verifyPhase4XmlProjectContext({
      resolveContext: async () => ({status: 'success', context: {}, resolvedSource: ''}),
      compile: async () => ({status: 'success', evidence: {level: 'compiled'}}),
    }),
    /deterministic golden|unsupported project-context diagnostics/u,
  );
});
