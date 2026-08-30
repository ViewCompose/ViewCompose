import {readFile} from 'node:fs/promises';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {compileKotlin} from './compiler-adapter.mjs';
import {resolveXmlLayoutDependencies} from './xml-layout-dependencies.mjs';
import {convertXmlToViewCompose} from './xml-migration.mjs';

const aiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const fixtureRoot = resolve(aiRoot, 'evaluation/fixtures/xml');

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function flatten(node) {
  return [node, ...node.children.flatMap(flatten)];
}

export async function verifyPhase4XmlLayoutDependencies({
  resolveDependencies = resolveXmlLayoutDependencies,
  compile = compileKotlin,
} = {}) {
  const [contract, metrics] = await Promise.all([
    readJson(resolve(fixtureRoot, 'layout-dependency-contract.json')),
    readJson(resolve(aiRoot, 'evaluation/metrics.json')),
  ]);
  for (const metricId of ['xml.layout-dependency-exactness', 'xml.include-merge-expansion']) {
    const metric = metrics.metrics.find((entry) => entry.id === metricId);
    if (!metric || metric.direction !== 'at_least' || metric.threshold !== 1) {
      throw new Error(`${metricId}: layout dependency acceptance must remain an exact 1.00 threshold`);
    }
  }

  let graphs = 0;
  let expansions = 0;
  let compiled = 0;
  let unsupported = 0;
  const fingerprints = [];
  for (const fixture of contract.supportedFixtures) {
    const request = {
      projectRoot: resolve(fixtureRoot, fixture.projectRoot),
      layoutPath: fixture.layoutPath,
      resourceRoots: fixture.resourceRoots,
      sourceRoots: fixture.sourceRoots,
    };
    const [first, second, goldenGraph, goldenKotlin] = await Promise.all([
      resolveDependencies(request),
      resolveDependencies(request),
      readJson(resolve(fixtureRoot, fixture.goldenGraph)),
      readFile(resolve(fixtureRoot, 'layout-dependencies/screen.kt'), 'utf8'),
    ]);
    if (
      first.status !== 'success' ||
      second.status !== 'success' ||
      JSON.stringify(first.graph) !== JSON.stringify(second.graph) ||
      JSON.stringify(first.graph) !== JSON.stringify(goldenGraph)
    ) {
      throw new Error(`${fixture.projectRoot}: layout dependency graph differs from its golden`);
    }
    graphs += 1;

    const converted = await convertXmlToViewCompose({
      ...request,
      mode: 'compile',
      requestId: `xml-layout-${fixture.expectedFunction}-compile`,
      compile,
      resolveLayoutDependencies: resolveDependencies,
    });
    const nodes = converted.data?.designIr?.roots?.[0]
      ? flatten(converted.data.designIr.roots[0])
      : [];
    const resources = converted.data?.migrationReport?.bindings?.resources
      ?.map((binding) => binding.source.slice(1)).sort();
    if (
      converted.status !== 'success' ||
      converted.evidence?.level !== 'compiled' ||
      JSON.stringify(converted.data?.layoutDependencies) !== JSON.stringify(goldenGraph) ||
      converted.data?.kotlin !== goldenKotlin ||
      converted.data?.migrationReport?.target?.functionName !== fixture.expectedFunction ||
      nodes.length !== fixture.expectedOutputNodes ||
      nodes.some((node) => !/^.+\.xml:[1-9][0-9]*$/u.test(node.provenance.sourceSpan)) ||
      JSON.stringify(resources) !== JSON.stringify([...fixture.expectedResources].sort())
    ) {
      const codes = converted.diagnostics?.map((diagnostic) => diagnostic.code).join(', ') ?? 'none';
      throw new Error(`${fixture.projectRoot}: include/merge expansion did not match and compile (${codes})`);
    }
    expansions += 1;
    compiled += 1;
    fingerprints.push({
      projectRoot: fixture.projectRoot,
      graph: first.graph.fingerprint,
      kotlin: converted.data.kotlinFingerprint,
      classes: converted.evidence.outputFingerprint,
    });
  }

  for (const fixture of contract.unsupportedFixtures) {
    const result = fixture.kind === 'project'
      ? await convertXmlToViewCompose({
          projectRoot: resolve(fixtureRoot, fixture.projectRoot),
          layoutPath: fixture.layoutPath,
          resourceRoots: fixture.resourceRoots,
          sourceRoots: [],
          mode: 'generate',
          requestId: 'xml-layout-unsupported-project',
          resolveLayoutDependencies: resolveDependencies,
        })
      : await convertXmlToViewCompose({
          source: await readFile(resolve(fixtureRoot, fixture.source), 'utf8'),
          path: fixture.source,
          mode: 'generate',
          requestId: 'xml-layout-unsupported-source',
        });
    const codes = result.diagnostics?.map((diagnostic) => diagnostic.code) ?? [];
    if (
      result.status !== 'unsupported' ||
      fixture.diagnosticCodes.some((code) => !codes.includes(code)) ||
      result.data?.kotlin !== undefined
    ) {
      throw new Error(`${fixture.projectRoot ?? fixture.source}: unsupported diagnostics changed`);
    }
    unsupported += 1;
  }

  if (
    graphs !== contract.supportedFixtures.length ||
    expansions !== contract.supportedFixtures.length ||
    compiled !== contract.supportedFixtures.length ||
    unsupported !== contract.unsupportedFixtures.length
  ) {
    throw new Error('Phase 4 layout dependency metrics did not reach their frozen thresholds');
  }
  return {
    graphs,
    expansions,
    compiled,
    supported: contract.supportedFixtures.length,
    unsupported,
    unsupportedFixtures: contract.unsupportedFixtures.length,
    fingerprints,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase4XmlLayoutDependencies()
    .then((summary) => {
      console.log(
        `Verified Phase 4 XML layout dependencies: ${summary.graphs}/${summary.supported} exact ` +
          `graphs, ${summary.expansions}/${summary.supported} include/merge expansions, ` +
          `${summary.compiled}/${summary.supported} hermetic compiles, and ` +
          `${summary.unsupported}/${summary.unsupportedFixtures} fail-closed unsupported inputs. ` +
          `Fingerprints: ${summary.fingerprints.map((item) =>
            `${item.projectRoot}=${item.graph}/${item.kotlin}/${item.classes}`).join(', ')}.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
