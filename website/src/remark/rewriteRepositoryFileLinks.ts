import {dirname, relative, resolve, sep} from 'node:path';

type MarkdownNode = {
  type?: string;
  url?: string;
  children?: MarkdownNode[];
};

type RemarkFile = {
  path?: string;
};

export type RepositoryFileLinkOptions = {
  repositoryRoot: string;
  docsRoot: string;
  localizedDocsRoots?: string[];
  repositorySourceUrl: string;
};

function isInside(root: string, target: string): boolean {
  const path = relative(root, target);
  return path === '' || (!path.startsWith(`..${sep}`) && path !== '..');
}

function rewriteUrl(
  url: string,
  sourcePath: string,
  options: RepositoryFileLinkOptions,
): string {
  if (url === '' || url.startsWith('#') || /^[a-z][a-z\d+.-]*:/iu.test(url) || url.startsWith('//')) {
    return url;
  }

  const suffixIndex = url.search(/[?#]/u);
  const pathname = suffixIndex === -1 ? url : url.slice(0, suffixIndex);
  const suffix = suffixIndex === -1 ? '' : url.slice(suffixIndex);
  if (pathname === '' || pathname.startsWith('/')) {
    return url;
  }

  const target = resolve(dirname(sourcePath), decodeURI(pathname));
  const documentationRoots = [options.docsRoot, ...(options.localizedDocsRoots ?? [])];
  // Localized Markdown is also documentation. Let Docusaurus resolve these links
  // before treating other repository files as source-only GitHub links.
  if (!isInside(options.repositoryRoot, target) || documentationRoots.some(root => isInside(root, target))) {
    return url;
  }

  const repositoryPath = relative(options.repositoryRoot, target).split(sep).join('/');
  return `${options.repositorySourceUrl}/${encodeURI(repositoryPath)}${suffix}`;
}

/**
 * Keeps repository-relative source links verifiable while preventing Docusaurus from copying the
 * linked production and test files into the deployed documentation artifact.
 */
export default function rewriteRepositoryFileLinks(options: RepositoryFileLinkOptions) {
  return (tree: MarkdownNode, file: RemarkFile): void => {
    if (!file.path) {
      return;
    }

    const visit = (node: MarkdownNode): void => {
      if (node.type === 'link' && typeof node.url === 'string') {
        node.url = rewriteUrl(node.url, file.path, options);
      }
      node.children?.forEach(visit);
    };
    visit(tree);
  };
}
