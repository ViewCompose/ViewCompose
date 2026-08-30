import assert from 'node:assert/strict';
import {mkdtemp, mkdir, readFile, realpath, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {
  activeFrameworkProfile,
  activeKnowledgePath,
  loadReleasedFrameworkProfiles,
  selectReleasedFrameworkProfile,
} from './framework-profile-selection.mjs';

const aiRoot = await realpath(new URL('../', import.meta.url));

test('selects current-source by default and one exact released profile explicitly', () => {
  const source = activeFrameworkProfile({
    aiRoot,
    environment: {VIEWCOMPOSE_FRAMEWORK_PROFILE: 'current-source'},
  });
  assert.equal(source.versionLane, 'current-source');
  assert.equal(activeKnowledgePath('manifest.json', {
    aiRoot,
    environment: {VIEWCOMPOSE_FRAMEWORK_PROFILE: 'current-source'},
  }), resolve(aiRoot, 'generated/current-source/manifest.json'));
  assert.equal(activeFrameworkProfile({aiRoot, environment: {}}).versionLane, 'current-source');
  const released = activeFrameworkProfile({
    aiRoot,
    environment: {
      VIEWCOMPOSE_FRAMEWORK_PROFILE: '895ed1e52e5a9735f87e6d996e77ea43ca34cc2e496854408c40772419129064',
    },
  });
  assert.equal(released.versionLane, 'released');
  assert.equal(released.profileId, '895ed1e52e5a9735f87e6d996e77ea43ca34cc2e496854408c40772419129064');
  assert.throws(
    () => activeFrameworkProfile({
      aiRoot,
      environment: {
        VIEWCOMPOSE_FRAMEWORK_PROFILE: released.profileId,
        VIEWCOMPOSE_SOURCE_ROOT: '/source',
      },
    }),
    /source-bound integration cannot select/u,
  );
});

test('loads an integrity-bound released profile inventory', async () => {
  const inventory = await loadReleasedFrameworkProfiles({aiRoot});
  assert.equal(inventory.profiles.length, 1);
  assert.equal(inventory.profiles[0].profile.profileId, inventory.index.defaultProfileId);
  assert.equal(
    inventory.profiles[0].manifest.bundleFingerprint,
    inventory.profiles[0].profile.knowledge.bundleFingerprint,
  );
});

test('matches every declared artifact exactly and treats an empty project separately', async () => {
  const inventory = await loadReleasedFrameworkProfiles({aiRoot});
  const profile = inventory.profiles[0].profile;
  const dependency = profile.artifacts.find((artifact) => artifact.knowledgeIncluded);
  const resolved = await selectReleasedFrameworkProfile({
    schemaVersion: 1,
    status: 'resolved',
    artifacts: [{coordinate: dependency.coordinate, version: dependency.version}],
  }, {aiRoot});
  assert.equal(resolved.profileId, profile.profileId);
  const empty = await selectReleasedFrameworkProfile({schemaVersion: 1, status: 'empty', artifacts: []}, {aiRoot});
  assert.equal(empty.profileId, profile.profileId);
  await assert.rejects(
    selectReleasedFrameworkProfile({
      schemaVersion: 1,
      status: 'resolved',
      artifacts: [{coordinate: dependency.coordinate, version: '0.0.0-unsupported'}],
    }, {aiRoot}),
    /No released framework profile matches/u,
  );
  await assert.rejects(
    selectReleasedFrameworkProfile({schemaVersion: 1, status: 'unresolved', artifacts: []}, {aiRoot}),
    /versions are unresolved/u,
  );
});

test('rejects a symbolic-link profile root before loading profile bytes', async () => {
  const temporary = await realpath(await mkdtemp(resolve(tmpdir(), 'viewcompose-profile-selection-')));
  try {
    await mkdir(resolve(temporary, 'contracts'), {recursive: true});
    await mkdir(resolve(temporary, 'generated/released'), {recursive: true});
    for (const name of [
      'framework-profile-index.schema.json',
      'framework-compatibility-profile.schema.json',
    ]) {
      await writeFile(
        resolve(temporary, 'contracts', name),
        await readFile(resolve(aiRoot, 'contracts', name)),
      );
    }
    const inventory = await loadReleasedFrameworkProfiles({aiRoot});
    await writeFile(
      resolve(temporary, 'generated/released/index.json'),
      `${JSON.stringify(inventory.index)}\n`,
    );
    const id = inventory.index.defaultProfileId;
    await symlink(
      resolve(aiRoot, 'generated/released', id),
      resolve(temporary, 'generated/released', id),
      'dir',
    );
    await assert.rejects(loadReleasedFrameworkProfiles({aiRoot: temporary}), /profile root is unsafe/u);
  } finally {
    await rm(temporary, {recursive: true, force: true});
  }
});
