import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyPhase5ScreenshotRender} from './verify-phase5-screenshot-render.mjs';

test('freezes screenshot Preview bindings and accepted evidence without running Gradle in unit tests', async () => {
  const result = await verifyPhase5ScreenshotRender({renderGolden: false});
  assert.equal(result.supportedGoldens, 1);
  assert.equal(result.failClosedDenominators, 3);
  assert.equal(result.rendered, 0);
  assert.equal(result.cacheHits, 0);
  assert.equal(
    result.requestFingerprint,
    '405dba6210f39fc0bc754a91c67afe48a5ff386ab78952bddaad7279ef58986a',
  );
  assert.equal(
    result.wrapperFingerprint,
    '7b0d004f650248f2108e960385efa7e9a324acc600bfcd142f71c4a8b8d5c65b',
  );
});
