import assert from 'node:assert/strict';
import {readFile, readdir} from 'node:fs/promises';
import test from 'node:test';
import {
  DESIGN_IR_V2_SCHEMA,
  FIGMA_EXPORT_SCHEMA,
  FIGMA_IMPORT_DIAGNOSTICS,
  FIGMA_IMPORT_LIMITS,
  FIGMA_IMPORT_REQUEST_SCHEMA,
} from './figma-contract.mjs';
import {validateSchemaValue} from './schema-validator.mjs';

const exampleRoot = new URL('../contracts/examples/', import.meta.url);
const mutationRoot = new URL('../evaluation/fixtures/figma/', import.meta.url);

async function json(url) {
  return JSON.parse(await readFile(url, 'utf8'));
}

test('freezes schema-valid Figma export and Design IR v2 examples', async () => {
  const [exported, designIr] = await Promise.all([
    json(new URL('figma-export.json', exampleRoot)),
    json(new URL('design-ir-v2.json', exampleRoot)),
  ]);
  assert.deepEqual(validateSchemaValue(exported, FIGMA_EXPORT_SCHEMA), []);
  assert.deepEqual(validateSchemaValue(designIr, DESIGN_IR_V2_SCHEMA), []);
  assert.equal(designIr.schemaVersion, 2);
  assert.equal(designIr.source.kind, 'figma');
});

test('freezes mutually exclusive inspect, generate, and verify requests', async () => {
  const exportJson = await readFile(new URL('figma-export.json', exampleRoot), 'utf8');
  for (const mode of ['inspect', 'generate']) {
    assert.deepEqual(validateSchemaValue({
      schemaVersion: 1,
      kind: 'figma-import-request',
      mode,
      exportJson,
    }, FIGMA_IMPORT_REQUEST_SCHEMA), []);
  }
  const verify = {
    schemaVersion: 1,
    kind: 'figma-import-request',
    mode: 'verify',
    exportJson,
    verification: {
      widthDp: 360,
      heightDp: 120,
      density: 1,
      fontScale: 1,
      theme: 'Light',
      layoutDirection: 'Ltr',
    },
  };
  assert.deepEqual(validateSchemaValue(verify, FIGMA_IMPORT_REQUEST_SCHEMA), []);
  delete verify.verification;
  assert.notDeepEqual(validateSchemaValue(verify, FIGMA_IMPORT_REQUEST_SCHEMA), []);
});

test('keeps normalized Figma payloads inside the fixed CLI and MCP ceiling', () => {
  assert.equal(FIGMA_IMPORT_LIMITS.maxArgumentsBytes, 3145728);
  assert.equal(FIGMA_IMPORT_LIMITS.maxResultBytes, 3145728);
  assert.equal(FIGMA_IMPORT_LIMITS.maxTransportBytes, 4194304);
  assert.ok(FIGMA_IMPORT_LIMITS.maxArgumentsBytes < FIGMA_IMPORT_LIMITS.maxTransportBytes);
  assert.ok(FIGMA_IMPORT_LIMITS.maxResultBytes < FIGMA_IMPORT_LIMITS.maxTransportBytes);
  assert.equal(FIGMA_IMPORT_LIMITS.maxAssetBytesTotal, 1048576);
});

test('freezes adversarial and unsupported mutation expectations', async () => {
  const names = (await readdir(mutationRoot)).filter((name) => name.endsWith('.json')).sort();
  assert.deepEqual(names, [
    'active-plugin-data.mutation.json',
    'asset-integrity.mutation.json',
    'path-traversal.mutation.json',
    'prototype-interaction.mutation.json',
    'undeclared-font.mutation.json',
    'unsupported-effect.mutation.json',
    'url-reference.mutation.json',
  ]);
  const fixtures = await Promise.all(names.map((name) => json(new URL(name, mutationRoot))));
  for (const fixture of fixtures) {
    assert.equal(fixture.schemaVersion, 1);
    assert.ok(FIGMA_IMPORT_DIAGNOSTICS.includes(fixture.expectedDiagnostic));
  }
});
