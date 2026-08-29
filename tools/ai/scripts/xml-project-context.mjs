import {createHash} from 'node:crypto';
import {lstat, readFile, readdir, realpath} from 'node:fs/promises';
import {basename, extname, isAbsolute, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {assertSchemaValue} from './schema-validator.mjs';
import {parseBoundedAndroidLayoutXml} from './xml-to-design-ir.mjs';

export const XML_PROJECT_CONTEXT_LIMITS = Object.freeze({
  maxFiles: 1000,
  maxBytes: 4 * 1024 * 1024,
  maxResourceRoots: 16,
  maxSourceRoots: 16,
  maxStyleDepth: 16,
  maxDefinitionsPerResource: 64,
  maxCallSites: 4096,
  timeoutMs: 10_000,
});

const contextSchemaPath = fileURLToPath(
  new URL('../contracts/xml-project-context.schema.json', import.meta.url),
);
const styleAttributeOrder = [
  'android:layout_width',
  'android:layout_height',
  'android:orientation',
  'android:padding',
  'android:text',
  'android:hint',
  'android:inputType',
];
const commonAttributes = new Set(['android:layout_width', 'android:layout_height']);
const elementAttributes = Object.freeze({
  LinearLayout: new Set(['android:orientation', 'android:padding']),
  TextView: new Set(['android:text']),
  EditText: new Set(['android:hint', 'android:inputType']),
  Button: new Set(['android:text']),
});
const sourceExtensions = new Set(['.java', '.kt']);
let contextSchemaPromise;

function loadContextSchema() {
  contextSchemaPromise ??= readFile(contextSchemaPath, 'utf8').then(JSON.parse);
  return contextSchemaPromise;
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
  if (normalized.split('/').some((segment) => !segment || segment === '.' || segment === '..')) return null;
  return normalized;
}

function contained(root, candidate) {
  const path = relative(root, candidate);
  return path === '' || (!path.startsWith(`..${sep}`) && path !== '..' && !isAbsolute(path));
}

function sourcePosition(source, path, offset) {
  const before = source.slice(0, Math.max(0, offset));
  const lines = before.split('\n');
  const startLine = lines.length;
  const startColumn = lines.at(-1).length + 1;
  return {path, startLine, startColumn, endLine: startLine, endColumn: startColumn + 1};
}

function failure(status, code, message, path = 'project', source = '', offset = 0) {
  return {
    status,
    diagnostics: [{
      code,
      severity: 'error',
      message,
      nextAction: 'Narrow the explicit project roots or resolve this unsupported Android project input.',
      source: sourcePosition(source, path, offset),
    }],
  };
}

function normalizedLimits(requested = {}) {
  if (requested === null || typeof requested !== 'object' || Array.isArray(requested)) return null;
  const limits = {...XML_PROJECT_CONTEXT_LIMITS};
  for (const [name, ceiling] of Object.entries(XML_PROJECT_CONTEXT_LIMITS)) {
    if (requested[name] === undefined) continue;
    const value = requested[name];
    if (!Number.isInteger(value) || value <= 0 || value > ceiling) return null;
    limits[name] = value;
  }
  return limits;
}

async function canonicalRoot(path) {
  if (typeof path !== 'string' || !isAbsolute(path) || path.length > 4096 || path.includes('\0')) return null;
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
    const metadata = await lstat(current).catch(() => null);
    if (!metadata) return {error: 'missing'};
    if (metadata.isSymbolicLink()) return {error: 'symlink'};
  }
  const canonical = await realpath(candidate);
  if (!contained(root, canonical)) return {error: 'path'};
  return {path: candidate, normalized, metadata: await lstat(candidate)};
}

