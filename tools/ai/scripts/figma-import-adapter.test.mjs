import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {importFigmaExport} from './figma-import-adapter.mjs';
import {semanticToolResult} from './tool-core.mjs';

const exampleUrl = new URL('../contracts/examples/figma-export.json', import.meta.url);
const mutationRoot = new URL('../evaluation/fixtures/figma/', import.meta.url);

async function example() {
  return JSON.parse(await readFile(exampleUrl, 'utf8'));
}

async function mutation(name) {
  return JSON.parse(await readFile(new URL(name, mutationRoot), 'utf8'));
}

function request(exported, mode = 'inspect') {
  return {
    schemaVersion: 1,
    kind: 'figma-import-request',
    mode,
    exportJson: JSON.stringify(exported),
    ...(mode === 'verify' ? {
      verification: {
        widthDp: 360,
        heightDp: 120,
        density: 1,
        fontScale: 1,
        theme: 'Light',
        layoutDirection: 'Ltr',
      },
    } : {}),
  };
}

function reorder(value) {
  if (Array.isArray(value)) return value.map(reorder);
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).reverse().map((key) => [key, reorder(value[key])]));
  }
  return value;
}

function applyMutation(exported, descriptor) {
  if (descriptor.operation === 'add-fact') {
    exported.nodes.find((node) => node.id === descriptor.nodeId).facts.push(descriptor.fact);
  } else if (descriptor.operation === 'replace-asset-logical-path') {
    exported.assets[0].logicalPath = descriptor.value;
  } else if (descriptor.operation === 'replace-asset-sha256') {
    exported.assets[0].sha256 = descriptor.value;
  } else if (descriptor.operation === 'replace-reference') {
    exported.nodes.find((node) => node.id === descriptor.nodeId).facts
      .find((fact) => fact.path === descriptor.path).value.id = descriptor.value;
  } else {
    throw new Error(`Unknown fixture mutation ${descriptor.operation}`);
  }
  return exported;
}

test('imports a complete offline Figma graph deterministically without echoing asset bytes', async () => {
  const exported = await example();
  const first = await importFigmaExport(request(exported), {requestId: 'figma-inspect-1'});
  const secondRequest = request(reorder(exported));
  secondRequest.exportJson = JSON.stringify(reorder(exported));
  const second = await importFigmaExport(secondRequest, {requestId: 'figma-inspect-2'});
  assert.equal(first.status, 'success');
  assert.equal(first.evidence.level, 'static');
  assert.equal(first.data.designIr.schemaVersion, 2);
  assert.equal(first.data.designIr.source.kind, 'figma');
  assert.equal(first.data.auditSummary.nodes, 3);
  assert.equal(first.data.auditSummary.blocking, 0);
  assert.equal(first.data.auditSummary.generationAllowed, true);
  assert.equal(first.data.audit.factCoverage.percent, 100);
  assert.equal(first.data.audit.assetCoverage.percent, 100);
  assert.equal(first.data.inputFingerprint, second.data.inputFingerprint);
  assert.equal(first.data.irFingerprint, second.data.irFingerprint);
  assert.deepEqual(
    semanticToolResult({...first, requestId: 'stable'}),
    semanticToolResult({...second, requestId: 'stable'}),
  );
  assert.equal(JSON.stringify(first).includes(exported.assets[0].data), false);
});

test('rejects duplicate JSON keys before ordinary parsing loses their identity', async () => {
  const source = await readFile(exampleUrl, 'utf8');
  const duplicate = source.replace(
    '"revision": "version-1",',
    '"revision": "version-1", "revision": "version-1",',
  );
  const result = await importFigmaExport({
    schemaVersion: 1,
    kind: 'figma-import-request',
    mode: 'inspect',
    exportJson: duplicate,
  }, {requestId: 'figma-duplicate'});
  assert.equal(result.status, 'invalid');
  assert.equal(result.diagnostics[0].code, 'VC-AI-FIGMA-CONTRACT-INVALID');
});

test('resolves typed token aliases and preserves non-primitive variant provenance', async () => {
  const exported = await example();
  exported.catalogs.tokens.push(
    {
      id: 'token-base-spacing',
      collectionId: 'collection-spacing',
      nameDigest: 'a'.repeat(64),
      resolvedType: 'FLOAT',
      value: {kind: 'number', value: 16},
      aliases: [],
    },
    {
      id: 'token-card-spacing',
      collectionId: 'collection-spacing',
      nameDigest: 'b'.repeat(64),
      resolvedType: 'FLOAT',
      value: {kind: 'reference', referenceType: 'token', id: 'token-base-spacing'},
      aliases: ['token-base-spacing'],
    },
  );
  exported.nodes[0].variantProperties.push({
    path: 'variant.breakpoints',
    phase: 'structure',
    value: {kind: 'number-list', values: [360, 600]},
  });
  const result = await importFigmaExport(request(exported), {requestId: 'figma-token-alias'});
  assert.equal(result.status, 'success');
  const alias = result.data.designIr.catalogs.tokens
    .find((token) => token.id === 'token-card-spacing');
  assert.equal(alias.resolvedType, 'number');
  assert.deepEqual(alias.value, {
    kind: 'token',
    tokenId: 'token-base-spacing',
    resolvedValue: {kind: 'literal', value: 16},
  });
  assert.equal(
    result.data.designIr.roots[0].provenance.variantProperties[0].value.value,
    '[360,600]',
  );
});

