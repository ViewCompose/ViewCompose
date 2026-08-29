import {createHash} from 'node:crypto';
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
    xmlProjectContext: 'xml-project-context.schema.json',
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
    ['xml-project-context.json', 'xml-project-context.schema.json'],
  ];
  for (const [exampleName, schemaName] of examples) {
    const example = await readJson(resolve(contractsDirectory, 'examples', exampleName));
    assertSchemaValue(example, schemas.get(schemaName), exampleName);
  }
}

function visitDesignNodes(roots, visitor) {
  for (const node of roots) {
    visitor(node);
    visitDesignNodes(node.children, visitor);
  }
}

function designIrFacts(ir) {
  const nodeIds = [];
  const resources = [];
  const stateBindings = [];
  visitDesignNodes(ir.roots, (node) => {
    nodeIds.push(node.id);
    for (const fields of [node.properties, node.semantics, node.state]) {
      assertUnique(fields.map((field) => field.name), `${node.id} field names`);
      for (const field of fields) {
        if (field.value.kind === 'resource') {
          resources.push(`${field.value.resourceType}/${field.value.name}`);
        }
        if (field.value.kind === 'binding') stateBindings.push(field.value.name);
      }
    }
    assertUnique(node.modifiers.map((modifier) => modifier.kind), `${node.id} modifier kinds`);
    for (const modifier of node.modifiers) {
      assertUnique(
        modifier.arguments.map((argument) => argument.name),
        `${node.id}.${modifier.kind} argument names`,
      );
    }
  });
  assertUnique(nodeIds, 'Design IR node IDs');
  return {
    nodes: nodeIds.length,
    resources: [...new Set(resources)].sort(),
    stateBindings: [...new Set(stateBindings)].sort(),
  };
}

