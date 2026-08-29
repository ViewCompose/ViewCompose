import {createHash} from 'node:crypto';
import {
  lstat,
  mkdir,
  open,
  readFile,
  readdir,
  rename,
  unlink,
  writeFile,
} from 'node:fs/promises';
import {dirname, isAbsolute, relative, resolve, sep} from 'node:path';
import {executeBoundedProcess} from './bounded-process.mjs';
import {
  detectJavaFeature,
  diagnostic,
  repositoryRoot,
  toolResult,
  utf8Bytes,
  verifyConfiguredSourceRoot,
} from './tool-core.mjs';
import {validateKotlin} from './static-validator.mjs';

export const COMPILER_LANE =
  'current-source/jdk-21/agp-9.1.1/kotlin-2.2.10/android-36/jvm-11';
export const SUPPORTED_COMPILER_ARTIFACTS = Object.freeze(['viewcompose-ui-foundation']);
export const DEFAULT_COMPILER_LIMITS = Object.freeze({
  maxSourceBytes: 1024 * 1024,
  timeoutMs: 120_000,
  maxOutputBytes: 1024 * 1024,
});
const HARD_COMPILER_LIMITS = Object.freeze({
  maxSourceBytes: 4 * 1024 * 1024,
  timeoutMs: 180_000,
  maxOutputBytes: 2 * 1024 * 1024,
});
const MAX_CLASS_FILES = 10_000;
const MAX_CLASS_BYTES = 64 * 1024 * 1024;

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function normalizeLimits(requested) {
  if (requested !== undefined && (requested === null || typeof requested !== 'object' || Array.isArray(requested))) {
    return null;
  }
  const limits = {...DEFAULT_COMPILER_LIMITS};
  for (const [name, hardMaximum] of Object.entries(HARD_COMPILER_LIMITS)) {
    if (requested?.[name] === undefined) continue;
    const value = requested[name];
    if (!Number.isInteger(value) || value <= 0 || value > hardMaximum) return null;
    limits[name] = value;
  }
  return limits;
}

function logicalPathIsSafe(path) {
  return typeof path === 'string' &&
    path.length > 0 &&
    path.length <= 1024 &&
    !isAbsolute(path) &&
    !path.split(/[\\/]/u).includes('..');
}

async function compilerFailure({
  requestId,
  status = 'invalid',
  code,
  message,
  nextAction,
  level = 'static',
  cache = 'bypassed',
  elapsedMs,
  truncated = false,
}) {
  return toolResult({
    requestId,
    tool: 'validate_code',
    status,
    level,
    cache,
    compilerLane: COMPILER_LANE,
    diagnostics: [diagnostic({code, severity: 'error', message, nextAction})],
    elapsedMs,
    truncated,
  });
}

export function compilerRequestKey({source, artifactIds, bundleFingerprint}) {
  return sha256(JSON.stringify({
    schemaVersion: 1,
    compilerLane: COMPILER_LANE,
    bundleFingerprint,
    artifactIds: [...artifactIds].sort(),
    source,
  }));
}

function planFor({requestKey, cacheRoot, javaHome}) {
  const repository = repositoryRoot();
  const requestRoot = resolve(cacheRoot, requestKey);
  return {
    requestKey,
    requestRoot,
    inputPath: resolve(requestRoot, 'input/Snippet.kt'),
    cachePath: resolve(requestRoot, 'result-cache.json'),
    lockPath: resolve(requestRoot, 'compile.lock'),
    classesDirectory: resolve(requestRoot, 'harness/tmp/kotlin-classes/debug'),
    executable: resolve(repository, 'gradlew'),
    cwd: repository,
    args: [
      ':tools:ai-compiler-harness:compileAiSnippet',
      `-PviewComposeAiRequestKey=${requestKey}`,
      '-Pkotlin.compiler.execution.strategy=in-process',
      `-Dorg.gradle.java.home=${javaHome}`,
      '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8',
      '--offline',
      '--no-daemon',
      '--no-build-cache',
      '--no-configuration-cache',
      '--max-workers=2',
      '--console=plain',
    ],
  };
}

