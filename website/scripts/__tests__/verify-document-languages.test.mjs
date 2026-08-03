import assert from 'node:assert/strict';
import test from 'node:test';

import {
  analyzeCanonicalDocument,
  analyzeChineseDocument,
  requiredParityViolations,
  visibleProse,
} from '../verify-document-languages.mjs';

test('canonical prose rejects misplaced Chinese text with a line number', () => {
  const violations = analyzeCanonicalDocument('# Architecture\n\n这里不属于英文基准。\n', 'docs/a.md');
  assert.equal(violations.length, 1);
  assert.match(violations[0], /docs\/a\.md:3/u);
});

test('canonical examples and literal identifiers may contain Chinese text', () => {
  const content = [
    '# Architecture',
    '',
    'Use the literal `系统导航验收` in this verification path.',
    '',
    '```kotlin',
    'Text("中文示例")',
    '```',
  ].join('\n');
  assert.deepEqual(analyzeCanonicalDocument(content, 'docs/a.md'), []);
});

test('Chinese document rejects an English title and English-only body', () => {
  const content = '# Architecture\n\nThis page contains only English narrative prose.\n';
  const violations = analyzeChineseDocument(content, 'zh/a.md');
  assert.equal(violations.length, 2);
  assert.match(violations[0], /title must contain Han text/u);
  assert.match(violations[1], /missing or too short/u);
});

test('Chinese technical prose may retain English API identifiers', () => {
  const content = [
    '# 状态与导航教程',
    '',
    '本页解释如何使用 `NavHost`、`NavRoute` 和 `RenderSession`。',
    '这些标识符保持英文，但标题、说明和操作步骤均使用简体中文，读者可以按步骤完成验证。',
  ].join('\n');
  assert.deepEqual(analyzeChineseDocument(content, 'zh/a.md'), []);
});

test('Markdown stripping preserves prose line numbers', () => {
  const content = '# Title\n\n```kotlin\nText("示例")\n```\nVisible prose\n';
  assert.deepEqual(
    visibleProse(content).visibleLines.find((entry) => entry.text === 'Visible prose'),
    {line: 6, text: 'Visible prose'},
  );
});

test('public pages and locale mirrors must both be registered as required', () => {
  const violations = requiredParityViolations({
    canonicalPaths: ['README.md', 'guides/new-guide.md', 'project/plans/work.md'],
    translationPaths: ['README.md', 'guides/unregistered.md'],
    requiredPaths: ['README.md'],
  });
  assert.equal(violations.length, 2);
  assert.match(violations[0], /docs\/guides\/new-guide\.md/u);
  assert.match(violations[1], /guides\/unregistered\.md/u);
  assert.doesNotMatch(violations.join('\n'), /project\/plans/u);
});
