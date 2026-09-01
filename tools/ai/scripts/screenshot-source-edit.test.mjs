import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {
  deriveGeneratedPropertyEdit,
  reconstructGeneratedCandidate,
} from './screenshot-source-edit.mjs';

const goldenPath = new URL(
  '../evaluation/fixtures/visual/screenshot-generation/wireframe.generated.kt',
  import.meta.url,
);

test('derives and reconstructs one exact generated text property span', async () => {
  const currentKotlin = await readFile(goldenPath, 'utf8');
  const candidateKotlin = currentKotlin.replace('text = "Welcome"', 'text = "Hello"');
  const edit = deriveGeneratedPropertyEdit({
    currentKotlin,
    candidateKotlin,
    relativePath: 'app/src/main/java/example/LoginScreen.kt',
    nodeId: 'wireframe-title',
    propertyName: 'text',
    currentValue: 'Welcome',
    candidateValue: 'Hello',
  });
  assert.equal(edit.kind, 'replace-generated-property-value');
  assert.equal(edit.nodeId, 'wireframe-title');
  assert.match(edit.diff.text, /-            text = "Welcome",/u);
  assert.match(edit.diff.text, /\+            text = "Hello",/u);
  assert.deepEqual(
    reconstructGeneratedCandidate(Buffer.from(currentKotlin, 'utf8'), edit),
    Buffer.from(candidateKotlin, 'utf8'),
  );
});

test('rejects a candidate that changes anything outside the admitted property', async () => {
  const currentKotlin = await readFile(goldenPath, 'utf8');
  const candidateKotlin = currentKotlin
    .replace('text = "Welcome"', 'text = "Hello"')
    .replace('text = "Continue"', 'text = "Proceed"');
  assert.throws(() => deriveGeneratedPropertyEdit({
    currentKotlin,
    candidateKotlin,
    relativePath: 'app/src/main/java/example/LoginScreen.kt',
    nodeId: 'wireframe-title',
    propertyName: 'text',
    currentValue: 'Welcome',
    candidateValue: 'Hello',
  }), {code: 'VC-AI-SOURCE-APPLICATION-EDIT-UNSUPPORTED'});
});

test('rejects reconstructed source after preimage or replacement drift', async () => {
  const currentKotlin = await readFile(goldenPath, 'utf8');
  const candidateKotlin = currentKotlin.replace('text = "Welcome"', 'text = "Hello"');
  const edit = deriveGeneratedPropertyEdit({
    currentKotlin,
    candidateKotlin,
    relativePath: 'app/src/main/java/example/LoginScreen.kt',
    nodeId: 'wireframe-title',
    propertyName: 'text',
    currentValue: 'Welcome',
    candidateValue: 'Hello',
  });
  const changed = Buffer.from(currentKotlin.replace('"Welcome"', '"Welc0me"'), 'utf8');
  assert.throws(() => reconstructGeneratedCandidate(changed, edit), {
    code: 'VC-AI-SOURCE-APPLICATION-SPAN-DRIFT',
  });
  assert.throws(() => reconstructGeneratedCandidate(Buffer.from(currentKotlin), {
    ...edit,
    replacement: {...edit.replacement, data: 'Im5vcGUi'},
  }), {code: 'VC-AI-SOURCE-APPLICATION-SPAN-DRIFT'});
});
