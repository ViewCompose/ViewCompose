#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {compileKotlin} from './compiler-adapter.mjs';
import {generateScreenshotKotlin} from './screenshot-design-ir-to-kotlin.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {canonicalJson} from './screenshot-contract.mjs';
import {TOOL_DEFINITIONS, TOOL_NAMES} from './tool-catalog.mjs';

const fixtureRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));
const contractPath = `${fixtureRoot}screenshot-kotlin-generation-contract.json`;
const schemaPath = fileURLToPath(
  new URL('../contracts/screenshot-kotlin-generation.schema.json', import.meta.url),
);
const designIrSchemaPath = fileURLToPath(
  new URL('../contracts/design-ir.schema.json', import.meta.url),
);

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function fingerprintJson(value, omittedKey) {
  const copy = structuredClone(value);
  if (omittedKey) delete copy[omittedKey];
  return createHash('sha256').update(canonicalJson(copy)).digest('hex');
}

function fingerprintBytes(value) {
  return createHash('sha256').update(value).digest('hex');
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

function fieldMap(fields) {
  return new Map(fields.map((field) => [field.name, field.value]));
}

function assertContract(contract) {
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-screenshot-kotlin-generation-v1' ||
    JSON.stringify(contract.requiresContracts) !== JSON.stringify([
      'design-ir-v1',
      'screenshot-inference-resolution-v1',
      'screenshot-kotlin-generation-v1',
    ]) ||
    contract.activation?.tool !== 'generate_screenshot_viewcompose' ||
    contract.activation?.status !== 'implemented' ||
    contract.activation?.publicTool !== true ||
    contract.activation?.implementation !== true ||
    contract.activation?.evidence !== 'compiled-golden' ||
    !TOOL_NAMES.includes(contract.activation.tool) ||
    TOOL_DEFINITIONS[contract.activation.tool]?.defaultLimits?.maxInputBytes !== 2_000_000 ||
    TOOL_DEFINITIONS[contract.activation.tool]?.defaultLimits?.maxOutputBytes !== 2_000_000
  ) {
    throw new Error('Screenshot Kotlin generation activation changed');
  }
  if (
    contract.lineage?.acceptedInput !== 'resolved-screenshot-inference-only' ||
    contract.lineage?.resolutionStatus !== 'resolved' ||
    contract.lineage?.codeGenerationAllowed !== true ||
    contract.lineage?.resolutionResultFingerprint !== 'exact' ||
    contract.lineage?.resolvedDesignIrFingerprint !== 'exact' ||
    contract.lineage?.requestFingerprint !== 'sha256-canonical-json' ||
    contract.lineage?.kotlinFingerprint !== 'sha256-source-bytes' ||
    contract.lineage?.reportFingerprint !==
      'sha256-canonical-json-without-reportFingerprint'
  ) {
    throw new Error('Screenshot Kotlin generation lineage changed');
  }
  if (
    contract.mapping?.sourceKind !== 'screenshot' ||
    contract.mapping?.rootCount !== 1 ||
    JSON.stringify(contract.mapping?.supportedNodeKinds) !==
      JSON.stringify(['button', 'column', 'text', 'text-field']) ||
    contract.mapping?.expressions !== false ||
    contract.mapping?.resources !== false ||
    contract.mapping?.callbackSource !== false ||
    contract.mapping?.placeholderBindings !== false ||
    contract.mapping?.state?.['text-field.text']?.parameterType !== 'TextFieldState' ||
    contract.mapping?.events?.['button.click']?.parameterType !== '() -> Unit' ||
    contract.mapping?.events?.['text-field.focus-change']?.parameterType !==
      '(Boolean) -> Unit' ||
    contract.mapping?.events?.['text-field.keyboard-action']?.parameterType !==
      '(TextFieldImeAction) -> Boolean'
  ) {
    throw new Error('Screenshot Kotlin state, event, or safe-source mapping changed');
  }
  if (
    contract.accessibility?.reviewReceiptRequired !== true ||
    contract.accessibility?.allNodesReported !== true ||
    contract.accessibility?.traversal?.publicModifierAvailable !== false ||
    contract.accessibility?.traversal?.emission !== 'hierarchy-order' ||
    contract.accessibility?.traversal?.exactAscendingOrderRequired !== true ||
    contract.accessibility?.traversal?.callSiteReviewRequired !== true ||
    contract.accessibility?.decorative?.false !== 'default-visible' ||
    contract.accessibility?.decorative?.true !== 'unsupported-without-image-subset'
  ) {
    throw new Error('Screenshot Kotlin accessibility honesty changed');
  }
  if (
    contract.compiler?.required !== true ||
    contract.compiler?.lane !== 'jdk21-kotlin-2.3.10-source' ||
    JSON.stringify(contract.compiler?.artifactIds) !==
      JSON.stringify(['viewcompose-ui-foundation']) ||
    JSON.stringify(contract.compiler?.capabilityIds) !==
      JSON.stringify(['foundation.components']) ||
    contract.compiler?.evidenceLevel !== 'compiled' ||
    contract.compiler?.projectBuildExecution !== false ||
    contract.compiler?.networkAccess !== false ||
    contract.claims?.deterministicGeneration !== true ||
    contract.claims?.goldenCompilation !== true ||
    contract.claims?.runtimeRendering !== false ||
    contract.claims?.visualParity !== false
  ) {
    throw new Error('Screenshot Kotlin compiler or evidence boundary changed');
  }
  if (
    contract.limits?.maxNodes !== 1000 ||
    contract.limits?.maxBindings !== 1000 ||
    contract.limits?.maxGeneratedBytes !== 262144 ||
    new Set(contract.diagnosticCodes).size !== contract.diagnosticCodes.length ||
    contract.diagnosticCodes.some((code) =>
      !/^VC-AI-SCREENSHOT-GENERATION-[A-Z0-9-]+$/u.test(code))
  ) {
    throw new Error('Screenshot Kotlin limits or diagnostics changed');
  }
}

