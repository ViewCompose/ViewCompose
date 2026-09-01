import {lstat, readFile, readdir, realpath} from 'node:fs/promises';
import {basename, extname, isAbsolute, relative, resolve, sep} from 'node:path';
import {diagnostic, toolResult, utf8Bytes} from './tool-core.mjs';
import {activeKnowledgePath} from './framework-profile-selection.mjs';
import {analyzeKotlinImageCalls, maskNonCode} from './static-validator.mjs';
import {
  buildProjectAnalysis,
  parseSuppressionDirectives,
} from './project-analysis-engine.mjs';

const artifactsPath = activeKnowledgePath('artifacts.json');
const symbolsPath = activeKnowledgePath('symbols.jsonl');

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
const kotlinExtensions = new Set(['.java', '.kt', '.kts']);
const dependencyArtifactPattern = /^[a-z0-9][a-z0-9-]*$/u;
const importedNamePattern = /^com\.viewcompose\.[A-Za-z_][A-Za-z0-9_.]*(?:\.\*)?$/u;
const migrationWidgets = new Set([
  'Button',
  'FrameLayout',
  'ImageButton',
  'ImageView',
  'LinearLayout',
  'RecyclerView',
  'TextView',
]);

let projectKnowledgePromise;

async function loadProjectKnowledge() {
  projectKnowledgePromise ??= Promise.all([
    readFile(artifactsPath, 'utf8').then(JSON.parse),
    readFile(symbolsPath, 'utf8').then((content) => content.trim().split('\n').filter(Boolean).map(JSON.parse)),
  ]).then(([artifactDocument, symbols]) => {
    const artifactVersions = new Map(
      artifactDocument.artifacts.map((artifact) => [artifact.artifact, artifact.version]),
    );
    const imports = new Map();
    const namespaces = new Map();
    for (const symbol of symbols) {
      const importName = `${symbol.namespace}.${symbol.simpleName}`;
      const fact = {
        artifactId: symbol.artifactId,
        capabilityId: symbol.capabilityId,
        symbolId: symbol.symbolId,
      };
      const importFacts = imports.get(importName) ?? [];
      importFacts.push(fact);
      imports.set(importName, importFacts);
      const namespaceFacts = namespaces.get(symbol.namespace) ?? [];
      namespaceFacts.push(fact);
      namespaces.set(symbol.namespace, namespaceFacts);
    }
    return {artifactVersions, imports, namespaces};
  });
  return projectKnowledgePromise;
}

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

function sourcePosition(content, path, offset) {
  const lines = content.slice(0, offset).split('\n');
  return {
    path,
    startLine: lines.length,
    startColumn: lines.at(-1).length + 1,
  };
}

function maskCommentsPreservingStrings(source) {
  const characters = source.split('');
  const mask = (index) => {
    if (characters[index] !== '\n' && characters[index] !== '\r') characters[index] = ' ';
  };
  let index = 0;
  let quote = null;
  let escaped = false;
  while (index < source.length) {
    if (quote !== null) {
      const character = source[index];
      if (escaped) {
        escaped = false;
      } else if (character === '\\') {
        escaped = true;
      } else if (character === quote) {
        quote = null;
      }
      index += 1;
      continue;
    }
    if (source[index] === '"' || source[index] === '\'') {
      quote = source[index];
      index += 1;
      continue;
    }
    if (source.startsWith('//', index)) {
      while (index < source.length && source[index] !== '\n') {
        mask(index);
        index += 1;
      }
      continue;
    }
    if (source.startsWith('/*', index)) {
      let depth = 0;
      while (index < source.length) {
        if (source.startsWith('/*', index)) {
          depth += 1;
          mask(index);
          mask(index + 1);
          index += 2;
        } else if (source.startsWith('*/', index)) {
          depth -= 1;
          mask(index);
          mask(index + 1);
          index += 2;
          if (depth === 0) break;
        } else {
          mask(index);
          index += 1;
        }
      }
      continue;
    }
    index += 1;
  }
  return characters.join('');
}

function pushUnique(map, key, value, identity) {
  const values = map.get(key) ?? [];
  if (!values.some((entry) => identity(entry) === identity(value))) values.push(value);
  map.set(key, values);
}

