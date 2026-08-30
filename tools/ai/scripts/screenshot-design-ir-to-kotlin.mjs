import {createHash} from 'node:crypto';
import {canonicalJson} from './screenshot-contract.mjs';
import {
  DESIGN_IR_SCHEMA,
  SCREENSHOT_GENERATION_ARGUMENTS_SCHEMA,
  SCREENSHOT_GENERATION_REPORT_SCHEMA,
  SCREENSHOT_GENERATION_REQUEST_SCHEMA,
  SCREENSHOT_RESOLUTION_RESULT_SCHEMA,
} from './screenshot-generation-contract.mjs';
import {validateSchemaValue} from './schema-validator.mjs';

const packageName = 'generated.viewcompose';
const kotlinKeywords = new Set([
  'as', 'break', 'class', 'continue', 'do', 'else', 'false', 'for', 'fun', 'if', 'in',
  'interface', 'is', 'null', 'object', 'package', 'return', 'super', 'this', 'throw',
  'true', 'try', 'typealias', 'typeof', 'val', 'var', 'when', 'while',
]);
const supportedKinds = new Set(['button', 'column', 'text', 'text-field']);
const eventTypes = Object.freeze({
  click: '() -> Unit',
  'focus-change': '(Boolean) -> Unit',
  'keyboard-action': '(TextFieldImeAction) -> Boolean',
});

class ScreenshotGenerationError extends Error {
  constructor(code, message, nextAction, status = 'invalid') {
    super(message);
    this.code = code;
    this.nextAction = nextAction;
    this.status = status;
  }
}

function fail(code, message, nextAction, status) {
  throw new ScreenshotGenerationError(code, message, nextAction, status);
}

function fingerprintJson(value, omittedKey) {
  const copy = structuredClone(value);
  if (omittedKey) delete copy[omittedKey];
  return createHash('sha256').update(canonicalJson(copy)).digest('hex');
}

function fingerprintBytes(value) {
  return createHash('sha256').update(value).digest('hex');
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
  if (!result) result = 'binding';
  if (!/^[A-Za-z_]/u.test(result)) result = `binding${result}`;
  if (kotlinKeywords.has(result)) result = `${result}Value`;
  return result;
}

function upperCamel(value) {
  const result = words(value).map((part) => `${part[0].toUpperCase()}${part.slice(1)}`).join('');
  return /^[A-Za-z_]/u.test(result) ? result : `Generated${result || 'Screenshot'}`;
}

function kotlinString(value) {
  return JSON.stringify(value).replaceAll('$', '\\$');
}

function fieldMap(fields) {
  return new Map(fields.map((field) => [field.name, field.value]));
}

function collectNodes(roots) {
  const nodes = [];
  const visit = (node) => {
    nodes.push(node);
    for (const child of node.children) visit(child);
  };
  for (const root of roots) visit(root);
  return nodes;
}

function allocateIdentifier(source, used) {
  const base = lowerCamel(source);
  let candidate = base;
  let suffix = 2;
  while (used.has(candidate)) {
    candidate = `${base}${suffix}`;
    suffix += 1;
  }
  used.add(candidate);
  return candidate;
}

function assertExactFields(node, actual, expected, label) {
  const names = actual.map((field) => field.name).sort();
  if (JSON.stringify(names) !== JSON.stringify([...expected].sort())) {
    fail(
      'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
      `Node ${node.id} has ${label} outside the screenshot v1 mapping.`,
      'Resolve the node to the exact supported property, state, event, and accessibility fields.',
      'unsupported',
    );
  }
}

