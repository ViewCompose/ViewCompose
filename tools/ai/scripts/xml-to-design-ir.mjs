import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {basename, extname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {assertSchemaValue} from './schema-validator.mjs';
import {sourceLocation, utf8Bytes} from './tool-core.mjs';

export const ANDROID_XML_NAMESPACE = 'http://schemas.android.com/apk/res/android';
export const XML_MIGRATION_LIMITS = Object.freeze({
  maxInputBytes: 262144,
  maxDepth: 32,
  maxNodes: 500,
  maxAttributesPerNode: 64,
  maxUnsupportedFragments: 1000,
});

const designIrSchemaPath = fileURLToPath(
  new URL('../contracts/design-ir.schema.json', import.meta.url),
);
const supportedElements = new Set([
  'LinearLayout',
  'FrameLayout',
  'TextView',
  'EditText',
  'Button',
  'ImageView',
]);
const elementAttributes = Object.freeze({
  LinearLayout: new Set(['android:orientation', 'android:padding']),
  FrameLayout: new Set(['android:padding']),
  TextView: new Set(['android:text']),
  EditText: new Set(['android:hint', 'android:inputType']),
  Button: new Set(['android:text']),
  ImageView: new Set(['android:src', 'android:contentDescription', 'android:scaleType']),
});
const commonAttributes = new Set([
  'android:id',
  'android:layout_width',
  'android:layout_height',
  'android:visibility',
]);
const inputTypes = new Set(['text', 'textEmailAddress', 'textPassword', 'number']);
const imageScaleTypes = new Map([
  ['fitCenter', 'fit'],
  ['centerCrop', 'crop'],
  ['fitXY', 'fill-bounds'],
  ['centerInside', 'inside'],
]);
const visibilityValues = new Set(['visible', 'invisible', 'gone']);
let designIrSchemaPromise;

function loadDesignIrSchema() {
  designIrSchemaPromise ??= readFile(designIrSchemaPath, 'utf8').then(JSON.parse);
  return designIrSchemaPromise;
}

function normalizedLimits(requested = {}) {
  if (requested === null || typeof requested !== 'object' || Array.isArray(requested)) return null;
  const limits = {...XML_MIGRATION_LIMITS};
  for (const [name, ceiling] of Object.entries(XML_MIGRATION_LIMITS)) {
    if (requested[name] === undefined) continue;
    if (!Number.isInteger(requested[name]) || requested[name] <= 0 || requested[name] > ceiling) {
      return null;
    }
    limits[name] = requested[name];
  }
  return limits;
}

function logicalPath(path) {
  if (
    typeof path !== 'string' ||
    path.length === 0 ||
    path.length > 4096 ||
    path.includes('\0') ||
    path.startsWith('/') ||
    /^[a-zA-Z]:[\\/]/u.test(path)
  ) return null;
  const normalized = path.replaceAll('\\', '/');
  if (normalized.split('/').some((segment) => segment === '..' || segment === '.' || segment.length === 0)) {
    return null;
  }
  return normalized;
}

function documentId(path) {
  const name = basename(path, extname(path));
  const normalized = name
    .replace(/[^a-zA-Z0-9._:-]+/gu, '-')
    .replace(/^-+|-+$/gu, '');
  return normalized || 'layout';
}

function lineNumber(source, offset) {
  let line = 1;
  for (let index = 0; index < offset; index += 1) {
    if (source.charCodeAt(index) === 10) line += 1;
  }
  return line;
}

function xmlNameCharacter(character, first) {
  return first
    ? /[A-Za-z_]/u.test(character)
    : /[A-Za-z0-9_.:-]/u.test(character);
}

function readName(source, cursor) {
  const start = cursor;
  if (!xmlNameCharacter(source[cursor] ?? '', true)) return null;
  cursor += 1;
  while (cursor < source.length && xmlNameCharacter(source[cursor], false)) cursor += 1;
  return {value: source.slice(start, cursor), end: cursor};
}

function skipWhitespace(source, cursor) {
  while (cursor < source.length && /\s/u.test(source[cursor])) cursor += 1;
  return cursor;
}

function validXmlCodePoint(value) {
  return value === 0x9 || value === 0xa || value === 0xd ||
    (value >= 0x20 && value <= 0xd7ff) ||
    (value >= 0xe000 && value <= 0xfffd) ||
    (value >= 0x10000 && value <= 0x10ffff);
}

function invalidXmlCharacterOffset(source) {
  for (let index = 0; index < source.length;) {
    const value = source.codePointAt(index);
    if (!validXmlCodePoint(value)) return index;
    index += value > 0xffff ? 2 : 1;
  }
  return -1;
}

function decodeAttribute(value) {
  const replacements = {
    amp: '&',
    apos: "'",
    gt: '>',
    lt: '<',
    quot: '"',
  };
  let invalid = null;
  const decoded = value.replace(/&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z][A-Za-z0-9]+);/gu, (match, entity) => {
    if (Object.hasOwn(replacements, entity)) return replacements[entity];
    const numeric = entity.startsWith('#x')
      ? Number.parseInt(entity.slice(2), 16)
      : entity.startsWith('#')
        ? Number.parseInt(entity.slice(1), 10)
        : Number.NaN;
    if (Number.isInteger(numeric) && validXmlCodePoint(numeric)) {
      return String.fromCodePoint(numeric);
    }
    invalid = match;
    return match;
  });
  if (invalid || /&(?!#x[0-9a-fA-F]+;|#[0-9]+;|amp;|apos;|gt;|lt;|quot;)/u.test(value)) {
    return null;
  }
  return decoded;
}

