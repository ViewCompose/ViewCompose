import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {verifyConsumerWorkflows} from './consumer-workflows.mjs';

const evaluationRoot = fileURLToPath(new URL('../evaluation/', import.meta.url));
const metrics = JSON.parse(await readFile(resolve(evaluationRoot, 'metrics.json'), 'utf8'));
const metric = metrics.metrics.find((entry) => entry.id === 'workflow.contract-completeness');
if (!metric) throw new Error('The consumer workflow completeness metric is missing.');

const result = await verifyConsumerWorkflows();
if (result.exactMatchRatio < metric.threshold) {
  throw new Error(
    `Consumer workflow completeness ${result.exactMatchRatio} is below ${metric.threshold}.`,
  );
}
console.log(
  `Verified client-neutral consumer workflows: ${result.exactMatches}/${result.workflows} exact ` +
    'contracts, 6 valid skills, and 0 provider-specific adapters.',
);
