import {applyDesignIrRepairPatch} from './design-ir-repair-patch.mjs';
import {compareGeneratedLayout} from './layout-comparator.mjs';
import {compareScreenshotPixels} from './pixel-comparator.mjs';
import {
  fingerprintRepairValue,
  sealRepairEvaluation,
} from './repair-orchestrator.mjs';
import {generateScreenshotViewCompose} from './screenshot-generation-adapter.mjs';
import {
  SCREENSHOT_GENERATION_ARGUMENTS_SCHEMA,
  SCREENSHOT_RESOLUTION_RESULT_SCHEMA,
} from './screenshot-generation-contract.mjs';
import {validateSchemaValue} from './schema-validator.mjs';
import {toolCacheRoot} from './tool-core.mjs';

const SHA256 = /^[a-f0-9]{64}$/u;
const ZERO_FINGERPRINT = '0'.repeat(64);
const STANDARD_GATES = Object.freeze(['safety', 'compilation', 'render', 'semantics', 'structure']);
const GATE_ORDER = Object.freeze([...STANDARD_GATES, 'exact-pixels']);
const MAX_EVIDENCE_BYTES = 16 * 1024 * 1024;

export class ScreenshotRepairCandidateEvaluationError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'ScreenshotRepairCandidateEvaluationError';
    this.code = code;
  }
}

function throwIfCancelled(signal) {
  if (signal?.aborted) {
    throw new ScreenshotRepairCandidateEvaluationError(
      'VC-AI-REPAIR-CANCELLED',
      'Screenshot repair candidate evaluation was cancelled.',
    );
  }
}

function standardGate(name, status, passedChecks, totalChecks, evidenceFingerprint) {
  return {name, status, passedChecks, totalChecks, evidenceFingerprint};
}

function pixelGate(status, comparedPixels, mismatchedPixels, maxChannelDelta, evidenceFingerprint) {
  return {
    name: 'exact-pixels',
    status,
    comparedPixels,
    mismatchedPixels,
    maxChannelDelta,
    evidenceFingerprint,
  };
}

function notRunGate(name) {
  return name === 'exact-pixels'
    ? pixelGate('not-run', 0, 0, 0, ZERO_FINGERPRINT)
    : standardGate(name, 'not-run', 0, 0, ZERO_FINGERPRINT);
}

function completeAfter(gates, name) {
  for (const later of GATE_ORDER.slice(GATE_ORDER.indexOf(name) + 1)) {
    gates.push(notRunGate(later));
  }
}

function diagnosticCodes(value) {
  return [...new Set((Array.isArray(value?.diagnostics) ? value.diagnostics : [])
    .map((item) => item?.code)
    .filter((code) => /^VC-AI-[A-Z0-9-]+$/u.test(code ?? '')))].sort();
}

function sealCandidateEvidence({
  baseResolutionResultFingerprint,
  candidateResolutionResultFingerprint,
  inputDesignIrFingerprint,
  candidateEvaluation,
  designIr,
  changeFingerprint,
  diagnosticsByGate,
  layoutComparison,
  pixelComparison,
  pixelLocalization,
}) {
  const evidence = {
    schemaVersion: 1,
    status: candidateEvaluation.gates.at(-1).status !== 'not-run'
      ? 'complete'
      : 'short-circuited',
    lineage: {
      baseResolutionResultFingerprint,
      candidateResolutionResultFingerprint,
      inputDesignIrFingerprint,
      candidateDesignIrFingerprint: candidateEvaluation.designIrFingerprint,
      changeFingerprint: changeFingerprint ?? null,
    },
    candidateEvaluation: structuredClone(candidateEvaluation),
    designIr: structuredClone(designIr),
    diagnostics: GATE_ORDER.map((gate) => ({
      gate,
      codes: [...(diagnosticsByGate.get(gate) ?? [])],
    })),
    layoutComparison: layoutComparison === undefined ? null : structuredClone(layoutComparison),
    pixelComparison: pixelComparison === undefined ? null : structuredClone(pixelComparison),
    pixelLocalization: pixelLocalization === undefined ? null : structuredClone(pixelLocalization),
  };
  if (Buffer.byteLength(JSON.stringify(evidence), 'utf8') > MAX_EVIDENCE_BYTES) {
    throw new ScreenshotRepairCandidateEvaluationError(
      'VC-AI-REPAIR-INPUT-INVALID',
      'Screenshot repair candidate evidence exceeds the 16 MiB internal ceiling.',
    );
  }
  evidence.evidenceFingerprint = fingerprintRepairValue(evidence);
  return evidence;
}

