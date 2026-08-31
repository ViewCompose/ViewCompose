import assert from 'node:assert/strict';
import {chmod, mkdir, mkdtemp, readFile, stat, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, resolve} from 'node:path';
import test from 'node:test';
import {
  BOOTSTRAP_VERSION,
  createBootstrapSeed,
  STABLE_VERSION,
  verifyBootstrapSeed,
  VERSION_REPLACEMENTS,
} from './npm-bootstrap-seed.mjs';

async function writeJson(root, path, value) {
  const target = resolve(root, path);
  await mkdir(dirname(target), {recursive: true});
  await writeFile(target, `${JSON.stringify(value, null, 2)}\n`);
}

async function fixture() {
  const root = await mkdtemp(resolve(tmpdir(), 'viewcompose-npm-bootstrap-seed-'));
  const stableRoot = resolve(root, 'stable');
  await mkdir(stableRoot);
  for (const [path, count] of VERSION_REPLACEMENTS) {
    if (path === 'package.json') {
      await writeJson(stableRoot, path, {
        name: '@viewcompose/ai-tooling',
        version: STABLE_VERSION,
        publishConfig: {access: 'public'},
      });
    } else if (path === 'distribution.json') {
      await writeJson(stableRoot, path, {
        package: {name: '@viewcompose/ai-tooling', version: STABLE_VERSION},
      });
    } else {
      await writeJson(stableRoot, path, {versions: Array(count).fill(STABLE_VERSION)});
    }
  }
  await writeFile(resolve(stableRoot, 'README.md'), `Install ${STABLE_VERSION}.\n`);
  await mkdir(resolve(stableRoot, 'scripts'));
  await writeFile(resolve(stableRoot, 'scripts/mcp-server.mjs'), '#!/usr/bin/env node\n');
  await chmod(resolve(stableRoot, 'scripts/mcp-server.mjs'), 0o755);
  return {root, stableRoot, seedRoot: resolve(root, 'seed')};
}

test('creates a seed that changes only frozen version-bearing JSON fields', async () => {
  const {stableRoot, seedRoot} = await fixture();
  const result = await createBootstrapSeed({stableRoot, seedRoot});
  assert.equal(result.files, VERSION_REPLACEMENTS.size + 2);
  assert.equal(JSON.parse(await readFile(resolve(seedRoot, 'package.json'))).version, BOOTSTRAP_VERSION);
  assert.equal(await readFile(resolve(seedRoot, 'README.md'), 'utf8'), `Install ${STABLE_VERSION}.\n`);
  assert.equal((await stat(resolve(seedRoot, 'scripts/mcp-server.mjs'))).mode & 0o777, 0o755);
  await verifyBootstrapSeed({stableRoot, seedRoot});
});

test('rejects a stable package whose frozen replacement count drifted', async () => {
  const {stableRoot, seedRoot} = await fixture();
  await writeJson(stableRoot, 'contracts/bootstrap.schema.json', {version: 'unexpected'});
  await assert.rejects(
    createBootstrapSeed({stableRoot, seedRoot}),
    /occurrence count drifted/u,
  );
});

test('rejects any non-version seed mutation', async () => {
  const {stableRoot, seedRoot} = await fixture();
  await createBootstrapSeed({stableRoot, seedRoot});
  await writeFile(resolve(seedRoot, 'README.md'), 'mutated\n');
  await assert.rejects(
    verifyBootstrapSeed({stableRoot, seedRoot}),
    /non-version file/u,
  );
});
