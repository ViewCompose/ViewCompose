import {readFile} from 'node:fs/promises';
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  applyDesignIrRepairPatch,
  DesignIrRepairPatchError,
} from './design-ir-repair-patch.mjs';
import {
  fingerprintRepairValue,
  sealRepairPatch,
} from './repair-orchestrator.mjs';

const resolution = JSON.parse(await readFile(new URL(
  '../evaluation/fixtures/visual/screenshot-resolution/wireframe.result.json',
  import.meta.url,
)));

function request(operations, designIr = resolution.designIr) {
  return {
    designIr,
    expectedDesignIrFingerprint: fingerprintRepairValue(designIr),
    patch: sealRepairPatch(operations),
  };
}

function replaceTitle(value) {
  return {
    op: 'replace-field',
    nodeId: 'wireframe-title',
    collection: 'properties',
    name: 'text',
    value: {kind: 'literal', value},
  };
}

async function expectCode(promise, code) {
  await assert.rejects(promise, (error) => {
    assert.ok(error instanceof DesignIrRepairPatchError);
    assert.equal(error.code, code);
    return true;
  });
}

test('applies one typed field replacement deterministically without mutating input', async () => {
  const input = structuredClone(resolution.designIr);
  const before = JSON.stringify(input);
  const arguments_ = request([replaceTitle('Hello')], input);
  const first = await applyDesignIrRepairPatch(arguments_);
  const second = await applyDesignIrRepairPatch(arguments_);
  assert.deepEqual(first, second);
  assert.equal(JSON.stringify(input), before);
  assert.equal(first.operationCount, 1);
  assert.deepEqual(first.changedPaths, ['wireframe-title.properties.text']);
  assert.equal(first.inputDesignIrFingerprint, resolution.designIrFingerprint);
  assert.notEqual(first.designIrFingerprint, resolution.designIrFingerprint);
  assert.equal(
    first.designIr.roots[0].children[0].properties[0].value.value,
    'Hello',
  );
});

test('applies modifier arguments, node kind, and exact child permutation', async () => {
  const input = structuredClone(resolution.designIr);
  input.roots[0].children[0].modifiers.push({
    kind: 'padding',
    arguments: [{name: 'all', value: {kind: 'dimension', value: 8, unit: 'dp'}}],
  });
  const result = await applyDesignIrRepairPatch(request([
    {
      op: 'replace-modifier-argument',
      nodeId: 'wireframe-title',
      modifierIndex: 0,
      name: 'all',
      value: {kind: 'dimension', value: 12, unit: 'dp'},
    },
    {op: 'replace-node-kind', nodeId: 'wireframe-button', kind: 'text'},
    {
      op: 'reorder-children',
      nodeId: 'wireframe-root',
      orderedChildIds: ['wireframe-field', 'wireframe-title', 'wireframe-button'],
    },
  ], input));
  assert.deepEqual(result.changedPaths, [
    'wireframe-title.modifiers[0].all',
    'wireframe-button.kind',
    'wireframe-root.children',
  ]);
  assert.equal(
    result.designIr.roots[0].children[1].modifiers[0].arguments[0].value.value,
    12,
  );
  assert.equal(result.designIr.roots[0].children[2].kind, 'text');
});

test('rejects no-op values and unchanged child order', async () => {
  await expectCode(
    applyDesignIrRepairPatch(request([replaceTitle('Welcome')])),
    'VC-AI-REPAIR-NO-ELIGIBLE-CHANGE',
  );
  await expectCode(
    applyDesignIrRepairPatch(request([{
      op: 'reorder-children',
      nodeId: 'wireframe-root',
      orderedChildIds: ['wireframe-title', 'wireframe-field', 'wireframe-button'],
    }])),
    'VC-AI-REPAIR-NO-ELIGIBLE-CHANGE',
  );
});

test('rejects missing nodes, fields, modifiers, and non-permutation child lists', async () => {
  await expectCode(
    applyDesignIrRepairPatch(request([{...replaceTitle('Hello'), nodeId: 'missing'}])),
    'VC-AI-REPAIR-INPUT-INVALID',
  );
  await expectCode(
    applyDesignIrRepairPatch(request([{...replaceTitle('Hello'), name: 'missing'}])),
    'VC-AI-REPAIR-INPUT-INVALID',
  );
  await expectCode(
    applyDesignIrRepairPatch(request([{
      op: 'replace-modifier-argument',
      nodeId: 'wireframe-title',
      modifierIndex: 0,
      name: 'all',
      value: {kind: 'dimension', value: 12, unit: 'dp'},
    }])),
    'VC-AI-REPAIR-INPUT-INVALID',
  );
  await expectCode(
    applyDesignIrRepairPatch(request([{
      op: 'reorder-children',
      nodeId: 'wireframe-root',
      orderedChildIds: ['wireframe-title', 'wireframe-field'],
    }])),
    'VC-AI-REPAIR-INPUT-INVALID',
  );
});

test('rejects executable patches and changed input lineage before mutation', async () => {
  await expectCode(
    applyDesignIrRepairPatch(request([{
      ...replaceTitle('ignored'),
      value: {kind: 'expression', language: 'kotlin', source: 'runProject()'},
    }])),
    'VC-AI-REPAIR-INPUT-INVALID',
  );
  const changedLineage = request([replaceTitle('Hello')]);
  changedLineage.expectedDesignIrFingerprint = 'f'.repeat(64);
  await expectCode(
    applyDesignIrRepairPatch(changedLineage),
    'VC-AI-REPAIR-INPUT-INVALID',
  );
});

test('requires a resolved screenshot IR with no unsupported or expression content', async () => {
  const unsupported = structuredClone(resolution.designIr);
  unsupported.unsupported.push({
    sourceId: 'screenshot:test',
    sourceSpan: 'pixels:0,0,1,1',
    code: 'VC-AI-TEST-UNSUPPORTED',
    reason: 'test',
    preservedSource: '',
    disposition: 'blocked',
  });
  await expectCode(
    applyDesignIrRepairPatch(request([replaceTitle('Hello')], unsupported)),
    'VC-AI-REPAIR-INPUT-INVALID',
  );
  const expression = structuredClone(resolution.designIr);
  expression.roots[0].children[0].properties[0].value = {
    kind: 'expression',
    language: 'kotlin',
    source: 'runProject()',
  };
  await expectCode(
    applyDesignIrRepairPatch(request([replaceTitle('Hello')], expression)),
    'VC-AI-REPAIR-INPUT-INVALID',
  );
});

test('honors cancellation before reading or applying patch operations', async () => {
  const controller = new AbortController();
  controller.abort();
  await expectCode(
    applyDesignIrRepairPatch(request([replaceTitle('Hello')]), {signal: controller.signal}),
    'VC-AI-REPAIR-CANCELLED',
  );
});
