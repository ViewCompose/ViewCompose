import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {deflateSync} from 'node:zlib';
import test from 'node:test';
import {prepareScreenshot} from './screenshot-preprocessor.mjs';

const requestFixture = new URL(
  '../evaluation/fixtures/visual/screenshot/privacy-grid.request.json',
  import.meta.url,
);
const resultFixture = new URL(
  '../evaluation/fixtures/visual/screenshot/privacy-grid.result.json',
  import.meta.url,
);
const pathFixture = new URL(
  '../evaluation/fixtures/visual/screenshot/path-input.request.json',
  import.meta.url,
);
const providerFixture = new URL(
  '../evaluation/fixtures/visual/screenshot/provider-transfer.request.json',
  import.meta.url,
);
const PNG_SIGNATURE = Buffer.from('89504e470d0a1a0a', 'hex');

const CRC_TABLE = new Uint32Array(256);
for (let index = 0; index < CRC_TABLE.length; index += 1) {
  let value = index;
  for (let bit = 0; bit < 8; bit += 1) {
    value = (value >>> 1) ^ (value & 1 ? 0xedb88320 : 0);
  }
  CRC_TABLE[index] = value >>> 0;
}

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const result = Buffer.alloc(12 + data.length);
  result.writeUInt32BE(data.length, 0);
  result.write(type, 4, 'ascii');
  data.copy(result, 8);
  result.writeUInt32BE(crc32(result.subarray(4, 8 + data.length)), 8 + data.length);
  return result;
}

function paeth(left, above, upperLeft) {
  const estimate = left + above - upperLeft;
  const distances = [
    Math.abs(estimate - left),
    Math.abs(estimate - above),
    Math.abs(estimate - upperLeft),
  ];
  const minimum = Math.min(...distances);
  return distances[0] === minimum ? left : distances[1] === minimum ? above : upperLeft;
}

function filteredRows(pixels, width, height, filters) {
  const rowBytes = width * 4;
  const filtered = Buffer.alloc(pixels.length + height);
  for (let y = 0; y < height; y += 1) {
    const filter = filters[y % filters.length];
    const outputOffset = y * (rowBytes + 1);
    const rowOffset = y * rowBytes;
    filtered[outputOffset] = filter;
    for (let x = 0; x < rowBytes; x += 1) {
      const left = x >= 4 ? pixels[rowOffset + x - 4] : 0;
      const above = y > 0 ? pixels[rowOffset - rowBytes + x] : 0;
      const upperLeft = y > 0 && x >= 4 ? pixels[rowOffset - rowBytes + x - 4] : 0;
      const predictor = [
        0,
        left,
        above,
        Math.floor((left + above) / 2),
        paeth(left, above, upperLeft),
      ][filter] ?? 0;
      filtered[outputOffset + 1 + x] = (pixels[rowOffset + x] - predictor) & 0xff;
    }
  }
  return filtered;
}

