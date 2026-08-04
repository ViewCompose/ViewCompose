import {rm} from 'node:fs/promises';
import {resolve} from 'node:path';
import {buildDir} from './site-quality-lib.mjs';

/**
 * Removes locale-prefixed copies of the canonical Dokka output.
 *
 * Docusaurus copies every static directory into every locale output. Dokka API pages are not
 * localized and all documentation links intentionally target `/api/**`, so retaining those copies
 * would multiply the immutable release history by the locale count without adding a route readers
 * use.
 */
export async function pruneLocalizedApiCopies({
  buildDirectory = buildDir,
  localizedOutputDirectories = ['zh-CN'],
} = {}) {
  for (const localeDirectory of localizedOutputDirectories) {
    await rm(resolve(buildDirectory, localeDirectory, 'api'), {recursive: true, force: true});
  }
}
