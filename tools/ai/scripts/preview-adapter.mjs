import {createHash} from 'node:crypto';
import {lstat, readFile} from 'node:fs/promises';
import {dirname, isAbsolute, relative, resolve, sep} from 'node:path';
import {executeBoundedProcess} from './bounded-process.mjs';
import {
  detectJavaFeature,
  diagnostic,
  repositoryRoot,
  toolResult,
} from './tool-core.mjs';

export const PREVIEW_COMPILER_LANE =
  'current-source/jdk-21/agp-9.1.1/kotlin-2.2.10/android-37/jvm-11';
export const RENDER_LANE =
  'current-source/preview-protocol-1/paparazzi-2.0.0-alpha05/layoutlib-16.2.1';
export const DEFAULT_PREVIEW_LIMITS = Object.freeze({
  timeoutMs: 120_000,
  maxOutputBytes: 1024 * 1024,
});
const HARD_PREVIEW_LIMITS = Object.freeze({
  timeoutMs: 120_000,
  maxOutputBytes: 2 * 1024 * 1024,
});
const MAX_CATALOG_BYTES = 2 * 1024 * 1024;
const MAX_RESPONSE_BYTES = 1024 * 1024;
const MAX_IMAGE_BYTES = 16 * 1024 * 1024;
const MAX_RENDER_TREE_BYTES = 8 * 1024 * 1024;
const SHA256 = /^[a-f0-9]{64}$/u;
const PREVIEW_ID = /^[a-z0-9]+(?:(?:-|__)[a-z0-9]+)*$/u;

export const SUPPORTED_PREVIEW_TARGETS = Object.freeze({
  'samples.counter.CounterPreview': Object.freeze({
    modulePath: ':samples:counter',
    projectDirectory: 'samples/counter',
    buildVariant: 'debug',
    discoveryTask: ':samples:counter:discoverDebugViewComposePreviews',
    renderTask: ':samples:counter:renderDebugViewComposePreview',
    displayName: 'CounterPreview',
    ownerClassName: 'com.viewcompose.samples.counter.CounterPreviewKt',
    methodName: 'CounterPreview',
    capabilityIds: Object.freeze(['preview.runner']),
    configuration: Object.freeze({
      widthDp: 411,
      heightDp: -1,
      density: 2.625,
      fontScale: 1,
      localeTags: Object.freeze(['en-US']),
      layoutDirection: 'Ltr',
      theme: 'Light',
    }),
  }),
});

function normalizeLimits(requested) {
  if (requested !== undefined && (requested === null || typeof requested !== 'object' || Array.isArray(requested))) {
    return null;
  }
  const limits = {...DEFAULT_PREVIEW_LIMITS};
  for (const [name, hardMaximum] of Object.entries(HARD_PREVIEW_LIMITS)) {
    if (requested?.[name] === undefined) continue;
    const value = requested[name];
    if (!Number.isInteger(value) || value <= 0 || value > hardMaximum) return null;
    limits[name] = value;
  }
  if (requested && Object.keys(requested).some((name) => !(name in HARD_PREVIEW_LIMITS))) return null;
  return limits;
}

function normalizeConfiguration(requested, defaults) {
  if (requested !== undefined && (requested === null || typeof requested !== 'object' || Array.isArray(requested))) {
    return null;
  }
  const known = new Set(Object.keys(defaults));
  if (requested && Object.keys(requested).some((name) => !known.has(name))) return null;
  const configuration = {...defaults, ...requested};
  if (!['Light', 'Dark'].includes(configuration.theme)) return null;
  if (!['Ltr', 'Rtl'].includes(configuration.layoutDirection)) return null;
  if (!Number.isInteger(configuration.widthDp) || configuration.widthDp <= 0 || configuration.widthDp > 2000) return null;
  if (!Number.isInteger(configuration.heightDp) || configuration.heightDp < -1 || configuration.heightDp > 4000) return null;
  if (typeof configuration.density !== 'number' || configuration.density < 0.5 || configuration.density > 8) return null;
  if (typeof configuration.fontScale !== 'number' || configuration.fontScale < 0.5 || configuration.fontScale > 3) return null;
  if (
    !Array.isArray(configuration.localeTags) ||
    configuration.localeTags.length === 0 ||
    configuration.localeTags.length > 4 ||
    configuration.localeTags.some((tag) => typeof tag !== 'string' || !/^[A-Za-z0-9-]{2,35}$/u.test(tag))
  ) return null;
  return configuration;
}

