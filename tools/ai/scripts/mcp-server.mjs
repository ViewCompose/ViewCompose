#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {realpathSync} from 'node:fs';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createToolRequest, dispatchToolRequest} from './ai-tool.mjs';
import {publicToolDefinition, TOOL_DEFINITIONS, TOOL_NAMES} from './tool-catalog.mjs';
import {utf8Bytes} from './tool-core.mjs';

const contractPath = fileURLToPath(new URL('../contracts/mcp-protocol.json', import.meta.url));
export const MCP_PROTOCOL = Object.freeze(JSON.parse(await readFile(contractPath, 'utf8')));
const modernVersion = MCP_PROTOCOL.preferredVersion;
const legacyVersions = new Set(MCP_PROTOCOL.supportedVersions.filter((version) => version !== modernVersion));
const protocolVersionKey = 'io.modelcontextprotocol/protocolVersion';
const clientInfoKey = 'io.modelcontextprotocol/clientInfo';
const clientCapabilitiesKey = 'io.modelcontextprotocol/clientCapabilities';
const serverInfoKey = 'io.modelcontextprotocol/serverInfo';
const JSON_RPC = '2.0';

function rpcError(id, code, message, data) {
  return Object.fromEntries(Object.entries({
    jsonrpc: JSON_RPC,
    id,
    error: Object.fromEntries(Object.entries({code, message, data}).filter(([, value]) => value !== undefined)),
  }).filter(([, value]) => value !== undefined));
}

function rpcResult(id, result) {
  return {jsonrpc: JSON_RPC, id, result};
}

function unsupportedVersion(id, requested) {
  return rpcError(id, -32022, 'Unsupported protocol version', {
    supported: MCP_PROTOCOL.supportedVersions,
    requested,
  });
}

function serverMeta() {
  return {[serverInfoKey]: MCP_PROTOCOL.server};
}

function modernResult(fields) {
  return {resultType: 'complete', ...fields, _meta: {...(fields._meta ?? {}), ...serverMeta()}};
}

function requestKey(id) {
  return `${typeof id}:${JSON.stringify(id)}`;
}

export function mcpToolRequestId(id) {
  return `mcp:${createHash('sha256').update(JSON.stringify(id)).digest('hex').slice(0, 24)}`;
}

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function validateModernMetadata(params) {
  const meta = params?._meta;
  if (!isObject(meta)) return {error: 'Request params._meta must be an object.'};
  const version = meta[protocolVersionKey];
  if (typeof version !== 'string') return {error: `Request _meta.${protocolVersionKey} is required.`};
  if (!isObject(meta[clientCapabilitiesKey])) {
    return {error: `Request _meta.${clientCapabilitiesKey} must be an object.`};
  }
  if (meta[clientInfoKey] !== undefined && !isObject(meta[clientInfoKey])) {
    return {error: `Request _meta.${clientInfoKey} must be an object when present.`};
  }
  return {version, meta};
}

function progressNotification(token, progress, message) {
  return {
    jsonrpc: JSON_RPC,
    method: 'notifications/progress',
    params: {progressToken: token, progress, total: 1, message},
  };
}

function legacyInitializeResult(version) {
  return {
    protocolVersion: version,
    capabilities: {tools: {listChanged: false}},
    serverInfo: MCP_PROTOCOL.server,
    instructions: serverInstructions(),
  };
}

function serverInstructions() {
  return 'Use exact current-source ViewCompose knowledge first, then validate. ' +
    'Compiled and rendered evidence is labeled explicitly. The server is local, read-only with ' +
    'respect to the selected project, model-free, and has no network listener.';
}

function publicTools() {
  return TOOL_NAMES.map(publicToolDefinition);
}

function boundedResponse(response) {
  if (!response || utf8Bytes(JSON.stringify(response)) <= MCP_PROTOCOL.limits.maxMessageBytes) {
    return response;
  }
  return rpcError(response.id, -31001, 'MCP response exceeds the fixed stdio message limit.');
}

export class ViewComposeMcpSession {
  constructor({dispatch = dispatchToolRequest} = {}) {
    this.dispatch = dispatch;
    this.legacyState = 'uninitialized';
    this.legacyVersion = null;
    this.inFlight = new Map();
    this.closed = false;
  }

  close() {
    this.closed = true;
    for (const entry of this.inFlight.values()) {
      entry.cancelled = true;
      entry.controller.abort('stdio closed');
    }
  }

