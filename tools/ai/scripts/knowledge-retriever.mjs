import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {validateSchemaValue} from './schema-validator.mjs';
import {diagnostic, loadKnowledgeManifest, toolResult} from './tool-core.mjs';

const bundleRoot = fileURLToPath(new URL('../generated/current-source/', import.meta.url));
const supportedVersionLane = 'current-source';
const stopWords = new Set(['a', 'an', 'and', 'for', 'of', 'the', 'to', 'use', 'using', 'with']);

export const KNOWLEDGE_TOOL_DEFINITIONS = Object.freeze({
  get_api_reference: {
    description: 'Resolve one exact ViewCompose symbol, capability, or artifact in an immutable bundle.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['versionLane', 'identifier'],
      properties: {
        versionLane: {const: supportedVersionLane},
        identifier: {type: 'string', minLength: 1, maxLength: 512},
      },
    },
  },
  get_component_reference: {
    description: 'Resolve one component with overload parameters, ownership, rules, and compiled sample.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['versionLane', 'name'],
      properties: {
        versionLane: {const: supportedVersionLane},
        name: {type: 'string', minLength: 1, maxLength: 512},
        artifactId: {type: 'string', minLength: 1, maxLength: 128},
        receiver: {type: 'string', minLength: 1, maxLength: 256},
      },
    },
  },
  search_component: {
    description: 'Rank governed ViewCompose symbols deterministically with exact lane and optional ownership filters.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['versionLane', 'query'],
      properties: {
        versionLane: {const: supportedVersionLane},
        query: {type: 'string', minLength: 1, maxLength: 512},
        limit: {type: 'integer', minimum: 1, maximum: 50},
        artifactId: {type: 'string', minLength: 1, maxLength: 128},
        artifactVersion: {type: 'string', minLength: 1, maxLength: 128},
        capabilityId: {type: 'string', minLength: 1, maxLength: 128},
        kind: {
          enum: ['component', 'dsl', 'host', 'integration', 'modifier', 'tooling'],
        },
      },
    },
  },
  get_sample: {
    description: 'Return one exact compiled or explicitly non-executable sample and its ownership evidence.',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['versionLane', 'sampleId'],
      properties: {
        versionLane: {const: supportedVersionLane},
        sampleId: {type: 'string', minLength: 1, maxLength: 256},
      },
    },
  },
});

let knowledgeIndexPromise;

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function parseJsonLines(content) {
  return content.trimEnd().split('\n').filter(Boolean).map(JSON.parse);
}

function addToIndex(index, key, value) {
  const values = index.get(key) ?? [];
  values.push(value);
  index.set(key, values);
}

