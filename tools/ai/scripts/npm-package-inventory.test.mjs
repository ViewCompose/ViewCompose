import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import test from 'node:test';
import {gzipSync} from 'node:zlib';
import {verifyNpmPackageInventory} from './npm-package-inventory.mjs';

const payload = Buffer.from('frozen tar payload');
const archive = gzipSync(payload, {level: 1});
const publishedArchive = gzipSync(payload, {level: 9});
const publishedRoot = '/tmp/viewcompose-ai-published-test';
const distribution = {
  archivePath: '/tmp/viewcompose-ai-tooling-0.5.0.tgz',
  manifest: {
    archive: {path: 'viewcompose-ai-tooling-0.5.0.tgz'},
    files: [{path: 'README.md'}, {path: 'package.json'}],
  },
};
const contract = {
  package: {name: '@viewcompose/ai-tooling', version: '0.5.0'},
};
const inventory = {
  id: '@viewcompose/ai-tooling@0.5.0',
  name: '@viewcompose/ai-tooling',
  version: '0.5.0',
  filename: 'viewcompose-ai-tooling-0.5.0.tgz',
  files: [{path: 'package.json'}, {path: 'README.md'}],
};

function missingVersion() {
  const error = new Error('npm view failed');
  error.stderr = 'npm error code E404';
  return error;
}

test('uses npm publish dry-run before the exact version exists', async () => {
  const calls = [];
  const mode = await verifyNpmPackageInventory(distribution, contract, {
    execute: async (_command, arguments_) => {
      calls.push(arguments_);
      if (arguments_[0] === 'view') throw missingVersion();
      return {stdout: JSON.stringify(inventory)};
    },
  });
  assert.equal(mode, 'publish-dry-run');
  assert.deepEqual(calls.map((arguments_) => arguments_[0]), ['view', 'publish']);
});

test('accepts the package-keyed npm publish dry-run inventory', async () => {
  const mode = await verifyNpmPackageInventory(distribution, contract, {
    execute: async (_command, arguments_) => {
      if (arguments_[0] === 'view') throw missingVersion();
      return {stdout: JSON.stringify({'@viewcompose/ai-tooling': inventory})};
    },
  });
  assert.equal(mode, 'publish-dry-run');
});

test('rejects an ambiguous package-keyed npm publish dry-run inventory', async () => {
  await assert.rejects(
    verifyNpmPackageInventory(distribution, contract, {
      execute: async (_command, arguments_) => {
        if (arguments_[0] === 'view') throw missingVersion();
        return {stdout: JSON.stringify({
          '@viewcompose/ai-tooling': inventory,
          '@viewcompose/unexpected': inventory,
        })};
      },
    }),
    /did not return one package inventory/u,
  );
});

test('uses registry integrity and exact uncompressed payload after publication', async () => {
  const integrity = `sha512-${createHash('sha512').update(publishedArchive).digest('base64')}`;
  const calls = [];
  let removed = false;
  const mode = await verifyNpmPackageInventory(distribution, contract, {
    createTemporaryDirectory: async () => publishedRoot,
    removeTemporaryDirectory: async () => {
      removed = true;
    },
    readArchive: async (path) => path === distribution.archivePath ? archive : publishedArchive,
    execute: async (_command, arguments_) => {
      calls.push(arguments_);
      return arguments_[0] === 'view'
        ? {stdout: JSON.stringify(integrity)}
        : {stdout: JSON.stringify([{...inventory, integrity}])};
    },
  });
  assert.equal(mode, 'published-payload');
  assert.deepEqual(calls.map((arguments_) => arguments_[0]), ['view', 'pack']);
  assert.equal(removed, true);
});

test('rejects a published version whose payload differs from the candidate', async () => {
  const differentArchive = gzipSync(Buffer.from('different tar payload'), {level: 9});
  const integrity = `sha512-${createHash('sha512').update(differentArchive).digest('base64')}`;
  await assert.rejects(
    verifyNpmPackageInventory(distribution, contract, {
      createTemporaryDirectory: async () => publishedRoot,
      removeTemporaryDirectory: async () => {},
      readArchive: async (path) => path === distribution.archivePath ? archive : differentArchive,
      execute: async (_command, arguments_) => arguments_[0] === 'view'
        ? {stdout: JSON.stringify(integrity)}
        : {stdout: JSON.stringify([{...inventory, integrity}])},
    }),
    /Published npm payload differs/u,
  );
});

test('rejects a downloaded archive outside the registry integrity identity', async () => {
  const integrity = `sha512-${createHash('sha512').update(publishedArchive).digest('base64')}`;
  await assert.rejects(
    verifyNpmPackageInventory(distribution, contract, {
      createTemporaryDirectory: async () => publishedRoot,
      removeTemporaryDirectory: async () => {},
      readArchive: async () => archive,
      execute: async (_command, arguments_) => arguments_[0] === 'view'
        ? {stdout: JSON.stringify(integrity)}
        : {stdout: JSON.stringify([{...inventory, integrity}])},
    }),
    /does not match the registry integrity identity/u,
  );
});

test('does not reinterpret registry or authentication failures as an unpublished version', async () => {
  const failure = new Error('registry unavailable');
  failure.stderr = 'npm error code E401';
  await assert.rejects(
    verifyNpmPackageInventory(distribution, contract, {
      execute: async () => {
        throw failure;
      },
    }),
    /registry unavailable/u,
  );
});
