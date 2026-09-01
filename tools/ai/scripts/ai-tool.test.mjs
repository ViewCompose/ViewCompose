import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import test from 'node:test';
import {dispatchToolRequest} from './ai-tool.mjs';
import {loadKnowledgeManifest, toolResult} from './tool-core.mjs';

const executable = fileURLToPath(new URL('./ai-tool.mjs', import.meta.url));
const projectContextRoot = fileURLToPath(
  new URL('../evaluation/fixtures/xml/project-context/supported/', import.meta.url),
);
const screenshotRequestPath = new URL(
  '../evaluation/fixtures/visual/screenshot/privacy-grid.request.json',
  import.meta.url,
);
const screenshotResultPath = new URL(
  '../evaluation/fixtures/visual/screenshot/privacy-grid.result.json',
  import.meta.url,
);
const screenshotPathInput = new URL(
  '../evaluation/fixtures/visual/screenshot/path-input.request.json',
  import.meta.url,
);
const screenshotProviderTransfer = new URL(
  '../evaluation/fixtures/visual/screenshot/provider-transfer.request.json',
  import.meta.url,
);
const inferencePreprocessingRequestPath = new URL(
  '../evaluation/fixtures/visual/screenshot/inference-wireframe.request.json',
  import.meta.url,
);
const inferenceRequestPath = new URL(
  '../evaluation/fixtures/visual/screenshot-inference/wireframe.request.json',
  import.meta.url,
);
const inferenceResultPath = new URL(
  '../evaluation/fixtures/visual/screenshot-inference/wireframe.result.json',
  import.meta.url,
);
const resolutionRequestPath = new URL(
  '../evaluation/fixtures/visual/screenshot-resolution/wireframe.request.json',
  import.meta.url,
);
const resolvedScreenshotPath = new URL(
  '../evaluation/fixtures/visual/screenshot-resolution/wireframe.result.json',
  import.meta.url,
);
const generationRequestPath = new URL(
  '../evaluation/fixtures/visual/screenshot-generation/wireframe.request.json',
  import.meta.url,
);
const renderGenerationRequestPath = new URL(
  '../evaluation/fixtures/visual/screenshot-render/wireframe.generation-request.json',
  import.meta.url,
);
const screenshotPreviewRequestPath = new URL(
  '../evaluation/fixtures/visual/screenshot-render/wireframe.preview-request.json',
  import.meta.url,
);
const pixelReferenceRequestPath = new URL(
  '../evaluation/fixtures/visual/screenshot-pixel/pixel-reference.request.json',
  import.meta.url,
);
const pixelReferenceResultPath = new URL(
  '../evaluation/fixtures/visual/screenshot-pixel/pixel-reference.result.json',
  import.meta.url,
);

async function request(tool, arguments_, overrides = {}) {
  const manifest = await loadKnowledgeManifest();
  return {
    schemaVersion: 1,
    kind: 'request',
    requestId: overrides.requestId ?? 'cli-test',
    tool,
    framework: overrides.framework ?? manifest.framework,
    limits: {
      timeoutMs: 10_000,
      maxInputBytes: 256 * 1024,
      maxOutputBytes: 1024 * 1024,
      ...overrides.limits,
    },
    arguments: arguments_,
  };
}