function parseStartTag(source, start, limits) {
  let cursor = start + 1;
  const parsedName = readName(source, cursor);
  if (!parsedName) return {errorOffset: cursor, reason: 'Expected an XML element name.'};
  cursor = parsedName.end;
  const attributes = [];
  const names = new Set();
  while (cursor < source.length) {
    cursor = skipWhitespace(source, cursor);
    if (source.startsWith('/>', cursor)) {
      return {
        node: {
          name: parsedName.value,
          attributes,
          children: [],
          start,
          end: cursor + 2,
          raw: source.slice(start, cursor + 2),
        },
        selfClosing: true,
        end: cursor + 2,
      };
    }
    if (source[cursor] === '>') {
      return {
        node: {
          name: parsedName.value,
          attributes,
          children: [],
          start,
          end: null,
          raw: null,
        },
        selfClosing: false,
        end: cursor + 1,
      };
    }
    const attributeStart = cursor;
    const attributeName = readName(source, cursor);
    if (!attributeName) return {errorOffset: cursor, reason: 'Expected an XML attribute name.'};
    cursor = skipWhitespace(source, attributeName.end);
    if (source[cursor] !== '=') {
      return {errorOffset: cursor, reason: `Attribute ${attributeName.value} is missing '='.`};
    }
    cursor = skipWhitespace(source, cursor + 1);
    const quote = source[cursor];
    if (quote !== '"' && quote !== "'") {
      return {errorOffset: cursor, reason: `Attribute ${attributeName.value} must be quoted.`};
    }
    const valueStart = cursor + 1;
    const valueEnd = source.indexOf(quote, valueStart);
    if (valueEnd < 0) {
      return {errorOffset: valueStart, reason: `Attribute ${attributeName.value} is not closed.`};
    }
    const rawValue = source.slice(valueStart, valueEnd);
    if (rawValue.includes('<')) {
      return {errorOffset: valueStart, reason: `Attribute ${attributeName.value} contains '<'.`};
    }
    const value = decodeAttribute(rawValue);
    if (value === null) {
      return {errorOffset: valueStart, reason: `Attribute ${attributeName.value} has an invalid entity.`};
    }
    if (names.has(attributeName.value)) {
      return {errorOffset: attributeStart, reason: `Attribute ${attributeName.value} is duplicated.`};
    }
    names.add(attributeName.value);
    attributes.push({
      name: attributeName.value,
      value,
      rawValue,
      start: attributeStart,
      raw: source.slice(attributeStart, valueEnd + 1),
    });
    if (attributes.length > limits.maxAttributesPerNode) {
      return {limitedOffset: attributeStart, reason: 'The element exceeds maxAttributesPerNode.'};
    }
    cursor = valueEnd + 1;
  }
  return {errorOffset: start, reason: `Element ${parsedName.value} is not closed.`};
}