test('rejects URLs, active content, unsafe paths, changed assets, and undeclared fonts', async () => {
  for (const name of [
    'url-reference.mutation.json',
    'active-plugin-data.mutation.json',
    'path-traversal.mutation.json',
    'asset-integrity.mutation.json',
    'undeclared-font.mutation.json',
  ]) {
    const descriptor = await mutation(name);
    const result = await importFigmaExport(
      request(applyMutation(await example(), descriptor)),
      {requestId: `figma-${name}`},
    );
    assert.notEqual(result.status, 'success', name);
    assert.equal(result.diagnostics[0].code, descriptor.expectedDiagnostic, name);
  }
});

test('preserves unsupported effects and prototype interactions while blocking generation', async () => {
  for (const name of [
    'unsupported-effect.mutation.json',
    'prototype-interaction.mutation.json',
  ]) {
    const descriptor = await mutation(name);
    const exported = applyMutation(await example(), descriptor);
    const inspected = await importFigmaExport(request(exported), {requestId: `figma-${name}`});
    assert.equal(inspected.status, 'unsupported', name);
    assert.equal(inspected.data.auditSummary.blocking, 1, name);
    assert.equal(inspected.data.auditSummary.generationAllowed, false, name);
    assert.equal(inspected.data.designIr.unsupported.length, 1, name);
    assert.equal(inspected.diagnostics[0].code, descriptor.expectedDiagnostic, name);
    const generated = await importFigmaExport(request(exported, 'generate'), {
      requestId: `figma-generate-${name}`,
    });
    assert.equal(generated.status, 'unsupported', name);
    assert.equal(generated.diagnostics[0].code, descriptor.expectedDiagnostic, name);
  }
});

test('rejects cyclic or detached selected graphs and honors cancellation', async () => {
  const cyclic = await example();
  cyclic.nodes.find((node) => node.id === '1:3').childIds.push('1:1');
  const graph = await importFigmaExport(request(cyclic), {requestId: 'figma-cycle'});
  assert.equal(graph.status, 'invalid');
  assert.equal(graph.diagnostics[0].code, 'VC-AI-FIGMA-GRAPH-INVALID');

  const controller = new AbortController();
  controller.abort();
  const cancelled = await importFigmaExport(request(await example()), {
    requestId: 'figma-cancelled',
    signal: controller.signal,
  });
  assert.equal(cancelled.status, 'cancelled');
  assert.equal(cancelled.diagnostics[0].code, 'VC-AI-FIGMA-CANCELLED');
});

test('generates virtual files and returns bounded Preview verification evidence', async () => {
  const exported = await example();
  const generated = await importFigmaExport(request(exported, 'generate'), {
    requestId: 'figma-generate',
  });
  assert.equal(generated.status, 'success');
  assert.equal(generated.data.mode, 'generate');
  assert.equal(generated.data.virtualFiles.length, 2);
  assert.deepEqual(
    generated.data.virtualFiles.map((file) => file.mediaType).sort(),
    ['image/png', 'text/x-kotlin'],
  );

  let renderRequest;
  const verified = await importFigmaExport(request(exported, 'verify'), {
    requestId: 'figma-verify',
    render: async (rendered) => {
      renderRequest = rendered;
      return {
        status: 'success',
        diagnostics: [],
        truncated: false,
        evidence: {
          level: 'rendered',
          cache: 'miss',
          compilerLane: 'figma-test-compiler',
          renderLane: 'figma-test-renderer',
          outputFingerprint: 'e'.repeat(64),
        },
        data: {generatedPreview: {requestFingerprint: 'f'.repeat(64)}},
      };
    },
    compare: async () => ({
      status: 'success',
      evidenceLevel: 'compared',
      diagnostics: [],
      comparison: {
        comparisonFingerprint: 'd'.repeat(64),
        nodes: [{
          checks: [
            {category: 'identity', status: 'passed'},
            {category: 'structure', status: 'passed'},
            {category: 'semantic', status: 'passed'},
            {category: 'geometry', status: 'passed'},
          ],
        }],
      },
    }),
  });
  assert.equal(verified.status, 'success');
  assert.equal(verified.evidence.level, 'compared');
  assert.equal(verified.data.mode, 'verify');
  assert.equal(verified.data.verification.compilation.status, 'passed');
  assert.equal(verified.data.verification.preview.status, 'passed');
  assert.equal(verified.data.verification.categories.style.conclusion, 'incomplete');
  assert.equal(verified.data.verification.conclusion, 'incomplete');
  assert.equal(renderRequest.previewConfiguration.widthDp, 360);
  assert.equal(renderRequest.generationReport.kind, 'figma-generation-report');
});
