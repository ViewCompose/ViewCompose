import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {importFigmaExport} from './figma-import-adapter.mjs';
import {createFigmaRenderPlan, generateFigmaArtifacts} from './figma-render-plan.mjs';
import {createGeneratedPreviewPlan} from './generated-preview-adapter.mjs';

const exampleUrl = new URL('../contracts/examples/figma-export.json', import.meta.url);

async function fixture() {
  const exported = JSON.parse(await readFile(exampleUrl, 'utf8'));
  const request = {
    schemaVersion: 1,
    kind: 'figma-import-request',
    mode: 'inspect',
    exportJson: JSON.stringify(exported),
  };
  const inspected = await importFigmaExport(request, {requestId: 'figma-render-plan'});
  assert.equal(inspected.status, 'success');
  const assets = new Map(exported.assets.map((asset) => [asset.id, {
    asset,
    bytes: Buffer.from(asset.data, 'base64'),
  }]));
  return {exported, inspected, assets};
}

test('lowers one audited Figma root to deterministic Kotlin and virtual PNG files', async () => {
  const {inspected, assets} = await fixture();
  const firstPlan = createFigmaRenderPlan({
    designIr: inspected.data.designIr,
    assets,
  });
  const secondPlan = createFigmaRenderPlan({
    designIr: structuredClone(inspected.data.designIr),
    assets,
  });
  assert.deepEqual(firstPlan, secondPlan);
  const first = generateFigmaArtifacts(firstPlan);
  const second = generateFigmaArtifacts(secondPlan);
  assert.deepEqual(first, second);
  assert.match(first.kotlin, /fun UiTreeBuilder\.FigmaFileExampleView\(/u);
  assert.match(first.kotlin, /Column\(/u);
  assert.match(first.kotlin, /Text\(/u);
  assert.match(first.kotlin, /Image\(/u);
  assert.match(first.kotlin, /UiTextStyle\(/u);
  assert.match(first.kotlin, /contentDescription = null/u);
  assert.match(first.kotlin, /\.padding\(left = 16\.dp/u);
  assert.equal(first.virtualFiles.length, 2);
  assert.deepEqual(
    first.virtualFiles.map((file) => file.mediaType).sort(),
    ['image/png', 'text/x-kotlin'],
  );
  assert.equal(first.previewBindings.length, 1);
  assert.equal(first.report.kind, 'figma-generation-report');
  const previewPlan = await createGeneratedPreviewPlan({
    generatedKotlin: first.kotlin,
    generationReport: first.report,
    previewBindings: first.previewBindings,
    previewConfiguration: {
      widthDp: 360,
      heightDp: 120,
      density: 1,
      fontScale: 1,
      localeTag: 'en-US',
      layoutDirection: 'Ltr',
      theme: 'Light',
      apiLevel: null,
    },
  });
  assert.equal(previewPlan.status, 'success');
  assert.equal(previewPlan.profile.targetId, 'tools.ai.GeneratedFigmaPreview');
  assert.equal(previewPlan.request.generatedSource.sourceKind, 'figma');
});

test('refuses multiple roots and error-level mapping decisions', async () => {
  const {inspected, assets} = await fixture();
  const multiple = structuredClone(inspected.data.designIr);
  multiple.roots.push(structuredClone(multiple.roots[0]));
  assert.throws(
    () => createFigmaRenderPlan({designIr: multiple, assets}),
    /requires one supported root/u,
  );
  const blocked = structuredClone(inspected.data.designIr);
  blocked.unsupported.push({
    nodeId: blocked.roots[0].id,
    sourcePath: 'effect.blur',
    code: 'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
    reason: 'Unsupported effect.',
    sourceValueFingerprint: 'a'.repeat(64),
    severity: 'error',
    disposition: 'blocked',
  });
  assert.throws(
    () => createFigmaRenderPlan({designIr: blocked, assets}),
    /requires one supported root/u,
  );
});

test('keeps adversarial document identities and text inside Kotlin syntax boundaries', async () => {
  const {inspected, assets} = await fixture();
  const adversarial = structuredClone(inspected.data.designIr);
  adversarial.documentId = '9 class ${Injected}\n) { injectedCall()';
  const textNode = adversarial.roots[0].children.find((node) => node.kind === 'text');
  const text = textNode.properties.find((property) => property.name === 'text');
  text.value.value = '" ); injectedCall() // ${runtime}\nnext';

  const generated = generateFigmaArtifacts(createFigmaRenderPlan({
    designIr: adversarial,
    assets,
  }));

  assert.match(generated.kotlin, /fun UiTreeBuilder\.Generated9ClassInjectedInjectedCallView\(/u);
  assert.ok(generated.kotlin.includes(
    'text = "\\\" ); injectedCall() // \\${runtime}\\nnext",',
  ));
  assert.equal(generated.kotlin.match(/^\) \{ injectedCall\(\)$/gmu), null);
  assert.equal(generated.virtualFiles[0].path.includes('..'), false);
});
