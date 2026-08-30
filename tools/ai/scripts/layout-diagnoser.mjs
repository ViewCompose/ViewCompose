import {basename} from 'node:path';
import {readAcceptedPreviewSnapshot, renderPreview} from './preview-adapter.mjs';
import {diagnostic, toolResult} from './tool-core.mjs';

const MAX_LAYOUT_DIAGNOSTICS = 10_000;
const MAX_RENDER_WARNINGS = 1_000;
const MAX_FINDINGS = 100;
const MAX_SOURCE_CALL_SITES = 20;
const MAX_METRICS = 50;
const MAX_TEXT = 4096;
const INTEGER_LIMIT = 100_000_000;
const kindContracts = Object.freeze({
  ZeroLayoutSize: Object.freeze({
    code: 'VC-AI-LAYOUT-ZERO-SIZE',
    message: (entry) => `${entry.className} has zero laid-out width or height at ${boundsText(entry.bounds)}.`,
    nextAction: 'Inspect parent constraints, explicit dimensions, and intrinsic content before changing the layout.',
  }),
  PartiallyClipped: Object.freeze({
    code: 'VC-AI-LAYOUT-PARTIALLY-CLIPPED',
    message: (entry) => `${entry.className} is partially clipped from ${boundsText(entry.bounds)} to ${boundsText(entry.visibleBounds)}${clipText(entry)}.`,
    nextAction: 'Inspect the recorded clipping ancestor and bounds; change constraints only when this clipping is not intentional.',
  }),
  FullyClipped: Object.freeze({
    code: 'VC-AI-LAYOUT-FULLY-CLIPPED',
    message: (entry) => `${entry.className} is fully clipped at ${boundsText(entry.bounds)}${clipText(entry)}.`,
    nextAction: 'Inspect ancestor clipping, scrolling, and placement constraints before changing the affected node.',
  }),
  TextEllipsized: Object.freeze({
    code: 'VC-AI-LAYOUT-TEXT-ELLIPSIZED',
    message: (entry) => `${entry.className} ellipsizes rendered text at ${boundsText(entry.bounds)}${metricsText(entry.metrics)}.`,
    nextAction: 'Confirm that max-lines and ellipsis are intentional; otherwise allocate space or revise the text constraint.',
  }),
  TextContentClipped: Object.freeze({
    code: 'VC-AI-LAYOUT-TEXT-CONTENT-CLIPPED',
    message: (entry) => `${entry.className} clips text content at ${boundsText(entry.bounds)}${metricsText(entry.metrics)}.`,
    nextAction: 'Increase the available content size or revise line and height constraints using the recorded metrics.',
  }),
});

const severityMap = Object.freeze({Error: 'error', Warning: 'warning', Info: 'info'});
const severityRank = Object.freeze({error: 0, warning: 1, info: 2});

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function invalidEvidence() {
  throw new Error('LAYOUT_EVIDENCE_INVALID');
}

function boundedString(value, {nullable = false, maximum = MAX_TEXT} = {}) {
  if (nullable && (value === null || value === undefined)) return undefined;
  if (typeof value !== 'string' || value.length === 0 || value.length > maximum) invalidEvidence();
  return value;
}

function boundedInteger(value, minimum = -INTEGER_LIMIT, maximum = INTEGER_LIMIT) {
  if (!Number.isInteger(value) || value < minimum || value > maximum) invalidEvidence();
  return value;
}

function normalizeBounds(value, {nullable = false} = {}) {
  if (nullable && (value === null || value === undefined)) return undefined;
  if (!isObject(value)) invalidEvidence();
  const bounds = {
    left: boundedInteger(value.left),
    top: boundedInteger(value.top),
    right: boundedInteger(value.right),
    bottom: boundedInteger(value.bottom),
  };
  if (bounds.right < bounds.left || bounds.bottom < bounds.top) invalidEvidence();
  return bounds;
}

function normalizeMetrics(value) {
  if (value === undefined) return {};
  if (!isObject(value)) invalidEvidence();
  const entries = Object.entries(value);
  if (entries.length > MAX_METRICS) invalidEvidence();
  return Object.fromEntries(entries
    .map(([name, metric]) => [boundedString(name, {maximum: 128}), boundedInteger(metric)])
    .sort(([first], [second]) => first.localeCompare(second, 'en')));
}

