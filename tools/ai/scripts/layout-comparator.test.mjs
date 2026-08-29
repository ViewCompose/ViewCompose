import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdir, mkdtemp, readFile, rm, symlink, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {compareGeneratedLayout} from './layout-comparator.mjs';

const fixtureRoot = fileURLToPath(new URL('../evaluation/fixtures/xml/', import.meta.url));

async function fixture(name) {
  return JSON.parse(await readFile(resolve(fixtureRoot, name), 'utf8'));
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function virtual(type, key, nodeId, children = []) {
  return {type, key, nodeId, synthetic: false, children};
}

function native(className, nodeId, bounds, properties = {}, children = [], visibility = 'VISIBLE') {
  const hidden = visibility === 'GONE';
  return {
    className,
    nodeId,
    bounds,
    visibleBounds: hidden ? null : bounds,
    visibility,
    properties,
    children,
  };
}

function bounds(left, top, right, bottom) {
  return {left, top, right, bottom};
}

function loginTree() {
  const title = virtual('Text', 'title', 'node-1');
  const field = virtual('TextField', null, 'node-2');
  const email = virtual('Column', 'email', 'node-3', [field]);
  const submit = virtual('Button', 'submit', 'node-4');
  const root = virtual('Column', 'xml:0', 'node-5', [title, email, submit]);
  return {
    tree: [root],
    nativeViewTree: [native(
      'android.widget.FrameLayout',
      null,
      bounds(0, 0, 1079, 2339),
      {},
      [native(
        'com.viewcompose.renderer.view.container.DeclarativeLinearLayout',
        'node-5',
        bounds(0, 0, 1079, 2339),
        {layoutWidth: 'match_parent', layoutHeight: 'match_parent'},
        [
          native(
            'android.widget.TextView',
            'node-1',
            bounds(42, 42, 568, 97),
            {text: 'Sign in to ViewCompose'},
          ),
          native(
            'com.viewcompose.renderer.view.container.DeclarativeLinearLayout',
            'node-3',
            bounds(42, 97, 1037, 244),
            {},
            [native(
              'com.viewcompose.renderer.view.tree.ViewComposeEditText',
              'node-2',
              bounds(42, 97, 1037, 244),
            )],
          ),
          native(
            'android.widget.Button',
            'node-4',
            bounds(42, 244, 1037, 370),
            {text: 'Sign in'},
          ),
        ],
      )],
    )],
  };
}

function profileTree() {
  const avatar = virtual('Image', 'avatar', 'node-1');
  const status = virtual('Text', 'status', 'node-2');
  const root = virtual('Box', 'profile_card', 'node-3', [avatar, status]);
  return {
    tree: [root],
    nativeViewTree: [native(
      'android.widget.FrameLayout',
      null,
      bounds(0, 0, 1079, 2339),
      {},
      [native(
        'com.viewcompose.renderer.view.container.DeclarativeBoxLayout',
        'node-3',
        bounds(0, 0, 1079, 420),
        {},
        [
          native(
            'android.widget.ImageView',
            'node-1',
            bounds(42, 42, 294, 294),
            {contentDescription: 'Profile photo'},
          ),
          native(
            'android.widget.TextView',
            'node-2',
            bounds(0, 0, 0, 0),
            {text: 'Available'},
            [],
            'GONE',
          ),
        ],
      )],
    )],
  };
}

async function compareFixture(context, name, tree, mutatePreview) {
  const repository = await mkdtemp(resolve(tmpdir(), 'viewcompose-layout-comparison-'));
  context.after(() => rm(repository, {recursive: true, force: true}));
  const path = resolve(repository, 'evidence/render-tree.json');
  await mkdir(dirname(path), {recursive: true});
  const bytes = Buffer.from(JSON.stringify(tree));
  await writeFile(path, bytes);
  const designIr = await fixture(`${name}.design-ir.json`);
  const request = await fixture(name === 'login'
    ? 'generated-preview/login.preview-request.json'
    : 'generated-preview/image-binding.preview-request.json');
  const preview = {
    configuration: {
      density: 2.625,
      fontScale: 1,
      localeTags: ['en-US'],
      layoutDirection: 'Ltr',
    },
    image: {widthPx: 1079, heightPx: 2339},
    renderTree: {
      path: 'evidence/render-tree.json',
      bytes: bytes.length,
      sha256: sha256(bytes),
    },
    generatedPreview: {
      requestFingerprint: sha256(JSON.stringify(request)),
      renderTreeSha256: sha256(bytes),
    },
  };
  mutatePreview?.(preview, path);
  return compareGeneratedLayout({
    designIr,
    previewBindings: request.bindings,
    preview,
    previewEvidence: {level: 'rendered', outputFingerprint: 'a'.repeat(64)},
  }, {repository});
}

test('compares the login semantic wrapper and exact geometry', async (context) => {
  const result = await compareFixture(context, 'login', loginTree());

  assert.equal(result.status, 'success');
  assert.equal(result.evidenceLevel, 'compared');
  assert.deepEqual(result.comparison.summary, {
    designNodes: 4,
    mappedNodes: 4,
    requiredChecks: 32,
    passedChecks: 32,
    failedChecks: 0,
    notApplicableChecks: 0,
  });
  const email = result.comparison.nodes.find((node) => node.designNodeId === 'id:email');
  assert.equal(email.wrapperDepth, 1);
  assert.equal(email.identityRenderNodeId, 'node-3');
  assert.equal(email.semanticRenderNodeId, 'node-2');
  assert.match(result.comparison.comparisonFingerprint, /^[a-f0-9]{64}$/u);
});

test('compares image semantics and keeps hidden geometry not applicable', async (context) => {
  const result = await compareFixture(context, 'profile-card', profileTree());

  assert.equal(result.status, 'success');
  assert.deepEqual(result.comparison.summary, {
    designNodes: 3,
    mappedNodes: 3,
    requiredChecks: 24,
    passedChecks: 24,
    failedChecks: 0,
    notApplicableChecks: 1,
  });
  const hidden = result.comparison.nodes
    .find((node) => node.designNodeId === 'id:status')
    .checks.find((item) => item.id === 'geometry.hidden');
  assert.equal(hidden.status, 'not-applicable');
});

test('fails an exact-dp node moved by one pixel', async (context) => {
  const tree = profileTree();
  tree.nativeViewTree[0].children[0].children[0].bounds.right += 1;
  tree.nativeViewTree[0].children[0].children[0].visibleBounds.right += 1;
  const result = await compareFixture(context, 'profile-card', tree);

  assert.equal(result.status, 'failed');
  assert.equal(result.evidenceLevel, 'rendered');
  assert.ok(result.diagnostics.some((item) => item.code === 'VC-AI-COMPARE-GEOMETRY-MISMATCH'));
  assert.equal(result.comparison.summary.failedChecks, 1);
});

test('fails semantic text and authored child-order drift separately', async (context) => {
  const tree = loginTree();
  tree.nativeViewTree[0].children[0].children[0].properties.text = 'Changed';
  const [title, email, submit] = tree.tree[0].children;
  tree.tree[0].children = [email, title, submit];
  const result = await compareFixture(context, 'login', tree);

  assert.equal(result.status, 'failed');
  assert.ok(result.diagnostics.some((item) => item.code === 'VC-AI-COMPARE-SEMANTIC-MISMATCH'));
  assert.ok(result.diagnostics.some((item) => item.code === 'VC-AI-COMPARE-STRUCTURE-MISMATCH'));
});

test('fails duplicate authored keys without selecting one candidate', async (context) => {
  const tree = loginTree();
  tree.tree[0].children.push(virtual('Text', 'title', 'node-6'));
  tree.nativeViewTree[0].children[0].children.push(native(
    'android.widget.TextView',
    'node-6',
    bounds(42, 370, 100, 400),
    {text: 'Duplicate'},
  ));
  const result = await compareFixture(context, 'login', tree);

  assert.equal(result.status, 'failed');
  assert.ok(result.diagnostics.some((item) => item.code === 'VC-AI-COMPARE-NODE-AMBIGUOUS'));
});

test('rejects changed or symbolic-link render evidence before comparison', async (context) => {
  const changed = await compareFixture(context, 'login', loginTree(), (preview) => {
    preview.renderTree.sha256 = 'f'.repeat(64);
    preview.generatedPreview.renderTreeSha256 = 'f'.repeat(64);
  });
  assert.equal(changed.status, 'failed');
  assert.equal(changed.comparison, undefined);
  assert.equal(changed.diagnostics[0].code, 'VC-AI-COMPARE-RENDER-EVIDENCE-MISMATCH');

  const repository = await mkdtemp(resolve(tmpdir(), 'viewcompose-layout-symlink-'));
  context.after(() => rm(repository, {recursive: true, force: true}));
  const outside = resolve(repository, 'outside.json');
  const linked = resolve(repository, 'evidence/render-tree.json');
  const bytes = Buffer.from(JSON.stringify(loginTree()));
  await writeFile(outside, bytes);
  await mkdir(dirname(linked), {recursive: true});
  await symlink(outside, linked);
  const designIr = await fixture('login.design-ir.json');
  const request = await fixture('generated-preview/login.preview-request.json');
  const symbolic = await compareGeneratedLayout({
    designIr,
    previewBindings: request.bindings,
    preview: {
      configuration: {
        density: 2.625,
        fontScale: 1,
        localeTags: ['en-US'],
        layoutDirection: 'Ltr',
      },
      image: {widthPx: 1079, heightPx: 2339},
      renderTree: {path: 'evidence/render-tree.json', bytes: bytes.length, sha256: sha256(bytes)},
      generatedPreview: {
        requestFingerprint: sha256(JSON.stringify(request)),
        renderTreeSha256: sha256(bytes),
      },
    },
    previewEvidence: {level: 'rendered', outputFingerprint: 'a'.repeat(64)},
  }, {repository});
  assert.equal(symbolic.status, 'failed');
  assert.equal(symbolic.diagnostics[0].code, 'VC-AI-COMPARE-RENDER-EVIDENCE-MISMATCH');
});