function validateNodeMapping(nodes) {
  for (const node of nodes) {
    if (!supportedKinds.has(node.kind)) {
      fail(
        'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
        `Node ${node.id} has unsupported kind ${node.kind}.`,
        'Use only the frozen Column, Text, TextField, and Button screenshot subset.',
        'unsupported',
      );
    }
    if (node.modifiers.length > 0) {
      fail(
        'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
        `Node ${node.id} contains modifiers outside the screenshot v1 mapping.`,
        'Resolve geometry through a later bounded mapping contract before generation.',
        'unsupported',
      );
    }
    const properties = fieldMap(node.properties);
    if (node.kind === 'column') {
      assertExactFields(node, node.properties, ['orientation'], 'properties');
      const orientation = properties.get('orientation');
      if (
        orientation?.kind !== 'enum' ||
        orientation.type !== 'axis' ||
        orientation.value !== 'vertical'
      ) {
        fail(
          'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
          `Column ${node.id} does not use the normalized vertical screenshot axis.`,
          'Resolve the container to the frozen vertical Column mapping.',
          'unsupported',
        );
      }
      assertExactFields(node, node.state, [], 'state');
      if (node.events.length > 0) {
        fail(
          'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
          `Column ${node.id} contains unsupported behavior.`,
          'Move behavior to a supported resolved component binding.',
          'unsupported',
        );
      }
    } else if (node.kind === 'text') {
      assertExactFields(node, node.properties, ['text'], 'properties');
      const text = properties.get('text');
      if (text?.kind !== 'literal' || typeof text.value !== 'string' || text.value.length === 0) {
        fail(
          'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
          `Text ${node.id} requires one non-empty resolved literal.`,
          'Resolve the visible text without expressions or resources.',
          'unsupported',
        );
      }
      assertExactFields(node, node.state, [], 'state');
      if (node.events.length > 0) {
        fail(
          'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
          `Text ${node.id} contains unsupported behavior.`,
          'Use a supported Button for click behavior.',
          'unsupported',
        );
      }
    } else if (node.kind === 'text-field') {
      assertExactFields(node, node.properties, ['hint', 'inputType'], 'properties');
      const hint = properties.get('hint');
      const inputType = properties.get('inputType');
      if (
        hint?.kind !== 'literal' ||
        typeof hint.value !== 'string' ||
        hint.value.length === 0 ||
        inputType?.kind !== 'enum' ||
        inputType.type !== 'android-input-type' ||
        !['number', 'text', 'textEmailAddress', 'textPassword'].includes(inputType.value)
      ) {
        fail(
          'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
          `Text field ${node.id} has unsupported content or input purpose.`,
          'Resolve a literal hint and one frozen TextField input profile.',
          'unsupported',
        );
      }
      assertExactFields(node, node.state, ['text'], 'state');
      const textState = fieldMap(node.state).get('text');
      if (textState?.kind !== 'binding' || textState.status !== 'resolved') {
        fail(
          'VC-AI-SCREENSHOT-GENERATION-NOT-ELIGIBLE',
          `Text field ${node.id} lacks a resolved caller-owned TextFieldState binding.`,
          'Complete typed resolution before requesting code generation.',
        );
      }
      if (
        node.events.length > 2 ||
        new Set(node.events.map((event) => event.kind)).size !== node.events.length ||
        node.events.some((event) =>
          !['focus-change', 'keyboard-action'].includes(event.kind) ||
          event.status !== 'resolved')
      ) {
        fail(
          'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
          `Text field ${node.id} has an unsupported or unresolved event binding.`,
          'Use only resolved focus-change and keyboard-action bindings.',
          'unsupported',
        );
      }
    } else {
      assertExactFields(node, node.properties, ['text'], 'properties');
      const text = properties.get('text');
      if (text?.kind !== 'literal' || typeof text.value !== 'string' || text.value.length === 0) {
        fail(
          'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
          `Button ${node.id} requires one non-empty resolved literal.`,
          'Resolve the visible button text before generation.',
          'unsupported',
        );
      }
      assertExactFields(node, node.state, [], 'state');
      if (
        node.events.length !== 1 ||
        node.events[0].kind !== 'click' ||
        node.events[0].status !== 'resolved'
      ) {
        fail(
          'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
          `Button ${node.id} requires exactly one resolved click binding.`,
          'Resolve one caller-owned click binding before generation.',
          'unsupported',
        );
      }
    }
    if (node.kind !== 'column' && node.children.length > 0) {
      fail(
        'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
        `Leaf node ${node.id} contains children.`,
        'Use the frozen component hierarchy without leaf children.',
        'unsupported',
      );
    }
    const semanticNames = node.kind === 'button' || node.kind === 'text-field'
      ? ['accessibilityLabelSource', 'decorative', 'role', 'traversalIndex']
      : ['accessibilityLabelSource', 'decorative', 'traversalIndex'];
    assertExactFields(node, node.semantics, semanticNames, 'semantics');
    const semantics = fieldMap(node.semantics);
    const expectedRole = node.kind === 'button' ? 'button' :
      node.kind === 'text-field' ? 'text-field' : undefined;
    const role = semantics.get('role');
    const expectedLabel = node.kind === 'text-field' ? 'field-label' :
      ['button', 'text'].includes(node.kind) ? 'visible-text' : 'none';
    if (
      (expectedRole === undefined ? role !== undefined :
        role?.kind !== 'enum' || role.type !== 'semantic-role' || role.value !== expectedRole) ||
      semantics.get('accessibilityLabelSource')?.kind !== 'enum' ||
      semantics.get('accessibilityLabelSource')?.type !== 'accessibility-label-source' ||
      semantics.get('accessibilityLabelSource')?.value !== expectedLabel ||
      semantics.get('traversalIndex')?.kind !== 'literal' ||
      !Number.isInteger(semantics.get('traversalIndex')?.value) ||
      semantics.get('decorative')?.kind !== 'literal' ||
      semantics.get('decorative')?.value !== false
    ) {
      fail(
        'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
        `Node ${node.id} has accessibility decisions outside the screenshot v1 mapping.`,
        'Resolve role, label source, traversal, and decorative status for the exact component kind.',
        'unsupported',
      );
    }
  }
  const traversal = nodes.map((node) => fieldMap(node.semantics).get('traversalIndex').value);
  if (traversal.some((value, index) => value !== index)) {
    fail(
      'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
      'Resolved accessibility traversal does not equal generated hierarchy order.',
      'Use contiguous traversal indices in the same pre-order as the resolved hierarchy.',
      'unsupported',
    );
  }
}

