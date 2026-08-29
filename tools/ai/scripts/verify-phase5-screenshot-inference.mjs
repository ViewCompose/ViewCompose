#!/usr/bin/env node
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {verifyScreenshotDesignInferenceContracts} from './verify-phase0.mjs';

const contractPath = fileURLToPath(
  new URL('../evaluation/fixtures/visual/screenshot-inference-contract.json', import.meta.url),
);

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'));
}

export async function verifyPhase5ScreenshotInference() {
  const [verification, contract] = await Promise.all([
    verifyScreenshotDesignInferenceContracts(),
    readJson(contractPath),
  ]);
  const golden = contract.supportedFixtures[0];
  if (
    verification.screenshotPreprocessing.supportedFixtures.length !== 2 ||
    verification.screenshotPreprocessing.unsupportedFixtures.length !== 2 ||
    verification.screenshotDesignInference.supportedFixtures.length !== 1 ||
    verification.screenshotDesignInference.unsupportedFixtures.length !== 3 ||
    contract.supportedFixtures.length !== 1 ||
    contract.unsupportedFixtures.length !== 3 ||
    golden.expectedNodes !== golden.expectedEvidenceRecords ||
    golden.expectedBlockingQuestions === 0 ||
    golden.expectedCodeGenerationAllowed !== false ||
    contract.execution.providerExecution !== false ||
    contract.execution.networkAccess !== false ||
    contract.execution.providerSelected !== false
  ) {
    throw new Error('Screenshot inference acceptance denominators changed');
  }
  return {
    supportedGoldens: contract.supportedFixtures.length,
    failClosedDenominators: contract.unsupportedFixtures.length,
    nodes: golden.expectedNodes,
    evidenceRecords: golden.expectedEvidenceRecords,
    unresolvedQuestions: golden.expectedUnresolvedQuestions,
    blockingQuestions: golden.expectedBlockingQuestions,
    requestFingerprint: golden.expectedRequestFingerprint,
    designIrFingerprint: golden.expectedDesignIrFingerprint,
    resultFingerprint: golden.expectedResultFingerprint,
    providerExecutions: 0,
    networkRequests: 0,
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  verifyPhase5ScreenshotInference()
    .then((summary) => {
      process.stdout.write(
        `Verified Phase 5 screenshot inference contract: ` +
        `${summary.supportedGoldens}/1 human golden, ` +
        `${summary.nodes}/${summary.evidenceRecords} node/evidence records, ` +
        `${summary.unresolvedQuestions} unresolved and ${summary.blockingQuestions} blocking questions, ` +
        `${summary.failClosedDenominators}/3 fail-closed denominators, ` +
        `${summary.providerExecutions} provider executions, and ${summary.networkRequests} network requests; ` +
        `Design IR ${summary.designIrFingerprint}, result ${summary.resultFingerprint}.\n`,
      );
    })
    .catch((error) => {
      process.stderr.write(`ViewCompose screenshot inference verification failed: ${error.message}\n`);
      process.exitCode = 1;
    });
}
