#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {execFile, spawn} from 'node:child_process';
import {
  access,
  lstat,
  mkdir,
  mkdtemp,
  readFile,
  realpath,
  rm,
  symlink,
  writeFile,
} from 'node:fs/promises';
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
const xmlFixturePath = fileURLToPath(
  new URL('../evaluation/fixtures/xml/login.xml', import.meta.url),
);
const generatedPreviewContractPath = fileURLToPath(
  new URL('../evaluation/fixtures/xml/generated-preview-contract.json', import.meta.url),
);
const layoutComparisonContractPath = fileURLToPath(
  new URL('../evaluation/fixtures/xml/layout-comparison-contract.json', import.meta.url),
);
const screenshotRequestPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot/privacy-grid.request.json', import.meta.url),
);
const screenshotResultPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot/privacy-grid.result.json', import.meta.url),
);
const inferencePreprocessingRequestPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot/inference-wireframe.request.json', import.meta.url),
);
const inferenceRequestPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-inference/wireframe.request.json', import.meta.url),
);
const inferenceResultPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-inference/wireframe.result.json', import.meta.url),
);
const resolutionRequestPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-resolution/wireframe.request.json', import.meta.url),
);
const screenshotGenerationRequestPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-generation/wireframe.request.json', import.meta.url),
);
const screenshotRenderGenerationRequestPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-render/wireframe.generation-request.json', import.meta.url),
);
const screenshotRenderPreviewRequestPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-render/wireframe.preview-request.json', import.meta.url),
);
const screenshotGeneratedPreviewContractPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-generated-preview-contract.json', import.meta.url),
);
const screenshotComparisonContractPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-layout-comparison-contract.json', import.meta.url),
);
const screenshotCompareGenerationRequestPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-compare/wireframe.generation-request.json', import.meta.url),
);
const screenshotPixelComparisonContractPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-pixel-comparison-contract.json', import.meta.url),
);
const screenshotPixelGenerationRequestPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-pixel/wireframe.generation-request.json', import.meta.url),
);
const screenshotPixelReferenceRequestPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-pixel/pixel-reference.request.json', import.meta.url),
);
const screenshotPixelReferenceResultPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-pixel/pixel-reference.result.json', import.meta.url),
);
const generatedPreviewRequestPath = fileURLToPath(
  new URL('../evaluation/fixtures/xml/generated-preview/login.preview-request.json', import.meta.url),
);
const generatedImagePreviewRequestPath = fileURLToPath(
  new URL('../evaluation/fixtures/xml/generated-preview/image-binding.preview-request.json', import.meta.url),
);
const xmlV2FixturePath = fileURLToPath(
  new URL('../evaluation/fixtures/xml/profile-card.xml', import.meta.url),
);
const xmlProjectFixtureRoot = fileURLToPath(
  new URL('../evaluation/fixtures/xml/project-context/supported/', import.meta.url),
);
const xmlLayoutDependencyFixtureRoot = fileURLToPath(
  new URL('../evaluation/fixtures/xml/layout-dependencies/supported/', import.meta.url),
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
      timeoutMs: tool === 'validate_code' ||
        ['compile', 'render', 'compare', 'compare-pixels'].includes(arguments_.mode) ||
        ['compile', 'render', 'compare', 'compare-pixels']
          .includes(arguments_.generationRequest?.mode)
        ? 120_000
        : 10_000,
      maxInputBytes: 4 * 1024 * 1024,
      maxOutputBytes: 1024 * 1024,
    },
    arguments: arguments_,
  };
  const result = await runStreaming(executable, JSON.stringify(request), {env});
  if (result.stderr !== '') throw new Error('Installed CLI emitted unexpected stderr.');
  return JSON.parse(result.stdout);
}

function runMcp(executable, messages, {timeoutMs = 180_000, env = {}} = {}) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(executable, [], {
      cwd: repositoryRoot,
      env: {...process.env, ...env},
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    const expectedResponses = messages.filter((message) => message.id !== undefined).length;
    const responses = [];
    const stderr = [];
    let stdoutBuffer = '';
    let outputBytes = 0;
    let settled = false;
    const settle = (callback, value) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      callback(value);
    };
    const timer = setTimeout(() => {
      child.kill('SIGKILL');
      settle(reject, new Error(`${executable} exceeded ${timeoutMs} ms.`));
    }, timeoutMs);
    child.stderr.on('data', (chunk) => stderr.push(chunk));
    child.stdout.on('data', (chunk) => {
      outputBytes += chunk.length;
      if (outputBytes > 8 * 1024 * 1024) {
        child.kill('SIGKILL');
        settle(reject, new Error(`${executable} exceeded the verifier output bound.`));
        return;
      }
      stdoutBuffer += chunk.toString('utf8');
      let newline = stdoutBuffer.indexOf('\n');
      while (newline !== -1) {
        const line = stdoutBuffer.slice(0, newline).trim();
        stdoutBuffer = stdoutBuffer.slice(newline + 1);
        if (line !== '') responses.push(JSON.parse(line));
        newline = stdoutBuffer.indexOf('\n');
      }
      if (responses.length === expectedResponses && !child.stdin.destroyed) child.stdin.end();
    });
    child.once('error', (error) => settle(reject, error));
    child.once('close', (exitCode) => {
      const errorOutput = Buffer.concat(stderr).toString('utf8');
      if (exitCode !== 0) {
        settle(reject, new Error(`${executable} exited ${exitCode}: ${errorOutput.slice(0, 4096)}`));
      } else if (errorOutput !== '') {
        settle(reject, new Error('Installed MCP server emitted unexpected stderr.'));
      } else if (responses.length !== expectedResponses) {
        settle(reject, new Error(
          `Installed MCP server returned ${responses.length} of ${expectedResponses} responses.`,
        ));
      } else {
        settle(resolvePromise, responses);
      }
    });
    child.stdin.write(`${messages.map(JSON.stringify).join('\n')}\n`);
  });
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

async function runAgentCommand(agent, arguments_) {
  const result = await execFileAsync(agent, arguments_, {
    cwd: repositoryRoot,
    encoding: 'utf8',
    maxBuffer: 1024 * 1024,
  });
  if (result.stderr !== '') throw new Error('Installed agent integration command emitted stderr.');
  return result.stdout;
}

