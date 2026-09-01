import assert from 'node:assert/strict';
import {mkdir, mkdtemp, readFile, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import test from 'node:test';
import {fingerprintRepairValue} from './repair-orchestrator.mjs';
import {
  prepareScreenshotSourceApplication,
} from './screenshot-source-application-preparer.mjs';
import {fingerprintSourceBytes} from './screenshot-source-edit.mjs';

const goldenPath = new URL(
  '../evaluation/fixtures/visual/screenshot-generation/wireframe.generated.kt',
  import.meta.url,
);
const profile = 'a'.repeat(64);

function fixture(source) {
  const currentDesignIr = {
    roots: [{
      id: 'wireframe-title',
      properties: [{name: 'text', value: {kind: 'literal', value: 'Hello'}}],
      children: [],
    }],
  };
  const currentFingerprint = fingerprintRepairValue(currentDesignIr);
  const resolutionResult = {
    designIr: currentDesignIr,
    designIrFingerprint: currentFingerprint,
    resultFingerprint: 'b'.repeat(64),
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
    changeFingerprint: 'c'.repeat(64),
  };
  const proposal = {
    status: 'proposed',
    patch,
    proposalFingerprint: 'd'.repeat(64),
  };
  const candidateEvidence = {
    evidenceFingerprint: 'e'.repeat(64),
    lineage: {candidateDesignIrFingerprint: currentFingerprint},
  };
  const baselineEvidence = {evidenceFingerprint: 'f'.repeat(64)};
  const repairedSource = source.replace('text = "Hello"', 'text = "Welcome"');
  const dependencies = {
    boundProjectRoot: undefined,
    frameworkProfileFingerprint: profile,
    now: () => Date.parse('2026-09-01T12:00:00.000Z'),
    nonce: () => '0123456789abcdef0123456789abcdef',
    propose: async () => proposal,
    validateAuthorization: async () => ({
      status: 'validated',
      validationFingerprint: '1'.repeat(64),
    }),
    applyPatch: async () => {
      const designIr = structuredClone(currentDesignIr);
      designIr.roots[0].properties[0].value.value = 'Welcome';
      return {
        designIr,
        designIrFingerprint: fingerprintRepairValue(designIr),
        outputFingerprint: '2'.repeat(64),
      };
    },
    evaluate: async () => ({
      evaluation: {gates: Array.from({length: 6}, (_, index) => ({name: `${index}`, status: 'passed'}))},
      evidence: {evidenceFingerprint: '3'.repeat(64)},
    }),
    generate: async ({resolutionResult: value}) => {
      const text = value.designIr.roots[0].properties[0].value.value;
      const kotlin = text === 'Hello' ? source : repairedSource;
      return {
        status: 'success',
        kotlin,
        outputFingerprint: fingerprintSourceBytes(Buffer.from(kotlin)),
      };
    },
  };
  return {resolutionResult, baselineEvidence, candidateEvidence, dependencies};
}

async function withProject(run) {
  const root = await mkdtemp(join(tmpdir(), 'viewcompose-source-prepare-'));
  const relativePath = 'app/src/main/java/example/LoginScreen.kt';
  const target = resolve(root, relativePath);
  await mkdir(resolve(target, '..'), {recursive: true});
  const golden = await readFile(goldenPath, 'utf8');
  const source = golden.replace('text = "Welcome"', 'text = "Hello"');
  await writeFile(target, source);
  try {
    await run({root, relativePath, target, source});
  } finally {
    await rm(root, {recursive: true, force: true});
  }
}

test('prepares one inert request bound to exact source and six passed gates', async () => {
  await withProject(async ({root, relativePath, source}) => {
    const input = fixture(source);
    const result = await prepareScreenshotSourceApplication({
      projectRoot: root,
      relativePath,
      baselineEvidence: input.baselineEvidence,
      candidateEvidence: input.candidateEvidence,
      authorization: {},
      resolutionResult: input.resolutionResult,
      generationRequest: {mode: 'generate'},
      previewBindings: [],
      pixelReference: {},
    }, {...input.dependencies, boundProjectRoot: root});
    assert.equal(result.request.status, 'prepared');
    assert.equal(result.request.project.relativePath, relativePath);
    assert.equal(result.request.policy.mcpSourceWrite, false);
    assert.equal(result.request.authorization.bypassAllowed, false);
    assert.match(result.request.authorization.confirmationSuffix, /^[A-F0-9]{12}$/u);
    assert.equal(result.request.edit.nodeId, 'wireframe-title');
    assert.equal(result.request.edit.propertyName, 'text');
  });
});

test('rejects preimage drift and a project-root mismatch before preparation', async () => {
  await withProject(async ({root, relativePath, source}) => {
    const input = fixture(source.replace('text = "Hello"', 'text = "Changed"'));
    await assert.rejects(prepareScreenshotSourceApplication({
      projectRoot: root,
      relativePath,
      baselineEvidence: input.baselineEvidence,
      candidateEvidence: input.candidateEvidence,
      authorization: {},
      resolutionResult: input.resolutionResult,
      generationRequest: {mode: 'generate'},
      previewBindings: [],
      pixelReference: {},
    }, {...input.dependencies, boundProjectRoot: root}), {
      code: 'VC-AI-SOURCE-APPLICATION-PREIMAGE-DRIFT',
    });
    const other = await mkdtemp(join(tmpdir(), 'viewcompose-source-other-'));
    try {
      await assert.rejects(prepareScreenshotSourceApplication({
        projectRoot: root,
        relativePath,
      }, {...input.dependencies, boundProjectRoot: other}), {
        code: 'VC-AI-SOURCE-APPLICATION-ROOT-DRIFT',
      });
    } finally {
      await rm(other, {recursive: true, force: true});
    }
  });
});

test('rejects a symbolic-link path before reading source', async () => {
  const root = await mkdtemp(join(tmpdir(), 'viewcompose-source-link-'));
  const outside = await mkdtemp(join(tmpdir(), 'viewcompose-source-link-target-'));
  try {
    await mkdir(resolve(root, 'app/src/main/java'), {recursive: true});
    await writeFile(resolve(outside, 'LoginScreen.kt'), 'fun unsafe() = Unit\n');
    await symlink(outside, resolve(root, 'app/src/main/java/example'));
    const input = fixture('fun unused() = Unit\n');
    await assert.rejects(prepareScreenshotSourceApplication({
      projectRoot: root,
      relativePath: 'app/src/main/java/example/LoginScreen.kt',
    }, {...input.dependencies, boundProjectRoot: root}), {
      code: 'VC-AI-SOURCE-APPLICATION-SYMLINK',
    });
  } finally {
    await rm(root, {recursive: true, force: true});
    await rm(outside, {recursive: true, force: true});
  }
});
