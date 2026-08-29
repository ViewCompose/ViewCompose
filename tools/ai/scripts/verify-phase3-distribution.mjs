#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {execFile, spawn} from 'node:child_process';
import {access, lstat, mkdtemp, readFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import {promisify} from 'node:util';
import {fileURLToPath} from 'node:url';
import {createDistribution} from './package-distribution.mjs';

const execFileAsync = promisify(execFile);
const aiRoot = fileURLToPath(new URL('../', import.meta.url));
const repositoryRoot = resolve(aiRoot, '../..');
const contractPath = fileURLToPath(
  new URL('../evaluation/fixtures/distribution/package-contract.json', import.meta.url),
);
const knowledgePath = fileURLToPath(new URL('../generated/current-source/manifest.json', import.meta.url));
const compileFixturePath = fileURLToPath(
  new URL('../evaluation/fixtures/kotlin/foundation-profile-summary-valid.kt', import.meta.url),
);
const outputRoot = resolve(aiRoot, 'build/distribution');
const npmEnvironment = Object.freeze({
  npm_config_audit: 'false',
  npm_config_fund: 'false',
  npm_config_offline: 'true',
  npm_config_registry: 'http://127.0.0.1:9',
  npm_config_update_notifier: 'false',
});

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

async function exists(path) {
  return lstat(path).then(() => true, (error) => {
    if (error?.code === 'ENOENT') return false;
    throw error;
  });
}

function runStreaming(executable, input, {env = {}, timeoutMs = 180_000} = {}) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(executable, [], {
      cwd: repositoryRoot,
      env: {...process.env, ...env},
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    const stdout = [];
    const stderr = [];
    let bytes = 0;
    const timer = setTimeout(() => {
      child.kill('SIGKILL');
      reject(new Error(`${executable} exceeded ${timeoutMs} ms.`));
    }, timeoutMs);
    child.stdout.on('data', (chunk) => {
      bytes += chunk.length;
      if (bytes > 8 * 1024 * 1024) {
        child.kill('SIGKILL');
        reject(new Error(`${executable} exceeded the verifier output bound.`));
      } else {
        stdout.push(chunk);
      }
    });
    child.stderr.on('data', (chunk) => stderr.push(chunk));
    child.on('error', reject);
    child.on('close', (exitCode) => {
      clearTimeout(timer);
      if (exitCode !== 0) {
        reject(new Error(
          `${executable} exited ${exitCode}: ${Buffer.concat(stderr).toString('utf8').slice(0, 4096)}`,
        ));
      } else {
        resolvePromise({
          stdout: Buffer.concat(stdout).toString('utf8'),
          stderr: Buffer.concat(stderr).toString('utf8'),
        });
      }
    });
    child.stdin.end(input);
  });
}

async function runCli(executable, knowledge, tool, arguments_, requestId, env = {}) {
  const request = {
    schemaVersion: 1,
    kind: 'request',
    requestId,
    tool,
    framework: knowledge.framework,
    limits: {
      timeoutMs: tool === 'validate_code' ? 120_000 : 10_000,
      maxInputBytes: 4 * 1024 * 1024,
      maxOutputBytes: 1024 * 1024,
    },
    arguments: arguments_,
  };
  const result = await runStreaming(executable, JSON.stringify(request), {env});
  if (result.stderr !== '') throw new Error('Installed CLI emitted unexpected stderr.');
  return JSON.parse(result.stdout);
}

async function runMcp(executable, messages) {
  const result = await runStreaming(executable, `${messages.map(JSON.stringify).join('\n')}\n`);
  if (result.stderr !== '') throw new Error('Installed MCP server emitted unexpected stderr.');
  return result.stdout.trim().split('\n').filter(Boolean).map(JSON.parse);
}

function modernMeta(version) {
  return {
    'io.modelcontextprotocol/protocolVersion': version,
    'io.modelcontextprotocol/clientInfo': {name: 'distribution-verifier', version: '1.0.0'},
    'io.modelcontextprotocol/clientCapabilities': {},
  };
}

async function verifyInstalledFiles(packageRoot, manifest) {
  for (const file of manifest.files) {
    const path = resolve(packageRoot, file.path);
    const metadata = await lstat(path);
    if (!metadata.isFile() || metadata.isSymbolicLink()) {
      throw new Error(`Installed package entry is not a regular file: ${file.path}`);
    }
    const content = await readFile(path);
    if (content.length !== file.bytes || sha256(content) !== file.sha256) {
      throw new Error(`Installed package integrity failed for ${file.path}.`);
    }
  }
}

