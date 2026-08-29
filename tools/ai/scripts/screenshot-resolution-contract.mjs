import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {
  DESIGN_IR_SCHEMA,
  SCREENSHOT_INFERENCE_SCHEMA,
} from './screenshot-inference-contract.mjs';

const resolutionSchemaPath = fileURLToPath(
  new URL('../contracts/screenshot-inference-resolution.schema.json', import.meta.url),
);

export const SCREENSHOT_RESOLUTION_SCHEMA = Object.freeze(
  JSON.parse(await readFile(resolutionSchemaPath, 'utf8')),
);
export const SCREENSHOT_RESOLUTION_REQUEST_SCHEMA = Object.freeze({
  ...structuredClone(SCREENSHOT_RESOLUTION_SCHEMA.$defs.request),
  $defs: structuredClone(SCREENSHOT_RESOLUTION_SCHEMA.$defs),
});
export const SCREENSHOT_RESOLUTION_RESULT_SCHEMA = Object.freeze({
  ...structuredClone(SCREENSHOT_RESOLUTION_SCHEMA.$defs.result),
  $defs: structuredClone(SCREENSHOT_RESOLUTION_SCHEMA.$defs),
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

const inferencePrefix = 'inference_';
const resolutionPrefix = 'resolution_';
const inferenceResult = SCREENSHOT_INFERENCE_SCHEMA.$defs.result;
const validatedInference = {
  type: 'object',
  additionalProperties: false,
  required: [
    'schemaVersion',
    'kind',
    'status',
    'authorization',
    'producer',
    'fingerprints',
    'designIr',
    'nodeEvidence',
    'unresolvedQuestions',
    'summary',
    'inferenceDiagnostics',
    'validationFingerprint',
  ],
  properties: {
    schemaVersion: {const: 1},
    kind: {const: 'validated-screenshot-inference'},
    status: structuredClone(inferenceResult.properties.status),
    authorization: {
      type: 'object',
      additionalProperties: false,
      required: ['mode', 'approvedInputFingerprint'],
      properties: {
        mode: {enum: ['human-golden', 'provider-adapter']},
        providerId: {$ref: `#/$defs/${inferencePrefix}stableId`},
        approvedInputFingerprint: {$ref: `#/$defs/${inferencePrefix}sha256`},
      },
    },
    producer: {$ref: `#/$defs/${inferencePrefix}producer`},
    fingerprints: {
      type: 'object',
      additionalProperties: false,
      required: [
        'preprocessingRequest',
        'preprocessingOutput',
        'screenshot',
        'inferenceRequest',
        'inferenceResult',
        'designIr',
      ],
      properties: Object.fromEntries([
        'preprocessingRequest',
        'preprocessingOutput',
        'screenshot',
        'inferenceRequest',
        'inferenceResult',
        'designIr',
      ].map((name) => [name, {$ref: `#/$defs/${inferencePrefix}sha256`}]))
    },
    designIr: {type: 'object'},
    nodeEvidence: namespaceSchema(inferenceResult.properties.nodeEvidence, inferencePrefix),
    unresolvedQuestions: namespaceSchema(
      inferenceResult.properties.unresolvedQuestions,
      inferencePrefix,
    ),
    summary: namespaceSchema(inferenceResult.properties.summary, inferencePrefix),
    inferenceDiagnostics: namespaceSchema(
      inferenceResult.properties.diagnostics,
      inferencePrefix,
    ),
    validationFingerprint: {$ref: `#/$defs/${inferencePrefix}sha256`},
  },
};

export const SCREENSHOT_RESOLUTION_ARGUMENTS_SCHEMA = Object.freeze({
  type: 'object',
  additionalProperties: false,
  required: ['validatedInference', 'resolutionRequest'],
  properties: {
    validatedInference: {$ref: '#/$defs/validated_inference'},
    resolutionRequest: {$ref: `#/$defs/${resolutionPrefix}request`},
  },
  $defs: {
    ...namespacedDefinitions(SCREENSHOT_INFERENCE_SCHEMA.$defs, inferencePrefix),
    ...namespacedDefinitions(SCREENSHOT_RESOLUTION_SCHEMA.$defs, resolutionPrefix),
    validated_inference: validatedInference,
  },
});

export {DESIGN_IR_SCHEMA};
