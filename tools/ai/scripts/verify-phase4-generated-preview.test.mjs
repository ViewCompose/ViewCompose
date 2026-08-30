import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase4GeneratedPreview} from './verify-phase4-generated-preview.mjs';

const loginExpected = Object.freeze({
  request: 'd70e50206f87bc0c6e10b487f5a47b72b1928f1162286cb204a1047f9376be3d',
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
  designIr: 'a938f6c0bd8333e195414353766d7e577bbcab0584c219cf4d123869192964d4',
  comparison: 'fed14c0d17ef19b3f5fe22652f9d3f2314d5383fec0ce6b5c42bb0c873c21c12',
  comparisonSummary: {
    designNodes: 4,
    mappedNodes: 4,
    requiredChecks: 32,
    passedChecks: 32,
    failedChecks: 0,
    notApplicableChecks: 0,
  },
  comparisonNodes: [
    {
      id: 'xml:0', key: 'xml:0', kind: 'column', wrapperDepth: 0,
      bounds: [0, 0, 1079, 2339],
      checks: [
        'identity.key', 'structure.parent', 'structure.children', 'semantic.kind',
        'semantic.visibility', 'geometry.width.match-parent', 'geometry.height.match-parent',
        'geometry.padding.all',
      ],
    },
    {
      id: 'id:title', key: 'title', kind: 'text', wrapperDepth: 0,
      bounds: [42, 42, 568, 97],
      checks: [
        'identity.key', 'structure.parent', 'structure.sibling-order', 'semantic.kind',
        'semantic.visibility', 'semantic.text', 'geometry.containment',
      ],
    },
    {
      id: 'id:email', key: 'email', kind: 'text-field', wrapperDepth: 1,
      bounds: [42, 97, 1037, 244],
      checks: [
        'identity.key', 'structure.parent', 'structure.sibling-order', 'semantic.kind',
        'semantic.role', 'semantic.visibility', 'geometry.width.match-parent',
        'geometry.containment',
      ],
    },
    {
      id: 'id:submit', key: 'submit', kind: 'button', wrapperDepth: 0,
      bounds: [42, 244, 1037, 370],
      checks: [
        'identity.key', 'structure.parent', 'structure.sibling-order', 'semantic.kind',
        'semantic.role', 'semantic.visibility', 'semantic.text',
        'geometry.width.match-parent', 'geometry.containment',
      ],
    },
  ],
});

const profileExpected = Object.freeze({
  request: 'ce0fc5b926e8a243d2cf5b32a568911899e19d329e07ad9698e5f90c6b2976ef',
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
  designIr: '8a860b20a34b87d0eae3918f12d1968e3653e0fe46da0cceffa68f70e9c25b09',
  comparison: '073a3e2dfdcbfec55e952d19296bd66008cde09bc8d2a259c4b0fb3b9badb200',
  comparisonSummary: {
    designNodes: 3,
    mappedNodes: 3,
    requiredChecks: 24,
    passedChecks: 24,
    failedChecks: 0,
    notApplicableChecks: 1,
  },
  comparisonNodes: [
    {
      id: 'id:profile_card', key: 'profile_card', kind: 'box', wrapperDepth: 0,
      bounds: [0, 0, 1079, 420],
      checks: [
        'identity.key', 'structure.parent', 'structure.children', 'semantic.kind',
        'semantic.visibility', 'geometry.width.match-parent', 'geometry.height.dp',
        'geometry.padding.all',
      ],
    },
    {
      id: 'id:avatar', key: 'avatar', kind: 'image', wrapperDepth: 0,
      bounds: [42, 42, 294, 294],
      checks: [
        'identity.key', 'structure.parent', 'structure.sibling-order', 'semantic.kind',
        'semantic.role', 'semantic.visibility', 'semantic.content-description',
        'geometry.width.dp', 'geometry.height.dp', 'geometry.containment',
      ],
    },
    {
      id: 'id:status', key: 'status', kind: 'text', wrapperDepth: 0,
      bounds: [0, 0, 0, 0],
      checks: [
        'identity.key', 'structure.parent', 'structure.sibling-order', 'semantic.kind',
        'semantic.visibility', 'semantic.text', 'geometry.hidden',
      ],
    },
  ],
});

function rendered(expected, cache = 'miss') {
  const comparisonNodes = expected.comparisonNodes.map((node, index) => ({
    designNodeId: node.id,
    designPath: [node.id],
    identityKey: node.key,
    identityRenderNodeId: `node-${index + 1}`,
    semanticRenderNodeId: `node-${index + 1}`,
    expectedKind: node.kind,
    actualKind: node.kind,
    wrapperDepth: node.wrapperDepth,
    bounds: {
      left: node.bounds[0], top: node.bounds[1], right: node.bounds[2], bottom: node.bounds[3],
    },
    checks: node.checks.map((id) => ({
      id,
      category: id.split('.')[0] === 'identity' ? 'identity' : id.split('.')[0],
      status: id === 'geometry.hidden' ? 'not-applicable' : 'passed',
      expected: 'expected',
      actual: 'actual',
    })),
  }));
  return {
    status: 'success',
    evidence: {
      level: 'compared',
      cache,
      compilerLane: 'current-source/jdk-21/agp-9.1.1/kotlin-2.2.10/android-37/jvm-11',
      renderLane: 'current-source/preview-protocol-1/paparazzi-2.0.0-alpha05/layoutlib-16.2.1',
      outputFingerprint: expected.comparison,
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
      comparison: {
        schemaVersion: 1,
        status: 'passed',
        designIr: {
          documentId: 'fixture',
          sourceFingerprint: 'a'.repeat(64),
          irFingerprint: expected.designIr,
        },
        render: {
          requestFingerprint: expected.request,
          outputFingerprint: expected.output,
          renderTreeFingerprint: expected.tree,
          viewport: {widthPx: 1079, heightPx: 2339},
          density: 2.625,
          fontScale: 1,
          localeTag: 'en-US',
          layoutDirection: 'Ltr',
        },
        summary: expected.comparisonSummary,
        nodes: comparisonNodes,
        findings: [],
        comparisonFingerprint: expected.comparison,
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
  assert.equal(summary.fingerprints[0].render, loginExpected.output);
  assert.equal(summary.fingerprints[0].comparison, loginExpected.comparison);
  assert.equal(summary.fingerprints[1].render, profileExpected.output);
  assert.equal(summary.fingerprints[1].comparison, profileExpected.comparison);
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
