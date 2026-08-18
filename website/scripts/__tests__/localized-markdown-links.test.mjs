import assert from 'node:assert/strict';
import {mkdtemp, mkdir, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {createLocalizedMarkdownLinkResolver} from '../../src/config/localizedMarkdownLinks.ts';

test('uses the Docusaurus route for localized targets with number prefixes', async (t) => {
  const repositoryRoot = await mkdtemp(resolve(tmpdir(), 'viewcompose-localized-links-'));
  t.after(() => rm(repositoryRoot, {recursive: true, force: true}));

  const siteDir = resolve(repositoryRoot, 'website');
  const docsDir = resolve(repositoryRoot, 'docs');
  const sourcePath = resolve(
    docsDir,
    'project/plans/animation-compose-capability-expansion.md',
  );
  const targetRelativePath =
    'architecture/decisions/0009-development-tooling-isolation.md';
  const targetPath = resolve(docsDir, targetRelativePath);
  const localizedTargetPath = resolve(
    siteDir,
    'i18n/zh-CN/docusaurus-plugin-content-docs/current',
    targetRelativePath,
  );

  await mkdir(resolve(sourcePath, '..'), {recursive: true});
  await mkdir(resolve(targetPath, '..'), {recursive: true});
  await mkdir(resolve(localizedTargetPath, '..'), {recursive: true});
  await writeFile(sourcePath, '# Animation Compose capability expansion\n');
  await writeFile(targetPath, '# Development tooling isolation\n');
  await writeFile(localizedTargetPath, '# 开发工具隔离\n');

  const resolveLocalizedLink = createLocalizedMarkdownLinkResolver({
    siteDir,
    docsDir,
    locales: ['en', 'zh-CN'],
    defaultLocale: 'en',
    trailingSlash: true,
  });

  assert.equal(
    resolveLocalizedLink({
      sourceFilePath: sourcePath,
      url: '../../architecture/decisions/0009-development-tooling-isolation.md#activation',
    }),
    '/architecture/decisions/development-tooling-isolation/#activation',
  );
});
