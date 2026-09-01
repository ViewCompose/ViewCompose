import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import test from 'node:test';
import {assertSchemaValue, validateSchemaValue} from './schema-validator.mjs';

const contractsRoot = fileURLToPath(new URL('../contracts/', import.meta.url));
const schema = JSON.parse(await readFile(`${contractsRoot}/bootstrap.schema.json`, 'utf8'));
const example = JSON.parse(await readFile(`${contractsRoot}/examples/bootstrap.json`, 'utf8'));

test('accepts the frozen Wave A bootstrap contract example', () => {
  assertSchemaValue(example, schema, 'bootstrap contract example');
});

test('rejects a bootstrap contract that can configure an ephemeral path or write before selection', () => {
  const invalid = structuredClone(example);
  invalid.cache.configuredPath = 'npm-cache-path';
  invalid.compatibility.writesBeforeSelection = true;
  const violations = validateSchemaValue(invalid, schema);
  assert.ok(violations.some((item) => item.includes('durable-cache-only')));
  assert.ok(violations.some((item) => item.includes('expected constant false')));
});

test('rejects a bootstrap contract that silently accepts a logical project-root alias', () => {
  const invalid = structuredClone(example);
  invalid.rootResolution.symbolicLinks = 'follow';
  invalid.transaction.partialSelection = 'select-staged';
  const violations = validateSchemaValue(invalid, schema);
  assert.ok(violations.some((item) => item.includes('reject-logical-alias')));
  assert.ok(violations.some((item) => item.includes('never-select')));
});

test('rejects a mutable or non-current package identity', () => {
  const invalid = structuredClone(example);
  invalid.package.version = 'latest';
  invalid.compatibility.frameworkSelection = 'newest-tooling-profile';
  const violations = validateSchemaValue(invalid, schema);
  assert.ok(violations.some((item) => item.includes('0.7.0')));
  assert.ok(violations.some((item) => item.includes('exact-current-profile-only')));
});
