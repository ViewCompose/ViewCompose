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
  ]);
  assert.deepEqual(Object.keys(TOOL_DEFINITIONS).sort(), [...TOOL_NAMES].sort());
  for (const name of TOOL_NAMES) {
    const definition = publicToolDefinition(name);
    assert.equal(definition.name, name);
    assert.equal(definition.inputSchema.type, 'object');
    assert.equal(definition.annotations.readOnlyHint, true);
    assert.ok(TOOL_DEFINITIONS[name].defaultLimits.maxOutputBytes <= 1024 * 1024);
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

  const ambiguousXmlInput = validateSchemaValue({
    source: '<TextView />',
    projectRoot: '/workspace/sample',
    layoutPath: 'app/src/main/res/layout/screen.xml',
    resourceRoots: ['app/src/main/res'],
    mode: 'generate',
  }, TOOL_DEFINITIONS.convert_xml_to_viewcompose.inputSchema);
  assert.deepEqual(ambiguousXmlInput, ['$: expected exactly one oneOf match, found 0']);
});
