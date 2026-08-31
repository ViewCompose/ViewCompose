import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdir, mkdtemp, readFile, realpath, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {
  boundedReleaseRequest,
  compareToolingVersions,
  downloadCompatibleCandidate,
  npmInvocation,
  selectCompatibleUpgradeCandidate,
  upgradeAgentClient,
  verifyInstalledCandidate,
} from './tooling-upgrade.mjs';

test('uses the Node entry point for npm command shims on every platform', () => {
  assert.deepEqual(npmInvocation('/opt/npm-cli.js'), {
    executable: process.execPath,
    arguments: ['/opt/npm-cli.js'],
  });
  assert.deepEqual(npmInvocation(''), {executable: 'npm', arguments: []});
});

const aiRoot = await realpath(new URL('../', import.meta.url));
const profile = JSON.parse(await readFile(
  resolve(
    aiRoot,
    'generated/released/895ed1e52e5a9735f87e6d996e77ea43ca34cc2e496854408c40772419129064/profile.json',
  ),
  'utf8',
));

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function release(version) {
  return {
    tag_name: `ai-tooling-v${version}`,
    draft: false,
    prerelease: false,
    assets: [
      {name: `viewcompose-ai-tooling-${version}.tgz`, browser_download_url: `https://github.com/archive-${version}`},
      {name: 'manifest.json', browser_download_url: `https://github.com/manifest-${version}`},
      {name: 'SHA256SUMS', browser_download_url: `https://github.com/checksums-${version}`},
    ],
  };
}

function manifest(version, frameworkProfile = profile, archive = Buffer.from(`archive-${version}`)) {
  return {
    schemaVersion: 2,
    package: {name: '@viewcompose/ai-tooling', version},
    compatibility: {
      agentClientIntegration: 5,
      frameworkCompatibilityProfile: 1,
      frameworkProfileIndex: 1,
    },
    frameworkProfileIndex: {
      schemaVersion: 1,
      defaultProfileId: frameworkProfile.profileId,
      profiles: [{
        profileId: frameworkProfile.profileId,
        bundlePath: frameworkProfile.profileId,
        profilePath: `${frameworkProfile.profileId}/profile.json`,
      }],
    },
    frameworkProfiles: [frameworkProfile],
    archive: {
      path: `viewcompose-ai-tooling-${version}.tgz`,
      bytes: archive.length,
      sha256: sha256(archive),
    },
    files: [{path: 'package.json', bytes: 1, sha256: 'a'.repeat(64)}],
  };
}

test('orders stable tooling versions without treating recency as framework compatibility', () => {
  assert.ok(compareToolingVersions('0.4.0', '0.3.9') > 0);
  assert.equal(compareToolingVersions('1.2.3', '1.2.3'), 0);
  assert.throws(() => compareToolingVersions('latest', '1.0.0'), /semantic versions/u);
});

test('allows the bounded GitHub asset redirect host and rejects inventory/path drift', async () => {
  const originalFetch = globalThis.fetch;
  try {
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      url: 'https://release-assets.githubusercontent.com/release-asset',
      headers: {get: () => '2'},
      arrayBuffer: async () => Buffer.from('ok'),
    });
    assert.equal(
      Buffer.from(await boundedReleaseRequest('https://github.com/release-asset', 2, 'asset')).toString(),
      'ok',
    );
  } finally {
    globalThis.fetch = originalFetch;
  }

  const extraAsset = release('0.4.0');
  extraAsset.assets.push({name: 'unexpected', browser_download_url: 'https://github.com/unexpected'});
  assert.equal(await selectCompatibleUpgradeCandidate({
    releases: [extraAsset],
    currentVersion: '0.3.0',
    projectProfile: {status: 'empty', artifacts: []},
  }), null);

  const unsafe = manifest('0.4.0');
  unsafe.files[0].path = '../package.json';
  await assert.rejects(selectCompatibleUpgradeCandidate({
    releases: [release('0.4.0')],
    currentVersion: '0.3.0',
    projectProfile: {status: 'empty', artifacts: []},
    request: async () => Buffer.from(JSON.stringify(unsafe)),
  }), /invalid sidecar manifest|inconsistent framework profiles/u);
});

test('accepts only the exact installed Package file inventory', async () => {
  const packageRoot = await realpath(await mkdtemp(resolve(tmpdir(), 'viewcompose-upgrade-package-')));
  try {
    const profileIndex = {
      schemaVersion: 1,
      defaultProfileId: profile.profileId,
      profiles: [{
        profileId: profile.profileId,
        bundlePath: profile.profileId,
        profilePath: `${profile.profileId}/profile.json`,
      }],
    };
    const contents = {
      'distribution.json': Buffer.from(JSON.stringify({
        package: {name: '@viewcompose/ai-tooling', version: '0.4.0'},
        frameworkProfile: {profileId: profile.profileId},
      })),
      'generated/released/index.json': Buffer.from(JSON.stringify(profileIndex)),
      [`generated/released/${profile.profileId}/profile.json`]: Buffer.from(JSON.stringify(profile)),
      'package.json': Buffer.from('{"name":"@viewcompose/ai-tooling"}'),
      'scripts/mcp-server.mjs': Buffer.from('export {};\n'),
    };
    await mkdir(resolve(packageRoot, 'scripts'), {recursive: true});
    await mkdir(resolve(packageRoot, 'generated/released', profile.profileId), {recursive: true});
    for (const [path, bytes] of Object.entries(contents)) {
      await writeFile(resolve(packageRoot, path), bytes);
    }
    const candidateManifest = manifest('0.4.0');
    candidateManifest.frameworkProfileIndex = profileIndex;
    candidateManifest.files = Object.entries(contents).map(([path, bytes]) => ({
      path,
      bytes: bytes.length,
      sha256: sha256(bytes),
    }));
    await verifyInstalledCandidate(packageRoot, candidateManifest);
    await writeFile(resolve(packageRoot, 'unexpected'), 'changed');
    await assert.rejects(
      verifyInstalledCandidate(packageRoot, candidateManifest),
      /file inventory differs/u,
    );
  } finally {
    await rm(packageRoot, {recursive: true, force: true});
  }
});

