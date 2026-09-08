import assert from 'node:assert/strict';
import test from 'node:test';
import {executeBoundedProcess} from './bounded-process.mjs';

const plan = (code) => ({executable: process.execPath, args: ['-e', code], cwd: process.cwd()});
const options = {timeoutMs: 200, maxOutputBytes: 1024};
const descendant = `
  const {spawn} = require('node:child_process');
  const child = spawn(process.execPath, ['-e', 'setTimeout(() => {}, 3500)'],
    {stdio: ['ignore', 1, 2]});
  process.stdout.write(String(child.pid));
`;

test('timeout terminates descendants holding inherited pipes after the parent exits', async () => {
  const started = performance.now();
  const result = await executeBoundedProcess(plan(descendant + 'process.exit(0);'), options);
  assert.equal(result.timedOut, true);
  assert.ok(performance.now() - started < 3000, 'must not wait for the 3.5 second descendant');
  assert.match(result.output, /^\d+$/);
});

test('cancellation terminates the task tree and preserves captured output', async () => {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 200);
  try {
    const started = performance.now();
    const result = await executeBoundedProcess(plan(descendant + 'setInterval(() => {}, 1000);'),
      {...options, timeoutMs: 5000, signal: controller.signal});
    assert.equal(result.cancelled, true);
    assert.equal(result.timedOut, false);
    assert.ok(performance.now() - started < 3000);
    assert.match(result.output, /^\d+$/);
  } finally { clearTimeout(timer); }
});

test('output overflow terminates descendants and retains exactly the byte budget', async () => {
  const started = performance.now();
  const result = await executeBoundedProcess(
    plan(descendant + "process.stdout.write('x'.repeat(4096)); setInterval(() => {}, 1000);"),
    {...options, timeoutMs: 5000, maxOutputBytes: 64});
  assert.equal(result.truncated, true);
  assert.equal(Buffer.byteLength(result.output), 64);
  assert.ok(performance.now() - started < 3000);
});

test('an already aborted task never starts its executable', async () => {
  const controller = new AbortController();
  controller.abort();
  const result = await executeBoundedProcess({executable: '/missing/should-not-spawn', args: []},
    {...options, signal: controller.signal});
  assert.equal(result.cancelled, true);
  assert.equal(result.spawnError, null);
});

test('ordinary completion and spawn failure preserve the result contract', async () => {
  const success = await executeBoundedProcess(plan("process.stdout.write('ok')"), options);
  assert.equal(success.exitCode, 0);
  assert.equal(success.output, 'ok');
  assert.equal(success.timedOut, false);
  const failure = await executeBoundedProcess({executable: '/missing/bounded-process', args: []}, options);
  assert.equal(failure.spawnError.code, 'ENOENT');
  assert.equal(failure.timedOut, false);
});

test('ignored termination is force-killed within the final grace boundary',
  {skip: process.platform === 'win32'}, async () => {
    const started = performance.now();
    const result = await executeBoundedProcess(plan(
      "process.on('SIGTERM', () => {}); setInterval(() => {}, 1000);"), options);
    assert.equal(result.timedOut, true);
    assert.ok(performance.now() - started < 3000);
  });
