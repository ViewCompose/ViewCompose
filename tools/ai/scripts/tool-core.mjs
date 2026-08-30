import {spawnSync} from 'node:child_process';
import {existsSync, realpathSync} from 'node:fs';
import {lstat, readFile} from 'node:fs/promises';
import {homedir, platform} from 'node:os';
import {isAbsolute, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {
  activeFrameworkProfile,
  activeKnowledgePath,
  FRAMEWORK_PROFILE_ENVIRONMENT_VARIABLE,
} from './framework-profile-selection.mjs';

const aiRoot = fileURLToPath(new URL('../', import.meta.url));
const repository = resolve(aiRoot, '../..');
export const SOURCE_ROOT_ENVIRONMENT_VARIABLE = 'VIEWCOMPOSE_SOURCE_ROOT';
export const PROJECT_ROOT_ENVIRONMENT_VARIABLE = 'VIEWCOMPOSE_PROJECT_ROOT';
export {FRAMEWORK_PROFILE_ENVIRONMENT_VARIABLE};
const manifestPath = activeKnowledgePath('manifest.json');

let manifestPromise;

export function loadKnowledgeManifest() {
  manifestPromise ??= readFile(manifestPath, 'utf8').then((text) => {
    const manifest = JSON.parse(text);
    const selected = activeFrameworkProfile();
    if (
      manifest.framework?.versionLane !== selected.versionLane ||
      (selected.versionLane === 'released' && manifest.framework?.identity !== selected.profileId)
    ) {
      throw new Error('Active Knowledge Bundle differs from the selected framework profile.');
    }
    return manifest;
  });
  return manifestPromise;
}

export function aiToolingRoot() {
  return aiRoot;
}

export function executionHarnessRoot() {
  return resolve(aiRoot, 'harness');
}

export function toolCacheRoot() {
  const base = platform() === 'darwin'
    ? resolve(homedir(), 'Library/Caches')
    : process.env.XDG_CACHE_HOME && isAbsolute(process.env.XDG_CACHE_HOME)
      ? resolve(process.env.XDG_CACHE_HOME)
      : resolve(homedir(), '.cache');
  return resolve(base, 'viewcompose/ai-tooling/0.2.0');
}

export function repositoryRoot() {
  const configured = process.env[SOURCE_ROOT_ENVIRONMENT_VARIABLE];
  if (configured !== undefined) {
    if (
      configured.length === 0 ||
      configured.length > 4096 ||
      configured.includes('\0') ||
      !isAbsolute(configured)
    ) {
      throw new Error(`${SOURCE_ROOT_ENVIRONMENT_VARIABLE} must be an absolute local path.`);
    }
    return resolve(configured);
  }
  return repository;
}

export async function verifyConfiguredSourceRoot(root = repositoryRoot()) {
  const configured = process.env[SOURCE_ROOT_ENVIRONMENT_VARIABLE] !== undefined;
  // A non-default root is an injected, process-local test/adapter boundary. Public tool calls cannot
  // select it; retaining this path keeps hermetic adapter tests independent of a Git checkout.
  if (!configured && resolve(root) !== repository) return {matched: true, mode: 'inferred'};
  const mode = configured ? 'configured' : 'standalone';
  const manifest = await loadKnowledgeManifest();
  for (const path of ['gradlew', 'settings.gradle.kts']) {
    const metadata = await lstat(resolve(root, path)).catch((error) => {
      if (error?.code === 'ENOENT') return null;
      throw error;
    });
    if (!metadata?.isFile() || metadata.isSymbolicLink()) {
      return {
        matched: false,
        mode,
        reason: configured ? 'required-file' : 'source-root-unavailable',
      };
    }
  }
  const revision = manifest.source.revision;
  const resolved = spawnSync('git', ['-C', root, 'rev-parse', '--verify', `${revision}^{commit}`], {
    encoding: 'utf8',
  });
  const ancestor = spawnSync('git', ['-C', root, 'merge-base', '--is-ancestor', revision, 'HEAD'], {
    encoding: 'utf8',
  });
  if (resolved.error || resolved.status !== 0 || ancestor.error || ancestor.status !== 0) {
    return {matched: false, mode, reason: 'framework-identity'};
  }
  return {matched: true, mode: configured ? 'configured' : 'inferred'};
}

export async function verifyConfiguredProjectRoot(
  root = process.env[PROJECT_ROOT_ENVIRONMENT_VARIABLE],
) {
  if (
    typeof root !== 'string' ||
    root.length === 0 ||
    root.length > 4096 ||
    root.includes('\0') ||
    !isAbsolute(root)
  ) {
    return {matched: false, reason: 'project-root-unavailable'};
  }
  const absolute = resolve(root);
  const metadata = await lstat(absolute).catch((error) => {
    if (error?.code === 'ENOENT') return null;
    throw error;
  });
  if (!metadata?.isDirectory() || metadata.isSymbolicLink()) {
    return {matched: false, reason: 'project-root-unsafe'};
  }
  let canonical;
  try {
    canonical = realpathSync(absolute);
  } catch {
    return {matched: false, reason: 'project-root-unsafe'};
  }
  if (canonical !== absolute) return {matched: false, reason: 'project-root-unsafe'};
  return {matched: true, root: absolute};
}

function javaRuntime(executable) {
  const result = spawnSync(executable, ['-XshowSettings:properties', '-version'], {
    encoding: 'utf8',
  });
  if (result.error || result.status !== 0) return null;
  const output = `${result.stdout ?? ''}\n${result.stderr ?? ''}`;
  const version = /\bjava\.version\s*=\s*([^\s]+)/u.exec(output)?.[1];
  const javaHome = /\bjava\.home\s*=\s*([^\r\n]+)/u.exec(output)?.[1]?.trim();
  if (!version || !javaHome || !isAbsolute(javaHome)) return null;
  const feature = Number.parseInt(version.startsWith('1.') ? version.split('.')[1] : version, 10);
  if (!Number.isInteger(feature)) return null;
  return {feature, javaHome: realpathSync(javaHome)};
}

export function detectJavaRuntime(javaHome = process.env.JAVA_HOME) {
  const candidates = [
    javaHome,
    process.env.STUDIO_JDK,
    platform() === 'darwin' ? '/Applications/Android Studio.app/Contents/jbr/Contents/Home' : null,
    resolve(homedir(), '.sdkman/candidates/java/current'),
  ].filter((candidate) => typeof candidate === 'string' && candidate.length > 0);
  for (const candidate of [...new Set(candidates)]) {
    const executable = resolve(candidate, 'bin/java');
    if (!existsSync(executable)) continue;
    try {
      const runtime = javaRuntime(executable);
      if (runtime) return runtime;
    } catch {
      // Continue through deterministic local candidates before consulting PATH.
    }
  }
  try {
    return javaRuntime('java');
  } catch {
    return null;
  }
}

export function detectAndroidSdk(apiLevel = 36) {
  const candidates = [
    process.env.ANDROID_HOME,
    process.env.ANDROID_SDK_ROOT,
    platform() === 'darwin' ? resolve(homedir(), 'Library/Android/sdk') : null,
    resolve(homedir(), 'Android/Sdk'),
  ].filter((candidate) => typeof candidate === 'string' && candidate.length > 0);
  for (const candidate of [...new Set(candidates)]) {
    if (!isAbsolute(candidate)) continue;
    const androidJar = resolve(candidate, `platforms/android-${apiLevel}/android.jar`);
    if (!existsSync(androidJar)) continue;
    try {
      return {root: realpathSync(candidate), apiLevel};
    } catch {
      // Ignore a disappearing or non-canonical candidate.
    }
  }
  return null;
}

export function detectJavaFeature(javaHome = process.env.JAVA_HOME) {
  return detectJavaRuntime(javaHome)?.feature ?? null;
}

export function diagnostic({
  code,
  severity,
  message,
  nextAction,
  source,
  artifactId,
  capabilityId,
}) {
  return Object.fromEntries(
    Object.entries({
      code,
      severity,
      message,
      nextAction,
      artifactId,
      capabilityId,
      source,
    }).filter(([, value]) => value !== undefined),
  );
}

export async function toolResult({
  requestId,
  tool,
  status,
  level,
  diagnostics = [],
  data,
  elapsedMs = 0,
  cache = 'bypassed',
  truncated = false,
  compilerLane,
  renderLane,
  outputFingerprint,
}) {
  const manifest = await loadKnowledgeManifest();
  return Object.fromEntries(
    Object.entries({
      schemaVersion: 1,
      kind: 'result',
      requestId,
      tool,
      status,
      framework: manifest.framework,
      evidence: Object.fromEntries(
        Object.entries({
          level,
          bundleFingerprint: manifest.bundleFingerprint,
          cache,
          compilerLane,
          renderLane,
          outputFingerprint,
        }).filter(([, value]) => value !== undefined),
      ),
      diagnostics,
      data,
      elapsedMs: Math.max(0, Math.round(elapsedMs)),
      truncated,
    }).filter(([, value]) => value !== undefined),
  );
}

export function sourceLocation(source, path, offset, length = 1) {
  const before = source.slice(0, offset);
  const lines = before.split('\n');
  const startLine = lines.length;
  const startColumn = lines.at(-1).length + 1;
  const selected = source.slice(offset, offset + Math.max(1, length));
  const selectedLines = selected.split('\n');
  return {
    path,
    startLine,
    startColumn,
    endLine: startLine + selectedLines.length - 1,
    endColumn: selectedLines.length === 1
      ? startColumn + selected.length
      : selectedLines.at(-1).length + 1,
  };
}

export function utf8Bytes(value) {
  return Buffer.byteLength(value, 'utf8');
}

export function semanticToolResult(result) {
  const normalized = structuredClone(result);
  delete normalized.elapsedMs;
  return normalized;
}