async function verifyXmlSubset(schemas) {
  const fixtureDirectory = resolve(evaluationDirectory, 'fixtures/xml');
  const contract = await readJson(resolve(fixtureDirectory, 'subset-contract.json'));
  if (contract.schemaVersion !== 1 || contract.subsetId !== 'android-xml-layout-v1') {
    throw new Error('XML subset contract must remain android-xml-layout-v1 schema version 1');
  }
  if (
    contract.source?.networkAccess !== false ||
    contract.source?.allowDoctype !== false ||
    contract.source?.allowEntities !== false
  ) {
    throw new Error('XML subset contract must stay offline and reject DOCTYPE/entities');
  }
  for (const [name, ceiling] of Object.entries({
    maxInputBytes: 262144,
    maxDepth: 32,
    maxNodes: 500,
    maxAttributesPerNode: 64,
    maxUnsupportedFragments: 1000,
  })) {
    const value = contract.limits?.[name];
    if (!Number.isInteger(value) || value <= 0 || value > ceiling) {
      throw new Error(`XML subset limit ${name} exceeds its frozen ceiling`);
    }
  }
  const expectedElements = ['LinearLayout', 'TextView', 'EditText', 'Button'];
  if (JSON.stringify(contract.elements?.map((element) => element.source)) !== JSON.stringify(expectedElements)) {
    throw new Error('XML subset element order changed without contract review');
  }
  if (
    contract.unsupportedPolicy?.status !== 'unsupported' ||
    contract.unsupportedPolicy?.emitKotlin !== false ||
    contract.unsupportedPolicy?.preserveSource !== true ||
    contract.unsupportedPolicy?.localizeEveryFragment !== true
  ) {
    throw new Error('XML unsupported policy must fail closed without Kotlin output');
  }
  assertUnique(contract.diagnosticCodes, 'XML migration diagnostic codes');
  for (const code of contract.diagnosticCodes) {
    if (!/^VC-AI-XML-[A-Z0-9-]+$/u.test(code)) {
      throw new Error(`Invalid XML migration diagnostic code: ${code}`);
    }
  }

  const designSchema = schemas.get('design-ir.schema.json');
  const example = await readJson(resolve(contractsDirectory, 'examples/design-ir.json'));
  const declaredSources = new Set();
  for (const fixture of contract.supportedFixtures) {
    const sourcePath = resolve(fixtureDirectory, fixture.source);
    const goldenPath = resolve(fixtureDirectory, fixture.goldenIr);
    const [source, golden, goldenKotlin] = await Promise.all([
      readFile(sourcePath),
      readJson(goldenPath),
      readFile(resolve(fixtureDirectory, fixture.goldenKotlin), 'utf8'),
    ]);
    assertSchemaValue(golden, designSchema, fixture.goldenIr);
    const fingerprint = createHash('sha256').update(source).digest('hex');
    if (golden.source.fingerprint !== fingerprint) {
      throw new Error(`${fixture.goldenIr}: source fingerprint does not match ${fixture.source}`);
    }
    const expectedIdentity = relative(repositoryRoot, sourcePath).replaceAll(sep, '/');
    if (golden.source.identity !== expectedIdentity) {
      throw new Error(`${fixture.goldenIr}: source identity must be ${expectedIdentity}`);
    }
    const facts = designIrFacts(golden);
    if (
      facts.nodes !== fixture.expectedNodes ||
      JSON.stringify(facts.resources) !== JSON.stringify(fixture.expectedResources) ||
      JSON.stringify(facts.stateBindings) !== JSON.stringify(fixture.expectedStateBindings)
    ) {
      throw new Error(`${fixture.goldenIr}: declared Design IR denominator does not match the golden`);
    }
    declaredSources.add(fixture.source);
    if (fixture.goldenIr === 'login.design-ir.json' && JSON.stringify(golden) !== JSON.stringify(example)) {
      throw new Error('The public Design IR example must equal the frozen login golden');
    }
    if (
      !goldenKotlin.startsWith('package generated.viewcompose\n') ||
      !goldenKotlin.includes(`fun UiTreeBuilder.${fixture.expectedFunction}(`) ||
      fixture.expectedResourceParameters.some((parameter) =>
        !goldenKotlin.includes(`    ${parameter}: String,`)) ||
      !goldenKotlin.endsWith('\n')
    ) {
      throw new Error(`${fixture.goldenKotlin}: generated Kotlin contract is incomplete`);
    }
  }
  for (const fixture of contract.unsupportedFixtures) {
    await readFile(resolve(fixtureDirectory, fixture.source));
    declaredSources.add(fixture.source);
    for (const code of fixture.diagnosticCodes) {
      if (!contract.diagnosticCodes.includes(code)) {
        throw new Error(`${fixture.source}: undeclared diagnostic code ${code}`);
      }
    }
  }
  assertUnique([...declaredSources], 'XML subset source fixtures');
  return contract;
}

async function readProjectFixtureFile(projectRoot, projectPath) {
  const candidate = resolve(projectRoot, projectPath);
  if (!isWithin(projectRoot, candidate)) {
    throw new Error(`XML project-context fixture escapes its root: ${projectPath}`);
  }
  const canonicalRoot = await realpath(projectRoot);
  const canonicalCandidate = await realpath(candidate);
  if (!isWithin(canonicalRoot, canonicalCandidate)) {
    throw new Error(`XML project-context fixture resolves outside its root: ${projectPath}`);
  }
  let current = canonicalRoot;
  for (const segment of relative(canonicalRoot, canonicalCandidate).split(sep).filter(Boolean)) {
    current = resolve(current, segment);
    if ((await lstat(current)).isSymbolicLink()) {
      throw new Error(`XML project-context fixture traverses a symbolic link: ${projectPath}`);
    }
  }
  if (!(await lstat(canonicalCandidate)).isFile()) {
    throw new Error(`XML project-context fixture is not a regular file: ${projectPath}`);
  }
  return readFile(canonicalCandidate);
}

