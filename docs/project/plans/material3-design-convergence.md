# Material 3 Design Convergence Plan

## Status

Active. Phase 0 and the low-risk Phase 1 release slice are implemented in the current worktree and
covered by focused unit tests and representative real-renderer visual evidence. Later interaction
and geometry phases remain unscheduled and must start from the baselines defined here.

This is a temporary execution plan and remains canonical English-only under the documentation
governance policy. When the accepted work is complete, durable contracts move into the theme guide
and owning module manuals before this plan moves to `docs/archive/`.

Last verified: 2026-08-06.

Next action: review and release Phase 1, then decide whether the touch-target baseline in Phase 2
has enough product value to enter scheduling. Do not start TextField or custom-control structural
changes before their required current-behavior tests exist.

## Maven release changesets

- `release/changes/20260807-material3-phase1.json`

## Objective

Move ViewCompose's standard Android experience toward the project-pinned Material 3 baseline while
preserving the five-layer architecture:

1. UI Foundation owns design-system-neutral semantic token contracts and component resolution.
2. `viewcompose-material3` owns concrete Material 3 values and Android theme-resource mapping.
3. Android Renderer consumes resolved NodeSpecs and never chooses Material defaults.
4. Core widgets remain native Android View or framework View implementations; convergence does not
   require replacing them with Material Components widgets.
5. High-complexity parity work is retained only when tests and visual/behavioral evidence justify
   its risk.

The baseline is Material Components for Android `1.13.0`, which is the repository dependency at the
time of this audit. This plan targets the standard, non-Expressive Material 3 component set. Material
3 Expressive must be evaluated as a separate versioned migration instead of mixing two token and
geometry systems in one release.

## Scope and non-goals

In scope:

- complete Material 3 color, typography, shape, state, and selected component-sizing tokens;
- Android theme mapping and deterministic light/dark fallback values;
- semantic component defaults whose correction does not change component structure;
- test and diagnostic baselines for touch targets, focus/error states, label behavior, and custom
  control geometry;
- owning module, theme-guide, API-comment, and release documentation.

Not in scope:

- compiler plugins, generated change masks, stability inference, or other Compose compiler work;
- replacing the native Android View engine or renderer with Compose or Material widget rendering;
- reading Android resources from UI Foundation or Android Renderer;
- making Material Components a dependency of UI Foundation or Android Renderer;
- reproducing every Compose Material 3 overload, style object, slot, or animation API;
- adopting Material 3 Expressive tokens or motion in the `1.13.0` standard baseline;
- changing component structure without a baseline, independently revertible implementation, and
  explicit keep/revert decision;
- keeping low-value visual parity whose maintenance cost exceeds a demonstrated usability or
  consistency benefit.

## Audit baseline

The audit used current source, tests, the local Material Components `1.13.0` resources, and the
current five-layer architecture boundary. The core renderer has no direct Material widget usage.
Material Components is confined to `viewcompose-material3` and explicitly Material-backed overlay
integration.

### Token and bridge gaps found

| Area | Previous state | Material 3 consequence | Decision |
| --- | --- | --- | --- |
| Shape scale | Only `small / medium / large` | Extra-small fields, extra-large dialogs, and full/pill controls had to share incorrect roles | Add the six-role semantic scale in Phase 1 |
| Type scale | Only `title / body / label` families | Display and headline roles could not be represented; dialog titles borrowed title roles | Add all 15 standard roles in Phase 1 |
| Static fallback | Bridge fell back to framework-branded defaults | Missing Android attributes produced a non-Material theme | Add deterministic Material light/dark snapshots in Phase 1 |
| Shape reader | Read generic component shapes | It could not preserve the complete Material corner scale | Read `shapeAppearanceCornerExtraSmall` through `ExtraLarge` in Phase 1 |
| Type reader | Read nine Material roles plus legacy aliases | Display/headline customizations were silently lost | Read all 15 Material text appearances in Phase 1 |
| Control sizing | Used framework values after bridge fallback | Common heights, paddings, progress sizes, FAB icon size, search elevation, and badge dot size drifted | Supply a Material sizing profile in Phase 1; keep touch/visual separation for Phase 2 |

The `UiShapes` and `UiTypography` expansions are Q2 public value-contract changes: they are
immutable, contain no lifecycle or mutable ownership, and preserve source construction through
derived defaults. Canonical-English KDoc and owning-module documentation are required. Q3 compiled
samples are not required because construction and lookup are direct value selection without a
protocol, lifecycle, or failure contract.

