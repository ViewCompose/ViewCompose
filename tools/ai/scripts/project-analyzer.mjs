import {lstat, readFile, readdir, realpath} from 'node:fs/promises';
import {basename, extname, isAbsolute, relative, resolve, sep} from 'node:path';
import {diagnostic, toolResult, utf8Bytes} from './tool-core.mjs';

export const DEFAULT_PROJECT_LIMITS = Object.freeze({
  maxFiles: 1000,
  maxBytes: 4 * 1024 * 1024,
  maxDepth: 16,
  timeoutMs: 10_000,
  maxOutputBytes: 1024 * 1024,
});
const HARD_PROJECT_LIMITS = Object.freeze({
  maxFiles: 10_000,
  maxBytes: 32 * 1024 * 1024,
  maxDepth: 32,
  timeoutMs: 30_000,
  maxOutputBytes: 4 * 1024 * 1024,
});

const defaultExcludedNames = new Set([
  '.git',
  '.gradle',
  '.idea',
  'build',
  'local.properties',
]);
const secretSuffixes = ['.jks', '.keystore', '.p12', '.pfx', '.pem', '.key'];
const readableExtensions = new Set(['.gradle', '.java', '.json', '.kts', '.kt', '.toml', '.xml']);

function pathEscapes(root, candidate) {
  const relation = relative(root, candidate);
  return relation === '..' || relation.startsWith(`..${sep}`) || isAbsolute(relation);
}

function isSecretPath(path) {
  const name = basename(path).toLowerCase();
  return name === 'local.properties' ||
    name === '.env' ||
    name.startsWith('.env.') ||
    secretSuffixes.some((suffix) => name.endsWith(suffix));
}

function isExcluded(path, extraExcluded = []) {
  const name = basename(path);
  if (defaultExcludedNames.has(name) || isSecretPath(path)) return true;
  return extraExcluded.some((pattern) => {
    if (pattern.startsWith('*.')) return name.endsWith(pattern.slice(1));
    return name === pattern;
  });
}

function securityFailure({requestId, code, message, nextAction}) {
  return toolResult({
    requestId,
    tool: 'analyze_project',
    status: 'invalid',
    level: 'static',
    diagnostics: [diagnostic({code, severity: 'error', message, nextAction})],
  });
}

function normalizeLimits(requested) {
  if (requested !== undefined && (requested === null || typeof requested !== 'object' || Array.isArray(requested))) {
    return null;
  }
  const limits = {...DEFAULT_PROJECT_LIMITS};
  for (const [name, hardMaximum] of Object.entries(HARD_PROJECT_LIMITS)) {
    if (requested?.[name] === undefined) continue;
    const value = requested[name];
    if (!Number.isInteger(value) || value <= 0 || value > hardMaximum) return null;
    limits[name] = value;
  }
  return limits;
}

export async function inspectProjectRequest(spec, {requestId = 'analyze-project'} = {}) {
  if (!spec || typeof spec.projectRoot !== 'string' || !isAbsolute(spec.projectRoot)) {
    return securityFailure({
      requestId,
      code: 'VC-AI-PROJECT-ROOT-INVALID',
      message: 'Project analysis requires one absolute project root.',
      nextAction: 'Submit one normalized absolute root and keep analysis read-only.',
    });
  }
  if (spec.requestedOperation && spec.requestedOperation !== 'analyze-read-only') {
    return securityFailure({
      requestId,
      code: 'VC-AI-BUILD-EXECUTION-DENIED',
      message: `Project operation ${spec.requestedOperation} is not permitted.`,
      nextAction: 'Use the pinned tool-owned compiler harness; never execute inspected-project build logic.',
    });
  }
  if (spec.readOnly === false) {
    return securityFailure({
      requestId,
      code: 'VC-AI-PROJECT-READ-ONLY',
      message: 'Project analysis is read-only and cannot apply writes.',
      nextAction: 'Request an inventory or a bounded patch plan for explicit client application.',
    });
  }
  const limits = normalizeLimits(spec.limits);
  if (!limits) {
    return securityFailure({
      requestId,
      code: 'VC-AI-PROJECT-LIMIT-INVALID',
      message: 'Project-analysis limits must be positive integers within the tool hard caps.',
      nextAction: 'Use the documented bounded file, byte, depth, timeout, and output limits.',
    });
  }
  if (spec.requestedPath) {
    const candidate = resolve(spec.projectRoot, spec.requestedPath);
    if (pathEscapes(resolve(spec.projectRoot), candidate)) {
      return securityFailure({
        requestId,
        code: 'VC-AI-PATH-ESCAPE',
        message: 'The requested path resolves outside the declared project root.',
        nextAction: 'Submit a root-contained relative path without parent traversal.',
      });
    }
  }
  const files = Array.isArray(spec.files) ? [...spec.files].sort() : [];
  for (const file of files) {
    const candidate = resolve(spec.projectRoot, file);
    if (isAbsolute(file) || pathEscapes(resolve(spec.projectRoot), candidate)) {
      return securityFailure({
        requestId,
        code: 'VC-AI-PATH-ESCAPE',
        message: `Declared file ${file} resolves outside the project root.`,
        nextAction: 'Declare only normalized project-relative file paths.',
      });
    }
  }
  if (files.length > limits.maxFiles) {
    return toolResult({
      requestId,
      tool: 'analyze_project',
      status: 'limited',
      level: 'static',
      diagnostics: [diagnostic({
        code: 'VC-AI-PROJECT-LIMIT',
        severity: 'error',
        message: `Declared inventory exceeds the ${limits.maxFiles}-file limit.`,
        nextAction: 'Narrow the requested project scope.',
      })],
      truncated: true,
    });
  }
  return toolResult({
    requestId,
    tool: 'analyze_project',
    status: 'success',
    level: 'static',
    diagnostics: [],
    data: {
      projectRoot: spec.projectRoot,
      readOnly: true,
      files,
      excluded: [...new Set(spec.excluded ?? [])].sort(),
      limits,
    },
  });
}

