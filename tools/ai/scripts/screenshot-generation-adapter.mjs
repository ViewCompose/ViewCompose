import {compileKotlin} from './compiler-adapter.mjs';
import {renderGeneratedPreview} from './generated-preview-adapter.mjs';
import {generateScreenshotKotlin} from './screenshot-design-ir-to-kotlin.mjs';
import {compareGeneratedLayout} from './layout-comparator.mjs';
import {diagnostic, toolResult} from './tool-core.mjs';

function generatedData(generated, compilation, preview, comparison) {
  return Object.fromEntries(Object.entries({
    kotlin: generated?.kotlin,
    generationReport: generated?.report,
    kotlinFingerprint: generated?.outputFingerprint,
    compilation,
    preview,
    comparison,
  }).filter(([, value]) => value !== undefined));
}

export async function generateScreenshotViewCompose(arguments_, {
  requestId = 'generate-screenshot-viewcompose',
  limits,
  signal,
  compile = compileKotlin,
  render = renderGeneratedPreview,
  compare = compareGeneratedLayout,
} = {}) {
  const started = performance.now();
  if (signal?.aborted) {
    return toolResult({
      requestId,
      tool: 'generate_screenshot_viewcompose',
      status: 'cancelled',
      level: 'static',
      diagnostics: [diagnostic({
        code: 'VC-AI-SCREENSHOT-GENERATION-CANCELLED',
        severity: 'error',
        message: 'Screenshot Kotlin generation was cancelled before source was accepted.',
        nextAction: 'Retry the same immutable resolution result and generation request.',
      })],
      elapsedMs: performance.now() - started,
    });
  }
  const generated = await generateScreenshotKotlin(arguments_);
  if (generated.status !== 'success') {
    return toolResult({
      requestId,
      tool: 'generate_screenshot_viewcompose',
      status: generated.status,
      level: 'static',
      diagnostics: generated.diagnostics,
      elapsedMs: performance.now() - started,
      truncated: generated.status === 'limited',
    });
  }
  if (arguments_.generationRequest.mode === 'generate') {
    return toolResult({
      requestId,
      tool: 'generate_screenshot_viewcompose',
      status: 'success',
      level: 'static',
      diagnostics: [],
      data: generatedData(generated),
      elapsedMs: performance.now() - started,
      outputFingerprint: generated.outputFingerprint,
    });
  }
  if (['render', 'compare'].includes(arguments_.generationRequest.mode)) {
    const preview = await render({
      generatedKotlin: generated.kotlin,
      generationReport: generated.report,
      previewBindings: arguments_.previewBindings,
      requestId,
      limits: {
        timeoutMs: limits?.timeoutMs,
        maxOutputBytes: limits?.maxOutputBytes,
      },
      signal,
    });
    if (preview.status !== 'success' || arguments_.generationRequest.mode === 'render') {
      return toolResult({
        requestId,
        tool: 'generate_screenshot_viewcompose',
        status: preview.status,
        level: preview.evidence.level,
        diagnostics: preview.diagnostics,
        data: generatedData(generated, undefined, preview.data),
        elapsedMs: performance.now() - started,
        cache: preview.evidence.cache,
        compilerLane: preview.evidence.compilerLane,
        renderLane: preview.evidence.renderLane,
        outputFingerprint: preview.evidence.outputFingerprint,
        truncated: preview.truncated,
      });
    }
    const compared = await compare({
      designIr: arguments_.resolutionResult.designIr,
      previewBindings: arguments_.previewBindings,
      preview: preview.data,
      previewEvidence: preview.evidence,
    });
    return toolResult({
      requestId,
      tool: 'generate_screenshot_viewcompose',
      status: compared.status,
      level: compared.evidenceLevel,
      diagnostics: [...preview.diagnostics, ...compared.diagnostics],
      data: generatedData(generated, undefined, preview.data, compared.comparison),
      elapsedMs: performance.now() - started,
      cache: preview.evidence.cache,
      compilerLane: preview.evidence.compilerLane,
      renderLane: preview.evidence.renderLane,
      outputFingerprint: compared.status === 'success'
        ? compared.comparison.comparisonFingerprint
        : preview.evidence.outputFingerprint,
      truncated: preview.truncated || compared.status === 'limited',
    });
  }
  const compilation = await compile({
    source: generated.kotlin,
    path: `generated/viewcompose/${generated.report.target.functionName}.kt`,
    artifactIds: generated.report.target.artifactIds,
    capabilityIds: generated.report.target.capabilityIds,
    requestId,
    limits: {
      maxSourceBytes: limits?.maxSourceBytes,
      timeoutMs: limits?.timeoutMs,
      maxOutputBytes: limits?.maxOutputBytes,
    },
    signal,
  });
  return toolResult({
    requestId,
    tool: 'generate_screenshot_viewcompose',
    status: compilation.status,
    level: compilation.evidence.level,
    diagnostics: compilation.diagnostics,
    data: generatedData(generated, compilation.data),
    elapsedMs: performance.now() - started,
    cache: compilation.evidence.cache,
    compilerLane: compilation.evidence.compilerLane,
    outputFingerprint: compilation.evidence.outputFingerprint,
    truncated: compilation.truncated,
  });
}