### Component-default gaps found

| Component family | Previous mismatch | Phase 1 resolution |
| --- | --- | --- |
| Button and IconButton | Small rounded shape, wrong outlined content, opaque disabled roles, filled IconButton default | Full shape, primary outlined content, 12% disabled container/border, 38% disabled content, standard icon-only default |
| Chip and SegmentedControl | Selected colors and shapes used general primary/surface roles | Secondary-container selection roles, correct content roles, small/full shapes, Material disabled opacity |
| TextField | Medium text role, generic surface/error container, error-colored editable text and placeholder, small shape | Body-large editing, surface-container roles, error limited to label/support/border, extra-small shape |
| Checkbox, Radio, Switch, Slider | Unchecked, disabled, thumb, track, and active-track roles drifted | Material semantic colors and opacity values; exact geometry remains test-first work |
| Progress | Track used outline variant | Secondary-container track |
| Card | Filled/elevated/outlined containers, elevation, and outline role drifted | Highest/low/surface containers, 1dp elevation, outline-variant border |
| Dialog, menu, tooltip | Wrong typography or shape tier | Headline-small dialog, extra-large dialog, extra-small menu/tooltip, body-small tooltip |
| Search and navigation surfaces | Generic surface variant or surface roles | Full search shape, high/container surface roles |
| FAB | Hard-coded radii bypassed theme shapes | Medium/large/extra-large semantic shape roles |
| State layer | Pressed overlay was about 13% | Standard 10% pressed overlay baseline |

## Priority and scheduling decision

| Priority | Work item | Expected value | Complexity/risk | Decision |
| --- | --- | --- | --- | --- |
| P0 | Complete type/shape contracts and Material fallback | Very high; removes structural impossibility | Low to medium; public alpha API | Implemented in Phase 1 |
| P0 | Android bridge completeness | Very high for app theme fidelity | Medium; resource parsing | Implemented with Robolectric and mapper tests |
| P0 | Low-risk semantic default corrections | High visible consistency | Low; no new component state machine | Implemented in Phase 1 |
| P1 | Minimum 48dp touch targets separated from visual bounds | High accessibility value | Medium; hit testing/layout interaction | Baseline first in Phase 2 |
| P1 | Focused/hovered/pressed state-layer fidelity | High interaction consistency | Medium; state propagation | Baseline first in Phase 2 |
| P1 | TextField floating label, focused indicator, and focused/error precedence | High for a common control | High; structure, focus, measurement, animation | Separately revertible Phase 3 experiment |
| P2 | Switch and Slider exact geometry and motion | Medium visual value | High; custom drawing and interaction | Conditional Phase 4 experiment |
| P2 | Full state-by-state golden screenshot matrix | Medium regression value | Medium infrastructure cost | Add only for components entering structural phases; keep the representative Phase 1 release screenshots below |
| Deferred | Material 3 Expressive | Unknown until a version decision | High and cross-cutting | Separate future migration |
| Rejected | Wholesale Material widget replacement | Low relative to boundary damage | Very high | Do not implement |

## Phase 0: Baseline and diagnostics

Status: complete for Phase 1 scope; required extensions remain before later phases.

Completed evidence:

- direct Material dependency and native View mapping inventory;
- exact `1.13.0` standard Material color, type, and corner-resource audit;
- mapper tests for complete typography and shape roles;
- deterministic static-token tests and component-default semantic tests;
- existing token-consumption audit updated so every new public role is consumed or explicitly
  reserved.

Before Phase 2 or later work, add diagnostics/test fixtures that can record:

- visual bounds, measured bounds, semantic bounds, and effective touch bounds;
- focused, hovered, pressed, selected, checked, enabled, and error state precedence;
- TextField label position, indicator width/color, cursor color, and supporting-text layout;
- Switch thumb/track dimensions and Slider thumb/active/inactive track geometry;
- density, font scale, layout direction, light/dark mode, enabled state, and API level for visual
  evidence.

Diagnostics must remain test-only or debug-only and must not add an always-on event history.

## Phase 1: Token, bridge, and semantic-default convergence

Status: implemented; awaiting review and release.

Implemented work:

