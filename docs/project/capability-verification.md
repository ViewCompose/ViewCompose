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
  - ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.P1CoreCapabilitiesUiTest
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

The 2026-08-22 accepted run used a Xiaomi MI 6 on API 28. All three instrumentation methods passed
in `71.693 s`, producing 32 screenshots and 32 metadata records across 12 scenario IDs. Automated
assertions and manual review both accepted all 32 frames: popup anchoring, rounded geometry,
four-edge shadow sampling and outside-touch dismissal; equal theme-swatch geometry; exact
three-column Grid state with separate first/last-row clipping evidence; segmented selection and
insets; standard and One UI pressed/released navigation feedback; bidirectional nested-list edge
handoff; and complete focused-editor reveal above the IME for LazyColumn, LazyVerticalGrid,
ScrollableColumn, VerticalPager, and PullToRefresh owners.

The first capture attempt was rejected because MIUI launch confirmation and window transitions
contaminated several frames, the Grid assertion accepted any state change instead of exactly three
columns, and navigation captured only release state. The hardened run increased focused evidence
from 26 to 32 frames (`+6`, `+23.1%`) by adding four pressed-state frames and splitting Grid top and
bottom coverage. Conclusion: `improved`. This is a coverage change, not a performance comparison.

The cleanup validation also ran the complete Demo instrumentation APK on the same rebuilt app APK.
The first run passed 135/137 tests and exposed two deterministic harness defects: Activity-routed
touch used screen rather than window coordinates, and a Collections scenario role still depended
on a fine-grained string tag. The hard cut corrected the gesture coordinate contract, made the
design-system pressure path dismiss the IME before device-level navigation and segmented taps, and
migrated Collections to its owned Android resource ID without an alias. The final run passed all
137 tests in `742.903 s`. This is behavioral and isolation evidence, not a performance baseline.

Limitations: this result covers one API 28 device and a pairwise pressure matrix, not the complete
Cartesian product or every required platform tier. Screenshots establish geometry and visible
state only; the same tests retain native touch, popup dismissal, nested-scroll ownership, focus,
IME, and reset assertions for behavioral evidence. Re-run this matrix when one of the covered
systems changes, and add API 24 and current-target devices when the affected release requires the
full platform matrix.

## Failure triage

- A `RolledBack` report must preserve the old visible tree and must not run candidate commit
  effects.
- A `Committed` report may contain isolated post-commit failures; every remaining callback and
  cleanup operation must still run.
- Native failures must include `operation` and `nodeKey`. Missing metadata is a framework bug, not
  an application logging concern.
- A third-party View that mutates hidden internal state from `update` must be adapted so that
  `update` is replay-safe and the external action moves to `onCommit`.
