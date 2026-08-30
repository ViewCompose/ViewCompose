import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {searchComponents} from './knowledge-retriever.mjs';

const evaluationRoot = fileURLToPath(new URL('../evaluation/', import.meta.url));
const corpus = JSON.parse(await readFile(resolve(evaluationRoot, 'corpus.json'), 'utf8'));
const metrics = JSON.parse(await readFile(resolve(evaluationRoot, 'metrics.json'), 'utf8'));
const cases = corpus.cases.filter((entry) => entry.category === 'retrieval');

if (cases.length === 0) throw new Error('The frozen retrieval corpus is empty.');
let topFiveHits = 0;
let exactReciprocalRank = null;
for (const testCase of cases) {
  const arguments_ = {
    versionLane: 'current-source',
    query: testCase.input.text,
    limit: 5,
  };
  const first = await searchComponents(arguments_, {requestId: testCase.id});
  const second = await searchComponents(arguments_, {requestId: testCase.id});
  if (first.status !== 'success' || first.evidence.level !== testCase.expected.evidenceLevel) {
    throw new Error(`${testCase.id} did not return accepted knowledge evidence.`);
  }
  if (JSON.stringify(first.data) !== JSON.stringify(second.data)) {
    throw new Error(`${testCase.id} returned nondeterministic retrieval data.`);
  }
  const rank = first.data.results.find((entry) =>
    (testCase.expected.capabilityIds ?? []).includes(entry.capabilityId))?.rank;
  if (!rank || rank > testCase.expected.maxRank) {
    throw new Error(
      `${testCase.id} expected ${testCase.expected.capabilityIds.join(', ')} by rank ` +
        `${testCase.expected.maxRank}; got ${rank ?? 'no hit'}.`,
    );
  }
  if (rank <= 5) topFiveHits += 1;
  if (testCase.metricIds.includes('retrieval.exact-symbol-rank')) exactReciprocalRank = 1 / rank;
  console.log(`${testCase.id}: rank ${rank}, ${first.data.results[rank - 1].symbolId}`);
}

const topFiveRecall = topFiveHits / cases.length;
const topFiveThreshold = metrics.metrics.find((entry) => entry.id === 'retrieval.top5-recall').threshold;
const exactThreshold = metrics.metrics.find((entry) => entry.id === 'retrieval.exact-symbol-rank').threshold;
if (topFiveRecall < topFiveThreshold) {
  throw new Error(`Retrieval top-five recall ${topFiveRecall} is below ${topFiveThreshold}.`);
}
if (exactReciprocalRank === null || exactReciprocalRank < exactThreshold) {
  throw new Error(`Exact-symbol reciprocal rank ${exactReciprocalRank} is below ${exactThreshold}.`);
}
console.log(
  `Verified deterministic retrieval corpus: ${cases.length} cases, ` +
    `top-five recall ${topFiveRecall.toFixed(2)}, exact reciprocal rank ${exactReciprocalRank.toFixed(2)}.`,
);
