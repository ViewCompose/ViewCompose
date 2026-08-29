import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {SCREENSHOT_PREPROCESSING_SCHEMA} from './screenshot-contract.mjs';

const inferenceSchemaPath = fileURLToPath(
  new URL('../contracts/screenshot-design-inference.schema.json', import.meta.url),
);
const designIrSchemaPath = fileURLToPath(
  new URL('../contracts/design-ir.schema.json', import.meta.url),
);

export const SCREENSHOT_INFERENCE_SCHEMA = Object.freeze(
  JSON.parse(await readFile(inferenceSchemaPath, 'utf8')),
);
export const SCREENSHOT_INFERENCE_REQUEST_SCHEMA = Object.freeze({
  ...structuredClone(SCREENSHOT_INFERENCE_SCHEMA.$defs.request),
  $defs: structuredClone(SCREENSHOT_INFERENCE_SCHEMA.$defs),
});
export const SCREENSHOT_INFERENCE_RESULT_SCHEMA = Object.freeze({
  ...structuredClone(SCREENSHOT_INFERENCE_SCHEMA.$defs.result),
  $defs: structuredClone(SCREENSHOT_INFERENCE_SCHEMA.$defs),
});
export const DESIGN_IR_SCHEMA = Object.freeze(
  JSON.parse(await readFile(designIrSchemaPath, 'utf8')),
);

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

const preprocessingPrefix = 'preprocessing_';
const inferencePrefix = 'inference_';
const inferenceRequest = SCREENSHOT_INFERENCE_SCHEMA.$defs.request;
const inferenceDeclaration = {
  type: 'object',
  additionalProperties: false,
  required: ['interpretation', 'intent', 'policy', 'authorization'],
  properties: Object.fromEntries(
    ['interpretation', 'intent', 'policy', 'authorization'].map((name) => [
      name,
      namespaceSchema(inferenceRequest.properties[name], inferencePrefix),
    ]),
  ),
};

export const SCREENSHOT_INFERENCE_VALIDATION_ARGUMENTS_SCHEMA = Object.freeze({
  type: 'object',
  additionalProperties: false,
  required: ['preprocessingRequest', 'inferenceDeclaration', 'inferenceResult'],
  properties: {
    preprocessingRequest: {$ref: `#/$defs/${preprocessingPrefix}request`},
    inferenceDeclaration: {$ref: '#/$defs/inference_declaration'},
    inferenceResult: {$ref: `#/$defs/${inferencePrefix}result`},
  },
  $defs: {
    ...namespacedDefinitions(SCREENSHOT_PREPROCESSING_SCHEMA.$defs, preprocessingPrefix),
    ...namespacedDefinitions(SCREENSHOT_INFERENCE_SCHEMA.$defs, inferencePrefix),
    inference_declaration: inferenceDeclaration,
  },
});
