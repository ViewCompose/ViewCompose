#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {lstat, readFile} from 'node:fs/promises';
import {isAbsolute, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {generateScreenshotViewCompose} from './screenshot-generation-adapter.mjs';
import {SCREENSHOT_GENERATION_REQUEST_SCHEMA} from './screenshot-generation-contract.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {canonicalJson} from './screenshot-contract.mjs';
import {toolCacheRoot} from './tool-core.mjs';

const fixtureRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));
const contractPath = `${fixtureRoot}screenshot-generated-preview-contract.json`;
const requestSchemaPath = fileURLToPath(
  new URL('../contracts/generated-preview-request.schema.json', import.meta.url),
);

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function declaredIdentity(binding) {
  return `${binding.parameter}\0${binding.source}\0${binding.type}`;
}

function suppliedType(binding) {
  return {
    'text-field-state': 'TextFieldState',
    'unit-callback': '() -> Unit',
    'boolean-callback': '(Boolean) -> Unit',
    'ime-action-callback': '(TextFieldImeAction) -> Boolean',
  }[binding.kind];
}

function suppliedIdentity(binding) {
  return `${binding.parameter}\0${binding.source}\0${suppliedType(binding)}`;
}

function assertContract(contract) {
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-screenshot-generated-preview-v1' ||
    JSON.stringify(contract.requiresContracts) !== JSON.stringify([
      'viewcompose-screenshot-kotlin-generation-v1',
      'viewcompose-generated-preview-v1',
      'generated-preview-request-v1',
    ]) ||
    contract.activation?.tool !== 'generate_screenshot_viewcompose' ||
    contract.activation?.status !== 'implemented' ||
    contract.activation?.publicRenderMode !== true ||
    contract.activation?.implementation !== true ||
    contract.activation?.evidence !== 'rendered-golden'
  ) {
    throw new Error('Screenshot generated Preview activation boundary changed');
  }
  if (
    contract.profile?.sourceKind !== 'screenshot' ||
    contract.profile?.targetId !== 'tools.ai.GeneratedScreenshotPreview' ||
    contract.profile?.functionName !== 'GeneratedScreenshotPreview' ||
    contract.profile?.annotationNamePrefix !== 'Generated Screenshot · ' ||
    contract.profile?.annotationGroup !== 'AI/Screenshot' ||
    contract.profile?.ownerClassName !== 'generated.viewcompose.GeneratedPreviewKt'
  ) {
    throw new Error('Screenshot generated Preview profile changed');
  }
  if (
    contract.bindings?.exactOrderRequired !== true ||
    contract.bindings?.exactParameterRequired !== true ||
    contract.bindings?.exactSourceRequired !== true ||
    contract.bindings?.exactTypeRequired !== true ||
    contract.bindings?.callbackSourceAllowed !== false ||
    contract.bindings?.allowed?.['TextFieldState']?.kind !== 'text-field-state' ||
    contract.bindings?.allowed?.['() -> Unit']?.kind !== 'unit-callback' ||
    contract.bindings?.allowed?.['(Boolean) -> Unit']?.kind !== 'boolean-callback' ||
    contract.bindings?.allowed?.['(TextFieldImeAction) -> Boolean']?.kind !==
      'ime-action-callback'
  ) {
    throw new Error('Screenshot generated Preview safe-binding contract changed');
  }
  if (
    contract.execution?.fixedPreviewHarness !== true ||
    contract.execution?.projectBuildExecution !== false ||
    contract.execution?.networkAccess !== 'first-use dependency resolution only' ||
    contract.execution?.providerExecution !== false ||
    contract.execution?.callerSourceExecution !== false ||
    contract.execution?.callerTaskSelection !== false ||
    contract.execution?.callerDependencySelection !== false ||
    contract.execution?.callerOutputPathSelection !== false ||
    contract.claims?.deterministicWrapper !== true ||
    contract.claims?.safeBindingContract !== true ||
    contract.claims?.wrapperCompilation !== true ||
    contract.claims?.runtimeRendering !== true ||
    contract.claims?.semanticComparison !== false ||
    contract.claims?.visualParity !== false ||
    contract.limits?.maxBindings !== 64 ||
    contract.limits?.maxGeneratedKotlinBytes !== 1048576 ||
    contract.limits?.maxWrapperBytes !== 262144
  ) {
    throw new Error('Screenshot generated Preview execution or evidence boundary changed');
  }
  if (
    new Set(contract.diagnosticCodes).size !== contract.diagnosticCodes.length ||
    contract.diagnosticCodes.some((code) => !/^VC-AI-PREVIEW-[A-Z0-9-]+$/u.test(code))
  ) {
    throw new Error('Screenshot generated Preview diagnostic contract changed');
  }
}

