import {execFile} from 'node:child_process';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {promisify} from 'node:util';
import {assertSchemaValue} from './schema-validator.mjs';
import {
  aiRoot,
  buildKnowledgeBundle,
  gitRevisionKnowledgeSourceProvider,
  repositoryRoot,
  stableJson,
} from './knowledge-generator.mjs';

const execFileAsync = promisify(execFile);
const publishingPath = resolve(repositoryRoot, 'gradle/viewcompose-publishing.properties');
const historyPath = resolve(repositoryRoot, 'gradle/viewcompose-documentation-releases.properties');
const referencePath = resolve(repositoryRoot, 'website/src/data/capability-reference.json');
const executionContractPath = resolve(aiRoot, 'contracts/examples/consumer-project-execution.json');
const profileSchemaPath = resolve(aiRoot, 'contracts/framework-compatibility-profile.schema.json');
const profileIndexSchemaPath = resolve(aiRoot, 'contracts/framework-profile-index.schema.json');

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

export function parseProperties(text) {
  const values = new Map();
  for (const rawLine of text.replaceAll('\r\n', '\n').split('\n')) {
    const line = rawLine.trim();
    if (line === '' || line.startsWith('#')) continue;
    const separator = line.indexOf('=');
    if (separator <= 0) throw new Error(`Malformed release property: ${rawLine}`);
    const key = line.slice(0, separator).trim();
    const value = line.slice(separator + 1).trim();
    if (values.has(key)) throw new Error(`Duplicate release property: ${key}`);
    values.set(key, value);
  }
  return values;
}

function required(properties, key) {
  const value = properties.get(key);
  if (value === undefined || value === '') throw new Error(`Missing release property ${key}.`);
  return value;
}