function stageFingerprint(stage, value) {
  const outputFingerprint = value?.evidence?.outputFingerprint ??
    value?.comparison?.comparisonFingerprint;
  if (SHA256.test(outputFingerprint ?? '')) return outputFingerprint;
  return fingerprintRepairValue({
    stage,
    status: value?.status ?? 'invalid',
    evidence: {
      level: value?.evidence?.level ?? value?.evidenceLevel ?? null,
      compilerLane: value?.evidence?.compilerLane ?? null,
      renderLane: value?.evidence?.renderLane ?? null,
    },
    diagnostics: Array.isArray(value?.diagnostics) ? value.diagnostics : [],
  });
}

function resolutionFingerprint(resolutionResult) {
  const copy = structuredClone(resolutionResult);
  delete copy.resultFingerprint;
  return fingerprintRepairValue(copy);
}

function candidateResolution(resolutionResult, designIr, designIrFingerprint) {
  const candidate = structuredClone(resolutionResult);
  candidate.designIr = structuredClone(designIr);
  candidate.designIrFingerprint = designIrFingerprint;
  candidate.resultFingerprint = resolutionFingerprint(candidate);
  return candidate;
}

function generationRequest(base, mode, resolutionResult) {
  const request = structuredClone(base);
  request.mode = mode;
  request.input = {
    resolutionResultFingerprint: resolutionResult.resultFingerprint,
    resolvedDesignIrFingerprint: resolutionResult.designIrFingerprint,
  };
  return request;
}

function candidateFingerprint({
  resolutionResult,
  generationRequest: request,
  previewBindings,
  pixelReference,
  patch,
}) {
  return fingerprintRepairValue({
    schemaVersion: 1,
    resolutionResultFingerprint: resolutionResult.resultFingerprint,
    designIrFingerprint: resolutionResult.designIrFingerprint,
    generationPolicy: request?.policy ?? null,
    previewBindings,
    pixelReference: {
      requestFingerprint: pixelReference?.result?.requestFingerprint ?? null,
      outputFingerprint: pixelReference?.result?.outputFingerprint ?? null,
    },
    changeFingerprint: patch?.changeFingerprint ?? null,
  });
}

function failedSafetyEvaluation(input, evidence) {
  const designIrFingerprint = SHA256.test(input?.resolutionResult?.designIrFingerprint ?? '')
    ? input.resolutionResult.designIrFingerprint
    : fingerprintRepairValue(input?.resolutionResult?.designIr ?? null);
  const fallbackResolution = {
    resultFingerprint: SHA256.test(input?.resolutionResult?.resultFingerprint ?? '')
      ? input.resolutionResult.resultFingerprint
      : ZERO_FINGERPRINT,
    designIrFingerprint,
  };
  const gates = [standardGate('safety', 'failed', 0, 1, fingerprintRepairValue(evidence))];
  completeAfter(gates, 'safety');
  return sealRepairEvaluation({
    candidateFingerprint: candidateFingerprint({
      ...input,
      resolutionResult: fallbackResolution,
    }),
    designIrFingerprint,
    gates,
  });
}

function validateAcceptedResolution(resolutionResult) {
  return validateSchemaValue(
    resolutionResult,
    SCREENSHOT_RESOLUTION_RESULT_SCHEMA,
  ).length === 0 &&
    resolutionResult?.status === 'resolved' &&
    resolutionResult?.summary?.codeGenerationAllowed === true &&
    resolutionResult?.designIr?.source?.kind === 'screenshot' &&
    resolutionResult?.designIr?.unsupported?.length === 0 &&
    resolutionResult.designIrFingerprint === fingerprintRepairValue(resolutionResult.designIr) &&
    resolutionResult.resultFingerprint === resolutionFingerprint(resolutionResult);
}

