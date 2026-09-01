#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {execFile} from 'node:child_process';
import {
  chmod,
  copyFile,
  lstat,
  mkdir,
  mkdtemp,
  readFile,
  readdir,
  rename,
  rm,
  writeFile,
} from 'node:fs/promises';
import {basename, dirname, isAbsolute, relative, resolve, sep} from 'node:path';
import {promisify} from 'node:util';
import {fileURLToPath} from 'node:url';
import {TOOL_NAMES} from './tool-catalog.mjs';

const execFileAsync = promisify(execFile);
const aiRoot = fileURLToPath(new URL('../', import.meta.url));
const repositoryRoot = resolve(aiRoot, '../..');
const packageContractPath = fileURLToPath(
  new URL('../evaluation/fixtures/distribution/package-contract.json', import.meta.url),
);
const releasedProfileId = '895ed1e52e5a9735f87e6d996e77ea43ca34cc2e496854408c40772419129064';
const releasedBundleRoot = `generated/released/${releasedProfileId}`;
const sourcePaths = Object.freeze([
  'contracts/agent-client-integration.schema.json',
  'contracts/examples/agent-client-integration.json',
  'contracts/bootstrap.schema.json',
  'contracts/examples/bootstrap.json',
  'contracts/consumer-project-execution.schema.json',
  'contracts/examples/consumer-project-execution.json',
  'contracts/framework-compatibility-profile.schema.json',
  'contracts/examples/framework-compatibility-profile.json',
  'contracts/framework-profile-index.schema.json',
  'contracts/examples/framework-profile-index.json',
  'contracts/versions.json',
  'contracts/ai-tooling-release.schema.json',
  'contracts/examples/ai-tooling-release.json',
  'contracts/design-ir.schema.json',
  'contracts/generated-preview-request.schema.json',
  'contracts/layout-comparison.schema.json',
  'contracts/mcp-protocol.json',
  'contracts/screenshot-design-inference.schema.json',
  'contracts/screenshot-inference-resolution.schema.json',
  'contracts/screenshot-kotlin-generation.schema.json',
  'contracts/screenshot-pixel-comparison.schema.json',
  'contracts/screenshot-pixel-localization.schema.json',
  'contracts/screenshot-preprocessing.schema.json',
  'contracts/screenshot-repair.schema.json',
  'contracts/screenshot-repair-authorization.schema.json',
  'contracts/screenshot-repair-candidate-evidence.schema.json',
  'contracts/screenshot-repair-applied-result-handoff.schema.json',
  'contracts/screenshot-repair-execution-outcome.schema.json',
  'contracts/screenshot-repair-host-grant.schema.json',
  'contracts/screenshot-repair-proposal.schema.json',
  'contracts/tool-envelope.schema.json',
  'contracts/xml-project-context.schema.json',
  'contracts/xml-layout-dependencies.schema.json',
  'generated/current-source/artifacts.json',
  'generated/current-source/capabilities.json',
  'generated/current-source/llms-full.txt',
  'generated/current-source/llms.txt',
  'generated/current-source/manifest.json',
  'generated/current-source/rules.json',
  'generated/current-source/samples.jsonl',
  'generated/current-source/symbols.jsonl',
  'generated/released/index.json',
  `${releasedBundleRoot}/artifacts.json`,
  `${releasedBundleRoot}/capabilities.json`,
  `${releasedBundleRoot}/llms-full.txt`,
  `${releasedBundleRoot}/llms.txt`,
  `${releasedBundleRoot}/manifest.json`,
  `${releasedBundleRoot}/profile.json`,
  `${releasedBundleRoot}/rules.json`,
  `${releasedBundleRoot}/samples.jsonl`,
  `${releasedBundleRoot}/symbols.jsonl`,
  'harness/build.gradle.kts',
  'harness/compiler/build.gradle.kts',
  'harness/gradle.properties',
  'harness/preview/build.gradle.kts',
  'harness/preview/src/main/AndroidManifest.xml',
  'harness/settings.gradle.kts',
  'scripts/ai-tool.mjs',
  'scripts/agent-client-integration.mjs',
  'scripts/bounded-process.mjs',
  'scripts/compiler-adapter.mjs',
  'scripts/design-ir-to-kotlin.mjs',
  'scripts/design-ir-repair-patch.mjs',
  'scripts/generated-preview-adapter.mjs',
  'scripts/framework-project-profile.mjs',
  'scripts/framework-profile-selection.mjs',
  'scripts/knowledge-retriever.mjs',
  'scripts/layout-diagnoser.mjs',
  'scripts/layout-comparator.mjs',
  'scripts/pixel-comparator.mjs',
  'scripts/mcp-server.mjs',
  'scripts/preview-adapter.mjs',
  'scripts/project-analyzer.mjs',
  'scripts/repair-orchestrator.mjs',
  'scripts/screenshot-repair-candidate-evaluator.mjs',
  'scripts/screenshot-repair-authorization-validator.mjs',
  'scripts/screenshot-repair-execution-adapter.mjs',
  'scripts/screenshot-repair-host-grant-adapter.mjs',
  'scripts/screenshot-repair-proposer.mjs',
  'scripts/screenshot-repair-terminal-store.mjs',
  'scripts/schema-validator.mjs',
  'scripts/screenshot-contract.mjs',
  'scripts/screenshot-inference-contract.mjs',
  'scripts/screenshot-inference-validator.mjs',
  'scripts/screenshot-preprocessor.mjs',
  'scripts/screenshot-design-ir-to-kotlin.mjs',
  'scripts/screenshot-generation-adapter.mjs',
  'scripts/screenshot-generation-contract.mjs',
  'scripts/screenshot-resolution-adapter.mjs',
  'scripts/screenshot-resolution-contract.mjs',
  'scripts/static-validator.mjs',
  'scripts/tool-catalog.mjs',
  'scripts/tool-core.mjs',
  'scripts/tooling-upgrade.mjs',
  'scripts/xml-migration.mjs',
  'scripts/xml-layout-dependencies.mjs',
  'scripts/xml-project-context.mjs',
  'scripts/xml-to-design-ir.mjs',
  'skills/manifest.json',
  'skills/viewcompose-api-reference/SKILL.md',
  'skills/viewcompose-convert-xml/SKILL.md',
  'skills/viewcompose-create-screen/SKILL.md',
  'skills/viewcompose-debug-layout/SKILL.md',
  'skills/viewcompose-review/SKILL.md',
  'skills/viewcompose-validate/SKILL.md',
]);
const mappedSourcePaths = Object.freeze([
  {source: resolve(repositoryRoot, 'gradlew'), target: 'harness/gradlew'},
  {
    source: resolve(repositoryRoot, 'gradlew.bat'),
    target: 'harness/gradlew.bat',
    normalizeLineEndings: true,
  },
  {
    source: resolve(repositoryRoot, 'gradle/wrapper/gradle-wrapper.jar'),
    target: 'harness/gradle/wrapper/gradle-wrapper.jar',
  },
  {
    source: resolve(aiRoot, 'harness/gradle-wrapper.properties'),
    target: 'harness/gradle/wrapper/gradle-wrapper.properties',
  },
]);
const allowedRepositorySources = new Set([
  resolve(repositoryRoot, 'LICENSE'),
  ...mappedSourcePaths.map((entry) => entry.source).filter((path) => !contained(aiRoot, path)),
]);
const fixedCreationTime = '2026-08-29T00:00:00Z';

