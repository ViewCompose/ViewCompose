import {readFile, stat} from 'node:fs/promises';
import {relative, resolve} from 'node:path';
import {
  buildDir,
  collectFiles,
  formatKiB,
  formatMiB,
  readJson,
  totalBytes,
  websiteRoot,
} from './site-quality-lib.mjs';

const MIB = 1024 * 1024;
const KIB = 1024;

export async function verifySiteBudgets({
  buildDurationSeconds,
  buildDirectory = buildDir,
  budgetsPath = resolve(websiteRoot, 'site-budgets.json'),
} = {}) {
  const budgets = await readJson(budgetsPath);
  const allFiles = await collectFiles(buildDirectory);
  const relativePath = (path) => relative(buildDirectory, path).replaceAll('\\', '/');
  const javascriptFiles = allFiles.filter((path) => relativePath(path).startsWith('assets/js/'));
  const cssFiles = allFiles.filter((path) => relativePath(path).startsWith('assets/css/'));
  const searchIndexes = allFiles.filter((path) => /(^|\/)search-index-[^/]+\.json$/.test(relativePath(path)));
  const canonicalApiFiles = allFiles.filter((path) => relativePath(path).startsWith('api/'));
  const localizedApiCopies = allFiles.filter((path) => {
    const segments = relativePath(path).split('/');
    return segments[1] === 'api' && !(segments.length === 3 && segments[2] === 'index.html');
  });
  const apiManifest = await readJson(resolve(buildDirectory, 'api', 'manifest.json'));

  const outputBytes = await totalBytes(allFiles);
  const apiBytes = await totalBytes(canonicalApiFiles);
  const nonApiBytes = outputBytes - apiBytes;
  const javascriptBytes = await totalBytes(javascriptFiles);
  const cssBytes = await totalBytes(cssFiles);
  const javascriptSizes = await Promise.all(
    javascriptFiles.map(async (path) => ({path, bytes: (await stat(path)).size})),
  );
  const largestJavaScript = javascriptSizes.sort((left, right) => right.bytes - left.bytes)[0];
  const searchIndexSizes = await Promise.all(
    searchIndexes.map(async (path) => ({path, bytes: (await stat(path)).size})),
  );
  const apiVersionSizes = await Promise.all(
    apiManifest.map(async ({artifact, version}) => {
      const prefix = `api/${artifact}/${version}/`;
      const files = canonicalApiFiles.filter((path) => relativePath(path).startsWith(prefix));
      return {artifact, version, bytes: await totalBytes(files)};
    }),
  );
  const versionedApiBytes = apiVersionSizes.reduce((total, entry) => total + entry.bytes, 0);
  const apiRoutingOverheadBytes = apiBytes - versionedApiBytes;
  const averageApiVersionBytes = apiVersionSizes.length === 0
    ? 0
    : versionedApiBytes / apiVersionSizes.length;
  const violations = [];

  const check = (actual, maximum, description, format) => {
    if (actual > maximum) {
      violations.push(`${description}: ${format(actual)} exceeds ${format(maximum)}`);
    }
  };

  check(nonApiBytes, budgets.maxNonApiOutputMiB * MIB, 'non-API site output', formatMiB);
  check(
    averageApiVersionBytes,
    budgets.maxAverageApiVersionMiB * MIB,
    'average immutable API version',
    formatMiB,
  );
  check(
    apiRoutingOverheadBytes,
    budgets.maxApiRoutingOverheadMiB * MIB,
    'API manifest and alias overhead',
    formatMiB,
  );
  for (const entry of apiVersionSizes) {
    check(
      entry.bytes,
      budgets.maxApiVersionMiB * MIB,
      `API version (${entry.artifact}/${entry.version})`,
      formatMiB,
    );
  }
  if (apiVersionSizes.length === 0) {
    violations.push('API manifest contains no immutable artifact versions');
  }
  if (localizedApiCopies.length > 0) {
    violations.push(
      `localized API output duplicates canonical Dokka files: ${relativePath(localizedApiCopies[0])}`,
    );
  }
  check(javascriptBytes, budgets.maxTotalJavaScriptMiB * MIB, 'total JavaScript', formatMiB);
  check(cssBytes, budgets.maxTotalCssKiB * KIB, 'total CSS', formatKiB);
  if (largestJavaScript) {
    check(
      largestJavaScript.bytes,
      budgets.maxLargestJavaScriptKiB * KIB,
      `largest JavaScript asset (${relativePath(largestJavaScript.path)})`,
      formatKiB,
    );
  }
  for (const index of searchIndexSizes) {
    check(
      index.bytes,
      budgets.maxSearchIndexMiBPerLocale * MIB,
      `search index (${relativePath(index.path)})`,
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
      relativePath(path).startsWith('zh-CN/') ? 'zh-CN' : 'en',
    ),
  );
  for (const locale of budgets.requiredSearchLocales) {
    if (!searchLocales.has(locale)) {
      violations.push(`missing search index for locale ${locale}`);
    }
  }
  for (const [redirect, target] of Object.entries(budgets.requiredRedirects)) {
    try {
      const html = await readFile(resolve(buildDirectory, redirect), 'utf8');
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
    `output ${formatMiB(outputBytes)}`,
    `non-API ${formatMiB(nonApiBytes)}/${formatMiB(budgets.maxNonApiOutputMiB * MIB)}`,
    `API ${apiVersionSizes.length} versions averaging ${formatMiB(averageApiVersionBytes)}/${formatMiB(budgets.maxAverageApiVersionMiB * MIB)}`,
    `API routing overhead ${formatMiB(apiRoutingOverheadBytes)}/${formatMiB(budgets.maxApiRoutingOverheadMiB * MIB)}`,
    `JavaScript ${formatMiB(javascriptBytes)}/${formatMiB(budgets.maxTotalJavaScriptMiB * MIB)}`,
    `largest JS ${formatKiB(largestJavaScript?.bytes ?? 0)}/${formatKiB(budgets.maxLargestJavaScriptKiB * KIB)}`,
    `CSS ${formatKiB(cssBytes)}/${formatKiB(budgets.maxTotalCssKiB * KIB)}`,
    `${searchIndexSizes.length} search indexes`,
  ];
  if (buildDurationSeconds !== undefined) {
    summary.push(`build ${buildDurationSeconds.toFixed(1)} s/${budgets.maxBuildSeconds} s`);
  }
  console.log(`Site budget verification passed: ${summary.join(', ')}.`);
  return {
    buildDurationSeconds,
    outputBytes,
    nonApiBytes,
    apiBytes,
    averageApiVersionBytes,
    apiRoutingOverheadBytes,
    apiVersionSizes: Object.fromEntries(
      apiVersionSizes.map(({artifact, version, bytes}) => [`${artifact}/${version}`, bytes]),
    ),
    javascriptBytes,
    largestJavaScriptBytes: largestJavaScript?.bytes ?? 0,
    cssBytes,
    searchIndexSizes: Object.fromEntries(
      searchIndexSizes.map(({path, bytes}) => [relativePath(path), bytes]),
    ),
  };
}

if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  verifySiteBudgets().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
