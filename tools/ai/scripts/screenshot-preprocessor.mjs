import {createHash} from 'node:crypto';
import {deflateSync, inflateSync} from 'node:zlib';
import {assertSchemaValue, validateSchemaValue} from './schema-validator.mjs';
import {
  canonicalJson,
  SCREENSHOT_PREPROCESSING_SCHEMA,
  SCREENSHOT_REQUEST_SCHEMA,
} from './screenshot-contract.mjs';
import {diagnostic, toolResult} from './tool-core.mjs';

export const SCREENSHOT_PREPROCESSING_LIMITS = Object.freeze({
  maxCompressedBytes: 1_310_720,
  maxDimensionPx: 4096,
  maxDecodedBytes: 16 * 1024 * 1024,
  maxPngChunks: 256,
  maxRedactions: 64,
  maxOutputBytes: 1_310_720,
});

const PNG_SIGNATURE = Buffer.from('89504e470d0a1a0a', 'hex');
const CRC_TABLE = new Uint32Array(256);
for (let index = 0; index < CRC_TABLE.length; index += 1) {
  let value = index;
  for (let bit = 0; bit < 8; bit += 1) {
    value = (value >>> 1) ^ (value & 1 ? 0xedb88320 : 0);
  }
  CRC_TABLE[index] = value >>> 0;
}