function configurationsEqual(first, second) {
  return first.widthDp === second.widthDp &&
    first.heightDp === second.heightDp &&
    first.density === second.density &&
    first.fontScale === second.fontScale &&
    first.layoutDirection === second.layoutDirection &&
    first.theme === second.theme &&
    Array.isArray(first.localeTags) &&
    first.localeTags.length === second.localeTags.length &&
    first.localeTags.every((tag, index) => tag === second.localeTags[index]);
}

function isWithin(parent, child) {
  const path = relative(parent, child);
  return path === '' || (!path.startsWith(`..${sep}`) && path !== '..' && !path.startsWith(sep));
}

async function readBoundedRegularFile(path, maximumBytes, containmentRoot) {
  if (!isAbsolute(path) || !isWithin(containmentRoot, path)) throw new Error('PATH_ESCAPE');
  const rootMetadata = await lstat(containmentRoot);
  if (!rootMetadata.isDirectory() || rootMetadata.isSymbolicLink()) throw new Error('ROOT_UNSAFE');
  const pathFromRoot = relative(containmentRoot, path);
  let current = containmentRoot;
  for (const segment of pathFromRoot.split(sep).filter(Boolean)) {
    current = resolve(current, segment);
    const metadata = await lstat(current);
    if (metadata.isSymbolicLink()) throw new Error('SYMLINK');
  }
  const metadata = await lstat(path);
  if (!metadata.isFile()) throw new Error('NOT_FILE');
  if (metadata.size > maximumBytes) throw new Error('FILE_LIMIT');
  return {buffer: await readFile(path), bytes: metadata.size};
}

async function readJsonFile(path, maximumBytes, containmentRoot) {
  const file = await readBoundedRegularFile(path, maximumBytes, containmentRoot);
  return {value: JSON.parse(file.buffer.toString('utf8')), bytes: file.bytes};
}

function sha256(buffer) {
  return createHash('sha256').update(buffer).digest('hex');
}

function renderOutputFingerprint(image, tree) {
  return createHash('sha256')
    .update('preview.png\0')
    .update(image)
    .update('\nrender-tree.json\0')
    .update(tree)
    .digest('hex');
}

function pngDimensions(buffer) {
  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  if (buffer.length < 57 || !buffer.subarray(0, 8).equals(signature)) return null;
  let offset = 8;
  let width;
  let height;
  let sawImageData = false;
  let sawEnd = false;
  while (offset + 12 <= buffer.length) {
    const length = buffer.readUInt32BE(offset);
    const end = offset + 12 + length;
    if (end > buffer.length) return null;
    const type = buffer.subarray(offset + 4, offset + 8).toString('ascii');
    if (offset === 8) {
      if (type !== 'IHDR' || length !== 13) return null;
      width = buffer.readUInt32BE(offset + 8);
      height = buffer.readUInt32BE(offset + 12);
    }
    if (type === 'IDAT') sawImageData = true;
    if (type === 'IEND') {
      if (length !== 0 || end !== buffer.length) return null;
      sawEnd = true;
      break;
    }
    offset = end;
  }
  if (!sawImageData || !sawEnd || width === 0 || height === 0 || width > 20_000 || height > 20_000) return null;
  return {width, height};
}

function gradlePlan(repository, javaHome, args) {
  return {
    executable: resolve(repository, 'gradlew'),
    cwd: repository,
    args: [
      ...args,
      `-Dorg.gradle.java.home=${javaHome}`,
      '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8',
      '--offline',
      '--no-daemon',
      '--no-build-cache',
      '--no-configuration-cache',
      '--max-workers=2',
      '--console=plain',
    ],
  };
}

