import path from 'node:path';

export function parseDryRunTasks(output) {
  const tasks = [];
  const seen = new Set();
  for (const line of output.split(/\r?\n/u)) {
    const match = /^(:\S+)\s+SKIPPED\s*$/u.exec(line.trim());
    if (match && !seen.has(match[1])) {
      seen.add(match[1]);
      tasks.push(match[1]);
    }
  }
  return tasks;
}
function instant(value, label) {
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) {
    throw new Error(`Invalid ${label} timestamp: ${value}`);
  }
  return timestamp;
}

export function measureJob(run, jobName, executionStartStep, executionEndStep) {
  const job = run.jobs.find((candidate) => candidate.name === jobName);
  if (!job) {
    throw new Error(`Run ${run.databaseId} has no '${jobName}' job.`);
  }
  const startStep = job.steps.find((step) => step.name === executionStartStep);
  const endStep = job.steps.find((step) => step.name === executionEndStep);
  if (!startStep || !endStep) {
    throw new Error(
      `Run ${run.databaseId} job '${jobName}' does not contain the configured execution boundary.`,
    );
  }

  const runCreated = instant(run.createdAt, 'run creation');
  const jobStarted = instant(job.startedAt, 'job start');
  const executionStarted = instant(startStep.startedAt, 'execution start');
  const executionCompleted = instant(endStep.completedAt, 'execution completion');
  const jobCompleted = instant(job.completedAt, 'job completion');

  return {
    runId: run.databaseId,
    jobId: job.databaseId,
    url: run.url,
    queueMillis: jobStarted - runCreated,
    setupMillis: executionStarted - jobStarted,
    executionMillis: executionCompleted - executionStarted,
    postMillis: jobCompleted - executionCompleted,
    totalMillis: jobCompleted - runCreated,
    executionBoundary: {
      firstStep: executionStartStep,
      lastStep: executionEndStep,
    },
    executionSteps: job.steps
      .filter((step) => {
        const started = Date.parse(step.startedAt);
        const completed = Date.parse(step.completedAt);
        return started >= executionStarted && completed <= executionCompleted;
      })
      .map((step) => ({
        name: step.name,
        durationMillis: Date.parse(step.completedAt) - Date.parse(step.startedAt),
      })),
  };
}

function matchesPattern(candidatePath, pattern) {
  const normalized = candidatePath.split(path.sep).join('/').replace(/^\.\//u, '');
  if (pattern.endsWith('/**')) {
    const prefix = pattern.slice(0, -3);
    return normalized === prefix || normalized.startsWith(`${prefix}/`);
  }
  return normalized === pattern;
}

export function classifyFixturePath(candidatePath, policy) {
  const fullPattern = policy.alwaysFull.find((pattern) => matchesPattern(candidatePath, pattern));
  if (fullPattern) {
    return { mode: 'full', reason: `always-full:${fullPattern}` };
  }
  const scopedPattern = policy.knownScoped.find((pattern) => matchesPattern(candidatePath, pattern));
  if (scopedPattern) {
    return { mode: 'scoped', reason: `known-scoped:${scopedPattern}` };
  }
  return { mode: 'full', reason: 'unknown-path' };
}
