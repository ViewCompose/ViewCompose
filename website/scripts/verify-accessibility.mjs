import {readFile} from 'node:fs/promises';
import {load} from 'cheerio';
import {buildDir, collectFiles, relativeBuildPath} from './site-quality-lib.mjs';

function explicitAccessibleName($, element) {
  const node = $(element);
  const labelledBy = node.attr('aria-labelledby');
  const referencedText = labelledBy
    ?.split(/\s+/)
    .map((id) =>
      $('[id]')
        .filter((_, candidate) => $(candidate).attr('id') === id)
        .text()
        .trim(),
    )
    .filter(Boolean)
    .join(' ');

  return (
    node.attr('aria-label')?.trim() ||
    referencedText ||
    node.attr('title')?.trim() ||
    ''
  );
}

function accessibleName($, element) {
  const node = $(element);

  return (
    explicitAccessibleName($, element) ||
    node.text().trim() ||
    node.find('img[alt]').attr('alt')?.trim() ||
    ''
  );
}

function isRedirect($) {
  return $('meta[http-equiv="refresh" i]').length > 0;
}

function isClientRenderedSearchPage(path) {
  return /^(?:zh-CN\/)?search(?:\/index)?\.html$/u.test(path);
}

export async function verifyAccessibility() {
  const htmlFiles = await collectFiles(buildDir, (path) => path.endsWith('.html'));
  const violations = [];
  let auditedPages = 0;
  let redirectPages = 0;
  let generatedApiPages = 0;
  let clientRenderedSearchPages = 0;

  for (const file of htmlFiles) {
    const path = relativeBuildPath(file);
    // Dokka owns its generated markup and currently has a separate integrity gate. Auditing those
    // pages here would make third-party template changes indistinguishable from site regressions.
    if (path.startsWith('api/') || path.includes('/api/')) {
      generatedApiPages += 1;
      continue;
    }
    // Local search renders its results after hydration and intentionally has no server-rendered
    // document landmark. Audit every authored target page instead of treating this shell as prose.
    if (isClientRenderedSearchPage(path)) {
      clientRenderedSearchPages += 1;
      continue;
    }

    const $ = load(await readFile(file, 'utf8'));
    if (isRedirect($)) {
      redirectPages += 1;
      continue;
    }
    auditedPages += 1;

    const report = (message) => violations.push(`${path}: ${message}`);
    const expectedLanguage = path.startsWith('zh-CN/') ? 'zh-CN' : 'en';
    const actualLanguage = $('html').attr('lang');
    if (actualLanguage !== expectedLanguage) {
      report(`expected html lang="${expectedLanguage}", found ${JSON.stringify(actualLanguage)}`);
    }
    if ($('title').text().trim().length === 0) {
      report('missing a non-empty document title');
    }
    if ($('main').length !== 1) {
      report(`expected exactly one main landmark, found ${$('main').length}`);
    }

    const mainHeadings = $('main').find('h1, h2, h3, h4, h5, h6').toArray();
    const h1Count = mainHeadings.filter((heading) => heading.tagName === 'h1').length;
    if (h1Count !== 1) {
      report(`expected exactly one h1 in main, found ${h1Count}`);
    }
    let previousLevel = 0;
    for (const heading of mainHeadings) {
      const level = Number(heading.tagName.slice(1));
      if (previousLevel > 0 && level > previousLevel + 1) {
        report(`heading level skips from h${previousLevel} to h${level}: ${$(heading).text().trim()}`);
      }
      previousLevel = level;
    }

    $('img').each((_, image) => {
      if ($(image).attr('alt') === undefined) {
        report(`image is missing alt text: ${$(image).attr('src') ?? '<inline>'}`);
      }
    });
    $('iframe').each((_, iframe) => {
      if (!$(iframe).attr('title')?.trim()) {
        report(`iframe is missing a title: ${$(iframe).attr('src') ?? '<inline>'}`);
      }
    });
    $('button, [role="button"], a[href]').each((_, control) => {
      if (!accessibleName($, control)) {
        report(`${control.tagName} has no accessible name`);
      }
    });
    $('input:not([type="hidden"]), select, textarea').each((_, control) => {
      const node = $(control);
      const id = node.attr('id');
      const hasLabel = id
        ? $('label[for]').filter((_, label) => $(label).attr('for') === id).length > 0
        : false;
      if (!hasLabel && !explicitAccessibleName($, control)) {
        report(`${control.tagName} has no accessible label`);
      }
    });
    $('table').each((_, table) => {
      if ($(table).find('th').length === 0) {
        report('table has no header cells');
      }
    });

    const seenIds = new Set();
    $('[id]').each((_, element) => {
      const id = $(element).attr('id');
      if (seenIds.has(id)) {
        report(`duplicate id ${JSON.stringify(id)}`);
      }
      seenIds.add(id);
    });
  }

  if (violations.length > 0) {
    throw new Error(`Accessibility verification failed:\n${violations.join('\n')}`);
  }

  console.log(
    `Accessibility verification passed: ${auditedPages} site pages audited, ` +
      `${redirectPages} redirects, ${clientRenderedSearchPages} client-rendered search pages, and ` +
      `${generatedApiPages} generated API pages excluded.`,
  );
  return {auditedPages, redirectPages, clientRenderedSearchPages, generatedApiPages};
}

if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  verifyAccessibility().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