async function previewFailure({
  requestId,
  status = 'failed',
  level = 'compiled',
  cache = 'bypassed',
  code,
  message,
  nextAction,
  elapsedMs,
  truncated = false,
  diagnostics,
}) {
  return toolResult({
    requestId,
    tool: 'render_preview',
    status,
    level,
    cache,
    compilerLane: PREVIEW_COMPILER_LANE,
    renderLane: RENDER_LANE,
    diagnostics: diagnostics ?? [diagnostic({
      code,
      severity: 'error',
      message,
      nextAction,
    })],
    elapsedMs,
    truncated,
  });
}

function processFailure(execution, requestId, phase, started, timeoutMs) {
  if (execution.cancelled) {
    return previewFailure({
      requestId,
      status: 'cancelled',
      code: 'VC-AI-PREVIEW-CANCELLED',
      message: `Preview ${phase} was cancelled before evidence was accepted.`,
      nextAction: 'Retry the same fixed target when render evidence is still required.',
      elapsedMs: performance.now() - started,
    });
  }
  if (execution.timedOut) {
    return previewFailure({
      requestId,
      status: 'limited',
      code: 'VC-AI-PREVIEW-TIMEOUT',
      message: `Preview ${phase} exceeded the remaining ${Math.max(1, timeoutMs)} ms timeout.`,
      nextAction: 'Retry after preparing the fixed Preview lane or use a smaller supported target.',
      elapsedMs: performance.now() - started,
    });
  }
  if (execution.truncated) {
    return previewFailure({
      requestId,
      status: 'limited',
      code: 'VC-AI-PREVIEW-OUTPUT-LIMIT',
      message: `Preview ${phase} exceeded the bounded process-output limit.`,
      nextAction: 'Correct repeated build or render failures before retrying.',
      elapsedMs: performance.now() - started,
      truncated: true,
    });
  }
  if (execution.spawnError) {
    return previewFailure({
      requestId,
      code: 'VC-AI-PREVIEW-START-FAILED',
      message: `The fixed Preview ${phase} process could not be started.`,
      nextAction: 'Verify the repository wrapper and pinned JDK 21 installation.',
      elapsedMs: performance.now() - started,
    });
  }
  if (execution.exitCode !== 0) {
    return previewFailure({
      requestId,
      code: 'VC-AI-PREVIEW-BUILD-FAILED',
      message: `The fixed Preview ${phase} task failed without an accepted protocol response.`,
      nextAction: 'Run the fixed Preview preparation lane and inspect repository-owned build diagnostics.',
      elapsedMs: performance.now() - started,
    });
  }
  return null;
}

function mapPreviewDiagnostics(entries, repository) {
  if (!Array.isArray(entries)) return [];
  return entries.slice(0, 100).map((entry) => {
    let source;
    const location = entry?.sourceLocation;
    if (
      location &&
      typeof location.filePath === 'string' &&
      Number.isInteger(location.line) &&
      location.line > 0
    ) {
      const absolute = resolve(location.filePath);
      if (isWithin(repository, absolute)) {
        source = {
          path: relative(repository, absolute).replaceAll(sep, '/'),
          startLine: location.line,
          startColumn: Number.isInteger(location.column) && location.column > 0 ? location.column : 1,
        };
      }
    }
    const phase = typeof entry?.phase === 'string' ? entry.phase : 'unknown';
    return diagnostic({
      code: 'VC-AI-PREVIEW-DIAGNOSTIC',
      severity: String(entry?.severity).toLowerCase() === 'warning'
        ? 'warning'
        : String(entry?.severity).toLowerCase() === 'info' ? 'info' : 'error',
      message: `${phase}: ${String(entry?.message ?? 'Preview reported an unspecified diagnostic.')}`.slice(0, 4096),
      nextAction: 'Correct the reported Preview phase failure and render the immutable target again.',
      source,
    });
  });
}

