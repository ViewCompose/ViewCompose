import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import {
  compareWorkflowContracts,
  validateSkillMarkdown,
  verifyConsumerWorkflows,
} from './consumer-workflows.mjs';

test('accepts the complete checked-in client-neutral consumer workflow set', async () => {
  const result = await verifyConsumerWorkflows();
  assert.equal(result.workflows, 8);
  assert.equal(result.exactMatches, 8);
  assert.equal(result.exactMatchRatio, 1);
});

test('rejects workflow tool drift, evidence upgrades, and provider-specific skill text', () => {
  const expected = {
    schemaVersion: 1,
    workflowSetId: 'viewcompose-consumer-skills-v1',
    versionLane: 'current-source',
    sharedInvariants: ['select-exact-framework-identity'],
    workflows: [{
      id: 'viewcompose-check',
      requiredTools: ['validate_code'],
      conditionalTools: [],
      minimumEvidence: 'compiled',
      maximumEvidence: 'compiled',
      mutationPolicy: 'read-only',
    }],
  };
  const manifest = {
    ...expected,
    skills: [{
      ...expected.workflows[0],
      path: 'skills/viewcompose-check/SKILL.md',
      maximumEvidence: 'verified',
    }],
  };
  delete manifest.workflows;
  assert.throws(() => compareWorkflowContracts(expected, manifest), /invalid consumer evidence range/u);

  const skill = {
    ...expected.workflows[0],
    path: 'skills/viewcompose-check/SKILL.md',
  };
  assert.throws(() => validateSkillMarkdown(skill, `---
name: viewcompose-check
description: Validate ViewCompose code.
---

## Exact version and evidence

Use \`validate_code\` with exact framework evidence. Never fabricate an API.

## Stop and authority

This is read-only. Stop when the same diagnostic repeats without new evidence. Use Codex.
`), /provider-specific/u);
});

test('routes official Figma context to the attended screenshot evidence workflow', async () => {
  const skill = await readFile(
    new URL('../skills/viewcompose-import-figma/SKILL.md', import.meta.url),
    'utf8',
  );

  for (const expected of [
    '`prepare_screenshot`',
    '`validate_screenshot_inference`',
    '`resolve_screenshot_inference`',
    '`generate_screenshot_viewcompose`',
    'reference-assisted, attended adaptation',
    'Do not pass that response to `convert_figma_to_viewcompose`',
    'Never retain a temporary URL in',
  ]) {
    assert.ok(skill.includes(expected), `missing official Figma workflow rule: ${expected}`);
  }
  assert.match(skill, /do not say “direct Figma conversion,”/u);
});
