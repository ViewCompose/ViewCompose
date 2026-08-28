import {spawn} from 'node:child_process';
import {mkdir, rm, writeFile} from 'node:fs/promises';
import {dirname, posix, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {ensureRevisionAvailable} from './assemble-versioned-api-docs.mjs';
import {loadDocumentationReleases} from './documentation-releases.mjs';

const websiteRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = resolve(websiteRoot, '..');
const outputRoot = resolve(websiteRoot, 'src/generated/moduleManualPages');
const ignoredNumberPrefix = /^\d+[-_.]\d+/u;
const numberPrefix = /^\d+\s*[-_.]+\s*([^-_.\s].*)$/u;

function stripNumberPrefix(segment) {
  if (ignoredNumberPrefix.test(segment)) return segment;
  return numberPrefix.exec(segment)?.[1] ?? segment;
}

function gitFile(revision, path, root = repositoryRoot) {
  return new Promise((accept, reject) => {
    const child = spawn('git', ['show', `${revision}:${path}`], {
      cwd: root,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    const output = [];
    const errors = [];
    child.stdout.on('data', (chunk) => output.push(chunk));
    child.stderr.on('data', (chunk) => errors.push(chunk));
    child.on('error', reject);
    child.on('exit', (code) => {
      if (code === 0) accept(Buffer.concat(output).toString('utf8'));
      else {
        reject(
          new Error(
            `Unable to read ${path} at ${revision}: ${Buffer.concat(errors).toString('utf8').trim()}`,
          ),
        );
      }
    });
  });
}

export async function ensureManualSourceRevisions(
  entries,
  {root = repositoryRoot, ensureRevision = ensureRevisionAvailable} = {},
) {
  const revisions = [...new Set(entries.map(({sourceRevision}) => sourceRevision))].sort();
  for (const revision of revisions) {
    await ensureRevision(revision, {root});
  }
}

function routeForMarkdown(sourcePath, target) {
  const [path, fragment] = target.split(/(?=#)/u, 2);
  if (!path.endsWith('.md') && !path.endsWith('.mdx')) return undefined;
  const resolved = posix.normalize(posix.join(posix.dirname(sourcePath), path));
  if (!resolved.startsWith('docs/')) return undefined;
  const routeSegments = resolved
    .slice('docs/'.length)
    .replace(/\.mdx?$/u, '')
    .split('/')
    .map(stripNumberPrefix);
  if (['readme', 'index'].includes(routeSegments.at(-1)?.toLowerCase())) routeSegments.pop();
  const route = `/${routeSegments.join('/')}`;
  const slashTerminatedRoute = route === '/' ? route : `${route}/`;
  const targetRoute = `${slashTerminatedRoute}${fragment ?? ''}`;
  return fragment ? `https://docs.viewcompose.com${targetRoute}` : targetRoute;
}

function repositoryFileUrl(sourcePath, target, sourceRevision) {
  const [path, suffix] = target.split(/(?=[?#])/u, 2);
  const resolved = posix.normalize(posix.join(posix.dirname(sourcePath), path));
  if (
    posix.isAbsolute(resolved) ||
    resolved === '..' ||
    resolved.startsWith('../')
  ) {
    return undefined;
  }
  return (
    `https://github.com/ViewCompose/ViewCompose/blob/${sourceRevision}/${resolved}` +
    (suffix ?? '')
  );
}

export function rewriteSnapshotLinks(
  content,
  {artifact, order, entries, sourcePath, sourceRevision},
) {
  const versionAtRelease = (targetArtifact) =>
    entries
      .filter((entry) => entry.artifact === targetArtifact && entry.order <= order)
      .at(-1)?.version;
  let rewritten = content.replace(
    /https:\/\/docs\.viewcompose\.com\/api\/(viewcompose-[a-z0-9-]+)\/current\//gu,
    (url, targetArtifact) => {
      const version = versionAtRelease(targetArtifact);
      return version
        ? `https://docs.viewcompose.com/api/${targetArtifact}/${version}/`
        : url;
    },
  );
  rewritten = rewritten.replace(/\]\(([^)]+)\)/gu, (link, rawTarget) => {
    const target = rawTarget.trim().replace(/^<|>$/gu, '');
    if (!target.startsWith('.')) return link;
    const route = routeForMarkdown(sourcePath, target);
    if (!route) {
      const repositoryUrl = repositoryFileUrl(sourcePath, target, sourceRevision);
      return repositoryUrl ? `](${repositoryUrl})` : link;
    }
    if (/^(?:https:\/\/docs\.viewcompose\.com)?\/project\/plans\//u.test(route)) {
      const repositoryUrl = repositoryFileUrl(sourcePath, target, sourceRevision);
      return repositoryUrl ? `](${repositoryUrl})` : link;
    }
    const moduleRoute = /^\/modules\/(viewcompose-[a-z0-9-]+)\/(#.*)?$/u.exec(route);
    if (!moduleRoute) return `](${route})`;
    const version = versionAtRelease(moduleRoute[1]);
    return version
      ? `](/modules/${moduleRoute[1]}/${version}/${moduleRoute[2] ?? ''})`
      : `](${route})`;
  });
  return rewritten;
}

function removeFrontMatter(content) {
  return content.replace(/^---\r?\n[\s\S]*?\r?\n---(?:\r?\n|$)/u, '');
}

export function versionedManualDocument(entry, source, entries) {
  const sourcePath = `docs/modules/${entry.artifact}/README.md`;
  const body = rewriteSnapshotLinks(removeFrontMatter(source), {
    artifact: entry.artifact,
    order: entry.order,
    entries,
    sourcePath,
    sourceRevision: entry.sourceRevision,
  });
  const title = /^#\s+(.+)$/mu.exec(body)?.[1] ?? entry.artifact;
  const revisionUrl =
    `https://github.com/ViewCompose/ViewCompose/blob/${entry.sourceRevision}/${sourcePath}`;
  return `---
title: ${JSON.stringify(`${title} ${entry.version}`)}
slug: /modules/${entry.artifact}/${entry.version}
pagination_next: null
pagination_prev: null
custom_edit_url: null
---

> **Released documentation snapshot.** This immutable manual describes
> \`${entry.artifact}:${entry.version}\` from
> [source revision \`${entry.sourceRevision.slice(0, 8)}\`](${revisionUrl}). For current guidance,
> open the [current module catalog](/modules/).

<span data-viewcompose-static-release-manual="source"></span>

${body.trim()}\n`;
}

export async function generateVersionedModuleManuals({
  root = repositoryRoot,
  destination = outputRoot,
  releaseLoader = loadDocumentationReleases,
  ensureRevision = ensureRevisionAvailable,
  readRevisionFile = gitFile,
} = {}) {
  const releases = await releaseLoader(root);
  await ensureManualSourceRevisions(releases.entries, {root, ensureRevision});
  await rm(destination, {recursive: true, force: true});
  await mkdir(destination, {recursive: true});
  for (const entry of releases.entries) {
    const sourcePath = `docs/modules/${entry.artifact}/README.md`;
    const source = await readRevisionFile(entry.sourceRevision, sourcePath, root);
    const output = resolve(destination, 'modules', entry.artifact, `${entry.version}.md`);
    await mkdir(dirname(output), {recursive: true});
    await writeFile(output, versionedManualDocument(entry, source, releases.entries), 'utf8');
  }
  await writeFile(
    resolve(destination, 'manifest.json'),
    `${JSON.stringify(releases.entries, null, 2)}\n`,
    'utf8',
  );
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  generateVersionedModuleManuals().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