function makePng({
  width,
  height,
  pixels,
  filters = [0],
  colorType = 6,
  interlace = 0,
  ancillary = [],
}) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr.set([8, colorType, 0, 0, interlace], 8);
  return Buffer.concat([
    PNG_SIGNATURE,
    chunk('IHDR', ihdr),
    ...ancillary.map(({type, data}) => chunk(type, data)),
    chunk('IDAT', deflateSync(filteredRows(pixels, width, height, filters), {level: 9})),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

function embeddedAsset(bytes, width, height) {
  return {
    mediaType: 'image/png',
    encoding: 'base64',
    data: bytes.toString('base64'),
    bytes: bytes.length,
    sha256: createHash('sha256').update(bytes).digest('hex'),
    widthPx: width,
    heightPx: height,
  };
}

async function fixture(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function pngChunkTypes(data) {
  const bytes = Buffer.from(data, 'base64');
  const types = [];
  let cursor = 8;
  while (cursor < bytes.length) {
    const length = bytes.readUInt32BE(cursor);
    types.push(bytes.subarray(cursor + 4, cursor + 8).toString('ascii'));
    cursor += 12 + length;
  }
  return types;
}

test('reproduces the exact privacy-grid golden and canonical fingerprints', async () => {
  const request = await fixture(requestFixture);
  const expected = await fixture(resultFixture);
  const first = await prepareScreenshot(request, {requestId: 'screenshot-golden-1'});
  const second = await prepareScreenshot(request, {requestId: 'screenshot-golden-2'});
  assert.equal(first.status, 'success');
  assert.deepEqual(first.data, expected);
  assert.deepEqual(second.data, expected);
  assert.equal(first.evidence.outputFingerprint, expected.outputFingerprint);
  assert.deepEqual(pngChunkTypes(first.data.output.data), ['IHDR', 'IDAT', 'IEND']);

  const reordered = {
    output: request.output,
    privacy: request.privacy,
    interpretation: request.interpretation,
    screenshot: request.screenshot,
    source: request.source,
    kind: request.kind,
    schemaVersion: request.schemaVersion,
  };
  const reorderedResult = await prepareScreenshot(reordered, {requestId: 'screenshot-reordered'});
  assert.equal(reorderedResult.data.requestFingerprint, expected.requestFingerprint);
  assert.equal(reorderedResult.data.outputFingerprint, expected.outputFingerprint);
});

test('decodes every PNG filter and strips ancillary metadata from canonical output', async () => {
  const request = await fixture(requestFixture);
  const width = 2;
  const height = 5;
  const pixels = Buffer.from(Array.from({length: width * height * 4}, (_, index) =>
    (index * 37 + 11) & 0xff));
  const png = makePng({
    width,
    height,
    pixels,
    filters: [0, 1, 2, 3, 4],
    ancillary: [
      {type: 'sRGB', data: Buffer.from([0])},
      {type: 'tEXt', data: Buffer.from('private=metadata', 'utf8')},
    ],
  });
  request.screenshot = embeddedAsset(png, width, height);
  request.interpretation.crop = {x: 0, y: 0, width, height};
  request.interpretation.systemBars = {leftPx: 0, topPx: 0, rightPx: 0, bottomPx: 0};
  request.privacy.redactions = [];
  request.output.maxWidthPx = width;
  request.output.maxHeightPx = height;

  const result = await prepareScreenshot(request, {requestId: 'screenshot-filters'});
  assert.equal(result.status, 'success');
  assert.deepEqual(pngChunkTypes(result.data.output.data), ['IHDR', 'IDAT', 'IEND']);
  assert.equal(
    Buffer.from(result.data.output.data, 'base64').equals(makePng({
      width,
      height,
      pixels,
      filters: [0],
    })),
    true,
  );
  assert.equal(Buffer.from(result.data.output.data, 'base64').includes('private=metadata'), false);
});

test('fails closed on external references and provider transfer', async () => {
  const path = await prepareScreenshot(await fixture(pathFixture), {requestId: 'screenshot-path'});
  assert.equal(path.status, 'invalid');
  assert.equal(path.diagnostics[0].code, 'VC-AI-SCREENSHOT-PATH-DENIED');

  const provider = await prepareScreenshot(await fixture(providerFixture), {
    requestId: 'screenshot-provider',
  });
  assert.equal(provider.status, 'invalid');
  assert.equal(
    provider.diagnostics[0].code,
    'VC-AI-SCREENSHOT-PROVIDER-TRANSFER-DENIED',
  );
});

test('rejects changed identity and validly rehashed corrupt PNG chunks', async () => {
  const changedIdentity = await fixture(requestFixture);
  changedIdentity.screenshot.sha256 = '0'.repeat(64);
  const identity = await prepareScreenshot(changedIdentity, {requestId: 'screenshot-sha'});
  assert.equal(identity.status, 'invalid');
  assert.equal(identity.diagnostics[0].code, 'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID');

  const corruptRequest = await fixture(requestFixture);
  const corrupt = Buffer.from(corruptRequest.screenshot.data, 'base64');
  corrupt[corrupt.length - 1] ^= 0x01;
  corruptRequest.screenshot = embeddedAsset(corrupt, 4, 4);
  const crc = await prepareScreenshot(corruptRequest, {requestId: 'screenshot-crc'});
  assert.equal(crc.status, 'invalid');
  assert.equal(crc.diagnostics[0].code, 'VC-AI-SCREENSHOT-PNG-INTEGRITY-INVALID');
});

test('localizes unsupported PNG format, crop, redaction, and cancellation', async () => {
  const unsupportedRequest = await fixture(requestFixture);
  const unsupportedPng = makePng({
    width: 1,
    height: 1,
    pixels: Buffer.from([1, 2, 3, 255]),
    colorType: 2,
  });
  unsupportedRequest.screenshot = embeddedAsset(unsupportedPng, 1, 1);
  unsupportedRequest.interpretation.crop = {x: 0, y: 0, width: 1, height: 1};
  unsupportedRequest.privacy.redactions = [];
  unsupportedRequest.output.maxWidthPx = 1;
  unsupportedRequest.output.maxHeightPx = 1;
  const unsupported = await prepareScreenshot(unsupportedRequest, {
    requestId: 'screenshot-format',
  });
  assert.equal(unsupported.status, 'unsupported');
  assert.equal(unsupported.diagnostics[0].code, 'VC-AI-SCREENSHOT-PNG-UNSUPPORTED');

  const cropRequest = await fixture(requestFixture);
  cropRequest.interpretation.crop = {x: 3, y: 0, width: 2, height: 4};
  const crop = await prepareScreenshot(cropRequest, {requestId: 'screenshot-crop'});
  assert.equal(crop.status, 'invalid');
  assert.equal(crop.diagnostics[0].code, 'VC-AI-SCREENSHOT-CROP-INVALID');

  const redactionRequest = await fixture(requestFixture);
  redactionRequest.privacy.redactions[0].rectangle = {x: 3, y: 1, width: 2, height: 2};
  const redaction = await prepareScreenshot(redactionRequest, {
    requestId: 'screenshot-redaction',
  });
  assert.equal(redaction.status, 'invalid');
  assert.equal(redaction.diagnostics[0].code, 'VC-AI-SCREENSHOT-REDACTION-INVALID');

  const controller = new AbortController();
  controller.abort('test cancellation');
  const cancelled = await prepareScreenshot(await fixture(requestFixture), {
    requestId: 'screenshot-cancelled',
    signal: controller.signal,
  });
  assert.equal(cancelled.status, 'cancelled');
  assert.equal(cancelled.diagnostics[0].code, 'VC-AI-SCREENSHOT-CANCELLED');
});

test('rejects embedded color profiles and animated PNG semantics', async () => {
  for (const semanticChunk of [
    {type: 'iCCP', data: Buffer.from('profile\0\0data', 'binary')},
    {type: 'acTL', data: Buffer.alloc(8)},
  ]) {
    const request = await fixture(requestFixture);
    const png = makePng({
      width: 1,
      height: 1,
      pixels: Buffer.from([1, 2, 3, 255]),
      ancillary: [semanticChunk],
    });
    request.screenshot = embeddedAsset(png, 1, 1);
    request.interpretation.crop = {x: 0, y: 0, width: 1, height: 1};
    request.privacy.redactions = [];
    request.output.maxWidthPx = 1;
    request.output.maxHeightPx = 1;
    const result = await prepareScreenshot(request, {
      requestId: `screenshot-${semanticChunk.type}`,
    });
    assert.equal(result.status, 'unsupported');
    assert.equal(result.diagnostics[0].code, 'VC-AI-SCREENSHOT-PNG-UNSUPPORTED');
  }
});