function requiredChecks(comparison, categories) {
  return comparison.nodes
    .flatMap((node) => node.checks)
    .filter((check) => categories.includes(check.category) && check.status !== 'not-applicable');
}

function comparisonGates(compared) {
  const evidenceFingerprint = stageFingerprint('layout-comparison', compared);
  const comparison = compared?.comparison;
  if (
    !SHA256.test(comparison?.comparisonFingerprint ?? '') ||
    !Array.isArray(comparison?.nodes) ||
    !Array.isArray(comparison?.findings) ||
    comparison.nodes.some((node) => !Array.isArray(node?.checks))
  ) {
    return {
      semantics: standardGate('semantics', 'failed', 0, 1, evidenceFingerprint),
      structure: notRunGate('structure'),
    };
  }
  const semanticChecks = requiredChecks(comparison, ['semantic']);
  const semanticPassed = semanticChecks.filter((check) => check.status === 'passed').length;
  const semanticGate = standardGate(
    'semantics',
    semanticChecks.length > 0 && semanticPassed === semanticChecks.length ? 'passed' : 'failed',
    semanticPassed,
    Math.max(1, semanticChecks.length),
    evidenceFingerprint,
  );
  if (semanticGate.status !== 'passed') {
    return {semantics: semanticGate, structure: notRunGate('structure')};
  }

  const structureChecks = requiredChecks(comparison, ['identity', 'structure', 'geometry']);
  const knownCheckIds = new Set(
    comparison.nodes.flatMap((node) => node.checks).map((check) => check.id),
  );
  const unboundFindings = comparison.findings.filter((finding) =>
    !finding.checkId || !knownCheckIds.has(finding.checkId));
  const unexplainedFailure = comparison.status !== 'passed' &&
    structureChecks.every((check) => check.status === 'passed') &&
    unboundFindings.length === 0 ? 1 : 0;
  const structuralFailures = structureChecks.filter((check) => check.status === 'failed').length +
    unboundFindings.length + unexplainedFailure;
  const structureTotal = structureChecks.length + unboundFindings.length + unexplainedFailure;
  const structurePassed = structureChecks.filter((check) => check.status === 'passed').length;
  return {
    semantics: semanticGate,
    structure: standardGate(
      'structure',
      structureTotal > 0 && structuralFailures === 0 && comparison.status === 'passed'
        ? 'passed'
        : 'failed',
      structurePassed,
      Math.max(1, structureTotal),
      evidenceFingerprint,
    ),
  };
}

function exactPixelGate(compared) {
  const evidenceFingerprint = stageFingerprint('exact-pixels', compared);
  const metrics = compared?.comparison?.metrics;
  if (
    !Number.isInteger(metrics?.comparedPixels) ||
    !Number.isInteger(metrics?.mismatchedPixels) ||
    !Number.isInteger(metrics?.maxChannelDelta) ||
    metrics.comparedPixels < 1 ||
    metrics.mismatchedPixels < 0 ||
    metrics.mismatchedPixels > metrics.comparedPixels ||
    metrics.maxChannelDelta < 0 ||
    metrics.maxChannelDelta > 255 ||
    (metrics.mismatchedPixels === 0) !== (metrics.maxChannelDelta === 0)
  ) {
    return pixelGate('failed', 0, 0, 0, evidenceFingerprint);
  }
  const passed = compared.status === 'success' &&
    compared.comparison.status === 'passed' &&
    metrics.mismatchedPixels === 0 &&
    metrics.maxChannelDelta === 0;
  if (!passed && metrics.mismatchedPixels === 0) {
    return pixelGate('failed', 0, 0, 0, evidenceFingerprint);
  }
  return pixelGate(
    passed ? 'passed' : 'failed',
    metrics.comparedPixels,
    metrics.mismatchedPixels,
    metrics.maxChannelDelta,
    evidenceFingerprint,
  );
}