function parseDependencies(content, path, knowledge, findings, unsupported) {
  const coordinatePatterns = [
    /["']com\.viewcompose:([a-z0-9][a-z0-9-]*):([^"']+)["']/gu,
    /module\s*=\s*["']com\.viewcompose:([a-z0-9][a-z0-9-]*)["'][^\n]*?version\s*=\s*["']([^"']+)["']/gu,
    /group\s*=\s*["']com\.viewcompose["'][^\n]*?name\s*=\s*["']([a-z0-9][a-z0-9-]*)["'][^\n]*?version\s*=\s*["']([^"']+)["']/gu,
  ];
  for (const pattern of coordinatePatterns) {
    for (const match of content.matchAll(pattern)) {
      const artifactId = match[1];
      if (!dependencyArtifactPattern.test(artifactId)) continue;
      const version = match[2];
      if (/[$<{]/u.test(version)) {
        unsupported.push({
          kind: 'dynamic-dependency',
          reason: 'The ViewCompose dependency version is dynamic or unresolved.',
          source: sourcePosition(content, path, match.index),
        });
      }
      pushUnique(findings.dependencies, artifactId, {
        artifactId,
        version,
        expectedCurrentVersion: knowledge.artifactVersions.get(artifactId) ?? null,
        path,
        ...sourcePosition(content, path, match.index),
      }, (entry) => `${entry.version}:${entry.path}:${entry.startLine}`);
    }
  }
  for (const match of content.matchAll(/project\(\s*["']:([a-z0-9:-]+)["']\s*\)/gu)) {
    const artifactId = match[1].split(':').filter(Boolean).at(-1);
    if (!knowledge.artifactVersions.has(artifactId)) continue;
    pushUnique(findings.dependencies, artifactId, {
      artifactId,
      version: 'current-project',
      expectedCurrentVersion: knowledge.artifactVersions.get(artifactId),
      path,
      ...sourcePosition(content, path, match.index),
      }, (entry) => `${entry.version}:${entry.path}:${entry.startLine}`);
  }
  for (const match of content.matchAll(/["'](com\.viewcompose:[^"']*)["']/gu)) {
    if (/^com\.viewcompose:[a-z0-9][a-z0-9-]*:[^\s:]+$/u.test(match[1])) continue;
    unsupported.push({
      kind: 'dynamic-dependency',
      reason: 'The ViewCompose dependency coordinate is not one complete literal group, artifact, and version.',
      source: sourcePosition(content, path, match.index),
    });
  }
}

function parseImports(content, path, knowledge, findings, unsupported) {
  for (const match of content.matchAll(/^\s*import\s+(com\.viewcompose\.[A-Za-z_][A-Za-z0-9_.]*(?:\.\*)?)(?:\s+as\s+([A-Za-z_][A-Za-z0-9_]*))?/gmu)) {
    const importName = match[1];
    if (!importedNamePattern.test(importName)) continue;
    if (match[2]) {
      unsupported.push({
        kind: 'alias',
        reason: 'Aliased ViewCompose imports require semantic name resolution.',
        source: sourcePosition(content, path, match.index),
      });
      continue;
    }
    if (importName.endsWith('.*')) {
      unsupported.push({
        kind: 'star-import',
        reason: 'Star imports do not prove which ViewCompose symbols are used.',
        source: sourcePosition(content, path, match.index),
      });
      continue;
    }
    let facts;
    let resolution;
    facts = knowledge.imports.get(importName);
    if (facts) {
      resolution = 'governed-symbol';
    } else {
      facts = knowledge.namespaces.get(importName.slice(0, importName.lastIndexOf('.')));
      resolution = facts ? 'supporting-symbol' : 'unknown-namespace';
    }
    const uniqueFacts = facts ? [...new Map(facts.map((fact) => [
      `${fact.artifactId}:${fact.capabilityId}:${fact.symbolId}`,
      fact,
    ])).values()] : [];
    const entry = {
      importName,
      resolution,
      artifactIds: [...new Set(uniqueFacts.map((fact) => fact.artifactId))].sort(),
      capabilityIds: resolution === 'governed-symbol'
        ? [...new Set(uniqueFacts.map((fact) => fact.capabilityId))].sort()
        : [],
      symbolIds: resolution === 'governed-symbol'
        ? [...new Set(uniqueFacts.map((fact) => fact.symbolId))].sort()
        : [],
      path,
      ...sourcePosition(content, path, match.index),
    };
    findings.imports.push(entry);
    if (resolution === 'unknown-namespace') findings.unknownImports.push(entry);
  }
}

function parseConfiguration(content, path, findings) {
  for (const [field, pattern] of Object.entries({
    compileSdk: /\bcompileSdk(?:Version)?\s*(?:=\s*)?(\d+)/gu,
    minSdk: /\bminSdk(?:Version)?\s*(?:=\s*)?(\d+)/gu,
    targetSdk: /\btargetSdk(?:Version)?\s*(?:=\s*)?(\d+)/gu,
  })) {
    for (const match of content.matchAll(pattern)) {
      findings.configuration.push({
        field,
        value: Number(match[1]),
        path,
        ...sourcePosition(content, path, match.index),
      });
    }
  }
}

function parseMigrationSignals(content, path, extension, findings) {
  if (extension === '.xml') {
    const widgets = [];
    for (const match of content.matchAll(/<\s*(?:[A-Za-z0-9_.]+\.)?([A-Z][A-Za-z0-9_]*)\b/gu)) {
      if (migrationWidgets.has(match[1])) widgets.push(match[1]);
    }
    if (widgets.length > 0) {
      findings.migrations.androidXml.push({path, widgets: [...new Set(widgets)].sort()});
    }
  }
  if (kotlinExtensions.has(extension)) {
    const composeImports = [...content.matchAll(/^\s*import\s+(androidx\.compose\.[A-Za-z0-9_.]+)/gmu)]
      .map((match) => match[1]);
    if (composeImports.length > 0) {
      findings.migrations.jetpackCompose.push({
        path,
        imports: [...new Set(composeImports)].sort(),
      });
    }
    if (/@(?:com\.viewcompose\.preview\.tooling\.)?ViewComposePreview\b/u.test(content)) {
      findings.previewSources.push(path);
    }
  }
}

function finalizeFindings(findings, knowledge) {
  const dependencies = [...findings.dependencies.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .flatMap(([, entries]) => entries.sort((left, right) =>
      `${left.path}:${left.startLine}`.localeCompare(`${right.path}:${right.startLine}`)));
  const declaredArtifacts = new Set(dependencies.map((entry) => entry.artifactId));
  const usedArtifacts = new Set(findings.imports.flatMap((entry) => entry.artifactIds));
  const capabilityUsage = new Map();
  for (const entry of findings.imports) {
    for (const capabilityId of entry.capabilityIds) {
      const usage = capabilityUsage.get(capabilityId) ?? {capabilityId, imports: [], paths: []};
      usage.imports.push(entry.importName);
      usage.paths.push(entry.path);
      capabilityUsage.set(capabilityId, usage);
    }
  }
  const versionLanes = dependencies.map((entry) => ({
    ...entry,
    state: entry.version === 'current-project' || entry.version === entry.expectedCurrentVersion
      ? 'current-bundle'
      : /[$<{]|\.ref$/u.test(entry.version)
        ? 'unresolved-expression'
        : 'different-from-current-bundle',
  }));
  return {
    dependencies: versionLanes,
    imports: findings.imports.sort((left, right) =>
      `${left.path}:${left.startLine}:${left.importName}`
        .localeCompare(`${right.path}:${right.startLine}:${right.importName}`)),
    artifacts: [...usedArtifacts].sort().map((artifactId) => ({
      artifactId,
      expectedCurrentVersion: knowledge.artifactVersions.get(artifactId) ?? null,
      declared: declaredArtifacts.has(artifactId),
    })),
    capabilities: [...capabilityUsage.values()].sort((left, right) =>
      left.capabilityId.localeCompare(right.capabilityId)).map((entry) => ({
      capabilityId: entry.capabilityId,
      imports: [...new Set(entry.imports)].sort(),
      paths: [...new Set(entry.paths)].sort(),
    })),
    unknownImports: findings.unknownImports,
    configuration: findings.configuration.sort((left, right) =>
      `${left.path}:${left.startLine}:${left.field}`
        .localeCompare(`${right.path}:${right.startLine}:${right.field}`)),
    migrations: findings.migrations,
    previewSources: [...new Set(findings.previewSources)].sort(),
  };
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
  if (
    spec.excluded !== undefined &&
    (!Array.isArray(spec.excluded) ||
      spec.excluded.length > 100 ||
      spec.excluded.some((value) => typeof value !== 'string' || value.length === 0 || value.length > 128))
  ) {
    return securityFailure({
      requestId,
      code: 'VC-AI-PROJECT-EXCLUSION-INVALID',
      message: 'Project exclusions must be a bounded array of non-empty file names or suffixes.',
      nextAction: 'Use at most 100 simple names or patterns such as *.generated.kt.',
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
    if (isSecretPath(candidate)) {
      return securityFailure({
        requestId,
        code: 'VC-AI-SECRET-PATH-DENIED',
        message: 'Project analysis refuses direct access to a secret-bearing path.',
        nextAction: 'Analyze source and configuration declarations without credential files.',
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
    {projectRoot, requestedPath, readOnly: true, limits: requestedLimits, excluded},
    {requestId},
  );
  if (preflight.status !== 'success') return preflight;
  const limits = preflight.data.limits;
  const knowledge = await loadProjectKnowledge();
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
  const collectedFindings = {
    dependencies: new Map(),
    imports: [],
    unknownImports: [],
    configuration: [],
    migrations: {androidXml: [], jetpackCompose: []},
    previewSources: [],
  };
  const imageOpportunities = [];
  const suppressionDirectives = [];
  const unsupported = [];
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
    if (isExcluded(current.path, excluded)) {
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
      const codeContent = kotlinExtensions.has(extension) ? maskNonCode(content) : content;
      const dependencyContent = maskCommentsPreservingStrings(content);
      signals.viewComposeImports += (content.match(/\b(?:import|implementation)\b[^\n]*\bcom\.viewcompose\b/gu) ?? []).length;
      parseDependencies(dependencyContent, projectPath, knowledge, collectedFindings, unsupported);
      parseImports(codeContent, projectPath, knowledge, collectedFindings, unsupported);
      parseConfiguration(codeContent, projectPath, collectedFindings);
      parseMigrationSignals(codeContent, projectPath, extension, collectedFindings);
      if (extension === '.kt' || extension === '.kts') {
        const imageAnalysis = await analyzeKotlinImageCalls({source: content, path: projectPath});
        imageOpportunities.push(...imageAnalysis.opportunities);
        unsupported.push(...imageAnalysis.unsupported);
        suppressionDirectives.push(...parseSuppressionDirectives(content, projectPath));
      }
    }
  }
  files.sort((left, right) => left.path < right.path ? -1 : left.path > right.path ? 1 : 0);
  const findings = finalizeFindings(collectedFindings, knowledge);
  signals.viewComposeArtifacts = findings.artifacts.length;
  signals.viewComposeCapabilities = findings.capabilities.length;
  signals.migrationFiles = findings.migrations.androidXml.length + findings.migrations.jetpackCompose.length;
  const evaluated = await buildProjectAnalysis({
    inventory: findings,
    scan: {
      files: files.length,
      bytes: totalBytes,
      kotlinFiles: signals.kotlinFiles,
      gradleFiles: signals.gradleFiles,
      xmlFiles: signals.xmlFiles,
      excludedEntries: excludedCount,
    },
    imageOpportunities,
    suppressionDirectives,
    unsupported,
  });
  const diagnostics = evaluated.diagnostics;
  const data = {
    projectRoot: root,
    readOnly: true,
    files,
    totalBytes,
    excludedCount,
    signals,
    findings,
    analysis: evaluated.analysis,
    limits,
  };
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
    })] : diagnostics,
    data: serializedBytes > limits.maxOutputBytes ? undefined : data,
    elapsedMs: performance.now() - started,
    truncated: serializedBytes > limits.maxOutputBytes,
  });
}
