import assert from 'node:assert/strict';
import test from 'node:test';
import rewriteRepositoryFileLinks from '../../src/remark/rewriteRepositoryFileLinks.ts';

const options = {
  repositoryRoot: '/workspace/ViewCompose',
  docsRoot: '/workspace/ViewCompose/docs',
  repositorySourceUrl: 'https://github.com/ViewCompose/ViewCompose/blob/main',
};

test('rewrites repository files outside docs and preserves suffixes', () => {
  const tree = {
    type: 'root',
    children: [
      {
        type: 'link',
        url: '../../viewcompose-runtime/src/main/java/example/Runtime.kt#L12',
      },
    ],
  };

  rewriteRepositoryFileLinks(options)(tree, {
    path: '/workspace/ViewCompose/docs/architecture/runtime.md',
  });

  assert.equal(
    tree.children[0].url,
    'https://github.com/ViewCompose/ViewCompose/blob/main/viewcompose-runtime/src/main/java/example/Runtime.kt#L12',
  );
});

test('keeps documentation, external, image, and escaping links unchanged', () => {
  const tree = {
    type: 'root',
    children: [
      {type: 'link', url: '../project/documentation-site.md'},
      {type: 'link', url: 'https://developer.android.com/topic/libraries/architecture/viewmodel'},
      {type: 'image', url: '../../art/architecture.png'},
      {type: 'link', url: '../../../outside.txt'},
    ],
  };

  rewriteRepositoryFileLinks(options)(tree, {
    path: '/workspace/ViewCompose/docs/architecture/runtime.md',
  });

  assert.deepEqual(
    tree.children.map(({url}) => url),
    [
      '../project/documentation-site.md',
      'https://developer.android.com/topic/libraries/architecture/viewmodel',
      '../../art/architecture.png',
      '../../../outside.txt',
    ],
  );
});

test('ignores trees without a source path', () => {
  const tree = {type: 'root', children: [{type: 'link', url: '../../module/File.kt'}]};

  rewriteRepositoryFileLinks(options)(tree, {});

  assert.equal(tree.children[0].url, '../../module/File.kt');
});
