#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { measureJob } from './phase0-lib.mjs';

const phaseRoot = path.dirname(fileURLToPath(import.meta.url));
const selectionPath = path.join(phaseRoot, 'fixtures/ci-run-selection.json');
const outputPath = path.join(phaseRoot, 'fixtures/ci-timings.json');
const selection = JSON.parse(readFileSync(selectionPath, 'utf8'));

function loadRun(runId) {
  return JSON.parse(
    execFileSync(
      'gh',
      [
        'run',
        'view',
        String(runId),
        '--json',
        'databaseId,createdAt,updatedAt,conclusion,event,headSha,headBranch,url,jobs',
      ],
      { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 },
    ),
  );
}

const records = selection.runs.map((selected) => {
  const ci = loadRun(selected.ciRunId);
  const documentation = loadRun(selected.documentationRunId);
  for (const run of [ci, documentation]) {
    if (run.conclusion !== 'success' || run.event !== 'pull_request') {
      throw new Error(`Run ${run.databaseId} is not a successful pull-request run.`);
    }
    if (run.headSha !== selected.headSha) {
      throw new Error(`Run ${run.databaseId} does not match ${selected.headSha}.`);
    }
  }
  return {
    headSha: selected.headSha,
    headBranch: ci.headBranch,
    qaQuick: measureJob(ci, 'qaQuick', 'Run qaQuick', 'Run qaQuick'),
    qaPreview: measureJob(ci, 'qaPreview', 'Run qaPreview', 'Run qaPreview'),
    documentation: measureJob(
      documentation,
      'Build documentation',
      'Verify documentation sources and translations',
      'Build website',
    ),
  };
});

const durations = (gate, field) => records.map((record) => record[gate][field]).sort((a, b) => a - b);
const percentile = (values, fraction) => values[Math.ceil(values.length * fraction) - 1];
const summary = {};
for (const gate of ['qaQuick', 'qaPreview', 'documentation']) {
  summary[gate] = {};
  for (const field of ['queueMillis', 'setupMillis', 'executionMillis', 'postMillis', 'totalMillis']) {
    const values = durations(gate, field);
    summary[gate][field] = {
      min: values[0],
      p50: percentile(values, 0.5),
      p95: percentile(values, 0.95),
      max: values.at(-1),
    };
  }
}
const requiredCriticalPath = records
  .map((record) => Math.max(record.qaQuick.totalMillis, record.documentation.totalMillis))
  .sort((a, b) => a - b);
summary.requiredCriticalPathMillis = {
  definition: 'max(qaQuick.totalMillis, documentation.totalMillis) for each pull-request head',
  min: requiredCriticalPath[0],
  p50: percentile(requiredCriticalPath, 0.5),
  p95: percentile(requiredCriticalPath, 0.95),
  max: requiredCriticalPath.at(-1),
};

writeFileSync(
  outputPath,
  `${JSON.stringify(
    {
      schemaVersion: 1,
      capturedAt: selection.capturedAt,
      methodology: {
        queue: 'job.startedAt - workflow.createdAt',
        setup: 'first gate step.startedAt - job.startedAt',
        execution: 'last gate step.completedAt - first gate step.startedAt',
        post: 'job.completedAt - last gate step.completedAt',
        total: 'job.completedAt - workflow.createdAt',
      },
      sampleCount: records.length,
      summary,
      runs: records,
    },
    null,
    2,
  )}\n`,
);
