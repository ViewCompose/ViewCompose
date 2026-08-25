import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import { classifyFixturePath, measureJob, parseDryRunTasks } from './phase0-lib.mjs';

const phaseRoot = path.dirname(fileURLToPath(import.meta.url));
const fixture = (name) => JSON.parse(readFileSync(path.join(phaseRoot, 'fixtures', name), 'utf8'));

test('dry-run output is normalized to stable task paths', () => {
  assert.deepEqual(parseDryRunTasks(':one SKIPPED\n:two:three SKIPPED\n:one SKIPPED\n'), [
    ':one',
    ':two:three',
  ]);
});
test('timing segments preserve the complete workflow duration', () => {
  const run = {
    databaseId: 10,
    createdAt: '2026-01-01T00:00:00Z',
    url: 'https://example.invalid/run/10',
    jobs: [
      {
        databaseId: 20,
        name: 'gate',
        startedAt: '2026-01-01T00:00:10Z',
        completedAt: '2026-01-01T00:01:20Z',
        steps: [
          {
            name: 'setup',
            startedAt: '2026-01-01T00:00:10Z',
            completedAt: '2026-01-01T00:00:30Z',
          },
          {
            name: 'execute',
            startedAt: '2026-01-01T00:00:30Z',
            completedAt: '2026-01-01T00:01:10Z',
          },
        ],
      },
    ],
  };
  const timing = measureJob(run, 'gate', 'execute', 'execute');
  assert.equal(
    timing.queueMillis + timing.setupMillis + timing.executionMillis + timing.postMillis,
    timing.totalMillis,
  );
});

test('unknown paths conservatively select the full gate', () => {
  const policy = fixture('full-fallback-paths.json');
  for (const scenario of policy.cases) {
    assert.deepEqual(classifyFixturePath(scenario.path, policy), scenario.expected);
  }
  assert.deepEqual(classifyFixturePath('future-system/unknown.file', policy), {
    mode: 'full',
    reason: 'unknown-path',
  });
});

test('every extraction scanner has a concrete failing fixture and diagnostic', () => {
  const manifest = fixture('gate-failure-fixtures.json');
  const tasks = new Set();
  for (const item of manifest.fixtures) {
    assert.ok(item.id);
    assert.ok(item.task);
    assert.ok(item.expectedDiagnostic);
    assert.ok(item.input && Object.keys(item.input).length > 0);
    assert.ok(!tasks.has(item.task), `duplicate task fixture: ${item.task}`);
    tasks.add(item.task);
  }
  assert.deepEqual([...tasks].sort(), [...manifest.requiredScannerTasks].sort());
});

test('checked-in task graphs contain their targets and declared counts', () => {
  for (const target of ['qaQuick', 'qaPreview', 'qaFull', 'verifyDocumentationStructure']) {
    const graph = fixture(`task-graphs/${target}.json`);
    assert.equal(graph.taskCount, graph.tasks.length);
    assert.ok(graph.tasks.includes(`:${target}`));
  }
});

test('timing baseline contains ten comparable successful PR samples', () => {
  const timings = fixture('ci-timings.json');
  assert.ok(timings.sampleCount >= 10);
  assert.equal(timings.runs.length, timings.sampleCount);
  for (const record of timings.runs) {
    for (const gate of ['qaQuick', 'qaPreview', 'documentation']) {
      const timing = record[gate];
      assert.equal(
        timing.queueMillis + timing.setupMillis + timing.executionMillis + timing.postMillis,
        timing.totalMillis,
      );
    }
  }
});
