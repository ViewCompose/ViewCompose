#!/usr/bin/env node
import {realpathSync} from 'node:fs';
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
import {prepareScreenshot} from './screenshot-preprocessor.mjs';
import {validateScreenshotInference} from './screenshot-inference-validator.mjs';
import {resolveScreenshotInference} from './screenshot-resolution-adapter.mjs';
import {generateScreenshotViewCompose} from './screenshot-generation-adapter.mjs';
import {diagnoseLayout} from './layout-diagnoser.mjs';
import {assertSchemaValue, validateSchemaValue} from './schema-validator.mjs';
import {validateKotlin} from './static-validator.mjs';
import {TOOL_DEFINITIONS} from './tool-catalog.mjs';
import {convertXmlToViewCompose} from './xml-migration.mjs';
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
  diagnose = diagnoseLayout,
  analyze = analyzeProject,
  getApiReference = retrieveApiReference,
  getComponentReference = retrieveComponentReference,
  searchComponent = searchComponents,
  getSample = retrieveSample,
  convertXml = convertXmlToViewCompose,
  prepare = prepareScreenshot,
  validateScreenshot = validateScreenshotInference,
  resolveScreenshot = resolveScreenshotInference,
  generateScreenshot = generateScreenshotViewCompose,
  renderGenerated,
  compareGenerated,
  signal,
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
  const definition = TOOL_DEFINITIONS[request.tool];
  if (!definition) {
    return boundaryResult(request, {
      status: 'unsupported',
      code: 'VC-AI-TOOL-UNSUPPORTED',
      message: `AI tool ${request.tool} is not implemented.`,
      nextAction: 'Use one tool declared by the current ViewCompose AI tool catalog.',
    });
  }
  const argumentViolations = validateSchemaValue(request.arguments, definition.inputSchema);
  if (argumentViolations.length > 0) {
    const screenshotPathDenied = request.tool === 'prepare_screenshot' &&
      request.arguments?.screenshot && ['path', 'url', 'uri'].some((key) =>
        Object.hasOwn(request.arguments.screenshot, key));
    const screenshotProviderTransferDenied = request.tool === 'prepare_screenshot' &&
      request.arguments?.privacy?.providerTransfer === true;
    const inferenceAuthorization = request.tool === 'validate_screenshot_inference'
      ? request.arguments?.inferenceDeclaration?.authorization
      : undefined;
    const inferenceCredentialDenied = inferenceAuthorization &&
      ['apiKey', 'token', 'credential', 'credentials'].some((key) =>
        Object.hasOwn(inferenceAuthorization, key));
    const inferenceConsentRequired = inferenceAuthorization?.mode === 'provider-adapter' &&
      (!Object.hasOwn(inferenceAuthorization, 'consentReceipt') ||
        !Object.hasOwn(inferenceAuthorization, 'consentInputFingerprint'));
    const resolutionAnswers = request.tool === 'resolve_screenshot_inference'
      ? request.arguments?.resolutionRequest?.answers ?? []
      : [];
    const resolutionExecutableDenied = resolutionAnswers.some((answer) =>
      (answer.decision?.fields ?? []).some((field) =>
        ['expression', 'resource'].includes(field.value?.kind)) ||
      Object.hasOwn(answer.decision ?? {}, 'source'));
    return boundaryResult(request, {
      status: 'invalid',
      code: screenshotPathDenied
        ? 'VC-AI-SCREENSHOT-PATH-DENIED'
        : screenshotProviderTransferDenied
          ? 'VC-AI-SCREENSHOT-PROVIDER-TRANSFER-DENIED'
          : inferenceCredentialDenied
            ? 'VC-AI-SCREENSHOT-INFERENCE-CREDENTIAL-DENIED'
            : inferenceConsentRequired
              ? 'VC-AI-SCREENSHOT-INFERENCE-CONSENT-REQUIRED'
              : resolutionExecutableDenied
                ? 'VC-AI-SCREENSHOT-RESOLUTION-EXECUTABLE-DENIED'
              : request.tool === 'validate_screenshot_inference'
                ? 'VC-AI-SCREENSHOT-INFERENCE-INPUT-INVALID'
                : request.tool === 'resolve_screenshot_inference'
                  ? 'VC-AI-SCREENSHOT-RESOLUTION-INPUT-INVALID'
                  : request.tool === 'generate_screenshot_viewcompose'
                    ? 'VC-AI-SCREENSHOT-GENERATION-INPUT-INVALID'
          : 'VC-AI-ARGUMENTS-INVALID',
      message: screenshotPathDenied
        ? 'Screenshot preprocessing accepts no path, URL, or URI input.'
        : screenshotProviderTransferDenied
          ? 'The deterministic screenshot preprocessor cannot transfer input to a provider.'
          : inferenceCredentialDenied
            ? 'Screenshot inference validation accepts no credential field.'
            : inferenceConsentRequired
              ? 'Provider-produced inference requires consent bound to the exact preprocessed input.'
              : resolutionExecutableDenied
                ? 'Screenshot resolution accepts no executable expression, callback source, or guessed resource.'
              : request.tool === 'validate_screenshot_inference'
                ? `Screenshot inference arguments violate the fixed schema: ${argumentViolations.slice(0, 3).join('; ')}`
                : request.tool === 'resolve_screenshot_inference'
                  ? `Screenshot resolution arguments violate the fixed schema: ${argumentViolations.slice(0, 3).join('; ')}`
                  : request.tool === 'generate_screenshot_viewcompose'
                    ? `Screenshot generation arguments violate the fixed schema: ${argumentViolations.slice(0, 3).join('; ')}`
          : `${request.tool} arguments violate the fixed schema: ${argumentViolations.slice(0, 3).join('; ')}`,
      nextAction: screenshotPathDenied
        ? 'Embed one integrity-declared PNG as canonical base64.'
        : screenshotProviderTransferDenied
          ? 'Set providerTransfer to false and use a separately reviewed provider adapter later.'
          : inferenceCredentialDenied
            ? 'Remove credentials; this validator performs no provider or network execution.'
            : inferenceConsentRequired
              ? 'Provide the consent receipt identity and exact approved input fingerprint.'
              : resolutionExecutableDenied
                ? 'Use only typed literal, binding, event, and accessibility decisions.'
              : request.tool === 'validate_screenshot_inference'
                ? 'Use the exact preprocessing request, inference declaration, and result contracts.'
                : request.tool === 'resolve_screenshot_inference'
                  ? 'Use the unchanged validated import and exact human-resolution request.'
                  : request.tool === 'generate_screenshot_viewcompose'
                    ? 'Use one exact resolved result and the frozen generate, compile, or render request with explicit Preview bindings.'
          : 'Use the exact arguments declared by the current tool catalog.',
      level: definition.evidenceLevel === 'knowledge' ? 'knowledge' : 'static',
    });
  }

  const controller = new AbortController();
  const cancel = () => controller.abort(signal?.reason);
  signal?.addEventListener('abort', cancel, {once: true});
  if (signal?.aborted) cancel();
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
      case 'diagnose_layout':
        result = await diagnose({
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
      case 'convert_xml_to_viewcompose':
        result = await convertXml({
          source: request.arguments.source,
          path: request.arguments.path,
          projectRoot: request.arguments.projectRoot,
          layoutPath: request.arguments.layoutPath,
          resourceRoots: request.arguments.resourceRoots,
          sourceRoots: request.arguments.sourceRoots,
          mode: request.arguments.mode,
          previewBindings: request.arguments.previewBindings,
          requestId: request.requestId,
          limits: {
            maxSourceBytes: request.limits.maxInputBytes,
            timeoutMs: request.limits.timeoutMs,
            maxOutputBytes: request.limits.maxOutputBytes,
          },
          signal: controller.signal,
          compile,
          render: renderGenerated,
          compare: compareGenerated,
        });
        break;
      case 'prepare_screenshot':
        result = await prepare(request.arguments, {
          requestId: request.requestId,
          signal: controller.signal,
        });
        break;
      case 'validate_screenshot_inference':
        result = await validateScreenshot(request.arguments, {
          requestId: request.requestId,
          signal: controller.signal,
          prepare,
        });
        break;
      case 'resolve_screenshot_inference':
        result = await resolveScreenshot(request.arguments, {
          requestId: request.requestId,
          signal: controller.signal,
        });
        break;
      case 'generate_screenshot_viewcompose':
        result = await generateScreenshot(request.arguments, {
          requestId: request.requestId,
          limits: {
            maxSourceBytes: request.limits.maxInputBytes,
            timeoutMs: request.limits.timeoutMs,
            maxOutputBytes: request.limits.maxOutputBytes,
          },
          signal: controller.signal,
          compile,
          render: renderGenerated,
          compare: compareGenerated,
        });
        break;
      default:
        throw new Error(`Tool catalog and dispatcher diverged for ${request.tool}.`);
    }
  } finally {
    clearTimeout(timeout);
    signal?.removeEventListener('abort', cancel);
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

export async function createToolRequest({tool, arguments: arguments_, requestId, limits}) {
  const manifest = await loadKnowledgeManifest();
  const definition = TOOL_DEFINITIONS[tool];
  if (!definition) throw new Error(`Unknown ViewCompose AI tool ${tool}.`);
  return {
    schemaVersion: 1,
    kind: 'request',
    requestId,
    tool,
    framework: manifest.framework,
    limits: {...definition.defaultLimits, ...limits},
    arguments: arguments_ ?? {},
  };
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

const entryPath = process.argv[1] ? realpathSync(resolve(process.argv[1])) : '';
if (entryPath === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`ViewCompose AI internal CLI rejected the request: ${error.message}\n`);
    process.exitCode = 2;
  });
}