async function validateCatalog({catalogPath, artifactRoot, repository, target, configuration}) {
  const {value: catalog} = await readJsonFile(catalogPath, MAX_CATALOG_BYTES, artifactRoot);
  if (
    catalog.protocolVersion !== 1 ||
    catalog.modulePath !== target.modulePath ||
    catalog.buildVariant !== target.buildVariant ||
    !SHA256.test(catalog.buildFingerprint ?? '') ||
    !Array.isArray(catalog.descriptors) ||
    catalog.descriptors.length > 1000
  ) throw new Error('CATALOG_INVALID');
  const catalogDiagnostics = mapPreviewDiagnostics(catalog.diagnostics, repository);
  if (catalogDiagnostics.some((entry) => entry.severity === 'error')) {
    throw new Error('CATALOG_DIAGNOSTIC');
  }
  const matches = catalog.descriptors.filter((descriptor) =>
    descriptor?.displayName === target.displayName &&
    descriptor?.entryPoint?.ownerClassName === target.ownerClassName &&
    descriptor?.entryPoint?.methodName === target.methodName,
  );
  if (matches.length !== 1 || !PREVIEW_ID.test(matches[0].id ?? '')) throw new Error('TARGET_MISSING');
  const descriptor = matches[0];
  if (
    !descriptor.sourceLocation ||
    !Number.isInteger(descriptor.sourceLocation.line) ||
    descriptor.sourceLocation.line <= 0 ||
    !Number.isInteger(descriptor.sourceLocation.column) ||
    descriptor.sourceLocation.column <= 0
  ) throw new Error('SOURCE_INVALID');
  if (!Array.isArray(descriptor.variants) || descriptor.variants.length > 100) {
    throw new Error('CATALOG_INVALID');
  }
  const variants = descriptor.variants.filter((variant) =>
    PREVIEW_ID.test(variant?.id ?? '') &&
    configurationsEqual(variant?.configuration ?? {}, configuration),
  );
  if (variants.length !== 1) throw new Error('VARIANT_UNSUPPORTED');
  if (!isAbsolute(descriptor.sourceLocation?.filePath ?? '')) throw new Error('SOURCE_ESCAPE');
  const sourcePath = resolve(descriptor.sourceLocation.filePath);
  if (!isWithin(repository, sourcePath)) throw new Error('SOURCE_ESCAPE');
  await readBoundedRegularFile(sourcePath, 4 * 1024 * 1024, repository);
  return {
    catalog,
    descriptor,
    variant: variants[0],
    sourcePath: relative(repository, sourcePath).replaceAll(sep, '/'),
    catalogDiagnostics,
  };
}

async function validateRenderArtifacts({
  responsePath,
  artifactRoot,
  repository,
  descriptor,
  variant,
}) {
  const {value: response} = await readJsonFile(responsePath, MAX_RESPONSE_BYTES, artifactRoot);
  if (
    response.protocolVersion !== 1 ||
    response.previewId !== descriptor.id ||
    response.variantId !== variant.id ||
    typeof response.requestId !== 'string' ||
    !response.requestId.endsWith(`:${descriptor.id}:${variant.id}`) ||
    !['Success', 'CompileFailure', 'RenderFailure', 'Cancelled', 'TimedOut', 'ProtocolMismatch']
      .includes(response.status)
  ) throw new Error('RESPONSE_INVALID');
  const mappedDiagnostics = mapPreviewDiagnostics(response.diagnostics, repository);
  if (response.status !== 'Success') {
    if (mappedDiagnostics.length === 0) throw new Error('RESPONSE_INVALID');
    return {response, diagnostics: mappedDiagnostics};
  }
  if (!response.artifacts || mappedDiagnostics.some((entry) => entry.severity === 'error')) {
    throw new Error('RESPONSE_INVALID');
  }
  const imagePath = resolve(response.artifacts.imagePath ?? '');
  const treePath = resolve(response.artifacts.renderTreePath ?? '');
  const expectedDirectory = dirname(responsePath);
  if (
    imagePath !== resolve(expectedDirectory, 'preview.png') ||
    treePath !== resolve(expectedDirectory, 'render-tree.json')
  ) throw new Error('ARTIFACT_PATH_INVALID');
  const image = await readBoundedRegularFile(imagePath, MAX_IMAGE_BYTES, artifactRoot);
  const tree = await readBoundedRegularFile(treePath, MAX_RENDER_TREE_BYTES, artifactRoot);
  const dimensions = pngDimensions(image.buffer);
  if (!dimensions) throw new Error('IMAGE_INVALID');
  const treeValue = JSON.parse(tree.buffer.toString('utf8'));
  if (
    treeValue === null ||
    typeof treeValue !== 'object' ||
    Array.isArray(treeValue) ||
    !Array.isArray(treeValue.tree)
  ) throw new Error('TREE_INVALID');
  return {
    response,
    diagnostics: mappedDiagnostics,
    image: {...image, path: imagePath, sha256: sha256(image.buffer), ...dimensions},
    tree: {...tree, path: treePath, sha256: sha256(tree.buffer)},
    outputFingerprint: renderOutputFingerprint(image.buffer, tree.buffer),
  };
}

