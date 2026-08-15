# Remaining Component Appearance Convergence Plan

## Status

Completed on 2026-08-15. The FAB, app-bar, Badge, AlertDialog, and modal-bottom-sheet appearance
contracts, downstream presenters and consumers, Demo verification surfaces, documentation, and
repository quality gates are complete. Scaffold and the raw Dialog overlay remain intentionally
outside sparse appearance overrides.

Last verified: 2026-08-15.

## Maven release changesets

- `release/changes/20260815-component-appearance-convergence.json`

## Objective

Complete the component-appearance boundary accepted by
[ADR-0013](../architecture/decisions/0013-component-appearance-resolution-boundary.md) for the
remaining families whose current contracts now meet the activation criteria:

1. remove low-frequency FAB, app-bar, and Badge colors from their primary signatures;
2. give visual composites typed sparse overrides without moving behavior, identity, content, or
   lifecycle into appearance objects;
3. make app bars own the content-color environment of navigation and action slots;
4. make modal bottom-sheet appearance a resolved request snapshot consumed by every presenter on
   both show and same-key update; and
5. preserve Scaffold and raw Dialog as layout and overlay protocols rather than manufacturing
   speculative override families.

## Audit decision

| Family | Decision | Public appearance fields |
| --- | --- | --- |
| FloatingActionButton | Add a dedicated sparse override and remove direct colors | container/content colors, shape, elevation, ripple, and state-layer colors |
| ExtendedFloatingActionButton | Use an independent typed override because its typography and geometry do not apply to regular FABs | shared FAB appearance plus text style, icon size, height, horizontal padding, and icon spacing |
| Scaffold | Keep the current signature and add no override provider | container and content colors remain primary page-surface inputs |
| TopAppBar | Add a dedicated sparse override and own navigation/action content-color scopes | container/title/navigation/action colors, title style, height, and padding |
| BottomAppBar | Add an independent sparse override and content-color scope | container/content colors, height, padding, and elevation |
| Badge | Add a sparse override and remove direct colors | colors, text style, shape, dot size, and labeled-pill geometry |
| BadgedBox | Add no appearance override | alignment or offsets remain future layout inputs |
| Dialog | Add no appearance override | visibility, identity, dismissal, position, scrim, callback, and caller-owned content remain direct overlay contracts |
| AlertDialog | Add a sparse visual override | surface, text, icon, typography, shape, spacing, icon size, and minimum width |
| ModalBottomSheet | Add a sparse override and extend the overlay request protocol | container/content colors, shape, scrim opacity, and tri-state navigation-bar color |

## Required architecture

### Component resolution

Each activated high-level component follows the existing precedence contract:

`semantic Defaults -> outer scoped overrides -> nearest scoped overrides -> instance overrides`

The shared `None` instance remains the no-allocation fast path. Nested providers merge field by
field and restore the previous scope after content returns or throws. Regular and extended FABs,
and top and bottom app bars, use separate public types so callers cannot set fields that the target
component ignores.

### App-bar slot ownership

TopAppBar resolves title, navigation, and action colors independently. It provides the matching
content color while building each slot and supplies a nested IconButton override whose disabled
content derives from the resolved enabled role. An IconButton instance override still wins.
BottomAppBar provides its resolved content color to arbitrary row content and nested IconButtons.

### Modal bottom-sheet request snapshot

The DSL resolves one immutable appearance before submitting the request. The overlay specification
carries that appearance across the session boundary. Both Material 3 and One UI presenters apply
it when showing a platform sheet and on every same-key update. Captured sheet content receives the
resolved content color.

Navigation-bar appearance has three states: inherit the scoped/default value, request an exact
ARGB color, or restore the presenter/platform default. A plain nullable override cannot represent
that contract and is not accepted. Presenter-specific geometry, drag behavior, and other branded
chrome remain downstream recipe details.

### Non-goals

This plan does not:

- add Scaffold or raw Dialog override providers;
- move callbacks, enabled/selected state, overlay visibility, request identity, dismissal policy,
  expansion policy, content, or layout slots into appearance objects;
- add one universal component-style registry or one override type shared by unrelated variants;
- make One UI presenter-specific margins or gestures Foundation theme tokens; or
- infer application capture changes that remain outside State or explicit revision contracts.

## Implementation phases

### Phase 1: component override families

- Add Q2 immutable override values and Q3 scoped providers for regular/extended FAB, top/bottom app
  bars, Badge, and AlertDialog.
- Add resolved internal appearance snapshots to the owning Defaults objects.
- Hard-cut direct low-frequency color parameters from FAB, app-bar, and Badge signatures.
- Make app-bar navigation/action slots consume their resolved content roles.

### Phase 2: modal bottom-sheet protocol

- Add `ModalBottomSheetOverrides`, a tri-state navigation-bar color value, fieldwise provider merge,
  validation, and a resolved appearance snapshot.
- Carry resolved appearance in `ModalBottomSheetOverlaySpec` equality and update semantics.
- Apply container color, shape, scrim, and navigation-bar policy in Material 3 and One UI presenters.
- Provide the resolved content color to captured sheet content.
- Make Material partial-expansion updates reversible when a same-key request changes from skipping
  the partial state back to allowing it.

