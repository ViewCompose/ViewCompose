# Multi-Design-System and High-Fidelity Theme Plan

## Status

Active. Phases 0 through 8 are implemented on `codex/multi-design-system-foundation`. Phase 8 closes
the retained native-behavior parity gaps exposed by the current design-system-owned controls. The internal
pressure slice now exercises three visually distinct design-system bundles through shared Basic
primitives and design-system-owned composites. Its behavior and screenshot matrix pass on API 24,
31, 35, and 36 emulators. Emulator benchmark traces are reproducible but remain non-representative
for release performance acceptance; physical-device and Samsung visual acceptance remain
release-owner gates rather than reasons to broaden the architecture. The public
`viewcompose-oneui7` artifact now supplies a deliberately bounded five-component alpha without
adding Samsung policy to UI Foundation, Android Renderer, or the default Material aggregate.

This plan is canonical English-only under the documentation governance policy. It records both the
target architecture and the staged evidence required before public APIs or production rendering
behavior are retained. Durable accepted contracts must move into the architecture guide, theme
guide, and owning module manuals before this plan is archived.

Last verified: 2026-08-09.

Next action: complete release-owner Pixel and physical Samsung acceptance, run formal
physical-device benchmarks, and publish the
bounded One UI 7 alpha through Maven. The release workflow must archive this plan only after every
linked changeset is included and immediately before Maven upload.

## Maven release changesets

- [`20260809-multi-design-system-high-fidelity.json`](../../../release/changes/20260809-multi-design-system-high-fidelity.json)
  is the single PR-owned release intent. It covers the Phase 2 neutral Surface and Button
  foundations, Phase 3 semantic motion and compatible shape interpolation, and the Phase 6 public
  One UI 7 five-component alpha artifact. Reverse-dependency propagation remains release-planner
  owned.

## Objective

Make ViewCompose capable of hosting multiple high-fidelity design systems on the Android View
engine while preserving one shared runtime, declarative tree, renderer contract, diagnostics
model, and accessibility baseline.

The target is not a larger color theme. A design system may independently define:

- foundation tokens such as color, typography, density, shape, motion, elevation, and effects;
- component recipes that resolve variants, states, sizes, decoration, and content arrangement;
- composite components whose structure is specific to that design language;
- custom Android View drawing when native widgets cannot reproduce the required geometry;
- explicit fidelity and fallback behavior by Android API level and device capability.

Success means that Material 3, One UI, Cupertino-inspired, or a product-owned design system can
share ViewCompose infrastructure without requiring Android Renderer to identify the active design
system and without turning `UiThemeTokens` into one union of every component policy.

## Feasibility decision

Goal B is feasible on Android View when high fidelity is defined as a layered capability rather
than universal pixel identity on every API level and OEM implementation.

Android View provides sufficient primitives for custom layout, Canvas and Path drawing, native
text input, accessibility nodes, pointer/focus input, RenderNode effects, and platform-gated
rendering. The difficult areas are not general drawing capacity; they are consistency across API
levels, OEM-native widget geometry, IME/accessibility preservation, background blur semantics,
and the cost of owning custom controls.

The accepted product policy is:

- shape morph may use a discrete shape transition or no animation when the required interpolation
  path is unavailable or fails the component's performance gate;
- glass, acrylic, and backdrop blur may degrade to a translucent tinted surface, cached snapshot
  blur, or no blur according to the component's declared fallback;
- a degraded effect must preserve content contrast, hit targets, focus, semantics, and layout;
- diagnostics and screenshot metadata must identify the selected implementation and fallback;
- unsupported fidelity must never silently change component structure or input ownership.

## Architectural invariants

Every implementation phase must preserve these boundaries.

1. **Foundation tokens remain immutable data.** `UiThemeTokens` describes reusable semantic
   values. It must not contain component factories, Android resources, callbacks, design-system
   identities, or a union of every design system's component variants.
2. **Component recipes are separate from foundation tokens.** Recipes select component values and
   structure from tokens. They use typed, immutable data and stable identity; arbitrary behavior
   closures are not stored in the theme snapshot.
3. **Resolved NodeSpecs remain design-system neutral.** UI Foundation or a design-system module
   resolves policy before emission. Android Renderer receives colors, paths/shapes, measurements,
   state-layer values, draw/effect descriptions, semantics, and callbacks, never `Material3`,
   `OneUi`, or `Cupertino` branches.
4. **Simple and structural components are treated differently.** A shared primitive may consume a
   recipe when design systems differ only in values and decoration. A design-system-owned
   composite is preferred when content order, slot model, gesture semantics, or state machine
   differs materially.
5. **Native behavioral cores are preserved where valuable.** Text editing retains `EditText`
   behavior for IME, cursor, selection, autofill, and accessibility while the framework may own
   surrounding decoration. A custom-drawn control must reproduce the applicable interaction and
   accessibility contract before replacing a native control.
6. **Capability fallbacks are explicit data.** API/OEM capability detection belongs to Android
   Engine or an integration adapter. Components consume a renderer-neutral resolved strategy and
   record the result in debug/test diagnostics.
7. **Runtime switching is a root lifecycle operation.** Initial public support may rebuild the
   root/session under a new provider. In-place mutation of an existing design-system object is not
   required and must not introduce mixed old/new overlay or lazy-content state.
8. **No renderer registry without evidence.** Existing generic nodes, Surface, Canvas, graphics,
   and custom View backends are used first. A public renderer/plugin registry requires at least two
   independently implemented design systems that cannot be expressed safely through those paths.
9. **Material 3 remains the standard adapter, not the universal model.** Current Material defaults
   and public APIs keep their compatibility behavior. New shared contracts use semantic concepts
   that can be demonstrated by the internal non-Material fixture.
