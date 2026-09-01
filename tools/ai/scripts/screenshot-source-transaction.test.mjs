import assert from 'node:assert/strict';
import {mkdir, mkdtemp, readFile, rename, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import test from 'node:test';
import {
  prepareScreenshotSourceApplication,
} from './screenshot-source-application-preparer.mjs';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {fingerprintSourceBytes} from './screenshot-source-edit.mjs';
import {
  applyPreparedSourceApplication,
  recoverPreparedSourceApplication,
  rollbackPreparedSourceApplication,
  storePreparedSourceApplication,
} from './screenshot-source-transaction.mjs';

const goldenPath = new URL(
  '../evaluation/fixtures/visual/screenshot-generation/wireframe.generated.kt',
  import.meta.url,
);

async function preparedFixture(root, relativePath, source) {
  const designIr = {
    roots: [{
      id: 'wireframe-title',
      properties: [{name: 'text', value: {kind: 'literal', value: 'Hello'}}],
      children: [],
    }],
  };
  const resolutionResult = {
    designIr,
    designIrFingerprint: fingerprintRepairValue(designIr),
    resultFingerprint: 'a'.repeat(64),
  };
  const patch = {
    schemaVersion: 1,
    operations: [{
      op: 'replace-field',
      nodeId: 'wireframe-title',
      collection: 'properties',
      name: 'text',
      value: {kind: 'literal', value: 'Welcome'},
    }],
    changeFingerprint: 'b'.repeat(64),
  };
  const repaired = source.replace('text = "Hello"', 'text = "Welcome"');
  return prepareScreenshotSourceApplication({
    projectRoot: root,
    relativePath,
    baselineEvidence: {evidenceFingerprint: 'c'.repeat(64)},
    candidateEvidence: {
      evidenceFingerprint: 'd'.repeat(64),
      lineage: {candidateDesignIrFingerprint: resolutionResult.designIrFingerprint},
    },
    authorization: {},
    resolutionResult,
    generationRequest: {mode: 'generate'},
    previewBindings: [],
    pixelReference: {},
  }, {
    boundProjectRoot: root,
    frameworkProfileFingerprint: 'e'.repeat(64),
    now: () => Date.now(),
    nonce: () => '0123456789abcdef0123456789abcdef',
    propose: async () => ({status: 'proposed', patch, proposalFingerprint: 'f'.repeat(64)}),
    validateAuthorization: async () => ({status: 'validated', validationFingerprint: '1'.repeat(64)}),
    applyPatch: async () => {
      const next = structuredClone(designIr);
      next.roots[0].properties[0].value.value = 'Welcome';
      return {
        designIr: next,
        designIrFingerprint: fingerprintRepairValue(next),
        outputFingerprint: '2'.repeat(64),
      };
    },
    evaluate: async () => ({
      evaluation: {gates: Array.from({length: 6}, (_, index) => ({name: `${index}`, status: 'passed'}))},
      evidence: {evidenceFingerprint: '3'.repeat(64)},
    }),
    generate: async ({resolutionResult: value}) => {
      const kotlin = value.designIr.roots[0].properties[0].value.value === 'Hello' ? source : repaired;
      return {status: 'success', kotlin, outputFingerprint: fingerprintSourceBytes(Buffer.from(kotlin))};
    },
  });
}

async function fixture(run) {
  const root = await mkdtemp(join(tmpdir(), 'viewcompose-source-transaction-'));
  const stateRoot = await mkdtemp(join(tmpdir(), 'viewcompose-source-state-'));
  const relativePath = 'app/src/main/java/example/LoginScreen.kt';
  const target = resolve(root, relativePath);
  const golden = await readFile(goldenPath, 'utf8');
  const source = golden.replace('text = "Welcome"', 'text = "Hello"');
  await mkdir(resolve(target, '..'), {recursive: true});
  await writeFile(target, source, {mode: 0o600});
  try {
    const bundle = await preparedFixture(root, relativePath, source);
    await storePreparedSourceApplication(bundle, {stateRoot, projectRoot: root});
    await run({root, stateRoot, relativePath, target, source, bundle});
  } finally {
    await rm(root, {recursive: true, force: true});
    await rm(stateRoot, {recursive: true, force: true});
  }
}

async function localReplace({projectRoot, relativePath, expectedSha256, candidatePath, candidateSha256, temporaryName}) {
  const target = resolve(projectRoot, relativePath);
  const current = await readFile(target);
  assert.equal(fingerprintSourceBytes(current), expectedSha256);
  const candidate = await readFile(candidatePath);
  assert.equal(fingerprintSourceBytes(candidate), candidateSha256);
  const temporary = resolve(target, '..', temporaryName);
  await writeFile(temporary, candidate, {flag: 'wx', mode: 0o600});
  await rename(temporary, target);
  return {status: 'committed', sha256: candidateSha256, fileKey: 'test'};
}

function verifiedEvidence(source) {
  const evidence = {
    status: 'verified',
    sourceSha256: fingerprintSourceBytes(source),
    static: {status: 'passed', passed: 1, total: 1, evidenceFingerprint: '4'.repeat(64)},
    compilation: {status: 'passed', passed: 1, total: 1, evidenceFingerprint: '5'.repeat(64)},
    render: {status: 'passed', passed: 1, total: 1, evidenceFingerprint: '6'.repeat(64)},
    semanticGeometry: {status: 'passed', passed: 8, total: 8, evidenceFingerprint: '7'.repeat(64)},
    eligiblePixels: {status: 'passed', passed: 100, total: 100, evidenceFingerprint: '8'.repeat(64)},
  };
  evidence.evidenceFingerprint = fingerprintRepairValue(evidence);
  return evidence;
}

test('applies one attended candidate, persists evidence, and explicitly rolls back', async () => {
  await fixture(async ({root, stateRoot, target, source, bundle}) => {
    const confirmations = [];
    const confirm = async (request) => {
      confirmations.push(request);
      return true;
    };
    const applied = await applyPreparedSourceApplication({
      requestFingerprint: bundle.request.requestFingerprint,
      projectRoot: root,
    }, {
      stateRoot,
      confirm,
      replaceSource: localReplace,
      verify: async (_bundle, bytes) => verifiedEvidence(bytes),
    });
    assert.equal(applied.status, 'applied-verified');
    assert.match((await readFile(target, 'utf8')), /text = "Welcome"/u);
    assert.deepEqual(await applyPreparedSourceApplication({
      requestFingerprint: bundle.request.requestFingerprint,
      projectRoot: root,
    }, {stateRoot, confirm: async () => assert.fail('replay must not ask again')}), applied);
    const rolledBack = await rollbackPreparedSourceApplication({
      requestFingerprint: bundle.request.requestFingerprint,
      projectRoot: root,
    }, {stateRoot, confirm, replaceSource: localReplace});
    assert.equal(rolledBack.status, 'rolled-back');
    assert.equal(await readFile(target, 'utf8'), source);
    assert.deepEqual(await rollbackPreparedSourceApplication({
      requestFingerprint: bundle.request.requestFingerprint,
      projectRoot: root,
    }, {stateRoot, confirm: async () => assert.fail('rollback replay must not ask again')}), rolledBack);
    assert.deepEqual(confirmations.map((item) => item.operation), ['apply', 'rollback']);
  });
});

test('leaves a failed validation candidate committed and refuses rollback after a user edit', async () => {
  await fixture(async ({root, stateRoot, target, bundle}) => {
    const failed = await applyPreparedSourceApplication({
      requestFingerprint: bundle.request.requestFingerprint,
      projectRoot: root,
    }, {
      stateRoot,
      confirm: async () => true,
      replaceSource: localReplace,
      verify: async (_bundle, bytes) => ({...verifiedEvidence(bytes), status: 'failed'}),
    });
    assert.equal(failed.status, 'applied-validation-failed');
    await writeFile(target, 'fun userEdited() = Unit\n');
    await assert.rejects(rollbackPreparedSourceApplication({
      requestFingerprint: bundle.request.requestFingerprint,
      projectRoot: root,
    }, {stateRoot, confirm: async () => true, replaceSource: localReplace}), {
      code: 'VC-AI-SOURCE-APPLICATION-PREIMAGE-DRIFT',
    });
    assert.equal(await readFile(target, 'utf8'), 'fun userEdited() = Unit\n');
  });
});

test('rejects missing attended confirmation without creating recovery source bytes', async () => {
  await fixture(async ({root, stateRoot, bundle}) => {
    await assert.rejects(applyPreparedSourceApplication({
      requestFingerprint: bundle.request.requestFingerprint,
      projectRoot: root,
    }, {stateRoot, confirm: async () => false, replaceSource: localReplace}), {
      code: 'VC-AI-SOURCE-APPLICATION-AUTHORIZATION-DENIED',
    });
  });
});

test('recovers a crash after atomic replacement without rolling source back', async () => {
  await fixture(async ({root, stateRoot, target, bundle}) => {
    await assert.rejects(applyPreparedSourceApplication({
      requestFingerprint: bundle.request.requestFingerprint,
      projectRoot: root,
    }, {
      stateRoot,
      confirm: async () => true,
      replaceSource: localReplace,
      verify: async (_bundle, bytes) => verifiedEvidence(bytes),
      failpoint: async (name) => {
        if (name === 'after-atomic-replace') throw new Error('simulated process death');
      },
    }), /simulated process death/u);
    assert.match(await readFile(target, 'utf8'), /text = "Welcome"/u);
    const recovered = await recoverPreparedSourceApplication({
      requestFingerprint: bundle.request.requestFingerprint,
      projectRoot: root,
    }, {
      stateRoot,
      verify: async (_bundle, bytes) => verifiedEvidence(bytes),
    });
    assert.equal(recovered.status, 'applied-verified');
    assert.match(await readFile(target, 'utf8'), /text = "Welcome"/u);
    assert.deepEqual(await recoverPreparedSourceApplication({
      requestFingerprint: bundle.request.requestFingerprint,
      projectRoot: root,
    }, {stateRoot}), recovered);
  });
});

test('recovers a crash before replacement as not applied', async () => {
  await fixture(async ({root, stateRoot, target, source, bundle}) => {
    await assert.rejects(applyPreparedSourceApplication({
      requestFingerprint: bundle.request.requestFingerprint,
      projectRoot: root,
    }, {
      stateRoot,
      confirm: async () => true,
      replaceSource: localReplace,
      failpoint: async (name) => {
        if (name === 'after-applying-journal-fsync') throw new Error('simulated process death');
      },
    }), /simulated process death/u);
    const recovered = await recoverPreparedSourceApplication({
      requestFingerprint: bundle.request.requestFingerprint,
      projectRoot: root,
    }, {stateRoot});
    assert.equal(recovered.status, 'not-applied');
    assert.equal(await readFile(target, 'utf8'), source);
  });
});

test('rejects a concurrent apply while the first transaction owns the lock', async () => {
  await fixture(async ({root, stateRoot, bundle}) => {
    let enteredReplace;
    let releaseReplace;
    const entered = new Promise((resolveEntered) => { enteredReplace = resolveEntered; });
    const release = new Promise((resolveRelease) => { releaseReplace = resolveRelease; });
    const delayedReplace = async (...args) => {
      enteredReplace();
      await release;
      return localReplace(...args);
    };
    const first = applyPreparedSourceApplication({
      requestFingerprint: bundle.request.requestFingerprint,
      projectRoot: root,
    }, {
      stateRoot,
      confirm: async () => true,
      replaceSource: delayedReplace,
      verify: async (_bundle, bytes) => verifiedEvidence(bytes),
    });
    await entered;
    await assert.rejects(applyPreparedSourceApplication({
      requestFingerprint: bundle.request.requestFingerprint,
      projectRoot: root,
    }, {
      stateRoot,
      confirm: async () => true,
      replaceSource: localReplace,
    }), {code: 'VC-AI-SOURCE-APPLICATION-CONCURRENT'});
    releaseReplace();
    assert.equal((await first).status, 'applied-verified');
  });
});
