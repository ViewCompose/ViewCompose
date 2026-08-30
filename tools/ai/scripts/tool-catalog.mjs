import {KNOWLEDGE_TOOL_DEFINITIONS} from './knowledge-retriever.mjs';
import {SCREENSHOT_REQUEST_SCHEMA} from './screenshot-contract.mjs';
import {SCREENSHOT_INFERENCE_VALIDATION_ARGUMENTS_SCHEMA} from './screenshot-inference-contract.mjs';
import {SCREENSHOT_GENERATION_ARGUMENTS_SCHEMA} from './screenshot-generation-contract.mjs';
import {SCREENSHOT_RESOLUTION_ARGUMENTS_SCHEMA} from './screenshot-resolution-contract.mjs';

const stableId = {
  type: 'string',
  minLength: 1,
  maxLength: 128,
  pattern: '^[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}$',
};
const capabilityIds = {
  type: 'array',
  maxItems: 100,
  uniqueItems: true,
  items: stableId,
};
const artifactIds = {
  type: 'array',
  minItems: 1,
  maxItems: 30,
  uniqueItems: true,
  items: stableId,
};
const previewConfiguration = {
  type: 'object',
  additionalProperties: false,
  properties: {
    widthDp: {type: 'integer', minimum: 1, maximum: 2000},
    heightDp: {type: 'integer', minimum: -1, maximum: 4000},
    density: {type: 'number', minimum: 0.5, maximum: 8},
    fontScale: {type: 'number', minimum: 0.5, maximum: 3},
    localeTags: {
      type: 'array',
      minItems: 1,
      maxItems: 4,
      items: {type: 'string', minLength: 2, maxLength: 35},
    },
    layoutDirection: {enum: ['Ltr', 'Rtl']},
    theme: {enum: ['Light', 'Dark']},
  },
};
const previewArguments = {
  targetId: {type: 'string', minLength: 1, maxLength: 256},
  capabilityIds,
  configuration: previewConfiguration,
};
const previewLimits = {
  timeoutMs: 120000,
  maxInputBytes: 262144,
  maxOutputBytes: 1048576,
};
const xmlResourceRoots = {
  type: 'array',
  minItems: 1,
  maxItems: 16,
  uniqueItems: true,
  items: {type: 'string', minLength: 1, maxLength: 4096},
};
const xmlSourceRoots = {
  type: 'array',
  maxItems: 16,
  uniqueItems: true,
  items: {type: 'string', minLength: 1, maxLength: 4096},
};
const generatedPreviewAsset = {
  type: 'object',
  additionalProperties: false,
  required: ['mediaType', 'encoding', 'data', 'bytes', 'sha256', 'widthPx', 'heightPx'],
  properties: {
    mediaType: {const: 'image/png'},
    encoding: {const: 'base64'},
    data: {
      type: 'string',
      maxLength: 699052,
      pattern: '^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$',
    },
    bytes: {type: 'integer', minimum: 1, maximum: 524288},
    sha256: {type: 'string', pattern: '^[a-f0-9]{64}$'},
    widthPx: {type: 'integer', minimum: 1, maximum: 1024},
    heightPx: {type: 'integer', minimum: 1, maximum: 1024},
  },
};
const generatedPreviewBindings = {
  type: 'array',
  maxItems: 64,
  items: {
    oneOf: [
      {
        type: 'object',
        additionalProperties: false,
        required: ['kind', 'parameter', 'source', 'value'],
        properties: {
          kind: {const: 'string'},
          parameter: {type: 'string', pattern: '^[a-z][A-Za-z0-9]{0,127}$'},
          source: {type: 'string', pattern: '^@string/[a-z][a-z0-9_]*$'},
          value: {type: 'string', maxLength: 16384},
        },
      },
      {
        type: 'object',
        additionalProperties: false,
        required: ['kind', 'parameter', 'source', 'initialText'],
        properties: {
          kind: {const: 'text-field-state'},
          parameter: {type: 'string', pattern: '^[a-z][A-Za-z0-9]{0,127}$'},
          source: {type: 'string', pattern: '^[a-z][A-Za-z0-9]{0,127}$'},
          initialText: {type: 'string', maxLength: 16384},
        },
      },
      {
        type: 'object',
        additionalProperties: false,
        required: ['kind', 'parameter', 'source'],
        properties: {
          kind: {const: 'image-source'},
          parameter: {type: 'string', pattern: '^[a-z][A-Za-z0-9]{0,127}$'},
          source: {type: 'string', pattern: '^@drawable/[a-z][a-z0-9_]*$'},
          asset: generatedPreviewAsset,
        },
      },
    ],
  },
};

