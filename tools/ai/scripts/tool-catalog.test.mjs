import assert from 'node:assert/strict';
import test from 'node:test';
import {validateSchemaValue} from './schema-validator.mjs';
import {publicToolDefinition, TOOL_DEFINITIONS, TOOL_NAMES} from './tool-catalog.mjs';

test('publishes one stable catalog for retrieval, validation, Preview diagnosis, and project analysis', () => {
  assert.deepEqual(TOOL_NAMES, [
    'get_api_reference',
    'get_component_reference',
    'search_component',
    'get_sample',
    'validate_code',
    'render_preview',
    'diagnose_layout',
    'analyze_project',
    'convert_xml_to_viewcompose',
    'prepare_screenshot',
    'validate_screenshot_inference',
  ]);
  assert.deepEqual(Object.keys(TOOL_DEFINITIONS).sort(), [...TOOL_NAMES].sort());
  for (const name of TOOL_NAMES) {
    const definition = publicToolDefinition(name);
    assert.equal(definition.name, name);
    assert.equal(definition.inputSchema.type, 'object');
    assert.equal(definition.annotations.readOnlyHint, true);
    assert.ok(TOOL_DEFINITIONS[name].defaultLimits.maxOutputBytes <=
      (['prepare_screenshot', 'validate_screenshot_inference'].includes(name)
        ? 2_000_000
        : 1024 * 1024));
  }
});

test('the executable catalog rejects unbounded arrays and undeclared arguments', () => {
  const tooManyCapabilities = validateSchemaValue({
    source: 'fun screen() = Unit',
    capabilityIds: Array.from({length: 101}, (_, index) => `capability-${index}`),
  }, TOOL_DEFINITIONS.validate_code.inputSchema);
  assert.ok(tooManyCapabilities.some((violation) => violation.includes('more than 100')));

  const undeclared = validateSchemaValue({
    targetId: 'samples.counter.CounterPreview',
    gradleTask: ':app:runAnything',
  }, TOOL_DEFINITIONS.render_preview.inputSchema);
  assert.ok(undeclared.some((violation) => violation.includes('unexpected property gradleTask')));

  const diagnosisEscape = validateSchemaValue({
    targetId: 'samples.counter.CounterPreview',
    renderTreePath: '../../arbitrary.json',
  }, TOOL_DEFINITIONS.diagnose_layout.inputSchema);
  assert.ok(diagnosisEscape.some((violation) =>
    violation.includes('unexpected property renderTreePath')));

  const missingXmlMode = validateSchemaValue({
    source: '<TextView />',
  }, TOOL_DEFINITIONS.convert_xml_to_viewcompose.inputSchema);
  assert.deepEqual(missingXmlMode, ['$: expected exactly one oneOf match, found 0']);

  const projectContext = validateSchemaValue({
    projectRoot: '/workspace/sample',
    layoutPath: 'app/src/main/res/layout/screen.xml',
    resourceRoots: ['app/src/main/res'],
    sourceRoots: ['app/src/main/java'],
    mode: 'generate',
  }, TOOL_DEFINITIONS.convert_xml_to_viewcompose.inputSchema);
  assert.deepEqual(projectContext, []);

  const renderedXml = validateSchemaValue({
    source: '<TextView />',
    mode: 'render',
    previewBindings: [{
      kind: 'string',
      parameter: 'title',
      source: '@string/title',
      value: 'Title',
    }],
  }, TOOL_DEFINITIONS.convert_xml_to_viewcompose.inputSchema);
  assert.deepEqual(renderedXml, []);

  const missingPreviewBindings = validateSchemaValue({
    source: '<TextView />',
    mode: 'render',
  }, TOOL_DEFINITIONS.convert_xml_to_viewcompose.inputSchema);
  assert.deepEqual(missingPreviewBindings, ['$: expected exactly one oneOf match, found 0']);

  const projectRenderedXml = validateSchemaValue({
    projectRoot: '/workspace/sample',
    layoutPath: 'app/src/main/res/layout/login.xml',
    resourceRoots: ['app/src/main/res'],
    sourceRoots: ['app/src/main/java'],
    mode: 'render',
    previewBindings: [{
      kind: 'text-field-state',
      parameter: 'emailState',
      source: 'emailState',
      initialText: '',
    }],
  }, TOOL_DEFINITIONS.convert_xml_to_viewcompose.inputSchema);
  assert.deepEqual(projectRenderedXml, []);

  const embeddedAsset = {
    mediaType: 'image/png',
    encoding: 'base64',
    data: 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==',
    bytes: 70,
    sha256: '4ff6ab670a58c14270e034e2090d9a432caa263a14e0a25785386b0c12f880b5',
    widthPx: 1,
    heightPx: 1,
  };
  const renderedImageXml = validateSchemaValue({
    source: '<ImageView />',
    mode: 'render',
    previewBindings: [{
      kind: 'image-source',
      parameter: 'avatar',
      source: '@drawable/avatar',
      asset: embeddedAsset,
    }],
  }, TOOL_DEFINITIONS.convert_xml_to_viewcompose.inputSchema);
  assert.deepEqual(renderedImageXml, []);

  const imagePathDenied = validateSchemaValue({
    source: '<ImageView />',
    mode: 'render',
    previewBindings: [{
      kind: 'image-source',
      parameter: 'avatar',
      source: '@drawable/avatar',
      asset: {...embeddedAsset, path: '/workspace/avatar.png'},
    }],
  }, TOOL_DEFINITIONS.convert_xml_to_viewcompose.inputSchema);
  assert.deepEqual(imagePathDenied, ['$: expected exactly one oneOf match, found 0']);

  const generatedBuildSelection = validateSchemaValue({
    source: '<TextView />',
    mode: 'render',
    previewBindings: [],
    gradleTask: ':app:assembleDebug',
  }, TOOL_DEFINITIONS.convert_xml_to_viewcompose.inputSchema);
  assert.deepEqual(generatedBuildSelection, ['$: expected exactly one oneOf match, found 0']);

  const ambiguousXmlInput = validateSchemaValue({
    source: '<TextView />',
    projectRoot: '/workspace/sample',
    layoutPath: 'app/src/main/res/layout/screen.xml',
    resourceRoots: ['app/src/main/res'],
    mode: 'generate',
  }, TOOL_DEFINITIONS.convert_xml_to_viewcompose.inputSchema);
  assert.deepEqual(ambiguousXmlInput, ['$: expected exactly one oneOf match, found 0']);

  assert.equal(
    TOOL_DEFINITIONS.prepare_screenshot.inputSchema.$defs.pngAsset.properties.bytes.maximum,
    1_310_720,
  );
  const screenshotPath = validateSchemaValue({
    schemaVersion: 1,
    kind: 'request',
    source: {identity: 'test'},
    screenshot: {path: '/tmp/screenshot.png'},
    interpretation: {},
    privacy: {},
    output: {},
  }, TOOL_DEFINITIONS.prepare_screenshot.inputSchema);
  assert.ok(screenshotPath.some((violation) => violation.includes('unexpected property path')));

  assert.deepEqual(
    TOOL_DEFINITIONS.validate_screenshot_inference.inputSchema.required,
    ['preprocessingRequest', 'inferenceDeclaration', 'inferenceResult'],
  );
  assert.equal(
    TOOL_DEFINITIONS.validate_screenshot_inference.defaultLimits.maxInputBytes,
    4_000_000,
  );
});