export function loadKnowledgeIndex() {
  knowledgeIndexPromise ??= loadKnowledgeManifest().then(async (manifest) => {
    if (
      manifest.schemaVersion !== 1 ||
      manifest.framework?.versionLane !== supportedVersionLane ||
      manifest.framework?.identity !== manifest.source?.revision
    ) {
      throw new Error('Knowledge Bundle manifest identity is outside the supported retrieval lane.');
    }
    const requiredPaths = [
      'artifacts.json',
      'capabilities.json',
      'llms-full.txt',
      'llms.txt',
      'rules.json',
      'samples.jsonl',
      'symbols.jsonl',
    ];
    const declaredPaths = manifest.files.map((file) => file.path).sort();
    if (JSON.stringify(declaredPaths) !== JSON.stringify(requiredPaths)) {
      throw new Error('Knowledge Bundle manifest contains an unexpected file set.');
    }
    const contents = new Map();
    for (const file of manifest.files) {
      const content = await readFile(resolve(bundleRoot, file.path));
      if (content.length !== file.bytes || sha256(content) !== file.sha256) {
        throw new Error(`Knowledge Bundle integrity check failed for ${file.path}.`);
      }
      contents.set(file.path, content.toString('utf8'));
    }
    const computedBundleFingerprint = sha256(
      manifest.files.map((file) => `${file.path}\0${file.sha256}\n`).join(''),
    );
    if (computedBundleFingerprint !== manifest.bundleFingerprint) {
      throw new Error('Knowledge Bundle manifest fingerprint does not match its file descriptors.');
    }

    const artifacts = JSON.parse(contents.get('artifacts.json')).artifacts;
    const capabilities = JSON.parse(contents.get('capabilities.json')).capabilities;
    const symbols = parseJsonLines(contents.get('symbols.jsonl'));
    const samples = parseJsonLines(contents.get('samples.jsonl'));
    const rules = JSON.parse(contents.get('rules.json')).rules;
    const actualCounts = {
      artifacts: artifacts.length,
      capabilities: capabilities.length,
      symbols: symbols.length,
      samples: samples.length,
      rules: rules.length,
    };
    if (Object.entries(actualCounts).some(([name, count]) => manifest.counts[name] !== count)) {
      throw new Error('Knowledge Bundle parsed counts do not match its manifest.');
    }

    const byArtifactId = new Map(artifacts.map((entry) => [entry.artifact, entry]));
    const byCapabilityId = new Map(capabilities.map((entry) => [entry.capabilityId, entry]));
    const bySymbolId = new Map(symbols.map((entry) => [entry.symbolId, entry]));
    const bySampleId = new Map(samples.map((entry) => [entry.sampleId, entry]));
    const byRuleCode = new Map(rules.map((entry) => [entry.code, entry]));
    const symbolsBySimpleName = new Map();
    const symbolsByImport = new Map();
    const symbolsByCapability = new Map();
    const capabilitiesByArtifact = new Map();
    for (const symbol of symbols) {
      addToIndex(symbolsBySimpleName, symbol.simpleName.toLowerCase(), symbol);
      addToIndex(symbolsByImport, `${symbol.namespace}.${symbol.simpleName}`, symbol);
      addToIndex(symbolsByCapability, symbol.capabilityId, symbol);
    }
    for (const capability of capabilities) {
      addToIndex(capabilitiesByArtifact, capability.artifactId, capability);
    }
    return {
      manifest,
      artifacts,
      capabilities,
      symbols,
      samples,
      rules,
      byArtifactId,
      byCapabilityId,
      bySymbolId,
      bySampleId,
      byRuleCode,
      symbolsBySimpleName,
      symbolsByImport,
      symbolsByCapability,
      capabilitiesByArtifact,
    };
  });
  return knowledgeIndexPromise;
}

function normalizeText(value) {
  return value
    .normalize('NFKC')
    .replace(/([\p{Ll}\d])([\p{Lu}])/gu, '$1 $2')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim();
}

function textTokens(value) {
  return normalizeText(value).split(' ').filter((token) => token && !stopWords.has(token));
}

function invalidArguments(tool, requestId, violations) {
  return toolResult({
    requestId,
    tool,
    status: 'invalid',
    level: 'knowledge',
    diagnostics: [diagnostic({
      code: 'VC-AI-ARGUMENTS-INVALID',
      severity: 'error',
      message: `${tool} arguments violate the fixed schema: ${violations.slice(0, 3).join('; ')}`,
      nextAction: 'Use the tool catalog schema and select the immutable current-source lane.',
    })],
  });
}

function notFound(tool, requestId, message, nextAction) {
  return toolResult({
    requestId,
    tool,
    status: 'invalid',
    level: 'knowledge',
    diagnostics: [diagnostic({
      code: 'VC-AI-REFERENCE-NOT-FOUND',
      severity: 'error',
      message,
      nextAction,
    })],
  });
}

function validateArguments(tool, arguments_) {
  const definition = KNOWLEDGE_TOOL_DEFINITIONS[tool];
  if (!definition) return [`Unsupported knowledge tool ${tool}`];
  return validateSchemaValue(arguments_, definition.inputSchema);
}

