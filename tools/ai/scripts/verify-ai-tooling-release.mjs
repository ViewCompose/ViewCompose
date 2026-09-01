#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {lstat, readFile, readdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {assertSchemaValue} from './schema-validator.mjs';

const aiRoot = fileURLToPath(new URL('../', import.meta.url));
const repositoryRoot = resolve(aiRoot, '../..');

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function assertIncludes(text, expected, label) {
  if (!text.includes(expected)) throw new Error(`AI tooling release workflow is missing ${label}.`);
}

function verifyWorkflow(workflow, contract) {
  const requiredFragments = [
    ['tags:\n      - "ai-tooling-v*"', 'the immutable AI tooling tag trigger'],
    ['contents: write', 'contents write permission'],
    ['id-token: write', 'OIDC token permission'],
    ['attestations: write', 'artifact attestation permission'],
    ['actions/checkout@v7', 'the pinned checkout action'],
    ['fetch-depth: 0', 'complete tag history'],
    ['actions/setup-node@v7', 'the pinned Node setup action'],
    ['node-version: "24.19.0"', 'the pinned Node runtime'],
    ['registry-url: "https://registry.npmjs.org"', 'the canonical npm registry'],
    ['package-manager-cache: false', 'the release-build package-manager cache prohibition'],
    ['npm install --global npm@11.8.0', 'the trusted-publishing npm client'],
    ['environment: ai-tooling-release', 'the protected release environment'],
    ['actions/attest-build-provenance@v3', 'GitHub build provenance'],
    ['./gradlew verifyAiToolingRelease --stacktrace', 'the release quality gate'],
    ['--verify-tag', 'tag existence verification'],
    ['gh api "repos/$GITHUB_REPOSITORY/releases/tags/$GITHUB_REF_NAME"', 'existing-release inspection'],
    ['gh release download "$GITHUB_REF_NAME"', 'existing-release byte verification'],
    ['npm view @viewcompose/ai-tooling@0.4.1 version', 'unpublished npm-identity requirement'],
    ['automated recovery cannot prove its OIDC provenance', 'existing npm provenance rejection'],
    ['npm publish', 'npm publication'],
    ['--access public', 'public scoped-package access'],
    ['--provenance', 'npm provenance'],
  ];
  for (const [fragment, label] of requiredFragments) assertIncludes(workflow, fragment, label);
  for (const asset of contract.assets) {
    assertIncludes(workflow, `tools/ai/build/distribution/${asset}`, `release asset ${asset}`);
  }
  if (/cancel-in-progress:\s*true/u.test(workflow)) {
    throw new Error('AI tooling release workflow must not cancel an in-progress immutable release.');
  }
  if (/\b(?:latest|main)\b.*viewcompose-ai-tooling/u.test(workflow)) {
    throw new Error('AI tooling release workflow must not publish a mutable package selector.');
  }
  if (/NODE_AUTH_TOKEN|NPM_TOKEN|npm_token/iu.test(workflow)) {
    throw new Error('AI tooling release workflow must use OIDC without a long-lived npm token.');
  }
  if (workflow.indexOf('gh release create') > workflow.indexOf('npm publish')) {
    throw new Error('AI tooling release workflow must create or verify the GitHub Release before npm publication.');
  }
  if (!workflow.includes("if: steps.github-release.outputs.state == 'missing'")) {
    throw new Error('AI tooling release workflow must recover only from a byte-verified GitHub Release.');
  }
}