  async receive(message, emit = () => {}) {
    if (!isObject(message) || message.jsonrpc !== JSON_RPC || typeof message.method !== 'string') {
      return rpcError(undefined, -32600, 'Invalid JSON-RPC request.');
    }
    const notification = !Object.hasOwn(message, 'id');
    if (notification) {
      this.#receiveNotification(message);
      return null;
    }
    if (!['string', 'number'].includes(typeof message.id) || message.id === null) {
      return rpcError(undefined, -32600, 'JSON-RPC request id must be a string or number.');
    }
    if (this.closed) return rpcError(message.id, -31000, 'MCP stdio session is closing.');
    const key = requestKey(message.id);
    if (this.inFlight.has(key)) {
      return rpcError(message.id, -32600, 'JSON-RPC request id is already in flight.');
    }
    if (message.method === 'tools/call' && this.inFlight.size >= MCP_PROTOCOL.limits.maxConcurrentRequests) {
      return rpcError(message.id, -31000, 'MCP concurrent request limit reached.');
    }

    const entry = {controller: new AbortController(), cancelled: false};
    this.inFlight.set(key, entry);
    const guardedEmit = (outbound) => {
      if (!entry.cancelled && !this.closed) emit(boundedResponse(outbound));
    };
    try {
      const response = await this.#receiveRequest(message, guardedEmit, entry.controller.signal);
      return entry.cancelled || this.closed ? null : boundedResponse(response);
    } catch (error) {
      return entry.cancelled || this.closed
        ? null
        : rpcError(message.id, -32603, 'Internal MCP server error.', {
          category: error?.name === 'AbortError' ? 'cancelled' : 'unexpected',
        });
    } finally {
      this.inFlight.delete(key);
    }
  }

  #receiveNotification(message) {
    if (message.method === 'notifications/cancelled') {
      const id = message.params?.requestId;
      if (!['string', 'number'].includes(typeof id)) return;
      const entry = this.inFlight.get(requestKey(id));
      if (!entry) return;
      entry.cancelled = true;
      entry.controller.abort('client cancelled request');
      return;
    }
    if (message.method === 'notifications/initialized' && this.legacyState === 'initializing') {
      this.legacyState = 'initialized';
    }
  }

  async #receiveRequest(message, emit, signal) {
    if (message.method === 'initialize') return this.#initializeLegacy(message);

    const metadata = validateModernMetadata(message.params);
    let modern = false;
    if (!metadata.error) {
      if (!MCP_PROTOCOL.supportedVersions.includes(metadata.version)) {
        return unsupportedVersion(message.id, metadata.version);
      }
      modern = metadata.version === modernVersion;
      if (!modern && !legacyVersions.has(metadata.version)) {
        return unsupportedVersion(message.id, metadata.version);
      }
      if (!modern && this.legacyState !== 'initialized') {
        return rpcError(message.id, -32602, 'Legacy MCP requests require initialize/initialized first.');
      }
    } else if (this.legacyState !== 'initialized') {
      return rpcError(message.id, -32602, metadata.error, {
        supported: MCP_PROTOCOL.supportedVersions,
      });
    }

    if (message.method === 'server/discover') {
      if (metadata.error || !modern) {
        return metadata.error
          ? rpcError(message.id, -32602, metadata.error)
          : unsupportedVersion(message.id, metadata.version);
      }
      return rpcResult(message.id, modernResult({
        supportedVersions: MCP_PROTOCOL.supportedVersions,
        capabilities: {tools: {listChanged: false}},
        instructions: serverInstructions(),
        ttlMs: 3600000,
        cacheScope: 'public',
      }));
    }
    if (message.method === 'ping') {
      return rpcResult(message.id, modern ? modernResult({}) : {});
    }
    if (message.method === 'tools/list') {
      if (message.params?.cursor !== undefined) {
        return rpcError(message.id, -32602, 'This deterministic tool catalog has one page.');
      }
      const result = {
        tools: publicTools(),
        ttlMs: 3600000,
        cacheScope: 'public',
      };
      return rpcResult(message.id, modern ? modernResult(result) : result);
    }
    if (message.method === 'tools/call') {
      return this.#callTool(message, modern, emit, signal);
    }
    return rpcError(message.id, -32601, `Method not found: ${message.method}`);
  }

  #initializeLegacy(message) {
    const params = message.params;
    if (
      !isObject(params) ||
      typeof params.protocolVersion !== 'string' ||
      !isObject(params.capabilities) ||
      !isObject(params.clientInfo)
    ) {
      return rpcError(message.id, -32602, 'Legacy initialize params are malformed.', {
        supported: MCP_PROTOCOL.supportedVersions,
      });
    }
    if (!legacyVersions.has(params.protocolVersion)) {
      return unsupportedVersion(message.id, params.protocolVersion);
    }
    this.legacyState = 'initializing';
    this.legacyVersion = params.protocolVersion;
    return rpcResult(message.id, legacyInitializeResult(params.protocolVersion));
  }

  async #callTool(message, modern, emit, signal) {
    const params = message.params;
    if (!isObject(params) || typeof params.name !== 'string' || !isObject(params.arguments ?? {})) {
      return rpcError(message.id, -32602, 'tools/call requires a tool name and object arguments.');
    }
    const definition = TOOL_DEFINITIONS[params.name];
    if (!definition) return rpcError(message.id, -32602, `Unknown tool: ${params.name}`);

    const token = params._meta?.progressToken;
    const progressRequested = typeof token === 'string' || typeof token === 'number';
    if (progressRequested) emit(progressNotification(token, 0, `Running ${params.name}.`));
    const request = await createToolRequest({
      tool: params.name,
      arguments: params.arguments ?? {},
      requestId: mcpToolRequestId(message.id),
    });
    const result = await this.dispatch(request, {signal});
    if (progressRequested && !signal.aborted) {
      emit(progressNotification(token, 1, `${params.name} completed.`));
    }
    return rpcResult(message.id, this.#toolResult(modern, result));
  }

  #toolResult(modern, result) {
    const fields = {
      content: [{type: 'text', text: JSON.stringify(result)}],
      structuredContent: result,
      isError: result.status !== 'success',
    };
    return modern ? modernResult(fields) : fields;
  }
}

