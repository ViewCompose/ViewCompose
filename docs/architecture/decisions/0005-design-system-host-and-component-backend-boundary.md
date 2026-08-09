# ADR-0005: Design-System Host and Component Backend Boundary

## Status and date

Accepted on 2026-08-09.

## Context and forces

[ADR-0004](./0004-design-system-resolution-boundary.md) established that foundation tokens,
component recipes, and resolved renderer contracts are separate and that Android Renderer cannot
identify the active design system. The first multi-design implementation proved that this is
sufficient for values and component structure, but it also exposed two boundaries that need an
explicit decision.

First, Android Views may read style attributes from their `Context` during construction. The
current `viewcompose-android` convenience root resolves a Material-themed context and exposes
Material policy types even when the content provides One UI or another token bundle. Providing
different tokens inside composition does not undo native defaults already selected from that
context.

Second, native Android widgets carry valuable editing, gesture, focus, scrolling, and accessibility
behavior, but their visual geometry is not uniformly adaptable to unrelated design systems. A
policy of always using native widgets cannot provide high fidelity, while replacing every widget
would duplicate mature behavior and create a large maintenance burden.

The architecture must preserve Android View as the execution engine, keep Material a first-party
experience, permit high-fidelity non-Material systems, and avoid creating a generic extension API
before more than one integration proves its shape.

## Decision

### Neutral host and two-phase design-system installation

The low-level Android host and generally named host entry points are design-system neutral.
Design-system installation has two distinct phases:

1. A named Android design-system adapter may resolve the effective themed `Context`, resources,
   dynamic-color policy, configuration, and capabilities before root View creation.
2. The composition root provides one immutable design-system snapshot containing tokens, recipes,
   motion, capability/fallback policy, and diagnostic provenance.

The neutral host accepts the resolved platform environment and mounts content. It does not select
Material, expose Material policy types, or implicitly wrap every root in a Material context.
Overlays and delayed sessions use the same captured snapshot. Runtime design-system switching
replaces the root/session rather than mutating a live identity in place.

`viewcompose-android` will converge on neutral convenience entry points. Material-specific
convenience moves behind a Material-named module or compatibility facade. The migration must first
characterize public APIs and generated Maven dependencies, then preserve source compatibility when
the benefit justifies it. New Material coupling is forbidden during the transition.

The first extraction remains explicit and internal. ViewCompose will not publish a general host
theme/plugin SPI until a second design system independently needs to alter Android context
construction and demonstrates the same lifecycle and resolution contract.

### Component backend ladder

Each component chooses among three production strategies:

1. retain a native behavioral core when Android owns costly editing, selection, scrolling,
   accessibility, or input behavior;
2. use a design-system-owned DSL composite when shared Views, gestures, and semantics can express
   the required structure and state machine; or
3. use a design-neutral custom View when reusable drawing, layout, clipping, or measured performance
   requires one renderer-owned View.

A design-specific Android implementation or external widget remains in a named integration and is
mounted through the neutral `AndroidView` boundary. It moves into generic Renderer only after two
independent consumers prove a name-free resolved contract and its lifecycle, rollback,
accessibility, and performance behavior.

Behavioral parity is a prerequisite for replacing a native widget. Resting screenshot fidelity is
not sufficient. Shared interaction foundations are implemented before additional custom component
catalogs.

### Material ownership and module names

Material 3 is the first-party reference design system, not the rendering substrate. Android XML
theme mapping, dynamic color, Material recipes/components, and optional Material Components widget
integration remain in Material-named modules. Generic nodes are not mapped to Material widgets.

The artifacts `viewcompose-material3` and `viewcompose-oneui7` keep their names. A module is split
only for a real dependency, platform, publication, or release-ownership boundary, using a
capability/platform suffix such as `-android`. Generic `design` or `theme` words are not inserted
into every artifact name.

## Alternatives considered

### Keep a Material-first aggregate and rely on inner token overrides

Rejected as the target architecture. Native Views can consume the outer Material context before
inner tokens exist, so a non-Material tree can inherit Material colors, shapes, overlays, and
widget behavior unintentionally.

### Map all generic controls to Material Components widgets

