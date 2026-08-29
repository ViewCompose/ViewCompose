import {createHash} from 'node:crypto';
import {lstat, readFile, realpath} from 'node:fs/promises';
import {basename, isAbsolute, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {assertSchemaValue} from './schema-validator.mjs';
import {parseBoundedAndroidLayoutXml} from './xml-to-design-ir.mjs';

export const XML_LAYOUT_DEPENDENCY_LIMITS = Object.freeze({
  maxLayoutFiles: 64,
  maxIncludeDepth: 16,
  maxIncludeEdges: 256,
  maxExpandedBytes: 1024 * 1024,
});

const schemaPath = fileURLToPath(
  new URL('../contracts/xml-layout-dependencies.schema.json', import.meta.url),
);
let schemaPromise;

function loadSchema() {
  schemaPromise ??= readFile(schemaPath, 'utf8').then(JSON.parse);
  return schemaPromise;
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function projectPath(value) {
  if (
    typeof value !== 'string' ||
    value.length === 0 ||
    value.length > 4096 ||
    value.includes('\0') ||
    isAbsolute(value) ||
    /^[A-Za-z]:[\\/]/u.test(value)
  ) return null;
  const normalized = value.replaceAll('\\', '/');
  if (normalized.split('/').some((segment) => !segment || segment === '.' || segment === '..')) {
    return null;
  }
  return normalized;
}

function contained(root, candidate) {
  const path = relative(root, candidate);
  return path === '' || (!path.startsWith(`..${sep}`) && path !== '..' && !isAbsolute(path));
}

async function canonicalRoot(path) {
  if (typeof path !== 'string' || !isAbsolute(path) || path.length > 4096 || path.includes('\0')) {
    return null;
  }
  const canonical = await realpath(path).catch(() => null);
  if (!canonical) return null;
  const metadata = await lstat(canonical);
  return metadata.isDirectory() && !metadata.isSymbolicLink() ? canonical : null;
}

async function safeMetadata(root, relativePath) {
  const normalized = projectPath(relativePath);
  if (!normalized) return {error: 'path'};
  const candidate = resolve(root, normalized);
  if (!contained(root, candidate)) return {error: 'path'};
  let current = root;
  for (const segment of normalized.split('/')) {
    current = resolve(current, segment);
    const metadata = await lstat(current).catch((error) => {
      if (error?.code === 'ENOENT') return null;
      throw error;
    });
    if (!metadata) return {error: 'missing'};
    if (metadata.isSymbolicLink()) return {error: 'symlink'};
  }
  const canonical = await realpath(candidate);
  if (!contained(root, canonical)) return {error: 'path'};
  return {path: candidate, normalized, metadata: await lstat(candidate)};
}

function sourcePosition(source, path, offset) {
  const before = source.slice(0, Math.max(0, offset));
  const lines = before.split('\n');
  return {
    path,
    startLine: lines.length,
    startColumn: lines.at(-1).length + 1,
  };
}

function failure(status, code, message, path = 'project', source = '', offset = 0) {
  const position = sourcePosition(source, path, offset);
  return {
    status,
    diagnostics: [{
      code,
      severity: 'error',
      message,
      nextAction: 'Resolve the explicit layout dependency or narrow the declared project roots.',
      source: {
        ...position,
        endLine: position.startLine,
        endColumn: position.startColumn + 1,
      },
    }],
  };
}

function normalizedLimits(requested = {}) {
  if (requested === null || typeof requested !== 'object' || Array.isArray(requested)) return null;
  const limits = {...XML_LAYOUT_DEPENDENCY_LIMITS};
  for (const [name, ceiling] of Object.entries(XML_LAYOUT_DEPENDENCY_LIMITS)) {
    if (requested[name] === undefined) continue;
    const value = requested[name];
    if (!Number.isInteger(value) || value <= 0 || value > ceiling) return null;
    limits[name] = value;
  }
  return limits;
}

function layoutReference(name) {
  return /^[A-Za-z][A-Za-z0-9_]*$/u.test(name) ? `@layout/${name}` : null;
}

function cloneNode(node, document) {
  return {
    ...node,
    attributes: node.attributes.map((attribute) => ({...attribute})),
    children: [],
    origin: {path: document.path, source: document.source},
  };
}

function withoutNamespace(node) {
  return {
    ...node,
    attributes: node.attributes.filter((attribute) => attribute.name !== 'xmlns:android'),
  };
}

export async function resolveXmlLayoutDependencies({
  projectRoot,
  layoutPath,
  resourceRoots = [],
  sourceOverrides = {},
  limits: requestedLimits,
} = {}) {
  const started = performance.now();
  const limits = normalizedLimits(requestedLimits);
  if (!limits) {
    return {
      ...failure('invalid', 'VC-AI-XML-LAYOUT-DEPENDENCY-LIMIT',
        'Layout dependency limits exceed the frozen ceilings.'),
      elapsedMs: performance.now() - started,
    };
  }
  const root = await canonicalRoot(projectRoot);
  const normalizedLayout = projectPath(layoutPath);
  const normalizedResourceRoots = resourceRoots.map(projectPath);
  if (
    !root ||
    !normalizedLayout ||
    sourceOverrides === null ||
    typeof sourceOverrides !== 'object' ||
    Array.isArray(sourceOverrides) ||
    resourceRoots.length === 0 ||
    resourceRoots.length > 16 ||
    normalizedResourceRoots.some((path) => !path) ||
    new Set(normalizedResourceRoots).size !== normalizedResourceRoots.length
  ) {
    return {
      ...failure('invalid', 'VC-AI-XML-PROJECT-CONTEXT-REQUIRED',
        'Layout dependencies require a canonical project root and ordered explicit resource roots.'),
      elapsedMs: performance.now() - started,
    };
  }

  for (const resourceRoot of normalizedResourceRoots) {
    const metadata = await safeMetadata(root, resourceRoot);
    if (metadata.error === 'symlink') {
      return {
        ...failure('invalid', 'VC-AI-XML-PROJECT-CONTEXT-REQUIRED',
          `Resource root ${resourceRoot} traverses a symbolic link.`, resourceRoot),
        elapsedMs: performance.now() - started,
      };
    }
    if (metadata.error || !metadata.metadata.isDirectory()) {
      return {
        ...failure('invalid', 'VC-AI-XML-PROJECT-CONTEXT-REQUIRED',
          `Resource root ${resourceRoot} is not a contained directory.`, resourceRoot),
        elapsedMs: performance.now() - started,
      };
    }
  }

  const layoutName = basename(normalizedLayout, '.xml');
  const rootReference = layoutReference(layoutName);
  const rootPrecedence = normalizedResourceRoots.findIndex(
    (resourceRoot) => normalizedLayout === `${resourceRoot}/layout/${layoutName}.xml`,
  );
  if (!rootReference || !normalizedLayout.endsWith('.xml') || rootPrecedence < 0) {
    return {
      ...failure('invalid', 'VC-AI-XML-PROJECT-CONTEXT-REQUIRED',
        'The root layout must be a default layout XML inside an explicit resource root.', normalizedLayout),
      elapsedMs: performance.now() - started,
    };
  }

  const documents = new Map();
  const nodes = [];
  const edges = [];
  let expandedBytes = 0;

  async function loadDocument(reference, requestedPath, sourceDocument, sourceOffset) {
    const cached = documents.get(reference);
    if (cached) return {status: 'success', document: cached};
    const name = reference.slice('@layout/'.length);
    let selected;
    let precedence = -1;
    if (requestedPath) {
      const metadata = await safeMetadata(root, requestedPath);
      if (metadata.error === 'symlink') {
        return failure('invalid', 'VC-AI-XML-PROJECT-CONTEXT-REQUIRED',
          `Layout ${requestedPath} traverses a symbolic link.`, requestedPath);
      }
      if (metadata.error || !metadata.metadata.isFile()) {
        return failure('unsupported', 'VC-AI-XML-LAYOUT-MISSING',
          `Layout ${reference} does not resolve to a regular file.`,
          sourceDocument?.path ?? requestedPath,
          sourceDocument?.source ?? '', sourceOffset);
      }
      selected = metadata;
      precedence = rootPrecedence;
    } else {
      for (const [index, resourceRoot] of normalizedResourceRoots.entries()) {
        const candidate = `${resourceRoot}/layout/${name}.xml`;
        const metadata = await safeMetadata(root, candidate);
        if (metadata.error === 'missing') continue;
        if (metadata.error === 'symlink') {
          return failure('invalid', 'VC-AI-XML-PROJECT-CONTEXT-REQUIRED',
            `Layout ${candidate} traverses a symbolic link.`,
            sourceDocument?.path ?? candidate,
            sourceDocument?.source ?? '', sourceOffset);
        }
        if (metadata.error || !metadata.metadata.isFile()) {
          return failure('unsupported', 'VC-AI-XML-LAYOUT-MISSING',
            `Layout ${reference} does not resolve to a regular file.`,
            sourceDocument?.path ?? candidate,
            sourceDocument?.source ?? '', sourceOffset);
        }
        selected = metadata;
        precedence = index;
        break;
      }
    }
    if (!selected) {
      return failure('unsupported', 'VC-AI-XML-LAYOUT-MISSING',
        `Layout ${reference} was not found in the explicit default-layout roots.`,
        sourceDocument?.path ?? normalizedLayout,
        sourceDocument?.source ?? '', sourceOffset);
    }
    if (documents.size >= limits.maxLayoutFiles || selected.metadata.size > 256 * 1024) {
      return failure('limited', 'VC-AI-XML-LAYOUT-DEPENDENCY-LIMIT',
        'Layout dependency files exceed the frozen count or per-file ceiling.', selected.normalized);
    }
    const rawSource = await readFile(selected.path, 'utf8');
    const source = Object.hasOwn(sourceOverrides, selected.normalized)
      ? sourceOverrides[selected.normalized]
      : rawSource;
    if (
      typeof source !== 'string' ||
      source.length === 0 ||
      Buffer.byteLength(source, 'utf8') > 256 * 1024
    ) {
      return failure('limited', 'VC-AI-XML-LAYOUT-DEPENDENCY-LIMIT',
        'Resolved layout source exceeds the frozen per-file ceiling.', selected.normalized);
    }
    const parsed = parseBoundedAndroidLayoutXml({source, path: selected.normalized});
    if (parsed.status !== 'success') return parsed;
    const document = {
      reference,
      path: selected.normalized,
      source,
      root: parsed.root,
      rootKind: parsed.root.name === 'merge' ? 'merge' : 'layout',
      resourceRootPrecedence: precedence,
      fingerprint: sha256(rawSource),
    };
    documents.set(reference, document);
    nodes.push({
      reference,
      path: document.path,
      fingerprint: document.fingerprint,
      rootKind: document.rootKind,
      resourceRootPrecedence: precedence,
    });
    return {status: 'success', document};
  }

  async function expandNode(node, document, stack, depth) {
    if (node.name === 'merge') {
      return failure('unsupported', 'VC-AI-XML-MERGE-ROOT-UNSUPPORTED',
        'A merge element is supported only as the root reached by an include.',
        document.path, document.source, node.start);
    }
    if (node.name === 'include') {
      if (
        node.children.length > 0 ||
        node.attributes.length !== 1 ||
        node.attributes[0].name !== 'layout'
      ) {
        const unsupported = node.attributes.find((attribute) => attribute.name !== 'layout');
        return failure('unsupported', 'VC-AI-XML-INCLUDE-ATTRIBUTE-UNSUPPORTED',
          'An include accepts only its unqualified layout attribute.',
          document.path, document.source, unsupported?.start ?? node.start);
      }
      const reference = /^@layout\/[A-Za-z][A-Za-z0-9_]*$/u.test(node.attributes[0].value)
        ? node.attributes[0].value
        : null;
      if (!reference) {
        return failure('unsupported', 'VC-AI-XML-INCLUDE-ATTRIBUTE-UNSUPPORTED',
          'The include layout value must be an unqualified @layout/name reference.',
          document.path, document.source, node.attributes[0].start);
      }
      if (edges.length >= limits.maxIncludeEdges || depth >= limits.maxIncludeDepth) {
        return failure('limited', 'VC-AI-XML-LAYOUT-DEPENDENCY-LIMIT',
          'Layout include depth or edge count exceeds the frozen ceiling.',
          document.path, document.source, node.start);
      }
      if (stack.includes(reference)) {
        return failure('unsupported', 'VC-AI-XML-INCLUDE-CYCLE',
          `Layout include cycle detected: ${[...stack, reference].join(' -> ')}.`,
          document.path, document.source, node.start);
      }
      const loaded = await loadDocument(reference, undefined, document, node.start);
      if (loaded.status !== 'success') return loaded;
      edges.push({
        from: document.reference,
        to: reference,
        source: sourcePosition(document.source, document.path, node.start),
      });
      const expanded = await expandDocument(loaded.document, [...stack, reference], depth + 1, true);
      return expanded;
    }

    const cloned = cloneNode(node, document);
    for (const child of node.children) {
      const expanded = await expandNode(child, document, stack, depth);
      if (expanded.status !== 'success') return expanded;
      cloned.children.push(...expanded.nodes);
    }
    return {status: 'success', nodes: [cloned]};
  }

  async function expandDocument(document, stack, depth, included) {
    expandedBytes += Buffer.byteLength(document.source, 'utf8');
    if (expandedBytes > limits.maxExpandedBytes) {
      return failure('limited', 'VC-AI-XML-LAYOUT-DEPENDENCY-LIMIT',
        'Expanded layout input exceeds maxExpandedBytes.', document.path, document.source);
    }
    if (document.rootKind === 'merge') {
      if (!included) {
        return failure('unsupported', 'VC-AI-XML-MERGE-ROOT-UNSUPPORTED',
          'A standalone merge root has no parent and cannot be converted safely.',
          document.path, document.source, document.root.start);
      }
      if (document.root.attributes.some((attribute) => attribute.name !== 'xmlns:android')) {
        const unsupported = document.root.attributes.find(
          (attribute) => attribute.name !== 'xmlns:android',
        );
        return failure('unsupported', 'VC-AI-XML-MERGE-ROOT-UNSUPPORTED',
          'An included merge root accepts only the Android namespace declaration.',
          document.path, document.source, unsupported?.start ?? document.root.start);
      }
      const nodes = [];
      for (const child of document.root.children) {
        const expanded = await expandNode(child, document, stack, depth);
        if (expanded.status !== 'success') return expanded;
        nodes.push(...expanded.nodes);
      }
      return {status: 'success', nodes};
    }
    const expanded = await expandNode(document.root, document, stack, depth);
    if (expanded.status !== 'success') return expanded;
    return {
      status: 'success',
      nodes: included ? [withoutNamespace(expanded.nodes[0])] : expanded.nodes,
    };
  }

  const loadedRoot = await loadDocument(rootReference, normalizedLayout);
  if (loadedRoot.status !== 'success') {
    return {...loadedRoot, elapsedMs: performance.now() - started};
  }
  const expanded = await expandDocument(loadedRoot.document, [rootReference], 0, false);
  if (expanded.status !== 'success') {
    return {...expanded, elapsedMs: performance.now() - started};
  }
  const graph = {
    schemaVersion: 1,
    graphId: `android-xml-layout-dependencies-v1:${layoutName}`,
    root: rootReference,
    nodes,
    edges,
    coverage: {
      layoutFiles: nodes.length,
      expandedIncludes: edges.length,
      selection: 'ordered-explicit-resource-roots-default-layout-only',
      completeness: 'explicit-roots-only',
      executedProjectBuildLogic: false,
      networkAccess: false,
    },
    fingerprint: sha256(JSON.stringify({root: rootReference, nodes, edges})),
  };
  assertSchemaValue(graph, await loadSchema(), 'Resolved Android XML layout dependency graph');
  return {
    status: 'success',
    diagnostics: [],
    graph,
    expandedRoot: expanded.nodes[0],
    documents: [...documents.values()],
    elapsedMs: performance.now() - started,
  };
}
