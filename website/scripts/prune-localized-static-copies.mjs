import {access, rm} from 'node:fs/promises';
import {resolve} from 'node:path';
import {buildDir} from './site-quality-lib.mjs';

const canonicalStaticAssets = ['img/social-card.png'];

/**
 * Removes locale copies of assets whose generated pages use one canonical absolute URL.
 *
 * Docusaurus copies the complete static directory into every locale even when an asset is not
 * localized. The canonical root copy remains deployed, so deleting these exact duplicates does
 * not remove a supported route.
 */
export async function pruneLocalizedStaticCopies({
  buildDirectory = buildDir,
  localizedOutputDirectories = ['zh-CN'],
} = {}) {
  await Promise.all(
    canonicalStaticAssets.map((asset) => access(resolve(buildDirectory, asset))),
  );
  await Promise.all(
    localizedOutputDirectories.flatMap((localeDirectory) =>
      canonicalStaticAssets.map((asset) =>
        rm(resolve(buildDirectory, localeDirectory, asset), {force: true}),
      ),
    ),
  );
}
