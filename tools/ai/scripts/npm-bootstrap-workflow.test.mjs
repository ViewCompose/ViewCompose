import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import test from 'node:test';

const workflowPath = fileURLToPath(
  new URL('../../../.github/workflows/ai-tooling-npm-bootstrap.yml', import.meta.url),
);

test('one-time npm bootstrap workflow is fixed, isolated, and provenance-bearing', async () => {
  const workflow = await readFile(workflowPath, 'utf8');
  for (const required of [
    'workflow_dispatch:',
    'contents: read',
    'id-token: write',
    'environment: ai-tooling-release',
    'test "$GITHUB_REF" = "refs/heads/$GITHUB_DEFAULT_BRANCH"',
    'test "$GITHUB_REPOSITORY" = "ViewCompose/ViewCompose"',
    'npm install --global npm@11.8.0',
    'npm-bootstrap-seed.mjs create',
    'viewcompose-ai-tooling-0.4.0-bootstrap.0.tgz',
    'secrets.NPM_BOOTSTRAP_TOKEN',
    '--tag bootstrap',
    '--provenance',
    'has("latest")',
    'dist.attestations.provenance.predicateType',
  ]) {
    assert.match(workflow, new RegExp(required.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&'), 'u'));
  }
  assert.doesNotMatch(workflow, /workflow_dispatch:\s*\n\s+inputs:/u);
  assert.doesNotMatch(workflow, /contents:\s+write|packages:\s+write|pull-requests:\s+write/u);
  assert.doesNotMatch(workflow, /\$\{\{\s*inputs\.|repository_dispatch|schedule:/u);
  assert.equal((workflow.match(/npm publish/gu) ?? []).length, 1);
  assert.equal((workflow.match(/NPM_BOOTSTRAP_TOKEN/gu) ?? []).length, 1);
});
