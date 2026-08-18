# Active Execution Plans

This directory contains multi-step work that is currently active and must survive across sessions.

## Active plans

- [Cross-session theme propagation](./cross-session-theme-propagation.md) — make one observable
  application theme choice converge across independent Activity roots, add a secondary-Activity
  switch-and-return Demo, and refresh retained NavHost destinations with the latest Local snapshot
  before they become visible without recreating their owners or sessions.
- [Multi-design-system and high-fidelity theme](./multi-design-system-high-fidelity.md) — establish
  immutable foundation-token and typed component-recipe layers, validate them with a deliberately
  non-Material internal design system, then stage shared primitives, motion, capability fallbacks,
  root switching, and a first public high-fidelity non-Material design-system slice without adding
  design-system policy to Android Renderer.
- [Material 3 design convergence](./material3-design-convergence.md) — release the completed token,
  theme-bridge, component-default, touch-target, and state-layer scope; TextField and Switch/Slider
  structural fidelity remain roadmap candidates that require separate future plans after their
  activation triggers are met.
- [Five-layer module architecture hard cut](./five-layer-module-architecture-hard-cut.md) —
  replace the broad foundation/optional split with enforceable Kernel, UI Foundation, Android
  Engine, Design System, and Integration layers; rename misleading artifacts, isolate Material 3,
  converge exclusive package ownership and opaque platform handles, and restore the complete Maven,
  sample, tooling, and documentation gates after the hard cut.
- [Demo benchmark and verification harness rearchitecture](./demo-benchmark-verification-harness-rearchitecture.md) —
  replace the module-oriented, text-coupled Demo with directly launchable revisioned scenarios,
  locale-independent automation targets, fixture-first benchmark hosts, complete English and
  Simplified Chinese resources, and optional human guidance before benchmarking Runtime/View patch
  optimizations against a new stable baseline.
- [Observed property transactions](./observed-property-transactions.md) — hard-cut explicitly
  observed node properties onto a session-owned, frame-batched transaction path that reads one
  Snapshot, patches only affected mounted nodes, rolls back the complete batch on failure, and
  preserves full composition for structural changes.
- [Lazy collection memory efficiency](./lazy-collection-memory-efficiency.md) — remove per-element
  lazy-session callback wrappers, converge duplicate adapter key metadata, and make common shape
  drawing resources lazy while rejecting any memory win that shifts work into bind or fling paths.
Completed native-widget, component-appearance, tutorial, language-consistency, migration-sample,
hosted-documentation, and version-retention plans are retained in the
[archive](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/README.md).

Before adding a plan, read [Documentation governance](../documentation-governance.md). A plan must
have a clear completion condition, be updated during implementation, and move to
[`docs/archive/`](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/README.md)
when complete.

Every active plan must also contain exactly one `## Maven release changesets` section. Declare
`- None.` before publication-relevant implementation begins; afterward, replace it with one
inline-code bullet for every immutable `release/changes/*.json` file owned by the plan. Maven
Central upload rejects selected direct or dependency-propagated artifacts while their linked plan
remains in this directory.