async function verifyXmlProjectContext(schemas) {
  const fixtureDirectory = resolve(evaluationDirectory, 'fixtures/xml');
  const contract = await readJson(resolve(fixtureDirectory, 'project-context-contract.json'));
  if (
    contract.schemaVersion !== 1 ||
    contract.subsetId !== 'android-xml-project-context-v1' ||
    contract.baseSubsetId !== 'android-xml-layout-v1'
  ) {
    throw new Error('XML project context must extend android-xml-layout-v1 at schema version 1');
  }
  if (
    contract.execution?.readOnly !== true ||
    contract.execution?.networkAccess !== false ||
    contract.execution?.executeProjectBuildLogic !== false ||
    contract.execution?.followSymbolicLinks !== false ||
    contract.execution?.automaticVariantSelection !== false
  ) {
    throw new Error('XML project context must remain read-only, offline, and independent of project build logic');
  }
  for (const [name, ceiling] of Object.entries({
    maxFiles: 1000,
    maxBytes: 4 * 1024 * 1024,
    maxResourceRoots: 16,
    maxSourceRoots: 16,
    maxStyleDepth: 16,
    maxDefinitionsPerResource: 64,
    maxCallSites: 4096,
    timeoutMs: 10_000,
  })) {
    const value = contract.limits?.[name];
    if (!Number.isInteger(value) || value <= 0 || value > ceiling) {
      throw new Error(`XML project-context limit ${name} exceeds its frozen ceiling`);
    }
  }
  if (
    JSON.stringify(contract.resourceResolution?.types) !== JSON.stringify(['string', 'dimen']) ||
    contract.resourceResolution?.qualifiedDefinitions !== 'inventory-only' ||
    contract.styleResolution?.cycles !== 'fail closed' ||
    contract.styleResolution?.implicitDottedParents !== false ||
    contract.styleResolution?.themeAttributes !== false ||
    contract.callSiteInventory?.analysis !== 'bounded-lexical' ||
    contract.callSiteInventory?.completeness !== 'not-proven' ||
    contract.callSiteInventory?.rawSourceInOutput !== false
  ) {
    throw new Error('XML project-context evidence or unsupported boundary changed without contract review');
  }
  assertUnique(contract.diagnosticCodes, 'XML project-context diagnostic codes');
  for (const code of contract.diagnosticCodes) {
    if (!/^VC-AI-XML-[A-Z0-9-]+$/u.test(code)) {
      throw new Error(`Invalid XML project-context diagnostic code: ${code}`);
    }
  }

  const contextSchema = schemas.get('xml-project-context.schema.json');
  const publicExample = await readJson(resolve(contractsDirectory, 'examples/xml-project-context.json'));
  const declaredRoots = new Set();
  for (const fixture of contract.supportedFixtures) {
    const projectRoot = resolve(fixtureDirectory, fixture.projectRoot);
    const canonicalRoot = await realpath(projectRoot);
    if (!isWithin(await realpath(fixtureDirectory), canonicalRoot)) {
      throw new Error(`${fixture.projectRoot}: project fixture escapes the XML fixture root`);
    }
    const files = [...fixture.files].sort();
    if (JSON.stringify(files) !== JSON.stringify(fixture.files)) {
      throw new Error(`${fixture.projectRoot}: declared files must use normalized path order`);
    }
    assertUnique(files, `${fixture.projectRoot} declared files`);
    const aggregate = createHash('sha256');
    const contentByPath = new Map();
    let scannedBytes = 0;
    for (const projectPath of files) {
      const content = await readProjectFixtureFile(canonicalRoot, projectPath);
      const fingerprint = createHash('sha256').update(content).digest('hex');
      aggregate.update(projectPath).update('\0').update(fingerprint).update('\n');
      contentByPath.set(projectPath, content);
      scannedBytes += content.byteLength;
    }
    const golden = await readJson(resolve(fixtureDirectory, fixture.goldenContext));
    assertSchemaValue(golden, contextSchema, fixture.goldenContext);
    if (JSON.stringify(golden) !== JSON.stringify(publicExample)) {
      throw new Error('The public XML project-context example must equal the frozen supported golden');
    }
    const layout = contentByPath.get(fixture.layoutPath);
    if (!layout || golden.layout.path !== fixture.layoutPath) {
      throw new Error(`${fixture.projectRoot}: the golden layout path is outside the declared files`);
    }
    if (golden.layout.fingerprint !== createHash('sha256').update(layout).digest('hex')) {
      throw new Error(`${fixture.projectRoot}: the golden layout fingerprint is stale`);
    }
    if (
      golden.fingerprint !== aggregate.digest('hex') ||
      golden.coverage.scannedBytes !== scannedBytes ||
      golden.coverage.resourceFiles !== fixture.resourceFiles.length ||
      golden.coverage.sourceFiles !== fixture.sourceFiles.length ||
      golden.coverage.completeness !== 'not-proven' ||
      golden.coverage.executedProjectBuildLogic !== false ||
      golden.coverage.networkAccess !== false
    ) {
      throw new Error(`${fixture.projectRoot}: project-context coverage or fingerprint is stale`);
    }
    const resources = golden.resources.map((entry) => entry.reference);
    const styles = golden.styles.map((entry) => entry.reference);
    const callSiteKinds = Object.fromEntries(
      [...new Set(golden.callSites.map((entry) => entry.kind))].sort().map((kind) => [
        kind,
        golden.callSites.filter((entry) => entry.kind === kind).length,
      ]),
    );
    if (
      JSON.stringify(resources) !== JSON.stringify(fixture.expectedResources) ||
      JSON.stringify(styles) !== JSON.stringify(fixture.expectedStyles) ||
      JSON.stringify(callSiteKinds) !== JSON.stringify(fixture.expectedCallSiteKinds)
    ) {
      throw new Error(`${fixture.projectRoot}: golden resources, styles, or call-site denominator changed`);
    }
    for (const callSite of golden.callSites) {
      const content = contentByPath.get(callSite.path)?.toString('utf8');
      const line = content?.split('\n')[callSite.startLine - 1];
      if (!line || callSite.startColumn > line.length + 1) {
        throw new Error(`${fixture.projectRoot}: call-site position is outside ${callSite.path}`);
      }
      const snippetFingerprint = createHash('sha256').update(line.trim()).digest('hex');
      if (callSite.snippetFingerprint !== snippetFingerprint) {
        throw new Error(`${fixture.projectRoot}: call-site snippet fingerprint is stale at ${callSite.path}:${callSite.startLine}`);
      }
    }
    declaredRoots.add(fixture.projectRoot);
  }
  for (const fixture of contract.unsupportedFixtures) {
    const projectRoot = resolve(fixtureDirectory, fixture.projectRoot);
    for (const projectPath of fixture.files) await readProjectFixtureFile(projectRoot, projectPath);
    for (const code of fixture.diagnosticCodes) {
      if (!contract.diagnosticCodes.includes(code)) {
        throw new Error(`${fixture.projectRoot}: undeclared project-context diagnostic code ${code}`);
      }
    }
    declaredRoots.add(fixture.projectRoot);
  }
  assertUnique([...declaredRoots], 'XML project-context fixture roots');
  return contract;
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
  const xmlSubset = await verifyXmlSubset(schemas);
  const xmlProjectContext = await verifyXmlProjectContext(schemas);
  const metrics = await verifyMetrics(schemas);
  const corpus = await verifyCorpus(schemas, metrics);
  return {
    schemas: schemas.size,
    metrics: metrics.metrics.length,
    cases: corpus.cases.length,
    fixtures: corpus.cases.filter((item) => item.input.kind === 'fixture').length,
    reservedCapabilities: versions.reservedCapabilities.length,
    xmlFixtures: xmlSubset.supportedFixtures.length + xmlSubset.unsupportedFixtures.length,
    xmlProjectContextFixtures:
      xmlProjectContext.supportedFixtures.length + xmlProjectContext.unsupportedFixtures.length,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase0()
    .then((summary) => {
      console.log(
        `Verified AI tooling Phase 0: ${summary.schemas} schemas, ` +
          `${summary.reservedCapabilities} reserved capabilities, ${summary.metrics} metrics, ` +
          `${summary.cases} cases, ${summary.fixtures} fixture-backed cases, and ` +
          `${summary.xmlFixtures} frozen XML fixtures and ` +
          `${summary.xmlProjectContextFixtures} frozen XML project-context fixtures.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
