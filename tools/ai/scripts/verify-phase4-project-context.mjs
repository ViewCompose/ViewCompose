import {readFile} from 'node:fs/promises';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {resolveXmlProjectContext} from './xml-project-context.mjs';

const aiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const fixtureRoot = resolve(aiRoot, 'evaluation/fixtures/xml');

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

export async function verifyPhase4XmlProjectContext({resolveContext = resolveXmlProjectContext} = {}) {
  const [contract, metrics] = await Promise.all([
    readJson(resolve(fixtureRoot, 'project-context-contract.json')),
    readJson(resolve(aiRoot, 'evaluation/metrics.json')),
  ]);
  const requiredMetrics = [
    'xml.project-resource-resolution',
    'xml.project-style-resolution',
    'xml.callsite-inventory-exactness',
  ];
  for (const metricId of requiredMetrics) {
    const metric = metrics.metrics.find((entry) => entry.id === metricId);
    if (!metric || metric.direction !== 'at_least' || metric.threshold !== 1) {
      throw new Error(`${metricId}: project-context acceptance must remain an exact 1.00 threshold`);
    }
  }

  let deterministic = 0;
  let resources = 0;
  let styles = 0;
  let callSites = 0;
  for (const fixture of contract.supportedFixtures) {
    const request = {
      projectRoot: resolve(fixtureRoot, fixture.projectRoot),
      layoutPath: fixture.layoutPath,
      resourceRoots: fixture.resourceRoots,
      sourceRoots: fixture.sourceRoots,
    };
    const [first, second, golden] = await Promise.all([
      resolveContext(request),
      resolveContext(request),
      readJson(resolve(fixtureRoot, fixture.goldenContext)),
    ]);
    if (
      first.status !== 'success' ||
      second.status !== 'success' ||
      JSON.stringify(first.context) !== JSON.stringify(second.context) ||
      first.resolvedSource !== second.resolvedSource ||
      JSON.stringify(first.context) !== JSON.stringify(golden)
    ) {
      throw new Error(`${fixture.projectRoot}: project context differs from its deterministic golden`);
    }
    deterministic += 1;
    resources += first.context.resources.length;
    styles += first.context.styles.length;
    callSites += first.context.callSites.length;
  }

  let unsupported = 0;
  for (const fixture of contract.unsupportedFixtures) {
    const result = await resolveContext({
      projectRoot: resolve(fixtureRoot, fixture.projectRoot),
      layoutPath: fixture.layoutPath,
      resourceRoots: ['app/src/main/res'],
      sourceRoots: [],
    });
    const codes = result.diagnostics?.map((diagnostic) => diagnostic.code) ?? [];
    if (result.status !== 'unsupported' || fixture.diagnosticCodes.some((code) => !codes.includes(code))) {
      throw new Error(`${fixture.projectRoot}: unsupported project-context diagnostics changed`);
    }
    unsupported += 1;
  }

  if (
    deterministic !== contract.supportedFixtures.length ||
    unsupported !== contract.unsupportedFixtures.length
  ) {
    throw new Error('Phase 4 XML project-context metrics did not reach their frozen thresholds');
  }
  return {
    deterministic,
    supported: contract.supportedFixtures.length,
    unsupported,
    unsupportedFixtures: contract.unsupportedFixtures.length,
    resources,
    styles,
    callSites,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase4XmlProjectContext()
    .then((summary) => {
      console.log(
        `Verified Phase 4 XML project context: ${summary.deterministic}/${summary.supported} ` +
          `deterministic goldens with ${summary.resources} resources, ${summary.styles} styles, ` +
          `${summary.callSites} call sites, and ${summary.unsupported}/${summary.unsupportedFixtures} ` +
          `fail-closed unsupported projects.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
