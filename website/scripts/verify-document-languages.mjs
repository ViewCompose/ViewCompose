import {readdir, readFile} from 'node:fs/promises';
import {dirname, extname, relative, resolve, sep} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';

const scriptPath = fileURLToPath(import.meta.url);
const defaultWebsiteRoot = resolve(dirname(scriptPath), '..');
const defaultRepositoryRoot = resolve(defaultWebsiteRoot, '..');
const markdownExtensions = new Set(['.md', '.mdx']);
const hanPattern = /\p{Script=Han}/gu;
const latinWordPattern = /\b[A-Za-z][A-Za-z'-]*\b/gu;

function repositoryRelative(path, root) {
  return relative(root, path).split(sep).join('/');
}

async function listMarkdownFiles(root, {exclude = () => false} = {}) {
  const files = [];
  async function visit(directory) {
    const entries = await readdir(directory, {withFileTypes: true});
    for (const entry of entries) {
      const path = resolve(directory, entry.name);
      const relativePath = repositoryRelative(path, root);
      if (exclude(relativePath)) continue;
      if (entry.isDirectory()) {
        await visit(path);
      } else if (markdownExtensions.has(extname(entry.name))) {
        files.push(path);
      }
    }
  }
  await visit(root);
  return files.sort();
}

function parseFrontMatter(lines) {
  if (lines[0]?.trim() !== '---') {
    return {endLine: 0, values: new Map()};
  }

  const values = new Map();
  let endLine = 0;
  for (let index = 1; index < lines.length; index += 1) {
    if (lines[index].trim() === '---') {
      endLine = index + 1;
      break;
    }
    const field = /^([A-Za-z0-9_-]+):\s*(.*?)\s*$/u.exec(lines[index]);
    if (field) {
      values.set(field[1], field[2].replace(/^(?:"(.*)"|'(.*)')$/u, '$1$2'));
    }
  }
  return {endLine, values};
}

function stripMarkdownLine(line, commentState) {
  let value = line;

  if (commentState.html) {
    const end = value.indexOf('-->');
    if (end === -1) return {value: '', commentState};
    value = value.slice(end + 3);
    commentState.html = false;
  }
  while (value.includes('<!--')) {
    const start = value.indexOf('<!--');
    const end = value.indexOf('-->', start + 4);
    if (end === -1) {
      value = value.slice(0, start);
      commentState.html = true;
      break;
    }
    value = `${value.slice(0, start)} ${value.slice(end + 3)}`;
  }

  if (commentState.mdx) {
    const end = value.indexOf('*/}');
    if (end === -1) return {value: '', commentState};
    value = value.slice(end + 3);
    commentState.mdx = false;
  }
  while (value.includes('{/*')) {
    const start = value.indexOf('{/*');
    const end = value.indexOf('*/}', start + 3);
    if (end === -1) {
      value = value.slice(0, start);
      commentState.mdx = true;
      break;
    }
    value = `${value.slice(0, start)} ${value.slice(end + 3)}`;
  }

  value = value
    .replace(/`+[^`]*?`+/gu, ' ')
    .replace(/!?\[([^\]]*)\]\([^)]*\)/gu, '$1')
    .replace(/<https?:\/\/[^>]+>/gu, ' ')
    .replace(/https?:\/\/\S+/gu, ' ')
    .replace(/<[^>]+>/gu, ' ');
  return {value, commentState};
}

export function visibleProse(content) {
  const lines = content.replaceAll('\r\n', '\n').replaceAll('\r', '\n').split('\n');
  const frontMatter = parseFrontMatter(lines);
  const visibleLines = [];
  const commentState = {html: false, mdx: false};
  let fence = null;

  for (let index = frontMatter.endLine; index < lines.length; index += 1) {
    const line = lines[index];
    const fenceMatch = /^\s*(`{3,}|~{3,})/u.exec(line);
    if (fenceMatch) {
      if (fence === null) {
        fence = fenceMatch[1][0];
      } else if (fenceMatch[1][0] === fence) {
        fence = null;
      }
      continue;
    }
    if (fence !== null) continue;

    const stripped = stripMarkdownLine(line, commentState);
    visibleLines.push({line: index + 1, text: stripped.value});
  }

  return {frontMatter, visibleLines};
}

