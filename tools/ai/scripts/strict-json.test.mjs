import assert from 'node:assert/strict';
import test from 'node:test';
import {parseStrictJson, StrictJsonError} from './strict-json.mjs';

test('parses canonical JSON without changing its values', () => {
  assert.deepEqual(parseStrictJson('{"a":[1,true,null,"x"],"b":{"c":-1.5e2}}'), {
    a: [1, true, null, 'x'],
    b: {c: -150},
  });
});

test('rejects duplicate decoded object keys at any depth', () => {
  for (const source of ['{"a":1,"a":2}', '{"a":{"x":1,"\\u0078":2}}']) {
    assert.throws(
      () => parseStrictJson(source),
      (error) => error instanceof StrictJsonError && error.code === 'DUPLICATE_KEY',
    );
  }
});

test('rejects malformed, trailing, and over-depth JSON', () => {
  assert.throws(() => parseStrictJson('{"a":}'), StrictJsonError);
  assert.throws(() => parseStrictJson('{}[]'), StrictJsonError);
  assert.throws(
    () => parseStrictJson('{"a":{"b":1}}', {maxDepth: 2}),
    (error) => error.code === 'DEPTH',
  );
});
