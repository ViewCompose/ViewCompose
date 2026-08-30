import {createHash} from 'node:crypto';
import {canonicalJson} from './screenshot-contract.mjs';
import {
  DESIGN_IR_SCHEMA,
  SCREENSHOT_RESOLUTION_ARGUMENTS_SCHEMA,
  SCREENSHOT_RESOLUTION_REQUEST_SCHEMA,
  SCREENSHOT_RESOLUTION_RESULT_SCHEMA,
} from './screenshot-resolution-contract.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {diagnostic, toolResult} from './tool-core.mjs';

class ScreenshotResolutionError extends Error {
  constructor(code, message, nextAction, status = 'invalid') {
    super(message);
    this.code = code;
    this.nextAction = nextAction;
    this.status = status;
  }
}

function fingerprint(value, omittedKey) {
  const copy = structuredClone(value);
  if (omittedKey) delete copy[omittedKey];
  return createHash('sha256').update(canonicalJson(copy)).digest('hex');
}

function fail(code, message, nextAction, status) {
  throw new ScreenshotResolutionError(code, message, nextAction, status);
}

function throwIfCancelled(signal) {
  if (signal?.aborted) {
    fail(
      'VC-AI-SCREENSHOT-RESOLUTION-CANCELLED',
      'Screenshot inference resolution was cancelled before the patch was accepted.',
      'Retry the same immutable validated inference and human-resolution request.',
      'cancelled',
    );
  }
}

function collectNodes(roots) {
  const nodes = [];
  const visit = (node) => {
    nodes.push(node);
    for (const child of node.children) visit(child);
  };
  for (const root of roots) visit(root);
  return nodes;
}

function sameRegion(left, right) {
  return ['x', 'y', 'width', 'height'].every((key) => left?.[key] === right?.[key]);
}

function regionSpan(region) {
  return `pixels:${region.x},${region.y},${region.width},${region.height}`;
}

function assertUnique(values, label) {
  if (new Set(values).size !== values.length) {
    fail(
      'VC-AI-SCREENSHOT-RESOLUTION-QUESTION-MISMATCH',
      `${label} must be unique.`,
      'Remove duplicate answers or decisions and retry the exact imported questions.',
    );
  }
}

function fieldMap(fields) {
  return new Map(fields.map((field) => [field.name, field.value]));
}

function setField(fields, name, value) {
  const index = fields.findIndex((field) => field.name === name);
  const next = {name, value: structuredClone(value)};
  if (index >= 0) fields[index] = next;
  else fields.push(next);
}

function allowedField(node, field) {
  const key = `${field.collection}:${field.name}`;
  const allowed = {
    text: new Set(['properties:text']),
    button: new Set(['properties:text']),
    'text-field': new Set(['properties:hint', 'properties:inputType', 'state:text']),
  }[node.kind];
  if (!allowed?.has(key)) return false;
  if (key === 'properties:text' || key === 'properties:hint') {
    return field.value.kind === 'literal' && field.value.value.trim().length > 0;
  }
  if (key === 'properties:inputType') {
    return field.value.kind === 'enum' &&
      field.value.type === 'android-input-type' &&
      ['number', 'text', 'textEmailAddress', 'textPassword'].includes(field.value.value);
  }
  return field.value.kind === 'binding' && field.value.status === 'resolved';
}

function applyFieldAnswer(node, answer) {
  assertUnique(
    answer.decision.fields.map((field) => `${field.collection}:${field.name}`),
    `${answer.questionId} fields`,
  );
  for (const field of answer.decision.fields) {
    if (!allowedField(node, field)) {
      fail(
        'VC-AI-SCREENSHOT-RESOLUTION-DECISION-UNSUPPORTED',
        `${answer.questionId} cannot set ${node.kind}.${field.collection}.${field.name}.`,
        'Use only the typed text, text-field purpose/state, or button-label decisions.',
      );
    }
    setField(node[field.collection], field.name, field.value);
  }
}

function applyEventAnswer(node, answer) {
  assertUnique(
    answer.decision.events.map((event) => event.kind),
    `${answer.questionId} events`,
  );
  const allowed = node.kind === 'button'
    ? new Set(['click'])
    : node.kind === 'text-field'
      ? new Set(['focus-change', 'keyboard-action'])
      : new Set();
  for (const event of answer.decision.events) {
    if (!allowed.has(event.kind) || event.status !== 'resolved') {
      fail(
        'VC-AI-SCREENSHOT-RESOLUTION-DECISION-UNSUPPORTED',
        `${answer.questionId} cannot bind ${event.kind} on ${node.kind}.`,
        'Use a caller-owned callback kind supported by the resolved ViewCompose component.',
      );
    }
    const index = node.events.findIndex((candidate) => candidate.kind === event.kind);
    if (index >= 0) node.events[index] = structuredClone(event);
    else node.events.push(structuredClone(event));
  }
}

