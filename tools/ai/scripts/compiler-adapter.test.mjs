import assert from 'node:assert/strict';
import {mkdir, mkdtemp, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import test from 'node:test';
import {
  COMPILER_LANE,
  compileKotlin,
  compilerRequestKey,
  executeCompilerProcess,
} from './compiler-adapter.mjs';

const validSource = `
  package example
  import com.viewcompose.ui.foundation.Text
  import com.viewcompose.ui.foundation.UiTreeBuilder
  fun UiTreeBuilder.example() { Text("Ready") }
`;
const fixedExecution = Object.freeze({
  projectRoot: process.cwd(),
  androidSdk: Object.freeze({root: '/fixed/android-sdk', apiLevel: 36}),
});

async function successfulCompiler(plan) {
  await mkdir(plan.classesDirectory, {recursive: true});
  await writeFile(resolve(plan.classesDirectory, 'ExampleKt.class'), 'compiled-bytecode');
  return {
    exitCode: 0,
    signal: null,
    output: '',
    truncated: false,
    timedOut: false,
    cancelled: false,
    spawnError: null,
  };
}

test('request keys cover source, artifact allowlist, lane, and bundle identity', () => {
  const first = compilerRequestKey({
    source: validSource,
    artifactIds: ['viewcompose-ui-foundation'],
    bundleFingerprint: 'a'.repeat(64),
  });
  const second = compilerRequestKey({
    source: `${validSource}\n`,
    artifactIds: ['viewcompose-ui-foundation'],
    bundleFingerprint: 'a'.repeat(64),
  });
  assert.match(first, /^[a-f0-9]{64}$/u);
  assert.notEqual(first, second);
  assert.match(COMPILER_LANE, /released-maven.*jdk-17-or-21.*agp-9\.1\.1.*kotlin-2\.2\.10/u);
});

test('compiles in the fixed plan and accepts only an integrity-verified cache hit', async () => {
  const cacheRoot = await mkdtemp(join(tmpdir(), 'viewcompose-ai-compiler-'));
  let executions = 0;
  let capturedPlan;
  const runCompiler = async (plan) => {
    executions += 1;
    capturedPlan = plan;
    return successfulCompiler(plan);
  };
  const options = {
    ...fixedExecution,
    cacheRoot,
    javaFeature: 21,
    javaHome: '/fixed/jdk-21',
    runCompiler,
  };
  try {
    const first = await compileKotlin({source: validSource}, options);
    assert.equal(first.status, 'success');
    assert.equal(first.evidence.level, 'compiled');
    assert.equal(first.evidence.cache, 'miss');
    assert.match(first.evidence.outputFingerprint, /^[a-f0-9]{64}$/u);
    assert.equal(executions, 1);
    assert.equal(capturedPlan.args.includes('--offline'), false);
    assert.ok(capturedPlan.args.includes('--no-daemon'));
    assert.ok(capturedPlan.args.includes(':compiler:compileAiSnippet'));
    assert.equal(capturedPlan.args.some((argument) => argument.includes(validSource)), false);

    const second = await compileKotlin({source: validSource}, options);
    assert.equal(second.status, 'success');
    assert.equal(second.evidence.cache, 'hit');
    assert.equal(second.evidence.outputFingerprint, first.evidence.outputFingerprint);
    assert.equal(executions, 1);

    await writeFile(resolve(capturedPlan.classesDirectory, 'ExampleKt.class'), 'tampered');
    const poisoned = await compileKotlin({source: validSource}, options);
    assert.equal(poisoned.status, 'failed');
    assert.equal(poisoned.diagnostics[0].code, 'VC-AI-CACHE-POISONED');
    assert.equal(executions, 1);
  } finally {
    await rm(cacheRoot, {recursive: true, force: true});
  }
});

test('rejects static failures and artifacts outside the fixed compiler classpath', async () => {
  let executions = 0;
  const runCompiler = async (plan) => {
    executions += 1;
    return successfulCompiler(plan);
  };
  const common = {
    ...fixedExecution,
    javaFeature: 21,
    javaHome: '/fixed/jdk-21',
    runCompiler,
  };
  const staticFailure = await compileKotlin({
    source: `
      package example
      import com.viewcompose.ui.foundation.Column
      import com.viewcompose.ui.foundation.UiTreeBuilder
      fun UiTreeBuilder.example() { Column { padding(16) } }
    `,
  }, common);
  assert.equal(staticFailure.status, 'invalid');
  assert.equal(staticFailure.evidence.level, 'static');

  const artifactFailure = await compileKotlin({
    source: validSource,
    artifactIds: ['viewcompose-material3-android'],
  }, common);
  assert.equal(artifactFailure.status, 'unsupported');
  assert.equal(artifactFailure.diagnostics[0].code, 'VC-AI-COMPILER-ARTIFACT-UNSUPPORTED');

  const invalidSelection = await compileKotlin({
    source: validSource,
    artifactIds: 'viewcompose-ui-foundation',
  }, common);
  assert.equal(invalidSelection.status, 'invalid');
  assert.equal(invalidSelection.diagnostics[0].code, 'VC-AI-COMPILER-SELECTION-INVALID');
  assert.equal(executions, 0);
});

test('rejects unsafe or unbounded compiler inputs before process execution', async () => {
  let executions = 0;
  const options = {
    ...fixedExecution,
    javaFeature: 21,
    javaHome: '/fixed/jdk-21',
    runCompiler: async (plan) => {
      executions += 1;
      return successfulCompiler(plan);
    },
  };
  const empty = await compileKotlin({source: ''}, options);
  assert.equal(empty.status, 'limited');
  assert.equal(empty.diagnostics[0].code, 'VC-AI-INPUT-LIMIT');

  const traversingPath = await compileKotlin({
    source: validSource,
    path: '../Snippet.kt',
  }, options);
  assert.equal(traversingPath.status, 'invalid');
  assert.equal(traversingPath.diagnostics[0].code, 'VC-AI-INPUT-PATH-INVALID');
  assert.equal(executions, 0);
});

test('rejects symbolic links in content-addressed class output', async () => {
  const cacheRoot = await mkdtemp(join(tmpdir(), 'viewcompose-ai-compiler-symlink-'));
  try {
    const result = await compileKotlin({source: validSource}, {
      ...fixedExecution,
      cacheRoot,
      javaFeature: 21,
      javaHome: '/fixed/jdk-21',
      runCompiler: async (plan) => {
        await mkdir(plan.classesDirectory, {recursive: true});
        await symlink('/dev/null', resolve(plan.classesDirectory, 'Unsafe.class'));
        return {
          exitCode: 0,
          signal: null,
          output: '',
          truncated: false,
          timedOut: false,
          cancelled: false,
          spawnError: null,
        };
      },
    });
    assert.equal(result.status, 'failed');
    assert.equal(result.diagnostics[0].code, 'VC-AI-COMPILER-OUTPUT-INVALID');
  } finally {
    await rm(cacheRoot, {recursive: true, force: true});
  }
});

test('normalizes source compiler errors without exposing the request directory', async () => {
  const cacheRoot = await mkdtemp(join(tmpdir(), 'viewcompose-ai-compiler-error-'));
  try {
    const result = await compileKotlin({
      source: 'package example\nfun example() { Imaginary() }\n',
      path: 'src/main/java/example/Example.kt',
    }, {
      ...fixedExecution,
      cacheRoot,
      javaFeature: 21,
      javaHome: '/fixed/jdk-21',
      runCompiler: async (plan) => ({
        exitCode: 1,
        signal: null,
        output: `e: file://${plan.inputPath}:2:17 Unresolved reference 'Imaginary'.\n`,
        truncated: false,
        timedOut: false,
        cancelled: false,
        spawnError: null,
      }),
    });
    assert.equal(result.status, 'invalid');
    assert.equal(result.evidence.level, 'static');
    assert.equal(result.diagnostics[0].code, 'VC-AI-KOTLIN-COMPILER');
    assert.equal(result.diagnostics[0].source.path, 'src/main/java/example/Example.kt');
    assert.equal(result.diagnostics[0].source.startLine, 2);
    assert.equal(JSON.stringify(result).includes(cacheRoot), false);
  } finally {
    await rm(cacheRoot, {recursive: true, force: true});
  }
});

test('returns stable timeout and cancellation outcomes', async () => {
  const cacheRoot = await mkdtemp(join(tmpdir(), 'viewcompose-ai-compiler-limits-'));
  try {
    const timedOut = await compileKotlin({source: validSource}, {
      ...fixedExecution,
      cacheRoot,
      javaFeature: 21,
      javaHome: '/fixed/jdk-21',
      runCompiler: async () => ({
        exitCode: null,
        signal: 'SIGTERM',
        output: '',
        truncated: false,
        timedOut: true,
        cancelled: false,
        spawnError: null,
      }),
    });
    assert.equal(timedOut.status, 'limited');
    assert.equal(timedOut.diagnostics[0].code, 'VC-AI-COMPILER-TIMEOUT');

    const cancelled = await compileKotlin({source: `${validSource}\n`}, {
      ...fixedExecution,
      cacheRoot,
      javaFeature: 21,
      javaHome: '/fixed/jdk-21',
      runCompiler: async () => ({
        exitCode: null,
        signal: 'SIGTERM',
        output: '',
        truncated: false,
        timedOut: false,
        cancelled: true,
        spawnError: null,
      }),
    });
    assert.equal(cancelled.status, 'cancelled');
    assert.equal(cancelled.diagnostics[0].code, 'VC-AI-COMPILER-CANCELLED');
  } finally {
    await rm(cacheRoot, {recursive: true, force: true});
  }
});

test('the process boundary enforces timeout, cancellation, and captured-output limits', async () => {
  const basePlan = {executable: process.execPath, cwd: process.cwd()};
  const timedOut = await executeCompilerProcess({
    ...basePlan,
    args: ['-e', 'setInterval(() => {}, 1000)'],
  }, {timeoutMs: 25, maxOutputBytes: 1024});
  assert.equal(timedOut.timedOut, true);

  const controller = new AbortController();
  setTimeout(() => controller.abort(), 25);
  const cancelled = await executeCompilerProcess({
    ...basePlan,
    args: ['-e', 'setInterval(() => {}, 1000)'],
  }, {timeoutMs: 1000, maxOutputBytes: 1024, signal: controller.signal});
  assert.equal(cancelled.cancelled, true);

  const outputLimited = await executeCompilerProcess({
    ...basePlan,
    args: ['-e', "process.stdout.write('x'.repeat(4096)); setInterval(() => {}, 1000)"],
  }, {timeoutMs: 1000, maxOutputBytes: 128});
  assert.equal(outputLimited.truncated, true);
  assert.equal(Buffer.byteLength(outputLimited.output), 128);
});

test('identical in-flight content-addressed requests do not compile concurrently', async () => {
  const cacheRoot = await mkdtemp(join(tmpdir(), 'viewcompose-ai-compiler-lock-'));
  let signalStarted;
  let releaseCompiler;
  const started = new Promise((resolvePromise) => {
    signalStarted = resolvePromise;
  });
  const release = new Promise((resolvePromise) => {
    releaseCompiler = resolvePromise;
  });
  const options = {
    ...fixedExecution,
    cacheRoot,
    javaFeature: 21,
    javaHome: '/fixed/jdk-21',
    runCompiler: async (plan) => {
      signalStarted();
      await release;
      return successfulCompiler(plan);
    },
  };
  try {
    const firstPromise = compileKotlin({source: validSource}, options);
    await started;
    const duplicate = await compileKotlin({source: validSource}, options);
    assert.equal(duplicate.status, 'limited');
    assert.equal(duplicate.diagnostics[0].code, 'VC-AI-COMPILER-BUSY');
    releaseCompiler();
    const first = await firstPromise;
    assert.equal(first.status, 'success');
  } finally {
    releaseCompiler?.();
    await rm(cacheRoot, {recursive: true, force: true});
  }
});
