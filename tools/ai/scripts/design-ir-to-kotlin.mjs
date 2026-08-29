import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {validateSchemaValue} from './schema-validator.mjs';

const schemaPath = fileURLToPath(new URL('../contracts/design-ir.schema.json', import.meta.url));
const packageName = 'generated.viewcompose';
const supportedKinds = new Set(['row', 'column', 'text', 'text-field', 'button']);
const kotlinKeywords = new Set([
  'as', 'break', 'class', 'continue', 'do', 'else', 'false', 'for', 'fun', 'if', 'in',
  'interface', 'is', 'null', 'object', 'package', 'return', 'super', 'this', 'throw',
  'true', 'try', 'typealias', 'typeof', 'val', 'var', 'when', 'while',
]);
let schemaPromise;

function loadSchema() {
  schemaPromise ??= readFile(schemaPath, 'utf8').then(JSON.parse);
  return schemaPromise;
}

function generatorDiagnostic(code, message, nextAction = 'Resolve the unsupported IR before generating Kotlin.') {
  return {code, severity: 'error', message, nextAction};
}

function words(value) {
  return value
    .replace(/([a-z0-9])([A-Z])/gu, '$1 $2')
    .split(/[^A-Za-z0-9]+/u)
    .filter(Boolean);
}

function lowerCamel(value) {
  const parts = words(value);
  let result = parts.map((part, index) => index === 0
    ? `${part[0].toLowerCase()}${part.slice(1)}`
    : `${part[0].toUpperCase()}${part.slice(1)}`).join('');
  if (!result) result = 'value';
  if (!/^[A-Za-z_]/u.test(result)) result = `value${result}`;
  if (kotlinKeywords.has(result)) result = `${result}Value`;
  return result;
}

function upperCamel(value) {
  const result = words(value).map((part) => `${part[0].toUpperCase()}${part.slice(1)}`).join('');
  return /^[A-Za-z_]/u.test(result) ? result : `Generated${result || 'Layout'}`;
}

function kotlinString(value) {
  return JSON.stringify(value).replaceAll('$', '\\$');
}

function fieldMap(fields) {
  return new Map(fields.map((field) => [field.name, field.value]));
}

function allocateIdentifier(base, used) {
  let candidate = lowerCamel(base);
  let suffix = 2;
  while (used.has(candidate)) {
    candidate = `${lowerCamel(base)}${suffix}`;
    suffix += 1;
  }
  used.add(candidate);
  return candidate;
}

function visitNodes(roots, visitor) {
  for (const node of roots) {
    visitor(node);
    visitNodes(node.children, visitor);
  }
}

function collectBindings(ir) {
  const used = new Set();
  const resourceByIdentity = new Map();
  const stateByName = new Map();
  const ids = [];
  visitNodes(ir.roots, (node) => {
    if (node.id.startsWith('id:')) {
      ids.push({
        source: `@id/${node.id.slice(3)}`,
        nodeId: node.id,
        key: node.id.slice(3),
      });
    }
    for (const field of node.properties) {
      if (field.value.kind !== 'resource') continue;
      const identity = `@${field.value.resourceType}/${field.value.name}`;
      let binding = resourceByIdentity.get(identity);
      if (!binding) {
        binding = {
          source: identity,
          parameter: allocateIdentifier(field.value.name, used),
          type: 'String',
          nodes: [],
        };
        resourceByIdentity.set(identity, binding);
      }
      binding.nodes.push(node.id);
    }
  });
  visitNodes(ir.roots, (node) => {
    for (const field of node.state) {
      if (field.value.kind !== 'binding') continue;
      let binding = stateByName.get(field.value.name);
      if (!binding) {
        const parameter = allocateIdentifier(field.value.name, used);
        binding = {
          source: field.value.name,
          parameter,
          type: 'TextFieldState',
          status: field.value.status,
          nodes: [],
        };
        stateByName.set(field.value.name, binding);
      }
      binding.nodes.push(node.id);
    }
  });
  return {
    resources: [...resourceByIdentity.values()],
    states: [...stateByName.values()],
    ids,
    resourceByIdentity,
    stateByName,
  };
}

function stringValueSupported(value) {
  return (value?.kind === 'literal' && typeof value.value === 'string') ||
    (value?.kind === 'resource' && value.resourceType === 'string' && value.package === undefined);
}

function sizeValueSupported(value) {
  return (value?.kind === 'layout-dimension' && ['match-parent', 'wrap-content'].includes(value.value)) ||
    (value?.kind === 'dimension' && value.unit === 'dp' && value.value >= 0);
}

