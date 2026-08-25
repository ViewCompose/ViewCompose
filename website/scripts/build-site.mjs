import {spawn} from 'node:child_process';
import {mkdir, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {performance} from 'node:perf_hooks';
import {websiteRoot} from './site-quality-lib.mjs';
import {pruneLocalizedApiCopies} from './prune-localized-api-copies.mjs';
import {pruneLocalizedStaticCopies} from './prune-localized-static-copies.mjs';
import {verifyAccessibility} from './verify-accessibility.mjs';
import {verifySiteBudgets} from './verify-site-budgets.mjs';
import {verifySiteShell} from './verify-site-shell.mjs';
import {verifyVersionedDocumentation} from './verify-versioned-documentation.mjs';

const cli = resolve(websiteRoot, 'node_modules/@docusaurus/core/bin/docusaurus.mjs');
const qualityReportDirectory = resolve(websiteRoot, '..', 'build', 'reports', 'documentation');
const qualityReportPath = resolve(qualityReportDirectory, 'site-quality-report.json');
const startedAt = performance.now();
const child = spawn(process.execPath, [cli, 'build'], {
  cwd: websiteRoot,
  stdio: 'inherit',
});
const exitCode = await new Promise((resolveExit, reject) => {
  child.once('error', reject);
  child.once('exit', (code, signal) => {
    if (signal) {
      reject(new Error(`Docusaurus build terminated by ${signal}`));
    } else {
      resolveExit(code ?? 1);
    }
  });
});

if (exitCode !== 0) {
  process.exitCode = exitCode;
} else {
  try {
    const buildDurationSeconds = (performance.now() - startedAt) / 1000;
    await pruneLocalizedApiCopies();
    await pruneLocalizedStaticCopies();
    const versionedDocumentation = await verifyVersionedDocumentation();
    const siteShell = await verifySiteShell();
    const accessibility = await verifyAccessibility();
    const budgets = await verifySiteBudgets({buildDurationSeconds});
    await mkdir(qualityReportDirectory, {recursive: true});
    await writeFile(
      qualityReportPath,
      `${JSON.stringify({versionedDocumentation, siteShell, accessibility, budgets}, null, 2)}\n`,
      'utf8',
    );
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
