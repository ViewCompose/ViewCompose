import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const visualRoot = fileURLToPath(new URL('../evaluation/fixtures/visual/', import.meta.url));
const contractPath = resolve(
  visualRoot,
  'screenshot-repair-applied-result-handoff-contract.json',
);
const schemaPath = fileURLToPath(new URL(
  '../contracts/screenshot-repair-applied-result-handoff.schema.json',
  import.meta.url,
));

function same(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

export async function verifyPhase5ScreenshotRepairAppliedResultHandoffContract() {
  const [contract, schema] = await Promise.all([
    readFile(contractPath, 'utf8').then(JSON.parse),
    readFile(schemaPath, 'utf8').then(JSON.parse),
  ]);
  if (
    contract.schemaVersion !== 1 ||
    contract.contractId !== 'viewcompose-screenshot-repair-applied-result-handoff-v1' ||
    !same(contract.requiresContracts, [
      'design-ir-v1',
      'screenshot-repair-execution-outcome-v1',
    ]) ||
    contract.activation?.tool !== 'generate_screenshot_viewcompose' ||
    contract.activation?.status !== 'contract-frozen' ||
    contract.activation?.implementation !== false ||
    contract.activation?.publicRepairMode !== false ||
    contract.activation?.persistentSourceWrite !== false
  ) {
    throw new Error('Screenshot repair applied-result handoff activation boundary changed');
  }
  if (
    contract.boundary?.authority !==
      'the exact process-local applied outcome object returned by attended execution' ||
    contract.boundary?.durability !==
      'the same trusted host must read and revalidate the exact persisted terminal outcome before delivery' ||
    contract.boundary?.result !==
      'the exact frozen in-memory Design IR object retained from typed patch application' ||
    contract.boundary?.singleUse !== true ||
    contract.boundary?.persistentResultStorage !== false ||
    contract.boundary?.persistentSourceWrite !== false ||
    contract.boundary?.providerCalls !== false ||
    contract.boundary?.toolNetworkAccess !== false ||
    contract.boundary?.publicActivation !== false
  ) {
    throw new Error('Screenshot repair applied-result handoff policy changed');
  }
  if (!same(contract.acceptance, {
    successfulHandoffs: 1,
    durableReceiptReads: 1,
    exactObjectDeliveries: 1,
    serializedAuthoritiesAccepted: 0,
    nonAppliedResultsDelivered: 0,
    mismatchedReceiptsAccepted: 0,
    replayedDeliveries: 0,
    concurrentDeliveries: 1,
  })) {
    throw new Error('Screenshot repair applied-result handoff denominator changed');
  }
  if (!same(contract.diagnosticCodes, [
    'VC-AI-REPAIR-HANDOFF-AUTHORITY-INVALID',
    'VC-AI-REPAIR-HANDOFF-HOST-UNTRUSTED',
    'VC-AI-REPAIR-HANDOFF-IN-PROGRESS',
    'VC-AI-REPAIR-HANDOFF-ALREADY-DELIVERED',
    'VC-AI-REPAIR-HANDOFF-TERMINAL-OUTCOME-INVALID',
    'VC-AI-REPAIR-HANDOFF-DURABLE-RECEIPT-UNAVAILABLE',
    'VC-AI-REPAIR-HANDOFF-DURABLE-RECEIPT-MISMATCH',
    'VC-AI-REPAIR-HANDOFF-RESULT-INTEGRITY-MISMATCH',
  ])) {
    throw new Error('Screenshot repair applied-result handoff diagnostics changed');
  }
  if (
    schema.$id !==
      'https://schemas.viewcompose.com/ai/screenshot-repair-applied-result-handoff-v1.schema.json' ||
    schema.properties?.status?.const !== 'delivered' ||
    schema.properties?.delivery?.properties?.terminalOutcome?.const !==
      'durable-reconciled' ||
    schema.properties?.delivery?.properties?.terminalReceiptRevalidated?.const !== true ||
    schema.properties?.delivery?.properties?.exactInMemoryObject?.const !== true ||
    schema.properties?.delivery?.properties?.singleUse?.const !== true ||
    schema.properties?.delivery?.properties?.persistentResultStorage?.const !== false ||
    schema.properties?.delivery?.properties?.persistentSourceWrite?.const !== false ||
    schema.properties?.delivery?.properties?.publicToolMode?.const !== false
  ) {
    throw new Error('Screenshot repair applied-result handoff schema boundary changed');
  }
  return {
    implementation: false,
    publicRepairMode: false,
    persistentSourceWrite: false,
    successfulHandoffsRequired: contract.acceptance.successfulHandoffs,
    invalidAuthoritiesAccepted: contract.acceptance.serializedAuthoritiesAccepted,
    replayedDeliveries: contract.acceptance.replayedDeliveries,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase5ScreenshotRepairAppliedResultHandoffContract()
    .then((summary) => {
      console.log(
        'Verified screenshot repair applied-result handoff contract: ' +
          `${summary.successfulHandoffsRequired}/1 successful durable handoff required, ` +
          `${summary.invalidAuthoritiesAccepted}/0 serialized authorities and ` +
          `${summary.replayedDeliveries}/0 replays accepted; implementation and public ` +
          'execution remain off.',
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