async function expectAgentFailure(agent, arguments_, pattern) {
  try {
    await runAgentCommand(agent, arguments_);
  } catch (error) {
    if (pattern.test(`${error.stderr ?? ''}\n${error.message ?? ''}`)) return;
    throw error;
  }
  throw new Error(`Installed agent integration command unexpectedly succeeded: ${arguments_.join(' ')}`);
}

async function verifyInstalledAgentClients(agent, packageRoot, temporaryRoot) {
  const [profiles, skills, canonicalSourceRoot] = await Promise.all([
    readJson(resolve(packageRoot, 'contracts/examples/agent-client-integration.json')),
    readJson(resolve(packageRoot, 'skills/manifest.json')),
    realpath(repositoryRoot),
  ]);
  const installedMcp = await realpath(resolve(packageRoot, 'scripts/mcp-server.mjs'));
  for (const profile of profiles.clients) {
    const projectRoot = resolve(temporaryRoot, `agent-${profile.id}`);
    await mkdir(projectRoot);
    const canonicalProjectRoot = await realpath(projectRoot);
    const standaloneConfig = await runAgentCommand(agent, [
      'config',
      '--client',
      profile.id,
      '--project-root',
      canonicalProjectRoot,
    ]);
    const sourceBoundConfig = await runAgentCommand(agent, [
      'config',
      '--client',
      profile.id,
      '--project-root',
      canonicalProjectRoot,
      '--source-root',
      canonicalSourceRoot,
    ]);
    if (profile.config.format === 'json') {
      const standalone = JSON.parse(standaloneConfig);
      const parsed = JSON.parse(sourceBoundConfig);
      if (
        standalone.mcpServers?.viewcompose?.command !== process.execPath ||
        standalone.mcpServers?.viewcompose?.args?.[0] !== installedMcp ||
        standalone.mcpServers?.viewcompose?.env?.VIEWCOMPOSE_PROJECT_ROOT !==
          canonicalProjectRoot ||
        parsed.mcpServers?.viewcompose?.command !== process.execPath ||
        parsed.mcpServers?.viewcompose?.args?.[0] !== installedMcp ||
        parsed.mcpServers?.viewcompose?.env?.VIEWCOMPOSE_PROJECT_ROOT !== canonicalProjectRoot ||
        parsed.mcpServers?.viewcompose?.env?.VIEWCOMPOSE_SOURCE_ROOT !== canonicalSourceRoot
      ) throw new Error(`Installed ${profile.id} configuration does not bind the packaged MCP server.`);
    } else if (
      !standaloneConfig.includes('[mcp_servers.viewcompose]') ||
      !standaloneConfig.includes('[mcp_servers.viewcompose.env]') ||
      !standaloneConfig.includes(JSON.stringify(canonicalProjectRoot)) ||
      !sourceBoundConfig.includes('[mcp_servers.viewcompose.env]') ||
      !sourceBoundConfig.includes(JSON.stringify(installedMcp)) ||
      !sourceBoundConfig.includes(JSON.stringify(canonicalSourceRoot))
    ) {
      throw new Error('Installed Codex configuration does not bind the packaged MCP server.');
    }

    const first = JSON.parse(await runAgentCommand(agent, [
      'init',
      '--client',
      profile.id,
      '--project-root',
      canonicalProjectRoot,
    ]));
    if (
      first.mode !== 'project-bound' ||
      first.config.status !== 'installed' ||
      first.skills.installed.length !== skills.skills.length ||
      first.skills.unchanged.length !== 0
    ) {
      throw new Error(`Installed ${profile.id} project-bound initialization was incomplete.`);
    }
    for (const skill of skills.skills) {
      const actual = await readFile(resolve(projectRoot, profile.skills.projectPath, skill.id, 'SKILL.md'));
      const expected = await readFile(resolve(packageRoot, skill.path));
      if (!actual.equals(expected)) throw new Error(`Installed ${profile.id}/${skill.id} bytes drifted.`);
    }
    const doctor = JSON.parse(await runAgentCommand(agent, [
      'doctor',
      '--client',
      profile.id,
      '--project-root',
      canonicalProjectRoot,
    ]));
    if (
      doctor.status !== 'project-bound-ready' ||
      doctor.capabilities.knowledgeAndGeneration !== 'ready' ||
      doctor.capabilities.compilationPreviewAndLayout !== 'project-bound-ready' ||
      doctor.host?.status !== 'ready'
    ) throw new Error(`Installed ${profile.id} project-bound doctor drifted.`);
    const second = JSON.parse(await runAgentCommand(agent, [
      'init',
      '--client',
      profile.id,
      '--project-root',
      canonicalProjectRoot,
    ]));
    if (
      second.config.status !== 'unchanged' ||
      second.skills.installed.length !== 0 ||
      second.skills.unchanged.length !== skills.skills.length
    ) {
      throw new Error(`Installed ${profile.id} lifecycle was not idempotent.`);
    }
    const removed = JSON.parse(await runAgentCommand(agent, [
      'uninstall',
      '--client',
      profile.id,
      '--project-root',
      canonicalProjectRoot,
    ]));
    if (removed.config.status !== 'removed' || removed.skills.removed.length !== skills.skills.length) {
      throw new Error(`Installed ${profile.id} lifecycle did not uninstall cleanly.`);
    }
  }

  const conflictRoot = resolve(temporaryRoot, 'agent-conflict');
  await mkdir(conflictRoot);
  const conflictInstall = JSON.parse(await runAgentCommand(agent, [
    'install-skills',
    '--client',
    'codex',
    '--project-root',
    await realpath(conflictRoot),
  ]));
  await writeFile(
    resolve(conflictRoot, conflictInstall.skillRoot, conflictInstall.installed[0], 'SKILL.md'),
    'conflict\n',
  );
  await expectAgentFailure(agent, [
    'install-skills',
    '--client',
    'codex',
    '--project-root',
    await realpath(conflictRoot),
  ], /Refusing to overwrite conflicting Skill bytes/u);
  await expectAgentFailure(agent, [
    'install-skills',
    '--client',
    'codex',
    '--project-root',
    'relative',
  ], /absolute path/u);
  const physicalRoot = resolve(temporaryRoot, 'agent-physical');
  const linkedRoot = resolve(temporaryRoot, 'agent-linked');
  await mkdir(physicalRoot);
  await symlink(physicalRoot, linkedRoot, 'dir');
  await expectAgentFailure(agent, [
    'install-skills',
    '--client',
    'cursor',
    '--project-root',
    linkedRoot,
  ], /symbolic link/u);
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
    licenses.distributedRuntimeDependencies?.length !== 0 ||
    licenses.developmentToolsIncluded !== true ||
    licenses.distributedDevelopmentTools?.[0]?.name !== 'Gradle Wrapper' ||
    licenses.distributedDevelopmentTools?.[0]?.version !== '9.3.1'
  ) {
    throw new Error('Installed SPDX or license inventory differs from the frozen contract.');
  }
  const encoded = JSON.stringify({packageMetadata, distribution, sbom, licenses});
  if (encoded.includes(repositoryRoot)) {
    throw new Error('Installed metadata contains a local absolute repository path.');
  }
}