function decodeXmlText(value) {
  const replacements = {amp: '&', apos: "'", gt: '>', lt: '<', quot: '"'};
  let invalid = false;
  const decoded = value.replace(/&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z][A-Za-z0-9]+);/gu, (match, entity) => {
    if (Object.hasOwn(replacements, entity)) return replacements[entity];
    const number = entity.startsWith('#x')
      ? Number.parseInt(entity.slice(2), 16)
      : entity.startsWith('#') ? Number.parseInt(entity.slice(1), 10) : Number.NaN;
    if (Number.isInteger(number) && number >= 0x20 && number <= 0x10ffff) return String.fromCodePoint(number);
    invalid = true;
    return match;
  });
  if (invalid || /&(?!#x[0-9a-fA-F]+;|#[0-9]+;|amp;|apos;|gt;|lt;|quot;)/u.test(value)) return null;
  return decoded;
}

function lineNumber(source, offset) {
  return source.slice(0, offset).split('\n').length;
}

function stripLeadingDeclaration(source) {
  const declaration = /^\s*<\?xml\s+version=(['"])1\.0\1(?:\s+encoding=(['"])utf-8\2)?\s*\?>/iu.exec(source);
  return declaration ? declaration[0].length : 0;
}

function parseAttributes(source, raw, offset) {
  const attributes = new Map();
  let cursor = 0;
  while (cursor < raw.length) {
    const whitespace = /^\s+/u.exec(raw.slice(cursor));
    if (!whitespace) return null;
    cursor += whitespace[0].length;
    if (cursor === raw.length) break;
    const match = /^([A-Za-z_][A-Za-z0-9_.:-]*)\s*=\s*(['"])(.*?)\2/su.exec(raw.slice(cursor));
    if (!match || attributes.has(match[1])) return null;
    const value = decodeXmlText(match[3]);
    if (value === null || match[3].includes('<')) return null;
    attributes.set(match[1], {
      name: match[1],
      value,
      start: offset + cursor,
    });
    cursor += match[0].length;
  }
  return attributes;
}

function nextElement(source, cursor, end) {
  while (cursor < end) {
    const whitespace = /^\s+/u.exec(source.slice(cursor, end));
    if (whitespace) {
      cursor += whitespace[0].length;
      continue;
    }
    if (source.startsWith('<!--', cursor)) {
      const close = source.indexOf('-->', cursor + 4);
      if (close < 0 || close + 3 > end) return {error: cursor};
      cursor = close + 3;
      continue;
    }
    break;
  }
  return {cursor};
}

function parseStyleItems(source, start, end, path) {
  const items = [];
  let cursor = start;
  while (cursor < end) {
    const next = nextElement(source, cursor, end);
    if (next.error !== undefined) return failure('invalid', 'VC-AI-XML-RESOURCE-UNSUPPORTED',
      'Style XML contains an unclosed comment.', path, source, next.error);
    cursor = next.cursor;
    if (cursor >= end) break;
    const match = /^<item\b([^>]*)>([^<]*)<\/item\s*>/su.exec(source.slice(cursor, end));
    if (!match) return failure('unsupported', 'VC-AI-XML-STYLE-ITEM-UNSUPPORTED',
      'Only text-only style items with a name attribute are supported.', path, source, cursor);
    const attributes = parseAttributes(source, match[1], cursor + '<item'.length);
    if (!attributes || attributes.size !== 1 || !attributes.has('name')) {
      return failure('unsupported', 'VC-AI-XML-STYLE-ITEM-UNSUPPORTED',
        'Style items require exactly one name attribute.', path, source, cursor);
    }
    const value = decodeXmlText(match[2].trim());
    if (value === null || value.length === 0) {
      return failure('unsupported', 'VC-AI-XML-STYLE-ITEM-UNSUPPORTED',
        'Style item values must be non-empty text.', path, source, cursor);
    }
    items.push({
      attribute: attributes.get('name').value,
      rawValue: value,
      startLine: lineNumber(source, cursor),
    });
    cursor += match[0].length;
  }
  return {status: 'success', items};
}

function parseValuesFile(source, path, qualifiers, precedence) {
  if (/<!DOCTYPE\b|<!ENTITY\b/iu.test(source)) {
    const offset = source.search(/<!DOCTYPE\b|<!ENTITY\b/iu);
    return failure('unsupported', 'VC-AI-XML-RESOURCE-UNSUPPORTED',
      'Resource declarations cannot contain DOCTYPE or entity declarations.', path, source, offset);
  }
  const declarationEnd = stripLeadingDeclaration(source);
  const root = /<resources\s*>/gu;
  root.lastIndex = declarationEnd;
  const opening = root.exec(source);
  if (!opening || source.slice(declarationEnd, opening.index).trim()) {
    return failure('invalid', 'VC-AI-XML-RESOURCE-UNSUPPORTED',
      'A values file must contain one plain resources root.', path, source, declarationEnd);
  }
  const closing = source.lastIndexOf('</resources>');
  if (closing < opening.index + opening[0].length || source.slice(closing + '</resources>'.length).trim()) {
    return failure('invalid', 'VC-AI-XML-RESOURCE-UNSUPPORTED',
      'A values file must close its resources root.', path, source, Math.max(opening.index, closing));
  }
  const definitions = [];
  const styles = [];
  let cursor = opening.index + opening[0].length;
  while (cursor < closing) {
    const next = nextElement(source, cursor, closing);
    if (next.error !== undefined) return failure('invalid', 'VC-AI-XML-RESOURCE-UNSUPPORTED',
      'Values XML contains an unclosed comment.', path, source, next.error);
    cursor = next.cursor;
    if (cursor >= closing) break;
    const valueMatch = /^<(string|dimen)\b([^>]*)>([^<]*)<\/\1\s*>/su.exec(source.slice(cursor, closing));
    if (valueMatch) {
      const attributes = parseAttributes(source, valueMatch[2], cursor + valueMatch[1].length + 1);
      if (!attributes || attributes.size !== 1 || !attributes.has('name')) {
        return failure('unsupported', 'VC-AI-XML-RESOURCE-UNSUPPORTED',
          'String and dimension resources require exactly one name attribute.', path, source, cursor);
      }
      const name = attributes.get('name').value;
      if (!/^[A-Za-z][A-Za-z0-9_]*$/u.test(name)) {
        return failure('unsupported', 'VC-AI-XML-RESOURCE-UNSUPPORTED',
          'Resource names must be stable unqualified identifiers.', path, source, cursor);
      }
      const rawValue = valueMatch[3].trim();
      const decoded = decodeXmlText(rawValue);
      let value;
      if (valueMatch[1] === 'string') {
        if (decoded === null || /%(?:\d+\$)?[A-Za-z]/u.test(decoded)) {
          return failure('unsupported', 'VC-AI-XML-RESOURCE-UNSUPPORTED',
            'Formatted or entity-bearing strings are outside the project-context subset.', path, source, cursor);
        }
        value = {kind: 'string', value: decoded};
      } else {
        const dimension = /^(0|[1-9][0-9]*(?:\.[0-9]+)?)(dp|sp|px)$/u.exec(rawValue);
        if (!dimension || !Number.isFinite(Number(dimension[1]))) {
          return failure('unsupported', 'VC-AI-XML-RESOURCE-UNSUPPORTED',
            'Dimensions must be finite non-negative dp, sp, or px values.', path, source, cursor);
        }
        value = {kind: 'dimension', value: Number(dimension[1]), unit: dimension[2]};
      }
      definitions.push({
        type: valueMatch[1],
        name,
        path,
        startLine: lineNumber(source, cursor),
        qualifiers,
        precedence,
        value,
      });
      cursor += valueMatch[0].length;
      continue;
    }
    const selfClosingStyle = /^<style\b([^>]*)\/>/su.exec(source.slice(cursor, closing));
    const styleOpening = /^<style\b([^>]*)>/su.exec(source.slice(cursor, closing));
    const styleMatch = selfClosingStyle ?? styleOpening;
    if (!styleMatch) {
      return failure('unsupported', 'VC-AI-XML-RESOURCE-UNSUPPORTED',
        'Only string, dimen, and style declarations are supported in scanned values files.', path, source, cursor);
    }
    const attributes = parseAttributes(source, styleMatch[1], cursor + '<style'.length);
    if (!attributes || !attributes.has('name') || [...attributes.keys()].some((name) => !['name', 'parent'].includes(name))) {
      return failure('unsupported', 'VC-AI-XML-RESOURCE-UNSUPPORTED',
        'Styles require a name and at most one explicit parent.', path, source, cursor);
    }
    const name = attributes.get('name').value;
    const parent = attributes.get('parent')?.value;
    if (!/^[A-Za-z][A-Za-z0-9_]*$/u.test(name) || (parent && !/^@style\/[A-Za-z][A-Za-z0-9_]*$/u.test(parent))) {
      return failure('unsupported', 'VC-AI-XML-RESOURCE-UNSUPPORTED',
        'Style names and parents must be explicit unqualified identifiers.', path, source, cursor);
    }
    let items = [];
    let consumed = styleMatch[0].length;
    if (!selfClosingStyle) {
      const styleEnd = source.indexOf('</style>', cursor + styleOpening[0].length);
      if (styleEnd < 0 || styleEnd > closing) {
        return failure('invalid', 'VC-AI-XML-RESOURCE-UNSUPPORTED',
          'Style declaration is not closed.', path, source, cursor);
      }
      const parsedItems = parseStyleItems(
        source,
        cursor + styleOpening[0].length,
        styleEnd,
        path,
      );
      if (parsedItems.status !== 'success') return parsedItems;
      items = parsedItems.items;
      consumed = styleEnd + '</style>'.length - cursor;
    }
    styles.push({
      name,
      parent: parent?.slice('@style/'.length),
      items,
      path,
      startLine: lineNumber(source, cursor),
      qualifiers,
      precedence,
    });
    cursor += consumed;
  }
  return {status: 'success', definitions, styles};
}

function attributeValue(value, attribute) {
  const resource = /^@(string|dimen|style)\/([A-Za-z][A-Za-z0-9_]*)$/u.exec(value);
  if (resource) {
    return {kind: 'resource', reference: value, type: resource[1], name: resource[2]};
  }
  if (value.startsWith('?')) return null;
  if (attribute === 'android:layout_width' || attribute === 'android:layout_height') {
    if (value === 'match_parent') return {kind: 'layout-dimension', value: 'match-parent'};
    if (value === 'wrap_content') return {kind: 'layout-dimension', value: 'wrap-content'};
  }
  if (attribute === 'android:padding') {
    const dimension = /^(0|[1-9][0-9]*(?:\.[0-9]+)?)(dp|sp|px)$/u.exec(value);
    if (dimension) return {kind: 'dimension', value: Number(dimension[1]), unit: dimension[2]};
  }
  if (attribute === 'android:orientation') return {kind: 'enum', type: 'orientation', value};
  if (attribute === 'android:inputType') return {kind: 'enum', type: 'android-input-type', value};
  if (attribute === 'android:text' || attribute === 'android:hint') return {kind: 'string', value};
  return null;
}

function definitionKey(type, name) {
  return `${type}/${name}`;
}

function selectedDefinitions(entries, codePath) {
  const defaults = entries.filter((entry) => entry.qualifiers.length === 0)
    .sort((left, right) => left.precedence - right.precedence || left.path.localeCompare(right.path));
  if (defaults.length === 0) {
    return failure('unsupported', 'VC-AI-XML-RESOURCE-MISSING-DEFAULT',
      `Resource @${definitionKey(entries[0].type, entries[0].name)} has no default values definition.`, codePath);
  }
  if (defaults.length > 1 && defaults[0].precedence === defaults[1].precedence) {
    return failure('unsupported', 'VC-AI-XML-RESOURCE-DUPLICATE',
      `Resource @${definitionKey(entries[0].type, entries[0].name)} is duplicated at the same precedence.`, codePath);
  }
  return {status: 'success', selected: defaults[0]};
}

function resolveStyle(name, stylesByName, limits, stack = []) {
  if (stack.includes(name)) {
    return failure('unsupported', 'VC-AI-XML-STYLE-CYCLE',
      `Style parent cycle detected: ${[...stack, name].join(' -> ')}.`, stylesByName.get(name)?.[0]?.path ?? 'styles.xml');
  }
  if (stack.length >= limits.maxStyleDepth) {
    return failure('limited', 'VC-AI-XML-PROJECT-LIMIT',
      `Style ${name} exceeds maxStyleDepth.`, stylesByName.get(name)?.[0]?.path ?? 'styles.xml');
  }
  const entries = stylesByName.get(name);
  if (!entries) {
    return failure('unsupported', 'VC-AI-XML-RESOURCE-MISSING-DEFAULT',
      `Style @style/${name} has no definition.`, 'styles.xml');
  }
  const selectedResult = selectedDefinitions(entries.map((entry) => ({...entry, type: 'style'})), entries[0].path);
  if (selectedResult.status !== 'success') return selectedResult;
  const selected = selectedResult.selected;
  let chain = [];
  let inherited = new Map();
  if (selected.parent) {
    const parent = resolveStyle(selected.parent, stylesByName, limits, [...stack, name]);
    if (parent.status !== 'success') return parent;
    chain = [...parent.chain];
    inherited = new Map(parent.items.map((item) => [item.attribute, item]));
  }
  for (const item of selected.items) inherited.set(item.attribute, {...item, definedBy: name});
  return {status: 'success', chain: [...chain, name], items: [...inherited.values()]};
}

function visitLayout(node, visitor) {
  visitor(node);
  for (const child of node.children) visitLayout(child, visitor);
}

function layoutIds(root) {
  const ids = new Set();
  visitLayout(root, (node) => {
    const id = node.attributes.find((attribute) => attribute.name === 'android:id')?.value;
    const match = /^@\+?id\/([A-Za-z][A-Za-z0-9_]*)$/u.exec(id ?? '');
    if (match) ids.add(match[1]);
  });
  return ids;
}

function pascalCase(value) {
  return value.split(/[^A-Za-z0-9]+/u).filter(Boolean)
    .map((word) => `${word[0].toUpperCase()}${word.slice(1)}`).join('');
}

function lowerCamel(value) {
  const pascal = pascalCase(value);
  return pascal ? `${pascal[0].toLowerCase()}${pascal.slice(1)}` : value;
}

function callSite({kind, target, path, line, column, language, evidence, confidence, action}) {
  return {
    kind,
    target,
    path,
    startLine: line.number,
    startColumn: column,
    language,
    evidence,
    confidence,
    snippetFingerprint: sha256(line.text.trim()),
    migrationAction: action,
  };
}

function inventoryCallSites(files, layoutName, ids, limits) {
  const findings = [];
  const bindingClass = `${pascalCase(layoutName)}Binding`;
  const bindingFields = new Map([...ids].map((id) => [lowerCamel(id), id]));
  for (const file of files) {
    const language = extname(file.path) === '.kt' ? 'kotlin' : 'java';
    const localIds = new Map();
    const bindingVariables = new Set();
    const lines = file.source.split('\n').map((text, index) => ({text, number: index + 1}));
    for (const line of lines) {
      if (/^\s*import\b/u.test(line.text)) continue;
      for (const match of line.text.matchAll(/R\.layout\.([A-Za-z][A-Za-z0-9_]*)/gu)) {
        if (match[1] === layoutName) findings.push(callSite({
          kind: 'layout-reference', target: `layout/${layoutName}`, path: file.path, line,
          column: match.index + 1, language, evidence: 'exact-symbol', confidence: 'exact',
          action: 'replace-inflation',
        }));
      }
      for (const match of line.text.matchAll(/R\.id\.([A-Za-z][A-Za-z0-9_]*)/gu)) {
        if (!ids.has(match[1])) continue;
        findings.push(callSite({
          kind: 'id-reference', target: `id/${match[1]}`, path: file.path, line,
          column: match.index + 1, language, evidence: 'exact-symbol', confidence: 'exact',
          action: 'reconnect-binding',
        }));
        const prefix = line.text.slice(0, match.index);
        const variable = /(?:val|var|[A-Za-z0-9_<>?]+)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*[^\n]*$/u.exec(prefix)?.[1];
        if (variable) localIds.set(variable, match[1]);
      }
      for (const match of line.text.matchAll(/R\.(string|dimen|style)\.([A-Za-z][A-Za-z0-9_]*)/gu)) {
        findings.push(callSite({
          kind: 'resource-reference', target: `${match[1]}/${match[2]}`, path: file.path, line,
          column: match.index + 1, language, evidence: 'exact-symbol', confidence: 'exact',
          action: 'preserve-resource',
        }));
      }
      const bindingIndex = line.text.indexOf(bindingClass);
      if (bindingIndex >= 0) {
        findings.push(callSite({
          kind: 'view-binding-reference', target: `layout/${layoutName}`, path: file.path, line,
          column: bindingIndex + 1, language, evidence: 'binding-convention', confidence: 'candidate',
          action: 'reconnect-binding',
        }));
        const variable = /(?:val|var|[A-Za-z0-9_<>?]+)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=/u.exec(line.text)?.[1];
        if (variable) bindingVariables.add(variable);
      }
      for (const [variable, id] of localIds) {
        const listener = new RegExp(`\\b${variable}\\.setOnClickListener\\b`, 'u').exec(line.text);
        if (listener) findings.push(callSite({
          kind: 'listener-registration', target: `id/${id}`, path: file.path, line,
          column: listener.index + variable.length + 2, language, evidence: 'bounded-lexical',
          confidence: 'candidate', action: 'reconnect-listener',
        }));
      }
      for (const variable of bindingVariables) {
        const listenerPattern = new RegExp(`\\b${variable}\\.([A-Za-z_][A-Za-z0-9_]*)\\.setOnClickListener\\b`, 'u');
        const listener = listenerPattern.exec(line.text);
        const listenerId = listener && bindingFields.get(listener[1]);
        if (listenerId) findings.push(callSite({
          kind: 'listener-registration', target: `id/${listenerId}`, path: file.path, line,
          column: listener.index + listener[0].lastIndexOf('setOnClickListener') + 1,
          language, evidence: 'binding-convention', confidence: 'candidate', action: 'reconnect-listener',
        }));
        const mutationPattern = new RegExp(`\\b${variable}\\.([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)\\s*=`, 'u');
        const mutation = mutationPattern.exec(line.text);
        const mutationId = mutation && bindingFields.get(mutation[1]);
        if (mutationId) findings.push(callSite({
          kind: mutation[2] === 'adapter' ? 'adapter-assignment' : 'imperative-mutation',
          target: `id/${mutationId}`, path: file.path, line, column: mutation.index + 1,
          language, evidence: 'binding-convention', confidence: 'candidate',
          action: mutation[2] === 'adapter' ? 'review-adapter' : 'review-mutation',
        }));
      }
      if (findings.length > limits.maxCallSites) {
        return failure('limited', 'VC-AI-XML-CALLSITE-LIMIT',
          'Call-site inventory exceeds maxCallSites.', file.path, file.source);
      }
    }
  }
  findings.sort((left, right) =>
    `${left.path}:${String(left.startLine).padStart(8, '0')}:${String(left.startColumn).padStart(8, '0')}:${left.kind}:${left.target}`
      .localeCompare(`${right.path}:${String(right.startLine).padStart(8, '0')}:${String(right.startColumn).padStart(8, '0')}:${right.kind}:${right.target}`));
  return {status: 'success', findings};
}

function startTagEnd(source, node) {
  let quote = null;
  for (let index = node.start + 1; index < source.length; index += 1) {
    const character = source[index];
    if (quote) {
      if (character === quote) quote = null;
    } else if (character === '"' || character === "'") quote = character;
    else if (character === '>') return source[index - 1] === '/' ? index - 1 : index;
  }
  return null;
}

function serializeResolvedValue(value, resourcesByReference) {
  if (value.kind === 'resource' && value.type === 'dimen') {
    const resource = resourcesByReference.get(value.reference);
    if (!resource || resource.effectiveValue.kind !== 'dimension') return null;
    return `${resource.effectiveValue.value}${resource.effectiveValue.unit}`;
  }
  if (value.kind === 'resource') return value.reference;
  if (value.kind === 'dimension') return `${value.value}${value.unit}`;
  if (value.kind === 'layout-dimension') return value.value.replace('-', '_');
  return value.value;
}

function resolveLayoutStyles(source, root, effectiveStyles, resourcesByReference) {
  const edits = [];
  const stylesByReference = new Map(effectiveStyles.map((style) => [style.reference, style]));
  visitLayout(root, (node) => {
    const inline = new Map(node.attributes.map((attribute) => [attribute.name, attribute]));
    const styleAttribute = inline.get('style');
    const effectiveStyle = styleAttribute ? stylesByReference.get(styleAttribute.value) : null;
    if (styleAttribute) edits.push({start: styleAttribute.start, end: styleAttribute.start + styleAttribute.raw.length, value: ' '.repeat(styleAttribute.raw.length)});
    const additions = [];
    for (const item of effectiveStyle?.items ?? []) {
      if (inline.has(item.attribute)) continue;
      const serialized = serializeResolvedValue(item.value, resourcesByReference);
      if (serialized !== null) additions.push(` ${item.attribute}="${serialized}"`);
    }
    for (const attribute of node.attributes) {
      const value = attributeValue(attribute.value, attribute.name);
      if (value?.kind !== 'resource' || value.type !== 'dimen') continue;
      const serialized = serializeResolvedValue(value, resourcesByReference);
      if (serialized !== null) edits.push({start: attribute.start, end: attribute.start + attribute.raw.length, value: `${attribute.name}="${serialized}"`});
    }
    if (additions.length > 0) {
      const end = startTagEnd(source, node);
      if (end !== null) edits.push({start: end, end, value: additions.join('')});
    }
  });
  let resolvedSource = source;
  for (const edit of edits.sort((left, right) => right.start - left.start || right.end - left.end)) {
    resolvedSource = `${resolvedSource.slice(0, edit.start)}${edit.value}${resolvedSource.slice(edit.end)}`;
  }
  return resolvedSource;
}

async function collectFiles(root, roots, limits, started) {
  const collected = [];
  for (const declaredRoot of roots) {
    const metadata = await safeMetadata(root, declaredRoot);
    if (metadata.error) return {status: metadata.error, path: declaredRoot};
    if (!metadata.metadata.isDirectory()) return {status: 'path', path: declaredRoot};
    const queue = [metadata];
    while (queue.length > 0) {
      if (performance.now() - started > limits.timeoutMs) return {status: 'timeout', path: declaredRoot};
      const current = queue.shift();
      const children = await readdir(current.path);
      children.sort();
      for (const child of children) {
        const path = `${current.normalized}/${child}`;
        const childMetadata = await safeMetadata(root, path);
        if (childMetadata.error) return {status: childMetadata.error, path};
        if (childMetadata.metadata.isDirectory()) queue.push(childMetadata);
        else if (childMetadata.metadata.isFile()) collected.push(childMetadata);
      }
    }
  }
  const unique = new Map(collected.map((entry) => [entry.normalized, entry]));
  return {status: 'success', files: [...unique.values()].sort((left, right) => left.normalized.localeCompare(right.normalized))};
}

export async function resolveXmlProjectContext({
  projectRoot,
  layoutPath,
  layoutPaths,
  resourceRoots = [],
  sourceRoots = [],
  limits: requestedLimits,
} = {}) {
  const started = performance.now();
  const limits = normalizedLimits(requestedLimits);
  if (!limits) return {...failure('invalid', 'VC-AI-XML-PROJECT-LIMIT', 'Project-context limits exceed the frozen ceilings.'), elapsedMs: performance.now() - started};
  const root = await canonicalRoot(projectRoot);
  const normalizedLayout = projectPath(layoutPath);
  const normalizedLayouts = layoutPaths === undefined
    ? [normalizedLayout]
    : Array.isArray(layoutPaths) ? layoutPaths.map(projectPath) : [];
  const normalizedResourceRoots = resourceRoots.map(projectPath);
  const normalizedSourceRoots = sourceRoots.map(projectPath);
  if (
    !root || !normalizedLayout ||
    normalizedLayouts.length === 0 || normalizedLayouts.length > 64 ||
    normalizedLayouts.some((path) => !path) ||
    !normalizedLayouts.includes(normalizedLayout) ||
    new Set(normalizedLayouts).size !== normalizedLayouts.length ||
    resourceRoots.length === 0 || resourceRoots.length > limits.maxResourceRoots ||
    sourceRoots.length > limits.maxSourceRoots ||
    normalizedResourceRoots.some((path) => !path) || normalizedSourceRoots.some((path) => !path)
  ) {
    return {...failure('invalid', 'VC-AI-XML-PROJECT-PATH-INVALID', 'Project context requires canonical contained layout, resource, and source roots.'), elapsedMs: performance.now() - started};
  }
  const layoutMetadata = await safeMetadata(root, normalizedLayout);
  if (layoutMetadata.error === 'symlink') return {...failure('invalid', 'VC-AI-XML-PROJECT-SYMLINK-DENIED', `Layout ${normalizedLayout} traverses a symbolic link.`, normalizedLayout), elapsedMs: performance.now() - started};
  if (layoutMetadata.error || !layoutMetadata.metadata.isFile()) return {...failure('invalid', 'VC-AI-XML-PROJECT-PATH-INVALID', `Layout ${normalizedLayout} is not a contained regular file.`, normalizedLayout), elapsedMs: performance.now() - started};
  if (!normalizedResourceRoots.some((path) => normalizedLayout.startsWith(`${path}/layout/`))) {
    return {...failure('invalid', 'VC-AI-XML-PROJECT-PATH-INVALID', 'The layout must be inside an explicit resource-root layout directory.', normalizedLayout), elapsedMs: performance.now() - started};
  }
  if (normalizedLayouts.some((path) =>
    !normalizedResourceRoots.some((resourceRoot) =>
      /^layout\/[^/]+\.xml$/u.test(path.slice(resourceRoot.length + 1)) &&
      path.startsWith(`${resourceRoot}/`)))) {
    return {...failure('invalid', 'VC-AI-XML-PROJECT-PATH-INVALID', 'Every layout dependency must be a default layout XML inside an explicit resource root.', normalizedLayout), elapsedMs: performance.now() - started};
  }

  const resourceFiles = await collectFiles(root, normalizedResourceRoots, limits, started);
  const sourceFiles = await collectFiles(root, normalizedSourceRoots, limits, started);
  for (const result of [resourceFiles, sourceFiles]) {
    if (result.status === 'success') continue;
    const code = result.status === 'symlink' ? 'VC-AI-XML-PROJECT-SYMLINK-DENIED' : 'VC-AI-XML-PROJECT-PATH-INVALID';
    const status = result.status === 'timeout' ? 'limited' : 'invalid';
    return {...failure(status, result.status === 'timeout' ? 'VC-AI-XML-PROJECT-LIMIT' : code, `Project traversal rejected ${result.path}.`, result.path), elapsedMs: performance.now() - started};
  }
  const valueFiles = resourceFiles.files.filter((file) => {
    const resourceRelative = normalizedResourceRoots.find((path) => file.normalized.startsWith(`${path}/`));
    if (!resourceRelative || extname(file.normalized) !== '.xml') return false;
    const directory = file.normalized.slice(resourceRelative.length + 1).split('/')[0];
    return directory === 'values' || directory.startsWith('values-');
  });
  const readableSources = sourceFiles.files.filter((file) => sourceExtensions.has(extname(file.normalized)));
  const layoutFiles = [];
  for (const path of normalizedLayouts) {
    const metadata = path === normalizedLayout ? layoutMetadata : await safeMetadata(root, path);
    if (metadata.error === 'symlink') return {...failure('invalid', 'VC-AI-XML-PROJECT-SYMLINK-DENIED', `Layout ${path} traverses a symbolic link.`, path), elapsedMs: performance.now() - started};
    if (metadata.error || !metadata.metadata.isFile()) return {...failure('invalid', 'VC-AI-XML-PROJECT-PATH-INVALID', `Layout ${path} is not a contained regular file.`, path), elapsedMs: performance.now() - started};
    layoutFiles.push(metadata);
  }
  const allFiles = new Map(layoutFiles.map((metadata) => [metadata.normalized, metadata]));
  for (const file of [...valueFiles, ...readableSources]) allFiles.set(file.normalized, file);
  if (allFiles.size > limits.maxFiles) return {...failure('limited', 'VC-AI-XML-PROJECT-LIMIT', 'Project context exceeds maxFiles.'), elapsedMs: performance.now() - started};

  let totalBytes = 0;
  const contentByPath = new Map();
  for (const [path, metadata] of [...allFiles].sort(([left], [right]) => left.localeCompare(right))) {
    if (metadata.metadata.size > 256 * 1024 || totalBytes + metadata.metadata.size > limits.maxBytes) {
      return {...failure('limited', 'VC-AI-XML-PROJECT-LIMIT', 'Project context exceeds its per-file or total byte limit.', path), elapsedMs: performance.now() - started};
    }
    const content = await readFile(metadata.path);
    contentByPath.set(path, content);
    totalBytes += content.byteLength;
  }
  const layoutSource = contentByPath.get(normalizedLayout).toString('utf8');
  const parsedLayouts = [];
  for (const path of normalizedLayouts) {
    const source = contentByPath.get(path).toString('utf8');
    const parsed = parseBoundedAndroidLayoutXml({source, path});
    if (parsed.status !== 'success') return {...parsed, elapsedMs: performance.now() - started};
    parsedLayouts.push({path, source, root: parsed.root});
    for (const node of (() => { const nodes = []; visitLayout(parsed.root, (item) => nodes.push(item)); return nodes; })()) {
      for (const attribute of node.attributes) {
        if (attribute.value.startsWith('?')) {
          return {...failure('unsupported', 'VC-AI-XML-THEME-ATTRIBUTE-UNSUPPORTED', 'Theme attributes require runtime theme resolution.', path, source, attribute.start), elapsedMs: performance.now() - started};
        }
      }
    }
  }

  const definitionsByKey = new Map();
  const stylesByName = new Map();
  for (const file of valueFiles) {
    const resourceRoot = normalizedResourceRoots.find((path) => file.normalized.startsWith(`${path}/`));
    const precedence = normalizedResourceRoots.indexOf(resourceRoot);
    const directory = file.normalized.slice(resourceRoot.length + 1).split('/')[0];
    const qualifiers = directory === 'values' ? [] : directory.slice('values-'.length).split('-');
    const parsed = parseValuesFile(contentByPath.get(file.normalized).toString('utf8'), file.normalized, qualifiers, precedence);
    if (parsed.status !== 'success') return {...parsed, elapsedMs: performance.now() - started};
    for (const definition of parsed.definitions) {
      const key = definitionKey(definition.type, definition.name);
      const entries = definitionsByKey.get(key) ?? [];
      entries.push(definition);
      if (entries.length > limits.maxDefinitionsPerResource) return {...failure('limited', 'VC-AI-XML-PROJECT-LIMIT', `Resource @${key} exceeds maxDefinitionsPerResource.`, file.normalized), elapsedMs: performance.now() - started};
      definitionsByKey.set(key, entries);
    }
    for (const style of parsed.styles) {
      const entries = stylesByName.get(style.name) ?? [];
      entries.push(style);
      stylesByName.set(style.name, entries);
    }
  }

  const effectiveStyles = [];
  const referencedResourceKeys = new Set();
  let styleFailure = null;
  for (const layout of parsedLayouts) {
    visitLayout(layout.root, (node) => {
      if (styleFailure) return;
      const attributes = new Map(node.attributes.map((attribute) => [attribute.name, attribute]));
      for (const attribute of node.attributes) {
        const resource = /^@(string|dimen)\/([A-Za-z][A-Za-z0-9_]*)$/u.exec(attribute.value);
        if (resource) referencedResourceKeys.add(definitionKey(resource[1], resource[2]));
      }
      const styleAttribute = attributes.get('style');
      if (!styleAttribute) return;
      const match = /^@style\/([A-Za-z][A-Za-z0-9_]*)$/u.exec(styleAttribute.value);
      if (!match) {
        styleFailure = failure('unsupported', 'VC-AI-XML-RESOURCE-UNSUPPORTED', 'Styles must use unqualified @style/name references.', layout.path, layout.source, styleAttribute.start);
        return;
      }
      const resolvedStyle = resolveStyle(match[1], stylesByName, limits);
      if (resolvedStyle.status !== 'success') {
        styleFailure = resolvedStyle;
        return;
      }
      const allowed = new Set([...commonAttributes, ...(elementAttributes[node.name] ?? [])]);
      const items = [];
      for (const item of resolvedStyle.items) {
        if (!allowed.has(item.attribute)) {
          styleFailure = failure('unsupported', 'VC-AI-XML-STYLE-ITEM-UNSUPPORTED', `${item.attribute} is not supported on ${node.name}.`, item.path);
          return;
        }
        const value = attributeValue(item.rawValue, item.attribute);
        if (!value || value.kind === 'resource' && value.type === 'style') {
          styleFailure = failure('unsupported', item.rawValue.startsWith('?') ? 'VC-AI-XML-THEME-ATTRIBUTE-UNSUPPORTED' : 'VC-AI-XML-STYLE-ITEM-UNSUPPORTED', `Style item ${item.attribute} has an unsupported value.`, item.path);
          return;
        }
        if (value.kind === 'resource') referencedResourceKeys.add(definitionKey(value.type, value.name));
        items.push({attribute: item.attribute, value, definedBy: item.definedBy, startLine: item.startLine});
      }
      items.sort((left, right) => styleAttributeOrder.indexOf(left.attribute) - styleAttributeOrder.indexOf(right.attribute));
      effectiveStyles.push({reference: styleAttribute.value, name: match[1], chain: resolvedStyle.chain, items});
    });
  }
  if (styleFailure) return {...styleFailure, elapsedMs: performance.now() - started};

  const ids = new Set(parsedLayouts.flatMap((layout) => [...layoutIds(layout.root)]));
  const layoutName = basename(normalizedLayout, '.xml');
  const sourceInputs = readableSources.map((file) => ({
    path: file.normalized,
    source: contentByPath.get(file.normalized).toString('utf8'),
  }));
  const inventory = inventoryCallSites(sourceInputs, layoutName, ids, limits);
  if (inventory.status !== 'success') return {...inventory, elapsedMs: performance.now() - started};
  for (const finding of inventory.findings) {
    const match = /^(string|dimen)\/([A-Za-z][A-Za-z0-9_]*)$/u.exec(finding.target);
    if (match) referencedResourceKeys.add(definitionKey(match[1], match[2]));
  }

  const resources = [];
  for (const key of [...referencedResourceKeys].sort()) {
    const entries = definitionsByKey.get(key);
    if (!entries) return {...failure('unsupported', 'VC-AI-XML-RESOURCE-MISSING-DEFAULT', `Resource @${key} has no scanned definition.`, normalizedLayout), elapsedMs: performance.now() - started};
    const selectedResult = selectedDefinitions(entries, normalizedLayout);
    if (selectedResult.status !== 'success') return {...selectedResult, elapsedMs: performance.now() - started};
    const selected = selectedResult.selected;
    resources.push({
      reference: `@${key}`,
      type: selected.type,
      name: selected.name,
      status: selected.type === 'string' ? 'preserved' : 'resolved',
      definitions: entries.sort((left, right) => left.path.localeCompare(right.path) || left.startLine - right.startLine).map((entry) => ({
        path: entry.path,
        startLine: entry.startLine,
        qualifiers: entry.qualifiers,
        selected: entry === selected,
        value: entry.value,
      })),
      effectiveValue: selected.value,
    });
  }
  const normalizedStyles = [...new Map(effectiveStyles.map((style) => [style.reference, style])).values()]
    .sort((left, right) => left.reference.localeCompare(right.reference));
  const resourcesByReference = new Map(resources.map((resource) => [resource.reference, resource]));
  const rootLayout = parsedLayouts.find((layout) => layout.path === normalizedLayout);
  const resolvedSource = resolveLayoutStyles(layoutSource, rootLayout.root, normalizedStyles, resourcesByReference);
  const parsedResolved = parseBoundedAndroidLayoutXml({source: resolvedSource, path: normalizedLayout});
  if (parsedResolved.status !== 'success') return {...parsedResolved, elapsedMs: performance.now() - started};

  const fingerprint = createHash('sha256');
  for (const [path, content] of [...contentByPath].sort(([left], [right]) => left.localeCompare(right))) {
    fingerprint.update(path).update('\0').update(sha256(content)).update('\n');
  }
  const context = {
    schemaVersion: 1,
    contextId: `android-xml-project-context-v1:${layoutName}`,
    layout: {path: normalizedLayout, resourceName: layoutName, fingerprint: sha256(layoutSource)},
    resourceRoots: normalizedResourceRoots.map((path, precedence) => ({path, precedence})),
    sourceRoots: normalizedSourceRoots,
    resources,
    styles: normalizedStyles,
    callSites: inventory.findings,
    coverage: {
      resourceFiles: valueFiles.length,
      sourceFiles: readableSources.length,
      scannedBytes: totalBytes,
      resourceResolution: 'explicit-roots-only',
      callSiteAnalysis: 'bounded-lexical',
      completeness: 'not-proven',
      executedProjectBuildLogic: false,
      networkAccess: false,
    },
    limitations: [
      'agp-variant-merge-not-executed',
      'dynamic-and-generated-call-sites-not-proven',
      'qualified-resource-selection-not-proven',
      'semantic-listener-and-mutation-ownership-not-proven',
    ],
    fingerprint: fingerprint.digest('hex'),
  };
  assertSchemaValue(context, await loadContextSchema(), 'Resolved Android XML project context');
  return {
    status: 'success',
    diagnostics: [],
    context,
    resolvedSource,
    resolvedLayoutSources: Object.fromEntries(parsedLayouts.map((layout) => [
      layout.path,
      resolveLayoutStyles(layout.source, layout.root, normalizedStyles, resourcesByReference),
    ])),
    elapsedMs: performance.now() - started,
  };
}