const executableDefinitions = {
  validate_code: {
    title: 'Validate ViewCompose Kotlin',
    description: 'Run deterministic static validation or the pinned hermetic Kotlin compiler.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['source'],
      properties: {
        mode: {enum: ['static', 'compile']},
        source: {type: 'string', minLength: 1, maxLength: 4194304},
        path: {type: 'string', minLength: 1, maxLength: 1024},
        artifactIds,
        capabilityIds,
      },
    },
    defaultLimits: {
      timeoutMs: 120000,
      maxInputBytes: 4194304,
      maxOutputBytes: 1048576,
    },
    evidenceLevel: 'static',
  },
  render_preview: {
    title: 'Render a ViewCompose Preview',
    description: 'Render one allowlisted compiled Preview target through the pinned Layoutlib lane.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['targetId'],
      properties: previewArguments,
    },
    defaultLimits: previewLimits,
    evidenceLevel: 'rendered',
  },
  diagnose_layout: {
    title: 'Diagnose a ViewCompose Layout',
    description: 'Interpret accepted Preview protocol layout facts without model or pixel inference.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['targetId'],
      properties: previewArguments,
    },
    defaultLimits: previewLimits,
    evidenceLevel: 'rendered',
  },
  analyze_project: {
    title: 'Analyze a ViewCompose Project',
    description: 'Inspect a bounded root read-only for framework facts, risks, and migration signals.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['projectRoot'],
      properties: {
        projectRoot: {type: 'string', minLength: 1, maxLength: 4096},
        requestedPath: {type: 'string', minLength: 1, maxLength: 4096},
        excluded: {
          type: 'array',
          maxItems: 100,
          uniqueItems: true,
          items: {type: 'string', minLength: 1, maxLength: 256},
        },
        maxFiles: {type: 'integer', minimum: 1, maximum: 10000},
        maxDepth: {type: 'integer', minimum: 1, maximum: 32},
      },
    },
    defaultLimits: {
      timeoutMs: 30000,
      maxInputBytes: 4194304,
      maxOutputBytes: 1048576,
    },
    evidenceLevel: 'static',
  },
  convert_xml_to_viewcompose: {
    title: 'Convert Android XML to ViewCompose',
    description:
      'Generate, compile, or source-bind, render, and compare deterministic ViewCompose Kotlin from supported Android XML.',
    inputSchema: {
      type: 'object',
      oneOf: [
        {
          type: 'object',
          additionalProperties: false,
          required: ['source', 'mode'],
          properties: {
            source: {type: 'string', minLength: 1, maxLength: 262144},
            path: {type: 'string', minLength: 1, maxLength: 1024},
            mode: {enum: ['generate', 'compile']},
          },
        },
        {
          type: 'object',
          additionalProperties: false,
          required: ['projectRoot', 'layoutPath', 'resourceRoots', 'mode'],
          properties: {
            projectRoot: {type: 'string', minLength: 1, maxLength: 4096},
            layoutPath: {type: 'string', minLength: 1, maxLength: 4096},
            resourceRoots: xmlResourceRoots,
            sourceRoots: xmlSourceRoots,
            mode: {enum: ['generate', 'compile']},
          },
        },
        {
          type: 'object',
          additionalProperties: false,
          required: ['source', 'mode', 'previewBindings'],
          properties: {
            source: {type: 'string', minLength: 1, maxLength: 262144},
            path: {type: 'string', minLength: 1, maxLength: 1024},
            mode: {const: 'render'},
            previewBindings: generatedPreviewBindings,
          },
        },
        {
          type: 'object',
          additionalProperties: false,
          required: [
            'projectRoot',
            'layoutPath',
            'resourceRoots',
            'mode',
            'previewBindings',
          ],
          properties: {
            projectRoot: {type: 'string', minLength: 1, maxLength: 4096},
            layoutPath: {type: 'string', minLength: 1, maxLength: 4096},
            resourceRoots: xmlResourceRoots,
            sourceRoots: xmlSourceRoots,
            mode: {const: 'render'},
            previewBindings: generatedPreviewBindings,
          },
        },
      ],
    },
    defaultLimits: {
      timeoutMs: 120000,
      maxInputBytes: 4194304,
      maxOutputBytes: 1048576,
    },
    evidenceLevel: 'static',
  },
  prepare_screenshot: {
    title: 'Prepare a Screenshot for ViewCompose Generation',
    description:
      'Verify, crop, explicitly redact, and canonically encode one embedded PNG without provider or network access.',
    inputSchema: SCREENSHOT_REQUEST_SCHEMA,
    defaultLimits: {
      timeoutMs: 10000,
      maxInputBytes: 2000000,
      maxOutputBytes: 2000000,
    },
    evidenceLevel: 'static',
  },
  validate_screenshot_inference: {
    title: 'Validate and Import Screenshot Design Inference',
    description:
      'Reproduce screenshot preprocessing and validate provider-neutral Design IR lineage, evidence, uncertainty, and consent without provider or network execution.',
    inputSchema: SCREENSHOT_INFERENCE_VALIDATION_ARGUMENTS_SCHEMA,
    defaultLimits: {
      timeoutMs: 10000,
      maxInputBytes: 4000000,
      maxOutputBytes: 2000000,
    },
    evidenceLevel: 'static',
  },
  resolve_screenshot_inference: {
    title: 'Resolve Validated Screenshot Inference',
    description:
      'Apply exact typed human answers to one validated screenshot inference without executable content, provider execution, or network access.',
    inputSchema: SCREENSHOT_RESOLUTION_ARGUMENTS_SCHEMA,
    defaultLimits: {
      timeoutMs: 10000,
      maxInputBytes: 2000000,
      maxOutputBytes: 2000000,
    },
    evidenceLevel: 'static',
  },
  generate_screenshot_viewcompose: {
    title: 'Generate ViewCompose from Resolved Screenshot Design IR',
    description:
      'Generate, hermetically compile, source-bind and render, exactly compare layout evidence, or compare an eligible canonical pixel reference from one resolved screenshot result without provider, network, callback-source, or inspected-project build execution.',
    inputSchema: SCREENSHOT_GENERATION_ARGUMENTS_SCHEMA,
    defaultLimits: {
      timeoutMs: 120000,
      maxInputBytes: 2000000,
      maxOutputBytes: 2000000,
    },
    evidenceLevel: 'static',
  },
};

