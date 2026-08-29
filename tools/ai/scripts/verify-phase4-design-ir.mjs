import {readFile} from 'node:fs/promises';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {convertXmlToDesignIr} from './xml-to-design-ir.mjs';

const aiRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const fixtureRoot = resolve(aiRoot, 'evaluation/fixtures/xml');

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function visitNodes(roots, visitor) {
  for (const node of roots) {
    visitor(node);
    visitNodes(node.children, visitor);
  }
}

function facts(ir) {
  const resources = new Set();
  const stateBindings = new Set();
  let nodes = 0;
  let completeProvenance = 0;
  visitNodes(ir.roots, (node) => {
    nodes += 1;
    if (
      node.provenance.sourceId &&
      node.provenance.sourceSpan &&
      typeof node.provenance.confidence === 'number' &&
      node.provenance.decision
    ) completeProvenance += 1;
    for (const field of [...node.properties, ...node.semantics, ...node.state]) {
      if (field.value.kind === 'resource') {
        resources.add(`${field.value.resourceType}/${field.value.name}`);
      }
      if (field.value.kind === 'binding') stateBindings.add(field.value.name);
    }
  });
  return {
    nodes,
    completeProvenance,
    resources: [...resources].sort(),
    stateBindings: [...stateBindings].sort(),
  };
}

export async function verifyPhase4DesignIr() {
  const contract = await readJson(resolve(fixtureRoot, 'subset-contract.json'));
  let deterministicMatches = 0;
  let schemaMatches = 0;
  let provenanceNodes = 0;
  let totalNodes = 0;
  let resourceMatches = 0;
  for (const fixture of contract.supportedFixtures) {
    const sourcePath = resolve(fixtureRoot, fixture.source);
    const source = await readFile(sourcePath, 'utf8');
    const path = `tools/ai/evaluation/fixtures/xml/${fixture.source}`;
    const [first, second, golden] = await Promise.all([
      convertXmlToDesignIr({source, path}),
      convertXmlToDesignIr({source, path}),
      readJson(resolve(fixtureRoot, fixture.goldenIr)),
    ]);
    if (first.status !== 'success' || second.status !== 'success') {
      throw new Error(`${fixture.source}: supported conversion did not succeed`);
    }
    if (JSON.stringify(first.ir) !== JSON.stringify(second.ir)) {
      throw new Error(`${fixture.source}: repeated Design IR bytes differ`);
    }
    deterministicMatches += 1;
    if (JSON.stringify(first.ir) !== JSON.stringify(golden)) {
      throw new Error(`${fixture.source}: converted Design IR differs from the frozen golden`);
    }
    schemaMatches += 1;
    const actual = facts(first.ir);
    totalNodes += actual.nodes;
    provenanceNodes += actual.completeProvenance;
    if (
      actual.nodes !== fixture.expectedNodes ||
      JSON.stringify(actual.resources) !== JSON.stringify(fixture.expectedResources) ||
      JSON.stringify(actual.stateBindings) !== JSON.stringify(fixture.expectedStateBindings)
    ) {
      throw new Error(`${fixture.source}: preservation denominator differs from the contract`);
    }
    resourceMatches += 1;
  }

  let unsupportedMatches = 0;
  for (const fixture of contract.unsupportedFixtures) {
    const source = await readFile(resolve(fixtureRoot, fixture.source), 'utf8');
    const result = await convertXmlToDesignIr({
      source,
      path: `tools/ai/evaluation/fixtures/xml/${fixture.source}`,
    });
    const codes = new Set(result.diagnostics.map((diagnostic) => diagnostic.code));
    if (
      result.status !== 'unsupported' ||
      fixture.diagnosticCodes.some((code) => !codes.has(code)) ||
      Object.hasOwn(result, 'kotlin')
    ) {
      throw new Error(`${fixture.source}: unsupported semantics were not reported exactly`);
    }
    unsupportedMatches += 1;
  }

  const summary = {
    supported: {
      deterministicMatches,
      schemaMatches,
      resourceMatches,
      fixtures: contract.supportedFixtures.length,
      provenanceNodes,
      totalNodes,
    },
    unsupported: {
      matches: unsupportedMatches,
      fixtures: contract.unsupportedFixtures.length,
    },
  };
  if (
    deterministicMatches !== contract.supportedFixtures.length ||
    schemaMatches !== contract.supportedFixtures.length ||
    resourceMatches !== contract.supportedFixtures.length ||
    provenanceNodes !== totalNodes ||
    unsupportedMatches !== contract.unsupportedFixtures.length
  ) {
    throw new Error('Phase 4 Design IR metrics did not reach their frozen 1.00 thresholds');
  }
  return summary;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase4DesignIr()
    .then((summary) => {
      console.log(
        `Verified Phase 4 Design IR: ${summary.supported.schemaMatches}/` +
          `${summary.supported.fixtures} schema goldens, ` +
          `${summary.supported.deterministicMatches}/${summary.supported.fixtures} deterministic, ` +
          `${summary.supported.provenanceNodes}/${summary.supported.totalNodes} provenance-complete, ` +
          `${summary.supported.resourceMatches}/${summary.supported.fixtures} resource-preserving, and ` +
          `${summary.unsupported.matches}/${summary.unsupported.fixtures} unsupported fixtures honest.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
