import {createHash} from 'node:crypto';
import {lstat, readFile, readdir, realpath} from 'node:fs/promises';
import {basename, extname, isAbsolute, relative, resolve, sep} from 'node:path';

const artifactPattern = /^[a-z0-9][a-z0-9-]*$/u;
const excludedNames = new Set([
  '.cache',
  '.codegraph',
  '.docusaurus',
  '.git',
  '.gradle',
  '.idea',
  '.kotlin',
  'build',
  'coverage',
  'dist',
  'local.properties',
  'node_modules',
  'out',
]);
const sourceExtensions = new Set(['.java', '.kt']);
const configurationNames = new Set([
  'build.gradle',
  'build.gradle.kts',
  'settings.gradle',
  'settings.gradle.kts',
  'gradle.properties',
  'gradle.lockfile',
]);

export const DEFAULT_FRAMEWORK_PROFILE_LIMITS = Object.freeze({
  maxFiles: 10_000,
  maxBytes: 32 * 1024 * 1024,
  maxDepth: 20,
  maxFileBytes: 256 * 1024,
});

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function portable(path) {
  return path.split(sep).join('/');
}

function pathEscapes(root, candidate) {
  const relation = relative(root, candidate);
  return relation === '..' || relation.startsWith(`..${sep}`) || isAbsolute(relation);
}

function isRelevantConfiguration(path) {
  const name = basename(path);
  return configurationNames.has(name) || name.endsWith('.versions.toml') || name.endsWith('.lockfile');
}

function isExactVersion(version) {
  return typeof version === 'string' &&
    version.length > 0 &&
    version.length <= 128 &&
    !/[\s$+{}()[\],]/u.test(version) &&
    !/^(?:latest\.|release$|integration$)/iu.test(version);
}

function evidence(path, line, kind) {
  return {path, line, kind};
}

function lineAt(content, offset) {
  return content.slice(0, offset).split('\n').length;
}

function pushDeclaration(declarations, artifact, version, source) {
  if (!artifactPattern.test(artifact)) return;
  const entries = declarations.get(artifact) ?? [];
  entries.push({version: version.trim(), evidence: source});
  declarations.set(artifact, entries);
}

