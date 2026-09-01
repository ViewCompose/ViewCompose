import {randomBytes} from 'node:crypto';
import {lstat, readFile, realpath} from 'node:fs/promises';
import {isAbsolute, relative, resolve, sep} from 'node:path';
import {TextDecoder} from 'node:util';
import {applyDesignIrRepairPatch} from './design-ir-repair-patch.mjs';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {
  evaluateScreenshotRepairCandidate,
} from './screenshot-repair-candidate-evaluator.mjs';
import {
  validateScreenshotRepairAuthorization,
} from './screenshot-repair-authorization-validator.mjs';
import {proposeScreenshotRepair} from './screenshot-repair-proposer.mjs';
import {generateScreenshotKotlin} from './screenshot-design-ir-to-kotlin.mjs';
import {
  deriveGeneratedPropertyEdit,
  fingerprintSourceBytes,
} from './screenshot-source-edit.mjs';
import {validateSchemaValue} from './schema-validator.mjs';

const requestSchema = JSON.parse(await readFile(
  new URL('../contracts/screenshot-source-application-request.schema.json', import.meta.url),
  'utf8',
));
const MAX_SOURCE_BYTES = 1024 * 1024;
const REQUEST_LIFETIME_MS = 10 * 60 * 1000;
const SHA256 = /^[a-f0-9]{64}$/u;
const relativeKotlinPath = /^(?!\/)(?!.*(?:^|\/)\.\.(?:\/|$))(?!.*\\)[A-Za-z0-9._/-]+\.kt$/u;

export class ScreenshotSourceApplicationPreparationError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'ScreenshotSourceApplicationPreparationError';
    this.code = code;
  }
}

function fail(code, message) {
  throw new ScreenshotSourceApplicationPreparationError(code, message);
}

function contained(root, candidate) {
  const path = relative(root, candidate);
  return path === '' || (!isAbsolute(path) && path !== '..' && !path.startsWith(`..${sep}`));
}

async function safeSourceFile(projectRoot, requestedPath) {
  if (!relativeKotlinPath.test(requestedPath ?? '')) {
    fail('VC-AI-SOURCE-APPLICATION-PATH-INVALID', 'Target must be one root-relative Kotlin path.');
  }
  const root = await realpath(projectRoot);
  const rootMetadata = await lstat(root);
  if (!rootMetadata.isDirectory() || rootMetadata.isSymbolicLink()) {
    fail('VC-AI-SOURCE-APPLICATION-ROOT-DRIFT', 'Physical project root is not a regular directory.');
  }
  const target = resolve(root, requestedPath);
  if (!contained(root, target)) {
    fail('VC-AI-SOURCE-APPLICATION-PATH-INVALID', 'Target escapes the physical project root.');
  }
  let cursor = root;
  for (const segment of requestedPath.split('/')) {
    cursor = resolve(cursor, segment);
    const metadata = await lstat(cursor).catch(() => null);
    if (metadata === null) {
      fail('VC-AI-SOURCE-APPLICATION-PATH-INVALID', 'Every target component must already exist.');
    }
    if (metadata.isSymbolicLink()) {
      fail('VC-AI-SOURCE-APPLICATION-SYMLINK', 'Target path contains a symbolic link.');
    }
  }
  const physicalTarget = await realpath(target);
  if (physicalTarget !== target || !contained(root, physicalTarget)) {
    fail('VC-AI-SOURCE-APPLICATION-SYMLINK', 'Target physical identity escaped its project path.');
  }
  const metadata = await lstat(physicalTarget);
  if (!metadata.isFile() || metadata.nlink !== 1 || metadata.size < 1 || metadata.size > MAX_SOURCE_BYTES) {
    fail(
      'VC-AI-SOURCE-APPLICATION-FILE-UNSAFE',
      'Target must be one existing regular single-link Kotlin file within the size bound.',
    );
  }
  const bytes = await readFile(physicalTarget);
  let source;
  try {
    source = new TextDecoder('utf-8', {fatal: true}).decode(bytes);
  } catch {
    fail('VC-AI-SOURCE-APPLICATION-FILE-UNSAFE', 'Target must contain strict UTF-8.');
  }
  if (source.includes('\r') || !source.endsWith('\n')) {
    fail('VC-AI-SOURCE-APPLICATION-FILE-UNSAFE', 'Target must use generated LF text with a final newline.');
  }
  return {
    root,
    target: physicalTarget,
    bytes,
    source,
    metadata,
    identity: `dev:${metadata.dev}:ino:${metadata.ino}`,
  };
}

function findNode(nodes, nodeId) {
  for (const node of nodes ?? []) {
    if (node.id === nodeId) return node;
    const child = findNode(node.children, nodeId);
    if (child) return child;
  }
  return undefined;
}

