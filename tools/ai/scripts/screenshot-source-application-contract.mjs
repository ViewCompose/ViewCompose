import {SCREENSHOT_GENERATION_ARGUMENTS_SCHEMA} from './screenshot-generation-contract.mjs';

const sha256 = {type: 'string', pattern: '^[a-f0-9]{64}$'};
const evidence = {
  type: 'object',
  required: ['schemaVersion', 'status', 'lineage', 'candidateEvaluation', 'designIr', 'evidenceFingerprint'],
  properties: {
    schemaVersion: {const: 1},
    status: {enum: ['complete', 'short-circuited']},
    lineage: {
      type: 'object',
      required: ['candidateDesignIrFingerprint'],
      properties: {candidateDesignIrFingerprint: sha256},
    },
    candidateEvaluation: {type: 'object'},
    designIr: {type: 'object'},
    evidenceFingerprint: sha256,
  },
};
const exactEvaluationInput = structuredClone(
  SCREENSHOT_GENERATION_ARGUMENTS_SCHEMA.oneOf.find((candidate) =>
    candidate.properties?.generationRequest?.properties?.mode?.const === 'compare-pixels'),
);
exactEvaluationInput.properties.patch = {type: 'object'};
const evaluationDefinitions = structuredClone(SCREENSHOT_GENERATION_ARGUMENTS_SCHEMA.$defs);

export const SCREENSHOT_SOURCE_APPLICATION_ARGUMENTS_SCHEMA = Object.freeze({
  type: 'object',
  oneOf: [
    {
      type: 'object',
      additionalProperties: false,
      required: ['operation', 'evaluationInput'],
      properties: {
        operation: {const: 'evaluate'},
        evaluationInput: exactEvaluationInput,
      },
    },
    {
      type: 'object',
      additionalProperties: false,
      required: ['operation', 'baselineEvidence', 'candidateEvidence'],
      properties: {
        operation: {const: 'propose'},
        baselineEvidence: evidence,
        candidateEvidence: evidence,
      },
    },
    {
      type: 'object',
      additionalProperties: false,
      required: [
        'operation',
        'projectRoot',
        'relativePath',
        'baselineEvidence',
        'candidateEvidence',
        'authorization',
        'resolutionResult',
        'generationRequest',
        'previewBindings',
        'pixelReference',
      ],
      properties: {
        operation: {const: 'prepare'},
        projectRoot: {type: 'string', minLength: 1, maxLength: 4096},
        relativePath: {
          type: 'string',
          minLength: 3,
          maxLength: 1024,
          pattern: '^(?!/)(?!.*(?:^|/)\\.\\.(?:/|$))(?!.*\\\\)[A-Za-z0-9._/-]+\\.kt$',
        },
        baselineEvidence: evidence,
        candidateEvidence: evidence,
        authorization: {type: 'object'},
        resolutionResult: {type: 'object'},
        generationRequest: {type: 'object'},
        previewBindings: {type: 'array', maxItems: 64},
        pixelReference: {type: 'object'},
      },
    },
  ],
  $defs: evaluationDefinitions,
});
