#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {canonicalJson} from './screenshot-contract.mjs';
import {resolveScreenshotInference} from './screenshot-resolution-adapter.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {TOOL_DEFINITIONS, TOOL_NAMES} from './tool-catalog.mjs';

const contractPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-resolution-contract.json', import.meta.url),
);
const inferenceContractPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-inference-contract.json', import.meta.url),
);
const inferenceResultPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-inference/wireframe.result.json', import.meta.url),
);
const inferenceRequestPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-inference/wireframe.request.json', import.meta.url),
);
const resolutionSchemaPath = fileURLToPath(
  new URL('../contracts/screenshot-inference-resolution.schema.json', import.meta.url),
);
const designIrSchemaPath = fileURLToPath(
  new URL('../contracts/design-ir.schema.json', import.meta.url),
);
const fixtureRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function fingerprint(value, omittedKey) {
  const copy = structuredClone(value);
  if (omittedKey) delete copy[omittedKey];
  return createHash('sha256').update(canonicalJson(copy)).digest('hex');
}

function assertUnique(values, label) {
  if (new Set(values).size !== values.length) throw new Error(`${label} must be unique`);
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

function fieldValues(node) {
  return [...node.properties, ...node.semantics, ...node.state, ...node.modifiers.flatMap(
    (modifier) => modifier.arguments,
  )].map((field) => field.value);
}

function applyMutation(base, descriptor) {
  const mutated = structuredClone(base);
  if (descriptor.operation === 'remove-answer') {
    mutated.answers = mutated.answers.filter((answer) => answer.questionId !== descriptor.questionId);
  } else if (descriptor.operation === 'replace-field-value') {
    const answer = mutated.answers.find((candidate) => candidate.questionId === descriptor.questionId);
    const field = answer?.decision?.fields?.find((candidate) => candidate.name === descriptor.fieldName);
    if (!field) throw new Error(`${descriptor.operation}: mutation target is missing`);
    field.value = descriptor.value;
  } else if (descriptor.operation === 'replace-validation-fingerprint') {
    mutated.input.validationFingerprint = descriptor.value;
  } else {
    throw new Error(`Unsupported screenshot resolution mutation: ${descriptor.operation}`);
  }
  return mutated;
}

function sameRegion(left, right) {
  return ['x', 'y', 'width', 'height'].every((key) => left?.[key] === right?.[key]);
}

export async function verifyPhase5ScreenshotResolution() {
  const [
    contract,
    inferenceContract,
    inferenceRequest,
    inferenceResult,
    resolutionSchema,
    designIrSchema,
  ] = await Promise.all([
    readJson(contractPath),
    readJson(inferenceContractPath),
    readJson(inferenceRequestPath),
    readJson(inferenceResultPath),
    readJson(resolutionSchemaPath),
    readJson(designIrSchemaPath),
  ]);
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-screenshot-inference-resolution-v1' ||
    JSON.stringify(contract.requiresContracts) !== JSON.stringify([
      'design-ir-v1',
      'screenshot-design-inference-v1',
      'screenshot-inference-resolution-v1',
    ]) ||
    contract.activation?.tool !== 'resolve_screenshot_inference' ||
    contract.activation?.status !== 'implemented' ||
    contract.activation?.publicTool !== true ||
    contract.activation?.evidence !== 'static' ||
    contract.activation?.generationImplementation !== false ||
    contract.execution?.providerExecution !== false ||
    contract.execution?.networkAccess !== false ||
    contract.execution?.credentialsAccepted !== false ||
    contract.execution?.executableSourceAccepted !== false ||
    contract.execution?.resourceGuessing !== false ||
    !TOOL_NAMES.includes(contract.activation.tool) ||
    TOOL_DEFINITIONS[contract.activation.tool]?.defaultLimits?.maxInputBytes !== 2_000_000 ||
    TOOL_DEFINITIONS[contract.activation.tool]?.defaultLimits?.maxOutputBytes !== 2_000_000
  ) {
    throw new Error('Screenshot resolution activation or execution boundary changed');
  }
  if (
    contract.lineage?.acceptedInput !== 'validated-screenshot-inference-only' ||
    contract.lineage?.validationFingerprint !== 'exact' ||
    contract.lineage?.inferenceResultFingerprint !== 'exact' ||
    contract.lineage?.designIrFingerprint !== 'exact' ||
    contract.lineage?.authorizationBoundToValidationFingerprint !== true ||
    contract.lineage?.questionId !== 'exact-imported-question' ||
    contract.lineage?.questionNode !== 'exact-imported-node' ||
    contract.lineage?.questionRegion !== 'exact-imported-region' ||
    contract.lineage?.requestFingerprint !== 'sha256-canonical-json' ||
    contract.lineage?.resultFingerprint !== 'sha256-canonical-json-without-resultFingerprint'
  ) {
    throw new Error('Screenshot resolution lineage contract changed');
  }
  if (
    contract.answerPolicy?.allBlockingQuestionsRequired !== true ||
    contract.answerPolicy?.duplicateAnswers !== 'forbidden' ||
    contract.answerPolicy?.unknownQuestions !== 'forbidden' ||
    contract.answerPolicy?.requiredActionMustMatch !== true ||
    contract.answerPolicy?.content?.decisionKind !== 'set-fields' ||
    JSON.stringify(contract.answerPolicy?.content?.allowedCollections) !==
      JSON.stringify(['properties', 'state']) ||
    JSON.stringify(contract.answerPolicy?.content?.allowedValues) !==
      JSON.stringify(['binding', 'enum', 'literal']) ||
    contract.answerPolicy?.content?.expressions !== false ||
    contract.answerPolicy?.content?.resources !== false ||
    contract.answerPolicy?.behavior?.decisionKind !== 'set-events' ||
    JSON.stringify(contract.answerPolicy?.behavior?.allowedEvents) !==
      JSON.stringify(['click', 'focus-change', 'keyboard-action']) ||
    contract.answerPolicy?.behavior?.bindingOnly !== true ||
    contract.answerPolicy?.behavior?.callbackSource !== false ||
    contract.answerPolicy?.accessibility?.decisionKind !== 'accessibility-review' ||
    contract.answerPolicy?.accessibility?.allNodesCovered !== true ||
    contract.answerPolicy?.accessibility?.rolesExplicit !== true ||
    contract.answerPolicy?.accessibility?.labelSourceExplicit !== true ||
    contract.answerPolicy?.accessibility?.traversalOrderExplicit !== true ||
    contract.answerPolicy?.accessibility?.decorativeDecisionExplicit !== true
  ) {
    throw new Error('Screenshot resolution typed-answer policy changed');
  }
  const eligibility = contract.resolutionPolicy?.codeGenerationEligibility;
  if (
    contract.resolutionPolicy?.preserveScreenshotProvenance !== true ||
    contract.resolutionPolicy?.accessibilityReviewPersistedInDesignIr !== true ||
    contract.resolutionPolicy?.resolveOnlyQuestionBoundUnsupportedSemantics !== true ||
    contract.resolutionPolicy?.unansweredQuestionsRemainBlocking !== true ||
    contract.resolutionPolicy?.unresolvedUnsupportedSemanticsRemainBlocked !== true ||
    contract.resolutionPolicy?.placeholderBindingsRemainBlocked !== true ||
    eligibility?.remainingBlockingQuestions !== 0 ||
    eligibility?.remainingUnsupportedSemantics !== 0 ||
    eligibility?.placeholderBindings !== 0 ||
    eligibility?.schemaValidDesignIr !== true ||
    contract.resolutionPolicy?.compilationClaim !== false ||
    contract.resolutionPolicy?.renderClaim !== false ||
    contract.resolutionPolicy?.visualParityClaim !== false
  ) {
    throw new Error('Screenshot resolution code-generation gate changed');
  }
  if (
    contract.authorization?.mode !== 'human-resolution' ||
    contract.authorization?.reviewerIdentityRequired !== true ||
    contract.authorization?.reviewReceiptRequired !== true ||
    contract.authorization?.approvedPurpose !== 'resolve-screenshot-inference' ||
    contract.authorization?.sourceInspectionRequiredForBehavior !== true ||
    contract.authorization?.providerExecution !== false ||
    contract.authorization?.networkAccess !== false ||
    contract.authorization?.logs !== 'metadata-only' ||
    contract.limits?.maxAnswers !== 1000 ||
    contract.limits?.maxFieldDecisionsPerAnswer !== 16 ||
    contract.limits?.maxEventDecisionsPerAnswer !== 8 ||
    contract.limits?.maxAccessibilityNodes !== 1000
  ) {
    throw new Error('Screenshot resolution authorization or limits changed');
  }
  assertUnique(contract.diagnosticCodes, 'Screenshot resolution diagnostics');
  if (contract.diagnosticCodes.some((code) =>
    !/^VC-AI-SCREENSHOT-RESOLUTION-[A-Z0-9-]+$/u.test(code))) {
    throw new Error('Screenshot resolution diagnostic namespace changed');
  }

  const answerSchema = resolutionSchema.$defs?.request?.properties?.answers;
  const policySchema = resolutionSchema.$defs?.request?.properties?.policy?.properties;
  const safeValueSchema = resolutionSchema.$defs?.safeValue?.oneOf ?? [];
  if (
    answerSchema?.maxItems !== contract.limits.maxAnswers ||
    resolutionSchema.$defs?.contentAnswer?.properties?.decision?.properties?.fields?.maxItems !==
      contract.limits.maxFieldDecisionsPerAnswer ||
    resolutionSchema.$defs?.behaviorAnswer?.properties?.decision?.properties?.events?.maxItems !==
      contract.limits.maxEventDecisionsPerAnswer ||
    resolutionSchema.$defs?.accessibilityAnswer?.properties?.decision?.properties?.nodes?.maxItems !==
      contract.limits.maxAccessibilityNodes ||
    policySchema?.expressions?.const !== 'forbidden' ||
    policySchema?.resourceGuessing?.const !== 'forbidden' ||
    policySchema?.executableSource?.const !== false ||
    safeValueSchema.some((candidate) =>
      ['expression', 'resource'].includes(candidate.properties?.kind?.const))
  ) {
    throw new Error('Screenshot resolution schema and frozen safety policy diverged');
  }

  const golden = contract.supportedFixtures[0];
  const [request, result] = await Promise.all([
    readJson(`${fixtureRoot}${golden.request}`),
    readJson(`${fixtureRoot}${golden.result}`),
  ]);
  const requestViolations = validateSchemaValue(request, resolutionSchema);
  const resultViolations = validateSchemaValue(result, resolutionSchema);
  const designIrViolations = validateSchemaValue(result.designIr, designIrSchema);
  if (requestViolations.length || resultViolations.length || designIrViolations.length) {
    throw new Error('Screenshot resolution golden violates its frozen schema');
  }
  const requestFingerprint = fingerprint(request);
  const resultFingerprint = fingerprint(result, 'resultFingerprint');
  const resolvedDesignIrFingerprint = fingerprint(result.designIr);
  const inputDesignIrFingerprint = fingerprint(inferenceResult.designIr);
  const inferenceResultFingerprint = fingerprint(inferenceResult, 'resultFingerprint');
  const validatedInference = {
    schemaVersion: 1,
    kind: 'validated-screenshot-inference',
    status: inferenceResult.status,
    authorization: Object.fromEntries(Object.entries({
      mode: inferenceRequest.authorization.mode,
      providerId: inferenceRequest.authorization.providerId,
      approvedInputFingerprint: inferenceRequest.authorization.approvedInputFingerprint,
    }).filter(([, value]) => value !== undefined)),
    producer: inferenceResult.producer,
    fingerprints: {
      preprocessingRequest: inferenceRequest.source.preprocessingRequestFingerprint,
      preprocessingOutput: inferenceRequest.source.preprocessingOutputFingerprint,
      screenshot: inferenceRequest.screenshot.sha256,
      inferenceRequest: fingerprint(inferenceRequest),
      inferenceResult: inferenceResultFingerprint,
      designIr: inputDesignIrFingerprint,
    },
    designIr: inferenceResult.designIr,
    nodeEvidence: inferenceResult.nodeEvidence,
    unresolvedQuestions: inferenceResult.unresolvedQuestions,
    summary: inferenceResult.summary,
    inferenceDiagnostics: inferenceResult.diagnostics,
  };
  validatedInference.validationFingerprint = fingerprint(validatedInference);
  if (
    golden.status !== 'implemented' ||
    requestFingerprint !== golden.expectedRequestFingerprint ||
    requestFingerprint !== result.requestFingerprint ||
    resultFingerprint !== golden.expectedResultFingerprint ||
    resultFingerprint !== result.resultFingerprint ||
    inputDesignIrFingerprint !== golden.expectedInputDesignIrFingerprint ||
    inputDesignIrFingerprint !== request.input.designIrFingerprint ||
    resolvedDesignIrFingerprint !== golden.expectedResolvedDesignIrFingerprint ||
    resolvedDesignIrFingerprint !== result.designIrFingerprint ||
    inferenceResultFingerprint !== request.input.inferenceResultFingerprint ||
    inferenceResultFingerprint !== result.input.inferenceResultFingerprint ||
    request.input.validationFingerprint !==
      inferenceContract.validation.expectedValidationFingerprint ||
    request.authorization.approvedValidationFingerprint !== request.input.validationFingerprint ||
    JSON.stringify(result.input) !== JSON.stringify(request.input) ||
    JSON.stringify(result.authorization) !== JSON.stringify(request.authorization)
  ) {
    throw new Error('Screenshot resolution golden lineage or fingerprint changed');
  }
  const resolutionArguments = {validatedInference, resolutionRequest: request};
  const [firstResolution, secondResolution] = await Promise.all([
    resolveScreenshotInference(resolutionArguments, {requestId: 'phase5-resolution-first'}),
    resolveScreenshotInference(resolutionArguments, {requestId: 'phase5-resolution-second'}),
  ]);
  if (
    validatedInference.validationFingerprint !== request.input.validationFingerprint ||
    firstResolution.status !== 'success' ||
    secondResolution.status !== 'success' ||
    JSON.stringify(firstResolution.data) !== JSON.stringify(result) ||
    JSON.stringify(secondResolution.data) !== JSON.stringify(result)
  ) {
    throw new Error('Screenshot resolution adapter did not reproduce the exact result twice');
  }

  const questions = inferenceResult.unresolvedQuestions;
  const questionById = new Map(questions.map((question) => [question.id, question]));
  assertUnique(questions.map((question) => question.id), 'Imported screenshot questions');
  assertUnique(request.answers.map((answer) => answer.questionId), 'Screenshot resolution answers');
  if (
    request.answers.length !== golden.expectedAnsweredQuestions ||
    request.answers.length !== questions.filter((question) => question.blocking).length
  ) {
    throw new Error('Screenshot resolution answer coverage changed');
  }
  const inputNodes = collectNodes(inferenceResult.designIr.roots);
  const resolvedNodes = collectNodes(result.designIr.roots);
  const inputNodeById = new Map(inputNodes.map((node) => [node.id, node]));
  const resolvedNodeById = new Map(resolvedNodes.map((node) => [node.id, node]));
  for (const answer of request.answers) {
    const question = questionById.get(answer.questionId);
    if (
      !question ||
      answer.category !== question.category ||
      answer.requiredAction !== question.requiredAction ||
      answer.nodeId !== question.nodeId ||
      !sameRegion(answer.sourceRegion, question.sourceRegion) ||
      !request.policy.allowedDecisionKinds.includes(answer.decision.kind)
    ) {
      throw new Error(`${answer.questionId}: typed answer no longer matches its imported question`);
    }
    if (answer.decision.kind === 'set-fields' && answer.decision.fields.some((field) =>
      ['expression', 'resource'].includes(field.value.kind))) {
      throw new Error(`${answer.questionId}: typed fields contain executable or guessed content`);
    }
    if (answer.decision.kind === 'set-events' && answer.decision.events.some((event) =>
      event.status !== 'resolved' || !contract.answerPolicy.behavior.allowedEvents.includes(event.kind))) {
      throw new Error(`${answer.questionId}: behavior is not a typed caller binding`);
    }
    if (answer.decision.kind === 'accessibility-review') {
      assertUnique(answer.decision.nodes.map((node) => node.nodeId), 'Accessibility review nodes');
      assertUnique(answer.decision.nodes.map((node) => node.traversalIndex), 'Accessibility traversal');
      if (answer.decision.nodes.length !== inputNodes.length ||
          answer.decision.nodes.some((node) => !inputNodeById.has(node.nodeId))) {
        throw new Error('Accessibility review must cover every imported node exactly once');
      }
    }
  }

  if (
    inputNodes.length !== resolvedNodes.length ||
    inputNodes.some((node) => {
      const resolved = resolvedNodeById.get(node.id);
      return !resolved || resolved.kind !== node.kind ||
        JSON.stringify(resolved.provenance) !== JSON.stringify(node.provenance);
    }) ||
    result.designIr.documentId !== inferenceResult.designIr.documentId ||
    result.designIr.source.fingerprint !== inferenceResult.designIr.source.fingerprint ||
    result.designIr.source.identity !== inferenceResult.designIr.source.identity
  ) {
    throw new Error('Screenshot resolution changed inferred structure or pixel provenance');
  }
  const placeholders = resolvedNodes.flatMap(fieldValues).filter((value) =>
    value.kind === 'binding' && value.status === 'placeholder').length;
  const events = resolvedNodes.flatMap((node) => node.events).filter((event) =>
    event.status === 'resolved').length;
  const semanticRoles = resolvedNodes.flatMap((node) => node.semantics).filter((field) =>
    field.name === 'role' && field.value.kind === 'enum').length;
  const accessibilityAnswer = request.answers.find((answer) =>
    answer.decision.kind === 'accessibility-review');
  for (const decision of accessibilityAnswer.decision.nodes) {
    const semantics = new Map(
      resolvedNodeById.get(decision.nodeId).semantics.map((field) => [field.name, field.value]),
    );
    const role = semantics.get('role');
    const labelSource = semantics.get('accessibilityLabelSource');
    const traversalIndex = semantics.get('traversalIndex');
    const decorative = semantics.get('decorative');
    if (
      (decision.role === 'none' ? role !== undefined :
        role?.kind !== 'enum' || role.type !== 'semantic-role' || role.value !== decision.role) ||
      labelSource?.kind !== 'enum' ||
      labelSource.type !== 'accessibility-label-source' ||
      labelSource.value !== decision.labelSource ||
      traversalIndex?.kind !== 'literal' ||
      traversalIndex.value !== decision.traversalIndex ||
      decorative?.kind !== 'literal' ||
      decorative.value !== decision.decorative
    ) {
      throw new Error(`${decision.nodeId}: accessibility review was not persisted in Design IR`);
    }
  }
  const accessibilityFields = resolvedNodes.flatMap((node) => node.semantics).filter((field) =>
    ['role', 'accessibilityLabelSource', 'traversalIndex', 'decorative'].includes(field.name)
  ).length;
  if (
    result.resolutionRecords.length !== golden.expectedResolutionRecords ||
    result.summary.answeredQuestions !== golden.expectedAnsweredQuestions ||
    result.summary.resolvedUnsupportedSemantics !== golden.expectedResolvedUnsupportedSemantics ||
    result.summary.remainingQuestions !== golden.expectedRemainingQuestions ||
    result.remainingQuestionIds.length !== golden.expectedRemainingQuestions ||
    result.summary.remainingUnsupportedSemantics !== golden.expectedRemainingUnsupportedSemantics ||
    result.designIr.unsupported.length !== golden.expectedRemainingUnsupportedSemantics ||
    placeholders !== golden.expectedPlaceholderBindings ||
    result.summary.placeholderBindings !== placeholders ||
    events !== golden.expectedResolvedEvents ||
    semanticRoles !== golden.expectedResolvedSemanticRoles ||
    accessibilityFields !== golden.expectedResolvedAccessibilityFields ||
    result.summary.codeGenerationAllowed !== golden.expectedCodeGenerationAllowed ||
    result.status !== 'resolved'
  ) {
    throw new Error('Screenshot resolution result or code-generation eligibility changed');
  }
  assertUnique(result.resolutionRecords.map((record) => record.questionId), 'Resolution records');
  if (result.resolutionRecords.some((record) =>
    !questionById.has(record.questionId) ||
    record.reviewReceipt !== request.authorization.reviewReceipt)) {
    throw new Error('Screenshot resolution records lost question or review provenance');
  }

  for (const fixture of contract.unsupportedFixtures) {
    const descriptor = await readJson(`${fixtureRoot}${fixture.mutation}`);
    const base = await readJson(`${fixtureRoot}${descriptor.baseFixture}`);
    const mutated = applyMutation(base, descriptor);
    const violations = validateSchemaValue(mutated, resolutionSchema);
    if (
      descriptor.expectedSchemaValid !== fixture.schemaValid ||
      descriptor.expectedDiagnostic !== fixture.diagnosticCodes[0] ||
      (fixture.schemaValid && violations.length > 0) ||
      (!fixture.schemaValid && violations.length === 0)
    ) {
      throw new Error(`${fixture.mutation}: fail-closed denominator changed`);
    }
    const provesCoverage = descriptor.operation === 'remove-answer' &&
      mutated.answers.length < questions.filter((question) => question.blocking).length;
    const provesExecutable = descriptor.operation === 'replace-field-value' &&
      descriptor.value.kind === 'expression';
    const provesLineage = descriptor.operation === 'replace-validation-fingerprint' &&
      mutated.input.validationFingerprint !== request.input.validationFingerprint;
    if (
      (fixture.diagnosticCodes.includes('VC-AI-SCREENSHOT-RESOLUTION-COVERAGE-INCOMPLETE') &&
        !provesCoverage) ||
      (fixture.diagnosticCodes.includes('VC-AI-SCREENSHOT-RESOLUTION-EXECUTABLE-DENIED') &&
        !provesExecutable) ||
      (fixture.diagnosticCodes.includes('VC-AI-SCREENSHOT-RESOLUTION-LINEAGE-MISMATCH') &&
        !provesLineage)
    ) {
      throw new Error(`${fixture.mutation}: failure reason changed`);
    }
    const rejected = await resolveScreenshotInference({
      validatedInference,
      resolutionRequest: mutated,
    }, {requestId: `phase5-${descriptor.operation}`});
    if (
      rejected.status !== 'invalid' ||
      rejected.diagnostics?.[0]?.code !== fixture.diagnosticCodes[0]
    ) {
      throw new Error(`${fixture.mutation}: adapter did not return the frozen failure`);
    }
  }

  return {
    supportedGoldens: contract.supportedFixtures.length,
    failClosedDenominators: contract.unsupportedFixtures.length,
    answers: request.answers.length,
    resolvedUnsupportedSemantics: result.summary.resolvedUnsupportedSemantics,
    resolvedEvents: events,
    resolvedSemanticRoles: semanticRoles,
    resolvedAccessibilityFields: accessibilityFields,
    remainingQuestions: result.summary.remainingQuestions,
    remainingUnsupportedSemantics: result.summary.remainingUnsupportedSemantics,
    placeholders,
    requestFingerprint,
    resultFingerprint,
    resolvedDesignIrFingerprint,
    codeGenerationAllowed: result.summary.codeGenerationAllowed,
    deterministicResolutions: 2,
    providerExecutions: 0,
    networkRequests: 0,
  };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  try {
    const result = await verifyPhase5ScreenshotResolution();
    process.stdout.write(
      `Verified Phase 5 screenshot inference resolution contract: ${result.supportedGoldens}/1 golden, ` +
      `${result.answers}/6 typed answers, ${result.resolvedUnsupportedSemantics}/6 resolved unsupported semantics, ` +
      `${result.resolvedEvents}/2 event bindings, ${result.resolvedAccessibilityFields}/14 accessibility fields, ` +
      `${result.failClosedDenominators}/3 fail-closed denominators, ${result.deterministicResolutions}/2 deterministic resolutions, ` +
      `${result.remainingQuestions} questions, ` +
      `${result.remainingUnsupportedSemantics} unsupported semantics, ${result.placeholders} placeholders, ` +
      `code generation ${result.codeGenerationAllowed}, ${result.providerExecutions} provider executions, and ` +
      `${result.networkRequests} network requests; resolved Design IR ${result.resolvedDesignIrFingerprint}, ` +
      `result ${result.resultFingerprint}.\n`,
    );
  } catch (error) {
    process.stderr.write(`Phase 5 screenshot resolution verification failed: ${error.message}\n`);
    process.exitCode = 1;
  }
}