function firstHeading(visibleLines) {
  for (const entry of visibleLines) {
    const match = /^\s*#\s+(.+?)\s*#*\s*$/u.exec(entry.text);
    if (match) return {line: entry.line, text: match[1]};
  }
  return null;
}

function markdownHeadings(visibleLines) {
  return visibleLines.flatMap((entry) => {
    const match = /^\s*#{1,6}\s+(.*?)\s*#*\s*$/u.exec(entry.text);
    return match ? [{line: entry.line, text: match[1]}] : [];
  });
}

function proseBlocks(visibleLines, {excludedLines = new Set()} = {}) {
  const blocks = [];
  let current = [];

  function flush() {
    if (current.length > 0) blocks.push(current);
    current = [];
  }

  for (const entry of visibleLines) {
    if (excludedLines.has(entry.line) || entry.text.trim() === '') {
      flush();
      continue;
    }
    current.push(entry);
  }
  flush();
  return blocks;
}

function countMatches(value, pattern) {
  return [...value.matchAll(pattern)].length;
}

export function analyzeCanonicalDocument(content, relativePath = '<document>') {
  const {frontMatter, visibleLines} = visibleProse(content);
  const violations = [];
  const frontMatterTitle = frontMatter.values.get('title');
  if (frontMatterTitle && hanPattern.test(frontMatterTitle)) {
    hanPattern.lastIndex = 0;
    violations.push(`${relativePath}:2 -> canonical title contains Han text: ${frontMatterTitle}`);
  }
  hanPattern.lastIndex = 0;

  for (const entry of visibleLines) {
    if (hanPattern.test(entry.text)) {
      hanPattern.lastIndex = 0;
      const excerpt = entry.text.trim().replace(/\s+/gu, ' ').slice(0, 120);
      violations.push(`${relativePath}:${entry.line} -> canonical prose contains Han text: ${excerpt}`);
    }
    hanPattern.lastIndex = 0;
  }
  return violations;
}

export function analyzeChineseDocument(content, relativePath = '<document>') {
  const {frontMatter, visibleLines} = visibleProse(content);
  const violations = [];
  const heading = firstHeading(visibleLines);
  const headings = markdownHeadings(visibleLines);
  const title = frontMatter.values.get('title') ?? heading?.text;
  const titleLine = frontMatter.values.has('title') ? 2 : heading?.line ?? 1;

  if (!title) {
    violations.push(`${relativePath}:${titleLine} -> Chinese document has no title or level-one heading`);
  } else if (!hanPattern.test(title)) {
    violations.push(`${relativePath}:${titleLine} -> Chinese title must contain Han text: ${title}`);
  }
  hanPattern.lastIndex = 0;

  const titleUsesFirstHeading = !frontMatter.values.has('title') && heading;
  for (const currentHeading of headings) {
    if (titleUsesFirstHeading && currentHeading.line === heading.line) continue;
    if (!hanPattern.test(currentHeading.text)) {
      hanPattern.lastIndex = 0;
      violations.push(
        `${relativePath}:${currentHeading.line} -> Chinese heading must contain Han text: ` +
          currentHeading.text,
      );
    }
    hanPattern.lastIndex = 0;
  }

  const body = visibleLines
    .filter((entry) => entry.line !== heading?.line)
    .map((entry) => entry.text)
    .join('\n');
  const hanCount = countMatches(body, hanPattern);
  const latinWordCount = countMatches(body, latinWordPattern);
  const pageBodyIsMissing = hanCount < 20;
  const pageBodyIsEnglishDominant = latinWordCount > hanCount * 4;
  if (pageBodyIsMissing) {
    violations.push(
      `${relativePath} -> Chinese prose is missing or too short (${hanCount} Han characters)`,
    );
  } else if (pageBodyIsEnglishDominant) {
    violations.push(
      `${relativePath} -> Chinese prose appears English-dominant ` +
        `(${hanCount} Han characters, ${latinWordCount} Latin words)`,
    );
  }

  const headingLines = new Set(headings.map((entry) => entry.line));
  const blocks = pageBodyIsMissing || pageBodyIsEnglishDominant
    ? []
    : proseBlocks(visibleLines, {excludedLines: headingLines});
  for (const block of blocks) {
    const blockText = block.map((entry) => entry.text).join('\n');
    const blockHanCount = countMatches(blockText, hanPattern);
    const blockLatinWordCount = countMatches(blockText, latinWordPattern);
    const isEnglishOnly = blockHanCount === 0 && blockLatinWordCount >= 4;
    const isEnglishDominant =
      blockLatinWordCount >= 12 && blockLatinWordCount > blockHanCount * 4;
    if (isEnglishOnly || isEnglishDominant) {
      const excerpt = blockText.trim().replace(/\s+/gu, ' ').slice(0, 120);
      violations.push(
        `${relativePath}:${block[0].line} -> Chinese prose block appears English-dominant ` +
          `(${blockHanCount} Han characters, ${blockLatinWordCount} Latin words): ${excerpt}`,
      );
    }
  }
  return violations;
}

