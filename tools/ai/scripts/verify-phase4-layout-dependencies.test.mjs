import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase4XmlLayoutDependencies} from './verify-phase4-layout-dependencies.mjs';

test('requires exact dependency graph, expansion, provenance, resources, and compile evidence', async () => {
  const requests = [];
  const summary = await verifyPhase4XmlLayoutDependencies({
    compile: async (request) => {
      requests.push(request);
      return {
        status: 'success',
        evidence: {level: 'compiled', outputFingerprint: 'e'.repeat(64)},
        diagnostics: [],
      };
    },
  });

  assert.equal(summary.graphs, 1);
  assert.equal(summary.expansions, 1);
  assert.equal(summary.compiled, 1);
  assert.equal(summary.supported, 1);
  assert.equal(summary.unsupported, 2);
  assert.equal(summary.unsupportedFixtures, 2);
  assert.equal(summary.fingerprints[0].classes, 'e'.repeat(64));
  assert.equal(requests.length, 1);
  assert.ok(requests[0].source.includes('fun UiTreeBuilder.ScreenView('));
});

test('rejects expansion without compiled evidence', async () => {
  await assert.rejects(
    verifyPhase4XmlLayoutDependencies({
      compile: async () => ({status: 'invalid', evidence: {level: 'static'}, diagnostics: []}),
    }),
    /did not match and compile/u,
  );
});
