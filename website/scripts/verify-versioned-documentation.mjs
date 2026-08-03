import {access, readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildDir, websiteRoot} from './site-quality-lib.mjs';
import {isStableRelease, loadDocumentationReleases} from './documentation-releases.mjs';

const repositoryRoot = resolve(websiteRoot, '..');

async function requireFile(path, failures) {
  try {
    await access(path);
  } catch {
    failures.push(path.replace(`${buildDir}/`, ''));
  }
}

export async function verifyVersionedDocumentation() {
  const releases = await loadDocumentationReleases(repositoryRoot);
  const failures = [];
  for (const entry of releases.entries) {
    await requireFile(
      resolve(buildDir, 'api', entry.artifact, entry.version, 'index.html'),
      failures,
    );
    await requireFile(
      resolve(buildDir, 'modules', entry.artifact, `${entry.version}.html`),
      failures,
    );
    await requireFile(
      resolve(buildDir, 'zh-CN', 'modules', entry.artifact, `${entry.version}.html`),
      failures,
    );
  }
  for (const [artifact, current] of releases.current) {
    const currentRedirect = resolve(buildDir, 'api', artifact, 'current', 'index.html');
    await requireFile(currentRedirect, failures);
    try {
      const redirect = await readFile(currentRedirect, 'utf8');
      if (!redirect.includes(`../${current.version}/`)) {
        failures.push(`api/${artifact}/current -> does not target ${current.version}`);
      }
    } catch {
      // Missing files are already reported above.
    }
    const latestStable = releases.entries
      .filter((entry) => entry.artifact === artifact && isStableRelease(entry.version))
      .at(-1);
    const latestRedirect = resolve(buildDir, 'api', artifact, 'latest', 'index.html');
    if (latestStable) {
      await requireFile(latestRedirect, failures);
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