async function evaluateCandidateCore({
  resolutionResult,
  generationRequest: baseGenerationRequest,
  previewBindings,
  pixelReference,
  patch,
} = {}, {
  requestId = 'screenshot-repair-candidate',
  limits,
  repository = toolCacheRoot(),
  signal,
  applyPatch = applyDesignIrRepairPatch,
  generate = generateScreenshotViewCompose,
  compare = compareGeneratedLayout,
  comparePixels = compareScreenshotPixels,
} = {}) {
  throwIfCancelled(signal);
  const input = {
    resolutionResult,
    generationRequest: baseGenerationRequest,
    previewBindings,
    pixelReference,
    patch,
  };
  if (!validateAcceptedResolution(resolutionResult)) {
    return {
      evaluation: failedSafetyEvaluation(input, {
        stage: 'resolution',
        code: 'VC-AI-REPAIR-INPUT-INVALID',
      }),
      evidence: null,
    };
  }

  let designIr = structuredClone(resolutionResult.designIr);
  let designIrFingerprint = resolutionResult.designIrFingerprint;
  let safetyEvidenceFingerprint = resolutionResult.resultFingerprint;
  if (patch !== undefined) {
    try {
      const applied = await applyPatch({
        designIr,
        expectedDesignIrFingerprint: designIrFingerprint,
        patch,
      }, {signal});
      throwIfCancelled(signal);
      designIr = applied.designIr;
      designIrFingerprint = applied.designIrFingerprint;
      safetyEvidenceFingerprint = applied.outputFingerprint;
    } catch (error) {
      if (signal?.aborted || error?.code === 'VC-AI-REPAIR-CANCELLED') throwIfCancelled(signal);
      return {
        evaluation: failedSafetyEvaluation(input, {
          stage: 'typed-patch',
          code: error?.code ?? 'VC-AI-REPAIR-INPUT-INVALID',
        }),
        evidence: null,
      };
    }
  }

  const candidate = candidateResolution(resolutionResult, designIr, designIrFingerprint);
  const pixelRequest = generationRequest(baseGenerationRequest, 'compare-pixels', candidate);
  const fullArguments = {
    resolutionResult: candidate,
    generationRequest: pixelRequest,
    previewBindings: structuredClone(previewBindings),
    pixelReference: structuredClone(pixelReference),
  };
  if (validateSchemaValue(fullArguments, SCREENSHOT_GENERATION_ARGUMENTS_SCHEMA).length > 0) {
    return {
      evaluation: failedSafetyEvaluation({...input, resolutionResult: candidate}, {
        stage: 'candidate-input',
        code: 'VC-AI-REPAIR-INPUT-INVALID',
      }),
      evidence: null,
    };
  }

  const identity = candidateFingerprint({
    resolutionResult: candidate,
    generationRequest: pixelRequest,
    previewBindings,
    pixelReference,
    patch,
  });
  const gates = [standardGate('safety', 'passed', 1, 1, safetyEvidenceFingerprint)];
  const diagnosticsByGate = new Map(GATE_ORDER.map((gate) => [gate, []]));
  let layoutComparison;
  let pixelComparison;
  let pixelLocalization;
  const finish = () => {
    const evaluation = sealRepairEvaluation({
      candidateFingerprint: identity,
      designIrFingerprint,
      gates,
    });
    return {
      evaluation,
      evidence: sealCandidateEvidence({
        baseResolutionResultFingerprint: resolutionResult.resultFingerprint,
        candidateResolutionResultFingerprint: candidate.resultFingerprint,
        inputDesignIrFingerprint: resolutionResult.designIrFingerprint,
        candidateEvaluation: evaluation,
        designIr,
        changeFingerprint: patch?.changeFingerprint,
        diagnosticsByGate,
        layoutComparison,
        pixelComparison,
        pixelLocalization,
      }),
    };
  };
  const compileResult = await generate({
    resolutionResult: candidate,
    generationRequest: generationRequest(baseGenerationRequest, 'compile', candidate),
  }, {requestId: `${requestId}-compile`, limits, signal});
  throwIfCancelled(signal);
  diagnosticsByGate.set('compilation', diagnosticCodes(compileResult));
  const compileFingerprint = stageFingerprint('compilation', compileResult);
  const compilePassed = compileResult?.status === 'success' &&
    compileResult?.evidence?.level === 'compiled' &&
    SHA256.test(compileResult?.evidence?.outputFingerprint ?? '') &&
    SHA256.test(compileResult?.data?.kotlinFingerprint ?? '');
  gates.push(standardGate(
    'compilation',
    compilePassed ? 'passed' : 'failed',
    compilePassed ? 1 : 0,
    1,
    compileFingerprint,
  ));
  if (!compilePassed) {
    completeAfter(gates, 'compilation');
    return finish();
  }

  const renderResult = await generate({
    resolutionResult: candidate,
    generationRequest: generationRequest(baseGenerationRequest, 'render', candidate),
    previewBindings: structuredClone(previewBindings),
  }, {requestId: `${requestId}-render`, limits, signal});
  throwIfCancelled(signal);
  diagnosticsByGate.set('render', diagnosticCodes(renderResult));
  const renderFingerprint = stageFingerprint('render', renderResult);
  const renderPassed = renderResult?.status === 'success' &&
    renderResult?.evidence?.level === 'rendered' &&
    SHA256.test(renderResult?.evidence?.outputFingerprint ?? '') &&
    renderResult?.data?.preview !== null &&
    typeof renderResult?.data?.preview === 'object' &&
    !Array.isArray(renderResult?.data?.preview);
  gates.push(standardGate(
    'render',
    renderPassed ? 'passed' : 'failed',
    renderPassed ? 1 : 0,
    1,
    renderFingerprint,
  ));
  if (!renderPassed) {
    completeAfter(gates, 'render');
    return finish();
  }

  const compared = await compare({
    designIr,
    previewBindings,
    preview: renderResult.data.preview,
    previewEvidence: renderResult.evidence,
  }, {repository});
  throwIfCancelled(signal);
  layoutComparison = compared?.comparison;
  const layoutDiagnosticCodes = diagnosticCodes(compared);
  diagnosticsByGate.set(
    'semantics',
    layoutDiagnosticCodes.filter((code) => code.includes('SEMANTIC')),
  );
  diagnosticsByGate.set(
    'structure',
    layoutDiagnosticCodes.filter((code) => !code.includes('SEMANTIC')),
  );
  const comparison = comparisonGates(compared);
  gates.push(comparison.semantics);
  if (comparison.semantics.status !== 'passed') {
    completeAfter(gates, 'semantics');
    return finish();
  }
  gates.push(comparison.structure);
  if (comparison.structure.status !== 'passed') {
    completeAfter(gates, 'structure');
    return finish();
  }

  const pixelCompared = await comparePixels({
    referenceRequest: pixelReference.request,
    referenceResult: pixelReference.result,
    semanticComparison: compared.comparison,
    preview: renderResult.data.preview,
    previewEvidence: renderResult.evidence,
  }, {repository, signal});
  throwIfCancelled(signal);
  pixelComparison = pixelCompared?.comparison;
  pixelLocalization = pixelCompared?.localization;
  diagnosticsByGate.set('exact-pixels', diagnosticCodes(pixelCompared));
  gates.push(exactPixelGate(pixelCompared));
  return finish();
}

