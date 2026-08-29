import {compileKotlin} from './compiler-adapter.mjs';
import {generateViewComposeKotlin} from './design-ir-to-kotlin.mjs';
import {toolResult} from './tool-core.mjs';
import {resolveXmlLayoutDependencies} from './xml-layout-dependencies.mjs';
import {resolveXmlProjectContext} from './xml-project-context.mjs';
import {convertXmlToDesignIr} from './xml-to-design-ir.mjs';

function resultData(converted, generated, projectContext, layoutDependencies) {
  const migrationReport = generated?.report && projectContext ? {
    ...generated.report,
    projectEvidence: {
      schemaVersion: projectContext.schemaVersion,
      fingerprint: projectContext.fingerprint,
      resources: projectContext.resources.length,
      styles: projectContext.styles.length,
      callSites: projectContext.callSites.length,
      layoutFiles: layoutDependencies?.coverage.layoutFiles ?? 1,
      expandedIncludes: layoutDependencies?.coverage.expandedIncludes ?? 0,
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
    layoutDependencies,
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
  resolveLayoutDependencies = resolveXmlLayoutDependencies,
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
  let layoutDependencies;
  let expandedRoot;
  let sourceToConvert = source;
  let pathToConvert = path;
  if (projectRoot !== undefined) {
    const dependencyResult = await resolveLayoutDependencies({
      projectRoot,
      layoutPath,
      resourceRoots,
      limits: {
        maxExpandedBytes: Math.min(limits?.maxSourceBytes ?? 1024 * 1024, 1024 * 1024),
      },
    });
    if (dependencyResult.status !== 'success') {
      return toolResult({
        requestId,
        tool: 'convert_xml_to_viewcompose',
        status: dependencyResult.status,
        level: 'static',
        diagnostics: dependencyResult.diagnostics,
        elapsedMs: performance.now() - started,
        truncated: dependencyResult.status === 'limited',
      });
    }
    layoutDependencies = dependencyResult.graph;
    const resolved = await resolveProjectContext({
      projectRoot,
      layoutPath,
      layoutPaths: dependencyResult.documents.map((document) => document.path),
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
    if (layoutDependencies.edges.length > 0) {
      const expandedResult = await resolveLayoutDependencies({
        projectRoot,
        layoutPath,
        resourceRoots,
        sourceOverrides: resolved.resolvedLayoutSources,
        limits: {
          maxExpandedBytes: Math.min(limits?.maxSourceBytes ?? 1024 * 1024, 1024 * 1024),
        },
      });
      if (expandedResult.status !== 'success') {
        return toolResult({
          requestId,
          tool: 'convert_xml_to_viewcompose',
          status: expandedResult.status,
          level: 'static',
          diagnostics: expandedResult.diagnostics,
          elapsedMs: performance.now() - started,
          truncated: expandedResult.status === 'limited',
        });
      }
      expandedRoot = expandedResult.expandedRoot;
    }
  }
  const converted = await convertXmlToDesignIr({
    source: sourceToConvert,
    path: pathToConvert,
    limits: {
      maxInputBytes: Math.min(limits?.maxSourceBytes ?? 262144, 262144),
    },
    expandedRoot,
  });
  if (converted.status !== 'success') {
    return toolResult({
      requestId,
      tool: 'convert_xml_to_viewcompose',
      status: converted.status,
      level: 'static',
      diagnostics: converted.diagnostics,
      data: resultData(converted, undefined, projectContext, layoutDependencies),
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
      data: resultData(converted, undefined, projectContext, layoutDependencies),
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
      data: resultData(converted, generated, projectContext, layoutDependencies),
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
      ...resultData(converted, generated, projectContext, layoutDependencies),
      compilation: compilation.data,
    },
    elapsedMs: performance.now() - started,
    cache: compilation.evidence.cache,
    compilerLane: compilation.evidence.compilerLane,
    outputFingerprint: compilation.evidence.outputFingerprint,
    truncated: compilation.truncated,
  });
}
