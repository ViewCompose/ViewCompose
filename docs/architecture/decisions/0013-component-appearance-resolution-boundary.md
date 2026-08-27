---
schema_version: 2
document_id: architecture.component-appearance-resolution
doc_type: architecture
slug: /architecture/decisions/component-appearance-resolution-boundary
owner:
  kind: capability
  id: foundation.components
version_lane: released
capability_ids:
  - foundation.components
  - theme.foundation
  - material3.components
  - oneui7.components
  - overlay.foundation
  - overlay.material3
  - overlay.oneui7
artifact_ids:
  - viewcompose-ui-foundation
  - viewcompose-material3
  - viewcompose-oneui7
  - viewcompose-overlay-material3-android
  - viewcompose-overlay-oneui7-android
sample_ids:
  - module.ui-foundation-profile-summary
  - guide.theming-local-override
  - module.material3-components
  - module.oneui7-components
  - tutorial.overlays
  - module.overlay-material3-dependency
  - module.overlay-oneui7-dependency
invariants:
  - High-level components resolve sparse appearance overrides fieldwise from instance through nested scopes to recipe and semantic-token fallbacks.
  - Basic primitives and overlay presenters receive complete immutable resolved appearance without performing theme, Local, or design-system policy lookup.
evidence:
  - Foundation, Material 3, One UI, overlay presenter, nested-override, NodeSpec mapping, Demo compilation, and API-surface suites.
---

# ADR-0013: Component appearance resolution boundary

- Status: Accepted
- Date: 2026-08-15
- Supersedes: the component-local override guidance now replaced by
  [local theme overrides](../../guides/theming-local-overrides.md)

## Context

ViewCompose needs two different appearance contracts. Application code needs a compact escape
hatch for the uncommon case where one component or subtree differs from its design-system recipe.
Design-system implementations need a complete, theme-independent value snapshot before they call
neutral `Basic*` primitives. Treating both requirements as direct component parameters makes the
high-level DSL grow with every uncommon visual slot. Treating both as a complete style object moves
recipe construction into application code and weakens the design-system boundary.

The existing component color providers only covered a subset of appearance. Nested providers
replaced the complete sparse patch instead of merging it, some declared TextField error-container
slots were never consumed, and one four-field input-control model collapsed distinct Checkbox,
Switch, RadioButton, and Slider states. Several high-level components also exposed long lists of
low-frequency visual parameters while `BasicTextField` exposed individual resolved fields rather
than one complete editing-core style.

## Decision

### High-level components use sparse overrides

Each high-level component family may expose one typed `XxxOverrides` value for low-frequency
appearance differences. Every field is optional and `null` means unspecified unless the component
needs an explicit tri-state wrapper to distinguish an intentional `null` from inheritance.

The same sparse model is accepted by the component instance and its scoped provider. Resolution is
fieldwise and follows this strict order:

1. instance overrides;
2. the nearest matching scoped provider;
3. outer matching providers, merged field by field;
4. component Defaults or the active design-system recipe;
5. semantic theme tokens.

An inner sparse provider never discards an unspecified outer field. Providers use named UI locals
so diagnostics identify the component family rather than an allocation-order name.

Overrides contain appearance only: colors, typography, shapes, borders, visual dimensions, and
state-layer values. Controlled state, callbacks, enabled state, selection, navigation policy,
image loading, keyboard behavior, lifecycle, reuse, and resource ownership remain explicit
component contracts. Frequently changed semantic content and commonly customized primary values
may also remain direct parameters where that keeps normal call sites clearer.

### Basic primitives use complete resolved styles

A `Basic*` primitive that assembles or binds a design-system-neutral visual accepts a complete
`BasicXxxStyle`. The style contains no nullable inheritance markers, variant identity, theme lookup,
or design-system policy. It is resolved before the primitive runs. A single component function does
not expose both a complete style and sparse overrides.

