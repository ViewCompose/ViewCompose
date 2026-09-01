import {createHash} from 'node:crypto';
import {
  DESIGN_IR_V2_SCHEMA,
  FIGMA_EXPORT_SCHEMA,
  FIGMA_IMPORT_LIMITS,
  FIGMA_IMPORT_SCHEMA,
  FIGMA_IMPORT_REQUEST_SCHEMA,
} from './figma-contract.mjs';
import {canonicalJson} from './screenshot-contract.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {parseStrictJson, StrictJsonError} from './strict-json.mjs';
import {
  createFigmaRenderPlan,
  FigmaRenderPlanError,
  generateFigmaArtifacts,
} from './figma-render-plan.mjs';
import {renderGeneratedPreview} from './generated-preview-adapter.mjs';
import {compareGeneratedLayout} from './layout-comparator.mjs';
import {diagnostic, loadKnowledgeManifest, toolResult, utf8Bytes} from './tool-core.mjs';

const TOOL_NAME = 'convert_figma_to_viewcompose';
const ACTIVE_PATH = /(?:^|\.)(?:pluginData|sharedPluginData|script|html|iframe|devResource)(?:\.|$)/iu;
const CREDENTIAL_PATH = /(?:^|\.)(?:apiKey|accessToken|credential|password|secret)(?:\.|$)/iu;
const URL_VALUE = /(?:^|\s)[a-z][a-z0-9+.-]*:(?:\/\/)?/iu;
const CONTAINER_TYPES = new Set(['FRAME', 'GROUP', 'COMPONENT', 'COMPONENT_SET', 'INSTANCE']);

class FigmaImportError extends Error {
  constructor(code, message, nextAction, status = 'invalid') {
    super(message);
    this.name = 'FigmaImportError';
    this.code = code;
    this.nextAction = nextAction;
    this.status = status;
  }
}

function fail(code, message, nextAction, status) {
  throw new FigmaImportError(code, message, nextAction, status);
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function fingerprint(value) {
  return sha256(canonicalJson(value));
}

function throwIfCancelled(signal) {
  if (signal?.aborted) {
    fail(
      'VC-AI-FIGMA-CANCELLED',
      'Offline Figma import was cancelled before an immutable result was accepted.',
      'Retry the same export when import is still required.',
      'cancelled',
    );
  }
}

function safePath(path) {
  if (
    typeof path !== 'string' || path.length === 0 || path.includes('\0') || path.includes('\\') ||
    path.startsWith('/') || /^[a-zA-Z]:/u.test(path)
  ) return false;
  return path.split('/').every((segment) => segment.length > 0 && segment !== '.' && segment !== '..');
}

function exactUnique(items, selector, label) {
  const seen = new Set();
  for (const item of items) {
    const identity = selector(item);
    if (seen.has(identity)) {
      fail(
        'VC-AI-FIGMA-GRAPH-INVALID',
        `${label} repeats identity ${identity}.`,
        'Export one acyclic graph with unique node and catalog identities.',
      );
    }
    seen.add(identity);
  }
  return seen;
}

function assertFiniteAndBoundedStrings(value, path = '$') {
  if (typeof value === 'number' && !Number.isFinite(value)) {
    fail(
      'VC-AI-FIGMA-CONTRACT-INVALID',
      `${path} contains a non-finite number.`,
      'Export only finite JSON numbers.',
    );
  }
  if (typeof value === 'string' && utf8Bytes(value) > FIGMA_IMPORT_LIMITS.maxStringBytes) {
    fail(
      'VC-AI-FIGMA-LIMIT-EXCEEDED',
      `${path} exceeds the 32 KiB string ceiling.`,
      'Reduce or redact the declared source value.',
      'limited',
    );
  }
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertFiniteAndBoundedStrings(item, `${path}[${index}]`));
  } else if (value !== null && typeof value === 'object') {
    Object.entries(value).forEach(([key, item]) => {
      if (utf8Bytes(key) > FIGMA_IMPORT_LIMITS.maxIdBytes) {
        fail(
          'VC-AI-FIGMA-LIMIT-EXCEEDED',
          `${path} contains an object key above 256 bytes.`,
          'Use bounded stable identifiers.',
          'limited',
        );
      }
      assertFiniteAndBoundedStrings(item, `${path}.${key}`);
    });
  }
}

function assetSignatureValid(asset, bytes) {
  if (asset.mediaType === 'image/png') {
    return bytes.length >= 8 && bytes.subarray(0, 8).toString('hex') === '89504e470d0a1a0a';
  }
  if (asset.mediaType === 'image/jpeg') {
    return bytes.length >= 4 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes.at(-2) === 0xff &&
      bytes.at(-1) === 0xd9;
  }
  if (asset.mediaType === 'image/webp') {
    return bytes.length >= 12 && bytes.subarray(0, 4).toString('ascii') === 'RIFF' &&
      bytes.subarray(8, 12).toString('ascii') === 'WEBP';
  }
  return true;
}

function validateVectorAsset(asset, bytes) {
  if (asset.mediaType !== 'application/vnd.viewcompose.vector+json') return 0;
  let vector;
  try {
    vector = parseStrictJson(bytes.toString('utf8'), {maxDepth: 16});
  } catch {
    fail(
      'VC-AI-FIGMA-SECURITY-ACTIVE-CONTENT',
      `Vector asset ${asset.id} is not the inert vector-command JSON format.`,
      'Export finite M/L/Q/C/Z command JSON without SVG, XML, script, filters, masks, or text.',
    );
  }
  if (
    vector === null || typeof vector !== 'object' || Array.isArray(vector) ||
    JSON.stringify(Object.keys(vector).sort()) !== JSON.stringify(['commands', 'schemaVersion']) ||
    vector.schemaVersion !== 1 || !Array.isArray(vector.commands)
  ) {
    fail(
      'VC-AI-FIGMA-SECURITY-ACTIVE-CONTENT',
      `Vector asset ${asset.id} violates the inert vector-command envelope.`,
      'Use only the frozen vector-command JSON object.',
    );
  }
  if (vector.commands.length > FIGMA_IMPORT_LIMITS.maxVectorCommands) {
    fail(
      'VC-AI-FIGMA-LIMIT-EXCEEDED',
      `Vector asset ${asset.id} exceeds the per-node command ceiling.`,
      'Simplify or rasterize the selected vector.',
      'limited',
    );
  }
  for (const command of vector.commands) {
    if (
      command === null || typeof command !== 'object' || Array.isArray(command) ||
      !['M', 'L', 'Q', 'C', 'Z'].includes(command.op) || !Array.isArray(command.values) ||
      command.values.length > 6 || command.values.some((value) => !Number.isFinite(value)) ||
      Object.keys(command).some((key) => !['op', 'values'].includes(key))
    ) {
      fail(
        'VC-AI-FIGMA-SECURITY-ACTIVE-CONTENT',
        `Vector asset ${asset.id} contains a command outside the frozen finite subset.`,
        'Use only finite M/L/Q/C/Z path commands.',
      );
    }
  }
  return vector.commands.length;
}

