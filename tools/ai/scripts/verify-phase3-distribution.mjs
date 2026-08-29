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
      timeoutMs: tool === 'validate_code' || ['compile', 'render'].includes(arguments_.mode)
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

function runMcp(executable, messages, {timeoutMs = 180_000} = {}) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(executable, [], {
      cwd: repositoryRoot,
      env: process.env,
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

async function verifyCliFlow(
  cli,
  knowledge,
  installedPackageRoot,
  generatedPreviewContract,
  layoutComparisonContract,
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
      '556c13d133d63e34fa81d1c04df3bee938509c5ced1d244ccf2366d48cb6e845' ||
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
      '61426e6904d9ffbdf1b29ec77fd8e6e0ee345494a0aad3b18028781f20ef981a' ||
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
    {VIEWCOMPOSE_SOURCE_ROOT: repositoryRoot},
  );
  if (
    compiledScreenshot.status !== 'success' ||
    compiledScreenshot.evidence.level !== 'compiled' ||
    compiledScreenshot.data?.kotlinFingerprint !==
      '5812c3ccbd0a6f30a0cc4c3ff4e71453006745d5dd76e63e153b2501131252e9' ||
    compiledScreenshot.data?.generationReport?.reportFingerprint !==
      '51c09b75e1a8bec953191e50388795c61fff6c45841de1f7832e050d2824752d'
  ) {
    throw new Error('Installed CLI did not compile the frozen screenshot Kotlin golden.');
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
  }, 'distribution-xml-mismatched-source', {VIEWCOMPOSE_SOURCE_ROOT: installedPackageRoot});
  if (
    rejectedXmlCompile.status !== 'unsupported' ||
    rejectedXmlCompile.diagnostics?.[0]?.code !== 'VC-AI-SOURCE-ROOT-MISMATCH'
  ) {
    throw new Error('Installed XML compile did not reject a mismatched source checkout.');
  }
  const compiledXml = await runCli(cli, knowledge, 'convert_xml_to_viewcompose', {
    source: xml,
    path: 'res/layout/login.xml',
    mode: 'compile',
  }, 'distribution-xml-compile', {VIEWCOMPOSE_SOURCE_ROOT: repositoryRoot});
  if (compiledXml.status !== 'success' || compiledXml.evidence.level !== 'compiled') {
    throw new Error('Installed CLI did not compile the frozen XML migration.');
  }
  const previewRequest = await readJson(generatedPreviewRequestPath);
  const renderedXml = await runCli(cli, knowledge, 'convert_xml_to_viewcompose', {
    source: xml,
    path: 'res/layout/login.xml',
    mode: 'render',
    previewBindings: previewRequest.bindings,
  }, 'distribution-xml-render', {VIEWCOMPOSE_SOURCE_ROOT: repositoryRoot});
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
    throw new Error('Installed CLI did not render the frozen generated XML Preview.');
  }
  const imagePreviewRequest = await readJson(generatedImagePreviewRequestPath);
  const renderedImageXml = await runCli(cli, knowledge, 'convert_xml_to_viewcompose', {
    source: xmlV2,
    path: 'res/layout/profile-card.xml',
    mode: 'render',
    previewBindings: imagePreviewRequest.bindings,
  }, 'distribution-xml-image-render', {VIEWCOMPOSE_SOURCE_ROOT: repositoryRoot});
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
    throw new Error('Installed CLI did not render the frozen generated XML image Preview.');
  }
  const compiledXmlV2 = await runCli(cli, knowledge, 'convert_xml_to_viewcompose', {
    source: xmlV2,
    path: 'res/layout/profile-card.xml',
    mode: 'compile',
  }, 'distribution-xml-v2-compile', {VIEWCOMPOSE_SOURCE_ROOT: repositoryRoot});
  if (compiledXmlV2.status !== 'success' || compiledXmlV2.evidence.level !== 'compiled') {
    throw new Error('Installed CLI did not compile the frozen XML v2 migration.');
  }
  const compiledXmlLayoutDependencies = await runCli(cli, knowledge, 'convert_xml_to_viewcompose', {
    projectRoot: xmlLayoutDependencyFixtureRoot,
    layoutPath: 'app/src/main/res/layout/screen.xml',
    resourceRoots: ['app/src/main/res'],
    sourceRoots: [],
    mode: 'compile',
  }, 'distribution-xml-layout-dependencies-compile', {VIEWCOMPOSE_SOURCE_ROOT: repositoryRoot});
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
  return {
    screenshot: screenshot.evidence.outputFingerprint,
    screenshotInference: validatedInference.evidence.outputFingerprint,
    screenshotResolution: resolvedInference.evidence.outputFingerprint,
    screenshotGeneration: compiledScreenshot.evidence.outputFingerprint,
    screenshotKotlin: generatedScreenshot.evidence.outputFingerprint,
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
  const modernXmlProject = modern.find((response) => response.id === 'modern-xml-project');
  const modernXmlV2 = modern.find((response) => response.id === 'modern-xml-v2');
  const modernXmlLayoutDependencies = modern.find(
    (response) => response.id === 'modern-xml-layout-dependencies',
  );
  if (
    modern.length !== 9 ||
    modernList?.result?.tools?.map((tool) => tool.name).join(',') !== contract.contents.tools.join(',') ||
    modernXml?.result?.structuredContent?.status !== 'success' ||
    modernScreenshot?.result?.structuredContent?.evidence?.outputFingerprint !==
      screenshotExpected.outputFingerprint ||
    JSON.stringify(modernScreenshot?.result?.structuredContent?.data) !==
      JSON.stringify(screenshotExpected) ||
    modernScreenshotInference?.result?.structuredContent?.evidence?.outputFingerprint !==
      '556c13d133d63e34fa81d1c04df3bee938509c5ced1d244ccf2366d48cb6e845' ||
    modernScreenshotResolution?.result?.structuredContent?.evidence?.outputFingerprint !==
      '61426e6904d9ffbdf1b29ec77fd8e6e0ee345494a0aad3b18028781f20ef981a' ||
    modernScreenshotGeneration?.result?.structuredContent?.evidence?.outputFingerprint !==
      '5812c3ccbd0a6f30a0cc4c3ff4e71453006745d5dd76e63e153b2501131252e9' ||
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
  const [contract, knowledge, generatedPreviewContract, layoutComparisonContract] = await Promise.all([
    readJson(contractPath),
    readJson(knowledgePath),
    readJson(generatedPreviewContractPath),
    readJson(layoutComparisonContractPath),
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
    const mcp = resolve(prefix, 'bin/viewcompose-mcp');
    await Promise.all([access(cli), access(mcp), access(packageRoot)]);
    await verifyInstalledFiles(packageRoot, primary.manifest);
    await verifyInventory(packageRoot, contract);
    const compileFingerprints = await verifyCliFlow(
      cli,
      knowledge,
      packageRoot,
      generatedPreviewContract,
      layoutComparisonContract,
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
    if (await exists(cli) || await exists(mcp) || await exists(packageRoot)) {
      throw new Error('Offline uninstallation left package or executable entries behind.');
    }

    process.stdout.write(
      `Verified ViewCompose AI distribution: 2/2 reproducible builds, ` +
      `1/1 offline install-uninstall lifecycle, 1/1 SPDX/license inventory, ` +
      `2/2 installed MCP protocol versions, compiled example ${compileFingerprints.sample}, ` +
      `prepared screenshot ${compileFingerprints.screenshot}, ` +
      `validated screenshot inference ${compileFingerprints.screenshotInference}, ` +
      `resolved screenshot inference ${compileFingerprints.screenshotResolution}, ` +
      `generated screenshot Kotlin ${compileFingerprints.screenshotKotlin}, ` +
      `compiled screenshot Kotlin ${compileFingerprints.screenshotGeneration}, ` +
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
