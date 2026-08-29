import {compileKotlin} from './compiler-adapter.mjs';
import {generateViewComposeKotlin} from './design-ir-to-kotlin.mjs';
import {toolResult} from './tool-core.mjs';
import {resolveXmlProjectContext} from './xml-project-context.mjs';
import {convertXmlToDesignIr} from './xml-to-design-ir.mjs';

function resultData(converted, generated, projectContext) {
  const migrationReport = generated?.report && projectContext ? {
    ...generated.report,
    projectEvidence: {
      schemaVersion: projectContext.schemaVersion,
      fingerprint: projectContext.fingerprint,
      resources: projectContext.resources.length,
      styles: projectContext.styles.length,
      callSites: projectContext.callSites.length,
      completeness: projectContext.coverage.completeness,
    },
    callSiteReview: {
      ...generated.report.callSiteReview,
      inventory: projectContext.callSites,
    },
  } : generated?.report;
  return Object.fromEntries(Object.entries({
    designIr: converted.ir,
    kotlin: generated?.kotlin,
    migrationReport,
    projectContext,
    unsupported: converted.unsupported,
    kotlinFingerprint: generated?.outputFingerprint,
  }).filter(([, value]) => value !== undefined));
}

export async function convertXmlToViewCompose({
  source,
  path = 'layout.xml',
  projectRoot,
  layoutPath,
  resourceRoots,
  sourceRoots,
  mode,
  requestId,
  limits,
  signal,
  compile = compileKotlin,
  resolveProjectContext = resolveXmlProjectContext,
} = {}) {
  const started = performance.now();
  if (signal?.aborted) {
    return toolResult({
      requestId,
      tool: 'convert_xml_to_viewcompose',
      status: 'cancelled',
      level: 'static',
      diagnostics: [],
      elapsedMs: performance.now() - started,
    });
  }
  let projectContext;
  let sourceToConvert = source;
  let pathToConvert = path;
  if (projectRoot !== undefined) {
    const resolved = await resolveProjectContext({
      projectRoot,
      layoutPath,
      resourceRoots,
      sourceRoots,
      limits: {
        maxBytes: limits?.maxSourceBytes,
        timeoutMs: Math.min(limits?.timeoutMs ?? 10_000, 10_000),
      },
    });
    if (resolved.status !== 'success') {
      return toolResult({
        requestId,
        tool: 'convert_xml_to_viewcompose',
        status: resolved.status,
        level: 'static',
        diagnostics: resolved.diagnostics,
        elapsedMs: performance.now() - started,
        truncated: resolved.status === 'limited',
      });
    }
    projectContext = resolved.context;
    sourceToConvert = resolved.resolvedSource;
    pathToConvert = resolved.context.layout.path;
  }
  const converted = await convertXmlToDesignIr({
    source: sourceToConvert,
    path: pathToConvert,
    limits: {
      maxInputBytes: Math.min(limits?.maxSourceBytes ?? 262144, 262144),
    },
  });
  if (converted.status !== 'success') {
    return toolResult({
      requestId,
      tool: 'convert_xml_to_viewcompose',
      status: converted.status,
      level: 'static',
      diagnostics: converted.diagnostics,
      data: resultData(converted, undefined, projectContext),
      elapsedMs: performance.now() - started,
      truncated: converted.status === 'limited',
    });
  }
  const generated = await generateViewComposeKotlin(converted.ir);
  if (generated.status !== 'success') {
    return toolResult({
      requestId,
      tool: 'convert_xml_to_viewcompose',
      status: generated.status,
      level: 'static',
      diagnostics: generated.diagnostics,
      data: resultData(converted, undefined, projectContext),
      elapsedMs: performance.now() - started,
    });
  }
  if (mode === 'generate') {
    return toolResult({
      requestId,
      tool: 'convert_xml_to_viewcompose',
      status: 'success',
      level: 'static',
      diagnostics: [],
      data: resultData(converted, generated, projectContext),
      elapsedMs: performance.now() - started,
      outputFingerprint: generated.outputFingerprint,
    });
  }

  const compilation = await compile({
    source: generated.kotlin,
    path: `generated/viewcompose/${generated.report.target.functionName}.kt`,
    artifactIds: generated.report.target.artifactIds,
    capabilityIds: ['foundation.components', 'modifier.layout'],
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
    tool: 'convert_xml_to_viewcompose',
    status: compilation.status,
    level: compilation.evidence.level,
    diagnostics: compilation.diagnostics,
    data: {
      ...resultData(converted, generated, projectContext),
      compilation: compilation.data,
    },
    elapsedMs: performance.now() - started,
    cache: compilation.evidence.cache,
    compilerLane: compilation.evidence.compilerLane,
    outputFingerprint: compilation.evidence.outputFingerprint,
    truncated: compilation.truncated,
  });
}