test('skips a newer incompatible Release and selects the newest exact profile match', async () => {
  const dependency = profile.artifacts.find((artifact) => artifact.knowledgeIncluded);
  const incompatible = structuredClone(profile);
  incompatible.profileId = 'f'.repeat(64);
  incompatible.artifacts.find((artifact) => artifact.coordinate === dependency.coordinate).version = '9.9.9';
  const manifests = new Map([
    ['https://github.com/manifest-0.5.0', Buffer.from(JSON.stringify(manifest('0.5.0', incompatible)))],
    ['https://github.com/manifest-0.4.0', Buffer.from(JSON.stringify(manifest('0.4.0')))],
  ]);
  const candidate = await selectCompatibleUpgradeCandidate({
    releases: [release('0.4.0'), release('0.5.0')],
    currentVersion: '0.3.0',
    projectProfile: {
      status: 'resolved',
      artifacts: [{coordinate: dependency.coordinate, version: dependency.version}],
    },
    request: async (url) => manifests.get(url),
  });
  assert.equal(candidate.version, '0.4.0');
  assert.equal(candidate.selectedProfile.profileId, profile.profileId);
});

test('returns no candidate for unsupported versions and rejects unresolved project identity', async () => {
  const bytes = Buffer.from(JSON.stringify(manifest('0.4.0')));
  const candidate = await selectCompatibleUpgradeCandidate({
    releases: [release('0.4.0')],
    currentVersion: '0.3.0',
    projectProfile: {
      status: 'resolved',
      artifacts: [{coordinate: 'com.viewcompose:viewcompose-runtime', version: '0.0.0'}],
    },
    request: async () => bytes,
  });
  assert.equal(candidate, null);
  await assert.rejects(selectCompatibleUpgradeCandidate({
    releases: [],
    currentVersion: '0.3.0',
    projectProfile: {status: 'unresolved'},
  }), /status is unresolved/u);
});

test('downloads only a checksum-bound archive and rejects changed bytes', async () => {
  const archive = Buffer.from('candidate-archive');
  const candidateManifest = manifest('0.4.0', profile, archive);
  const manifestBytes = Buffer.from(JSON.stringify(candidateManifest));
  const candidate = {
    version: '0.4.0',
    manifest: candidateManifest,
    manifestBytes,
    assets: {
      SHA256SUMS: 'https://github.com/checksums',
      [candidateManifest.archive.path]: 'https://github.com/archive',
    },
  };
  const checksums = Buffer.from(
    `${candidateManifest.archive.sha256}  ${candidateManifest.archive.path}\n` +
    `${sha256(manifestBytes)}  manifest.json\n`,
  );
  const directory = await realpath(await mkdtemp(resolve(tmpdir(), 'viewcompose-upgrade-download-')));
  try {
    const result = await downloadCompatibleCandidate(candidate, {
      directory,
      request: async (url) => url.endsWith('checksums') ? checksums : archive,
    });
    assert.equal(await readFile(result.archivePath, 'utf8'), 'candidate-archive');
    await assert.rejects(downloadCompatibleCandidate(candidate, {
      directory: resolve(directory, 'changed'),
      request: async (url) => url.endsWith('checksums') ? checksums : Buffer.from('changed'),
    }), /differs from the selected Release manifest/u);
  } finally {
    await rm(directory, {recursive: true, force: true});
  }
});

test('orchestrates one compatible side-by-side upgrade and preserves no-candidate state', async () => {
  const installRoot = await realpath(await mkdtemp(resolve(tmpdir(), 'viewcompose-upgrade-flow-')));
  const active = {
    client: 'codex',
    projectRoot: installRoot,
    version: '0.3.0',
    frameworkProfile: profile.profileId,
  };
  const inspect = async () => active;
  try {
    const unchanged = await upgradeAgentClient({
      client: 'codex',
      projectRoot: installRoot,
      installRoot,
      inspect,
      discover: async () => ({projectProfile: {status: 'resolved'}, candidate: null}),
    });
    assert.equal(unchanged.status, 'no-compatible-update');

    const candidate = {
      version: '0.4.0',
      selectedProfile: profile,
      manifest: manifest('0.4.0'),
    };
    const calls = [];
    const upgraded = await upgradeAgentClient({
      client: 'codex',
      projectRoot: installRoot,
      installRoot,
      inspect,
      discover: async () => ({projectProfile: {status: 'resolved'}, candidate}),
      download: async (_candidate, {directory}) => {
        calls.push('download');
        return {archivePath: resolve(directory, 'candidate.tgz')};
      },
      install: async () => {
        calls.push('install');
        return {packageRoot: resolve(installRoot, 'package')};
      },
      migrate: async () => {
        calls.push('migrate');
        return {config: {status: 'migrated'}};
      },
    });
    assert.deepEqual(calls, ['download', 'install', 'migrate']);
    assert.equal(upgraded.status, 'upgraded');
    assert.equal(upgraded.installedVersion, '0.4.0');
  } finally {
    await rm(installRoot, {recursive: true, force: true});
  }
});
