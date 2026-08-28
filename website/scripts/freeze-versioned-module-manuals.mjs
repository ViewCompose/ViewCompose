import {readFile, rm, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {buildDir, collectFiles, relativeBuildPath} from './site-quality-lib.mjs';
import {loadDocumentationReleases} from './documentation-releases.mjs';

const repositoryRoot = resolve(import.meta.dirname, '..', '..');
export const staticManualMarker = 'data-viewcompose-static-release-manual';
const externalScriptPattern = /<script\b[^>]*\bsrc=(?:"[^"]*"|'[^']*'|[^\s>]+)[^>]*><\/script>/giu;

function manualPath(buildDirectory, locale, {artifact, version}) {
  return resolve(
    buildDirectory,
    ...(locale ? [locale] : []),
    'modules',
    artifact,
    version,
    'index.html',
  );
}

export function freezeVersionedManualHtml(source) {
  const withoutHydration = source.replace(externalScriptPattern, '');
  if (withoutHydration.includes(`<html ${staticManualMarker}`)) return withoutHydration;
  return withoutHydration.replace('<html ', `<html ${staticManualMarker}=true `);
}

export async function freezeVersionedModuleManuals({
  buildDirectory = buildDir,
  root = repositoryRoot,
  locales = ['', 'zh-CN'],
  releaseLoader = loadDocumentationReleases,
} = {}) {
  const releases = await releaseLoader(root);
  let manualPages = 0;
  for (const locale of locales) {
    for (const entry of releases.entries) {
      const path = manualPath(buildDirectory, locale, entry);
      const source = await readFile(path, 'utf8');
      const frozen = freezeVersionedManualHtml(source);
      if (frozen === source) {
        throw new Error(`Versioned module manual has no hydration payload to freeze: ${path}`);
      }
      await writeFile(path, frozen, 'utf8');
      manualPages += 1;
    }
  }

  const javascriptFiles = await collectFiles(
    buildDirectory,
    (path) => path.endsWith('.js') && relativeBuildPath(path).includes('assets/js/'),
  );
  const snapshotChunks = [];
  for (const path of javascriptFiles) {
    if ((await readFile(path, 'utf8')).includes(staticManualMarker)) snapshotChunks.push(path);
  }
  if (snapshotChunks.length !== manualPages) {
    throw new Error(
      `Expected one versioned-manual JavaScript chunk per static page: ` +
        `${snapshotChunks.length} chunks for ${manualPages} pages.`,
    );
  }
  await Promise.all(snapshotChunks.map((path) => rm(path)));
  console.log(
    `Versioned module manuals frozen as static HTML: ${manualPages} pages and ` +
      `${snapshotChunks.length} redundant hydration chunks removed.`,
  );
  return {manualPages, removedChunks: snapshotChunks.length};
}