function semanticFieldsSupported(node) {
  if (['row', 'column', 'text'].includes(node.kind)) return node.semantics.length === 0;
  const role = fieldMap(node.semantics).get('role');
  const expected = node.kind === 'button' ? 'button' : 'text-field';
  return node.semantics.length === 1 &&
    role?.kind === 'enum' && role.type === 'semantic-role' && role.value === expected;
}

function validateSupportedIr(ir) {
  const diagnostics = [];
  if (ir.source.kind !== 'android-xml' || ir.roots.length !== 1) {
    diagnostics.push(generatorDiagnostic(
      'VC-AI-GENERATOR-UNSUPPORTED',
      'The first Kotlin generator accepts exactly one Android XML root.',
    ));
    return diagnostics;
  }
  if (ir.unsupported.length > 0) {
    diagnostics.push(generatorDiagnostic(
      'VC-AI-IR-UNSUPPORTED',
      'Design IR contains blocked source fragments and cannot produce Kotlin.',
    ));
  }
  visitNodes(ir.roots, (node) => {
    if (!supportedKinds.has(node.kind)) {
      diagnostics.push(generatorDiagnostic(
        'VC-AI-GENERATOR-UNSUPPORTED',
        `Node ${node.id} has unsupported kind ${node.kind}.`,
      ));
      return;
    }
    const allowedProperties = {
      row: new Set(['orientation']),
      column: new Set(['orientation']),
      text: new Set(['text']),
      'text-field': new Set(['hint', 'inputType']),
      button: new Set(['text']),
    }[node.kind];
    for (const property of node.properties) {
      if (!allowedProperties.has(property.name)) {
        diagnostics.push(generatorDiagnostic(
          'VC-AI-GENERATOR-UNSUPPORTED',
          `Node ${node.id} has unsupported property ${property.name}.`,
        ));
      }
      if (property.value.kind === 'expression') {
        diagnostics.push(generatorDiagnostic(
          'VC-AI-IR-UNSUPPORTED',
          `Node ${node.id} contains an unresolved expression.`,
        ));
      }
      if (property.value.kind === 'resource' && property.value.resourceType !== 'string') {
        diagnostics.push(generatorDiagnostic(
          'VC-AI-GENERATOR-UNSUPPORTED',
          `Node ${node.id} uses unsupported resource type ${property.value.resourceType}.`,
        ));
      }
    }
    const properties = fieldMap(node.properties);
    if (node.kind === 'row' || node.kind === 'column') {
      const orientation = properties.get('orientation');
      const expected = node.kind === 'row' ? 'horizontal' : 'vertical';
      if (
        node.properties.length !== 1 ||
        orientation?.kind !== 'enum' ||
        orientation.type !== 'linear-orientation' ||
        orientation.value !== expected
      ) {
        diagnostics.push(generatorDiagnostic(
          'VC-AI-GENERATOR-UNSUPPORTED',
          `Container ${node.id} does not have the normalized ${expected} orientation.`,
        ));
      }
    }
    if (node.kind === 'text' &&
        (node.properties.length > 1 ||
          (properties.has('text') && !stringValueSupported(properties.get('text'))))) {
      diagnostics.push(generatorDiagnostic(
        'VC-AI-GENERATOR-UNSUPPORTED',
        `Text node ${node.id} has a non-string text value.`,
      ));
    }
    if (node.kind === 'button' &&
        (node.properties.length > 1 ||
          (properties.has('text') && !stringValueSupported(properties.get('text'))))) {
      diagnostics.push(generatorDiagnostic(
        'VC-AI-GENERATOR-UNSUPPORTED',
        `Button node ${node.id} has a non-string text value.`,
      ));
    }
    if (node.kind === 'text-field') {
      const hint = properties.get('hint');
      const input = properties.get('inputType');
      if (hint && !stringValueSupported(hint)) {
        diagnostics.push(generatorDiagnostic(
          'VC-AI-GENERATOR-UNSUPPORTED',
          `Text field ${node.id} has a non-string hint value.`,
        ));
      }
      if (input && (
        input.kind !== 'enum' ||
        input.type !== 'android-input-type' ||
        !['text', 'textEmailAddress', 'textPassword', 'number'].includes(input.value)
      )) {
        diagnostics.push(generatorDiagnostic(
          'VC-AI-GENERATOR-UNSUPPORTED',
          `Text field ${node.id} has an unsupported input profile.`,
        ));
      }
    }
    if (!semanticFieldsSupported(node)) {
      diagnostics.push(generatorDiagnostic(
        'VC-AI-GENERATOR-UNSUPPORTED',
        `Node ${node.id} has semantics outside the normalized XML v1 mapping.`,
      ));
    }
    if (node.events.length > 0) {
      diagnostics.push(generatorDiagnostic(
        'VC-AI-IR-UNSUPPORTED',
        `Node ${node.id} contains event behavior outside the XML v1 subset.`,
      ));
    }
    for (const modifier of node.modifiers) {
      const arguments_ = fieldMap(modifier.arguments);
      if (modifier.kind === 'size') {
        if (
          modifier.arguments.length !== 2 ||
          !sizeValueSupported(arguments_.get('width')) ||
          !sizeValueSupported(arguments_.get('height'))
        ) {
          diagnostics.push(generatorDiagnostic(
            'VC-AI-GENERATOR-UNSUPPORTED',
            `Node ${node.id} has an unsupported size modifier.`,
          ));
        }
      } else if (modifier.kind === 'padding') {
        const all = arguments_.get('all');
        if (
          modifier.arguments.length !== 1 ||
          all?.kind !== 'dimension' ||
          all.unit !== 'dp' ||
          all.value < 0
        ) {
          diagnostics.push(generatorDiagnostic(
            'VC-AI-GENERATOR-UNSUPPORTED',
            `Node ${node.id} has an unsupported padding modifier.`,
          ));
        }
      } else {
        diagnostics.push(generatorDiagnostic(
          'VC-AI-GENERATOR-UNSUPPORTED',
          `Node ${node.id} contains unsupported modifier ${modifier.kind}.`,
        ));
      }
    }
    if (node.kind !== 'text-field' && node.state.length > 0) {
      diagnostics.push(generatorDiagnostic(
        'VC-AI-GENERATOR-UNSUPPORTED',
        `Node ${node.id} has unsupported state ownership.`,
      ));
    }
    if (node.kind === 'text-field') {
      const textState = fieldMap(node.state).get('text');
      if (textState?.kind !== 'binding') {
        diagnostics.push(generatorDiagnostic(
          'VC-AI-GENERATOR-UNSUPPORTED',
          `Text field ${node.id} requires one caller-owned text binding.`,
        ));
      }
    }
    if (!['row', 'column'].includes(node.kind) && node.children.length > 0) {
      diagnostics.push(generatorDiagnostic(
        'VC-AI-GENERATOR-UNSUPPORTED',
        `Leaf node ${node.id} cannot contain children.`,
      ));
    }
  });
  return diagnostics;
}