Rejected. It would give strong Material integration but make Renderer, host context, dependency
metadata, widget version behavior, and non-Material fidelity depend on Material.

### Implement every component as a custom View

Rejected. Android View can draw the visuals, but the framework would unnecessarily own text
editing, selection, scrolling, input arbitration, accessibility, and OEM/API compatibility for
every control.

### Require every design system to use the same public component hierarchy

Rejected. It would create a union API and force unrelated slot models and state machines into one
contract. Sharing is based on proven semantic equality, not similar names.

### Publish a general design-system or host plugin registry immediately

Deferred. One Material context adapter and static non-Material tokens are insufficient evidence
for a durable public SPI. Explicit composition and named integration modules are cheaper to revise.

### Rename every design artifact to include `design` or `theme`

Rejected. Existing names already express their design-system identity; the added word does not
identify a dependency or platform boundary and would impose avoidable Maven migration.

## Consequences and trade-offs

- Non-Material systems can construct native Views without accidental Material context defaults.
- Material setup becomes more explicit internally, while a Material-named convenience API may
  preserve a small application setup surface.
- Host refactoring affects public overloads and dependency metadata, so it requires source/API and
  Maven baselines before implementation plus a reversible compatibility layer.
- Some component structure is intentionally duplicated between systems, while behavior,
  primitives, and renderer execution remain shared.
- Backend selection becomes evidence-driven per component. A design system may mix native cores,
  composites, and custom Views without violating architectural consistency.
- The absence of a public adapter SPI limits speculative flexibility but protects the framework
  from freezing a one-consumer abstraction.
- Material Components may still be used where they have retained value, but only behind a named
  integration and without exposing concrete widget types.

## Affected modules and public contracts

- `viewcompose-host-android`: remains the neutral mounting and platform-installation kernel.
- `viewcompose-android`: transitions from an implicitly Material convenience aggregate toward
  neutral entry points; compatibility is determined from the implementation baseline.
- `viewcompose-material3`: owns theme/context resolution, dynamic color, recipes, components, and
  any retained Material-specific Android integration.
- `viewcompose-oneui7` and future design-system modules: own their vocabulary and components while
  consuming only neutral foundations and execution contracts.
- `viewcompose-ui-foundation`: owns reusable Basic primitives and interaction/semantic contracts,
  not named component policy.
- `viewcompose-renderer-android`: owns Android View execution and neutral custom Views without
  design-system selection.
- `viewcompose-ui-contract`: receives only stable, name-free execution semantics proven by more
  than one independent consumer.

Any changed public/protected API must receive its Q-level documentation, compiled samples, module
manual changes, compatibility evidence, and release changeset in the same implementation change.

## Validation and rollout

Rollout is ordered to keep architecture work ahead of component work:

1. freeze the architecture standard and add dependency/source audits;
2. record current public host signatures, Maven metadata, context/token provenance, native widget
   defaults, screenshots, and performance baselines;
3. separate platform context resolution from composition policy provision without changing the
   renderer contract;
4. remove Material types and implicit Material context selection from neutral host entry points,
   retaining a compatibility facade only when its measured migration benefit exceeds its cost;
5. verify Material and One UI roots, overlays, lazy/navigation sessions, XML/static/application
   token provenance, process/configuration recreation, and API 24/31/35/current behavior;
6. inventory every mapped component backend and close shared behavior gaps before replacing
   additional native controls; and
7. expand design-system component catalogs only after isolation, behavior, accessibility,
   screenshot, and performance gates pass.

The host refactor is rolled back if it breaks root/session coherence, public compatibility outside
the approved migration, Maven dependency ergonomics, or measured startup/patch behavior. Rollback
must restore the adapter wiring, not introduce Material knowledge into Renderer or UI Foundation.

## Related decisions

- [ADR-0002: Five-layer runtime module architecture](./0002-five-layer-runtime-module-architecture.md)
- [ADR-0003: Public package ownership and platform handles](./0003-public-package-ownership-and-platform-handles.md)
- [ADR-0004: Design-system resolution boundary](./0004-design-system-resolution-boundary.md)
- [Multi-design-system architecture and integration standard](../design-systems.md)
