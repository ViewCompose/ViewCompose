import {proposeScreenshotRepair} from './screenshot-repair-proposer.mjs';
import {
  evaluateScreenshotRepairCandidateWithEvidence,
} from './screenshot-repair-candidate-evaluator.mjs';
import {
  prepareScreenshotSourceApplication,
} from './screenshot-source-application-preparer.mjs';
import {
  storePreparedSourceApplication,
} from './screenshot-source-transaction.mjs';
import {diagnostic, toolResult} from './tool-core.mjs';

export async function prepareScreenshotRepairTool(arguments_, {
  requestId = 'prepare-screenshot-repair',
  limits,
  signal,
  evaluate = evaluateScreenshotRepairCandidateWithEvidence,
  propose = proposeScreenshotRepair,
  prepare = prepareScreenshotSourceApplication,
  store = storePreparedSourceApplication,
} = {}) {
  if (arguments_.operation === 'evaluate') {
    const evaluated = await evaluate(arguments_.evaluationInput, {
      requestId,
      limits,
      signal,
    });
    const successful = evaluated.evidence?.status === 'complete';
    return toolResult({
      requestId,
      tool: 'prepare_screenshot_repair',
      status: successful ? 'success' : 'failed',
      level: 'compared',
      diagnostics: successful ? [] : [diagnostic({
        code: 'VC-AI-REPAIR-EVALUATION-INCOMPLETE',
        severity: 'error',
        message: 'The six-gate screenshot repair evidence is incomplete.',
        nextAction: 'Resolve the first failed gate before proposing a source repair.',
      })],
      data: evaluated,
      outputFingerprint: evaluated.evidence?.evidenceFingerprint,
    });
  }
  if (arguments_.operation === 'propose') {
    const proposal = await propose({
      baselineEvidence: arguments_.baselineEvidence,
      candidateEvidence: arguments_.candidateEvidence,
    }, {signal});
    const successful = proposal.status === 'proposed';
    return toolResult({
      requestId,
      tool: 'prepare_screenshot_repair',
      status: successful ? 'success' : proposal.status === 'invalid' ? 'invalid' : 'failed',
      level: 'compared',
      diagnostics: proposal.diagnostics,
      data: {proposal},
      outputFingerprint: proposal.proposalFingerprint,
    });
  }
  const bundle = await prepare(arguments_, {signal});
  const stored = await store(bundle, {projectRoot: arguments_.projectRoot});
  const fingerprint = bundle.request.requestFingerprint;
  return toolResult({
    requestId,
    tool: 'prepare_screenshot_repair',
    status: 'success',
    level: 'compared',
    diagnostics: [],
    data: {
      request: bundle.request,
      preApplyEvidence: bundle.preApplyEvidence,
      storage: stored,
      nextCommands: {
        review: `viewcompose-repair show ${fingerprint} --pretty`,
        apply: `viewcompose-repair apply ${fingerprint} --pretty`,
        recover: `viewcompose-repair recover ${fingerprint} --pretty`,
      },
      sourceWritePerformed: false,
    },
    outputFingerprint: fingerprint,
  });
}
