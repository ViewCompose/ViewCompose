# ADR-0002: Five-Layer Runtime Module Architecture

## Status and date

Accepted and implemented — 2026-08-06.

The aggregate's original implicit Material assembly is superseded by
[ADR-0005](./0005-design-system-host-and-component-backend-boundary.md):
`viewcompose-android` is neutral and `viewcompose-material3-android` is the named Material
application aggregate. The five-layer direction remains in force.

## Context

ViewCompose originally classified most required runtime artifacts as `foundation` and everything
else as an optional capability or tooling. That direction protected the core render path from
optional features, but the term became too broad: it grouped platform-neutral kernels, declarative
UI APIs, the Android View renderer, Android hosts, AndroidX lifecycle adapters, and Material-backed
theme/component behavior under one label.

The ambiguity produced three recurring problems:

1. a module name such as `viewcompose-widget-core` or `viewcompose-overlay-android` did not reveal
   whether it owned generic UI semantics, Android execution, or Material presentation;
2. AndroidX infrastructure and Material design-system implementation were treated as comparable
   dependency categories even though they change for different reasons;
3. convenience aggregation could pull design-system behavior into Host or Renderer without an
   explicit architecture decision.

ViewCompose targets the Android View system. This decision does not introduce cross-platform
rendering, but it keeps deterministic kernels, UI semantics, Android execution, design policy, and
replaceable integrations independently testable and releasable.

## Decision

### 1. Runtime code uses five responsibility layers

Every published runtime artifact belongs to exactly one layer:

1. **Kernel** — deterministic state, immutable contracts, editing/navigation/animation/gesture/
   graphics policy, with no Android, AndroidX, Material, integration, or tooling dependency.
2. **UI Foundation** — design-system-neutral declarative tree building, locals, effects, generic
   component semantics, token schemas, and delayed-content contracts.
3. **Android Engine** — Android View creation, reconciliation, binding, hosting, scheduling,
   environment adaptation, and explicit Android/AndroidX interop.
4. **Design System** — concrete visual tokens, theme resolution, component presentation defaults,
   and design-system-specific platform adaptation. Material 3 is a named implementation, not a
   neutral-host default.
5. **Integrations** — optional AndroidX or third-party adapters whose removal removes only the
   integrated capability.

Tooling is orthogonal and downstream. A consumer aggregate may intentionally expose a reviewed
default stack but owns no runtime semantics.

### 2. Dependency direction is enforced, not advisory

A lower layer never depends on a higher layer. Kernel is the reusable base. UI Foundation and
Android Engine consume Kernel contracts. Design System consumes UI Foundation. Integrations may
consume the lower contracts required for their feature, while lower layers never consume an
Integration. Tooling may consume runtime layers; runtime layers never consume tooling.

Every module is registered with its layer and allowed dependency layers. Verification
rejects an unclassified runtime module, a reversed edge, forbidden platform/vendor imports, and an
unreviewed public dependency exposure.

### 3. AndroidX and Material have different architectural meaning

AndroidX is permitted infrastructure inside Android Engine and explicitly named AndroidX
integrations. Material is a Design System implementation or an explicitly Material-backed
Integration. Package ownership alone does not classify a module, but Material code and resources
cannot enter Kernel, UI Foundation, or Android Engine public contracts.

### 4. Resolved semantics cross the renderer boundary

UI nodes carry resolved generic semantics and visual values. Android Renderer implements those
values without selecting Material defaults. The renderer does not expose a general node plugin
registry merely to move a small number of upstream widgets; a new renderer SPI requires its own
use case, lifecycle contract, performance evidence, and architecture decision.

### 5. Convenience is isolated in an aggregate

`viewcompose-android` is the neutral one-dependency entry point. It intentionally exposes the
standard Android host, UI Foundation, and reviewed AndroidX integrations without selecting a
design system. `viewcompose-material3-android` is the named one-dependency Material entry point and
transitively exposes the neutral aggregate plus `viewcompose-material3`. Advanced consumers may
depend on lower artifacts directly. Host and Renderer do not depend upward on either aggregate or
Design System to make the beginner path convenient.

### 6. Artifact names describe responsibility

