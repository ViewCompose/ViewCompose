import assert from 'node:assert/strict';
import {mkdtemp, readFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {createDistribution} from './package-distribution.mjs';

test('creates an exact reproducible dependency-free npm distribution', async () => {
  const root = await mkdtemp(resolve(tmpdir(), 'viewcompose-ai-package-test-'));
  try {
    const first = await createDistribution({outputRoot: resolve(root, 'first')});
    const second = await createDistribution({outputRoot: resolve(root, 'second')});
    assert.equal(first.manifest.archive.sha256, second.manifest.archive.sha256);
    assert.deepEqual(first.manifest.files, second.manifest.files);
    assert.equal((await readFile(first.archivePath)).equals(await readFile(second.archivePath)), true);

    const packageEntry = first.manifest.files.find((entry) => entry.path === 'package.json');
    const sbomEntry = first.manifest.files.find((entry) => entry.path === 'sbom.spdx.json');
    const licenseEntry = first.manifest.files.find((entry) => entry.path === 'LICENSE');
    assert.ok(packageEntry?.bytes > 0);
    assert.ok(sbomEntry?.bytes > 0);
    assert.ok(licenseEntry?.bytes > 0);
    assert.equal(first.manifest.files.some((entry) => entry.path.includes('node_modules')), false);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('rejects a broad distribution output target', async () => {
  await assert.rejects(createDistribution({outputRoot: resolve('/')}), /dedicated non-root/u);
});
