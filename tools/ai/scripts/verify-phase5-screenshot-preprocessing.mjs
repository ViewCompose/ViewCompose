#!/usr/bin/env node
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {prepareScreenshot} from './screenshot-preprocessor.mjs';
import {TOOL_DEFINITIONS, TOOL_NAMES} from './tool-catalog.mjs';

const requestPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot/privacy-grid.request.json', import.meta.url),
);
const resultPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot/privacy-grid.result.json', import.meta.url),
);
const pathInputPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot/path-input.request.json', import.meta.url),
);
const providerTransferPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot/provider-transfer.request.json', import.meta.url),
);

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function requireDiagnostic(result, status, code, label) {
  if (result.status !== status || result.diagnostics?.[0]?.code !== code) {
    throw new Error(`${label}: expected ${status}/${code}`);
  }
}

export async function verifyPhase5ScreenshotPreprocessing() {
  const [request, expected, pathInput, providerTransfer] = await Promise.all([
    readJson(requestPath),
    readJson(resultPath),
    readJson(pathInputPath),
    readJson(providerTransferPath),
  ]);
  const [first, second] = await Promise.all([
    prepareScreenshot(request, {requestId: 'phase5-screenshot-first'}),
    prepareScreenshot(request, {requestId: 'phase5-screenshot-second'}),
  ]);
  if (
    first.status !== 'success' ||
    second.status !== 'success' ||
    JSON.stringify(first.data) !== JSON.stringify(expected) ||
    JSON.stringify(second.data) !== JSON.stringify(expected) ||
    first.evidence.outputFingerprint !== expected.outputFingerprint ||
    second.evidence.outputFingerprint !== expected.outputFingerprint
  ) {
    throw new Error('Screenshot preprocessing did not reproduce the exact golden twice');
  }

  const reordered = {
    output: request.output,
    privacy: request.privacy,
    interpretation: request.interpretation,
    screenshot: request.screenshot,
    source: request.source,
    kind: request.kind,
    schemaVersion: request.schemaVersion,
  };
  const reorderedResult = await prepareScreenshot(reordered, {
    requestId: 'phase5-screenshot-reordered',
  });
  if (
    reorderedResult.data?.requestFingerprint !== expected.requestFingerprint ||
    reorderedResult.data?.outputFingerprint !== expected.outputFingerprint
  ) {
    throw new Error('Screenshot canonical fingerprints still depend on JSON key order');
  }

  const [pathDenied, providerDenied] = await Promise.all([
    prepareScreenshot(pathInput, {requestId: 'phase5-screenshot-path'}),
    prepareScreenshot(providerTransfer, {requestId: 'phase5-screenshot-provider'}),
  ]);
  requireDiagnostic(
    pathDenied,
    'invalid',
    'VC-AI-SCREENSHOT-PATH-DENIED',
    'path input',
  );
  requireDiagnostic(
    providerDenied,
    'invalid',
    'VC-AI-SCREENSHOT-PROVIDER-TRANSFER-DENIED',
    'provider transfer',
  );

  const changedIdentity = structuredClone(request);
  changedIdentity.screenshot.sha256 = '0'.repeat(64);
  const integrityDenied = await prepareScreenshot(changedIdentity, {
    requestId: 'phase5-screenshot-integrity',
  });
  requireDiagnostic(
    integrityDenied,
    'invalid',
    'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID',
    'changed PNG identity',
  );

  const controller = new AbortController();
  controller.abort('phase5 acceptance cancellation');
  const cancelled = await prepareScreenshot(request, {
    requestId: 'phase5-screenshot-cancelled',
    signal: controller.signal,
  });
  requireDiagnostic(
    cancelled,
    'cancelled',
    'VC-AI-SCREENSHOT-CANCELLED',
    'cancellation',
  );

  const definition = TOOL_DEFINITIONS.prepare_screenshot;
  if (
    TOOL_NAMES.at(-1) !== 'prepare_screenshot' ||
    definition.defaultLimits.maxInputBytes !== 2_000_000 ||
    definition.defaultLimits.maxOutputBytes !== 2_000_000 ||
    Buffer.byteLength(JSON.stringify(first)) > definition.defaultLimits.maxOutputBytes ||
    first.data.privacy.providerTransfer !== false ||
    first.data.privacy.inputPersisted !== false ||
    first.data.privacy.logs !== 'metadata-only'
  ) {
    throw new Error('Screenshot public-tool transport or privacy boundary changed');
  }

  return {
    supportedGoldens: 1,
    deterministicRuns: 3,
    privacyDenials: 2,
    integrityDenials: 1,
    cancellations: 1,
    inputSha256: first.data.input.sha256,
    outputSha256: first.data.output.sha256,
    outputFingerprint: first.data.outputFingerprint,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase5ScreenshotPreprocessing()
    .then((summary) => {
      process.stdout.write(
        `Verified Phase 5 screenshot preprocessing: ${summary.supportedGoldens}/1 golden, ` +
        `${summary.deterministicRuns}/3 deterministic/canonical runs, ` +
        `${summary.privacyDenials}/2 privacy denials, ` +
        `${summary.integrityDenials}/1 integrity denial, and ` +
        `${summary.cancellations}/1 cancellation; output ${summary.outputSha256}, ` +
        `result ${summary.outputFingerprint}.\n`,
      );
    })
    .catch((error) => {
      process.stderr.write(`ViewCompose screenshot preprocessing verification failed: ${error.message}\n`);
      process.exitCode = 1;
    });
}