async function ensureRequestDirectories(cacheRoot, requestRoot, inputDirectory) {
  if (relative(resolve(cacheRoot), requestRoot).startsWith(`..${sep}`)) {
    throw new Error('CACHE_PATH_ESCAPE');
  }
  for (const directory of [cacheRoot, requestRoot, inputDirectory]) {
    await mkdir(directory, {recursive: true});
    const metadata = await lstat(directory);
    if (!metadata.isDirectory() || metadata.isSymbolicLink()) throw new Error('CACHE_PATH_UNSAFE');
  }
}

async function persistImmutableInput(path, source) {
  const existing = await lstat(path).catch((error) => {
    if (error?.code === 'ENOENT') return null;
    throw error;
  });
  if (existing?.isSymbolicLink() || (existing && !existing.isFile())) {
    throw new Error('CACHE_INPUT_UNSAFE');
  }
  try {
    await writeFile(path, source, {encoding: 'utf8', flag: 'wx'});
  } catch (error) {
    if (error?.code !== 'EEXIST') throw error;
    if (await readFile(path, 'utf8') !== source) {
      throw new Error('CACHE_INPUT_MISMATCH');
    }
  }
}

async function fingerprintClasses(directory) {
  const rootMetadata = await lstat(directory).catch(() => null);
  if (!rootMetadata?.isDirectory()) return null;
  const files = [];
  const queue = [directory];
  let totalBytes = 0;
  while (queue.length > 0) {
    const current = queue.shift();
    const children = await readdir(current);
    children.sort();
    for (const child of children) {
      const path = resolve(current, child);
      const metadata = await lstat(path);
      if (metadata.isSymbolicLink()) throw new Error('CACHE_SYMLINK');
      if (metadata.isDirectory()) {
        queue.push(path);
      } else if (metadata.isFile()) {
        totalBytes += metadata.size;
        if (files.length >= MAX_CLASS_FILES || totalBytes > MAX_CLASS_BYTES) {
          throw new Error('CACHE_OUTPUT_LIMIT');
        }
        files.push(path);
      }
    }
  }
  if (files.length === 0) return null;
  files.sort();
  const hash = createHash('sha256');
  for (const path of files) {
    hash.update(relative(directory, path).replaceAll(sep, '/'));
    hash.update('\0');
    hash.update(await readFile(path));
    hash.update('\n');
  }
  return {fingerprint: hash.digest('hex'), files: files.length, bytes: totalBytes};
}

async function readVerifiedCache(plan) {
  const metadata = await lstat(plan.cachePath).catch((error) => {
    if (error?.code === 'ENOENT') return null;
    throw error;
  });
  if (metadata?.isSymbolicLink() || (metadata && !metadata.isFile())) {
    return {state: 'poisoned'};
  }
  const content = await readFile(plan.cachePath, 'utf8').catch((error) => {
    if (error?.code === 'ENOENT') return null;
    throw error;
  });
  if (content === null) return {state: 'miss'};
  let record;
  try {
    record = JSON.parse(content);
  } catch {
    return {state: 'poisoned'};
  }
  if (
    record.schemaVersion !== 1 ||
    record.requestKey !== plan.requestKey ||
    record.compilerLane !== COMPILER_LANE ||
    !/^[a-f0-9]{64}$/u.test(record.outputFingerprint ?? '')
  ) {
    return {state: 'poisoned'};
  }
  const output = await fingerprintClasses(plan.classesDirectory).catch(() => null);
  if (!output || output.fingerprint !== record.outputFingerprint) return {state: 'poisoned'};
  return {state: 'hit', output};
}

async function writeCacheRecord(plan, output) {
  const temporaryPath = `${plan.cachePath}.${process.pid}-${Date.now()}.tmp`;
  const record = `${JSON.stringify({
    schemaVersion: 1,
    requestKey: plan.requestKey,
    compilerLane: COMPILER_LANE,
    outputFingerprint: output.fingerprint,
    files: output.files,
    bytes: output.bytes,
  }, null, 2)}\n`;
  await writeFile(temporaryPath, record, {encoding: 'utf8', flag: 'wx'});
  await rename(temporaryPath, plan.cachePath);
}

