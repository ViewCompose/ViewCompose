import assert from 'node:assert/strict';
import test from 'node:test';
import stripGovernanceFrontMatter from '../../src/remark/stripGovernanceFrontMatter.ts';

test('removes governance and translation metadata but preserves presentation fields', () => {
  const frontMatter = {
    schema_version: 2,
    document_id: 'architecture.example',
    doc_type: 'architecture',
    owner: {kind: 'project', id: 'example'},
    version_lane: 'released',
    capability_ids: ['example.capability'],
    artifact_ids: ['viewcompose-example'],
    sample_ids: ['example.sample'],
    invariants: ['One invariant.'],
    evidence: ['One suite.'],
    translation_source: 'architecture/example.md',
    translation_source_hash: 'abc123',
    translation_status: 'current',
    title: 'Example',
    slug: '/architecture/example',
    sidebar_position: 3,
    draft: false,
  };

  stripGovernanceFrontMatter()(null, {data: {frontMatter}});

  assert.deepEqual(frontMatter, {
    title: 'Example',
    slug: '/architecture/example',
    sidebar_position: 3,
    draft: false,
  });
});

test('ignores files without object front matter', () => {
  const transform = stripGovernanceFrontMatter();
  assert.doesNotThrow(() => transform(null, {}));
  assert.doesNotThrow(() => transform(null, {data: {frontMatter: null}}));
  assert.doesNotThrow(() => transform(null, {data: {frontMatter: []}}));
});