export async function analyzeProject({
  projectRoot,
  requestedPath = '.',
  limits: requestedLimits,
  excluded = [],
  requestId = 'analyze-project',
} = {}) {
  const started = performance.now();
  const preflight = await inspectProjectRequest(
    {projectRoot, requestedPath, readOnly: true, limits: requestedLimits},
    {requestId},
  );
  if (preflight.status !== 'success') return preflight;
  const limits = preflight.data.limits;
  const root = await realpath(projectRoot);
  const target = resolve(root, requestedPath);
  if (pathEscapes(root, target)) {
    return securityFailure({
      requestId,
      code: 'VC-AI-PATH-ESCAPE',
      message: 'The requested path resolves outside the canonical project root.',
      nextAction: 'Submit a canonical root-contained relative path.',
    });
  }

  const files = [];
  let totalBytes = 0;
  let excludedCount = 0;
  const signals = {viewComposeImports: 0, gradleFiles: 0, kotlinFiles: 0, xmlFiles: 0};
  const queue = [{path: target, depth: 0}];
  while (queue.length > 0) {
    if (performance.now() - started > limits.timeoutMs) {
      return toolResult({
        requestId,
        tool: 'analyze_project',
        status: 'limited',
        level: 'static',
        diagnostics: [diagnostic({
          code: 'VC-AI-PROJECT-TIMEOUT',
          severity: 'error',
          message: `Project analysis exceeded ${limits.timeoutMs} ms.`,
          nextAction: 'Narrow the root or increase the bounded timeout.',
        })],
        data: {files, totalBytes, excludedCount, signals},
        elapsedMs: performance.now() - started,
        truncated: true,
      });
    }
    const current = queue.shift();
    if (current.depth > limits.maxDepth) {
      return toolResult({
        requestId,
        tool: 'analyze_project',
        status: 'limited',
        level: 'static',
        diagnostics: [diagnostic({
          code: 'VC-AI-PROJECT-LIMIT',
          severity: 'error',
          message: `Project traversal exceeded depth ${limits.maxDepth}.`,
          nextAction: 'Narrow the project-analysis root.',
        })],
        data: {files, totalBytes, excludedCount, signals},
        elapsedMs: performance.now() - started,
        truncated: true,
      });
    }
    const metadata = await lstat(current.path);
    if (metadata.isSymbolicLink()) {
      return securityFailure({
        requestId,
        code: 'VC-AI-SYMLINK-DENIED',
        message: `Project analysis rejects symbolic link ${relative(root, current.path)}.`,
        nextAction: 'Replace the link with a root-contained regular file or exclude it.',
      });
    }
    if (isExcluded(current.path, excluded) && current.path !== target) {
      excludedCount += 1;
      continue;
    }
    if (metadata.isDirectory()) {
      const children = await readdir(current.path);
      children.sort();
      queue.push(...children.map((child) => ({
        path: resolve(current.path, child),
        depth: current.depth + 1,
      })));
      continue;
    }
    if (!metadata.isFile()) continue;
    if (files.length >= limits.maxFiles || totalBytes + metadata.size > limits.maxBytes) {
      return toolResult({
        requestId,
        tool: 'analyze_project',
        status: 'limited',
        level: 'static',
        diagnostics: [diagnostic({
          code: 'VC-AI-PROJECT-LIMIT',
          severity: 'error',
          message: 'Project analysis reached its file or byte limit.',
          nextAction: 'Narrow the project scope or raise an explicit bounded limit.',
        })],
        data: {files, totalBytes, excludedCount, signals},
        elapsedMs: performance.now() - started,
        truncated: true,
      });
    }
    const projectPath = relative(root, current.path).replaceAll(sep, '/');
    files.push({path: projectPath, bytes: metadata.size});
    totalBytes += metadata.size;
    const extension = extname(current.path);
    if (extension === '.kt' || extension === '.kts') signals.kotlinFiles += 1;
    if (extension === '.xml') signals.xmlFiles += 1;
    if (basename(current.path).includes('gradle')) signals.gradleFiles += 1;
    if (readableExtensions.has(extension) && metadata.size <= 256 * 1024) {
      const content = await readFile(current.path, 'utf8');
      signals.viewComposeImports += (content.match(/\b(?:import|implementation)\b[^\n]*\bcom\.viewcompose\b/gu) ?? []).length;
    }
  }
  files.sort((left, right) => left.path < right.path ? -1 : left.path > right.path ? 1 : 0);
  const data = {projectRoot: root, readOnly: true, files, totalBytes, excludedCount, signals, limits};
  const serializedBytes = utf8Bytes(JSON.stringify(data));
  return toolResult({
    requestId,
    tool: 'analyze_project',
    status: serializedBytes > limits.maxOutputBytes ? 'limited' : 'success',
    level: 'static',
    diagnostics: serializedBytes > limits.maxOutputBytes ? [diagnostic({
      code: 'VC-AI-OUTPUT-LIMIT',
      severity: 'error',
      message: 'Project inventory exceeds the bounded output limit.',
      nextAction: 'Narrow the requested project scope.',
    })] : [],
    data: serializedBytes > limits.maxOutputBytes ? undefined : data,
    elapsedMs: performance.now() - started,
    truncated: serializedBytes > limits.maxOutputBytes,
  });
}
