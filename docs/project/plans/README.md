# Active Execution Plans

This directory contains multi-step work that is currently active and must survive across sessions.

## Active plans

- [Five-layer module architecture hard cut](./five-layer-module-architecture-hard-cut.md) —
  replace the broad foundation/optional split with enforceable Kernel, UI Foundation, Android
  Engine, Design System, and Integration layers; rename misleading artifacts, isolate Material 3,
  converge exclusive package ownership and opaque platform handles, and restore the complete Maven,
  sample, tooling, and documentation gates after the hard cut.
- [Runtime data propagation and Android View patch optimization](./runtime-data-propagation-and-view-patch-optimization.md) —
  establish diagnostic and benchmark baselines, optimize atomic state publication, modifier-only
  View patches, LocalSnapshot allocation, and conditionally shared frame scheduling while recording
  explicit rollback gates and rejected high-risk alternatives.
- [Compose migration capability convergence](./compose-migration-capability-convergence.md) —
  prioritize lifecycle, ownership, keyed identity, RTL, Insets, and atomic navigation gaps while
  preserving the native Android View engine and recording explicit test, rollback, and rejection
  decisions for high-risk Compose parity work.

Completed tutorial, language-consistency, migration-sample, hosted-documentation, and
version-retention plans are retained in the
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
