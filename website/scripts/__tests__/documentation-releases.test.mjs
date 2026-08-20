import assert from 'node:assert/strict';
import test from 'node:test';
import {
  isStableRelease,
  parseDocumentationReleases,
  parseJavaProperties,
} from '../documentation-releases.mjs';
import {
  rewriteSnapshotLinks,
  versionedManualDocument,
} from '../generate-versioned-module-manuals.mjs';

const revision = '0123456789abcdef0123456789abcdef01234567';

test('parses immutable history and current publishing metadata', () => {
  const result = parseDocumentationReleases({
    historyContent: `
schema.version=1
release.count=1
release.0.version=0.1.0-alpha01
release.0.sourceRevision=${revision}
release.0.modules=viewcompose-runtime,viewcompose-ui-foundation
`,
    publishingContent: `
module.viewcompose-runtime.version=0.1.0-alpha01
module.viewcompose-runtime.sourceRevision=${revision}
module.viewcompose-ui-foundation.version=0.1.0-alpha01
module.viewcompose-ui-foundation.sourceRevision=${revision}
`,
  });

  assert.equal(result.entries.length, 2);
  assert.deepEqual(result.current.get('viewcompose-runtime'), {
    version: '0.1.0-alpha01',
    sourceRevision: revision,
  });
});

test('rejects a current publication that has no immutable history entry', () => {
  assert.throws(
    () =>
      parseDocumentationReleases({
        historyContent: `
schema.version=1
release.count=1
release.0.version=0.1.0-alpha01
release.0.sourceRevision=${revision}
release.0.modules=viewcompose-runtime
`,
        publishingContent: `
module.viewcompose-runtime.version=0.1.0-alpha02
module.viewcompose-runtime.sourceRevision=${revision}
`,
      }),
    /is missing from immutable documentation history/u,
  );
});

test('keeps retired history and permits an explicitly unpublished current artifact', () => {
  const result = parseDocumentationReleases({
    historyContent: `
schema.version=1
release.count=1
release.0.version=0.1.0-alpha01
release.0.sourceRevision=${revision}
release.0.modules=viewcompose-widget-core
`,
    publishingContent: `
release.unpublishedModules=viewcompose-ui-foundation
release.retiredModules=viewcompose-widget-core
module.viewcompose-ui-foundation.version=0.1.0-alpha01
module.viewcompose-ui-foundation.sourceRevision=${revision}
`,
  });

  assert.deepEqual([...result.unpublished], ['viewcompose-ui-foundation']);
  assert.deepEqual([...result.retired], ['viewcompose-widget-core']);
  assert.equal(result.entries[0].artifact, 'viewcompose-widget-core');
});

test('rejects retired artifacts that remain active publications', () => {
  assert.throws(
    () =>
      parseDocumentationReleases({
        historyContent: `
schema.version=1
release.count=1
release.0.version=0.1.0-alpha01
release.0.sourceRevision=${revision}
release.0.modules=viewcompose-runtime
`,
        publishingContent: `
release.retiredModules=viewcompose-runtime
module.viewcompose-runtime.version=0.1.0-alpha01
module.viewcompose-runtime.sourceRevision=${revision}
`,
      }),
    /Retired artifacts remain active/u,
  );
});

test('rejects duplicate properties and classifies stable aliases', () => {
  assert.throws(() => parseJavaProperties('key=one\nkey=two\n'), /Duplicate property/u);
  assert.equal(isStableRelease('1.0.0'), true);
  assert.equal(isStableRelease('1.0.0-alpha01'), false);
  assert.equal(isStableRelease('1.0.0-rc1'), false);
});

test('rewrites historical manuals to immutable API and public document routes', () => {
  const entries = [
    {
      order: 0,
      artifact: 'viewcompose-runtime',
      version: '0.1.0-alpha01',
      sourceRevision: revision,
    },
    {
      order: 0,
      artifact: 'viewcompose-widget-core',
      version: '0.1.0-alpha01',
      sourceRevision: revision,
    },
  ];
  const rewritten = rewriteSnapshotLinks(
    '[API](https://docs.viewcompose.com/api/viewcompose-runtime/current/) ' +
      '[Architecture](../../architecture/overview.md) ' +
      '[ADR](../../architecture/decisions/0009-development-tooling-isolation.md#activation) ' +
      '[Widget](../viewcompose-widget-core/README.md)',
    {
      artifact: 'viewcompose-runtime',
      order: 0,
      entries,
      sourcePath: 'docs/modules/viewcompose-runtime/README.md',
    },
  );
  assert.equal(
    rewritten,
    '[API](https://docs.viewcompose.com/api/viewcompose-runtime/0.1.0-alpha01/) ' +
      '[Architecture](/architecture/overview/) ' +
      '[ADR](/architecture/decisions/development-tooling-isolation/#activation) ' +
      '[Widget](/modules/viewcompose-widget-core/0.1.0-alpha01/)',
  );

  const document = versionedManualDocument(entries[0], '# Runtime\n\nBody.\n', entries);
  assert.match(document, /slug: \/modules\/viewcompose-runtime\/0\.1\.0-alpha01/u);
  assert.match(document, /Released documentation snapshot/u);
  assert.match(document, new RegExp(revision, 'u'));
  assert.match(document, /\[current module catalog\]\(\/modules\/\)/u);
});
