import {createHash} from 'node:crypto';
import {lstat, readFile} from 'node:fs/promises';
import {dirname, isAbsolute, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {
  validateGeneratedPreviewRequest,
} from './generated-preview-adapter.mjs';
import {convertXmlToViewCompose} from './xml-migration.mjs';

const aiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = resolve(aiRoot, '../..');
const fixtureRoot = resolve(aiRoot, 'evaluation/fixtures/xml');

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function containedRelativePath(path) {
  if (typeof path !== 'string' || path.length === 0 || isAbsolute(path)) return false;
  const normalized = relative(repositoryRoot, resolve(repositoryRoot, path));
  return normalized !== '..' && !normalized.startsWith(`..${sep}`) && !isAbsolute(normalized);
}

async function inspectArtifacts(preview, fixture) {
  if (!containedRelativePath(preview.image?.path) || !containedRelativePath(preview.renderTree?.path)) {
    throw new Error(`${fixture.source}: rendered evidence exposed an unsafe artifact path`);
  }
  const imagePath = resolve(repositoryRoot, preview.image.path);
  const renderTreePath = resolve(repositoryRoot, preview.renderTree.path);
  const [imageMetadata, treeMetadata, image, treeBytes] = await Promise.all([
    lstat(imagePath),
    lstat(renderTreePath),
    readFile(imagePath),
    readFile(renderTreePath),
  ]);
  if (
    !imageMetadata.isFile() || imageMetadata.isSymbolicLink() ||
    !treeMetadata.isFile() || treeMetadata.isSymbolicLink()
  ) {
    throw new Error(`${fixture.source}: rendered evidence is not a regular immutable artifact`);
  }
  if (
    image.length !== fixture.expectedImage.bytes ||
    sha256(image) !== fixture.expectedImage.sha256 ||
    treeBytes.length !== fixture.expectedRenderTree.bytes ||
    sha256(treeBytes) !== fixture.expectedRenderTree.sha256
  ) {
    throw new Error(`${fixture.source}: reopened Preview artifacts differ from the frozen evidence`);
  }
  if (
    image.length < 24 ||
    image.subarray(1, 4).toString('ascii') !== 'PNG' ||
    image.readUInt32BE(16) !== fixture.expectedImage.widthPx ||
    image.readUInt32BE(20) !== fixture.expectedImage.heightPx
  ) {
    throw new Error(`${fixture.source}: Preview PNG header or dimensions changed`);
  }
  const tree = JSON.parse(treeBytes.toString('utf8'));
  const texts = [...new Set([...treeBytes.toString('utf8').matchAll(/"text":\s*"([^"]+)"/gu)]
    .map((match) => match[1]))];
  const contentDescriptions = [...new Set([
    ...treeBytes.toString('utf8').matchAll(/"contentDescription":\s*"([^"]+)"/gu),
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
    throw new Error(`${fixture.source}: Preview render-tree semantics changed`);
  }
  return {texts, contentDescriptions, structure: tree.structure};
}

function assertComparison(result, fixture, comparisonFixture) {
  const comparison = result.data?.comparison;
  const expectedNodes = comparisonFixture.expectedNodes;
  const actualNodes = comparison?.nodes?.map((node) => ({
    designNodeId: node.designNodeId,
    identityKey: node.identityKey,
    semanticRenderKind: node.actualKind,
    wrapperDepth: node.wrapperDepth,
    bounds: node.bounds === null
      ? null
      : [node.bounds.left, node.bounds.top, node.bounds.right, node.bounds.bottom],
    checkIds: node.checks.map((item) => item.id),
    statuses: node.checks.map((item) => item.status),
  }));
  if (
    comparison?.schemaVersion !== 1 ||
    comparison?.status !== 'passed' ||
    comparison?.designIr?.documentId === undefined ||
    comparison?.designIr?.sourceFingerprint === undefined ||
    comparison?.designIr?.irFingerprint !== comparisonFixture.expectedComparedDesignIrFingerprint ||
    comparison?.render?.requestFingerprint !== fixture.expectedRequestFingerprint ||
    comparison?.render?.outputFingerprint !== fixture.expectedOutputFingerprint ||
    comparison?.render?.renderTreeFingerprint !== fixture.expectedRenderTree.sha256 ||
    JSON.stringify(comparison?.render?.viewport) !== JSON.stringify(comparisonFixture.viewport) ||
    comparison?.render?.density !== 2.625 ||
    comparison?.render?.fontScale !== 1 ||
    comparison?.render?.localeTag !== 'en-US' ||
    comparison?.render?.layoutDirection !== 'Ltr' ||
    JSON.stringify(comparison?.summary) !== JSON.stringify(comparisonFixture.expectedSummary) ||
    comparison?.findings?.length !== 0 ||
    comparison?.comparisonFingerprint !== comparisonFixture.expectedComparisonFingerprint ||
    actualNodes?.length !== expectedNodes.length
  ) {
    throw new Error(`${fixture.source}: generated layout comparison evidence changed`);
  }
  for (let index = 0; index < expectedNodes.length; index += 1) {
    const expected = expectedNodes[index];
    const actual = actualNodes[index];
    const expectedStatuses = expected.checkIds.map((id) =>
      id === 'geometry.hidden' ? 'not-applicable' : 'passed');
    if (
      actual.designNodeId !== expected.designNodeId ||
      actual.identityKey !== expected.identityKey ||
      actual.semanticRenderKind !== expected.semanticRenderKind ||
      actual.wrapperDepth !== expected.wrapperDepth ||
      JSON.stringify(actual.bounds) !== JSON.stringify(expected.bounds) ||
      JSON.stringify(actual.checkIds) !== JSON.stringify(expected.checkIds) ||
      JSON.stringify(actual.statuses) !== JSON.stringify(expectedStatuses)
    ) {
      throw new Error(`${fixture.source}: compared node ${expected.designNodeId} changed`);
    }
  }
  return comparison;
}

function assertRendered(result, fixture, comparisonFixture, requiredCache) {
  const preview = result.data?.preview;
  if (
    result.status !== 'success' ||
    result.evidence?.level !== 'compared' ||
    (requiredCache && result.evidence.cache !== requiredCache) ||
    result.evidence?.compilerLane !== 'current-source/jdk-21/agp-9.1.1/kotlin-2.2.10/android-37/jvm-11' ||
    result.evidence?.renderLane !==
      'current-source/preview-protocol-1/paparazzi-2.0.0-alpha05/layoutlib-16.2.1' ||
    result.evidence?.outputFingerprint !== comparisonFixture.expectedComparisonFingerprint ||
    result.diagnostics?.length !== 0 ||
    preview?.targetId !== 'tools.ai.GeneratedXmlPreview' ||
    preview?.modulePath !== ':tools:ai-preview-harness' ||
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
      `build/ai/preview/requests/${fixture.expectedRequestFingerprint}/input/GeneratedPreview.kt` ||
    preview?.source?.line !== fixture.expectedSourceLine ||
    preview?.source?.column !== 1 ||
    preview?.image?.mediaType !== 'image/png' ||
    preview?.image?.widthPx !== fixture.expectedImage.widthPx ||
    preview?.image?.heightPx !== fixture.expectedImage.heightPx ||
    preview?.image?.bytes !== fixture.expectedImage.bytes ||
    preview?.image?.sha256 !== fixture.expectedImage.sha256 ||
    preview?.renderTree?.bytes !== fixture.expectedRenderTree.bytes ||
    preview?.renderTree?.sha256 !== fixture.expectedRenderTree.sha256 ||
    preview?.generatedPreview?.requestFingerprint !== fixture.expectedRequestFingerprint ||
    preview?.generatedPreview?.generatedKotlinFingerprint !==
      fixture.expectedGeneratedKotlinFingerprint ||
    preview?.generatedPreview?.wrapperFingerprint !== fixture.expectedWrapperFingerprint ||
    preview?.generatedPreview?.pngSha256 !== fixture.expectedImage.sha256 ||
    preview?.generatedPreview?.renderTreeSha256 !== fixture.expectedRenderTree.sha256 ||
    JSON.stringify(preview?.generatedPreview?.assets ?? []) !== JSON.stringify(
      fixture.expectedAssets === undefined ? [] : [{
        resourceName: fixture.expectedResourceName,
        bytes: fixture.expectedAssetBytes,
        sha256: fixture.expectedAssetSha256,
        widthPx: fixture.expectedAssetWidthPx,
        heightPx: fixture.expectedAssetHeightPx,
      }],
    )
  ) {
    const codes = result.diagnostics?.map((diagnostic) => diagnostic.code).join(', ') ?? 'none';
    throw new Error(`${fixture.source}: generated Preview evidence changed (${codes})`);
  }
  assertComparison(result, fixture, comparisonFixture);
  return preview;
}

export async function verifyPhase4GeneratedPreview({
  convert = convertXmlToViewCompose,
  validateRequest = validateGeneratedPreviewRequest,
  inspect = inspectArtifacts,
} = {}) {
  const [contract, comparisonContract, metrics] = await Promise.all([
    readJson(resolve(fixtureRoot, 'generated-preview-contract.json')),
    readJson(resolve(fixtureRoot, 'layout-comparison-contract.json')),
    readJson(resolve(aiRoot, 'evaluation/metrics.json')),
  ]);
  for (const metricId of [
    'xml.generated-preview-render-success',
    'xml.generated-preview-binding-exactness',
    'xml.generated-preview-asset-integrity',
    'xml.generated-preview-isolation',
    'xml.generated-layout-semantic-exactness',
    'xml.generated-layout-geometry-exactness',
  ]) {
    const metric = metrics.metrics.find((entry) => entry.id === metricId);
    if (!metric || metric.direction !== 'at_least' || metric.threshold !== 1) {
      throw new Error(`${metricId}: generated Preview acceptance must remain an exact 1.00 threshold`);
    }
  }

  let rendered = 0;
  let cacheHits = 0;
  let blocked = 0;
  const fingerprints = [];
  const implementedFixtures = contract.supportedFixtures.filter(
    (fixture) => fixture.status === 'implemented',
  );
  const comparisonBySource = new Map(comparisonContract.supportedFixtures
    .filter((fixture) => fixture.status === 'implemented')
    .map((fixture) => [fixture.source, fixture]));
  for (const fixture of implementedFixtures) {
    const comparisonFixture = comparisonBySource.get(fixture.source);
    if (!comparisonFixture) {
      throw new Error(`${fixture.source}: implemented generated Preview lacks comparison evidence`);
    }
    const [source, request] = await Promise.all([
      readFile(resolve(fixtureRoot, fixture.source), 'utf8'),
      readJson(resolve(fixtureRoot, fixture.request)),
    ]);
    const input = {
      source,
      path: `res/layout/${fixture.source}`,
      mode: 'render',
      previewBindings: request.bindings,
      limits: {
        maxSourceBytes: 4 * 1024 * 1024,
        timeoutMs: contract.limits.timeoutMs,
        maxOutputBytes: contract.limits.maxProcessOutputBytes,
      },
    };
    const first = await convert({...input, requestId: `xml-${fixture.expectedFunction}-render`});
    const firstPreview = assertRendered(first, fixture, comparisonFixture);
    await inspect(firstPreview, fixture);
    rendered += 1;

    const second = await convert({...input, requestId: `xml-${fixture.expectedFunction}-cache`});
    assertRendered(second, fixture, comparisonFixture, 'hit');
    cacheHits += 1;
    fingerprints.push({
      source: fixture.source,
      request: fixture.expectedRequestFingerprint,
      render: fixture.expectedOutputFingerprint,
      comparison: comparisonFixture.expectedComparisonFingerprint,
      png: fixture.expectedImage.sha256,
      tree: fixture.expectedRenderTree.sha256,
    });
  }

  for (const fixture of contract.unsupportedFixtures) {
    const request = await readJson(resolve(fixtureRoot, fixture.request));
    let result;
    if (fixture.schemaValid === false) {
      result = await validateRequest(request);
    } else {
      const sourceName = request.bindings.some((binding) => binding.kind === 'image-source')
        ? 'profile-card.xml'
        : 'login.xml';
      result = await convert({
        source: await readFile(resolve(fixtureRoot, sourceName), 'utf8'),
        path: `res/layout/${sourceName}`,
        mode: 'render',
        previewBindings: request.bindings,
        requestId: `xml-generated-preview-blocked-${blocked}`,
      });
    }
    const codes = result.diagnostics?.map((diagnostic) => diagnostic.code) ??
      [result.diagnostic?.code].filter(Boolean);
    if (
      !['invalid', 'unsupported'].includes(result.status) ||
      fixture.diagnosticCodes.some((code) => !codes.includes(code)) ||
      result.evidence?.level === 'rendered'
    ) {
      throw new Error(`${fixture.request}: generated Preview isolation denominator changed`);
    }
    blocked += 1;
  }

  if (
    rendered !== implementedFixtures.length ||
    cacheHits !== implementedFixtures.length ||
    blocked !== contract.unsupportedFixtures.length
  ) {
    throw new Error('Phase 4 generated Preview metrics did not reach their frozen thresholds');
  }
  return {
    rendered,
    cacheHits,
    supported: implementedFixtures.length,
    blocked,
    unsupported: contract.unsupportedFixtures.length,
    fingerprints,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase4GeneratedPreview()
    .then((summary) => {
      console.log(
        `Verified Phase 4 generated Preview: ${summary.rendered}/${summary.supported} exact renders, ` +
          `${summary.cacheHits}/${summary.supported} stable cache hits, and ` +
          `${summary.blocked}/${summary.unsupported} fail-closed unsafe or unsupported requests. ` +
          `Fingerprints: ${summary.fingerprints.map((item) =>
            `${item.source}=${item.request}/${item.render}/${item.comparison}/${item.png}/${item.tree}`).join(', ')}.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
