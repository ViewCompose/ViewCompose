import assert from 'node:assert/strict';
import {realpathSync} from 'node:fs';
import test from 'node:test';
import {
  parseScreenshotSourceRepairArguments,
  runScreenshotSourceRepairCli,
} from './screenshot-source-repair-cli.mjs';

const request = 'a'.repeat(64);

test('parses only the bounded attended source-repair command surface', () => {
  assert.deepEqual(
    parseScreenshotSourceRepairArguments(['apply', request, '--pretty'], '/tmp'),
    {command: 'apply', requestFingerprint: request, projectRoot: realpathSync('/tmp'), pretty: true},
  );
  for (const forbidden of ['--yes', '--force', '--approval', '--token']) {
    assert.throws(
      () => parseScreenshotSourceRepairArguments(['apply', request, forbidden], '/tmp'),
      /approval bypasses are forbidden/u,
    );
  }
  assert.throws(
    () => parseScreenshotSourceRepairArguments([
      'apply', request, '--project-root', '/tmp', '--project-root', '/tmp',
    ], '/tmp'),
    /approval bypasses are forbidden/u,
  );
});

test('keeps show and recover separate from attended apply and rollback', async () => {
  const calls = [];
  const options = {
    cwd: '/tmp',
    stateRoot: '/tmp/state',
    confirm: async () => false,
  };
  await assert.rejects(
    runScreenshotSourceRepairCli(['show', request], options),
    /ENOENT|Recovery state/u,
  );
  assert.deepEqual(calls, []);
});