function validateAssets(exported) {
  let totalBytes = 0;
  let vectorCommands = 0;
  const assetById = new Map();
  for (const asset of exported.assets) {
    if (!safePath(asset.logicalPath)) {
      fail(
        'VC-AI-FIGMA-PATH-INVALID',
        `Asset ${asset.id} has an unsafe logical path.`,
        'Use one relative normalized path without traversal, drive, backslash, or empty segments.',
      );
    }
    const bytes = Buffer.from(asset.data, 'base64');
    if (
      bytes.toString('base64') !== asset.data || bytes.length !== asset.bytes ||
      bytes.length > FIGMA_IMPORT_LIMITS.maxAssetBytes || sha256(bytes) !== asset.sha256
    ) {
      fail(
        'VC-AI-FIGMA-INTEGRITY-MISMATCH',
        `Asset ${asset.id} does not match its declared canonical base64, byte count, and SHA-256.`,
        'Re-export the exact embedded bytes and identity.',
      );
    }
    if (!assetSignatureValid(asset, bytes)) {
      fail(
        'VC-AI-FIGMA-INTEGRITY-MISMATCH',
        `Asset ${asset.id} does not match its declared media signature.`,
        'Use one declared PNG, JPEG, WebP, or inert vector-command JSON asset.',
      );
    }
    totalBytes += bytes.length;
    vectorCommands += validateVectorAsset(asset, bytes);
    assetById.set(asset.id, {asset, bytes});
  }
  if (
    totalBytes > FIGMA_IMPORT_LIMITS.maxAssetBytesTotal ||
    vectorCommands > FIGMA_IMPORT_LIMITS.maxVectorCommandsTotal
  ) {
    fail(
      'VC-AI-FIGMA-LIMIT-EXCEEDED',
      'Embedded assets or vector commands exceed the aggregate offline import ceiling.',
      'Reduce the selection, simplify vectors, or recompress raster assets.',
      'limited',
    );
  }
  return assetById;
}

function validateSecurity(exported) {
  for (const node of exported.nodes) {
    for (const fact of [...node.variantProperties, ...node.facts]) {
      if (ACTIVE_PATH.test(fact.path)) {
        fail(
          'VC-AI-FIGMA-SECURITY-ACTIVE-CONTENT',
          `Node ${node.id} declares forbidden active-content path ${fact.path}.`,
          'Exclude plugin data, scripts, HTML, iframes, and dev-resource payloads before export.',
        );
      }
      if (CREDENTIAL_PATH.test(fact.path)) {
        fail(
          'VC-AI-FIGMA-SECURITY-ACTIVE-CONTENT',
          `Node ${node.id} declares credential-shaped path ${fact.path}.`,
          'Remove credentials and re-export the provider-offline snapshot.',
        );
      }
      if (fact.value.kind === 'string' && URL_VALUE.test(fact.value.value)) {
        fail(
          'VC-AI-FIGMA-SECURITY-FORBIDDEN-REFERENCE',
          `Node ${node.id} declares a URL or URI in ${fact.path}.`,
          'Embed declared bytes or replace the value with a stable local identity.',
        );
      }
    }
  }
}

function detectTokenCycles(tokens) {
  const byId = new Map(tokens.map((token) => [token.id, token]));
  const visiting = new Set();
  const visited = new Set();
  const visit = (id) => {
    if (visiting.has(id)) {
      fail(
        'VC-AI-FIGMA-GRAPH-INVALID',
        `Token alias graph contains a cycle at ${id}.`,
        'Resolve token aliases to one acyclic declared primitive graph.',
      );
    }
    if (visited.has(id)) return;
    visiting.add(id);
    for (const alias of byId.get(id)?.aliases ?? []) {
      if (!byId.has(alias)) {
        fail(
          'VC-AI-FIGMA-REFERENCE-UNRESOLVED',
          `Token ${id} aliases undeclared token ${alias}.`,
          'Include every referenced token or remove the alias.',
        );
      }
      visit(alias);
    }
    visiting.delete(id);
    visited.add(id);
  };
  for (const id of byId.keys()) visit(id);
  return byId;
}

function tokenValueToIr(token, tokenById, resolving = new Set()) {
  const direct = token.value;
  const expected = {
    BOOLEAN: 'boolean',
    COLOR: 'color',
    FLOAT: 'number',
    STRING: 'string',
  }[token.resolvedType];
  if (direct.kind === 'reference') {
    if (direct.referenceType !== 'token' || !token.aliases.includes(direct.id)) {
      fail(
        'VC-AI-FIGMA-DECLARATION-MISSING',
        `Token ${token.id} has a value reference that is not one of its declared token aliases.`,
        'Declare the referenced token identity in aliases and keep token values offline.',
      );
    }
    const target = tokenById.get(direct.id);
    if (!target) {
      fail(
        'VC-AI-FIGMA-REFERENCE-UNRESOLVED',
        `Token ${token.id} resolves through missing token ${direct.id}.`,
        'Include every referenced token in the bounded token catalog.',
      );
    }
    if (target.resolvedType !== token.resolvedType || resolving.has(target.id)) {
      fail(
        'VC-AI-FIGMA-GRAPH-INVALID',
        `Token ${token.id} has a cyclic or type-changing alias through ${target.id}.`,
        'Resolve aliases to an acyclic token with the same declared resolved type.',
      );
    }
    const resolvedValue = tokenValueToIr(target, tokenById, new Set([...resolving, token.id]));
    return {
      kind: 'token',
      tokenId: direct.id,
      resolvedValue: resolvedValue.kind === 'token' ? resolvedValue.resolvedValue : resolvedValue,
    };
  }
  if (direct.kind !== expected) {
    fail(
      'VC-AI-FIGMA-CONTRACT-INVALID',
      `Token ${token.id} declares ${token.resolvedType} but carries ${direct.kind}.`,
      'Export a primitive token value that matches its declared resolved type.',
    );
  }
  return direct.kind === 'color'
    ? {kind: 'color', argb: direct.argb}
    : irLiteral(direct.value);
}

function provenanceValue(fact) {
  if (['string', 'number', 'boolean', 'null'].includes(fact.value.kind)) {
    return irLiteral(fact.value.value);
  }
  if (fact.value.kind === 'color') return {kind: 'color', argb: fact.value.argb};
  if (fact.value.kind === 'reference') {
    return irLiteral(`${fact.value.referenceType}:${fact.value.id}`);
  }
  if (fact.value.kind === 'digest') return irLiteral(`sha256:${fact.value.sha256}`);
  return irLiteral(canonicalJson(fact.value.values));
}

