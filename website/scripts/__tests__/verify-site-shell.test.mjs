import assert from 'node:assert/strict';
import test from 'node:test';
import {
  analyzeSiteShellPages,
  analyzeSiteShellStyles,
} from '../verify-site-shell.mjs';

const homepage = (themeStorageKey, body = '<main>Documentation</main>') => `
<!doctype html>
<html>
  <head>
    <meta property="og:image" content="https://docs.viewcompose.com/img/social-card.png">
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
  assert.equal(result.socialCard, 'https://docs.viewcompose.com/img/social-card.png');
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

test('rejects a locale-prefixed social card URL', () => {
  const localizedCard = homepage('theme-viewcompose-docs').replaceAll(
    'https://docs.viewcompose.com/img/social-card.png',
    'https://docs.viewcompose.com/zh-CN/img/social-card.png',
  );
  const result = analyzeSiteShellPages({
    'index.html': homepage('theme-viewcompose-docs'),
    'zh-CN/index.html': localizedCard,
  });

  assert.equal(result.violations.length, 1);
  assert.match(result.violations[0], /social card must use/u);
});

test('rejects navbar styles that confine the fixed mobile sidebar', () => {
  const violations = analyzeSiteShellStyles({
    'assets/css/styles.css': '.navbar { backdrop-filter: blur(16px); }',
  });

  assert.equal(violations.length, 1);
  assert.match(violations[0], /\.navbar must not set backdrop-filter/u);
});

test('allows visual effects on a navbar pseudo-element', () => {
  const violations = analyzeSiteShellStyles({
    'assets/css/styles.css': [
      '.navbar::before { backdrop-filter: blur(16px); }',
      '.navbar:before{-webkit-backdrop-filter:blur(16px)}',
      '.navbar__inner { transform: translateZ(0); }',
    ].join('\n'),
  });

  assert.deepEqual(violations, []);
});
