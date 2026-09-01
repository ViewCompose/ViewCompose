import {readFile} from 'node:fs/promises';

const figmaImportSchemaPath = new URL('../contracts/figma-import.schema.json', import.meta.url);
const designIrV2SchemaPath = new URL('../contracts/design-ir-v2.schema.json', import.meta.url);

export const FIGMA_IMPORT_LIMITS = Object.freeze({
  maxArgumentsBytes: 3 * 1024 * 1024,
  maxResultBytes: 3 * 1024 * 1024,
  maxTransportBytes: 4 * 1024 * 1024,
  maxJsonDepth: 48,
  maxSelectedRoots: 64,
  maxNodes: 2048,
  maxChildReferences: 8192,
  maxComponents: 512,
  maxTokens: 1024,
  maxStyles: 512,
  maxFonts: 64,
  maxAssets: 64,
  maxAssetBytes: 512 * 1024,
  maxAssetBytesTotal: 1024 * 1024,
  maxIdBytes: 256,
  maxStringBytes: 32768,
  maxVectorCommands: 4096,
  maxVectorCommandsTotal: 32768,
  maxUnsupported: 1024,
  maxDiagnostics: 256,
  maxEvidenceMismatches: 512,
  maxInspectIrBytes: 2 * 1024 * 1024,
  maxInspectReportBytes: 512 * 1024,
  maxVirtualFiles: 128,
  maxVirtualFileBytes: 512 * 1024,
  maxVirtualFileBytesTotal: 1280 * 1024,
  maxVerificationBytes: 512 * 1024,
});

export const FIGMA_IMPORT_DIAGNOSTICS = Object.freeze([
  'VC-AI-FIGMA-CONTRACT-INVALID',
  'VC-AI-FIGMA-VERSION-UNSUPPORTED',
  'VC-AI-FIGMA-LIMIT-EXCEEDED',
  'VC-AI-FIGMA-SECURITY-FORBIDDEN-REFERENCE',
  'VC-AI-FIGMA-SECURITY-ACTIVE-CONTENT',
  'VC-AI-FIGMA-PATH-INVALID',
  'VC-AI-FIGMA-INTEGRITY-MISMATCH',
  'VC-AI-FIGMA-DECLARATION-MISSING',
  'VC-AI-FIGMA-REFERENCE-UNRESOLVED',
  'VC-AI-FIGMA-GRAPH-INVALID',
  'VC-AI-FIGMA-MAPPING-UNSUPPORTED',
  'VC-AI-FIGMA-PROFILE-UNAVAILABLE',
  'VC-AI-FIGMA-GENERATION-FAILED',
  'VC-AI-FIGMA-VERIFICATION-FAILED',
]);

export const FIGMA_IMPORT_SCHEMA = Object.freeze(
  JSON.parse(await readFile(figmaImportSchemaPath, 'utf8')),
);

export const DESIGN_IR_V2_SCHEMA = Object.freeze(
  JSON.parse(await readFile(designIrV2SchemaPath, 'utf8')),
);

export const FIGMA_IMPORT_REQUEST_SCHEMA = Object.freeze({
  type: 'object',
  ...structuredClone(FIGMA_IMPORT_SCHEMA.$defs.request),
  $defs: structuredClone(FIGMA_IMPORT_SCHEMA.$defs),
});

export const FIGMA_EXPORT_SCHEMA = Object.freeze({
  ...structuredClone(FIGMA_IMPORT_SCHEMA.$defs.export),
  $defs: structuredClone(FIGMA_IMPORT_SCHEMA.$defs),
});