function normalizeSourceCallSites(value) {
  if (value === undefined) return [];
  if (!Array.isArray(value) || value.length > MAX_SOURCE_CALL_SITES) invalidEvidence();
  return value.map((entry) => {
    if (!isObject(entry)) invalidEvidence();
    return {
      className: boundedString(entry.className, {maximum: 512}),
      methodName: boundedString(entry.methodName, {maximum: 512}),
      fileName: boundedString(entry.fileName, {maximum: 512}),
      lineNumber: boundedInteger(entry.lineNumber),
    };
  });
}

function normalizeLayoutDiagnostic(value) {
  if (!isObject(value) || !Object.hasOwn(kindContracts, value.kind)) invalidEvidence();
  if (!Object.hasOwn(severityMap, value.severity)) invalidEvidence();
  if (value.clippingExpected !== undefined && typeof value.clippingExpected !== 'boolean') {
    invalidEvidence();
  }
  if (value.synthetic !== undefined && typeof value.synthetic !== 'boolean') invalidEvidence();
  return Object.fromEntries(Object.entries({
    kind: value.kind,
    severity: severityMap[value.severity],
    className: boundedString(value.className, {maximum: 512}),
    bounds: normalizeBounds(value.bounds),
    visibleBounds: normalizeBounds(value.visibleBounds, {nullable: true}),
    clippingAncestorClassName: boundedString(value.clippingAncestorClassName, {
      nullable: true,
      maximum: 512,
    }),
    clippingAncestorNodeId: boundedString(value.clippingAncestorNodeId, {
      nullable: true,
      maximum: 512,
    }),
    clippingExpected: value.clippingExpected === true,
    metrics: normalizeMetrics(value.metrics),
    nodeId: boundedString(value.nodeId, {nullable: true, maximum: 512}),
    sourceCallSites: normalizeSourceCallSites(value.sourceCallSites),
    synthetic: value.synthetic === true,
  }).filter(([, entry]) => entry !== undefined));
}

function normalizeStructure(value) {
  if (value === undefined) return {
    vnodeCount: 0,
    mountedNodeCount: 0,
    maxVNodeDepth: 0,
    maxMountedDepth: 0,
  };
  if (!isObject(value)) invalidEvidence();
  return {
    vnodeCount: boundedInteger(value.vnodeCount, 0),
    mountedNodeCount: boundedInteger(value.mountedNodeCount, 0),
    maxVNodeDepth: boundedInteger(value.maxVNodeDepth, 0),
    maxMountedDepth: boundedInteger(value.maxMountedDepth, 0),
  };
}

function boundsText(bounds) {
  if (!bounds) return '[unavailable]';
  return `[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]`;
}

function metricsText(metrics) {
  const entries = Object.entries(metrics);
  return entries.length === 0
    ? ''
    : ` (${entries.map(([name, value]) => `${name}=${value}`).join(', ')})`;
}

function clipText(entry) {
  const ancestor = entry.clippingAncestorClassName
    ? ` by ${entry.clippingAncestorClassName}`
    : '';
  return `${ancestor}${entry.clippingExpected ? ' (expected container clipping)' : ''}`;
}

function sourceFor(entry, sourcePath) {
  if (typeof sourcePath !== 'string' || sourcePath.length === 0) return undefined;
  const site = entry.sourceCallSites.find((candidate) =>
    candidate.lineNumber > 0 && candidate.fileName === basename(sourcePath));
  if (!site) return undefined;
  return {
    path: sourcePath,
    startLine: site.lineNumber,
    startColumn: 1,
  };
}