function indexGraph(exported, assets) {
  const nodeIds = exactUnique(exported.nodes, (node) => node.id, 'Figma nodes');
  const componentIds = exactUnique(exported.catalogs.components, (item) => item.id, 'Components');
  const tokenIds = exactUnique(exported.catalogs.tokens, (item) => item.id, 'Tokens');
  const styleIds = exactUnique(exported.catalogs.styles, (item) => item.id, 'Styles');
  const fontIds = exactUnique(exported.catalogs.fonts, (item) => item.id, 'Fonts');
  exactUnique(exported.assets, (item) => item.id, 'Assets');
  const tokenById = detectTokenCycles(exported.catalogs.tokens);
  const nodeById = new Map(exported.nodes.map((node) => [node.id, node]));
  const parents = new Map(exported.nodes.map((node) => [node.id, 0]));
  let childReferences = 0;
  let facts = 0;
  const referencedAssets = new Set();

  const referenceSets = {
    asset: new Set(assets.keys()),
    component: componentIds,
    font: fontIds,
    node: nodeIds,
    style: styleIds,
    token: tokenIds,
  };
  for (const node of exported.nodes) {
    exactUnique(node.facts, (fact) => fact.path, `Facts for node ${node.id}`);
    exactUnique(node.variantProperties, (fact) => fact.path, `Variant facts for node ${node.id}`);
    facts += node.facts.length + node.variantProperties.length;
    for (const childId of node.childIds) {
      childReferences += 1;
      if (!nodeById.has(childId)) {
        fail(
          'VC-AI-FIGMA-REFERENCE-UNRESOLVED',
          `Node ${node.id} references missing child ${childId}.`,
          'Export complete selected subtrees.',
        );
      }
      parents.set(childId, parents.get(childId) + 1);
      if (parents.get(childId) > 1) {
        fail(
          'VC-AI-FIGMA-GRAPH-INVALID',
          `Node ${childId} has more than one structural parent.`,
          'Flatten instances without sharing mutable child nodes.',
        );
      }
    }
    for (const componentId of node.componentLineage) {
      if (!componentIds.has(componentId)) {
        fail(
          'VC-AI-FIGMA-REFERENCE-UNRESOLVED',
          `Node ${node.id} has undeclared component lineage ${componentId}.`,
          'Include each referenced component identity.',
        );
      }
    }
    for (const fact of [...node.variantProperties, ...node.facts]) {
      if (fact.value.kind !== 'reference') continue;
      const set = referenceSets[fact.value.referenceType];
      if (!set.has(fact.value.id)) {
        fail(
          'VC-AI-FIGMA-DECLARATION-MISSING',
          `Node ${node.id} references undeclared ${fact.value.referenceType} ${fact.value.id}.`,
          'Declare every asset, component, font, node, style, and token before import.',
        );
      }
      if (fact.value.referenceType === 'asset') referencedAssets.add(fact.value.id);
    }
  }
  if (childReferences > FIGMA_IMPORT_LIMITS.maxChildReferences || facts > 32768) {
    fail(
      'VC-AI-FIGMA-LIMIT-EXCEEDED',
      'The selected graph exceeds child-reference or render-fact limits.',
      'Reduce the selected design subtree.',
      'limited',
    );
  }
  for (const selected of exported.document.selectedNodeIds) {
    if (!nodeById.has(selected)) {
      fail(
        'VC-AI-FIGMA-REFERENCE-UNRESOLVED',
        `Selected root ${selected} is missing.`,
        'Export every selected root and its complete subtree.',
      );
    }
    if (parents.get(selected) !== 0) {
      fail(
        'VC-AI-FIGMA-GRAPH-INVALID',
        `Selected root ${selected} is also a child node.`,
        'Select independent roots only.',
      );
    }
  }
  const visiting = new Set();
  const visited = new Set();
  const visit = (id) => {
    if (visiting.has(id)) {
      fail(
        'VC-AI-FIGMA-GRAPH-INVALID',
        `Selected node graph contains a cycle at ${id}.`,
        'Export one acyclic selected hierarchy.',
      );
    }
    if (visited.has(id)) return;
    visiting.add(id);
    nodeById.get(id).childIds.forEach(visit);
    visiting.delete(id);
    visited.add(id);
  };
  exported.document.selectedNodeIds.forEach(visit);
  if (visited.size !== exported.nodes.length) {
    fail(
      'VC-AI-FIGMA-GRAPH-INVALID',
      'The export contains nodes outside the complete selected subtrees.',
      'Remove hidden or unrelated detached nodes before import.',
    );
  }
  for (const id of assets.keys()) {
    if (!referencedAssets.has(id)) {
      fail(
        'VC-AI-FIGMA-DECLARATION-MISSING',
        `Asset ${id} is embedded but not referenced by a selected node.`,
        'Remove unreferenced bytes from the offline export.',
      );
    }
  }
  return {nodeById, tokenById, facts, referencedAssets};
}

function factMap(node) {
  return new Map(node.facts.map((fact) => [fact.path, fact]));
}

function valueOf(fact) {
  if (!fact) return undefined;
  if (['string', 'number', 'boolean', 'null'].includes(fact.value.kind)) return fact.value.value;
  if (fact.value.kind === 'color') return fact.value.argb;
  if (fact.value.kind === 'reference') return fact.value.id;
  return fact.value;
}

function stableDocumentId(identity) {
  const normalized = identity.replace(/[^a-zA-Z0-9._:-]+/gu, '-').replace(/^-+|-+$/gu, '');
  return (normalized || `figma-${sha256(identity).slice(0, 16)}`).slice(0, 256);
}

function irLiteral(value) {
  return {kind: 'literal', value};
}

function irDimension(value, unit) {
  return {kind: 'dimension', value, unit};
}

function irEnum(type, value) {
  return {kind: 'enum', type, value};
}

