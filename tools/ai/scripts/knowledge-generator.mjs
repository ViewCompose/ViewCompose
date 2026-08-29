import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {mkdir, readFile, readdir, writeFile} from 'node:fs/promises';
import {dirname, relative, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const modulePath = fileURLToPath(import.meta.url);
const scriptDirectory = dirname(modulePath);
export const aiRoot = resolve(scriptDirectory, '..');
export const repositoryRoot = resolve(aiRoot, '../..');
export const generatedDirectory = resolve(aiRoot, 'generated/current-source');
export const hostedLlmsPath = resolve(repositoryRoot, 'website/static/llms.txt');
const capabilityRecordDirectory = resolve(
  repositoryRoot,
  'docs/project/records/documentation-governance-v2/capabilities',
);
const sampleRecordDirectory = resolve(
  repositoryRoot,
  'docs/project/records/documentation-governance-v2/samples',
);
const capabilityReferencePath = resolve(
  repositoryRoot,
  'website/src/data/capability-reference.json',
);
const rulesPath = resolve(aiRoot, 'knowledge/rules.json');
const generatorBaseVersion = '1.0.0';

export function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function sortValue(value) {
  if (Array.isArray(value)) return value.map(sortValue);
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map((key) => [key, sortValue(value[key])]),
    );
  }
  return value;
}

export function stableJson(value) {
  return `${JSON.stringify(sortValue(value), null, 2)}\n`;
}

function jsonLine(value) {
  return JSON.stringify(sortValue(value));
}

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

async function readJsonDirectory(path) {
  const names = (await readdir(path)).filter((name) => name.endsWith('.json')).sort();
  return Promise.all(names.map((name) => readJson(resolve(path, name))));
}

function normalizeWhitespace(value) {
  return value.replace(/\s+/gu, ' ').trim();
}

function trimIndent(value) {
  const lines = value.replaceAll('\r\n', '\n').split('\n');
  while (lines.length > 0 && lines[0].trim() === '') lines.shift();
  while (lines.length > 0 && lines.at(-1).trim() === '') lines.pop();
  const indentation = lines
    .filter((line) => line.trim() !== '')
    .map((line) => line.match(/^\s*/u)[0].length);
  const minimum = indentation.length === 0 ? 0 : Math.min(...indentation);
  return lines.map((line) => line.slice(minimum)).join('\n');
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&');
}

function lineNumberAt(source, offset) {
  return source.slice(0, offset).split('\n').length;
}

function kdocSummaryBefore(source, offset) {
  const prefix = source.slice(0, offset);
  const start = prefix.lastIndexOf('/**');
  if (start < 0) return null;
  const end = prefix.indexOf('*/', start);
  if (end < 0) return null;
  const trailing = prefix.slice(end + 2);
  if (!/^\s*(?:@[A-Za-z][^\n]*\s*)*$/u.test(trailing)) return null;
  const lines = prefix.slice(start + 3, end)
    .split('\n')
    .map((line) => line.replace(/^\s*\* ?/u, '').trimEnd());
  const narrative = [];
  for (const line of lines) {
    if (line.trimStart().startsWith('@')) break;
    if (line.trim() === '' && narrative.some((item) => item.trim() !== '')) break;
    narrative.push(line.trim());
  }
  const summary = normalizeWhitespace(narrative.join(' '));
  return summary.length === 0 ? null : summary;
}