function responseStatus(status) {
  switch (status) {
    case 'CompileFailure': return {status: 'invalid', level: 'static'};
    case 'Cancelled': return {status: 'cancelled', level: 'compiled'};
    case 'TimedOut': return {status: 'limited', level: 'compiled'};
    default: return {status: 'failed', level: 'compiled'};
  }
}

export async function renderPreview({
  targetId = 'samples.counter.CounterPreview',
  configuration: requestedConfiguration,
  capabilityIds = [],
  requestId = 'render-preview',
  limits: requestedLimits,
  signal,
} = {}, {
  runProcess = executeBoundedProcess,
  javaFeature = detectJavaFeature(),
  javaHome = process.env.JAVA_HOME,
  repository = repositoryRoot(),
  targets = SUPPORTED_PREVIEW_TARGETS,
} = {}) {
  const started = performance.now();
  const limits = normalizeLimits(requestedLimits);
  if (!limits) {
    return previewFailure({
      requestId,
      status: 'invalid',
      level: 'static',
      code: 'VC-AI-PREVIEW-LIMIT-INVALID',
      message: 'Preview limits must use only positive timeout/output integers within fixed caps.',
      nextAction: 'Use the documented Preview limits.',
      elapsedMs: performance.now() - started,
    });
  }
  if (!Array.isArray(capabilityIds) || capabilityIds.length > 100 || capabilityIds.some((id) =>
    typeof id !== 'string' || !/^[a-z0-9][a-z0-9.-]*$/u.test(id))) {
    return previewFailure({
      requestId,
      status: 'invalid',
      level: 'static',
      code: 'VC-AI-PREVIEW-SELECTION-INVALID',
      message: 'Preview capability selections must be a bounded stable-ID array.',
      nextAction: 'Submit generated capability IDs without paths or coordinates.',
      elapsedMs: performance.now() - started,
    });
  }
  const target = Object.hasOwn(targets, targetId) ? targets[targetId] : null;
  if (!target) {
    return previewFailure({
      requestId,
      status: 'unsupported',
      level: 'static',
      code: 'VC-AI-PREVIEW-TARGET-UNSUPPORTED',
      message: 'The requested Preview target is outside the fixed current-source allowlist.',
      nextAction: `Use one of: ${Object.keys(targets).sort().join(', ')}.`,
      elapsedMs: performance.now() - started,
    });
  }
  const configuration = normalizeConfiguration(requestedConfiguration, target.configuration);
  if (!configuration) {
    return previewFailure({
      requestId,
      status: 'invalid',
      level: 'static',
      code: 'VC-AI-PREVIEW-CONFIGURATION-INVALID',
      message: 'Preview configuration contains an unknown, malformed, or out-of-range field.',
      nextAction: 'Use the bounded theme, locale, viewport, density, font-scale, and direction fields.',
      elapsedMs: performance.now() - started,
    });
  }
  if (javaFeature !== 21 || !javaHome) {
    return previewFailure({
      requestId,
      status: 'unsupported',
      level: 'static',
      code: 'VC-AI-PREVIEW-LANE-MISMATCH',
      message: `The ${RENDER_LANE} render lane requires JAVA_HOME to resolve JDK 21.`,
      nextAction: 'Select the pinned JDK 21 lane before rendering.',
      elapsedMs: performance.now() - started,
    });
  }

  const projectDirectory = resolve(repository, target.projectDirectory);
  const artifactRoot = resolve(projectDirectory, `build/viewcompose-preview/${target.buildVariant}`);
  const catalogPath = resolve(artifactRoot, 'descriptors.json');
  const remainingTimeout = () => limits.timeoutMs - Math.round(performance.now() - started);
  const discovery = await runProcess(
    gradlePlan(repository, javaHome, [target.discoveryTask]),
    {...limits, timeoutMs: Math.max(1, remainingTimeout()), signal},
  );
  const discoveryFailure = processFailure(
    discovery,
    requestId,
    'discovery',
    started,
    remainingTimeout(),
  );
  if (discoveryFailure) return discoveryFailure;

  let selection;
  try {
    selection = await validateCatalog({
      catalogPath,
      artifactRoot,
      repository,
      target,
      configuration,
    });
  } catch (error) {
    return previewFailure({
      requestId,
      status: error.message === 'VARIANT_UNSUPPORTED' ? 'unsupported' : 'failed',
      level: 'compiled',
      code: error.message === 'VARIANT_UNSUPPORTED'
        ? 'VC-AI-PREVIEW-CONFIGURATION-UNSUPPORTED'
        : 'VC-AI-PREVIEW-CATALOG-INVALID',
      message: error.message === 'VARIANT_UNSUPPORTED'
        ? 'The compiled target does not declare the requested bounded Preview configuration.'
        : 'The fixed Preview descriptor catalog failed containment, integrity, or target checks.',
      nextAction: 'Regenerate the fixed descriptor catalog from the accepted source lane.',
      elapsedMs: performance.now() - started,
    });
  }

  const responsePath = resolve(
    artifactRoot,
    'render-cache',
    selection.catalog.buildFingerprint,
    selection.descriptor.id,
    selection.variant.id,
    'response.json',
  );
  if (remainingTimeout() <= 0) {
    return previewFailure({
      requestId,
      status: 'limited',
      code: 'VC-AI-PREVIEW-TIMEOUT',
      message: `Preview selection exceeded the ${limits.timeoutMs} ms request timeout.`,
      nextAction: 'Retry after preparing the fixed Preview lane.',
      elapsedMs: performance.now() - started,
    });
  }
  let cachedMetadata;
  try {
    cachedMetadata = await lstat(responsePath).catch((error) => {
      if (error?.code === 'ENOENT') return null;
      throw error;
    });
  } catch {
    return previewFailure({
      requestId,
      code: 'VC-AI-RENDER-CACHE-POISONED',
      message: 'The content-addressed Preview response path could not be inspected safely.',
      nextAction: 'Remove the fixed target render cache and render again.',
      elapsedMs: performance.now() - started,
    });
  }
  if (cachedMetadata) {
    try {
      const cached = await validateRenderArtifacts({
        responsePath,
        artifactRoot,
        repository,
        descriptor: selection.descriptor,
        variant: selection.variant,
      });
      if (cached.response.status === 'Success') {
        return toolResult({
          requestId,
          tool: 'render_preview',
          status: 'success',
          level: 'rendered',
          cache: 'hit',
          compilerLane: PREVIEW_COMPILER_LANE,
          renderLane: RENDER_LANE,
          outputFingerprint: cached.outputFingerprint,
          diagnostics: [...selection.catalogDiagnostics, ...cached.diagnostics],
          data: renderData({
            targetId,
            target,
            configuration,
            capabilityIds,
            selection,
            rendered: cached,
            repository,
          }),
          elapsedMs: performance.now() - started,
        });
      }
      const mapped = responseStatus(cached.response.status);
      return previewFailure({
        requestId,
        ...mapped,
        cache: 'hit',
        code: 'VC-AI-PREVIEW-FAILED',
        message: `The cached Preview response reported ${cached.response.status}.`,
        nextAction: 'Correct the structured Preview diagnostics before rendering again.',
        diagnostics: cached.diagnostics,
        elapsedMs: performance.now() - started,
      });
    } catch {
      return previewFailure({
        requestId,
        code: 'VC-AI-RENDER-CACHE-POISONED',
        message: 'The content-addressed Preview response or artifact failed integrity checks.',
        nextAction: 'Remove the fixed target render cache and render again.',
        elapsedMs: performance.now() - started,
      });
    }
  }

  const render = await runProcess(
    gradlePlan(repository, javaHome, [
      target.renderTask,
      `-PviewComposePreviewId=${selection.descriptor.id}`,
      `-PviewComposePreviewVariantId=${selection.variant.id}`,
    ]),
    {...limits, timeoutMs: Math.max(1, remainingTimeout()), signal},
  );
  if (render.cancelled || render.timedOut || render.truncated || render.spawnError) {
    return processFailure(render, requestId, 'render', started, remainingTimeout());
  }

  let rendered;
  try {
    rendered = await validateRenderArtifacts({
      responsePath,
      artifactRoot,
      repository,
      descriptor: selection.descriptor,
      variant: selection.variant,
    });
  } catch {
    const failure = processFailure(render, requestId, 'render', started, remainingTimeout());
    return failure ?? previewFailure({
      requestId,
      code: 'VC-AI-PREVIEW-OUTPUT-INVALID',
      message: 'Preview execution did not produce contained, bounded, valid protocol artifacts.',
      nextAction: 'Reject the result and repair the fixed Preview output contract.',
      elapsedMs: performance.now() - started,
    });
  }
  if (rendered.response.status !== 'Success') {
    const mapped = responseStatus(rendered.response.status);
    return previewFailure({
      requestId,
      ...mapped,
      cache: 'miss',
      code: 'VC-AI-PREVIEW-FAILED',
      message: `The Preview worker reported ${rendered.response.status}.`,
      nextAction: 'Correct the structured Preview diagnostics before rendering again.',
      diagnostics: rendered.diagnostics,
      elapsedMs: performance.now() - started,
    });
  }
  if (render.exitCode !== 0) {
    return processFailure(render, requestId, 'render', started, remainingTimeout());
  }
  return toolResult({
    requestId,
    tool: 'render_preview',
    status: 'success',
    level: 'rendered',
    cache: 'miss',
    compilerLane: PREVIEW_COMPILER_LANE,
    renderLane: RENDER_LANE,
    outputFingerprint: rendered.outputFingerprint,
    diagnostics: [...selection.catalogDiagnostics, ...rendered.diagnostics],
    data: renderData({
      targetId,
      target,
      configuration,
      capabilityIds,
      selection,
      rendered,
      repository,
    }),
    elapsedMs: performance.now() - started,
  });
}

