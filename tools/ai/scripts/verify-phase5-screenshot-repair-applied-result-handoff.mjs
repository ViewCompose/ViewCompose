import {mkdtemp, readFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {
  createTrustedScreenshotRepairOutcomeHost,
  executeTrustedScreenshotRepair,
  handoffTrustedScreenshotRepairAppliedResult,
} from './screenshot-repair-execution-adapter.mjs';
import {
  createTrustedScreenshotRepairHost,
  requestScreenshotRepairHostGrant,
} from './screenshot-repair-host-grant-adapter.mjs';
import {
  createFileBackedScreenshotRepairTerminalStore,
} from './screenshot-repair-terminal-store.mjs';

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
    contract.activation?.status !== 'implemented-internal' ||
    contract.activation?.implementation !== true ||
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
  return {contract, schema};
}

function findNode(nodes, id) {
  for (const node of nodes) {
    if (node.id === id) return node;
    const child = findNode(node.children, id);
    if (child) return child;
  }
  return undefined;
}

function executionInput(grantDecision, resolutionResult, proposerContract) {
  const designIr = structuredClone(resolutionResult.designIr);
  findNode(designIr.roots, 'wireframe-title')
    .properties.find((field) => field.name === 'text').value.value = 'Hello';
  if (fingerprintRepairValue(designIr) !== grantDecision.grant.targetDesignIrFingerprint) {
    throw new Error('Screenshot repair applied-result candidate identity changed');
  }
  return {
    grantDecision,
    designIr,
    patch: structuredClone(proposerContract.supportedFixtures[0].expectedPatch),
  };
}

function directGrant(hostGrant, authorization, validationResult) {
  const host = createTrustedScreenshotRepairHost({
    trustDomainId: hostGrant.trustDomainId,
    reserve: async () => structuredClone(hostGrant),
  });
  return requestScreenshotRepairHostGrant({authorization, validationResult}, {host});
}

async function createStore(parent, name, trustDomainId) {
  return createFileBackedScreenshotRepairTerminalStore({
    storeRoot: resolve(parent, name),
    storeId: name,
    trustDomainId,
  });
}

function rejectionCode(settled) {
  return settled.find((entry) => entry.status === 'rejected')?.reason?.code;
}

