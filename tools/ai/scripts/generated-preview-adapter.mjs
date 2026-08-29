import {createHash} from 'node:crypto';
import {lstat, mkdir, readFile, readdir, writeFile} from 'node:fs/promises';
import {relative, resolve, sep} from 'node:path';
import {
  PREVIEW_COMPILER_LANE,
  RENDER_LANE,
  renderPreview,
} from './preview-adapter.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {
  diagnostic,
  loadKnowledgeManifest,
  repositoryRoot,
  toolResult,
  utf8Bytes,
} from './tool-core.mjs';

const schemaPath = new URL('../contracts/generated-preview-request.schema.json', import.meta.url);
const GENERATED_TARGET_ID = 'tools.ai.GeneratedXmlPreview';
const GENERATED_PREVIEW_FUNCTION = 'GeneratedXmlPreview';
const MAX_GENERATED_KOTLIN_BYTES = 1024 * 1024;
const MAX_WRAPPER_BYTES = 256 * 1024;
const MAX_BINDINGS = 64;
const MAX_BINDING_TEXT_BYTES = 64 * 1024;
const MAX_ASSETS = 16;
const MAX_ASSET_BYTES = 512 * 1024;
const MAX_TOTAL_ASSET_BYTES = 1024 * 1024;
const MAX_ASSET_WIDTH_PX = 1024;
const MAX_ASSET_HEIGHT_PX = 1024;
const MAX_PNG_CHUNKS = 256;
const SHA256 = /^[a-f0-9]{64}$/u;
const bindingKindByType = Object.freeze({
  String: 'string',
  TextFieldState: 'text-field-state',
  ImageSource: 'image-source',
});

export const GENERATED_PREVIEW_CONFIGURATION = Object.freeze({
  widthDp: 411,
  heightDp: -1,
  density: 2.625,
  fontScale: 1,
  localeTag: 'en-US',
  layoutDirection: 'Ltr',
  theme: 'Light',
  apiLevel: null,
});

let schemaPromise;
let renderTail = Promise.resolve();