export async function evaluateScreenshotRepairCandidate(input, options) {
  return (await evaluateCandidateCore(input, options)).evaluation;
}

export async function evaluateScreenshotRepairCandidateWithEvidence(input, options) {
  return evaluateCandidateCore(input, options);
}

export function createScreenshotRepairCandidateSession(input, options = {}) {
  const immutableInput = structuredClone(input);
  const evidenceByCandidate = new Map();
  const evaluate = async (patch, signal) => {
    const result = await evaluateCandidateCore({
      ...structuredClone(immutableInput),
      ...(patch === undefined ? {} : {patch: structuredClone(patch)}),
    }, {
      ...options,
      signal: signal ?? options.signal,
    });
    if (result.evidence !== null) {
      evidenceByCandidate.set(
        result.evaluation.candidateFingerprint,
        structuredClone(result.evidence),
      );
    }
    return result.evaluation;
  };
  return Object.freeze({
    evaluateInitial: ({signal} = {}) => evaluate(undefined, signal),
    evaluatePatch: ({patch}, {signal} = {}) => evaluate(patch, signal),
    readEvidence: (candidateFingerprint) => {
      const evidence = evidenceByCandidate.get(candidateFingerprint);
      return evidence === undefined ? null : structuredClone(evidence);
    },
  });
}

export function createScreenshotRepairCandidateEvaluator(input, options = {}) {
  return createScreenshotRepairCandidateSession(input, options).evaluatePatch;
}
