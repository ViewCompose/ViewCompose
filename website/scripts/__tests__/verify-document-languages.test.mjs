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

test('canonical front matter rejects a Chinese title', () => {
  const content = '---\ntitle: 架构说明\n---\n\n# Architecture\n\nEnglish narrative.\n';
  const violations = analyzeCanonicalDocument(content, 'docs/a.md');
  assert.equal(violations.length, 1);
  assert.match(violations[0], /canonical title contains Han text/u);
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

test('Chinese document rejects an English section heading even with Chinese body prose', () => {
  const content = [
    '---',
    'title: 中文架构说明',
    '---',
    '',
    '# 中文架构说明',
    '',
    '## Studio Preview',
    '',
    '本节已经使用中文解释功能、限制、操作步骤和验证方式。',
  ].join('\n');
  const violations = analyzeChineseDocument(content, 'zh/a.md');
  assert.equal(violations.length, 1);
  assert.match(violations[0], /Chinese heading must contain Han text/u);
  assert.match(violations[0], /:7/u);
});

test('Chinese document rejects one English prose block hidden by a long Chinese page', () => {
  const content = [
    '# 中文架构说明',
    '',
    '这里有足够长的中文内容，用来说明背景、约束、状态所有权、生命周期以及验证方法。',
    '继续补充中文叙述，确保整篇页面总体上明显属于中文页面。',
    '',
    'This paragraph remains entirely in English and must fail block-level language verification.',
    '',
    '## 中文结论',
    '',
    '这里继续使用中文总结已经验证的行为。',
  ].join('\n');
  const violations = analyzeChineseDocument(content, 'zh/a.md');
  assert.equal(violations.length, 1);
  assert.match(violations[0], /Chinese prose block appears English-dominant/u);
  assert.match(violations[0], /:6/u);
});

test('Chinese heading requires narrative text around an inline API literal', () => {
  const invalid = '# 中文页面\n\n## `NavHost`\n\n这里解释导航宿主的使用方式和验证路径。\n';
  assert.match(
    analyzeChineseDocument(invalid, 'zh/a.md').join('\n'),
    /Chinese heading must contain Han text/u,
  );

  const valid = '# 中文页面\n\n## 使用 `NavHost` 导航\n\n这里解释导航宿主的使用方式和验证路径。\n';
  assert.deepEqual(analyzeChineseDocument(valid, 'zh/a.md'), []);
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