function loadSchema() {
  schemaPromise ??= readFile(schemaPath, 'utf8').then(JSON.parse);
  return schemaPromise;
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function planIssue(code, message, nextAction, status = 'invalid') {
  return {
    status,
    diagnostic: diagnostic({code, severity: 'error', message, nextAction}),
  };
}

function kotlinString(value) {
  return JSON.stringify(value).replaceAll('$', '\\$');
}

function canonicalBinding(binding) {
  switch (binding?.kind) {
    case 'string':
      return {
        kind: 'string',
        parameter: binding.parameter,
        source: binding.source,
        value: binding.value,
      };
    case 'text-field-state':
      return {
        kind: 'text-field-state',
        parameter: binding.parameter,
        source: binding.source,
        initialText: binding.initialText,
      };
    case 'image-source':
      return {
        kind: 'image-source',
        parameter: binding.parameter,
        source: binding.source,
        ...(binding.asset === undefined ? {} : {
          asset: {
            mediaType: binding.asset.mediaType,
            encoding: binding.asset.encoding,
            data: binding.asset.data,
            bytes: binding.asset.bytes,
            sha256: binding.asset.sha256,
            widthPx: binding.asset.widthPx,
            heightPx: binding.asset.heightPx,
          },
        }),
      };
    default:
      return binding;
  }
}

function reportBindings(report) {
  const resources = Array.isArray(report?.bindings?.resources) ? report.bindings.resources : [];
  const states = Array.isArray(report?.bindings?.states) ? report.bindings.states : [];
  return [...resources, ...states].map((binding) => ({
    parameter: binding.parameter,
    source: binding.source,
    type: binding.type,
  }));
}

function hasDuplicates(values) {
  return new Set(values).size !== values.length;
}

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit += 1) {
      crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function decodeEmbeddedPng(asset) {
  const bytes = Buffer.from(asset.data, 'base64');
  if (
    bytes.toString('base64') !== asset.data ||
    bytes.length !== asset.bytes ||
    bytes.length > MAX_ASSET_BYTES ||
    sha256(bytes) !== asset.sha256 ||
    bytes.length < 33 ||
    bytes.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a'
  ) {
    return planIssue(
      'VC-AI-PREVIEW-ASSET-INTEGRITY-INVALID',
      'Embedded Preview PNG bytes do not match their canonical encoding, size, hash, or signature.',
      'Provide exact canonical PNG bytes and matching immutable identity fields.',
    );
  }
  let cursor = 8;
  let chunks = 0;
  let ihdr = 0;
  let iend = 0;
  while (cursor < bytes.length) {
    if (cursor + 12 > bytes.length) {
      return planIssue(
        'VC-AI-PREVIEW-ASSET-INTEGRITY-INVALID',
        'Embedded Preview PNG has a truncated chunk header.',
        'Provide one complete bounded PNG.',
      );
    }
    const length = bytes.readUInt32BE(cursor);
    const typeStart = cursor + 4;
    const dataStart = typeStart + 4;
    const dataEnd = dataStart + length;
    const crcEnd = dataEnd + 4;
    if (crcEnd > bytes.length) {
      return planIssue(
        'VC-AI-PREVIEW-ASSET-INTEGRITY-INVALID',
        'Embedded Preview PNG chunk exceeds the declared bytes.',
        'Provide one complete bounded PNG.',
      );
    }
    chunks += 1;
    if (chunks > MAX_PNG_CHUNKS) {
      return planIssue(
        'VC-AI-PREVIEW-ASSET-LIMIT',
        `Embedded Preview PNG exceeds the ${MAX_PNG_CHUNKS}-chunk limit.`,
        'Use a simpler bounded PNG.',
        'limited',
      );
    }
    const type = bytes.subarray(typeStart, dataStart).toString('ascii');
    if (crc32(bytes.subarray(typeStart, dataEnd)) !== bytes.readUInt32BE(dataEnd)) {
      return planIssue(
        'VC-AI-PREVIEW-ASSET-INTEGRITY-INVALID',
        `Embedded Preview PNG chunk ${type} has an invalid CRC.`,
        'Provide exact unmodified PNG bytes.',
      );
    }
    if (type === 'IHDR') {
      ihdr += 1;
      if (
        chunks !== 1 || length !== 13 ||
        bytes.readUInt32BE(dataStart) !== asset.widthPx ||
        bytes.readUInt32BE(dataStart + 4) !== asset.heightPx ||
        asset.widthPx > MAX_ASSET_WIDTH_PX ||
        asset.heightPx > MAX_ASSET_HEIGHT_PX
      ) {
        return planIssue(
          'VC-AI-PREVIEW-ASSET-INTEGRITY-INVALID',
          'Embedded Preview PNG IHDR does not match the exact bounded dimensions.',
          'Provide matching PNG width and height identity.',
        );
      }
    }
    if (type === 'IEND') {
      iend += 1;
      if (length !== 0 || crcEnd !== bytes.length) {
        return planIssue(
          'VC-AI-PREVIEW-ASSET-INTEGRITY-INVALID',
          'Embedded Preview PNG IEND does not terminate the exact bytes.',
          'Remove trailing or malformed PNG data.',
        );
      }
    }
    cursor = crcEnd;
  }
  if (ihdr !== 1 || iend !== 1) {
    return planIssue(
      'VC-AI-PREVIEW-ASSET-INTEGRITY-INVALID',
      'Embedded Preview PNG requires exactly one leading IHDR and terminal IEND.',
      'Provide one structurally complete PNG.',
    );
  }
  return {
    status: 'success',
    bytes,
    resourceName: `vc_ai_${asset.sha256}`,
  };
}

export async function validateGeneratedPreviewRequest(request) {
  const forbidden = [
    'gradleTask',
    'dependency',
    'outputPath',
    'projectPath',
    'buildScript',
  ].filter((field) => Object.hasOwn(request ?? {}, field));
  if (forbidden.length > 0) {
    return planIssue(
      'VC-AI-PREVIEW-BUILD-SELECTION-DENIED',
      `Generated Preview requests cannot select build execution fields: ${forbidden.join(', ')}.`,
      'Remove caller-selected build fields and use the fixed generated-Preview harness.',
    );
  }
  const violations = validateSchemaValue(request, await loadSchema());
  if (violations.length > 0) {
    return planIssue(
      'VC-AI-PREVIEW-BINDING-VALUE-INVALID',
      `Generated Preview request violates schema v1: ${violations.slice(0, 3).join('; ')}`,
      'Use exact bounded generated-source, binding, configuration, and lane fields.',
    );
  }
  return {status: 'success'};
}

export function generatePreviewWrapper(request) {
  const imports = [
    'com.viewcompose.preview.tooling.PreviewLayoutDirection',
    'com.viewcompose.preview.tooling.PreviewTheme',
    'com.viewcompose.preview.tooling.ViewComposePreview',
  ];
  if (request.bindings.some((binding) => binding.kind === 'text-field-state')) {
    imports.push('com.viewcompose.text.TextFieldState');
  }
  if (request.bindings.some((binding) => binding.kind === 'image-source')) {
    imports.push('com.viewcompose.ai.preview.harness.R');
    imports.push('com.viewcompose.ui.node.ImageSource');
  }
  imports.push('com.viewcompose.ui.foundation.UiTreeBuilder');
  const arguments_ = request.bindings.map((binding) => {
    let expression;
    if (binding.kind === 'string') {
      expression = kotlinString(binding.value);
    } else if (binding.kind === 'text-field-state') {
      expression = binding.initialText.length === 0
        ? 'TextFieldState()'
        : `TextFieldState().apply { setTextAndPlaceCursorAtEnd(${kotlinString(binding.initialText)}) }`;
    } else if (binding.kind === 'image-source') {
      expression = `ImageSource.Resource(R.drawable.vc_ai_${binding.asset.sha256})`;
    } else {
      throw new Error(`Unsupported generated Preview binding kind: ${binding.kind}`);
    }
    return `        ${binding.parameter} = ${expression},`;
  });
  const configuration = request.configuration;
  return [
    'package generated.viewcompose',
    '',
    ...imports.sort().map((name) => `import ${name}`),
    '',
    '@ViewComposePreview(',
    `    name = "Generated XML · ${request.generatedSource.functionName}",`,
    '    group = "AI/XML",',
    `    widthDp = ${configuration.widthDp},`,
    `    heightDp = ${configuration.heightDp},`,
    `    density = ${configuration.density}f,`,
    `    fontScale = ${configuration.fontScale.toFixed(1)}f,`,
    `    localeTag = ${kotlinString(configuration.localeTag)},`,
    `    layoutDirection = PreviewLayoutDirection.${configuration.layoutDirection},`,
    `    theme = PreviewTheme.${configuration.theme},`,
    `    apiLevel = ${configuration.apiLevel ?? -1},`,
    ')',
    `fun UiTreeBuilder.${GENERATED_PREVIEW_FUNCTION}() {`,
    `    ${request.generatedSource.functionName}(`,
    ...arguments_,
    '    )',
    '}',
    '',
  ].join('\n');
}

export async function createGeneratedPreviewPlan({
  generatedKotlin,
  generationReport,
  previewBindings,
} = {}) {
  if (
    typeof generatedKotlin !== 'string' ||
    generatedKotlin.length === 0 ||
    utf8Bytes(generatedKotlin) > MAX_GENERATED_KOTLIN_BYTES ||
    generationReport?.target?.packageName !== 'generated.viewcompose' ||
    generationReport?.target?.language !== 'kotlin' ||
    !/^[A-Z][A-Za-z0-9]{0,127}$/u.test(generationReport?.target?.functionName ?? '') ||
    JSON.stringify(generationReport?.target?.artifactIds) !==
      JSON.stringify(['viewcompose-ui-foundation']) ||
    !generatedKotlin.includes(
      `fun UiTreeBuilder.${generationReport?.target?.functionName ?? '<missing>'}(`,
    )
  ) {
    return planIssue(
      'VC-AI-PREVIEW-GENERATED-SOURCE-MISMATCH',
      'Generated Preview accepts only bounded Kotlin and the exact report from the same XML generator.',
      'Regenerate the XML migration and pass its unchanged Kotlin and report to render mode.',
      'unsupported',
    );
  }
  if (!Array.isArray(previewBindings) || previewBindings.length > MAX_BINDINGS) {
    return planIssue(
      'VC-AI-PREVIEW-BINDING-VALUE-INVALID',
      `Generated Preview bindings must be an array with at most ${MAX_BINDINGS} entries.`,
      'Provide one bounded explicit value for every generated function parameter.',
    );
  }
  const manifest = await loadKnowledgeManifest();
  const declaredBindings = reportBindings(generationReport);
  const bindings = previewBindings.map(canonicalBinding);
  const request = {
    schemaVersion: 1,
    framework: {
      versionLane: 'current-source',
      identity: manifest.framework.identity,
      bundleFingerprint: manifest.bundleFingerprint,
    },
    generatedSource: {
      packageName: 'generated.viewcompose',
      functionName: generationReport.target.functionName,
      kotlinFingerprint: sha256(generatedKotlin),
      artifactIds: [...generationReport.target.artifactIds],
      declaredBindings,
    },
    bindings,
    configuration: {...GENERATED_PREVIEW_CONFIGURATION},
    lanes: {
      compiler: PREVIEW_COMPILER_LANE,
      render: RENDER_LANE,
    },
  };
  const schemaResult = await validateGeneratedPreviewRequest(request);
  if (schemaResult.status !== 'success') return schemaResult;
  if (
    hasDuplicates(declaredBindings.map((binding) => binding.parameter)) ||
    hasDuplicates(bindings.map((binding) => binding.parameter))
  ) {
    return planIssue(
      'VC-AI-PREVIEW-BINDING-DUPLICATE',
      'Generated Preview parameters and supplied bindings must each be unique.',
      'Remove duplicate binding parameters and regenerate if the generator report is duplicated.',
      'unsupported',
    );
  }
  const suppliedByParameter = new Map(bindings.map((binding) => [binding.parameter, binding]));
  const declaredByParameter = new Map(
    declaredBindings.map((binding) => [binding.parameter, binding]),
  );
  const missing = declaredBindings.filter((binding) => !suppliedByParameter.has(binding.parameter));
  if (missing.length > 0) {
    return planIssue(
      'VC-AI-PREVIEW-BINDING-MISSING',
      `Generated Preview is missing bindings for: ${missing.map((binding) => binding.parameter).join(', ')}.`,
      'Provide explicit values for every generator-reported parameter.',
      'unsupported',
    );
  }
  const extra = bindings.filter((binding) => !declaredByParameter.has(binding.parameter));
  if (extra.length > 0) {
    return planIssue(
      'VC-AI-PREVIEW-BINDING-EXTRA',
      `Generated Preview received undeclared bindings for: ${extra.map((binding) => binding.parameter).join(', ')}.`,
      'Remove values that are not present in the exact generator report.',
      'unsupported',
    );
  }
  const assetsBySha = new Map();
  for (let index = 0; index < declaredBindings.length; index += 1) {
    const declared = declaredBindings[index];
    const supplied = bindings[index];
    if (
      supplied.parameter !== declared.parameter ||
      supplied.source !== declared.source ||
      supplied.kind !== bindingKindByType[declared.type]
    ) {
      return planIssue(
        'VC-AI-PREVIEW-GENERATED-SOURCE-MISMATCH',
        `Binding ${declared.parameter} does not match the generator's ordered source and type.`,
        'Use the exact binding order, parameter, source identity, and type from the migration report.',
        'unsupported',
      );
    }
    if (declared.type === 'ImageSource') {
      if (supplied.asset === undefined) {
        return planIssue(
          'VC-AI-PREVIEW-ASSET-MISSING',
          `ImageSource binding ${declared.parameter} has no explicit embedded Preview asset.`,
          'Provide exact bounded PNG bytes; paths, URLs, project resources, and invented IDs remain forbidden.',
          'unsupported',
        );
      }
      const decoded = decodeEmbeddedPng(supplied.asset);
      if (decoded.status !== 'success') return decoded;
      const existing = assetsBySha.get(supplied.asset.sha256);
      if (existing && !existing.bytes.equals(decoded.bytes)) {
        return planIssue(
          'VC-AI-PREVIEW-ASSET-INTEGRITY-INVALID',
          'Two embedded Preview assets claim one SHA-256 but have different bytes.',
          'Provide one exact byte sequence for each asset identity.',
        );
      }
      assetsBySha.set(supplied.asset.sha256, {
        sha256: supplied.asset.sha256,
        resourceName: decoded.resourceName,
        widthPx: supplied.asset.widthPx,
        heightPx: supplied.asset.heightPx,
        bytes: decoded.bytes,
      });
    }
  }
  const assets = [...assetsBySha.values()].sort((left, right) =>
    left.sha256.localeCompare(right.sha256));
  const totalAssetBytes = assets.reduce((total, asset) => total + asset.bytes.length, 0);
  if (assets.length > MAX_ASSETS || totalAssetBytes > MAX_TOTAL_ASSET_BYTES) {
    return planIssue(
      'VC-AI-PREVIEW-ASSET-LIMIT',
      `Generated Preview assets exceed ${MAX_ASSETS} unique files or ${MAX_TOTAL_ASSET_BYTES} total bytes.`,
      'Use fewer or smaller exact embedded PNG assets.',
      'limited',
    );
  }
  const bindingTextBytes = bindings.reduce((total, binding) => total + utf8Bytes(
    binding.kind === 'string' ? binding.value : binding.initialText ?? '',
  ), 0);
  if (bindingTextBytes > MAX_BINDING_TEXT_BYTES) {
    return planIssue(
      'VC-AI-PREVIEW-BINDING-VALUE-INVALID',
      `Generated Preview binding text exceeds the ${MAX_BINDING_TEXT_BYTES}-byte limit.`,
      'Use smaller deterministic Preview strings and state values.',
      'limited',
    );
  }
  const wrapper = generatePreviewWrapper(request);
  if (utf8Bytes(wrapper) > MAX_WRAPPER_BYTES) {
    return planIssue(
      'VC-AI-PREVIEW-WRAPPER-INVALID',
      'The deterministic generated Preview wrapper exceeds its fixed byte limit.',
      'Reduce the supported binding set before rendering.',
      'limited',
    );
  }
  return {
    status: 'success',
    request,
    requestFingerprint: sha256(JSON.stringify(request)),
    generatedKotlinFingerprint: request.generatedSource.kotlinFingerprint,
    wrapper,
    wrapperFingerprint: sha256(wrapper),
    assets,
  };
}

function isWithin(parent, child) {
  const path = relative(parent, child);
  return path === '' || (!path.startsWith(`..${sep}`) && path !== '..' && !path.startsWith(sep));
}

async function ensureContainedDirectory(repository, directory) {
  const root = resolve(repository);
  const target = resolve(directory);
  if (!isWithin(root, target)) throw new Error('CACHE_PATH_ESCAPE');
  const rootMetadata = await lstat(root);
  if (!rootMetadata.isDirectory() || rootMetadata.isSymbolicLink()) {
    throw new Error('CACHE_ROOT_UNSAFE');
  }
  let current = root;
  for (const segment of relative(root, target).split(sep).filter(Boolean)) {
    current = resolve(current, segment);
    let metadata = await lstat(current).catch((error) => {
      if (error?.code === 'ENOENT') return null;
      throw error;
    });
    if (!metadata) {
      await mkdir(current);
      metadata = await lstat(current);
    }
    if (!metadata.isDirectory() || metadata.isSymbolicLink()) {
      throw new Error('CACHE_PATH_UNSAFE');
    }
  }
}

async function persistImmutableInput(path, content) {
  const metadata = await lstat(path).catch((error) => {
    if (error?.code === 'ENOENT') return null;
    throw error;
  });
  if (metadata?.isSymbolicLink() || (metadata && !metadata.isFile())) {
    throw new Error('CACHE_INPUT_UNSAFE');
  }
  const expected = Buffer.isBuffer(content) ? content : Buffer.from(content, 'utf8');
  try {
    await writeFile(path, expected, {flag: 'wx'});
  } catch (error) {
    if (error?.code !== 'EEXIST') throw error;
    if (!(await readFile(path)).equals(expected)) throw new Error('CACHE_INPUT_MISMATCH');
  }
}

async function stagePlan(plan, repository, cacheRoot) {
  const requestRoot = resolve(cacheRoot, plan.requestFingerprint);
  const inputDirectory = resolve(requestRoot, 'input');
  await ensureContainedDirectory(repository, inputDirectory);
  await persistImmutableInput(resolve(inputDirectory, 'GeneratedView.kt'), plan.generatedKotlin);
  await persistImmutableInput(resolve(inputDirectory, 'GeneratedPreview.kt'), plan.wrapper);
  const children = (await readdir(inputDirectory)).sort();
  if (JSON.stringify(children) !== JSON.stringify(['GeneratedPreview.kt', 'GeneratedView.kt'])) {
    throw new Error('CACHE_INPUT_UNSAFE');
  }
  if (plan.assets.length > 0) {
    const resourceRoot = resolve(requestRoot, 'res');
    const drawableDirectory = resolve(resourceRoot, 'drawable');
    await ensureContainedDirectory(repository, drawableDirectory);
    for (const asset of plan.assets) {
      await persistImmutableInput(
        resolve(drawableDirectory, `${asset.resourceName}.png`),
        asset.bytes,
      );
    }
    const assetChildren = (await readdir(drawableDirectory)).sort();
    const expectedAssets = plan.assets.map((asset) => `${asset.resourceName}.png`).sort();
    if (
      JSON.stringify(assetChildren) !== JSON.stringify(expectedAssets) ||
      JSON.stringify((await readdir(resourceRoot)).sort()) !== JSON.stringify(['drawable'])
    ) {
      throw new Error('CACHE_INPUT_UNSAFE');
    }
  }
  const requestChildren = (await readdir(requestRoot)).sort();
  const expectedRequestChildren = plan.assets.length > 0 ? ['input', 'res'] : ['input'];
  if (JSON.stringify(requestChildren) !== JSON.stringify(expectedRequestChildren)) {
    throw new Error('CACHE_INPUT_UNSAFE');
  }
}

async function serialized(action) {
  const previous = renderTail;
  let release;
  renderTail = new Promise((resolvePromise) => { release = resolvePromise; });
  await previous;
  try {
    return await action();
  } finally {
    release();
  }
}

async function generatedPreviewFailure(requestId, plan) {
  return toolResult({
    requestId,
    tool: 'render_preview',
    status: plan.status,
    level: 'static',
    diagnostics: [plan.diagnostic],
    compilerLane: PREVIEW_COMPILER_LANE,
    renderLane: RENDER_LANE,
    truncated: plan.status === 'limited',
  });
}

export async function renderGeneratedPreview({
  generatedKotlin,
  generationReport,
  previewBindings,
  requestId = 'render-generated-preview',
  limits,
  signal,
} = {}, {
  render = renderPreview,
  repository = repositoryRoot(),
  cacheRoot = resolve(repository, 'build/ai/preview/requests'),
} = {}) {
  const plan = await createGeneratedPreviewPlan({
    generatedKotlin,
    generationReport,
    previewBindings,
  });
  if (plan.status !== 'success') return generatedPreviewFailure(requestId, plan);
  plan.generatedKotlin = generatedKotlin;
  try {
    await stagePlan(plan, repository, cacheRoot);
  } catch {
    return toolResult({
      requestId,
      tool: 'render_preview',
      status: 'failed',
      level: 'static',
      diagnostics: [diagnostic({
        code: 'VC-AI-RENDER-CACHE-POISONED',
        severity: 'error',
        message: 'The content-addressed generated Preview input failed containment or integrity checks.',
        nextAction: 'Remove the tool-owned generated Preview request cache and render again.',
      })],
      compilerLane: PREVIEW_COMPILER_LANE,
      renderLane: RENDER_LANE,
    });
  }
  const capabilityIds = plan.assets.length > 0
    ? ['foundation.components', 'image.foundation', 'modifier.drawing', 'modifier.layout']
    : ['foundation.components', 'modifier.layout'];
  const target = Object.freeze({
    modulePath: ':tools:ai-preview-harness',
    projectDirectory: 'tools/ai-preview-harness',
    buildVariant: 'debug',
    discoveryTask: ':tools:ai-preview-harness:discoverDebugViewComposePreviews',
    renderTask: ':tools:ai-preview-harness:renderDebugViewComposePreview',
    displayName: GENERATED_PREVIEW_FUNCTION,
    ownerClassName: 'generated.viewcompose.GeneratedPreviewKt',
    methodName: GENERATED_PREVIEW_FUNCTION,
    capabilityIds: Object.freeze(capabilityIds),
    configuration: Object.freeze({
      widthDp: GENERATED_PREVIEW_CONFIGURATION.widthDp,
      heightDp: GENERATED_PREVIEW_CONFIGURATION.heightDp,
      density: GENERATED_PREVIEW_CONFIGURATION.density,
      fontScale: GENERATED_PREVIEW_CONFIGURATION.fontScale,
      localeTags: Object.freeze([GENERATED_PREVIEW_CONFIGURATION.localeTag]),
      layoutDirection: GENERATED_PREVIEW_CONFIGURATION.layoutDirection,
      theme: GENERATED_PREVIEW_CONFIGURATION.theme,
    }),
    gradleArguments: Object.freeze([
      `-PviewComposeAiPreviewRequestKey=${plan.requestFingerprint}`,
    ]),
  });
  const rendered = await serialized(() => render({
    targetId: GENERATED_TARGET_ID,
    capabilityIds,
    requestId,
    limits,
    signal,
  }, {
    repository,
    targets: {[GENERATED_TARGET_ID]: target},
  }));
  if (rendered.status !== 'success') return rendered;
  if (
    rendered.evidence?.level !== 'rendered' ||
    rendered.evidence?.compilerLane !== PREVIEW_COMPILER_LANE ||
    rendered.evidence?.renderLane !== RENDER_LANE ||
    !SHA256.test(rendered.evidence?.outputFingerprint ?? '') ||
    !SHA256.test(rendered.data?.image?.sha256 ?? '') ||
    !SHA256.test(rendered.data?.renderTree?.sha256 ?? '')
  ) {
    return toolResult({
      requestId,
      tool: 'render_preview',
      status: 'failed',
      level: 'compiled',
      diagnostics: [diagnostic({
        code: 'VC-AI-PREVIEW-OUTPUT-INVALID',
        severity: 'error',
        message: 'Generated Preview did not return complete pinned rendered evidence.',
        nextAction: 'Reject the result and repair the fixed generated-Preview output contract.',
      })],
      compilerLane: PREVIEW_COMPILER_LANE,
      renderLane: RENDER_LANE,
    });
  }
  return {
    ...rendered,
    data: {
      ...rendered.data,
      generatedPreview: {
        requestFingerprint: plan.requestFingerprint,
        generatedKotlinFingerprint: plan.generatedKotlinFingerprint,
        wrapperFingerprint: plan.wrapperFingerprint,
        pngSha256: rendered.data.image.sha256,
        renderTreeSha256: rendered.data.renderTree.sha256,
        assets: plan.assets.map((asset) => ({
          resourceName: asset.resourceName,
          bytes: asset.bytes.length,
          sha256: asset.sha256,
          widthPx: asset.widthPx,
          heightPx: asset.heightPx,
        })),
      },
    },
  };
}
