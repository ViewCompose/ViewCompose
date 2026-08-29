import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {convertXmlToDesignIr, XML_MIGRATION_LIMITS} from './xml-to-design-ir.mjs';
import {verifyPhase4DesignIr} from './verify-phase4-design-ir.mjs';

const loginPath = fileURLToPath(
  new URL('../evaluation/fixtures/xml/login.xml', import.meta.url),
);
const goldenPath = fileURLToPath(
  new URL('../evaluation/fixtures/xml/login.design-ir.json', import.meta.url),
);

async function fixture(name) {
  return readFile(fileURLToPath(new URL(`../evaluation/fixtures/xml/${name}`, import.meta.url)), 'utf8');
}

test('converts the frozen login XML to byte-stable schema-valid Design IR', async () => {
  const [source, golden] = await Promise.all([
    readFile(loginPath, 'utf8'),
    readFile(goldenPath, 'utf8').then(JSON.parse),
  ]);
  const input = {source, path: 'tools/ai/evaluation/fixtures/xml/login.xml'};
  const first = await convertXmlToDesignIr(input);
  const second = await convertXmlToDesignIr(input);

  assert.equal(first.status, 'success');
  assert.deepEqual(first.diagnostics, []);
  assert.deepEqual(first.ir, golden);
  assert.equal(JSON.stringify(first.ir), JSON.stringify(second.ir));
  assert.deepEqual(first.unsupported, []);
});

test('maps horizontal defaults, literal strings, integer dp sizes, and supported input profiles', async () => {
  const source = `<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="320dp"
    android:layout_height="wrap_content">
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Account" />
    <EditText
        android:id="@+id/password"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="textPassword" />
    <Button
        android:layout_width="wrap_content"
        android:layout_height="48dp"
        android:text="Continue" />
</LinearLayout>
`;
  const result = await convertXmlToDesignIr({source, path: 'res/layout/account.xml'});

  assert.equal(result.status, 'success');
  assert.equal(result.ir.roots[0].kind, 'row');
  assert.deepEqual(result.ir.roots[0].modifiers[0].arguments[0].value, {
    kind: 'dimension',
    value: 320,
    unit: 'dp',
  });
  assert.deepEqual(result.ir.roots[0].children[0].properties[0].value, {
    kind: 'literal',
    value: 'Account',
  });
  assert.equal(result.ir.roots[0].children[1].state[0].value.name, 'passwordState');
  assert.equal(result.ir.roots[0].children[1].properties[0].value.value, 'textPassword');
});

test('localizes custom Views, Data Binding, and unknown attributes without Kotlin claims', async () => {
  const cases = [
    ['custom-view.xml', 'VC-AI-XML-CUSTOM-VIEW-UNSUPPORTED'],
    ['data-binding.xml', 'VC-AI-XML-DATA-BINDING-UNSUPPORTED'],
    ['unsupported-attribute.xml', 'VC-AI-XML-ATTRIBUTE-UNSUPPORTED'],
  ];
  for (const [name, code] of cases) {
    const result = await convertXmlToDesignIr({
      source: await fixture(name),
      path: `tools/ai/evaluation/fixtures/xml/${name}`,
    });
    assert.equal(result.status, 'unsupported', name);
    assert.ok(result.unsupported.some((item) => item.code === code), name);
    assert.ok(result.diagnostics.some((item) => item.code === code), name);
    assert.equal(Object.hasOwn(result, 'kotlin'), false, name);
  }
});

test('rejects unsafe declarations, malformed XML, duplicate ids, and resource-limit drift', async () => {
  const cases = [
    [
      '<!DOCTYPE layout [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><TextView xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="wrap_content" android:layout_height="wrap_content"/>',
      'VC-AI-XML-DOCTYPE-UNSUPPORTED',
      'unsupported',
    ],
    [
      '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent"><TextView></LinearLayout>',
      'VC-AI-XML-MALFORMED',
      'invalid',
    ],
    [
      '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent"><TextView android:id="@+id/title" android:layout_width="wrap_content" android:layout_height="wrap_content"/><Button android:id="@id/title" android:layout_width="wrap_content" android:layout_height="wrap_content"/></LinearLayout>',
      'VC-AI-XML-DUPLICATE-ID',
      'unsupported',
    ],
  ];
  for (const [source, code, status] of cases) {
    const result = await convertXmlToDesignIr({source, path: 'res/layout/unsafe.xml'});
    assert.equal(result.status, status);
    assert.ok(result.diagnostics.some((item) => item.code === code));
  }

  const limited = await convertXmlToDesignIr({
    source: '<TextView xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="wrap_content" android:layout_height="wrap_content"/>',
    path: 'res/layout/limited.xml',
    limits: {...XML_MIGRATION_LIMITS, maxInputBytes: 16},
  });
  assert.equal(limited.status, 'limited');
  assert.equal(limited.diagnostics[0].code, 'VC-AI-XML-LIMIT');

  const forbiddenCharacter = await convertXmlToDesignIr({
    source: '<TextView\u0000/>',
    path: 'res/layout/invalid-character.xml',
  });
  assert.equal(forbiddenCharacter.status, 'invalid');
  assert.equal(forbiddenCharacter.diagnostics[0].code, 'VC-AI-XML-MALFORMED');
});

test('requires a bounded logical source identity and the exact Android namespace', async () => {
  const source = '<TextView xmlns:android="urn:wrong" android:layout_width="wrap_content" android:layout_height="wrap_content"/>';
  const escaped = await convertXmlToDesignIr({source, path: '../layout.xml'});
  assert.equal(escaped.status, 'invalid');
  assert.equal(escaped.diagnostics[0].code, 'VC-AI-XML-VALUE-UNSUPPORTED');

  const namespace = await convertXmlToDesignIr({source, path: 'res/layout/layout.xml'});
  assert.equal(namespace.status, 'unsupported');
  assert.equal(namespace.diagnostics[0].code, 'VC-AI-XML-NAMESPACE-UNSUPPORTED');
});

test('meets every frozen Phase 4 Design IR and unsupported-honesty denominator', async () => {
  const summary = await verifyPhase4DesignIr();
  assert.deepEqual(summary.supported, {
    deterministicMatches: 1,
    schemaMatches: 1,
    resourceMatches: 1,
    fixtures: 1,
    provenanceNodes: 4,
    totalNodes: 4,
  });
  assert.deepEqual(summary.unsupported, {matches: 3, fixtures: 3});
});
