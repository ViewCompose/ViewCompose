---
schema_version: 2
document_id: architecture.design-system-resolution
doc_type: architecture
slug: /architecture/decisions/design-system-resolution-boundary
owner:
  kind: capability
  id: theme.foundation
version_lane: released
capability_ids:
  - animation.composition-motion
  - foundation.components
  - material3.components
  - oneui7.components
  - theme.foundation
  - theme.material3
  - theme.oneui7
artifact_ids:
  - viewcompose-animation-core
  - viewcompose-animation
  - viewcompose-ui-contract
  - viewcompose-ui-foundation
  - viewcompose-renderer-android
  - viewcompose-material3
  - viewcompose-oneui7
sample_ids:
  - module.material3-theme
  - module.material3-components
  - module.oneui7-theme
  - module.oneui7-components
invariants:
  - Foundation tokens, typed design-system recipes, and resolved renderer execution contracts remain separate immutable boundaries.
  - Android Renderer never branches on the originating design-system identity.
  - Shared Basic primitives carry only semantics proven reusable across materially different systems; named structure remains in its owning module.
  - Runtime switching replaces one coherent root and Session bundle instead of mutating design-system identity in place.
  - Motion policy resolves immutable semantic roles while lifecycle-owned animation APIs retain execution ownership.
evidence:
  - viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/theme/ThemeRecipeBoundaryGuardTest.kt
  - viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/theme/DesignSystemDiagnosticsTest.kt
  - viewcompose-renderer-android/src/test/java/com/viewcompose/renderer/guard/DesignSystemIsolationGuardTest.kt
  - viewcompose-material3/src/test/java/com/viewcompose/material3/Material3ThemeBridgeTest.kt
  - viewcompose-oneui7/src/test/java/com/viewcompose/oneui7/OneUi7ComponentsTest.kt
---

# ADR-0004: Design-System Resolution Boundary

## Status and date

Accepted on 2026-08-07.

## Context and forces

ViewCompose must support high-fidelity Material, One UI, Cupertino-inspired, and product-owned
interfaces on one Android View runtime. These systems may differ in component structure, not only
color and radius values. A single enlarged `UiThemeTokens` snapshot would couple unrelated
component vocabularies, while design-system branches in Android Renderer would reverse the module
dependency direction and make every renderer change aware of product policy.

The framework must still share expensive behavioral and rendering infrastructure: state and
composition, native text editing, target bounds, semantics, shapes, effects, animation ownership,
View reconciliation, and diagnostics. Runtime switching and fallback selection also need one
coherent snapshot across lazy content and overlays.

## Decision

ViewCompose separates design-system resolution into three data boundaries:

1. Foundation tokens are immutable reusable semantics such as color, typography, density, shape,
   elevation, and motion. They contain no component factories, callbacks, Android resources, or
   design-system identity.
2. Typed component recipes are owned by a concrete design-system module. A recipe selects resolved
   values or design-system-owned structure and has stable value identity suitable for Local
   snapshots. ViewCompose does not publish one universal recipe bundle.
3. Shared Basic primitives or design-system composites emit design-system-neutral `NodeSpec`
   values. Android Renderer executes resolved geometry, interaction, semantics, effects, and
   fallback strategies without identifying the originating design system.

`BasicSurface` is the first shared resolved boundary. `BasicButton` demonstrates a neutral action
composite. `BasicTextField` remains the native editing core while decoration is design-system
owned. A shared Basic toggle is deferred until drag, accessibility, state restoration, and device
evidence prove one common behavioral contract. Structurally different navigation remains an owning
design-system composite.

Motion policy is separate immutable data. `MotionScheme` resolves semantic roles and reduced-motion
substitution, while the existing lifecycle-owned `Animatable` and `Transition` APIs run it.
Compatible shape parameters may interpolate; incompatible geometry reports a discrete/static
fallback. Arbitrary Path Morph and a second component-local animation runner are outside the shared
contract.

Runtime switching replaces the root/session under one immutable design-system bundle. In-place
mutation of a live bundle is not required. Overlays and delayed content capture the same resolved
snapshot as their owning root.

## Alternatives considered

- Expand `UiThemeTokens` with every component style. Rejected because it creates a cross-system
  union and makes unrelated token changes invalidate the whole theme snapshot.
- Add `when (designSystem)` branches to Android Renderer. Rejected because it reverses dependency
  ownership and couples rendering infrastructure to named product policy.
- Publish a renderer/plugin registry first. Rejected until two independent systems demonstrate a
  rendering transport that generic nodes, Surface, Canvas, graphics, or custom View backends cannot
  express safely.
- Force every component through one Basic component hierarchy. Rejected because navigation,
  text-field decoration, switch interaction, and slot order may have materially different
  structures and state machines.

## Consequences and trade-offs

- New design systems can share the runtime and renderer without adding named branches below their
  module boundary.
- Some components intentionally duplicate small composition structures so their public vocabulary
  stays coherent instead of becoming a universal union.
- Design-system providers must resolve complete recipes before emission and keep their values
  immutable; this is more explicit than reading global defaults deep inside components.
- Root replacement may recreate native Views. It provides atomic policy switching and avoids mixed
  old/new overlay state, with caller-owned saveable state preserved only where its contract allows.
- High-fidelity effects carry an explicit Exact, Equivalent, Degraded, or Unsupported result.
  Fallback may reduce decoration but never changes input, semantics, bounds, or target state.

## Affected modules and public contracts

- `viewcompose-ui-contract`: resolved geometry, effect, bounds, state-layer, semantics, and
  diagnostic transport only.
- `viewcompose-ui-foundation`: neutral Basic primitives and reusable foundation-token access.
- `viewcompose-animation-core` and `viewcompose-animation`: semantic motion resolution, shared
  lifecycle ownership, and compatible-shape interpolation.
- `viewcompose-renderer-android`: generic execution and capability probing without design-system
  policy.
- `viewcompose-material3` and future named design-system artifacts: tokens, recipes, composites,
  conformance declarations, and integration mapping.
- `viewcompose-host-android` and aggregates: root/session installation, not design-system selection
  inside the generic host.

## Validation and rollout

Each shared contract must first pass an internal visually contrasting five-component fixture:
Button, Switch, TextField, NavigationBar, and Surface/Card. Tests cover Local snapshot propagation,
retained patches, accessibility and input, compatible and fallback geometry, reduced motion,
switching, overlays, and design-system isolation. Public non-Material artifacts additionally require
device/emulator screenshots, performance comparison, module documentation, compiled samples, and
immutable Maven changesets. Hardware-only OEM acceptance remains a release gate when representative
emulators cannot reproduce it.

## Related decisions

- [ADR-0002: Five-layer runtime module architecture](./0002-five-layer-runtime-module-architecture.md)
- [ADR-0003: Public package ownership and platform handles](./0003-public-package-ownership-and-platform-handles.md)