10. **Accessibility and diagnostics are architecture, not cleanup.** Every new primitive or effect
    exposes measurement, state, semantics, fallback, and performance evidence before visual polish
    is accepted.

## Target layering

| Layer | Owns | Must not own |
| --- | --- | --- |
| Runtime and UI Contract | state, identity, immutable resolved NodeSpecs, renderer/host contracts | design-system policy or Android resources |
| UI Foundation | semantic foundation-token contracts, locals, basic interaction/layout primitives, design-system-neutral components | Material resource lookup or named non-neutral recipes |
| Android Engine and Renderer | View creation/patching, Canvas/Path/effect execution, input, focus, accessibility, capability probes | active design-system selection or semantic token resolution |
| Design-system modules | concrete token profiles, component recipes, design-system composites, conformance declarations | direct renderer branching or runtime ownership |
| Integration modules | Android theme/resource mapping, host installation, overlay and system-UI integration | reusable component policy that belongs to a design system |

The intended resolution flow is:

```text
Application design-system provider
  -> immutable foundation tokens + typed component recipes + capability policy
  -> Basic or design-system-owned component
  -> resolved design-system-neutral NodeSpec
  -> Android Renderer / custom View backend
  -> native View tree and diagnostics
```

## Conformance and fallback model

Every capability entering a public design-system module must declare one of these outcomes for the
active environment:

| Outcome | Meaning | Acceptance rule |
| --- | --- | --- |
| Exact | Geometry, state, motion, and effect match the pinned reference | Screenshot/behavior baselines pass on the declared matrix |
| Equivalent | Different implementation preserves the intended appearance and interaction | Difference is documented and manually accepted |
| Degraded | A named fallback is selected | Contrast, semantics, input, and layout remain correct; diagnostics expose the fallback |
| Unsupported | No safe presentation exists | Component omits only the optional effect or uses a documented static alternative; never crashes |

Fallback selection is deterministic for the same API, capability snapshot, theme, and component
state. A fallback may reduce an optional visual effect but must not reduce the 48dp target policy,
remove labels, hide focus, change enabled state, or weaken accessibility semantics.

Initial advanced-effect policy:

| Feature | Preferred path | Required fallback |
| --- | --- | --- |
| Continuous/superellipse corners | framework Path geometry and matching clip/border/hit outline | rounded rectangle using the closest semantic radius |
| Shape morph | parameterized compatible-path interpolation | discrete shape transition or static destination shape |
| Foreground/content blur | platform RenderEffect when supported and accepted | pre-rendered/cached blur or translucent tint |
| Backdrop blur/glass/acrylic | supported window or snapshot-backed implementation | translucent tinted surface with sufficient contrast |
| Dynamic shader effect | runtime shader on supported devices | gradient, solid fill, or omitted decorative effect |
| Spring motion | bounded physical solver with interruption policy | duration/easing transition or immediate state update under reduced motion |