function applyMutation(request, descriptor) {
  const mutated = structuredClone(request);
  const index = mutated.bindings.findIndex((binding) =>
    binding.parameter === descriptor.parameter);
  if (index < 0) throw new Error(`${descriptor.parameter}: mutation target is missing`);
  if (descriptor.operation === 'add-callback-source') {
    mutated.bindings[index].value = descriptor.value;
  } else if (descriptor.operation === 'remove-binding') {
    mutated.bindings.splice(index, 1);
  } else if (descriptor.operation === 'replace-binding-kind') {
    const current = mutated.bindings[index];
    mutated.bindings[index] = {
      kind: descriptor.value.kind,
      parameter: current.parameter,
      source: current.source,
      behavior: descriptor.value.behavior,
    };
  } else {
    throw new Error(`${descriptor.operation}: unknown screenshot Preview mutation`);
  }
  return mutated;
}

function inferDiagnostic(request, schema) {
  if (validateSchemaValue(request, schema).length > 0) {
    return 'VC-AI-PREVIEW-BINDING-VALUE-INVALID';
  }
  const declared = request.generatedSource.declaredBindings;
  const supplied = request.bindings;
  const suppliedParameters = new Set(supplied.map((binding) => binding.parameter));
  if (declared.some((binding) => !suppliedParameters.has(binding.parameter))) {
    return 'VC-AI-PREVIEW-BINDING-MISSING';
  }
  if (
    JSON.stringify(declared.map(declaredIdentity)) !==
      JSON.stringify(supplied.map(suppliedIdentity))
  ) {
    return 'VC-AI-PREVIEW-GENERATED-SOURCE-MISMATCH';
  }
  return null;
}

function containedRelativePath(path, repository) {
  if (typeof path !== 'string' || path.length === 0 || isAbsolute(path)) return false;
  const normalized = relative(repository, resolve(repository, path));
  return normalized !== '..' && !normalized.startsWith(`..${sep}`) && !isAbsolute(normalized);
}

async function inspectArtifacts(preview, fixture, repository = toolCacheRoot()) {
  if (
    !containedRelativePath(preview.image?.path, repository) ||
    !containedRelativePath(preview.renderTree?.path, repository)
  ) {
    throw new Error('Screenshot Preview exposed an unsafe rendered artifact path');
  }
  const imagePath = resolve(repository, preview.image.path);
  const treePath = resolve(repository, preview.renderTree.path);
  const [imageMetadata, treeMetadata, image, treeBytes] = await Promise.all([
    lstat(imagePath),
    lstat(treePath),
    readFile(imagePath),
    readFile(treePath),
  ]);
  if (
    !imageMetadata.isFile() || imageMetadata.isSymbolicLink() ||
    !treeMetadata.isFile() || treeMetadata.isSymbolicLink() ||
    image.length !== fixture.expectedImage.bytes ||
    sha256(image) !== fixture.expectedImage.sha256 ||
    treeBytes.length !== fixture.expectedRenderTree.bytes ||
    sha256(treeBytes) !== fixture.expectedRenderTree.sha256
  ) {
    throw new Error('Screenshot Preview artifacts differ from accepted immutable evidence');
  }
  if (
    image.length < 24 ||
    image.subarray(1, 4).toString('ascii') !== 'PNG' ||
    image.readUInt32BE(16) !== fixture.expectedImage.widthPx ||
    image.readUInt32BE(20) !== fixture.expectedImage.heightPx
  ) {
    throw new Error('Screenshot Preview PNG header or dimensions changed');
  }
  const tree = JSON.parse(treeBytes.toString('utf8'));
  const treeText = treeBytes.toString('utf8');
  const texts = [...new Set([...treeText.matchAll(/"text":\s*"([^"]+)"/gu)]
    .map((match) => match[1]))];
  const contentDescriptions = [...new Set([
    ...treeText.matchAll(/"contentDescription":\s*"([^"]+)"/gu),
  ].map((match) => match[1]))];
  if (
    JSON.stringify(tree.structure) !== JSON.stringify({
      vnodeCount: fixture.expectedRenderTree.vnodeCount,
      mountedNodeCount: fixture.expectedRenderTree.mountedNodeCount,
      maxVNodeDepth: fixture.expectedRenderTree.maxVNodeDepth,
      maxMountedDepth: fixture.expectedRenderTree.maxMountedDepth,
    }) ||
    JSON.stringify(texts) !== JSON.stringify(fixture.expectedRenderTree.texts) ||
    JSON.stringify(contentDescriptions) !==
      JSON.stringify(fixture.expectedRenderTree.contentDescriptions) ||
    tree.warnings?.length !== 0 ||
    tree.layoutDiagnostics?.length !== 0
  ) {
    throw new Error('Screenshot Preview render-tree semantics or diagnostics changed');
  }
  return {texts, contentDescriptions, structure: tree.structure};
}