function artifactSummary(artifact) {
  return artifact ? {
    artifactId: artifact.artifact,
    version: artifact.version,
    versionState: artifact.versionState,
    apiReference: artifact.apiReference,
    moduleManual: artifact.moduleManual,
  } : null;
}

function symbolSummary(symbol, index) {
  const artifact = index.byArtifactId.get(symbol.artifactId);
  const capability = index.byCapabilityId.get(symbol.capabilityId);
  return {
    symbolId: symbol.symbolId,
    importName: `${symbol.namespace}.${symbol.simpleName}`,
    simpleName: symbol.simpleName,
    kind: symbol.kind,
    receiver: symbol.receiver,
    artifactId: symbol.artifactId,
    artifactVersion: artifact?.version ?? null,
    capabilityId: symbol.capabilityId,
    capabilityVersion: capability?.versionState ?? null,
    overloadCount: symbol.overloadCount,
    summaries: [...new Set(symbol.declarations.map((entry) => entry.summary).filter(Boolean))],
    source: symbol.source,
    sourceFingerprint: symbol.sourceFingerprint,
  };
}

function sampleForCapability(capability, index) {
  if (!capability?.sample?.sampleId) return null;
  return index.bySampleId.get(capability.sample.sampleId) ?? null;
}

function searchScore(symbol, rawQuery, normalizedQuery, queryTokens) {
  const simple = normalizeText(symbol.simpleName);
  const importName = `${symbol.namespace}.${symbol.simpleName}`;
  const normalizedImport = normalizeText(importName);
  const normalizedSymbolId = normalizeText(symbol.symbolId);
  const normalizedCapability = normalizeText(symbol.capabilityId);
  const lastSegment = normalizeText(rawQuery.split('.').at(-1));
  const fields = [
    symbol.symbolId,
    importName,
    symbol.simpleName,
    symbol.receiver,
    symbol.kind,
    symbol.artifactId,
    symbol.capabilityId,
    ...(symbol.searchTerms ?? []),
    ...symbol.declarations.flatMap((entry) => [entry.signature, entry.summary]),
  ].filter(Boolean);
  const normalizedFields = fields.map(normalizeText);
  const candidateTokens = new Set(normalizedFields.flatMap((value) => value.split(' ')));
  let score = 0;
  if (symbol.symbolId.toLowerCase() === rawQuery.toLowerCase()) score += 100_000;
  if (importName.toLowerCase() === rawQuery.toLowerCase()) score += 95_000;
  if (simple === normalizedQuery) score += 90_000;
  if (normalizedCapability === normalizedQuery) score += 85_000;
  if ((symbol.searchTerms ?? []).some((term) => normalizeText(term) === normalizedQuery)) score += 80_000;
  if (lastSegment === simple) score += 50_000;
  if (normalizedSymbolId.includes(normalizedQuery) || normalizedImport.includes(normalizedQuery)) {
    score += 20_000;
  }
  let matchedTokens = 0;
  for (const token of queryTokens) {
    if (candidateTokens.has(token)) {
      score += 1_000;
      matchedTokens += 1;
    } else if ([...candidateTokens].some((candidate) => candidate.startsWith(token) || token.startsWith(candidate))) {
      score += 250;
      matchedTokens += 1;
    }
  }
  if (queryTokens.length > 0) score += Math.round((matchedTokens / queryTokens.length) * 1_000);
  return score;
}

function splitTopLevel(value, separator) {
  const parts = [];
  let start = 0;
  let round = 0;
  let square = 0;
  let angle = 0;
  let curly = 0;
  let quote = null;
  let escaped = false;
  for (let index = 0; index < value.length; index += 1) {
    const character = value[index];
    if (quote) {
      if (escaped) escaped = false;
      else if (character === '\\') escaped = true;
      else if (character === quote) quote = null;
      continue;
    }
    if (character === '"' || character === '\'') quote = character;
    else if (character === '(') round += 1;
    else if (character === ')') round = Math.max(0, round - 1);
    else if (character === '[') square += 1;
    else if (character === ']') square = Math.max(0, square - 1);
    else if (character === '<') angle += 1;
    else if (character === '>') angle = Math.max(0, angle - 1);
    else if (character === '{') curly += 1;
    else if (character === '}') curly = Math.max(0, curly - 1);
    else if (character === separator && round === 0 && square === 0 && angle === 0 && curly === 0) {
      parts.push(value.slice(start, index));
      start = index + 1;
    }
  }
  parts.push(value.slice(start));
  return parts;
}

