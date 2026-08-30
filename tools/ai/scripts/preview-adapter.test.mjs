import assert from 'node:assert/strict';
import {mkdir, mkdtemp, realpath, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, join, resolve} from 'node:path';
import test from 'node:test';
import {
  readAcceptedPreviewSnapshot,
  RENDER_LANE,
  renderPreview,
} from './preview-adapter.mjs';

const buildFingerprint = 'a'.repeat(64);
const previewId = 'counterpreviewkt-counterpreview-2942afc5dcb6';
const lightVariantId = 'counter-light-abee9c74';

function successfulProcess() {
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

function png(width = 1079, height = 2339) {
  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(25);
  ihdr.writeUInt32BE(13, 0);
  ihdr.write('IHDR', 4, 'ascii');
  ihdr.writeUInt32BE(width, 8);
  ihdr.writeUInt32BE(height, 12);
  ihdr[16] = 8;
  ihdr[17] = 6;
  const idat = Buffer.alloc(12);
  idat.write('IDAT', 4, 'ascii');
  const iend = Buffer.alloc(12);
  iend.write('IEND', 4, 'ascii');
  return Buffer.concat([signature, ihdr, idat, iend]);
}

async function fixtureRepository() {
  const repository = await realpath(await mkdtemp(join(tmpdir(), 'viewcompose-ai-preview-')));
  const source = resolve(
    repository,
    'samples/counter/src/debug/java/com/viewcompose/samples/counter/CounterPreview.kt',
  );
  const artifactRoot = resolve(repository, 'samples/counter/build/viewcompose-preview/debug');
  await mkdir(dirname(source), {recursive: true});
  await mkdir(artifactRoot, {recursive: true});
  await writeFile(source, 'fun CounterPreview() = Unit\n');
  const configuration = {
    widthDp: 411,
    heightDp: -1,
    density: 2.625,
    fontScale: 1,
    localeTags: ['en-US'],
    layoutDirection: 'Ltr',
    theme: 'Light',
  };
  const catalog = {
    protocolVersion: 1,
    modulePath: ':samples:counter',
    buildVariant: 'debug',
    buildFingerprint,
    descriptors: [{
      id: previewId,
      displayName: 'CounterPreview',
      group: 'Samples/Getting started',
      entryPoint: {
        ownerClassName: 'com.viewcompose.samples.counter.CounterPreviewKt',
        methodName: 'CounterPreview',
      },
      variants: [{id: lightVariantId, displayName: 'Counter · Light', configuration}],
      sourceLocation: {filePath: source, line: 23, column: 1, symbolName: 'CounterPreview'},
    }],
    diagnostics: [],
  };
  await writeFile(
    resolve(artifactRoot, 'descriptors.json'),
    `${JSON.stringify(catalog, null, 2)}\n`,
  );
  return {repository, artifactRoot, catalog, configuration};
}

async function writeSuccessfulRender(fixture) {
  const directory = resolve(
    fixture.artifactRoot,
    'render-cache',
    buildFingerprint,
    previewId,
    lightVariantId,
  );
  const imagePath = resolve(directory, 'preview.png');
  const renderTreePath = resolve(directory, 'render-tree.json');
  const responsePath = resolve(directory, 'response.json');
  await mkdir(directory, {recursive: true});
  await writeFile(imagePath, png());
  await writeFile(renderTreePath, '{"stats":{},"tree":[]}\n');
  await writeFile(responsePath, `${JSON.stringify({
    protocolVersion: 1,
    requestId: `${buildFingerprint}:${'b'.repeat(64)}:${previewId}:${lightVariantId}`,
    previewId,
    variantId: lightVariantId,
    status: 'Success',
    artifacts: {imagePath, renderTreePath},
    diagnostics: [],
    durationMillis: 220,
    phaseTimings: [{phase: 'image-export', durationMillis: 61}],
  }, null, 2)}\n`);
  return {directory, responsePath, imagePath, renderTreePath};
}

test('renders only the fixed target and returns contained protocol evidence', async () => {
  const fixture = await fixtureRepository();
  const plans = [];
  try {
    const result = await renderPreview({}, {
      repository: fixture.repository,
      javaFeature: 21,
      javaHome: '/fixed/jdk-21',
      runProcess: async (plan) => {
        plans.push(plan);
        if (plan.args.includes(':samples:counter:renderDebugViewComposePreview')) {
          await writeSuccessfulRender(fixture);
        }
        return successfulProcess();
      },
    });
    assert.equal(result.status, 'success');
    assert.equal(result.evidence.level, 'rendered');
    assert.equal(result.evidence.cache, 'miss');
    assert.equal(result.evidence.renderLane, RENDER_LANE);
    assert.match(result.evidence.outputFingerprint, /^[a-f0-9]{64}$/u);
    assert.equal(result.data.image.widthPx, 1079);
    assert.equal(result.data.image.heightPx, 2339);
    assert.equal(result.data.renderTree.bytes, 23);
    assert.equal(result.data.source.path.endsWith('CounterPreview.kt'), true);
    assert.equal(JSON.stringify(result).includes(fixture.repository), false);
    assert.equal(plans.length, 2);
    assert.ok(plans.every((plan) => !plan.args.includes('--offline')));
    assert.ok(plans.every((plan) => plan.args.includes('-PviewComposeAiPreviewRequestCacheRoot=' +
      resolve(fixture.repository, 'preview/requests'))));
  } finally {
    await rm(fixture.repository, {recursive: true, force: true});
  }
});

test('accepts a cache hit only after revalidating image and render-tree bytes', async () => {
  const fixture = await fixtureRepository();
  let executions = 0;
  try {
    const artifacts = await writeSuccessfulRender(fixture);
    const first = await renderPreview({}, {
      repository: fixture.repository,
      javaFeature: 21,
      javaHome: '/fixed/jdk-21',
      runProcess: async () => {
        executions += 1;
        return successfulProcess();
      },
    });
    assert.equal(first.status, 'success');
    assert.equal(first.evidence.cache, 'hit');
    assert.equal(executions, 1);

    await writeFile(artifacts.imagePath, 'not-a-png');
    const poisoned = await renderPreview({}, {
      repository: fixture.repository,
      javaFeature: 21,
      javaHome: '/fixed/jdk-21',
      runProcess: async () => {
        executions += 1;
        return successfulProcess();
      },
    });
    assert.equal(poisoned.status, 'failed');
    assert.equal(poisoned.diagnostics[0].code, 'VC-AI-RENDER-CACHE-POISONED');
    assert.equal(executions, 2);
  } finally {
    await rm(fixture.repository, {recursive: true, force: true});
  }
});

test('reopens only the exact content-addressed render tree and rejects later mutation', async () => {
  const fixture = await fixtureRepository();
  try {
    const artifacts = await writeSuccessfulRender(fixture);
    const rendered = await renderPreview({}, {
      repository: fixture.repository,
      javaFeature: 21,
      javaHome: '/fixed/jdk-21',
      runProcess: async () => successfulProcess(),
    });
    const snapshot = await readAcceptedPreviewSnapshot(rendered, {
      repository: fixture.repository,
    });
    assert.deepEqual(snapshot.tree, []);

    await writeFile(artifacts.renderTreePath, '{"stats":{},"tree":[],"warnings":["changed"]}\n');
    await assert.rejects(
      readAcceptedPreviewSnapshot(rendered, {repository: fixture.repository}),
      /RENDER_EVIDENCE_INVALID/u,
    );
  } finally {
    await rm(fixture.repository, {recursive: true, force: true});
  }
});

test('rejects unsupported targets, malformed configurations, and lane mismatches before Gradle', async () => {
  let executions = 0;
  const options = {
    javaFeature: 21,
    javaHome: '/fixed/jdk-21',
    runProcess: async () => {
      executions += 1;
      return successfulProcess();
    },
  };
  const unsupported = await renderPreview({targetId: 'application.arbitrary.Screen'}, options);
  assert.equal(unsupported.status, 'unsupported');
  assert.equal(unsupported.diagnostics[0].code, 'VC-AI-PREVIEW-TARGET-UNSUPPORTED');

  const inherited = await renderPreview({targetId: '__proto__'}, options);
  assert.equal(inherited.status, 'unsupported');

  const malformed = await renderPreview({configuration: {widthDp: 100_000}}, options);
  assert.equal(malformed.status, 'invalid');
  assert.equal(malformed.diagnostics[0].code, 'VC-AI-PREVIEW-CONFIGURATION-INVALID');

  const lane = await renderPreview({}, {...options, javaFeature: 11});
  assert.equal(lane.status, 'unsupported');
  assert.equal(lane.diagnostics[0].code, 'VC-AI-PREVIEW-LANE-MISMATCH');
  assert.equal(executions, 0);
});

test('rejects source escape and symbolic-link render artifacts', async () => {
  const fixture = await fixtureRepository();
  try {
    const catalogPath = resolve(fixture.artifactRoot, 'descriptors.json');
    const escapedCatalog = structuredClone(fixture.catalog);
    escapedCatalog.descriptors[0].sourceLocation.filePath = '/etc/passwd';
    await writeFile(catalogPath, JSON.stringify(escapedCatalog));
    const escaped = await renderPreview({}, {
      repository: fixture.repository,
      javaFeature: 21,
      javaHome: '/fixed/jdk-21',
      runProcess: async () => successfulProcess(),
    });
    assert.equal(escaped.status, 'failed');
    assert.equal(escaped.diagnostics[0].code, 'VC-AI-PREVIEW-CATALOG-INVALID');

    await writeFile(catalogPath, JSON.stringify(fixture.catalog));
    const artifacts = await writeSuccessfulRender(fixture);
    await rm(artifacts.imagePath);
    await symlink('/dev/null', artifacts.imagePath);
    const symlinked = await renderPreview({}, {
      repository: fixture.repository,
      javaFeature: 21,
      javaHome: '/fixed/jdk-21',
      runProcess: async () => successfulProcess(),
    });
    assert.equal(symlinked.status, 'failed');
    assert.equal(symlinked.diagnostics[0].code, 'VC-AI-RENDER-CACHE-POISONED');
  } finally {
    await rm(fixture.repository, {recursive: true, force: true});
  }
});

test('normalizes cancellation, timeout, and structured worker failure', async () => {
  const fixture = await fixtureRepository();
  try {
    const cancelled = await renderPreview({}, {
      repository: fixture.repository,
      javaFeature: 21,
      javaHome: '/fixed/jdk-21',
      runProcess: async () => ({...successfulProcess(), exitCode: null, cancelled: true}),
    });
    assert.equal(cancelled.status, 'cancelled');
    assert.equal(cancelled.diagnostics[0].code, 'VC-AI-PREVIEW-CANCELLED');

    const timedOut = await renderPreview({}, {
      repository: fixture.repository,
      javaFeature: 21,
      javaHome: '/fixed/jdk-21',
      runProcess: async () => ({...successfulProcess(), exitCode: null, timedOut: true}),
    });
    assert.equal(timedOut.status, 'limited');
    assert.equal(timedOut.diagnostics[0].code, 'VC-AI-PREVIEW-TIMEOUT');

    let executions = 0;
    const failed = await renderPreview({}, {
      repository: fixture.repository,
      javaFeature: 21,
      javaHome: '/fixed/jdk-21',
      runProcess: async (plan) => {
        executions += 1;
        if (plan.args.includes(':samples:counter:renderDebugViewComposePreview')) {
          const artifacts = await writeSuccessfulRender(fixture);
          await writeFile(artifacts.responsePath, JSON.stringify({
            protocolVersion: 1,
            requestId: `${buildFingerprint}:${'b'.repeat(64)}:${previewId}:${lightVariantId}`,
            previewId,
            variantId: lightVariantId,
            status: 'RenderFailure',
            diagnostics: [{severity: 'Error', message: 'Mount failed', phase: 'mount-layout'}],
          }));
          return {...successfulProcess(), exitCode: 1};
        }
        return successfulProcess();
      },
    });
    assert.equal(failed.status, 'failed');
    assert.equal(failed.evidence.level, 'compiled');
    assert.equal(failed.diagnostics[0].code, 'VC-AI-PREVIEW-DIAGNOSTIC');
    assert.equal(executions, 2);
  } finally {
    await rm(fixture.repository, {recursive: true, force: true});
  }
});