function findingForLayout(entry, sourcePath) {
  const contract = kindContracts[entry.kind];
  return Object.fromEntries(Object.entries({
    code: contract.code,
    severity: entry.severity,
    message: contract.message(entry).slice(0, MAX_TEXT),
    nextAction: contract.nextAction,
    kind: entry.kind,
    className: entry.className,
    bounds: entry.bounds,
    visibleBounds: entry.visibleBounds,
    clippingAncestorClassName: entry.clippingAncestorClassName,
    clippingAncestorNodeId: entry.clippingAncestorNodeId,
    clippingExpected: entry.clippingExpected,
    metrics: entry.metrics,
    nodeId: entry.nodeId,
    sourceCallSites: entry.sourceCallSites,
    source: sourceFor(entry, sourcePath),
    synthetic: entry.synthetic,
  }).filter(([, value]) => value !== undefined));
}

function compareFindings(first, second) {
  return severityRank[first.severity] - severityRank[second.severity] ||
    (first.bounds?.top ?? INTEGER_LIMIT) - (second.bounds?.top ?? INTEGER_LIMIT) ||
    (first.bounds?.left ?? INTEGER_LIMIT) - (second.bounds?.left ?? INTEGER_LIMIT) ||
    first.code.localeCompare(second.code, 'en') ||
    (first.className ?? '').localeCompare(second.className ?? '', 'en') ||
    (first.nodeId ?? '').localeCompare(second.nodeId ?? '', 'en');
}

/** Interprets only facts emitted by Preview protocol v1; it does not inspect pixels or source. */
export function interpretLayoutSnapshot(snapshot, {sourcePath} = {}) {
  if (!isObject(snapshot)) invalidEvidence();
  const rawDiagnostics = snapshot.layoutDiagnostics ?? [];
  const rawWarnings = snapshot.warnings ?? [];
  if (
    !Array.isArray(rawDiagnostics) ||
    rawDiagnostics.length > MAX_LAYOUT_DIAGNOSTICS ||
    !Array.isArray(rawWarnings) ||
    rawWarnings.length > MAX_RENDER_WARNINGS
  ) invalidEvidence();
  const layoutFindings = rawDiagnostics
    .map(normalizeLayoutDiagnostic)
    .map((entry) => findingForLayout(entry, sourcePath))
    .sort(compareFindings);
  const warningFindings = rawWarnings.map((warning) => ({
    code: 'VC-AI-LAYOUT-RENDER-WARNING',
    severity: 'warning',
    message: boundedString(warning).slice(0, MAX_TEXT),
    nextAction: 'Inspect the accepted Preview warning before treating the rendered layout as clean.',
    kind: 'RenderWarning',
    sourceCallSites: [],
    synthetic: false,
  }));
  const allFindings = [...layoutFindings, ...warningFindings];
  const findings = allFindings.slice(0, MAX_FINDINGS);
  const severityCounts = {error: 0, warning: 0, info: 0};
  for (const finding of allFindings) severityCounts[finding.severity] += 1;
  return {
    summary: {
      clean: allFindings.length === 0,
      actionableCount: severityCounts.error + severityCounts.warning,
      findingCount: allFindings.length,
      returnedCount: findings.length,
      severityCounts,
      layoutDiagnosticCount: layoutFindings.length,
      renderWarningCount: warningFindings.length,
    },
    structure: normalizeStructure(snapshot.structure),
    findings,
    truncated: findings.length !== allFindings.length,
  };
}

function publicDiagnostic(finding) {
  return diagnostic({
    code: finding.code,
    severity: finding.severity,
    message: finding.message,
    nextAction: finding.nextAction,
    source: finding.source,
  });
}

async function renamedRenderResult(rendered, requestId, elapsedMs) {
  const evidence = rendered?.evidence ?? {};
  return toolResult({
    requestId,
    tool: 'diagnose_layout',
    status: rendered?.status ?? 'failed',
    level: evidence.level ?? 'compiled',
    cache: evidence.cache ?? 'bypassed',
    compilerLane: evidence.compilerLane,
    renderLane: evidence.renderLane,
    outputFingerprint: evidence.outputFingerprint,
    diagnostics: Array.isArray(rendered?.diagnostics) ? rendered.diagnostics : [diagnostic({
      code: 'VC-AI-LAYOUT-RENDER-FAILED',
      severity: 'error',
      message: 'Layout diagnosis could not obtain accepted Preview evidence.',
      nextAction: 'Render the fixed Preview target successfully before diagnosing its layout.',
    })],
    elapsedMs,
    truncated: rendered?.truncated === true,
  });
}