function parseDirectCoordinates(content, path, declarations) {
  const patterns = [
    /["']com\.viewcompose:([a-z0-9][a-z0-9-]*):([^"']+)["']/gu,
    /module\s*=\s*["']com\.viewcompose:([a-z0-9][a-z0-9-]*)["'][^\n]*?version\s*=\s*["']([^"']+)["']/gu,
    /group\s*=\s*["']com\.viewcompose["'][^\n]*?name\s*=\s*["']([a-z0-9][a-z0-9-]*)["'][^\n]*?version\s*=\s*["']([^"']+)["']/gu,
  ];
  for (const pattern of patterns) {
    for (const match of content.matchAll(pattern)) {
      pushDeclaration(
        declarations,
        match[1],
        match[2],
        evidence(path, lineAt(content, match.index), 'gradle-declaration'),
      );
    }
  }
}

function parseLockCoordinates(content, path, declarations) {
  for (const match of content.matchAll(/^com\.viewcompose:([a-z0-9][a-z0-9-]*):([^=\s]+)=.*$/gmu)) {
    pushDeclaration(
      declarations,
      match[1],
      match[2],
      evidence(path, lineAt(content, match.index), 'dependency-lock'),
    );
  }
}

function stripTomlComment(line) {
  let quote = null;
  let escaped = false;
  for (let index = 0; index < line.length; index += 1) {
    const character = line[index];
    if (quote) {
      if (escaped) escaped = false;
      else if (character === '\\') escaped = true;
      else if (character === quote) quote = null;
    } else if (character === '"' || character === "'") {
      quote = character;
    } else if (character === '#') {
      return line.slice(0, index);
    }
  }
  return line;
}

function tomlString(value) {
  const trimmed = value.trim();
  const match = /^(["'])(.*)\1$/u.exec(trimmed);
  return match ? match[2] : null;
}

function inlineTable(value) {
  const trimmed = value.trim();
  if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) return null;
  const body = trimmed.slice(1, -1);
  const parts = [];
  let quote = null;
  let escaped = false;
  let start = 0;
  for (let index = 0; index <= body.length; index += 1) {
    const character = body[index];
    if (quote) {
      if (escaped) escaped = false;
      else if (character === '\\') escaped = true;
      else if (character === quote) quote = null;
    } else if (character === '"' || character === "'") {
      quote = character;
    } else if (character === ',' || index === body.length) {
      parts.push(body.slice(start, index));
      start = index + 1;
    }
  }
  const fields = new Map();
  for (const part of parts) {
    const match = /^\s*([A-Za-z0-9_.-]+)\s*=\s*(.*?)\s*$/u.exec(part);
    if (!match) return null;
    fields.set(match[1], tomlString(match[2]) ?? match[2].trim());
  }
  return fields;
}

function catalogAccessor(alias) {
  return alias.split(/[._-]+/u).filter(Boolean).join('.');
}

function catalogAliasIsUsed(alias, buildText) {
  const accessor = catalogAccessor(alias).replaceAll('.', '\\.');
  const quoted = alias.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&');
  return new RegExp(`\\blibs\\.${accessor}\\b|\\blibs\\s*\\[\\s*["']${quoted}["']\\s*\\]`, 'u')
    .test(buildText);
}

function tomlStringArray(value) {
  const trimmed = value.trim();
  if (!trimmed.startsWith('[') || !trimmed.endsWith(']')) return null;
  const items = trimmed.slice(1, -1).split(',').map((item) => tomlString(item));
  return items.every((item) => item !== null) ? items : null;
}

function parseVersionCatalog(content, path, buildText, declarations) {
  const versions = new Map();
  const libraries = [];
  const bundles = [];
  let section = '';
  const lines = content.replaceAll('\r\n', '\n').split('\n');
  for (let index = 0; index < lines.length; index += 1) {
    const line = stripTomlComment(lines[index]).trim();
    if (line === '') continue;
    const sectionMatch = /^\[([^\u005d]+)\]$/u.exec(line);
    if (sectionMatch) {
      section = sectionMatch[1];
      continue;
    }
    const assignment = /^([A-Za-z0-9_.-]+)\s*=\s*(.+)$/u.exec(line);
    if (!assignment) continue;
    const [, key, value] = assignment;
    if (section === 'versions') {
      const scalar = tomlString(value);
      const table = inlineTable(value);
      const exact = scalar ?? table?.get('strictly') ?? table?.get('require') ?? null;
      if (exact !== null) versions.set(key, exact);
    } else if (section === 'libraries') {
      libraries.push({alias: key, value, line: index + 1});
    } else if (section === 'bundles') {
      bundles.push({alias: key, libraries: tomlStringArray(value) ?? []});
    }
  }

  const bundledAliases = new Set(
    bundles
      .filter((bundle) => catalogAliasIsUsed(`bundles.${bundle.alias}`, buildText))
      .flatMap((bundle) => bundle.libraries),
  );
  for (const library of libraries) {
    if (!catalogAliasIsUsed(library.alias, buildText) && !bundledAliases.has(library.alias)) continue;
    const scalar = tomlString(library.value);
    const table = inlineTable(library.value);
    let artifact;
    let version;
    if (scalar) {
      const match = /^com\.viewcompose:([a-z0-9][a-z0-9-]*):(.+)$/u.exec(scalar);
      if (!match) continue;
      [, artifact, version] = match;
    } else if (table) {
      const module = table.get('module');
      const moduleMatch = /^com\.viewcompose:([a-z0-9][a-z0-9-]*)$/u.exec(module ?? '');
      if (moduleMatch) artifact = moduleMatch[1];
      if (!artifact && table.get('group') === 'com.viewcompose' && artifactPattern.test(table.get('name') ?? '')) {
        artifact = table.get('name');
      }
      version = table.get('version');
      const versionReference = table.get('version.ref');
      if (versionReference !== undefined) version = versions.get(versionReference) ?? `$unresolved:${versionReference}`;
    }
    if (artifact) {
      pushDeclaration(
        declarations,
        artifact,
        version ?? '$unresolved:missing-version',
        evidence(path, library.line, 'version-catalog'),
      );
    }
  }
}

function canonicalArtifacts(declarations) {
  const artifacts = [];
  const unresolved = [];
  const conflicts = [];
  for (const [artifactId, entries] of [...declarations].sort(([left], [right]) => left.localeCompare(right))) {
    const exact = entries.filter((entry) => isExactVersion(entry.version));
    const versions = [...new Set(exact.map((entry) => entry.version))].sort();
    if (versions.length > 1) {
      conflicts.push({
        coordinate: `com.viewcompose:${artifactId}`,
        versions,
        evidence: entries.map((entry) => entry.evidence),
      });
      continue;
    }
    if (versions.length === 0) {
      unresolved.push({
        coordinate: `com.viewcompose:${artifactId}`,
        expressions: [...new Set(entries.map((entry) => entry.version))].sort(),
        evidence: entries.map((entry) => entry.evidence),
      });
      continue;
    }
    artifacts.push({
      coordinate: `com.viewcompose:${artifactId}`,
      version: versions[0],
      evidence: entries
        .filter((entry) => entry.version === versions[0] || entry.evidence.kind === 'dependency-lock')
        .map((entry) => entry.evidence),
    });
  }
  return {artifacts, unresolved, conflicts};
}

export async function detectFrameworkProjectProfile({
  projectRoot,
  limits = DEFAULT_FRAMEWORK_PROFILE_LIMITS,
} = {}) {
  if (typeof projectRoot !== 'string' || !isAbsolute(projectRoot)) {
    throw new Error('Framework profile detection requires one absolute project root.');
  }
  const requestedRoot = resolve(projectRoot);
  const rootMetadata = await lstat(requestedRoot);
  const root = await realpath(requestedRoot);
  if (!rootMetadata.isDirectory() || rootMetadata.isSymbolicLink()) {
    throw new Error('Framework profile detection requires one physical non-symbolic-link project root.');
  }
  const configuredLimits = {...DEFAULT_FRAMEWORK_PROFILE_LIMITS, ...limits};
  for (const [name, maximum] of Object.entries(DEFAULT_FRAMEWORK_PROFILE_LIMITS)) {
    const value = configuredLimits[name];
    if (!Number.isInteger(value) || value < 1 || value > maximum) {
      throw new Error(`Framework profile limit ${name} is invalid.`);
    }
  }

  const configurations = [];
  const catalogs = [];
  let importCount = 0;
  let totalBytes = 0;
  let fileCount = 0;
  let visitedEntries = 0;
  const queue = [{path: root, depth: 0}];
  while (queue.length > 0) {
    const current = queue.shift();
    visitedEntries += 1;
    if (visitedEntries > 100_000) throw new Error('Framework profile traversal exceeded its entry ceiling.');
    if (current.depth > configuredLimits.maxDepth) throw new Error('Framework profile traversal exceeded maxDepth.');
    if (pathEscapes(root, current.path)) throw new Error('Framework profile traversal escaped the project root.');
    const metadata = await lstat(current.path);
    if (metadata.isSymbolicLink()) throw new Error(`Framework profile detection rejects symbolic link ${portable(relative(root, current.path))}.`);
    if (metadata.isDirectory()) {
      const children = await readdir(current.path, {withFileTypes: true});
      for (const child of children.sort((left, right) => left.name.localeCompare(right.name))) {
        if (excludedNames.has(child.name)) continue;
        queue.push({path: resolve(current.path, child.name), depth: current.depth + 1});
      }
      continue;
    }
    if (!metadata.isFile()) continue;
    if (metadata.size > configuredLimits.maxFileBytes) continue;
    const extension = extname(current.path);
    const relevantConfiguration = isRelevantConfiguration(current.path);
    if (!relevantConfiguration && !sourceExtensions.has(extension)) continue;
    fileCount += 1;
    if (fileCount > configuredLimits.maxFiles) throw new Error('Framework profile traversal exceeded maxFiles.');
    totalBytes += metadata.size;
    if (totalBytes > configuredLimits.maxBytes) throw new Error('Framework profile traversal exceeded maxBytes.');
    const content = await readFile(current.path, 'utf8');
    const path = portable(relative(root, current.path));
    if (sourceExtensions.has(extension)) {
      importCount += (content.match(/^\s*import\s+com\.viewcompose\./gmu) ?? []).length;
    }
    if (relevantConfiguration) {
      const record = {path, content};
      if (current.path.endsWith('.versions.toml')) catalogs.push(record);
      else configurations.push(record);
    }
  }

  const declarations = new Map();
  for (const configuration of configurations) {
    parseDirectCoordinates(configuration.content, configuration.path, declarations);
    if (configuration.path.endsWith('.lockfile')) {
      parseLockCoordinates(configuration.content, configuration.path, declarations);
    }
  }
  const buildText = configurations
    .filter((configuration) => /(?:^|\/)build\.gradle(?:\.kts)?$/u.test(configuration.path))
    .map((configuration) => configuration.content)
    .join('\n');
  for (const catalog of catalogs) parseVersionCatalog(catalog.content, catalog.path, buildText, declarations);

  const result = canonicalArtifacts(declarations);
  if (importCount > 0 && result.artifacts.length === 0 && result.unresolved.length === 0 && result.conflicts.length === 0) {
    result.unresolved.push({
      coordinate: null,
      expressions: ['ViewCompose imports found without an exact declared dependency'],
      evidence: [],
    });
  }
  const status = result.conflicts.length > 0
    ? 'conflict'
    : result.unresolved.length > 0
      ? 'unresolved'
      : result.artifacts.length > 0
        ? 'resolved'
        : 'empty';
  const identity = result.artifacts.map(({coordinate, version}) => `${coordinate}:${version}`).join('\n');
  return Object.freeze({
    schemaVersion: 1,
    status,
    projectRoot: root,
    profileFingerprint: status === 'resolved' || status === 'empty' ? sha256(identity) : null,
    artifacts: result.artifacts,
    unresolved: result.unresolved,
    conflicts: result.conflicts,
    signals: {viewComposeImports: importCount, inspectedFiles: fileCount, inspectedBytes: totalBytes},
  });
}