function splitList(value) {
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

function publicationHistory(properties) {
  const count = Number(required(properties, 'release.count'));
  if (!Number.isInteger(count) || count < 1) throw new Error('Release history count is invalid.');
  const entries = new Set();
  for (let index = 0; index < count; index += 1) {
    const version = required(properties, `release.${index}.version`);
    const sourceRevision = required(properties, `release.${index}.sourceRevision`);
    if (!/^[a-f0-9]{40}$/u.test(sourceRevision)) {
      throw new Error(`Release history revision ${sourceRevision} is invalid.`);
    }
    for (const artifact of splitList(required(properties, `release.${index}.modules`))) {
      entries.add(`${artifact}\0${version}\0${sourceRevision}`);
    }
  }
  return entries;
}

async function git(args, root = repositoryRoot) {
  const {stdout} = await execFileAsync('git', args, {
    cwd: root,
    encoding: 'utf8',
    maxBuffer: 8 * 1024 * 1024,
  });
  return stdout.trim();
}

async function releaseAnchor(revisions) {
  const unique = [...new Set(revisions)].sort();
  const dated = await Promise.all(unique.map(async (revision) => ({
    revision,
    timestamp: Number(await git(['show', '-s', '--format=%ct', revision])),
  })));
  dated.sort((left, right) => right.timestamp - left.timestamp || left.revision.localeCompare(right.revision));
  if (!Number.isInteger(dated[0]?.timestamp) || dated[0].timestamp === dated[1]?.timestamp) {
    throw new Error('Released Knowledge Pack requires one unambiguous newest source anchor.');
  }
  return dated[0].revision;
}

async function verifyArtifactTrees(artifacts, anchorRevision) {
  for (const artifact of artifacts) {
    const artifactId = artifact.coordinate.slice('com.viewcompose:'.length);
    const [releasedTree, anchorTree] = await Promise.all([
      git(['rev-parse', `${artifact.sourceRevision}:${artifactId}/src/main`]),
      git(['rev-parse', `${anchorRevision}:${artifactId}/src/main`]),
    ]);
    if (releasedTree !== anchorTree) {
      throw new Error(
        `${artifact.coordinate}:${artifact.version} differs between its release revision and ` +
        `Knowledge Pack anchor ${anchorRevision}.`,
      );
    }
  }
}

export async function buildReleasedKnowledgePack() {
  const [
    publishingText,
    historyText,
    reference,
    executionContract,
    profileSchema,
    profileIndexSchema,
  ] = await Promise.all([
    readFile(publishingPath, 'utf8'),
    readFile(historyPath, 'utf8'),
    readFile(referencePath, 'utf8').then(JSON.parse),
    readFile(executionContractPath, 'utf8').then(JSON.parse),
    readFile(profileSchemaPath, 'utf8').then(JSON.parse),
    readFile(profileIndexSchemaPath, 'utf8').then(JSON.parse),
  ]);
  const publishing = parseProperties(publishingText);
  const history = publicationHistory(parseProperties(historyText));
  const unpublished = new Set(splitList(publishing.get('release.unpublishedModules') ?? ''));
  const knowledgeArtifacts = new Set(
    reference.artifacts
      .filter((artifact) => artifact.versionState === 'released')
      .map((artifact) => artifact.artifact),
  );
  const artifactIds = [...publishing.keys()]
    .map((key) => /^module\.([a-z0-9-]+)\.version$/u.exec(key)?.[1])
    .filter((artifact) => artifact !== undefined && !unpublished.has(artifact))
    .sort();
  const artifacts = artifactIds.map((artifact) => {
    const version = required(publishing, `module.${artifact}.version`);
    const sourceRevision = required(publishing, `module.${artifact}.sourceRevision`);
    if (!/^[a-f0-9]{40}$/u.test(sourceRevision)) {
      throw new Error(`Published source revision for ${artifact} is invalid.`);
    }
    if (!history.has(`${artifact}\0${version}\0${sourceRevision}`)) {
      throw new Error(`${artifact}:${version} at ${sourceRevision} is absent from immutable release history.`);
    }
    return {
      coordinate: `com.viewcompose:${artifact}`,
      version,
      sourceRevision,
      knowledgeIncluded: knowledgeArtifacts.has(artifact),
    };
  });
  if (artifacts.length === 0 || !artifacts.some((artifact) => artifact.knowledgeIncluded)) {
    throw new Error('Released Knowledge Pack has no published artifact knowledge.');
  }
  const anchorRevision = await releaseAnchor(artifacts.map((artifact) => artifact.sourceRevision));
  await verifyArtifactTrees(artifacts, anchorRevision);

  const harnessCoordinates = executionContract.artifacts
    .map((artifact) => `${artifact.coordinate}:${artifact.version}`)
    .sort();
  const profileIdentity = {
    schemaVersion: 1,
    stability: 'released',
    artifacts,
    harness: {
      artifactCoordinates: harnessCoordinates,
      executesConsumerBuildLogic: false,
    },
  };
  const profileId = sha256(stableJson(profileIdentity));
  const knowledgeIds = artifacts
    .filter((artifact) => artifact.knowledgeIncluded)
    .map((artifact) => artifact.coordinate.slice('com.viewcompose:'.length));
  const artifactVersions = new Map(
    artifacts.map((artifact) => [artifact.coordinate.slice('com.viewcompose:'.length), artifact.version]),
  );
  const sourceProvider = await gitRevisionKnowledgeSourceProvider(anchorRevision);
  const bundlePath = `tools/ai/generated/released/${profileId}`;
  const bundle = await buildKnowledgeBundle({
    sourceRevision: anchorRevision,
    versionLane: 'released',
    frameworkIdentity: profileId,
    bundlePath,
    sourceProvider,
    artifactFilter: knowledgeIds,
    artifactVersions,
  });
  const profile = {
    schemaVersion: 1,
    profileId,
    stability: 'released',
    consumerSelectable: true,
    knowledge: {
      versionLane: 'released',
      bundleFingerprint: bundle.manifest.bundleFingerprint,
      sourcePolicy: 'exact-artifact-release-revisions',
    },
    artifacts,
    harness: profileIdentity.harness,
    selection: {
      projectMatch: 'exact-declared-artifact-version-subset',
      unresolvedVersion: 'reject',
      conflictingVersion: 'reject',
      emptyProject: 'newest-stable-compatible-profile',
      upgradeCandidate: 'newest-tooling-release-with-matching-profile',
    },
  };
  assertSchemaValue(profile, profileSchema, 'generated released framework profile');
  const index = {
    schemaVersion: 1,
    defaultProfileId: profileId,
    profiles: [{
      profileId,
      profilePath: `${profileId}/profile.json`,
      bundlePath: profileId,
    }],
  };
  assertSchemaValue(index, profileIndexSchema, 'generated released framework profile index');
  for (const coordinate of harnessCoordinates) {
    const [group, artifact, version] = coordinate.split(':');
    const matched = artifacts.find((entry) => entry.coordinate === `${group}:${artifact}`);
    if (!matched || matched.version !== version) {
      throw new Error(`Harness coordinate ${coordinate} is outside the released framework profile.`);
    }
  }
  return {profile, index, bundle, anchorRevision};
}

export const releasedKnowledgeRoot = fileURLToPath(new URL('../generated/released/', import.meta.url));
