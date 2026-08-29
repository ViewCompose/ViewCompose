#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {validateSchemaValue} from './schema-validator.mjs';
import {canonicalJson} from './screenshot-contract.mjs';

const fixtureRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));
const contractPath = `${fixtureRoot}screenshot-generated-preview-contract.json`;
const requestSchemaPath = fileURLToPath(
  new URL('../contracts/generated-preview-request.schema.json', import.meta.url),
);

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function declaredIdentity(binding) {
  return `${binding.parameter}\0${binding.source}\0${binding.type}`;
}

function suppliedType(binding) {
  return {
    'text-field-state': 'TextFieldState',
    'unit-callback': '() -> Unit',
    'boolean-callback': '(Boolean) -> Unit',
    'ime-action-callback': '(TextFieldImeAction) -> Boolean',
  }[binding.kind];
}

function suppliedIdentity(binding) {
  return `${binding.parameter}\0${binding.source}\0${suppliedType(binding)}`;
}

function assertContract(contract) {
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-screenshot-generated-preview-v1' ||
    JSON.stringify(contract.requiresContracts) !== JSON.stringify([
      'viewcompose-screenshot-kotlin-generation-v1',
      'viewcompose-generated-preview-v1',
      'generated-preview-request-v1',
    ]) ||
    contract.activation?.tool !== 'generate_screenshot_viewcompose' ||
    contract.activation?.status !== 'contract-frozen' ||
    contract.activation?.publicRenderMode !== false ||
    contract.activation?.implementation !== false ||
    contract.activation?.evidence !== 'static-golden'
  ) {
    throw new Error('Screenshot generated Preview activation boundary changed');
  }
  if (
    contract.profile?.sourceKind !== 'screenshot' ||
    contract.profile?.targetId !== 'tools.ai.GeneratedScreenshotPreview' ||
    contract.profile?.functionName !== 'GeneratedScreenshotPreview' ||
    contract.profile?.annotationNamePrefix !== 'Generated Screenshot · ' ||
    contract.profile?.annotationGroup !== 'AI/Screenshot' ||
    contract.profile?.ownerClassName !== 'generated.viewcompose.GeneratedPreviewKt'
  ) {
    throw new Error('Screenshot generated Preview profile changed');
  }
  if (
    contract.bindings?.exactOrderRequired !== true ||
    contract.bindings?.exactParameterRequired !== true ||
    contract.bindings?.exactSourceRequired !== true ||
    contract.bindings?.exactTypeRequired !== true ||
    contract.bindings?.callbackSourceAllowed !== false ||
    contract.bindings?.allowed?.['TextFieldState']?.kind !== 'text-field-state' ||
    contract.bindings?.allowed?.['() -> Unit']?.kind !== 'unit-callback' ||
    contract.bindings?.allowed?.['(Boolean) -> Unit']?.kind !== 'boolean-callback' ||
    contract.bindings?.allowed?.['(TextFieldImeAction) -> Boolean']?.kind !==
      'ime-action-callback'
  ) {
    throw new Error('Screenshot generated Preview safe-binding contract changed');
  }
  if (
    Object.values(contract.execution ?? {}).some((value, index) =>
      index === 0 ? value !== true : value !== false) ||
    contract.claims?.deterministicWrapper !== true ||
    contract.claims?.safeBindingContract !== true ||
    contract.claims?.wrapperCompilation !== false ||
    contract.claims?.runtimeRendering !== false ||
    contract.claims?.semanticComparison !== false ||
    contract.claims?.visualParity !== false ||
    contract.limits?.maxBindings !== 64 ||
    contract.limits?.maxGeneratedKotlinBytes !== 1048576 ||
    contract.limits?.maxWrapperBytes !== 262144
  ) {
    throw new Error('Screenshot generated Preview execution or evidence boundary changed');
  }
  if (
    new Set(contract.diagnosticCodes).size !== contract.diagnosticCodes.length ||
    contract.diagnosticCodes.some((code) => !/^VC-AI-PREVIEW-[A-Z0-9-]+$/u.test(code))
  ) {
    throw new Error('Screenshot generated Preview diagnostic contract changed');
  }
}

function applyMutation(request, descriptor) {
  const mutated = structuredClone(request);
  const index = mutated.bindings.findIndex((binding) =>
    binding.parameter === descriptor.parameter);
  if (index < 0) throw new Error(`${descriptor.parameter}: mutation target is missing`);
  if (descriptor.operation === 'add-callback-source') {
    mutated.bindings[index].value = descriptor.value;
  } else if (descriptor.operation === 'remove-binding') {
    mutated.bindings.splice(index, 1);
  } else if (descriptor.operation === 'replace-binding-kind') {
    const current = mutated.bindings[index];
    mutated.bindings[index] = {
      kind: descriptor.value.kind,
      parameter: current.parameter,
      source: current.source,
      behavior: descriptor.value.behavior,
    };
  } else {
    throw new Error(`${descriptor.operation}: unknown screenshot Preview mutation`);
  }
  return mutated;
}

function inferDiagnostic(request, schema) {
  if (validateSchemaValue(request, schema).length > 0) {
    return 'VC-AI-PREVIEW-BINDING-VALUE-INVALID';
  }
  const declared = request.generatedSource.declaredBindings;
  const supplied = request.bindings;
  const suppliedParameters = new Set(supplied.map((binding) => binding.parameter));
  if (declared.some((binding) => !suppliedParameters.has(binding.parameter))) {
    return 'VC-AI-PREVIEW-BINDING-MISSING';
  }
  if (
    JSON.stringify(declared.map(declaredIdentity)) !==
      JSON.stringify(supplied.map(suppliedIdentity))
  ) {
    return 'VC-AI-PREVIEW-GENERATED-SOURCE-MISMATCH';
  }
  return null;
}

