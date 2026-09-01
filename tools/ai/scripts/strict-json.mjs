export class StrictJsonError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'StrictJsonError';
    this.code = code;
  }
}

function fail(code, message) {
  throw new StrictJsonError(code, message);
}

export function parseStrictJson(source, {maxDepth = 48} = {}) {
  if (typeof source !== 'string') fail('TYPE', 'Strict JSON input must be a string.');
  let cursor = 0;

  const whitespace = () => {
    while (/[\t\n\r ]/u.test(source[cursor] ?? '')) cursor += 1;
  };

  const stringToken = () => {
    const start = cursor;
    if (source[cursor] !== '"') fail('SYNTAX', `Expected string at byte ${cursor}.`);
    cursor += 1;
    while (cursor < source.length) {
      const character = source[cursor];
      if (character === '"') {
        cursor += 1;
        const token = source.slice(start, cursor);
        try {
          return JSON.parse(token);
        } catch {
          fail('SYNTAX', `Invalid JSON string at byte ${start}.`);
        }
      }
      if (character === '\\') {
        cursor += 1;
        if (source[cursor] === 'u') {
          if (!/^[a-fA-F0-9]{4}$/u.test(source.slice(cursor + 1, cursor + 5))) {
            fail('SYNTAX', `Invalid Unicode escape at byte ${cursor}.`);
          }
          cursor += 5;
          continue;
        }
        if (!/["\\/bfnrt]/u.test(source[cursor] ?? '')) {
          fail('SYNTAX', `Invalid escape at byte ${cursor}.`);
        }
        cursor += 1;
        continue;
      }
      if (character.codePointAt(0) < 0x20) {
        fail('SYNTAX', `Unescaped control character at byte ${cursor}.`);
      }
      cursor += 1;
    }
    fail('SYNTAX', `Unterminated JSON string at byte ${start}.`);
  };

  const value = (depth) => {
    whitespace();
    if (depth > maxDepth) fail('DEPTH', `JSON nesting exceeds ${maxDepth}.`);
    const character = source[cursor];
    if (character === '"') {
      stringToken();
      return;
    }
    if (character === '{') {
      cursor += 1;
      whitespace();
      const keys = new Set();
      if (source[cursor] === '}') {
        cursor += 1;
        return;
      }
      while (true) {
        whitespace();
        const keyOffset = cursor;
        const key = stringToken();
        if (keys.has(key)) fail('DUPLICATE_KEY', `Duplicate JSON key ${key} at byte ${keyOffset}.`);
        keys.add(key);
        whitespace();
        if (source[cursor] !== ':') fail('SYNTAX', `Expected colon at byte ${cursor}.`);
        cursor += 1;
        value(depth + 1);
        whitespace();
        if (source[cursor] === '}') {
          cursor += 1;
          return;
        }
        if (source[cursor] !== ',') fail('SYNTAX', `Expected comma at byte ${cursor}.`);
        cursor += 1;
      }
    }
    if (character === '[') {
      cursor += 1;
      whitespace();
      if (source[cursor] === ']') {
        cursor += 1;
        return;
      }
      while (true) {
        value(depth + 1);
        whitespace();
        if (source[cursor] === ']') {
          cursor += 1;
          return;
        }
        if (source[cursor] !== ',') fail('SYNTAX', `Expected comma at byte ${cursor}.`);
        cursor += 1;
      }
    }
    const remaining = source.slice(cursor);
    const literal = remaining.match(/^(?:true|false|null)/u)?.[0];
    if (literal) {
      cursor += literal.length;
      return;
    }
    const number = remaining.match(/^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?/u)?.[0];
    if (number) {
      cursor += number.length;
      return;
    }
    fail('SYNTAX', `Invalid JSON value at byte ${cursor}.`);
  };

  whitespace();
  value(1);
  whitespace();
  if (cursor !== source.length) fail('SYNTAX', `Unexpected content at byte ${cursor}.`);
  try {
    return JSON.parse(source);
  } catch {
    fail('SYNTAX', 'JSON parsing failed after strict structural validation.');
  }
}
