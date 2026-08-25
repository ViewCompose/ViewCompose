import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {buildDir} from './site-quality-lib.mjs';

const homepagePaths = ['index.html', 'zh-CN/index.html'];
const removedHomepageCoordinate = 'com.viewcompose:viewcompose-ui-foundation';
const canonicalSocialCard = 'https://docs.viewcompose.com/img/social-card.png';
const themeStoragePattern = /localStorage\.getItem\(["'](theme(?:-[A-Za-z0-9_-]+)?)["']\)/gu;

export function analyzeSiteShellPages(pages) {
  const violations = [];
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
  const pages = Object.fromEntries(
    await Promise.all(
      homepagePaths.map(async (path) => [
        path,
        await readFile(resolve(buildDirectory, path), 'utf8'),
      ]),
    ),
  );
  const result = analyzeSiteShellPages(pages);

  if (result.violations.length > 0) {
    throw new Error(`Site shell verification failed:\n${result.violations.join('\n')}`);
  }

  console.log(
    `Site shell verification passed: ${homepagePaths.length} localized homepages share ` +
      `${result.themeStorageKey}, use one canonical social card, and omit the standalone Maven coordinate.`,
  );
  return {
    homepageCount: homepagePaths.length,
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
