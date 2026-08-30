import {readFile} from 'node:fs/promises';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {compileKotlin} from './compiler-adapter.mjs';
import {generateViewComposeKotlin} from './design-ir-to-kotlin.mjs';
import {convertXmlToDesignIr} from './xml-to-design-ir.mjs';

const aiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const fixtureRoot = resolve(aiRoot, 'evaluation/fixtures/xml');

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

export async function verifyPhase4XmlMigration({compile = compileKotlin} = {}) {
  const contracts = await Promise.all([
    readJson(resolve(fixtureRoot, 'subset-contract.json')),
    readJson(resolve(fixtureRoot, 'subset-v2-contract.json')),
  ]);
  const supportedFixtures = contracts.flatMap((contract) =>
    contract.supportedFixtures.map((fixture) => ({
      ...fixture,
      compileArtifacts: contract.codeGeneration.compileArtifacts,
    })));
  let generated = 0;
  let compiled = 0;
  let resourcesPreserved = 0;
  const fingerprints = [];
  for (const fixture of supportedFixtures) {
    const source = await readFile(resolve(fixtureRoot, fixture.source), 'utf8');
    const converted = await convertXmlToDesignIr({
      source,
      path: `tools/ai/evaluation/fixtures/xml/${fixture.source}`,
    });
    if (converted.status !== 'success') {
      throw new Error(`${fixture.source}: XML to Design IR conversion failed`);
    }
    const [first, second, golden] = await Promise.all([
      generateViewComposeKotlin(converted.ir),
      generateViewComposeKotlin(converted.ir),
      readFile(resolve(fixtureRoot, fixture.goldenKotlin), 'utf8'),
    ]);
    if (
      first.status !== 'success' ||
      second.status !== 'success' ||
      first.kotlin !== second.kotlin ||
      first.outputFingerprint !== second.outputFingerprint ||
      first.kotlin !== golden ||
      first.report.target.functionName !== fixture.expectedFunction
    ) {
      throw new Error(`${fixture.source}: deterministic Kotlin differs from its frozen golden`);
    }
    generated += 1;
    const resourceNames = first.report.bindings.resources
      .map((binding) => binding.source.slice(1))
      .sort();
    const parameters = first.report.bindings.resources.map((binding) => binding.parameter);
    const bindings = first.report.bindings.resources.map(({parameter, type}) => ({parameter, type}));
    const expectedBindings = fixture.expectedBindings ??
      fixture.expectedResourceParameters.map((parameter) => ({parameter, type: 'String'}));
    const expectedParameters = expectedBindings.map((binding) => binding.parameter);
    if (
      JSON.stringify(resourceNames) !== JSON.stringify(fixture.expectedResources) ||
      JSON.stringify(parameters) !== JSON.stringify(expectedParameters) ||
      JSON.stringify(bindings) !== JSON.stringify(expectedBindings) ||
      first.report.callSiteReview.required !== true
    ) {
      throw new Error(`${fixture.source}: generated migration report lost a resource or review item`);
    }
    resourcesPreserved += 1;
    const compileResult = await compile({
      source: first.kotlin,
      path: `generated/viewcompose/${fixture.expectedFunction}.kt`,
      artifactIds: fixture.compileArtifacts,
      capabilityIds: [
        'foundation.components',
        'image.foundation',
        'modifier.drawing',
        'modifier.layout',
      ],
      requestId: `xml-${fixture.expectedFunction}-compile`,
    });
    if (compileResult.status !== 'success' || compileResult.evidence?.level !== 'compiled') {
      const codes = compileResult.diagnostics?.map((diagnostic) => diagnostic.code).join(', ') ?? 'none';
      throw new Error(`${fixture.source}: generated Kotlin did not compile (${codes})`);
    }
    compiled += 1;
    fingerprints.push({
      source: fixture.source,
      kotlin: first.outputFingerprint,
      classes: compileResult.evidence.outputFingerprint,
    });
  }
  if (
    generated !== supportedFixtures.length ||
    compiled !== supportedFixtures.length ||
    resourcesPreserved !== supportedFixtures.length
  ) {
    throw new Error('Phase 4 XML migration metrics did not reach their frozen 1.00 thresholds');
  }
  return {
    generated,
    compiled,
    resourcesPreserved,
    fixtures: supportedFixtures.length,
    fingerprints,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase4XmlMigration()
    .then((summary) => {
      console.log(
        `Verified Phase 4 XML migration: ${summary.generated}/${summary.fixtures} deterministic ` +
          `Kotlin goldens, ${summary.resourcesPreserved}/${summary.fixtures} resource reports, and ` +
          `${summary.compiled}/${summary.fixtures} hermetic compiles. ` +
          `Fingerprints: ${summary.fingerprints.map((item) =>
            `${item.source}=${item.kotlin}/${item.classes}`).join(', ')}.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