function executeCli(input, arguments_ = []) {
  return new Promise((resolvePromise) => {
    const child = spawn(process.execPath, [executable, ...arguments_], {
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    const stdout = [];
    const stderr = [];
    child.stdout.on('data', (chunk) => stdout.push(chunk));
    child.stderr.on('data', (chunk) => stderr.push(chunk));
    child.on('close', (exitCode) => resolvePromise({
      exitCode,
      stdout: Buffer.concat(stdout).toString('utf8'),
      stderr: Buffer.concat(stderr).toString('utf8'),
    }));
    child.stdin.end(input);
  });
}

test('dispatches static validation through the frozen request and result envelope', async () => {
  const result = await dispatchToolRequest(await request('validate_code', {
    mode: 'static',
    path: 'Screen.kt',
    source: `
      package example
      import com.viewcompose.ui.foundation.Text
      import com.viewcompose.ui.foundation.UiTreeBuilder
      fun UiTreeBuilder.screen() { Text("Ready") }
    `,
  }));
  assert.equal(result.status, 'success');
  assert.equal(result.tool, 'validate_code');
  assert.equal(result.evidence.level, 'static');
});

test('dispatches deterministic knowledge retrieval through the same envelope', async () => {
  const search = await dispatchToolRequest(await request('search_component', {
    versionLane: 'current-source',
    query: 'add padding and fill the available width',
    limit: 5,
  }));
  assert.equal(search.status, 'success');
  assert.equal(search.evidence.level, 'knowledge');
  assert.ok(search.data.results.some((entry) => entry.capabilityId === 'modifier.layout'));

  const sample = await dispatchToolRequest(await request('get_sample', {
    versionLane: 'current-source',
    sampleId: 'module.ui-foundation-profile-summary',
  }));
  assert.equal(sample.status, 'success');
  assert.equal(sample.data.executable, true);
});

test('dispatches standalone XML migration through the frozen tool envelope', async () => {
  const source = await readFile(
    new URL('../evaluation/fixtures/xml/login.xml', import.meta.url),
    'utf8',
  );
  const result = await dispatchToolRequest(await request('convert_xml_to_viewcompose', {
    source,
    path: 'res/layout/login.xml',
    mode: 'generate',
  }));
  assert.equal(result.status, 'success');
  assert.equal(result.tool, 'convert_xml_to_viewcompose');
  assert.equal(result.evidence.level, 'static');
  assert.ok(result.data.kotlin.includes('fun UiTreeBuilder.LoginView('));
  assert.equal(result.data.migrationReport.callSiteReview.required, true);
});

test('dispatches XML render mode with explicit generated Preview bindings', async () => {
  const source = await readFile(
    new URL('../evaluation/fixtures/xml/login.xml', import.meta.url),
    'utf8',
  );
  const previewRequest = JSON.parse(await readFile(
    new URL(
      '../evaluation/fixtures/xml/generated-preview/login.preview-request.json',
      import.meta.url,
    ),
    'utf8',
  ));
  let rendered = 0;
  const result = await dispatchToolRequest(await request('convert_xml_to_viewcompose', {
    source,
    path: 'res/layout/login.xml',
    mode: 'render',
    previewBindings: previewRequest.bindings,
  }), {
    renderGenerated: async (arguments_) => {
      rendered += 1;
      assert.equal(arguments_.generationReport.target.functionName, 'LoginView');
      assert.deepEqual(arguments_.previewBindings, previewRequest.bindings);
      return toolResult({
        requestId: arguments_.requestId,
        tool: 'render_preview',
        status: 'success',
        level: 'rendered',
        cache: 'miss',
        compilerLane: 'test-preview-compiler-lane',
        renderLane: 'test-preview-render-lane',
        outputFingerprint: 'a'.repeat(64),
        diagnostics: [],
        data: {generatedPreview: {requestFingerprint: 'b'.repeat(64)}},
      });
    },
    compareGenerated: async ({designIr, preview}) => {
      assert.equal(designIr.documentId, 'login');
      assert.equal(preview.generatedPreview.requestFingerprint, 'b'.repeat(64));
      return {
        status: 'success',
        evidenceLevel: 'compared',
        diagnostics: [],
        comparison: {comparisonFingerprint: 'c'.repeat(64)},
      };
    },
  });

  assert.equal(rendered, 1);
  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'compared');
  assert.equal(result.evidence.outputFingerprint, 'c'.repeat(64));
  assert.equal(result.data.preview.generatedPreview.requestFingerprint, 'b'.repeat(64));
});

test('dispatches explicit-root XML project migration through the same envelope', async () => {
  const result = await dispatchToolRequest(await request('convert_xml_to_viewcompose', {
    projectRoot: projectContextRoot,
    layoutPath: 'app/src/main/res/layout/styled_login.xml',
    resourceRoots: ['app/src/main/res'],
    sourceRoots: ['app/src/main/java'],
    mode: 'generate',
  }));
  assert.equal(result.status, 'success');
  assert.equal(result.data.projectContext.resources.length, 4);
  assert.equal(result.data.projectContext.styles.length, 2);
  assert.equal(result.data.projectContext.callSites.length, 7);
  assert.equal(result.data.migrationReport.callSiteReview.inventory.length, 7);
  assert.ok(result.data.kotlin.includes('fun UiTreeBuilder.StyledLoginView('));
});

test('dispatches deterministic screenshot preprocessing and preserves fail-closed diagnostics', async () => {
  const [screenshot, expected, pathInput, providerTransfer] = await Promise.all([
    readFile(screenshotRequestPath, 'utf8').then(JSON.parse),
    readFile(screenshotResultPath, 'utf8').then(JSON.parse),
    readFile(screenshotPathInput, 'utf8').then(JSON.parse),
    readFile(screenshotProviderTransfer, 'utf8').then(JSON.parse),
  ]);
  const result = await dispatchToolRequest(await request('prepare_screenshot', screenshot, {
    requestId: 'screenshot-dispatch',
    limits: {maxInputBytes: 2_000_000, maxOutputBytes: 2_000_000},
  }));
  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'static');
  assert.deepEqual(result.data, expected);

  const path = await dispatchToolRequest(await request('prepare_screenshot', pathInput));
  assert.equal(path.status, 'invalid');
  assert.equal(path.diagnostics[0].code, 'VC-AI-SCREENSHOT-PATH-DENIED');

  const provider = await dispatchToolRequest(await request(
    'prepare_screenshot',
    providerTransfer,
  ));
  assert.equal(provider.status, 'invalid');
  assert.equal(
    provider.diagnostics[0].code,
    'VC-AI-SCREENSHOT-PROVIDER-TRANSFER-DENIED',
  );
});