export async function verifyPhase5ScreenshotRender() {
  const [contract, schema] = await Promise.all([
    readJson(contractPath),
    readJson(requestSchemaPath),
  ]);
  assertContract(contract);
  const fixture = contract.supportedFixtures[0];
  const [resolution, generationRequest, generatedKotlin, generationReport, request, wrapper] =
    await Promise.all([
      readJson(`${fixtureRoot}${fixture.resolutionResult}`),
      readJson(`${fixtureRoot}${fixture.generationRequest}`),
      readFile(`${fixtureRoot}${fixture.generatedKotlin}`, 'utf8'),
      readJson(`${fixtureRoot}${fixture.generationReport}`),
      readJson(`${fixtureRoot}${fixture.previewRequest}`),
      readFile(`${fixtureRoot}${fixture.previewWrapper}`, 'utf8'),
    ]);
  const violations = validateSchemaValue(request, schema);
  if (violations.length > 0) {
    throw new Error(`Screenshot generated Preview request violates schema: ${violations[0]}`);
  }
  const declared = request.generatedSource.declaredBindings;
  const supplied = request.bindings;
  if (
    fixture.status !== 'contract-frozen' ||
    request.generatedSource.sourceKind !== 'screenshot' ||
    request.generatedSource.functionName !== generationReport.target.functionName ||
    request.generatedSource.kotlinFingerprint !== sha256(generatedKotlin) ||
    request.generatedSource.kotlinFingerprint !== contract.lineage.generatedKotlinFingerprint ||
    resolution.resultFingerprint !== contract.lineage.resolutionResultFingerprint ||
    resolution.designIrFingerprint !== contract.lineage.resolvedDesignIrFingerprint ||
    generationReport.requestFingerprint !== contract.lineage.generationRequestFingerprint ||
    generationReport.reportFingerprint !== contract.lineage.generationReportFingerprint ||
    declared.length !== fixture.expectedBindings ||
    supplied.length !== fixture.expectedBindings ||
    supplied.filter((binding) => binding.kind.endsWith('-callback')).length !==
      fixture.expectedCallbackBindings ||
    JSON.stringify(declared.map(declaredIdentity)) !==
      JSON.stringify(supplied.map(suppliedIdentity)) ||
    supplied.some((binding) => Object.hasOwn(binding, 'value'))
  ) {
    throw new Error('Screenshot generated Preview lineage or exact binding mapping changed');
  }
  const generationRequestFingerprint = sha256(canonicalJson(generationRequest));
  const previewRequestFingerprint = sha256(JSON.stringify(request));
  const previewWrapperFingerprint = sha256(wrapper);
  const requiredWrapper = [
    'name = "Generated Screenshot · ScreenshotWireframeView"',
    'group = "AI/Screenshot"',
    'fun UiTreeBuilder.GeneratedScreenshotPreview()',
    'emailState = TextFieldState()',
    'onEmailSubmit = { _ -> false }',
    'onContinue = { }',
  ];
  if (
    generationRequestFingerprint !== contract.lineage.generationRequestFingerprint ||
    previewRequestFingerprint !== contract.lineage.previewRequestFingerprint ||
    previewWrapperFingerprint !== contract.lineage.previewWrapperFingerprint ||
    Buffer.byteLength(generatedKotlin) > contract.limits.maxGeneratedKotlinBytes ||
    Buffer.byteLength(wrapper) > contract.limits.maxWrapperBytes ||
    requiredWrapper.some((fragment) => !wrapper.includes(fragment)) ||
    /Runtime\.getRuntime|ProcessBuilder|java\.net|kotlin\.reflect|navigate\(\)/u.test(wrapper) ||
    !wrapper.endsWith('\n')
  ) {
    throw new Error('Screenshot generated Preview deterministic wrapper golden changed');
  }

  let blocked = 0;
  for (const fixtureEntry of contract.unsupportedFixtures) {
    const descriptor = await readJson(`${fixtureRoot}${fixtureEntry.mutation}`);
    const expected = fixtureEntry.diagnosticCodes[0];
    if (
      descriptor.expectedDiagnostic !== expected ||
      !contract.diagnosticCodes.includes(expected) ||
      inferDiagnostic(applyMutation(request, descriptor), schema) !== expected
    ) {
      throw new Error(`${fixtureEntry.mutation}: screenshot Preview fail-closed reason changed`);
    }
    blocked += 1;
  }
  return {
    supportedGoldens: 1,
    failClosedDenominators: blocked,
    requestFingerprint: previewRequestFingerprint,
    wrapperFingerprint: previewWrapperFingerprint,
  };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  verifyPhase5ScreenshotRender()
    .then((result) => {
      process.stdout.write(
        `Verified Phase 5 screenshot Preview contract: ${result.supportedGoldens}/1 wrapper, ` +
          `${result.failClosedDenominators}/3 unsafe bindings blocked, request ` +
          `${result.requestFingerprint}, wrapper ${result.wrapperFingerprint}.\n`,
      );
    })
    .catch((error) => {
      process.stderr.write(`Phase 5 screenshot Preview verification failed: ${error.message}\n`);
      process.exitCode = 1;
    });
}
