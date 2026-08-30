import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import test from 'node:test';
import {diagnoseLayout, interpretLayoutSnapshot} from './layout-diagnoser.mjs';
import {PREVIEW_COMPILER_LANE, RENDER_LANE} from './preview-adapter.mjs';

const fixturePath = fileURLToPath(
  new URL('../evaluation/fixtures/render/layout-diagnostics.json', import.meta.url),
);
const outputFingerprint = 'a'.repeat(64);

async function fixture() {
  return JSON.parse(await readFile(fixturePath, 'utf8'));
}

function acceptedRender(overrides = {}) {
  return {
    tool: 'render_preview',
    status: 'success',
    evidence: {
      level: 'rendered',
      cache: 'hit',
      compilerLane: PREVIEW_COMPILER_LANE,
      renderLane: RENDER_LANE,
      outputFingerprint,
    },
    diagnostics: [],
    data: {
      targetId: 'samples.counter.CounterPreview',
      buildFingerprint: 'b'.repeat(64),
      previewId: 'counter-preview',
      variantId: 'counter-light',
      configuration: {theme: 'Light'},
      capabilityIds: ['preview.runner'],
      source: {path: 'samples/counter/src/debug/CounterPreview.kt', line: 23, column: 1},
      image: {path: 'preview.png', sha256: 'c'.repeat(64)},
      renderTree: {path: 'render-tree.json', sha256: 'd'.repeat(64), bytes: 100},
    },
    elapsedMs: 1,
    truncated: false,
    ...overrides,
  };
}

test('maps accepted Preview layout facts to stable source-aware findings', async () => {
  const interpreted = interpretLayoutSnapshot(await fixture(), {
    sourcePath: 'samples/counter/src/debug/CounterPreview.kt',
  });
  assert.equal(interpreted.summary.clean, false);
  assert.equal(interpreted.summary.actionableCount, 1);
  assert.deepEqual(interpreted.findings.map((entry) => entry.code), [
    'VC-AI-LAYOUT-PARTIALLY-CLIPPED',
    'VC-AI-LAYOUT-TEXT-ELLIPSIZED',
  ]);
  assert.deepEqual(interpreted.findings[0].source, {
    path: 'samples/counter/src/debug/CounterPreview.kt',
    startLine: 31,
    startColumn: 1,
  });
  assert.equal(interpreted.findings[0].bounds.right, 460);
  assert.equal(interpreted.findings[1].metrics.ellipsizedLineCount, 1);
});

test('reports a clean snapshot without inventing source or geometry diagnostics', () => {
  const result = interpretLayoutSnapshot({
    structure: {vnodeCount: 2, mountedNodeCount: 2, maxVNodeDepth: 1, maxMountedDepth: 1},
    warnings: [],
    layoutDiagnostics: [],
  });
  assert.equal(result.summary.clean, true);
  assert.equal(result.summary.findingCount, 0);
  assert.deepEqual(result.findings, []);
});

test('fails closed on unknown protocol kinds and bounds while bounding findings', () => {
  assert.throws(() => interpretLayoutSnapshot({
    layoutDiagnostics: [{
      kind: 'OverlappingChildren',
      severity: 'Warning',
      className: 'android.view.View',
      bounds: {left: 0, top: 0, right: 1, bottom: 1},
    }],
  }), /LAYOUT_EVIDENCE_INVALID/u);
  assert.throws(() => interpretLayoutSnapshot({
    layoutDiagnostics: [{
      kind: 'ZeroLayoutSize',
      severity: 'Warning',
      className: 'android.view.View',
      bounds: {left: 2, top: 0, right: 1, bottom: 1},
    }],
  }), /LAYOUT_EVIDENCE_INVALID/u);

  const repeated = Array.from({length: 101}, (_, index) => ({
    kind: 'ZeroLayoutSize',
    severity: 'Warning',
    className: `android.view.View${index}`,
    bounds: {left: 0, top: index, right: 0, bottom: index + 1},
  }));
  const bounded = interpretLayoutSnapshot({layoutDiagnostics: repeated});
  assert.equal(bounded.summary.findingCount, 101);
  assert.equal(bounded.findings.length, 100);
  assert.equal(bounded.truncated, true);
});

test('returns one rendered diagnose_layout result over the accepted render identity', async () => {
  const rendered = acceptedRender();
  const result = await diagnoseLayout({
    targetId: rendered.data.targetId,
    requestId: 'diagnose-integration',
  }, {
    render: async () => rendered,
    readSnapshot: async (candidate) => {
      assert.equal(candidate, rendered);
      return fixture();
    },
  });
  assert.equal(result.tool, 'diagnose_layout');
  assert.equal(result.status, 'success');
  assert.equal(result.evidence.level, 'rendered');
  assert.equal(result.evidence.outputFingerprint, outputFingerprint);
  assert.equal(result.data.summary.findingCount, 2);
  assert.deepEqual(result.diagnostics.map((entry) => entry.code), [
    'VC-AI-LAYOUT-PARTIALLY-CLIPPED',
    'VC-AI-LAYOUT-TEXT-ELLIPSIZED',
  ]);
});

test('renames render failures and rejects changed layout evidence', async () => {
  const renderFailure = acceptedRender({
    status: 'limited',
    evidence: {level: 'compiled', cache: 'bypassed', renderLane: RENDER_LANE},
    diagnostics: [{
      code: 'VC-AI-PREVIEW-TIMEOUT',
      severity: 'error',
      message: 'Preview timed out.',
      nextAction: 'Retry.',
    }],
  });
  const limited = await diagnoseLayout({requestId: 'diagnose-timeout'}, {
    render: async () => renderFailure,
  });
  assert.equal(limited.tool, 'diagnose_layout');
  assert.equal(limited.status, 'limited');
  assert.equal(limited.diagnostics[0].code, 'VC-AI-PREVIEW-TIMEOUT');

  const invalid = await diagnoseLayout({requestId: 'diagnose-invalid'}, {
    render: async () => acceptedRender(),
    readSnapshot: async () => {
      throw new Error('changed');
    },
  });
  assert.equal(invalid.status, 'failed');
  assert.equal(invalid.evidence.level, 'compiled');
  assert.equal(invalid.diagnostics[0].code, 'VC-AI-LAYOUT-EVIDENCE-INVALID');
  assert.equal(invalid.evidence.outputFingerprint, undefined);
});