test('dispatches offline screenshot inference validation and denies credential-shaped input', async () => {
  const [preprocessingRequest, inferenceRequest, inferenceResult] = await Promise.all([
    readFile(inferencePreprocessingRequestPath, 'utf8').then(JSON.parse),
    readFile(inferenceRequestPath, 'utf8').then(JSON.parse),
    readFile(inferenceResultPath, 'utf8').then(JSON.parse),
  ]);
  const {interpretation, intent, policy, authorization} = inferenceRequest;
  const arguments_ = {
    preprocessingRequest,
    inferenceDeclaration: {interpretation, intent, policy, authorization},
    inferenceResult,
  };
  const result = await dispatchToolRequest(await request(
    'validate_screenshot_inference',
    arguments_,
    {requestId: 'inference-dispatch', limits: {maxInputBytes: 4_000_000, maxOutputBytes: 2_000_000}},
  ));
  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'static');
  assert.equal(
    result.data.validationFingerprint,
    'a9ebb9732105d35eab22ed56f67a6b1f02396985a5b19344b6be21b9f59e48ab',
  );
  assert.equal(result.data.summary.codeGenerationAllowed, false);

  const credential = structuredClone(arguments_);
  credential.inferenceDeclaration.authorization.apiKey = 'forbidden-not-a-real-secret';
  const denied = await dispatchToolRequest(await request(
    'validate_screenshot_inference',
    credential,
  ));
  assert.equal(denied.status, 'invalid');
  assert.equal(
    denied.diagnostics[0].code,
    'VC-AI-SCREENSHOT-INFERENCE-CREDENTIAL-DENIED',
  );
});