const knowledgeDefaults = {
  timeoutMs: 10000,
  maxInputBytes: 262144,
  maxOutputBytes: 1048576,
};
const knowledgeDefinitions = Object.fromEntries(
  Object.entries(KNOWLEDGE_TOOL_DEFINITIONS).map(([name, definition]) => [name, {
    ...definition,
    title: {
      get_api_reference: 'Get ViewCompose API Reference',
      get_component_reference: 'Get ViewCompose Component Reference',
      search_component: 'Search ViewCompose Components',
      get_sample: 'Get a ViewCompose Sample',
    }[name],
    defaultLimits: knowledgeDefaults,
    evidenceLevel: 'knowledge',
  }]),
);

export const TOOL_DEFINITIONS = Object.freeze({
  ...knowledgeDefinitions,
  ...executableDefinitions,
});

export const TOOL_NAMES = Object.freeze([
  'get_api_reference',
  'get_component_reference',
  'search_component',
  'get_sample',
  'validate_code',
  'render_preview',
  'diagnose_layout',
  'analyze_project',
  'convert_xml_to_viewcompose',
  'prepare_screenshot',
  'validate_screenshot_inference',
  'resolve_screenshot_inference',
  'generate_screenshot_viewcompose',
]);

export function publicToolDefinition(name) {
  const definition = TOOL_DEFINITIONS[name];
  if (!definition) return null;
  return {
    name,
    title: definition.title,
    description: definition.description,
    inputSchema: definition.inputSchema,
    annotations: {
      readOnlyHint: true,
      destructiveHint: false,
      idempotentHint: true,
      openWorldHint: false,
    },
  };
}