function expectedAccessibilityRole(node, decision) {
  if (node.kind === 'button') return 'button';
  if (node.kind === 'text-field') return 'text-field';
  if (node.kind === 'image') return decision.decorative ? 'none' : 'image';
  return 'none';
}

function applyAccessibilityAnswer(nodes, answer) {
  assertUnique(answer.decision.nodes.map((decision) => decision.nodeId), 'Accessibility node IDs');
  assertUnique(
    answer.decision.nodes.map((decision) => decision.traversalIndex),
    'Accessibility traversal indices',
  );
  const expectedTraversal = nodes.map((_, index) => index);
  const actualTraversal = answer.decision.nodes.map((decision) => decision.traversalIndex).sort(
    (left, right) => left - right,
  );
  if (
    answer.decision.nodes.length !== nodes.length ||
    JSON.stringify(actualTraversal) !== JSON.stringify(expectedTraversal)
  ) {
    fail(
      'VC-AI-SCREENSHOT-RESOLUTION-COVERAGE-INCOMPLETE',
      'Accessibility review must cover every imported node with one contiguous traversal index.',
      'Review every node exactly once and assign traversal indices from zero in reading order.',
    );
  }
  const nodeById = new Map(nodes.map((node) => [node.id, node]));
  for (const decision of answer.decision.nodes) {
    const node = nodeById.get(decision.nodeId);
    const expectedRole = node ? expectedAccessibilityRole(node, decision) : undefined;
    if (
      !node ||
      decision.role !== expectedRole ||
      (decision.decorative && node.kind !== 'image') ||
      (decision.role === 'none' && node.kind === 'image' && !decision.decorative) ||
      (decision.labelSource === 'field-label' && node.kind !== 'text-field') ||
      (decision.labelSource === 'visible-text' && !['button', 'text'].includes(node.kind)) ||
      (decision.labelSource === 'none' && ['button', 'text-field'].includes(node.kind))
    ) {
      fail(
        'VC-AI-SCREENSHOT-RESOLUTION-DECISION-UNSUPPORTED',
        `${decision.nodeId} has an incompatible accessibility role, label source, or decorative decision.`,
        'Match each explicit accessibility decision to the resolved component kind and visible label owner.',
      );
    }
    node.semantics = node.semantics.filter((field) => ![
      'role',
      'accessibilityLabelSource',
      'traversalIndex',
      'decorative',
    ].includes(field.name));
    if (decision.role !== 'none') {
      node.semantics.push({
        name: 'role',
        value: {kind: 'enum', type: 'semantic-role', value: decision.role},
      });
    }
    node.semantics.push(
      {
        name: 'accessibilityLabelSource',
        value: {
          kind: 'enum',
          type: 'accessibility-label-source',
          value: decision.labelSource,
        },
      },
      {name: 'traversalIndex', value: {kind: 'literal', value: decision.traversalIndex}},
      {name: 'decorative', value: {kind: 'literal', value: decision.decorative}},
    );
  }
}

function unsupportedCategory(unsupported) {
  if (unsupported.code.includes('BEHAVIOR')) return 'behavior';
  if (unsupported.code.includes('ACCESSIBILITY')) return 'accessibility';
  return 'content';
}

function countPlaceholders(nodes) {
  let count = 0;
  for (const node of nodes) {
    for (const fields of [
      node.properties,
      node.semantics,
      node.state,
      ...node.modifiers.map((modifier) => modifier.arguments),
    ]) {
      count += fields.filter((field) =>
        field.value.kind === 'binding' && field.value.status === 'placeholder').length;
    }
    count += node.events.filter((event) => event.status === 'placeholder').length;
  }
  return count;
}

function schemaFailure(arguments_, violations) {
  const answers = arguments_?.resolutionRequest?.answers ?? [];
  const values = answers.flatMap((answer) => answer.decision?.fields ?? []).map((field) => field.value);
  if (values.some((value) => ['expression', 'resource'].includes(value?.kind)) ||
      answers.some((answer) => answer.decision?.source !== undefined)) {
    return new ScreenshotResolutionError(
      'VC-AI-SCREENSHOT-RESOLUTION-EXECUTABLE-DENIED',
      'Screenshot resolution accepts no executable expression, callback source, or guessed resource.',
      'Use literal, input-profile, state-binding, event-binding, or accessibility-review decisions only.',
    );
  }
  return new ScreenshotResolutionError(
    'VC-AI-SCREENSHOT-RESOLUTION-INPUT-INVALID',
    `Screenshot resolution input violates the frozen schema: ${violations.slice(0, 3).join('; ')}`,
    'Use one validated inference import and one exact typed human-resolution request.',
  );
}

