import assert from 'node:assert/strict';
import test from 'node:test';
import {
  KNOWLEDGE_TOOL_DEFINITIONS,
  loadKnowledgeIndex,
  retrieveApiReference,
  retrieveComponentReference,
  retrieveSample,
  searchComponents,
} from './knowledge-retriever.mjs';

const lane = {versionLane: 'current-source'};

test('loads one integrity-checked immutable knowledge index', async () => {
  const index = await loadKnowledgeIndex();
  assert.equal(index.manifest.bundleFingerprint, 'e23da5d9835d00dd31eda167152a875ebefa3f519b992874635113eed5a9a537');
  assert.equal(index.artifacts.length, 31);
  assert.equal(index.capabilities.length, 82);
  assert.equal(index.symbols.length, 540);
  assert.equal(index.samples.length, 216);
  assert.equal(index.rules.length, 10);
  assert.deepEqual(Object.keys(KNOWLEDGE_TOOL_DEFINITIONS).sort(), [
    'get_api_reference',
    'get_component_reference',
    'get_sample',
    'search_component',
  ]);
});

test('ranks the frozen exact-symbol query first even when its recalled package is stale', async () => {
  const result = await searchComponents({
    ...lane,
    query: 'com.viewcompose.foundation.layout.Column',
    limit: 5,
  });
  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'knowledge');
  assert.equal(result.data.results[0].simpleName, 'Column');
  assert.equal(result.data.results[0].capabilityId, 'foundation.components');
});

test('places the frozen modifier-intent capability inside the first five results', async () => {
  const result = await searchComponents({
    ...lane,
    query: 'add padding and fill the available width',
    limit: 5,
  });
  assert.equal(result.status, 'success');
  assert.ok(result.data.results.some((entry) => entry.capabilityId === 'modifier.layout'));
});

test('search ranking and ownership filters are deterministic', async () => {
  const arguments_ = {
    ...lane,
    query: 'padding',
    artifactId: 'viewcompose-ui-contract',
    artifactVersion: '0.1.0-alpha05',
    capabilityId: 'modifier.layout',
    kind: 'modifier',
    limit: 10,
  };
  const first = await searchComponents(arguments_);
  const second = await searchComponents(arguments_);
  assert.deepEqual(first.data, second.data);
  assert.ok(first.data.results.length > 0);
  assert.ok(first.data.results.every((entry) =>
    entry.artifactId === 'viewcompose-ui-contract' &&
    entry.artifactVersion === '0.1.0-alpha05' &&
    entry.capabilityId === 'modifier.layout' &&
    entry.kind === 'modifier'));
});

test('resolves exact symbol, capability, and artifact references without conflating versions', async () => {
  const symbol = await retrieveApiReference({
    ...lane,
    identifier: 'com.viewcompose.ui.foundation.UiTreeBuilder.Column',
  });
  assert.equal(symbol.status, 'success');
  assert.equal(symbol.data.referenceType, 'symbol');
  assert.equal(symbol.data.artifact.version, '0.1.0-alpha02');
  assert.deepEqual(symbol.data.capability.versionState, {lane: 'released', version: '0.1.0-alpha01'});

  const capability = await retrieveApiReference({...lane, identifier: 'foundation.components'});
  assert.equal(capability.data.referenceType, 'capability');
  assert.ok(capability.data.symbols.some((entry) => entry.simpleName === 'Column'));

  const artifact = await retrieveApiReference({...lane, identifier: 'viewcompose-ui-foundation'});
  assert.equal(artifact.data.referenceType, 'artifact');
  assert.ok(artifact.data.capabilities.some((entry) => entry.capabilityId === 'foundation.components'));
});

test('returns component parameters, applicable rules, ownership, and its compiled sample', async () => {
  const column = await retrieveComponentReference({...lane, name: 'Column'});
  assert.equal(column.status, 'success');
  assert.equal(column.data.importName, 'com.viewcompose.ui.foundation.Column');
  const parameters = column.data.symbol.declarations[0].parameters;
  assert.deepEqual(parameters.find((entry) => entry.name === 'spacing'), {
    name: 'spacing',
    type: 'UiDp',
    default: 'UiDp.Zero',
    required: false,
  });
  assert.equal(parameters.find((entry) => entry.name === 'content').required, true);
  assert.equal(column.data.sample.sampleClass, 'compiled-region');
  assert.ok(column.data.rules.some((entry) => entry.code === 'VC-AI-MODIFIER-ORDER'));
  assert.ok(column.data.rules.some((entry) => entry.code === 'VC-AI-UNIT-DP-SP'));

  const image = await retrieveComponentReference({...lane, name: 'Image'});
  assert.ok(image.data.rules.some((entry) => entry.code === 'VC-AI-A11Y-IMAGE-DESCRIPTION'));
});

test('requires exact component disambiguation and rejects unknown references', async () => {
  const ambiguous = await retrieveComponentReference({...lane, name: 'AnimatedVisibility'});
  assert.equal(ambiguous.status, 'invalid');
  assert.equal(ambiguous.diagnostics[0].code, 'VC-AI-REFERENCE-AMBIGUOUS');
  assert.ok(ambiguous.data.candidates.length > 1);

  const missing = await retrieveApiReference({...lane, identifier: 'foundation.imaginary'});
  assert.equal(missing.status, 'invalid');
  assert.equal(missing.diagnostics[0].code, 'VC-AI-REFERENCE-NOT-FOUND');

  const wrongLane = await searchComponents({versionLane: 'released', query: 'Column'});
  assert.equal(wrongLane.status, 'invalid');
  assert.equal(wrongLane.diagnostics[0].code, 'VC-AI-ARGUMENTS-INVALID');
});

test('distinguishes compiled samples from explicit non-executable evidence', async () => {
  const compiled = await retrieveSample({...lane, sampleId: 'module.ui-foundation-profile-summary'});
  assert.equal(compiled.status, 'success');
  assert.equal(compiled.data.executable, true);
  assert.match(compiled.data.sample.code, /fun UiTreeBuilder\.ProfileSummary/u);
  assert.equal(compiled.data.sample.buildTarget, ':viewcompose-ui-foundation:compileDebugUnitTestKotlin');

  const outline = await retrieveSample({...lane, sampleId: 'adr.correlated-diagnostics-event-model'});
  assert.equal(outline.status, 'success');
  assert.equal(outline.data.executable, false);
  assert.equal(outline.data.sample.code, undefined);
  assert.match(outline.data.sample.visibleExplanation, /accepted event-contract outline/u);
});
