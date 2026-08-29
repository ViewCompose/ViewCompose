import {KNOWLEDGE_TOOL_DEFINITIONS} from './knowledge-retriever.mjs';

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
      'Convert the frozen Android layout XML subset to Design IR and deterministic ViewCompose Kotlin.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['source', 'mode'],
      properties: {
        source: {type: 'string', minLength: 1, maxLength: 262144},
        path: {type: 'string', minLength: 1, maxLength: 1024},
        mode: {enum: ['generate', 'compile']},
      },
    },
    defaultLimits: {
      timeoutMs: 120000,
      maxInputBytes: 262144,
      maxOutputBytes: 1048576,
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
