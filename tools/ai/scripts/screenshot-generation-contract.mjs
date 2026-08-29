import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {DESIGN_IR_SCHEMA, SCREENSHOT_RESOLUTION_RESULT_SCHEMA} from './screenshot-resolution-contract.mjs';

const generationSchemaPath = fileURLToPath(
  new URL('../contracts/screenshot-kotlin-generation.schema.json', import.meta.url),
);
const generatedPreviewSchemaPath = fileURLToPath(
  new URL('../contracts/generated-preview-request.schema.json', import.meta.url),
);
const screenshotPreprocessingSchemaPath = fileURLToPath(
  new URL('../contracts/screenshot-preprocessing.schema.json', import.meta.url),
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
const GENERATED_PREVIEW_SCHEMA = JSON.parse(await readFile(generatedPreviewSchemaPath, 'utf8'));
const SCREENSHOT_PREPROCESSING_SCHEMA = JSON.parse(
  await readFile(screenshotPreprocessingSchemaPath, 'utf8'),
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

const generationPrefix = 'generation_';
const previewPrefix = 'preview_';
const preprocessingPrefix = 'preprocessing_';
const generationRequestForModes = (modes) => {
  const request = namespaceSchema(
    structuredClone(SCREENSHOT_GENERATION_SCHEMA.$defs.request),
    generationPrefix,
  );
  request.properties.mode = modes.length === 1 ? {const: modes[0]} : {enum: modes};
  return request;
};
const commonArgumentProperties = Object.freeze({
  resolutionResult: {type: 'object'},
});
export const SCREENSHOT_GENERATION_ARGUMENTS_SCHEMA = Object.freeze({
  type: 'object',
  oneOf: [
    {
      type: 'object',
      additionalProperties: false,
      required: ['resolutionResult', 'generationRequest'],
      properties: {
        ...commonArgumentProperties,
        generationRequest: generationRequestForModes(['generate', 'compile']),
      },
    },
    {
      type: 'object',
      additionalProperties: false,
      required: ['resolutionResult', 'generationRequest', 'previewBindings'],
      properties: {
        ...commonArgumentProperties,
        generationRequest: generationRequestForModes(['render', 'compare']),
        previewBindings: {
          type: 'array',
          maxItems: 64,
          items: {
            oneOf: [
              {$ref: `#/$defs/${previewPrefix}textFieldStateBinding`},
              {$ref: `#/$defs/${previewPrefix}unitCallbackBinding`},
              {$ref: `#/$defs/${previewPrefix}booleanCallbackBinding`},
              {$ref: `#/$defs/${previewPrefix}imeActionCallbackBinding`},
            ],
          },
        },
      },
    },
    {
      type: 'object',
      additionalProperties: false,
      required: [
        'resolutionResult',
        'generationRequest',
        'previewBindings',
        'pixelReference',
      ],
      properties: {
        ...commonArgumentProperties,
        generationRequest: generationRequestForModes(['compare-pixels']),
        previewBindings: {
          type: 'array',
          maxItems: 64,
          items: {
            oneOf: [
              {$ref: `#/$defs/${previewPrefix}textFieldStateBinding`},
              {$ref: `#/$defs/${previewPrefix}unitCallbackBinding`},
              {$ref: `#/$defs/${previewPrefix}booleanCallbackBinding`},
              {$ref: `#/$defs/${previewPrefix}imeActionCallbackBinding`},
            ],
          },
        },
        pixelReference: {
          type: 'object',
          additionalProperties: false,
          required: ['request', 'result'],
          properties: {
            request: {$ref: `#/$defs/${preprocessingPrefix}request`},
            result: {$ref: `#/$defs/${preprocessingPrefix}result`},
          },
        },
      },
    },
  ],
  $defs: {
    ...namespacedDefinitions(SCREENSHOT_GENERATION_SCHEMA.$defs, generationPrefix),
    ...namespacedDefinitions(GENERATED_PREVIEW_SCHEMA.$defs, previewPrefix),
    ...namespacedDefinitions(SCREENSHOT_PREPROCESSING_SCHEMA.$defs, preprocessingPrefix),
  },
});

export {DESIGN_IR_SCHEMA, SCREENSHOT_RESOLUTION_RESULT_SCHEMA};