function parseXml(source, path, limits) {
  if (/<!DOCTYPE\b/iu.test(source)) {
    const offset = source.search(/<!DOCTYPE\b/iu);
    return failure('unsupported', 'VC-AI-XML-DOCTYPE-UNSUPPORTED',
      'DOCTYPE declarations are outside the offline XML migration subset.', source, path, offset);
  }
  if (/<!ENTITY\b/iu.test(source)) {
    const offset = source.search(/<!ENTITY\b/iu);
    return failure('unsupported', 'VC-AI-XML-ENTITY-UNSUPPORTED',
      'Entity declarations are outside the offline XML migration subset.', source, path, offset);
  }
  const roots = [];
  const stack = [];
  let cursor = 0;
  let nodes = 0;
  let sawDeclaration = false;
  while (cursor < source.length) {
    const opening = source.indexOf('<', cursor);
    const textEnd = opening < 0 ? source.length : opening;
    if (source.slice(cursor, textEnd).trim().length > 0) {
      return failure('invalid', 'VC-AI-XML-MALFORMED',
        'Android layout XML cannot contain text outside elements.', source, path, cursor);
    }
    if (opening < 0) break;
    if (source.startsWith('<!--', opening)) {
      const close = source.indexOf('-->', opening + 4);
      if (close < 0) {
        return failure('invalid', 'VC-AI-XML-MALFORMED',
          'XML comment is not closed.', source, path, opening);
      }
      cursor = close + 3;
      continue;
    }
    if (source.startsWith('<?', opening)) {
      const close = source.indexOf('?>', opening + 2);
      if (close < 0) {
        return failure('invalid', 'VC-AI-XML-MALFORMED',
          'XML processing instruction is not closed.', source, path, opening);
      }
      const instruction = source.slice(opening, close + 2);
      if (
        sawDeclaration ||
        roots.length > 0 ||
        stack.length > 0 ||
        !/^<\?xml\s+version=(['"])1\.0\1(?:\s+encoding=(['"])utf-8\2)?\s*\?>$/iu.test(instruction)
      ) {
        return failure('unsupported', 'VC-AI-XML-VALUE-UNSUPPORTED',
          'Only one leading XML 1.0 UTF-8 declaration is supported.', source, path, opening);
      }
      sawDeclaration = true;
      cursor = close + 2;
      continue;
    }
    if (source.startsWith('<![CDATA[', opening) || source.startsWith('<!', opening)) {
      return failure('unsupported', 'VC-AI-XML-VALUE-UNSUPPORTED',
        'CDATA and declarations are outside the Android layout XML subset.', source, path, opening);
    }
    if (source.startsWith('</', opening)) {
      let endCursor = skipWhitespace(source, opening + 2);
      const name = readName(source, endCursor);
      if (!name) {
        return failure('invalid', 'VC-AI-XML-MALFORMED',
          'Closing tag is missing an element name.', source, path, opening);
      }
      endCursor = skipWhitespace(source, name.end);
      if (source[endCursor] !== '>') {
        return failure('invalid', 'VC-AI-XML-MALFORMED',
          `Closing tag ${name.value} is malformed.`, source, path, opening);
      }
      const current = stack.pop();
      if (!current || current.name !== name.value) {
        return failure('invalid', 'VC-AI-XML-MALFORMED',
          `Closing tag ${name.value} does not match the open element.`, source, path, opening);
      }
      current.end = endCursor + 1;
      current.raw = source.slice(current.start, current.end);
      cursor = current.end;
      continue;
    }
    const startTag = parseStartTag(source, opening, limits);
    if (startTag.limitedOffset !== undefined) {
      return failure('limited', 'VC-AI-XML-LIMIT', startTag.reason, source, path, startTag.limitedOffset);
    }
    if (!startTag.node) {
      return failure('invalid', 'VC-AI-XML-MALFORMED', startTag.reason, source, path, startTag.errorOffset);
    }
    nodes += 1;
    if (nodes > limits.maxNodes) {
      return failure('limited', 'VC-AI-XML-LIMIT',
        'The document exceeds maxNodes.', source, path, opening);
    }
    const parent = stack.at(-1);
    if (parent) parent.children.push(startTag.node);
    else roots.push(startTag.node);
    if (roots.length > 1) {
      return failure('invalid', 'VC-AI-XML-MALFORMED',
        'Android layout XML must contain exactly one root element.', source, path, opening);
    }
    if (!startTag.selfClosing) {
      stack.push(startTag.node);
      if (stack.length > limits.maxDepth) {
        return failure('limited', 'VC-AI-XML-LIMIT',
          'The document exceeds maxDepth.', source, path, opening);
      }
    }
    cursor = startTag.end;
  }
  if (stack.length > 0) {
    return failure('invalid', 'VC-AI-XML-MALFORMED',
      `Element ${stack.at(-1).name} is not closed.`, source, path, stack.at(-1).start);
  }
  if (roots.length !== 1) {
    return failure('invalid', 'VC-AI-XML-MALFORMED',
      'Android layout XML must contain exactly one root element.', source, path, 0);
  }
  return {status: 'success', root: roots[0]};
}

function failure(status, code, message, source, path, offset, nextAction) {
  return {
    status,
    diagnostics: [{
      code,
      severity: 'error',
      message,
      nextAction: nextAction ?? 'Use only the frozen Android XML layout v1 subset.',
      source: sourceLocation(source, path, Math.max(0, offset ?? 0)),
    }],
  };
}

function attributeMap(node) {
  return new Map(node.attributes.map((attribute) => [attribute.name, attribute]));
}

function layoutDimension(value) {
  if (value === 'match_parent') return {kind: 'layout-dimension', value: 'match-parent'};
  if (value === 'wrap_content') return {kind: 'layout-dimension', value: 'wrap-content'};
  const dimension = /^(0|[1-9][0-9]*)dp$/u.exec(value);
  return dimension ? {kind: 'dimension', value: Number(dimension[1]), unit: 'dp'} : null;
}

function dpDimension(value) {
  const dimension = /^(0|[1-9][0-9]*)dp$/u.exec(value);
  return dimension ? {kind: 'dimension', value: Number(dimension[1]), unit: 'dp'} : null;
}

function resourceOrLiteral(value) {
  const resource = /^@string\/([A-Za-z0-9_]+)$/u.exec(value);
  if (resource) return {kind: 'resource', resourceType: 'string', name: resource[1]};
  if (value.startsWith('@') || value.startsWith('?')) return null;
  return {kind: 'literal', value};
}

function drawableResource(value) {
  const resource = /^@drawable\/([A-Za-z][A-Za-z0-9_]*)$/u.exec(value);
  return resource
    ? {kind: 'resource', resourceType: 'drawable', name: resource[1]}
    : null;
}

function imageDescription(value) {
  if (value === '@null') return {kind: 'literal', value: null};
  const description = resourceOrLiteral(value);
  if (description?.kind === 'literal' && description.value.trim().length === 0) return null;
  return description;
}

function androidId(value) {
  return /^@\+?id\/([A-Za-z][A-Za-z0-9_]*)$/u.exec(value)?.[1] ?? null;
}

function lowerCamel(value) {
  const words = value.split(/[^A-Za-z0-9]+/u).filter(Boolean);
  if (words.length === 0) return 'value';
  const joined = words.map((word, index) => index === 0
    ? `${word[0].toLowerCase()}${word.slice(1)}`
    : `${word[0].toUpperCase()}${word.slice(1)}`).join('');
  return /^[A-Za-z]/u.test(joined) ? joined : `value${joined}`;
}

function unsupportedFragment({node, attribute, code, reason, source, path, sourceId}) {
  const offset = attribute?.start ?? node.start;
  return {
    sourceId: attribute ? `${sourceId}.${attribute.name}` : sourceId,
    sourceSpan: `${path}:${lineNumber(source, offset)}`,
    code,
    reason,
    preservedSource: (attribute?.raw ?? node.raw ?? source.slice(node.start, node.end ?? node.start + 1))
      .slice(0, 16384),
    disposition: 'blocked',
    diagnosticSource: {path, source, offset},
  };
}

function addUnsupported(state, fragment) {
  if (state.unsupported.length >= state.limits.maxUnsupportedFragments) {
    state.limitExceeded = true;
    return;
  }
  const {diagnosticSource, ...publicFragment} = fragment;
  state.unsupported.push(publicFragment);
  state.unsupportedLocations.push(diagnosticSource);
}

function mapNode(node, sourcePath, source, state, sourceIndex) {
  sourcePath = node.origin?.path ?? sourcePath;
  source = node.origin?.source ?? source;
  const fallbackSourceId = `${node.name}[${sourceIndex}]`;
  if (node.name === 'layout') {
    addUnsupported(state, unsupportedFragment({
      node,
      code: 'VC-AI-XML-DATA-BINDING-UNSUPPORTED',
      reason: 'Android Data Binding layout roots and expressions require typed call-site analysis.',
      source,
      path: sourcePath,
      sourceId: fallbackSourceId,
    }));
    return null;
  }
  if (node.name === 'include') {
    addUnsupported(state, unsupportedFragment({
      node,
      code: 'VC-AI-XML-PROJECT-CONTEXT-REQUIRED',
      reason: 'Android include elements require explicit project and resource roots.',
      source,
      path: sourcePath,
      sourceId: fallbackSourceId,
    }));
    return null;
  }
  if (node.name === 'merge') {
    addUnsupported(state, unsupportedFragment({
      node,
      code: 'VC-AI-XML-MERGE-ROOT-UNSUPPORTED',
      reason: 'A merge root is supported only when expanded from an explicit project include.',
      source,
      path: sourcePath,
      sourceId: fallbackSourceId,
    }));
    return null;
  }
  if (!supportedElements.has(node.name)) {
    const custom = node.name.includes('.');
    addUnsupported(state, unsupportedFragment({
      node,
      code: custom ? 'VC-AI-XML-CUSTOM-VIEW-UNSUPPORTED' : 'VC-AI-XML-ELEMENT-UNSUPPORTED',
      reason: custom
        ? 'Custom Android Views require an explicit ViewCompose interop or component mapping.'
        : `Element ${node.name} is outside android-xml-layout-v1.`,
      source,
      path: sourcePath,
      sourceId: fallbackSourceId,
    }));
    return null;
  }

  const attributes = attributeMap(node);
  const idAttribute = attributes.get('android:id');
  const idName = idAttribute ? androidId(idAttribute.value) : null;
  const sourceId = idName ? `@id/${idName}` : fallbackSourceId;
  let nodeId = idName ? `id:${idName}` : `xml:${sourceIndex}`;
  if (idAttribute && !idName) {
    addUnsupported(state, unsupportedFragment({
      node,
      attribute: idAttribute,
      code: 'VC-AI-XML-VALUE-UNSUPPORTED',
      reason: 'android:id must be @+id/name or @id/name.',
      source,
      path: sourcePath,
      sourceId,
    }));
  } else if (idName) {
    if (state.ids.has(idName)) {
      nodeId = `xml:${sourceIndex}`;
      addUnsupported(state, unsupportedFragment({
        node,
        attribute: idAttribute,
        code: 'VC-AI-XML-DUPLICATE-ID',
        reason: `Android id ${idName} appears more than once.`,
        source,
        path: sourcePath,
        sourceId,
      }));
    }
    state.ids.add(idName);
  }

  for (const attribute of node.attributes) {
    if (attribute.name === 'xmlns:android') {
      if (sourceIndex === '0') continue;
      addUnsupported(state, unsupportedFragment({
        node,
        attribute,
        code: 'VC-AI-XML-NAMESPACE-UNSUPPORTED',
        reason: 'The Android namespace must be declared exactly once on the root element.',
        source,
        path: sourcePath,
        sourceId,
      }));
      continue;
    }
    if (attribute.value.includes('@{') || attribute.value.includes('@=')) {
      addUnsupported(state, unsupportedFragment({
        node,
        attribute,
        code: 'VC-AI-XML-DATA-BINDING-UNSUPPORTED',
        reason: 'Data Binding expressions require typed application-state analysis.',
        source,
        path: sourcePath,
        sourceId,
      }));
      continue;
    }
    if (attribute.name.startsWith('xmlns:') || !attribute.name.startsWith('android:')) {
      addUnsupported(state, unsupportedFragment({
        node,
        attribute,
        code: 'VC-AI-XML-NAMESPACE-UNSUPPORTED',
        reason: `Namespace attribute ${attribute.name} is outside android-xml-layout-v1.`,
        source,
        path: sourcePath,
        sourceId,
      }));
      continue;
    }
    if (!commonAttributes.has(attribute.name) && !elementAttributes[node.name].has(attribute.name)) {
      addUnsupported(state, unsupportedFragment({
        node,
        attribute,
        code: 'VC-AI-XML-ATTRIBUTE-UNSUPPORTED',
        reason: `${attribute.name} is not supported on ${node.name}.`,
        source,
        path: sourcePath,
        sourceId,
      }));
    }
  }

  const widthAttribute = attributes.get('android:layout_width');
  const heightAttribute = attributes.get('android:layout_height');
  for (const [name, attribute] of [['android:layout_width', widthAttribute], ['android:layout_height', heightAttribute]]) {
    if (!attribute) {
      addUnsupported(state, unsupportedFragment({
        node,
        code: 'VC-AI-XML-REQUIRED-ATTRIBUTE',
        reason: `${name} is required by Android layout XML.`,
        source,
        path: sourcePath,
        sourceId,
      }));
    }
  }
  const width = widthAttribute ? layoutDimension(widthAttribute.value) : null;
  const height = heightAttribute ? layoutDimension(heightAttribute.value) : null;
  for (const [attribute, parsed] of [[widthAttribute, width], [heightAttribute, height]]) {
    if (attribute && !parsed) {
      addUnsupported(state, unsupportedFragment({
        node,
        attribute,
        code: 'VC-AI-XML-VALUE-UNSUPPORTED',
        reason: `${attribute.name} must be match_parent, wrap_content, or a non-negative integer dp value.`,
        source,
        path: sourcePath,
        sourceId,
      }));
    }
  }

  const properties = [];
  const modifiers = [];
  if (width && height) {
    modifiers.push({
      kind: 'size',
      arguments: [
        {name: 'width', value: width},
        {name: 'height', value: height},
      ],
    });
  }
  let kind;
  let decision;
  if (node.name === 'LinearLayout') {
    const orientationAttribute = attributes.get('android:orientation');
    const orientation = orientationAttribute?.value ?? 'horizontal';
    if (!['horizontal', 'vertical'].includes(orientation)) {
      addUnsupported(state, unsupportedFragment({
        node,
        attribute: orientationAttribute,
        code: 'VC-AI-XML-VALUE-UNSUPPORTED',
        reason: 'android:orientation must be horizontal or vertical.',
        source,
        path: sourcePath,
        sourceId,
      }));
    }
    kind = orientation === 'vertical' ? 'column' : 'row';
    properties.push({
      name: 'orientation',
      value: {kind: 'enum', type: 'linear-orientation', value: orientation},
    });
    const paddingAttribute = attributes.get('android:padding');
    if (paddingAttribute) {
      const padding = dpDimension(paddingAttribute.value);
      if (padding) {
        modifiers.push({kind: 'padding', arguments: [{name: 'all', value: padding}]});
      } else {
        addUnsupported(state, unsupportedFragment({
          node,
          attribute: paddingAttribute,
          code: 'VC-AI-XML-VALUE-UNSUPPORTED',
          reason: 'android:padding must be a non-negative integer dp value.',
          source,
          path: sourcePath,
          sourceId,
        }));
      }
    }
    decision = orientation === 'vertical'
      ? 'Map a vertical LinearLayout to Column and preserve size and padding.'
      : 'Map a horizontal LinearLayout to Row and preserve size and padding.';
  } else if (node.name === 'FrameLayout') {
    kind = 'box';
    const paddingAttribute = attributes.get('android:padding');
    if (paddingAttribute) {
      const padding = dpDimension(paddingAttribute.value);
      if (padding) {
        modifiers.push({kind: 'padding', arguments: [{name: 'all', value: padding}]});
      } else {
        addUnsupported(state, unsupportedFragment({
          node,
          attribute: paddingAttribute,
          code: 'VC-AI-XML-VALUE-UNSUPPORTED',
          reason: 'android:padding must be a non-negative integer dp value.',
          source,
          path: sourcePath,
          sourceId,
        }));
      }
    }
    decision = 'Map FrameLayout to Box, preserve overlay child order, size, and padding.';
  } else if (node.name === 'TextView') {
    kind = 'text';
    const textAttribute = attributes.get('android:text');
    if (textAttribute) {
      const text = resourceOrLiteral(textAttribute.value);
      if (text) properties.push({name: 'text', value: text});
      else addUnsupported(state, unsupportedFragment({
        node,
        attribute: textAttribute,
        code: 'VC-AI-XML-VALUE-UNSUPPORTED',
        reason: 'android:text must be a literal or unqualified @string/name.',
        source,
        path: sourcePath,
        sourceId,
      }));
    }
    decision = 'Map TextView to Text and preserve its string resource as a caller binding.';
  } else if (node.name === 'EditText') {
    kind = 'text-field';
    const hintAttribute = attributes.get('android:hint');
    if (hintAttribute) {
      const hint = resourceOrLiteral(hintAttribute.value);
      if (hint) properties.push({name: 'hint', value: hint});
      else addUnsupported(state, unsupportedFragment({
        node,
        attribute: hintAttribute,
        code: 'VC-AI-XML-VALUE-UNSUPPORTED',
        reason: 'android:hint must be a literal or unqualified @string/name.',
        source,
        path: sourcePath,
        sourceId,
      }));
    }
    const inputTypeAttribute = attributes.get('android:inputType');
    if (inputTypeAttribute) {
      if (inputTypes.has(inputTypeAttribute.value)) {
        properties.push({
          name: 'inputType',
          value: {kind: 'enum', type: 'android-input-type', value: inputTypeAttribute.value},
        });
      } else {
        addUnsupported(state, unsupportedFragment({
          node,
          attribute: inputTypeAttribute,
          code: 'VC-AI-XML-VALUE-UNSUPPORTED',
          reason: 'android:inputType is outside the four accepted single-value profiles.',
          source,
          path: sourcePath,
          sourceId,
        }));
      }
    }
    decision = inputTypeAttribute?.value === 'textEmailAddress'
      ? 'Map EditText to TextField with caller-owned state and the Email input profile.'
      : 'Map EditText to TextField with caller-owned state and the declared input profile.';
  } else if (node.name === 'Button') {
    kind = 'button';
    const textAttribute = attributes.get('android:text');
    if (textAttribute) {
      const text = resourceOrLiteral(textAttribute.value);
      if (text) properties.push({name: 'text', value: text});
      else addUnsupported(state, unsupportedFragment({
        node,
        attribute: textAttribute,
        code: 'VC-AI-XML-VALUE-UNSUPPORTED',
        reason: 'android:text must be a literal or unqualified @string/name.',
        source,
        path: sourcePath,
        sourceId,
      }));
    }
    decision = 'Map Button to Button without inventing an absent click listener.';
  } else {
    kind = 'image';
    const sourceAttribute = attributes.get('android:src');
    const imageSource = sourceAttribute ? drawableResource(sourceAttribute.value) : null;
    if (imageSource) {
      properties.push({name: 'source', value: imageSource});
    } else {
      addUnsupported(state, unsupportedFragment({
        node,
        attribute: sourceAttribute,
        code: sourceAttribute ? 'VC-AI-XML-VALUE-UNSUPPORTED' : 'VC-AI-XML-REQUIRED-ATTRIBUTE',
        reason: 'ImageView requires android:src as an unqualified @drawable/name.',
        source,
        path: sourcePath,
        sourceId,
      }));
    }
    const descriptionAttribute = attributes.get('android:contentDescription');
    const contentDescription = descriptionAttribute
      ? imageDescription(descriptionAttribute.value)
      : null;
    if (contentDescription) {
      properties.push({name: 'contentDescription', value: contentDescription});
    } else {
      addUnsupported(state, unsupportedFragment({
        node,
        attribute: descriptionAttribute,
        code: 'VC-AI-XML-ACCESSIBILITY-REQUIRED',
        reason: 'ImageView requires a non-empty content description or explicit @null decoration.',
        source,
        path: sourcePath,
        sourceId,
      }));
    }
    const scaleAttribute = attributes.get('android:scaleType');
    const scale = scaleAttribute?.value ?? 'fitCenter';
    const mappedScale = imageScaleTypes.get(scale);
    if (mappedScale) {
      properties.push({
        name: 'contentScale',
        value: {kind: 'enum', type: 'image-content-scale', value: mappedScale},
      });
    } else {
      addUnsupported(state, unsupportedFragment({
        node,
        attribute: scaleAttribute,
        code: 'VC-AI-XML-VALUE-UNSUPPORTED',
        reason: 'android:scaleType must be fitCenter, centerCrop, fitXY, or centerInside.',
        source,
        path: sourcePath,
        sourceId,
      }));
    }
    decision = 'Map ImageView to Image with caller-owned drawable source and explicit content description.';
  }

  const visibilityAttribute = attributes.get('android:visibility');
  if (visibilityAttribute) {
    if (!visibilityValues.has(visibilityAttribute.value)) {
      addUnsupported(state, unsupportedFragment({
        node,
        attribute: visibilityAttribute,
        code: 'VC-AI-XML-VALUE-UNSUPPORTED',
        reason: 'android:visibility must be visible, invisible, or gone.',
        source,
        path: sourcePath,
        sourceId,
      }));
    } else if (visibilityAttribute.value !== 'visible') {
      modifiers.push({
        kind: 'visibility',
        arguments: [{
          name: 'value',
          value: {kind: 'enum', type: 'visibility', value: visibilityAttribute.value},
        }],
      });
    }
  }

  const container = node.name === 'LinearLayout' || node.name === 'FrameLayout';
  if (!container && node.children.length > 0) {
    for (const [index, child] of node.children.entries()) {
      addUnsupported(state, unsupportedFragment({
        node: child,
        code: 'VC-AI-XML-ELEMENT-UNSUPPORTED',
        reason: `${node.name} cannot own child layout elements in android-xml-layout-v1.`,
        source,
        path: sourcePath,
        sourceId: `${child.name}[${sourceIndex}.${index}]`,
      }));
    }
  }

  const children = container
    ? node.children.map((child, index) => mapNode(child, sourcePath, source, state, `${sourceIndex}.${index}`))
      .filter(Boolean)
    : [];
  const semantics = kind === 'button'
    ? [{name: 'role', value: {kind: 'enum', type: 'semantic-role', value: 'button'}}]
    : kind === 'text-field'
      ? [{name: 'role', value: {kind: 'enum', type: 'semantic-role', value: 'text-field'}}]
      : kind === 'image' && properties.find((field) => field.name === 'contentDescription')?.value.value !== null
        ? [{name: 'role', value: {kind: 'enum', type: 'semantic-role', value: 'image'}}]
        : [];
  const stateFields = kind === 'text-field'
    ? [{
        name: 'text',
        value: {
          kind: 'binding',
          name: `${lowerCamel(idName ?? `text-field-${sourceIndex}`)}State`,
          status: 'placeholder',
        },
      }]
    : [];
  return {
    id: nodeId,
    kind,
    properties,
    modifiers,
    semantics,
    events: [],
    state: stateFields,
    children,
    provenance: {
      sourceId,
      sourceSpan: `${sourcePath}:${lineNumber(source, node.start)}`,
      confidence: 1,
      decision,
    },
  };
}

function diagnosticsForUnsupported(unsupported, source, path, locations = []) {
  return unsupported.map((fragment, index) => {
    const location = locations[index];
    if (location) {
      return {
        code: fragment.code,
        severity: 'error',
        message: fragment.reason,
        nextAction: 'Resolve or explicitly replace this source fragment before generating Kotlin.',
        source: sourceLocation(location.source, location.path, location.offset),
      };
    }
    const line = Number(fragment.sourceSpan.slice(fragment.sourceSpan.lastIndexOf(':') + 1));
    const lines = source.split('\n');
    const offset = lines.slice(0, Math.max(0, line - 1)).reduce((sum, item) => sum + item.length + 1, 0);
    return {
      code: fragment.code,
      severity: 'error',
      message: fragment.reason,
      nextAction: 'Resolve or explicitly replace this source fragment before generating Kotlin.',
      source: sourceLocation(source, path, offset),
    };
  });
}

export function parseBoundedAndroidLayoutXml({
  source,
  path = 'layout.xml',
  limits,
} = {}) {
  if (typeof source !== 'string' || source.length === 0) {
    return failure('invalid', 'VC-AI-XML-MALFORMED',
      'XML source must be a non-empty string.', String(source ?? ''), String(path ?? 'layout.xml'), 0);
  }
  const normalizedPath = logicalPath(path);
  if (!normalizedPath) {
    return failure('invalid', 'VC-AI-XML-VALUE-UNSUPPORTED',
      'The XML source identity must be a bounded repository-relative logical path.', source, 'layout.xml', 0);
  }
  const acceptedLimits = normalizedLimits(limits);
  if (!acceptedLimits) {
    return failure('invalid', 'VC-AI-XML-LIMIT',
      'Requested XML limits must be positive integers within the frozen ceilings.', source, normalizedPath, 0);
  }
  if (utf8Bytes(source) > acceptedLimits.maxInputBytes) {
    return failure('limited', 'VC-AI-XML-LIMIT',
      'XML source exceeds maxInputBytes.', source, normalizedPath, 0);
  }
  const invalidCharacter = invalidXmlCharacterOffset(source);
  if (invalidCharacter >= 0) {
    return failure('invalid', 'VC-AI-XML-MALFORMED',
      'XML source contains a character forbidden by XML 1.0.', source, normalizedPath, invalidCharacter);
  }
  const parsed = parseXml(source, normalizedPath, acceptedLimits);
  if (parsed.status !== 'success') return parsed;
  const rootNamespace = attributeMap(parsed.root).get('xmlns:android');
  if (rootNamespace?.value !== ANDROID_XML_NAMESPACE) {
    return failure('unsupported', 'VC-AI-XML-NAMESPACE-UNSUPPORTED',
      `The root must declare xmlns:android="${ANDROID_XML_NAMESPACE}".`,
      source, normalizedPath, parsed.root.start);
  }
  return {
    status: 'success',
    root: parsed.root,
    normalizedPath,
    limits: acceptedLimits,
  };
}

export async function convertXmlToDesignIr({
  source,
  path = 'layout.xml',
  limits,
  expandedRoot,
} = {}) {
  const started = performance.now();
  const parsed = parseBoundedAndroidLayoutXml({source, path, limits});
  if (parsed.status !== 'success') {
    return {...parsed, elapsedMs: performance.now() - started};
  }
  const normalizedPath = parsed.normalizedPath;
  const acceptedLimits = parsed.limits;
  const state = {
    ids: new Set(),
    unsupported: [],
    unsupportedLocations: [],
    limitExceeded: false,
    limits: acceptedLimits,
  };
  const root = mapNode(expandedRoot ?? parsed.root, normalizedPath, source, state, '0');
  if (state.limitExceeded) {
    return {
      ...failure('limited', 'VC-AI-XML-LIMIT',
        'Unsupported fragments exceed maxUnsupportedFragments.', source, normalizedPath, 0),
      elapsedMs: performance.now() - started,
    };
  }
  if (!root) {
    return {
      status: 'unsupported',
      diagnostics: diagnosticsForUnsupported(
        state.unsupported,
        source,
        normalizedPath,
        state.unsupportedLocations,
      ),
      unsupported: state.unsupported,
      elapsedMs: performance.now() - started,
    };
  }
  const ir = {
    schemaVersion: 1,
    documentId: documentId(normalizedPath),
    source: {
      kind: 'android-xml',
      identity: normalizedPath,
      fingerprint: createHash('sha256').update(source).digest('hex'),
    },
    roots: [root],
    unsupported: state.unsupported,
  };
  assertSchemaValue(ir, await loadDesignIrSchema(), 'Converted Android XML Design IR');
  return {
    status: state.unsupported.length === 0 ? 'success' : 'unsupported',
    diagnostics: diagnosticsForUnsupported(
      state.unsupported,
      source,
      normalizedPath,
      state.unsupportedLocations,
    ),
    ir,
    unsupported: state.unsupported,
    elapsedMs: performance.now() - started,
  };
}
