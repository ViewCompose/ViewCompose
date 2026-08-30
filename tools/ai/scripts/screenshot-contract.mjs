import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

const schemaPath = fileURLToPath(
  new URL('../contracts/screenshot-preprocessing.schema.json', import.meta.url),
);

export const SCREENSHOT_PREPROCESSING_SCHEMA = Object.freeze(
  JSON.parse(await readFile(schemaPath, 'utf8')),
);

export const SCREENSHOT_REQUEST_SCHEMA = Object.freeze({
  ...structuredClone(SCREENSHOT_PREPROCESSING_SCHEMA.$defs.request),
  $defs: structuredClone(SCREENSHOT_PREPROCESSING_SCHEMA.$defs),
});

function canonicalValue(value) {
  if (Array.isArray(value)) return value.map(canonicalValue);
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.keys(value).sort().map((key) => [key, canonicalValue(value[key])]),
    );
  }
  return value;
}

export function canonicalJson(value) {
  return JSON.stringify(canonicalValue(value));
}
