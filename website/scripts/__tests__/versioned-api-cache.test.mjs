import assert from 'node:assert/strict';
import {mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {
  VERSIONED_API_INTEGRITY_MANIFEST,
  createRevisionIntegrityRecord,
  createVersionedApiCachePlan,
  pruneVersionedApiOutput,
  readVersionedApiIntegrityManifest,
  summarizeVersionedApiCacheRestore,
  validateRevisionIntegrity,
  writeVersionedApiIntegrityManifest,
} from '../versioned-api-cache.mjs';

const firstRevision = '1'.repeat(40);
const secondRevision = '2'.repeat(40);
const generatorFingerprint = 'a'.repeat(64);

function entry(artifact, version, sourceRevision = firstRevision) {
  return {artifact, version, sourceRevision};
}

async function withTemporaryOutput(operation) {
  const root = await mkdtemp(resolve(tmpdir(), 'viewcompose-api-cache-test-'));
  try {
    await operation(root);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
}

async function writeApiTree(root, artifact, version, content = 'api') {
  const destination = resolve(root, artifact, version);
  await mkdir(resolve(destination, 'nested'), {recursive: true});
  await writeFile(resolve(destination, 'index.html'), `<html>${content}</html>`, 'utf8');
  await writeFile(resolve(destination, 'nested/symbol.html'), content, 'utf8');
}

test('cache plan is deterministic and split by immutable source revision', async () => {
  const entries = [
    entry('viewcompose-runtime', '0.1.0-alpha02', secondRevision),
    entry('viewcompose-ui', '0.1.0-alpha01'),
    entry('viewcompose-runtime', '0.1.0-alpha01'),
  ];
  const first = await createVersionedApiCachePlan({entries, generatorFingerprint});
  const second = await createVersionedApiCachePlan({
    entries: [...entries].reverse(),
    generatorFingerprint,
  });

  assert.deepEqual(first, second);
  assert.equal(first.revisions.length, 2);
  assert.deepEqual(first.revisions.map(({revision}) => revision), [firstRevision, secondRevision]);
  assert.match(first.completeFingerprint, /^[a-f0-9]{64}$/u);
});

test('a complete revision record reuses exact output and detects content corruption', async () => {
  await withTemporaryOutput(async (outputRoot) => {
    const plan = await createVersionedApiCachePlan({
      entries: [entry('viewcompose-runtime', '0.1.0-alpha01')],
      generatorFingerprint,
    });
    const revision = plan.revisions[0];
    await writeApiTree(outputRoot, 'viewcompose-runtime', '0.1.0-alpha01');
    const record = await createRevisionIntegrityRecord(outputRoot, revision);
    await writeVersionedApiIntegrityManifest(outputRoot, plan, [record]);

    const manifest = await readVersionedApiIntegrityManifest(outputRoot);
    const hit = await validateRevisionIntegrity(outputRoot, revision, manifest);
    assert.equal(hit.status, 'hit');

    await writeFile(
      resolve(outputRoot, 'viewcompose-runtime/0.1.0-alpha01/nested/symbol.html'),
      'corrupt',
      'utf8',
    );
    const corrupt = await validateRevisionIntegrity(outputRoot, revision, manifest);
    assert.equal(corrupt.status, 'invalid');
    assert.match(corrupt.reason, /(size|digest) changed/u);
  });
});

test('missing extra and symbolic-link files cannot satisfy integrity', async (context) => {
  if (process.platform === 'win32') context.skip('symbolic-link permissions differ on Windows');
  const {symlink} = await import('node:fs/promises');
  await withTemporaryOutput(async (outputRoot) => {
    const plan = await createVersionedApiCachePlan({
      entries: [entry('viewcompose-runtime', '0.1.0-alpha01')],
      generatorFingerprint,
    });
    const revision = plan.revisions[0];
    await writeApiTree(outputRoot, 'viewcompose-runtime', '0.1.0-alpha01');
    const record = await createRevisionIntegrityRecord(outputRoot, revision);
    await writeVersionedApiIntegrityManifest(outputRoot, plan, [record]);
    const manifest = await readVersionedApiIntegrityManifest(outputRoot);

    await writeFile(
      resolve(outputRoot, 'viewcompose-runtime/0.1.0-alpha01/extra.html'),
      'extra',
      'utf8',
    );
    assert.equal(
      (await validateRevisionIntegrity(outputRoot, revision, manifest)).status,
      'invalid',
    );
    await rm(resolve(outputRoot, 'viewcompose-runtime/0.1.0-alpha01/extra.html'));
    await rm(resolve(outputRoot, 'viewcompose-runtime/0.1.0-alpha01/nested/symbol.html'));
    assert.equal(
      (await validateRevisionIntegrity(outputRoot, revision, manifest)).status,
      'invalid',
    );
    await symlink(
      resolve(outputRoot, 'viewcompose-runtime/0.1.0-alpha01/index.html'),
      resolve(outputRoot, 'viewcompose-runtime/0.1.0-alpha01/nested/symbol.html'),
    );
    const symbolic = await validateRevisionIntegrity(outputRoot, revision, manifest);
    assert.equal(symbolic.status, 'invalid');
    assert.match(symbolic.reason, /symbolic link/u);
  });
});

test('a new revision reuses prior groups while a tooling change makes them stale', async () => {
  await withTemporaryOutput(async (outputRoot) => {
    const initial = await createVersionedApiCachePlan({
      entries: [entry('viewcompose-runtime', '0.1.0-alpha01')],
      generatorFingerprint,
    });
    await writeApiTree(outputRoot, 'viewcompose-runtime', '0.1.0-alpha01');
    const record = await createRevisionIntegrityRecord(outputRoot, initial.revisions[0]);
    await writeVersionedApiIntegrityManifest(outputRoot, initial, [record]);
    const manifest = await readVersionedApiIntegrityManifest(outputRoot);

    const expanded = await createVersionedApiCachePlan({
      entries: [
        entry('viewcompose-runtime', '0.1.0-alpha01'),
        entry('viewcompose-runtime', '0.1.0-alpha02', secondRevision),
      ],
      generatorFingerprint,
    });
    assert.equal(
      (await validateRevisionIntegrity(outputRoot, expanded.revisions[0], manifest)).status,
      'hit',
    );
    assert.equal(
      (await validateRevisionIntegrity(outputRoot, expanded.revisions[1], manifest)).status,
      'miss',
    );

    const toolingChanged = await createVersionedApiCachePlan({
      entries: [entry('viewcompose-runtime', '0.1.0-alpha01')],
      generatorFingerprint: 'b'.repeat(64),
    });
    assert.equal(
      (await validateRevisionIntegrity(outputRoot, toolingChanged.revisions[0], manifest)).status,
      'stale',
    );
  });
});

test('malformed manifests and traversal paths fail closed', async () => {
  await withTemporaryOutput(async (outputRoot) => {
    const plan = await createVersionedApiCachePlan({
      entries: [entry('viewcompose-runtime', '0.1.0-alpha01')],
      generatorFingerprint,
    });
    const revision = plan.revisions[0];
    await writeApiTree(outputRoot, 'viewcompose-runtime', '0.1.0-alpha01');
    await writeFile(resolve(outputRoot, VERSIONED_API_INTEGRITY_MANIFEST), '{broken', 'utf8');
    const malformed = await readVersionedApiIntegrityManifest(outputRoot);
    assert.equal((await validateRevisionIntegrity(outputRoot, revision, malformed)).status, 'invalid');

    await writeFile(
      resolve(outputRoot, VERSIONED_API_INTEGRITY_MANIFEST),
      `${JSON.stringify({
        schemaVersion: 1,
        revisions: [{
          revision: firstRevision,
          fingerprint: revision.fingerprint,
          entries: revision.entries,
          files: [{path: '../outside', size: 1, sha256: 'c'.repeat(64)}],
        }],
      })}\n`,
      'utf8',
    );
    const traversal = await readVersionedApiIntegrityManifest(outputRoot);
    const result = await validateRevisionIntegrity(outputRoot, revision, traversal);
    assert.equal(result.status, 'invalid');
    assert.match(result.reason, /malformed/u);
  });
});

test('output pruning preserves only selected immutable versions and removes aliases', async () => {
  await withTemporaryOutput(async (outputRoot) => {
    await writeApiTree(outputRoot, 'viewcompose-runtime', '0.1.0-alpha01');
    await writeApiTree(outputRoot, 'viewcompose-runtime', 'obsolete');
    await mkdir(resolve(outputRoot, 'viewcompose-runtime/current'), {recursive: true});
    await mkdir(resolve(outputRoot, 'unexpected/0.1.0'), {recursive: true});
    await writeFile(resolve(outputRoot, 'manifest.json'), 'stale', 'utf8');

    await pruneVersionedApiOutput(
      outputRoot,
      [entry('viewcompose-runtime', '0.1.0-alpha01')],
      ['viewcompose-runtime'],
    );

    assert.match(
      await readFile(resolve(outputRoot, 'viewcompose-runtime/0.1.0-alpha01/index.html'), 'utf8'),
      /api/u,
    );
    await assert.rejects(readFile(resolve(outputRoot, 'viewcompose-runtime/obsolete/index.html')));
    await assert.rejects(readFile(resolve(outputRoot, 'viewcompose-runtime/current/index.html')));
    await assert.rejects(readFile(resolve(outputRoot, 'manifest.json')));
  });
});

test('integrity state remains outside the deployable API tree', async () => {
  await withTemporaryOutput(async (root) => {
    const outputRoot = resolve(root, 'generated/api');
    const stateRoot = resolve(root, 'build/versioned-api-cache');
    const plan = await createVersionedApiCachePlan({
      entries: [entry('viewcompose-runtime', '0.1.0-alpha01')],
      generatorFingerprint,
    });
    await writeApiTree(outputRoot, 'viewcompose-runtime', '0.1.0-alpha01');
    const record = await createRevisionIntegrityRecord(outputRoot, plan.revisions[0]);
    await writeVersionedApiIntegrityManifest(stateRoot, plan, [record]);

    await pruneVersionedApiOutput(
      outputRoot,
      [entry('viewcompose-runtime', '0.1.0-alpha01')],
      ['viewcompose-runtime'],
    );

    assert.equal((await readVersionedApiIntegrityManifest(stateRoot)).problem, null);
    await assert.rejects(readFile(resolve(outputRoot, VERSIONED_API_INTEGRITY_MANIFEST)));
  });
});

test('cache save decision trusts only a clean matching-fingerprint restore', () => {
  const base = {reusedGroups: 5, generatedGroups: 0, invalidGroups: 0};
  assert.deepEqual(
    summarizeVersionedApiCacheRestore(base, 'api-generator-complete-123', 'api-generator-complete-'),
    {status: 'hit', saveRequired: false},
  );
  assert.deepEqual(
    summarizeVersionedApiCacheRestore(
      {...base, reusedGroups: 4, generatedGroups: 1},
      'api-generator-complete-123',
      'api-generator-complete-',
    ),
    {status: 'partial', saveRequired: true},
  );
  assert.deepEqual(
    summarizeVersionedApiCacheRestore(
      {...base, reusedGroups: 4, generatedGroups: 1, invalidGroups: 1},
      'api-generator-complete-123',
      'api-generator-complete-',
    ),
    {status: 'recovered', saveRequired: true},
  );
});
