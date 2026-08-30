import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import test from 'node:test';
import {verifyAiToolingRelease} from './verify-ai-tooling-release.mjs';

const repositoryRoot = resolve(new URL('../', import.meta.url).pathname, '../..');
const workflow = await readFile(
  resolve(repositoryRoot, '.github/workflows/ai-tooling-release.yml'),
  'utf8',
);
const ciWorkflow = await readFile(resolve(repositoryRoot, '.github/workflows/ci.yml'), 'utf8');

test('accepts the frozen immutable GitHub Release workflow', async () => {
  const result = await verifyAiToolingRelease({checkAssets: false});
  assert.deepEqual(result, {tag: 'ai-tooling-v0.1.0', assets: 3});
});

test('rejects tag, provenance, and mutable release drift', async () => {
  await assert.rejects(
    verifyAiToolingRelease({tag: 'ai-tooling-v0.1.1', checkAssets: false}),
    /does not match frozen tag/u,
  );
  await assert.rejects(
    verifyAiToolingRelease({
      workflowText: workflow.replace('actions/attest-build-provenance@v3', 'actions/upload-artifact@v7'),
      checkAssets: false,
    }),
    /GitHub build provenance/u,
  );
  await assert.rejects(
    verifyAiToolingRelease({
      workflowText: workflow.replace('cancel-in-progress: false', 'cancel-in-progress: true'),
      checkAssets: false,
    }),
    /must not cancel/u,
  );
});

test('keeps source-bound Preview verification on a complete Git history', () => {
  const previewJob = /\n  qaPreviewWork:\n(?<body>[\s\S]*?)\n  qaPreview:\n/u.exec(ciWorkflow)?.groups?.body;
  assert.ok(previewJob, 'qaPreviewWork job is missing');
  assert.match(
    previewJob,
    /uses: actions\/checkout@v7\n\s+with:\n\s+fetch-depth: 0/u,
  );
});
