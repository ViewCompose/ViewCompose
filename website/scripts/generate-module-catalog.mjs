import {mkdir, readFile, writeFile} from 'node:fs/promises';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {loadDocumentationReleases} from './documentation-releases.mjs';

const websiteRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = resolve(websiteRoot, '..');
const catalogPath = resolve(repositoryRoot, 'docs/modules/README.md');
const outputPath = resolve(websiteRoot, 'src/generated/moduleCatalog.json');

const [releases, catalog] = await Promise.all([
  loadDocumentationReleases(repositoryRoot),
  readFile(catalogPath, 'utf8'),
]);

const entries = [];
const rowPattern = /^\|\s*`(viewcompose-[a-z0-9-]+)`\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|$/gmu;
for (const match of catalog.matchAll(rowPattern)) {
  const artifact = match[1];
  const current = releases.current.get(artifact);
  if (!current) {
    throw new Error(`Catalog artifact has no publishing version: ${artifact}`);
  }
  const manual = match[4].trim();
  const expectedManual = `[Available](./${artifact}/README.md)`;
  if (manual !== expectedManual) {
    throw new Error(
      `Published module must link its available manual: ${artifact} -> ${manual}`,
    );
  }
  entries.push({
    artifact,
    version: current.version,
    unpublished: releases.unpublished.has(artifact),
    versions: releases.entries
      .filter((entry) => entry.artifact === artifact)
      .map(({version, sourceRevision}) => ({version, sourceRevision})),
    family: match[2].trim(),
    role: match[3].trim(),
    manual,
  });
}

for (const artifact of releases.retired) {
  const versions = releases.entries
    .filter((entry) => entry.artifact === artifact)
    .map(({version, sourceRevision}) => ({version, sourceRevision}));
  const latest = versions.at(-1);
  if (!latest) {
    throw new Error(`Retired artifact has no immutable documentation history: ${artifact}`);
  }
  entries.push({
    artifact,
    version: latest.version,
    unpublished: false,
    versions,
    family: 'Retired',
    role: 'Superseded coordinate; immutable release history only',
    manual: '',
  });
}

const catalogArtifacts = new Set(entries.map((entry) => entry.artifact));
if (catalogArtifacts.size !== entries.length) {
  throw new Error('Published module catalog contains duplicate artifact rows');
}
const missing = [...releases.current.keys()].filter((artifact) => !catalogArtifacts.has(artifact));
if (missing.length > 0) {
  throw new Error(`Published modules missing from catalog: ${missing.sort().join(', ')}`);
}

entries.sort((left, right) => left.artifact.localeCompare(right.artifact));
await mkdir(dirname(outputPath), {recursive: true});
await writeFile(outputPath, `${JSON.stringify(entries, null, 2)}\n`, 'utf8');