test('dispatches exact typed screenshot resolution and denies executable content', async () => {
  const [preprocessingRequest, inferenceRequest, inferenceResult, resolutionRequest] =
    await Promise.all([
      readFile(inferencePreprocessingRequestPath, 'utf8').then(JSON.parse),
      readFile(inferenceRequestPath, 'utf8').then(JSON.parse),
      readFile(inferenceResultPath, 'utf8').then(JSON.parse),
      readFile(resolutionRequestPath, 'utf8').then(JSON.parse),
    ]);
  const {interpretation, intent, policy, authorization} = inferenceRequest;
  const imported = await dispatchToolRequest(await request('validate_screenshot_inference', {
    preprocessingRequest,
    inferenceDeclaration: {interpretation, intent, policy, authorization},
    inferenceResult,
  }, {
    limits: {maxInputBytes: 4_000_000, maxOutputBytes: 2_000_000},
  }));
  const arguments_ = {validatedInference: imported.data, resolutionRequest};
  const result = await dispatchToolRequest(await request(
    'resolve_screenshot_inference',
    arguments_,
    {requestId: 'resolution-dispatch', limits: {maxInputBytes: 2_000_000, maxOutputBytes: 2_000_000}},
  ));
  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'static');
  assert.equal(
    result.data.resultFingerprint,
    'acdc3a7ae1b43207ce885d4762c77630394e9734caf7305a896e6c90878274ee',
  );
  assert.equal(result.data.summary.codeGenerationAllowed, true);

  const executable = structuredClone(arguments_);
  executable.resolutionRequest.answers[0].decision.fields[0].value = {
    kind: 'expression',
    language: 'kotlin',
    source: 'loadTitle()',
  };
  const denied = await dispatchToolRequest(await request(
    'resolve_screenshot_inference',
    executable,
  ));
  assert.equal(denied.status, 'invalid');
  assert.equal(
    denied.diagnostics[0].code,
    'VC-AI-SCREENSHOT-RESOLUTION-EXECUTABLE-DENIED',
  );
});

test('dispatches deterministic screenshot Kotlin generation through the shared envelope', async () => {
  const [resolutionResult, generationRequest] = await Promise.all([
    readFile(resolvedScreenshotPath, 'utf8').then(JSON.parse),
    readFile(generationRequestPath, 'utf8').then(JSON.parse),
  ]);
  generationRequest.mode = 'generate';
  const result = await dispatchToolRequest(await request(
    'generate_screenshot_viewcompose',
    {resolutionResult, generationRequest},
    {requestId: 'screenshot-generation-dispatch', limits: {
      maxInputBytes: 2_000_000,
      maxOutputBytes: 2_000_000,
    }},
  ));
  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'static');
  assert.equal(
    result.data.kotlinFingerprint,
    '5812c3ccbd0a6f30a0cc4c3ff4e71453006745d5dd76e63e153b2501131252e9',
  );
  assert.equal(result.data.generationReport.bindings.states.length, 1);
  assert.equal(result.data.generationReport.bindings.events.length, 2);
});

test('dispatches screenshot render mode only with explicit source-free Preview bindings', async () => {
  const [resolutionResult, generationRequest, previewRequest] = await Promise.all([
    readFile(resolvedScreenshotPath, 'utf8').then(JSON.parse),
    readFile(renderGenerationRequestPath, 'utf8').then(JSON.parse),
    readFile(screenshotPreviewRequestPath, 'utf8').then(JSON.parse),
  ]);
  let invocation;
  const result = await dispatchToolRequest(await request(
    'generate_screenshot_viewcompose',
    {resolutionResult, generationRequest, previewBindings: previewRequest.bindings},
    {requestId: 'screenshot-render-dispatch', limits: {
      maxInputBytes: 2_000_000,
      maxOutputBytes: 2_000_000,
    }},
  ), {
    renderGenerated: async (value) => {
      invocation = value;
      return toolResult({
        requestId: value.requestId,
        tool: 'render_preview',
        status: 'success',
        level: 'rendered',
        diagnostics: [],
        data: {targetId: 'tools.ai.GeneratedScreenshotPreview'},
        compilerLane: 'preview-compiler',
        renderLane: 'preview-renderer',
        outputFingerprint: 'a'.repeat(64),
      });
    },
  });
  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'rendered');
  assert.equal(result.data.preview.targetId, 'tools.ai.GeneratedScreenshotPreview');
  assert.equal(invocation.generationReport.target.functionName, 'ScreenshotWireframeView');
  assert.deepEqual(invocation.previewBindings, previewRequest.bindings);

  const missing = await dispatchToolRequest(await request(
    'generate_screenshot_viewcompose',
    {resolutionResult, generationRequest},
  ));
  assert.equal(missing.status, 'invalid');
  assert.equal(missing.diagnostics[0].code, 'VC-AI-SCREENSHOT-GENERATION-INPUT-INVALID');
});

