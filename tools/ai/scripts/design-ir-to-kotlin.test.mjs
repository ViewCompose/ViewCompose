import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {generateViewComposeKotlin} from './design-ir-to-kotlin.mjs';

async function goldenIr() {
  return readFile(
    fileURLToPath(new URL('../evaluation/fixtures/xml/login.design-ir.json', import.meta.url)),
    'utf8',
  ).then(JSON.parse);
}

async function profileIr() {
  return readFile(
    fileURLToPath(new URL('../evaluation/fixtures/xml/profile-card.design-ir.json', import.meta.url)),
    'utf8',
  ).then(JSON.parse);
}

test('generates the exact deterministic ViewCompose Kotlin golden and migration report', async () => {
  const [ir, golden] = await Promise.all([
    goldenIr(),
    readFile(
      fileURLToPath(new URL('../evaluation/fixtures/xml/login.viewcompose.kt', import.meta.url)),
      'utf8',
    ),
  ]);
  const first = await generateViewComposeKotlin(ir);
  const second = await generateViewComposeKotlin(ir);

  assert.equal(first.status, 'success');
  assert.equal(first.kotlin, golden);
  assert.equal(first.kotlin, second.kotlin);
  assert.equal(first.outputFingerprint, second.outputFingerprint);
  assert.deepEqual(first.report.target, {
    language: 'kotlin',
    packageName: 'generated.viewcompose',
    functionName: 'LoginView',
    artifactIds: ['viewcompose-ui-foundation'],
  });
  assert.deepEqual(
    first.report.bindings.resources.map((binding) => binding.parameter),
    ['loginTitle', 'emailHint', 'loginAction'],
  );
  assert.deepEqual(first.report.bindings.states.map((binding) => binding.parameter), ['emailState']);
  assert.equal(first.report.callSiteReview.required, true);
  assert.deepEqual(first.report.callSiteReview.items, [
    'Resolve every caller resource parameter from its recorded Android resource at the ViewCompose host boundary.',
    'Retain caller ownership and restoration policy for every TextFieldState parameter.',
    'Inventory every caller-owned state source and update cadence before integration; preserve its initial value, throttling, completion, and error semantics.',
    'For external imperative state, retain one RenderSession, update the latest immutable snapshot, and call RenderSession.render() on the Android main thread; never create a session for each emission.',
    'Keep native siblings, animations, advertising, navigation, analytics, and other side effects outside the selected container under their existing owners.',
    'Review ViewBinding references, listeners, adapters, and imperative mutations outside the XML input.',
    'Prefer Activity or Fragment setUiContent; when using low-level renderInto, wrap content in AndroidResourceEnvironment(container.context) and dispose the RenderSession with the host lifecycle.',
    'Stop state collectors from rendering after host teardown, and verify the initial state, at least one later state, and completion or navigation behavior.',
  ]);
});

test('rejects schema-invalid, blocked, behavioral, and non-normalized IR', async () => {
  const invalid = await goldenIr();
  invalid.schemaVersion = 2;
  const invalidResult = await generateViewComposeKotlin(invalid);
  assert.equal(invalidResult.status, 'invalid');
  assert.equal(invalidResult.diagnostics[0].code, 'VC-AI-IR-INVALID');

  const behavioral = await goldenIr();
  behavioral.roots[0].children[2].events.push({
    kind: 'click',
    binding: 'submit',
    status: 'placeholder',
  });
  const behavioralResult = await generateViewComposeKotlin(behavioral);
  assert.equal(behavioralResult.status, 'unsupported');
  assert.ok(behavioralResult.diagnostics.some((item) => item.code === 'VC-AI-IR-UNSUPPORTED'));
  assert.equal(Object.hasOwn(behavioralResult, 'kotlin'), false);

  const nonString = await goldenIr();
  nonString.roots[0].children[0].properties[0].value = {kind: 'literal', value: 42};
  const nonStringResult = await generateViewComposeKotlin(nonString);
  assert.equal(nonStringResult.status, 'unsupported');
  assert.ok(nonStringResult.diagnostics.some((item) => item.code === 'VC-AI-GENERATOR-UNSUPPORTED'));
});

test('generates the exact Box, Image, accessibility, drawable, and visibility golden', async () => {
  const [ir, golden] = await Promise.all([
    profileIr(),
    readFile(
      fileURLToPath(new URL('../evaluation/fixtures/xml/profile-card.viewcompose.kt', import.meta.url)),
      'utf8',
    ),
  ]);
  const result = await generateViewComposeKotlin(ir);

  assert.equal(result.status, 'success');
  assert.equal(result.kotlin, golden);
  assert.deepEqual(
    result.report.bindings.resources.map(({source, parameter, type}) => ({source, parameter, type})),
    [
      {source: '@drawable/profile_avatar', parameter: 'profileAvatar', type: 'ImageSource'},
      {source: '@string/profile_photo', parameter: 'profilePhoto', type: 'String'},
      {source: '@string/status_label', parameter: 'statusLabel', type: 'String'},
    ],
  );
  assert.ok(result.kotlin.includes('contentDescription = profilePhoto'));
  assert.ok(result.kotlin.includes('Modifier.visibility(Visibility.Gone)'));
});

test('escapes Kotlin templates and deterministically disambiguates parameter names', async () => {
  const ir = await goldenIr();
  ir.roots[0].children[0].properties[0].value = {kind: 'literal', value: 'Price $5'};
  ir.roots[0].children[1].properties[0].value = {
    kind: 'resource',
    resourceType: 'string',
    name: 'email_state',
  };
  const result = await generateViewComposeKotlin(ir);

  assert.equal(result.status, 'success');
  assert.ok(result.kotlin.includes('text = "Price \\$5"'));
  assert.ok(result.kotlin.includes('emailState: String'));
  assert.ok(result.kotlin.includes('emailState2: TextFieldState'));
  assert.ok(result.kotlin.includes('state = emailState2'));
});
