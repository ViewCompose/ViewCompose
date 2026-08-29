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
        ...(binding.asset === undefined ? {} : {asset: binding.asset}),
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
  imports.push('com.viewcompose.ui.foundation.UiTreeBuilder');
  const arguments_ = request.bindings.map((binding) => {
    let expression;
    if (binding.kind === 'string') {
      expression = kotlinString(binding.value);
    } else if (binding.kind === 'text-field-state') {
      expression = binding.initialText.length === 0
        ? 'TextFieldState()'
        : `TextFieldState().apply { setTextAndPlaceCursorAtEnd(${kotlinString(binding.initialText)}) }`;
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
      return planIssue(
        'VC-AI-PREVIEW-BINDING-TYPE-UNSUPPORTED',
        'Embedded ImageSource assets are contract-frozen but not yet staged by this implementation.',
        'Finish at compiled evidence until the tool-owned resource staging gate is implemented.',
        'unsupported',
      );
    }
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
  try {
    await writeFile(path, content, {encoding: 'utf8', flag: 'wx'});
  } catch (error) {
    if (error?.code !== 'EEXIST') throw error;
    if (await readFile(path, 'utf8') !== content) throw new Error('CACHE_INPUT_MISMATCH');
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
  const target = Object.freeze({
    modulePath: ':tools:ai-preview-harness',
    projectDirectory: 'tools/ai-preview-harness',
    buildVariant: 'debug',
    discoveryTask: ':tools:ai-preview-harness:discoverDebugViewComposePreviews',
    renderTask: ':tools:ai-preview-harness:renderDebugViewComposePreview',
    displayName: GENERATED_PREVIEW_FUNCTION,
    ownerClassName: 'generated.viewcompose.GeneratedPreviewKt',
    methodName: GENERATED_PREVIEW_FUNCTION,
    capabilityIds: Object.freeze(['foundation.components', 'modifier.layout']),
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
    capabilityIds: ['foundation.components', 'modifier.layout'],
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
      },
    },
  };
}
