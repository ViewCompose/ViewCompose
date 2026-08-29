import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {Readable, Writable} from 'node:stream';
import {fileURLToPath} from 'node:url';
import test from 'node:test';
import {createToolRequest, dispatchToolRequest} from './ai-tool.mjs';
import {
  MCP_PROTOCOL,
  mcpToolRequestId,
  serveStdio,
  ViewComposeMcpSession,
} from './mcp-server.mjs';
import {semanticToolResult, toolResult} from './tool-core.mjs';

const executable = fileURLToPath(new URL('./mcp-server.mjs', import.meta.url));
const protocolVersionKey = 'io.modelcontextprotocol/protocolVersion';
const clientInfoKey = 'io.modelcontextprotocol/clientInfo';
const clientCapabilitiesKey = 'io.modelcontextprotocol/clientCapabilities';

function modernMeta(extra = {}) {
  return {
    [protocolVersionKey]: '2026-07-28',
    [clientInfoKey]: {name: 'viewcompose-test', version: '1.0.0'},
    [clientCapabilitiesKey]: {},
    ...extra,
  };
}

function request(id, method, params = {}) {
  return {jsonrpc: '2.0', id, method, params: {...params, _meta: modernMeta(params._meta)}};
}

test('freezes modern and legacy MCP versions without implicit downgrade', () => {
  assert.equal(MCP_PROTOCOL.preferredVersion, '2026-07-28');
  assert.deepEqual(MCP_PROTOCOL.supportedVersions, ['2026-07-28', '2025-11-25']);
  assert.equal(MCP_PROTOCOL.transport, 'stdio');
  assert.equal(MCP_PROTOCOL.compatibility.implicitVersionDowngrade, false);
});

test('discovers the stateless modern server and deterministically lists the shared catalog', async () => {
  const session = new ViewComposeMcpSession();
  const discovery = await session.receive(request('discover', 'server/discover'));
  assert.equal(discovery.result.resultType, 'complete');
  assert.deepEqual(discovery.result.supportedVersions, MCP_PROTOCOL.supportedVersions);
  assert.deepEqual(discovery.result.capabilities, {tools: {listChanged: false}});

  const listing = await session.receive(request('list', 'tools/list'));
  assert.equal(listing.result.resultType, 'complete');
  assert.deepEqual(listing.result.tools.map((tool) => tool.name), [
    'get_api_reference',
    'get_component_reference',
    'search_component',
    'get_sample',
    'validate_code',
    'render_preview',
    'analyze_project',
  ]);
  assert.equal(listing.result.tools[0].inputSchema.required.includes('versionLane'), true);
});

test('rejects unsupported modern versions with the frozen supported set', async () => {
  const session = new ViewComposeMcpSession();
  const response = await session.receive(request(1, 'tools/list', {
    _meta: modernMeta({[protocolVersionKey]: '1900-01-01'}),
  }));
  assert.equal(response.error.code, -32022);
  assert.deepEqual(response.error.data.supported, MCP_PROTOCOL.supportedVersions);
  assert.equal(response.error.data.requested, '1900-01-01');
});

test('returns the exact provider-neutral result through CLI and MCP', async () => {
  const id = 'parity';
  const arguments_ = {
    versionLane: 'current-source',
    identifier: 'modifier.layout',
  };
  const direct = await dispatchToolRequest(await createToolRequest({
    tool: 'get_api_reference',
    arguments: arguments_,
    requestId: mcpToolRequestId(id),
  }));
  const response = await new ViewComposeMcpSession().receive(request(id, 'tools/call', {
    name: 'get_api_reference',
    arguments: arguments_,
  }));
  assert.deepEqual(
    semanticToolResult(response.result.structuredContent),
    semanticToolResult(direct),
  );
  assert.equal(JSON.parse(response.result.content[0].text).evidence.bundleFingerprint,
    direct.evidence.bundleFingerprint);
  assert.equal(response.result.isError, false);
});

test('keeps invalid tool arguments actionable and unknown tools at protocol level', async () => {
  const session = new ViewComposeMcpSession();
  const invalid = await session.receive(request(1, 'tools/call', {
    name: 'get_api_reference',
    arguments: {versionLane: 'current-source'},
  }));
  assert.equal(invalid.result.isError, true);
  assert.equal(invalid.result.structuredContent.status, 'invalid');
  assert.equal(invalid.result.structuredContent.diagnostics[0].code, 'VC-AI-ARGUMENTS-INVALID');

  const unknown = await session.receive(request(2, 'tools/call', {
    name: 'run_gradle',
    arguments: {},
  }));
  assert.equal(unknown.error.code, -32602);
  assert.match(unknown.error.message, /Unknown tool/u);
});

