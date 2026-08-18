import {readFile, writeFile} from 'node:fs/promises';
import {dirname, relative, resolve, sep} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {
  canonicalSourceHash,
  parseTranslationFrontMatter,
} from './verify-translations.mjs';

const scriptPath = fileURLToPath(import.meta.url);
const defaultWebsiteRoot = resolve(dirname(scriptPath), '..');
const defaultRepositoryRoot = resolve(defaultWebsiteRoot, '..');

function resolveRelativePath(root, path, label) {
  const absolutePath = resolve(root, path);
  const relativePath = relative(root, absolutePath);
  if (
    relativePath === '' ||
    relativePath === '..' ||
    relativePath.startsWith(`..${sep}`)
  ) {
    throw new Error(`${label} must be a file path inside its root: ${path}`);
  }
  return absolutePath;
}

export async function markTranslationsReviewed({
  sourcePaths,
  repositoryRoot = defaultRepositoryRoot,
  websiteRoot = defaultWebsiteRoot,
} = {}) {
  if (!Array.isArray(sourcePaths) || sourcePaths.length === 0) {
    throw new Error(
      'Pass at least one canonical path relative to docs/, after reviewing its Chinese mirror.',
    );
  }

  const canonicalRoot = resolve(repositoryRoot, 'docs');
  const translationRoot = resolve(
    websiteRoot,
    'i18n/zh-CN/docusaurus-plugin-content-docs/current',
  );
  const results = [];

  for (const sourcePath of [...new Set(sourcePaths)]) {
    const canonicalPath = resolveRelativePath(canonicalRoot, sourcePath, 'Canonical source');
    const translationPath = resolveRelativePath(translationRoot, sourcePath, 'Chinese mirror');
    const [canonicalContent, translationContent] = await Promise.all([
      readFile(canonicalPath, 'utf8'),
      readFile(translationPath, 'utf8'),
    ]);
    const frontMatter = parseTranslationFrontMatter(translationContent, sourcePath);
    if (frontMatter.get('translation_source') !== sourcePath) {
      throw new Error(
        `${sourcePath} -> translation_source must match the reviewed canonical path`,
      );
    }
    if (frontMatter.get('translation_status') !== 'current') {
      throw new Error(`${sourcePath} -> only a current translation can be marked reviewed`);
    }

    const fingerprintLines = translationContent.match(/^translation_source_hash:.*$/gmu) ?? [];
    if (fingerprintLines.length !== 1) {
      throw new Error(`${sourcePath} -> expected exactly one translation_source_hash field`);
    }

    const fingerprint = canonicalSourceHash(canonicalContent);
    const nextContent = translationContent.replace(
      /^translation_source_hash:.*$/mu,
      `translation_source_hash: ${fingerprint}`,
    );
    if (nextContent !== translationContent) {
      await writeFile(translationPath, nextContent);
    }
    results.push({sourcePath, fingerprint, changed: nextContent !== translationContent});
  }

  return results;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const results = await markTranslationsReviewed({sourcePaths: process.argv.slice(2)});
    for (const result of results) {
      const action = result.changed ? 'Updated' : 'Confirmed';
      console.log(`${action} reviewed fingerprint for ${result.sourcePath}: ${result.fingerprint}`);
    }
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
