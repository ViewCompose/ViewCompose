import test from 'node:test';
import assert from 'node:assert/strict';
import {validateSchemaValue} from './schema-validator.mjs';

const schema = {
  type: 'object',
  additionalProperties: false,
  required: ['schemaVersion', 'ids'],
  properties: {
    schemaVersion: {const: 1},
    ids: {
      type: 'array',
      minItems: 1,
      uniqueItems: true,
      items: {type: 'string', pattern: '^[a-z.]+$'},
    },
  },
};

test('accepts the supported deterministic JSON Schema subset', () => {
  assert.deepEqual(validateSchemaValue({schemaVersion: 1, ids: ['layout.column']}, schema), []);
});

test('reports constants, duplicate array values, patterns, and additional properties', () => {
  const violations = validateSchemaValue(
    {schemaVersion: 2, ids: ['Column', 'Column'], unknown: true},
    schema,
  );
  assert.ok(violations.some((item) => item.includes('constant')));
  assert.ok(violations.some((item) => item.includes('unique')));
  assert.ok(violations.some((item) => item.includes('does not match')));
  assert.ok(violations.some((item) => item.includes('unexpected property')));
});

test('supports allOf and fixed prefixItems contracts', () => {
  const fixedTupleSchema = {
    type: 'array',
    prefixItems: [
      {type: 'string'},
      {allOf: [{type: 'number'}, {minimum: 1}]},
    ],
    items: false,
  };

  assert.deepEqual(validateSchemaValue(['profile', 1], fixedTupleSchema), []);
  assert.ok(validateSchemaValue(['profile', 0], fixedTupleSchema).some((item) =>
    item.includes('below 1')));
  assert.ok(validateSchemaValue(['profile', 1, true], fixedTupleSchema).some((item) =>
    item.includes('outside the accepted prefix')));
});