1. Expand `UiShapes` to extra-small, small, medium, large, extra-large, and full while preserving
   three-tier construction defaults.
2. Expand `UiTypography` to the complete 15-role Material-compatible type scale while preserving
   existing construction defaults.
3. Add `Material3ThemeDefaults.light()` and `dark()` as deterministic standard Material snapshots.
4. Make the bridge read all five absolute Material corner roles and all 15 text appearances.
5. Retain legacy Android large/medium/small text appearances as title/body/label family fallbacks;
   new display and headline roles keep the complete Material fallback when their Material
   appearances are absent.
6. Apply the low-risk semantic default corrections recorded in the audit table.
7. Pin exact fallback values and component role selection with unit and Robolectric tests.
8. Add a dedicated real-Android-Renderer fixture and device test for representative actions,
   inputs, surfaces, typography, progress, and navigation in deterministic Material light/dark
   themes plus the application's Android-theme bridge.

Visual acceptance scope:

- capture `actions`, `inputs`, and `surfaces` in both deterministic Material light and dark themes;
- capture `surfaces` once through the host-provided Android theme bridge;
- assert that the representative tagged controls are fully visible before every capture;
- store a metadata sidecar beside every screenshot with device model, API level, density, font
  scale, locale, layout direction, system night mode, selected theme, and Material Components
  baseline;
- treat these seven release-review images as representative acceptance evidence, not as the full
  state-by-state golden matrix reserved for structural phases.

Reproduce the evidence on an awake, unlocked device:

```shell
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.Material3VisualBaselineUiTest
adb pull /sdcard/Download/viewcompose-material3-visual-baseline \
  app/build/reports/material3-visual-baseline
```

Latest execution evidence, 2026-08-06:

- `Material3VisualBaselineUiTest` passed on the Pixel 9a Android 15 AVD through the real Android
  View renderer;
- the evidence environment was API 35, 420 dpi, font scale 1.0, `en-US`, LTR, and system light
  mode, with Material Components `1.13.0` as the pinned comparison baseline;
- all required tagged anchors were fully visible before capture;
- visual review accepted the six deterministic Light/Dark images and the custom Android-theme
  bridge image with no clipping, overlap, unintended wrapping, stale transition frame, or
  light/dark role inversion;
- an initial run exposed an Activity enter-transition frame and narrow fixture cards; the fixture
  now waits for compositor transitions and gives card rows the full available width before evidence
  is captured;
- screenshots remain generated test artifacts rather than committed golden files. Each run exports
  seven PNG files plus seven metadata sidecars to the reproducible output directory above.

This records implementation-side visual acceptance. The release owner's review of the same images
remains a separate completion gate for visible alpha defaults.

Completion gate:

- UI Foundation and Material 3 unit tests pass;
- documentation, API-comment, Changeset, and repository quality gates pass;
- no Material dependency enters UI Foundation or Android Renderer;
- current applications can still override explicit component values and sparse local colors;
- the seven real-renderer screenshots and their environment sidecars are produced from the current
  revision without clipped required anchors;
- release review explicitly accepts the visible default changes for the alpha line after reviewing
  the Light/Dark pairs and Android-theme bridge image.

## Phase 2: Touch targets and interaction states

Do not begin with production behavior changes.

Required baseline:

1. Cover Button, IconButton, Chip, Checkbox, RadioButton, Switch, Slider, navigation destinations,
   and any other clickable compact control at 1.0x and 1.3x font scale.
2. Assert separately the visual container and effective touch target; a 40dp visual button inside a
   48dp touch target must not be represented as one ambiguous size.
3. Cover overlapping expanded hit regions and define deterministic target selection.
4. Verify semantics bounds, accessibility focus, ripple clipping, parent clipping, scrolling,
   minimum-size overrides, and explicit compact application layouts.
5. Record current behavior before adding a touch-delegate, outer node, or renderer mechanism.

Keep the implementation only when it provides at least 48dp effective targets for standard
Material components without changing visual geometry, stealing adjacent input, breaking scrolling,
or weakening explicit sizing. If no small, renderer-neutral contract can satisfy those conditions,
retain the tests and document the limitation instead of adding a Material-only renderer branch.

Interaction-state work in this phase may add missing focused and hovered state layers only after
the full precedence matrix is executable. Do not encode focus color guesses in Android Renderer.

## Phase 3: TextField structural fidelity