function collectBindings(nodes) {
  const used = new Set();
  const stateBySource = new Map();
  const eventByIdentity = new Map();
  for (const node of nodes) {
    if (node.kind !== 'text-field') continue;
    const value = fieldMap(node.state).get('text');
    let binding = stateBySource.get(value.name);
    if (!binding) {
      binding = {
        source: value.name,
        parameter: allocateIdentifier(value.name, used),
        type: 'TextFieldState',
        status: 'resolved',
        nodeIds: [],
      };
      stateBySource.set(value.name, binding);
    }
    binding.nodeIds.push(node.id);
  }
  for (const node of nodes) {
    for (const event of node.events) {
      const type = eventTypes[event.kind];
      const identity = `${event.binding}:${event.kind}`;
      const incompatible = [...eventByIdentity.values()].find((binding) =>
        binding.source === event.binding && binding.type !== type);
      if (incompatible) {
        fail(
          'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
          `Callback ${event.binding} is assigned incompatible event types.`,
          'Use distinct caller-owned callback bindings for different signatures.',
          'unsupported',
        );
      }
      let binding = eventByIdentity.get(identity);
      if (!binding) {
        binding = {
          source: event.binding,
          parameter: allocateIdentifier(event.binding, used),
          event: event.kind,
          type,
          status: 'resolved',
          nodeIds: [],
        };
        eventByIdentity.set(identity, binding);
      }
      binding.nodeIds.push(node.id);
    }
  }
  return {
    states: [...stateBySource.values()],
    events: [...eventByIdentity.values()],
    stateBySource,
    eventByIdentity,
  };
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

function inputProfile(value) {
  return {
    text: 'Text',
    textEmailAddress: 'Email',
    textPassword: 'Password',
    number: 'Number',
  }[value];
}

function emitNode(node, bindings, indent, lines) {
  const properties = fieldMap(node.properties);
  const common = [['key', kotlinString(node.id)]];
  if (node.kind === 'column') {
    emitCall('Column', common, indent, () => {
      for (const child of node.children) emitNode(child, bindings, indent + 4, lines);
    }, lines);
  } else if (node.kind === 'text') {
    emitCall('Text', [
      ['text', kotlinString(properties.get('text').value)],
      ...common,
    ], indent, null, lines);
  } else if (node.kind === 'text-field') {
    const state = fieldMap(node.state).get('text');
    const arguments_ = [
      ['state', bindings.stateBySource.get(state.name).parameter],
      ['placeholder', kotlinString(properties.get('hint').value)],
      ['inputProfile', `TextFieldInputProfile.${inputProfile(properties.get('inputType').value)}`],
    ];
    for (const event of node.events) {
      const argument = event.kind === 'keyboard-action' ? 'onKeyboardAction' : 'onFocusChange';
      arguments_.push([
        argument,
        bindings.eventByIdentity.get(`${event.binding}:${event.kind}`).parameter,
      ]);
    }
    emitCall('TextField', [...arguments_, ...common], indent, null, lines);
  } else {
    const event = node.events[0];
    emitCall('Button', [
      ['text', kotlinString(properties.get('text').value)],
      ['onClick', bindings.eventByIdentity.get(`${event.binding}:${event.kind}`).parameter],
      ...common,
    ], indent, null, lines);
  }
}

function accessibilityRecord(node) {
  const semantics = fieldMap(node.semantics);
  const role = semantics.get('role')?.value ?? 'none';
  const labelSource = semantics.get('accessibilityLabelSource').value;
  return {
    nodeId: node.id,
    role,
    labelSource,
    traversalIndex: semantics.get('traversalIndex').value,
    decorative: semantics.get('decorative').value,
    emission: {
      role: role === 'none' ? 'not-applicable' : 'component-default',
      label: labelSource === 'field-label' ? 'component-placeholder' : labelSource,
      traversal: 'hierarchy-order',
      decorative: semantics.get('decorative').value ? 'hidden' : 'default-visible',
    },
  };
}

function generationFailure(error) {
  const known = error instanceof ScreenshotGenerationError;
  return {
    status: known ? error.status : 'failed',
    diagnostics: [{
      code: known ? error.code : 'VC-AI-SCREENSHOT-GENERATION-INPUT-INVALID',
      severity: 'error',
      message: known
        ? error.message
        : 'Screenshot Kotlin generation failed before deterministic source was accepted.',
      nextAction: known
        ? error.nextAction
        : 'Use one exact resolved screenshot result and the frozen generation request.',
    }],
  };
}

export async function generateScreenshotKotlin(arguments_) {
  try {
    const argumentViolations = validateSchemaValue(arguments_, SCREENSHOT_GENERATION_ARGUMENTS_SCHEMA);
    if (argumentViolations.length > 0) {
      fail(
        'VC-AI-SCREENSHOT-GENERATION-INPUT-INVALID',
        `Screenshot generation arguments violate v1: ${argumentViolations.slice(0, 3).join('; ')}`,
        'Use one resolved screenshot result and one exact generation request.',
      );
    }
    const {resolutionResult: resolution, generationRequest: request} = arguments_;
    const requestViolations = validateSchemaValue(request, SCREENSHOT_GENERATION_REQUEST_SCHEMA);
    if (requestViolations.length > 0) {
      fail(
        'VC-AI-SCREENSHOT-GENERATION-INPUT-INVALID',
        `Screenshot generation request violates v1: ${requestViolations.slice(0, 3).join('; ')}`,
        'Use the frozen generate or compile request without executable fields.',
      );
    }
    if (
      resolution.status !== 'resolved' ||
      resolution.summary?.codeGenerationAllowed !== true ||
      resolution.summary?.remainingQuestions !== 0 ||
      resolution.summary?.remainingUnsupportedSemantics !== 0 ||
      resolution.summary?.placeholderBindings !== 0
    ) {
      fail(
        'VC-AI-SCREENSHOT-GENERATION-NOT-ELIGIBLE',
        'Screenshot resolution has not passed every mechanical code-generation eligibility gate.',
        'Complete typed resolution until all questions, unsupported semantics, and placeholders reach zero.',
      );
    }
    const resolutionViolations = validateSchemaValue(resolution, SCREENSHOT_RESOLUTION_RESULT_SCHEMA);
    if (resolutionViolations.length > 0) {
      const unsupportedEvent = collectNodes(resolution.designIr?.roots ?? []).some((node) =>
        node.events?.some((event) => !Object.hasOwn(eventTypes, event.kind)));
      fail(
        unsupportedEvent
          ? 'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED'
          : 'VC-AI-SCREENSHOT-GENERATION-INPUT-INVALID',
        unsupportedEvent
          ? 'Screenshot resolution contains an event outside the frozen generator mapping.'
          : `Screenshot resolution result violates v1: ${resolutionViolations.slice(0, 3).join('; ')}`,
        unsupportedEvent
          ? 'Use only resolved click, focus-change, and keyboard-action bindings.'
          : 'Pass the unchanged result returned by resolve_screenshot_inference.',
        unsupportedEvent ? 'unsupported' : 'invalid',
      );
    }
    const designIrViolations = validateSchemaValue(resolution.designIr, DESIGN_IR_SCHEMA);
    if (designIrViolations.length > 0) {
      fail(
        'VC-AI-SCREENSHOT-GENERATION-INPUT-INVALID',
        `Resolved Design IR violates v1: ${designIrViolations.slice(0, 3).join('; ')}`,
        'Pass the unchanged resolved Design IR.',
      );
    }
    const resolutionFingerprint = fingerprintJson(resolution, 'resultFingerprint');
    const designIrFingerprint = fingerprintJson(resolution.designIr);
    if (
      resolution.resultFingerprint !== resolutionFingerprint ||
      resolution.designIrFingerprint !== designIrFingerprint ||
      request.input.resolutionResultFingerprint !== resolutionFingerprint ||
      request.input.resolvedDesignIrFingerprint !== designIrFingerprint
    ) {
      fail(
        'VC-AI-SCREENSHOT-GENERATION-LINEAGE-MISMATCH',
        'Generation request, resolution result, and resolved Design IR do not share one exact lineage.',
        'Pass the unchanged resolution result and bind the request to its exact fingerprints.',
      );
    }
    if (
      resolution.designIr.source.kind !== 'screenshot' ||
      resolution.designIr.roots.length !== 1 ||
      resolution.designIr.unsupported.length !== 0
    ) {
      fail(
        'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
        'The screenshot generator requires one supported screenshot root with no blocked semantics.',
        'Use the exact resolved screenshot subset before generation.',
        'unsupported',
      );
    }
    const nodes = collectNodes(resolution.designIr.roots);
    if (nodes.length === 0 || nodes.length > 1000 || new Set(nodes.map((node) => node.id)).size !== nodes.length) {
      fail(
        'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
        'The resolved screenshot node inventory is empty, duplicated, or exceeds the 1000-node bound.',
        'Use one bounded resolved hierarchy with unique node IDs.',
        'unsupported',
      );
    }
    validateNodeMapping(nodes);
    const bindings = collectBindings(nodes);
    if (bindings.states.length + bindings.events.length > 1000) {
      fail(
        'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
        'The resolved screenshot exceeds the 1000-binding limit.',
        'Split the screen into bounded generated functions.',
        'limited',
      );
    }
    const imports = [
      'com.viewcompose.text.TextFieldState',
      'com.viewcompose.ui.foundation.Button',
      'com.viewcompose.ui.foundation.Column',
      'com.viewcompose.ui.foundation.Text',
      'com.viewcompose.ui.foundation.TextField',
      'com.viewcompose.ui.foundation.TextFieldInputProfile',
      'com.viewcompose.ui.foundation.UiTreeBuilder',
    ];
    if (bindings.events.some((binding) => binding.event === 'keyboard-action')) {
      imports.push('com.viewcompose.ui.node.TextFieldImeAction');
    }
    const functionName = `${upperCamel(resolution.designIr.documentId)}View`;
    const body = [];
    emitNode(resolution.designIr.roots[0], bindings, 4, body);
    const parameters = [...bindings.states, ...bindings.events]
      .map((binding) => `    ${binding.parameter}: ${binding.type},`);
    const kotlin = [
      `package ${packageName}`,
      '',
      ...imports.sort().map((name) => `import ${name}`),
      '',
      `fun UiTreeBuilder.${functionName}(`,
      ...parameters,
      ') {',
      ...body,
      '}',
      '',
    ].join('\n');
    if (Buffer.byteLength(kotlin) > 262144) {
      fail(
        'VC-AI-SCREENSHOT-GENERATION-UNSUPPORTED',
        'Generated Kotlin exceeds the 262144-byte source limit.',
        'Split the screen into smaller resolved inputs.',
        'limited',
      );
    }
    const requestFingerprint = fingerprintJson(request);
    const kotlinFingerprint = fingerprintBytes(kotlin);
    const reviewReceipt = resolution.resolutionRecords.find((record) =>
      record.decisionKind === 'accessibility-review')?.reviewReceipt;
    if (!reviewReceipt) {
      fail(
        'VC-AI-SCREENSHOT-GENERATION-NOT-ELIGIBLE',
        'Resolved accessibility decisions have no immutable review receipt.',
        'Repeat typed accessibility resolution and preserve its review record.',
      );
    }
    const report = {
      schemaVersion: 1,
      kind: 'report',
      requestFingerprint,
      input: structuredClone(request.input),
      target: {
        language: 'kotlin',
        packageName,
        functionName,
        artifactIds: ['viewcompose-ui-foundation'],
        capabilityIds: ['foundation.components'],
      },
      bindings: {
        states: bindings.states,
        events: bindings.events,
      },
      accessibility: {
        reviewReceipt,
        nodes: nodes.map(accessibilityRecord),
        traversal: {
          mode: 'hierarchy-order',
          explicitModifier: false,
          orderedNodeIds: nodes.map((node) => node.id),
          reviewRequired: true,
        },
      },
      callSiteReview: {
        required: true,
        items: [
          'Retain caller ownership and restoration policy for every TextFieldState parameter.',
          'Keep keyboard, focus, and click callbacks synchronous with their documented renderer-thread contracts.',
          'Verify structural accessibility traversal and labels on the target Android API levels before integration.',
          'Render and compare the generated screen before claiming screenshot visual parity.',
        ],
      },
      verification: {
        generation: 'deterministic',
        compilation: 'required',
        rendering: 'not-claimed',
        visualComparison: 'not-claimed',
      },
      kotlinFingerprint,
    };
    report.reportFingerprint = fingerprintJson(report);
    const reportViolations = validateSchemaValue(report, SCREENSHOT_GENERATION_REPORT_SCHEMA);
    if (reportViolations.length > 0) {
      fail(
        'VC-AI-SCREENSHOT-GENERATION-INPUT-INVALID',
        `Generated report violates v1: ${reportViolations.slice(0, 3).join('; ')}`,
        'Correct the deterministic generator implementation before accepting output.',
      );
    }
    return {
      status: 'success',
      diagnostics: [],
      kotlin,
      report,
      outputFingerprint: kotlinFingerprint,
    };
  } catch (error) {
    return generationFailure(error);
  }
}
