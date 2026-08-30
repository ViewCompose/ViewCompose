import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {compileKotlin} from './compiler-adapter.mjs';

const evaluationRoot = fileURLToPath(new URL('../evaluation/', import.meta.url));
const corpus = JSON.parse(await readFile(resolve(evaluationRoot, 'corpus.json'), 'utf8'));
const cases = corpus.cases.filter(
  (entry) => entry.phase === 2 && entry.category === 'compilation',
);

if (cases.length === 0) throw new Error('Phase 2 compilation corpus is empty.');
const results = [];
for (const testCase of cases) {
  const fixturePath = resolve(evaluationRoot, testCase.input.fixture);
  const result = await compileKotlin({
    source: await readFile(fixturePath, 'utf8'),
    path: testCase.input.fixture,
    capabilityIds: testCase.expected.capabilityIds ?? [],
    requestId: testCase.id,
  });
  if (result.status !== 'success') {
    throw new Error(
      `${testCase.id} expected compilation success; got ${result.status}: ` +
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
    classFiles: result.data.classFiles,
    classBytes: result.data.classBytes,
  });
}

console.log(`Verified Phase 2 compiler corpus: ${results.length} case(s).`);
for (const result of results) {
  console.log(
    `${result.id}: ${result.cache}, ${result.elapsedMs} ms, ` +
      `${result.classFiles} classes/${result.classBytes} bytes, ${result.outputFingerprint}`,
  );
}
