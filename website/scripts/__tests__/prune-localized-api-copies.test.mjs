import assert from 'node:assert/strict';
import {access, mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {pruneLocalizedApiCopies} from '../prune-localized-api-copies.mjs';

test('localized API copies are removed without deleting locale landing pages', async () => {
  const buildDirectory = await mkdtemp(resolve(tmpdir(), 'viewcompose-api-prune-test-'));
  try {
    await mkdir(resolve(buildDirectory, 'api/artifact/version'), {recursive: true});
    await mkdir(resolve(buildDirectory, 'zh-CN/api/artifact/version'), {recursive: true});
    await writeFile(resolve(buildDirectory, 'api/artifact/version/index.html'), 'canonical', 'utf8');
    await writeFile(resolve(buildDirectory, 'zh-CN/api/artifact/version/index.html'), 'duplicate', 'utf8');
    await writeFile(resolve(buildDirectory, 'zh-CN/api.html'), 'landing', 'utf8');

    await pruneLocalizedApiCopies({buildDirectory});

    assert.equal(
      await readFile(resolve(buildDirectory, 'api/artifact/version/index.html'), 'utf8'),
      'canonical',
    );
    assert.equal(await readFile(resolve(buildDirectory, 'zh-CN/api.html'), 'utf8'), 'landing');
    await assert.rejects(access(resolve(buildDirectory, 'zh-CN/api')));
  } finally {
    await rm(buildDirectory, {recursive: true, force: true});
  }
});