function findFunctionDeclarations(source, targetName) {
  const lines = source.replaceAll('\r\n', '\n').split('\n');
  const target = new RegExp(`(?:\\.|\\b)${escapeRegex(targetName)}\\s*\\(`, 'u');
  const results = [];
  let sourceOffset = 0;
  let inBlockComment = false;

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    const trimmed = line.trimStart();
    if (inBlockComment) {
      if (trimmed.includes('*/')) inBlockComment = false;
      sourceOffset += line.length + 1;
      continue;
    }
    if (trimmed.startsWith('/*')) {
      if (!trimmed.includes('*/')) inBlockComment = true;
      sourceOffset += line.length + 1;
      continue;
    }
    if (trimmed.startsWith('//') || !/\bfun\b/u.test(line)) {
      sourceOffset += line.length + 1;
      continue;
    }
    const candidate = lines.slice(index, index + 100).join('\n');
    const functionOffset = candidate.search(/\bfun\b/u);
    const declarationText = candidate.slice(functionOffset);
    const nameMatch = target.exec(declarationText);
    if (!nameMatch) {
      sourceOffset += line.length + 1;
      continue;
    }
    const firstBody = declarationText.search(/[={]/u);
    if (firstBody >= 0 && nameMatch.index > firstBody) {
      sourceOffset += line.length + 1;
      continue;
    }
    const openParenthesis = declarationText.indexOf('(', nameMatch.index);
    let depth = 0;
    let closeParenthesis = -1;
    let quote = null;
    let escaped = false;
    for (let cursor = openParenthesis; cursor < declarationText.length; cursor += 1) {
      const character = declarationText[cursor];
      if (quote) {
        if (escaped) escaped = false;
        else if (character === '\\') escaped = true;
        else if (character === quote) quote = null;
        continue;
      }
      if (character === '"' || character === '\'') {
        quote = character;
        continue;
      }
      if (character === '(') depth += 1;
      else if (character === ')') {
        depth -= 1;
        if (depth === 0) {
          closeParenthesis = cursor;
          break;
        }
      }
    }
    if (closeParenthesis < 0) {
      sourceOffset += line.length + 1;
      continue;
    }
    let end = closeParenthesis + 1;
    let genericDepth = 0;
    while (end < declarationText.length) {
      const character = declarationText[end];
      if (character === '<') genericDepth += 1;
      else if (character === '>') genericDepth = Math.max(0, genericDepth - 1);
      if (genericDepth === 0 && (character === '{' || character === '=')) break;
      if (genericDepth === 0 && character === '\n') {
        const rest = declarationText.slice(end + 1).trimStart();
        if (/^(?:fun|class|interface|object|typealias|val|var|\/\*\*)\b/u.test(rest)) break;
      }
      end += 1;
    }
    const signature = normalizeWhitespace(declarationText.slice(0, end));
    const absoluteOffset = sourceOffset + functionOffset;
    results.push({
      signature,
      signatureHash: sha256(signature),
      line: lineNumberAt(source, absoluteOffset),
      summary: kdocSummaryBefore(source, sourceOffset),
    });
    sourceOffset += line.length + 1;
  }
  return results.filter(
    (item, index) => results.findIndex((candidate) => candidate.signature === item.signature) === index,
  );
}