Platform behavior must be verified against the official Android references for
[custom drawing](https://developer.android.com/develop/ui/views/layout/custom-views/custom-drawing),
[Path](https://developer.android.com/reference/android/graphics/Path),
[RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect),
[cross-window blur](https://developer.android.com/reference/android/view/WindowManager), and
[custom View accessibility](https://developer.android.com/guide/topics/ui/accessibility/views/custom-views).

## Reference component set

Architecture work uses five components that expose different risks. A phase cannot claim general
design-system support by demonstrating Button alone.

| Component | Why it is required |
| --- | --- |
| Button | value recipe, state layers, shape, target/visual-bound separation, icon/text arrangement |
| Switch | custom geometry, drag/click behavior, animation, checked semantics, OEM independence |
| TextField | hybrid native editing core, decoration structure, focus/error precedence, IME and autofill |
| NavigationBar | selection, destinations, indicator geometry, labels, insets, composite structure |
| Surface/Card | shared shape, border, clip, shadow, gradient/effect, and fallback foundation |

The internal reference fixture must use a deliberately non-Material palette, shape language,
density profile, and component arrangement so accidental reuse of Material defaults remains
visible. It is a test/diagnostic design system, not a publishable product API.

## Phase 0: Architecture characterization and baselines

Status: complete for the pre-production baseline.

Scope:

1. Record this plan and register it in the active-plan index.
2. Pin the invariants above with source-boundary and context-propagation tests.
3. Record current NodeSpec and renderer behavior for the five reference components.
4. Inventory every component default that currently derives policy directly from `Theme` and
   classify it as foundation value, simple recipe, structural recipe, or platform behavior.
5. Define screenshot metadata for design-system identity, token origin, recipe identity,
   capability result, conformance outcome, API, OEM, density, font scale, layout direction, and
   light/dark mode.
6. Establish performance baselines for tree build, retained-View patching, custom draw allocation,
   and representative animation frame time before new custom controls are introduced.

Keep criteria:

- no public API or production rendering behavior changes;
- tests demonstrate that a typed recipe context can be nested, captured, and restored separately
  from the theme snapshot;
- current Material 3 output and the accepted interaction/touch-target contracts remain unchanged;
- the five reference components have reproducible baseline owners and evidence paths;
- the module impact and public-API strategy for Phase 1 are reviewed before production work.

Rollback: documentation and characterization tests are independently revertible. A failed
experiment does not authorize changes to `UiThemeTokens`, NodeType, or Android Renderer.

### Phase 0 implementation evidence

- `DesignSystemRecipeContextCharacterizationTest` proves that typed immutable recipe values can be
  nested and restored independently of `UiThemeTokens`, captured together as one coherent
  `UiLocalSnapshot`, and compared by value for delayed-content invalidation.
- `ThemeRecipeBoundaryGuardTest` rejects recipe/design-system fields and behavior closures on
  `UiThemeTokens`.
- `DesignSystemIsolationGuardTest` rejects concrete Material 3, One UI, Cupertino, or
  design-system-identity branches in Android Renderer production source.
- The existing Material 3 real-renderer fixture is the current five-component visual baseline:
  `Material3VisualBaselineUiTest` captures Button/Card, Switch/TextField, and NavigationBar in
  Light/Dark modes, and `Material3TouchTargetBaselineUiTest` records current effective, visual, and
  semantic bounds plus the isolated theme-source matrix.
- Current unit owners are `ButtonTest` and `ButtonStateLayerRenderingTest`, `InputControlTest` and
  `NativeInputColorBindingTest`, `TextFieldTest` and `TextFieldControllerTest`,
  `AdditionalWidgetCoverageTest` and `NavigationContainerInvalidationTest`, plus `ThemeTest`,
  `CompositeStateLayerTest`, and `UiShapeAndroidBridgeTest` for Surface/Card geometry and state.
- `viewcompose-benchmark` and `docs/tooling/performance.md` remain the canonical macrobenchmark
  runner and comparison policy. Phase 1 may reuse the recorded release baseline because its first
  experiment is internal and must add no measurable cost to the Material path. Component-specific
  draw and animation scenarios become mandatory before Phase 2 custom geometry is retained.

### Current default-policy inventory

This inventory classifies policy ownership; it does not require every listed default object to be
replaced. Migration happens only when the internal contrast design system proves value.

| Classification | Current families | Phase direction |
| --- | --- | --- |
| Foundation values | `TextDefaults`, `IconDefaults`, `DividerDefaults`, `ScaffoldDefaults` | Keep semantic role lookup in foundation tokens; do not create component recipes without a structural or policy difference |
| Shared surface foundation | `SurfaceDefaults` | Move resolved fill/border/shape/clip/state/shadow/effect ordering into the Phase 2 surface contract while preserving current overloads |
| Simple value recipes | `ButtonDefaults`, `IconButtonDefaults`, `ChipDefaults`, `FabDefaults`, `CardDefaults`, `BadgeDefaults`, `ProgressIndicatorDefaults`, `PullToRefreshDefaults`, `TooltipDefaults` | First candidates for immutable typed recipes over shared Basic primitives |
| Structural/composite recipes | `SegmentedControlDefaults`, `TextFieldDefaults`, `SearchBarDefaults`, `NavigationBarDefaults`, `TopAppBarDefaults`, `BottomAppBarDefaults`, `TabRowDefaults`, `ListItemDefaults`, `AlertDialogDefaults`, `DropdownMenuDefaults`, `ModalBottomSheetDefaults` | Allow design-system-owned composition or decoration; do not force one universal variant/slot union |
| Platform-behavior adapters | `InputControlDefaults` with native Checkbox, RadioButton, Switch, and Slider; `BasicTextField` with `EditText`; overlay hosts and window presentation | Keep platform input/lifecycle/accessibility ownership below recipes; replace drawing only after behavioral parity tests |

The inventory exposes the main current limitation: high-level defaults select both semantic values
and Material-shaped variants directly from `Theme`. Phase 1 must separate that policy for the
smallest action/surface slice without rewriting every default object or changing renderer input.

## Phase 1: Recipe boundary and internal contrast design system

Status: complete in test source; no production or publication input changed.

Build the smallest internal architecture experiment that can express two visibly different
component policies over the same runtime and renderer contracts. The experiment remains outside
the Maven production artifact. Only the Basic primitives and recipe values that survive the full
five-component contrast fixture may enter production in Phase 2.

Planned work:

1. Introduce a test-only typed recipe-provider boundary separate from `UiThemeTokens`.
2. Give each recipe set stable value identity suitable for LocalSnapshot and delayed-content
   invalidation; do not use mutable singletons or compare function instances.
3. Resolve one simple action component and one Surface/Card path through immutable recipe data.
4. Implement an internal contrast design system in tests or the demo diagnostic source. It must
   differ from Material in geometry and role selection, not only color.
5. Verify nested providers, lazy content, overlays, recomposition, save/restore, retained patches,
   and root replacement without mixed recipe state.
6. Keep all recipe types test-only until the five-component vertical slice proves the shared
   semantic vocabulary. A design-system module may later own its provider rather than forcing one
   universal bundle into UI Foundation.

Public API rule: if cross-module implementation requires a public/protected alpha contract, assign
its Q level before implementation and include canonical-English KDoc, compiled Q3 samples where
applicable, owning-module documentation, and an immutable Maven changeset. Prefer an experimental
package or internal adapter over a premature universal component hierarchy.

Keep criteria:

- `UiThemeTokens` remains free of recipe/factory fields;
- two recipe providers can share identical foundation tokens and still emit different resolved
  NodeSpecs;
- emitted NodeSpecs contain no design-system identity;
- Android Renderer has no design-system conditionals or new Material dependency;
- LocalSnapshot and delayed-content tests demonstrate deterministic invalidation;
- the experiment adds no production bytecode, lookup, or steady-state allocation to trees that use
  only Material 3.

Rollback: remove the recipe provider and internal contrast fixture while retaining Phase 0
baselines. Do not retain an unused public marker API.

Current evidence:

- the test-only Action and Surface recipes contain only fully resolved immutable values;
- rounded and cut-corner recipe bundles use the same `UiThemeTokens` but emit visibly different
  generic Button and Surface contracts;
- recipe identity is present only in the Local diagnostic context and is absent from emitted
  NodeSpecs;
- disabled Action values remove interaction layers deterministically;
- installing the experimental provider does not change existing Button or Surface defaults;
- a framework-composed Switch expresses independent track/thumb geometry, rounded or cut shapes,
  leading or trailing control placement, 48dp-or-larger effective targets, state layers, and merged
  checked semantics through generic Row/Box/Text nodes. It deliberately does not use
  `ToggleNodeProps` or the OEM-native `android.widget.Switch` path;
- current `ToggleNodeProps` carries tint and checked state but no track/thumb geometry, shape, or
  motion contract. It remains suitable for native themed controls, not the high-fidelity custom
  Switch target;
- both TextField recipes retain `BasicTextField`, `TextFieldState`, and the native editing node for
  IME, selection, cursor, autofill, receive-content, and undo ownership. One recipe emits an
  external stacked label while the other uses placeholder-label structure; error border and
  semantic error resolution remain recipe-owned decoration;
- one NavigationBar recipe retains the existing fixed navigation node while the contrast recipe
  emits generic destination containers with a cut indicator and selected-only labels. The latter
  demonstrates that structural choice belongs above NodeSpec resolution and does not require an
  Android Renderer design-system branch;
- current `NavigationBarNodeProps` does not carry indicator shape, label visibility policy, or a
  general destination layout contract. Extending it into a union of every navigation structure is
  rejected; materially different bars remain design-system-owned composites;
- the combined fixture emits Button, Surface/Card, Switch, TextField, and NavigationBar behavior
  over one typed recipe Local, and no emitted NodeSpec contains recipe or design-system identity;
- no production source, public API, publication input, or Maven changeset is added by the
  experiment.
- focused UI Foundation and Android Renderer tests, `verifyDocumentationStructure`, and `qaQuick`
  passed on 2026-08-07. The full quick gate was repeated after the five-component fixture and
  executed 1,359 tasks successfully.

### Phase 1 production decision

The five-component fixture does not justify a public universal `ComponentRecipes` bundle. A
design-system module can own its typed recipe Local and resolve values before calling shared Basic
primitives or its own composites.

Production candidates, in order:

1. **Interactive Basic surface:** highest reuse across action containers, Card, navigation
   destinations, chips, and custom controls. It must unify resolved fill, shape, clip, border,
   state layer, click semantics, and effective/visual bounds without selecting a design system.
2. **Existing BasicTextField:** retain and document it as the shared editing core; add no competing
   text editing primitive. Design-system modules own label and supporting decoration.
3. **Basic toggle:** conditionally build on the surface/semantics foundation after device tests
   cover click, keyboard/d-pad, TalkBack actions, checked announcement, RTL, overlapping targets,
   drag policy, animation, and state restoration.
4. **Action composites:** first attempt to build design-system actions from the interactive surface
   plus Row/Text/Icon. Keep the existing Button node for current Material/native compatibility;
   add a lower-level Button transport only if the composite path fails measurable behavior or
   performance gates.
5. **Navigation composites:** keep structure in each design-system module. Do not add one universal
   BasicNavigationBar or expand `NavigationBarNodeProps` until two independent systems demonstrate
   the same missing renderer transport.

`BasicSurface` is therefore the first Phase 2 production experiment. Before implementation, assign
Q levels to every proposed public value and component API, define exact modifier precedence, add a
compiled Q3 sample, and record a publication changeset in a release-scoped execution slice.

## Phase 2: Basic components and unified surface/shape foundation

Status: complete. The production slice implements Q3 `BasicSurface`, Q2
`BasicSurfaceStyle`/`SurfaceNodeProps`, gradient fills, continuous corners, and effective-versus-
visual bounds. Q3 `BasicButton` and Q2 `BasicButtonStyle` now provide the shared action composite
used by the internal contrast fixture. A public Basic toggle was rejected: the second system proved
that control structure and placement remain recipe-owned while existing surface, semantics, motion,
and native input contracts already supply the reusable core.

Planned work:

1. Add or extract `BasicSurface`, `BasicButton`, and `BasicToggle`-level primitives only where the
   internal contrast fixture proves a shared behavioral core.
2. Preserve `BasicTextField` as the native editing core and separate text editing from decoration
   recipes rather than replacing `EditText` behavior.
3. Unify background fill, gradient/brush, border, clip path, state layer, shadow, and optional
   effect ordering into one resolved Surface contract.
4. Add continuous-corner Path support with the same geometry used for fill, border, ripple clip,
   shadow strategy, and diagnostics.
5. Separate effective target bounds, visual surface bounds, semantic bounds, and clip bounds for
   custom and composite controls.
6. Validate RTL, font scale, parent clipping, scrolling, nested click targets, and accessibility
   traversal before migrating existing public components.

Keep criteria:

- primitives reduce duplicated component policy or geometry across at least two design systems;
- public Material component behavior remains source-compatible and visually accepted;
- no per-frame Path, shader, drawable, or collection allocation in stable animated/drawn states;
- custom geometry produces one consistent fill/border/clip/hit diagnostic model;
- Basic components do not expose a Material-named variant vocabulary.

Rollback: retain the validated geometry utilities only if they independently improve current
Surface correctness. Revert a Basic primitive that merely wraps the existing high-level component
without enabling a second design system.

## Phase 3: Motion scheme, shape transition, and reduced motion

Status: complete. The semantic motion and shape fallback contracts are integrated into the
five-component slice and covered by deterministic unit, interaction, reduced-motion, and emulator
evidence.

Planned work:

1. Add immutable duration, easing, spring, interruption, and reduced-motion policy tokens separate
   from component structure.
2. Establish one lifecycle-owned animation runner shared by custom components; do not place
   animation loops inside recipes.
3. Implement a bounded spring solver only when it improves Switch/Button/indicator fidelity over
   the current duration approximation and meets deterministic test gates.
4. Add compatible-geometry shape interpolation and the discrete/static fallback policy.
5. Test cancellation, reversal, rapid state changes, detach/reattach, reduced motion, and saved
   state without retaining obsolete callbacks or Views.

Keep criteria:

- animation state remains caller-owned or renderer-owned according to the existing lifecycle;
- interruption and reverse transitions are deterministic;
- reduced motion removes non-essential movement without hiding state changes;
- frame-time and allocation baselines remain within the budget recorded in Phase 0;
- shape morph failure selects the documented fallback without visual corruption.

Rollback: keep static shape support and duration/easing transitions; remove the physical solver or
morph path independently if it does not improve fidelity or performance.

### Phase 3 implementation decision

- `MotionScheme` separates five semantic roles, interruption policy, and reduced-motion policy from
  component structure. It resolves to existing immutable `AnimationSpec` values and owns no clock
  or coroutine.
- Existing `Animatable`, target-as-state APIs, and `Transition` remain the shared lifecycle-owned
  runners. Their last-writer cancellation and stale-frame rejection already satisfy the required
  retargeting contract, so a parallel component loop was rejected.
- The existing bounded, duration-based `SpringSpec` remains the fallback spring model. No measured
  fidelity or performance evidence justified adding a physical solver in this phase.
- `interpolateUiShape` interpolates matching corner families with matching absolute/relative size
  representations. Any mismatch returns an attributable discrete endpoint fallback; arbitrary Path
  Morph remains explicitly unsupported.
- Unit evidence covers semantic role selection, snap/shorten reduced motion, recursive keyframe
  scaling, compatible absolute/relative interpolation, clamped progress, and incompatible-family
  fallback. Cancellation, reversal, detach, saved state, and visual timing stay in the Phase 4
  component/emulator matrix where an actual owner and rendered View exist.

## Phase 4: Five-component high-fidelity vertical slice

Status: complete for implementation and representative emulator evidence. Physical Pixel/Samsung
visual acceptance and physical-device performance thresholds remain release gates.

Implement the internal contrast design system across Button, Switch, TextField, NavigationBar, and
Surface/Card. This phase proves the architecture before a public non-Material artifact exists.

Required evidence:

- Light/Dark, LTR/RTL, 1.0/1.3/2.0 font scale, enabled/disabled, selected/checked, focus, hover,
  pressed, error, and runtime provider replacement where applicable;
- TalkBack semantics and traversal, keyboard/d-pad focus, switch access actions, TextField IME,
  selection, autofill, and save/restore;
- deterministic screenshots with token/recipe/capability/fallback metadata;
- API 24, 31, 35, and current compile-target tests on at least Pixel and Samsung hardware or
  representative emulator/device coverage;
- benchmark comparison against Phase 0 for initial build, patch-only update, scroll, draw, and
  active animation.

Keep criteria:

- no component requires Android Renderer to know the design system;
- at least one component uses a shared Basic primitive and at least one remains a justified
  design-system composite;
- the TextField keeps native editing correctness;
- degraded effects remain usable and attributable;
- the result is visually distinct enough that accidental Material fallback is obvious.

Failure decision: revise or narrow the shared recipe vocabulary. Do not expand an unproven model
into every component to protect sunk cost.

### Phase 4 implementation decision and evidence

- `RoundedReference` and `CutContrast` resolve immutable token, recipe, motion, capability, and
  conformance bundles outside Android Renderer. The renderer has no design-system branch.
- Button and Surface/Card reuse `BasicButton` and `BasicSurface`. Switch and NavigationBar remain
  owned composites; TextField retains the native `EditText` editing core through `BasicTextField`.
- The fixture exposes its design-system identity, token source, recipe identity, mode, reduced
  motion, font scale, direction, shape, colors, capabilities, and per-component conformance in the
  rendered screen and screenshot sidecars.
- Instrumentation covers enabled/disabled, selected/checked, error, focus, hover, pressed, d-pad
  activation, accessibility switch action, native IME connection, autofill or the pre-26 fallback,
  selection, state recreation, and the Settings entry.
- The deterministic matrix passes on API 24, 31, 35, and 36 emulators for light LTR 1.0x, dark RTL
  1.3x with reduced motion, and light LTR 2.0x. Every final API 35 screenshot was visually inspected;
  API 24, 31, and 36 compatibility captures were also inspected. The 2.0x pass found and corrected
  missing explicit multi-line line heights in the diagnostic fixture.
- Macrobenchmark coverage records initial build, patch-only update, scroll/draw, and active
  animation for both bundles. A one-iteration API 35 emulator smoke trace passes, but emulator frame
  timing is intentionally not compared with Phase 0 budgets. The formal five-iteration and physical
  performance runs remain release-owner gates.

## Phase 5: Host resolution, switching, overlays, and system UI

Status: complete for the internal host/session contract and emulator evidence. Public integration
entry points remain owned by the Phase 6 design-system artifact rather than a general registry.

Planned work:

1. Resolve a design-system bundle at the application root without changing the generic
   `viewcompose-host-android` boundary.
2. Keep the existing aggregate `viewcompose-android` Material 3 default for compatibility; add
   explicit design-system integration entry points rather than making the generic host select one.
3. Implement design-system switching as root/session replacement with preserved caller state where
   contracts allow it.
4. Ensure lazy content, overlays, dialogs, popups, bottom sheets, system bars, insets, and nested
   sessions capture one coherent token/recipe/capability snapshot.
5. Define overlay presentation recipes separately from platform window semantics. Reuse the generic
   OverlayHost unless a design system demonstrates a different window-level behavior.
6. Add fallback reporting to debug diagnostics and screenshot sidecars without an always-on event
   history.

Keep criteria:

- switching cannot produce a frame with mixed design-system surfaces or stale overlay recipes;
- lifecycle, saveable state, focus, IME, and back handling remain correct;
- applications that use the standard aggregate artifact receive Material 3 without extra setup;
- custom hosts can install a bundle without depending on Material Components.

### Phase 5 implementation decision and evidence

- The generic `viewcompose-host-android` boundary is unchanged, and the aggregate
  `viewcompose-android` entry continues to install Material 3 by default. The internal fixture
  resolves its complete immutable bundle at the application root; no host-side design-system
  selector or general recipe registry was added.
- Switching replaces the Activity root and its `RenderSession` instead of mutating an active
  bundle. Android saved state restores committed caller-owned `rememberSaveable` values while all
  session-bound recipe and capability values are rebuilt from the new bundle.
- Instrumentation proves that the root View identity changes, caller state survives, and both a
  keyed `LazyColumn` item and a `Dialog` overlay expose only the new design-system identity. The old
  overlay session is cleared atomically; no mixed or stale token/recipe snapshot remains visible.
- Overlay presentation remains design-system-owned content inside the generic Android overlay
  window semantics. Insets, edge-to-edge behavior, back dismissal, and platform focus ownership
  therefore remain host contracts rather than component-recipe fields.
- Two API 35 emulator captures record the complete root/lazy/overlay identity before and after the
  overlay-triggered replacement. Diagnostics stay snapshot-based and attributable; the phase did
  not add an always-on event history.
- The test intentionally waits for state mutation to commit before requesting root replacement.
  Rebuilding in the same main-thread callback as an uncommitted write is outside the saveable-state
  contract and would make the test validate scheduler timing rather than application behavior.

## Phase 6: First public non-Material design system

Status: complete for implementation and API 35 emulator evidence. Pixel and physical Samsung
screenshot acceptance plus Maven publication remain release-owner gates.

One UI is the preferred first public target because it exercises Android-native interaction,
large-title/density/shape differences, and Samsung device validation without making backdrop blur a
release blocker. The exact target version and reference artifacts must be pinned at phase start.

Planned release slice:

- publish concrete foundation tokens and the reference component set;
- classify each component as Exact, Equivalent, Degraded, or Unsupported for the supported matrix;
- publish a minimal application setup and design-system switch sample;
- add binary/source/API documentation and release changesets for every affected artifact;
- require release-owner screenshot acceptance on Pixel and Samsung devices.

Do not claim complete One UI support from a five-component slice. Artifact and documentation names
must state the supported component set and alpha stability.

### Phase 6 implementation decision and evidence

- The pinned target is One UI 7, using Samsung's public One UI 7 design story and Samsung
  Developer component guidance reviewed on 2026-08-09. The artifact explicitly states that its
  static values are ViewCompose interpretations, not Samsung internal tokens or endorsement.
- `viewcompose-oneui7:0.1.0-alpha01` is an independent Design System artifact with one API
  dependency on `viewcompose-ui-foundation` and no Material dependency. It is not added to the
  standard `viewcompose-android` aggregate, so existing applications retain Material 3 defaults.
- `OneUi7ThemeDefaults.light/dark` and `OneUi7Theme` install one immutable token/recipe snapshot.
  Button and Surface reuse `BasicButton`/`BasicSurface`; Switch and text-only NavigationBar are
  owned composites; TextField retains the native Android editing core.
- Public Q2/Q3 KDoc, one compiled five-component sample, module/API documentation, catalog and
  dependency registration, and an immutable Maven changeset ship with the implementation.
- Unit tests cover token snapshots, structure, validation, and callback behavior. An API 35
  emulator test covers Button, disabled state, Switch state, native TextField editing,
  NavigationBar selection/RTL ordering, and deterministic Light/LTR/1.0 plus Dark/RTL/1.3
  screenshots. All four final screenshots were visually inspected.
- Backdrop blur and shape morph are outside the public alpha surface. The documented fallback is a
  contrast-safe tinted Surface and a discrete/static shape endpoint; neither optional effect can
  alter content, input, semantics, or layout.
- Physical Samsung acceptance remains mandatory before the first Maven upload. This external gate
  prevents a release claim, but does not justify moving Samsung/OEM policy into shared layers.

## Phase 7: Cupertino pressure test and advanced effects

Status: complete for the internal pressure fixture and API 24, 31, 35, and 36 emulator evidence.
No Cupertino artifact or public API is planned from this phase.

Use a Cupertino-inspired system as a structural pressure test, not a promise of Apple-platform
behavior on Android. It should challenge navigation structure, segmented controls, switches,
continuous corners, typography, translucency, and motion while retaining Android input,
accessibility, lifecycle, and back-navigation expectations.

Glass/acrylic/backdrop blur enters only after the fallback model and Surface ordering are stable.
The release may ship with API/OEM-specific Exact or Equivalent paths and a tinted translucent
Degraded path. Blur availability is never allowed to block content rendering or input.

Keep criteria:

- the shared contracts accommodate Cupertino without adding Cupertino-specific fields to generic
  theme tokens or Android Renderer;
- component structure may remain design-system owned where a shared recipe would become a union;
- effect fallback preserves contrast and produces stable screenshots;
- any snapshot-backed backdrop strategy has explicit invalidation, memory, privacy, and scroll
  performance bounds.

### Phase 7 implementation decision and evidence

- The retained fixture is Cupertino-inspired and internal to `app`; it is a structural pressure
  test, not an Apple-platform compatibility claim, artifact, or public API promise. It introduces
  no Cupertino identity or component field into `UiThemeTokens`, UI Contract, or Android Renderer.
- Its token and recipe bundle deliberately differs in palette, typography, sizing, continuous
  corners, spring motion, navigation treatment, and translucent surfaces. Button and Surface use
  shared Basic primitives; Switch, NavigationBar, and SegmentedControl remain design-system-owned
  composites; TextField keeps the native Android editing core.
- Conformance and fallback metadata are screenshot-visible. Continuous paths are retained as an
  exact generic renderer capability, incompatible shape transitions use a discrete endpoint, and
  backdrop blur uses a contrast-safe tinted translucent Surface.
- Snapshot-backed backdrop capture was not retained. The phase produced no bounded evidence for
  invalidation, memory, privacy, or scroll behavior that would justify its complexity. The
  translucent fallback proves the ordering and diagnostics contract without expanding renderer
  state or weakening the rollback boundary.
- Arbitrary shape morph was also not retained. Existing compatible interpolation remains generic;
  unrelated paths resolve to the documented discrete/static endpoint.
- API 35 runs cover the full pressure matrix, root/session replacement, lazy content, overlays,
  saved state, component behavior, and deterministic screenshot export. Targeted Cupertino runs
  on API 24, 31, and 36 cover Light/LTR/1.0 plus Dark/RTL/1.3/reduced-motion fixtures. All 18
  cross-version screenshots and the API 35 full-matrix screenshots were visually inspected.
- The RTL matrix exposed a logical-versus-physical Switch thumb offset above the renderer. The
  component-owned calculation was corrected for both cut-contrast and Cupertino bundles without a
  renderer design-system branch.
- API 35 emulator macrobenchmark smoke runs completed for cold initial build and active Switch
  animation. The single-iteration observations were 305.6 ms time to initial display and 32.0 ms
  frame CPU P50 across eight animation frames. These values prove reproducible instrumentation
  only; emulator numbers are not release thresholds and require physical-device comparison before
  publication claims.

## Phase 8: Native-behavior parity foundation

Status: implemented and retained. The source audit, controlled two-state drag foundation,
collection-position semantics, renderer verification, and emulator interaction matrix are complete.

Moving geometry from an Android widget into a design-system-owned DSL composite also moves hidden
behavioral ownership. A component is not accepted merely because its resting screenshot matches.
The applicable native behavior must either remain in a native core or be supplied by a shared,
design-system-neutral interaction contract before the custom component is retained.

The audit produced the following benefit-ordered decisions:

| Priority | Native behavior at risk | Decision and gate |
| --- | --- | --- |
| P0 | Switch follow-finger movement, bounded travel, velocity/position settle, cancellation, RTL, and click suppression after drag | Implement now in `viewcompose-gesture`; use it in the internal contrast and public One UI 7 Switches |
| P0 | Anchored state surviving recomposition without jumping, and restoring the committed anchor after cancellation | Correct the generic anchored-drag contract before wiring any component |
| P1 | Custom Slider tap-to-position, continuous drag, step quantization, directional keys, range accessibility actions, and RTL | Mandatory before any design system replaces the native `SeekBar`; do not publish an unproven universal range API while every current Slider still keeps the native core |
| P1 | Custom Checkbox/Radio tri-state or group selection semantics and mark-transition policy | Mandatory before the first custom tri-state or mutually exclusive control family; current native controls and two-state composite semantics remain sufficient |
| P1 | Navigation/Tab/Segmented collection position announcements and explicit traversal policy | Implement logical collection dimensions and item positions for current NavigationBar and SegmentedControl composites; retain native focus search and do not introduce a global roving-focus state machine without a failing fixture |
| P2 | Explicit haptic policy, rotary input, pointer hover variants, and drag-across selection | Add only for a pinned design-system requirement and physical-device evidence; Android click sound, focus, hover/state layers, and keyboard activation remain available today |
| P2 | Virtual accessibility descendants for a single custom-drawn View | Require before collapsing a semantic composite into one Canvas/custom View; current DSL composites intentionally retain real child Views |

TextField keeps the native `EditText` editing core. Current native Checkbox, RadioButton, Switch,
and Slider nodes keep their platform gesture and accessibility behavior. Existing generic click,
focus, key input, state-layer, transform, nested-scroll, pager, and scroll contracts are retained;
Phase 8 does not duplicate them inside each design-system module.

The retained implementation slice is deliberately narrow:

1. clamp anchored visual offsets to the installed range;
2. preserve active offsets when an equivalent anchor set is reinstalled by recomposition;
3. restore the last committed anchor before cancellation callbacks;
4. report the semantic value selected by a normal settle;
5. expose Q3 controlled logical toggle progress independent of physical LTR/RTL anchor direction;
6. compose that input with the existing click, focus, checked semantics, state-layer, and
   design-system motion contracts rather than introducing a new Switch node;
7. wire the internal contrast and One UI 7 Switches without adding design-system branches to the
   Android Renderer;
8. expose generic Q3 collection dimensions, selection cardinality, logical child positions, and
   spans, then map them to Android accessibility metadata without duplicating item selected or
   heading state;
9. wire current custom NavigationBar and SegmentedControl composites with logical positions while
   retaining native View focus search.

Keep criteria:

- a tap toggles exactly once, and a recognized drag never also invokes the click callback;
- the thumb follows the pointer, never travels beyond either anchor, and settles by the renderer's
  position/velocity policy;
- cancellation, modifier replacement, and controlled-state rejection restore caller-owned state;
- LTR and RTL expose the same logical unchecked-to-checked progress and opposite physical travel;
- TalkBack click action, D-pad/keyboard activation, checked announcement, 48dp target, state
  layers, and reduced-motion policy do not regress;
- equivalent anchor reinstallation during drag does not jump or restart the gesture;
- targeted unit/renderer tests and API 24, 31, 35, and 36 emulator interaction fixtures pass.

Retained evidence:

- generic anchored-drag tests cover clamping, equivalent-anchor reinstallation, cancellation,
  settle callbacks, controlled rejection, and logical RTL resynchronization;
- a renderer touch fixture proves that a recognized drag settles exactly once and does not also
  click;
- the public One UI 7 controlled Switch fixture passes click, real follow-finger drag, settle, and
  post-animation stability in LTR and RTL on API 24, 31, 35, and 36;
- API 35 stable-frame LTR and RTL screenshots were inspected after the animation window rather
  than capturing a stale intermediate frame;
- Android accessibility nodes expose one-row, single-selection collection metadata and logical
  item positions for One UI 7 NavigationBar in LTR and RTL on API 24 and 35;
- equivalent anchors installed twice during composition originally exposed an Android snapshot
  apply conflict; idempotent anchor installation fixed the shared state contract rather than
  adding a component workaround.

Rollback: revert the component wiring and controlled toggle adapter together. Generic anchored
clamping, recomposition preservation, and cancellation restoration may remain only if their
independent tests and compatibility review pass. Do not compensate for a failed composite by
adding One UI, Cupertino, or Material knowledge to Android Renderer.

## Testing and evidence matrix

Every structural phase selects a proportional subset and records why omitted cells are safe.

| Dimension | Required coverage |
| --- | --- |
| Android API | 24 baseline, 31 effects boundary, 35 reference device, current target |
| Device/OEM | Pixel plus Samsung before public One UI release |
| Theme | Light, Dark, Android XML where applicable, static adapter tokens, custom product tokens |
| Direction/locale | LTR and RTL; representative long labels and locale changes |
| Font/accessibility | 1.0, 1.3, and 2.0 font scale; TalkBack; keyboard/d-pad; touch exploration where relevant |
| State | enabled, disabled, pressed, focused, hovered, selected/checked, error, loading/read-only where supported |
| Bounds | measured View, visual surface, clip, semantics, effective touch, parent clipping, scroll viewport |
| Lifecycle | initial build, retained patch, removal, reinsert, root rebuild, configuration change, save/restore |
| Performance | build time, patch time, allocations, draw time, animation frame time, memory of cached effects |
| Diagnostics | token source, recipe identity, capability path, conformance result, API/OEM and screenshot anchors |

Generated screenshot evidence remains a reproducible build artifact unless a later phase explicitly
adopts a reviewed golden-update workflow. Tests must fail on missing/empty/clipped anchors before
capture so a blank transition frame cannot be accepted.

## Module and API strategy

Expected initial ownership:

- `viewcompose-ui-foundation`: neutral token and Basic-component contracts that survive the
  internal contrast fixture;
- `viewcompose-ui-contract`: resolved effect/geometry/NodeSpec values only when renderer transport
  is required;
- `viewcompose-android-renderer`: generic execution of resolved geometry, state, and fallback
  strategy;
- `viewcompose-graphics`: reusable Path, gradient, effect, cache, and drawing primitives;
- `viewcompose-material3`: Material token/recipe implementation and Android resource mapping;
- future design-system artifacts: named token profiles, recipes, composites, and integration;
- `app`: internal contrast fixture, manual verification, screenshot diagnostics, and switch entry.

Do not create one artifact per token family or component. Start with internal packages, then split a
design-system artifact only when publication, dependencies, or independent release ownership
requires it. A new published module must enter the module catalog, dependency diagrams, Maven BOM
or aggregate dependency guidance, API docs, changeset plan, and release gates in the same change.

## Risks and controls

| Risk | Control |
| --- | --- |
| Material vocabulary leaks into shared APIs | Internal contrast fixture must implement every proposed shared semantic before publication |
| Theme snapshot becomes a mega object | Recipes and capability policy remain separate typed locals with stable value identity |
| Renderer accumulates DS branches | dependency/source guards and NodeSpec tests reject design-system identity below the DS layer |
| Custom controls regress behavior | preserve native cores where valuable; require accessibility/input/lifecycle matrix before replacement |
| API/OEM visual inconsistency | own geometry for high-fidelity controls and declare conformance/fallback by capability |
| Blur/morph destabilizes older devices | explicit degraded/static paths, debug diagnostics, and independent rollback |
| Runtime switch mixes state | rebuild at the root and capture one coherent bundle for lazy/overlay sessions |
| Too many files change per component | land recipe, Basic primitive, surface/effect, motion, and public DS work in separate reversible phases |
| Performance degrades silently | Phase 0 baseline plus per-phase allocation/frame-time keep gates |
| Alpha public API freezes too early | internal-first experiment, Q-level review, compiled samples, and no unused marker contracts |

## Explicitly deferred or rejected work

Deferred until evidence exists:

- public general renderer/plugin registration;
- in-place live mutation of an active design-system instance;
- full One UI or Cupertino component catalog before the five-component slice;
- custom TextField editing engine;
- backdrop capture that lacks bounded invalidation and memory behavior;
- universal shape morph across unrelated arbitrary paths;
- Material 3 Expressive migration, which remains a separate versioned concern.

Rejected for this plan:

- one mega `UiThemeTokens` type containing every design system's component styles;
- Android Renderer `when (designSystem)` branches;
- Material Components dependencies in UI Foundation or the generic Android Renderer;
- pixel-identical claims across every API/OEM with no declared fallback;
- replacing all native widgets before their behavioral value is measured;
- function-object recipes whose identity invalidates LocalSnapshot or delayed content on every
  build;
- retaining a complex abstraction only because implementation effort has already been spent.

## Completion and archival gate

This plan is complete only when:

1. the recipe/theme/renderer boundaries are durable architecture documentation and automated
   guards;
2. the internal pressure slice passes the declared visual, behavioral, accessibility,
   lifecycle, fallback, and performance matrix;
3. one public non-Material design-system slice ships through Maven with correct dependency and API
   documentation;
4. root switching, overlays, and diagnostics preserve a coherent design-system snapshot;
5. shape morph and blur either have retained implementations or documented degraded/static paths;
6. every linked immutable Maven changeset has shipped and the release workflow has archived this
   plan before Maven upload.

At completion, move durable architecture to `docs/architecture/`, user setup to `docs/guides/`,
artifact contracts to `docs/modules/`, and historical implementation evidence to `docs/archive/`.
Do not leave normative behavior only in the archived plan.