async function verifyCliFlow(
  cli,
  knowledge,
  installedPackageRoot,
  generatedPreviewContract,
  layoutComparisonContract,
  screenshotGeneratedPreviewContract,
  screenshotComparisonContract,
  screenshotPixelComparisonContract,
) {
  const [screenshotRequest, screenshotExpected] = await Promise.all([
    readJson(screenshotRequestPath),
    readJson(screenshotResultPath),
  ]);
  const screenshot = await runCli(
    cli,
    knowledge,
    'prepare_screenshot',
    screenshotRequest,
    'distribution-screenshot',
  );
  if (
    screenshot.status !== 'success' ||
    screenshot.evidence.level !== 'static' ||
    screenshot.evidence.outputFingerprint !== screenshotExpected.outputFingerprint ||
    JSON.stringify(screenshot.data) !== JSON.stringify(screenshotExpected)
  ) {
    throw new Error('Installed CLI did not reproduce the frozen screenshot preprocessing golden.');
  }
  const [inferencePreprocessingRequest, inferenceRequest, inferenceResult] = await Promise.all([
    readJson(inferencePreprocessingRequestPath),
    readJson(inferenceRequestPath),
    readJson(inferenceResultPath),
  ]);
  const {interpretation, intent, policy, authorization} = inferenceRequest;
  const inferenceArguments = {
    preprocessingRequest: inferencePreprocessingRequest,
    inferenceDeclaration: {interpretation, intent, policy, authorization},
    inferenceResult,
  };
  const validatedInference = await runCli(
    cli,
    knowledge,
    'validate_screenshot_inference',
    inferenceArguments,
    'distribution-screenshot-inference',
  );
  if (
    validatedInference.status !== 'success' ||
    validatedInference.evidence.level !== 'static' ||
    validatedInference.evidence.outputFingerprint !==
      'a9ebb9732105d35eab22ed56f67a6b1f02396985a5b19344b6be21b9f59e48ab' ||
    validatedInference.data?.summary?.codeGenerationAllowed !== false
  ) {
    throw new Error('Installed CLI did not validate the frozen screenshot inference golden.');
  }
  const resolutionRequest = await readJson(resolutionRequestPath);
  const resolvedInference = await runCli(
    cli,
    knowledge,
    'resolve_screenshot_inference',
    {validatedInference: validatedInference.data, resolutionRequest},
    'distribution-screenshot-resolution',
  );
  if (
    resolvedInference.status !== 'success' ||
    resolvedInference.evidence.level !== 'static' ||
    resolvedInference.evidence.outputFingerprint !==
      'acdc3a7ae1b43207ce885d4762c77630394e9734caf7305a896e6c90878274ee' ||
    resolvedInference.data?.summary?.codeGenerationAllowed !== true
  ) {
    throw new Error('Installed CLI did not resolve the frozen screenshot inference golden.');
  }
  const generationRequest = await readJson(screenshotGenerationRequestPath);
  generationRequest.mode = 'generate';
  const generatedScreenshot = await runCli(
    cli,
    knowledge,
    'generate_screenshot_viewcompose',
    {resolutionResult: resolvedInference.data, generationRequest},
    'distribution-screenshot-generation',
  );
  if (
    generatedScreenshot.status !== 'success' ||
    generatedScreenshot.evidence.level !== 'static' ||
    generatedScreenshot.evidence.outputFingerprint !==
      '5812c3ccbd0a6f30a0cc4c3ff4e71453006745d5dd76e63e153b2501131252e9' ||
    generatedScreenshot.data?.generationReport?.bindings?.states?.length !== 1 ||
    generatedScreenshot.data?.generationReport?.bindings?.events?.length !== 2 ||
    generatedScreenshot.data?.generationReport?.accessibility?.nodes?.length !== 4
  ) {
    throw new Error('Installed CLI did not generate the frozen screenshot Kotlin golden.');
  }
  generationRequest.mode = 'compile';
  const compiledScreenshot = await runCli(
    cli,
    knowledge,
    'generate_screenshot_viewcompose',
    {resolutionResult: resolvedInference.data, generationRequest},
    'distribution-screenshot-generation-compile',
    {VIEWCOMPOSE_PROJECT_ROOT: repositoryRoot},
  );
  if (
    compiledScreenshot.status !== 'success' ||
    compiledScreenshot.evidence.level !== 'compiled' ||
    compiledScreenshot.data?.kotlinFingerprint !==
      '5812c3ccbd0a6f30a0cc4c3ff4e71453006745d5dd76e63e153b2501131252e9' ||
    compiledScreenshot.data?.generationReport?.reportFingerprint !==
      '91da4ff1eaf1f4d2fb0f8c73d8816d2c91030510ff00730ee96abe80f0efa319'
  ) {
    throw new Error('Installed CLI did not compile the frozen screenshot Kotlin golden.');
  }
  const [renderGenerationRequest, screenshotPreviewRequest] = await Promise.all([
    readJson(screenshotRenderGenerationRequestPath),
    readJson(screenshotRenderPreviewRequestPath),
  ]);
  const renderedScreenshot = await runCli(
    cli,
    knowledge,
    'generate_screenshot_viewcompose',
    {
      resolutionResult: resolvedInference.data,
      generationRequest: renderGenerationRequest,
      previewBindings: screenshotPreviewRequest.bindings,
    },
    'distribution-screenshot-render',
    {VIEWCOMPOSE_PROJECT_ROOT: repositoryRoot},
  );
  const expectedScreenshotPreview = screenshotGeneratedPreviewContract.supportedFixtures[0];
  if (
    renderedScreenshot.status !== 'success' ||
    renderedScreenshot.evidence.level !== 'rendered' ||
    renderedScreenshot.evidence.outputFingerprint !==
      expectedScreenshotPreview.expectedOutputFingerprint ||
    renderedScreenshot.data?.generationReport?.reportFingerprint !==
      screenshotGeneratedPreviewContract.lineage.renderGenerationReportFingerprint ||
    renderedScreenshot.data?.preview?.targetId !==
      screenshotGeneratedPreviewContract.profile.targetId ||
    renderedScreenshot.data?.preview?.generatedPreview?.requestFingerprint !==
      screenshotGeneratedPreviewContract.lineage.previewRequestFingerprint ||
    renderedScreenshot.data?.preview?.image?.sha256 !==
      expectedScreenshotPreview.expectedImage.sha256 ||
    renderedScreenshot.data?.preview?.renderTree?.sha256 !==
      expectedScreenshotPreview.expectedRenderTree.sha256
  ) {
    throw new Error(
      'Installed CLI did not render the frozen screenshot-generated Preview: ' +
      JSON.stringify({
        status: renderedScreenshot.status,
        evidence: renderedScreenshot.evidence,
        diagnosticCodes: renderedScreenshot.diagnostics?.map((item) => item.code),
        reportFingerprint: renderedScreenshot.data?.generationReport?.reportFingerprint,
        targetId: renderedScreenshot.data?.preview?.targetId,
        requestFingerprint:
          renderedScreenshot.data?.preview?.generatedPreview?.requestFingerprint,
        imageFingerprint: renderedScreenshot.data?.preview?.image?.sha256,
        renderTreeFingerprint: renderedScreenshot.data?.preview?.renderTree?.sha256,
      }),
    );
  }
  const compareGenerationRequest = await readJson(screenshotCompareGenerationRequestPath);
  const comparedScreenshot = await runCli(
    cli,
    knowledge,
    'generate_screenshot_viewcompose',
    {
      resolutionResult: resolvedInference.data,
      generationRequest: compareGenerationRequest,
      previewBindings: screenshotPreviewRequest.bindings,
    },
    'distribution-screenshot-compare',
    {VIEWCOMPOSE_PROJECT_ROOT: repositoryRoot},
  );
  const expectedScreenshotComparison = screenshotComparisonContract.supportedFixtures[0];
  if (
    comparedScreenshot.status !== 'success' ||
    comparedScreenshot.evidence.level !== 'compared' ||
    comparedScreenshot.evidence.outputFingerprint !==
      expectedScreenshotComparison.expectedComparisonFingerprint ||
    comparedScreenshot.data?.comparison?.comparisonFingerprint !==
      expectedScreenshotComparison.expectedComparisonFingerprint ||
    JSON.stringify(comparedScreenshot.data?.comparison?.summary) !==
      JSON.stringify(expectedScreenshotComparison.expectedSummary) ||
    comparedScreenshot.data?.preview?.renderTree?.sha256 !==
      screenshotComparisonContract.lineage.acceptedRenderTreeFingerprint
  ) {
    throw new Error('Installed CLI did not compare the screenshot-generated layout exactly.');
  }
  const [pixelGenerationRequest, pixelReferenceRequest, pixelReferenceResult] = await Promise.all([
    readJson(screenshotPixelGenerationRequestPath),
    readJson(screenshotPixelReferenceRequestPath),
    readJson(screenshotPixelReferenceResultPath),
  ]);
  const pixelComparedScreenshot = await runCli(
    cli,
    knowledge,
    'generate_screenshot_viewcompose',
    {
      resolutionResult: resolvedInference.data,
      generationRequest: pixelGenerationRequest,
      previewBindings: screenshotPreviewRequest.bindings,
      pixelReference: {request: pixelReferenceRequest, result: pixelReferenceResult},
    },
    'distribution-screenshot-compare-pixels',
    {VIEWCOMPOSE_PROJECT_ROOT: repositoryRoot},
  );
  const expectedPixelComparison = screenshotPixelComparisonContract.supportedFixtures[0];
  if (
    pixelComparedScreenshot.status !== 'success' ||
    pixelComparedScreenshot.evidence.level !== 'compared' ||
    pixelComparedScreenshot.evidence.outputFingerprint !==
      expectedPixelComparison.expectedComparisonFingerprint ||
    pixelComparedScreenshot.data?.comparison?.comparisonFingerprint !==
      screenshotPixelComparisonContract.lineage.semanticComparisonFingerprint ||
    pixelComparedScreenshot.data?.pixelComparison?.comparisonFingerprint !==
      expectedPixelComparison.expectedComparisonFingerprint ||
    JSON.stringify(pixelComparedScreenshot.data?.pixelComparison?.metrics) !==
      JSON.stringify(expectedPixelComparison.expectedMetrics) ||
    pixelComparedScreenshot.data?.pixelLocalization?.pixelComparisonFingerprint !==
      expectedPixelComparison.expectedComparisonFingerprint ||
    pixelComparedScreenshot.data?.pixelLocalization?.localizationFingerprint !==
      expectedPixelComparison.expectedLocalization.localizationFingerprint ||
    pixelComparedScreenshot.data?.pixelLocalization?.status !==
      expectedPixelComparison.expectedLocalization.status ||
    pixelComparedScreenshot.data?.pixelLocalization?.attributions?.length !==
      expectedPixelComparison.expectedLocalization.attributions
  ) {
    throw new Error(
      'Installed CLI did not compare the eligible screenshot pixels exactly: ' +
      JSON.stringify({
        status: pixelComparedScreenshot.status,
        evidence: pixelComparedScreenshot.evidence,
        diagnosticCodes: pixelComparedScreenshot.diagnostics?.map((item) => item.code),
        semanticFingerprint:
          pixelComparedScreenshot.data?.comparison?.comparisonFingerprint,
        pixelFingerprint:
          pixelComparedScreenshot.data?.pixelComparison?.comparisonFingerprint,
        metrics: pixelComparedScreenshot.data?.pixelComparison?.metrics,
        localization: pixelComparedScreenshot.data?.pixelLocalization,
        image: pixelComparedScreenshot.data?.preview?.image,
      }),
    );
  }
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

  const xml = await readFile(xmlFixturePath, 'utf8');
  const generated = await runCli(cli, knowledge, 'convert_xml_to_viewcompose', {
    source: xml,
    path: 'res/layout/login.xml',
    mode: 'generate',
  }, 'distribution-xml-generate');
  if (
    generated.status !== 'success' ||
    generated.evidence.level !== 'static' ||
    !generated.data?.kotlin?.includes('fun UiTreeBuilder.LoginView(') ||
    generated.data?.migrationReport?.bindings?.resources?.length !== 3
  ) {
    throw new Error('Installed CLI did not generate the frozen standalone XML migration.');
  }

  const projectGenerated = await runCli(cli, knowledge, 'convert_xml_to_viewcompose', {
    projectRoot: xmlProjectFixtureRoot,
    layoutPath: 'app/src/main/res/layout/styled_login.xml',
    resourceRoots: ['app/src/main/res'],
    sourceRoots: ['app/src/main/java'],
    mode: 'generate',
  }, 'distribution-xml-project-generate');
  if (
    projectGenerated.status !== 'success' ||
    !projectGenerated.data?.kotlin?.includes('fun UiTreeBuilder.StyledLoginView(') ||
    projectGenerated.data?.projectContext?.resources?.length !== 4 ||
    projectGenerated.data?.projectContext?.styles?.length !== 2 ||
    projectGenerated.data?.migrationReport?.callSiteReview?.inventory?.length !== 7
  ) {
    throw new Error('Installed CLI did not preserve explicit-root XML project context.');
  }

  const layoutGenerated = await runCli(cli, knowledge, 'convert_xml_to_viewcompose', {
    projectRoot: xmlLayoutDependencyFixtureRoot,
    layoutPath: 'app/src/main/res/layout/screen.xml',
    resourceRoots: ['app/src/main/res'],
    sourceRoots: [],
    mode: 'generate',
  }, 'distribution-xml-layout-dependencies-generate');
  if (
    layoutGenerated.status !== 'success' ||
    !layoutGenerated.data?.kotlin?.includes('fun UiTreeBuilder.ScreenView(') ||
    layoutGenerated.data?.layoutDependencies?.nodes?.length !== 3 ||
    layoutGenerated.data?.layoutDependencies?.edges?.length !== 2 ||
    layoutGenerated.data?.designIr?.roots?.[0]?.children?.length !== 4
  ) {
    throw new Error('Installed CLI did not expand the frozen XML layout dependency graph.');
  }

  const xmlV2 = await readFile(xmlV2FixturePath, 'utf8');
  const generatedV2 = await runCli(cli, knowledge, 'convert_xml_to_viewcompose', {
    source: xmlV2,
    path: 'res/layout/profile_card.xml',
    mode: 'generate',
  }, 'distribution-xml-v2-generate');
  if (
    generatedV2.status !== 'success' ||
    !generatedV2.data?.kotlin?.includes('fun UiTreeBuilder.ProfileCardView(') ||
    generatedV2.data?.migrationReport?.bindings?.resources?.[0]?.type !== 'ImageSource'
  ) {
    throw new Error('Installed CLI did not generate the frozen XML v2 migration.');
  }

  const rejectedXmlCompile = await runCli(cli, knowledge, 'convert_xml_to_viewcompose', {
    source: xml,
    path: 'res/layout/login.xml',
    mode: 'compile',
  }, 'distribution-xml-missing-project', {
    VIEWCOMPOSE_PROJECT_ROOT: resolve(installedPackageRoot, 'missing-project'),
  });
  if (
    rejectedXmlCompile.status !== 'unsupported' ||
    rejectedXmlCompile.diagnostics?.[0]?.code !== 'VC-AI-PROJECT-ROOT-MISMATCH'
  ) {
    throw new Error('Installed XML compile did not reject a mismatched source checkout.');
  }
  const compiledXml = await runCli(cli, knowledge, 'convert_xml_to_viewcompose', {
    source: xml,
    path: 'res/layout/login.xml',
    mode: 'compile',
  }, 'distribution-xml-compile', {VIEWCOMPOSE_PROJECT_ROOT: repositoryRoot});
  if (compiledXml.status !== 'success' || compiledXml.evidence.level !== 'compiled') {
    throw new Error('Installed CLI did not compile the frozen XML migration.');
  }
  const previewRequest = await readJson(generatedPreviewRequestPath);
  const renderedXml = await runCli(cli, knowledge, 'convert_xml_to_viewcompose', {
    source: xml,
    path: 'res/layout/login.xml',
    mode: 'render',
    previewBindings: previewRequest.bindings,
  }, 'distribution-xml-render', {VIEWCOMPOSE_PROJECT_ROOT: repositoryRoot});
  const expectedPreview = generatedPreviewContract.supportedFixtures[0];
  const expectedComparison = layoutComparisonContract.supportedFixtures.find(
    (fixture) => fixture.source === 'login.xml',
  );
  if (
    renderedXml.status !== 'success' ||
    renderedXml.evidence.level !== 'compared' ||
    renderedXml.evidence.outputFingerprint !== expectedComparison.expectedComparisonFingerprint ||
    renderedXml.data?.comparison?.render?.outputFingerprint !==
      expectedPreview.expectedOutputFingerprint ||
    renderedXml.data?.comparison?.comparisonFingerprint !==
      expectedComparison.expectedComparisonFingerprint ||
    renderedXml.data?.preview?.generatedPreview?.requestFingerprint !==
      expectedPreview.expectedRequestFingerprint ||
    renderedXml.data?.preview?.image?.sha256 !== expectedPreview.expectedImage.sha256 ||
    renderedXml.data?.preview?.renderTree?.sha256 !== expectedPreview.expectedRenderTree.sha256
  ) {
    throw new Error(
      'Installed CLI did not render the frozen generated XML Preview: ' + JSON.stringify({
        status: renderedXml.status,
        evidenceLevel: renderedXml.evidence?.level,
        outputFingerprint: renderedXml.evidence?.outputFingerprint,
        diagnosticCodes: renderedXml.diagnostics?.map((diagnostic) => diagnostic.code),
        renderFingerprint: renderedXml.data?.comparison?.render?.outputFingerprint,
        comparisonFingerprint: renderedXml.data?.comparison?.comparisonFingerprint,
        previewRequestFingerprint:
          renderedXml.data?.preview?.generatedPreview?.requestFingerprint,
        imageSha256: renderedXml.data?.preview?.image?.sha256,
        renderTreeSha256: renderedXml.data?.preview?.renderTree?.sha256,
      }),
    );
  }
  const imagePreviewRequest = await readJson(generatedImagePreviewRequestPath);
  const renderedImageXml = await runCli(cli, knowledge, 'convert_xml_to_viewcompose', {
    source: xmlV2,
    path: 'res/layout/profile-card.xml',
    mode: 'render',
    previewBindings: imagePreviewRequest.bindings,
  }, 'distribution-xml-image-render', {VIEWCOMPOSE_PROJECT_ROOT: repositoryRoot});
  const expectedImagePreview = generatedPreviewContract.supportedFixtures.find(
    (fixture) => fixture.expectedFunction === 'ProfileCardView',
  );
  const expectedImageComparison = layoutComparisonContract.supportedFixtures.find(
    (fixture) => fixture.source === 'profile-card.xml',
  );
  if (
    renderedImageXml.status !== 'success' ||
    renderedImageXml.evidence.level !== 'compared' ||
    renderedImageXml.evidence.outputFingerprint !==
      expectedImageComparison.expectedComparisonFingerprint ||
    renderedImageXml.data?.comparison?.render?.outputFingerprint !==
      expectedImagePreview.expectedOutputFingerprint ||
    renderedImageXml.data?.comparison?.comparisonFingerprint !==
      expectedImageComparison.expectedComparisonFingerprint ||
    renderedImageXml.data?.preview?.generatedPreview?.requestFingerprint !==
      expectedImagePreview.expectedRequestFingerprint ||
    renderedImageXml.data?.preview?.generatedPreview?.assets?.[0]?.sha256 !==
      expectedImagePreview.expectedAssetSha256 ||
    renderedImageXml.data?.preview?.image?.sha256 !== expectedImagePreview.expectedImage.sha256 ||
    renderedImageXml.data?.preview?.renderTree?.sha256 !==
      expectedImagePreview.expectedRenderTree.sha256
  ) {
    throw new Error(
      'Installed CLI did not render the frozen generated XML image Preview: ' + JSON.stringify({
        status: renderedImageXml.status,
        evidenceLevel: renderedImageXml.evidence?.level,
        outputFingerprint: renderedImageXml.evidence?.outputFingerprint,
        diagnosticCodes: renderedImageXml.diagnostics?.map((diagnostic) => diagnostic.code),
        renderFingerprint: renderedImageXml.data?.comparison?.render?.outputFingerprint,
        comparisonFingerprint: renderedImageXml.data?.comparison?.comparisonFingerprint,
        previewRequestFingerprint:
          renderedImageXml.data?.preview?.generatedPreview?.requestFingerprint,
        assetSha256: renderedImageXml.data?.preview?.generatedPreview?.assets?.[0]?.sha256,
        imageSha256: renderedImageXml.data?.preview?.image?.sha256,
        renderTreeSha256: renderedImageXml.data?.preview?.renderTree?.sha256,
      }),
    );
  }
  const compiledXmlV2 = await runCli(cli, knowledge, 'convert_xml_to_viewcompose', {
    source: xmlV2,
    path: 'res/layout/profile-card.xml',
    mode: 'compile',
  }, 'distribution-xml-v2-compile', {VIEWCOMPOSE_PROJECT_ROOT: repositoryRoot});
  if (compiledXmlV2.status !== 'success' || compiledXmlV2.evidence.level !== 'compiled') {
    throw new Error('Installed CLI did not compile the frozen XML v2 migration.');
  }
  const compiledXmlLayoutDependencies = await runCli(cli, knowledge, 'convert_xml_to_viewcompose', {
    projectRoot: xmlLayoutDependencyFixtureRoot,
    layoutPath: 'app/src/main/res/layout/screen.xml',
    resourceRoots: ['app/src/main/res'],
    sourceRoots: [],
    mode: 'compile',
  }, 'distribution-xml-layout-dependencies-compile', {VIEWCOMPOSE_PROJECT_ROOT: repositoryRoot});
  if (
    compiledXmlLayoutDependencies.status !== 'success' ||
    compiledXmlLayoutDependencies.evidence.level !== 'compiled'
  ) {
    throw new Error('Installed CLI did not compile the frozen XML layout dependency migration.');
  }

  const source = await readFile(compileFixturePath, 'utf8');
  const rejected = await runCli(cli, knowledge, 'validate_code', {
    mode: 'compile',
    source,
    path: 'DistributionExample.kt',
    artifactIds: ['viewcompose-ui-foundation'],
    capabilityIds: ['foundation.components'],
  }, 'distribution-missing-project', {
    VIEWCOMPOSE_PROJECT_ROOT: resolve(installedPackageRoot, 'missing-project'),
  });
  if (
    rejected.status !== 'unsupported' ||
    rejected.diagnostics?.[0]?.code !== 'VC-AI-PROJECT-ROOT-MISMATCH'
  ) {
    throw new Error('Installed CLI did not reject a mismatched configured source checkout.');
  }
  const compiled = await runCli(cli, knowledge, 'validate_code', {
    mode: 'compile',
    source,
    path: 'DistributionExample.kt',
    artifactIds: ['viewcompose-ui-foundation'],
    capabilityIds: ['foundation.components'],
  }, 'distribution-compile', {VIEWCOMPOSE_PROJECT_ROOT: repositoryRoot});
  if (compiled.status !== 'success' || compiled.evidence.level !== 'compiled') {
    throw new Error('Installed CLI did not compile the frozen end-to-end sample.');
  }
  return {
    screenshot: screenshot.evidence.outputFingerprint,
    screenshotInference: validatedInference.evidence.outputFingerprint,
    screenshotResolution: resolvedInference.evidence.outputFingerprint,
    screenshotGeneration: compiledScreenshot.evidence.outputFingerprint,
    screenshotKotlin: generatedScreenshot.evidence.outputFingerprint,
    screenshotPreview: renderedScreenshot.evidence.outputFingerprint,
    screenshotComparison: comparedScreenshot.evidence.outputFingerprint,
    screenshotPixelComparison: pixelComparedScreenshot.evidence.outputFingerprint,
    sample: compiled.evidence.outputFingerprint,
    xml: compiledXml.evidence.outputFingerprint,
    xmlPreview: renderedXml.evidence.outputFingerprint,
    xmlImagePreview: renderedImageXml.evidence.outputFingerprint,
    xmlV2: compiledXmlV2.evidence.outputFingerprint,
    xmlLayoutDependencies: compiledXmlLayoutDependencies.evidence.outputFingerprint,
  };
}