function findTypeDeclarations(source, targetName) {
  const lines = source.replaceAll('\r\n', '\n').split('\n');
  const target = new RegExp(
    `\\b(?:class|interface|object|typealias)\\s+${escapeRegex(targetName)}\\b`,
    'u',
  );
  const results = [];
  let sourceOffset = 0;
  let inBlockComment = false;
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    const trimmed = line.trimStart();
    if (inBlockComment) {
      if (trimmed.includes('*/')) inBlockComment = false;
      sourceOffset += line.length + 1;
      continue;
    }
    if (trimmed.startsWith('/*')) {
      if (!trimmed.includes('*/')) inBlockComment = true;
      sourceOffset += line.length + 1;
      continue;
    }
    if (trimmed.startsWith('//')) {
      sourceOffset += line.length + 1;
      continue;
    }
    const candidate = lines.slice(index, index + 100).join('\n');
    const match = target.exec(candidate);
    if (!match || match.index > line.length + 1) {
      sourceOffset += line.length + 1;
      continue;
    }
    const declarationStart = candidate.lastIndexOf('\n', match.index) + 1;
    const declaration = candidate.slice(declarationStart);
    const typealias = /\btypealias\b/u.test(declaration.slice(0, match.index - declarationStart + 20));
    let round = 0;
    let angle = 0;
    let square = 0;
    let end = declaration.length;
    for (let cursor = 0; cursor < declaration.length; cursor += 1) {
      const character = declaration[cursor];
      if (character === '(') round += 1;
      else if (character === ')') round = Math.max(0, round - 1);
      else if (character === '<') angle += 1;
      else if (character === '>') angle = Math.max(0, angle - 1);
      else if (character === '[') square += 1;
      else if (character === ']') square = Math.max(0, square - 1);
      const balanced = round === 0 && angle === 0 && square === 0;
      if (balanced && character === '{') {
        end = cursor;
        break;
      }
      if (balanced && character === '\n') {
        const current = declaration.slice(0, cursor).trimEnd();
        const rest = declaration.slice(cursor + 1).trimStart();
        if (typealias || (!current.endsWith(',') && !current.endsWith(':') && !rest.startsWith(':'))) {
          end = cursor;
          break;
        }
      }
    }
    const signature = normalizeWhitespace(declaration.slice(0, end));
    const absoluteOffset = sourceOffset + declarationStart;
    results.push({
      signature,
      signatureHash: sha256(signature),
      line: lineNumberAt(source, absoluteOffset),
      summary: kdocSummaryBefore(source, sourceOffset + declarationStart),
    });
    sourceOffset += line.length + 1;
  }
  return results.filter(
    (item, index) => results.findIndex((candidate) => candidate.signature === item.signature) === index,
  );
}

export function extractDeclarations(source, symbolId, kind) {
  const targetName = symbolId.split('.').at(-1);
  return kind === 'type'
    ? findTypeDeclarations(source, targetName)
    : findFunctionDeclarations(source, targetName);
}

function extractRegion(source, region) {
  const normalized = source.replaceAll('\r\n', '\n');
  const startMarker = `// DOCS_REGION_START(${region})`;
  const endMarker = `// DOCS_REGION_END(${region})`;
  const startIndex = normalized.indexOf(startMarker);
  const endIndex = normalized.indexOf(endMarker, startIndex + startMarker.length);
  if (startIndex < 0 || endIndex < 0 || normalized.indexOf(startMarker, startIndex + 1) >= 0) {
    throw new Error(`Expected exactly one compiled region ${region}`);
  }
  const contentStart = startIndex + startMarker.length;
  const code = trimIndent(normalized.slice(contentStart, endIndex));
  return {
    code,
    line: lineNumberAt(normalized, contentStart) + (normalized[contentStart] === '\n' ? 1 : 0),
  };
}

function searchTerms(symbol) {
  const simple = symbol.symbol.split('.').at(-1);
  return [...new Set([
    simple,
    simple.replace(/([a-z0-9])([A-Z])/gu, '$1 $2'),
    symbol.capabilityId,
    symbol.artifact,
    symbol.kind,
    symbol.receiver,
  ].filter(Boolean).map((item) => item.toLowerCase()))].sort();
}

function currentGitRevision() {
  return execFileSync('git', ['rev-parse', 'HEAD'], {
    cwd: repositoryRoot,
    encoding: 'utf8',
  }).trim();
}

async function generatorVersion() {
  return `${generatorBaseVersion}+${sha256(await readFile(modulePath)).slice(0, 12)}`;
}

