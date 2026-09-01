import {createHash} from 'node:crypto';

const MAX_SOURCE_BYTES = 1024 * 1024;
const MAX_REPLACEMENT_BYTES = 64 * 1024;

export class ScreenshotSourceEditError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'ScreenshotSourceEditError';
    this.code = code;
  }
}

function fail(code, message) {
  throw new ScreenshotSourceEditError(code, message);
}

export function fingerprintSourceBytes(value) {
  return createHash('sha256').update(value).digest('hex');
}

function kotlinString(value) {
  return JSON.stringify(value).replaceAll('$', '\\$');
}

function lineStart(source, offset) {
  return source.lastIndexOf('\n', offset - 1) + 1;
}

function lineEnd(source, offset) {
  const end = source.indexOf('\n', offset);
  return end === -1 ? source.length : end;
}

function lineNumber(source, offset) {
  let result = 1;
  for (let index = 0; index < offset; index += 1) {
    if (source.charCodeAt(index) === 10) result += 1;
  }
  return result;
}

function nodeBlock(source, nodeId) {
  const keyLine = `key = ${kotlinString(nodeId)},`;
  const keyOffset = source.indexOf(keyLine);
  if (keyOffset < 0 || source.indexOf(keyLine, keyOffset + keyLine.length) >= 0) {
    fail(
      'VC-AI-SOURCE-APPLICATION-NODE-AMBIGUOUS',
      `Generated Kotlin must contain one exact key for node ${nodeId}.`,
    );
  }
  const keyIndent = source.slice(lineStart(source, keyOffset), keyOffset);
  if (!/^\s*$/u.test(keyIndent) || keyIndent.length < 4) {
    fail('VC-AI-SOURCE-APPLICATION-NODE-AMBIGUOUS', 'Generated node key indentation is invalid.');
  }
  const callIndent = keyIndent.slice(0, -4);
  let cursor = lineStart(source, keyOffset) - 1;
  let start = -1;
  while (cursor >= 0) {
    const candidateStart = lineStart(source, cursor);
    const candidateEnd = lineEnd(source, candidateStart);
    const line = source.slice(candidateStart, candidateEnd);
    if (
      line.startsWith(callIndent) &&
      /^(?:Text|TextField|Button)\($/u.test(line.slice(callIndent.length))
    ) {
      start = candidateStart;
      break;
    }
    cursor = candidateStart - 1;
  }
  if (start < 0) {
    fail('VC-AI-SOURCE-APPLICATION-NODE-AMBIGUOUS', 'Generated node call boundary is missing.');
  }
  const terminator = `\n${callIndent})`;
  const end = source.indexOf(terminator, keyOffset);
  if (end < 0 || end - start > 16384) {
    fail('VC-AI-SOURCE-APPLICATION-NODE-AMBIGUOUS', 'Generated node call exceeds its bound.');
  }
  return {start, end: end + terminator.length};
}

function propertyLiteral(source, {nodeId, propertyName, value}) {
  if (!['text', 'hint'].includes(propertyName) || typeof value !== 'string') {
    fail(
      'VC-AI-SOURCE-APPLICATION-EDIT-UNSUPPORTED',
      'Source application v1 supports only literal text and hint property values.',
    );
  }
  const block = nodeBlock(source, nodeId);
  const argument = propertyName === 'hint' ? 'placeholder' : 'text';
  const literal = kotlinString(value);
  const pattern = `${argument} = ${literal},`;
  const offset = source.indexOf(pattern, block.start);
  const duplicate = offset < 0 ? -1 : source.indexOf(pattern, offset + pattern.length);
  if (offset < 0 || offset >= block.end || (duplicate >= 0 && duplicate < block.end)) {
    fail(
      'VC-AI-SOURCE-APPLICATION-SPAN-AMBIGUOUS',
      `Generated node ${nodeId} must contain one exact ${argument} literal.`,
    );
  }
  return {
    block,
    literal,
    literalStart: offset + `${argument} = `.length,
    literalEnd: offset + `${argument} = `.length + literal.length,
    lineStart: lineStart(source, offset),
    lineEnd: lineEnd(source, offset),
  };
}

export function deriveGeneratedPropertyEdit({
  currentKotlin,
  candidateKotlin,
  relativePath,
  nodeId,
  propertyName,
  currentValue,
  candidateValue,
} = {}) {
  if (
    typeof currentKotlin !== 'string' ||
    typeof candidateKotlin !== 'string' ||
    typeof relativePath !== 'string' ||
    typeof nodeId !== 'string' ||
    currentKotlin === candidateKotlin ||
    Buffer.byteLength(currentKotlin, 'utf8') > MAX_SOURCE_BYTES ||
    Buffer.byteLength(candidateKotlin, 'utf8') > MAX_SOURCE_BYTES ||
    !currentKotlin.endsWith('\n') ||
    !candidateKotlin.endsWith('\n')
  ) {
    fail(
      'VC-AI-SOURCE-APPLICATION-EDIT-INVALID',
      'Source application requires two distinct bounded generated Kotlin files.',
    );
  }
  const current = propertyLiteral(currentKotlin, {
    nodeId,
    propertyName,
    value: currentValue,
  });
  const candidate = propertyLiteral(candidateKotlin, {
    nodeId,
    propertyName,
    value: candidateValue,
  });
  const reconstructed =
    currentKotlin.slice(0, current.literalStart) +
    candidate.literal +
    currentKotlin.slice(current.literalEnd);
  if (reconstructed !== candidateKotlin) {
    fail(
      'VC-AI-SOURCE-APPLICATION-EDIT-UNSUPPORTED',
      'Generated sources differ outside one exact property literal.',
    );
  }
  const expectedBytes = Buffer.from(current.literal, 'utf8');
  const replacementBytes = Buffer.from(candidate.literal, 'utf8');
  if (
    expectedBytes.length < 1 ||
    replacementBytes.length < 1 ||
    replacementBytes.length > MAX_REPLACEMENT_BYTES
  ) {
    fail('VC-AI-SOURCE-APPLICATION-EDIT-INVALID', 'Generated replacement exceeds its bound.');
  }
  const byteStart = Buffer.byteLength(currentKotlin.slice(0, current.literalStart), 'utf8');
  const byteEnd = byteStart + expectedBytes.length;
  const currentLine = currentKotlin.slice(current.lineStart, current.lineEnd);
  const candidateLine = candidateKotlin.slice(candidate.lineStart, candidate.lineEnd);
  const sourceLine = lineNumber(currentKotlin, current.literalStart);
  const diffText = [
    `--- a/${relativePath}`,
    `+++ b/${relativePath}`,
    `@@ -${sourceLine},1 +${sourceLine},1 @@`,
    `-${currentLine}`,
    `+${candidateLine}`,
    '',
  ].join('\n');
  return Object.freeze({
    kind: 'replace-generated-property-value',
    nodeId,
    propertyName,
    byteStart,
    byteEnd,
    expectedSpan: {
      bytes: expectedBytes.length,
      sha256: fingerprintSourceBytes(expectedBytes),
    },
    replacement: {
      encoding: 'base64',
      data: replacementBytes.toString('base64'),
      bytes: replacementBytes.length,
      sha256: fingerprintSourceBytes(replacementBytes),
    },
    candidate: {
      bytes: Buffer.byteLength(candidateKotlin, 'utf8'),
      sha256: fingerprintSourceBytes(Buffer.from(candidateKotlin, 'utf8')),
    },
    diff: {
      format: 'unified-v1',
      text: diffText,
      sha256: fingerprintSourceBytes(Buffer.from(diffText, 'utf8')),
    },
  });
}

export function reconstructGeneratedCandidate(preimage, edit) {
  if (!Buffer.isBuffer(preimage) || edit?.kind !== 'replace-generated-property-value') {
    fail('VC-AI-SOURCE-APPLICATION-EDIT-INVALID', 'Candidate reconstruction input is invalid.');
  }
  const expected = preimage.subarray(edit.byteStart, edit.byteEnd);
  const replacement = Buffer.from(edit.replacement?.data ?? '', 'base64');
  if (
    expected.length !== edit.expectedSpan?.bytes ||
    fingerprintSourceBytes(expected) !== edit.expectedSpan?.sha256 ||
    replacement.length !== edit.replacement?.bytes ||
    replacement.toString('base64') !== edit.replacement?.data ||
    fingerprintSourceBytes(replacement) !== edit.replacement?.sha256
  ) {
    fail('VC-AI-SOURCE-APPLICATION-SPAN-DRIFT', 'Source span or replacement identity drifted.');
  }
  const candidate = Buffer.concat([
    preimage.subarray(0, edit.byteStart),
    replacement,
    preimage.subarray(edit.byteEnd),
  ]);
  if (
    candidate.length !== edit.candidate?.bytes ||
    fingerprintSourceBytes(candidate) !== edit.candidate?.sha256
  ) {
    fail('VC-AI-SOURCE-APPLICATION-CANDIDATE-DRIFT', 'Reconstructed candidate identity drifted.');
  }
  return candidate;
}
