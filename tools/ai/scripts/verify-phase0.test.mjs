import test from 'node:test';
import assert from 'node:assert/strict';
import {resolveFixturePath, verifyPhase0} from './verify-phase0.mjs';

test('accepts the checked-in Phase 0 contracts and evaluation corpus', async () => {
  const summary = await verifyPhase0();
  assert.equal(summary.schemas, 18);
  assert.equal(summary.reservedCapabilities, 5);
  assert.equal(summary.metrics, 64);
  assert.equal(summary.cases, 73);
  assert.equal(summary.fixtures, 70);
  assert.equal(summary.xmlFixtures, 4);
  assert.equal(summary.xmlV2Fixtures, 2);
  assert.equal(summary.xmlProjectContextFixtures, 3);
  assert.equal(summary.xmlLayoutDependencyFixtures, 3);
  assert.equal(summary.generatedPreviewFixtures, 5);
  assert.equal(summary.layoutComparisonFixtures, 2);
  assert.equal(summary.screenshotPreprocessingFixtures, 4);
  assert.equal(summary.screenshotDesignInferenceFixtures, 4);
  assert.equal(summary.screenshotInferenceResolutionFixtures, 4);
  assert.equal(summary.screenshotKotlinGenerationFixtures, 4);
  assert.equal(summary.screenshotGeneratedPreviewFixtures, 4);
  assert.equal(summary.screenshotLayoutComparisonFixtures, 3);
  assert.equal(summary.screenshotPixelComparisonFixtures, 5);
  assert.equal(summary.screenshotRepairFixtures, 9);
});

test('rejects a fixture that resolves outside the repository', async () => {
  await assert.rejects(resolveFixturePath('../../../../../../etc/passwd'), /escapes repository root/u);
});
