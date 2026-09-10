import {spawn} from 'node:child_process';

export function executeBoundedProcess(plan, {timeoutMs, maxOutputBytes, signal}) {
  if (signal?.aborted) {
    return Promise.resolve({exitCode: null, signal: null, output: '', truncated: false,
      timedOut: false, cancelled: true, spawnError: null});
  }
  return new Promise((resolvePromise) => {
    const ownsProcessGroup = process.platform !== 'win32';
    const child = spawn(plan.executable, plan.args, {
      cwd: plan.cwd,
      env: plan.env ?? process.env,
      stdio: ['ignore', 'pipe', 'pipe'],
      detached: ownsProcessGroup,
    });
    const chunks = [];
    let capturedBytes = 0;
    let truncated = false;
    let timedOut = false;
    let cancelled = false;
    let spawnError = null;
    let forceKillTimer = null;
    let settled = false;
    let terminating = false;
    const signalTree = (force) => {
      if (!child.pid) return;
      if (ownsProcessGroup) {
        try {
          process.kill(-child.pid, force ? 'SIGKILL' : 'SIGTERM');
        } catch (error) {
          if (error.code !== 'ESRCH') child.kill(force ? 'SIGKILL' : 'SIGTERM');
        }
      } else {
        // Windows has no POSIX process groups; taskkill owns descendant traversal without a shell.
        const killer = spawn('taskkill', ['/pid', String(child.pid), '/T', '/F'], {
          stdio: 'ignore', windowsHide: true,
        });
        killer.on('error', () => child.kill('SIGKILL'));
        killer.unref();
      }
    };
    const finish = (exitCode, childSignal) => {
      if (settled) return;
      settled = true;
      if (terminating) signalTree(true);
      clearTimeout(timeout);
      if (forceKillTimer) clearTimeout(forceKillTimer);
      signal?.removeEventListener('abort', cancellation);
      resolvePromise({exitCode, signal: childSignal, output: Buffer.concat(chunks).toString('utf8'),
        truncated, timedOut, cancelled, spawnError});
    };
    const requestTermination = () => {
      if (terminating || settled) return;
      terminating = true;
      forceKillTimer = setTimeout(() => {
        signalTree(true);
        // Inherited pipes or a descendant that left the process group cannot prolong completion.
        child.stdout.destroy();
        child.stderr.destroy();
        child.unref();
        finish(child.exitCode, child.signalCode);
      }, 2_000);
      signalTree(false);
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
    child.on('close', finish);
  });
}