export const executeCompilerProcess = executeBoundedProcess;

function parseCompilerDiagnostics(output, sourcePath, requestRoot) {
  const normalized = output.replaceAll(requestRoot, '<request>');
  const patterns = [
    /^[ew]:\s+(?:file:\/\/)?[^\n]*?Snippet\.kt:(\d+):(\d+)\s+(.+)$/gmu,
    /^[ew]:\s+[^\n]*?Snippet\.kt:\s*\((\d+),\s*(\d+)\):\s*(.+)$/gmu,
  ];
  const diagnostics = [];
  for (const pattern of patterns) {
    for (const match of normalized.matchAll(pattern)) {
      const line = Number(match[1]);
      const column = Number(match[2]);
      const severity = match[0].startsWith('w:') ? 'warning' : 'error';
      diagnostics.push(diagnostic({
        code: 'VC-AI-KOTLIN-COMPILER',
        severity,
        message: match[3].trim().slice(0, 4096),
        nextAction: 'Correct the reported Kotlin declaration, import, type, or overload and compile again.',
        source: {
          path: sourcePath,
          startLine: line,
          startColumn: column,
          endLine: line,
          endColumn: column + 1,
        },
      }));
    }
  }
  return [...new Map(diagnostics.map((entry) => [
    `${entry.severity}:${entry.source.startLine}:${entry.source.startColumn}:${entry.message}`,
    entry,
  ])).values()].slice(0, 100);
}