function failureResult({requestId, error, elapsedMs}) {
  const known = error instanceof ScreenshotResolutionError;
  return toolResult({
    requestId,
    tool: 'resolve_screenshot_inference',
    status: known ? error.status : 'failed',
    level: 'static',
    diagnostics: [diagnostic({
      code: known ? error.code : 'VC-AI-SCREENSHOT-RESOLUTION-INPUT-INVALID',
      severity: 'error',
      message: known
        ? error.message
        : 'Screenshot inference resolution failed before a typed patch was accepted.',
      nextAction: known
        ? error.nextAction
        : 'Correct the validated import and typed answers without adding executable content.',
    })],
    elapsedMs,
    truncated: known && error.status === 'limited',
  });
}

export async function resolveScreenshotInference(arguments_, {
  requestId = 'resolve-screenshot-inference',
  signal,
} = {}) {
  const started = performance.now();
  try {
    throwIfCancelled(signal);
    const argumentViolations = validateSchemaValue(
      arguments_,
      SCREENSHOT_RESOLUTION_ARGUMENTS_SCHEMA,
    );
    if (argumentViolations.length > 0) throw schemaFailure(arguments_, argumentViolations);
    const validated = arguments_.validatedInference;
    const request = arguments_.resolutionRequest;
    const requestViolations = validateSchemaValue(request, SCREENSHOT_RESOLUTION_REQUEST_SCHEMA);
    if (requestViolations.length > 0) throw schemaFailure(arguments_, requestViolations);
    const inputDesignIrViolations = validateSchemaValue(validated.designIr, DESIGN_IR_SCHEMA);
    if (inputDesignIrViolations.length > 0) {
      fail(
        'VC-AI-SCREENSHOT-RESOLUTION-INPUT-INVALID',
        `Validated Design IR violates v1: ${inputDesignIrViolations.slice(0, 3).join('; ')}`,
        'Re-run validate_screenshot_inference and pass its unchanged imported data.',
      );
    }
    if (
      fingerprint(validated, 'validationFingerprint') !== validated.validationFingerprint ||
      fingerprint(validated.designIr) !== validated.fingerprints.designIr ||
      request.input.validationFingerprint !== validated.validationFingerprint ||
      request.input.inferenceResultFingerprint !== validated.fingerprints.inferenceResult ||
      request.input.designIrFingerprint !== validated.fingerprints.designIr ||
      request.authorization.approvedValidationFingerprint !== validated.validationFingerprint
    ) {
      fail(
        'VC-AI-SCREENSHOT-RESOLUTION-LINEAGE-MISMATCH',
        'Resolution input, authorization, and the validated inference do not share one exact lineage.',
        'Pass the unchanged validated import and bind the request and review receipt to its fingerprints.',
      );
    }
    if (
      validated.status !== 'incomplete' ||
      validated.summary.codeGenerationAllowed !== false ||
      validated.summary.unresolvedQuestions !== validated.unresolvedQuestions.length ||
      validated.summary.unsupportedSemantics !== validated.designIr.unsupported.length
    ) {
      fail(
        'VC-AI-SCREENSHOT-RESOLUTION-INPUT-INVALID',
        'Resolution requires one internally consistent incomplete validated inference import.',
        'Do not resolve an already complete or summary-inconsistent inference result.',
      );
    }

    const questions = validated.unresolvedQuestions;
    const questionById = new Map(questions.map((question) => [question.id, question]));
    assertUnique(questions.map((question) => question.id), 'Imported question IDs');
    assertUnique(request.answers.map((answer) => answer.questionId), 'Answer question IDs');
    const answerIds = new Set(request.answers.map((answer) => answer.questionId));
    if (
      questions.some((question) => !answerIds.has(question.id)) ||
      request.answers.some((answer) => !questionById.has(answer.questionId))
    ) {
      fail(
        'VC-AI-SCREENSHOT-RESOLUTION-COVERAGE-INCOMPLETE',
        'Every imported question requires exactly one typed answer before resolution.',
        'Answer the exact question set without omissions or additions.',
      );
    }

    const designIr = structuredClone(validated.designIr);
    const nodes = collectNodes(designIr.roots);
    const nodeById = new Map(nodes.map((node) => [node.id, node]));
    const resolutionRecords = [];
    const resolvedUnsupported = new Set();
    for (const answer of request.answers) {
      throwIfCancelled(signal);
      const question = questionById.get(answer.questionId);
      if (
        answer.nodeId !== question.nodeId ||
        answer.category !== question.category ||
        answer.requiredAction !== question.requiredAction ||
        !sameRegion(answer.sourceRegion, question.sourceRegion)
      ) {
        fail(
          'VC-AI-SCREENSHOT-RESOLUTION-QUESTION-MISMATCH',
          `${answer.questionId} does not match its imported node, region, category, or required action.`,
          'Copy the exact immutable question identity and attach only its reviewed decision.',
        );
      }
      const node = answer.nodeId ? nodeById.get(answer.nodeId) : undefined;
      if (answer.nodeId && !node) {
        fail(
          'VC-AI-SCREENSHOT-RESOLUTION-QUESTION-MISMATCH',
          `${answer.questionId} refers to a node outside the validated Design IR.`,
          'Use the exact imported node ID.',
        );
      }
      if (answer.decision.kind === 'set-fields') applyFieldAnswer(node, answer);
      else if (answer.decision.kind === 'set-events') applyEventAnswer(node, answer);
      else applyAccessibilityAnswer(nodes, answer);

      const matchedUnsupported = designIr.unsupported.filter((unsupported, index) =>
        !resolvedUnsupported.has(index) &&
        unsupportedCategory(unsupported) === answer.category &&
        unsupported.sourceSpan === regionSpan(answer.sourceRegion));
      if (matchedUnsupported.length === 0) {
        fail(
          'VC-AI-SCREENSHOT-RESOLUTION-UNSUPPORTED-REMAINS',
          `${answer.questionId} has no exact question-bound unsupported semantic to resolve.`,
          'Preserve source-region and category lineage for every unsupported semantic.',
        );
      }
      const resolvedCodes = [];
      for (const unsupported of matchedUnsupported) {
        const index = designIr.unsupported.indexOf(unsupported);
        resolvedUnsupported.add(index);
        if (!resolvedCodes.includes(unsupported.code)) resolvedCodes.push(unsupported.code);
      }
      resolutionRecords.push({
        questionId: answer.questionId,
        category: answer.category,
        requiredAction: answer.requiredAction,
        decisionKind: answer.decision.kind,
        targetNodeIds: answer.decision.kind === 'accessibility-review'
          ? answer.decision.nodes.map((decision) => decision.nodeId)
          : [answer.nodeId],
        resolvedUnsupportedCodes: resolvedCodes,
        reviewReceipt: request.authorization.reviewReceipt,
      });
    }

    designIr.unsupported = designIr.unsupported.filter((_, index) => !resolvedUnsupported.has(index));
    const placeholders = countPlaceholders(nodes);
    const remainingQuestionIds = questions.filter((question) => !answerIds.has(question.id)).map(
      (question) => question.id,
    );
    const codeGenerationAllowed = remainingQuestionIds.length === 0 &&
      designIr.unsupported.length === 0 && placeholders === 0;
    if (!codeGenerationAllowed) {
      fail(
        'VC-AI-SCREENSHOT-RESOLUTION-CODEGEN-BLOCKED',
        'Typed answers left questions, unsupported semantics, or placeholder bindings unresolved.',
        'Complete the exact human review before requesting screenshot-specific Kotlin generation.',
      );
    }

    const result = {
      schemaVersion: 1,
      kind: 'result',
      status: 'resolved',
      requestFingerprint: fingerprint(request),
      input: request.input,
      authorization: request.authorization,
      designIr,
      designIrFingerprint: fingerprint(designIr),
      resolutionRecords,
      remainingQuestionIds,
      summary: {
        answeredQuestions: request.answers.length,
        remainingQuestions: remainingQuestionIds.length,
        resolvedUnsupportedSemantics: resolvedUnsupported.size,
        remainingUnsupportedSemantics: designIr.unsupported.length,
        placeholderBindings: placeholders,
        codeGenerationAllowed,
      },
      diagnostics: [],
    };
    result.resultFingerprint = fingerprint(result);
    const resultViolations = validateSchemaValue(result, SCREENSHOT_RESOLUTION_RESULT_SCHEMA);
    const designIrViolations = validateSchemaValue(result.designIr, DESIGN_IR_SCHEMA);
    if (resultViolations.length > 0 || designIrViolations.length > 0) {
      fail(
        'VC-AI-SCREENSHOT-RESOLUTION-INPUT-INVALID',
        'Resolved output no longer conforms to the frozen result or Design IR schema.',
        'Keep typed patching within the schema-owned Design IR surface.',
      );
    }
    return toolResult({
      requestId,
      tool: 'resolve_screenshot_inference',
      status: 'success',
      level: 'static',
      outputFingerprint: result.resultFingerprint,
      diagnostics: [],
      data: result,
      elapsedMs: performance.now() - started,
    });
  } catch (error) {
    return failureResult({requestId, error, elapsedMs: performance.now() - started});
  }
}
