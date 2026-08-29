#!/usr/bin/env node
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {validateSchemaValue} from './schema-validator.mjs';

const fixtureRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));
const contractPath = `${fixtureRoot}screenshot-layout-comparison-contract.json`;
const designSchemaPath = fileURLToPath(new URL('../contracts/design-ir.schema.json', import.meta.url));
const previewSchemaPath = fileURLToPath(
  new URL('../contracts/generated-preview-request.schema.json', import.meta.url),
);

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function flatten(nodes, target = []) {
  for (const node of nodes) {
    target.push(node);
    flatten(node.children, target);
  }
  return target;
}

function assertUnique(values, label) {
  if (new Set(values).size !== values.length) throw new Error(`${label} are not unique`);
}

function assertContract(contract) {
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-screenshot-layout-comparison-v1' ||
    JSON.stringify(contract.requiresContracts) !== JSON.stringify([
      'design-ir-v1',
      'viewcompose-screenshot-generated-preview-v1',
      'layout-comparison-v1',
    ]) ||
    contract.activation?.tool !== 'generate_screenshot_viewcompose' ||
    contract.activation?.status !== 'contract-frozen' ||
    contract.activation?.publicCompareMode !== false ||
    contract.activation?.implementation !== false ||
    contract.activation?.successEvidence !== 'compared' ||
    contract.activation?.failureEvidence !== 'rendered'
  ) {
    throw new Error('Screenshot layout comparison activation boundary changed');
  }
  if (
    contract.input?.callerSuppliedDesignIr !== false ||
    contract.input?.callerSuppliedRenderTree !== false ||
    contract.input?.callerSuppliedPolicy !== false ||
    contract.integrity?.followSymbolicLinks !== false ||
    contract.integrity?.absolutePathsInPublicResult !== false ||
    contract.integrity?.networkAccess !== false ||
    contract.integrity?.executeInspectedProjectBuildLogic !== false ||
    contract.identity?.keyMultiplicity !== 'exactly one virtual node' ||
    contract.policy?.geometryTolerancePx !== 0 ||
    contract.policy?.allRequiredChecksMustPass !== true ||
    contract.policy?.oneAggregateScore !== false
  ) {
    throw new Error('Screenshot layout comparison integrity or exact-pass boundary changed');
  }
  if (
    JSON.stringify(contract.semanticHostWrappers) !== JSON.stringify([{
      designKind: 'text-field',
      identityRenderKind: 'column',
      semanticRenderKind: 'text-field',
      maxDepth: 1,
      requirements: [
        'exactly one child',
        'child key is absent',
        'identity and semantic host bounds are equal',
      ],
    }]) ||
    !contract.claims?.checked?.includes('declared title and button visible text') ||
    !contract.claims?.checked?.includes('parent containment') ||
    !contract.claims?.notClaimed?.includes(
      'text-field placeholder because render-tree properties omit it',
    ) ||
    !contract.claims?.notClaimed?.includes('pixel or perceptual similarity') ||
    !contract.claims?.notClaimed?.includes(
      'state mutation, event execution, focus, or interaction behavior',
    )
  ) {
    throw new Error('Screenshot layout comparison claim boundary changed');
  }
  for (const [name, ceiling] of Object.entries({
    maxDesignNodes: 1000,
    maxVirtualNodes: 2000,
    maxNativeNodes: 4000,
    maxDepth: 64,
    maxRenderTreeBytes: 8 * 1024 * 1024,
    maxChecksPerNode: 128,
    maxFindings: 1000,
    maxWrapperDepth: 1,
  })) {
    const value = contract.limits?.[name];
    if (!Number.isInteger(value) || value <= 0 || value > ceiling) {
      throw new Error(`Screenshot layout comparison limit ${name} exceeds its ceiling`);
    }
  }
  assertUnique(contract.diagnosticCodes, 'Screenshot layout comparison diagnostic codes');
  if (contract.diagnosticCodes.some((code) => !/^VC-AI-COMPARE-[A-Z0-9-]+$/u.test(code))) {
    throw new Error('Screenshot layout comparison diagnostic namespace changed');
  }
}

