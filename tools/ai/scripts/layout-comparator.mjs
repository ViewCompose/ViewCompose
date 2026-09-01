import {createHash} from 'node:crypto';
import {lstat, readFile} from 'node:fs/promises';
import {isAbsolute, relative, resolve, sep} from 'node:path';
import {validateSchemaValue} from './schema-validator.mjs';
import {diagnostic, toolCacheRoot} from './tool-core.mjs';

const designSchemaPath = new URL('../contracts/design-ir.schema.json', import.meta.url);
const comparisonSchemaPath = new URL('../contracts/layout-comparison.schema.json', import.meta.url);
const MAX_DESIGN_NODES = 1000;
const MAX_VIRTUAL_NODES = 2000;
const MAX_NATIVE_NODES = 4000;
const MAX_DEPTH = 64;
const MAX_RENDER_TREE_BYTES = 8 * 1024 * 1024;
const MAX_CHECKS_PER_NODE = 128;
const MAX_FINDINGS = 1000;
const SHA256 = /^[a-f0-9]{64}$/u;
const STABLE_ID = /^[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}$/u;
const POLICY = Object.freeze({
  version: 1,
  nodeIdentity: 'exact-normalized-key',
  semanticHost: 'identity-or-allowlisted-single-child-wrapper',
  resourceValues: 'exact-preview-binding-source',
  dpRounding: 'nearest-integer-px',
  geometryTolerancePx: 0,
  hiddenGeometry: 'not-applicable-only-for-gone',
});
const KIND_BY_RENDER_TYPE = Object.freeze({
  Box: 'box',
  Button: 'button',
  Column: 'column',
  Image: 'image',
  Row: 'row',
  Text: 'text',
  TextField: 'text-field',
});

let schemasPromise;