function auditAndMap(exported, graph, assets, inputFingerprint) {
  const decisions = [];
  const unsupported = [];
  const decisionKeys = new Set();
  const unit = exported.units.dpPerUnit;
  const textUnit = exported.units.spPerUnit;

  const decide = (node, fact, {
    targetPath,
    status = 'mapped',
    severity = 'info',
    disposition = 'emitted',
    reasonCode,
    reason,
  }) => {
    const key = `${node.id}\0${fact.path}`;
    if (decisionKeys.has(key)) return;
    decisionKeys.add(key);
    const entry = {
      nodeId: node.id,
      sourcePath: fact.path,
      phase: fact.phase,
      status,
      severity,
      disposition,
      sourceValueFingerprint: fingerprint(fact.value),
      reasonCode,
    };
    if (targetPath) entry.targetPath = targetPath;
    decisions.push(entry);
    if (status === 'unsupported') {
      if (unsupported.length >= FIGMA_IMPORT_LIMITS.maxUnsupported) {
        fail(
          'VC-AI-FIGMA-LIMIT-EXCEEDED',
          'Unsupported mapping decisions exceed the 1024-entry inspect ceiling.',
          'Reduce the selected subtree or normalize unsupported source facts.',
          'limited',
        );
      }
      unsupported.push({
        nodeId: node.id,
        sourcePath: fact.path,
        code: reasonCode,
        reason,
        sourceValueFingerprint: entry.sourceValueFingerprint,
        severity: severity === 'info' ? 'warning' : severity,
        disposition: disposition === 'emitted' ? 'preserved' : disposition,
      });
    }
  };

  const required = (node, facts, path) => {
    const fact = facts.get(path);
    if (!fact) {
      fail(
        'VC-AI-FIGMA-DECLARATION-MISSING',
        `Node ${node.id} is missing required render fact ${path}.`,
        'Re-export with complete geometry, layout, text, resource, and accessibility facts.',
      );
    }
    return fact;
  };

  const mapLayout = (node, facts, container) => {
    const x = required(node, facts, 'geometry.x');
    const y = required(node, facts, 'geometry.y');
    const width = required(node, facts, 'geometry.width');
    const height = required(node, facts, 'geometry.height');
    const widthMode = required(node, facts, 'layout.width');
    const heightMode = required(node, facts, 'layout.height');
    for (const [fact, target] of [
      [x, 'layout.xDp'], [y, 'layout.yDp'], [width, 'layout.width.valueDp'],
      [height, 'layout.height.valueDp'], [widthMode, 'layout.width.mode'],
      [heightMode, 'layout.height.mode'],
    ]) {
      decide(node, fact, {targetPath: target, reasonCode: 'VC-AI-FIGMA-LAYOUT-MAPPED'});
    }
    const sizing = (modeFact, dimensionFact) => {
      const mode = `${valueOf(modeFact)}`.toLowerCase();
      if (!['fixed', 'hug', 'fill'].includes(mode)) {
        decide(node, modeFact, {
          status: 'unsupported', severity: 'error', disposition: 'blocked',
          reasonCode: 'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
          reason: `Sizing mode ${valueOf(modeFact)} is outside FIXED/HUG/FILL.`,
        });
        return {mode: 'fixed', valueDp: valueOf(dimensionFact) * unit};
      }
      return {mode, valueDp: valueOf(dimensionFact) * unit};
    };
    const padding = {left: 0, top: 0, right: 0, bottom: 0};
    let gapDp = 0;
    let mainAxisArrangement = 'start';
    let horizontalAlignment = 'start';
    let verticalAlignment = 'start';
    let clip = 'none';
    let mode = 'fixed';
    if (container) {
      const layoutMode = required(node, facts, 'layout.mode');
      const wrap = required(node, facts, 'layout.wrap');
      const primary = required(node, facts, 'layout.primaryAlignment');
      const counter = required(node, facts, 'layout.counterAlignment');
      const gap = required(node, facts, 'layout.gap');
      const clipFact = required(node, facts, 'clip.enabled');
      for (const edge of Object.keys(padding)) {
        const fact = required(node, facts, `layout.padding.${edge}`);
        padding[edge] = valueOf(fact) * unit;
        decide(node, fact, {
          targetPath: `layout.padding.${edge}`,
          reasonCode: 'VC-AI-FIGMA-LAYOUT-MAPPED',
        });
      }
      mode = {HORIZONTAL: 'row', VERTICAL: 'column', NONE: 'fixed'}[valueOf(layoutMode)];
      if (!mode || valueOf(wrap) !== 'NO_WRAP') {
        for (const fact of [layoutMode, wrap]) {
          decide(node, fact, {
            status: 'unsupported', severity: 'error', disposition: 'blocked',
            reasonCode: 'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
            reason: 'Only NONE, HORIZONTAL, or VERTICAL non-wrapping layout is supported.',
          });
        }
        mode ??= 'fixed';
      } else {
        decide(node, layoutMode, {targetPath: 'layout.mode', reasonCode: 'VC-AI-FIGMA-LAYOUT-MAPPED'});
        decide(node, wrap, {targetPath: 'layout.wrap', reasonCode: 'VC-AI-FIGMA-LAYOUT-MAPPED'});
      }
      const primaryValue = valueOf(primary);
      const counterValue = valueOf(counter);
      const arrangement = {
        MIN: 'start', CENTER: 'center', MAX: 'end', SPACE_BETWEEN: 'space-between',
        SPACE_AROUND: 'space-around', SPACE_EVENLY: 'space-evenly',
      };
      const alignment = {MIN: 'start', CENTER: 'center', MAX: 'end'};
      mainAxisArrangement = arrangement[primaryValue] ?? 'start';
      const crossAxisAlignment = alignment[counterValue];
      if (!Object.hasOwn(arrangement, primaryValue) || crossAxisAlignment === undefined) {
        for (const fact of [primary, counter]) {
          decide(node, fact, {
            status: 'unsupported', severity: 'error', disposition: 'blocked',
            reasonCode: 'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
            reason: 'Alignment is outside the bounded start/center/end and main-axis spacing subset.',
          });
        }
      }
      if (mode === 'row') {
        horizontalAlignment = alignment[primaryValue] ?? 'start';
        verticalAlignment = crossAxisAlignment ?? 'start';
      } else {
        verticalAlignment = alignment[primaryValue] ?? 'start';
        horizontalAlignment = crossAxisAlignment ?? 'start';
      }
      for (const [fact, target] of [[primary, 'layout.primaryAlignment'], [counter, 'layout.counterAlignment']]) {
        decide(node, fact, {targetPath: target, reasonCode: 'VC-AI-FIGMA-LAYOUT-MAPPED'});
      }
      gapDp = valueOf(gap) * unit;
      clip = valueOf(clipFact) ? 'rectangle' : 'none';
      decide(node, gap, {targetPath: 'layout.gapDp', reasonCode: 'VC-AI-FIGMA-LAYOUT-MAPPED'});
      decide(node, clipFact, {targetPath: 'layout.clip', reasonCode: 'VC-AI-FIGMA-STYLE-MAPPED'});
    }
    return {
      mode,
      xDp: valueOf(x) * unit,
      yDp: valueOf(y) * unit,
      width: sizing(widthMode, width),
      height: sizing(heightMode, height),
      padding,
      gapDp,
      mainAxisArrangement,
      horizontalAlignment,
      verticalAlignment,
      clip,
    };
  };

  const mapAccessibility = (node, facts, properties, semantics) => {
    const decorative = facts.get('accessibility.decorative');
    const name = facts.get('accessibility.name');
    if (decorative && valueOf(decorative) === true) {
      properties.push({name: 'contentDescription', value: irLiteral(null)});
      semantics.push({name: 'decorative', value: irLiteral(true)});
      decide(node, decorative, {
        targetPath: 'semantics.decorative',
        reasonCode: 'VC-AI-FIGMA-SEMANTICS-MAPPED',
      });
      return;
    }
    if (name && typeof valueOf(name) === 'string' && valueOf(name).trim().length > 0) {
      properties.push({name: 'contentDescription', value: irLiteral(valueOf(name))});
      semantics.push({name: 'role', value: irEnum('semantic-role', 'image')});
      decide(node, name, {
        targetPath: 'properties.contentDescription',
        reasonCode: 'VC-AI-FIGMA-SEMANTICS-MAPPED',
      });
      return;
    }
    fail(
      'VC-AI-FIGMA-DECLARATION-MISSING',
      `Image-like node ${node.id} lacks explicit accessibility name or decorative intent.`,
      'Declare accessibility.name or accessibility.decorative without inference.',
    );
  };

  const mapNode = (node) => {
    const facts = factMap(node);
    const container = CONTAINER_TYPES.has(node.type);
    const layout = mapLayout(node, facts, container);
    const properties = [];
    const semantics = [];
    let kind;
    if (container) {
      kind = layout.mode === 'row' ? 'row' : layout.mode === 'column' ? 'column' : 'box';
      const fill = facts.get('fill.solid');
      if (fill) {
        properties.push({name: 'backgroundColor', value: {kind: 'color', argb: valueOf(fill)}});
        decide(node, fill, {targetPath: 'properties.backgroundColor', reasonCode: 'VC-AI-FIGMA-STYLE-MAPPED'});
      }
      if (node.type === 'INSTANCE' && node.componentLineage.length === 0) {
        fail(
          'VC-AI-FIGMA-DECLARATION-MISSING',
          `Instance ${node.id} has no declared component lineage.`,
          'Resolve the instance component and selected variant properties before export.',
        );
      }
    } else if (node.type === 'TEXT') {
      kind = 'text';
      const characters = required(node, facts, 'text.characters');
      const font = required(node, facts, 'text.font');
      const fontSize = required(node, facts, 'text.fontSize');
      const lineHeight = required(node, facts, 'text.lineHeight');
      const letterSpacing = required(node, facts, 'text.letterSpacing');
      const fill = required(node, facts, 'text.fill');
      const fontRecord = exported.catalogs.fonts.find((item) => item.id === valueOf(font));
      if (!fontRecord || fontRecord.genericFamily === 'custom') {
        decide(node, font, {
          status: 'unsupported', severity: 'error', disposition: 'blocked',
          reasonCode: 'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
          reason: 'Only declared generic system font families are renderable in v1.',
        });
      } else {
        properties.push(
          {name: 'fontFamily', value: irEnum('generic-font-family', fontRecord.genericFamily)},
          {name: 'fontWeight', value: irLiteral(fontRecord.weight)},
          {name: 'fontStyle', value: irEnum('font-style', fontRecord.style)},
        );
        decide(node, font, {
          targetPath: 'properties.fontFamily,fontWeight,fontStyle',
          reasonCode: 'VC-AI-FIGMA-STYLE-MAPPED',
        });
      }
      properties.push(
        {name: 'text', value: irLiteral(valueOf(characters))},
        {name: 'fontSize', value: irDimension(valueOf(fontSize) * textUnit, 'sp')},
        {name: 'lineHeight', value: irDimension(valueOf(lineHeight) * textUnit, 'sp')},
        {
          name: 'letterSpacingEm',
          value: irLiteral(valueOf(letterSpacing) / Math.max(valueOf(fontSize), Number.EPSILON)),
        },
        {name: 'color', value: {kind: 'color', argb: valueOf(fill)}},
      );
      for (const [fact, target] of [
        [characters, 'properties.text'], [fontSize, 'properties.fontSize'],
        [lineHeight, 'properties.lineHeight'], [letterSpacing, 'properties.letterSpacingEm'],
        [fill, 'properties.color'],
      ]) {
        decide(node, fact, {
          targetPath: target,
          reasonCode: fact === characters ? 'VC-AI-FIGMA-TEXT-MAPPED' : 'VC-AI-FIGMA-STYLE-MAPPED',
        });
      }
      const role = facts.get('accessibility.role');
      if (role) {
        semantics.push({name: 'role', value: irEnum('semantic-role', `${valueOf(role)}`.toLowerCase())});
        decide(node, role, {targetPath: 'semantics.role', reasonCode: 'VC-AI-FIGMA-SEMANTICS-MAPPED'});
      }
    } else if (node.type === 'RECTANGLE' && facts.has('image.asset')) {
      kind = 'image';
      const source = facts.get('image.asset');
      const scale = required(node, facts, 'image.scale');
      const asset = assets.get(valueOf(source))?.asset;
      if (!asset || asset.redistribution !== 'allowed' || asset.mediaType !== 'image/png') {
        decide(node, source, {
          status: 'unsupported', severity: 'error', disposition: 'blocked',
          reasonCode: 'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
          reason: 'Figma v1 generation requires verified redistributable PNG bytes.',
        });
      } else {
        properties.push({name: 'source', value: {kind: 'resource', resourceId: asset.id}});
        decide(node, source, {targetPath: 'properties.source', reasonCode: 'VC-AI-FIGMA-ASSET-MAPPED'});
      }
      const mappedScale = {FIT: 'fit', FILL: 'fill-bounds', CROP: 'crop'}[valueOf(scale)];
      if (!mappedScale) {
        decide(node, scale, {
          status: 'unsupported', severity: 'error', disposition: 'blocked',
          reasonCode: 'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
          reason: 'Image scale must be FIT, FILL, or CROP without transform or tiling.',
        });
      } else {
        properties.push({name: 'contentScale', value: irEnum('image-content-scale', mappedScale)});
        decide(node, scale, {targetPath: 'properties.contentScale', reasonCode: 'VC-AI-FIGMA-STYLE-MAPPED'});
      }
      mapAccessibility(node, facts, properties, semantics);
    } else if (node.type === 'VECTOR') {
      kind = 'vector';
      const source = required(node, facts, 'vector.asset');
      const asset = assets.get(valueOf(source))?.asset;
      if (asset?.mediaType === 'application/vnd.viewcompose.vector+json') {
        properties.push({name: 'source', value: {kind: 'resource', resourceId: asset.id}});
      }
      decide(node, source, {
        status: 'unsupported', severity: 'error', disposition: 'blocked',
        reasonCode: 'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
        reason: 'Inert vector commands remain inspectable but are not emitted by the Figma v1 generator.',
      });
      mapAccessibility(node, facts, properties, semantics);
    } else if (node.type === 'RECTANGLE') {
      kind = 'box';
      const fill = required(node, facts, 'fill.solid');
      properties.push({name: 'backgroundColor', value: {kind: 'color', argb: valueOf(fill)}});
      decide(node, fill, {targetPath: 'properties.backgroundColor', reasonCode: 'VC-AI-FIGMA-STYLE-MAPPED'});
    } else {
      fail(
        'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
        `Node ${node.id} has unsupported Figma type ${node.type}.`,
        'Use FRAME/GROUP/COMPONENT/COMPONENT_SET/INSTANCE/TEXT/RECTANGLE/VECTOR in the v1 subset.',
        'unsupported',
      );
    }
    for (const fact of node.facts) {
      const key = `${node.id}\0${fact.path}`;
      if (decisionKeys.has(key)) continue;
      decide(node, fact, {
        status: 'unsupported',
        severity: 'error',
        disposition: 'blocked',
        reasonCode: 'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
        reason: `Render fact ${fact.path} is outside the frozen mapping subset.`,
      });
    }
    const variantProperties = node.variantProperties.map((fact) => {
      decide(node, fact, {
        targetPath: `provenance.variantProperties.${fact.path}`,
        status: 'preserved-only',
        severity: 'info',
        disposition: 'preserved',
        reasonCode: 'VC-AI-FIGMA-VARIANT-PRESERVED',
      });
      return {name: fact.path.replaceAll('.', '-'), value: provenanceValue(fact)};
    });
    const referenceFacts = [...node.facts, ...node.variantProperties]
      .filter((fact) => fact.value.kind === 'reference');
    return {
      id: node.id,
      kind,
      layout,
      properties,
      semantics,
      children: node.childIds.map((id) => mapNode(graph.nodeById.get(id))),
      provenance: {
        sourceNodeId: node.id,
        sourcePath: `nodes/${node.id}`,
        componentLineage: [...node.componentLineage],
        variantProperties,
        tokenReferences: referenceFacts.filter((fact) => fact.value.referenceType === 'token')
          .map((fact) => fact.value.id).sort(),
        styleReferences: referenceFacts.filter((fact) => fact.value.referenceType === 'style')
          .map((fact) => fact.value.id).sort(),
        confidence: 1,
        decision: node.type === 'INSTANCE'
          ? 'Flattened one resolved instance snapshot while preserving component and variant lineage.'
          : 'Mapped only declared offline Figma facts without inferred behavior.',
      },
    };
  };

  const ir = {
    schemaVersion: 2,
    kind: 'design-ir',
    documentId: stableDocumentId(exported.document.identity),
    source: {
      kind: 'figma',
      documentIdentity: exported.document.identity,
      revision: exported.document.revision,
      exportVersion: exported.exportVersion,
      fingerprint: inputFingerprint,
    },
    importFingerprint: fingerprint({inputFingerprint, mappingVersion: 1, schemaVersion: 2}),
    selections: [...exported.document.selectedNodeIds],
    exportSettings: {
      dpPerUnit: exported.units.dpPerUnit,
      spPerUnit: exported.units.spPerUnit,
      colorSpace: exported.settings.colorSpace,
      coordinateSpace: exported.settings.coordinateSpace,
    },
    privacy: {
      classification: exported.privacy.classification,
      textIncluded: exported.privacy.textIncluded,
      labelsIncluded: exported.privacy.labelsIncluded,
      pluginDataExcluded: exported.completeness.pluginDataExcluded,
      externalReferencesExcluded: exported.completeness.externalReferencesExcluded,
      redactionDeclared: exported.privacy.redactionDeclared,
    },
    catalogs: {
      components: [...exported.catalogs.components]
        .sort((left, right) => left.id.localeCompare(right.id)),
      tokens: exported.catalogs.tokens
        .map((token) => ({
          id: token.id,
          collectionId: token.collectionId,
          nameDigest: token.nameDigest,
          resolvedType: token.resolvedType === 'FLOAT'
            ? 'number'
            : token.resolvedType.toLowerCase(),
          value: tokenValueToIr(token, graph.tokenById),
          aliases: [...token.aliases],
        }))
        .sort((left, right) => left.id.localeCompare(right.id)),
      styles: exported.catalogs.styles
        .map((style) => ({...style, kind: style.kind.toLowerCase()}))
        .sort((left, right) => left.id.localeCompare(right.id)),
      fonts: exported.catalogs.fonts
        .map((font) => ({...font, renderable: font.genericFamily !== 'custom'}))
        .sort((left, right) => left.id.localeCompare(right.id)),
    },
    resources: [...assets.values()].map(({asset}) => ({
      id: asset.id,
      kind: asset.mediaType === 'application/vnd.viewcompose.vector+json' ? 'vector' : 'raster',
      mediaType: asset.mediaType,
      bytes: asset.bytes,
      sha256: asset.sha256,
      intrinsicWidth: asset.intrinsicWidth,
      intrinsicHeight: asset.intrinsicHeight,
      sourceNodeId: asset.sourceNodeId,
      ownership: asset.ownership,
      redistribution: asset.redistribution,
      ...(asset.licenseId ? {licenseId: asset.licenseId} : {}),
    })).sort((left, right) => left.id.localeCompare(right.id)),
    roots: exported.document.selectedNodeIds.map((id) => mapNode(graph.nodeById.get(id))),
    mappingLedger: decisions.sort((left, right) =>
      left.nodeId.localeCompare(right.nodeId) || left.sourcePath.localeCompare(right.sourcePath)),
    unsupported: unsupported.sort((left, right) =>
      left.nodeId.localeCompare(right.nodeId) || left.sourcePath.localeCompare(right.sourcePath)),
  };
  if (ir.roots.length !== 1) {
    ir.unsupported.push({
      nodeId: exported.document.selectedNodeIds[0],
      sourcePath: 'document.selectedNodeIds',
      code: 'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
      reason: 'Figma v1 generation requires exactly one selected root.',
      sourceValueFingerprint: fingerprint(exported.document.selectedNodeIds),
      severity: 'error',
      disposition: 'blocked',
    });
  }
  const violations = validateSchemaValue(ir, DESIGN_IR_V2_SCHEMA);
  if (violations.length > 0) {
    fail(
      'VC-AI-FIGMA-CONTRACT-INVALID',
      `Mapped Design IR v2 violates its frozen schema: ${violations.slice(0, 3).join('; ')}`,
      'Report the deterministic adapter defect without using the partial result.',
      'failed',
    );
  }
  const summary = {
    selectedRoots: ir.roots.length,
    nodes: graph.nodeById.size,
    facts: graph.facts,
    mapped: decisions.filter((item) => item.status === 'mapped').length,
    flattened: decisions.filter((item) => item.status === 'flattened').length,
    preservedOnly: decisions.filter((item) => item.status === 'preserved-only').length,
    unsupported: ir.unsupported.length,
    blocking: ir.unsupported.filter((item) => item.severity === 'error').length,
    generationAllowed: ir.unsupported.every((item) => item.severity !== 'error'),
  };
  return {ir, summary};
}

