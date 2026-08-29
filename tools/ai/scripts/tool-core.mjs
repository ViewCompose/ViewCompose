import {spawnSync} from 'node:child_process';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const aiRoot = fileURLToPath(new URL('../', import.meta.url));
const repository = resolve(aiRoot, '../..');
const manifestPath = fileURLToPath(
  new URL('../generated/current-source/manifest.json', import.meta.url),
);

let manifestPromise;

export function loadKnowledgeManifest() {
  manifestPromise ??= readFile(manifestPath, 'utf8').then(JSON.parse);
  return manifestPromise;
}

export function aiToolingRoot() {
  return aiRoot;
}

export function repositoryRoot() {
  return repository;
}

export function detectJavaFeature(javaHome = process.env.JAVA_HOME) {
  if (!javaHome) return null;
  const executable = resolve(javaHome, 'bin/java');
  const result = spawnSync(executable, ['-XshowSettings:properties', '-version'], {
    encoding: 'utf8',
  });
  if (result.error || result.status !== 0) return null;
  const output = `${result.stdout ?? ''}\n${result.stderr ?? ''}`;
  const version = /\bjava\.version\s*=\s*([^\s]+)/u.exec(output)?.[1];
  if (!version) return null;
  const feature = Number.parseInt(version.startsWith('1.') ? version.split('.')[1] : version, 10);
  return Number.isInteger(feature) ? feature : null;
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
