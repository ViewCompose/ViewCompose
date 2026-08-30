import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {fileURLToPath} from 'node:url';
import {validateSchemaValue} from './schema-validator.mjs';
import {loadValidatorIndex, validateKotlin} from './static-validator.mjs';

const fixture = (name) => readFile(
  fileURLToPath(new URL(`../evaluation/fixtures/kotlin/${name}`, import.meta.url)),
  'utf8',
);

test('derives the validator index from all generated source-resolved symbols', async () => {
  const index = await loadValidatorIndex();
  assert.equal(index.symbols.length, 540);
  assert.ok(index.byImport.has('com.viewcompose.ui.foundation.Column'));
  assert.ok(index.bySimpleName.has('padding'));
});

test('rejects a known modifier symbol used without its documented receiver/import', async () => {
  const result = await validateKotlin({
    source: await fixture('fabricated-padding.kt'),
    path: 'fabricated-padding.kt',
  });
  assert.equal(result.status, 'invalid');
  assert.deepEqual(result.diagnostics.map(({code}) => code), ['VC-AI-UNKNOWN-SYMBOL']);
  assert.equal(result.diagnostics[0].source.startLine, 9);
});

test('requires an explicit image description decision', async () => {
  const result = await validateKotlin({
    source: await fixture('missing-content-description.kt'),
    path: 'missing-content-description.kt',
  });
  assert.equal(result.status, 'invalid');
  assert.deepEqual(
    result.diagnostics.map(({code}) => code),
    ['VC-AI-A11Y-IMAGE-DESCRIPTION'],
  );
});

test('accepts meaningful and explicitly decorative image descriptions', async () => {
  for (const description of ['"Profile picture"', 'null']) {
    const result = await validateKotlin({source: `
      package sample
      import com.viewcompose.ui.foundation.Image
      import com.viewcompose.ui.node.ImageSource
      fun com.viewcompose.ui.foundation.UiTreeBuilder.example() {
        Image(source = ImageSource.Resource(1), contentDescription = ${description})
      }
    `});
    assert.equal(result.status, 'success');
  }
});

test('does not apply ViewCompose Image rules to a local function', async () => {
  const result = await validateKotlin({source: `
    package sample
    fun Image(source: Int) = source
    fun example() { Image(1) }
  `});
  assert.equal(result.status, 'success');
});

test('ignores symbol-like text in Kotlin strings and nested comments', async () => {
  const result = await validateKotlin({source: `
    package sample
    import com.viewcompose.ui.foundation.Image
    val text = "padding(16.dp) and Image(source = fake)"
    val block = """Image(source = fake)"""
    /* padding(16.dp) /* Image(source = fake) */ */
  `});
  assert.equal(result.status, 'success');
});

test('rejects a governed ViewCompose symbol imported from a nonexistent package', async () => {
  const result = await validateKotlin({source: `
    package sample
    import com.viewcompose.ui.modifier.Column
    fun example() = Unit
  `});
  assert.equal(result.status, 'invalid');
  assert.equal(result.diagnostics[0].code, 'VC-AI-UNKNOWN-SYMBOL');
  const schema = JSON.parse(await readFile(
    fileURLToPath(new URL('../contracts/tool-envelope.schema.json', import.meta.url)),
    'utf8',
  ));
  assert.deepEqual(validateSchemaValue(result, schema), []);
});