test('dispatches screenshot comparison with exact resolved and rendered lineage', async () => {
  const [resolutionResult, generationRequest, previewRequest] = await Promise.all([
    readFile(resolvedScreenshotPath, 'utf8').then(JSON.parse),
    readFile(renderGenerationRequestPath, 'utf8').then(JSON.parse),
    readFile(screenshotPreviewRequestPath, 'utf8').then(JSON.parse),
  ]);
  generationRequest.mode = 'compare';
  let compared;
  const result = await dispatchToolRequest(await request(
    'generate_screenshot_viewcompose',
    {resolutionResult, generationRequest, previewBindings: previewRequest.bindings},
  ), {
    renderGenerated: async (value) => toolResult({
      requestId: value.requestId,
      tool: 'render_preview',
      status: 'success',
      level: 'rendered',
      diagnostics: [],
      data: {generatedPreview: {requestFingerprint: 'a'.repeat(64)}},
      compilerLane: 'preview-compiler',
      renderLane: 'preview-renderer',
      outputFingerprint: 'b'.repeat(64),
    }),
    compareGenerated: async (value) => {
      compared = value;
      return {
        status: 'success',
        evidenceLevel: 'compared',
        diagnostics: [],
        comparison: {comparisonFingerprint: 'c'.repeat(64)},
      };
    },
  });

  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'compared');
  assert.equal(result.evidence.outputFingerprint, 'c'.repeat(64));
  assert.equal(compared.designIr.documentId, 'screenshot-wireframe');
  assert.deepEqual(compared.previewBindings, previewRequest.bindings);
  assert.equal(compared.previewEvidence.outputFingerprint, 'b'.repeat(64));
});

test('dispatches eligible pixel comparison only after semantic comparison', async () => {
  const [
    resolutionResult,
    generationRequest,
    previewRequest,
    referenceRequest,
    referenceResult,
  ] = await Promise.all([
    readFile(resolvedScreenshotPath, 'utf8').then(JSON.parse),
    readFile(renderGenerationRequestPath, 'utf8').then(JSON.parse),
    readFile(screenshotPreviewRequestPath, 'utf8').then(JSON.parse),
    readFile(pixelReferenceRequestPath, 'utf8').then(JSON.parse),
    readFile(pixelReferenceResultPath, 'utf8').then(JSON.parse),
  ]);
  generationRequest.mode = 'compare-pixels';
  let pixelInvocation;
  const result = await dispatchToolRequest(await request(
    'generate_screenshot_viewcompose',
    {
      resolutionResult,
      generationRequest,
      previewBindings: previewRequest.bindings,
      pixelReference: {request: referenceRequest, result: referenceResult},
    },
    {limits: {maxInputBytes: 2_000_000, maxOutputBytes: 2_000_000}},
  ), {
    renderGenerated: async (value) => toolResult({
      requestId: value.requestId,
      tool: 'render_preview',
      status: 'success',
      level: 'rendered',
      diagnostics: [],
      data: {generatedPreview: {requestFingerprint: 'a'.repeat(64)}},
      compilerLane: 'preview-compiler',
      renderLane: 'preview-renderer',
      outputFingerprint: 'b'.repeat(64),
    }),
    compareGenerated: async () => ({
      status: 'success',
      evidenceLevel: 'compared',
      diagnostics: [],
      comparison: {comparisonFingerprint: 'c'.repeat(64)},
    }),
    comparePixelsGenerated: async (value) => {
      pixelInvocation = value;
      return {
        status: 'success',
        evidenceLevel: 'compared',
        diagnostics: [],
        comparison: {comparisonFingerprint: 'd'.repeat(64)},
      };
    },
  });

  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'compared');
  assert.equal(result.evidence.outputFingerprint, 'd'.repeat(64));
  assert.equal(pixelInvocation.semanticComparison.comparisonFingerprint, 'c'.repeat(64));
  assert.equal(pixelInvocation.referenceResult.outputFingerprint, referenceResult.outputFingerprint);
});

