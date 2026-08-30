import {spawn} from 'node:child_process';

export function executeBoundedProcess(plan, {timeoutMs, maxOutputBytes, signal}) {
  return new Promise((resolvePromise) => {
    const child = spawn(plan.executable, plan.args, {
      cwd: plan.cwd,
      env: plan.env ?? process.env,
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    const chunks = [];
    let capturedBytes = 0;
    let truncated = false;
    let timedOut = false;
    let cancelled = false;
    let spawnError = null;
    let forceKillTimer = null;
    const requestTermination = () => {
      if (child.exitCode !== null || child.signalCode !== null) return;
      child.kill('SIGTERM');
      forceKillTimer ??= setTimeout(() => {
        if (child.exitCode === null && child.signalCode === null) child.kill('SIGKILL');
      }, 2_000);
    };
    const capture = (chunk) => {
      if (truncated) return;
      const remaining = maxOutputBytes - capturedBytes;
      if (chunk.length > remaining) {
        if (remaining > 0) chunks.push(chunk.subarray(0, remaining));
        capturedBytes = maxOutputBytes;
        truncated = true;
        requestTermination();
      } else {
        chunks.push(chunk);
        capturedBytes += chunk.length;
      }
    };
    child.stdout.on('data', capture);
    child.stderr.on('data', capture);
    child.on('error', (error) => {
      spawnError = error;
    });
    const timeout = setTimeout(() => {
      timedOut = true;
      requestTermination();
    }, timeoutMs);
    const cancellation = () => {
      cancelled = true;
      requestTermination();
    };
    signal?.addEventListener('abort', cancellation, {once: true});
    if (signal?.aborted) cancellation();
    child.on('close', (exitCode, childSignal) => {
      clearTimeout(timeout);
      if (forceKillTimer) clearTimeout(forceKillTimer);
      signal?.removeEventListener('abort', cancellation);
      resolvePromise({
        exitCode,
        signal: childSignal,
        output: Buffer.concat(chunks).toString('utf8'),
        truncated,
        timedOut,
        cancelled,
        spawnError,
      });
    });
  });
}
