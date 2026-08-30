import {createHash} from 'node:crypto';
import {canonicalJson} from './screenshot-contract.mjs';
import {
  DESIGN_IR_SCHEMA,
  SCREENSHOT_INFERENCE_REQUEST_SCHEMA,
  SCREENSHOT_INFERENCE_RESULT_SCHEMA,
  SCREENSHOT_INFERENCE_VALIDATION_ARGUMENTS_SCHEMA,
} from './screenshot-inference-contract.mjs';
import {prepareScreenshot} from './screenshot-preprocessor.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {diagnostic, loadKnowledgeManifest, toolResult} from './tool-core.mjs';

const SHA256 = /^[a-f0-9]{64}$/u;
const PIXEL_SPAN = /^pixels:(0|[1-9][0-9]*),(0|[1-9][0-9]*),([1-9][0-9]*),([1-9][0-9]*)$/u;

class ScreenshotInferenceValidationError extends Error {
  constructor(code, message, nextAction, status = 'invalid') {
    super(message);
    this.code = code;
    this.nextAction = nextAction;
    this.status = status;
  }
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function fail(code, message, nextAction, status) {
  throw new ScreenshotInferenceValidationError(code, message, nextAction, status);
}

function throwIfCancelled(signal) {
  if (signal?.aborted) {
    fail(
      'VC-AI-SCREENSHOT-INFERENCE-CANCELLED',
      'Screenshot inference validation was cancelled before evidence was accepted.',
      'Retry the same immutable preprocessing request, declaration, and inference result.',
      'cancelled',
    );
  }
}

function schemaFailure(arguments_, violations) {
  const authorization = arguments_?.inferenceDeclaration?.authorization;
  if (authorization && ['apiKey', 'token', 'credential', 'credentials'].some((key) =>
    Object.hasOwn(authorization, key))) {
    return new ScreenshotInferenceValidationError(
      'VC-AI-SCREENSHOT-INFERENCE-CREDENTIAL-DENIED',
      'Screenshot inference validation accepts no credential field.',
      'Remove credentials; the validator performs no provider or network execution.',
    );
  }
  if (
    authorization?.mode === 'provider-adapter' &&
    (!Object.hasOwn(authorization, 'consentReceipt') ||
      !Object.hasOwn(authorization, 'consentInputFingerprint'))
  ) {
    return new ScreenshotInferenceValidationError(
      'VC-AI-SCREENSHOT-INFERENCE-CONSENT-REQUIRED',
      'Provider-produced inference requires an explicit receipt bound to the exact input.',
      'Provide the consent receipt identity and the approved preprocessing output fingerprint.',
    );
  }
  return new ScreenshotInferenceValidationError(
    'VC-AI-SCREENSHOT-INFERENCE-INPUT-INVALID',
    `Screenshot inference validation input violates the frozen schema: ${violations.slice(0, 3).join('; ')}`,
    'Use one preprocessing request, one inference declaration, and one provider-neutral result.',
  );
}

function canonicalFingerprint(value, omittedKey) {
  const copy = structuredClone(value);
  if (omittedKey) delete copy[omittedKey];
  return sha256(canonicalJson(copy));
}

function collectNodes(roots) {
  const nodes = [];
  function visit(node, depth) {
    nodes.push({node, depth});
    for (const child of node.children) visit(child, depth + 1);
  }
  for (const root of roots) visit(root, 1);
  return nodes;
}

function assertUnique(values, label) {
  if (new Set(values).size !== values.length) {
    fail(
      'VC-AI-SCREENSHOT-INFERENCE-EVIDENCE-INCOMPLETE',
      `${label} must be unique.`,
      'Remove duplicate inference identities before importing the result.',
    );
  }
}

function assertRegion(region, screenshot, label) {
  if (
    !region ||
    !Number.isInteger(region.x) ||
    !Number.isInteger(region.y) ||
    !Number.isInteger(region.width) ||
    !Number.isInteger(region.height) ||
    region.x < 0 || region.y < 0 || region.width <= 0 || region.height <= 0 ||
    region.x + region.width > screenshot.widthPx ||
    region.y + region.height > screenshot.heightPx
  ) {
    fail(
      'VC-AI-SCREENSHOT-INFERENCE-REGION-INVALID',
      `${label} leaves the exact preprocessed screenshot.`,
      'Use one positive, in-bounds pixel rectangle from the accepted screenshot.',
    );
  }
}

function regionSpan(region) {
  return `pixels:${region.x},${region.y},${region.width},${region.height}`;
}

function parseSpan(span, screenshot, label) {
  const match = PIXEL_SPAN.exec(span);
  if (!match) {
    fail(
      'VC-AI-SCREENSHOT-INFERENCE-REGION-INVALID',
      `${label} does not use pixels:x,y,width,height provenance.`,
      'Preserve one exact screenshot pixel rectangle for every source span.',
    );
  }
  const region = {
    x: Number(match[1]),
    y: Number(match[2]),
    width: Number(match[3]),
    height: Number(match[4]),
  };
  assertRegion(region, screenshot, label);
  return region;
}

function verifyFields(node, evidence, threshold) {
  const assessments = new Map(
    evidence.assessments.map((assessment) => [assessment.dimension, assessment]),
  );
  for (const [label, fields] of [
    ['properties', node.properties],
    ['semantics', node.semantics],
    ['state', node.state],
  ]) {
    assertUnique(fields.map((field) => field.name), `${node.id} ${label}`);
    for (const field of fields) {
      if (field.value.kind === 'resource' || field.value.kind === 'expression') {
        fail(
          'VC-AI-SCREENSHOT-INFERENCE-EVIDENCE-INCOMPLETE',
          `${node.id}.${field.name} invents a resource or executable expression from pixels.`,
          'Use a placeholder binding and preserve the missing value as a blocking question.',
        );
      }
      if (field.value.kind === 'binding' && field.value.status !== 'placeholder') {
        fail(
          'VC-AI-SCREENSHOT-INFERENCE-EVIDENCE-INCOMPLETE',
          `${node.id}.${field.name} resolves a binding that the screenshot contract did not supply.`,
          'Keep screenshot-derived bindings as placeholders until an explicit owner resolves them.',
        );
      }
    }
  }
  assertUnique(node.modifiers.map((modifier) => modifier.kind), `${node.id} modifiers`);
  for (const modifier of node.modifiers) {
    assertUnique(modifier.arguments.map((field) => field.name), `${node.id}.${modifier.kind} arguments`);
    for (const field of modifier.arguments) {
      if (['resource', 'expression'].includes(field.value.kind)) {
        fail(
          'VC-AI-SCREENSHOT-INFERENCE-EVIDENCE-INCOMPLETE',
          `${node.id}.${modifier.kind} invents an unverified resource or expression.`,
          'Keep imported modifiers data-only and backed by explicit pixel evidence.',
        );
      }
    }
  }
  if (node.events.length > 0) {
    fail(
      'VC-AI-SCREENSHOT-INFERENCE-BEHAVIOR-FORBIDDEN',
      `${node.id} infers behavior from a still screenshot.`,
      'Remove events and ask who owns each interaction before code generation.',
    );
  }
  if (node.semantics.length > 0 &&
      (assessments.get('semantics')?.confidence ?? -1) < threshold) {
    fail(
      'VC-AI-SCREENSHOT-INFERENCE-CONFIDENCE-UNRESOLVED',
      `${node.id} resolves semantics below the accepted confidence threshold.`,
      'Replace the semantics with a blocking question or supply stronger reviewed evidence.',
    );
  }
}

function validationFailure({requestId, error, elapsedMs}) {
  const known = error instanceof ScreenshotInferenceValidationError;
  return toolResult({
    requestId,
    tool: 'validate_screenshot_inference',
    status: known ? error.status : 'failed',
    level: 'static',
    diagnostics: [diagnostic({
      code: known ? error.code : 'VC-AI-SCREENSHOT-INFERENCE-INPUT-INVALID',
      severity: 'error',
      message: known ? error.message : 'Screenshot inference validation failed before evidence was accepted.',
      nextAction: known
        ? error.nextAction
        : 'Correct the provider-neutral request/result contract and retry without provider execution.',
    })],
    elapsedMs,
    truncated: known && error.status === 'limited',
  });
}

export async function validateScreenshotInference(arguments_, {
  requestId = 'validate-screenshot-inference',
  signal,
  prepare = prepareScreenshot,
} = {}) {
  const started = performance.now();
  try {
    throwIfCancelled(signal);
    const argumentViolations = validateSchemaValue(
      arguments_,
      SCREENSHOT_INFERENCE_VALIDATION_ARGUMENTS_SCHEMA,
    );
    if (argumentViolations.length > 0) throw schemaFailure(arguments_, argumentViolations);

    const prepared = await prepare(arguments_.preprocessingRequest, {
      requestId: `${requestId}:preprocess`,
      signal,
    });
    throwIfCancelled(signal);
    if (prepared.status !== 'success') {
      const code = prepared.diagnostics?.[0]?.code ?? 'VC-AI-SCREENSHOT-INPUT-INVALID';
      fail(
        'VC-AI-SCREENSHOT-INFERENCE-PREPROCESSING-FAILED',
        `Screenshot preprocessing did not produce accepted input (${code}).`,
        'Correct the embedded PNG, privacy review, crop, redactions, or declared identity first.',
        prepared.status === 'limited' ? 'limited' : prepared.status === 'cancelled' ? 'cancelled' : 'invalid',
      );
    }
    const declaration = arguments_.inferenceDeclaration;
    const inferenceRequest = {
      schemaVersion: 1,
      kind: 'request',
      source: {
        preprocessingRequestFingerprint: prepared.data.requestFingerprint,
        preprocessingOutputFingerprint: prepared.data.outputFingerprint,
      },
      screenshot: prepared.data.output,
      interpretation: declaration.interpretation,
      intent: declaration.intent,
      policy: declaration.policy,
      authorization: declaration.authorization,
    };
    const requestViolations = validateSchemaValue(
      inferenceRequest,
      SCREENSHOT_INFERENCE_REQUEST_SCHEMA,
    );
    if (requestViolations.length > 0) throw schemaFailure(arguments_, requestViolations);
    const result = arguments_.inferenceResult;
    const resultViolations = validateSchemaValue(result, SCREENSHOT_INFERENCE_RESULT_SCHEMA);
    if (resultViolations.length > 0) throw schemaFailure(arguments_, resultViolations);
    const designIrViolations = validateSchemaValue(result.designIr, DESIGN_IR_SCHEMA);
    if (designIrViolations.length > 0) {
      fail(
        'VC-AI-SCREENSHOT-INFERENCE-INPUT-INVALID',
        `Imported Design IR violates v1: ${designIrViolations.slice(0, 3).join('; ')}`,
        'Return schema-valid Design IR v1 without unknown or executable fields.',
      );
    }

    const manifest = await loadKnowledgeManifest();
    if (
      inferenceRequest.policy.frameworkVersionLane !== manifest.framework.versionLane ||
      inferenceRequest.policy.frameworkIdentity !== manifest.framework.identity
    ) {
      fail(
        'VC-AI-SCREENSHOT-INFERENCE-LINEAGE-MISMATCH',
        'Inference policy targets a different framework identity than the loaded tool.',
        `Use ${manifest.framework.versionLane}/${manifest.framework.identity}.`,
      );
    }
    const requestFingerprint = canonicalFingerprint(inferenceRequest);
    const resultFingerprint = canonicalFingerprint(result, 'resultFingerprint');
    const designIrFingerprint = canonicalFingerprint(result.designIr);
    const screenshot = inferenceRequest.screenshot;
    const authorization = inferenceRequest.authorization;
    if (
      result.requestFingerprint !== requestFingerprint ||
      result.resultFingerprint !== resultFingerprint ||
      result.input.preprocessingOutputFingerprint !== prepared.data.outputFingerprint ||
      result.input.screenshotSha256 !== screenshot.sha256 ||
      result.input.widthPx !== screenshot.widthPx ||
      result.input.heightPx !== screenshot.heightPx ||
      authorization.approvedInputFingerprint !== prepared.data.outputFingerprint
    ) {
      fail(
        'VC-AI-SCREENSHOT-INFERENCE-LINEAGE-MISMATCH',
        'Inference request, result, authorization, or screenshot identity does not share one lineage.',
        'Recompute the result from the exact canonical preprocessing output and request fingerprint.',
      );
    }
    if (authorization.mode === 'human-golden') {
      if (
        result.producer.kind !== 'human-golden' ||
        result.producer.networkAccess !== false ||
        result.producer.providerTransfer !== false
      ) {
        fail(
          'VC-AI-SCREENSHOT-INFERENCE-LINEAGE-MISMATCH',
          'Human-golden authorization cannot import a provider-produced result.',
          'Use matching producer provenance and authorization mode.',
        );
      }
    } else if (
      result.producer.kind !== 'provider-adapter' ||
      result.producer.providerId !== authorization.providerId ||
      authorization.consentInputFingerprint !== prepared.data.outputFingerprint ||
      !SHA256.test(result.producer.providerRequestFingerprint) ||
      !SHA256.test(result.producer.providerResponseFingerprint)
    ) {
      fail(
        'VC-AI-SCREENSHOT-INFERENCE-CONSENT-REQUIRED',
        'Provider provenance, consent input, and authorization do not bind the same producer and screenshot.',
        'Use the exact provider ID, consent-bound input fingerprint, and immutable provider identities.',
      );
    }

    if (
      result.designIr.source.kind !== 'screenshot' ||
      result.designIr.source.identity !== `preprocessed:${prepared.data.outputFingerprint}` ||
      result.designIr.source.fingerprint !== screenshot.sha256
    ) {
      fail(
        'VC-AI-SCREENSHOT-INFERENCE-LINEAGE-MISMATCH',
        'Design IR source identity does not name the exact preprocessed screenshot.',
        'Bind Design IR to preprocessed:<outputFingerprint> and the screenshot SHA-256.',
      );
    }

    const nodesWithDepth = collectNodes(result.designIr.roots);
    const nodes = nodesWithDepth.map(({node}) => node);
    assertUnique(nodes.map((node) => node.id), 'Design IR node IDs');
    if (
      nodes.length > inferenceRequest.policy.maxNodes ||
      Math.max(...nodesWithDepth.map(({depth}) => depth)) > inferenceRequest.policy.maxDepth ||
      result.nodeEvidence.length > inferenceRequest.policy.maxNodes
    ) {
      fail(
        'VC-AI-SCREENSHOT-INFERENCE-LIMIT',
        'Inference result exceeds its declared node, depth, or evidence limit.',
        'Return a smaller bounded Design IR and evidence set.',
        'limited',
      );
    }
    const nodeById = new Map(nodes.map((node) => [node.id, node]));
    const evidenceByNode = new Map();
    for (const evidence of result.nodeEvidence) {
      throwIfCancelled(signal);
      if (evidenceByNode.has(evidence.nodeId) || !nodeById.has(evidence.nodeId)) {
        fail(
          'VC-AI-SCREENSHOT-INFERENCE-EVIDENCE-INCOMPLETE',
          `Evidence for ${evidence.nodeId} is duplicate or has no Design IR node.`,
          'Provide exactly one evidence record for every imported node.',
        );
      }
      assertRegion(evidence.sourceRegion, screenshot, `${evidence.nodeId} evidence`);
      assertUnique(
        evidence.assessments.map((assessment) => assessment.dimension),
        `${evidence.nodeId} assessment dimensions`,
      );
      for (const assessment of evidence.assessments) {
        if (!evidence.observations.includes(assessment.dimension)) {
          fail(
            'VC-AI-SCREENSHOT-INFERENCE-EVIDENCE-INCOMPLETE',
            `${evidence.nodeId} assessment ${assessment.dimension} lacks a matching observation.`,
            'List each independently assessed evidence dimension exactly once.',
          );
        }
        if (
          assessment.basis === 'not-observed' &&
          assessment.confidence >= inferenceRequest.policy.minimumAcceptedConfidence
        ) {
          fail(
            'VC-AI-SCREENSHOT-INFERENCE-CONFIDENCE-UNRESOLVED',
            `${evidence.nodeId} marks unobserved evidence as accepted confidence.`,
            'Lower the confidence and preserve an explicit unresolved question.',
          );
        }
      }
      assertUnique(
        (evidence.alternatives ?? []).map((alternative) => alternative.kind),
        `${evidence.nodeId} alternatives`,
      );
      if ((evidence.alternatives ?? []).some((alternative) =>
        !inferenceRequest.policy.allowedNodeKinds.includes(alternative.kind))) {
        fail(
          'VC-AI-SCREENSHOT-INFERENCE-EVIDENCE-INCOMPLETE',
          `${evidence.nodeId} proposes an alternative outside the request allowlist.`,
          'Use only node kinds explicitly allowed by the inference request.',
        );
      }
      evidenceByNode.set(evidence.nodeId, evidence);
    }
    if (evidenceByNode.size !== nodes.length) {
      fail(
        'VC-AI-SCREENSHOT-INFERENCE-EVIDENCE-INCOMPLETE',
        'Every imported Design IR node requires exactly one evidence record.',
        'Add the missing node evidence before importing the result.',
      );
    }

    assertUnique(result.unresolvedQuestions.map((question) => question.id), 'Unresolved question IDs');
    const questionsByNode = new Map();
    for (const question of result.unresolvedQuestions) {
      throwIfCancelled(signal);
      assertRegion(question.sourceRegion, screenshot, `${question.id} question`);
      if (question.forbiddenDefault !== true) {
        fail(
          'VC-AI-SCREENSHOT-INFERENCE-CONFIDENCE-UNRESOLVED',
          `${question.id} permits an invented default.`,
          'Set forbiddenDefault to true and require an explicit answer or source inspection.',
        );
      }
      if (question.nodeId) {
        const evidence = evidenceByNode.get(question.nodeId);
        if (!evidence || regionSpan(question.sourceRegion) !== regionSpan(evidence.sourceRegion)) {
          fail(
            'VC-AI-SCREENSHOT-INFERENCE-REGION-INVALID',
            `${question.id} does not point to its node evidence region.`,
            'Bind node questions to the exact evidence rectangle.',
          );
        }
        const questions = questionsByNode.get(question.nodeId) ?? [];
        questions.push(question);
        questionsByNode.set(question.nodeId, questions);
      }
    }

    const expectedSourceId = `screenshot:${screenshot.sha256}`;
    for (const node of nodes) {
      throwIfCancelled(signal);
      const evidence = evidenceByNode.get(node.id);
      if (!inferenceRequest.policy.allowedNodeKinds.includes(node.kind)) {
        fail(
          'VC-AI-SCREENSHOT-INFERENCE-EVIDENCE-INCOMPLETE',
          `${node.id} uses node kind ${node.kind} outside the request allowlist.`,
          'Use an allowed kind or preserve it as an unresolved alternative.',
        );
      }
      if (
        node.provenance.sourceId !== expectedSourceId ||
        node.provenance.sourceSpan !== regionSpan(evidence.sourceRegion) ||
        !evidence.assessments.some((assessment) =>
          assessment.confidence === node.provenance.confidence)
      ) {
        fail(
          'VC-AI-SCREENSHOT-INFERENCE-EVIDENCE-INCOMPLETE',
          `${node.id} provenance does not match its exact evidence decision.`,
          'Use the screenshot SHA-256, exact pixel span, and one dimension-specific confidence.',
        );
      }
      const hasLowConfidence = evidence.assessments.some((assessment) =>
        assessment.confidence < inferenceRequest.policy.minimumAcceptedConfidence);
      if (hasLowConfidence && !(questionsByNode.get(node.id)?.length > 0)) {
        fail(
          'VC-AI-SCREENSHOT-INFERENCE-CONFIDENCE-UNRESOLVED',
          `${node.id} has low-confidence evidence without an explicit node question.`,
          'Preserve every below-threshold decision as a question with a forbidden default.',
        );
      }
      verifyFields(node, evidence, inferenceRequest.policy.minimumAcceptedConfidence);
    }
    for (const unsupported of result.designIr.unsupported) {
      throwIfCancelled(signal);
      if (unsupported.sourceId !== expectedSourceId || unsupported.disposition !== 'blocked') {
        fail(
          'VC-AI-SCREENSHOT-INFERENCE-EVIDENCE-INCOMPLETE',
          'Unsupported screenshot semantics must stay source-bound and blocked.',
          'Do not silently preserve or upgrade an unverified semantic decision.',
        );
      }
      parseSpan(unsupported.sourceSpan, screenshot, `${unsupported.code} unsupported source`);
    }

    const blockingQuestions = result.unresolvedQuestions.filter((question) => question.blocking);
    if (
      result.summary.nodes !== nodes.length ||
      result.summary.evidenceRecords !== result.nodeEvidence.length ||
      result.summary.unresolvedQuestions !== result.unresolvedQuestions.length ||
      result.summary.blockingQuestions !== blockingQuestions.length ||
      result.summary.unsupportedSemantics !== result.designIr.unsupported.length ||
      result.summary.confidenceAggregation !== 'none' ||
      (blockingQuestions.length > 0 && result.summary.codeGenerationAllowed !== false) ||
      (blockingQuestions.length > 0 && result.status !== 'incomplete')
    ) {
      fail(
        'VC-AI-SCREENSHOT-INFERENCE-CODEGEN-BLOCKED',
        'Inference summary hides unresolved evidence or permits code generation while blocked.',
        'Recompute exact counts and keep incomplete/codeGenerationAllowed=false until questions resolve.',
      );
    }

    const imported = {
      schemaVersion: 1,
      kind: 'validated-screenshot-inference',
      status: result.status,
      authorization: Object.fromEntries(Object.entries({
        mode: authorization.mode,
        providerId: authorization.providerId,
        approvedInputFingerprint: authorization.approvedInputFingerprint,
      }).filter(([, value]) => value !== undefined)),
      producer: result.producer,
      fingerprints: {
        preprocessingRequest: prepared.data.requestFingerprint,
        preprocessingOutput: prepared.data.outputFingerprint,
        screenshot: screenshot.sha256,
        inferenceRequest: requestFingerprint,
        inferenceResult: resultFingerprint,
        designIr: designIrFingerprint,
      },
      designIr: result.designIr,
      nodeEvidence: result.nodeEvidence,
      unresolvedQuestions: result.unresolvedQuestions,
      summary: result.summary,
      inferenceDiagnostics: result.diagnostics,
    };
    imported.validationFingerprint = canonicalFingerprint(imported);
    return toolResult({
      requestId,
      tool: 'validate_screenshot_inference',
      status: 'success',
      level: 'static',
      outputFingerprint: imported.validationFingerprint,
      diagnostics: result.diagnostics,
      data: imported,
      elapsedMs: performance.now() - started,
    });
  } catch (error) {
    return validationFailure({requestId, error, elapsedMs: performance.now() - started});
  }
}
