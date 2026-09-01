import assert from 'node:assert/strict';
import {mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import test from 'node:test';
import {detectJavaRuntime} from './tool-core.mjs';
import {fingerprintSourceBytes} from './screenshot-source-edit.mjs';
import {secureReplaceSource} from './screenshot-source-secure-backend.mjs';

const javaRuntime = detectJavaRuntime();

test('secure host atomically replaces one exact file and rejects a stale preimage', {
  skip: !javaRuntime || ![17, 21].includes(javaRuntime.feature),
}, async () => {
  const root = await mkdtemp(join(tmpdir(), 'viewcompose-secure-source-'));
  const state = await mkdtemp(join(tmpdir(), 'viewcompose-secure-state-'));
  const relativePath = 'app/src/main/java/example/LoginScreen.kt';
  const target = resolve(root, relativePath);
  const candidatePath = resolve(state, 'candidate.kt');
  const preimage = Buffer.from('fun screen() = "before"\n');
  const candidate = Buffer.from('fun screen() = "after"\n');
  try {
    await mkdir(resolve(target, '..'), {recursive: true});
    await writeFile(target, preimage, {mode: 0o600});
    await writeFile(candidatePath, candidate, {mode: 0o600});
    const result = await secureReplaceSource({
      projectRoot: root,
      relativePath,
      expectedSha256: fingerprintSourceBytes(preimage),
      candidatePath,
      candidateSha256: fingerprintSourceBytes(candidate),
      temporaryName: '.viewcompose-0123456789abcdef0123456789abcdef.tmp',
    }, {javaRuntime});
    assert.equal(result.status, 'committed');
    assert.deepEqual(await readFile(target), candidate);
    await assert.rejects(secureReplaceSource({
      projectRoot: root,
      relativePath,
      expectedSha256: fingerprintSourceBytes(preimage),
      candidatePath,
      candidateSha256: fingerprintSourceBytes(candidate),
      temporaryName: '.viewcompose-abcdef0123456789abcdef0123456789.tmp',
    }, {javaRuntime}), {code: 'VC-AI-SOURCE-APPLICATION-PREIMAGE-DRIFT'});
  } finally {
    await rm(root, {recursive: true, force: true});
    await rm(state, {recursive: true, force: true});
  }
});

test('maps an unavailable secure filesystem to one fail-closed diagnostic', async () => {
  await assert.rejects(secureReplaceSource({}, {javaRuntime: null}), {
    code: 'VC-AI-SOURCE-APPLICATION-FILESYSTEM-UNSUPPORTED',
  });
});