export async function compileKotlin({
  source,
  path = 'Snippet.kt',
  artifactIds = SUPPORTED_COMPILER_ARTIFACTS,
  capabilityIds = [],
  requestId = 'compile-code',
  limits: requestedLimits,
  signal,
} = {}, {
  runCompiler = executeCompilerProcess,
  javaFeature = detectJavaFeature(),
  javaHome = process.env.JAVA_HOME,
  cacheRoot = resolve(repositoryRoot(), 'build/ai/compiler/requests'),
} = {}) {
  const started = performance.now();
  const limits = normalizeLimits(requestedLimits);
  if (!limits) {
    return compilerFailure({
      requestId,
      code: 'VC-AI-COMPILER-LIMIT-INVALID',
      message: 'Compiler limits must be positive integers within the fixed hard caps.',
      nextAction: 'Use the documented source, timeout, and output limits.',
      elapsedMs: performance.now() - started,
    });
  }
  if (!logicalPathIsSafe(path)) {
    return compilerFailure({
      requestId,
      code: 'VC-AI-INPUT-PATH-INVALID',
      message: 'Compiler source labels must be non-empty logical relative paths without parent traversal.',
      nextAction: 'Use a display path such as src/main/java/example/Screen.kt.',
      elapsedMs: performance.now() - started,
    });
  }
  if (typeof source !== 'string' || source.length === 0 || utf8Bytes(source) > limits.maxSourceBytes) {
    return compilerFailure({
      requestId,
      status: 'limited',
      code: 'VC-AI-INPUT-LIMIT',
      message: `Kotlin source is missing or exceeds the ${limits.maxSourceBytes}-byte compiler limit.`,
      nextAction: 'Submit one smaller, non-empty Kotlin source file.',
      elapsedMs: performance.now() - started,
      truncated: true,
    });
  }
  if (
    !Array.isArray(artifactIds) ||
    !Array.isArray(capabilityIds) ||
    artifactIds.length > SUPPORTED_COMPILER_ARTIFACTS.length ||
    capabilityIds.length > 100 ||
    [...artifactIds, ...capabilityIds].some((value) =>
      typeof value !== 'string' || !/^[a-z0-9][a-z0-9.-]*$/u.test(value))
  ) {
    return compilerFailure({
      requestId,
      code: 'VC-AI-COMPILER-SELECTION-INVALID',
      message: 'Compiler artifact and capability selections must be bounded stable-ID arrays.',
      nextAction: 'Submit generated artifact and capability IDs without paths or coordinates.',
      elapsedMs: performance.now() - started,
    });
  }
  const requestedArtifacts = [...new Set(artifactIds)].sort();
  if (
    requestedArtifacts.length === 0 ||
    requestedArtifacts.some((artifact) => !SUPPORTED_COMPILER_ARTIFACTS.includes(artifact))
  ) {
    return compilerFailure({
      requestId,
      status: 'unsupported',
      code: 'VC-AI-COMPILER-ARTIFACT-UNSUPPORTED',
      message: 'The current compiler lane accepts only the fixed UI Foundation artifact allowlist.',
      nextAction: `Use one of: ${SUPPORTED_COMPILER_ARTIFACTS.join(', ')}.`,
      elapsedMs: performance.now() - started,
    });
  }
  const staticResult = await validateKotlin({
    source,
    path,
    requestId,
    maxInputBytes: limits.maxSourceBytes,
  });
  if (staticResult.status !== 'success') return staticResult;
  if (javaFeature !== 21 || !javaHome) {
    return compilerFailure({
      requestId,
      status: 'unsupported',
      code: 'VC-AI-COMPILER-LANE-MISMATCH',
      message: `The ${COMPILER_LANE} compiler lane requires JAVA_HOME to resolve JDK 21.`,
      nextAction: 'Select the pinned JDK 21 lane before compiling.',
      elapsedMs: performance.now() - started,
    });
  }
  const sourceRoot = await verifyConfiguredSourceRoot();
  if (!sourceRoot.matched) {
    return compilerFailure({
      requestId,
      status: 'unsupported',
      code: 'VC-AI-SOURCE-ROOT-MISMATCH',
      message: 'The configured ViewCompose source root does not contain the packaged framework identity.',
      nextAction: 'Select a matching ViewCompose checkout with the required Gradle wrapper.',
      elapsedMs: performance.now() - started,
    });
  }

  const bundleFingerprint = staticResult.evidence.bundleFingerprint;
  const requestKey = compilerRequestKey({
    source,
    artifactIds: requestedArtifacts,
    bundleFingerprint,
  });
  const plan = planFor({requestKey, cacheRoot, javaHome});
  try {
    await ensureRequestDirectories(cacheRoot, plan.requestRoot, dirname(plan.inputPath));
  } catch {
    return compilerFailure({
      requestId,
      status: 'failed',
      code: 'VC-AI-CACHE-POISONED',
      message: 'The tool-owned compiler request path failed containment or file-type checks.',
      nextAction: 'Remove the tool-owned request cache and compile again.',
      elapsedMs: performance.now() - started,
    });
  }
  try {
    await persistImmutableInput(plan.inputPath, source);
  } catch (error) {
    if (error.message.startsWith('CACHE_INPUT_')) {
      return compilerFailure({
        requestId,
        status: 'failed',
        code: 'VC-AI-CACHE-POISONED',
        message: 'The content-addressed compiler input does not match its request key.',
        nextAction: 'Remove the tool-owned request cache and compile again.',
        elapsedMs: performance.now() - started,
      });
    }
    throw error;
  }

  const cached = await readVerifiedCache(plan);
  if (cached.state === 'poisoned') {
    return compilerFailure({
      requestId,
      status: 'failed',
      code: 'VC-AI-CACHE-POISONED',
      message: 'The compiler cache record or class output failed integrity verification.',
      nextAction: 'Remove the tool-owned request cache and compile again.',
      elapsedMs: performance.now() - started,
    });
  }
  if (cached.state === 'hit') {
    return toolResult({
      requestId,
      tool: 'validate_code',
      status: 'success',
      level: 'compiled',
      cache: 'hit',
      compilerLane: COMPILER_LANE,
      outputFingerprint: cached.output.fingerprint,
      diagnostics: [],
      data: {
        requestKey,
        artifactIds: requestedArtifacts,
        capabilityIds: [...new Set(capabilityIds)].sort(),
        classFiles: cached.output.files,
        classBytes: cached.output.bytes,
      },
      elapsedMs: performance.now() - started,
    });
  }

  let lock;
  try {
    lock = await open(plan.lockPath, 'wx');
    await lock.writeFile(`${process.pid}\n`, 'utf8');
  } catch (error) {
    if (error?.code !== 'EEXIST') throw error;
    return compilerFailure({
      requestId,
      status: 'limited',
      code: 'VC-AI-COMPILER-BUSY',
      message: 'An identical content-addressed compiler request is already running.',
      nextAction: 'Retry after the active request finishes.',
      elapsedMs: performance.now() - started,
    });
  }

  try {
    const execution = await runCompiler(plan, {...limits, signal});
    if (execution.cancelled) {
      return compilerFailure({
        requestId,
        status: 'cancelled',
        code: 'VC-AI-COMPILER-CANCELLED',
        message: 'Compilation was cancelled before a result was accepted.',
        nextAction: 'Retry the same immutable request when compilation is still required.',
        elapsedMs: performance.now() - started,
      });
    }
    if (execution.timedOut) {
      return compilerFailure({
        requestId,
        status: 'limited',
        code: 'VC-AI-COMPILER-TIMEOUT',
        message: `Compilation exceeded the ${limits.timeoutMs} ms timeout.`,
        nextAction: 'Reduce the source or retry under an accepted compiler lane.',
        elapsedMs: performance.now() - started,
      });
    }
    if (execution.truncated) {
      return compilerFailure({
        requestId,
        status: 'limited',
        code: 'VC-AI-COMPILER-OUTPUT-LIMIT',
        message: `Compiler output exceeded the ${limits.maxOutputBytes}-byte limit.`,
        nextAction: 'Correct repeated compiler failures or raise the bounded output limit.',
        elapsedMs: performance.now() - started,
        truncated: true,
      });
    }
    if (execution.spawnError) {
      return compilerFailure({
        requestId,
        status: 'failed',
        code: 'VC-AI-COMPILER-START-FAILED',
        message: 'The fixed Gradle compiler process could not be started.',
        nextAction: 'Verify the repository wrapper and pinned JDK 21 installation.',
        elapsedMs: performance.now() - started,
      });
    }
    const compilerDiagnostics = parseCompilerDiagnostics(execution.output, path, plan.requestRoot);
    if (execution.exitCode !== 0) {
      return toolResult({
        requestId,
        tool: 'validate_code',
        status: compilerDiagnostics.length > 0 ? 'invalid' : 'failed',
        level: 'static',
        cache: 'miss',
        compilerLane: COMPILER_LANE,
        diagnostics: compilerDiagnostics.length > 0 ? compilerDiagnostics : [diagnostic({
          code: 'VC-AI-COMPILER-FAILED',
          severity: 'error',
          message: 'The fixed compiler harness failed without a source diagnostic.',
          nextAction: 'Verify the accepted SDK/toolchain lane, then retry the immutable request.',
        })],
        elapsedMs: performance.now() - started,
      });
    }
    let output;
    try {
      output = await fingerprintClasses(plan.classesDirectory);
    } catch {
      return compilerFailure({
        requestId,
        status: 'failed',
        code: 'VC-AI-COMPILER-OUTPUT-INVALID',
        message: 'The compiler class output exceeded fixed limits or contained an unsafe file type.',
        nextAction: 'Reject the result and repair the fixed harness output contract.',
        elapsedMs: performance.now() - started,
      });
    }
    if (!output) {
      return compilerFailure({
        requestId,
        status: 'failed',
        code: 'VC-AI-COMPILER-OUTPUT-MISSING',
        message: 'The compiler reported success without bounded Kotlin class output.',
        nextAction: 'Reject the result and repair the fixed harness output contract.',
        elapsedMs: performance.now() - started,
      });
    }
    await writeCacheRecord(plan, output);
    return toolResult({
      requestId,
      tool: 'validate_code',
      status: 'success',
      level: 'compiled',
      cache: 'miss',
      compilerLane: COMPILER_LANE,
      outputFingerprint: output.fingerprint,
      diagnostics: compilerDiagnostics,
      data: {
        requestKey,
        artifactIds: requestedArtifacts,
        capabilityIds: [...new Set(capabilityIds)].sort(),
        classFiles: output.files,
        classBytes: output.bytes,
      },
      elapsedMs: performance.now() - started,
    });
  } finally {
    await lock?.close();
    await unlink(plan.lockPath).catch((error) => {
      if (error?.code !== 'ENOENT') throw error;
    });
  }
}
