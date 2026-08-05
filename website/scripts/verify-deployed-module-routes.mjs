import {execFile} from 'node:child_process';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {promisify} from 'node:util';
import {loadDocumentationReleases} from './documentation-releases.mjs';

const websiteRoot = resolve(import.meta.dirname, '..');
const repositoryRoot = resolve(websiteRoot, '..');
const locales = ['', 'zh-CN'];
const notFoundMarkers = ['Page Not Found', '找不到页面'];
const execFileAsync = promisify(execFile);
const statusMarker = '\nVIEWCOMPOSE_HTTP_STATUS:';

function routeFor(locale, artifact) {
  const localePrefix = locale ? `/${locale}` : '';
  return artifact ? `${localePrefix}/modules/${artifact}/` : `${localePrefix}/modules/`;
}

function pageFailure({body, response, route}, artifacts, catalog) {
  if (!response.ok) return `returned HTTP ${response.status}`;
  if (notFoundMarkers.some((marker) => body.includes(marker))) {
    return 'rendered the Docusaurus not-found page';
  }
  if (!body.includes('plugin-id-default')) {
    return 'was not rendered by the primary documentation plugin';
  }
  if (catalog) {
    const missing = artifacts.filter((artifact) => !body.includes(routeFor(catalog.locale, artifact)));
    if (missing.length > 0) return `is missing module links: ${missing.join(', ')}`;
  } else if (!body.includes(route.split('/').filter(Boolean).at(-1))) {
    return 'does not contain its module artifact id';
  }
  return undefined;
}

async function inspectRoute({baseUrl, cacheKey, fetchImpl, route, artifacts, catalog}) {
  const url = new URL(route, baseUrl);
  url.searchParams.set('deployment-check', cacheKey);
  try {
    const response = await fetchImpl(url);
    const body = await response.text();
    const failure = pageFailure({body, response, route}, artifacts, catalog);
    return failure ? {route, failure} : {route};
  } catch (error) {
    return {route, failure: `request failed: ${error.message}`};
  }
}

async function requestWithCurl(url) {
  const {stdout} = await execFileAsync(
    'curl',
    [
      '--http1.1',
      '--silent',
      '--show-error',
      '--location',
      '--retry',
      '2',
      '--retry-all-errors',
      '--retry-delay',
      '1',
      '--connect-timeout',
      '15',
      '--max-time',
      '45',
      '--header',
      'cache-control: no-cache',
      '--user-agent',
      'ViewCompose-deployment-verifier',
      '--write-out',
      `${statusMarker}%{http_code}`,
      url.href,
    ],
    {maxBuffer: 1024 * 1024, timeout: 50_000},
  );
  const markerIndex = stdout.lastIndexOf(statusMarker);
  if (markerIndex < 0) throw new Error('curl response did not include an HTTP status');
  const body = stdout.slice(0, markerIndex);
  const status = Number.parseInt(stdout.slice(markerIndex + statusMarker.length), 10);
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => body,
  };
}

async function mapWithConcurrency(items, concurrency, worker) {
  const results = new Array(items.length);
  let nextIndex = 0;
  async function run() {
    while (nextIndex < items.length) {
      const index = nextIndex;
      nextIndex += 1;
      results[index] = await worker(items[index]);
    }
  }
  await Promise.all(Array.from({length: Math.min(concurrency, items.length)}, run));
  return results;
}

export async function verifyDeployedModuleRoutes({
  baseUrl,
  artifacts,
  fetchImpl = requestWithCurl,
  cacheKey = 'local',
  attempts = 6,
  retryDelayMs = 5_000,
  concurrency = 8,
  wait = (milliseconds) => new Promise((accept) => setTimeout(accept, milliseconds)),
}) {
  const compatibilityArtifact = artifacts.includes('viewcompose-animation')
    ? 'viewcompose-animation'
    : artifacts[0];
  const routes = locales.flatMap((locale) => [
    {route: routeFor(locale), catalog: {locale}},
    {route: routeFor(locale).slice(0, -1), catalog: {locale}},
    ...artifacts.map((artifact) => ({route: routeFor(locale, artifact)})),
    {route: routeFor(locale, compatibilityArtifact).slice(0, -1)},
  ]);
  let pending = routes;

  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    const inspected = await mapWithConcurrency(pending, concurrency, (entry) =>
      inspectRoute({baseUrl, cacheKey, fetchImpl, artifacts, ...entry}),
    );
    pending = inspected.filter((result) => result.failure);
    if (pending.length === 0) {
      console.log(
        `Deployed documentation verification passed: ${artifacts.length} current module manuals ` +
          `in ${locales.length} locales, plus no-trailing-slash compatibility routes, are live ` +
          `at ${baseUrl}.`,
      );
      return {artifacts: artifacts.length, locales: locales.length, routes: routes.length};
    }
    if (attempt < attempts) await wait(retryDelayMs);
  }

  throw new Error(
    [
      `Deployed documentation verification failed at ${baseUrl}:`,
      ...pending.map(({route, failure}) => `- ${route} -> ${failure}`),
    ].join('\n'),
  );
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const baseUrl = process.env.DOCUMENTATION_BASE_URL ?? 'https://docs.viewcompose.com';
  const releases = await loadDocumentationReleases(repositoryRoot);
  try {
    await verifyDeployedModuleRoutes({
      baseUrl,
      artifacts: [...releases.current.keys()].sort(),
      cacheKey: process.env.GITHUB_SHA ?? `${Date.now()}`,
    });
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
