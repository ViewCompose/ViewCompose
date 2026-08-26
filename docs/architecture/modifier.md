---
schema_version: 2
document_id: architecture.modifier-runtime
doc_type: architecture
owner:
  kind: capability
  id: modifier.layout
version_lane: released
capability_ids:
  - modifier.layout
  - modifier.appearance
  - modifier.drawing
  - modifier.interaction
  - modifier.shared-content
  - modifier.semantics
  - gesture.modifiers
  - focus.input
  - nested.scroll
  - shadow.modifiers
  - shadow.android-backend
artifact_ids:
  - viewcompose-ui-contract
  - viewcompose-ui-foundation
  - viewcompose-gesture
  - viewcompose-renderer-android
  - viewcompose-shadow-android
sample_ids:
  - tutorial.layouts-and-modifiers
  - architecture.modifier-appearance
  - architecture.modifier-drawing
  - architecture.modifier-interaction
  - architecture.modifier-shared-content
  - architecture.modifier-semantics
  - tutorial.gestures
  - guide.focus-form
  - guide.nested-scroll-toolbar
  - guide.shadow-card
invariants:
  - Modifier remains a renderer-neutral ordered chain; renderer resolution preserves chain order and each modifier family's documented replacement or accumulation rule.
  - General outer decoration belongs to Modifier, parent-specific data belongs to a typed scope, component semantics belong to NodeSpec, and Theme or Defaults only supply defaults.
  - Native View padding has one renderer owner that composes container content padding, resolved Modifier padding, and selected system-bar or IME inset edges before writing the View.
  - Physical spacing and inset selectors remain physical, while Relative forms resolve from the VNode's captured layout direction on every bind.
  - Focus requesters and nested-scroll dispatchers retain renderer-neutral identity and attach to one current platform connector.
  - Preview key input travels root-to-target before unconsumed target-to-root bubbling.
  - Nested pre phases travel outer-to-inner and post phases inner-to-outer, with every consumption bounded by the offered value.
  - Exact outer and inner shadows remain decoration planes that do not alter layout, input, elevation, or sibling order.
evidence:
  - viewcompose-ui-contract/src/test/kotlin/com/viewcompose/ui/modifier/ModifierContractTest.kt
  - viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/modifier/InsetsPaddingModifierTest.kt
  - viewcompose-ui-contract/src/test/kotlin/com/viewcompose/ui/modifier/FocusModifierContractTest.kt
  - viewcompose-ui-contract/src/test/kotlin/com/viewcompose/ui/gesture/NestedScrollContractsTest.kt
  - viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/modifier/ResolvedModifiersTest.kt
  - viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/tree/ModifierFocusInputApplierTest.kt
  - viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/view/container/NestedScrollHostLayoutTest.kt
  - viewcompose-shadow-android/src/test/java/com/viewcompose/shadow/android/ShadowDecorationLayerTest.kt
---

# Modifier Architecture

## 1. Scope

