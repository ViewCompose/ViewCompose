#!/usr/bin/env node
import {readFile, mkdtemp, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {assertSchemaValue} from './schema-validator.mjs';
import {analyzeProject} from './project-analyzer.mjs';

const aiRoot = fileURLToPath(new URL('../', import.meta.url));

async function readJson(relativePath) {
  return JSON.parse(await readFile(new URL(`../${relativePath}`, import.meta.url), 'utf8'));
}

function replaceVersions(source, versions) {
  return source.replace(/\{\{VERSION:([a-z0-9-]+)\}\}/gu, (_, artifactId) => {
    const version = versions.get(artifactId);
    if (!version) throw new Error(`Project-analysis corpus references unknown artifact ${artifactId}.`);
    return version;
  });
}

async function evaluateCase({matrix, label, case_, variant, versions}) {
  const root = await mkdtemp(join(tmpdir(), 'viewcompose-project-analysis-corpus-'));
  try {
    const source = replaceVersions(
      `${variant.prefix}${case_.source}${variant.suffix}`,
      versions,
    );
    await writeFile(join(root, case_.path), source);
    const result = await analyzeProject({projectRoot: root});
    if (result.status !== 'success') {
      throw new Error(`${matrix.ruleId}/${case_.id}/${variant.id} returned ${result.status}.`);
    }
    const actual = result.data.analysis.findings.filter(
      ({ruleId, suppression}) => ruleId === matrix.ruleId && suppression.state === 'none',
    ).length;
    const unsupported = result.data.analysis.scan.unsupported.length > 0 ? 1 : 0;
    if (label === 'unsupported' && unsupported !== 1) {
      throw new Error(`${matrix.ruleId}/${case_.id}/${variant.id} did not report unsupported syntax.`);
    }
    return {actual, expected: case_.expectedFindings, unsupported};
  } finally {
    await rm(root, {recursive: true, force: true});
  }
}

async function main() {
  const [corpus, corpusSchema, catalog, catalogSchema, quality, qualitySchema, artifacts] =
    await Promise.all([
      readJson('evaluation/project-analysis-corpus.json'),
      readJson('contracts/project-analysis-corpus.schema.json'),
      readJson('analysis/rules.json'),
      readJson('contracts/project-analysis-catalog.schema.json'),
      readJson('analysis/quality.json'),
      readJson('contracts/project-analysis-quality.schema.json'),
      readJson('generated/current-source/artifacts.json'),
    ]);
  assertSchemaValue(corpus, corpusSchema, 'project-analysis corpus');
  assertSchemaValue(catalog, catalogSchema, 'project-analysis rule catalog');
  assertSchemaValue(quality, qualitySchema, 'project-analysis quality snapshot');
  if (corpus.catalogVersion !== catalog.catalogVersion || quality.catalogVersion !== catalog.catalogVersion) {
    throw new Error('Project-analysis corpus, quality, and catalog versions differ.');
  }
  const versions = new Map(artifacts.artifacts.map((entry) => [entry.artifact, entry.version]));
  const measured = [];
  for (const matrix of corpus.ruleMatrices) {
    const counters = {
      truePositives: 0,
      falsePositives: 0,
      falseNegatives: 0,
      unsupportedOpportunities: 0,
    };
    for (const [label, cases] of [
      ['positive', matrix.positive],
      ['eligibleNegative', matrix.eligibleNegative],
      ['unsupported', matrix.unsupported],
    ]) {
      for (const case_ of cases) {
        for (const variant of corpus.formatVariants) {
          const result = await evaluateCase({matrix, label, case_, variant, versions});
          if (label === 'positive') {
            counters.truePositives += Math.min(result.actual, result.expected);
            counters.falsePositives += Math.max(0, result.actual - result.expected);
            counters.falseNegatives += Math.max(0, result.expected - result.actual);
          } else if (label === 'eligibleNegative') {
            counters.falsePositives += result.actual;
          } else {
            counters.unsupportedOpportunities += result.unsupported;
          }
        }
      }
    }
    const positiveOpportunities = matrix.positive.length * corpus.formatVariants.length;
    const eligibleNegativeOpportunities = matrix.eligibleNegative.length * corpus.formatVariants.length;
    const precision = counters.truePositives /
      Math.max(1, counters.truePositives + counters.falsePositives);
    const recall = counters.truePositives /
      Math.max(1, counters.truePositives + counters.falseNegatives);
    measured.push({
      ruleId: matrix.ruleId,
      ruleVersion: matrix.ruleVersion,
      positiveOpportunities,
      eligibleNegativeOpportunities,
      ...counters,
      precision,
      recall,
    });
  }
  if (JSON.stringify(measured) !== JSON.stringify(quality.rules)) {
    throw new Error(
      `Project-analysis quality snapshot differs from measured corpus.\nExpected: ${JSON.stringify(quality.rules)}\nMeasured: ${JSON.stringify(measured)}`,
    );
  }
  console.log(
    `Verified project analysis: ${measured.length} high-confidence rules, ` +
    `${measured.reduce((sum, entry) => sum + entry.positiveOpportunities, 0)} positive, ` +
    `${measured.reduce((sum, entry) => sum + entry.eligibleNegativeOpportunities, 0)} eligible negative, ` +
    `${measured.reduce((sum, entry) => sum + entry.unsupportedOpportunities, 0)} unsupported opportunities, ` +
    '100% observed precision and recall.',
  );
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
