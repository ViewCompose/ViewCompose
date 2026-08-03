import assert from 'node:assert/strict';
import {mkdtemp, mkdir, readFile, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, resolve} from 'node:path';
import test from 'node:test';
import {fileURLToPath} from 'node:url';
import {
  canonicalSourceHash,
  verifyTranslationTree,
} from '../verify-translations.mjs';

const websiteRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');

async function createFixture({
  source = '# Canonical\n',
  translationStatus = 'current',
  recordedSource = source,
  includeTranslation = true,
  required = ['guide.md'],
  includeStaleMarker = true,
} = {}) {
  const repositoryRoot = await mkdtemp(resolve(tmpdir(), 'viewcompose-i18n-'));
  const websiteRoot = resolve(repositoryRoot, 'website');
  const translationRoot = resolve(
    websiteRoot,
    'i18n/zh-CN/docusaurus-plugin-content-docs/current',
  );
  await mkdir(resolve(repositoryRoot, 'docs'), {recursive: true});
  await mkdir(translationRoot, {recursive: true});
  await writeFile(resolve(repositoryRoot, 'docs/guide.md'), source);
  await writeFile(
    resolve(websiteRoot, 'i18n/translation-policy.json'),
    JSON.stringify({
      schemaVersion: 1,
      locale: 'zh-CN',
      required,
      staleMarker: ':::warning 翻译状态',
    }),
  );
  if (includeTranslation) {
    const marker =
      translationStatus === 'stale' && includeStaleMarker ? '\n:::warning 翻译状态\n过期\n:::\n' : '';
    await writeFile(
      resolve(translationRoot, 'guide.md'),
      `---\ntranslation_source: guide.md\ntranslation_source_hash: ${canonicalSourceHash(recordedSource)}\ntranslation_status: ${translationStatus}\n---\n${marker}\n# 中文\n`,
    );
  }
  return {repositoryRoot, websiteRoot};
}

test('accepts a current required translation', async () => {
  const fixture = await createFixture();
  const result = await verifyTranslationTree(fixture);
  assert.equal(result.currentCount, 1);
  assert.equal(result.staleCount, 0);
});

test('rejects a missing required translation', async () => {
  const fixture = await createFixture({includeTranslation: false});
  await assert.rejects(
    verifyTranslationTree(fixture),
    /required Chinese translation is missing/u,
  );
});

test('rejects source drift presented as current', async () => {
  const fixture = await createFixture({recordedSource: '# Older canonical\n'});
  await assert.rejects(verifyTranslationTree(fixture), /current translation is behind/u);
});

test('permits an explicitly stale tracked translation with a warning', async () => {
  const fixture = await createFixture({
    translationStatus: 'stale',
    recordedSource: '# Older canonical\n',
    required: [],
  });
  const result = await verifyTranslationTree(fixture);
  assert.equal(result.staleCount, 1);
  assert.equal(result.warnings.length, 1);
});

test('rejects a stale required translation', async () => {
  const fixture = await createFixture({
    translationStatus: 'stale',
    recordedSource: '# Older canonical\n',
  });
  await assert.rejects(verifyTranslationTree(fixture), /required translation cannot be stale/u);
});

test('rejects stale content without its visible warning', async () => {
  const fixture = await createFixture({
    translationStatus: 'stale',
    recordedSource: '# Older canonical\n',
    required: [],
    includeStaleMarker: false,
  });
  await assert.rejects(verifyTranslationTree(fixture), /stale translation warning is missing/u);
});

test('keeps every public Compose migration page in the required tier', async () => {
  const policy = JSON.parse(
    await readFile(resolve(websiteRoot, 'i18n/translation-policy.json'), 'utf8'),
  );
  assert.deepEqual(
    policy.required.filter((path) => path.startsWith('migration/')),
    [
      'migration/README.md',
      'migration/compose-host-lifecycle-and-android-interop.md',
      'migration/compose-layout-modifier-and-environment.md',
      'migration/compose-navigation.md',
      'migration/compose-state-recomposition-and-restoration.md',
    ],
  );
});