### Phase 3: downstream migration and verification surfaces

- Migrate Foundation, Material 3, One UI, Preview, Demo, and compiled samples without retaining
  parallel direct appearance paths.
- Keep the refactored Demo's scenario routes and automation tags stable.
- Exercise instance precedence, nested fieldwise scopes, app-bar slot colors, Badge geometry,
  AlertDialog appearance, bottom-sheet theme changes, exact navigation-bar color, and platform
  default restoration.

### Phase 4: documentation, release intent, and closure

- Update ADR-0013 consequences, theming guidance, overlay guidance, the UI Foundation manual, and
  required Simplified Chinese mirrors.
- Add one immutable changeset with every affected published artifact classification.
- Pass focused unit tests, presenter tests, compiled samples, Dokka, documentation gates,
  development-tooling isolation, and `qaQuick`.
- Record comparison context and limitations for any accepted performance or device evidence, then
  archive this plan when no implementation work remains.

## API documentation classification

- Sparse override data classes are Q2 immutable appearance contracts.
- Scoped providers and affected component functions are Q3 because their precedence and nesting
  affect an entire subtree; each links a compiled sample.
- `ModalBottomSheet`, its overlay specification, and presenter interfaces remain Q3 because they
  cross an Android window/session boundary and own update and dismissal lifecycle.
- Applicable contract fields include precedence, units and ranges, inheritance and explicit-null
  semantics, callback and update ordering, configuration/theme changes, Android presenter
  differences, validation failures, and allocation behavior of the `None` path.
- Canonical English KDoc, compiled Q3 samples, UI Foundation and overlay module manuals, release
  intent, and tests land with the hard cut.

## Validation

Focused verification includes:

```bash
./gradlew :viewcompose-ui-foundation:testDebugUnitTest
./gradlew :viewcompose-overlay-android:testDebugUnitTest
./gradlew :viewcompose-overlay-material3-android:testDebugUnitTest
./gradlew :viewcompose-overlay-oneui7-android:testDebugUnitTest
./gradlew :viewcompose-material3:testDebugUnitTest
./gradlew :viewcompose-oneui7:testDebugUnitTest
./gradlew :viewcompose-preview:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :viewcompose-ui-foundation:dokkaGenerateModuleHtml
./gradlew verifyDocumentationStructure verifyDevelopmentToolingIsolation
./gradlew qaQuick
```

## Completion criteria

1. No activated component retains a parallel direct low-frequency appearance parameter.
2. Empty fallback, nested merge, scope restoration, and instance precedence are deterministic for
   every new override family.
3. Top and bottom app-bar child content receives the resolved bar role without Demo workarounds.
4. Modal bottom-sheet appearance follows local and runtime theme changes through presenter update,
   while request identity and nested content state remain stable.
5. Exact and platform-default navigation-bar requests remain distinguishable.
6. Material partial-expansion policy is reversible on a same-key update.
7. Scaffold and raw Dialog gain no speculative override API.
8. Public signatures, KDoc, samples, module manuals, mirrors, release metadata, and tests agree.

## Completion evidence

- Comparison context: before this hard cut, FAB, app-bar, and Badge low-frequency appearance was
  split across primary parameters, AlertDialog had no complete scoped visual contract, and a
  modal-bottom-sheet request did not carry one resolved appearance snapshot through presenter
  updates. After the change, each activated family follows the same semantic-default, outer scope,
  inner scope, and instance precedence contract, while both Android presenters consume the same
  immutable sheet appearance on show and same-key update.
- Absolute verification: the implementation run of `qaQuick` passed 1,619 Gradle tasks in 3 minutes
  56 seconds, and the post-archive rerun passed the same 1,619-task graph in 1 minute 59 seconds.
  The graph includes all repository unit tests, compiled samples, Dokka generation,
  local-publication metadata, dependency contracts, documentation structure and language checks,
  release intent, and development-tooling isolation. Focused Foundation tests cover all five new
  appearance families; one Material and one One UI presenter test exercise sheet show, same-key
  update, navigation-bar policy, partial-state reversal, and dismissal.
- Normalized result: every activated primary DSL now has zero parallel low-frequency appearance
  parameters, both bottom-sheet presenters apply every platform-facing field from the shared
  resolved appearance snapshot, and Scaffold and raw Dialog gained zero speculative override
  families.
- Conclusion: **improved** for API ownership, scope determinism, and cross-session update
  consistency. The verification found no functional regression in repository tests or samples.
- Limitations: Robolectric proves presenter state and Android-window flags but does not establish
  OEM-specific sheet animation, system-bar rendering, or frame-time behavior on physical devices;
  runtime performance impact is therefore **inconclusive**, and no quantitative performance claim
  is made.
- Next action: use the existing Demo feedback-overlay route for optional physical-device visual
  validation. Any future BadgedBox layout option, Scaffold recipe, raw Dialog visual wrapper, or
  presenter-specific sheet chrome must satisfy its own activation trigger and evidence contract
  rather than reopening this archived plan.
