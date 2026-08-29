import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase4GeneratedPreview} from './verify-phase4-generated-preview.mjs';

const loginExpected = Object.freeze({
  request: '8b2d5460fea40ee539fc5aba01af5cac97d59002476c17b744fb7baa1144d063',
  kotlin: '6c4f6dafef9e0b4808eefab440d14e331b1a3b55bc8becff7a05d3669cc73be1',
  wrapper: '8d4ff9932ada6621a05b486a22d410d79a674db787c29ac22b1e7e4e0dcf8821',
  build: '87affa2585fc27354049e780b24c00e7d6ea5181e7b9b65af6d8a88e7d5ed08d',
  output: '6d2c8a5296db8cc95e5201092e40532f371f1d95621acd7bad343c913b4b9bab',
  png: 'e1efebaffa1efc19052a3fb1be33a8aa3fd670073a6330e976cd1be4082bb7fe',
  tree: 'd0373c8499b9d46f9cafa98a04c6f30d41a8ec69743a5ada35496ba0e2e05e85',
  variant: 'generated-xml-loginview-abee9c74',
  sourceLine: 22,
  capabilityIds: ['foundation.components', 'modifier.layout'],
  imageBytes: 38919,
  treeBytes: 202604,
  assets: [],
});

const profileExpected = Object.freeze({
  request: '1d81d2ed9db84ee022d806042cd883c426f4fe0061aa65c757ef0de3a91225f6',
  kotlin: '15b15098e92b62bc9730ab7b3f2bde7715596f22069490a18b1e7830ff92ad35',
  wrapper: '461d7c9e7b9898b9b9f7373775fa10c8a180097664627b442d36a8b2abd2a4b2',
  build: '76b256d15f1801358b009127e50467c5936af8b99714f6895e06dddef7a7b990',
  output: '31fb45a13a4d35badee2cf61ce7760a0540b60ed2e0def2d3e3910cfdb4268f5',
  png: 'bb130675ac0de5df6ad6ff93ded020cbe93704a80030301da3a2d57a56b9cd3f',
  tree: '58bbd8da9df6295da2419dc85bf4c7d4636419f8022237740b694966763b31e9',
  variant: 'generated-xml-profilecardview-abee9c74',
  sourceLine: 23,
  capabilityIds: [
    'foundation.components',
    'image.foundation',
    'modifier.drawing',
    'modifier.layout',
  ],
  imageBytes: 15217,
  treeBytes: 120988,
  assets: [{
    resourceName: 'vc_ai_4ff6ab670a58c14270e034e2090d9a432caa263a14e0a25785386b0c12f880b5',
    bytes: 70,
    sha256: '4ff6ab670a58c14270e034e2090d9a432caa263a14e0a25785386b0c12f880b5',
    widthPx: 1,
    heightPx: 1,
  }],
});

function rendered(expected, cache = 'miss') {
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
        variantId: expected.variant,
        configuration: {
          widthDp: 411,
          heightDp: -1,
          density: 2.625,
          fontScale: 1,
          localeTags: ['en-US'],
          layoutDirection: 'Ltr',
          theme: 'Light',
        },
        capabilityIds: expected.capabilityIds,
        source: {
          path: `build/ai/preview/requests/${expected.request}/input/GeneratedPreview.kt`,
          line: expected.sourceLine,
          column: 1,
        },
        image: {
          mediaType: 'image/png',
          widthPx: 1079,
          heightPx: 2339,
          bytes: expected.imageBytes,
          sha256: expected.png,
        },
        renderTree: {bytes: expected.treeBytes, sha256: expected.tree},
        generatedPreview: {
          requestFingerprint: expected.request,
          generatedKotlinFingerprint: expected.kotlin,
          wrapperFingerprint: expected.wrapper,
          pngSha256: expected.png,
          renderTreeSha256: expected.tree,
          assets: expected.assets,
        },
      },
    },
  };
}

test('requires exact render, artifact inspection, cache, and blocked isolation denominators', async () => {
  const supportedCalls = new Map();
  let inspected = 0;
  const summary = await verifyPhase4GeneratedPreview({
    convert: async (request) => {
      const supported = request.path.endsWith('login.xml') && request.previewBindings.length === 4
        ? loginExpected
        : request.path.endsWith('profile-card.xml') &&
            request.previewBindings.some((binding) => binding.asset !== undefined)
          ? profileExpected
          : null;
      if (supported) {
        const calls = (supportedCalls.get(supported.request) ?? 0) + 1;
        supportedCalls.set(supported.request, calls);
        return rendered(supported, calls === 1 ? 'miss' : 'hit');
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

  assert.equal(summary.rendered, 2);
  assert.equal(summary.cacheHits, 2);
  assert.equal(summary.blocked, 3);
  assert.equal(summary.supported, 2);
  assert.equal(summary.unsupported, 3);
  assert.deepEqual([...supportedCalls.values()], [2, 2]);
  assert.equal(inspected, 2);
  assert.equal(summary.fingerprints[0].output, loginExpected.output);
  assert.equal(summary.fingerprints[1].output, profileExpected.output);
});

test('rejects a rendered response whose exact output fingerprint drifts', async () => {
  await assert.rejects(
    verifyPhase4GeneratedPreview({
      convert: async () => {
        const result = rendered(loginExpected, 'hit');
        result.evidence.outputFingerprint = 'f'.repeat(64);
        return result;
      },
      validateRequest: async () => ({status: 'invalid', diagnostic: {code: 'ignored'}}),
      inspect: async () => {},
    }),
    /generated Preview evidence changed/u,
  );
});