function valueExpression(value, bindings) {
  if (!value) return null;
  if (value.kind === 'literal') return kotlinString(value.value);
  if (value.kind === 'resource') {
    return bindings.resourceByIdentity.get(`@${value.resourceType}/${value.name}`)?.parameter ?? null;
  }
  return null;
}

function dimensionExpression(value) {
  if (value?.kind !== 'dimension' || value.unit !== 'dp') return null;
  return `${Number.isInteger(value.value) ? value.value : value.value.toString()}.dp`;
}

function modifierExpression(node, imports) {
  const calls = [];
  for (const modifier of node.modifiers) {
    const arguments_ = fieldMap(modifier.arguments);
    if (modifier.kind === 'size') {
      const width = arguments_.get('width');
      const height = arguments_.get('height');
      if (width?.kind === 'layout-dimension' && width.value === 'match-parent' &&
          height?.kind === 'layout-dimension' && height.value === 'match-parent') {
        imports.add('com.viewcompose.ui.modifier.fillMaxSize');
        calls.push('fillMaxSize()');
      } else {
        if (width?.kind === 'layout-dimension' && width.value === 'match-parent') {
          imports.add('com.viewcompose.ui.modifier.fillMaxWidth');
          calls.push('fillMaxWidth()');
        } else if (width?.kind === 'dimension') {
          imports.add('com.viewcompose.ui.modifier.width');
          imports.add('com.viewcompose.ui.unit.dp');
          calls.push(`width(${dimensionExpression(width)})`);
        }
        if (height?.kind === 'layout-dimension' && height.value === 'match-parent') {
          imports.add('com.viewcompose.ui.modifier.fillMaxHeight');
          calls.push('fillMaxHeight()');
        } else if (height?.kind === 'dimension') {
          imports.add('com.viewcompose.ui.modifier.height');
          imports.add('com.viewcompose.ui.unit.dp');
          calls.push(`height(${dimensionExpression(height)})`);
        }
      }
    } else if (modifier.kind === 'padding') {
      const all = arguments_.get('all');
      imports.add('com.viewcompose.ui.modifier.padding');
      imports.add('com.viewcompose.ui.unit.dp');
      calls.push(`padding(${dimensionExpression(all)})`);
    }
  }
  if (calls.length === 0) return null;
  imports.add('com.viewcompose.ui.modifier.Modifier');
  return `Modifier.${calls.join('.')}`;
}

function inputProfile(value) {
  return {
    text: 'Text',
    textEmailAddress: 'Email',
    textPassword: 'Password',
    number: 'Number',
  }[value?.value];
}

