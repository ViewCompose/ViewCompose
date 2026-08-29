import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase4GeneratedPreview} from './verify-phase4-generated-preview.mjs';

const expected = Object.freeze({
  request: '8b2d5460fea40ee539fc5aba01af5cac97d59002476c17b744fb7baa1144d063',
  kotlin: '6c4f6dafef9e0b4808eefab440d14e331b1a3b55bc8becff7a05d3669cc73be1',
  wrapper: '8d4ff9932ada6621a05b486a22d410d79a674db787c29ac22b1e7e4e0dcf8821',
  build: '77a71a6650ac829fdb7fa072860a817438ac045164b27431697a1358b16715df',
  output: '6d2c8a5296db8cc95e5201092e40532f371f1d95621acd7bad343c913b4b9bab',
  png: 'e1efebaffa1efc19052a3fb1be33a8aa3fd670073a6330e976cd1be4082bb7fe',
  tree: 'd0373c8499b9d46f9cafa98a04c6f30d41a8ec69743a5ada35496ba0e2e05e85',
});

function rendered(cache = 'miss') {
  return {
    status: 'success',
    evidence: {
      level: 'rendered',
      cache,
      compilerLane: 'current-source/jdk-21/agp-9.1.1/kotlin-2.2.10/android-37/jvm-11',
      renderLane: 'current-source/preview-protocol-1/paparazzi-2.0.0-alpha05/layoutlib-16.2.1',
      outputFingerprint: expected.output,
    },
    diagnostics: [],
    data: {
      preview: {
        targetId: 'tools.ai.GeneratedXmlPreview',
        modulePath: ':tools:ai-preview-harness',
        buildVariant: 'debug',
        buildFingerprint: expected.build,
        previewId: 'generatedpreviewkt-generatedxmlpreview-8ccd5bd7b4eb',
        variantId: 'generated-xml-loginview-abee9c74',
        configuration: {
          widthDp: 411,
          heightDp: -1,
          density: 2.625,
          fontScale: 1,
          localeTags: ['en-US'],
          layoutDirection: 'Ltr',
          theme: 'Light',
        },
        capabilityIds: ['foundation.components', 'modifier.layout'],
        source: {
          path: `build/ai/preview/requests/${expected.request}/input/GeneratedPreview.kt`,
          line: 22,
          column: 1,
        },
        image: {
          mediaType: 'image/png',
          widthPx: 1079,
          heightPx: 2339,
          bytes: 38919,
          sha256: expected.png,
        },
        renderTree: {bytes: 202604, sha256: expected.tree},
        generatedPreview: {
          requestFingerprint: expected.request,
          generatedKotlinFingerprint: expected.kotlin,
          wrapperFingerprint: expected.wrapper,
          pngSha256: expected.png,
          renderTreeSha256: expected.tree,
        },
      },
    },
  };
}

test('requires exact render, artifact inspection, cache, and blocked isolation denominators', async () => {
  let supportedCalls = 0;
  let inspected = 0;
  const summary = await verifyPhase4GeneratedPreview({
    convert: async (request) => {
      if (request.previewBindings.length === 4 && request.path.endsWith('login.xml')) {
        supportedCalls += 1;
        return rendered(supportedCalls === 1 ? 'miss' : 'hit');
      }
      const image = request.previewBindings.some((binding) => binding.kind === 'image-source');
      return {
        status: 'unsupported',
        evidence: {level: 'static'},
        diagnostics: [{
          code: image
            ? 'VC-AI-PREVIEW-ASSET-MISSING'
            : 'VC-AI-PREVIEW-BINDING-MISSING',
        }],
      };
    },
    validateRequest: async () => ({
      status: 'invalid',
      diagnostic: {code: 'VC-AI-PREVIEW-BUILD-SELECTION-DENIED'},
    }),
    inspect: async () => { inspected += 1; },
  });

  assert.equal(summary.rendered, 1);
  assert.equal(summary.cacheHits, 1);
  assert.equal(summary.blocked, 3);
  assert.equal(summary.supported, 1);
  assert.equal(summary.unsupported, 3);
  assert.equal(supportedCalls, 2);
  assert.equal(inspected, 1);
  assert.equal(summary.fingerprints[0].output, expected.output);
});

test('rejects a rendered response whose exact output fingerprint drifts', async () => {
  await assert.rejects(
    verifyPhase4GeneratedPreview({
      convert: async () => {
        const result = rendered('hit');
        result.evidence.outputFingerprint = 'f'.repeat(64);
        return result;
      },
      validateRequest: async () => ({status: 'invalid', diagnostic: {code: 'ignored'}}),
      inspect: async () => {},
    }),
    /generated Preview evidence changed/u,
  );
});
