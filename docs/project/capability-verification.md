---
schema_version: 2
document_id: project.capability-verification
doc_type: project
slug: /project/capability-verification
owner:
  kind: project
  id: capability-verification
version_lane: version-agnostic
capability_ids: []
artifact_ids: []
sample_ids: []
workflow: Define repository and controlled-device verification matrices plus their accepted evidence.
validation:
  - ./gradlew qaQuick
  - ./gradlew verifyNavigationCoverage
  - ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.P1CoreCapabilitiesUiTest
  - ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.NavigationBackDeviceTest
lifecycle: Update whenever capability gates, device requirements, scenario ownership, or accepted verification evidence changes.
---

# Capability verification

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

## Navigation lifecycle and resource closure

Run `./gradlew verifyNavigationCoverage` for the selected critical-path bundles. The gate rejects
missing execution data or class bundles and enforces Core line/branch floors of `80%`/`70%` plus
Android floors of `70%`/`60%`; its XML and HTML are under
`build/reports/viewcompose-quality/navigation-coverage/`. These percentages intentionally describe
the owned reducer, lifecycle/scene, executor, owner/session, retention, Back, runtime, and host
paths—not every class in either module.

Run `NavigationBackDeviceTest` with an explicit `ANDROID_SERIAL`. A current-target device must run
the complete class; an API 28–30 device must also run terminal-pop reachability, bounded-eviction
reachability, and depth-13 retention evidence. Terminal pop must release the presentation,
LifecycleOwner, and ViewModel. `Bounded(2)` must release the evicted presentation while retaining
its logical owner and ViewModel. Resource samples record live presentations, Java/native allocated
heap, PSS, and median synchronous pop time under the same warmup and GC procedure; only structural
presentation counts are hard thresholds. Accepted absolute and normalized results, device/build
context, limitations, and next action live in the active navigation evolution plan.

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

## Post-release Demo pressure matrix

Run the deterministic real-device matrix with:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.DemoPostReleaseVisualMatrixUiTest
```

The suite uses scenario IDs and Android resource IDs rather than visible copy. Each run owns and
replaces only its scenarios' evidence files, rejects capture unless the target application window
is active, and records one metadata file beside every screenshot. It executes this pairwise matrix:

| Configuration | Locale | Theme | Direction | Font scale | Density scale |
| --- | --- | --- | --- | ---: | ---: |
| Default reference | English | light | LTR | `1.0` | `1.0` |
| Pressure reference | Simplified Chinese | dark | RTL | `1.3` | `1.25` |

The accepted 2026-08-22 Xiaomi MI 6/API 28 run passed 3/3 methods in `71.693 s`: 32 screenshots and
metadata records across 12 scenarios passed automated assertions and manual review. Evidence covers
popup/shadow geometry and dismissal, exact three-column Grid edges, segmented insets, pressed and
released standard/One UI navigation, bidirectional nested-list handoff, and focused-editor reveal
above the IME for all five scroll owners.

An earlier 26-frame run was rejected for MIUI/window contamination, a weak Grid assertion, and
release-only navigation evidence. Hardening added six frames (`+23.1%`) and made coverage
`improved`, not faster. The complete Demo APK then exposed Activity touch-coordinate and
Collections tag coupling at 135/137; hard-cutting both contracts plus dismissing the IME before
device-level taps produced 137/137 in `742.903 s`. This is behavioral/isolation evidence, not a
performance baseline.

Limitations: one API 28 device and a pairwise matrix do not cover the Cartesian product or every
platform tier. Screenshots prove visible geometry; native touch, dismissal, nested-scroll, focus,
IME, and reset assertions provide behavior. Re-run affected systems and add API 24 plus the current
target when a release requires the full matrix.

## Failure triage

- A `RolledBack` report must preserve the old visible tree and must not run candidate commit
  effects.
- A `Committed` report may contain isolated post-commit failures; every remaining callback and
  cleanup operation must still run.
- Native failures must include `operation` and `nodeKey`. Missing metadata is a framework bug, not
  an application logging concern.
- A third-party View that mutates hidden internal state from `update` must be adapted so that
  `update` is replay-safe and the external action moves to `onCommit`.
