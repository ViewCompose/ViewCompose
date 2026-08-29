import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdir, mkdtemp, readFile, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {
  createGeneratedPreviewPlan,
  renderGeneratedPreview,
  validateGeneratedPreviewRequest,
} from './generated-preview-adapter.mjs';
import {PREVIEW_COMPILER_LANE, RENDER_LANE} from './preview-adapter.mjs';

const fixtureRoot = new URL('../evaluation/fixtures/xml/', import.meta.url);

async function fixture(path) {
  return readFile(new URL(path, fixtureRoot), 'utf8');
}

function loginReport() {
  return {
    schemaVersion: 1,
    target: {
      language: 'kotlin',
      packageName: 'generated.viewcompose',
      functionName: 'LoginView',
      artifactIds: ['viewcompose-ui-foundation'],
    },
    bindings: {
      resources: [
        {parameter: 'loginTitle', source: '@string/login_title', type: 'String'},
        {parameter: 'emailHint', source: '@string/email_hint', type: 'String'},
        {parameter: 'loginAction', source: '@string/login_action', type: 'String'},
      ],
      states: [
        {parameter: 'emailState', source: 'emailState', type: 'TextFieldState'},
      ],
    },
  };
}

function profileReport() {
  return {
    schemaVersion: 1,
    target: {
      language: 'kotlin',
      packageName: 'generated.viewcompose',
      functionName: 'ProfileCardView',
      artifactIds: ['viewcompose-ui-foundation'],
    },
    bindings: {
      resources: [
        {parameter: 'profileAvatar', source: '@drawable/profile_avatar', type: 'ImageSource'},
        {parameter: 'profilePhoto', source: '@string/profile_photo', type: 'String'},
        {parameter: 'statusLabel', source: '@string/status_label', type: 'String'},
      ],
      states: [],
    },
  };
}

test('builds the exact frozen generated Preview request and Kotlin wrapper', async () => {
  const expectedRequest = JSON.parse(await fixture('generated-preview/login.preview-request.json'));
  const bindings = expectedRequest.bindings;
  const plan = await createGeneratedPreviewPlan({
    generatedKotlin: await fixture('login.viewcompose.kt'),
    generationReport: loginReport(),
    previewBindings: bindings,
  });

  assert.equal(plan.status, 'success');
  assert.deepEqual(plan.request, expectedRequest);
  assert.equal(
    plan.wrapper,
    await fixture('generated-preview/login.preview-wrapper.kt'),
  );
  assert.equal(
    plan.requestFingerprint,
    '8b2d5460fea40ee539fc5aba01af5cac97d59002476c17b744fb7baa1144d063',
  );
  assert.equal(
    plan.wrapperFingerprint,
    '8d4ff9932ada6621a05b486a22d410d79a674db787c29ac22b1e7e4e0dcf8821',
  );
});

test('builds the exact frozen embedded PNG request, wrapper, and resource plan', async () => {
  const expectedRequest = JSON.parse(
    await fixture('generated-preview/image-binding.preview-request.json'),
  );
  const plan = await createGeneratedPreviewPlan({
    generatedKotlin: await fixture('profile-card.viewcompose.kt'),
    generationReport: profileReport(),
    previewBindings: expectedRequest.bindings,
  });

  assert.equal(plan.status, 'success');
  assert.deepEqual(plan.request, expectedRequest);
  assert.equal(plan.wrapper, await fixture('generated-preview/profile-card.preview-wrapper.kt'));
  assert.equal(
    plan.requestFingerprint,
    '1d81d2ed9db84ee022d806042cd883c426f4fe0061aa65c757ef0de3a91225f6',
  );
  assert.equal(
    plan.wrapperFingerprint,
    '461d7c9e7b9898b9b9f7373775fa10c8a180097664627b442d36a8b2abd2a4b2',
  );
  assert.equal(plan.assets.length, 1);
  assert.equal(plan.assets[0].bytes.length, 70);
  assert.equal(
    plan.assets[0].resourceName,
    'vc_ai_4ff6ab670a58c14270e034e2090d9a432caa263a14e0a25785386b0c12f880b5',
  );
});

test('fails closed on missing and unsupported image bindings before rendering', async () => {
  const missing = JSON.parse(await fixture('generated-preview/missing-binding.preview-request.json'));
  const missingPlan = await createGeneratedPreviewPlan({
    generatedKotlin: await fixture('login.viewcompose.kt'),
    generationReport: loginReport(),
    previewBindings: missing.bindings,
  });
  assert.equal(missingPlan.status, 'unsupported');
  assert.equal(missingPlan.diagnostic.code, 'VC-AI-PREVIEW-BINDING-MISSING');

  const image = JSON.parse(
    await fixture('generated-preview/image-asset-missing.preview-request.json'),
  );
  const imagePlan = await createGeneratedPreviewPlan({
    generatedKotlin: await fixture('profile-card.viewcompose.kt'),
    generationReport: profileReport(),
    previewBindings: image.bindings,
  });
  assert.equal(imagePlan.status, 'unsupported');
  assert.equal(imagePlan.diagnostic.code, 'VC-AI-PREVIEW-ASSET-MISSING');
});

