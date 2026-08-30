import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase4GeneratedPreview} from './verify-phase4-generated-preview.mjs';

const loginExpected = Object.freeze({
  request: 'e8e7bee0775fd57e81c84073b4249406b2189536438ebb851d9d6ec6898ec69a',
  kotlin: '6c4f6dafef9e0b4808eefab440d14e331b1a3b55bc8becff7a05d3669cc73be1',
  wrapper: '8d4ff9932ada6621a05b486a22d410d79a674db787c29ac22b1e7e4e0dcf8821',
  build: '761d759c682110d9702f169c7b885a30ac77e3821401a22c21629c508760e18c',
  output: 'f92cfa3f26a76a3955064f0beea0971400c62e0393889273de1e7a96dc09e995',
  png: 'adea48c351d08a9949694173cba497f530389cd3891f8312c4cfef08c01b5540',
  tree: 'ad1665ecd9f6d7e2f7f97b25b338454f32b79ee7bcd0405f81b089a557969536',
  variant: 'generated-xml-loginview-abee9c74',
  sourceLine: 22,
  capabilityIds: ['foundation.components', 'modifier.layout'],
  imageBytes: 37608,
  treeBytes: 202604,
  treeStructure: {vnodeCount: 5, mountedNodeCount: 5, maxVNodeDepth: 3, maxMountedDepth: 3},
  assets: [],
  designIr: 'a938f6c0bd8333e195414353766d7e577bbcab0584c219cf4d123869192964d4',
  comparison: 'a9507e2e496a5d43fe7be925b16e6abe4646a219fb3ede78f0b3f4238f966ebd',
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
      bounds: [42, 42, 456, 85],
      checks: [
        'identity.key', 'structure.parent', 'structure.sibling-order', 'semantic.kind',
        'semantic.visibility', 'semantic.text', 'geometry.containment',
      ],
    },
    {
      id: 'id:email', key: 'email', kind: 'text-field', wrapperDepth: 1,
      bounds: [42, 85, 1037, 232],
      checks: [
        'identity.key', 'structure.parent', 'structure.sibling-order', 'semantic.kind',
        'semantic.role', 'semantic.visibility', 'geometry.width.match-parent',
        'geometry.containment',
      ],
    },
    {
      id: 'id:submit', key: 'submit', kind: 'button', wrapperDepth: 0,
      bounds: [42, 232, 1037, 358],
      checks: [
        'identity.key', 'structure.parent', 'structure.sibling-order', 'semantic.kind',
        'semantic.role', 'semantic.visibility', 'semantic.text',
        'geometry.width.match-parent', 'geometry.containment',
      ],
    },
  ],
});

const profileExpected = Object.freeze({
  request: '3411584d19996d667cd82da9ae2f6dff98e41bb5f28288a04760fd5ceaf6ba26',
  kotlin: '15b15098e92b62bc9730ab7b3f2bde7715596f22069490a18b1e7830ff92ad35',
  wrapper: '461d7c9e7b9898b9b9f7373775fa10c8a180097664627b442d36a8b2abd2a4b2',
  build: '7a5e1e105705669c6e103cf101533c8417bbba5f5acace9316d5dbc1d4b7e46a',
  output: 'f91bc14f5830c5707157dd0a683fbc48612beee755333e44c7f6b55a13788369',
  png: '3e5437e7fc53f3d3d5235df1287b3ea0fc5d93a86350b7abbcbe26ab3beaef11',
  tree: '5b174559d469bc65e63edfb3e0ba91e476e31d3039486faacaa452a89d0368d8',
  variant: 'generated-xml-profilecardview-abee9c74',
  sourceLine: 23,
  capabilityIds: [
    'foundation.components',
    'image.foundation',
    'modifier.drawing',
    'modifier.layout',
  ],
  imageBytes: 15218,
  treeBytes: 120988,
  treeStructure: {vnodeCount: 3, mountedNodeCount: 3, maxVNodeDepth: 2, maxMountedDepth: 2},
  assets: [{
    resourceName: 'vc_ai_4ff6ab670a58c14270e034e2090d9a432caa263a14e0a25785386b0c12f880b5',
    bytes: 70,
    sha256: '4ff6ab670a58c14270e034e2090d9a432caa263a14e0a25785386b0c12f880b5',
    widthPx: 1,
    heightPx: 1,
  }],
  designIr: '8a860b20a34b87d0eae3918f12d1968e3653e0fe46da0cceffa68f70e9c25b09',
  comparison: '7410eb175279e971e035612ee40c19e3247f1bc4ffb520ec95490d25008079bb',
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
      compilerLane:
        'released-maven/jdk-17-or-21/gradle-9.3.1/agp-9.1.1/kotlin-2.2.10/android-36/jvm-11',
      renderLane: 'released-maven/preview-protocol-1/paparazzi-2.0.0-alpha02/layoutlib-15.2.3',
      outputFingerprint: expected.comparison,
    },
    diagnostics: [],
    data: {
      preview: {
        targetId: 'tools.ai.GeneratedXmlPreview',
        modulePath: ':preview',
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
          path: `preview/requests/${expected.request}/input/GeneratedPreview.kt`,
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
        layoutDiagnosis: {
          summary: {clean: true, actionableCount: 0},
          structure: expected.treeStructure,
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
