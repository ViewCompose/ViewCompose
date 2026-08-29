import {readFile} from 'node:fs/promises';
import {validateSchemaValue} from './schema-validator.mjs';
import {
  fingerprintRepairValue,
  validateRepairPatch,
} from './repair-orchestrator.mjs';

const designIrSchemaPath = new URL('../contracts/design-ir.schema.json', import.meta.url);
const MAX_NODES = 1000;
const MAX_DEPTH = 64;

let designIrSchemaPromise;

function loadDesignIrSchema() {
  designIrSchemaPromise ??= readFile(designIrSchemaPath, 'utf8').then(JSON.parse);
  return designIrSchemaPromise;
}

export class DesignIrRepairPatchError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'DesignIrRepairPatchError';
    this.code = code;
  }
}

function fail(code, message) {
  throw new DesignIrRepairPatchError(code, message);
}

function throwIfCancelled(signal) {
  if (signal?.aborted) {
    fail('VC-AI-REPAIR-CANCELLED', 'Typed Design IR patch application was cancelled.');
  }
}

function collectValueExpressions(value, label) {
  if (value?.kind === 'expression') {
    fail('VC-AI-REPAIR-INPUT-INVALID', `${label} contains executable expression content.`);
  }
}

function indexDesignIr(designIr, signal) {
  const nodeById = new Map();
  const visit = (node, depth) => {
    throwIfCancelled(signal);
    if (depth > MAX_DEPTH || nodeById.size >= MAX_NODES) {
      fail('VC-AI-REPAIR-INPUT-INVALID', 'Design IR exceeds the repair node or depth ceiling.');
    }
    if (nodeById.has(node.id)) {
      fail('VC-AI-REPAIR-INPUT-INVALID', `Design IR repeats node id ${node.id}.`);
    }
    nodeById.set(node.id, node);
    for (const [collection, fields] of [
      ['properties', node.properties],
      ['semantics', node.semantics],
      ['state', node.state],
    ]) {
      const names = fields.map((field) => field.name);
      if (new Set(names).size !== names.length) {
        fail(
          'VC-AI-REPAIR-INPUT-INVALID',
          `${node.id}.${collection} contains duplicate field names.`,
        );
      }
      for (const field of fields) {
        collectValueExpressions(field.value, `${node.id}.${collection}.${field.name}`);
      }
    }
    for (const [modifierIndex, modifier] of node.modifiers.entries()) {
      const names = modifier.arguments.map((field) => field.name);
      if (new Set(names).size !== names.length) {
        fail(
          'VC-AI-REPAIR-INPUT-INVALID',
          `${node.id}.modifiers[${modifierIndex}] contains duplicate argument names.`,
        );
      }
      for (const field of modifier.arguments) {
        collectValueExpressions(
          field.value,
          `${node.id}.modifiers[${modifierIndex}].${field.name}`,
        );
      }
    }
    for (const child of node.children) visit(child, depth + 1);
  };
  for (const root of designIr.roots) visit(root, 1);
  return nodeById;
}

function replaceField(node, operation) {
  const fields = node[operation.collection];
  const index = fields.findIndex((field) => field.name === operation.name);
  if (index < 0) {
    fail(
      'VC-AI-REPAIR-INPUT-INVALID',
      `${node.id}.${operation.collection}.${operation.name} does not exist.`,
    );
  }
  if (fingerprintRepairValue(fields[index].value) === fingerprintRepairValue(operation.value)) {
    fail(
      'VC-AI-REPAIR-NO-ELIGIBLE-CHANGE',
      `${node.id}.${operation.collection}.${operation.name} already has the proposed value.`,
    );
  }
  fields[index] = {name: operation.name, value: structuredClone(operation.value)};
  return `${node.id}.${operation.collection}.${operation.name}`;
}