function inputIdentity(exported) {
  const identity = structuredClone(exported);
  identity.assets = identity.assets.map((asset) => {
    const {data: _data, ...rest} = asset;
    return rest;
  });
  return fingerprint(identity);
}

async function failureResult({requestId, error, elapsedMs}) {
  const known = error instanceof FigmaImportError || error instanceof FigmaRenderPlanError;
  return toolResult({
    requestId,
    tool: TOOL_NAME,
    status: known ? error.status : 'failed',
    level: 'static',
    diagnostics: [diagnostic({
      code: known ? error.code : 'VC-AI-FIGMA-CONTRACT-INVALID',
      severity: 'error',
      message: known
        ? error.message
        : 'Offline Figma import failed before an immutable result was accepted.',
      nextAction: known
        ? error.nextAction
        : 'Use the frozen self-contained Figma export contract and report deterministic adapter failures.',
    })],
    elapsedMs,
    truncated: known && error.status === 'limited',
  });
}

function verificationCategory({eligible, evaluated = eligible, passed = evaluated, failed = 0, notApplicable = 0}) {
  let conclusion = 'incomplete';
  if (eligible === 0 && notApplicable > 0) conclusion = 'not-applicable';
  else if (failed > 0) conclusion = 'failed';
  else if (eligible === evaluated && evaluated === passed) conclusion = 'passed';
  return {eligible, evaluated, passed, failed, notApplicable, conclusion};
}

