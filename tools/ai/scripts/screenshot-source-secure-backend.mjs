import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {executeBoundedProcess} from './bounded-process.mjs';
import {detectJavaRuntime} from './tool-core.mjs';

const helperPath = fileURLToPath(
  new URL('../harness/source-repair/ViewComposeSourceRepairHost.java', import.meta.url),
);

export class ScreenshotSourceSecureBackendError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'ScreenshotSourceSecureBackendError';
    this.code = code;
  }
}

function parseFailure(output) {
  const line = output.split(/\r?\n/u).find((item) => item.startsWith('ERROR\t'));
  if (!line) {
    return new ScreenshotSourceSecureBackendError(
      'VC-AI-SOURCE-APPLICATION-FILESYSTEM-UNSUPPORTED',
      'Secure source host failed without a valid bounded diagnostic.',
    );
  }
  const [, code, message] = line.split('\t', 3);
  return new ScreenshotSourceSecureBackendError(code, message);
}

export async function secureReplaceSource({
  projectRoot,
  relativePath,
  expectedSha256,
  candidatePath,
  candidateSha256,
  temporaryName,
} = {}, {
  javaRuntime = detectJavaRuntime(),
  execute = executeBoundedProcess,
  signal,
} = {}) {
  if (!javaRuntime || ![17, 21].includes(javaRuntime.feature)) {
    throw new ScreenshotSourceSecureBackendError(
      'VC-AI-SOURCE-APPLICATION-FILESYSTEM-UNSUPPORTED',
      'Secure source application requires the accepted JDK 17 or 21 lane.',
    );
  }
  const result = await execute({
    executable: resolve(javaRuntime.javaHome, 'bin/java'),
    args: [
      '--add-opens',
      'java.base/sun.nio.fs=ALL-UNNAMED',
      '--add-opens',
      'java.base/sun.nio.ch=ALL-UNNAMED',
      '--source',
      '17',
      helperPath,
      'replace',
      projectRoot,
      relativePath,
      expectedSha256,
      candidatePath,
      candidateSha256,
      temporaryName,
    ],
  }, {timeoutMs: 30_000, maxOutputBytes: 16_384, signal});
  if (
    result.exitCode !== 0 ||
    result.timedOut ||
    result.cancelled ||
    result.truncated ||
    result.spawnError
  ) {
    throw parseFailure(result.output);
  }
  const line = result.output.trim();
  const fields = line.split('\t');
  if (fields.length !== 3 || fields[0] !== 'OK' || fields[1] !== candidateSha256) {
    throw new ScreenshotSourceSecureBackendError(
      'VC-AI-SOURCE-APPLICATION-FILESYSTEM-UNSUPPORTED',
      'Secure source host returned an invalid commit receipt.',
    );
  }
  return {status: 'committed', sha256: fields[1], fileKey: fields[2]};
}
