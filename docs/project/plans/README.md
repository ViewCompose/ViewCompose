# Active Execution Plans

This directory contains multi-step work that is currently active and must survive across sessions.

## Active plans

- [Demo post-release verification closeout](./demo-post-release-verification-closeout.md) — finish
  the intentionally deferred collection-stress revision-3 fixed-clock baseline, broad bilingual and
  configuration visual matrix, popup pixel golden, and zero-caller hard deletion of obsolete Demo
  harness infrastructure after the coordinated framework and Preview-plugin release.
- [Diagnostics correlation, inspection, and production observability](./diagnostics-correlation-inspection-observability.md) —
  correlate frames and failures across render sessions, add bounded privacy-safe production failure
  aggregation, highlight real View boundaries only on explicit debug requests, and capture finite
  per-node timing without charging inactive render paths.
- [Paging 3 integration](./paging3-integration.md) — integrate official AndroidX Paging through an
  optional custom-presenter bridge, preserving ViewCompose lazy identity and renderer ownership
  without moving paging types or loading policy into the core contract.
- [Third-party Android View integrations](./third-party-android-view-integrations.md) — add a typed
  transaction-aware Android View adapter foundation and independently removable integrations for
  AndroidX Media3, legacy ExoPlayer 2, Google Maps, and CameraX with explicit theme, lifecycle,
  saved-state, construction, ownership, Preview, and device-validation contracts.
Completed architecture, animation-capability, design-system, theme-propagation, native-widget,
component-appearance, tutorial, language-consistency, migration-sample, hosted-documentation, and
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