function replaceModifierArgument(node, operation) {
  const modifier = node.modifiers[operation.modifierIndex];
  if (!modifier) {
    fail(
      'VC-AI-REPAIR-INPUT-INVALID',
      `${node.id}.modifiers[${operation.modifierIndex}] does not exist.`,
    );
  }
  const index = modifier.arguments.findIndex((field) => field.name === operation.name);
  if (index < 0) {
    fail(
      'VC-AI-REPAIR-INPUT-INVALID',
      `${node.id}.modifiers[${operation.modifierIndex}].${operation.name} does not exist.`,
    );
  }
  if (
    fingerprintRepairValue(modifier.arguments[index].value) ===
      fingerprintRepairValue(operation.value)
  ) {
    fail(
      'VC-AI-REPAIR-NO-ELIGIBLE-CHANGE',
      `${node.id}.modifiers[${operation.modifierIndex}].${operation.name} is unchanged.`,
    );
  }
  modifier.arguments[index] = {name: operation.name, value: structuredClone(operation.value)};
  return `${node.id}.modifiers[${operation.modifierIndex}].${operation.name}`;
}

function replaceNodeKind(node, operation) {
  if (node.kind === operation.kind) {
    fail('VC-AI-REPAIR-NO-ELIGIBLE-CHANGE', `${node.id}.kind is already ${operation.kind}.`);
  }
  node.kind = operation.kind;
  return `${node.id}.kind`;
}

function reorderChildren(node, operation) {
  const currentIds = node.children.map((child) => child.id);
  if (
    currentIds.length !== operation.orderedChildIds.length ||
    currentIds.some((id) => !operation.orderedChildIds.includes(id))
  ) {
    fail(
      'VC-AI-REPAIR-INPUT-INVALID',
      `${node.id}.children repair must be an exact permutation of existing child ids.`,
    );
  }
  if (JSON.stringify(currentIds) === JSON.stringify(operation.orderedChildIds)) {
    fail('VC-AI-REPAIR-NO-ELIGIBLE-CHANGE', `${node.id}.children order is unchanged.`);
  }
  const childById = new Map(node.children.map((child) => [child.id, child]));
  node.children = operation.orderedChildIds.map((id) => childById.get(id));
  return `${node.id}.children`;
}

export async function applyDesignIrRepairPatch({
  designIr,
  expectedDesignIrFingerprint,
  patch,
} = {}, {signal} = {}) {
  throwIfCancelled(signal);
  const schema = await loadDesignIrSchema();
  const violations = validateSchemaValue(designIr, schema);
  if (
    violations.length > 0 ||
    designIr?.source?.kind !== 'screenshot' ||
    designIr?.unsupported?.length !== 0 ||
    fingerprintRepairValue(designIr) !== expectedDesignIrFingerprint ||
    !await validateRepairPatch(patch)
  ) {
    fail(
      'VC-AI-REPAIR-INPUT-INVALID',
      'Typed repair requires one exact resolved screenshot Design IR and one valid immutable patch.',
    );
  }
  const candidate = structuredClone(designIr);
  const nodeById = indexDesignIr(candidate, signal);
  const changedPaths = [];
  for (const operation of patch.operations) {
    throwIfCancelled(signal);
    const node = nodeById.get(operation.nodeId);
    if (!node) {
      fail('VC-AI-REPAIR-INPUT-INVALID', `Repair target node ${operation.nodeId} does not exist.`);
    }
    const path = {
      'replace-field': replaceField,
      'replace-modifier-argument': replaceModifierArgument,
      'replace-node-kind': replaceNodeKind,
      'reorder-children': reorderChildren,
    }[operation.op](node, operation);
    changedPaths.push(path);
  }
  const outputViolations = validateSchemaValue(candidate, schema);
  if (outputViolations.length > 0) {
    fail(
      'VC-AI-REPAIR-INPUT-INVALID',
      `Patched Design IR violates v1: ${outputViolations.slice(0, 3).join('; ')}`,
    );
  }
  const designIrFingerprint = fingerprintRepairValue(candidate);
  if (designIrFingerprint === expectedDesignIrFingerprint) {
    fail('VC-AI-REPAIR-NO-ELIGIBLE-CHANGE', 'Typed patch did not change the Design IR identity.');
  }
  return {
    schemaVersion: 1,
    designIr: candidate,
    inputDesignIrFingerprint: expectedDesignIrFingerprint,
    designIrFingerprint,
    changeFingerprint: patch.changeFingerprint,
    operationCount: patch.operations.length,
    changedPaths,
    outputFingerprint: fingerprintRepairValue({
      designIrFingerprint,
      changeFingerprint: patch.changeFingerprint,
      changedPaths,
    }),
  };
}
