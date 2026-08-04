import assert from 'node:assert/strict';
import {mkdir, mkdtemp, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {verifySiteBudgets} from '../verify-site-budgets.mjs';

const permissiveBudgets = Object.freeze({
  maxBuildSeconds: 120,
  maxNonApiOutputMiB: 1,
  maxAverageApiVersionMiB: 1,
  maxApiVersionMiB: 1,
  maxApiRoutingOverheadMiB: 1,
  maxLargestJavaScriptKiB: 1024,
  maxTotalJavaScriptMiB: 1,
  maxTotalCssKiB: 1024,
  maxSearchIndexMiBPerLocale: 1,
  requiredSearchLocales: [],
  requiredRedirects: {},
});

async function fixture() {
  const root = await mkdtemp(resolve(tmpdir(), 'viewcompose-site-budget-test-'));
  const buildDirectory = resolve(root, 'build');
  const budgetsPath = resolve(root, 'site-budgets.json');
  await mkdir(resolve(buildDirectory, 'api/artifact/1.0.0'), {recursive: true});
  await writeFile(
    resolve(buildDirectory, 'api/manifest.json'),
    `${JSON.stringify([{artifact: 'artifact', version: '1.0.0'}])}\n`,
    'utf8',
  );
  await writeFile(resolve(buildDirectory, 'api/artifact/1.0.0/index.html'), 'api', 'utf8');
  await writeFile(resolve(buildDirectory, 'index.html'), 'site', 'utf8');
  await writeFile(budgetsPath, `${JSON.stringify(permissiveBudgets)}\n`, 'utf8');
  return {root, buildDirectory, budgetsPath};
}

test('API history is budgeted per immutable artifact version', async () => {
  const {root, buildDirectory, budgetsPath} = await fixture();
  try {
    const result = await verifySiteBudgets({buildDirectory, budgetsPath});

    assert.equal(result.apiVersionSizes['artifact/1.0.0'], 3);
    assert.equal(result.averageApiVersionBytes, 3);
    assert.equal(result.nonApiBytes, 4);
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('localized Dokka copies fail the site budget gate', async () => {
  const {root, buildDirectory, budgetsPath} = await fixture();
  try {
    await mkdir(resolve(buildDirectory, 'zh-CN/api/artifact/1.0.0'), {recursive: true});
    await writeFile(resolve(buildDirectory, 'zh-CN/api/artifact/1.0.0/index.html'), 'copy', 'utf8');

    await assert.rejects(
      verifySiteBudgets({buildDirectory, budgetsPath}),
      /localized API output duplicates canonical Dokka files/u,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});

test('one oversized API version cannot hide behind an acceptable average', async () => {
  const {root, buildDirectory, budgetsPath} = await fixture();
  try {
    await writeFile(
      budgetsPath,
      `${JSON.stringify({...permissiveBudgets, maxApiVersionMiB: 0.000001})}\n`,
      'utf8',
    );

    await assert.rejects(
      verifySiteBudgets({buildDirectory, budgetsPath}),
      /API version \(artifact\/1\.0\.0\)/u,
    );
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});