export function requiredParityViolations({
  canonicalPaths,
  translationPaths,
  requiredPaths,
  locale = 'zh-CN',
}) {
  const violations = [];
  const required = new Set(requiredPaths);
  const publicCanonicalPaths = canonicalPaths.filter(
    (relativePath) =>
      relativePath !== 'project/plans' && !relativePath.startsWith('project/plans/'),
  );

  for (const relativePath of publicCanonicalPaths) {
    if (!required.has(relativePath)) {
      violations.push(
        `docs/${relativePath} -> active public page is missing from translation-policy required`,
      );
    }
  }
  for (const relativePath of translationPaths) {
    if (!required.has(relativePath)) {
      violations.push(
        `website/i18n/${locale}/docusaurus-plugin-content-docs/current/${relativePath}` +
          ' -> locale mirror is not registered as a required public page',
      );
    }
  }
  return violations;
}

export async function verifyDocumentLanguages({
  repositoryRoot = defaultRepositoryRoot,
  websiteRoot = defaultWebsiteRoot,
} = {}) {
  const canonicalRoot = resolve(repositoryRoot, 'docs');
  const policy = JSON.parse(
    await readFile(resolve(websiteRoot, 'i18n/translation-policy.json'), 'utf8'),
  );
  const translationRoot = resolve(
    websiteRoot,
    `i18n/${policy.locale}/docusaurus-plugin-content-docs/current`,
  );
  const canonicalFiles = await listMarkdownFiles(canonicalRoot, {
    exclude: (relativePath) => relativePath === 'archive' || relativePath.startsWith('archive/'),
  });
  const translationFiles = await listMarkdownFiles(translationRoot);
  const violations = [];
  violations.push(
    ...requiredParityViolations({
      canonicalPaths: canonicalFiles.map((file) => repositoryRelative(file, canonicalRoot)),
      translationPaths: translationFiles.map((file) =>
        repositoryRelative(file, translationRoot),
      ),
      requiredPaths: policy.required ?? [],
      locale: policy.locale,
    }),
  );

  for (const file of canonicalFiles) {
    const relativePath = repositoryRelative(file, canonicalRoot);
    violations.push(
      ...analyzeCanonicalDocument(await readFile(file, 'utf8'), `docs/${relativePath}`),
    );
  }
  for (const file of translationFiles) {
    const relativePath = repositoryRelative(file, translationRoot);
    violations.push(
      ...analyzeChineseDocument(
        await readFile(file, 'utf8'),
        `website/i18n/${policy.locale}/docusaurus-plugin-content-docs/current/${relativePath}`,
      ),
    );
  }

  if (violations.length > 0) {
    throw new Error(
      ['Document language verification failed:', ...violations.sort().map((item) => `- ${item}`)].join(
        '\n',
      ),
    );
  }

  return {
    canonicalCount: canonicalFiles.length,
    locale: policy.locale,
    translationCount: translationFiles.length,
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const result = await verifyDocumentLanguages();
    console.log(
      `Verified language consistency for ${result.canonicalCount} canonical English documents ` +
        `and ${result.translationCount} ${result.locale} documents.`,
    );
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
