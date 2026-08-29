import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {DESIGN_IR_SCHEMA, SCREENSHOT_RESOLUTION_RESULT_SCHEMA} from './screenshot-resolution-contract.mjs';

const generationSchemaPath = fileURLToPath(
  new URL('../contracts/screenshot-kotlin-generation.schema.json', import.meta.url),
);

export const SCREENSHOT_GENERATION_SCHEMA = Object.freeze(
  JSON.parse(await readFile(generationSchemaPath, 'utf8')),
);
export const SCREENSHOT_GENERATION_REQUEST_SCHEMA = Object.freeze({
  ...structuredClone(SCREENSHOT_GENERATION_SCHEMA.$defs.request),
  $defs: structuredClone(SCREENSHOT_GENERATION_SCHEMA.$defs),
});
export const SCREENSHOT_GENERATION_REPORT_SCHEMA = Object.freeze({
  ...structuredClone(SCREENSHOT_GENERATION_SCHEMA.$defs.report),
  $defs: structuredClone(SCREENSHOT_GENERATION_SCHEMA.$defs),
});

function namespaceSchema(value, prefix) {
  if (Array.isArray(value)) return value.map((item) => namespaceSchema(item, prefix));
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, child]) => [
      key,
      key === '$ref' && typeof child === 'string' && child.startsWith('#/$defs/')
        ? child.replace('#/$defs/', `#/$defs/${prefix}`)
        : namespaceSchema(child, prefix),
    ]));
  }
  return value;
}

function namespacedDefinitions(definitions, prefix) {
  return Object.fromEntries(Object.entries(definitions).map(([name, definition]) => [
    `${prefix}${name}`,
    namespaceSchema(definition, prefix),
  ]));
}

const generationPrefix = 'generation_';
export const SCREENSHOT_GENERATION_ARGUMENTS_SCHEMA = Object.freeze({
  type: 'object',
  additionalProperties: false,
  required: ['resolutionResult', 'generationRequest'],
  properties: {
    resolutionResult: {type: 'object'},
    generationRequest: {$ref: `#/$defs/${generationPrefix}request`},
  },
  $defs: {
    ...namespacedDefinitions(SCREENSHOT_GENERATION_SCHEMA.$defs, generationPrefix),
  },
});

export {DESIGN_IR_SCHEMA, SCREENSHOT_RESOLUTION_RESULT_SCHEMA};
