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
const sourcePaths = Object.freeze([
  'contracts/design-ir.schema.json',
  'contracts/generated-preview-request.schema.json',
  'contracts/layout-comparison.schema.json',
  'contracts/mcp-protocol.json',
  'contracts/screenshot-design-inference.schema.json',
  'contracts/screenshot-inference-resolution.schema.json',
  'contracts/screenshot-preprocessing.schema.json',
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
  'scripts/ai-tool.mjs',
  'scripts/bounded-process.mjs',
  'scripts/compiler-adapter.mjs',
  'scripts/design-ir-to-kotlin.mjs',
  'scripts/generated-preview-adapter.mjs',
  'scripts/knowledge-retriever.mjs',
  'scripts/layout-diagnoser.mjs',
  'scripts/layout-comparator.mjs',
  'scripts/mcp-server.mjs',
  'scripts/preview-adapter.mjs',
  'scripts/project-analyzer.mjs',
  'scripts/schema-validator.mjs',
  'scripts/screenshot-contract.mjs',
  'scripts/screenshot-inference-contract.mjs',
  'scripts/screenshot-inference-validator.mjs',
  'scripts/screenshot-preprocessor.mjs',
  'scripts/screenshot-resolution-adapter.mjs',
  'scripts/screenshot-resolution-contract.mjs',
  'scripts/static-validator.mjs',
  'scripts/tool-catalog.mjs',
  'scripts/tool-core.mjs',
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
  if (!contained(aiRoot, source) && source !== resolve(repositoryRoot, 'LICENSE')) {
    throw new Error(`Distribution source escapes its allowlisted roots: ${source}`);
  }
  const metadata = await lstat(source);
  if (!metadata.isFile() || metadata.isSymbolicLink()) {
    throw new Error(`Distribution source is not a regular file: ${source}`);
  }
  await mkdir(dirname(target), {recursive: true});
  await copyFile(source, target);
}

async function packageMetadata(contract) {
  return {
    name: contract.package.name,
    version: contract.package.version,
    description: 'Deterministic local AI tooling for the ViewCompose Android UI framework.',
    private: true,
    type: 'module',
    license: contract.package.license,
    engines: {node: contract.package.nodeEngine},
    bin: {
      'viewcompose-ai': 'scripts/ai-tool.mjs',
      'viewcompose-mcp': 'scripts/mcp-server.mjs',
    },
    dependencies: {},
  };
}

function distributionMetadata(contract, knowledge, protocol, skills) {
  return {
    schemaVersion: 1,
    package: contract.package,
    framework: knowledge.framework,
    knowledge: {
      bundleFingerprint: knowledge.bundleFingerprint,
      generatorVersion: knowledge.generatorVersion,
    },
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
        referenceLocator: 'pkg:npm/%40viewcompose/ai-tooling@0.1.0',
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
    developmentToolsIncluded: false,
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

async function prepareStaging(stagingRoot, contract) {
  const knowledge = await readJson(resolve(aiRoot, 'generated/current-source/manifest.json'));
  const protocol = await readJson(resolve(aiRoot, 'contracts/mcp-protocol.json'));
  const skills = await readJson(resolve(aiRoot, 'skills/manifest.json'));

  for (const path of sourcePaths) {
    await copyRegularFile(resolve(aiRoot, path), resolve(stagingRoot, path));
  }
  await copyRegularFile(resolve(repositoryRoot, 'LICENSE'), resolve(stagingRoot, 'LICENSE'));
  await copyRegularFile(resolve(aiRoot, 'README.md'), resolve(stagingRoot, 'README.md'));
  await writeFile(resolve(stagingRoot, 'package.json'), json(await packageMetadata(contract)));
  await writeFile(
    resolve(stagingRoot, 'distribution.json'),
    json(distributionMetadata(contract, knowledge, protocol, skills)),
  );
  await writeFile(resolve(stagingRoot, 'sbom.spdx.json'), json(spdxDocument(contract, knowledge)));
  await writeFile(
    resolve(stagingRoot, 'third-party-licenses.json'),
    json(licenseInventory(contract)),
  );
  await chmod(resolve(stagingRoot, 'scripts/ai-tool.mjs'), 0o755);
  await chmod(resolve(stagingRoot, 'scripts/mcp-server.mjs'), 0o755);
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
  if (TOOL_NAMES.length !== contract.contents.tools.length ||
      TOOL_NAMES.some((tool, index) => tool !== contract.contents.tools[index])) {
    throw new Error('The packaged tool catalog differs from the frozen distribution contract.');
  }
}

export async function createDistribution({
  outputRoot = resolve(aiRoot, 'build/distribution'),
  npmExecutable = 'npm',
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
    const manifest = {
      schemaVersion: 1,
      package: contract.package,
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