Misleading alpha artifact names are replaced in one hard cut. Platform-neutral capability kernels
may retain `-core`; Android engines, AndroidX integrations, and Material-backed integrations state
that ownership in their artifact names. Superseded coordinates receive no compatibility facade.

## Target artifact changes

- `viewcompose-widget-core` becomes `viewcompose-ui-foundation`.
- `viewcompose-renderer` becomes `viewcompose-renderer-android`.
- `viewcompose-navigation` becomes `viewcompose-navigation-android`.
- `viewcompose-lifecycle` becomes `viewcompose-lifecycle-androidx`.
- `viewcompose-viewmodel` becomes `viewcompose-viewmodel-androidx`.
- `viewcompose-widget-constraintlayout` becomes `viewcompose-constraintlayout-androidx`.
- `viewcompose-overlay-android` becomes `viewcompose-overlay-material3-android`.
- `viewcompose-material3`, `viewcompose-android`, and the later superseding
  `viewcompose-material3-android` named aggregate are added.

Kernel, capability DSL, image integration, shadow, and tooling artifact names that already state
their responsibility remain unchanged.

## Alternatives considered

### Keep the existing modules and document conventions only

This avoids migration cost but leaves Material and Android execution mixed in artifacts whose names
promise generic core behavior. Documentation cannot prevent dependency drift when Gradle allows it.

### Mirror the official AndroidX and Material repositories

This would organize modules by upstream vendor and history instead of ViewCompose responsibility.
It would also encourage thin wrappers and unnecessary artifacts. The selected model uses upstream
names only for deliberate integrations or design-system identity.

### Preserve old Maven artifacts as forwarding facades

Facades reduce immediate consumer migration but prolong duplicate names, complicate independent
release propagation, and let new code continue choosing obsolete coordinates. The current artifacts
are alpha and the migration is explicitly authorized as a hard cut, so compatibility facades are
rejected.

### Open a general external renderer registry

An external registry could let Material3 provide View factories and binders, but it would create a
new public lifecycle, conflict, ordering, threading, patching, and performance contract. The current
Material use is too small to justify that permanent surface.

## Consequences and trade-offs

- The one-time migration affects source directories, Gradle paths, Maven coordinates, samples,
  tooling, publication metadata, API documentation, and consumer instructions.
- Material-free Engine consumers become possible and dependency ownership becomes inspectable.
- Material 3 can evolve or be replaced without changing Kernel or render transaction semantics.
- Ordinary users retain a one-dependency path through the aggregate rather than learning the
  internal module graph.
- Some Android types may remain in UI Foundation where they are part of an explicit Android-only
  declarative contract; this project does not claim multiplatform UI Foundation. Material types do
  not receive that exception.
- Split packages may be tolerated temporarily during source movement, but final artifacts must have
  unique classes and reviewed Android resource namespaces.

## Affected modules and public contracts

All published runtime artifacts, publishing metadata, dependency exposure contracts, samples,
preview classpaths, and module documentation are affected. Public host theme parameters, theme
resolver APIs, renderer implementation classes, and every renamed Maven coordinate are breaking
alpha contracts.

## Validation and rollout

1. Record a pre-cut compile/test and Material behavior baseline.
2. Register every runtime module in the five-layer dependency verifier.
3. Verify Kernel has no Android/AndroidX/Material dependencies.
4. Run `verifyDesignSystemIsolation` to prove UI Foundation and Android Engine have no Material
   dependency or import, and keep public Material types out of those classpaths.
5. Compile and run a base Android host consumer without Material on its dependency graph.
6. Compile and run independent one-coordinate consumers for neutral `viewcompose-android` and
   named Material `viewcompose-material3-android`.
7. Verify generated Maven metadata matches the reviewed `api`/`implementation` contracts.
8. Update current architecture, module manuals, tutorials, guides, migration pages, and reviewed
   Chinese mirrors after the implementation topology stabilizes.
9. Pass focused tests, `qaQuick`, `qaRelease`, documentation/localization verification, and local
   Maven publication smoke tests before completing the hard cut.

The execution ledger and phase gates live in the active
[five-layer hard-cut plan](../../project/plans/five-layer-module-architecture-hard-cut.md).
