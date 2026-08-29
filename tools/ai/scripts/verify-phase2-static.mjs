import {readFile} from 'node:fs/promises';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {inspectProjectRequest} from './project-analyzer.mjs';
import {validateKotlin} from './static-validator.mjs';

const evaluationRoot = fileURLToPath(new URL('../evaluation/', import.meta.url));
const corpus = JSON.parse(await readFile(resolve(evaluationRoot, 'corpus.json'), 'utf8'));
const supportedCategories = new Set([
  'hallucination',
  'project-analysis',
  'security',
  'static-validation',
]);

let evaluated = 0;
for (const testCase of corpus.cases.filter(
  (entry) => entry.phase === 2 && supportedCategories.has(entry.category),
)) {
  const fixturePath = resolve(evaluationRoot, testCase.input.fixture);
  const fixture = await readFile(fixturePath, 'utf8');
  let result;
  if (fixturePath.endsWith('.kt')) {
    result = await validateKotlin({source: fixture, path: testCase.input.fixture, requestId: testCase.id});
  } else {
    result = await inspectProjectRequest(JSON.parse(fixture), {requestId: testCase.id});
  }
  const actualCodes = new Set(result.diagnostics.map((entry) => entry.code));
  for (const expectedCode of testCase.expected.diagnosticCodes ?? []) {
    if (!actualCodes.has(expectedCode)) {
      throw new Error(`${testCase.id} did not emit ${expectedCode}; got ${[...actualCodes].join(', ')}`);
    }
  }
  const expectsPass = testCase.expected.outcome === 'pass';
  if ((result.status === 'success') !== expectsPass) {
    throw new Error(`${testCase.id} expected ${testCase.expected.outcome}, got ${result.status}`);
  }
  if (result.evidence.level !== testCase.expected.evidenceLevel) {
    throw new Error(`${testCase.id} expected ${testCase.expected.evidenceLevel}, got ${result.evidence.level}`);
  }
  evaluated += 1;
}

console.log(`Verified Phase 2 static/security corpus: ${evaluated} cases.`);