async function verifyMcpMatrix(mcp, contract) {
  const modernVersion = contract.compatibility.protocolVersions[0];
  const [
    xml,
    xmlV2,
    screenshotRequest,
    screenshotExpected,
    inferencePreprocessingRequest,
    inferenceRequest,
    inferenceResult,
    resolutionRequest,
    generationRequest,
    compareGenerationRequest,
    pixelGenerationRequest,
    pixelReferenceRequest,
    pixelReferenceResult,
    screenshotPreviewRequest,
  ] = await Promise.all([
    readFile(xmlFixturePath, 'utf8'),
    readFile(xmlV2FixturePath, 'utf8'),
    readJson(screenshotRequestPath),
    readJson(screenshotResultPath),
    readJson(inferencePreprocessingRequestPath),
    readJson(inferenceRequestPath),
    readJson(inferenceResultPath),
    readJson(resolutionRequestPath),
    readJson(screenshotGenerationRequestPath),
    readJson(screenshotCompareGenerationRequestPath),
    readJson(screenshotPixelGenerationRequestPath),
    readJson(screenshotPixelReferenceRequestPath),
    readJson(screenshotPixelReferenceResultPath),
    readJson(screenshotRenderPreviewRequestPath),
  ]);
  const {interpretation, intent, policy, authorization} = inferenceRequest;
  const modern = await runMcp(mcp, [{
    jsonrpc: '2.0',
    id: 'modern-list',
    method: 'tools/list',
    params: {_meta: modernMeta(modernVersion)},
  }, {
    jsonrpc: '2.0',
    id: 'modern-xml',
    method: 'tools/call',
    params: {
      name: 'convert_xml_to_viewcompose',
      arguments: {source: xml, path: 'res/layout/login.xml', mode: 'generate'},
      _meta: modernMeta(modernVersion),
    },
  }, {
    jsonrpc: '2.0',
    id: 'modern-xml-project',
    method: 'tools/call',
    params: {
      name: 'convert_xml_to_viewcompose',
      arguments: {
        projectRoot: xmlProjectFixtureRoot,
        layoutPath: 'app/src/main/res/layout/styled_login.xml',
        resourceRoots: ['app/src/main/res'],
        sourceRoots: ['app/src/main/java'],
        mode: 'generate',
      },
      _meta: modernMeta(modernVersion),
    },
  }, {
    jsonrpc: '2.0',
    id: 'modern-xml-v2',
    method: 'tools/call',
    params: {
      name: 'convert_xml_to_viewcompose',
      arguments: {source: xmlV2, path: 'res/layout/profile-card.xml', mode: 'generate'},
      _meta: modernMeta(modernVersion),
    },
  }]);
  modern.push(...await runMcp(mcp, [{
    jsonrpc: '2.0',
    id: 'modern-xml-layout-dependencies',
    method: 'tools/call',
    params: {
      name: 'convert_xml_to_viewcompose',
      arguments: {
        projectRoot: xmlLayoutDependencyFixtureRoot,
        layoutPath: 'app/src/main/res/layout/screen.xml',
        resourceRoots: ['app/src/main/res'],
        sourceRoots: [],
        mode: 'generate',
      },
      _meta: modernMeta(modernVersion),
    },
  }, {
    jsonrpc: '2.0',
    id: 'modern-screenshot',
    method: 'tools/call',
    params: {
      name: 'prepare_screenshot',
      arguments: screenshotRequest,
      _meta: modernMeta(modernVersion),
    },
  }, {
    jsonrpc: '2.0',
    id: 'modern-screenshot-inference',
    method: 'tools/call',
    params: {
      name: 'validate_screenshot_inference',
      arguments: {
        preprocessingRequest: inferencePreprocessingRequest,
        inferenceDeclaration: {interpretation, intent, policy, authorization},
        inferenceResult,
      },
      _meta: modernMeta(modernVersion),
    },
  }]));
  const modernList = modern.find((response) => response.id === 'modern-list');
  const modernXml = modern.find((response) => response.id === 'modern-xml');
  const modernScreenshot = modern.find((response) => response.id === 'modern-screenshot');
  const modernScreenshotInference = modern.find(
    (response) => response.id === 'modern-screenshot-inference',
  );
  modern.push(...await runMcp(mcp, [{
    jsonrpc: '2.0',
    id: 'modern-screenshot-resolution',
    method: 'tools/call',
    params: {
      name: 'resolve_screenshot_inference',
      arguments: {
        validatedInference: modernScreenshotInference?.result?.structuredContent?.data,
        resolutionRequest,
      },
      _meta: modernMeta(modernVersion),
    },
  }]));
  const modernScreenshotResolution = modern.find(
    (response) => response.id === 'modern-screenshot-resolution',
  );
  generationRequest.mode = 'generate';
  modern.push(...await runMcp(mcp, [{
    jsonrpc: '2.0',
    id: 'modern-screenshot-generation',
    method: 'tools/call',
    params: {
      name: 'generate_screenshot_viewcompose',
      arguments: {
        resolutionResult: modernScreenshotResolution?.result?.structuredContent?.data,
        generationRequest,
      },
      _meta: modernMeta(modernVersion),
    },
  }]));
  const modernScreenshotGeneration = modern.find(
    (response) => response.id === 'modern-screenshot-generation',
  );
  modern.push(...await runMcp(mcp, [{
    jsonrpc: '2.0',
    id: 'modern-screenshot-comparison',
    method: 'tools/call',
    params: {
      name: 'generate_screenshot_viewcompose',
      arguments: {
        resolutionResult: modernScreenshotResolution?.result?.structuredContent?.data,
        generationRequest: compareGenerationRequest,
        previewBindings: screenshotPreviewRequest.bindings,
      },
      _meta: modernMeta(modernVersion),
    },
  }], {env: {VIEWCOMPOSE_PROJECT_ROOT: repositoryRoot}}));
  const modernScreenshotComparison = modern.find(
    (response) => response.id === 'modern-screenshot-comparison',
  );
  modern.push(...await runMcp(mcp, [{
    jsonrpc: '2.0',
    id: 'modern-screenshot-pixel-comparison',
    method: 'tools/call',
    params: {
      name: 'generate_screenshot_viewcompose',
      arguments: {
        resolutionResult: modernScreenshotResolution?.result?.structuredContent?.data,
        generationRequest: pixelGenerationRequest,
        previewBindings: screenshotPreviewRequest.bindings,
        pixelReference: {request: pixelReferenceRequest, result: pixelReferenceResult},
      },
      _meta: modernMeta(modernVersion),
    },
  }], {env: {VIEWCOMPOSE_PROJECT_ROOT: repositoryRoot}}));
  const modernScreenshotPixelComparison = modern.find(
    (response) => response.id === 'modern-screenshot-pixel-comparison',
  );
  const modernXmlProject = modern.find((response) => response.id === 'modern-xml-project');
  const modernXmlV2 = modern.find((response) => response.id === 'modern-xml-v2');
  const modernXmlLayoutDependencies = modern.find(
    (response) => response.id === 'modern-xml-layout-dependencies',
  );
  if (
    modern.length !== 11 ||
    modernList?.result?.tools?.map((tool) => tool.name).join(',') !== contract.contents.tools.join(',') ||
    modernXml?.result?.structuredContent?.status !== 'success' ||
    modernScreenshot?.result?.structuredContent?.evidence?.outputFingerprint !==
      screenshotExpected.outputFingerprint ||
    JSON.stringify(modernScreenshot?.result?.structuredContent?.data) !==
      JSON.stringify(screenshotExpected) ||
    modernScreenshotInference?.result?.structuredContent?.evidence?.outputFingerprint !==
      'a9ebb9732105d35eab22ed56f67a6b1f02396985a5b19344b6be21b9f59e48ab' ||
    modernScreenshotResolution?.result?.structuredContent?.evidence?.outputFingerprint !==
      'acdc3a7ae1b43207ce885d4762c77630394e9734caf7305a896e6c90878274ee' ||
    modernScreenshotGeneration?.result?.structuredContent?.evidence?.outputFingerprint !==
      '5812c3ccbd0a6f30a0cc4c3ff4e71453006745d5dd76e63e153b2501131252e9' ||
    modernScreenshotComparison?.result?.structuredContent?.evidence?.outputFingerprint !==
      '779b41a96a08477bcb1f70311e8f42d8330e55a642fd91c261d211f7c31d4517' ||
    modernScreenshotPixelComparison?.result?.structuredContent?.evidence?.outputFingerprint !==
      '7504b5c23ed6e9fe142002572e08f24115e73fc311e4329057f8384f749bdd43' ||
    !modernXml?.result?.structuredContent?.data?.kotlin?.includes('fun UiTreeBuilder.LoginView(') ||
    modernXmlProject?.result?.structuredContent?.data?.projectContext?.callSites?.length !== 7 ||
    !modernXmlProject?.result?.structuredContent?.data?.kotlin
      ?.includes('fun UiTreeBuilder.StyledLoginView(') ||
    !modernXmlV2?.result?.structuredContent?.data?.kotlin
      ?.includes('fun UiTreeBuilder.ProfileCardView(') ||
    modernXmlLayoutDependencies?.result?.structuredContent?.data?.layoutDependencies?.edges?.length !== 2 ||
    !modernXmlLayoutDependencies?.result?.structuredContent?.data?.kotlin
      ?.includes('fun UiTreeBuilder.ScreenView(')
  ) {
    const responseSummary = modern.map((response) => ({
      id: response.id,
      error: response.error,
      toolCount: response.result?.tools?.length,
      status: response.result?.structuredContent?.status,
      diagnosticCodes: response.result?.structuredContent?.diagnostics?.map((diagnostic) => diagnostic.code),
      kotlinPrefix: response.result?.structuredContent?.data?.kotlin?.slice(0, 80),
    }));
    throw new Error(
      `Installed MCP server failed modern protocol ${modernVersion}: ` +
      JSON.stringify(responseSummary),
    );
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
  const [
    contract,
    knowledge,
    generatedPreviewContract,
    layoutComparisonContract,
    screenshotGeneratedPreviewContract,
    screenshotComparisonContract,
    screenshotPixelComparisonContract,
  ] = await Promise.all([
    readJson(contractPath),
    readJson(knowledgePath),
    readJson(generatedPreviewContractPath),
    readJson(layoutComparisonContractPath),
    readJson(screenshotGeneratedPreviewContractPath),
    readJson(screenshotComparisonContractPath),
    readJson(screenshotPixelComparisonContractPath),
  ]);
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
    const agent = resolve(prefix, 'bin/viewcompose-agent');
    const mcp = resolve(prefix, 'bin/viewcompose-mcp');
    await Promise.all([access(cli), access(agent), access(mcp), access(packageRoot)]);
    await verifyInstalledFiles(packageRoot, primary.manifest);
    await verifyInventory(packageRoot, contract);
    await verifyInstalledAgentClients(agent, packageRoot, temporaryRoot);
    const compileFingerprints = await verifyCliFlow(
      cli,
      knowledge,
      packageRoot,
      generatedPreviewContract,
      layoutComparisonContract,
      screenshotGeneratedPreviewContract,
      screenshotComparisonContract,
      screenshotPixelComparisonContract,
    );
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
    if (await exists(cli) || await exists(agent) || await exists(mcp) || await exists(packageRoot)) {
      throw new Error('Offline uninstallation left package or executable entries behind.');
    }

    process.stdout.write(
      `Verified ViewCompose AI distribution: 2/2 reproducible builds, ` +
      `1/1 offline install-uninstall lifecycle, 1/1 SPDX/license inventory, ` +
      `3/3 installed agent profiles with init/doctor/uninstall and 18/18 exact Skill copies, ` +
      `2/2 installed MCP protocol versions, compiled example ${compileFingerprints.sample}, ` +
      `prepared screenshot ${compileFingerprints.screenshot}, ` +
      `validated screenshot inference ${compileFingerprints.screenshotInference}, ` +
      `resolved screenshot inference ${compileFingerprints.screenshotResolution}, ` +
      `generated screenshot Kotlin ${compileFingerprints.screenshotKotlin}, ` +
      `compiled screenshot Kotlin ${compileFingerprints.screenshotGeneration}, ` +
      `rendered screenshot Preview ${compileFingerprints.screenshotPreview}, ` +
      `compared screenshot layout ${compileFingerprints.screenshotComparison}, ` +
      `compared screenshot pixels ${compileFingerprints.screenshotPixelComparison}, ` +
      `compiled XML v1 migration ${compileFingerprints.xml}, compiled XML v2 migration ` +
      `${compileFingerprints.xmlV2}, and compiled XML layout-dependency migration ` +
      `${compileFingerprints.xmlLayoutDependencies}; generated XML Preview compared as ` +
      `${compileFingerprints.xmlPreview}, and generated XML image Preview compared as ` +
      `${compileFingerprints.xmlImagePreview}.\n`,
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