function nodeKey(node) {
  return node.id.startsWith('id:') ? node.id.slice(3) : node.id;
}

function emitCall(name, arguments_, indent, children, lines) {
  const prefix = ' '.repeat(indent);
  lines.push(`${prefix}${name}(`);
  for (const [argument, value] of arguments_) {
    lines.push(`${prefix}    ${argument} = ${value},`);
  }
  if (children) {
    lines.push(`${prefix}) {`);
    children();
    lines.push(`${prefix}}`);
  } else {
    lines.push(`${prefix})`);
  }
}

function emitNode(node, bindings, imports, indent, lines) {
  const properties = fieldMap(node.properties);
  const modifier = modifierExpression(node, imports);
  const common = [['key', kotlinString(nodeKey(node))]];
  if (modifier) common.push(['modifier', modifier]);
  if (node.kind === 'row' || node.kind === 'column') {
    const name = node.kind === 'row' ? 'Row' : 'Column';
    imports.add(`com.viewcompose.ui.foundation.${name}`);
    emitCall(name, common, indent, () => {
      for (const child of node.children) emitNode(child, bindings, imports, indent + 4, lines);
    }, lines);
  } else if (node.kind === 'text') {
    imports.add('com.viewcompose.ui.foundation.Text');
    const text = valueExpression(properties.get('text'), bindings) ?? kotlinString('');
    emitCall('Text', [['text', text], ...common], indent, null, lines);
  } else if (node.kind === 'text-field') {
    imports.add('com.viewcompose.ui.foundation.TextField');
    imports.add('com.viewcompose.ui.foundation.TextFieldInputProfile');
    const state = fieldMap(node.state).get('text');
    const stateParameter = bindings.stateByName.get(state.name).parameter;
    const arguments_ = [['state', stateParameter]];
    const hint = valueExpression(properties.get('hint'), bindings);
    if (hint) arguments_.push(['placeholder', hint]);
    const profile = inputProfile(properties.get('inputType'));
    if (profile) arguments_.push(['inputProfile', `TextFieldInputProfile.${profile}`]);
    emitCall('TextField', [...arguments_, ...common], indent, null, lines);
  } else {
    imports.add('com.viewcompose.ui.foundation.Button');
    const text = valueExpression(properties.get('text'), bindings) ?? kotlinString('');
    emitCall('Button', [['text', text], ...common], indent, null, lines);
  }
}

export async function generateViewComposeKotlin(ir) {
  const schema = await loadSchema();
  const violations = validateSchemaValue(ir, schema);
  if (violations.length > 0) {
    return {
      status: 'invalid',
      diagnostics: [generatorDiagnostic(
        'VC-AI-IR-INVALID',
        `Design IR violates schema v1: ${violations.slice(0, 3).join('; ')}`,
        'Provide schema-valid Design IR v1 before requesting Kotlin generation.',
      )],
    };
  }
  const diagnostics = validateSupportedIr(ir);
  if (diagnostics.length > 0) return {status: 'unsupported', diagnostics};

  const bindings = collectBindings(ir);
  const imports = new Set([
    'com.viewcompose.ui.foundation.UiTreeBuilder',
  ]);
  if (bindings.states.length > 0) imports.add('com.viewcompose.text.TextFieldState');
  const functionName = `${upperCamel(ir.documentId)}View`;
  const body = [];
  emitNode(ir.roots[0], bindings, imports, 4, body);
  const parameters = [...bindings.resources, ...bindings.states]
    .map((binding) => `    ${binding.parameter}: ${binding.type},`);
  const kotlin = [
    `package ${packageName}`,
    '',
    ...[...imports].sort().map((name) => `import ${name}`),
    '',
    `fun UiTreeBuilder.${functionName}(`,
    ...parameters,
    ') {',
    ...body,
    '}',
    '',
  ].join('\n');
  const report = {
    schemaVersion: 1,
    source: ir.source,
    target: {
      language: 'kotlin',
      packageName,
      functionName,
      artifactIds: ['viewcompose-ui-foundation'],
    },
    bindings: {
      resources: bindings.resources,
      states: bindings.states,
    },
    preservedIds: bindings.ids,
    callSiteReview: {
      required: true,
      items: [
        'Resolve every string parameter from its recorded Android resource at the ViewCompose host boundary.',
        'Retain caller ownership and restoration policy for every TextFieldState parameter.',
        'Review ViewBinding references, listeners, adapters, and imperative mutations outside the XML input.',
      ],
    },
    verification: {
      generation: 'deterministic',
      compilation: 'required',
    },
  };
  return {
    status: 'success',
    diagnostics: [],
    kotlin,
    report,
    outputFingerprint: createHash('sha256').update(kotlin).digest('hex'),
  };
}
