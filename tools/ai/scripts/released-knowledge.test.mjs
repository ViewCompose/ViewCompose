import assert from 'node:assert/strict';
import test from 'node:test';
import {
  buildReleasedKnowledgePack,
  ensureReleaseRevisionAvailable,
  parseProperties,
} from './released-knowledge.mjs';

test('builds the exact consumer-selectable released Knowledge Pack and Artifact profile', async () => {
  const first = await buildReleasedKnowledgePack();
  const second = await buildReleasedKnowledgePack();
  assert.deepEqual(first.profile, second.profile);
  assert.equal(
    first.profile.profileId,
    '895ed1e52e5a9735f87e6d996e77ea43ca34cc2e496854408c40772419129064',
  );
  assert.equal(first.profile.consumerSelectable, true);
  assert.equal(first.index.defaultProfileId, first.profile.profileId);
  assert.deepEqual(first.index.profiles.map((profile) => profile.profileId), [first.profile.profileId]);
  assert.equal(first.profile.artifacts.length, 38);
  assert.equal(first.profile.artifacts.filter((artifact) => artifact.knowledgeIncluded).length, 30);
  assert.equal(first.bundle.manifest.framework.versionLane, 'released');
  assert.equal(first.bundle.manifest.framework.identity, first.profile.profileId);
  assert.equal(first.bundle.manifest.bundleFingerprint, first.profile.knowledge.bundleFingerprint);
  assert.equal(first.bundle.manifest.counts.capabilities, 70);
  assert.equal(first.anchorRevision, '2d37ff2e9544831e9209ef9eeadbb24f123cbd71');
});

test('rejects duplicate and malformed release properties', () => {
  assert.throws(() => parseProperties('value=one\nvalue=two\n'), /Duplicate release property/u);
  assert.throws(() => parseProperties('missing-separator\n'), /Malformed release property/u);
});

test('loads an exact missing release revision for shallow checkouts', async () => {
  const revision = 'a'.repeat(40);
  const calls = [];
  let fetched = false;
  const runGit = async (argumentsList, root) => {
    calls.push({argumentsList, root});
    if (argumentsList[0] === 'cat-file' && !fetched) throw new Error('missing object');
    if (argumentsList[0] === 'fetch') fetched = true;
    return '';
  };

  await ensureReleaseRevisionAvailable(revision, {root: '/checkout', runGit});

  assert.deepEqual(calls, [
    {argumentsList: ['cat-file', '-e', `${revision}^{commit}`], root: '/checkout'},
    {
      argumentsList: ['fetch', '--no-tags', '--depth=1', 'origin', revision],
      root: '/checkout',
    },
    {argumentsList: ['cat-file', '-e', `${revision}^{commit}`], root: '/checkout'},
  ]);
});

test('does not fetch a release revision that is already available', async () => {
  const revision = 'b'.repeat(40);
  const calls = [];
  await ensureReleaseRevisionAvailable(revision, {
    runGit: async (argumentsList) => calls.push(argumentsList),
  });
  assert.deepEqual(calls, [['cat-file', '-e', `${revision}^{commit}`]]);
});