test('rejects framework drift and unsupported tools without invoking adapters', async () => {
  let invocations = 0;
  const drift = await dispatchToolRequest(await request('validate_code', {
    mode: 'static',
    source: 'fun example() = Unit',
  }, {
    framework: {versionLane: 'current-source', identity: '0'.repeat(40)},
  }), {
    validate: async () => {
      invocations += 1;
    },
  });
  assert.equal(drift.status, 'unsupported');
  assert.equal(drift.diagnostics[0].code, 'VC-AI-VERSION-LANE-MISMATCH');

  const unsupported = await dispatchToolRequest(await request('generate_ui', {}));
  assert.equal(unsupported.status, 'unsupported');
  assert.equal(unsupported.diagnostics[0].code, 'VC-AI-TOOL-UNSUPPORTED');
  assert.equal(invocations, 0);
});

test('maps compile, render, layout diagnosis, project, and XML limits into provider-neutral adapters', async () => {
  const captured = [];
  const handler = (tool, level) => async (arguments_) => {
    captured.push({tool, arguments_});
    return toolResult({
      requestId: arguments_.requestId,
      tool,
      status: 'success',
      level,
      diagnostics: [],
      data: {},
    });
  };
  await dispatchToolRequest(await request('validate_code', {
    mode: 'compile',
    source: 'fun example() = Unit',
    artifactIds: ['viewcompose-ui-foundation'],
  }), {compile: handler('validate_code', 'compiled')});
  await dispatchToolRequest(await request('render_preview', {
    targetId: 'samples.counter.CounterPreview',
  }), {render: handler('render_preview', 'rendered')});
  await dispatchToolRequest(await request('diagnose_layout', {
    targetId: 'samples.counter.CounterPreview',
  }), {diagnose: handler('diagnose_layout', 'rendered')});
  await dispatchToolRequest(await request('analyze_project', {
    projectRoot: '/workspace/sample',
    maxFiles: 25,
    maxDepth: 5,
  }), {analyze: handler('analyze_project', 'static')});
  await dispatchToolRequest(await request('convert_xml_to_viewcompose', {
    projectRoot: '/workspace/sample',
    layoutPath: 'app/src/main/res/layout/screen.xml',
    resourceRoots: ['app/src/main/res'],
    sourceRoots: ['app/src/main/java'],
    mode: 'generate',
  }), {convertXml: handler('convert_xml_to_viewcompose', 'static')});
  await dispatchToolRequest(await request('convert_figma_to_viewcompose', {
    schemaVersion: 1,
    kind: 'figma-import-request',
    mode: 'inspect',
    exportJson: '{}',
  }), {
    convertFigma: async (arguments_, options) => {
      captured.push({tool: 'convert_figma_to_viewcompose', arguments_: {...arguments_, ...options}});
      return toolResult({
        requestId: options.requestId,
        tool: 'convert_figma_to_viewcompose',
        status: 'success',
        level: 'static',
        diagnostics: [],
        data: {},
      });
    },
  });

  assert.equal(captured[0].arguments_.limits.maxSourceBytes, 256 * 1024);
  assert.equal(captured[0].arguments_.signal instanceof AbortSignal, true);
  assert.equal(captured[1].arguments_.limits.timeoutMs, 10_000);
  assert.equal(captured[1].arguments_.signal instanceof AbortSignal, true);
  assert.equal(captured[2].arguments_.limits.timeoutMs, 10_000);
  assert.equal(captured[2].arguments_.signal instanceof AbortSignal, true);
  assert.equal(captured[3].arguments_.limits.maxFiles, 25);
  assert.equal(captured[3].arguments_.limits.maxDepth, 5);
  assert.equal(captured[4].arguments_.limits.maxSourceBytes, 256 * 1024);
  assert.equal(captured[4].arguments_.signal instanceof AbortSignal, true);
  assert.equal(captured[4].arguments_.mode, 'generate');
  assert.equal(captured[4].arguments_.projectRoot, '/workspace/sample');
  assert.deepEqual(captured[4].arguments_.resourceRoots, ['app/src/main/res']);
  assert.deepEqual(captured[4].arguments_.sourceRoots, ['app/src/main/java']);
  assert.equal(captured[5].arguments_.mode, 'inspect');
  assert.equal(captured[5].arguments_.signal instanceof AbortSignal, true);
});

