import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {assertSchemaValue} from './schema-validator.mjs';
import {
  buildKnowledgeBundle,
  extractDeclarations,
  repositoryRoot,
  stableJson,
} from './knowledge-generator.mjs';

test('extracts normalized signatures, defaults, lines, and KDoc summaries from canonical source', async () => {
  const source = await readFile(
    resolve(
      repositoryRoot,
      'viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/dsl/layout/LayoutWidgetsDsl.kt',
    ),
    'utf8',
  );
  const declarations = extractDeclarations(
    source,
    'com.viewcompose.ui.foundation.UiTreeBuilder.Column',
    'extension',
  );
  assert.equal(declarations.length, 1);
  assert.match(declarations[0].signature, /fun UiTreeBuilder\.Column\(/u);
  assert.match(declarations[0].signature, /spacing: UiDp = UiDp\.Zero/u);
  assert.ok(declarations[0].line > 1);
  assert.match(declarations[0].summary, /vertical/u);
});

test('builds a deterministic complete bundle from Governance V2 and compiled samples', async () => {
  const first = await buildKnowledgeBundle({sourceRevision: '0123456789abcdef'});
  const second = await buildKnowledgeBundle({sourceRevision: '0123456789abcdef'});
  assert.equal(stableJson(first.manifest), stableJson(second.manifest));
  assert.deepEqual([...first.files], [...second.files]);
  assert.equal(first.manifest.counts.artifacts, 30);
  assert.equal(first.manifest.counts.capabilities, 77);
  assert.equal(first.manifest.counts.symbols, 537);
  assert.equal(first.manifest.counts.samples, 209);
  assert.equal(first.manifest.counts.rules, 10);
  const resolved = first.symbols.filter((symbol) => symbol.declarations.length > 0).length;
  assert.equal(resolved, first.symbols.length);
  const sample = first.samples.find(
    (candidate) => candidate.sampleId === 'module.ui-foundation-profile-summary',
  );
  assert.match(sample.code, /fun UiTreeBuilder\.ProfileSummary/u);
  assert.equal(sample.buildTarget, ':viewcompose-ui-foundation:compileDebugUnitTestKotlin');
  const nonExecutable = first.samples.find((candidate) => candidate.sampleClass === 'non-executable');
  assert.equal(nonExecutable.code, undefined);
  assert.ok(nonExecutable.reason.length > 0);
});

test('emits a manifest accepted by the frozen Phase 0 schema', async () => {
  const [schema, bundle] = await Promise.all([
    readFile(resolve(repositoryRoot, 'tools/ai/contracts/knowledge-bundle-manifest.schema.json'), 'utf8')
      .then(JSON.parse),
    buildKnowledgeBundle({sourceRevision: '0123456789abcdef'}),
  ]);
  assertSchemaValue(bundle.manifest, schema, 'generated manifest');
});
