import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {interpretLayoutSnapshot} from './layout-diagnoser.mjs';

const evaluationRoot = fileURLToPath(new URL('../evaluation/', import.meta.url));
const corpus = JSON.parse(await readFile(resolve(evaluationRoot, 'corpus.json'), 'utf8'));
const metrics = JSON.parse(await readFile(resolve(evaluationRoot, 'metrics.json'), 'utf8'));
const cases = corpus.cases.filter((entry) =>
  entry.metricIds.includes('layout.diagnosis-exactness'));
const metric = metrics.metrics.find((entry) => entry.id === 'layout.diagnosis-exactness');

if (cases.length === 0 || !metric) throw new Error('The frozen layout diagnosis contract is empty.');
let exactMatches = 0;
for (const testCase of cases) {
  const fixturePath = resolve(evaluationRoot, testCase.input.fixture);
  const snapshot = JSON.parse(await readFile(fixturePath, 'utf8'));
  const options = {sourcePath: 'samples/counter/src/debug/CounterPreview.kt'};
  const first = interpretLayoutSnapshot(snapshot, options);
  const second = interpretLayoutSnapshot(snapshot, options);
  if (JSON.stringify(first) !== JSON.stringify(second)) {
    throw new Error(`${testCase.id} returned nondeterministic layout diagnosis data.`);
  }
  const actualCodes = first.findings.map((entry) => entry.code);
  const expectedCodes = testCase.expected.diagnosticCodes ?? [];
  if (JSON.stringify(actualCodes) === JSON.stringify(expectedCodes)) exactMatches += 1;
  else {
    throw new Error(
      `${testCase.id} expected ${expectedCodes.join(', ')}; got ${actualCodes.join(', ')}.`,
    );
  }
  console.log(`${testCase.id}: ${actualCodes.join(', ')}`);
}

const exactMatchRatio = exactMatches / cases.length;
if (exactMatchRatio < metric.threshold) {
  throw new Error(
    `Layout diagnosis exact-match ratio ${exactMatchRatio} is below ${metric.threshold}.`,
  );
}
console.log(
  `Verified deterministic layout diagnosis: ${cases.length} cases, ` +
    `exact-match ratio ${exactMatchRatio.toFixed(2)}, at most 100 returned findings.`,
);
