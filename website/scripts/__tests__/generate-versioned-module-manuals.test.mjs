import assert from 'node:assert/strict';
import {mkdtemp, readFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {
  ensureManualSourceRevisions,
  generateVersionedModuleManuals,
} from '../generate-versioned-module-manuals.mjs';

const firstRevision = '1'.repeat(40);
const secondRevision = '2'.repeat(40);

test('manual source revisions are resolved once before any snapshot is read', async () => {
  const destination = await mkdtemp(resolve(tmpdir(), 'viewcompose-versioned-manual-test-'));
  const available = new Set();
  const ensured = [];
  const entries = [
    {
      artifact: 'viewcompose-runtime',
      version: '0.1.0-alpha01',
      sourceRevision: secondRevision,
      order: 0,
    },
    {
      artifact: 'viewcompose-runtime',
      version: '0.1.0-alpha02',
      sourceRevision: firstRevision,
      order: 1,
    },
    {
      artifact: 'viewcompose-ui-contract',
      version: '0.1.0-alpha01',
      sourceRevision: secondRevision,
      order: 2,
    },
  ];
  try {
    await generateVersionedModuleManuals({
      root: '/test/repository',
      destination,
      releaseLoader: async () => ({entries}),
      ensureRevision: async (revision, {root}) => {
        assert.equal(root, '/test/repository');
        ensured.push(revision);
        available.add(revision);
      },
      readRevisionFile: async (revision) => {
        assert.ok(available.has(revision), `revision ${revision} must be resolved before reading`);
        return '# Historical manual\n';
      },
    });

    assert.deepEqual(ensured, [firstRevision, secondRevision]);
    assert.match(
      await readFile(
        resolve(destination, 'modules/viewcompose-runtime/0.1.0-alpha01.md'),
        'utf8',
      ),
      /Released documentation snapshot/u,
    );
  } finally {
    await rm(destination, {recursive: true, force: true});
  }
});

test('revision resolution deduplicates work deterministically', async () => {
  const ensured = [];
  await ensureManualSourceRevisions(
    [
      {sourceRevision: secondRevision},
      {sourceRevision: firstRevision},
      {sourceRevision: secondRevision},
    ],
    {
      root: '/test/repository',
      ensureRevision: async (revision) => ensured.push(revision),
    },
  );
  assert.deepEqual(ensured, [firstRevision, secondRevision]);
});
