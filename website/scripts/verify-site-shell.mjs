import {readFile} from 'node:fs/promises';
import {relative, resolve, sep} from 'node:path';
import {buildDir, collectFiles} from './site-quality-lib.mjs';

const homepagePaths = ['index.html', 'zh-CN/index.html'];
const siteAssetDirectories = ['assets', 'zh-CN/assets'];
const removedHomepageCoordinate = 'com.viewcompose:viewcompose-ui-foundation';
const canonicalSocialCard = 'https://docs.viewcompose.com/img/social-card.png';
const themeStoragePattern = /localStorage\.getItem\(["'](theme(?:-[A-Za-z0-9_-]+)?)["']\)/gu;
const cssRulePattern = /([^{}]+)\{([^{}]*)\}/gu;
const cssDeclarationPattern = /(?:^|;)\s*([-A-Za-z]+)\s*:\s*([^;}]+)/gu;
const fixedContainingBlockProperties = new Set([
  '-webkit-backdrop-filter',
  'backdrop-filter',
  'contain',
  'container-type',
  'content-visibility',
  'filter',
  'perspective',
  'transform',
  'will-change',
]);

function normalizedBuildPath(buildDirectory, path) {
  return relative(buildDirectory, path).split(sep).join('/');
}

function selectorTargetsNavbarShell(selector) {
  const withoutComments = selector.replaceAll(/\/\*[\s\S]*?\*\//gu, '').trim();
  const lastCompound = withoutComments.split(/[\s>+~]+/u).filter(Boolean).at(-1) ?? '';
  return /(?:^|[^A-Za-z0-9_-])\.navbar(?![A-Za-z0-9_-])/u.test(lastCompound);
}

function propertyCreatesFixedContainingBlock(property, value) {
  if (!fixedContainingBlockProperties.has(property)) {
    return false;
  }

  const normalizedValue = value.trim().toLowerCase();
  const safeValues = new Set(['initial', 'none', 'revert', 'revert-layer', 'unset']);
  if (safeValues.has(normalizedValue)) {
    return false;
  }
  if (property === 'will-change') {
    return normalizedValue !== 'auto';
  }
  if (property === 'container-type') {
    return normalizedValue !== 'normal';
  }
  if (property === 'content-visibility') {
    return normalizedValue !== 'visible';
  }
  return true;
}

export function analyzeSiteShellStyles(stylesheets) {
  const violations = [];

  for (const [path, css] of Object.entries(stylesheets)) {
    for (const rule of css.matchAll(cssRulePattern)) {
      const selectors = rule[1].split(',').map((selector) => selector.trim());
      if (!selectors.some(selectorTargetsNavbarShell)) {
        continue;
      }

      for (const declaration of rule[2].matchAll(cssDeclarationPattern)) {
        const property = declaration[1].toLowerCase();
        if (propertyCreatesFixedContainingBlock(property, declaration[2])) {
          violations.push(
            `${path}: .navbar and its pseudo-elements must not set ${property}; ` +
              'they can confine fixed layers or cover in-flow mobile controls',
          );
        }
      }
    }
  }

  return violations;
}

export function analyzeSiteShellPages(pages, stylesheets = {}) {
  const violations = analyzeSiteShellStyles(stylesheets);
  const themeStorageKeys = new Map();

  for (const [path, html] of Object.entries(pages)) {
    if (html.includes(removedHomepageCoordinate)) {
      violations.push(`${path}: removed standalone Maven coordinate is still rendered`);
    }
    if (!html.includes(canonicalSocialCard)) {
      violations.push(`${path}: social card must use ${canonicalSocialCard}`);
    }

    const keys = new Set(
      [...html.matchAll(themeStoragePattern)].map((match) => match[1]),
    );
    if (keys.size !== 1) {
      violations.push(
        `${path}: expected one inline color-mode storage key, found ${keys.size}`,
      );
      continue;
    }
    themeStorageKeys.set(path, [...keys][0]);
  }

  const distinctThemeStorageKeys = new Set(themeStorageKeys.values());
  if (
    themeStorageKeys.size === homepagePaths.length &&
    distinctThemeStorageKeys.size !== 1
  ) {
    violations.push(
      'localized homepages use different color-mode storage keys: ' +
        [...themeStorageKeys.entries()]
          .map(([path, key]) => `${path}=${key}`)
          .join(', '),
    );
  }

  return {
    violations,
    themeStorageKey:
      distinctThemeStorageKeys.size === 1 ? [...distinctThemeStorageKeys][0] : undefined,
    socialCard: violations.some((violation) => violation.includes('social card'))
      ? undefined
      : canonicalSocialCard,
  };
}

export async function verifySiteShell({buildDirectory = buildDir} = {}) {
  const [pages, stylesheetPaths] = await Promise.all([
    Promise.all(
      homepagePaths.map(async (path) => [
        path,
        await readFile(resolve(buildDirectory, path), 'utf8'),
      ]),
    ),
    Promise.all(
      siteAssetDirectories.map((directory) =>
        collectFiles(resolve(buildDirectory, directory), (path) => path.endsWith('.css')),
      ),
    ).then((paths) => paths.flat()),
  ]);
  const stylesheets = Object.fromEntries(
    await Promise.all(
      stylesheetPaths.map(async (path) => [
        normalizedBuildPath(buildDirectory, path),
        await readFile(path, 'utf8'),
      ]),
    ),
  );
  const result = analyzeSiteShellPages(Object.fromEntries(pages), stylesheets);

  if (result.violations.length > 0) {
    throw new Error(`Site shell verification failed:\n${result.violations.join('\n')}`);
  }

  console.log(
    `Site shell verification passed: ${homepagePaths.length} localized homepages share ` +
      `${result.themeStorageKey}, use one canonical social card, omit the standalone Maven coordinate, ` +
      `and ${stylesheetPaths.length} stylesheets keep the mobile navbar viewport-safe.`,
  );
  return {
    homepageCount: homepagePaths.length,
    stylesheetCount: stylesheetPaths.length,
    socialCard: result.socialCard,
    themeStorageKey: result.themeStorageKey,
  };
}

if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  verifySiteShell().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
