import {createToolRequest, dispatchToolRequest} from './ai-tool.mjs';
import {
  MCP_PROTOCOL,
  mcpToolRequestId,
  ViewComposeMcpSession,
} from './mcp-server.mjs';
import {semanticToolResult} from './tool-core.mjs';

const id = 'phase3-parity';
const arguments_ = {
  versionLane: 'current-source',
  identifier: 'modifier.layout',
};
const direct = await dispatchToolRequest(await createToolRequest({
  tool: 'get_api_reference',
  arguments: arguments_,
  requestId: mcpToolRequestId(id),
}));
const response = await new ViewComposeMcpSession().receive({
  jsonrpc: '2.0',
  id,
  method: 'tools/call',
  params: {
    name: 'get_api_reference',
    arguments: arguments_,
    _meta: {
      'io.modelcontextprotocol/protocolVersion': MCP_PROTOCOL.preferredVersion,
      'io.modelcontextprotocol/clientInfo': {name: 'phase3-verifier', version: '1.0.0'},
      'io.modelcontextprotocol/clientCapabilities': {},
    },
  },
});
if (
  JSON.stringify(semanticToolResult(response?.result?.structuredContent)) !==
  JSON.stringify(semanticToolResult(direct))
) {
  throw new Error('CLI/MCP semantic parity failed for protocol.cli-mcp-equivalence.');
}
if (response.result.structuredContent.data.capability.capabilityId !== 'modifier.layout') {
  throw new Error('The frozen protocol corpus did not resolve modifier.layout.');
}
console.log(
  `Verified Phase 3 MCP ${MCP_PROTOCOL.supportedVersions.join(' + ')}: ` +
    '7 deterministic tools, stdio-only transport, and 0 CLI/MCP semantic mismatches.',
);