export async function verifyPhase5ScreenshotComparison() {
  const [contract, designSchema, previewSchema] = await Promise.all([
    readJson(contractPath),
    readJson(designSchemaPath),
    readJson(previewSchemaPath),
  ]);
  assertContract(contract);
  const [resolution, previewRequest] = await Promise.all([
    readJson(`${fixtureRoot}${contract.lineage.resolutionResult}`),
    readJson(`${fixtureRoot}${contract.lineage.previewRequest}`),
  ]);
  const designViolations = validateSchemaValue(resolution.designIr, designSchema);
  const previewViolations = validateSchemaValue(previewRequest, previewSchema);
  if (designViolations.length > 0 || previewViolations.length > 0) {
    throw new Error(
      `Screenshot comparison lineage violates schema: ${
        [...designViolations, ...previewViolations][0]
      }`,
    );
  }
  if (
    resolution.resultFingerprint !== contract.lineage.resolutionResultFingerprint ||
    resolution.designIrFingerprint !== contract.lineage.resolvedDesignIrFingerprint ||
    sha256(JSON.stringify(resolution.designIr)) !== contract.lineage.comparedDesignIrFingerprint ||
    sha256(JSON.stringify(previewRequest)) !== contract.lineage.previewRequestFingerprint ||
    previewRequest.generatedSource?.sourceKind !== 'screenshot'
  ) {
    throw new Error('Screenshot comparison source, Design IR, or Preview lineage changed');
  }
  const fixture = contract.supportedFixtures[0];
  const designNodes = flatten(resolution.designIr.roots);
  const expectedNodes = fixture.expectedNodes;
  const checkCount = expectedNodes.reduce((total, node) => total + node.checkIds.length, 0);
  assertUnique(expectedNodes.map((node) => node.designNodeId), 'Compared screenshot node IDs');
  assertUnique(expectedNodes.map((node) => node.identityKey), 'Compared screenshot node keys');
  expectedNodes.forEach((node) => assertUnique(node.checkIds, `${node.designNodeId} check IDs`));
  if (
    fixture.status !== 'contract-frozen' ||
    JSON.stringify(designNodes.map((node) => node.id)) !==
      JSON.stringify(expectedNodes.map((node) => node.designNodeId)) ||
    fixture.expectedSummary.designNodes !== designNodes.length ||
    fixture.expectedSummary.mappedNodes !== expectedNodes.length ||
    fixture.expectedSummary.requiredChecks !== checkCount ||
    fixture.expectedSummary.passedChecks !== checkCount ||
    fixture.expectedSummary.failedChecks !== 0 ||
    fixture.expectedSummary.notApplicableChecks !== 0 ||
    fixture.viewport.widthPx !== 1079 ||
    fixture.viewport.heightPx !== 2339 ||
    !/^[a-f0-9]{64}$/u.test(fixture.expectedComparisonFingerprint)
  ) {
    throw new Error('Screenshot comparison positive denominator changed');
  }

  let blocked = 0;
  for (const unsupported of contract.unsupportedFixtures) {
    const mutation = await readJson(`${fixtureRoot}${unsupported.mutation}`);
    const expected = unsupported.diagnosticCodes[0];
    if (
      mutation.schemaVersion !== 1 ||
      mutation.expectedDiagnostic !== expected ||
      !contract.diagnosticCodes.includes(expected)
    ) {
      throw new Error(`${unsupported.mutation}: comparison fail-closed reason changed`);
    }
    blocked += 1;
  }
  return {
    supportedGoldens: 1,
    failClosedDenominators: blocked,
    compared: 0,
    comparisonFingerprint: fixture.expectedComparisonFingerprint,
  };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  verifyPhase5ScreenshotComparison()
    .then((result) => {
      process.stdout.write(
        `Verified Phase 5 screenshot comparison contract: ${result.supportedGoldens}/1 exact ` +
          `denominator and ${result.failClosedDenominators}/2 fail-closed mutations, expected ` +
          `${result.comparisonFingerprint}.\n`,
      );
    })
    .catch((error) => {
      process.stderr.write(`Phase 5 screenshot comparison verification failed: ${error.message}\n`);
      process.exitCode = 1;
    });
}
