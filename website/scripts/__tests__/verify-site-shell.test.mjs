import assert from 'node:assert/strict';
import test from 'node:test';
import {analyzeSiteShellPages} from '../verify-site-shell.mjs';

const homepage = (themeStorageKey, body = '<main>Documentation</main>') => `
<!doctype html>
<html>
  <head>
    <script>window.localStorage.getItem("${themeStorageKey}");</script>
  </head>
  <body>${body}</body>
</html>
`;

test('accepts one shared color-mode storage key across localized homepages', () => {
  const result = analyzeSiteShellPages({
    'index.html': homepage('theme-viewcompose-docs'),
    'zh-CN/index.html': homepage('theme-viewcompose-docs'),
  });

  assert.deepEqual(result.violations, []);
  assert.equal(result.themeStorageKey, 'theme-viewcompose-docs');
});

test('rejects locale-specific color-mode storage keys', () => {
  const result = analyzeSiteShellPages({
    'index.html': homepage('theme-english'),
    'zh-CN/index.html': homepage('theme-chinese'),
  });

  assert.equal(result.violations.length, 1);
  assert.match(result.violations[0], /different color-mode storage keys/u);
});

test('rejects the removed standalone Maven coordinate', () => {
  const result = analyzeSiteShellPages({
    'index.html': homepage(
      'theme-viewcompose-docs',
      '<main>com.viewcompose:viewcompose-ui-foundation:0.1.0-alpha01</main>',
    ),
    'zh-CN/index.html': homepage('theme-viewcompose-docs'),
  });

  assert.equal(result.violations.length, 1);
  assert.match(result.violations[0], /standalone Maven coordinate/u);
});
