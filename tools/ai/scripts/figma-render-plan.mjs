import {createHash} from 'node:crypto';
import {canonicalJson} from './screenshot-contract.mjs';

const PACKAGE_NAME = 'generated.viewcompose';
const KOTLIN_KEYWORDS = new Set([
  'as', 'break', 'class', 'continue', 'do', 'else', 'false', 'for', 'fun', 'if', 'in',
  'interface', 'is', 'null', 'object', 'return', 'super', 'this', 'throw', 'true', 'try',
  'typealias', 'typeof', 'val', 'var', 'when', 'while',
]);

export class FigmaRenderPlanError extends Error {
  constructor(code, message, nextAction, status = 'unsupported') {
    super(message);
    this.name = 'FigmaRenderPlanError';
    this.code = code;
    this.nextAction = nextAction;
    this.status = status;
  }
}

function fail(code, message, nextAction, status) {
  throw new FigmaRenderPlanError(code, message, nextAction, status);
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function fingerprint(value) {
  return sha256(canonicalJson(value));
}

function words(value) {
  return value
    .replace(/([a-z0-9])([A-Z])/gu, '$1 $2')
    .split(/[^A-Za-z0-9]+/u)
    .filter(Boolean);
}

function upperCamel(value) {
  const name = words(value).map((part) => `${part[0].toUpperCase()}${part.slice(1)}`).join('');
  return /^[A-Za-z_]/u.test(name) ? name : `Generated${name || 'Figma'}`;
}

function lowerCamel(value) {
  const parts = words(value);
  let name = parts.map((part, index) => index === 0
    ? `${part[0].toLowerCase()}${part.slice(1)}`
    : `${part[0].toUpperCase()}${part.slice(1)}`).join('');
  if (!/^[A-Za-z_]/u.test(name)) name = `value${name}`;
  if (KOTLIN_KEYWORDS.has(name)) name = `${name}Value`;
  return name || 'value';
}

function kotlinString(value) {
  return JSON.stringify(value).replaceAll('$', '\\$');
}

function floatLiteral(value) {
  if (!Number.isFinite(value)) {
    fail(
      'VC-AI-FIGMA-GENERATION-FAILED',
      'RenderPlan contains a non-finite numeric value.',
      'Reject the mapped IR and repair the deterministic adapter.',
      'failed',
    );
  }
  const normalized = Object.is(value, -0) ? 0 : value;
  return Number.isInteger(normalized) ? `${normalized}f` : `${normalized}f`;
}

function dp(value) {
  return Number.isInteger(value) ? `${value}.dp` : `${floatLiteral(value)}.dp`;
}

function sp(value) {
  return Number.isInteger(value) ? `${value}.sp` : `${floatLiteral(value)}.sp`;
}

function color(value) {
  return `0x${value.argb}.toInt()`;
}

function fieldMap(fields) {
  return new Map(fields.map((field) => [field.name, field.value]));
}

function renderKey(sourceId) {
  const prefix = sourceId.replace(/[^A-Za-z0-9._:-]+/gu, '-').slice(0, 72) || 'node';
  return `${prefix}-${sha256(sourceId).slice(0, 16)}`;
}

function uniqueIdentifier(base, used) {
  let candidate = lowerCamel(base);
  let suffix = 2;
  while (used.has(candidate)) {
    candidate = `${lowerCamel(base)}${suffix}`;
    suffix += 1;
  }
  used.add(candidate);
  return candidate;
}

function resourcePlan(designIr, assets) {
  const referenced = new Set();
  const visit = (node) => {
    const source = fieldMap(node.properties).get('source');
    if (source?.kind === 'resource') referenced.add(source.resourceId);
    node.children.forEach(visit);
  };
  designIr.roots.forEach(visit);
  const usedParameters = new Set();
  return [...referenced].sort().map((id) => {
    const decoded = assets.get(id);
    const declared = designIr.resources.find((resource) => resource.id === id);
    if (
      !decoded || !declared || declared.mediaType !== 'image/png' ||
      declared.redistribution !== 'allowed' || !decoded.bytes.equals(Buffer.from(decoded.asset.data, 'base64'))
    ) {
      fail(
        'VC-AI-FIGMA-GENERATION-FAILED',
        `RenderPlan cannot bind exact redistributable PNG resource ${id}.`,
        'Inspect asset integrity and redistribution decisions before generation.',
      );
    }
    const resourceName = `vc_figma_${declared.sha256}`;
    return {
      id,
      parameter: uniqueIdentifier(`image_${id}`, usedParameters),
      source: `@drawable/${resourceName}`,
      resourceName,
      mediaType: declared.mediaType,
      data: decoded.asset.data,
      bytes: declared.bytes,
      sha256: declared.sha256,
      widthPx: Math.round(declared.intrinsicWidth),
      heightPx: Math.round(declared.intrinsicHeight),
    };
  });
}

function planNode(node, resources, parent = null) {
  const properties = fieldMap(node.properties);
  const semantics = fieldMap(node.semantics);
  const planned = {
    sourceId: node.id,
    key: renderKey(node.id),
    kind: node.kind,
    layout: structuredClone(node.layout),
    properties: structuredClone(Object.fromEntries(properties)),
    semantics: structuredClone(Object.fromEntries(semantics)),
    relativeOffset: parent?.layout.mode === 'fixed'
      ? {
        xDp: node.layout.xDp - parent.layout.xDp - parent.layout.padding.left,
        yDp: node.layout.yDp - parent.layout.yDp - parent.layout.padding.top,
      }
      : null,
    children: [],
  };
  if (node.kind === 'image') {
    const resource = resources.find((item) => item.id === properties.get('source')?.resourceId);
    if (!resource) {
      fail(
        'VC-AI-FIGMA-GENERATION-FAILED',
        `Image node ${node.id} has no exact RenderPlan resource binding.`,
        'Restore the mapped resource identity before generation.',
        'failed',
      );
    }
    planned.resourceParameter = resource.parameter;
  }
  if (node.kind === 'vector') {
    fail(
      'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
      `Vector node ${node.id} is inspect-only in Figma generation v1.`,
      'Rasterize the vector to a declared redistributable PNG for this version.',
    );
  }
  planned.children = node.children.map((child) => planNode(child, resources, node));
  return planned;
}

export function createFigmaRenderPlan({designIr, assets}) {
  if (
    designIr?.schemaVersion !== 2 || designIr?.source?.kind !== 'figma' ||
    !Array.isArray(designIr.roots) || designIr.roots.length !== 1 ||
    !Array.isArray(designIr.unsupported) ||
    designIr.unsupported.some((item) => item.severity === 'error')
  ) {
    fail(
      'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
      'Figma generation v1 requires one supported root and no error-level mapping decisions.',
      'Inspect the design, select one independent root, and resolve every blocking mapping.',
    );
  }
  const resources = resourcePlan(designIr, assets);
  const plan = {
    schemaVersion: 1,
    kind: 'figma-render-plan',
    packageName: PACKAGE_NAME,
    functionName: `${upperCamel(designIr.documentId).slice(0, 110)}View`,
    inputFingerprint: designIr.source.fingerprint,
    irFingerprint: fingerprint(designIr),
    resources,
    root: planNode(designIr.roots[0], resources),
  };
  plan.planFingerprint = fingerprint(plan);
  return plan;
}

function modifierLines(node) {
  const calls = [];
  const {width, height, padding, clip} = node.layout;
  if (width.mode === 'fixed' && height.mode === 'fixed') {
    calls.push(`size(width = ${dp(width.valueDp)}, height = ${dp(height.valueDp)})`);
  } else {
    if (width.mode === 'fixed') calls.push(`width(${dp(width.valueDp)})`);
    if (height.mode === 'fixed') calls.push(`height(${dp(height.valueDp)})`);
    if (width.mode === 'fill') calls.push('fillMaxWidth()');
    if (height.mode === 'fill') calls.push('fillMaxHeight()');
  }
  if (node.relativeOffset && (node.relativeOffset.xDp !== 0 || node.relativeOffset.yDp !== 0)) {
    calls.push(`offset(x = ${dp(node.relativeOffset.xDp)}, y = ${dp(node.relativeOffset.yDp)})`);
  }
  if (Object.values(padding).some((value) => value !== 0)) {
    calls.push(
      `padding(left = ${dp(padding.left)}, top = ${dp(padding.top)}, ` +
      `right = ${dp(padding.right)}, bottom = ${dp(padding.bottom)})`,
    );
  }
  const background = node.properties.backgroundColor;
  if (background?.kind === 'color') calls.push(`backgroundColor(${color(background)})`);
  if (clip !== 'none') calls.push('clip()');
  return calls;
}

function modifierExpression(node, indent) {
  const calls = modifierLines(node);
  if (calls.length === 0) return 'Modifier';
  return ['Modifier', ...calls.map((call) => `${' '.repeat(indent)}.${call}`)].join('\n');
}

function emitCall(name, arguments_, node, indent, lines, children) {
  const prefix = ' '.repeat(indent);
  lines.push(`${prefix}${name}(`);
  for (const [argument, value] of arguments_) {
    const valueLines = value.split('\n');
    lines.push(`${prefix}    ${argument} = ${valueLines[0]}`);
    valueLines.slice(1).forEach((line) => lines.push(`${prefix}    ${line}`));
    lines[lines.length - 1] += ',';
  }
  lines.push(`${prefix}    key = ${kotlinString(node.key)},`);
  const modifier = modifierExpression(node, 8);
  const modifierLines_ = modifier.split('\n');
  lines.push(`${prefix}    modifier = ${modifierLines_[0]}`);
  modifierLines_.slice(1).forEach((line) => lines.push(`${prefix}    ${line}`));
  lines[lines.length - 1] += ',';
  if (children) {
    lines.push(`${prefix}) {`);
    children();
    lines.push(`${prefix}}`);
  } else {
    lines.push(`${prefix})`);
  }
}

function arrangement(value) {
  return {
    start: 'Start', center: 'Center', end: 'End',
    'space-between': 'SpaceBetween', 'space-around': 'SpaceAround', 'space-evenly': 'SpaceEvenly',
  }[value];
}

function emitNode(node, indent, lines) {
  if (node.kind === 'column') {
    emitCall('Column', [
      ['spacing', dp(node.layout.gapDp)],
      ['arrangement', `MainAxisArrangement.${arrangement(node.layout.mainAxisArrangement)}`],
      ['horizontalAlignment', `HorizontalAlignment.${upperCamel(node.layout.horizontalAlignment)}`],
    ], node, indent, lines, () => node.children.forEach((child) => emitNode(child, indent + 4, lines)));
    return;
  }
  if (node.kind === 'row') {
    emitCall('Row', [
      ['spacing', dp(node.layout.gapDp)],
      ['arrangement', `MainAxisArrangement.${arrangement(node.layout.mainAxisArrangement)}`],
      ['verticalAlignment', `VerticalAlignment.${{
        start: 'Top', center: 'Center', end: 'Bottom',
      }[node.layout.verticalAlignment]}`],
    ], node, indent, lines, () => node.children.forEach((child) => emitNode(child, indent + 4, lines)));
    return;
  }
  if (node.kind === 'box') {
    emitCall('Box', [], node, indent, lines, () =>
      node.children.forEach((child) => emitNode(child, indent + 4, lines)));
    return;
  }
  if (node.kind === 'text') {
    const family = {
      'sans-serif': 'Typeface.SANS_SERIF', serif: 'Typeface.SERIF', monospace: 'Typeface.MONOSPACE',
    }[node.properties.fontFamily?.value];
    const style = [
      `UiTextStyle(`,
      `    fontSizeSp = ${sp(node.properties.fontSize.value)},`,
      `    fontWeight = ${node.properties.fontWeight.value},`,
      `    fontFamily = Typeface.create(${family}, ` +
        `${node.properties.fontStyle.value === 'italic' ? 'Typeface.ITALIC' : 'Typeface.NORMAL'}),`,
      `    letterSpacingEm = ${floatLiteral(node.properties.letterSpacingEm.value)},`,
      `    lineHeightSp = ${sp(node.properties.lineHeight.value)},`,
      `)`,
    ].join('\n');
    emitCall('Text', [
      ['text', kotlinString(node.properties.text.value)],
      ['style', style],
      ['color', color(node.properties.color)],
    ], node, indent, lines);
    return;
  }
  if (node.kind === 'image') {
    const description = node.properties.contentDescription?.value ?? null;
    const scale = {
      fit: 'Fit', crop: 'Crop', 'fill-bounds': 'FillBounds', inside: 'Inside',
    }[node.properties.contentScale.value];
    emitCall('Image', [
      ['source', node.resourceParameter],
      ['contentDescription', description === null ? 'null' : kotlinString(description)],
      ['contentScale', `ImageContentScale.${scale}`],
    ], node, indent, lines);
    return;
  }
  fail(
    'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
    `RenderPlan node ${node.sourceId} has unsupported kind ${node.kind}.`,
    'Inspect the Figma mapping subset before generation.',
  );
}

function virtualFile(path, mediaType, encoding, data) {
  const bytes = encoding === 'base64' ? Buffer.from(data, 'base64') : Buffer.from(data, 'utf8');
  const contentSha = sha256(bytes);
  return {
    path,
    mediaType,
    encoding,
    data,
    bytes: bytes.length,
    sha256: contentSha,
    artifactFingerprint: fingerprint({path, mediaType, encoding, bytes: bytes.length, sha256: contentSha}),
  };
}

function comparisonDimension(layoutSize) {
  if (layoutSize.mode === 'fixed') {
    return {kind: 'dimension', value: layoutSize.valueDp, unit: 'dp'};
  }
  if (layoutSize.mode === 'fill') {
    return {kind: 'layout-dimension', value: 'match-parent'};
  }
  return null;
}

function comparisonNode(node) {
  const properties = [];
  if (node.kind === 'text') {
    properties.push({name: 'text', value: structuredClone(node.properties.text)});
  }
  if (
    node.kind === 'image' && node.properties.contentDescription?.kind === 'literal' &&
    typeof node.properties.contentDescription.value === 'string'
  ) {
    properties.push({
      name: 'contentDescription',
      value: structuredClone(node.properties.contentDescription),
    });
  }
  const sizeArguments = [];
  const width = comparisonDimension(node.layout.width);
  const height = comparisonDimension(node.layout.height);
  if (width) sizeArguments.push({name: 'width', value: width});
  if (height) sizeArguments.push({name: 'height', value: height});
  const modifiers = sizeArguments.length > 0
    ? [{kind: 'size', arguments: sizeArguments}]
    : [];
  const paddingValues = Object.values(node.layout.padding);
  if (paddingValues.length === 4 && paddingValues.every((value) => value === paddingValues[0])) {
    if (paddingValues[0] !== 0) {
      modifiers.push({
        kind: 'padding',
        arguments: [{
          name: 'all',
          value: {kind: 'dimension', value: paddingValues[0], unit: 'dp'},
        }],
      });
    }
  }
  const semantics = node.kind === 'image'
    ? [{name: 'role', value: {kind: 'enum', type: 'semantic-role', value: 'image'}}]
    : [];
  return {
    id: node.key,
    kind: node.kind,
    properties,
    modifiers,
    semantics,
    events: [],
    state: [],
    children: node.children.map(comparisonNode),
    provenance: {
      sourceId: node.sourceId,
      sourceSpan: `nodes/${node.sourceId}`,
      confidence: 1,
      decision: 'Lowered from the private Figma RenderPlan for rendered structure and geometry checks.',
    },
  };
}

function comparisonDesignIr(plan) {
  return {
    schemaVersion: 1,
    documentId: `figma-verify-${plan.planFingerprint.slice(0, 16)}`,
    source: {
      kind: 'figma',
      identity: plan.inputFingerprint,
      fingerprint: plan.inputFingerprint,
    },
    roots: [comparisonNode(plan.root)],
    unsupported: [],
  };
}

export function generateFigmaArtifacts(plan) {
  const imports = [
    'android.graphics.Typeface',
    'com.viewcompose.ui.foundation.Box',
    'com.viewcompose.ui.foundation.Column',
    'com.viewcompose.ui.foundation.Image',
    'com.viewcompose.ui.foundation.Row',
    'com.viewcompose.ui.foundation.Text',
    'com.viewcompose.ui.foundation.UiTextStyle',
    'com.viewcompose.ui.foundation.UiTreeBuilder',
    'com.viewcompose.ui.layout.HorizontalAlignment',
    'com.viewcompose.ui.layout.MainAxisArrangement',
    'com.viewcompose.ui.layout.VerticalAlignment',
    'com.viewcompose.ui.modifier.Modifier',
    'com.viewcompose.ui.modifier.backgroundColor',
    'com.viewcompose.ui.modifier.clip',
    'com.viewcompose.ui.modifier.fillMaxHeight',
    'com.viewcompose.ui.modifier.fillMaxWidth',
    'com.viewcompose.ui.modifier.height',
    'com.viewcompose.ui.modifier.offset',
    'com.viewcompose.ui.modifier.padding',
    'com.viewcompose.ui.modifier.size',
    'com.viewcompose.ui.modifier.width',
    'com.viewcompose.ui.node.ImageContentScale',
    'com.viewcompose.ui.node.ImageSource',
    'com.viewcompose.ui.unit.dp',
    'com.viewcompose.ui.unit.sp',
  ];
  const parameters = plan.resources.map((resource) => `    ${resource.parameter}: ImageSource,`);
  const body = [];
  emitNode(plan.root, 4, body);
  const kotlin = [
    `package ${plan.packageName}`,
    '',
    ...imports.sort().map((name) => `import ${name}`),
    '',
    `fun UiTreeBuilder.${plan.functionName}(`,
    ...parameters,
    ') {',
    ...body,
    '}',
    '',
  ].join('\n');
  const kotlinFile = virtualFile(
    `src/main/kotlin/generated/viewcompose/${plan.functionName}.kt`,
    'text/x-kotlin',
    'utf8',
    kotlin,
  );
  const assetFiles = plan.resources.map((resource) => virtualFile(
    `src/main/res/drawable/${resource.resourceName}.png`,
    resource.mediaType,
    'base64',
    resource.data,
  ));
  const report = {
    schemaVersion: 1,
    kind: 'figma-generation-report',
    input: {inputFingerprint: plan.inputFingerprint, irFingerprint: plan.irFingerprint},
    target: {
      language: 'kotlin',
      packageName: plan.packageName,
      functionName: plan.functionName,
      artifactIds: ['viewcompose-ui-foundation'],
      capabilityIds: ['foundation.components', 'image.foundation', 'modifier.drawing', 'modifier.layout'],
    },
    bindings: {
      resources: plan.resources.map((resource) => ({
        parameter: resource.parameter,
        source: resource.source,
        type: 'ImageSource',
      })),
      states: [],
      events: [],
    },
    planFingerprint: plan.planFingerprint,
    kotlinFingerprint: kotlinFile.sha256,
  };
  report.reportFingerprint = fingerprint(report);
  const previewBindings = plan.resources.map((resource) => ({
    kind: 'image-source',
    parameter: resource.parameter,
    source: resource.source,
    asset: {
      mediaType: resource.mediaType,
      encoding: 'base64',
      data: resource.data,
      bytes: resource.bytes,
      sha256: resource.sha256,
      widthPx: resource.widthPx,
      heightPx: resource.heightPx,
    },
  }));
  const virtualFiles = [kotlinFile, ...assetFiles]
    .sort((left, right) => left.path.localeCompare(right.path));
  return {
    kotlin,
    report,
    previewBindings,
    comparisonDesignIr: comparisonDesignIr(plan),
    virtualFiles,
    artifactSetFingerprint: fingerprint(virtualFiles.map((file) => ({
      path: file.path,
      bytes: file.bytes,
      sha256: file.sha256,
      artifactFingerprint: file.artifactFingerprint,
    }))),
  };
}