function assertRendered(result, fixture, requiredCache) {
  const preview = result.data?.preview;
  if (
    result.status !== 'success' ||
    result.evidence?.level !== 'rendered' ||
    (requiredCache && result.evidence.cache !== requiredCache) ||
    result.evidence?.compilerLane !==
      'released-maven/jdk-17-or-21/gradle-9.3.1/agp-9.1.1/kotlin-2.2.10/android-36/jvm-11' ||
    result.evidence?.renderLane !==
      'released-maven/preview-protocol-1/paparazzi-2.0.0-alpha02/layoutlib-15.2.3' ||
    result.evidence?.outputFingerprint !== fixture.expectedOutputFingerprint ||
    result.diagnostics?.length !== 0 ||
    result.data?.kotlinFingerprint !==
      '5812c3ccbd0a6f30a0cc4c3ff4e71453006745d5dd76e63e153b2501131252e9' ||
    result.data?.generationReport?.requestFingerprint !==
      '68d3da2054ffe5513a90975d96f47be6cfa1137ad1b1e796114a4a27827b3d49' ||
    result.data?.generationReport?.reportFingerprint !==
      '464d4f31c5ec59a5083b58309240c76fc69709b42648b79beb4ff281ac2f93db' ||
    preview?.targetId !== 'tools.ai.GeneratedScreenshotPreview' ||
    preview?.modulePath !== ':preview' ||
    preview?.buildVariant !== 'debug' ||
    preview?.buildFingerprint !== fixture.expectedBuildFingerprint ||
    preview?.previewId !== fixture.expectedPreviewId ||
    preview?.variantId !== fixture.expectedVariantId ||
    JSON.stringify(preview?.configuration) !== JSON.stringify({
      widthDp: 411,
      heightDp: -1,
      density: 2.625,
      fontScale: 1,
      localeTags: ['en-US'],
      layoutDirection: 'Ltr',
      theme: 'Light',
    }) ||
    JSON.stringify(preview?.capabilityIds) !== JSON.stringify(fixture.expectedCapabilityIds) ||
    preview?.source?.path !==
      `preview/requests/${
        '64957e0715f5bef6423275feb1c28637738e325c167641beca9d8616e90f55ed'
      }/input/GeneratedPreview.kt` ||
    preview?.source?.line !== fixture.expectedSourceLine ||
    preview?.source?.column !== 1 ||
    preview?.image?.mediaType !== 'image/png' ||
    preview?.image?.widthPx !== fixture.expectedImage.widthPx ||
    preview?.image?.heightPx !== fixture.expectedImage.heightPx ||
    preview?.image?.bytes !== fixture.expectedImage.bytes ||
    preview?.image?.sha256 !== fixture.expectedImage.sha256 ||
    preview?.renderTree?.bytes !== fixture.expectedRenderTree.bytes ||
    preview?.renderTree?.sha256 !== fixture.expectedRenderTree.sha256 ||
    preview?.generatedPreview?.sourceKind !== 'screenshot' ||
    preview?.generatedPreview?.targetId !== 'tools.ai.GeneratedScreenshotPreview' ||
    preview?.generatedPreview?.requestFingerprint !==
      '64957e0715f5bef6423275feb1c28637738e325c167641beca9d8616e90f55ed' ||
    preview?.generatedPreview?.generatedKotlinFingerprint !==
      '5812c3ccbd0a6f30a0cc4c3ff4e71453006745d5dd76e63e153b2501131252e9' ||
    preview?.generatedPreview?.wrapperFingerprint !==
      '7b0d004f650248f2108e960385efa7e9a324acc600bfcd142f71c4a8b8d5c65b' ||
    preview?.generatedPreview?.pngSha256 !== fixture.expectedImage.sha256 ||
    preview?.generatedPreview?.renderTreeSha256 !== fixture.expectedRenderTree.sha256 ||
    preview?.generatedPreview?.assets?.length !== 0 ||
    preview?.layoutDiagnosis?.summary?.clean !== true ||
    preview?.layoutDiagnosis?.summary?.actionableCount !== 0 ||
    JSON.stringify(preview?.layoutDiagnosis?.structure) !== JSON.stringify({
      vnodeCount: fixture.expectedRenderTree.vnodeCount,
      mountedNodeCount: fixture.expectedRenderTree.mountedNodeCount,
      maxVNodeDepth: fixture.expectedRenderTree.maxVNodeDepth,
      maxMountedDepth: fixture.expectedRenderTree.maxMountedDepth,
    })
  ) {
    const codes = result.diagnostics?.map((item) => item.code).join(', ') ?? 'none';
    throw new Error(`Screenshot generated Preview evidence changed (${codes})`);
  }
  return preview;
}

