import test from 'node:test';
import assert from 'node:assert/strict';
import {verifyPhase4XmlMigration} from './verify-phase4-xml.mjs';

test('requires deterministic golden generation, resource preservation, and compiled evidence', async () => {
  const requests = [];
  const summary = await verifyPhase4XmlMigration({
    compile: async (request) => {
      requests.push(request);
      return {
        status: 'success',
        evidence: {
          level: 'compiled',
          outputFingerprint: 'a'.repeat(64),
        },
        diagnostics: [],
      };
    },
  });

  assert.equal(summary.generated, 3);
  assert.equal(summary.compiled, 3);
  assert.equal(summary.resourcesPreserved, 3);
  assert.equal(requests.length, 3);
  assert.deepEqual(requests[0].artifactIds, ['viewcompose-ui-foundation']);
  assert.ok(requests[0].source.includes('fun UiTreeBuilder.LoginView('));
  assert.ok(requests[1].source.includes('fun UiTreeBuilder.ProfileCardView('));
  assert.ok(requests[2].source.includes('fun UiTreeBuilder.CommonAttributesView('));
});

test('rejects a compile result that does not carry compiled evidence', async () => {
  await assert.rejects(
    verifyPhase4XmlMigration({
      compile: async () => ({status: 'invalid', evidence: {level: 'static'}, diagnostics: []}),
    }),
    /did not compile/u,
  );
});
