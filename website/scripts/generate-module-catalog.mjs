import {mkdir, readFile, writeFile} from 'node:fs/promises';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const websiteRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = resolve(websiteRoot, '..');
const publishingPropertiesPath = resolve(
  repositoryRoot,
  'gradle/viewcompose-publishing.properties',
);
const catalogPath = resolve(repositoryRoot, 'docs/modules/README.md');
const outputPath = resolve(websiteRoot, 'src/generated/moduleCatalog.json');

const [publishingProperties, catalog] = await Promise.all([
  readFile(publishingPropertiesPath, 'utf8'),
  readFile(catalogPath, 'utf8'),
]);

const versions = new Map();
for (const line of publishingProperties.split(/\r?\n/u)) {
  const match = /^module\.([a-z0-9-]+)\.version=(.+)$/u.exec(line.trim());
  if (match) {
    versions.set(match[1], match[2]);
  }
}

const entries = [];
const rowPattern = /^\|\s*`(viewcompose-[a-z0-9-]+)`\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|$/gmu;
for (const match of catalog.matchAll(rowPattern)) {
  const artifact = match[1];
  const version = versions.get(artifact);
  if (!version) {
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
    version,
    family: match[2].trim(),
    role: match[3].trim(),
    manual,
  });
}

const catalogArtifacts = new Set(entries.map((entry) => entry.artifact));
if (catalogArtifacts.size !== entries.length) {
  throw new Error('Published module catalog contains duplicate artifact rows');
}
const missing = [...versions.keys()].filter((artifact) => !catalogArtifacts.has(artifact));
if (missing.length > 0) {
  throw new Error(`Published modules missing from catalog: ${missing.sort().join(', ')}`);
}

entries.sort((left, right) => left.artifact.localeCompare(right.artifact));
await mkdir(dirname(outputPath), {recursive: true});
await writeFile(outputPath, `${JSON.stringify(entries, null, 2)}\n`, 'utf8');
