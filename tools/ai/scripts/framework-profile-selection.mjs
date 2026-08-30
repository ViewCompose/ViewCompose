import {existsSync, readFileSync} from 'node:fs';
import {lstat, readFile, realpath} from 'node:fs/promises';
import {isAbsolute, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {assertSchemaValue} from './schema-validator.mjs';

const defaultAiRoot = fileURLToPath(new URL('../', import.meta.url));
const sha256Pattern = /^[a-f0-9]{64}$/u;
export const FRAMEWORK_PROFILE_ENVIRONMENT_VARIABLE = 'VIEWCOMPOSE_FRAMEWORK_PROFILE';
export const CURRENT_SOURCE_PROFILE = 'current-source';

function parseJson(text, label) {
  try {
    return JSON.parse(text);
  } catch (error) {
    throw new Error(`${label} is not valid JSON: ${error.message}`);
  }
}

function defaultReleasedProfile(aiRoot) {
  if (!existsSync(resolve(aiRoot, 'distribution.json'))) return null;
  const path = resolve(aiRoot, 'generated/released/index.json');
  if (!existsSync(path)) return null;
  const index = parseJson(readFileSync(path, 'utf8'), 'Released framework profile index');
  return typeof index.defaultProfileId === 'string' ? index.defaultProfileId : null;
}

export function activeFrameworkProfile({
  aiRoot = defaultAiRoot,
  environment = process.env,
} = {}) {
  const configured = environment[FRAMEWORK_PROFILE_ENVIRONMENT_VARIABLE];
  const sourceBound = environment.VIEWCOMPOSE_SOURCE_ROOT !== undefined;
  const inferred = configured ?? (sourceBound
    ? CURRENT_SOURCE_PROFILE
    : defaultReleasedProfile(aiRoot) ?? CURRENT_SOURCE_PROFILE);
  if (inferred !== CURRENT_SOURCE_PROFILE && !sha256Pattern.test(inferred)) {
    throw new Error(
      `${FRAMEWORK_PROFILE_ENVIRONMENT_VARIABLE} must be current-source or one exact profile ID.`,
    );
  }
  if (sourceBound && inferred !== CURRENT_SOURCE_PROFILE) {
    throw new Error('A source-bound integration cannot select a released framework profile.');
  }
  return Object.freeze({
    profileId: inferred,
    versionLane: inferred === CURRENT_SOURCE_PROFILE ? 'current-source' : 'released',
    bundleRoot: inferred === CURRENT_SOURCE_PROFILE
      ? resolve(aiRoot, 'generated/current-source')
      : resolve(aiRoot, 'generated/released', inferred),
  });
}

export function activeKnowledgePath(relativePath, options = {}) {
  if (
    typeof relativePath !== 'string' ||
    relativePath.length === 0 ||
    relativePath.startsWith('/') ||
    relativePath.split('/').some((segment) => ['', '.', '..'].includes(segment))
  ) {
    throw new Error(`Knowledge path is unsafe: ${relativePath}`);
  }
  return resolve(activeFrameworkProfile(options).bundleRoot, relativePath);
}

async function requireRegularFile(path, label) {
  const metadata = await lstat(path).catch((error) => {
    if (error?.code === 'ENOENT') throw new Error(`${label} is missing.`);
    throw error;
  });
  if (!metadata.isFile() || metadata.isSymbolicLink()) {
    throw new Error(`${label} must be a regular non-symbolic-link file.`);
  }
  return path;
}

async function loadJson(path, label) {
  return parseJson(await readFile(await requireRegularFile(path, label), 'utf8'), label);
}

export async function loadReleasedFrameworkProfiles({aiRoot = defaultAiRoot} = {}) {
  if (!isAbsolute(aiRoot)) throw new Error('AI package root must be absolute.');
  const canonicalRoot = await realpath(resolve(aiRoot));
  const releasedRoot = resolve(canonicalRoot, 'generated/released');
  const [index, indexSchema, profileSchema] = await Promise.all([
    loadJson(resolve(releasedRoot, 'index.json'), 'Released framework profile index'),
    loadJson(
      resolve(canonicalRoot, 'contracts/framework-profile-index.schema.json'),
      'Framework profile index schema',
    ),
    loadJson(
      resolve(canonicalRoot, 'contracts/framework-compatibility-profile.schema.json'),
      'Framework compatibility profile schema',
    ),
  ]);
  assertSchemaValue(index, indexSchema, 'released framework profile index');
  if (!index.profiles.some((entry) => entry.profileId === index.defaultProfileId)) {
    throw new Error('Released framework profile index default is absent from its inventory.');
  }
  const profiles = [];
  for (const entry of index.profiles) {
    if (
      entry.profilePath !== `${entry.profileId}/profile.json` ||
      entry.bundlePath !== entry.profileId
    ) {
      throw new Error(`Released framework profile index path drifted for ${entry.profileId}.`);
    }
    const profileRoot = resolve(releasedRoot, entry.profileId);
    const canonicalProfileRoot = await realpath(profileRoot);
    if (canonicalProfileRoot !== profileRoot) {
      throw new Error(`Released framework profile root is unsafe: ${entry.profileId}.`);
    }
    const [profile, manifest] = await Promise.all([
      loadJson(resolve(profileRoot, 'profile.json'), `Framework profile ${entry.profileId}`),
      loadJson(resolve(profileRoot, 'manifest.json'), `Knowledge manifest ${entry.profileId}`),
    ]);
    assertSchemaValue(profile, profileSchema, `framework profile ${entry.profileId}`);
    if (
      profile.profileId !== entry.profileId ||
      manifest.framework?.versionLane !== 'released' ||
      manifest.framework?.identity !== profile.profileId ||
      manifest.bundleFingerprint !== profile.knowledge.bundleFingerprint
    ) {
      throw new Error(`Released framework profile identity drifted for ${entry.profileId}.`);
    }
    profiles.push(Object.freeze({entry, profile, manifest, bundleRoot: profileRoot}));
  }
  return Object.freeze({index, profiles});
}

export function frameworkProfileMatchesProject(projectArtifacts, profile) {
  const versions = new Map(profile.artifacts.map((artifact) => [artifact.coordinate, artifact.version]));
  return projectArtifacts.every((artifact) => versions.get(artifact.coordinate) === artifact.version);
}

export async function selectReleasedFrameworkProfile(projectProfile, options = {}) {
  if (!projectProfile || !['empty', 'resolved', 'unresolved', 'conflict'].includes(projectProfile.status)) {
    throw new Error('Consumer framework project profile is invalid.');
  }
  if (projectProfile.status === 'unresolved') {
    throw new Error('ViewCompose dependency versions are unresolved; no framework profile was selected.');
  }
  if (projectProfile.status === 'conflict') {
    throw new Error('ViewCompose dependency versions conflict; no framework profile was selected.');
  }
  const inventory = await loadReleasedFrameworkProfiles(options);
  const candidates = projectProfile.status === 'empty'
    ? inventory.profiles
    : inventory.profiles.filter(({profile}) =>
      frameworkProfileMatchesProject(projectProfile.artifacts, profile));
  const selected = candidates.find(({profile}) => profile.profileId === inventory.index.defaultProfileId) ??
    candidates[0];
  if (!selected) {
    const coordinates = projectProfile.artifacts
      .map((artifact) => `${artifact.coordinate}:${artifact.version}`)
      .join(', ');
    throw new Error(`No released framework profile matches the project dependencies: ${coordinates}.`);
  }
  return Object.freeze({
    profileId: selected.profile.profileId,
    versionLane: 'released',
    profile: selected.profile,
    manifest: selected.manifest,
    projectProfile,
  });
}