Required baseline:

- empty/unfocused, empty/focused, populated/unfocused, populated/focused, disabled, read-only, error,
  single-line, multiline, leading/trailing content, supporting text, RTL, and runtime state changes;
- cursor and selection behavior, IME interaction, font scale, and save/restore;
- measurement and layout evidence for label position and content insets;
- failure/rollback coverage proving that a rejected frame does not leave an orphan label or stale
  native focus state.

Experiment scope:

- focused indicator/border width and color;
- floating-label placement and transition;
- correct focus/error/disabled precedence;
- supporting-text spacing and semantics.

Keep only if the result is materially closer to the pinned Material TextField behavior, remains
accessible, preserves current state ownership and renderer rollback, and does not require Material
Components inside UI Foundation or Android Renderer. Otherwise revert the structural code and keep
the baseline tests and recorded result.

## Phase 4: Conditional custom-control geometry

Switch and Slider exact geometry, state-specific dimensions, and motion are conditional. Begin only
if product review finds the current native/custom rendering visibly inadequate after Phase 1 color
correction.

Required evidence:

- screenshot or geometry comparisons for every enabled/disabled and checked/unchecked state;
- touch, keyboard, accessibility, RTL, and density coverage;
- frame and allocation comparison for animated interaction;
- isolated commits so each control can be reverted independently.

Do not keep a custom drawing state machine for a difference that is not visible at normal density
or does not affect accessibility. Do not make Switch and Slider share geometry merely because they
share colors.

## Explicitly rejected or deferred alternatives

### Replace core controls with Material Components widgets

Rejected. It would couple Android Renderer and UI Foundation to one design system, alter native
View identity and patch behavior, increase transitive dependencies, and make non-Material themes a
special case. The current token adapter plus semantic NodeSpec boundary is the intended design.

### Put concrete Material defaults in Android Renderer

Rejected. Renderer-owned defaults would bypass application themes, make screenshots dependent on
bind paths, and violate `Theme -> Defaults -> NodeSpec -> Renderer`.

### Add complete per-component Material style objects

Rejected for the current scope. Sparse semantic tokens and explicit component parameters already
cover customization. Add a new public component contract only when an actual missing behavior
cannot be expressed safely; do not mirror Compose API count.

### Adopt Material 3 Expressive incrementally

Deferred. Expressive changes type, shape, motion, and component geometry as a coordinated system.
Mixing isolated Expressive defaults into the standard `1.13.0` baseline would create a theme that
matches neither system.

### Force every reserved color role onto an existing component

Rejected. `errorContainer`, `onErrorContainer`, tertiary, inverse, application-semantic, and unused
surface roles remain valid palette entries even when no current core component has a semantically
correct consumer. The token audit allowlist is preferable to artificial use.

## Validation and rollback policy

Every retained phase must satisfy:

- focused unit or instrumentation coverage of the changed contract;
- light and dark themes plus at least one custom Android theme;
- 1.0x and 1.3x font scale for structural component changes;
- LTR and RTL where geometry or logical placement changes;
- no new Material dependency outside the design-system and explicit Material integration layers;
- public API quality, module documentation, localization, and release Changeset gates;
- an independently revertible implementation for Phase 2 or later structural work.

If a structural experiment fails its keep rule, revert production code. Preserve independently
useful baselines, diagnostics, screenshots, and the rejection decision in this plan. A reverted or
untriggered conditional item does not block plan completion.

## Completion criteria

This plan is complete when:

1. Phase 1 is released with all gates passing.
2. Phase 2 is either accepted with evidence or explicitly declined after baseline/product review.
3. TextField and custom-control experiments are either accepted with their keep gates satisfied or
   recorded as deferred/rejected without residual production complexity.
4. Durable token, bridge, component-default, accessibility, and compatibility conclusions are in
   active guides and module manuals.
5. The evidence ledger records final keep/revert decisions and the plan is archived before the
   affected Maven Central publication gate.

## Evidence ledger

| Date | Phase | Evidence | Decision |
| --- | --- | --- | --- |
| 2026-08-06 | 0 | CodeGraph/source inventory and local Material Components `1.13.0` resource audit | Standard non-Expressive baseline selected |
| 2026-08-06 | 1 | UI Foundation unit suite, Material mapper/static-token/Robolectric tests | Low-risk implementation retained pending release review |
