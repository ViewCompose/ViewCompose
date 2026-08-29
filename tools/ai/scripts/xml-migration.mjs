import {compileKotlin} from './compiler-adapter.mjs';
import {generateViewComposeKotlin} from './design-ir-to-kotlin.mjs';
import {toolResult} from './tool-core.mjs';
import {convertXmlToDesignIr} from './xml-to-design-ir.mjs';

function resultData(converted, generated) {
  return Object.fromEntries(Object.entries({
    designIr: converted.ir,
    kotlin: generated?.kotlin,
    migrationReport: generated?.report,
    unsupported: converted.unsupported,
    kotlinFingerprint: generated?.outputFingerprint,
  }).filter(([, value]) => value !== undefined));
}

export async function convertXmlToViewCompose({
  source,
  path = 'layout.xml',
  mode,
  requestId,
  limits,
  signal,
  compile = compileKotlin,
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
  const converted = await convertXmlToDesignIr({
    source,
    path,
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
      data: resultData(converted),
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
      data: resultData(converted),
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
      data: resultData(converted, generated),
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
      ...resultData(converted, generated),
      compilation: compilation.data,
    },
    elapsedMs: performance.now() - started,
    cache: compilation.evidence.cache,
    compilerLane: compilation.evidence.compilerLane,
    outputFingerprint: compilation.evidence.outputFingerprint,
    truncated: compilation.truncated,
  });
}
