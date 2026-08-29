import {createHash} from 'node:crypto';
import {lstat, readFile, realpath, readdir} from 'node:fs/promises';
import {dirname, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {inflateSync} from 'node:zlib';
import {assertSchemaValue, validateSchemaValue} from './schema-validator.mjs';
import {canonicalJson} from './screenshot-contract.mjs';
import {TOOL_DEFINITIONS, TOOL_NAMES} from './tool-catalog.mjs';

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

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit += 1) {
      crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function paethPredictor(left, above, upperLeft) {
  const prediction = left + above - upperLeft;
  const leftDistance = Math.abs(prediction - left);
  const aboveDistance = Math.abs(prediction - above);
  const upperLeftDistance = Math.abs(prediction - upperLeft);
  if (leftDistance <= aboveDistance && leftDistance <= upperLeftDistance) return left;
  if (aboveDistance <= upperLeftDistance) return above;
  return upperLeft;
}

function decodeScreenshotPng(asset, limits, label) {
  const bytes = Buffer.from(asset.data, 'base64');
  if (
    bytes.toString('base64') !== asset.data ||
    bytes.length !== asset.bytes ||
    bytes.length > limits.maxCompressedBytes ||
    createHash('sha256').update(bytes).digest('hex') !== asset.sha256 ||
    bytes.length < 33 ||
    bytes.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a'
  ) {
    throw new Error(`${label}: embedded screenshot PNG identity changed`);
  }
  let cursor = 8;
  let ihdr = 0;
  let iend = 0;
  const chunkTypes = [];
  const idatParts = [];
  while (cursor < bytes.length) {
    if (cursor + 12 > bytes.length) throw new Error(`${label}: truncated PNG chunk header`);
    const length = bytes.readUInt32BE(cursor);
    const typeStart = cursor + 4;
    const dataStart = typeStart + 4;
    const dataEnd = dataStart + length;
    const crcEnd = dataEnd + 4;
    if (crcEnd > bytes.length) throw new Error(`${label}: PNG chunk exceeds embedded bytes`);
    const type = bytes.subarray(typeStart, dataStart).toString('ascii');
    if (crc32(bytes.subarray(typeStart, dataEnd)) !== bytes.readUInt32BE(dataEnd)) {
      throw new Error(`${label}: PNG chunk ${type} has a changed CRC`);
    }
    chunkTypes.push(type);
    if (chunkTypes.length > limits.maxPngChunks) {
      throw new Error(`${label}: PNG chunk ceiling changed`);
    }
    if (type === 'IHDR') {
      ihdr += 1;
      if (
        chunkTypes.length !== 1 || length !== 13 ||
        bytes.readUInt32BE(dataStart) !== asset.widthPx ||
        bytes.readUInt32BE(dataStart + 4) !== asset.heightPx ||
        asset.widthPx > limits.maxDimensionPx ||
        asset.heightPx > limits.maxDimensionPx ||
        bytes[dataStart + 8] !== 8 ||
        bytes[dataStart + 9] !== 6 ||
        bytes[dataStart + 10] !== 0 ||
        bytes[dataStart + 11] !== 0 ||
        bytes[dataStart + 12] !== 0
      ) {
        throw new Error(`${label}: only non-interlaced 8-bit RGBA PNG is frozen`);
      }
    } else if (type === 'IDAT') {
      idatParts.push(bytes.subarray(dataStart, dataEnd));
    } else if (type === 'IEND') {
      iend += 1;
      if (length !== 0 || crcEnd !== bytes.length) {
        throw new Error(`${label}: PNG IEND must terminate the embedded bytes`);
      }
    } else if (type[0] === type[0]?.toUpperCase()) {
      throw new Error(`${label}: unsupported critical PNG chunk ${type}`);
    }
    cursor = crcEnd;
  }
  if (ihdr !== 1 || iend !== 1 || idatParts.length === 0) {
    throw new Error(`${label}: PNG requires one IHDR, IDAT data, and one IEND`);
  }
  const rowBytes = asset.widthPx * 4;
  const decodedBytes = (rowBytes + 1) * asset.heightPx;
  if (decodedBytes > limits.maxDecodedBytes) {
    throw new Error(`${label}: decoded PNG byte ceiling changed`);
  }
  let filtered;
  try {
    filtered = inflateSync(Buffer.concat(idatParts), {maxOutputLength: decodedBytes});
  } catch (error) {
    throw new Error(`${label}: PNG IDAT cannot be decoded: ${error.message}`);
  }
  if (filtered.length !== decodedBytes) {
    throw new Error(`${label}: PNG decoded byte count changed`);
  }
  const pixels = Buffer.alloc(rowBytes * asset.heightPx);
  for (let y = 0; y < asset.heightPx; y += 1) {
    const filteredOffset = y * (rowBytes + 1);
    const filterType = filtered[filteredOffset];
    if (![0, 1, 2, 3, 4].includes(filterType)) {
      throw new Error(`${label}: unsupported PNG filter type ${filterType}`);
    }
    const rowOffset = y * rowBytes;
    for (let x = 0; x < rowBytes; x += 1) {
      const encoded = filtered[filteredOffset + 1 + x];
      const left = x >= 4 ? pixels[rowOffset + x - 4] : 0;
      const above = y > 0 ? pixels[rowOffset - rowBytes + x] : 0;
      const upperLeft = y > 0 && x >= 4 ? pixels[rowOffset - rowBytes + x - 4] : 0;
      const predictor = {
        0: 0,
        1: left,
        2: above,
        3: Math.floor((left + above) / 2),
        4: paethPredictor(left, above, upperLeft),
      }[filterType];
      pixels[rowOffset + x] = (encoded + predictor) & 0xff;
    }
  }
  return {bytes, chunkTypes, pixels};
}

function verifyEmbeddedPng(asset, limits, label) {
  const bytes = Buffer.from(asset.data, 'base64');
  if (
    bytes.toString('base64') !== asset.data ||
    bytes.length !== asset.bytes ||
    bytes.length > limits.maxAssetBytes ||
    createHash('sha256').update(bytes).digest('hex') !== asset.sha256 ||
    bytes.length < 33 ||
    bytes.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a'
  ) {
    throw new Error(`${label}: embedded PNG bytes or identity changed`);
  }
  let cursor = 8;
  let chunks = 0;
  let ihdr = 0;
  let iend = 0;
  while (cursor < bytes.length) {
    if (cursor + 12 > bytes.length) throw new Error(`${label}: truncated PNG chunk header`);
    const length = bytes.readUInt32BE(cursor);
    const typeStart = cursor + 4;
    const dataStart = typeStart + 4;
    const dataEnd = dataStart + length;
    const crcEnd = dataEnd + 4;
    if (crcEnd > bytes.length) throw new Error(`${label}: PNG chunk exceeds the embedded bytes`);
    const type = bytes.subarray(typeStart, dataStart).toString('ascii');
    const expectedCrc = bytes.readUInt32BE(dataEnd);
    if (crc32(bytes.subarray(typeStart, dataEnd)) !== expectedCrc) {
      throw new Error(`${label}: PNG chunk ${type} has a changed CRC`);
    }
    chunks += 1;
    if (chunks > limits.maxPngChunks) throw new Error(`${label}: PNG chunk ceiling changed`);
    if (type === 'IHDR') {
      ihdr += 1;
      if (
        chunks !== 1 || length !== 13 ||
        bytes.readUInt32BE(dataStart) !== asset.widthPx ||
        bytes.readUInt32BE(dataStart + 4) !== asset.heightPx ||
        asset.widthPx > limits.maxAssetWidthPx ||
        asset.heightPx > limits.maxAssetHeightPx
      ) {
        throw new Error(`${label}: PNG IHDR identity changed`);
      }
    }
    if (type === 'IEND') {
      iend += 1;
      if (length !== 0 || crcEnd !== bytes.length) {
        throw new Error(`${label}: PNG IEND must terminate the exact bytes`);
      }
    }
    cursor = crcEnd;
  }
  if (ihdr !== 1 || iend !== 1) throw new Error(`${label}: PNG requires one IHDR and IEND`);
  return bytes;
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
    xmlLayoutDependencies: 'xml-layout-dependencies.schema.json',
    generatedPreviewRequest: 'generated-preview-request.schema.json',
    layoutComparison: 'layout-comparison.schema.json',
    screenshotPreprocessing: 'screenshot-preprocessing.schema.json',
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
    ['xml-layout-dependencies.json', 'xml-layout-dependencies.schema.json'],
    ['layout-comparison.json', 'layout-comparison.schema.json'],
    ['screenshot-preprocessing-request.json', 'screenshot-preprocessing.schema.json'],
    ['screenshot-preprocessing-result.json', 'screenshot-preprocessing.schema.json'],
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

async function verifyXmlSubsetV2(schemas) {
  const fixtureDirectory = resolve(evaluationDirectory, 'fixtures/xml');
  const contract = await readJson(resolve(fixtureDirectory, 'subset-v2-contract.json'));
  if (
    contract.schemaVersion !== 1 ||
    contract.subsetId !== 'android-xml-layout-v2' ||
    contract.baseSubsetId !== 'android-xml-layout-v1'
  ) {
    throw new Error('XML subset v2 must explicitly extend android-xml-layout-v1');
  }
  if (
    contract.source?.networkAccess !== false ||
    contract.source?.allowDoctype !== false ||
    contract.source?.allowEntities !== false ||
    JSON.stringify(contract.addedElements?.map((element) => element.source)) !==
      JSON.stringify(['FrameLayout', 'ImageView']) ||
    JSON.stringify(contract.addedAttributes?.commonOptional) !==
      JSON.stringify(['android:visibility']) ||
    contract.normalization?.drawableResourceStrategy !==
      'caller ImageSource parameter preserving @drawable identity' ||
    contract.normalization?.contentDescriptionStrategy !==
      'required Image parameter unless explicit @null marks decorative content'
  ) {
    throw new Error('XML subset v2 container, image, accessibility, or safety boundary changed');
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
      throw new Error(`XML subset v2 limit ${name} exceeds its frozen ceiling`);
    }
  }
  if (
    contract.unsupportedPolicy?.status !== 'unsupported' ||
    contract.unsupportedPolicy?.emitKotlin !== false ||
    contract.unsupportedPolicy?.preserveSource !== true ||
    contract.unsupportedPolicy?.localizeEveryFragment !== true
  ) {
    throw new Error('XML subset v2 unsupported policy must fail closed without Kotlin output');
  }
  assertUnique(contract.diagnosticCodes, 'XML subset v2 diagnostic codes');
  for (const code of contract.diagnosticCodes) {
    if (!/^VC-AI-XML-[A-Z0-9-]+$/u.test(code)) {
      throw new Error(`Invalid XML subset v2 diagnostic code: ${code}`);
    }
  }

  const designSchema = schemas.get('design-ir.schema.json');
  const declaredSources = new Set();
  for (const fixture of contract.supportedFixtures) {
    const sourcePath = resolve(fixtureDirectory, fixture.source);
    const [source, golden, goldenKotlin] = await Promise.all([
      readFile(sourcePath),
      readJson(resolve(fixtureDirectory, fixture.goldenIr)),
      readFile(resolve(fixtureDirectory, fixture.goldenKotlin), 'utf8'),
    ]);
    assertSchemaValue(golden, designSchema, fixture.goldenIr);
    if (
      golden.source.fingerprint !== createHash('sha256').update(source).digest('hex') ||
      golden.source.identity !== relative(repositoryRoot, sourcePath).replaceAll(sep, '/')
    ) {
      throw new Error(`${fixture.goldenIr}: XML subset v2 source identity is stale`);
    }
    const facts = designIrFacts(golden);
    if (
      facts.nodes !== fixture.expectedNodes ||
      JSON.stringify(facts.resources) !== JSON.stringify(fixture.expectedResources) ||
      JSON.stringify(facts.stateBindings) !== JSON.stringify(fixture.expectedStateBindings)
    ) {
      throw new Error(`${fixture.goldenIr}: XML subset v2 denominator differs from its golden`);
    }
    if (
      !goldenKotlin.startsWith('package generated.viewcompose\n') ||
      !goldenKotlin.includes(`fun UiTreeBuilder.${fixture.expectedFunction}(`) ||
      fixture.expectedBindings.some(({parameter, type}) =>
        !goldenKotlin.includes(`    ${parameter}: ${type},`)) ||
      !goldenKotlin.includes('contentDescription = profilePhoto,') ||
      !goldenKotlin.endsWith('\n')
    ) {
      throw new Error(`${fixture.goldenKotlin}: XML subset v2 Kotlin contract is incomplete`);
    }
    declaredSources.add(fixture.source);
  }
  for (const fixture of contract.unsupportedFixtures) {
    const source = await readFile(resolve(fixtureDirectory, fixture.source), 'utf8');
    if (source.includes('android:contentDescription=')) {
      throw new Error(`${fixture.source}: missing-description denominator no longer proves absence`);
    }
    for (const code of fixture.diagnosticCodes) {
      if (!contract.diagnosticCodes.includes(code)) {
        throw new Error(`${fixture.source}: undeclared XML subset v2 diagnostic code ${code}`);
      }
    }
    declaredSources.add(fixture.source);
  }
  assertUnique([...declaredSources], 'XML subset v2 source fixtures');
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
    const goldenKotlinPath = resolve(fixtureDirectory, fixture.goldenKotlin);
    if (!isWithin(fixtureDirectory, goldenKotlinPath)) {
      throw new Error(`${fixture.projectRoot}: generated Kotlin golden escapes the XML fixture root`);
    }
    const goldenKotlin = await readFile(goldenKotlinPath, 'utf8');
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
    if (
      !/^[A-Z][A-Za-z0-9]*$/u.test(fixture.expectedFunction) ||
      !goldenKotlin.includes(`fun UiTreeBuilder.${fixture.expectedFunction}(`) ||
      fixture.expectedResourceParameters.some((parameter) =>
        !goldenKotlin.includes(`${parameter}: String`)) ||
      fixture.expectedStateBindings.some((parameter) =>
        !goldenKotlin.includes(`${parameter}: TextFieldState`))
    ) {
      throw new Error(`${fixture.projectRoot}: project-aware Kotlin golden or bindings changed`);
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

async function verifyXmlLayoutDependencies(schemas) {
  const fixtureDirectory = resolve(evaluationDirectory, 'fixtures/xml');
  const contract = await readJson(resolve(fixtureDirectory, 'layout-dependency-contract.json'));
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'android-xml-layout-dependencies-v1' ||
    JSON.stringify(contract.requiresSubsetIds) !==
      JSON.stringify(['android-xml-layout-v1', 'android-xml-layout-v2']) ||
    contract.activation !== 'explicit-project-input-only'
  ) {
    throw new Error('XML layout dependencies must extend the exact accepted v1 and v2 subsets');
  }
  if (
    contract.execution?.readOnly !== true ||
    contract.execution?.networkAccess !== false ||
    contract.execution?.executeProjectBuildLogic !== false ||
    contract.execution?.followSymbolicLinks !== false ||
    contract.execution?.automaticVariantSelection !== false ||
    contract.selection?.qualifiedLayouts !== 'inventory-only' ||
    contract.selection?.resourceRootPrecedence !== 'first declared root wins' ||
    contract.include?.overrideAttributes !== false ||
    contract.merge?.activation !== 'included-root-only' ||
    contract.provenance?.rawSourceInOutput !== false
  ) {
    throw new Error('XML layout dependency execution, selection, expansion, or provenance boundary changed');
  }
  for (const [name, ceiling] of Object.entries({
    maxLayoutFiles: 64,
    maxIncludeDepth: 16,
    maxIncludeEdges: 256,
    maxExpandedBytes: 1024 * 1024,
  })) {
    const value = contract.limits?.[name];
    if (!Number.isInteger(value) || value <= 0 || value > ceiling) {
      throw new Error(`XML layout dependency limit ${name} exceeds its frozen ceiling`);
    }
  }
  assertUnique(contract.diagnosticCodes, 'XML layout dependency diagnostic codes');
  for (const code of contract.diagnosticCodes) {
    if (!/^VC-AI-XML-[A-Z0-9-]+$/u.test(code)) {
      throw new Error(`Invalid XML layout dependency diagnostic code: ${code}`);
    }
  }

  const schema = schemas.get('xml-layout-dependencies.schema.json');
  const publicExample = await readJson(
    resolve(contractsDirectory, 'examples/xml-layout-dependencies.json'),
  );
  const declaredInputs = new Set();
  for (const fixture of contract.supportedFixtures) {
    const projectRoot = resolve(fixtureDirectory, fixture.projectRoot);
    const files = [...fixture.files].sort();
    if (JSON.stringify(files) !== JSON.stringify(fixture.files)) {
      throw new Error(`${fixture.projectRoot}: layout dependency files must use normalized order`);
    }
    const contentByPath = new Map();
    for (const path of files) {
      contentByPath.set(path, await readProjectFixtureFile(projectRoot, path));
    }
    const graph = await readJson(resolve(fixtureDirectory, fixture.goldenGraph));
    assertSchemaValue(graph, schema, fixture.goldenGraph);
    if (JSON.stringify(graph) !== JSON.stringify(publicExample)) {
      throw new Error('The public XML layout dependency example must equal the frozen golden');
    }
    if (
      graph.nodes.length !== fixture.expectedLayoutFiles ||
      graph.edges.length !== fixture.expectedIncludes ||
      graph.coverage.layoutFiles !== fixture.expectedLayoutFiles ||
      graph.coverage.expandedIncludes !== fixture.expectedIncludes
    ) {
      throw new Error(`${fixture.projectRoot}: layout dependency denominator changed`);
    }
    for (const node of graph.nodes) {
      const content = contentByPath.get(node.path);
      if (!content || createHash('sha256').update(content).digest('hex') !== node.fingerprint) {
        throw new Error(`${fixture.projectRoot}: stale layout dependency node ${node.path}`);
      }
      const source = content.toString('utf8');
      const actualKind = /<merge\b/u.test(source) ? 'merge' : 'layout';
      if (node.rootKind !== actualKind) {
        throw new Error(`${fixture.projectRoot}: root kind changed for ${node.path}`);
      }
    }
    for (const edge of graph.edges) {
      const source = contentByPath.get(edge.source.path)?.toString('utf8');
      const line = source?.split('\n')[edge.source.startLine - 1];
      const expected = `<include layout="${edge.to}" />`;
      if (!line || line.indexOf(expected) + 1 !== edge.source.startColumn) {
        throw new Error(`${fixture.projectRoot}: include edge position changed for ${edge.to}`);
      }
    }
    const expectedFingerprint = createHash('sha256').update(JSON.stringify({
      root: graph.root,
      nodes: graph.nodes,
      edges: graph.edges,
    })).digest('hex');
    if (graph.fingerprint !== expectedFingerprint) {
      throw new Error(`${fixture.projectRoot}: layout dependency graph fingerprint is stale`);
    }
    declaredInputs.add(fixture.projectRoot);
  }
  for (const fixture of contract.unsupportedFixtures) {
    if (fixture.kind === 'project') {
      const projectRoot = resolve(fixtureDirectory, fixture.projectRoot);
      for (const path of fixture.files) await readProjectFixtureFile(projectRoot, path);
      declaredInputs.add(fixture.projectRoot);
    } else {
      await readFile(resolve(fixtureDirectory, fixture.source));
      declaredInputs.add(fixture.source);
    }
    for (const code of fixture.diagnosticCodes) {
      if (!contract.diagnosticCodes.includes(code)) {
        throw new Error(`Undeclared XML layout dependency diagnostic code ${code}`);
      }
    }
  }
  assertUnique([...declaredInputs], 'XML layout dependency fixture inputs');
  return contract;
}

async function verifyGeneratedPreview(schemas) {
  const fixtureDirectory = resolve(evaluationDirectory, 'fixtures/xml');
  const contract = await readJson(resolve(fixtureDirectory, 'generated-preview-contract.json'));
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-generated-preview-v1' ||
    JSON.stringify(contract.requiresContracts) !== JSON.stringify([
      'android-xml-layout-v1',
      'viewcompose-preview-protocol-v1',
      'generated-preview-request-v1',
    ]) ||
    contract.activation?.tool !== 'convert_xml_to_viewcompose' ||
    contract.activation?.mode !== 'render' ||
    contract.activation?.sourceLane !== 'current-source'
  ) {
    throw new Error('Generated Preview must extend the exact XML, Preview, and request contracts');
  }
  if (
    contract.execution?.harnessModule !== ':tools:ai-preview-harness' ||
    contract.execution?.readInspectedProject !== false ||
    contract.execution?.executeInspectedProjectBuildLogic !== false ||
    contract.execution?.networkAccess !== false ||
    contract.execution?.followSymbolicLinks !== false ||
    contract.execution?.callerSelectedGradleTask !== false ||
    contract.execution?.callerSelectedDependency !== false ||
    contract.execution?.callerSelectedOutputPath !== false ||
    contract.execution?.callerSuppliedKotlin !== false ||
    contract.execution?.callerSelectedAssetPath !== false ||
    contract.execution?.callerSelectedAssetUrl !== false ||
    contract.source?.mismatch !== 'fail closed before Gradle execution' ||
    contract.evidence?.requiredLevel !== 'rendered' ||
    contract.evidence?.compileBeforeRender !== true ||
    contract.evidence?.absolutePathsInPublicResult !== false
  ) {
    throw new Error('Generated Preview execution, source, or evidence isolation boundary changed');
  }
  if (
    JSON.stringify(contract.bindings?.supported?.map((binding) => binding.type)) !==
      JSON.stringify(['String', 'TextFieldState', 'ImageSource']) ||
    contract.bindings?.unsupported?.length !== 2 ||
    contract.bindings?.missing !== 'fail closed' ||
    contract.bindings?.extra !== 'fail closed' ||
    contract.bindings?.duplicate !== 'fail closed' ||
    contract.bindings?.applicationBehavior !== 'never invented'
  ) {
    throw new Error('Generated Preview binding support or fail-closed policy changed');
  }
  if (
    JSON.stringify(contract.assets?.mediaTypes) !== JSON.stringify(['image/png']) ||
    contract.assets?.readInspectedProjectResource !== false ||
    contract.assets?.acceptPath !== false ||
    contract.assets?.acceptUrl !== false ||
    contract.assets?.acceptAndroidResourceId !== false ||
    contract.assets?.acceptXmlDrawable !== false ||
    contract.assets?.networkAccess !== false ||
    contract.assets?.symlinkTraversal !== false ||
    contract.assets?.resourceName !== 'vc_ai_<full lowercase asset SHA-256>'
  ) {
    throw new Error('Generated Preview asset isolation boundary changed');
  }
  const expectedConfiguration = {
    widthDp: 411,
    heightDp: -1,
    density: 2.625,
    fontScale: 1,
    localeTag: 'en-US',
    layoutDirection: 'Ltr',
    theme: 'Light',
    apiLevel: null,
  };
  if (
    JSON.stringify(Object.fromEntries(Object.entries(contract.configuration)
      .filter(([name]) => name !== 'matrix'))) !== JSON.stringify(expectedConfiguration) ||
    contract.configuration?.matrix !== 'single frozen configuration in v1'
  ) {
    throw new Error('Generated Preview v1 configuration must remain the single frozen lane');
  }
  for (const [name, ceiling] of Object.entries({
    maxGeneratedKotlinBytes: 1024 * 1024,
    maxWrapperBytes: 256 * 1024,
    maxBindings: 64,
    maxBindingTextBytes: 64 * 1024,
    maxAssets: 16,
    maxAssetBytes: 512 * 1024,
    maxTotalAssetBytes: 1024 * 1024,
    maxAssetWidthPx: 1024,
    maxAssetHeightPx: 1024,
    maxPngChunks: 256,
    maxProcessOutputBytes: 2 * 1024 * 1024,
    maxImageBytes: 16 * 1024 * 1024,
    maxRenderTreeBytes: 8 * 1024 * 1024,
    timeoutMs: 120_000,
    maxConcurrentRequests: 1,
  })) {
    const value = contract.limits?.[name];
    if (!Number.isInteger(value) || value <= 0 || value > ceiling) {
      throw new Error(`Generated Preview limit ${name} exceeds its frozen ceiling`);
    }
  }
  assertUnique(contract.diagnosticCodes, 'Generated Preview diagnostic codes');
  for (const code of contract.diagnosticCodes) {
    if (!/^VC-AI-PREVIEW-[A-Z0-9-]+$/u.test(code)) {
      throw new Error(`Invalid generated Preview diagnostic code: ${code}`);
    }
  }

  const schema = schemas.get('generated-preview-request.schema.json');
  const manifest = await readJson(resolve(aiRoot, 'generated/current-source/manifest.json'));
  const declaredRequests = new Set();
  for (const fixture of contract.supportedFixtures) {
    const [source, generatedKotlin, request, wrapper] = await Promise.all([
      readFile(resolve(fixtureDirectory, fixture.source)),
      readFile(resolve(fixtureDirectory, fixture.generatedKotlin)),
      readJson(resolve(fixtureDirectory, fixture.request)),
      readFile(resolve(fixtureDirectory, fixture.wrapper)),
    ]);
    assertSchemaValue(request, schema, fixture.request);
    if (
      request.generatedSource.kotlinFingerprint !==
        createHash('sha256').update(generatedKotlin).digest('hex') ||
      source.byteLength === 0 ||
      request.generatedSource.functionName !== fixture.expectedFunction ||
      request.generatedSource.declaredBindings.length !== fixture.expectedBindings ||
      request.framework.identity !== manifest.framework.identity ||
      request.framework.bundleFingerprint !== manifest.bundleFingerprint ||
      JSON.stringify(request.configuration) !== JSON.stringify(expectedConfiguration) ||
      request.lanes.compiler !== contract.lanes.compiler ||
      request.lanes.render !== contract.lanes.render
    ) {
      throw new Error(`${fixture.request}: generated source, framework, binding, or lane identity is stale`);
    }
    assertUnique(
      request.generatedSource.declaredBindings.map((binding) => binding.parameter),
      `${fixture.request} declared binding parameters`,
    );
    assertUnique(
      request.bindings.map((binding) => binding.parameter),
      `${fixture.request} Preview binding parameters`,
    );
    const declared = request.generatedSource.declaredBindings.map((binding) =>
      `${binding.parameter}\0${binding.source}\0${binding.type}`);
    const supplied = request.bindings.map((binding) => {
      const type = {
        string: 'String',
        'text-field-state': 'TextFieldState',
        'image-source': 'ImageSource',
      }[binding.kind];
      return `${binding.parameter}\0${binding.source}\0${type}`;
    });
    if (JSON.stringify(declared) !== JSON.stringify(supplied)) {
      throw new Error(`${fixture.request}: Preview bindings no longer exactly match generator bindings`);
    }
    const assets = request.bindings.filter((binding) => binding.kind === 'image-source');
    const assetBytes = assets.map((binding) =>
      verifyEmbeddedPng(binding.asset, contract.limits, fixture.request));
    if (
      !['implemented', 'contract-frozen'].includes(fixture.status) ||
      assets.length !== (fixture.expectedAssets ?? 0) ||
      assetBytes.reduce((total, bytes) => total + bytes.length, 0) !==
        (fixture.expectedAssetBytes ?? 0) ||
      assets.some((binding) => binding.asset.sha256 !== fixture.expectedAssetSha256) ||
      assetBytes.reduce((total, bytes) => total + bytes.length, 0) >
        contract.limits.maxTotalAssetBytes
    ) {
      throw new Error(`${fixture.request}: embedded Preview asset denominator changed`);
    }
    const requestFingerprint = createHash('sha256')
      .update(JSON.stringify(request))
      .digest('hex');
    const wrapperFingerprint = createHash('sha256').update(wrapper).digest('hex');
    const wrapperSource = wrapper.toString('utf8');
    if (
      requestFingerprint !== fixture.expectedRequestFingerprint ||
      wrapperFingerprint !== fixture.expectedWrapperFingerprint ||
      wrapper.byteLength > contract.limits.maxWrapperBytes ||
      !wrapperSource.includes('fun UiTreeBuilder.GeneratedXmlPreview()') ||
      !wrapperSource.includes(`${fixture.expectedFunction}(`) ||
      fixture.expectedWrapperExpressions.some((expression) => !wrapperSource.includes(expression)) ||
      !wrapperSource.endsWith('\n')
    ) {
      throw new Error(`${fixture.wrapper}: generated Preview wrapper golden is stale`);
    }
    declaredRequests.add(fixture.request);
  }
  for (const fixture of contract.unsupportedFixtures) {
    const request = await readJson(resolve(fixtureDirectory, fixture.request));
    const violations = validateSchemaValue(request, schema);
    if (fixture.schemaValid === false) {
      if (violations.length === 0 || !Object.hasOwn(request, 'gradleTask')) {
        throw new Error(`${fixture.request}: unsafe build selection must remain schema-invalid`);
      }
    } else {
      if (violations.length > 0) {
        throw new Error(`${fixture.request}: unsupported binding fixture must remain schema-valid`);
      }
      const declared = new Map(request.generatedSource.declaredBindings.map((binding) => [
        binding.parameter,
        binding,
      ]));
      const supplied = new Map(request.bindings.map((binding) => [binding.parameter, binding]));
      const provesMissing = [...declared.keys()].some((parameter) => !supplied.has(parameter));
      const provesMissingAsset = request.bindings.some((binding) =>
        binding.kind === 'image-source' && binding.asset === undefined);
      if (
        fixture.diagnosticCodes.includes('VC-AI-PREVIEW-BINDING-MISSING') && !provesMissing ||
        fixture.diagnosticCodes.includes('VC-AI-PREVIEW-ASSET-MISSING') && !provesMissingAsset
      ) {
        throw new Error(`${fixture.request}: unsupported binding denominator changed`);
      }
    }
    for (const code of fixture.diagnosticCodes) {
      if (!contract.diagnosticCodes.includes(code)) {
        throw new Error(`${fixture.request}: undeclared generated Preview diagnostic ${code}`);
      }
    }
    declaredRequests.add(fixture.request);
  }
  assertUnique([...declaredRequests], 'Generated Preview fixture requests');
  return contract;
}

async function verifyLayoutComparison(schemas) {
  const fixtureDirectory = resolve(evaluationDirectory, 'fixtures/xml');
  const contract = await readJson(resolve(fixtureDirectory, 'layout-comparison-contract.json'));
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-generated-layout-comparison-v1' ||
    JSON.stringify(contract.requiresContracts) !== JSON.stringify([
      'design-ir-v1',
      'viewcompose-generated-preview-v1',
      'viewcompose-preview-protocol-v1',
      'layout-comparison-v1',
    ]) ||
    contract.activation?.tool !== 'convert_xml_to_viewcompose' ||
    contract.activation?.mode !== 'render' ||
    contract.activation?.successEvidence !== 'compared' ||
    contract.activation?.failureEvidence !== 'rendered'
  ) {
    throw new Error('Layout comparison must extend the exact Design IR and generated Preview contracts');
  }
  if (
    contract.input?.callerSuppliedDesignIr !== false ||
    contract.input?.callerSuppliedRenderTree !== false ||
    contract.input?.callerSuppliedPolicy !== false ||
    contract.integrity?.followSymbolicLinks !== false ||
    contract.integrity?.absolutePathsInPublicResult !== false ||
    contract.integrity?.networkAccess !== false ||
    contract.integrity?.executeInspectedProjectBuildLogic !== false ||
    contract.identity?.keyMultiplicity !== 'exactly one virtual node' ||
    contract.geometry?.tolerancePx !== 0 ||
    contract.evidence?.passLevel !== 'compared' ||
    contract.evidence?.mismatchLevel !== 'rendered' ||
    contract.evidence?.allRequiredChecksMustPass !== true ||
    contract.evidence?.oneAggregateScore !== false
  ) {
    throw new Error('Layout comparison integrity, isolation, or exact-pass boundary changed');
  }
  if (
    JSON.stringify(contract.semanticHostWrappers) !== JSON.stringify([{
      designKind: 'text-field',
      identityRenderKind: 'column',
      semanticRenderKind: 'text-field',
      maxDepth: 1,
      requirements: [
        'exactly one child',
        'child key is absent',
        'identity and semantic host bounds are equal',
      ],
    }]) ||
    JSON.stringify(contract.kindMapping) !== JSON.stringify({
      box: 'box',
      button: 'button',
      column: 'column',
      image: 'image',
      text: 'text',
      'text-field': 'text-field',
    })
  ) {
    throw new Error('Layout comparison kind or semantic-host mapping changed');
  }
  for (const [name, ceiling] of Object.entries({
    maxDesignNodes: 1000,
    maxVirtualNodes: 2000,
    maxNativeNodes: 4000,
    maxDepth: 64,
    maxRenderTreeBytes: 8 * 1024 * 1024,
    maxChecksPerNode: 128,
    maxFindings: 1000,
    maxWrapperDepth: 1,
  })) {
    const value = contract.limits?.[name];
    if (!Number.isInteger(value) || value <= 0 || value > ceiling) {
      throw new Error(`Layout comparison limit ${name} exceeds its frozen ceiling`);
    }
  }
  assertUnique(contract.diagnosticCodes, 'Layout comparison diagnostic codes');
  for (const code of contract.diagnosticCodes) {
    if (!/^VC-AI-COMPARE-[A-Z0-9-]+$/u.test(code)) {
      throw new Error(`Invalid layout comparison diagnostic code: ${code}`);
    }
  }

  const designSchema = schemas.get('design-ir.schema.json');
  const requestSchema = schemas.get('generated-preview-request.schema.json');
  const generatedPreview = await readJson(resolve(fixtureDirectory, 'generated-preview-contract.json'));
  const previewBySource = new Map(generatedPreview.supportedFixtures.map((fixture) => [
    fixture.source,
    fixture,
  ]));
  const declaredInputs = new Set();
  for (const fixture of contract.supportedFixtures) {
    const [source, designIr, previewRequest] = await Promise.all([
      readFile(resolve(fixtureDirectory, fixture.source)),
      readJson(resolve(fixtureDirectory, fixture.designIr)),
      readJson(resolve(fixtureDirectory, fixture.previewRequest)),
    ]);
    assertSchemaValue(designIr, designSchema, fixture.designIr);
    assertSchemaValue(previewRequest, requestSchema, fixture.previewRequest);
    const designFingerprint = createHash('sha256')
      .update(JSON.stringify(designIr))
      .digest('hex');
    const requestFingerprint = createHash('sha256')
      .update(JSON.stringify(previewRequest))
      .digest('hex');
    const comparedDesignIr = {
      ...designIr,
      source: {...designIr.source, identity: fixture.comparisonPath},
    };
    visitDesignNodes(comparedDesignIr.roots, (node) => {
      const separator = node.provenance.sourceSpan.lastIndexOf(':');
      node.provenance.sourceSpan = `${fixture.comparisonPath}${node.provenance.sourceSpan.slice(separator)}`;
    });
    if (
      !['contract-frozen', 'implemented'].includes(fixture.status) ||
      designFingerprint !== fixture.expectedDesignIrFingerprint ||
      createHash('sha256').update(JSON.stringify(comparedDesignIr)).digest('hex') !==
        fixture.expectedComparedDesignIrFingerprint ||
      requestFingerprint !== fixture.expectedRequestFingerprint ||
      createHash('sha256').update(source).digest('hex') !== designIr.source.fingerprint ||
      (fixture.status === 'implemented' &&
        !/^[a-f0-9]{64}$/u.test(fixture.expectedComparisonFingerprint ?? ''))
    ) {
      throw new Error(`${fixture.source}: layout comparison input identity is stale`);
    }
    const preview = previewBySource.get(fixture.source);
    if (
      !preview ||
      preview.request !== fixture.previewRequest ||
      preview.expectedRequestFingerprint !== fixture.expectedRequestFingerprint ||
      preview.expectedOutputFingerprint !== fixture.acceptedOutputFingerprint ||
      preview.expectedRenderTree?.sha256 !== fixture.acceptedRenderTreeFingerprint ||
      preview.expectedImage?.widthPx !== fixture.viewport.widthPx ||
      preview.expectedImage?.heightPx !== fixture.viewport.heightPx
    ) {
      throw new Error(`${fixture.source}: accepted Preview evidence changed beneath comparison`);
    }
    const designNodes = [];
    visitDesignNodes(designIr.roots, (node) => designNodes.push(node));
    const expectedNodes = fixture.expectedNodes;
    assertUnique(expectedNodes.map((node) => node.designNodeId), `${fixture.source} compared nodes`);
    assertUnique(expectedNodes.map((node) => node.identityKey), `${fixture.source} identity keys`);
    if (
      designNodes.length !== fixture.expectedSummary.designNodes ||
      JSON.stringify(designNodes.map((node) => node.id)) !==
        JSON.stringify(expectedNodes.map((node) => node.designNodeId)) ||
      fixture.expectedSummary.mappedNodes !== expectedNodes.length
    ) {
      throw new Error(`${fixture.source}: compared Design IR node denominator changed`);
    }
    let requiredChecks = 0;
    let notApplicableChecks = 0;
    for (let index = 0; index < expectedNodes.length; index += 1) {
      const expected = expectedNodes[index];
      const designNode = designNodes[index];
      const normalizedKey = designNode.id.startsWith('id:') ? designNode.id.slice(3) : designNode.id;
      const mappedKind = contract.kindMapping[designNode.kind];
      const wrapper = contract.semanticHostWrappers.find((candidate) =>
        candidate.designKind === designNode.kind);
      assertUnique(expected.checkIds, `${fixture.source} ${designNode.id} check IDs`);
      const hiddenChecks = expected.checkIds.filter((id) => id === 'geometry.hidden').length;
      requiredChecks += expected.checkIds.length - hiddenChecks;
      notApplicableChecks += hiddenChecks;
      if (
        expected.identityKey !== normalizedKey ||
        expected.semanticRenderKind !== mappedKind ||
        expected.wrapperDepth > contract.limits.maxWrapperDepth ||
        expected.checkIds.length > contract.limits.maxChecksPerNode ||
        expected.bounds.length !== 4 ||
        expected.bounds.some((coordinate) => !Number.isInteger(coordinate) || coordinate < 0) ||
        (expected.wrapperDepth === 0 && expected.identityRenderKind !== mappedKind) ||
        (expected.wrapperDepth === 1 && (
          wrapper?.identityRenderKind !== expected.identityRenderKind ||
          wrapper?.semanticRenderKind !== expected.semanticRenderKind
        ))
      ) {
        throw new Error(`${fixture.source}: mapping contract changed for ${designNode.id}`);
      }
    }
    if (
      fixture.expectedSummary.requiredChecks !== requiredChecks ||
      fixture.expectedSummary.passedChecks !== requiredChecks ||
      fixture.expectedSummary.failedChecks !== 0 ||
      fixture.expectedSummary.notApplicableChecks !== notApplicableChecks
    ) {
      throw new Error(`${fixture.source}: comparison check denominator changed`);
    }
    declaredInputs.add(`${fixture.designIr}\0${fixture.comparisonPath}\0${fixture.previewRequest}`);
  }
  assertUnique([...declaredInputs], 'Layout comparison fixture inputs');
  assertUnique(
    contract.unsupportedDenominators.map((item) => item.mutation),
    'Layout comparison unsupported mutations',
  );
  for (const denominator of contract.unsupportedDenominators) {
    for (const code of denominator.diagnosticCodes) {
      if (!contract.diagnosticCodes.includes(code)) {
        throw new Error(`Undeclared layout comparison diagnostic code ${code}`);
      }
    }
  }

  const example = await readJson(resolve(contractsDirectory, 'examples/layout-comparison.json'));
  const fingerprint = example.comparisonFingerprint;
  delete example.comparisonFingerprint;
  if (createHash('sha256').update(JSON.stringify(example)).digest('hex') !== fingerprint) {
    throw new Error('Layout comparison example fingerprint is stale');
  }
  return contract;
}