function parameterList(signature) {
  const open = signature.indexOf('(');
  if (open < 0) return [];
  let depth = 0;
  let close = -1;
  let quote = null;
  let escaped = false;
  for (let index = open; index < signature.length; index += 1) {
    const character = signature[index];
    if (quote) {
      if (escaped) escaped = false;
      else if (character === '\\') escaped = true;
      else if (character === quote) quote = null;
      continue;
    }
    if (character === '"' || character === '\'') quote = character;
    else if (character === '(') depth += 1;
    else if (character === ')') {
      depth -= 1;
      if (depth === 0) {
        close = index;
        break;
      }
    }
  }
  if (close < 0) return [];
  return splitTopLevel(signature.slice(open + 1, close), ',')
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((entry) => {
      const colonParts = splitTopLevel(entry, ':');
      if (colonParts.length < 2) return {declaration: entry};
      const name = colonParts.shift().trim().split(/\s+/u).at(-1);
      const typeAndDefault = colonParts.join(':').trim();
      const defaultParts = splitTopLevel(typeAndDefault, '=');
      const type = defaultParts.shift().trim();
      const defaultValue = defaultParts.length > 0 ? defaultParts.join('=').trim() : undefined;
      return Object.fromEntries(Object.entries({
        name,
        type,
        default: defaultValue,
        required: defaultValue === undefined,
      }).filter(([, value]) => value !== undefined));
    });
}

function applicableRules(symbol, overloads, index) {
  const selected = new Map();
  const include = (code, applicability, reason) => {
    const rule = index.byRuleCode.get(code);
    if (rule) selected.set(code, {...rule, applicability, reason});
  };
  include('VC-AI-ARTIFACT-REQUIRED', 'general', 'Every retrieved symbol carries exact artifact ownership.');
  include('VC-AI-EVIDENCE-DEPTH', 'general', 'Knowledge retrieval does not imply compilation or rendering.');
  include('VC-AI-UNKNOWN-SYMBOL', 'general', 'Calls must resolve to this exact bundle declaration.');
  const parameters = overloads.flatMap((entry) => entry.parameters);
  if (parameters.some((entry) => entry.name === 'modifier')) {
    include('VC-AI-MODIFIER-ORDER', 'signature', 'The component accepts an ordered Modifier chain.');
  }
  if (parameters.some((entry) => /\bUi(?:Dp|Sp)\b/u.test(entry.type ?? ''))) {
    include('VC-AI-UNIT-DP-SP', 'signature', 'The component signature contains typed UI dimensions.');
  }
  if (['Image', 'Icon'].includes(symbol.simpleName)) {
    include('VC-AI-A11Y-IMAGE-DESCRIPTION', 'component', 'The component emits visual content requiring an explicit description decision.');
  }
  if (parameters.some((entry) => /^(?:onClick|onLongClick)$/u.test(entry.name ?? ''))) {
    include('VC-AI-A11Y-TOUCH-TARGET', 'signature', 'The component exposes direct pointer interaction.');
  }
  return [...selected.values()];
}