function compactLlms({sourceRevision, capabilityReference, rules}) {
  const groups = capabilityReference.groups
    .map((group) => `- ${group.groupId}: ${group.entryCount} entries`)
    .join('\n');
  const coreRules = rules.slice(0, 6).map((rule) => `- ${rule.code}: ${rule.summary}`).join('\n');
  return `# ViewCompose\n\n` +
    `> Declarative Kotlin UI for Android that renders native Views. Use exact published APIs and ` +
    `validate generated code before delivery.\n\n` +
    `## Version and source\n\n` +
    `- Lane: current-source\n` +
    `- Source revision: ${sourceRevision}\n` +
    `- Capability fingerprint: ${capabilityReference.summary.sourceFingerprint}\n` +
    `- Entries: ${capabilityReference.summary.entryCount}\n` +
    `- Capabilities: ${capabilityReference.capabilities.length}\n` +
    `- Artifacts: ${capabilityReference.summary.artifactCount}\n\n` +
    `## Canonical references\n\n` +
    `- Documentation: https://docs.viewcompose.com/documentation/\n` +
    `- Capability Reference: https://docs.viewcompose.com/reference/\n` +
    `- Versioned API/KDoc: https://docs.viewcompose.com/api/\n` +
    `- Local structured bundle: tools/ai/generated/current-source/manifest.json\n` +
    `- Full machine guide: tools/ai/generated/current-source/llms-full.txt\n\n` +
    `## Evidence contract\n\n` +
    `Knowledge, static, compiled, rendered, and compared are cumulative evidence levels. A symbol ` +
    `match is not compilation; compilation is not rendering; visual similarity cannot override ` +
    `semantics, accessibility, unsupported content, or security failure.\n\n` +
    `## Core rules\n\n${coreRules}\n\n` +
    `## Capability groups\n\n${groups}\n\n` +
    `## Agent workflow\n\n` +
    `Select an exact version lane, retrieve capability and sample IDs, generate only current symbols, ` +
    `run static validation, compile in the tool-owned harness, render supported UI through Preview, ` +
    `and return the deepest evidence that passed. MCP and converters are not part of Phase 1.\n`;
}

function fullLlms({compact, capabilities, symbols, samples}) {
  const symbolByCapability = Map.groupBy(symbols, (symbol) => symbol.capabilityId);
  const sampleById = new Map(samples.map((sample) => [sample.sampleId, sample]));
  const sections = capabilities.map((capability) => {
    const capabilitySymbols = symbolByCapability.get(capability.capabilityId) ?? [];
    const sample = sampleById.get(capability.sample.sampleId);
    const declarations = capabilitySymbols
      .map((symbol) => {
        const signature = symbol.declarations.map((item) => item.signature).join(' | ');
        return `- ${symbol.symbolId}${signature ? ` — ${signature}` : ''}`;
      })
      .join('\n');
    return `## ${capability.capabilityId}\n\n` +
      `- Kind: ${capability.kind}\n` +
      `- Artifact: ${capability.artifactId}@${capability.versionState.version}\n` +
      `- Reference: ${capability.referenceId}\n` +
      `- Compiled sample: ${capability.sample.sampleId} (${sample?.buildTarget ?? 'unknown target'})\n` +
      `- Related documents: ${capability.relatedDocuments.map((document) => document.path).join(', ')}\n\n` +
      `${declarations}\n`;
  });
  return `${compact}\n# Capability details\n\n${sections.join('\n')}`;
}

