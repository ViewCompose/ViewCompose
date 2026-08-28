import assert from 'node:assert/strict';
import {mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {
  freezeVersionedModuleManuals,
  staticManualMarker,
} from '../freeze-versioned-module-manuals.mjs';

test('versioned manuals become static HTML and discard only their hydration chunks', async () => {
  const root = await mkdtemp(resolve(tmpdir(), 'viewcompose-static-manual-test-'));
  const entry = {artifact: 'viewcompose-runtime', version: '1.0.0'};
  try {
    for (const locale of ['', 'zh-CN']) {
      const page = resolve(
        root,
        ...(locale ? [locale] : []),
        'modules/viewcompose-runtime/1.0.0/index.html',
      );
      const assets = resolve(root, ...(locale ? [locale] : []), 'assets/js');
      await mkdir(resolve(page, '..'), {recursive: true});
      await mkdir(assets, {recursive: true});
      await writeFile(
        page,
        '<html lang="en"><head><script>inline()</script><script src=/assets/js/main.js defer></script></head><body>Manual</body></html>',
        'utf8',
      );
      await writeFile(
        resolve(assets, `manual-${locale || 'en'}.js`),
        `const marker = '${staticManualMarker}';`,
        'utf8',
      );
      await writeFile(resolve(assets, `main-${locale || 'en'}.js`), 'main();', 'utf8');
    }

    const result = await freezeVersionedModuleManuals({
      buildDirectory: root,
      root: '/repository',
      releaseLoader: async () => ({entries: [entry]}),
    });

    assert.deepEqual(result, {manualPages: 2, removedChunks: 2});
    for (const locale of ['', 'zh-CN']) {
      const page = resolve(
        root,
        ...(locale ? [locale] : []),
        'modules/viewcompose-runtime/1.0.0/index.html',
      );
      const html = await readFile(page, 'utf8');
      assert.match(html, new RegExp(`<html ${staticManualMarker}=true`, 'u'));
      assert.match(html, /<script>inline\(\)<\/script>/u);
      assert.doesNotMatch(html, /<script\b[^>]*\bsrc=/u);
      assert.equal(
        await readFile(
          resolve(root, ...(locale ? [locale] : []), 'assets/js', `main-${locale || 'en'}.js`),
          'utf8',
        ),
        'main();',
      );
    }
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