async function verifyScreenshotPreprocessing(schemas) {
  const fixtureDirectory = resolve(evaluationDirectory, 'fixtures/visual');
  const contract = await readJson(
    resolve(fixtureDirectory, 'screenshot-preprocessing-contract.json'),
  );
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-screenshot-preprocessing-v1' ||
    JSON.stringify(contract.requiresContracts) !== JSON.stringify([
      'screenshot-preprocessing-v1',
    ]) ||
    contract.activation?.tool !== 'prepare_screenshot' ||
    contract.activation?.status !== 'implemented' ||
    contract.activation?.evidence !== 'static'
  ) {
    throw new Error('Screenshot preprocessing activation or required contract changed');
  }
  if (
    contract.input?.embeddedPngOnly !== true ||
    contract.input?.pathInputAllowed !== false ||
    contract.input?.urlInputAllowed !== false ||
    contract.input?.uriInputAllowed !== false ||
    contract.input?.credentialsAllowed !== false ||
    JSON.stringify(contract.input?.acceptedBitDepths) !== JSON.stringify([8]) ||
    JSON.stringify(contract.input?.acceptedColorTypes) !== JSON.stringify([6]) ||
    JSON.stringify(contract.input?.acceptedFilterTypes) !== JSON.stringify([0, 1, 2, 3, 4]) ||
    JSON.stringify(contract.input?.acceptedInterlaceMethods) !== JSON.stringify([0]) ||
    contract.input?.acceptedSrgbChunk !== 'zero-or-one-valid-rendering-intent' ||
    JSON.stringify(contract.input?.rejectedSemanticChunks) !== JSON.stringify([
      'iCCP', 'cHRM', 'gAMA', 'cICP', 'mDCV', 'cLLI', 'tRNS', 'acTL', 'fcTL', 'fdAT',
    ])
  ) {
    throw new Error('Screenshot preprocessing must accept only embedded non-interlaced RGBA PNG');
  }
  if (
    JSON.stringify(contract.processing?.order) !== JSON.stringify([
      'verify', 'decode', 'crop', 'redact', 'encode',
    ]) ||
    contract.processing?.cropCoordinates !== 'source-image-pixels' ||
    contract.processing?.redactionCoordinates !== 'cropped-output-pixels' ||
    contract.processing?.redactionReplacement !== '#000000ff' ||
    contract.processing?.redactionOverlap !== 'request-order-idempotent' ||
    contract.processing?.resize !== 'none' ||
    contract.interpretation?.systemBars !== 'declaration-only' ||
    contract.interpretation?.automaticSystemBarInference !== false
  ) {
    throw new Error('Screenshot preprocessing ordering or coordinate semantics changed');
  }
  if (
    contract.output?.mediaType !== 'image/png' ||
    JSON.stringify(contract.output?.canonicalChunks) !== JSON.stringify(['IHDR', 'IDAT', 'IEND']) ||
    contract.output?.stripAncillaryChunks !== true ||
    contract.output?.filterType !== 0 ||
    contract.output?.zlibLevel !== 9 ||
    contract.output?.colorSpace !== 'sRGB' ||
    contract.output?.alphaMode !== 'straight'
  ) {
    throw new Error('Screenshot preprocessing canonical PNG output changed');
  }
  if (
    contract.privacy?.reviewRequired !== true ||
    contract.privacy?.providerTransfer !== false ||
    contract.privacy?.networkAccess !== false ||
    contract.privacy?.inputPersistence !== false ||
    contract.privacy?.outputPersistence !== false ||
    contract.privacy?.logs !== 'metadata-only' ||
    contract.privacy?.redactions !== 'explicit-rectangles-only' ||
    contract.privacy?.automaticSensitiveContentDetection !== false
  ) {
    throw new Error('Screenshot privacy, persistence, or provider boundary changed');
  }
  if (
    contract.transport?.protocol !== 'mcp-stdio' ||
    contract.transport?.maxMessageBytes !== 4 * 1024 * 1024 ||
    contract.transport?.maxInputJsonBytes !== 2_000_000 ||
    contract.transport?.maxToolResultJsonBytes !== 2_000_000 ||
    contract.transport?.resultRepresentations !== 2 ||
    contract.transport?.minimumResponseHeadroomBytes !== 194_304 ||
    contract.transport.maxToolResultJsonBytes * contract.transport.resultRepresentations +
      contract.transport.minimumResponseHeadroomBytes > contract.transport.maxMessageBytes
  ) {
    throw new Error('Screenshot preprocessing exceeds the frozen MCP stdio message boundary');
  }
  if (
    TOOL_NAMES.at(-1) !== contract.activation.tool ||
    TOOL_DEFINITIONS.prepare_screenshot?.defaultLimits?.maxInputBytes !==
      contract.transport.maxInputJsonBytes ||
    TOOL_DEFINITIONS.prepare_screenshot?.defaultLimits?.maxOutputBytes !==
      contract.transport.maxToolResultJsonBytes
  ) {
    throw new Error('Screenshot public tool and frozen transport limits diverged');
  }
  if (
    contract.integrity?.verifyCanonicalBase64 !== true ||
    contract.integrity?.verifyDeclaredBytes !== true ||
    contract.integrity?.verifySha256 !== true ||
    contract.integrity?.verifyDimensions !== true ||
    contract.integrity?.verifyChunkCrc !== true ||
    contract.integrity?.verifyDecodedByteCount !== true ||
    contract.integrity?.requestFingerprint !== 'sha256-canonical-json' ||
    contract.integrity?.outputFingerprint !==
      'sha256-canonical-json-without-outputFingerprint'
  ) {
    throw new Error('Screenshot preprocessing integrity contract changed');
  }
  for (const [name, ceiling] of Object.entries({
    maxCompressedBytes: 8 * 1024 * 1024,
    maxDimensionPx: 4096,
    maxDecodedBytes: 64 * 1024 * 1024,
    maxPngChunks: 256,
    maxRedactions: 64,
    maxOutputBytes: 8 * 1024 * 1024,
  })) {
    const value = contract.limits?.[name];
    if (!Number.isInteger(value) || value <= 0 || value > ceiling) {
      throw new Error(`Screenshot preprocessing limit ${name} exceeds its frozen ceiling`);
    }
  }
  if (
    contract.limits.maxCompressedBytes !== 1_310_720 ||
    contract.limits.maxDecodedBytes !== 16 * 1024 * 1024 ||
    contract.limits.maxOutputBytes !== 1_310_720
  ) {
    throw new Error('Screenshot byte limits changed without transport and latency review');
  }
  assertUnique(contract.diagnosticCodes, 'Screenshot preprocessing diagnostic codes');
  for (const code of contract.diagnosticCodes) {
    if (!/^VC-AI-SCREENSHOT-[A-Z0-9-]+$/u.test(code)) {
      throw new Error(`Invalid screenshot preprocessing diagnostic code: ${code}`);
    }
  }

  const schema = schemas.get('screenshot-preprocessing.schema.json');
  if (
    schema.$defs?.pngAsset?.properties?.bytes?.maximum !==
      contract.limits.maxCompressedBytes ||
    schema.$defs?.pngAsset?.properties?.data?.maxLength !==
      Math.ceil(contract.limits.maxCompressedBytes / 3) * 4
  ) {
    throw new Error('Screenshot schema and transport-safe PNG byte limits diverged');
  }
  const declaredRequests = new Set();
  for (const fixture of contract.supportedFixtures) {
    const [request, result] = await Promise.all([
      readJson(resolve(fixtureDirectory, fixture.request)),
      readJson(resolve(fixtureDirectory, fixture.result)),
    ]);
    assertSchemaValue(request, schema, fixture.request);
    assertSchemaValue(result, schema, fixture.result);
    const requestFingerprint = createHash('sha256')
      .update(canonicalJson(request))
      .digest('hex');
    const fingerprintedResult = {...result};
    delete fingerprintedResult.outputFingerprint;
    const outputFingerprint = createHash('sha256')
      .update(canonicalJson(fingerprintedResult))
      .digest('hex');
    const input = decodeScreenshotPng(request.screenshot, contract.limits, fixture.request);
    const output = decodeScreenshotPng(result.output, contract.limits, fixture.result);
    const crop = request.interpretation.crop;
    if (
      crop.x + crop.width > request.screenshot.widthPx ||
      crop.y + crop.height > request.screenshot.heightPx ||
      crop.width > request.output.maxWidthPx ||
      crop.height > request.output.maxHeightPx ||
      request.interpretation.systemBars.leftPx +
        request.interpretation.systemBars.rightPx > request.screenshot.widthPx ||
      request.interpretation.systemBars.topPx +
        request.interpretation.systemBars.bottomPx > request.screenshot.heightPx
    ) {
      throw new Error(`${fixture.request}: crop, output, or system-bar bounds changed`);
    }
    const expectedPixels = Buffer.alloc(crop.width * crop.height * 4);
    for (let y = 0; y < crop.height; y += 1) {
      const sourceOffset = ((crop.y + y) * request.screenshot.widthPx + crop.x) * 4;
      input.pixels.copy(expectedPixels, y * crop.width * 4, sourceOffset, sourceOffset + crop.width * 4);
    }
    for (const redaction of request.privacy.redactions) {
      const rectangle = redaction.rectangle;
      if (
        rectangle.x + rectangle.width > crop.width ||
        rectangle.y + rectangle.height > crop.height
      ) {
        throw new Error(`${fixture.request}: redaction leaves the cropped output`);
      }
      for (let y = rectangle.y; y < rectangle.y + rectangle.height; y += 1) {
        for (let x = rectangle.x; x < rectangle.x + rectangle.width; x += 1) {
          expectedPixels.set([0, 0, 0, 255], (y * crop.width + x) * 4);
        }
      }
    }
    const expectedTransformations = [
      {kind: 'crop', rectangle: crop},
      ...request.privacy.redactions.map((redaction) => ({
        kind: 'redact',
        rectangle: redaction.rectangle,
        replacement: redaction.replacement,
      })),
      {kind: 'strip-metadata'},
    ];
    if (
      fixture.status !== 'implemented' ||
      requestFingerprint !== fixture.expectedRequestFingerprint ||
      requestFingerprint !== result.requestFingerprint ||
      request.screenshot.sha256 !== fixture.expectedInputSha256 ||
      result.output.sha256 !== fixture.expectedOutputSha256 ||
      outputFingerprint !== fixture.expectedOutputFingerprint ||
      outputFingerprint !== result.outputFingerprint ||
      JSON.stringify([request.screenshot.widthPx, request.screenshot.heightPx]) !==
        JSON.stringify(fixture.expectedInputSize) ||
      JSON.stringify([result.output.widthPx, result.output.heightPx]) !==
        JSON.stringify(fixture.expectedOutputSize) ||
      result.output.widthPx !== crop.width ||
      result.output.heightPx !== crop.height ||
      result.output.bytes > contract.limits.maxOutputBytes ||
      !output.pixels.equals(expectedPixels) ||
      JSON.stringify(output.chunkTypes) !== JSON.stringify(contract.output.canonicalChunks) ||
      JSON.stringify(result.transformations) !== JSON.stringify(expectedTransformations) ||
      result.privacy.redactionsApplied !== request.privacy.redactions.length ||
      result.privacy.redactionsApplied !== fixture.expectedRedactions ||
      result.privacy.providerTransfer !== request.privacy.providerTransfer ||
      result.privacy.inputPersisted !== request.privacy.persistInput ||
      result.privacy.logs !== request.privacy.logs ||
      result.diagnostics.length !== 0
    ) {
      throw new Error(`${fixture.request}: screenshot preprocessing golden changed`);
    }
    declaredRequests.add(fixture.request);
  }
  for (const fixture of contract.unsupportedFixtures) {
    const request = await readJson(resolve(fixtureDirectory, fixture.request));
    const violations = validateSchemaValue(request, schema);
    const provesPathInput = Object.hasOwn(request.screenshot ?? {}, 'path');
    const provesProviderTransfer = request.privacy?.providerTransfer === true;
    if (
      fixture.schemaValid !== false ||
      violations.length === 0 ||
      (fixture.diagnosticCodes.includes('VC-AI-SCREENSHOT-PATH-DENIED') && !provesPathInput) ||
      (fixture.diagnosticCodes.includes('VC-AI-SCREENSHOT-PROVIDER-TRANSFER-DENIED') &&
        !provesProviderTransfer)
    ) {
      throw new Error(`${fixture.request}: screenshot fail-closed denominator changed`);
    }
    for (const code of fixture.diagnosticCodes) {
      if (!contract.diagnosticCodes.includes(code)) {
        throw new Error(`${fixture.request}: undeclared screenshot diagnostic ${code}`);
      }
    }
    declaredRequests.add(fixture.request);
  }
  assertUnique([...declaredRequests], 'Screenshot preprocessing fixture requests');

  const [exampleRequest, exampleResult] = await Promise.all([
    readJson(resolve(contractsDirectory, 'examples/screenshot-preprocessing-request.json')),
    readJson(resolve(contractsDirectory, 'examples/screenshot-preprocessing-result.json')),
  ]);
  const fixture = contract.supportedFixtures[0];
  const [fixtureRequest, fixtureResult] = await Promise.all([
    readJson(resolve(fixtureDirectory, fixture.request)),
    readJson(resolve(fixtureDirectory, fixture.result)),
  ]);
  if (
    JSON.stringify(exampleRequest) !== JSON.stringify(fixtureRequest) ||
    JSON.stringify(exampleResult) !== JSON.stringify(fixtureResult)
  ) {
    throw new Error('Screenshot preprocessing examples must stay aligned with the golden fixture');
  }
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
  const xmlSubsetV2 = await verifyXmlSubsetV2(schemas);
  const xmlProjectContext = await verifyXmlProjectContext(schemas);
  const xmlLayoutDependencies = await verifyXmlLayoutDependencies(schemas);
  const generatedPreview = await verifyGeneratedPreview(schemas);
  const layoutComparison = await verifyLayoutComparison(schemas);
  const screenshotPreprocessing = await verifyScreenshotPreprocessing(schemas);
  const metrics = await verifyMetrics(schemas);
  const corpus = await verifyCorpus(schemas, metrics);
  return {
    schemas: schemas.size,
    metrics: metrics.metrics.length,
    cases: corpus.cases.length,
    fixtures: corpus.cases.filter((item) => item.input.kind === 'fixture').length,
    reservedCapabilities: versions.reservedCapabilities.length,
    xmlFixtures: xmlSubset.supportedFixtures.length + xmlSubset.unsupportedFixtures.length,
    xmlV2Fixtures:
      xmlSubsetV2.supportedFixtures.length + xmlSubsetV2.unsupportedFixtures.length,
    xmlProjectContextFixtures:
      xmlProjectContext.supportedFixtures.length + xmlProjectContext.unsupportedFixtures.length,
    xmlLayoutDependencyFixtures:
      xmlLayoutDependencies.supportedFixtures.length +
        xmlLayoutDependencies.unsupportedFixtures.length,
    generatedPreviewFixtures:
      generatedPreview.supportedFixtures.length + generatedPreview.unsupportedFixtures.length,
    layoutComparisonFixtures: layoutComparison.supportedFixtures.length,
    screenshotPreprocessingFixtures:
      screenshotPreprocessing.supportedFixtures.length +
        screenshotPreprocessing.unsupportedFixtures.length,
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
          `${summary.xmlV2Fixtures} frozen XML v2 fixtures and ` +
          `${summary.xmlProjectContextFixtures} frozen XML project-context fixtures and ` +
          `${summary.xmlLayoutDependencyFixtures} frozen XML layout-dependency fixtures and ` +
          `${summary.generatedPreviewFixtures} frozen generated-Preview fixtures and ` +
          `${summary.layoutComparisonFixtures} frozen layout-comparison fixtures and ` +
          `${summary.screenshotPreprocessingFixtures} frozen screenshot-preprocessing fixtures.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