function candidateResolution(resolutionResult, applied) {
  const result = structuredClone(resolutionResult);
  result.designIr = structuredClone(applied.designIr);
  result.designIrFingerprint = applied.designIrFingerprint;
  delete result.resultFingerprint;
  result.resultFingerprint = fingerprintRepairValue(result);
  return result;
}

function generationArguments(resolutionResult, generationRequest) {
  const request = structuredClone(generationRequest);
  request.mode = 'generate';
  request.input = {
    resolutionResultFingerprint: resolutionResult.resultFingerprint,
    resolvedDesignIrFingerprint: resolutionResult.designIrFingerprint,
  };
  return {resolutionResult, generationRequest: request};
}

function exactPassedEvaluation(value) {
  const gates = value?.evaluation?.gates;
  return value?.evidence?.evidenceFingerprint &&
    Array.isArray(gates) && gates.length === 6 &&
    gates.every((gate) => gate.status === 'passed');
}

function iso(clock) {
  return new Date(clock).toISOString();
}

function sealRequest(request) {
  const confirmationSeed = fingerprintRepairValue(request);
  request.authorization.confirmationSuffix = confirmationSeed.slice(0, 12).toUpperCase();
  request.requestFingerprint = fingerprintRepairValue(request);
  const violations = validateSchemaValue(request, requestSchema);
  if (violations.length > 0) {
    fail(
      'VC-AI-SOURCE-APPLICATION-REQUEST-INVALID',
      `Prepared request violates v1: ${violations.slice(0, 3).join('; ')}`,
    );
  }
  return Object.freeze(structuredClone(request));
}