export async function buildKnowledgeBundle(options = {}) {
  const sourceRevision = options.sourceRevision ?? currentGitRevision();
  const [capabilityReference, capabilityRecords, sampleRecords, rulesDocument] = await Promise.all([
    readJson(capabilityReferencePath),
    readJsonDirectory(capabilityRecordDirectory),
    readJsonDirectory(sampleRecordDirectory),
    readJson(rulesPath),
  ]);
  const recordByCapability = new Map(
    capabilityRecords.map((record) => [record.capability_id, record]),
  );
  const symbolRecordById = new Map();
  for (const record of capabilityRecords) {
    for (const symbol of record.symbols) symbolRecordById.set(symbol.symbol_id, symbol);
  }
  const sourceCache = new Map();
  async function sourceText(path) {
    if (!sourceCache.has(path)) {
      sourceCache.set(path, await readFile(resolve(repositoryRoot, path), 'utf8'));
    }
    return sourceCache.get(path);
  }

  const referenceEntries = capabilityReference.groups
    .flatMap((group) => group.entries)
    .sort((left, right) => left.symbol.localeCompare(right.symbol));
  const symbols = [];
  for (const entry of referenceEntries) {
    const sourceRecord = symbolRecordById.get(entry.symbol);
    if (!sourceRecord) throw new Error(`Missing Governance source for ${entry.symbol}`);
    const source = await sourceText(sourceRecord.source);
    const declarations = extractDeclarations(source, entry.symbol, sourceRecord.kind);
    symbols.push({
      schemaVersion: 1,
      symbolId: entry.symbol,
      simpleName: entry.symbol.split('.').at(-1),
      capabilityId: entry.capabilityId,
      artifactId: entry.artifact,
      kind: entry.kind,
      governanceKind: sourceRecord.kind,
      visibility: sourceRecord.visibility,
      namespace: entry.namespace,
      receiver: entry.receiver ?? null,
      overloadCount: entry.overloadCount,
      source: {
        path: sourceRecord.source,
        line: declarations[0]?.line ?? null,
      },
      declarations,
      resolution: declarations.length > 0 ? 'source-declaration' : 'source-path-only',
      searchTerms: searchTerms(entry),
      sourceFingerprint: sha256(
        `${sourceRecord.source}\n${declarations.map((item) => item.signatureHash).join('\n')}`,
      ),
    });
  }
  const unresolvedSymbols = symbols
    .filter((symbol) => symbol.declarations.length === 0)
    .map((symbol) => symbol.symbolId);
  if (unresolvedSymbols.length > 0) {
    throw new Error(
      `Cannot generate source-complete knowledge; unresolved declarations:\n${unresolvedSymbols.join('\n')}`,
    );
  }

  const samples = [];
  for (const record of sampleRecords.sort((left, right) => left.sample_id.localeCompare(right.sample_id))) {
    if (record.sample_class === 'compiled-region') {
      const source = await sourceText(record.source);
      const region = extractRegion(source, record.region);
      samples.push({
        schemaVersion: 1,
        sampleId: record.sample_id,
        sampleClass: record.sample_class,
        language: record.language,
        capabilityId: record.capability_id,
        documentIds: [...record.document_ids].sort(),
        versionLane: record.version_lane,
        source: {path: record.source, region: record.region, line: region.line},
        buildTarget: record.build_target,
        code: region.code,
        contentFingerprint: sha256(region.code),
      });
    } else {
      samples.push({
        schemaVersion: 1,
        sampleId: record.sample_id,
        sampleClass: record.sample_class,
        language: record.language,
        capabilityId: record.capability_id,
        documentIds: [...record.document_ids].sort(),
        versionLane: record.version_lane,
        reason: record.reason,
        visibleExplanation: record.visible_explanation,
      });
    }
  }

  const capabilities = capabilityReference.capabilities
    .map((referenceCapability) => {
      const record = recordByCapability.get(referenceCapability.capabilityId);
      if (!record) throw new Error(`Missing capability record ${referenceCapability.capabilityId}`);
      const documents = referenceCapability.relatedDocumentIds.map((documentId) => ({
        documentId,
        ...(capabilityReference.documents[documentId] ?? {documentType: 'unknown', path: null}),
      }));
      return {
        schemaVersion: 1,
        capabilityId: referenceCapability.capabilityId,
        kind: record.kind,
        artifactId: record.artifact,
        versionState: record.version_state,
        referenceId: referenceCapability.referenceId,
        sample: referenceCapability.sample,
        symbolIds: record.symbols.map((symbol) => symbol.symbol_id).sort(),
        relatedDocuments: documents.sort((left, right) => left.documentId.localeCompare(right.documentId)),
      };
    })
    .sort((left, right) => left.capabilityId.localeCompare(right.capabilityId));

  const artifacts = capabilityReference.artifacts
    .map((artifact) => ({schemaVersion: 1, ...artifact}))
    .sort((left, right) => left.artifact.localeCompare(right.artifact));
  const rules = [...rulesDocument.rules].sort((left, right) => left.code.localeCompare(right.code));
  const compact = compactLlms({sourceRevision, capabilityReference, rules});
  const full = fullLlms({compact, capabilities, symbols, samples});
  const files = new Map([
    ['artifacts.json', stableJson({schemaVersion: 1, artifacts})],
    ['capabilities.json', stableJson({schemaVersion: 1, capabilities})],
    ['symbols.jsonl', `${symbols.map(jsonLine).join('\n')}\n`],
    ['samples.jsonl', `${samples.map(jsonLine).join('\n')}\n`],
    ['rules.json', stableJson({schemaVersion: 1, rules})],
    ['llms.txt', compact],
    ['llms-full.txt', full],
  ]);
  const metadata = [...files.entries()].map(([path, content]) => ({
    path,
    mediaType: path.endsWith('.json')
      ? 'application/json'
      : path.endsWith('.jsonl')
        ? 'application/x-ndjson'
        : 'text/plain',
    sha256: sha256(content),
    bytes: Buffer.byteLength(content),
  }));
  const bundleFingerprint = sha256(
    metadata.map((file) => `${file.path}\0${file.sha256}\n`).join(''),
  );
  const manifest = {
    schemaVersion: 1,
    generatorVersion: await generatorVersion(),
    framework: {versionLane: 'current-source', identity: sourceRevision},
    source: {
      revision: sourceRevision,
      capabilityFingerprint: capabilityReference.summary.sourceFingerprint,
    },
    files: metadata,
    counts: {
      artifacts: artifacts.length,
      capabilities: capabilities.length,
      symbols: symbols.length,
      samples: samples.length,
      rules: rules.length,
    },
    bundleFingerprint,
  };
  return {manifest, files, hostedLlms: compact, symbols, samples, capabilities};
}

