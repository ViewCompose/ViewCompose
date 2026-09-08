import assert from 'node:assert/strict';
import test from 'node:test';
import {compactCatalogSidebars} from '../../src/sidebarItemsGenerator.ts';

test('keeps the decision index but removes its repeated ADR children from the global sidebar', () => {
  const items = compactCatalogSidebars([
    {
      type: 'category',
      label: 'architecture',
      items: [
        {
          type: 'category',
          label: 'Architecture Decisions',
          link: {type: 'doc', id: 'architecture/decisions/README'},
          items: [
            {type: 'doc', id: 'architecture/decisions/0001-hosted-documentation-platform'},
            {type: 'doc', id: 'architecture/decisions/0025-version-bound-ai-tooling-upgrades'},
          ],
        },
        {type: 'doc', id: 'architecture/overview'},
      ],
    },
  ]);

  assert.deepEqual(items[0].items[0], {
    type: 'category',
    label: 'Architecture Decisions',
    link: {type: 'doc', id: 'architecture/decisions/README'},
    items: [],
  });
  assert.deepEqual(items[0].items[1], {type: 'doc', id: 'architecture/overview'});
});

test('keeps the module catalog and unrelated navigation without mutating generated items', () => {
  const original = [
    {
      type: 'category',
      label: '模块',
      link: {type: 'doc', id: 'modules/README'},
      items: [{type: 'doc', id: 'modules/viewcompose-runtime/README'}],
    },
    {
      type: 'category',
      label: 'Guides',
      link: {type: 'doc', id: 'guides/README'},
      items: [{type: 'doc', id: 'guides/image-loading'}],
    },
  ];
  const before = structuredClone(original);
  const items = compactCatalogSidebars(original);
  assert.deepEqual(items[0], {...before[0], items: []});
  assert.deepEqual(items[1], before[1]);
  assert.deepEqual(original, before);
});