function mappingCount(ir, phase) {
  return ir.mappingLedger.filter((item) => item.phase === phase).length;
}

function comparisonCategory(comparison, categories) {
  const checks = comparison.nodes.flatMap((node) => node.checks)
    .filter((item) => categories.includes(item.category));
  const failed = checks.filter((item) => item.status === 'failed').length;
  const passed = checks.filter((item) => item.status === 'passed').length;
  const notApplicable = checks.filter((item) => item.status === 'not-applicable').length;
  return verificationCategory({
    eligible: checks.length,
    evaluated: passed + failed,
    passed,
    failed,
    notApplicable,
  });
}

function generatedResultBase({mode, inputFingerprint, irFingerprint, profile, summary}) {
  return {
    schemaVersion: 1,
    kind: 'figma-import-result',
    mode,
    inputFingerprint,
    irFingerprint,
    profile,
    auditSummary: summary,
  };
}

function assertGeneratedArtifacts(artifacts) {
  const total = artifacts.virtualFiles.reduce((bytes, file) => bytes + file.bytes, 0);
  if (
    artifacts.virtualFiles.length > FIGMA_IMPORT_LIMITS.maxVirtualFiles ||
    artifacts.virtualFiles.some((file) => file.bytes > FIGMA_IMPORT_LIMITS.maxVirtualFileBytes) ||
    total > FIGMA_IMPORT_LIMITS.maxVirtualFileBytesTotal
  ) {
    fail(
      'VC-AI-FIGMA-LIMIT-EXCEEDED',
      'Generated Figma virtual files exceed the bounded file or aggregate byte ceiling.',
      'Reduce the selected design or embedded assets.',
      'limited',
    );
  }
}

