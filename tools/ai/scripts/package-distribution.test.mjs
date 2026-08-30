import assert from 'node:assert/strict';
import {mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
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
    const wrapperEntry = first.manifest.files.find(
      (entry) => entry.path === 'harness/gradle/wrapper/gradle-wrapper.jar',
    );
    const executionContract = first.manifest.files.find(
      (entry) => entry.path === 'contracts/consumer-project-execution.schema.json',
    );
    const releasedManifest = first.manifest.files.find(
      (entry) => entry.path ===
        'generated/released/895ed1e52e5a9735f87e6d996e77ea43ca34cc2e496854408c40772419129064/manifest.json',
    );
    assert.ok(packageEntry?.bytes > 0);
    assert.ok(sbomEntry?.bytes > 0);
    assert.ok(licenseEntry?.bytes > 0);
    assert.ok(wrapperEntry?.bytes > 0);
    assert.ok(executionContract?.bytes > 0);
    assert.ok(releasedManifest?.bytes > 0);
    assert.equal(first.manifest.schemaVersion, 2);
    assert.equal(first.manifest.frameworkProfiles[0].consumerSelectable, true);
    assert.equal(first.manifest.frameworkProfiles[0].knowledge.versionLane, 'released');
    assert.equal(
      first.manifest.frameworkProfileIndex.defaultProfileId,
      first.manifest.frameworkProfiles[0].profileId,
    );
    assert.equal(first.manifest.files.some((entry) => entry.path.includes('node_modules')), false);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('rejects a broad distribution output target', async () => {
  await assert.rejects(createDistribution({outputRoot: resolve('/')}), /dedicated non-root/u);
});

test('removes only a regular superseded generated archive', async () => {
  const root = await mkdtemp(resolve(tmpdir(), 'viewcompose-ai-package-upgrade-'));
  try {
    const output = resolve(root, 'distribution');
    await createDistribution({outputRoot: output});
    await writeFile(resolve(output, 'viewcompose-ai-tooling-0.1.0.tgz'), 'superseded');
    const current = await createDistribution({outputRoot: output});
    await assert.rejects(readFile(resolve(output, 'viewcompose-ai-tooling-0.1.0.tgz')), /ENOENT/u);
    assert.ok((await readFile(current.archivePath)).length > 0);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});