export async function verifyPhase5ScreenshotRepairAppliedResultHandoff() {
  const [{contract, schema}, hostGrant, authorization, validationResult, resolutionResult,
    proposerContract, designIrSchema] = await Promise.all([
    verifyPhase5ScreenshotRepairAppliedResultHandoffContract(),
    readFile(resolve(
      visualRoot,
      'screenshot-repair/rollback.host-grant-decision.json',
    ), 'utf8').then(JSON.parse),
    readFile(resolve(
      visualRoot,
      'screenshot-repair/rollback.authorization.json',
    ), 'utf8').then(JSON.parse),
    readFile(resolve(
      visualRoot,
      'screenshot-repair/rollback.authorization-validation.json',
    ), 'utf8').then(JSON.parse),
    readFile(resolve(
      visualRoot,
      'screenshot-resolution/wireframe.result.json',
    ), 'utf8').then(JSON.parse),
    readFile(resolve(
      visualRoot,
      'screenshot-repair-proposer-contract.json',
    ), 'utf8').then(JSON.parse),
    readFile(fileURLToPath(new URL('../contracts/design-ir.schema.json', import.meta.url)), 'utf8')
      .then(JSON.parse),
  ]);
  const root = await mkdtemp(join(tmpdir(), 'viewcompose-handoff-verifier-'));
  try {
    const store = await createStore(root, 'primary-store', hostGrant.trustDomainId);
    const outcome = await executeTrustedScreenshotRepair(
      executionInput(
        await directGrant(hostGrant, authorization, validationResult),
        resolutionResult,
        proposerContract,
      ),
      {host: store.host},
    );
    let serializedCode;
    try {
      await handoffTrustedScreenshotRepairAppliedResult(structuredClone(outcome), {
        host: store.host,
      });
    } catch (error) {
      serializedCode = error?.code;
    }
    const handoff = await handoffTrustedScreenshotRepairAppliedResult(outcome, {
      host: store.host,
    });
    let replayCode;
    try {
      await handoffTrustedScreenshotRepairAppliedResult(outcome, {host: store.host});
    } catch (error) {
      replayCode = error?.code;
    }

    const cancelledStore = await createStore(root, 'cancelled-store', hostGrant.trustDomainId);
    const cancelled = new AbortController();
    cancelled.abort();
    const cancelledOutcome = await executeTrustedScreenshotRepair(
      executionInput(
        await directGrant(hostGrant, authorization, validationResult),
        resolutionResult,
        proposerContract,
      ),
      {host: cancelledStore.host, signal: cancelled.signal},
    );
    let nonAppliedCode;
    try {
      await handoffTrustedScreenshotRepairAppliedResult(cancelledOutcome, {
        host: cancelledStore.host,
      });
    } catch (error) {
      nonAppliedCode = error?.code;
    }

    const concurrentStore = await createStore(root, 'concurrent-store', hostGrant.trustDomainId);
    const concurrentOutcome = await executeTrustedScreenshotRepair(
      executionInput(
        await directGrant(hostGrant, authorization, validationResult),
        resolutionResult,
        proposerContract,
      ),
      {host: concurrentStore.host},
    );
    const concurrent = await Promise.allSettled([
      handoffTrustedScreenshotRepairAppliedResult(concurrentOutcome, {
        host: concurrentStore.host,
      }),
      handoffTrustedScreenshotRepairAppliedResult(concurrentOutcome, {
        host: concurrentStore.host,
      }),
    ]);

    let recorded;
    const mismatchHost = createTrustedScreenshotRepairOutcomeHost({
      trustDomainId: hostGrant.trustDomainId,
      record: async (draft) => {
        recorded = {
          ...structuredClone(draft),
          receipt: {
            issuerTrustDomainId: draft.lineage.hostTrustDomainId,
            reservationReceipt: draft.lineage.reservationReceipt,
            terminalState: 'recorded',
            outcomeTransport: 'trusted-host-callback-only',
            outcomeReceipt: fingerprintRepairValue({draft, purpose: 'handoff-verifier'}),
          },
        };
        recorded.outcomeFingerprint = fingerprintRepairValue(recorded);
        return structuredClone(recorded);
      },
      reconcile: async () => {
        const changed = structuredClone(recorded);
        changed.receipt.outcomeReceipt = 'f'.repeat(64);
        delete changed.outcomeFingerprint;
        changed.outcomeFingerprint = fingerprintRepairValue(changed);
        return changed;
      },
    });
    const mismatchOutcome = await executeTrustedScreenshotRepair(
      executionInput(
        await directGrant(hostGrant, authorization, validationResult),
        resolutionResult,
        proposerContract,
      ),
      {host: mismatchHost},
    );
    let mismatchCode;
    try {
      await handoffTrustedScreenshotRepairAppliedResult(mismatchOutcome, {host: mismatchHost});
    } catch (error) {
      mismatchCode = error?.code;
    }

    const summary = {
      implementation: true,
      publicRepairMode: false,
      persistentSourceWrite: false,
      successfulHandoffs: handoff.receipt.status === 'delivered' ? 1 : 0,
      durableReceiptReads: handoff.receipt.delivery.terminalReceiptRevalidated ? 1 : 0,
      exactObjectDeliveries:
        Object.isFrozen(handoff.designIr) &&
        fingerprintRepairValue(handoff.designIr) === outcome.effect.resultDesignIrFingerprint &&
        validateSchemaValue(handoff.designIr, designIrSchema).length === 0
          ? 1
          : 0,
      serializedAuthoritiesAccepted:
        serializedCode === 'VC-AI-REPAIR-HANDOFF-AUTHORITY-INVALID' ? 0 : 1,
      nonAppliedResultsDelivered:
        nonAppliedCode === 'VC-AI-REPAIR-HANDOFF-AUTHORITY-INVALID' ? 0 : 1,
      mismatchedReceiptsAccepted:
        mismatchCode === 'VC-AI-REPAIR-HANDOFF-DURABLE-RECEIPT-MISMATCH' ? 0 : 1,
      replayedDeliveries:
        replayCode === 'VC-AI-REPAIR-HANDOFF-ALREADY-DELIVERED' ? 0 : 1,
      concurrentDeliveries:
        concurrent.filter((entry) => entry.status === 'fulfilled').length,
    };
    if (
      validateSchemaValue(handoff.receipt, schema).length > 0 ||
      handoff.receipt.handoffFingerprint !== (() => {
        const unsigned = structuredClone(handoff.receipt);
        delete unsigned.handoffFingerprint;
        return fingerprintRepairValue(unsigned);
      })() ||
      rejectionCode(concurrent) !== 'VC-AI-REPAIR-HANDOFF-IN-PROGRESS' ||
      !same(summary, {
        implementation: true,
        publicRepairMode: false,
        persistentSourceWrite: false,
        ...contract.acceptance,
      })
    ) {
      throw new Error('Screenshot repair applied-result handoff implementation changed');
    }
    return summary;
  } finally {
    await rm(root, {recursive: true, force: true});
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase5ScreenshotRepairAppliedResultHandoff()
    .then((summary) => {
      console.log(
        'Verified screenshot repair applied-result handoff: ' +
          `${summary.successfulHandoffs}/1 successful durable handoff, ` +
          `${summary.serializedAuthoritiesAccepted}/0 serialized authorities and ` +
          `${summary.replayedDeliveries}/0 replays accepted; public execution remains off.`,
      );
    })
    .catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
}