async function verifyInventory(packageRoot, contract) {
  const packageMetadata = await readJson(resolve(packageRoot, 'package.json'));
  const distribution = await readJson(resolve(packageRoot, 'distribution.json'));
  const sbom = await readJson(resolve(packageRoot, 'sbom.spdx.json'));
  const licenses = await readJson(resolve(packageRoot, 'third-party-licenses.json'));
  if (
    packageMetadata.name !== contract.package.name ||
    packageMetadata.version !== contract.package.version ||
    packageMetadata.license !== contract.package.license ||
    Object.keys(packageMetadata.dependencies ?? {}).length !== 0
  ) {
    throw new Error('Installed package metadata differs from the frozen dependency-free contract.');
  }
  if (
    JSON.stringify(distribution.tools) !== JSON.stringify(contract.contents.tools) ||
    JSON.stringify(distribution.skills) !== JSON.stringify([...contract.contents.skills].sort()) ||
    JSON.stringify(distribution.compatibility.protocolVersions) !==
      JSON.stringify(contract.compatibility.protocolVersions)
  ) {
    throw new Error('Installed distribution capability metadata differs from the frozen contract.');
  }
  if (
    sbom.spdxVersion !== contract.integrity.sbom.format ||
    sbom.packages?.length !== contract.integrity.sbom.packageCount ||
    sbom.packages[0].filesAnalyzed !== contract.integrity.sbom.filesAnalyzed ||
    sbom.packages[0].licenseDeclared !== contract.package.license ||
    licenses.reviewStatus !== 'passed' ||
    licenses.distributedRuntimeDependencies?.length !== 0
  ) {
    throw new Error('Installed SPDX or license inventory differs from the frozen contract.');
  }
  const encoded = JSON.stringify({packageMetadata, distribution, sbom, licenses});
  if (encoded.includes(repositoryRoot)) {
    throw new Error('Installed metadata contains a local absolute repository path.');
  }
}

async function verifyCliFlow(cli, knowledge, installedPackageRoot) {
  const component = await runCli(cli, knowledge, 'get_component_reference', {
    versionLane: 'current-source',
    name: 'Column',
  }, 'distribution-component');
  if (component.status !== 'success' || component.evidence.level !== 'knowledge') {
    throw new Error('Installed CLI did not retrieve the exact Column component.');
  }

  const sample = await runCli(cli, knowledge, 'get_sample', {
    versionLane: 'current-source',
    sampleId: 'module.ui-foundation-profile-summary',
  }, 'distribution-sample');
  if (sample.status !== 'success' || sample.data.executable !== true) {
    throw new Error('Installed CLI did not retrieve the frozen compiled sample.');
  }

  const source = await readFile(compileFixturePath, 'utf8');
  const rejected = await runCli(cli, knowledge, 'validate_code', {
    mode: 'compile',
    source,
    path: 'DistributionExample.kt',
    artifactIds: ['viewcompose-ui-foundation'],
    capabilityIds: ['foundation.components'],
  }, 'distribution-mismatched-source', {VIEWCOMPOSE_SOURCE_ROOT: installedPackageRoot});
  if (
    rejected.status !== 'unsupported' ||
    rejected.diagnostics?.[0]?.code !== 'VC-AI-SOURCE-ROOT-MISMATCH'
  ) {
    throw new Error('Installed CLI did not reject a mismatched configured source checkout.');
  }
  const compiled = await runCli(cli, knowledge, 'validate_code', {
    mode: 'compile',
    source,
    path: 'DistributionExample.kt',
    artifactIds: ['viewcompose-ui-foundation'],
    capabilityIds: ['foundation.components'],
  }, 'distribution-compile', {VIEWCOMPOSE_SOURCE_ROOT: repositoryRoot});
  if (compiled.status !== 'success' || compiled.evidence.level !== 'compiled') {
    throw new Error('Installed CLI did not compile the frozen end-to-end sample.');
  }
  return compiled.evidence.outputFingerprint;
}

