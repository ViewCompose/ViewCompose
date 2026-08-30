import assert from 'node:assert/strict';
import {mkdir, mkdtemp, readFile, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import test from 'node:test';
import {resolveXmlLayoutDependencies} from './xml-layout-dependencies.mjs';

const fixtureRoot = resolve(
  new URL('../evaluation/fixtures/xml/layout-dependencies/', import.meta.url).pathname,
);

function flatten(node) {
  return [node, ...node.children.flatMap(flatten)];
}

test('resolves the exact default-layout graph and expands include and merge in place', async () => {
  const projectRoot = resolve(fixtureRoot, 'supported');
  const expected = JSON.parse(await readFile(resolve(fixtureRoot, 'screen.dependencies.json'), 'utf8'));
  const result = await resolveXmlLayoutDependencies({
    projectRoot,
    layoutPath: 'app/src/main/res/layout/screen.xml',
    resourceRoots: ['app/src/main/res'],
  });

  assert.equal(result.status, 'success');
  assert.deepEqual(result.graph, expected);
  assert.deepEqual(
    result.expandedRoot.children.map((node) => node.name),
    ['FrameLayout', 'TextView', 'Button', 'TextView'],
  );
  const nodes = flatten(result.expandedRoot);
  assert.equal(nodes.length, 6);
  assert.equal(nodes[1].origin.path, 'app/src/main/res/layout/profile_header.xml');
  assert.equal(nodes[2].origin.path, 'app/src/main/res/layout/profile_header.xml');
  assert.equal(nodes[3].origin.path, 'app/src/main/res/layout/profile_actions.xml');
  assert.equal(nodes[4].origin.path, 'app/src/main/res/layout/profile_actions.xml');
  assert.equal(nodes[5].origin.path, 'app/src/main/res/layout/screen.xml');
  assert.equal(nodes[1].attributes.some((attribute) => attribute.name === 'xmlns:android'), false);
});

test('fails closed on include cycles and dependency limit drift', async () => {
  const projectRoot = resolve(fixtureRoot, 'cycle');
  const cycle = await resolveXmlLayoutDependencies({
    projectRoot,
    layoutPath: 'app/src/main/res/layout/a.xml',
    resourceRoots: ['app/src/main/res'],
  });
  assert.equal(cycle.status, 'unsupported');
  assert.equal(cycle.diagnostics[0].code, 'VC-AI-XML-INCLUDE-CYCLE');
  assert.equal(cycle.diagnostics[0].source.path, 'app/src/main/res/layout/b.xml');

  const invalidLimit = await resolveXmlLayoutDependencies({
    projectRoot,
    layoutPath: 'app/src/main/res/layout/a.xml',
    resourceRoots: ['app/src/main/res'],
    limits: {maxIncludeDepth: 17},
  });
  assert.equal(invalidLimit.status, 'invalid');
  assert.equal(invalidLimit.diagnostics[0].code, 'VC-AI-XML-LAYOUT-DEPENDENCY-LIMIT');
});

test('rejects missing layouts, include overrides, standalone merge, symlinks, and expansion limits', async (context) => {
  const projectRoot = await mkdtemp(resolve(tmpdir(), 'viewcompose-layout-dependencies-'));
  context.after(() => rm(projectRoot, {recursive: true, force: true}));
  const layoutRoot = resolve(projectRoot, 'app/src/main/res/layout');
  await mkdir(layoutRoot, {recursive: true});
  const document = (body) => `<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
${body}
</LinearLayout>
`;
  await writeFile(resolve(layoutRoot, 'screen.xml'), document('    <include layout="@layout/missing" />'));

  const request = {
    projectRoot,
    layoutPath: 'app/src/main/res/layout/screen.xml',
    resourceRoots: ['app/src/main/res'],
  };
  const missing = await resolveXmlLayoutDependencies(request);
  assert.equal(missing.status, 'unsupported');
  assert.equal(missing.diagnostics[0].code, 'VC-AI-XML-LAYOUT-MISSING');

  await writeFile(resolve(layoutRoot, 'child.xml'), document(''));
  await writeFile(resolve(layoutRoot, 'screen.xml'), document(
    '    <include layout="@layout/child" android:visibility="gone" />',
  ));
  const override = await resolveXmlLayoutDependencies(request);
  assert.equal(override.status, 'unsupported');
  assert.equal(override.diagnostics[0].code, 'VC-AI-XML-INCLUDE-ATTRIBUTE-UNSUPPORTED');

  await writeFile(resolve(layoutRoot, 'screen.xml'), `<?xml version="1.0" encoding="utf-8"?>
<merge xmlns:android="http://schemas.android.com/apk/res/android">
    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" />
</merge>
`);
  const standaloneMerge = await resolveXmlLayoutDependencies(request);
  assert.equal(standaloneMerge.status, 'unsupported');
  assert.equal(standaloneMerge.diagnostics[0].code, 'VC-AI-XML-MERGE-ROOT-UNSUPPORTED');

  await writeFile(resolve(layoutRoot, 'screen.xml'), document('    <include layout="@layout/linked" />'));
  await symlink(resolve(layoutRoot, 'child.xml'), resolve(layoutRoot, 'linked.xml'));
  const linked = await resolveXmlLayoutDependencies(request);
  assert.equal(linked.status, 'invalid');
  assert.equal(linked.diagnostics[0].code, 'VC-AI-XML-PROJECT-CONTEXT-REQUIRED');

  await writeFile(resolve(layoutRoot, 'screen.xml'), document(''));
  const limited = await resolveXmlLayoutDependencies({...request, limits: {maxExpandedBytes: 1}});
  assert.equal(limited.status, 'limited');
  assert.equal(limited.diagnostics[0].code, 'VC-AI-XML-LAYOUT-DEPENDENCY-LIMIT');
});

test('selects the first declared default layout root without variant inference', async (context) => {
  const projectRoot = await mkdtemp(resolve(tmpdir(), 'viewcompose-layout-precedence-'));
  context.after(() => rm(projectRoot, {recursive: true, force: true}));
  const appLayout = resolve(projectRoot, 'app/src/main/res/layout');
  const libraryLayout = resolve(projectRoot, 'library/src/main/res/layout');
  await Promise.all([
    mkdir(appLayout, {recursive: true}),
    mkdir(libraryLayout, {recursive: true}),
  ]);
  const root = `<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <include layout="@layout/child" />
</LinearLayout>
`;
  const child = (id) => `<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/${id}"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content" />
`;
  await Promise.all([
    writeFile(resolve(appLayout, 'screen.xml'), root),
    writeFile(resolve(appLayout, 'child.xml'), child('app_child')),
    writeFile(resolve(libraryLayout, 'child.xml'), child('library_child')),
  ]);

  const result = await resolveXmlLayoutDependencies({
    projectRoot,
    layoutPath: 'app/src/main/res/layout/screen.xml',
    resourceRoots: ['library/src/main/res', 'app/src/main/res'],
  });
  assert.equal(result.status, 'success');
  assert.equal(result.graph.nodes[0].resourceRootPrecedence, 1);
  assert.equal(result.graph.nodes[1].path, 'library/src/main/res/layout/child.xml');
  assert.equal(result.graph.nodes[1].resourceRootPrecedence, 0);
  assert.equal(result.expandedRoot.children[0].origin.path, 'library/src/main/res/layout/child.xml');
});