test('propagates transport cancellation into the bounded execution signal', async () => {
  const controller = new AbortController();
  let adapterStarted;
  const started = new Promise((resolvePromise) => { adapterStarted = resolvePromise; });
  const pending = dispatchToolRequest(await request('validate_code', {
    mode: 'compile',
    source: 'fun example() = Unit',
    artifactIds: ['viewcompose-ui-foundation'],
  }), {
    signal: controller.signal,
    compile: async (arguments_) => {
      adapterStarted();
      await new Promise((resolvePromise) =>
        arguments_.signal.addEventListener('abort', resolvePromise, {once: true}));
      return toolResult({
        requestId: arguments_.requestId,
        tool: 'validate_code',
        status: 'cancelled',
        level: 'static',
        diagnostics: [],
      });
    },
  });
  await started;
  controller.abort('test transport cancellation');
  assert.equal((await pending).status, 'cancelled');
});

test('dispatches screenshot repair preparation without granting MCP source writes', async () => {
  const repairEvidence = {
    schemaVersion: 1,
    status: 'complete',
    lineage: {candidateDesignIrFingerprint: 'a'.repeat(64)},
    candidateEvaluation: {},
    designIr: {},
    evidenceFingerprint: 'b'.repeat(64),
  };
  let invoked = 0;
  const result = await dispatchToolRequest(await request('prepare_screenshot_repair', {
    operation: 'propose',
    baselineEvidence: repairEvidence,
    candidateEvidence: repairEvidence,
  }), {
    prepareRepair: async (arguments_, options) => {
      invoked += 1;
      assert.equal(arguments_.operation, 'propose');
      assert.equal(options.signal instanceof AbortSignal, true);
      return toolResult({
        requestId: options.requestId,
        tool: 'prepare_screenshot_repair',
        status: 'success',
        level: 'compared',
        diagnostics: [],
        data: {sourceWritePerformed: false},
      });
    },
  });
  assert.equal(invoked, 1);
  assert.equal(result.status, 'success');
  assert.equal(result.data.sourceWritePerformed, false);
});

test('replaces oversized adapter data with one bounded stable result', async () => {
  const result = await dispatchToolRequest(await request('analyze_project', {
    projectRoot: '/workspace/sample',
  }, {
    limits: {maxOutputBytes: 1024},
  }), {
    analyze: async (arguments_) => toolResult({
      requestId: arguments_.requestId,
      tool: 'analyze_project',
      status: 'success',
      level: 'static',
      diagnostics: [],
      data: {content: 'x'.repeat(5000)},
    }),
  });
  assert.equal(result.status, 'limited');
  assert.equal(result.diagnostics[0].code, 'VC-AI-OUTPUT-LIMIT');
  assert.equal(result.data, undefined);
  assert.equal(result.truncated, true);
});

test('the executable CLI reads one stdin request and writes only the JSON result to stdout', async () => {
  const toolRequest = await request('validate_code', {
    mode: 'static',
    path: 'Screen.kt',
    source: 'package example\nfun screen() = Unit\n',
  }, {requestId: 'cli-process'});
  const execution = await executeCli(JSON.stringify(toolRequest), ['--pretty']);
  assert.equal(execution.exitCode, 0);
  assert.equal(execution.stderr, '');
  const result = JSON.parse(execution.stdout);
  assert.equal(result.requestId, 'cli-process');
  assert.equal(result.status, 'success');
});

test('the executable CLI rejects malformed envelopes without emitting partial JSON', async () => {
  const execution = await executeCli('{"kind":"request"}');
  assert.equal(execution.exitCode, 2);
  assert.equal(execution.stdout, '');
  assert.match(execution.stderr, /rejected the request/u);
});
