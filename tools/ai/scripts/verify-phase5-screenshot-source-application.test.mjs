import assert from 'node:assert/strict';
import test from 'node:test';
import {
  verifyPhase5ScreenshotSourceApplication,
} from './verify-phase5-screenshot-source-application.mjs';

test('freezes the attended screenshot source-application security denominator', async () => {
  assert.deepEqual(await verifyPhase5ScreenshotSourceApplication(), {
    schemas: 3,
    positiveExamples: 3,
    schemaRejections: 8,
    runtimeRejections: 12,
    crashBoundaries: 10,
  });
});
