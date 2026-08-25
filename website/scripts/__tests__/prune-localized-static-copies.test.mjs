import assert from 'node:assert/strict';
import {access, mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {pruneLocalizedStaticCopies} from '../prune-localized-static-copies.mjs';

test('canonical static assets retain only the root copy', async () => {
  const buildDirectory = await mkdtemp(resolve(tmpdir(), 'viewcompose-static-prune-test-'));
  try {
    const canonicalAsset = resolve(buildDirectory, 'img/social-card.png');
    const localizedAsset = resolve(buildDirectory, 'zh-CN/img/social-card.png');
    await mkdir(resolve(buildDirectory, 'zh-CN/img'), {recursive: true});
    await mkdir(resolve(buildDirectory, 'img'), {recursive: true});
    await writeFile(canonicalAsset, 'canonical', 'utf8');
    await writeFile(localizedAsset, 'duplicate', 'utf8');

    await pruneLocalizedStaticCopies({buildDirectory});

    assert.equal(await readFile(canonicalAsset, 'utf8'), 'canonical');
    await assert.rejects(access(localizedAsset));
  } finally {
    await rm(buildDirectory, {recursive: true, force: true});
  }
});

test('a missing canonical asset fails before deleting its locale copy', async () => {
  const buildDirectory = await mkdtemp(resolve(tmpdir(), 'viewcompose-static-prune-test-'));
  try {
    const localizedAsset = resolve(buildDirectory, 'zh-CN/img/social-card.png');
    await mkdir(resolve(buildDirectory, 'zh-CN/img'), {recursive: true});
    await writeFile(localizedAsset, 'only-copy', 'utf8');

    await assert.rejects(pruneLocalizedStaticCopies({buildDirectory}));

    assert.equal(await readFile(localizedAsset, 'utf8'), 'only-copy');
  } finally {
    await rm(buildDirectory, {recursive: true, force: true});
  }
});