export async function searchComponents(arguments_, {requestId = 'search-component'} = {}) {
  const started = performance.now();
  const violations = validateArguments('search_component', arguments_);
  if (violations.length > 0) return invalidArguments('search_component', requestId, violations);
  const index = await loadKnowledgeIndex();
  const normalizedQuery = normalizeText(arguments_.query);
  const queryTokens = textTokens(arguments_.query);
  if (!normalizedQuery || queryTokens.length === 0) {
    return invalidArguments('search_component', requestId, ['query has no searchable tokens']);
  }
  const candidates = index.symbols.filter((symbol) => {
    const artifact = index.byArtifactId.get(symbol.artifactId);
    return (!arguments_.artifactId || symbol.artifactId === arguments_.artifactId) &&
      (!arguments_.artifactVersion || artifact?.version === arguments_.artifactVersion) &&
      (!arguments_.capabilityId || symbol.capabilityId === arguments_.capabilityId) &&
      (!arguments_.kind || symbol.kind === arguments_.kind);
  });
  const limit = arguments_.limit ?? 10;
  const ranked = candidates.map((symbol) => ({
    symbol,
    score: searchScore(symbol, arguments_.query, normalizedQuery, queryTokens),
  })).filter((entry) => entry.score > 0).sort((left, right) =>
    right.score - left.score ||
    left.symbol.simpleName.localeCompare(right.symbol.simpleName) ||
    left.symbol.symbolId.localeCompare(right.symbol.symbolId),
  );
  const results = ranked.slice(0, limit).map((entry, index_) => ({
    rank: index_ + 1,
    score: entry.score,
    ...symbolSummary(entry.symbol, index),
  }));
  return toolResult({
    requestId,
    tool: 'search_component',
    status: 'success',
    level: 'knowledge',
    data: {
      query: arguments_.query,
      versionLane: supportedVersionLane,
      filters: Object.fromEntries(Object.entries({
        artifactId: arguments_.artifactId,
        artifactVersion: arguments_.artifactVersion,
        capabilityId: arguments_.capabilityId,
        kind: arguments_.kind,
      }).filter(([, value]) => value !== undefined)),
      candidateCount: candidates.length,
      matchCount: ranked.length,
      results,
    },
    elapsedMs: performance.now() - started,
  });
}

export async function retrieveApiReference(arguments_, {requestId = 'get-api-reference'} = {}) {
  const started = performance.now();
  const violations = validateArguments('get_api_reference', arguments_);
  if (violations.length > 0) return invalidArguments('get_api_reference', requestId, violations);
  const index = await loadKnowledgeIndex();
  const symbol = index.bySymbolId.get(arguments_.identifier);
  if (symbol) {
    const capability = index.byCapabilityId.get(symbol.capabilityId);
    return toolResult({
      requestId,
      tool: 'get_api_reference',
      status: 'success',
      level: 'knowledge',
      data: {
        referenceType: 'symbol',
        symbol,
        artifact: artifactSummary(index.byArtifactId.get(symbol.artifactId)),
        capability,
        sample: sampleForCapability(capability, index),
      },
      elapsedMs: performance.now() - started,
    });
  }
  const capability = index.byCapabilityId.get(arguments_.identifier);
  if (capability) {
    return toolResult({
      requestId,
      tool: 'get_api_reference',
      status: 'success',
      level: 'knowledge',
      data: {
        referenceType: 'capability',
        capability,
        artifact: artifactSummary(index.byArtifactId.get(capability.artifactId)),
        symbols: (index.symbolsByCapability.get(capability.capabilityId) ?? [])
          .map((entry) => symbolSummary(entry, index)),
        sample: sampleForCapability(capability, index),
      },
      elapsedMs: performance.now() - started,
    });
  }
  const artifact = index.byArtifactId.get(arguments_.identifier);
  if (artifact) {
    const capabilities = index.capabilitiesByArtifact.get(artifact.artifact) ?? [];
    return toolResult({
      requestId,
      tool: 'get_api_reference',
      status: 'success',
      level: 'knowledge',
      data: {
        referenceType: 'artifact',
        artifact: artifactSummary(artifact),
        capabilities: capabilities.map((entry) => ({
          capabilityId: entry.capabilityId,
          kind: entry.kind,
          versionState: entry.versionState,
          sample: entry.sample,
          symbolCount: (index.symbolsByCapability.get(entry.capabilityId) ?? []).length,
        })),
      },
      elapsedMs: performance.now() - started,
    });
  }
  return notFound(
    'get_api_reference',
    requestId,
    `No exact symbol, capability, or artifact has identifier ${arguments_.identifier}.`,
    'Run search_component first and use one returned stable identifier.',
  );
}