function json(value) {
  return `${JSON.stringify(value, null, 2)}\n`;
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function contained(root, candidate) {
  const path = relative(resolve(root), resolve(candidate));
  return path === '' || (!path.startsWith(`..${sep}`) && path !== '..' && !isAbsolute(path));
}

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

async function copyRegularFile(source, target) {
  if (!contained(aiRoot, source) && !allowedRepositorySources.has(resolve(source))) {
    throw new Error(`Distribution source escapes its allowlisted roots: ${source}`);
  }
  const metadata = await lstat(source);
  if (!metadata.isFile() || metadata.isSymbolicLink()) {
    throw new Error(`Distribution source is not a regular file: ${source}`);
  }
  await mkdir(dirname(target), {recursive: true});
  await copyFile(source, target);
}

export function normalizePackageText(value) {
  return Buffer.from(value.toString('utf8').replace(/\r\n?/gu, '\n'), 'utf8');
}

async function copyMappedFile(entry) {
  if (!entry.normalizeLineEndings) {
    await copyRegularFile(entry.source, resolve(entry.target));
    return;
  }
  if (!contained(aiRoot, entry.source) && !allowedRepositorySources.has(resolve(entry.source))) {
    throw new Error(`Distribution source escapes its allowlisted roots: ${entry.source}`);
  }
  const metadata = await lstat(entry.source);
  if (!metadata.isFile() || metadata.isSymbolicLink()) {
    throw new Error(`Distribution source is not a regular file: ${entry.source}`);
  }
  await mkdir(dirname(entry.target), {recursive: true});
  await writeFile(entry.target, normalizePackageText(await readFile(entry.source)));
}

async function packageMetadata(contract) {
  return {
    name: contract.package.name,
    version: contract.package.version,
    description: contract.package.description,
    type: 'module',
    license: contract.package.license,
    repository: contract.package.repository,
    homepage: contract.package.homepage,
    bugs: {url: contract.package.support},
    keywords: contract.package.keywords,
    publishConfig: {access: contract.package.publishAccess},
    engines: {node: contract.package.nodeEngine},
    bin: {
      'ai-tooling': 'scripts/agent-client-integration.mjs',
      'viewcompose-ai': 'scripts/ai-tool.mjs',
      'viewcompose-agent': 'scripts/agent-client-integration.mjs',
      'viewcompose-mcp': 'scripts/mcp-server.mjs',
    },
    dependencies: {},
  };
}

function distributionMetadata(contract, knowledge, releasedKnowledge, profile, protocol, skills) {
  return {
    schemaVersion: 1,
    package: contract.package,
    framework: releasedKnowledge.framework,
    contributorFramework: knowledge.framework,
    knowledge: {
      bundleFingerprint: releasedKnowledge.bundleFingerprint,
      generatorVersion: releasedKnowledge.generatorVersion,
    },
    frameworkProfile: profile,
    tools: [...TOOL_NAMES],
    skills: skills.skills.map((workflow) => workflow.id).sort(),
    executables: [...contract.contents.executables],
    integrity: contract.integrity,
    installation: contract.installation,
    compatibility: {
      ...contract.compatibility,
      protocolVersions: [...protocol.supportedVersions],
      transport: protocol.transport,
    },
  };
}

async function releasedProfileMetadata() {
  const index = await readJson(resolve(aiRoot, 'generated/released/index.json'));
  const profiles = await Promise.all(index.profiles.map((entry) =>
    readJson(resolve(aiRoot, 'generated/released', entry.profilePath))));
  if (
    !profiles.some((profile) => profile.profileId === index.defaultProfileId) ||
    profiles.some((profile, position) => profile.profileId !== index.profiles[position].profileId)
  ) {
    throw new Error('Released framework profile sidecar inventory is inconsistent.');
  }
  return {index, profiles};
}

function spdxDocument(contract, knowledge) {
  const packageSpdxId = 'SPDXRef-Package-viewcompose-ai-tooling';
  return {
    spdxVersion: 'SPDX-2.3',
    dataLicense: 'CC0-1.0',
    SPDXID: 'SPDXRef-DOCUMENT',
    name: `${contract.package.name}-${contract.package.version}`,
    documentNamespace:
      `https://github.com/ViewCompose/ViewCompose/spdx/${contract.package.version}/${knowledge.bundleFingerprint}`,
    creationInfo: {
      created: fixedCreationTime,
      creators: ['Organization: ViewCompose'],
    },
    packages: [{
      SPDXID: packageSpdxId,
      name: contract.package.name,
      versionInfo: contract.package.version,
      downloadLocation: 'NOASSERTION',
      filesAnalyzed: false,
      licenseConcluded: contract.package.license,
      licenseDeclared: contract.package.license,
      copyrightText: 'Copyright (c) 2026 guozhiqiang',
      externalRefs: [{
        referenceCategory: 'PACKAGE-MANAGER',
        referenceType: 'purl',
        referenceLocator: `pkg:npm/%40viewcompose/ai-tooling@${contract.package.version}`,
      }],
    }],
    relationships: [{
      spdxElementId: 'SPDXRef-DOCUMENT',
      relationshipType: 'DESCRIBES',
      relatedSpdxElement: packageSpdxId,
    }],
  };
}

function licenseInventory(contract) {
  return {
    schemaVersion: 1,
    reviewStatus: 'passed',
    project: {
      name: contract.package.name,
      version: contract.package.version,
      license: contract.package.license,
      licenseFile: 'LICENSE',
    },
    distributedRuntimeDependencies: [],
    developmentToolsIncluded: true,
    distributedDevelopmentTools: [{
      name: 'Gradle Wrapper',
      version: '9.3.1',
      license: 'Apache-2.0',
      path: 'harness/gradle/wrapper/gradle-wrapper.jar',
      source: 'https://github.com/gradle/gradle',
    }],
    reviewedAt: fixedCreationTime,
  };
}

async function listRegularFiles(root, current = root) {
  const files = [];
  for (const name of (await readdir(current)).sort()) {
    const path = resolve(current, name);
    if (!contained(root, path)) throw new Error(`Distribution path escapes staging: ${path}`);
    const metadata = await lstat(path);
    if (metadata.isSymbolicLink()) throw new Error(`Distribution staging contains a symbolic link: ${path}`);
    if (metadata.isDirectory()) {
      files.push(...await listRegularFiles(root, path));
    } else if (metadata.isFile()) {
      files.push(path);
    } else {
      throw new Error(`Distribution staging contains a non-regular entry: ${path}`);
    }
  }
  return files;
}

async function fileManifest(root) {
  const entries = [];
  for (const path of await listRegularFiles(root)) {
    const content = await readFile(path);
    entries.push({
      path: relative(root, path).split(sep).join('/'),
      bytes: content.length,
      sha256: sha256(content),
    });
  }
  return entries.sort((left, right) => left.path.localeCompare(right.path));
}

async function removeSupersededArchives(outputRoot, retainedName) {
  for (const name of await readdir(outputRoot)) {
    if (name === retainedName || !/^viewcompose-ai-tooling-[0-9]+\.[0-9]+\.[0-9]+\.tgz$/u.test(name)) {
      continue;
    }
    const path = resolve(outputRoot, name);
    const metadata = await lstat(path);
    if (!metadata.isFile() || metadata.isSymbolicLink()) {
      throw new Error(`Superseded distribution archive is unsafe: ${name}`);
    }
    await rm(path);
  }
}

async function prepareStaging(stagingRoot, contract) {
  const knowledge = await readJson(resolve(aiRoot, 'generated/current-source/manifest.json'));
  const releasedKnowledge = await readJson(resolve(aiRoot, releasedBundleRoot, 'manifest.json'));
  const profile = await readJson(resolve(aiRoot, releasedBundleRoot, 'profile.json'));
  const protocol = await readJson(resolve(aiRoot, 'contracts/mcp-protocol.json'));
  const skills = await readJson(resolve(aiRoot, 'skills/manifest.json'));
  if (
    profile.profileId !== releasedProfileId ||
    releasedKnowledge.framework?.versionLane !== 'released' ||
    releasedKnowledge.framework?.identity !== profile.profileId ||
    releasedKnowledge.bundleFingerprint !== profile.knowledge?.bundleFingerprint
  ) {
    throw new Error('Released Knowledge Pack identity differs from its framework profile.');
  }

  for (const path of sourcePaths) {
    await copyRegularFile(resolve(aiRoot, path), resolve(stagingRoot, path));
  }
  for (const entry of mappedSourcePaths) {
    await copyMappedFile({...entry, target: resolve(stagingRoot, entry.target)});
  }
  await copyRegularFile(resolve(repositoryRoot, 'LICENSE'), resolve(stagingRoot, 'LICENSE'));
  await copyRegularFile(resolve(aiRoot, 'README.md'), resolve(stagingRoot, 'README.md'));
  await writeFile(resolve(stagingRoot, 'package.json'), json(await packageMetadata(contract)));
  await writeFile(
    resolve(stagingRoot, 'distribution.json'),
    json(distributionMetadata(contract, knowledge, releasedKnowledge, profile, protocol, skills)),
  );
  await writeFile(resolve(stagingRoot, 'sbom.spdx.json'), json(spdxDocument(contract, knowledge)));
  await writeFile(
    resolve(stagingRoot, 'third-party-licenses.json'),
    json(licenseInventory(contract)),
  );
  await chmod(resolve(stagingRoot, 'scripts/ai-tool.mjs'), 0o755);
  await chmod(resolve(stagingRoot, 'scripts/agent-client-integration.mjs'), 0o755);
  await chmod(resolve(stagingRoot, 'scripts/mcp-server.mjs'), 0o755);
  await chmod(resolve(stagingRoot, 'harness/gradlew'), 0o755);
}

function assertExactContract(contract, files, packFiles) {
  const expected = [...contract.contents.requiredPaths].sort();
  const actual = files.map((file) => file.path).sort();
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    const missing = expected.filter((path) => !actual.includes(path));
    const extra = actual.filter((path) => !expected.includes(path));
    throw new Error(`Distribution file set drifted; missing=${missing.join(',')}; extra=${extra.join(',')}`);
  }
  const packed = packFiles.map((file) => file.path).sort();
  if (JSON.stringify(packed) !== JSON.stringify(expected)) {
    throw new Error('npm archive file set differs from the staged distribution contract.');
  }
  if (contract.package.runtimeDependencies.length !== 0) {
    throw new Error('The frozen distribution permits no runtime dependencies.');
  }
  if (contract.package.installScripts !== false) {
    throw new Error('The frozen distribution permits no installation scripts.');
  }
  if (TOOL_NAMES.length !== contract.contents.tools.length ||
      TOOL_NAMES.some((tool, index) => tool !== contract.contents.tools[index])) {
    throw new Error('The packaged tool catalog differs from the frozen distribution contract.');
  }
}

