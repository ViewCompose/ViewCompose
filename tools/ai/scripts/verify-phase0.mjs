import {lstat, readFile, realpath, readdir} from 'node:fs/promises';
import {dirname, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {assertSchemaValue} from './schema-validator.mjs';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const aiRoot = resolve(scriptDirectory, '..');
const repositoryRoot = resolve(aiRoot, '../..');
const contractsDirectory = resolve(aiRoot, 'contracts');
const evaluationDirectory = resolve(aiRoot, 'evaluation');

async function readJson(path) {
  try {
    return JSON.parse(await readFile(path, 'utf8'));
  } catch (error) {
    throw new Error(`${relative(repositoryRoot, path)}: ${error.message}`);
  }
}

function assertUnique(values, label) {
  const seen = new Set();
  for (const value of values) {
    if (seen.has(value)) throw new Error(`${label}: duplicate value ${value}`);
    seen.add(value);
  }
}

function isWithin(parent, child) {
  const path = relative(parent, child);
  return path === '' || (!path.startsWith(`..${sep}`) && path !== '..' && !path.startsWith(sep));
}

export async function resolveFixturePath(fixture, root = repositoryRoot) {
  if (typeof fixture !== 'string' || fixture.length === 0) {
    throw new Error('Fixture path must be a non-empty string.');
  }
  const candidate = resolve(evaluationDirectory, fixture);
  if (!isWithin(root, candidate)) {
    throw new Error(`Fixture escapes repository root: ${fixture}`);
  }
  const canonicalRoot = await realpath(root);
  const canonicalCandidate = await realpath(candidate);
  if (!isWithin(canonicalRoot, canonicalCandidate)) {
    throw new Error(`Fixture resolves outside repository root: ${fixture}`);
  }
  const repositoryRelative = relative(canonicalRoot, canonicalCandidate);
  let current = canonicalRoot;
  for (const segment of repositoryRelative.split(sep).filter(Boolean)) {
    current = resolve(current, segment);
    if ((await lstat(current)).isSymbolicLink()) {
      throw new Error(`Fixture traverses a symbolic link: ${fixture}`);
    }
  }
  if (!(await lstat(canonicalCandidate)).isFile()) {
    throw new Error(`Fixture is not a regular file: ${fixture}`);
  }
  return canonicalCandidate;
}

function schemaVersion(schema) {
  return schema.properties?.schemaVersion?.const ??
    schema.$defs?.request?.properties?.schemaVersion?.const;
}

async function verifySchemas(versions) {
  const schemaFiles = (await readdir(contractsDirectory))
    .filter((name) => name.endsWith('.schema.json'))
    .sort();
  const schemas = new Map();
  const ids = [];
  for (const name of schemaFiles) {
    const schema = await readJson(resolve(contractsDirectory, name));
    if (schema.$schema !== 'https://json-schema.org/draft/2020-12/schema') {
      throw new Error(`${name}: unsupported or missing JSON Schema draft`);
    }
    if (typeof schema.$id !== 'string' || schema.$id.length === 0) {
      throw new Error(`${name}: missing stable $id`);
    }
    if (!schema.type && !schema.oneOf) throw new Error(`${name}: missing root type or oneOf`);
    ids.push(schema.$id);
    schemas.set(name, schema);
  }
  assertUnique(ids, 'JSON Schema IDs');

  const contractSchemas = {
    knowledgeBundleManifest: 'knowledge-bundle-manifest.schema.json',
    toolEnvelope: 'tool-envelope.schema.json',
    designIr: 'design-ir.schema.json',
    evaluationCorpus: 'evaluation-corpus.schema.json',
    metricContract: 'metric-contract.schema.json',
  };
  for (const [contract, file] of Object.entries(contractSchemas)) {
    const expected = versions.contracts[contract];
    const actual = schemaVersion(schemas.get(file));
    if (expected !== actual) {
      throw new Error(`${contract}: versions.json declares ${expected}, schema declares ${actual}`);
    }
  }
  return schemas;
}

function verifyVersions(versions) {
  if (versions.schemaVersion !== 1) throw new Error('versions.json: unsupported schemaVersion');
  if (versions.compatibility?.policy !== 'exact-major') {
    throw new Error('versions.json: compatibility policy must remain exact-major');
  }
  if (versions.compatibility?.implicitDowngrade !== false) {
    throw new Error('versions.json: implicit downgrade must remain disabled');
  }
  assertUnique(versions.versionLanes.map((lane) => lane.id), 'Version lane IDs');
  if (versions.versionLanes.some((lane) => lane.movableAlias !== false)) {
    throw new Error('versions.json: every version lane must reject movable aliases');
  }
  assertUnique(versions.evidenceLevels, 'Evidence levels');
  assertUnique(versions.reservedCapabilities.map((item) => item.id), 'Reserved capability IDs');
  for (const capability of versions.reservedCapabilities) {
    if (!/^tooling\.ai-[a-z-]+$/u.test(capability.id)) {
      throw new Error(`Invalid reserved capability ID: ${capability.id}`);
    }
    if (!['Q2', 'Q3'].includes(capability.initialQLevel)) {
      throw new Error(`${capability.id}: initial Q level must be Q2 or Q3`);
    }
    if (!Array.isArray(capability.contractFields) || capability.contractFields.length === 0) {
      throw new Error(`${capability.id}: applicable contract fields are required`);
    }
  }
}

async function verifyMcpProtocol() {
  const protocol = await readJson(resolve(contractsDirectory, 'mcp-protocol.json'));
  if (protocol.schemaVersion !== 1 || protocol.transport !== 'stdio') {
    throw new Error('mcp-protocol.json: only frozen stdio contract version 1 is accepted');
  }
  if (protocol.specification !== 'https://modelcontextprotocol.io/specification/2026-07-28') {
    throw new Error('mcp-protocol.json: specification source changed without contract review');
  }
  if (protocol.preferredVersion !== protocol.supportedVersions?.[0]) {
    throw new Error('mcp-protocol.json: preferred version must be the first supported version');
  }
  assertUnique(protocol.supportedVersions, 'MCP protocol versions');
  if (
    JSON.stringify(protocol.supportedVersions) !==
    JSON.stringify(['2026-07-28', '2025-11-25'])
  ) {
    throw new Error('mcp-protocol.json: supported protocol eras changed without contract review');
  }
  if (protocol.compatibility?.implicitVersionDowngrade !== false) {
    throw new Error('mcp-protocol.json: implicit protocol downgrade must remain disabled');
  }
  if (
    !Number.isInteger(protocol.limits?.maxMessageBytes) ||
    !Number.isInteger(protocol.limits?.maxConcurrentRequests) ||
    protocol.limits.maxMessageBytes > 4 * 1024 * 1024 ||
    protocol.limits.maxConcurrentRequests > 4
  ) {
    throw new Error('mcp-protocol.json: stdio resource limits exceed the accepted contract');
  }
  return protocol;
}

async function verifyExamples(schemas) {
  const examples = [
    ['knowledge-bundle-manifest.json', 'knowledge-bundle-manifest.schema.json'],
    ['tool-request.json', 'tool-envelope.schema.json'],
    ['tool-result.json', 'tool-envelope.schema.json'],
    ['design-ir.json', 'design-ir.schema.json'],
  ];
  for (const [exampleName, schemaName] of examples) {
    const example = await readJson(resolve(contractsDirectory, 'examples', exampleName));
    assertSchemaValue(example, schemas.get(schemaName), exampleName);
  }
}

async function verifyMetrics(schemas) {
  const metrics = await readJson(resolve(evaluationDirectory, 'metrics.json'));
  assertSchemaValue(metrics, schemas.get('metric-contract.schema.json'), 'evaluation/metrics.json');
  assertUnique(metrics.metrics.map((metric) => metric.id), 'Metric IDs');
  return metrics;
}

async function verifyCorpus(schemas, metrics) {
  const corpus = await readJson(resolve(evaluationDirectory, 'corpus.json'));
  assertSchemaValue(corpus, schemas.get('evaluation-corpus.schema.json'), 'evaluation/corpus.json');
  assertUnique(corpus.cases.map((item) => item.id), 'Evaluation case IDs');
  const metricById = new Map(metrics.metrics.map((metric) => [metric.id, metric]));
  const referencedMetrics = new Set();
  const capabilityReference = await readJson(
    resolve(repositoryRoot, 'website/src/data/capability-reference.json'),
  );
  const capabilityIds = new Set(
    capabilityReference.capabilities.map((capability) => capability.capabilityId),
  );
  for (const item of corpus.cases) {
    if (item.input.kind === 'query' && !item.input.text) {
      throw new Error(`${item.id}: query input requires text`);
    }
    if (item.input.kind === 'fixture') {
      if (!item.input.fixture) throw new Error(`${item.id}: fixture input requires a path`);
      await resolveFixturePath(item.input.fixture);
    }
    for (const metricId of item.metricIds) {
      const metric = metricById.get(metricId);
      if (!metric) throw new Error(`${item.id}: unknown metric ${metricId}`);
      if (metric.phase !== item.phase) {
        throw new Error(`${item.id}: metric ${metricId} belongs to Phase ${metric.phase}`);
      }
      referencedMetrics.add(metricId);
    }
    for (const capabilityId of item.expected.capabilityIds ?? []) {
      if (!capabilityIds.has(capabilityId)) {
        throw new Error(`${item.id}: unknown canonical capability ${capabilityId}`);
      }
    }
    if (item.expected.outcome !== 'pass' && !(item.expected.diagnosticCodes?.length > 0)) {
      throw new Error(`${item.id}: failed or unsupported outcomes require diagnostic codes`);
    }
  }
  const missingMetricCases = metrics.metrics
    .map((metric) => metric.id)
    .filter((metricId) => !referencedMetrics.has(metricId));
  if (missingMetricCases.length > 0) {
    throw new Error(`Metrics without evaluation cases: ${missingMetricCases.join(', ')}`);
  }
  const requiredCategories = [
    'retrieval',
    'knowledge-freshness',
    'hallucination',
    'static-validation',
    'compilation',
    'render',
    'project-analysis',
    'security',
    'protocol',
    'packaging',
    'xml-migration',
    'compose-migration',
    'visual-generation',
  ];
  const actualCategories = new Set(corpus.cases.map((item) => item.category));
  const missingCategories = requiredCategories.filter((category) => !actualCategories.has(category));
  if (missingCategories.length > 0) {
    throw new Error(`Evaluation corpus is missing categories: ${missingCategories.join(', ')}`);
  }
  return corpus;
}

export async function verifyPhase0() {
  const versions = await readJson(resolve(contractsDirectory, 'versions.json'));
  verifyVersions(versions);
  await verifyMcpProtocol();
  const schemas = await verifySchemas(versions);
  await verifyExamples(schemas);
  const metrics = await verifyMetrics(schemas);
  const corpus = await verifyCorpus(schemas, metrics);
  return {
    schemas: schemas.size,
    metrics: metrics.metrics.length,
    cases: corpus.cases.length,
    fixtures: corpus.cases.filter((item) => item.input.kind === 'fixture').length,
    reservedCapabilities: versions.reservedCapabilities.length,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase0()
    .then((summary) => {
      console.log(
        `Verified AI tooling Phase 0: ${summary.schemas} schemas, ` +
          `${summary.reservedCapabilities} reserved capabilities, ${summary.metrics} metrics, ` +
          `${summary.cases} cases, and ${summary.fixtures} fixture-backed cases.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
