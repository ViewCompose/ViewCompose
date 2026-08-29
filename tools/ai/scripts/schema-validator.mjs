function typeMatches(value, expected) {
  switch (expected) {
    case 'object':
      return value !== null && typeof value === 'object' && !Array.isArray(value);
    case 'array':
      return Array.isArray(value);
    case 'integer':
      return Number.isInteger(value);
    case 'number':
      return typeof value === 'number' && Number.isFinite(value);
    case 'string':
      return typeof value === 'string';
    case 'boolean':
      return typeof value === 'boolean';
    case 'null':
      return value === null;
    default:
      throw new Error(`Unsupported JSON Schema type in Phase 0 validator: ${expected}`);
  }
}

function pointerValue(rootSchema, reference) {
  if (!reference.startsWith('#/')) {
    throw new Error(`Only local JSON Schema references are supported: ${reference}`);
  }
  return reference
    .slice(2)
    .split('/')
    .map((segment) => segment.replaceAll('~1', '/').replaceAll('~0', '~'))
    .reduce((current, segment) => current?.[segment], rootSchema);
}

function sameJson(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

export function validateSchemaValue(value, schema, rootSchema = schema, path = '$') {
  if (schema === true || Object.keys(schema).length === 0) return [];
  if (schema === false) return [`${path}: schema rejects every value`];
  if (schema.$ref) {
    const target = pointerValue(rootSchema, schema.$ref);
    if (!target) return [`${path}: unresolved schema reference ${schema.$ref}`];
    return validateSchemaValue(value, target, rootSchema, path);
  }
  if (schema.oneOf) {
    const candidates = schema.oneOf.map((candidate) =>
      validateSchemaValue(value, candidate, rootSchema, path),
    );
    const matches = candidates.filter((violations) => violations.length === 0);
    return matches.length === 1
      ? []
      : [`${path}: expected exactly one oneOf match, found ${matches.length}`];
  }

  const violations = [];
  if (schema.type && !typeMatches(value, schema.type)) {
    return [`${path}: expected ${schema.type}, found ${Array.isArray(value) ? 'array' : typeof value}`];
  }
  if (Object.hasOwn(schema, 'const') && !sameJson(value, schema.const)) {
    violations.push(`${path}: expected constant ${JSON.stringify(schema.const)}`);
  }
  if (schema.enum && !schema.enum.some((candidate) => sameJson(value, candidate))) {
    violations.push(`${path}: value is outside the accepted enum`);
  }

  if (typeof value === 'string') {
    if (schema.minLength !== undefined && value.length < schema.minLength) {
      violations.push(`${path}: string is shorter than ${schema.minLength}`);
    }
    if (schema.maxLength !== undefined && value.length > schema.maxLength) {
      violations.push(`${path}: string is longer than ${schema.maxLength}`);
    }
    if (schema.pattern && !new RegExp(schema.pattern, 'u').test(value)) {
      violations.push(`${path}: string does not match ${schema.pattern}`);
    }
  }

  if (typeof value === 'number') {
    if (schema.minimum !== undefined && value < schema.minimum) {
      violations.push(`${path}: number is below ${schema.minimum}`);
    }
    if (schema.maximum !== undefined && value > schema.maximum) {
      violations.push(`${path}: number is above ${schema.maximum}`);
    }
  }

  if (Array.isArray(value)) {
    if (schema.minItems !== undefined && value.length < schema.minItems) {
      violations.push(`${path}: array has fewer than ${schema.minItems} items`);
    }
    if (schema.uniqueItems) {
      const encoded = value.map((item) => JSON.stringify(item));
      if (new Set(encoded).size !== encoded.length) {
        violations.push(`${path}: array items must be unique`);
      }
    }
    if (schema.items) {
      value.forEach((item, index) => {
        violations.push(...validateSchemaValue(item, schema.items, rootSchema, `${path}[${index}]`));
      });
    }
  }

  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    for (const required of schema.required ?? []) {
      if (!Object.hasOwn(value, required)) violations.push(`${path}: missing required property ${required}`);
    }
    for (const [key, child] of Object.entries(value)) {
      const propertySchema = schema.properties?.[key];
      if (propertySchema) {
        violations.push(
          ...validateSchemaValue(child, propertySchema, rootSchema, `${path}.${key}`),
        );
      } else if (schema.additionalProperties === false) {
        violations.push(`${path}: unexpected property ${key}`);
      }
    }
  }
  return violations;
}

export function assertSchemaValue(value, schema, label) {
  const violations = validateSchemaValue(value, schema);
  if (violations.length > 0) {
    throw new Error(`${label} violates its JSON Schema:\n${violations.join('\n')}`);
  }
}