export async function createDistribution({
  outputRoot = resolve(aiRoot, 'build/distribution'),
  npmExecutable = process.env.npm_execpath ? process.execPath : 'npm',
  npmArguments = process.env.npm_execpath ? [process.env.npm_execpath] : [],
} = {}) {
  const absoluteOutput = resolve(outputRoot);
  if (absoluteOutput === resolve('/') || absoluteOutput === resolve(repositoryRoot)) {
    throw new Error('Distribution output must be a dedicated non-root directory.');
  }
  await mkdir(absoluteOutput, {recursive: true});
  const workRoot = await mkdtemp(resolve(absoluteOutput, '.package-'));
  const stagingRoot = resolve(workRoot, 'package');
  await mkdir(stagingRoot, {recursive: true});
  try {
    const contract = await readJson(packageContractPath);
    await prepareStaging(stagingRoot, contract);
    const files = await fileManifest(stagingRoot);
    const {stdout} = await execFileAsync(npmExecutable, [
      ...npmArguments,
      'pack',
      stagingRoot,
      '--json',
      '--ignore-scripts',
      '--pack-destination',
      workRoot,
    ], {
      cwd: aiRoot,
      encoding: 'utf8',
      maxBuffer: 4 * 1024 * 1024,
      env: {...process.env, npm_config_audit: 'false', npm_config_fund: 'false'},
    });
    const packResult = JSON.parse(stdout)[0];
    assertExactContract(contract, files, packResult.files);
    const temporaryArchive = resolve(workRoot, packResult.filename);
    const archive = await readFile(temporaryArchive);
    if (packResult.size !== archive.length) throw new Error('npm archive byte count is inconsistent.');
    const archiveName = packResult.filename;
    const archivePath = resolve(absoluteOutput, archiveName);
    const manifestPath = resolve(absoluteOutput, 'manifest.json');
    const checksumsPath = resolve(absoluteOutput, 'SHA256SUMS');
    const releasedProfiles = await releasedProfileMetadata();
    const manifest = {
      schemaVersion: 2,
      package: contract.package,
      compatibility: {
        agentClientIntegration: 5,
        frameworkCompatibilityProfile: 1,
        frameworkProfileIndex: 1,
      },
      frameworkProfileIndex: releasedProfiles.index,
      frameworkProfiles: releasedProfiles.profiles,
      archive: {
        path: archiveName,
        bytes: archive.length,
        sha256: sha256(archive),
        npmIntegrity: packResult.integrity,
        npmShasum: packResult.shasum,
      },
      files,
    };
    const temporaryManifest = resolve(workRoot, 'manifest.json');
    await writeFile(temporaryManifest, json(manifest));
    const manifestBytes = await readFile(temporaryManifest);
    const checksums = [
      `${manifest.archive.sha256}  ${archiveName}`,
      `${sha256(manifestBytes)}  manifest.json`,
    ].join('\n') + '\n';
    const temporaryChecksums = resolve(workRoot, 'SHA256SUMS');
    await writeFile(temporaryChecksums, checksums);
    await removeSupersededArchives(absoluteOutput, archiveName);
    await rm(archivePath, {force: true});
    await rm(manifestPath, {force: true});
    await rm(checksumsPath, {force: true});
    await rename(temporaryArchive, archivePath);
    await rename(temporaryManifest, manifestPath);
    await rename(temporaryChecksums, checksumsPath);
    return {outputRoot: absoluteOutput, archivePath, manifestPath, checksumsPath, manifest};
  } finally {
    await rm(workRoot, {recursive: true, force: true});
  }
}

async function main() {
  const arguments_ = process.argv.slice(2);
  if (arguments_.length > 2 || (arguments_.length === 2 && arguments_[0] !== '--output')) {
    throw new Error('Usage: node scripts/package-distribution.mjs [--output <directory>]');
  }
  const outputRoot = arguments_.length === 2 ? resolve(arguments_[1]) : undefined;
  const result = await createDistribution({outputRoot});
  process.stdout.write(
    `Created ${basename(result.archivePath)} (${result.manifest.archive.bytes} bytes, ` +
    `sha256 ${result.manifest.archive.sha256}).\n`,
  );
}

const entryPath = process.argv[1] ? resolve(process.argv[1]) : '';
if (entryPath === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`ViewCompose AI distribution packaging failed: ${error.message}\n`);
    process.exitCode = 1;
  });
}