This document owns the boundary and runtime rules shared by `Modifier`, scoped parent data,
component `NodeSpec`, and `Theme/Defaults`. The generated
[Capability Reference](https://docs.viewcompose.com/reference/) owns the exhaustive public-symbol
inventory; this page does not maintain a second list.

## 2. Ownership model

Use the first matching layer:

1. `Modifier` owns stable outer decoration and behavior that can apply to most nodes: layout,
   appearance, drawing, visibility, interaction, focus, semantics, testing, gestures, shared
   content, nested scroll, shadows, and layout animation.
2. A typed parent scope owns data meaningful only to that parent, such as
   `RowScope/ColumnScope.weight`, scoped alignment, or ConstraintLayout child constraints.
3. Component parameters and `NodeSpec` own component semantics such as text styling, image
   content scale, button variants, and text-field editing state.
4. `Theme`, design-system recipes, and `Defaults` supply defaults; renderers consume resolved
   values and do not invent business defaults.

Collection reuse and motion remain container policy. Focused-editor visibility belongs to the
nearest real scroll owner. A pager owns discrete page selection only, so a page that may be hidden
by the IME supplies its own page-local scrolling.

## 3. Runtime contracts

### 3.1 Ordering, layout, and appearance

`Modifier` is an immutable ordered chain rooted at `Modifier`. Renderer resolution preserves that
order. Later declarations replace earlier values for complete physical/relative spacing families,
background and shape families, visibility, click handling, and other documented single-value
properties. Accumulating properties such as `zIndex` keep their explicit accumulation contract.

Physical padding, margin, offset, and inset selectors never change meaning in RTL. Their
`Relative` counterparts resolve start/end from the VNode's captured direction on every bind. The
renderer has one native-padding writer: container content padding, resolved Modifier padding, and
selected insets are composed before the View is updated.

Maximum dimensions and aspect ratio are portable constraints, not raw Android setters. Android
Renderer folds them into one synthetic layout boundary around the complete node. Exact incoming
constraints remain authoritative; invalid finite/positive values or contradictory declared bounds
fail before rendering. Constraint parent data is meaningful only on ConstraintLayout children.

A drawable background takes precedence over a packed color and follows resource qualifiers from
the View context. Shape and legacy corner-radius declarations replace one another in chain order.
Clipping, platform elevation, exact shadows, and sibling order remain separate concerns.

### 3.2 Drawing, interaction, semantics, and shared content

Drawing callbacks execute in Modifier order. Behind callbacks run before wrapped content;
content-aware callbacks decide whether and when to forward content; cache builders use
renderer-owned caches. Visibility controls drawing and layout participation independently of draw
callback registration.

Interaction indication describes visual feedback only; it does not make a node clickable or
enabled. High-level components resolve design-system feedback before installing it. Accessibility
state travels through renderer-neutral semantics, with logical collection indexes unchanged by RTL.
`testTag` is diagnostic identity, not a globally unique application key.

Shared-content markers publish endpoint identity and mode. Pairing, snapshots, and fallback belong
to a shared-aware host; outside such a host the marker has no visual effect.

### 3.3 Focus, keys, gestures, and nested scroll

UI Contract owns normalized focus, key, gesture, and nested-scroll values. UI Foundation exposes
session services; Gesture contributes recognizer descriptions; Android Renderer attaches mounted
targets without upper layers retaining Views.

Preview keys travel root-to-target and unconsumed keys bubble target-to-root. Explicit focus
destinations win before native search. Gesture modifiers describe pointer, click, drag, anchored
drag, transform, and arbitration policy; the renderer owns platform timing, slop, pointer streams,
velocity, cancellation, and callback delivery.

Nested-scroll pre phases travel outer-to-inner and post phases inner-to-outer. Every finite result
is bounded by the remaining offered value. Framework scrollers and native children implementing
Android nested scrolling join the chain directly; other `AndroidView` children require an attached
dispatcher. The legacy native fling bridge can report only Boolean consumption, while the
ViewCompose chain preserves exact partial velocity.

### 3.4 Exact-shadow routing

UI Contract owns renderer-neutral shadow layers and order. Android Renderer owns before/after
decoration planes without depending on a raster backend. The optional Shadow Android artifact
resolves shapes and density, rasterizes layers, and replays them. Without it, exact-shadow requests
are no-ops and do not affect layout, input, elevation, or `zIndex`.

See the [advanced-shadow guide](../guides/shadows.md) and
[Shadow Android module manual](../modules/viewcompose-shadow-android/README.md) for application and
backend details.

## 4. Generated capability ownership

The source scanner discovers application-facing public/protected DSL, Modifier, component, host,
integration, and tooling entries from published production source sets. Each entry must resolve to
exactly one capability, artifact/version state, generated reference owner, sample decision, module
manual, and versioned API root. Internal, test, Demo, generated, and renderer-only helpers are
excluded.

The website and governance gate consume the same committed model. Refresh it with
`./gradlew updateDocumentationCapabilityReference`; stale output, duplicate ownership, or a new
orphan fails verification. Raw signatures and KDoc/Javadoc remain in the
[versioned API Reference](https://docs.viewcompose.com/api/).

## 5. Hard boundaries and change gate

Do not place component semantics in general `Modifier`, parent-specific data in global `Modifier`,
theme defaults in renderers, or first-party durable contracts in an untyped dynamic map. Do not
add a second handwritten symbol inventory beside the generated Reference.

A Modifier-boundary change must update this architecture owner, cover the affected contract and
renderer path, provide compiled Q3 samples and public documentation required by its Q level, and
add Demo or device evidence when behavior is visual or interactive. Follow the
[development workflow](../project/workflow.md).

## 6. Related documents

1. [Layouts and modifiers tutorial](../tutorials/layouts-and-modifiers.md)
2. [Gestures tutorial](../tutorials/gestures.md)
3. [Focus and input](../guides/focus-and-input.md)
4. [Nested scroll](../guides/nested-scroll.md)
5. [NodeSpec architecture](node-spec.md)
6. [Theme runtime architecture](theming.md)
