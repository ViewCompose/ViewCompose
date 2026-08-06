import {access, readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildDir, websiteRoot} from './site-quality-lib.mjs';
import {isStableRelease, loadDocumentationReleases} from './documentation-releases.mjs';

const repositoryRoot = resolve(websiteRoot, '..');

function routePage(...segments) {
  return resolve(buildDir, ...segments, 'index.html');
}

async function requireFile(path, failures) {
  try {
    await access(path);
  } catch {
    failures.push(path.replace(`${buildDir}/`, ''));
  }
}

async function fileExists(path) {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

function hasHref(content, route) {
  const escaped = route.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&');
  return new RegExp(`href=(?:"${escaped}"|'${escaped}'|${escaped})(?:[\\s>])`, 'u').test(content);
}

export async function verifyVersionedDocumentation() {
  const releases = await loadDocumentationReleases(repositoryRoot);
  const failures = [];
  const apiLandingPath = routePage('api');
  const localizedApiLandingPath = routePage('zh-CN', 'api');
  const moduleCatalogPath = routePage('modules');
  const localizedModuleCatalogPath = routePage('zh-CN', 'modules');
  await requireFile(apiLandingPath, failures);
  await requireFile(localizedApiLandingPath, failures);
  await requireFile(moduleCatalogPath, failures);
  await requireFile(localizedModuleCatalogPath, failures);
  const [apiLanding, localizedApiLanding, moduleCatalog, localizedModuleCatalog] = await Promise.all([
    readFile(apiLandingPath, 'utf8').catch(() => ''),
    readFile(localizedApiLandingPath, 'utf8').catch(() => ''),
    readFile(moduleCatalogPath, 'utf8').catch(() => ''),
    readFile(localizedModuleCatalogPath, 'utf8').catch(() => ''),
  ]);
  for (const entry of releases.entries) {
    await requireFile(
      resolve(buildDir, 'api', entry.artifact, entry.version, 'index.html'),
      failures,
    );
    await requireFile(
      routePage('modules', entry.artifact, entry.version),
      failures,
    );
    await requireFile(
      routePage('zh-CN', 'modules', entry.artifact, entry.version),
      failures,
    );
    const manualRoute = `/modules/${entry.artifact}/${entry.version}/`;
    if (!hasHref(apiLanding, manualRoute)) {
      failures.push(`api.html -> missing manual link ${manualRoute}`);
    }
    const localizedManualRoute = `/zh-CN${manualRoute}`;
    if (!hasHref(localizedApiLanding, localizedManualRoute)) {
      failures.push(`zh-CN/api.html -> missing manual link ${localizedManualRoute}`);
    }
  }
  for (const [artifact, current] of releases.current) {
    const currentManual = routePage('modules', artifact);
    const localizedCurrentManual = routePage('zh-CN', 'modules', artifact);
    await requireFile(currentManual, failures);
    await requireFile(localizedCurrentManual, failures);
    const currentManualRoute = `/modules/${artifact}/`;
    if (!hasHref(moduleCatalog, currentManualRoute)) {
      failures.push(`modules/index.html -> missing current manual link ${currentManualRoute}`);
    }
    const localizedCurrentManualRoute = `/zh-CN${currentManualRoute}`;
    if (!hasHref(localizedModuleCatalog, localizedCurrentManualRoute)) {
      failures.push(
        `zh-CN/modules/index.html -> missing current manual link ${localizedCurrentManualRoute}`,
      );
    }
    const [currentManualContent, localizedCurrentManualContent] = await Promise.all([
      readFile(currentManual, 'utf8').catch(() => ''),
      readFile(localizedCurrentManual, 'utf8').catch(() => ''),
    ]);
    if (!currentManualContent.includes('plugin-id-default')) {
      failures.push(`modules/${artifact}/ -> is not rendered by the current-manual docs plugin`);
    }
    if (!localizedCurrentManualContent.includes('plugin-id-default')) {
      failures.push(
        `zh-CN/modules/${artifact}/ -> is not rendered by the current-manual docs plugin`,
      );
    }

    const currentRedirect = routePage('api', artifact, 'current');
    await requireFile(currentRedirect, failures);
    try {
      const redirect = await readFile(currentRedirect, 'utf8');
      if (releases.unpublished.has(artifact) && redirect.includes('http-equiv="refresh"')) {
        failures.push(`api/${artifact}/current -> unpublished API must be generated from the working tree`);
      } else if (!releases.unpublished.has(artifact) && !redirect.includes(`../${current.version}/`)) {
        failures.push(`api/${artifact}/current -> does not target ${current.version}`);
      }
    } catch {
      // Missing files are already reported above.
    }
    const latestStable = releases.entries
      .filter((entry) => entry.artifact === artifact && isStableRelease(entry.version))
      .at(-1);
    const latestRedirect = routePage('api', artifact, 'latest');
    if (latestStable) {
      await requireFile(latestRedirect, failures);
      try {
        const redirect = await readFile(latestRedirect, 'utf8');
        if (!redirect.includes(`../${latestStable.version}/`)) {
          failures.push(`api/${artifact}/latest -> does not target ${latestStable.version}`);
        }
      } catch {
        // Missing files are already reported above.
      }
    } else if (await fileExists(latestRedirect)) {
      failures.push(`api/${artifact}/latest -> prerelease history must not create latest`);
    }
  }
  if (failures.length > 0) {
    throw new Error(
      `Versioned documentation verification failed:\n${failures.sort().map((item) => `- ${item}`).join('\n')}`,
    );
  }
  const result = {
    apiVersions: releases.entries.length,
    moduleManuals: releases.entries.length,
    localizedFallbackManuals: releases.entries.length,
  };
  console.log(
    `Versioned documentation verification passed: ${result.apiVersions} API versions, ` +
      `${result.moduleManuals} module manuals, and ${result.localizedFallbackManuals} zh-CN fallback routes.`,
  );
  return result;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  verifyVersionedDocumentation().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