export async function retrieveComponentReference(arguments_, {requestId = 'get-component-reference'} = {}) {
  const started = performance.now();
  const violations = validateArguments('get_component_reference', arguments_);
  if (violations.length > 0) return invalidArguments('get_component_reference', requestId, violations);
  const index = await loadKnowledgeIndex();
  let candidates = [];
  const exact = index.bySymbolId.get(arguments_.name);
  if (exact) candidates = [exact];
  else if (index.symbolsByImport.has(arguments_.name)) candidates = index.symbolsByImport.get(arguments_.name);
  else candidates = index.symbolsBySimpleName.get(arguments_.name.toLowerCase()) ?? [];
  candidates = candidates.filter((entry) => entry.kind === 'component');
  if (arguments_.artifactId) candidates = candidates.filter((entry) => entry.artifactId === arguments_.artifactId);
  if (arguments_.receiver) candidates = candidates.filter((entry) => entry.receiver === arguments_.receiver);
  if (candidates.length === 0) {
    return notFound(
      'get_component_reference',
      requestId,
      `No exact component matches ${arguments_.name} and the selected ownership filters.`,
      'Run search_component with kind component, then select an exact symbolId or receiver.',
    );
  }
  if (candidates.length > 1) {
    return toolResult({
      requestId,
      tool: 'get_component_reference',
      status: 'invalid',
      level: 'knowledge',
      diagnostics: [diagnostic({
        code: 'VC-AI-REFERENCE-AMBIGUOUS',
        severity: 'error',
        message: `${arguments_.name} matches ${candidates.length} governed component receivers.`,
        nextAction: 'Select one exact symbolId or add the documented receiver filter.',
      })],
      data: {candidates: candidates.map((entry) => symbolSummary(entry, index))},
      elapsedMs: performance.now() - started,
    });
  }
  const symbol = candidates[0];
  const capability = index.byCapabilityId.get(symbol.capabilityId);
  const overloads = symbol.declarations.map((declaration) => ({
    ...declaration,
    parameters: parameterList(declaration.signature),
  }));
  return toolResult({
    requestId,
    tool: 'get_component_reference',
    status: 'success',
    level: 'knowledge',
    data: {
      symbol: {...symbol, declarations: overloads},
      importName: `${symbol.namespace}.${symbol.simpleName}`,
      artifact: artifactSummary(index.byArtifactId.get(symbol.artifactId)),
      capability,
      sample: sampleForCapability(capability, index),
      rules: applicableRules(symbol, overloads, index),
    },
    elapsedMs: performance.now() - started,
  });
}

export async function retrieveSample(arguments_, {requestId = 'get-sample'} = {}) {
  const started = performance.now();
  const violations = validateArguments('get_sample', arguments_);
  if (violations.length > 0) return invalidArguments('get_sample', requestId, violations);
  const index = await loadKnowledgeIndex();
  const sample = index.bySampleId.get(arguments_.sampleId);
  if (!sample) {
    return notFound(
      'get_sample',
      requestId,
      `No exact sample has identifier ${arguments_.sampleId}.`,
      'Retrieve a capability or component reference and use its declared sampleId.',
    );
  }
  const capability = index.byCapabilityId.get(sample.capabilityId);
  return toolResult({
    requestId,
    tool: 'get_sample',
    status: 'success',
    level: 'knowledge',
    data: {
      sample,
      executable: sample.sampleClass === 'compiled-region',
      capability,
      artifact: artifactSummary(index.byArtifactId.get(capability?.artifactId)),
    },
    elapsedMs: performance.now() - started,
  });
}
