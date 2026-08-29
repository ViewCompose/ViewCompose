#!/usr/bin/env node
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {analyzeProject} from './project-analyzer.mjs';
import {compileKotlin} from './compiler-adapter.mjs';
import {
  retrieveApiReference,
  retrieveComponentReference,
  retrieveSample,
  searchComponents,
} from './knowledge-retriever.mjs';
import {renderPreview} from './preview-adapter.mjs';
import {assertSchemaValue} from './schema-validator.mjs';
import {validateKotlin} from './static-validator.mjs';
import {
  diagnostic,
  loadKnowledgeManifest,
  toolResult,
  utf8Bytes,
} from './tool-core.mjs';

const toolEnvelopePath = fileURLToPath(
  new URL('../contracts/tool-envelope.schema.json', import.meta.url),
);
const MAX_STDIN_BYTES = 4 * 1024 * 1024;
let schemaPromise;

function loadToolEnvelopeSchema() {
  schemaPromise ??= readFile(toolEnvelopePath, 'utf8').then(JSON.parse);
  return schemaPromise;
}

async function boundaryResult(request, {
  status,
  code,
  message,
  nextAction,
  level = 'knowledge',
  truncated = false,
}) {
  return toolResult({
    requestId: request.requestId,
    tool: request.tool,
    status,
    level,
    diagnostics: [diagnostic({code, severity: 'error', message, nextAction})],
    truncated,
  });
}

export async function dispatchToolRequest(request, {
  validate = validateKotlin,
  compile = compileKotlin,
  render = renderPreview,
  analyze = analyzeProject,
  getApiReference = retrieveApiReference,
  getComponentReference = retrieveComponentReference,
  searchComponent = searchComponents,
  getSample = retrieveSample,
} = {}) {
  const schema = await loadToolEnvelopeSchema();
  assertSchemaValue(request, schema, 'AI tool request');
  const manifest = await loadKnowledgeManifest();
  if (
    request.framework.versionLane !== manifest.framework.versionLane ||
    request.framework.identity !== manifest.framework.identity
  ) {
    return boundaryResult(request, {
      status: 'unsupported',
      code: 'VC-AI-VERSION-LANE-MISMATCH',
      message: 'The request framework identity does not match the loaded immutable Knowledge Bundle.',
      nextAction: `Use ${manifest.framework.versionLane}/${manifest.framework.identity}.`,
    });
  }
  if (utf8Bytes(JSON.stringify(request.arguments)) > request.limits.maxInputBytes) {
    return boundaryResult(request, {
      status: 'limited',
      code: 'VC-AI-INPUT-LIMIT',
      message: 'Tool arguments exceed the request input-byte limit.',
      nextAction: 'Submit a smaller bounded request.',
      truncated: true,
    });
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), request.limits.timeoutMs);
  let result;
  try {
    switch (request.tool) {
      case 'get_api_reference':
        result = await getApiReference(request.arguments, {requestId: request.requestId});
        break;
      case 'get_component_reference':
        result = await getComponentReference(request.arguments, {requestId: request.requestId});
        break;
      case 'search_component':
        result = await searchComponent(request.arguments, {requestId: request.requestId});
        break;
      case 'get_sample':
        result = await getSample(request.arguments, {requestId: request.requestId});
        break;
      case 'validate_code': {
        const mode = request.arguments.mode ?? 'static';
        if (!['static', 'compile'].includes(mode)) {
          result = await boundaryResult(request, {
            status: 'invalid',
            code: 'VC-AI-VALIDATION-MODE-INVALID',
            message: 'validate_code mode must be static or compile.',
            nextAction: 'Select one accepted evidence depth explicitly.',
            level: 'static',
          });
        } else if (mode === 'compile') {
          result = await compile({
            source: request.arguments.source,
            path: request.arguments.path,
            artifactIds: request.arguments.artifactIds,
            capabilityIds: request.arguments.capabilityIds,
            requestId: request.requestId,
            limits: {
              maxSourceBytes: request.limits.maxInputBytes,
              timeoutMs: request.limits.timeoutMs,
              maxOutputBytes: request.limits.maxOutputBytes,
            },
            signal: controller.signal,
          });
        } else {
          result = await validate({
            source: request.arguments.source,
            path: request.arguments.path,
            requestId: request.requestId,
            maxInputBytes: request.limits.maxInputBytes,
          });
        }
        break;
      }
      case 'render_preview':
        result = await render({
          targetId: request.arguments.targetId,
          configuration: request.arguments.configuration,
          capabilityIds: request.arguments.capabilityIds,
          requestId: request.requestId,
          limits: {
            timeoutMs: request.limits.timeoutMs,
            maxOutputBytes: request.limits.maxOutputBytes,
          },
          signal: controller.signal,
        });
        break;
      case 'analyze_project':
        result = await analyze({
          projectRoot: request.arguments.projectRoot,
          requestedPath: request.arguments.requestedPath,
          excluded: request.arguments.excluded,
          requestId: request.requestId,
          limits: Object.fromEntries(Object.entries({
            maxFiles: request.arguments.maxFiles,
            maxBytes: request.limits.maxInputBytes,
            maxDepth: request.arguments.maxDepth,
            timeoutMs: request.limits.timeoutMs,
            maxOutputBytes: request.limits.maxOutputBytes,
          }).filter(([, value]) => value !== undefined)),
        });
        break;
      default:
        result = await boundaryResult(request, {
          status: 'unsupported',
          code: 'VC-AI-TOOL-UNSUPPORTED',
          message: `Internal CLI tool ${request.tool} is not implemented.`,
          nextAction: 'Use one tool declared by the current ViewCompose AI tool catalog.',
        });
    }
  } finally {
    clearTimeout(timeout);
  }

  let encoded = JSON.stringify(result);
  if (utf8Bytes(encoded) > request.limits.maxOutputBytes) {
    result = await boundaryResult(request, {
      status: 'limited',
      code: 'VC-AI-OUTPUT-LIMIT',
      message: 'Tool result exceeds the request output-byte limit.',
      nextAction: 'Narrow the request or select a larger bounded output limit.',
      level: result.evidence?.level ?? 'knowledge',
      truncated: true,
    });
    encoded = JSON.stringify(result);
    if (utf8Bytes(encoded) > request.limits.maxOutputBytes) {
      throw new Error('The minimum bounded tool result exceeds maxOutputBytes.');
    }
  }
  assertSchemaValue(result, schema, 'AI tool result');
  return result;
}

async function readStdin() {
  const chunks = [];
  let bytes = 0;
  for await (const chunk of process.stdin) {
    bytes += chunk.length;
    if (bytes > MAX_STDIN_BYTES) throw new Error(`stdin exceeds ${MAX_STDIN_BYTES} bytes`);
    chunks.push(chunk);
  }
  if (bytes === 0) throw new Error('stdin must contain one JSON tool request');
  return Buffer.concat(chunks).toString('utf8');
}

async function main() {
  const arguments_ = process.argv.slice(2);
  if (arguments_.some((argument) => argument !== '--pretty') || arguments_.length > 1) {
    throw new Error('Usage: npm --prefix tools/ai run tool [-- --pretty] < request.json');
  }
  const request = JSON.parse(await readStdin());
  const result = await dispatchToolRequest(request);
  process.stdout.write(`${JSON.stringify(result, null, arguments_.includes('--pretty') ? 2 : 0)}\n`);
}

const entryPath = process.argv[1] ? resolve(process.argv[1]) : '';
if (entryPath === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`ViewCompose AI internal CLI rejected the request: ${error.message}\n`);
    process.exitCode = 2;
  });
}