class ScreenshotPreprocessingError extends Error {
  constructor(code, message, nextAction, status = 'invalid') {
    super(message);
    this.code = code;
    this.nextAction = nextAction;
    this.status = status;
  }
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function fail(code, message, nextAction, status) {
  throw new ScreenshotPreprocessingError(code, message, nextAction, status);
}

function throwIfCancelled(signal) {
  if (signal?.aborted) {
    fail(
      'VC-AI-SCREENSHOT-CANCELLED',
      'Screenshot preprocessing was cancelled before a result was accepted.',
      'Retry the same immutable screenshot request when preprocessing is still required.',
      'cancelled',
    );
  }
}

function schemaFailure(request, violations) {
  if (request?.screenshot && ['path', 'url', 'uri'].some((key) =>
    Object.hasOwn(request.screenshot, key))) {
    return new ScreenshotPreprocessingError(
      'VC-AI-SCREENSHOT-PATH-DENIED',
      'Screenshot preprocessing does not accept paths, URLs, or URIs.',
      'Embed one integrity-declared PNG as canonical base64.',
    );
  }
  if (request?.privacy?.providerTransfer === true) {
    return new ScreenshotPreprocessingError(
      'VC-AI-SCREENSHOT-PROVIDER-TRANSFER-DENIED',
      'The deterministic screenshot preprocessor cannot transfer input to a provider.',
      'Set providerTransfer to false and run provider transfer only through a separately reviewed adapter.',
    );
  }
  const asset = request?.screenshot;
  if (
    asset?.bytes > SCREENSHOT_PREPROCESSING_LIMITS.maxCompressedBytes ||
    asset?.widthPx > SCREENSHOT_PREPROCESSING_LIMITS.maxDimensionPx ||
    asset?.heightPx > SCREENSHOT_PREPROCESSING_LIMITS.maxDimensionPx ||
    request?.privacy?.redactions?.length > SCREENSHOT_PREPROCESSING_LIMITS.maxRedactions
  ) {
    return new ScreenshotPreprocessingError(
      'VC-AI-SCREENSHOT-LIMIT',
      'Screenshot input exceeds the frozen preprocessing limits.',
      'Reduce the PNG bytes, dimensions, or explicit redaction count.',
      'limited',
    );
  }
  return new ScreenshotPreprocessingError(
    'VC-AI-SCREENSHOT-INPUT-INVALID',
    `Screenshot request violates the frozen schema: ${violations.slice(0, 3).join('; ')}`,
    'Use the exact screenshot preprocessing v1 request contract.',
  );
}

function parsePng(asset, signal) {
  const bytes = Buffer.from(asset.data, 'base64');
  if (
    bytes.toString('base64') !== asset.data ||
    bytes.length !== asset.bytes ||
    bytes.length > SCREENSHOT_PREPROCESSING_LIMITS.maxCompressedBytes ||
    sha256(bytes) !== asset.sha256 ||
    bytes.length < 33 ||
    !bytes.subarray(0, PNG_SIGNATURE.length).equals(PNG_SIGNATURE)
  ) {
    fail(
      'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID',
      'Screenshot PNG bytes, canonical base64, declared size, signature, or SHA-256 do not match.',
      'Re-embed the unchanged PNG with its exact byte count, SHA-256, and dimensions.',
    );
  }

  let cursor = PNG_SIGNATURE.length;
  let sawIhdr = false;
  let sawIdat = false;
  let idatEnded = false;
  let sawIend = false;
  let sawSrgb = false;
  let chunkCount = 0;
  const idatParts = [];
  while (cursor < bytes.length) {
    throwIfCancelled(signal);
    if (cursor + 12 > bytes.length) {
      fail(
        'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID',
        'Screenshot PNG contains a truncated chunk header.',
        'Provide a complete PNG whose every chunk passes its CRC.',
      );
    }
    const length = bytes.readUInt32BE(cursor);
    const typeStart = cursor + 4;
    const dataStart = typeStart + 4;
    const dataEnd = dataStart + length;
    const chunkEnd = dataEnd + 4;
    if (chunkEnd > bytes.length) {
      fail(
        'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID',
        'Screenshot PNG chunk length exceeds the embedded bytes.',
        'Provide a complete PNG whose declared chunk sizes match its bytes.',
      );
    }
    const typeBytes = bytes.subarray(typeStart, dataStart);
    const type = typeBytes.toString('ascii');
    if (!/^[A-Za-z]{4}$/u.test(type) || crc32(bytes.subarray(typeStart, dataEnd)) !==
      bytes.readUInt32BE(dataEnd)) {
      fail(
        'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID',
        'Screenshot PNG contains an invalid chunk type or CRC.',
        'Provide a PNG with valid four-letter chunk types and CRC values.',
      );
    }
    chunkCount += 1;
    if (chunkCount > SCREENSHOT_PREPROCESSING_LIMITS.maxPngChunks) {
      fail(
        'VC-AI-SCREENSHOT-LIMIT',
        'Screenshot PNG exceeds the frozen chunk-count limit.',
        'Strip nonessential PNG chunks before embedding the image.',
        'limited',
      );
    }

    if (type === 'IHDR') {
      if (sawIhdr || chunkCount !== 1 || length !== 13) {
        fail(
          'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID',
          'Screenshot PNG must begin with exactly one 13-byte IHDR.',
          'Re-encode the image as a standards-conforming PNG.',
        );
      }
      sawIhdr = true;
      const width = bytes.readUInt32BE(dataStart);
      const height = bytes.readUInt32BE(dataStart + 4);
      if (
        width !== asset.widthPx ||
        height !== asset.heightPx ||
        width === 0 || height === 0 ||
        width > SCREENSHOT_PREPROCESSING_LIMITS.maxDimensionPx ||
        height > SCREENSHOT_PREPROCESSING_LIMITS.maxDimensionPx
      ) {
        fail(
          'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID',
          'Screenshot PNG IHDR dimensions do not match the bounded declaration.',
          'Declare the exact non-zero PNG width and height within 4,096 pixels.',
        );
      }
      if (
        bytes[dataStart + 8] !== 8 ||
        bytes[dataStart + 9] !== 6 ||
        bytes[dataStart + 10] !== 0 ||
        bytes[dataStart + 11] !== 0 ||
        bytes[dataStart + 12] !== 0
      ) {
        fail(
          'VC-AI-SCREENSHOT-PNG-UNSUPPORTED',
          'Screenshot preprocessing v1 supports only non-interlaced 8-bit RGBA PNG.',
          'Re-encode the screenshot as non-interlaced 8-bit straight-alpha RGBA PNG.',
          'unsupported',
        );
      }
    } else if (!sawIhdr) {
      fail(
        'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID',
        'Screenshot PNG contains data before IHDR.',
        'Re-encode the image as a standards-conforming PNG.',
      );
    } else if (type === 'IDAT') {
      if (idatEnded || sawIend) {
        fail(
          'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID',
          'Screenshot PNG IDAT chunks must be consecutive and precede IEND.',
          'Re-encode the image with one consecutive IDAT sequence.',
        );
      }
      sawIdat = true;
      idatParts.push(bytes.subarray(dataStart, dataEnd));
    } else if (type === 'IEND') {
      if (!sawIdat || sawIend || length !== 0 || chunkEnd !== bytes.length) {
        fail(
          'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID',
          'Screenshot PNG must end with one empty IEND after IDAT.',
          'Provide a complete PNG with no bytes after IEND.',
        );
      }
      sawIend = true;
    } else {
      if (sawIdat) idatEnded = true;
      if (typeBytes[0] >= 0x41 && typeBytes[0] <= 0x5a) {
        fail(
          'VC-AI-SCREENSHOT-PNG-UNSUPPORTED',
          `Screenshot preprocessing v1 does not support critical PNG chunk ${type}.`,
          'Re-encode the screenshot as a plain 8-bit RGBA PNG.',
          'unsupported',
        );
      }
      if (type === 'sRGB') {
        if (sawSrgb || length !== 1 || bytes[dataStart] > 3) {
          fail(
            'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID',
            'Screenshot PNG contains an invalid or duplicate sRGB rendering-intent chunk.',
            'Re-encode the screenshot with at most one valid sRGB chunk.',
          );
        }
        sawSrgb = true;
      }
      if (['iCCP', 'cHRM', 'gAMA', 'cICP', 'mDCV', 'cLLI', 'tRNS', 'acTL', 'fcTL', 'fdAT']
        .includes(type)) {
        fail(
          'VC-AI-SCREENSHOT-PNG-UNSUPPORTED',
          'Screenshot PNG carries unsupported color, transparency, or animation semantics.',
          'Flatten and convert the screenshot to one non-animated straight-alpha sRGB RGBA image.',
          'unsupported',
        );
      }
    }
    cursor = chunkEnd;
  }
  if (!sawIhdr || !sawIdat || !sawIend) {
    fail(
      'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID',
      'Screenshot PNG is missing required IHDR, IDAT, or IEND data.',
      'Provide a complete standards-conforming PNG.',
    );
  }

  const pixelBytes = asset.widthPx * asset.heightPx * 4;
  const rowBytes = asset.widthPx * 4;
  const filteredBytes = pixelBytes + asset.heightPx;
  if (pixelBytes > SCREENSHOT_PREPROCESSING_LIMITS.maxDecodedBytes) {
    fail(
      'VC-AI-SCREENSHOT-LIMIT',
      'Decoded screenshot pixels exceed the frozen 16 MiB limit.',
      'Crop or downscale the screenshot before embedding it.',
      'limited',
    );
  }
  let inflated;
  try {
    const compressed = Buffer.concat(idatParts);
    const decoded = inflateSync(compressed, {maxOutputLength: filteredBytes, info: true});
    if (decoded.engine.bytesWritten !== compressed.length) throw new Error('trailing IDAT bytes');
    inflated = decoded.buffer;
  } catch {
    fail(
      'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID',
      'Screenshot PNG IDAT data cannot be decoded within its declared dimensions.',
      'Re-encode the unchanged screenshot and update its integrity declaration.',
    );
  }
  if (inflated.length !== filteredBytes) {
    fail(
      'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID',
      'Screenshot PNG decoded byte count does not match its dimensions.',
      'Re-encode the screenshot as non-interlaced 8-bit RGBA PNG.',
    );
  }

  const pixels = Buffer.alloc(pixelBytes);
  for (let y = 0; y < asset.heightPx; y += 1) {
    throwIfCancelled(signal);
    const filteredOffset = y * (rowBytes + 1);
    const filterType = inflated[filteredOffset];
    if (filterType > 4) {
      fail(
        'VC-AI-SCREENSHOT-PNG-UNSUPPORTED',
        `Screenshot PNG uses unsupported filter type ${filterType}.`,
        'Re-encode the screenshot with PNG filter types 0 through 4.',
        'unsupported',
      );
    }
    const rowOffset = y * rowBytes;
    for (let x = 0; x < rowBytes; x += 1) {
      const encoded = inflated[filteredOffset + 1 + x];
      const left = x >= 4 ? pixels[rowOffset + x - 4] : 0;
      const above = y > 0 ? pixels[rowOffset - rowBytes + x] : 0;
      const upperLeft = y > 0 && x >= 4 ? pixels[rowOffset - rowBytes + x - 4] : 0;
      let predictor = 0;
      if (filterType === 1) predictor = left;
      if (filterType === 2) predictor = above;
      if (filterType === 3) predictor = Math.floor((left + above) / 2);
      if (filterType === 4) {
        const estimate = left + above - upperLeft;
        const leftDistance = Math.abs(estimate - left);
        const aboveDistance = Math.abs(estimate - above);
        const upperLeftDistance = Math.abs(estimate - upperLeft);
        predictor = leftDistance <= aboveDistance && leftDistance <= upperLeftDistance
          ? left
          : aboveDistance <= upperLeftDistance ? above : upperLeft;
      }
      pixels[rowOffset + x] = (encoded + predictor) & 0xff;
    }
  }
  return pixels;
}

function cropPixels(pixels, sourceWidth, rectangle) {
  const output = Buffer.alloc(rectangle.width * rectangle.height * 4);
  const rowBytes = rectangle.width * 4;
  for (let y = 0; y < rectangle.height; y += 1) {
    const sourceOffset = ((rectangle.y + y) * sourceWidth + rectangle.x) * 4;
    pixels.copy(output, y * rowBytes, sourceOffset, sourceOffset + rowBytes);
  }
  return output;
}

function applyRedactions(pixels, width, height, redactions, signal) {
  for (const redaction of redactions) {
    throwIfCancelled(signal);
    const rectangle = redaction.rectangle;
    if (rectangle.x + rectangle.width > width || rectangle.y + rectangle.height > height) {
      fail(
        'VC-AI-SCREENSHOT-REDACTION-INVALID',
        'An explicit redaction rectangle leaves the cropped screenshot.',
        'Express every redaction in bounded cropped-output pixel coordinates.',
      );
    }
    for (let y = rectangle.y; y < rectangle.y + rectangle.height; y += 1) {
      for (let x = rectangle.x; x < rectangle.x + rectangle.width; x += 1) {
        pixels.set([0, 0, 0, 255], (y * width + x) * 4);
      }
    }
  }
}

function pngChunk(type, data) {
  const typeBytes = Buffer.from(type, 'ascii');
  const chunk = Buffer.alloc(12 + data.length);
  chunk.writeUInt32BE(data.length, 0);
  typeBytes.copy(chunk, 4);
  data.copy(chunk, 8);
  chunk.writeUInt32BE(crc32(chunk.subarray(4, 8 + data.length)), 8 + data.length);
  return chunk;
}

function encodePng(pixels, width, height) {
  const rowBytes = width * 4;
  const filtered = Buffer.alloc(pixels.length + height);
  for (let y = 0; y < height; y += 1) {
    const filteredOffset = y * (rowBytes + 1);
    filtered[filteredOffset] = 0;
    pixels.copy(filtered, filteredOffset + 1, y * rowBytes, (y + 1) * rowBytes);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr.set([8, 6, 0, 0, 0], 8);
  const encoded = Buffer.concat([
    PNG_SIGNATURE,
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', deflateSync(filtered, {level: 9})),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
  if (encoded.length > SCREENSHOT_PREPROCESSING_LIMITS.maxOutputBytes) {
    fail(
      'VC-AI-SCREENSHOT-LIMIT',
      'Canonical screenshot output exceeds the frozen compressed-byte limit.',
      'Crop the screenshot to a smaller bounded region.',
      'limited',
    );
  }
  return encoded;
}

async function screenshotFailure({requestId, error, elapsedMs}) {
  return toolResult({
    requestId,
    tool: 'prepare_screenshot',
    status: error.status ?? 'failed',
    level: 'static',
    diagnostics: [diagnostic({
      code: error.code ?? 'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID',
      severity: 'error',
      message: error instanceof ScreenshotPreprocessingError
        ? error.message
        : 'Screenshot preprocessing failed before deterministic output was accepted.',
      nextAction: error instanceof ScreenshotPreprocessingError
        ? error.nextAction
        : 'Verify the frozen PNG input contract and retry the immutable request.',
    })],
    elapsedMs,
  });
}

export async function prepareScreenshot(request, {
  requestId = 'prepare-screenshot',
  signal,
} = {}) {
  const started = performance.now();
  try {
    throwIfCancelled(signal);
    const violations = validateSchemaValue(request, SCREENSHOT_REQUEST_SCHEMA);
    if (violations.length > 0) throw schemaFailure(request, violations);
    const inputPixels = parsePng(request.screenshot, signal);
    const crop = request.interpretation.crop;
    if (
      crop.x + crop.width > request.screenshot.widthPx ||
      crop.y + crop.height > request.screenshot.heightPx ||
      crop.width > request.output.maxWidthPx ||
      crop.height > request.output.maxHeightPx ||
      request.interpretation.systemBars.leftPx + request.interpretation.systemBars.rightPx >
        request.screenshot.widthPx ||
      request.interpretation.systemBars.topPx + request.interpretation.systemBars.bottomPx >
        request.screenshot.heightPx
    ) {
      fail(
        'VC-AI-SCREENSHOT-CROP-INVALID',
        'Screenshot crop, output bounds, or declared system-bar insets leave the source image.',
        'Use bounded source-image pixel coordinates and output dimensions.',
      );
    }
    const outputPixels = cropPixels(inputPixels, request.screenshot.widthPx, crop);
    applyRedactions(
      outputPixels,
      crop.width,
      crop.height,
      request.privacy.redactions,
      signal,
    );
    throwIfCancelled(signal);
    const outputBytes = encodePng(outputPixels, crop.width, crop.height);
    const requestFingerprint = sha256(canonicalJson(request));
    const result = {
      schemaVersion: 1,
      kind: 'result',
      status: 'success',
      requestFingerprint,
      input: {
        sha256: request.screenshot.sha256,
        widthPx: request.screenshot.widthPx,
        heightPx: request.screenshot.heightPx,
      },
      output: {
        mediaType: 'image/png',
        encoding: 'base64',
        data: outputBytes.toString('base64'),
        bytes: outputBytes.length,
        sha256: sha256(outputBytes),
        widthPx: crop.width,
        heightPx: crop.height,
      },
      transformations: [
        {kind: 'crop', rectangle: crop},
        ...request.privacy.redactions.map((redaction) => ({
          kind: 'redact',
          rectangle: redaction.rectangle,
          replacement: redaction.replacement,
        })),
        {kind: 'strip-metadata'},
      ],
      privacy: {
        redactionsApplied: request.privacy.redactions.length,
        providerTransfer: false,
        inputPersisted: false,
        logs: 'metadata-only',
      },
      diagnostics: [],
    };
    result.outputFingerprint = sha256(canonicalJson(result));
    assertSchemaValue(result, SCREENSHOT_PREPROCESSING_SCHEMA, 'screenshot preprocessing result');
    return toolResult({
      requestId,
      tool: 'prepare_screenshot',
      status: 'success',
      level: 'static',
      outputFingerprint: result.outputFingerprint,
      diagnostics: [],
      data: result,
      elapsedMs: performance.now() - started,
    });
  } catch (error) {
    return screenshotFailure({requestId, error, elapsedMs: performance.now() - started});
  }
}