function renderData({
  targetId,
  target,
  configuration,
  capabilityIds,
  selection,
  rendered,
  repository,
}) {
  return {
    targetId,
    modulePath: target.modulePath,
    buildVariant: target.buildVariant,
    buildFingerprint: selection.catalog.buildFingerprint,
    previewId: selection.descriptor.id,
    variantId: selection.variant.id,
    configuration,
    capabilityIds: [...new Set([...target.capabilityIds, ...capabilityIds])].sort(),
    source: {
      path: selection.sourcePath,
      line: selection.descriptor.sourceLocation.line,
      column: selection.descriptor.sourceLocation.column,
    },
    image: {
      path: relative(repository, rendered.image.path).replaceAll(sep, '/'),
      mediaType: 'image/png',
      widthPx: rendered.image.width,
      heightPx: rendered.image.height,
      bytes: rendered.image.bytes,
      sha256: rendered.image.sha256,
    },
    renderTree: {
      path: relative(repository, rendered.tree.path).replaceAll(sep, '/'),
      bytes: rendered.tree.bytes,
      sha256: rendered.tree.sha256,
    },
    durationMillis: rendered.response.durationMillis,
    phaseTimings: Array.isArray(rendered.response.phaseTimings)
      ? rendered.response.phaseTimings.slice(0, 50)
      : [],
  };
}
