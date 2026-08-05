import {existsSync, readFileSync} from 'node:fs';
import {dirname, isAbsolute, relative, resolve, sep} from 'node:path';

type ResolverOptions = {
  siteDir: string;
  docsDir: string;
  locales: readonly string[];
  defaultLocale: string;
  trailingSlash: boolean;
};

type BrokenMarkdownLink = {
  sourceFilePath: string;
  url: string;
};

const MARKDOWN_EXTENSION = /\.mdx?$/i;

function canonicalSourcePath(siteDir: string, sourceFilePath: string): string | undefined {
  if (isAbsolute(sourceFilePath)) {
    return existsSync(sourceFilePath) ? sourceFilePath : undefined;
  }

  const candidates = [resolve(process.cwd(), sourceFilePath), resolve(siteDir, sourceFilePath)];
  return candidates.find(existsSync);
}

function isWithin(parent: string, child: string): boolean {
  const path = relative(parent, child);
  return path !== '' && path !== '..' && !path.startsWith(`..${sep}`) && !isAbsolute(path);
}

function canonicalRoute(
  sourcePath: string,
  docsDir: string,
  trailingSlash: boolean,
): string {
  const source = readFileSync(sourcePath, 'utf8');
  const frontMatter = source.match(/^---\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)/)?.[1];
  const declaredSlug = frontMatter
    ?.match(/^slug:\s*(.+?)\s*$/m)?.[1]
    ?.replace(/^['"]|['"]$/g, '');
  let route: string;
  if (declaredSlug) {
    route = declaredSlug.startsWith('/') ? declaredSlug : `/${declaredSlug}`;
  } else {
    const relativeSource = relative(docsDir, sourcePath).split(sep).join('/');
    const routeSegments = relativeSource.replace(MARKDOWN_EXTENSION, '').split('/');
    const lastSegment = routeSegments.at(-1)?.toLowerCase();
    if (lastSegment === 'readme' || lastSegment === 'index') {
      routeSegments.pop();
    }
    route = routeSegments.length === 0 ? '/' : `/${routeSegments.join('/')}`;
  }

  return trailingSlash && route !== '/' && !route.endsWith('/') ? `${route}/` : route;
}

/**
 * Docusaurus replaces a canonical document with its localized mirror before resolving Markdown
 * links. A fallback English page can therefore fail to resolve a repository-relative link whose
 * target has already been translated. Rewrite only that verified case to the target's public
 * route; unresolved links continue through Docusaurus' strict broken-link gate.
 */
export function createLocalizedMarkdownLinkResolver({
  siteDir,
  docsDir,
  locales,
  defaultLocale,
  trailingSlash,
}: ResolverOptions): (link: BrokenMarkdownLink) => string | undefined {
  const localizedRoots = locales
    .filter((locale) => locale !== defaultLocale)
    .map((locale) =>
      resolve(siteDir, 'i18n', locale, 'docusaurus-plugin-content-docs', 'current'),
    );

  return ({sourceFilePath, url}) => {
    const sourcePath = canonicalSourcePath(siteDir, sourceFilePath);
    const urlPath = url.match(/^[^?#]*/)?.[0];
    if (!sourcePath || !urlPath || !MARKDOWN_EXTENSION.test(urlPath)) {
      return undefined;
    }

    const targetPath = resolve(dirname(sourcePath), decodeURIComponent(urlPath));
    if (!isWithin(docsDir, targetPath) || !existsSync(targetPath)) {
      return undefined;
    }

    const relativeTarget = relative(docsDir, targetPath);
    const hasLocalizedMirror = localizedRoots.some((root) =>
      existsSync(resolve(root, relativeTarget)),
    );
    if (!hasLocalizedMirror) {
      return undefined;
    }

    return `${canonicalRoute(targetPath, docsDir, trailingSlash)}${url.slice(urlPath.length)}`;
  };
}