export async function writeKnowledgeBundle(bundle, outputDirectory = generatedDirectory) {
  await mkdir(outputDirectory, {recursive: true});
  for (const [path, content] of bundle.files) {
    await writeFile(resolve(outputDirectory, path), content);
  }
  await writeFile(resolve(outputDirectory, 'manifest.json'), stableJson(bundle.manifest));
  await mkdir(dirname(hostedLlmsPath), {recursive: true});
  await writeFile(hostedLlmsPath, bundle.hostedLlms);
}

export async function verifyKnowledgeBundle(outputDirectory = generatedDirectory) {
  const manifestPath = resolve(outputDirectory, 'manifest.json');
  const committedManifest = await readJson(manifestPath);
  const expected = await buildKnowledgeBundle({sourceRevision: committedManifest.source.revision});
  const violations = [];
  const expectedPaths = [...expected.files.keys(), 'manifest.json'].sort();
  const actualPaths = (await readdir(outputDirectory)).filter((name) => !name.startsWith('.')).sort();
  if (JSON.stringify(expectedPaths) !== JSON.stringify(actualPaths)) {
    violations.push(`bundle files differ: expected ${expectedPaths.join(', ')}, found ${actualPaths.join(', ')}`);
  }
  for (const [path, content] of expected.files) {
    const actual = await readFile(resolve(outputDirectory, path), 'utf8').catch(() => null);
    if (actual !== content) violations.push(`${path}: stale generated output`);
  }
  const expectedManifest = stableJson(expected.manifest);
  const actualManifest = await readFile(manifestPath, 'utf8');
  if (actualManifest !== expectedManifest) violations.push('manifest.json: stale generated output');
  const actualHosted = await readFile(hostedLlmsPath, 'utf8').catch(() => null);
  if (actualHosted !== expected.hostedLlms) violations.push('website/static/llms.txt: stale generated output');
  if (violations.length > 0) {
    throw new Error(
      `AI Knowledge Bundle verification failed:\n${violations.map((item) => `- ${item}`).join('\n')}\n` +
        'Run npm --prefix tools/ai run generate:knowledge.',
    );
  }
  return expected.manifest;
}
