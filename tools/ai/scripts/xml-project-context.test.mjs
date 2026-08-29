import assert from 'node:assert/strict';
import {mkdtemp, mkdir, readFile, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, resolve} from 'node:path';
import test from 'node:test';
import {convertXmlToDesignIr} from './xml-to-design-ir.mjs';
import {resolveXmlProjectContext} from './xml-project-context.mjs';

const fixtureRoot = resolve(
  new URL('../evaluation/fixtures/xml/project-context/', import.meta.url).pathname,
);

test('resolves the exact resource, style, and bounded call-site golden', async () => {
  const projectRoot = resolve(fixtureRoot, 'supported');
  const expected = JSON.parse(await readFile(resolve(fixtureRoot, 'styled-login.context.json'), 'utf8'));
  const result = await resolveXmlProjectContext({
    projectRoot,
    layoutPath: 'app/src/main/res/layout/styled_login.xml',
    resourceRoots: ['app/src/main/res'],
    sourceRoots: ['app/src/main/java'],
  });

  assert.equal(result.status, 'success');
  assert.deepEqual(result.context, expected);
  assert.doesNotMatch(result.resolvedSource, /\bstyle\s*=/u);
  assert.match(result.resolvedSource, /android:orientation="vertical"/u);
  assert.match(result.resolvedSource, /android:padding="16dp"/u);
  assert.match(result.resolvedSource, /android:text="@string\/login_title"/u);

  const converted = await convertXmlToDesignIr({
    source: result.resolvedSource,
    path: result.context.layout.path,
  });
  assert.equal(converted.status, 'success');
  assert.equal(converted.ir.roots[0].kind, 'column');
  assert.equal(converted.ir.roots[0].children[0].properties[0].value.name, 'login_title');
});

test('fails closed on style cycles and theme attributes', async () => {
  const styleCycle = await resolveXmlProjectContext({
    projectRoot: resolve(fixtureRoot, 'style-cycle'),
    layoutPath: 'app/src/main/res/layout/cycle.xml',
    resourceRoots: ['app/src/main/res'],
  });
  assert.equal(styleCycle.status, 'unsupported');
  assert.equal(styleCycle.diagnostics[0].code, 'VC-AI-XML-STYLE-CYCLE');

  const themeAttribute = await resolveXmlProjectContext({
    projectRoot: resolve(fixtureRoot, 'theme-attribute'),
    layoutPath: 'app/src/main/res/layout/theme_text.xml',
    resourceRoots: ['app/src/main/res'],
  });
  assert.equal(themeAttribute.status, 'unsupported');
  assert.equal(themeAttribute.diagnostics[0].code, 'VC-AI-XML-THEME-ATTRIBUTE-UNSUPPORTED');
});

test('rejects traversal, symlinks, missing defaults, and duplicate definitions', async () => {
  const root = await mkdtemp(resolve(tmpdir(), 'viewcompose-xml-context-'));
  try {
    const layoutPath = 'app/src/main/res/layout/screen.xml';
    const valuesPath = 'app/src/main/res/values/strings.xml';
    await mkdir(resolve(root, dirname(layoutPath)), {recursive: true});
    await mkdir(resolve(root, dirname(valuesPath)), {recursive: true});
    await writeFile(resolve(root, layoutPath), `<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="@string/title" />
`);
    await writeFile(resolve(root, valuesPath), `<?xml version="1.0" encoding="utf-8"?>
<resources><string name="title">One</string><string name="title">Two</string></resources>
`);

    const traversal = await resolveXmlProjectContext({
      projectRoot: root,
      layoutPath: '../screen.xml',
      resourceRoots: ['app/src/main/res'],
    });
    assert.equal(traversal.diagnostics[0].code, 'VC-AI-XML-PROJECT-PATH-INVALID');

    const duplicate = await resolveXmlProjectContext({
      projectRoot: root,
      layoutPath,
      resourceRoots: ['app/src/main/res'],
    });
    assert.equal(duplicate.status, 'unsupported');
    assert.equal(duplicate.diagnostics[0].code, 'VC-AI-XML-RESOURCE-DUPLICATE');

    await rm(resolve(root, valuesPath));
    await mkdir(resolve(root, 'app/src/main/res/values-en'), {recursive: true});
    await writeFile(resolve(root, 'app/src/main/res/values-en/strings.xml'),
      '<resources><string name="title">Title</string></resources>\n');
    const missingDefault = await resolveXmlProjectContext({
      projectRoot: root,
      layoutPath,
      resourceRoots: ['app/src/main/res'],
    });
    assert.equal(missingDefault.status, 'unsupported');
    assert.equal(missingDefault.diagnostics[0].code, 'VC-AI-XML-RESOURCE-MISSING-DEFAULT');

    await symlink(resolve(root, layoutPath), resolve(root, 'app/src/main/res/layout/linked.xml'));
    const linked = await resolveXmlProjectContext({
      projectRoot: root,
      layoutPath: 'app/src/main/res/layout/linked.xml',
      resourceRoots: ['app/src/main/res'],
    });
    assert.equal(linked.status, 'invalid');
    assert.equal(linked.diagnostics[0].code, 'VC-AI-XML-PROJECT-SYMLINK-DENIED');
  } finally {
    await rm(root, {recursive: true, force: true});
  }
});
