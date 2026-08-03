import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';

const modulePattern = /^viewcompose-[a-z0-9-]+$/u;
const versionPattern = /^[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?$/u;
const revisionPattern = /^[a-f0-9]{40}$/u;

export function parseJavaProperties(content) {
  const properties = new Map();
  for (const rawLine of content.split(/\r?\n/u)) {
    const line = rawLine.trim();
    if (line.length === 0 || line.startsWith('#') || line.startsWith('!')) continue;
    const separator = line.search(/[=:]/u);
    if (separator < 1) {
      throw new Error(`Invalid properties line: ${rawLine}`);
    }
    const key = line.slice(0, separator).trim();
    const value = line.slice(separator + 1).trim();
    if (properties.has(key)) {
      throw new Error(`Duplicate property: ${key}`);
    }
    properties.set(key, value);
  }
  return properties;
}

function required(properties, key) {
  const value = properties.get(key);
  if (!value) throw new Error(`Missing required property: ${key}`);
  return value;
}

export function isStableRelease(version) {
  const qualifier = version.toLowerCase();
  return !['-alpha', '-beta', '-rc', '-snapshot', '-dev', '-preview', '-eap'].some(
    (marker) => qualifier.includes(marker),
  );
}

export function parseDocumentationReleases({historyContent, publishingContent}) {
  const history = parseJavaProperties(historyContent);
  const publishing = parseJavaProperties(publishingContent);
  if (required(history, 'schema.version') !== '1') {
    throw new Error(`Unsupported documentation release schema: ${history.get('schema.version')}`);
  }
  const releaseCount = Number.parseInt(required(history, 'release.count'), 10);
  if (!Number.isSafeInteger(releaseCount) || releaseCount <= 0) {
    throw new Error('release.count must be a positive integer');
  }

  const entries = [];
  for (let order = 0; order < releaseCount; order += 1) {
    const prefix = `release.${order}`;
    const version = required(history, `${prefix}.version`);
    const sourceRevision = required(history, `${prefix}.sourceRevision`);
    const modules = required(history, `${prefix}.modules`)
      .split(',')
      .map((module) => module.trim())
      .filter(Boolean);
    if (!versionPattern.test(version)) throw new Error(`${prefix}.version is invalid: ${version}`);
    if (!revisionPattern.test(sourceRevision)) {
      throw new Error(`${prefix}.sourceRevision is invalid: ${sourceRevision}`);
    }
    if (modules.length === 0 || new Set(modules).size !== modules.length) {
      throw new Error(`${prefix}.modules must contain unique artifact ids`);
    }
    for (const module of modules) {
      if (!modulePattern.test(module)) throw new Error(`${prefix}.modules is invalid: ${module}`);
      entries.push({order, artifact: module, version, sourceRevision});
    }
  }

  const current = new Map();
  for (const [key, version] of publishing) {
    const match = /^module\.(viewcompose-[a-z0-9-]+)\.version$/u.exec(key);
    if (!match) continue;
    const artifact = match[1];
    const sourceRevision = required(publishing, `module.${artifact}.sourceRevision`);
    current.set(artifact, {version, sourceRevision});
  }
  if (current.size === 0) throw new Error('No published modules were found');

  const seen = new Set();
  for (const entry of entries) {
    if (!current.has(entry.artifact)) {
      throw new Error(`Documentation history contains unknown artifact: ${entry.artifact}`);
    }
    const key = `${entry.artifact}|${entry.version}`;
    if (seen.has(key)) throw new Error(`Duplicate documentation release: ${key}`);
    seen.add(key);
  }
  for (const [artifact, release] of current) {
    const found = entries.some(
      (entry) =>
        entry.artifact === artifact &&
        entry.version === release.version &&
        entry.sourceRevision === release.sourceRevision,
    );
    if (!found) {
      throw new Error(
        `Current publication ${artifact}:${release.version} at ${release.sourceRevision} ` +
          'is missing from immutable documentation history',
      );
    }
  }

  entries.sort((left, right) => left.order - right.order || left.artifact.localeCompare(right.artifact));
  return {entries, current};
}

export async function loadDocumentationReleases(repositoryRoot) {
  const [historyContent, publishingContent] = await Promise.all([
    readFile(resolve(repositoryRoot, 'gradle/viewcompose-documentation-releases.properties'), 'utf8'),
    readFile(resolve(repositoryRoot, 'gradle/viewcompose-publishing.properties'), 'utf8'),
  ]);
  return parseDocumentationReleases({historyContent, publishingContent});
}