test('denies caller-selected build execution fields at the adapter boundary', async () => {
  const request = JSON.parse(
    await fixture('generated-preview/build-selection.preview-request.json'),
  );
  const result = await validateGeneratedPreviewRequest(request);
  assert.equal(result.status, 'invalid');
  assert.equal(result.diagnostic.code, 'VC-AI-PREVIEW-BUILD-SELECTION-DENIED');
});

test('stages immutable source and accepts only complete pinned render evidence', async (context) => {
  const repository = await mkdtemp(resolve(tmpdir(), 'viewcompose-generated-preview-'));
  context.after(() => rm(repository, {recursive: true, force: true}));
  const expectedRequest = JSON.parse(await fixture('generated-preview/login.preview-request.json'));
  let rendered = 0;
  const result = await renderGeneratedPreview({
    generatedKotlin: await fixture('login.viewcompose.kt'),
    generationReport: loginReport(),
    previewBindings: expectedRequest.bindings,
    requestId: 'generated-preview-success',
  }, {
    repository,
    cacheRoot: resolve(repository, 'build/ai/preview/requests'),
    render: async (request, options) => {
      rendered += 1;
      assert.equal(request.targetId, 'tools.ai.GeneratedXmlPreview');
      const target = options.targets[request.targetId];
      assert.equal(target.modulePath, ':tools:ai-preview-harness');
      assert.deepEqual(target.gradleArguments, [
        '-PviewComposeAiPreviewRequestKey=' +
          '8b2d5460fea40ee539fc5aba01af5cac97d59002476c17b744fb7baa1144d063',
      ]);
      const input = resolve(
        repository,
        'build/ai/preview/requests',
        '8b2d5460fea40ee539fc5aba01af5cac97d59002476c17b744fb7baa1144d063',
        'input',
      );
      assert.equal(await readFile(resolve(input, 'GeneratedView.kt'), 'utf8'),
        await fixture('login.viewcompose.kt'));
      assert.equal(await readFile(resolve(input, 'GeneratedPreview.kt'), 'utf8'),
        await fixture('generated-preview/login.preview-wrapper.kt'));
      return {
        schemaVersion: 1,
        kind: 'result',
        requestId: request.requestId,
        tool: 'render_preview',
        status: 'success',
        evidence: {
          level: 'rendered',
          cache: 'miss',
          compilerLane: PREVIEW_COMPILER_LANE,
          renderLane: RENDER_LANE,
          outputFingerprint: 'a'.repeat(64),
        },
        diagnostics: [],
        data: {
          image: {sha256: 'b'.repeat(64)},
          renderTree: {sha256: 'c'.repeat(64)},
        },
        elapsedMs: 1,
        truncated: false,
      };
    },
  });

  assert.equal(rendered, 1);
  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'rendered');
  assert.equal(
    result.data.generatedPreview.requestFingerprint,
    '8b2d5460fea40ee539fc5aba01af5cac97d59002476c17b744fb7baa1144d063',
  );
  assert.equal(result.data.generatedPreview.pngSha256, 'b'.repeat(64));
  assert.equal(result.data.generatedPreview.renderTreeSha256, 'c'.repeat(64));
});

test('stages an exact PNG resource and reports its public identity without raw bytes', async (context) => {
  const repository = await mkdtemp(resolve(tmpdir(), 'viewcompose-generated-preview-asset-'));
  context.after(() => rm(repository, {recursive: true, force: true}));
  const expectedRequest = JSON.parse(
    await fixture('generated-preview/image-binding.preview-request.json'),
  );
  const requestKey =
    '1d81d2ed9db84ee022d806042cd883c426f4fe0061aa65c757ef0de3a91225f6';
  const resourceName =
    'vc_ai_4ff6ab670a58c14270e034e2090d9a432caa263a14e0a25785386b0c12f880b5';
  const result = await renderGeneratedPreview({
    generatedKotlin: await fixture('profile-card.viewcompose.kt'),
    generationReport: profileReport(),
    previewBindings: expectedRequest.bindings,
    requestId: 'generated-preview-asset-success',
  }, {
    repository,
    cacheRoot: resolve(repository, 'build/ai/preview/requests'),
    render: async (request, options) => {
      assert.deepEqual(request.capabilityIds, [
        'foundation.components',
        'image.foundation',
        'modifier.drawing',
        'modifier.layout',
      ]);
      assert.deepEqual(options.targets[request.targetId].capabilityIds, request.capabilityIds);
      const requestRoot = resolve(repository, 'build/ai/preview/requests', requestKey);
      assert.equal(
        await readFile(resolve(requestRoot, 'input/GeneratedPreview.kt'), 'utf8'),
        await fixture('generated-preview/profile-card.preview-wrapper.kt'),
      );
      assert.deepEqual(
        await readFile(resolve(requestRoot, `res/drawable/${resourceName}.png`)),
        Buffer.from(expectedRequest.bindings[0].asset.data, 'base64'),
      );
      return {
        status: 'success',
        evidence: {
          level: 'rendered',
          cache: 'miss',
          compilerLane: PREVIEW_COMPILER_LANE,
          renderLane: RENDER_LANE,
          outputFingerprint: 'd'.repeat(64),
        },
        diagnostics: [],
        data: {
          image: {sha256: 'e'.repeat(64)},
          renderTree: {sha256: 'f'.repeat(64)},
        },
      };
    },
  });

  assert.equal(result.status, 'success');
  assert.deepEqual(result.data.generatedPreview.assets, [{
    resourceName,
    bytes: 70,
    sha256: '4ff6ab670a58c14270e034e2090d9a432caa263a14e0a25785386b0c12f880b5',
    widthPx: 1,
    heightPx: 1,
  }]);
  assert.equal(JSON.stringify(result).includes(expectedRequest.bindings[0].asset.data), false);
});

