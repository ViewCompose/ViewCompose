import {createHash} from 'node:crypto';
import {readdir, readFile} from 'node:fs/promises';
import {dirname, relative, resolve, sep} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';

const scriptPath = fileURLToPath(import.meta.url);
const defaultWebsiteRoot = resolve(dirname(scriptPath), '..');
const defaultRepositoryRoot = resolve(defaultWebsiteRoot, '..');
const markdownExtensions = new Set(['.md', '.mdx']);

export function canonicalSourceHash(content) {
  const normalized = content.replaceAll('\r\n', '\n').replaceAll('\r', '\n');
  return createHash('sha256').update(normalized, 'utf8').digest('hex');
}

export function parseTranslationFrontMatter(content, relativePath) {
  const match = /^---\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)/u.exec(content);
  if (!match) {
    throw new Error(`${relativePath} -> translation front matter is missing`);
  }

  const values = new Map();
  for (const line of match[1].split(/\r?\n/u)) {
    const field = /^([a-z_]+):\s*(.*?)\s*$/u.exec(line);
    if (!field) continue;
    values.set(field[1], field[2].replace(/^(?:"(.*)"|'(.*)')$/u, '$1$2'));
  }
  return values;
}

async function listMarkdownFiles(root) {
  const files = [];
  async function visit(directory) {
    const entries = await readdir(directory, {withFileTypes: true});
    for (const entry of entries) {
      const path = resolve(directory, entry.name);
      if (entry.isDirectory()) {
        await visit(path);
      } else if (markdownExtensions.has(entry.name.slice(entry.name.lastIndexOf('.')))) {
        files.push(path);
      }
    }
  }
  await visit(root);
  return files.sort();
}

function repositoryRelative(path, root) {
  return relative(root, path).split(sep).join('/');
}

function isInside(path, root) {
  const relativePath = relative(root, path);
  return relativePath !== '' && !relativePath.startsWith(`..${sep}`) && relativePath !== '..';
}

export async function verifyTranslationTree({
  repositoryRoot = defaultRepositoryRoot,
  websiteRoot = defaultWebsiteRoot,
} = {}) {
  const policyPath = resolve(websiteRoot, 'i18n/translation-policy.json');
  const canonicalRoot = resolve(repositoryRoot, 'docs');
  const policy = JSON.parse(await readFile(policyPath, 'utf8'));
  const locale = policy.locale;
  const translationRoot = resolve(
    websiteRoot,
    `i18n/${locale}/docusaurus-plugin-content-docs/current`,
  );
  const required = policy.required ?? [];
  const staleMarker = policy.staleMarker;
  const violations = [];
  const warnings = [];

  if (policy.schemaVersion !== 1) {
    violations.push(`translation policy -> unsupported schemaVersion: ${policy.schemaVersion}`);
  }
  if (typeof locale !== 'string' || locale.length === 0) {
    violations.push('translation policy -> locale must be a non-empty string');
  }
  if (!Array.isArray(required) || required.some((path) => typeof path !== 'string')) {
    violations.push('translation policy -> required must be an array of document paths');
  }
  if (new Set(required).size !== required.length) {
    violations.push('translation policy -> required document paths must be unique');
  }
  if (JSON.stringify([...required].sort()) !== JSON.stringify(required)) {
    violations.push('translation policy -> required document paths must be sorted');
  }
  if (typeof staleMarker !== 'string' || staleMarker.length === 0) {
    violations.push('translation policy -> staleMarker must be a non-empty string');
  }

  const translationFiles = await listMarkdownFiles(translationRoot);
  const translationByPath = new Map(
    translationFiles.map((path) => [repositoryRelative(path, translationRoot), path]),
  );
  const requiredSet = new Set(required);

  for (const requiredPath of required) {
    if (!translationByPath.has(requiredPath)) {
      violations.push(`${requiredPath} -> required Chinese translation is missing`);
    }
  }

  let currentCount = 0;
  let staleCount = 0;
  for (const [translationPath, absoluteTranslationPath] of translationByPath) {
    const content = await readFile(absoluteTranslationPath, 'utf8');
    let frontMatter;
    try {
      frontMatter = parseTranslationFrontMatter(content, translationPath);
    } catch (error) {
      violations.push(error.message);
      continue;
    }

    const source = frontMatter.get('translation_source');
    const recordedHash = frontMatter.get('translation_source_hash');
    const status = frontMatter.get('translation_status');
    if (!source) {
      violations.push(`${translationPath} -> translation_source is missing`);
      continue;
    }
    if (source !== translationPath) {
      violations.push(
        `${translationPath} -> translation_source must mirror its locale path, found ${source}`,
      );
    }

    const sourcePath = resolve(canonicalRoot, source);
    if (!isInside(sourcePath, canonicalRoot)) {
      violations.push(`${translationPath} -> translation_source escapes docs/: ${source}`);
      continue;
    }

    let sourceContent;
    try {
      sourceContent = await readFile(sourcePath, 'utf8');
    } catch {
      violations.push(`${translationPath} -> canonical source does not exist: ${source}`);
      continue;
    }

    if (!/^[a-f0-9]{64}$/u.test(recordedHash ?? '')) {
      violations.push(`${translationPath} -> translation_source_hash must be a SHA-256 value`);
      continue;
    }
    if (status !== 'current' && status !== 'stale') {
      violations.push(`${translationPath} -> translation_status must be current or stale`);
      continue;
    }

    const actualHash = canonicalSourceHash(sourceContent);
    if (status === 'current') {
      currentCount += 1;
      if (recordedHash !== actualHash) {
        violations.push(
          `${translationPath} -> current translation is behind ${source}; review and update it`,
        );
      }
    } else {
      staleCount += 1;
      if (requiredSet.has(translationPath)) {
        violations.push(`${translationPath} -> required translation cannot be stale`);
      }
      if (recordedHash === actualHash) {
        violations.push(`${translationPath} -> stale translation matches the current source hash`);
      }
      if (!content.includes(staleMarker)) {
        violations.push(`${translationPath} -> stale translation warning is missing`);
      }
      warnings.push(`${translationPath} -> explicitly stale`);
    }
  }

  if (violations.length > 0) {
    throw new Error(
      ['Translation verification failed:', ...violations.sort().map((item) => `- ${item}`)].join(
        '\n',
      ),
    );
  }

  return {
    locale,
    requiredCount: required.length,
    currentCount,
    staleCount,
    warnings,
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const result = await verifyTranslationTree();
    for (const warning of result.warnings) {
      console.warn(`[translation] ${warning}`);
    }
    console.log(
      `Verified ${result.currentCount} current and ${result.staleCount} stale ` +
        `${result.locale} translations (${result.requiredCount} required).`,
    );
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
