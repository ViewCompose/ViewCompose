import {readFile, stat} from 'node:fs/promises';
import {resolve} from 'node:path';
import {
  buildDir,
  collectFiles,
  formatKiB,
  formatMiB,
  readJson,
  relativeBuildPath,
  totalBytes,
  websiteRoot,
} from './site-quality-lib.mjs';

const MIB = 1024 * 1024;
const KIB = 1024;

export async function verifySiteBudgets({buildDurationSeconds} = {}) {
  const budgets = await readJson(resolve(websiteRoot, 'site-budgets.json'));
  const allFiles = await collectFiles(buildDir);
  const javascriptFiles = allFiles.filter((path) => relativeBuildPath(path).startsWith('assets/js/'));
  const cssFiles = allFiles.filter((path) => relativeBuildPath(path).startsWith('assets/css/'));
  const searchIndexes = allFiles.filter((path) => /(^|\/)search-index-[^/]+\.json$/.test(relativeBuildPath(path)));

  const outputBytes = await totalBytes(allFiles);
  const javascriptBytes = await totalBytes(javascriptFiles);
  const cssBytes = await totalBytes(cssFiles);
  const javascriptSizes = await Promise.all(
    javascriptFiles.map(async (path) => ({path, bytes: (await stat(path)).size})),
  );
  const largestJavaScript = javascriptSizes.sort((left, right) => right.bytes - left.bytes)[0];
  const searchIndexSizes = await Promise.all(
    searchIndexes.map(async (path) => ({path, bytes: (await stat(path)).size})),
  );
  const violations = [];

  const check = (actual, maximum, description, format) => {
    if (actual > maximum) {
      violations.push(`${description}: ${format(actual)} exceeds ${format(maximum)}`);
    }
  };

  check(outputBytes, budgets.maxOutputMiB * MIB, 'site output', formatMiB);
  check(javascriptBytes, budgets.maxTotalJavaScriptMiB * MIB, 'total JavaScript', formatMiB);
  check(cssBytes, budgets.maxTotalCssKiB * KIB, 'total CSS', formatKiB);
  if (largestJavaScript) {
    check(
      largestJavaScript.bytes,
      budgets.maxLargestJavaScriptKiB * KIB,
      `largest JavaScript asset (${relativeBuildPath(largestJavaScript.path)})`,
      formatKiB,
    );
  }
  for (const index of searchIndexSizes) {
    check(
      index.bytes,
      budgets.maxSearchIndexMiBPerLocale * MIB,
      `search index (${relativeBuildPath(index.path)})`,
      formatMiB,
    );
  }
  if (buildDurationSeconds !== undefined && buildDurationSeconds > budgets.maxBuildSeconds) {
    violations.push(
      `Docusaurus build time: ${buildDurationSeconds.toFixed(1)} s exceeds ${budgets.maxBuildSeconds} s`,
    );
  }

  const searchLocales = new Set(
    searchIndexSizes.map(({path}) =>
      relativeBuildPath(path).startsWith('zh-CN/') ? 'zh-CN' : 'en',
    ),
  );
  for (const locale of budgets.requiredSearchLocales) {
    if (!searchLocales.has(locale)) {
      violations.push(`missing search index for locale ${locale}`);
    }
  }
  for (const [redirect, target] of Object.entries(budgets.requiredRedirects)) {
    try {
      const html = await readFile(resolve(buildDir, redirect), 'utf8');
      if (!html.includes(`href="${target}"`)) {
        violations.push(`redirect ${redirect} does not declare canonical target ${target}`);
      }
    } catch {
      violations.push(`missing redirect output ${redirect}`);
    }
  }

  if (violations.length > 0) {
    throw new Error(`Site budget verification failed:\n${violations.join('\n')}`);
  }

  const summary = [
    `output ${formatMiB(outputBytes)}/${budgets.maxOutputMiB} MiB`,
    `JavaScript ${formatMiB(javascriptBytes)}/${budgets.maxTotalJavaScriptMiB} MiB`,
    `largest JS ${formatKiB(largestJavaScript?.bytes ?? 0)}/${budgets.maxLargestJavaScriptKiB} KiB`,
    `CSS ${formatKiB(cssBytes)}/${budgets.maxTotalCssKiB} KiB`,
    `${searchIndexSizes.length} search indexes`,
  ];
  if (buildDurationSeconds !== undefined) {
    summary.push(`build ${buildDurationSeconds.toFixed(1)} s/${budgets.maxBuildSeconds} s`);
  }
  console.log(`Site budget verification passed: ${summary.join(', ')}.`);
  return {
    buildDurationSeconds,
    outputBytes,
    javascriptBytes,
    largestJavaScriptBytes: largestJavaScript?.bytes ?? 0,
    cssBytes,
    searchIndexSizes: Object.fromEntries(
      searchIndexSizes.map(({path, bytes}) => [relativeBuildPath(path), bytes]),
    ),
  };
}

if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  verifySiteBudgets().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
