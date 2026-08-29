import {readFile} from 'node:fs/promises';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {compileKotlin} from './compiler-adapter.mjs';
import {convertXmlToViewCompose} from './xml-migration.mjs';
import {resolveXmlProjectContext} from './xml-project-context.mjs';

const aiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const fixtureRoot = resolve(aiRoot, 'evaluation/fixtures/xml');

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

export async function verifyPhase4XmlProjectContext({
  resolveContext = resolveXmlProjectContext,
  compile = compileKotlin,
} = {}) {
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
  let compiled = 0;
  const fingerprints = [];
  for (const fixture of contract.supportedFixtures) {
    const request = {
      projectRoot: resolve(fixtureRoot, fixture.projectRoot),
      layoutPath: fixture.layoutPath,
      resourceRoots: fixture.resourceRoots,
      sourceRoots: fixture.sourceRoots,
    };
    const [first, second, golden, goldenKotlin] = await Promise.all([
      resolveContext(request),
      resolveContext(request),
      readJson(resolve(fixtureRoot, fixture.goldenContext)),
      readFile(resolve(fixtureRoot, fixture.goldenKotlin), 'utf8'),
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
    const converted = await convertXmlToViewCompose({
      ...request,
      mode: 'compile',
      requestId: `xml-project-${fixture.expectedFunction}-compile`,
      compile,
      resolveProjectContext: resolveContext,
    });
    const parameters = converted.data?.migrationReport?.bindings?.resources
      ?.map((binding) => binding.parameter);
    const states = converted.data?.migrationReport?.bindings?.states
      ?.map((binding) => binding.parameter);
    if (
      converted.status !== 'success' ||
      converted.evidence?.level !== 'compiled' ||
      converted.data?.kotlin !== goldenKotlin ||
      converted.data?.migrationReport?.target?.functionName !== fixture.expectedFunction ||
      JSON.stringify(parameters) !== JSON.stringify(fixture.expectedResourceParameters) ||
      JSON.stringify(states) !== JSON.stringify(fixture.expectedStateBindings) ||
      converted.data?.migrationReport?.callSiteReview?.inventory?.length !==
        first.context.callSites.length ||
      JSON.stringify(converted.data?.projectContext) !== JSON.stringify(golden)
    ) {
      const codes = converted.diagnostics?.map((diagnostic) => diagnostic.code).join(', ') ?? 'none';
      throw new Error(
        `${fixture.projectRoot}: project-aware generated Kotlin did not match and compile (${codes})`,
      );
    }
    compiled += 1;
    fingerprints.push({
      projectRoot: fixture.projectRoot,
      context: first.context.fingerprint,
      kotlin: converted.data.kotlinFingerprint,
      classes: converted.evidence.outputFingerprint,
    });
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
    compiled !== contract.supportedFixtures.length ||
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
    compiled,
    fingerprints,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase4XmlProjectContext()
    .then((summary) => {
      console.log(
        `Verified Phase 4 XML project context: ${summary.deterministic}/${summary.supported} ` +
          `deterministic goldens with ${summary.resources} resources, ${summary.styles} styles, ` +
          `${summary.callSites} call sites, ${summary.compiled}/${summary.supported} hermetic compiles, ` +
          `and ${summary.unsupported}/${summary.unsupportedFixtures} fail-closed unsupported projects. ` +
          `Fingerprints: ${summary.fingerprints.map((item) =>
            `${item.projectRoot}=${item.context}/${item.kotlin}/${item.classes}`).join(', ')}.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