async function verifyMcpMatrix(mcp, contract) {
  const modernVersion = contract.compatibility.protocolVersions[0];
  const modern = await runMcp(mcp, [{
    jsonrpc: '2.0',
    id: 'modern-list',
    method: 'tools/list',
    params: {_meta: modernMeta(modernVersion)},
  }]);
  if (
    modern.length !== 1 ||
    modern[0].result?.tools?.map((tool) => tool.name).join(',') !== contract.contents.tools.join(',')
  ) {
    throw new Error(`Installed MCP server failed modern protocol ${modernVersion}.`);
  }

  const legacyVersion = contract.compatibility.protocolVersions[1];
  const legacy = await runMcp(mcp, [
    {
      jsonrpc: '2.0',
      id: 'legacy-init',
      method: 'initialize',
      params: {
        protocolVersion: legacyVersion,
        capabilities: {},
        clientInfo: {name: 'distribution-verifier', version: '1.0.0'},
      },
    },
    {jsonrpc: '2.0', method: 'notifications/initialized'},
    {jsonrpc: '2.0', id: 'legacy-list', method: 'tools/list', params: {}},
  ]);
  const initialized = legacy.find((response) => response.id === 'legacy-init');
  const listing = legacy.find((response) => response.id === 'legacy-list');
  if (
    initialized?.result?.protocolVersion !== legacyVersion ||
    listing?.result?.tools?.map((tool) => tool.name).join(',') !== contract.contents.tools.join(',')
  ) {
    throw new Error(`Installed MCP server failed legacy protocol ${legacyVersion}.`);
  }
}

async function main() {
  const contract = await readJson(contractPath);
  const knowledge = await readJson(knowledgePath);
  const temporaryRoot = await mkdtemp(resolve(tmpdir(), 'viewcompose-ai-distribution-'));
  const comparisonRoot = resolve(temporaryRoot, 'comparison');
  const prefix = resolve(temporaryRoot, 'prefix');
  let uninstalled = false;
  try {
    const primary = await createDistribution({outputRoot});
    const comparison = await createDistribution({outputRoot: comparisonRoot});
    if (
      primary.manifest.archive.sha256 !== comparison.manifest.archive.sha256 ||
      !(await readFile(primary.archivePath)).equals(await readFile(comparison.archivePath))
    ) {
      throw new Error('Two clean package builds did not produce the same archive bytes.');
    }

    const archive = await readFile(primary.archivePath);
    if (sha256(archive) !== primary.manifest.archive.sha256) {
      throw new Error('Packaged archive SHA-256 differs from its sidecar manifest.');
    }
    const checksums = await readFile(primary.checksumsPath, 'utf8');
    if (!checksums.includes(`${primary.manifest.archive.sha256}  ${primary.manifest.archive.path}`)) {
      throw new Error('SHA256SUMS does not cover the packaged archive.');
    }

    await execFileAsync('npm', [
      'install',
      '--global',
      '--prefix',
      prefix,
      '--offline',
      '--ignore-scripts',
      '--no-audit',
      '--no-fund',
      primary.archivePath,
    ], {
      cwd: temporaryRoot,
      env: {...process.env, ...npmEnvironment},
      encoding: 'utf8',
      maxBuffer: 4 * 1024 * 1024,
    });
    const {stdout: npmRootOutput} = await execFileAsync('npm', ['root', '--global', '--prefix', prefix], {
      env: {...process.env, ...npmEnvironment},
      encoding: 'utf8',
    });
    const packageRoot = resolve(npmRootOutput.trim(), '@viewcompose/ai-tooling');
    const cli = resolve(prefix, 'bin/viewcompose-ai');
    const mcp = resolve(prefix, 'bin/viewcompose-mcp');
    await Promise.all([access(cli), access(mcp), access(packageRoot)]);
    await verifyInstalledFiles(packageRoot, primary.manifest);
    await verifyInventory(packageRoot, contract);
    const compileFingerprint = await verifyCliFlow(cli, knowledge, packageRoot);
    await verifyMcpMatrix(mcp, contract);

    await execFileAsync('npm', [
      'uninstall',
      '--global',
      '--prefix',
      prefix,
      '--offline',
      '--ignore-scripts',
      '--no-audit',
      '--no-fund',
      contract.package.name,
    ], {
      cwd: temporaryRoot,
      env: {...process.env, ...npmEnvironment},
      encoding: 'utf8',
      maxBuffer: 4 * 1024 * 1024,
    });
    uninstalled = true;
    if (await exists(cli) || await exists(mcp) || await exists(packageRoot)) {
      throw new Error('Offline uninstallation left package or executable entries behind.');
    }

    process.stdout.write(
      `Verified ViewCompose AI distribution: 2/2 reproducible builds, ` +
      `1/1 offline install-uninstall lifecycle, 1/1 SPDX/license inventory, ` +
      `2/2 installed MCP protocol versions, and compiled example ${compileFingerprint}.\n`,
    );
  } finally {
    if (!uninstalled) {
      await rm(prefix, {recursive: true, force: true});
    }
    await rm(temporaryRoot, {recursive: true, force: true});
  }
}

main().catch((error) => {
  process.stderr.write(`ViewCompose AI distribution verification failed: ${error.message}\n`);
  process.exitCode = 1;
});