async function verifyAssets(contract, distributionRoot) {
  const names = (await readdir(distributionRoot)).sort();
  const expected = [...contract.assets].sort();
  if (JSON.stringify(names) !== JSON.stringify(expected)) {
    throw new Error(`Release asset inventory drifted: expected ${expected.join(', ')}.`);
  }
  const checksumText = await readFile(resolve(distributionRoot, 'SHA256SUMS'), 'utf8');
  const checksums = new Map(
    checksumText.trim().split('\n').map((line) => {
      const match = /^([a-f0-9]{64})  ([^/]+)$/u.exec(line);
      if (!match) throw new Error(`Malformed release checksum line: ${line}`);
      return [match[2], match[1]];
    }),
  );
  for (const asset of contract.assets.filter((name) => name !== 'SHA256SUMS')) {
    const path = resolve(distributionRoot, asset);
    const metadata = await lstat(path);
    if (!metadata.isFile() || metadata.isSymbolicLink()) {
      throw new Error(`Release asset is not a regular file: ${asset}`);
    }
    const digest = sha256(await readFile(path));
    if (checksums.get(asset) !== digest) throw new Error(`Release checksum drifted for ${asset}.`);
  }
  if (checksums.size !== contract.assets.length - 1) {
    throw new Error('Release checksum inventory contains an unexpected asset.');
  }
  const manifest = await readJson(resolve(distributionRoot, 'manifest.json'));
  const profileIds = manifest.frameworkProfiles?.map((profile) => profile.profileId) ?? [];
  const indexedIds = manifest.frameworkProfileIndex?.profiles?.map((profile) => profile.profileId) ?? [];
  if (
    manifest.schemaVersion !== 2 ||
    manifest.package?.name !== contract.package.name ||
    manifest.package?.version !== contract.package.version ||
    manifest.archive?.path !== contract.assets[0] ||
    checksums.get(manifest.archive.path) !== manifest.archive.sha256 ||
    manifest.compatibility?.agentClientIntegration !== 5 ||
    manifest.compatibility?.frameworkCompatibilityProfile !== 1 ||
    manifest.compatibility?.frameworkProfileIndex !== 1 ||
    profileIds.length < 1 ||
    JSON.stringify(profileIds) !== JSON.stringify(indexedIds) ||
    !profileIds.includes(manifest.frameworkProfileIndex?.defaultProfileId) ||
    manifest.frameworkProfiles.some((profile) =>
      profile.consumerSelectable !== true ||
      profile.knowledge?.versionLane !== 'released' ||
      !/^[a-f0-9]{64}$/u.test(profile.profileId ?? ''))
  ) {
    throw new Error('Release manifest framework compatibility inventory is invalid.');
  }
}

export async function verifyAiToolingRelease({
  tag,
  workflowText,
  distributionRoot = resolve(aiRoot, 'build/distribution'),
  checkAssets = true,
} = {}) {
  const [schema, contract, packageContract, workflow] = await Promise.all([
    readJson(resolve(aiRoot, 'contracts/ai-tooling-release.schema.json')),
    readJson(resolve(aiRoot, 'contracts/examples/ai-tooling-release.json')),
    readJson(resolve(aiRoot, 'evaluation/fixtures/distribution/package-contract.json')),
    workflowText === undefined
      ? readFile(resolve(repositoryRoot, '.github/workflows/ai-tooling-release.yml'), 'utf8')
      : workflowText,
  ]);
  assertSchemaValue(contract, schema, 'AI tooling release example');
  if (
    packageContract.package.name !== contract.package.name ||
    packageContract.package.version !== contract.package.version
  ) {
    throw new Error('GitHub Release identity drifted from the distribution package contract.');
  }
  if (tag !== undefined && tag !== contract.package.tag) {
    throw new Error(`Release tag ${tag} does not match frozen tag ${contract.package.tag}.`);
  }
  verifyWorkflow(workflow, contract);
  if (checkAssets) await verifyAssets(contract, distributionRoot);
  return {tag: contract.package.tag, assets: contract.assets.length};
}

function parseArguments(tokens) {
  let tag;
  for (let index = 0; index < tokens.length; index += 2) {
    if (tokens[index] !== '--tag' || tokens[index + 1] === undefined || tag !== undefined) {
      throw new Error('Usage: verify-ai-tooling-release.mjs [--tag <ai-tooling-vX.Y.Z>]');
    }
    tag = tokens[index + 1];
  }
  return {tag};
}

const entryPath = process.argv[1] ? resolve(process.argv[1]) : '';
if (entryPath === fileURLToPath(import.meta.url)) {
  verifyAiToolingRelease(parseArguments(process.argv.slice(2))).then((result) => {
    process.stdout.write(
      `Verified ViewCompose AI tooling release ${result.tag}: ${result.assets}/${result.assets} assets.\n`,
    );
  }).catch((error) => {
    process.stderr.write(`ViewCompose AI tooling release verification failed: ${error.message}\n`);
    process.exitCode = 1;
  });
}