test('supports the 2025-11-25 initialize lifecycle without weakening modern requests', async () => {
  const session = new ViewComposeMcpSession();
  const beforeInitialize = await session.receive({
    jsonrpc: '2.0',
    id: 1,
    method: 'tools/list',
    params: {},
  });
  assert.equal(beforeInitialize.error.code, -32602);

  const initialized = await session.receive({
    jsonrpc: '2.0',
    id: 2,
    method: 'initialize',
    params: {
      protocolVersion: '2025-11-25',
      capabilities: {},
      clientInfo: {name: 'legacy-test', version: '1.0.0'},
    },
  });
  assert.equal(initialized.result.protocolVersion, '2025-11-25');
  await session.receive({jsonrpc: '2.0', method: 'notifications/initialized'});
  const listing = await session.receive({
    jsonrpc: '2.0',
    id: 3,
    method: 'tools/list',
    params: {},
  });
  assert.equal(listing.result.resultType, undefined);
  assert.equal(listing.result.tools.length, 7);
});

test('emits bounded opt-in progress and suppresses all output after cancellation', async () => {
  let started;
  const running = new Promise((resolvePromise) => {
    started = resolvePromise;
  });
  const session = new ViewComposeMcpSession({
    dispatch: async (toolRequest, {signal}) => {
      started();
      await new Promise((resolvePromise) => signal.addEventListener('abort', resolvePromise, {once: true}));
      return toolResult({
        requestId: toolRequest.requestId,
        tool: toolRequest.tool,
        status: 'cancelled',
        level: 'static',
        diagnostics: [],
      });
    },
  });
  const emitted = [];
  const pending = session.receive(request('cancel-me', 'tools/call', {
    name: 'validate_code',
    arguments: {source: 'fun screen() = Unit'},
    _meta: modernMeta({progressToken: 'progress-1'}),
  }), (message) => emitted.push(message));
  await running;
  await session.receive({
    jsonrpc: '2.0',
    method: 'notifications/cancelled',
    params: {requestId: 'cancel-me', reason: 'test cancellation'},
  });
  assert.equal(await pending, null);
  assert.equal(emitted.length, 1);
  assert.equal(emitted[0].method, 'notifications/progress');
  assert.equal(emitted[0].params.progress, 0);
});

test('rejects calls beyond the frozen concurrency limit', async () => {
  const releases = [];
  const session = new ViewComposeMcpSession({
    dispatch: (toolRequest) => new Promise((resolvePromise) => {
      releases.push(async () => resolvePromise(await toolResult({
        requestId: toolRequest.requestId,
        tool: toolRequest.tool,
        status: 'success',
        level: 'static',
        diagnostics: [],
      })));
    }),
  });
  const pending = Array.from({length: MCP_PROTOCOL.limits.maxConcurrentRequests}, (_, index) =>
    session.receive(request(`bounded-${index}`, 'tools/call', {
      name: 'validate_code',
      arguments: {source: 'fun screen() = Unit'},
    })));
  while (releases.length < MCP_PROTOCOL.limits.maxConcurrentRequests) await Promise.resolve();
  const rejected = await session.receive(request('too-many', 'tools/call', {
    name: 'validate_code',
    arguments: {source: 'fun screen() = Unit'},
  }));
  assert.equal(rejected.error.code, -31000);
  await Promise.all(releases.map((release) => release()));
  assert.equal((await Promise.all(pending)).every((response) => response.result.isError === false), true);
});

test('the stdio framing boundary rejects an oversized line before JSON parsing', async () => {
  let output = '';
  const writable = new Writable({
    write(chunk, _encoding, callback) {
      output += chunk.toString('utf8');
      callback();
    },
  });
  await serveStdio({
    input: Readable.from([
      Buffer.alloc(MCP_PROTOCOL.limits.maxMessageBytes + 1, 0x20),
      Buffer.from('\n'),
    ]),
    output: writable,
  });
  const response = JSON.parse(output.trim());
  assert.equal(response.error.code, -32600);
  assert.match(response.error.message, /exceeds the fixed limit/u);
});

function executeOneMessage(message) {
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(process.execPath, [executable], {stdio: ['pipe', 'pipe', 'pipe']});
    let stdout = '';
    let stderr = '';
    child.stderr.on('data', (chunk) => { stderr += chunk; });
    child.stdout.on('data', (chunk) => {
      stdout += chunk;
      const newline = stdout.indexOf('\n');
      if (newline === -1) return;
      child.stdin.end();
      child.once('close', (exitCode) => resolvePromise({
        exitCode,
        response: JSON.parse(stdout.slice(0, newline)),
        extraStdout: stdout.slice(newline + 1),
        stderr,
      }));
    });
    child.once('error', rejectPromise);
    child.stdin.write(`${JSON.stringify(message)}\n`);
  });
}

test('the executable stdio server writes only newline-delimited MCP to stdout', async () => {
  const execution = await executeOneMessage(request('process', 'server/discover'));
  assert.equal(execution.exitCode, 0);
  assert.equal(execution.extraStdout, '');
  assert.equal(execution.stderr, '');
  assert.equal(execution.response.result.resultType, 'complete');
});