export async function importFigmaExport(arguments_, {
  requestId,
  signal,
  render = renderGeneratedPreview,
  compare = compareGeneratedLayout,
} = {}) {
  const started = performance.now();
  try {
    throwIfCancelled(signal);
    if (utf8Bytes(JSON.stringify(arguments_)) > FIGMA_IMPORT_LIMITS.maxArgumentsBytes) {
      fail(
        'VC-AI-FIGMA-LIMIT-EXCEEDED',
        'Normalized Figma arguments exceed the 3 MiB public envelope.',
        'Reduce selected nodes or embedded assets.',
        'limited',
      );
    }
    const requestViolations = validateSchemaValue(arguments_, FIGMA_IMPORT_REQUEST_SCHEMA);
    if (requestViolations.length > 0) {
      fail(
        'VC-AI-FIGMA-CONTRACT-INVALID',
        `Figma import arguments violate v1: ${requestViolations.slice(0, 3).join('; ')}`,
        'Use one inspect, generate, or verify request with raw exportJson.',
      );
    }
    let exported;
    try {
      exported = parseStrictJson(arguments_.exportJson, {maxDepth: FIGMA_IMPORT_LIMITS.maxJsonDepth});
    } catch (error) {
      const reason = error instanceof StrictJsonError && error.code === 'DUPLICATE_KEY'
        ? 'duplicate object keys'
        : error instanceof StrictJsonError && error.code === 'DEPTH'
          ? 'excessive nesting'
          : 'invalid JSON syntax';
      fail(
        error instanceof StrictJsonError && error.code === 'DEPTH'
          ? 'VC-AI-FIGMA-LIMIT-EXCEEDED'
          : 'VC-AI-FIGMA-CONTRACT-INVALID',
        `Figma export contains ${reason}.`,
        'Re-export one strict bounded JSON document without duplicate keys.',
        error instanceof StrictJsonError && error.code === 'DEPTH' ? 'limited' : 'invalid',
      );
    }
    const exportViolations = validateSchemaValue(exported, FIGMA_EXPORT_SCHEMA);
    if (exportViolations.length > 0) {
      fail(
        'VC-AI-FIGMA-CONTRACT-INVALID',
        `Figma export violates v1: ${exportViolations.slice(0, 3).join('; ')}`,
        'Use the complete self-contained viewcompose-figma-export/1 envelope.',
      );
    }
    assertFiniteAndBoundedStrings(exported);
    validateSecurity(exported);
    const assets = validateAssets(exported);
    const graph = indexGraph(exported, assets);
    throwIfCancelled(signal);
    const inputFingerprint = inputIdentity(exported);
    const {ir, summary} = auditAndMap(exported, graph, assets, inputFingerprint);
    const irFingerprint = fingerprint(ir);
    const manifest = await loadKnowledgeManifest();
    const profile = {
      versionLane: manifest.framework.versionLane,
      identity: manifest.framework.identity,
      match: 'exact',
    };
    if (arguments_.mode !== 'inspect' && !summary.generationAllowed) {
      fail(
        'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
        'Figma generation is blocked by error-level unsupported mapping decisions.',
        'Inspect the mapping ledger and reduce or normalize unsupported source facts.',
        'unsupported',
      );
    }
    if (arguments_.mode !== 'inspect') {
      const plan = createFigmaRenderPlan({designIr: ir, assets});
      const artifacts = generateFigmaArtifacts(plan);
      assertGeneratedArtifacts(artifacts);
      if (arguments_.mode === 'generate') {
        const data = {
          ...generatedResultBase({
            mode: 'generate', inputFingerprint, irFingerprint, profile, summary,
          }),
          virtualFiles: artifacts.virtualFiles,
        };
        const violations = validateSchemaValue(data, FIGMA_IMPORT_SCHEMA);
        if (
          violations.length > 0 ||
          utf8Bytes(JSON.stringify(data)) > FIGMA_IMPORT_LIMITS.maxResultBytes
        ) {
          fail(
            violations.length > 0
              ? 'VC-AI-FIGMA-GENERATION-FAILED'
              : 'VC-AI-FIGMA-LIMIT-EXCEEDED',
            violations.length > 0
              ? `Figma generate result violates v1: ${violations.slice(0, 3).join('; ')}`
              : 'Figma generate result exceeds the 3 MiB public envelope.',
            'Reduce the selection or repair the deterministic generator.',
            violations.length > 0 ? 'failed' : 'limited',
          );
        }
        return toolResult({
          requestId,
          tool: TOOL_NAME,
          status: 'success',
          level: 'static',
          diagnostics: [],
          data,
          elapsedMs: performance.now() - started,
          outputFingerprint: artifacts.artifactSetFingerprint,
        });
      }
      throwIfCancelled(signal);
      const requested = arguments_.verification;
      const preview = await render({
        generatedKotlin: artifacts.kotlin,
        generationReport: artifacts.report,
        previewBindings: artifacts.previewBindings,
        previewConfiguration: {
          widthDp: requested.widthDp,
          heightDp: requested.heightDp,
          density: requested.density,
          fontScale: requested.fontScale,
          localeTag: 'en-US',
          layoutDirection: requested.layoutDirection,
          theme: requested.theme,
          apiLevel: null,
        },
        requestId,
        signal,
      });
      if (preview.status !== 'success') {
        return toolResult({
          requestId,
          tool: TOOL_NAME,
          status: preview.status,
          level: preview.evidence?.level ?? 'static',
          diagnostics: preview.diagnostics,
          elapsedMs: performance.now() - started,
          cache: preview.evidence?.cache,
          compilerLane: preview.evidence?.compilerLane,
          renderLane: preview.evidence?.renderLane,
          outputFingerprint: preview.evidence?.outputFingerprint,
          truncated: preview.truncated,
        });
      }
      const compared = await compare({
        designIr: artifacts.comparisonDesignIr,
        previewBindings: artifacts.previewBindings,
        preview: preview.data,
        previewEvidence: preview.evidence,
      });
      if (!compared.comparison) {
        return toolResult({
          requestId,
          tool: TOOL_NAME,
          status: compared.status,
          level: compared.evidenceLevel ?? 'rendered',
          diagnostics: compared.diagnostics,
          elapsedMs: performance.now() - started,
          cache: preview.evidence.cache,
          compilerLane: preview.evidence.compilerLane,
          renderLane: preview.evidence.renderLane,
          outputFingerprint: preview.evidence.outputFingerprint,
        });
      }
      const style = mappingCount(ir, 'style');
      const resource = mappingCount(ir, 'resource');
      const verification = {
        compilation: {
          status: 'passed',
          fingerprint: fingerprint({
            lane: preview.evidence.compilerLane,
            kotlin: artifacts.report.kotlinFingerprint,
            request: preview.data.generatedPreview.requestFingerprint,
          }),
        },
        preview: {status: 'passed', fingerprint: preview.evidence.outputFingerprint},
        categories: {
          structure: comparisonCategory(compared.comparison, ['identity', 'structure']),
          semantics: comparisonCategory(compared.comparison, ['semantic']),
          geometry: comparisonCategory(compared.comparison, ['geometry']),
          style: verificationCategory({eligible: style, evaluated: 0, passed: 0}),
          assets: verificationCategory({eligible: resource}),
          pixels: verificationCategory({eligible: 0, evaluated: 0, passed: 0, notApplicable: 1}),
          perceptual: verificationCategory({
            eligible: 0, evaluated: 0, passed: 0, notApplicable: 1,
          }),
        },
        conclusion: compared.status !== 'success'
          ? 'failed'
          : style === 0 ? 'passed' : 'incomplete',
      };
      const data = {
        ...generatedResultBase({mode: 'verify', inputFingerprint, irFingerprint, profile, summary}),
        artifactSetFingerprint: artifacts.artifactSetFingerprint,
        verification,
      };
      const violations = validateSchemaValue(data, FIGMA_IMPORT_SCHEMA);
      if (
        violations.length > 0 ||
        utf8Bytes(JSON.stringify(data)) > FIGMA_IMPORT_LIMITS.maxVerificationBytes
      ) {
        fail(
          violations.length > 0
            ? 'VC-AI-FIGMA-VERIFICATION-FAILED'
            : 'VC-AI-FIGMA-LIMIT-EXCEEDED',
          violations.length > 0
            ? `Figma verify result violates v1: ${violations.slice(0, 3).join('; ')}`
            : 'Figma verification evidence exceeds the 512 KiB ceiling.',
          'Reject the partial verification and repair the deterministic evidence adapter.',
          violations.length > 0 ? 'failed' : 'limited',
        );
      }
      return toolResult({
        requestId,
        tool: TOOL_NAME,
        status: compared.status,
        level: compared.evidenceLevel,
        diagnostics: compared.diagnostics.slice(0, FIGMA_IMPORT_LIMITS.maxDiagnostics),
        data,
        elapsedMs: performance.now() - started,
        cache: preview.evidence.cache,
        compilerLane: preview.evidence.compilerLane,
        renderLane: preview.evidence.renderLane,
        outputFingerprint: compared.comparison.comparisonFingerprint,
      });
    }
    const data = {
      schemaVersion: 1,
      kind: 'figma-import-result',
      mode: 'inspect',
      inputFingerprint,
      irFingerprint,
      profile,
      auditSummary: summary,
      designIr: ir,
      audit: {
        factCoverage: {declared: graph.facts, decided: ir.mappingLedger.length, percent: 100},
        assetCoverage: {declared: assets.size, decided: graph.referencedAssets.size, percent: 100},
        privacy: exported.privacy.redactionDeclared ? 'redacted' : 'declared',
        limits: 'within-bounds',
      },
    };
    const resultViolations = validateSchemaValue(data, FIGMA_IMPORT_SCHEMA);
    if (resultViolations.length > 0 || utf8Bytes(JSON.stringify(data)) > FIGMA_IMPORT_LIMITS.maxResultBytes) {
      fail(
        resultViolations.length > 0
          ? 'VC-AI-FIGMA-CONTRACT-INVALID'
          : 'VC-AI-FIGMA-LIMIT-EXCEEDED',
        resultViolations.length > 0
          ? `Figma inspect result violates v1: ${resultViolations.slice(0, 3).join('; ')}`
          : 'Figma inspect result exceeds the 3 MiB public envelope.',
        'Reduce the selected subtree and retain the compact immutable audit.',
        resultViolations.length > 0 ? 'failed' : 'limited',
      );
    }
    const blockingDiagnostics = ir.unsupported.filter((item) => item.severity === 'error')
      .slice(0, FIGMA_IMPORT_LIMITS.maxDiagnostics)
      .map((item) => diagnostic({
        code: item.code,
        severity: 'error',
        message: `${item.sourcePath}: ${item.reason}`,
        nextAction: 'Inspect the mapping ledger; generation remains blocked until resolved.',
      }));
    return toolResult({
      requestId,
      tool: TOOL_NAME,
      status: summary.blocking > 0 ? 'unsupported' : 'success',
      level: 'static',
      diagnostics: blockingDiagnostics,
      data,
      elapsedMs: performance.now() - started,
      outputFingerprint: irFingerprint,
    });
  } catch (error) {
    return failureResult({requestId, error, elapsedMs: performance.now() - started});
  }
}
