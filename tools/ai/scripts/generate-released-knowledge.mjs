#!/usr/bin/env node
import {mkdir, readFile, readdir, rm, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {stableJson, writeKnowledgeBundle} from './knowledge-generator.mjs';
import {buildReleasedKnowledgePack, releasedKnowledgeRoot} from './released-knowledge.mjs';

async function expectedFiles(result) {
  return new Map([
    ...result.bundle.files,
    ['manifest.json', stableJson(result.bundle.manifest)],
    ['profile.json', stableJson(result.profile)],
  ]);
}

async function writeReleasedKnowledge(result) {
  await mkdir(releasedKnowledgeRoot, {recursive: true});
  for (const name of await readdir(releasedKnowledgeRoot)) {
    if (name === 'index.json') continue;
    if (!/^[a-f0-9]{64}$/u.test(name)) {
      throw new Error(`Released Knowledge Pack root contains an unknown entry: ${name}`);
    }
    if (name !== result.profile.profileId) {
      await rm(resolve(releasedKnowledgeRoot, name), {recursive: true, force: true});
    }
  }
  const output = resolve(releasedKnowledgeRoot, result.profile.profileId);
  await writeKnowledgeBundle(result.bundle, output, {writeHosted: false});
  await writeFile(resolve(output, 'profile.json'), stableJson(result.profile));
  await writeFile(resolve(releasedKnowledgeRoot, 'index.json'), stableJson(result.index));
}

async function verifyReleasedKnowledge(result) {
  const rootEntries = await readdir(releasedKnowledgeRoot).catch(() => []);
  if (JSON.stringify(rootEntries.sort()) !== JSON.stringify(['index.json', result.profile.profileId].sort())) {
    throw new Error('Released Knowledge Pack profile inventory is stale.');
  }
  const actualIndex = await readFile(resolve(releasedKnowledgeRoot, 'index.json'), 'utf8').catch(() => null);
  if (actualIndex !== stableJson(result.index)) {
    throw new Error('Released Knowledge Pack index.json is stale.');
  }
  const output = resolve(releasedKnowledgeRoot, result.profile.profileId);
  const expected = await expectedFiles(result);
  const actualNames = await readdir(output).catch(() => []);
  if (JSON.stringify(actualNames.sort()) !== JSON.stringify([...expected.keys()].sort())) {
    throw new Error('Released Knowledge Pack file inventory is stale.');
  }
  for (const [name, content] of expected) {
    const actual = await readFile(resolve(output, name), 'utf8').catch(() => null);
    if (actual !== content) throw new Error(`Released Knowledge Pack ${name} is stale.`);
  }
}

async function main() {
  const command = process.argv.slice(2);
  if (command.length !== 1 || !['--write', '--verify'].includes(command[0])) {
    throw new Error('Usage: generate-released-knowledge.mjs --write|--verify');
  }
  const result = await buildReleasedKnowledgePack();
  if (command[0] === '--write') await writeReleasedKnowledge(result);
  else await verifyReleasedKnowledge(result);
  process.stdout.write(
    `${command[0] === '--write' ? 'Generated' : 'Verified'} released Knowledge Pack ` +
    `${result.profile.profileId} at ${result.anchorRevision}: ` +
    `${result.bundle.manifest.counts.artifacts} artifacts, ` +
    `${result.bundle.manifest.counts.capabilities} capabilities.\n`,
  );
}

const entryPath = process.argv[1] ? resolve(process.argv[1]) : '';
if (entryPath === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`Released Knowledge Pack failed: ${error.message}\n`);
    process.exitCode = 1;
  });
}
