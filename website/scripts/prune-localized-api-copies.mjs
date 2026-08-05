import {readdir, rm} from 'node:fs/promises';
import {resolve} from 'node:path';
import {buildDir} from './site-quality-lib.mjs';

/**
 * Removes locale-prefixed copies of the canonical Dokka output.
 *
 * Docusaurus copies every static directory into every locale output. Dokka API pages are not
 * localized and all documentation links intentionally target `/api/**`, so retaining those copies
 * would multiply the immutable release history by the locale count without adding a route readers
 * use. The localized API landing page remains because the localized navigation links to it.
 */
export async function pruneLocalizedApiCopies({
  buildDirectory = buildDir,
  localizedOutputDirectories = ['zh-CN'],
} = {}) {
  for (const localeDirectory of localizedOutputDirectories) {
    const localizedApiDirectory = resolve(buildDirectory, localeDirectory, 'api');
    const entries = await readdir(localizedApiDirectory, {withFileTypes: true}).catch(() => []);
    await Promise.all(
      entries
        .filter((entry) => entry.name !== 'index.html')
        .map((entry) => rm(resolve(localizedApiDirectory, entry.name), {recursive: true, force: true})),
    );
  }
}
