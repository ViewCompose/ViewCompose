# P1 core capability verification

This matrix covers the P1 focus/key input, nested scroll, and render-failure/native-effect
boundaries. The fast JVM/Robolectric suite remains the default gate; a connected Android device
validates the native View dispatch paths.

## Automated gates

Run the complete compile and unit-test gate:

```bash
./gradlew qaQuick
```

Run only the P1 connected-device cases:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.P1CoreCapabilitiesUiTest
```

`P1CoreCapabilitiesUiTest` verifies on a real Android runtime that:

1. `FocusRequester` reaches the native focus target and hardware keys follow preview then bubble
   dispatch.
2. the transparent nested-scroll host implements AndroidX `NestedScrollingParent3` and reports
   native pre-scroll consumption.
3. a failed `AndroidView.update` restores the previous View configuration, emits a structured
   `RenderFailure`, and never publishes the failed candidate's `onCommit`.

The debug-only test activity uses `showWhenLocked` and `turnScreenOn`; it does not disable or alter
the device keyguard configuration.

## Required device matrix

Before a release that changes one of these systems, cover:

| Area | Required cases |
| --- | --- |
| Focus | touch-to-focus, programmatic request, clear, next/previous, four-way D-pad, group enter/exit, keyed removal/remount restore |
| Hardware keys | Tab/Shift+Tab, Enter/Space, arrows, Back/Escape, preview interception, target bubble, physical keyboard repeat/modifier flags |
| Text coexistence | focus movement into/out of `TextField`, IME open/close, hardware typing, selection keys, no duplicate key callback |
| Nested scroll | vertical and horizontal drag, pre/post consumption, touch-to-fling handoff, nested Lazy/Pager/Scrollable combinations, overscroll boundaries |
| Native interop | AndroidX nested child, AndroidX nested parent, one representative third-party nested-scrolling View, non-nested AndroidView fallback |
| Render failure | composition failure, native factory/update/reset failure, rollback rebind, `onCommit` isolation, release failure, session disposal |
| Lifecycle | rotate/recreate after focused or scrolled state, background/foreground with a pending frame, dispose with pending coroutine/fling |

Minimum platform coverage is API 24, one API 28-30 device, and the current target API. Add a
hardware-keyboard or TV/ChromeOS target when focus traversal or key mapping changes.

## Failure triage

- A `RolledBack` report must preserve the old visible tree and must not run candidate commit
  effects.
- A `Committed` report may contain isolated post-commit failures; every remaining callback and
  cleanup operation must still run.
- Native failures must include `operation` and `nodeKey`. Missing metadata is a framework bug, not
  an application logging concern.
- A third-party View that mutates hidden internal state from `update` must be adapted so that
  `update` is replay-safe and the external action moves to `onCommit`.