export async function verifyPhase5ScreenshotRender({
  renderGolden = true,
  generate = generateScreenshotViewCompose,
  inspect = inspectArtifacts,
} = {}) {
  const [contract, schema] = await Promise.all([
    readJson(contractPath),
    readJson(requestSchemaPath),
  ]);
  assertContract(contract);
  const fixture = contract.supportedFixtures[0];
  const [
    resolution,
    generationRequest,
    renderGenerationRequest,
    generatedKotlin,
    generationReport,
    request,
    wrapper,
  ] =
    await Promise.all([
      readJson(`${fixtureRoot}${fixture.resolutionResult}`),
      readJson(`${fixtureRoot}${fixture.generationRequest}`),
      readJson(`${fixtureRoot}${fixture.renderGenerationRequest}`),
      readFile(`${fixtureRoot}${fixture.generatedKotlin}`, 'utf8'),
      readJson(`${fixtureRoot}${fixture.generationReport}`),
      readJson(`${fixtureRoot}${fixture.previewRequest}`),
      readFile(`${fixtureRoot}${fixture.previewWrapper}`, 'utf8'),
    ]);
  const violations = validateSchemaValue(request, schema);
  const generationViolations = [generationRequest, renderGenerationRequest].flatMap((value) =>
    validateSchemaValue(value, SCREENSHOT_GENERATION_REQUEST_SCHEMA));
  if (violations.length > 0 || generationViolations.length > 0) {
    throw new Error(
      `Screenshot generated Preview request violates schema: ${
        [...violations, ...generationViolations][0]
      }`,
    );
  }
  const declared = request.generatedSource.declaredBindings;
  const supplied = request.bindings;
  if (
    fixture.status !== 'implemented' ||
    request.generatedSource.sourceKind !== 'screenshot' ||
    request.generatedSource.functionName !== generationReport.target.functionName ||
    request.generatedSource.kotlinFingerprint !== sha256(generatedKotlin) ||
    request.generatedSource.kotlinFingerprint !== contract.lineage.generatedKotlinFingerprint ||
    resolution.resultFingerprint !== contract.lineage.resolutionResultFingerprint ||
    resolution.designIrFingerprint !== contract.lineage.resolvedDesignIrFingerprint ||
    generationReport.requestFingerprint !==
      contract.lineage.sourceGenerationRequestFingerprint ||
    generationReport.reportFingerprint !==
      contract.lineage.sourceGenerationReportFingerprint ||
    declared.length !== fixture.expectedBindings ||
    supplied.length !== fixture.expectedBindings ||
    supplied.filter((binding) => binding.kind.endsWith('-callback')).length !==
      fixture.expectedCallbackBindings ||
    JSON.stringify(declared.map(declaredIdentity)) !==
      JSON.stringify(supplied.map(suppliedIdentity)) ||
    supplied.some((binding) => Object.hasOwn(binding, 'value'))
  ) {
    throw new Error('Screenshot generated Preview lineage or exact binding mapping changed');
  }
  const generationRequestFingerprint = sha256(canonicalJson(generationRequest));
  const renderGenerationRequestFingerprint = sha256(canonicalJson(renderGenerationRequest));
  const previewRequestFingerprint = sha256(JSON.stringify(request));
  const previewWrapperFingerprint = sha256(wrapper);
  const requiredWrapper = [
    'name = "Generated Screenshot · ScreenshotWireframeView"',
    'group = "AI/Screenshot"',
    'fun UiTreeBuilder.GeneratedScreenshotPreview()',
    'emailState = TextFieldState()',
    'onEmailSubmit = { _ -> false }',
    'onContinue = { }',
  ];
  if (
    generationRequestFingerprint !== contract.lineage.sourceGenerationRequestFingerprint ||
    renderGenerationRequestFingerprint !==
      contract.lineage.renderGenerationRequestFingerprint ||
    previewRequestFingerprint !== contract.lineage.previewRequestFingerprint ||
    previewWrapperFingerprint !== contract.lineage.previewWrapperFingerprint ||
    Buffer.byteLength(generatedKotlin) > contract.limits.maxGeneratedKotlinBytes ||
    Buffer.byteLength(wrapper) > contract.limits.maxWrapperBytes ||
    requiredWrapper.some((fragment) => !wrapper.includes(fragment)) ||
    /Runtime\.getRuntime|ProcessBuilder|java\.net|kotlin\.reflect|navigate\(\)/u.test(wrapper) ||
    !wrapper.endsWith('\n')
  ) {
    throw new Error('Screenshot generated Preview deterministic wrapper golden changed');
  }

  let blocked = 0;
  for (const fixtureEntry of contract.unsupportedFixtures) {
    const descriptor = await readJson(`${fixtureRoot}${fixtureEntry.mutation}`);
    const expected = fixtureEntry.diagnosticCodes[0];
    if (
      descriptor.expectedDiagnostic !== expected ||
      !contract.diagnosticCodes.includes(expected) ||
      inferDiagnostic(applyMutation(request, descriptor), schema) !== expected
    ) {
      throw new Error(`${fixtureEntry.mutation}: screenshot Preview fail-closed reason changed`);
    }
    blocked += 1;
  }
  let rendered = 0;
  let cacheHits = 0;
  if (renderGolden) {
    const input = {
      resolutionResult: resolution,
      generationRequest: renderGenerationRequest,
      previewBindings: request.bindings,
    };
    const first = await generate(input, {
      requestId: 'screenshot-wireframe-render',
      limits: {
        maxSourceBytes: 2_000_000,
        timeoutMs: 120_000,
        maxOutputBytes: 2_000_000,
      },
    });
    const firstPreview = assertRendered(first, fixture);
    if (
      first.data.generationReport.reportFingerprint !==
      contract.lineage.renderGenerationReportFingerprint
    ) {
      throw new Error('Screenshot render-mode generation report lineage changed');
    }
    await inspect(firstPreview, fixture);
    rendered += 1;

    const second = await generate(input, {
      requestId: 'screenshot-wireframe-render-cache',
      limits: {
        maxSourceBytes: 2_000_000,
        timeoutMs: 120_000,
        maxOutputBytes: 2_000_000,
      },
    });
    assertRendered(second, fixture, 'hit');
    cacheHits += 1;
  }
  return {
    supportedGoldens: 1,
    failClosedDenominators: blocked,
    rendered,
    cacheHits,
    requestFingerprint: previewRequestFingerprint,
    wrapperFingerprint: previewWrapperFingerprint,
    outputFingerprint: fixture.expectedOutputFingerprint,
    imageFingerprint: fixture.expectedImage.sha256,
    renderTreeFingerprint: fixture.expectedRenderTree.sha256,
  };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  verifyPhase5ScreenshotRender()
    .then((result) => {
      process.stdout.write(
        `Verified Phase 5 screenshot Preview: ${result.rendered}/1 exact render, ` +
          `${result.cacheHits}/1 stable cache hit, ` +
          `${result.failClosedDenominators}/3 unsafe bindings blocked, request ` +
          `${result.requestFingerprint}, wrapper ${result.wrapperFingerprint}, output ` +
          `${result.outputFingerprint}, PNG ${result.imageFingerprint}, tree ` +
          `${result.renderTreeFingerprint}.\n`,
      );
    })
    .catch((error) => {
      process.stderr.write(`Phase 5 screenshot Preview verification failed: ${error.message}\n`);
      process.exitCode = 1;
    });
}
