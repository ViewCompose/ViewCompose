import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {renderPreview} from './preview-adapter.mjs';

const evaluationRoot = fileURLToPath(new URL('../evaluation/', import.meta.url));
const corpus = JSON.parse(await readFile(resolve(evaluationRoot, 'corpus.json'), 'utf8'));
const cases = corpus.cases.filter((entry) => entry.phase === 2 && entry.category === 'render');

if (cases.length === 0) throw new Error('Phase 2 render corpus is empty.');
const targetByCase = {
  'render.counter-preview': 'samples.counter.CounterPreview',
};
const results = [];
for (const testCase of cases) {
  const targetId = targetByCase[testCase.id];
  if (!targetId) throw new Error(`${testCase.id} has no fixed Preview target mapping.`);
  const result = await renderPreview({
    targetId,
    capabilityIds: testCase.expected.capabilityIds ?? [],
    requestId: testCase.id,
  });
  if (result.status !== 'success') {
    throw new Error(
      `${testCase.id} expected render success; got ${result.status}: ` +
        result.diagnostics.map((entry) => `${entry.code} ${entry.message}`).join('; '),
    );
  }
  if (result.evidence.level !== testCase.expected.evidenceLevel) {
    throw new Error(
      `${testCase.id} expected ${testCase.expected.evidenceLevel}, got ${result.evidence.level}`,
    );
  }
  for (const capabilityId of testCase.expected.capabilityIds ?? []) {
    if (!result.data.capabilityIds.includes(capabilityId)) {
      throw new Error(`${testCase.id} omitted capability evidence ${capabilityId}.`);
    }
  }
  results.push({
    id: testCase.id,
    cache: result.evidence.cache,
    elapsedMs: result.elapsedMs,
    outputFingerprint: result.evidence.outputFingerprint,
    widthPx: result.data.image.widthPx,
    heightPx: result.data.image.heightPx,
    imageBytes: result.data.image.bytes,
    treeBytes: result.data.renderTree.bytes,
    diagnosticCount: result.diagnostics.length,
  });
}

console.log(`Verified Phase 2 render corpus: ${results.length} case(s).`);
for (const result of results) {
  console.log(
    `${result.id}: ${result.cache}, ${result.elapsedMs} ms, ` +
      `${result.widthPx}x${result.heightPx}, ${result.imageBytes} image bytes/` +
      `${result.treeBytes} tree bytes, ${result.diagnosticCount} diagnostics, ` +
      result.outputFingerprint,
  );
}