function loadSchemas() {
  schemasPromise ??= Promise.all([
    readFile(designSchemaPath, 'utf8').then(JSON.parse),
    readFile(comparisonSchemaPath, 'utf8').then(JSON.parse),
  ]);
  return schemasPromise;
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function compactFingerprint(value) {
  return sha256(JSON.stringify(value));
}

function contained(parent, child) {
  const path = relative(resolve(parent), resolve(child));
  return path === '' || (!path.startsWith(`..${sep}`) && path !== '..' && !isAbsolute(path));
}

function comparisonFailure(code, message, nextAction, status = 'failed') {
  return {
    status,
    evidenceLevel: 'rendered',
    diagnostics: [diagnostic({code, severity: 'error', message, nextAction})],
  };
}

async function readRenderTree({repository, artifact}) {
  if (
    !artifact ||
    typeof artifact.path !== 'string' ||
    artifact.path.length === 0 ||
    artifact.path.length > 4096 ||
    isAbsolute(artifact.path) ||
    !Number.isInteger(artifact.bytes) ||
    artifact.bytes < 1 ||
    artifact.bytes > MAX_RENDER_TREE_BYTES ||
    !SHA256.test(artifact.sha256 ?? '')
  ) {
    throw new Error('EVIDENCE');
  }
  const path = resolve(repository, artifact.path);
  if (!contained(repository, path)) throw new Error('EVIDENCE');
  let current = resolve(repository);
  const segments = relative(repository, path).split(sep).filter(Boolean);
  for (const segment of segments) {
    current = resolve(current, segment);
    const metadata = await lstat(current);
    if (metadata.isSymbolicLink()) throw new Error('EVIDENCE');
  }
  const metadata = await lstat(path);
  if (!metadata.isFile() || metadata.size !== artifact.bytes) throw new Error('EVIDENCE');
  const bytes = await readFile(path);
  if (bytes.length !== artifact.bytes || sha256(bytes) !== artifact.sha256) {
    throw new Error('EVIDENCE');
  }
  try {
    return JSON.parse(bytes.toString('utf8'));
  } catch {
    throw new Error('TREE');
  }
}

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function validBounds(value) {
  return isObject(value) &&
    ['left', 'top', 'right', 'bottom'].every((name) =>
      Number.isInteger(value[name]) && value[name] >= 0 && value[name] <= 65536) &&
    value.right >= value.left && value.bottom >= value.top;
}

function visitVirtual(nodes, visitor, parent = null, depth = 1) {
  if (!Array.isArray(nodes) || depth > MAX_DEPTH) throw new Error('TREE');
  nodes.forEach((node, index) => {
    if (
      !isObject(node) ||
      !Object.hasOwn(KIND_BY_RENDER_TYPE, node.type) ||
      !STABLE_ID.test(node.nodeId ?? '') ||
      !(node.key === null || node.key === undefined || STABLE_ID.test(node.key)) ||
      typeof node.synthetic !== 'boolean' ||
      !Array.isArray(node.children)
    ) {
      throw new Error('TREE');
    }
    visitor(node, parent, index, depth);
    visitVirtual(node.children, visitor, node, depth + 1);
  });
}

function visitNative(nodes, visitor, parent = null, depth = 1) {
  if (!Array.isArray(nodes) || depth > MAX_DEPTH) throw new Error('TREE');
  nodes.forEach((node, index) => {
    if (
      !isObject(node) ||
      typeof node.className !== 'string' ||
      !(node.nodeId === null || node.nodeId === undefined || STABLE_ID.test(node.nodeId)) ||
      !validBounds(node.bounds) ||
      !(node.visibleBounds === null || node.visibleBounds === undefined || validBounds(node.visibleBounds)) ||
      typeof node.visibility !== 'string' ||
      !isObject(node.properties) ||
      !Array.isArray(node.children)
    ) {
      throw new Error('TREE');
    }
    visitor(node, parent, index, depth);
    visitNative(node.children, visitor, node, depth + 1);
  });
}

function indexRenderTree(renderTree) {
  if (!isObject(renderTree) || !Array.isArray(renderTree.tree) || !Array.isArray(renderTree.nativeViewTree)) {
    throw new Error('TREE');
  }
  const virtual = [];
  const virtualByKey = new Map();
  const virtualById = new Map();
  visitVirtual(renderTree.tree, (node, parent, siblingIndex, depth) => {
    virtual.push({node, parent, siblingIndex, depth});
    if (virtual.length > MAX_VIRTUAL_NODES) throw new Error('LIMIT');
    if (virtualById.has(node.nodeId)) throw new Error('TREE');
    virtualById.set(node.nodeId, node);
    if (node.key !== null && node.key !== undefined) {
      const entries = virtualByKey.get(node.key) ?? [];
      entries.push(node);
      virtualByKey.set(node.key, entries);
    }
  });
  const native = [];
  const nativeById = new Map();
  visitNative(renderTree.nativeViewTree, (node, parent, siblingIndex, depth) => {
    native.push({node, parent, siblingIndex, depth});
    if (native.length > MAX_NATIVE_NODES) throw new Error('LIMIT');
    if (node.nodeId !== null && node.nodeId !== undefined) {
      const entries = nativeById.get(node.nodeId) ?? [];
      entries.push(node);
      nativeById.set(node.nodeId, entries);
    }
  });
  return {virtual, virtualByKey, virtualById, native, nativeById};
}

function flattenDesignIr(designIr) {
  const nodes = [];
  const ids = new Set();
  function visit(node, parent, siblingIndex, path) {
    if (nodes.length >= MAX_DESIGN_NODES || path.length > MAX_DEPTH || ids.has(node.id)) {
      throw new Error(nodes.length >= MAX_DESIGN_NODES || path.length > MAX_DEPTH ? 'LIMIT' : 'DESIGN');
    }
    ids.add(node.id);
    const record = {node, parent, siblingIndex, path};
    nodes.push(record);
    node.children.forEach((child, index) => visit(child, record, index, [...path, child.id]));
  }
  designIr.roots.forEach((root, index) => visit(root, null, index, [root.id]));
  return nodes;
}

function normalizedKey(id) {
  return id.startsWith('id:') ? id.slice(3) : id;
}

function renderKind(node) {
  return KIND_BY_RENDER_TYPE[node?.type] ?? 'missing';
}

function oneNative(index, nodeId) {
  const entries = index.nativeById.get(nodeId) ?? [];
  return entries.length === 1 ? entries[0] : null;
}

function sameBounds(left, right) {
  return left && right &&
    left.left === right.left && left.top === right.top &&
    left.right === right.right && left.bottom === right.bottom;
}

function mapDesignNodes(designNodes, index) {
  return designNodes.map((design) => {
    const key = normalizedKey(design.node.id);
    const candidates = index.virtualByKey.get(key) ?? [];
    if (candidates.length !== 1) return {design, key, candidates, mapped: false};
    const identity = candidates[0];
    const expectedKind = design.node.kind;
    let semantic = identity;
    let wrapperDepth = 0;
    if (renderKind(identity) !== expectedKind) {
      if (
        expectedKind === 'text-field' &&
        renderKind(identity) === 'column' &&
        identity.children.length === 1 &&
        (identity.children[0].key === null || identity.children[0].key === undefined) &&
        renderKind(identity.children[0]) === 'text-field'
      ) {
        semantic = identity.children[0];
        wrapperDepth = 1;
      }
    }
    const identityNative = oneNative(index, identity.nodeId);
    const semanticNative = oneNative(index, semantic.nodeId);
    const wrapperBoundsMatch = wrapperDepth === 0 ||
      sameBounds(identityNative?.bounds, semanticNative?.bounds);
    return {
      design,
      key,
      candidates,
      mapped: Boolean(identityNative && semanticNative),
      identity,
      semantic,
      identityNative,
      semanticNative,
      wrapperDepth,
      wrapperBoundsMatch,
    };
  });
}

function field(fields, name) {
  return fields.find((candidate) => candidate.name === name)?.value;
}

function modifier(node, kind) {
  return node.modifiers.find((candidate) => candidate.kind === kind);
}

function argument(item, name) {
  return item?.arguments.find((candidate) => candidate.name === name)?.value;
}

function expectedVisibility(node) {
  return argument(modifier(node, 'visibility'), 'value')?.value ?? 'visible';
}

function resolveExpectedString(value, bindings) {
  if (value?.kind === 'literal' && typeof value.value === 'string') return value.value;
  if (value?.kind !== 'resource' || value.resourceType !== 'string') return '<unsupported>';
  const source = `@string/${value.name}`;
  const candidates = bindings.filter((binding) =>
    binding.kind === 'string' && binding.source === source && typeof binding.value === 'string');
  return candidates.length === 1 ? candidates[0].value : '<unresolved>';
}

function dimensions(bounds) {
  return {width: bounds.right - bounds.left, height: bounds.bottom - bounds.top};
}

function paddingPx(node, density) {
  const value = argument(modifier(node, 'padding'), 'all');
  return value?.kind === 'dimension' && value.unit === 'dp'
    ? Math.round(value.value * density)
    : 0;
}

function parentContentBounds(mapping, density) {
  if (!mapping?.semanticNative) return null;
  const inset = paddingPx(mapping.design.node, density);
  const bounds = mapping.semanticNative.bounds;
  return {
    left: bounds.left + inset,
    top: bounds.top + inset,
    right: Math.max(bounds.left + inset, bounds.right - inset),
    bottom: Math.max(bounds.top + inset, bounds.bottom - inset),
  };
}

function check(id, category, expected, actual, passed, status = passed ? 'passed' : 'failed') {
  return {id, category, status, expected: String(expected), actual: String(actual)};
}

function findingFor(designNodeId, item) {
  const code = item.id === 'identity.key'
    ? 'VC-AI-COMPARE-NODE-MISSING'
    : item.id === 'semantic.kind'
      ? 'VC-AI-COMPARE-KIND-MISMATCH'
      : item.category === 'structure'
        ? 'VC-AI-COMPARE-STRUCTURE-MISMATCH'
        : item.category === 'semantic'
          ? 'VC-AI-COMPARE-SEMANTIC-MISMATCH'
          : 'VC-AI-COMPARE-GEOMETRY-MISMATCH';
  return {
    code,
    severity: 'error',
    designNodeId,
    checkId: item.id,
    message: `${designNodeId} failed ${item.id}: expected ${item.expected}, actual ${item.actual}.`,
    nextAction: 'Repair the generated ViewCompose node and rerun the exact comparison lane.',
  };
}

function addSizeChecks({checks, mapping, mappedById, viewport, density}) {
  const size = modifier(mapping.design.node, 'size');
  if (!size || expectedVisibility(mapping.design.node) === 'gone') return;
  const actual = dimensions(mapping.semanticNative.bounds);
  for (const dimension of ['width', 'height']) {
    const value = argument(size, dimension);
    if (value?.kind === 'dimension' && value.unit === 'dp') {
      const expected = Math.round(value.value * density);
      checks.push(check(
        `geometry.${dimension}.dp`,
        'geometry',
        `${expected}px`,
        `${actual[dimension]}px`,
        actual[dimension] === expected,
      ));
    } else if (value?.kind === 'layout-dimension' && value.value === 'match-parent') {
      const parent = mapping.design.parent
        ? mappedById.get(mapping.design.parent.node.id)
        : null;
      const available = parent
        ? dimensions(parentContentBounds(parent, density))[dimension]
        : viewport[dimension === 'width' ? 'widthPx' : 'heightPx'];
      checks.push(check(
        `geometry.${dimension}.match-parent`,
        'geometry',
        `${available}px`,
        `${actual[dimension]}px`,
        actual[dimension] === available,
      ));
    }
  }
}

function addPaddingCheck({checks, mapping, mappedById, density}) {
  const padding = modifier(mapping.design.node, 'padding');
  const value = argument(padding, 'all');
  if (value?.kind !== 'dimension' || value.unit !== 'dp') return;
  const expected = Math.round(value.value * density);
  const parentBounds = mapping.semanticNative.bounds;
  const children = mapping.design.node.children
    .map((child) => mappedById.get(child.id))
    .filter((child) => child?.mapped && expectedVisibility(child.design.node) !== 'gone');
  const observations = [];
  const first = children[0];
  if (first) {
    observations.push(['left', first.semanticNative.bounds.left - parentBounds.left]);
    observations.push(['top', first.semanticNative.bounds.top - parentBounds.top]);
  }
  for (const child of children) {
    const width = argument(modifier(child.design.node, 'size'), 'width');
    if (width?.kind === 'layout-dimension' && width.value === 'match-parent') {
      observations.push(['right', parentBounds.right - child.semanticNative.bounds.right]);
      break;
    }
  }
  const passed = observations.length >= 2 && observations.every(([, actual]) => actual === expected);
  checks.push(check(
    'geometry.padding.all',
    'geometry',
    `all=${expected}px`,
    observations.map(([edge, actual]) => `${edge}=${actual}px`).join(','),
    passed,
  ));
}

function addContainmentCheck({checks, mapping, mappedById, density}) {
  if (!mapping.design.parent || expectedVisibility(mapping.design.node) === 'gone') return;
  const parent = mappedById.get(mapping.design.parent.node.id);
  const content = parentContentBounds(parent, density);
  const bounds = mapping.semanticNative.bounds;
  const passed = Boolean(content) && bounds.left >= content.left && bounds.top >= content.top &&
    bounds.right <= content.right && bounds.bottom <= content.bottom;
  checks.push(check(
    'geometry.containment',
    'geometry',
    `inside:${normalizedKey(mapping.design.parent.node.id)}`,
    passed ? `inside:${normalizedKey(mapping.design.parent.node.id)}` : 'outside',
    passed,
  ));
}

function nodeChecks(mapping, mappedById, bindings, viewport, density) {
  const {design, key, identity, semantic, semanticNative} = mapping;
  const checks = [];
  checks.push(check('identity.key', 'identity', key, identity.key, identity.key === key));

  const expectedParent = design.parent ? normalizedKey(design.parent.node.id) : '<root>';
  const actualParent = indexParentKey(identity);
  checks.push(check(
    'structure.parent',
    'structure',
    expectedParent,
    actualParent,
    actualParent === expectedParent,
  ));
  if (design.node.children.length > 0) {
    const expectedChildren = design.node.children.map((child) => normalizedKey(child.id));
    const actualChildren = identity.children
      .map((child) => child.key)
      .filter((childKey) => childKey !== null && childKey !== undefined);
    checks.push(check(
      'structure.children',
      'structure',
      expectedChildren.join(','),
      actualChildren.join(','),
      JSON.stringify(actualChildren) === JSON.stringify(expectedChildren),
    ));
  } else {
    const expectedIndex = design.siblingIndex;
    const actualIndex = identityParent(identity)?.children
      .filter((child) => child.key !== null && child.key !== undefined)
      .findIndex((child) => child === identity) ?? design.siblingIndex;
    let ordered = actualIndex === expectedIndex;
    if (
      ordered && ['column', 'row'].includes(design.parent?.node.kind) && expectedIndex > 0
    ) {
      const previousDesign = design.parent.node.children[expectedIndex - 1];
      const previous = mappedById.get(previousDesign.id);
      if (previous?.mapped && expectedVisibility(previous.design.node) !== 'gone') {
        ordered = design.parent.node.kind === 'column'
          ? previous.semanticNative.bounds.bottom <= semanticNative.bounds.top
          : previous.semanticNative.bounds.right <= semanticNative.bounds.left;
      }
    }
    checks.push(check(
      'structure.sibling-order',
      'structure',
      String(expectedIndex),
      String(actualIndex),
      ordered,
    ));
  }

  const expectedKind = design.node.kind;
  const actualKind = renderKind(semantic);
  const actualKindEvidence = mapping.wrapperBoundsMatch
    ? actualKind
    : `${actualKind}:wrapper-bounds-mismatch`;
  checks.push(check(
    'semantic.kind',
    'semantic',
    expectedKind,
    actualKindEvidence,
    expectedKind === actualKind && mapping.wrapperBoundsMatch,
  ));
  const role = field(design.node.semantics, 'role')?.value;
  if (role !== undefined) {
    checks.push(check('semantic.role', 'semantic', role, actualKind, role === actualKind));
  }
  const expectedVisible = expectedVisibility(design.node);
  const actualVisible = semanticNative.visibility.toLowerCase();
  checks.push(check(
    'semantic.visibility',
    'semantic',
    expectedVisible,
    actualVisible,
    expectedVisible === actualVisible,
  ));
  const text = field(design.node.properties, 'text');
  if (text !== undefined) {
    const expected = resolveExpectedString(text, bindings);
    const actual = semanticNative.properties.text ?? '';
    checks.push(check('semantic.text', 'semantic', expected, actual, expected === actual));
  }
  const description = field(design.node.properties, 'contentDescription');
  if (description !== undefined) {
    const expected = resolveExpectedString(description, bindings);
    const actual = semanticNative.properties.contentDescription ?? '';
    checks.push(check(
      'semantic.content-description',
      'semantic',
      expected,
      actual,
      expected === actual,
    ));
  }

  if (expectedVisible === 'gone') {
    const bounds = semanticNative.bounds;
    const visible = semanticNative.visibleBounds;
    const zeroBounds = bounds.left === 0 && bounds.top === 0 && bounds.right === 0 && bounds.bottom === 0;
    const noVisibleBounds = visible === null || visible === undefined ||
      (visible.left === 0 && visible.top === 0 && visible.right === 0 && visible.bottom === 0);
    checks.push(check(
      'geometry.hidden',
      'geometry',
      'not-applicable:GONE',
      zeroBounds && noVisibleBounds ? 'not-applicable:GONE' : 'visible-geometry',
      zeroBounds && noVisibleBounds,
      zeroBounds && noVisibleBounds ? 'not-applicable' : 'failed',
    ));
  } else {
    addSizeChecks({checks, mapping, mappedById, viewport, density});
    addPaddingCheck({checks, mapping, mappedById, density});
    addContainmentCheck({checks, mapping, mappedById, density});
  }
  if (checks.length > MAX_CHECKS_PER_NODE) throw new Error('LIMIT');
  return checks;
}

const parentByVirtualNode = new WeakMap();

function installVirtualParents(index) {
  for (const record of index.virtual) parentByVirtualNode.set(record.node, record.parent);
}

function identityParent(node) {
  return parentByVirtualNode.get(node) ?? null;
}

function indexParentKey(node) {
  const parent = identityParent(node);
  if (!parent) return '<root>';
  if (parent.key !== null && parent.key !== undefined) return parent.key;
  return indexParentKey(parent);
}

function failedMappingNode(mapping, ambiguous = false) {
  const item = check(
    'identity.key',
    'identity',
    mapping.key,
    ambiguous ? '<ambiguous>' : '<missing>',
    false,
  );
  return {
    node: {
      designNodeId: mapping.design.node.id,
      designPath: mapping.design.path,
      identityKey: mapping.key,
      identityRenderNodeId: ambiguous ? 'ambiguous' : 'missing',
      semanticRenderNodeId: ambiguous ? 'ambiguous' : 'missing',
      expectedKind: mapping.design.node.kind,
      actualKind: ambiguous ? 'ambiguous' : 'missing',
      wrapperDepth: 0,
      bounds: null,
      checks: [item],
    },
    findings: [{
      code: ambiguous ? 'VC-AI-COMPARE-NODE-AMBIGUOUS' : 'VC-AI-COMPARE-NODE-MISSING',
      severity: 'error',
      designNodeId: mapping.design.node.id,
      checkId: item.id,
      message: `${mapping.design.node.id} resolved to ${mapping.candidates.length} virtual nodes.`,
      nextAction: 'Restore one exact authored key for every Design IR node.',
    }],
  };
}

function publicDiagnostics(findings) {
  return findings.slice(0, MAX_FINDINGS).map((finding) => diagnostic({
    code: finding.code,
    severity: finding.severity,
    message: finding.message,
    nextAction: finding.nextAction,
  }));
}

export async function compareGeneratedLayout({
  designIr,
  previewBindings,
  preview,
  previewEvidence,
} = {}, {
  repository = toolCacheRoot(),
} = {}) {
  const [designSchema, comparisonSchema] = await loadSchemas();
  const designViolations = validateSchemaValue(designIr, designSchema);
  if (
    designViolations.length > 0 ||
    !Array.isArray(previewBindings) ||
    !isObject(preview) ||
    !isObject(previewEvidence) ||
    previewEvidence.level !== 'rendered' ||
    !SHA256.test(previewEvidence.outputFingerprint ?? '') ||
    !SHA256.test(preview.generatedPreview?.requestFingerprint ?? '') ||
    preview.generatedPreview?.renderTreeSha256 !== preview.renderTree?.sha256 ||
    !Number.isInteger(preview.image?.widthPx) ||
    !Number.isInteger(preview.image?.heightPx) ||
    !isObject(preview.configuration) ||
    typeof preview.configuration.density !== 'number' ||
    typeof preview.configuration.fontScale !== 'number' ||
    !Array.isArray(preview.configuration.localeTags) ||
    preview.configuration.localeTags.length !== 1 ||
    !['Ltr', 'Rtl'].includes(preview.configuration.layoutDirection)
  ) {
    return comparisonFailure(
      'VC-AI-COMPARE-INPUT-INVALID',
      `Generated layout comparison input is incomplete or invalid${designViolations.length > 0 ? `: ${designViolations[0]}` : '.'}`,
      'Use only Design IR and rendered evidence produced by the same conversion request.',
      'invalid',
    );
  }
  let renderTree;
  try {
    renderTree = await readRenderTree({repository, artifact: preview.renderTree});
  } catch (error) {
    return comparisonFailure(
      error.message === 'TREE'
        ? 'VC-AI-COMPARE-RENDER-TREE-INVALID'
        : 'VC-AI-COMPARE-RENDER-EVIDENCE-MISMATCH',
      'The accepted generated Preview render tree failed containment, integrity, or structure checks.',
      'Reject the comparison and regenerate the fixed Preview evidence.',
      error.message === 'LIMIT' ? 'limited' : 'failed',
    );
  }

  let index;
  let designNodes;
  try {
    index = indexRenderTree(renderTree);
    installVirtualParents(index);
    designNodes = flattenDesignIr(designIr);
  } catch (error) {
    return comparisonFailure(
      error.message === 'LIMIT' ? 'VC-AI-COMPARE-LIMIT' : 'VC-AI-COMPARE-RENDER-TREE-INVALID',
      'The generated layout exceeds the comparison limits or contains invalid tree identity.',
      'Reduce the generated screen or repair the fixed render-tree producer.',
      error.message === 'LIMIT' ? 'limited' : 'failed',
    );
  }

  const mappings = mapDesignNodes(designNodes, index);
  const mappedById = new Map(mappings.map((mapping) => [mapping.design.node.id, mapping]));
  const expectedKeys = new Set(mappings.map((mapping) => mapping.key));
  const findings = [];
  for (const [key, nodes] of index.virtualByKey) {
    if (!expectedKeys.has(key)) {
      findings.push({
        code: 'VC-AI-COMPARE-STRUCTURE-MISMATCH',
        severity: 'error',
        message: `Render tree contains undeclared authored key ${key} on ${nodes.length} node(s).`,
        nextAction: 'Remove the extra generated node or preserve it explicitly in Design IR.',
      });
    }
  }
  for (const record of index.virtual) {
    if (record.node.synthetic === true) {
      findings.push({
        code: 'VC-AI-COMPARE-STRUCTURE-MISMATCH',
        severity: 'error',
        message: `Render tree contains unsupported synthetic node ${record.node.nodeId}.`,
        nextAction: 'Represent the node in Design IR or add a separately frozen wrapper policy.',
      });
    }
  }

  const nodes = [];
  for (const mapping of mappings) {
    if (mapping.candidates.length !== 1 || !mapping.mapped) {
      const failed = failedMappingNode(mapping, mapping.candidates.length > 1);
      nodes.push(failed.node);
      findings.push(...failed.findings);
      continue;
    }
    const checks = nodeChecks(
      mapping,
      mappedById,
      previewBindings,
      {widthPx: preview.image.widthPx, heightPx: preview.image.heightPx},
      preview.configuration.density,
    );
    for (const item of checks) {
      if (item.status === 'failed') findings.push(findingFor(mapping.design.node.id, item));
    }
    nodes.push({
      designNodeId: mapping.design.node.id,
      designPath: mapping.design.path,
      identityKey: mapping.key,
      identityRenderNodeId: mapping.identity.nodeId,
      semanticRenderNodeId: mapping.semantic.nodeId,
      expectedKind: mapping.design.node.kind,
      actualKind: renderKind(mapping.semantic),
      wrapperDepth: mapping.wrapperDepth,
      bounds: mapping.semanticNative.bounds,
      checks,
    });
  }
  if (findings.length > MAX_FINDINGS) {
    return comparisonFailure(
      'VC-AI-COMPARE-LIMIT',
      `Generated layout comparison exceeds the ${MAX_FINDINGS}-finding limit.`,
      'Reduce the generated screen before comparison.',
      'limited',
    );
  }
  const allChecks = nodes.flatMap((node) => node.checks);
  const passedChecks = allChecks.filter((item) => item.status === 'passed').length;
  const failedChecks = allChecks.filter((item) => item.status === 'failed').length;
  const notApplicableChecks = allChecks.filter((item) => item.status === 'not-applicable').length;
  const requiredChecks = passedChecks + failedChecks;
  const mappedNodes = mappings.filter((mapping) =>
    mapping.candidates.length === 1 && mapping.mapped).length;
  const comparison = {
    schemaVersion: 1,
    status: findings.length === 0 && failedChecks === 0 && mappedNodes === designNodes.length
      ? 'passed'
      : 'failed',
    designIr: {
      documentId: designIr.documentId,
      sourceFingerprint: designIr.source.fingerprint,
      irFingerprint: compactFingerprint(designIr),
    },
    render: {
      requestFingerprint: preview.generatedPreview.requestFingerprint,
      outputFingerprint: previewEvidence.outputFingerprint,
      renderTreeFingerprint: preview.renderTree.sha256,
      viewport: {widthPx: preview.image.widthPx, heightPx: preview.image.heightPx},
      density: preview.configuration.density,
      fontScale: preview.configuration.fontScale,
      localeTag: preview.configuration.localeTags[0],
      layoutDirection: preview.configuration.layoutDirection,
    },
    policy: {...POLICY},
    summary: {
      designNodes: designNodes.length,
      mappedNodes,
      requiredChecks,
      passedChecks,
      failedChecks,
      notApplicableChecks,
    },
    nodes,
    findings,
  };
  comparison.comparisonFingerprint = compactFingerprint(comparison);
  const violations = validateSchemaValue(comparison, comparisonSchema);
  if (violations.length > 0) {
    return comparisonFailure(
      'VC-AI-COMPARE-INPUT-INVALID',
      `Generated layout comparison result violates schema v1: ${violations.slice(0, 3).join('; ')}`,
      'Repair the deterministic comparator before accepting its output.',
    );
  }
  return {
    status: comparison.status === 'passed' ? 'success' : 'failed',
    evidenceLevel: comparison.status === 'passed' ? 'compared' : 'rendered',
    diagnostics: publicDiagnostics(findings),
    comparison,
  };
}