export async function diagnoseLayout({
  targetId = 'samples.counter.CounterPreview',
  configuration,
  capabilityIds = [],
  requestId = 'diagnose-layout',
  limits,
  signal,
} = {}, {
  render = renderPreview,
  readSnapshot = readAcceptedPreviewSnapshot,
} = {}) {
  const started = performance.now();
  const rendered = await render({
    targetId,
    configuration,
    capabilityIds,
    requestId,
    limits,
    signal,
  });
  if (rendered?.status !== 'success') {
    return renamedRenderResult(rendered, requestId, performance.now() - started);
  }
  if (signal?.aborted) {
    return toolResult({
      requestId,
      tool: 'diagnose_layout',
      status: 'cancelled',
      level: 'rendered',
      cache: rendered.evidence.cache,
      compilerLane: rendered.evidence.compilerLane,
      renderLane: rendered.evidence.renderLane,
      outputFingerprint: rendered.evidence.outputFingerprint,
      diagnostics: [diagnostic({
        code: 'VC-AI-LAYOUT-CANCELLED',
        severity: 'error',
        message: 'Layout diagnosis was cancelled after rendering and before evidence interpretation.',
        nextAction: 'Retry the same fixed target when layout diagnosis is still required.',
      })],
      elapsedMs: performance.now() - started,
    });
  }

  let interpreted;
  try {
    const snapshot = await readSnapshot(rendered);
    interpreted = interpretLayoutSnapshot(snapshot, {sourcePath: rendered.data?.source?.path});
  } catch {
    return toolResult({
      requestId,
      tool: 'diagnose_layout',
      status: 'failed',
      level: 'compiled',
      cache: rendered.evidence.cache,
      compilerLane: rendered.evidence.compilerLane,
      renderLane: rendered.evidence.renderLane,
      diagnostics: [diagnostic({
        code: 'VC-AI-LAYOUT-EVIDENCE-INVALID',
        severity: 'error',
        message: 'The accepted Preview render tree changed or violated the frozen layout evidence contract.',
        nextAction: 'Remove the fixed target render cache, render again, and retry diagnosis.',
      })],
      elapsedMs: performance.now() - started,
    });
  }

  const diagnostics = interpreted.findings.map(publicDiagnostic);
  if (interpreted.summary.clean) {
    diagnostics.push(diagnostic({
      code: 'VC-AI-LAYOUT-CLEAN',
      severity: 'info',
      message: 'Preview protocol v1 reported no layout diagnostics or render warnings.',
      nextAction: 'Keep the rendered evidence fingerprint with the delivered code.',
    }));
  }
  if (interpreted.truncated) {
    diagnostics.push(diagnostic({
      code: 'VC-AI-LAYOUT-FINDINGS-TRUNCATED',
      severity: 'warning',
      message: `Returned ${interpreted.summary.returnedCount} of ${interpreted.summary.findingCount} layout findings.`,
      nextAction: 'Reduce the fixed Preview target or inspect the render tree directly before retrying.',
    }));
  }
  return toolResult({
    requestId,
    tool: 'diagnose_layout',
    status: 'success',
    level: 'rendered',
    cache: rendered.evidence.cache,
    compilerLane: rendered.evidence.compilerLane,
    renderLane: rendered.evidence.renderLane,
    outputFingerprint: rendered.evidence.outputFingerprint,
    diagnostics,
    data: {
      targetId: rendered.data.targetId,
      configuration: rendered.data.configuration,
      capabilityIds: rendered.data.capabilityIds,
      source: rendered.data.source,
      render: {
        buildFingerprint: rendered.data.buildFingerprint,
        previewId: rendered.data.previewId,
        variantId: rendered.data.variantId,
        image: rendered.data.image,
        renderTree: rendered.data.renderTree,
      },
      summary: interpreted.summary,
      structure: interpreted.structure,
      findings: interpreted.findings,
    },
    elapsedMs: performance.now() - started,
    truncated: interpreted.truncated,
  });
}