function writeMessage(output, message) {
  if (!message) return;
  output.write(`${JSON.stringify(message)}\n`);
}

export async function serveStdio({input = process.stdin, output = process.stdout} = {}) {
  const session = new ViewComposeMcpSession();
  const pending = new Set();
  let buffered = Buffer.alloc(0);
  let discardingOversizedLine = false;

  const processLine = (line) => {
    if (line.length === 0) return;
    const task = Promise.resolve().then(async () => {
      let message;
      try {
        message = JSON.parse(line.toString('utf8'));
      } catch {
        writeMessage(output, rpcError(undefined, -32700, 'Parse error.'));
        return;
      }
      writeMessage(output, await session.receive(message, (outbound) => writeMessage(output, outbound)));
    }).catch(() => {
      writeMessage(output, rpcError(undefined, -32603, 'Internal MCP server error.'));
    }).finally(() => pending.delete(task));
    pending.add(task);
  };

  for await (const chunk of input) {
    let remaining = Buffer.from(chunk);
    while (remaining.length > 0) {
      const newline = remaining.indexOf(0x0a);
      const segment = newline === -1 ? remaining : remaining.subarray(0, newline);
      remaining = newline === -1 ? Buffer.alloc(0) : remaining.subarray(newline + 1);
      if (!discardingOversizedLine) {
        if (buffered.length + segment.length > MCP_PROTOCOL.limits.maxMessageBytes) {
          buffered = Buffer.alloc(0);
          discardingOversizedLine = true;
        } else {
          buffered = Buffer.concat([buffered, segment]);
        }
      }
      if (newline !== -1) {
        if (discardingOversizedLine) {
          writeMessage(output, rpcError(undefined, -32600, 'MCP stdio message exceeds the fixed limit.'));
        } else {
          if (buffered.at(-1) === 0x0d) buffered = buffered.subarray(0, -1);
          processLine(buffered);
        }
        buffered = Buffer.alloc(0);
        discardingOversizedLine = false;
      }
    }
  }
  session.close();
  await Promise.allSettled(pending);
}

const entryPath = process.argv[1] ? realpathSync(resolve(process.argv[1])) : '';
if (entryPath === fileURLToPath(import.meta.url)) {
  serveStdio().catch((error) => {
    process.stderr.write(`ViewCompose MCP server stopped (${error?.name ?? 'Error'}).\n`);
    process.exitCode = 2;
  });
}
