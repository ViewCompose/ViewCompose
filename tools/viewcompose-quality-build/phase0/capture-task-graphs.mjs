#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { parseDryRunTasks } from './phase0-lib.mjs';

const phaseRoot = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(phaseRoot, '../../..');
const outputRoot = path.join(phaseRoot, 'fixtures/task-graphs');
const targets = ['qaQuick', 'qaPreview', 'qaFull', 'verifyDocumentationStructure'];
const sourceRevision = execFileSync('git', ['rev-parse', 'HEAD'], {
  cwd: repositoryRoot,
  encoding: 'utf8',
}).trim();

mkdirSync(outputRoot, { recursive: true });

for (const target of targets) {
  const output = execFileSync(
    path.join(repositoryRoot, 'gradlew'),
    ['--dry-run', '--console=plain', target],
    { cwd: repositoryRoot, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 },
  );
  const tasks = parseDryRunTasks(output);
  if (tasks.length === 0 || !tasks.includes(`:${target}`)) {
    throw new Error(`Dry-run output for ${target} did not contain the requested task.`);
  }
  const fixture = {
    schemaVersion: 1,
    sourceRevision,
    target,
    command: `./gradlew --dry-run --console=plain ${target}`,
    taskCount: tasks.length,
    tasks,
  };
  writeFileSync(
    path.join(outputRoot, `${target}.json`),
    `${JSON.stringify(fixture, null, 2)}\n`,
  );
}