export async function prepareScreenshotSourceApplication({
  projectRoot,
  relativePath: requestedPath,
  baselineEvidence,
  candidateEvidence,
  authorization,
  resolutionResult,
  generationRequest,
  previewBindings,
  pixelReference,
} = {}, {
  boundProjectRoot = process.env.VIEWCOMPOSE_PROJECT_ROOT,
  frameworkProfileFingerprint = process.env.VIEWCOMPOSE_FRAMEWORK_PROFILE,
  now = () => Date.now(),
  nonce = () => randomBytes(16).toString('hex'),
  propose = proposeScreenshotRepair,
  validateAuthorization = validateScreenshotRepairAuthorization,
  applyPatch = applyDesignIrRepairPatch,
  evaluate = evaluateScreenshotRepairCandidate,
  generate = generateScreenshotKotlin,
  signal,
} = {}) {
  if (signal?.aborted) {
    fail('VC-AI-SOURCE-APPLICATION-CANCELLED', 'Source-application preparation was cancelled.');
  }
  if (!projectRoot || !boundProjectRoot || !SHA256.test(frameworkProfileFingerprint ?? '')) {
    fail(
      'VC-AI-SOURCE-APPLICATION-PROFILE-DRIFT',
      'Preparation requires the installed physical project binding and exact framework profile.',
    );
  }
  const [requestedRoot, boundRoot] = await Promise.all([realpath(projectRoot), realpath(boundProjectRoot)]);
  if (requestedRoot !== boundRoot) {
    fail('VC-AI-SOURCE-APPLICATION-ROOT-DRIFT', 'Requested project root does not match the installed binding.');
  }
  const sourceFile = await safeSourceFile(requestedRoot, requestedPath);
  const proposal = await propose({baselineEvidence, candidateEvidence}, {signal});
  if (proposal?.status !== 'proposed') {
    fail('VC-AI-SOURCE-APPLICATION-NOT-ELIGIBLE', 'Screenshot evidence produced no eligible repair.');
  }
  const validation = await validateAuthorization({
    baselineEvidence,
    candidateEvidence,
    proposal,
    authorization,
  }, {signal, propose});
  if (validation?.status !== 'validated') {
    fail(
      'VC-AI-SOURCE-APPLICATION-AUTHORIZATION-INVALID',
      'Screenshot repair attestations did not reproduce their exact evidence lineage.',
    );
  }
  if (
    resolutionResult?.designIrFingerprint !==
      candidateEvidence?.lineage?.candidateDesignIrFingerprint ||
    fingerprintRepairValue(resolutionResult?.designIr) !== resolutionResult?.designIrFingerprint
  ) {
    fail('VC-AI-SOURCE-APPLICATION-CANDIDATE-DRIFT', 'Resolution and candidate evidence diverged.');
  }
  const applied = await applyPatch({
    designIr: resolutionResult.designIr,
    expectedDesignIrFingerprint: resolutionResult.designIrFingerprint,
    patch: proposal.patch,
  }, {signal});
  const preApply = await evaluate({
    resolutionResult,
    generationRequest,
    previewBindings,
    pixelReference,
    patch: proposal.patch,
  }, {signal});
  if (!exactPassedEvaluation(preApply)) {
    fail(
      'VC-AI-SOURCE-APPLICATION-NOT-ELIGIBLE',
      'The exact repaired candidate did not pass all six pre-application gates.',
    );
  }
  const repairedResolution = candidateResolution(resolutionResult, applied);
  const [currentGenerated, candidateGenerated] = await Promise.all([
    generate(generationArguments(resolutionResult, generationRequest)),
    generate(generationArguments(repairedResolution, generationRequest)),
  ]);
  if (currentGenerated?.status !== 'success' || candidateGenerated?.status !== 'success') {
    fail('VC-AI-SOURCE-APPLICATION-CANDIDATE-DRIFT', 'Generated source could not be reproduced.');
  }
  if (
    fingerprintSourceBytes(sourceFile.bytes) !== currentGenerated.outputFingerprint ||
    sourceFile.source !== currentGenerated.kotlin
  ) {
    fail(
      'VC-AI-SOURCE-APPLICATION-PREIMAGE-DRIFT',
      'Physical target is not the exact current generated Kotlin source.',
    );
  }
  const operation = proposal.patch.operations[0];
  const currentNode = findNode(resolutionResult.designIr.roots, operation.nodeId);
  const currentField = currentNode?.properties?.find((field) => field.name === operation.name);
  if (
    operation.op !== 'replace-field' ||
    operation.collection !== 'properties' ||
    !['text', 'hint'].includes(operation.name) ||
    currentField?.value?.kind !== 'literal' ||
    operation.value?.kind !== 'literal'
  ) {
    fail(
      'VC-AI-SOURCE-APPLICATION-EDIT-UNSUPPORTED',
      'Public source application supports only one generated literal text or hint rollback.',
    );
  }
  const edit = deriveGeneratedPropertyEdit({
    currentKotlin: currentGenerated.kotlin,
    candidateKotlin: candidateGenerated.kotlin,
    relativePath: requestedPath,
    nodeId: operation.nodeId,
    propertyName: operation.name,
    currentValue: currentField.value.value,
    candidateValue: operation.value.value,
  });
  const createdAt = now();
  const request = {
    schemaVersion: 1,
    kind: 'screenshot-source-application-request',
    status: 'prepared',
    nonce: nonce(),
    createdAt: iso(createdAt),
    expiresAt: iso(createdAt + REQUEST_LIFETIME_MS),
    project: {
      physicalRootFingerprint: fingerprintRepairValue({physicalRoot: sourceFile.root}),
      frameworkProfileFingerprint,
      relativePath: requestedPath,
      fileIdentity: {kind: 'regular-file', key: sourceFile.identity, linkCount: 1},
      encoding: 'utf-8',
      preimage: {bytes: sourceFile.bytes.length, sha256: fingerprintSourceBytes(sourceFile.bytes)},
    },
    edit,
    lineage: {
      inputDesignIrFingerprint: resolutionResult.designIrFingerprint,
      resultDesignIrFingerprint: applied.designIrFingerprint,
      changeFingerprint: proposal.patch.changeFingerprint,
      proposalFingerprint: proposal.proposalFingerprint,
      authorizationValidationFingerprint: validation.validationFingerprint,
      patchOutputFingerprint: applied.outputFingerprint,
      baselineEvidenceFingerprint: baselineEvidence.evidenceFingerprint,
      candidateEvidenceFingerprint: candidateEvidence.evidenceFingerprint,
      preApplyEvidenceFingerprint: preApply.evidence.evidenceFingerprint,
    },
    authorization: {
      mode: 'controlling-tty-exact-request',
      confirmationSuffix: '000000000000',
      singleUse: true,
      bypassAllowed: false,
    },
    recovery: {
      location: 'platform-user-state-outside-project',
      sourceControl: false,
      permissions: 'owner-only',
      preimageSha256: fingerprintSourceBytes(sourceFile.bytes),
      candidateSha256: edit.candidate.sha256,
      rollbackTargetSha256: fingerprintSourceBytes(sourceFile.bytes),
    },
    policy: {
      version: 1,
      sourceOwnership: 'exact-tool-generated-kotlin',
      maxFiles: 1,
      maxEdits: 1,
      arbitrarySource: false,
      wholeFileReplacement: false,
      mcpSourceWrite: false,
      shellExecution: false,
      networkAccess: false,
      automaticRollback: false,
      postApplyEvidence: [
        'static',
        'compiled',
        'rendered',
        'semantic-geometry',
        'eligible-pixels',
      ],
    },
  };
  return {
    request: sealRequest(request),
    preApplyEvidence: structuredClone(preApply.evidence),
    repairedResolution,
    generationRequest: generationArguments(repairedResolution, generationRequest).generationRequest,
    previewBindings: structuredClone(previewBindings),
    pixelReference: structuredClone(pixelReference),
  };
}