Design-system modules own their typed recipes and construct complete Basic styles directly.
Foundation does not define a universal recipe bundle or registry, and Android Renderer continues to
receive only resolved `NodeSpec` values.

The resulting pipeline is:

`Theme -> design-system recipe or Foundation Defaults -> scoped overrides -> instance overrides -> resolved style/NodeSpec -> Renderer`

### API and compatibility policy

This is a deliberate hard cut. The legacy `*ColorOverride` and `Provide*Colors` APIs are replaced
rather than retained beside the new model. Low-frequency appearance parameters move into the
component's typed overrides, while non-appearance parameters remain direct. `BasicTextField`
replaces its individual appearance parameters with `BasicTextFieldStyle`.

Sparse override values are Q2 immutable contracts. Scoped providers and affected high-level
components are Q3 because nesting and precedence affect an entire subtree. Complete Basic styles
are Q2; Basic primitives remain Q3. Canonical KDoc, compiled samples, module documentation, and
deterministic tests ship with each hard-cut phase.

## Consequences

- Normal component call sites remain small while rare local divergence has one typed escape hatch.
- Nested scopes compose predictably without turning `UiThemeOverride` into a component matrix.
- Input-control state colors cannot leak across unrelated native control families.
- Basic primitives are deterministic inputs for Material 3, One UI, and future design systems.
- Adding a low-frequency appearance slot changes one override model instead of every ordinary call
  site.
- The framework still cannot infer whether an arbitrary captured non-State value changed; this
  decision does not alter the explicit revision contracts used by lazy content.
- A large override type is acceptable when its fields are discoverable but absent from the primary
  component signature. Behavior and lifecycle fields are never moved into it merely to reduce
  parameter count.
- Floating and extended FABs, top and bottom app bars, Badge, AlertDialog, and modal bottom sheet
  now follow this boundary. Regular and extended FABs, and top and bottom app bars, intentionally
  use independent types because their geometry and content roles are not interchangeable.
- TopAppBar owns separate navigation and action content-color scopes; BottomAppBar owns its row
  content-color scope. A child IconButton instance override still has final precedence.
- Modal-bottom-sheet appearance resolves to one immutable request snapshot before crossing the
  overlay session boundary. Every presenter applies the complete snapshot on show and same-key
  update, including a closed exact-versus-platform-default navigation-bar policy.
- Scaffold and raw Dialog intentionally have no appearance override type. Scaffold exposes primary
  page-surface layout inputs, while raw Dialog is a lifecycle/positioning protocol with
  caller-owned content; manufacturing sparse visual patches for either would blur ownership.

## Rejected alternatives

### Put every visual property directly on each component

Rejected because rare escape hatches dominate discovery and make the normal DSL expand as component
fidelity improves.

### Pass complete styles to high-level application components

Rejected because callers would need to rebuild design-system recipes and disabled/error states for
one small difference.

### Use one global component-style registry

Rejected because it centralizes unrelated design-system policy in Foundation, weakens module
ownership, and makes extension and diagnostics depend on untyped lookup.

### Encode component appearance in Modifier

Rejected because semantic component slots and state-dependent values are not general layout,
drawing, input, or semantics operations. Modifier ordering would obscure precedence and could not
express complete component resolution cleanly.

### Keep specialized color-only providers beside new overrides

Rejected because two competing precedence paths would preserve the existing ambiguity and double
the compatibility surface.

## Validation

Every migrated family requires tests for empty fallback, instance precedence, nested fieldwise
merge, provider restoration, enabled/disabled/error state resolution where applicable, and
environment changes. Basic styles require direct NodeSpec mapping tests and a proof that the Basic
primitive performs no theme or local lookup. Design-system and Demo compilation prove downstream
migration, while API dumps and documentation gates protect the hard-cut surface.

Overlay-backed components additionally require presenter tests for same-key appearance updates and
reversible platform policy. A protocol-object equality test alone is insufficient because window
flags, native container shape/color, and restored system-bar policy are presenter-owned effects.
