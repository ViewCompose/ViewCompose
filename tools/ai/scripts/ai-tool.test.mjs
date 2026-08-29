import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import test from 'node:test';
import {dispatchToolRequest} from './ai-tool.mjs';
import {loadKnowledgeManifest, toolResult} from './tool-core.mjs';

const executable = fileURLToPath(new URL('./ai-tool.mjs', import.meta.url));

async function request(tool, arguments_, overrides = {}) {
  const manifest = await loadKnowledgeManifest();
  return {
    schemaVersion: 1,
    kind: 'request',
    requestId: overrides.requestId ?? 'cli-test',
    tool,
    framework: overrides.framework ?? manifest.framework,
    limits: {
      timeoutMs: 10_000,
      maxInputBytes: 256 * 1024,
      maxOutputBytes: 1024 * 1024,
      ...overrides.limits,
    },
    arguments: arguments_,
  };
}

function executeCli(input, arguments_ = []) {
  return new Promise((resolvePromise) => {
    const child = spawn(process.execPath, [executable, ...arguments_], {
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    const stdout = [];
    const stderr = [];
    child.stdout.on('data', (chunk) => stdout.push(chunk));
    child.stderr.on('data', (chunk) => stderr.push(chunk));
    child.on('close', (exitCode) => resolvePromise({
      exitCode,
      stdout: Buffer.concat(stdout).toString('utf8'),
      stderr: Buffer.concat(stderr).toString('utf8'),
    }));
    child.stdin.end(input);
  });
}

test('dispatches static validation through the frozen request and result envelope', async () => {
  const result = await dispatchToolRequest(await request('validate_code', {
    mode: 'static',
    path: 'Screen.kt',
    source: `
      package example
      import com.viewcompose.ui.foundation.Text
      import com.viewcompose.ui.foundation.UiTreeBuilder
      fun UiTreeBuilder.screen() { Text("Ready") }
    `,
  }));
  assert.equal(result.status, 'success');
  assert.equal(result.tool, 'validate_code');
  assert.equal(result.evidence.level, 'static');
});

test('dispatches deterministic knowledge retrieval through the same envelope', async () => {
  const search = await dispatchToolRequest(await request('search_component', {
    versionLane: 'current-source',
    query: 'add padding and fill the available width',
    limit: 5,
  }));
  assert.equal(search.status, 'success');
  assert.equal(search.evidence.level, 'knowledge');
  assert.ok(search.data.results.some((entry) => entry.capabilityId === 'modifier.layout'));

  const sample = await dispatchToolRequest(await request('get_sample', {
    versionLane: 'current-source',
    sampleId: 'module.ui-foundation-profile-summary',
  }));
  assert.equal(sample.status, 'success');
  assert.equal(sample.data.executable, true);
});

test('rejects framework drift and unsupported tools without invoking adapters', async () => {
  let invocations = 0;
  const drift = await dispatchToolRequest(await request('validate_code', {
    mode: 'static',
    source: 'fun example() = Unit',
  }, {
    framework: {versionLane: 'current-source', identity: '0'.repeat(40)},
  }), {
    validate: async () => {
      invocations += 1;
    },
  });
  assert.equal(drift.status, 'unsupported');
  assert.equal(drift.diagnostics[0].code, 'VC-AI-VERSION-LANE-MISMATCH');

  const unsupported = await dispatchToolRequest(await request('generate_ui', {}));
  assert.equal(unsupported.status, 'unsupported');
  assert.equal(unsupported.diagnostics[0].code, 'VC-AI-TOOL-UNSUPPORTED');
  assert.equal(invocations, 0);
});

test('maps compile, render, and project request limits into provider-neutral adapters', async () => {
  const captured = [];
  const handler = (tool, level) => async (arguments_) => {
    captured.push({tool, arguments_});
    return toolResult({
      requestId: arguments_.requestId,
      tool,
      status: 'success',
      level,
      diagnostics: [],
      data: {},
    });
  };
  await dispatchToolRequest(await request('validate_code', {
    mode: 'compile',
    source: 'fun example() = Unit',
    artifactIds: ['viewcompose-ui-foundation'],
  }), {compile: handler('validate_code', 'compiled')});
  await dispatchToolRequest(await request('render_preview', {
    targetId: 'samples.counter.CounterPreview',
  }), {render: handler('render_preview', 'rendered')});
  await dispatchToolRequest(await request('analyze_project', {
    projectRoot: '/workspace/sample',
    maxFiles: 25,
    maxDepth: 5,
  }), {analyze: handler('analyze_project', 'static')});

  assert.equal(captured[0].arguments_.limits.maxSourceBytes, 256 * 1024);
  assert.equal(captured[0].arguments_.signal instanceof AbortSignal, true);
  assert.equal(captured[1].arguments_.limits.timeoutMs, 10_000);
  assert.equal(captured[1].arguments_.signal instanceof AbortSignal, true);
  assert.equal(captured[2].arguments_.limits.maxFiles, 25);
  assert.equal(captured[2].arguments_.limits.maxDepth, 5);
});

test('replaces oversized adapter data with one bounded stable result', async () => {
  const result = await dispatchToolRequest(await request('analyze_project', {
    projectRoot: '/workspace/sample',
  }, {
    limits: {maxOutputBytes: 1024},
  }), {
    analyze: async (arguments_) => toolResult({
      requestId: arguments_.requestId,
      tool: 'analyze_project',
      status: 'success',
      level: 'static',
      diagnostics: [],
      data: {content: 'x'.repeat(5000)},
    }),
  });
  assert.equal(result.status, 'limited');
  assert.equal(result.diagnostics[0].code, 'VC-AI-OUTPUT-LIMIT');
  assert.equal(result.data, undefined);
  assert.equal(result.truncated, true);
});

test('the executable CLI reads one stdin request and writes only the JSON result to stdout', async () => {
  const toolRequest = await request('validate_code', {
    mode: 'static',
    path: 'Screen.kt',
    source: 'package example\nfun screen() = Unit\n',
  }, {requestId: 'cli-process'});
  const execution = await executeCli(JSON.stringify(toolRequest), ['--pretty']);
  assert.equal(execution.exitCode, 0);
  assert.equal(execution.stderr, '');
  const result = JSON.parse(execution.stdout);
  assert.equal(result.requestId, 'cli-process');
  assert.equal(result.status, 'success');
});

test('the executable CLI rejects malformed envelopes without emitting partial JSON', async () => {
  const execution = await executeCli('{"kind":"request"}');
  assert.equal(execution.exitCode, 2);
  assert.equal(execution.stdout, '');
  assert.match(execution.stderr, /rejected the request/u);
});
