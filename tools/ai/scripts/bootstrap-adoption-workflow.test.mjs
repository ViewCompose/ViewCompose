import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import test from 'node:test';

const repositoryRoot = resolve(new URL('../', import.meta.url).pathname, '../..');
const workflow = await readFile(
  resolve(repositoryRoot, '.github/workflows/ai-tooling-adoption.yml'),
  'utf8',
);
const packageMetadata = JSON.parse(await readFile(resolve(repositoryRoot, 'tools/ai/package.json')));

test('pins the bootstrap adoption matrix to Linux, macOS, and Windows', () => {
  assert.match(workflow, /os: \[ubuntu-latest, macos-latest, windows-latest\]/u);
  assert.match(workflow, /node-version: "24\.19\.0"/u);
  assert.match(workflow, /java-version: "21"/u);
  assert.match(workflow, /platforms;android-36/u);
  assert.equal(workflow.includes('continue-on-error'), false);
});

test('runs the native bootstrap adoption verifier for AI package changes', () => {
  assert.equal(
    packageMetadata.scripts['verify:bootstrap-adoption'],
    'node scripts/verify-bootstrap-adoption.mjs',
  );
  assert.match(workflow, /npm --prefix tools\/ai run verify:bootstrap-adoption/u);
  assert.match(workflow, /"tools\/ai\/\*\*"/u);
});
