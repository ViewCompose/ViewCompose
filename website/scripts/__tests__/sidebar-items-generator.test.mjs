import assert from 'node:assert/strict';
import test from 'node:test';
import {compactArchitectureDecisionSidebar} from '../../src/sidebarItemsGenerator.ts';

test('keeps the decision index but removes its repeated ADR children from the global sidebar', () => {
  const items = compactArchitectureDecisionSidebar([
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
