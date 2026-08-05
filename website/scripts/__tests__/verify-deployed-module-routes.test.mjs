import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyDeployedModuleRoutes} from '../verify-deployed-module-routes.mjs';

const artifacts = ['viewcompose-animation', 'viewcompose-runtime'];

function successfulBody(pathname) {
  if (/\/modules\/?$/u.test(pathname)) {
    const locale = pathname.startsWith('/zh-CN/') ? '/zh-CN' : '';
    return `plugin-id-default ${artifacts
      .map((artifact) => `${locale}/modules/${artifact}/`)
      .join(' ')}`;
  }
  return `plugin-id-default ${pathname.split('/').filter(Boolean).at(-1)}`;
}

function response(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => body,
  };
}

test('accepts live catalogs and every localized current module route', async () => {
  const result = await verifyDeployedModuleRoutes({
    baseUrl: 'https://docs.example.test',
    artifacts,
    attempts: 1,
    fetchImpl: async (url) => response(successfulBody(url.pathname)),
  });

  assert.deepEqual(result, {artifacts: 2, locales: 2, routes: 10});
});

test('rejects an HTTP 200 response that renders the localized not-found page', async () => {
  await assert.rejects(
    verifyDeployedModuleRoutes({
      baseUrl: 'https://docs.example.test',
      artifacts,
      attempts: 1,
      fetchImpl: async (url) =>
        response(
          url.pathname === '/zh-CN/modules/viewcompose-animation/'
            ? 'plugin-id-default 找不到页面 viewcompose-animation'
            : successfulBody(url.pathname),
        ),
    }),
    /zh-CN\/modules\/viewcompose-animation\/ -> rendered the Docusaurus not-found page/u,
  );
});

test('retries a stale deployed page before failing the publication', async () => {
  let affectedRequests = 0;
  const result = await verifyDeployedModuleRoutes({
    baseUrl: 'https://docs.example.test',
    artifacts,
    attempts: 2,
    retryDelayMs: 0,
    wait: async () => {},
    fetchImpl: async (url) => {
      if (url.pathname === '/modules/viewcompose-runtime/') {
        affectedRequests += 1;
        if (affectedRequests === 1) return response('Page Not Found');
      }
      return response(successfulBody(url.pathname));
    },
  });

  assert.equal(affectedRequests, 2);
  assert.equal(result.routes, 10);
});