function assertResolvedMapping(resolution, request, report, kotlin, expected) {
  if (
    resolution.kind !== 'result' ||
    resolution.status !== 'resolved' ||
    resolution.summary?.codeGenerationAllowed !== true ||
    resolution.summary?.remainingQuestions !== 0 ||
    resolution.summary?.remainingUnsupportedSemantics !== 0 ||
    resolution.summary?.placeholderBindings !== 0 ||
    resolution.designIr?.source?.kind !== 'screenshot' ||
    resolution.designIr?.roots?.length !== 1 ||
    resolution.designIr?.unsupported?.length !== 0
  ) {
    throw new Error('Screenshot Kotlin golden is no longer generation-eligible');
  }
  const resolutionFingerprint = fingerprintJson(resolution, 'resultFingerprint');
  const designIrFingerprint = fingerprintJson(resolution.designIr);
  if (
    resolution.resultFingerprint !== resolutionFingerprint ||
    resolution.designIrFingerprint !== designIrFingerprint ||
    request.input.resolutionResultFingerprint !== resolutionFingerprint ||
    request.input.resolvedDesignIrFingerprint !== designIrFingerprint ||
    report.input.resolutionResultFingerprint !== resolutionFingerprint ||
    report.input.resolvedDesignIrFingerprint !== designIrFingerprint
  ) {
    throw new Error('Screenshot Kotlin request or report lost exact resolution lineage');
  }

  const nodes = collectNodes(resolution.designIr.roots);
  const nodeById = new Map(nodes.map((node) => [node.id, node]));
  const reportNodeById = new Map(report.accessibility.nodes.map((node) => [node.nodeId, node]));
  if (
    nodes.length !== expected.expectedNodes ||
    report.bindings.states.length !== expected.expectedStateBindings ||
    report.bindings.events.length !== expected.expectedEventBindings ||
    report.accessibility.nodes.length !== expected.expectedAccessibilityRecords ||
    reportNodeById.size !== nodes.length
  ) {
    throw new Error('Screenshot Kotlin golden coverage changed');
  }
  const ordered = [...nodes]
    .sort((left, right) =>
      fieldMap(left.semantics).get('traversalIndex').value -
        fieldMap(right.semantics).get('traversalIndex').value)
    .map((node) => node.id);
  if (JSON.stringify(ordered) !== JSON.stringify(report.accessibility.traversal.orderedNodeIds)) {
    throw new Error('Screenshot Kotlin report lost resolved accessibility traversal');
  }
  for (const node of nodes) {
    const semantics = fieldMap(node.semantics);
    const record = reportNodeById.get(node.id);
    const role = semantics.get('role')?.value ?? 'none';
    if (
      record.role !== role ||
      record.labelSource !== semantics.get('accessibilityLabelSource')?.value ||
      record.traversalIndex !== semantics.get('traversalIndex')?.value ||
      record.decorative !== semantics.get('decorative')?.value ||
      record.emission?.traversal !== 'hierarchy-order'
    ) {
      throw new Error(`${node.id}: screenshot accessibility record changed`);
    }
  }
  const title = nodeById.get('wireframe-title');
  const field = nodeById.get('wireframe-field');
  const button = nodeById.get('wireframe-button');
  if (
    fieldMap(title.properties).get('text')?.value !== 'Welcome' ||
    fieldMap(field.properties).get('hint')?.value !== 'Email address' ||
    fieldMap(field.properties).get('inputType')?.value !== 'textEmailAddress' ||
    fieldMap(field.state).get('text')?.name !== 'emailState' ||
    field.events?.[0]?.binding !== 'onEmailSubmit' ||
    fieldMap(button.properties).get('text')?.value !== 'Continue' ||
    button.events?.[0]?.binding !== 'onContinue'
  ) {
    throw new Error('Screenshot Kotlin golden no longer maps the resolved content and behavior');
  }
  const requiredSource = [
    'fun UiTreeBuilder.ScreenshotWireframeView(',
    'emailState: TextFieldState,',
    'onEmailSubmit: (TextFieldImeAction) -> Boolean,',
    'onContinue: () -> Unit,',
    'placeholder = "Email address",',
    'inputProfile = TextFieldInputProfile.Email,',
    'onKeyboardAction = onEmailSubmit,',
    'onClick = onContinue,',
  ];
  if (
    requiredSource.some((fragment) => !kotlin.includes(fragment)) ||
    /Runtime\.getRuntime|ProcessBuilder|java\.net|kotlin\.reflect|onEmailSubmit\s*=\s*\{/u.test(kotlin) ||
    Buffer.byteLength(kotlin) > 262144
  ) {
    throw new Error('Screenshot Kotlin golden lost a typed binding or safe-source boundary');
  }
}

async function assertUnsupportedFixtures(contract, descriptors, resolution, request) {
  for (let index = 0; index < contract.unsupportedFixtures.length; index += 1) {
    const fixture = contract.unsupportedFixtures[index];
    const descriptor = descriptors[index];
    if (
      descriptor.expectedDiagnostic !== fixture.diagnosticCodes[0] ||
      !contract.diagnosticCodes.includes(descriptor.expectedDiagnostic)
    ) {
      throw new Error(`${fixture.mutation}: diagnostic freeze changed`);
    }
    const provesNotEligible = descriptor.operation === 'replace-code-generation-eligibility' &&
      descriptor.value === false;
    const provesLineage = descriptor.operation === 'replace-resolved-design-ir-fingerprint' &&
      /^[a-f0-9]{64}$/u.test(descriptor.value);
    const provesUnsupported = descriptor.operation === 'replace-event-kind' &&
      descriptor.value === 'long-click';
    if (
      (descriptor.expectedDiagnostic.endsWith('NOT-ELIGIBLE') && !provesNotEligible) ||
      (descriptor.expectedDiagnostic.endsWith('LINEAGE-MISMATCH') && !provesLineage) ||
      (descriptor.expectedDiagnostic.endsWith('UNSUPPORTED') && !provesUnsupported)
    ) {
      throw new Error(`${fixture.mutation}: fail-closed reason changed`);
    }
    const arguments_ = {
      resolutionResult: structuredClone(resolution),
      generationRequest: structuredClone(request),
    };
    if (provesNotEligible) {
      arguments_.resolutionResult.summary.codeGenerationAllowed = false;
      arguments_.resolutionResult.resultFingerprint = fingerprintJson(
        arguments_.resolutionResult,
        'resultFingerprint',
      );
      arguments_.generationRequest.input.resolutionResultFingerprint =
        arguments_.resolutionResult.resultFingerprint;
    } else if (provesLineage) {
      arguments_.generationRequest.input.resolvedDesignIrFingerprint = descriptor.value;
    } else {
      const node = collectNodes(arguments_.resolutionResult.designIr.roots).find((candidate) =>
        candidate.id === descriptor.nodeId);
      const event = node.events.find((candidate) => candidate.kind === descriptor.from);
      event.kind = descriptor.value;
      arguments_.resolutionResult.designIrFingerprint = fingerprintJson(
        arguments_.resolutionResult.designIr,
      );
      arguments_.resolutionResult.resultFingerprint = fingerprintJson(
        arguments_.resolutionResult,
        'resultFingerprint',
      );
      arguments_.generationRequest.input.resolvedDesignIrFingerprint =
        arguments_.resolutionResult.designIrFingerprint;
      arguments_.generationRequest.input.resolutionResultFingerprint =
        arguments_.resolutionResult.resultFingerprint;
    }
    const rejected = await generateScreenshotKotlin(arguments_);
    const expectedStatus = descriptor.expectedDiagnostic.endsWith('UNSUPPORTED')
      ? 'unsupported'
      : 'invalid';
    if (
      rejected.status !== expectedStatus ||
      rejected.diagnostics?.[0]?.code !== descriptor.expectedDiagnostic
    ) {
      throw new Error(`${fixture.mutation}: generator did not return the frozen failure`);
    }
  }
}

export async function verifyPhase5ScreenshotGeneration({compileGolden = true, compile = compileKotlin} = {}) {
  const [contract, schema, designIrSchema] = await Promise.all([
    readJson(contractPath),
    readJson(schemaPath),
    readJson(designIrSchemaPath),
  ]);
  assertContract(contract);
  const fixture = contract.supportedFixtures[0];
  const [resolution, request, report, kotlin, ...unsupportedDescriptors] = await Promise.all([
    readJson(`${fixtureRoot}${fixture.resolutionResult}`),
    readJson(`${fixtureRoot}${fixture.request}`),
    readJson(`${fixtureRoot}${fixture.goldenReport}`),
    readFile(`${fixtureRoot}${fixture.goldenKotlin}`, 'utf8'),
    ...contract.unsupportedFixtures.map((entry) => readJson(`${fixtureRoot}${entry.mutation}`)),
  ]);
  const requestViolations = validateSchemaValue(request, schema);
  const reportViolations = validateSchemaValue(report, schema);
  const designIrViolations = validateSchemaValue(resolution.designIr, designIrSchema);
  if (requestViolations.length || reportViolations.length || designIrViolations.length) {
    throw new Error(
      `Screenshot Kotlin fixture violates schema: ${[
        ...requestViolations,
        ...reportViolations,
        ...designIrViolations,
      ].slice(0, 3).join('; ')}`,
    );
  }
  const requestFingerprint = fingerprintJson(request);
  const kotlinFingerprint = fingerprintBytes(kotlin);
  const reportFingerprint = fingerprintJson(report, 'reportFingerprint');
  if (
    requestFingerprint !== fixture.expectedRequestFingerprint ||
    requestFingerprint !== report.requestFingerprint ||
    kotlinFingerprint !== fixture.expectedKotlinFingerprint ||
    kotlinFingerprint !== report.kotlinFingerprint ||
    reportFingerprint !== fixture.expectedReportFingerprint ||
    reportFingerprint !== report.reportFingerprint
  ) {
    throw new Error('Screenshot Kotlin golden fingerprint changed');
  }
  assertResolvedMapping(resolution, request, report, kotlin, fixture);
  const [first, second] = await Promise.all([
    generateScreenshotKotlin({resolutionResult: resolution, generationRequest: request}),
    generateScreenshotKotlin({resolutionResult: resolution, generationRequest: request}),
  ]);
  if (
    first.status !== 'success' ||
    second.status !== 'success' ||
    first.kotlin !== kotlin ||
    second.kotlin !== kotlin ||
    JSON.stringify(first.report) !== JSON.stringify(report) ||
    JSON.stringify(second.report) !== JSON.stringify(report)
  ) {
    throw new Error('Screenshot Kotlin generator did not reproduce the frozen source and report');
  }
  await assertUnsupportedFixtures(contract, unsupportedDescriptors, resolution, request);

  let compilationFingerprint = null;
  if (compileGolden) {
    const compilation = await compile({
      source: first.kotlin,
      path: `generated/viewcompose/${report.target.functionName}.kt`,
      artifactIds: contract.compiler.artifactIds,
      capabilityIds: contract.compiler.capabilityIds,
      requestId: 'screenshot-generation-golden-compile',
    });
    if (compilation.status !== 'success' || compilation.evidence?.level !== 'compiled') {
      const codes = compilation.diagnostics?.map((item) => item.code).join(', ') ?? 'none';
      throw new Error(`Screenshot Kotlin golden did not compile (${codes})`);
    }
    compilationFingerprint = compilation.evidence.outputFingerprint;
  }
  return {
    supportedGoldens: contract.supportedFixtures.length,
    failClosedDenominators: contract.unsupportedFixtures.length,
    nodes: fixture.expectedNodes,
    stateBindings: fixture.expectedStateBindings,
    eventBindings: fixture.expectedEventBindings,
    accessibilityRecords: fixture.expectedAccessibilityRecords,
    requestFingerprint,
    kotlinFingerprint,
    reportFingerprint,
    compilationFingerprint,
    compiled: compileGolden ? 1 : 0,
    deterministicGenerations: 2,
    providerExecutions: 0,
    networkRequests: 0,
  };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  try {
    const result = await verifyPhase5ScreenshotGeneration();
    process.stdout.write(
      `Verified Phase 5 screenshot Kotlin generation contract: ${result.supportedGoldens}/1 golden, ` +
        `${result.nodes}/4 nodes, ${result.stateBindings}/1 state binding, ` +
        `${result.eventBindings}/2 event bindings, ${result.accessibilityRecords}/4 accessibility records, ` +
        `${result.failClosedDenominators}/3 fail-closed denominators, ` +
        `${result.deterministicGenerations}/2 deterministic generations, ` +
        `${result.compiled}/1 hermetic compile, ` +
        `${result.providerExecutions} provider executions, and ${result.networkRequests} network requests; ` +
        `Kotlin ${result.kotlinFingerprint}, report ${result.reportFingerprint}, ` +
        `classes ${result.compilationFingerprint}.\n`,
    );
  } catch (error) {
    process.stderr.write(`Phase 5 screenshot Kotlin generation verification failed: ${error.message}\n`);
    process.exitCode = 1;
  }
}