test('rejects changed PNG CRC and a symbolic-link asset before Gradle execution', async (context) => {
  const expectedRequest = JSON.parse(
    await fixture('generated-preview/image-binding.preview-request.json'),
  );
  const corruptedBindings = structuredClone(expectedRequest.bindings);
  const corrupted = Buffer.from(corruptedBindings[0].asset.data, 'base64');
  const idat = corrupted.indexOf(Buffer.from('IDAT'));
  corrupted[idat + 4] ^= 1;
  corruptedBindings[0].asset.data = corrupted.toString('base64');
  corruptedBindings[0].asset.sha256 = createHash('sha256').update(corrupted).digest('hex');
  const invalid = await createGeneratedPreviewPlan({
    generatedKotlin: await fixture('profile-card.viewcompose.kt'),
    generationReport: profileReport(),
    previewBindings: corruptedBindings,
  });
  assert.equal(invalid.status, 'invalid');
  assert.equal(invalid.diagnostic.code, 'VC-AI-PREVIEW-ASSET-INTEGRITY-INVALID');

  const repository = await mkdtemp(resolve(tmpdir(), 'viewcompose-generated-preview-asset-link-'));
  context.after(() => rm(repository, {recursive: true, force: true}));
  const requestKey =
    '1d81d2ed9db84ee022d806042cd883c426f4fe0061aa65c757ef0de3a91225f6';
  const drawable = resolve(repository, 'build/ai/preview/requests', requestKey, 'res/drawable');
  await mkdir(drawable, {recursive: true});
  const external = resolve(repository, 'external.png');
  await writeFile(external, Buffer.from(expectedRequest.bindings[0].asset.data, 'base64'));
  await symlink(
    external,
    resolve(
      drawable,
      'vc_ai_4ff6ab670a58c14270e034e2090d9a432caa263a14e0a25785386b0c12f880b5.png',
    ),
  );
  let rendered = 0;
  const poisoned = await renderGeneratedPreview({
    generatedKotlin: await fixture('profile-card.viewcompose.kt'),
    generationReport: profileReport(),
    previewBindings: expectedRequest.bindings,
    requestId: 'generated-preview-asset-link',
  }, {
    repository,
    cacheRoot: resolve(repository, 'build/ai/preview/requests'),
    render: async () => { rendered += 1; },
  });
  assert.equal(rendered, 0);
  assert.equal(poisoned.status, 'failed');
  assert.equal(poisoned.diagnostics[0].code, 'VC-AI-RENDER-CACHE-POISONED');
});

test('rejects changed bytes in an existing content-addressed input', async (context) => {
  const repository = await mkdtemp(resolve(tmpdir(), 'viewcompose-generated-preview-poison-'));
  context.after(() => rm(repository, {recursive: true, force: true}));
  const requestKey = '8b2d5460fea40ee539fc5aba01af5cac97d59002476c17b744fb7baa1144d063';
  const input = resolve(repository, 'build/ai/preview/requests', requestKey, 'input');
  await mkdir(input, {recursive: true});
  await writeFile(resolve(input, 'GeneratedView.kt'), 'tampered\n');
  const expectedRequest = JSON.parse(await fixture('generated-preview/login.preview-request.json'));
  let rendered = 0;
  const result = await renderGeneratedPreview({
    generatedKotlin: await fixture('login.viewcompose.kt'),
    generationReport: loginReport(),
    previewBindings: expectedRequest.bindings,
    requestId: 'generated-preview-poisoned',
  }, {
    repository,
    cacheRoot: resolve(repository, 'build/ai/preview/requests'),
    render: async () => {
      rendered += 1;
    },
  });
  assert.equal(rendered, 0);
  assert.equal(result.status, 'failed');
  assert.equal(result.diagnostics[0].code, 'VC-AI-RENDER-CACHE-POISONED');
});
