---
schema_version: 2
document_id: architecture.in-memory-development-tooling-installation
doc_type: architecture
slug: /architecture/decisions/in-memory-development-tooling-installation
owner:
  kind: capability
  id: diagnostics.session-inspection
version_lane: released
capability_ids:
  - diagnostics.session-inspection
  - host.android-container
  - preview.integration
  - animation.composition-motion
artifact_ids:
  - viewcompose-ui-foundation
  - viewcompose-host-android
  - viewcompose-animation
  - viewcompose-preview
sample_ids: []
invariants:
  - Host and Animation each resolve one synchronized process-local nullable tooling slot exactly once, with absence, ambiguity, and late installation failing closed.
  - Preview installs both neutral ports in memory before application startup only for a debuggable process; the inactive path performs no discovery or file I/O.
evidence:
  - Host and Animation slot suites, Preview initializer tests, development-tooling isolation verification, debug and release manifest checks, and accepted Pixel StrictMode evidence.
---

# ADR-0022: In-memory development-tooling installation

- Status: Accepted
- Date: 2026-08-24
- Supersedes: the `ServiceLoader` discovery mechanism in
  [ADR-0009](0009-development-tooling-isolation.md) and the animation-provider discovery mechanism
  in [ADR-0019](0019-animation-physics-transition-and-inspection-ownership.md)

## Context

ADR-0009 moved concrete running-device tooling downstream and limited inactive runtime cost, but
its Host integration still performed a lazy `ServiceLoader` scan when the first render session was
created. The animation timeline later adopted the same pattern when the first transition attached.

Pixel 4 XL API 33 credentialed Google Maps acceptance on 2026-08-24 enabled thread and VM
`StrictMode` around initial composition. The first Host session produced four integration-owned
main-thread disk-read violations, all originating from `ServiceLoader` configuration lookup. This
contradicted ADR-0009's inactive-path prohibition on tooling file I/O. Delaying, caching, or
filtering the violations would preserve the incorrect ownership boundary.

## Decision

1. Runtime and animation artifacts do not discover development tooling from classpath resources.
   Each owns one synchronized, process-local nullable slot for its neutral tooling port.
2. The optional `viewcompose-preview` artifact contributes one non-exported Android initialization
   provider. Android creates it before `Application.onCreate` and Activities. It first verifies
   `FLAG_DEBUGGABLE`, then installs both neutral port implementations directly in memory.
3. Installation must finish before the first Host session or transition reads its port. First read
   freezes the slot for the process lifetime. No installation freezes to `null`; later installation
   is ignored.
4. Reinstalling the same instance before first read is idempotent. Distinct instances make the slot
   ambiguous and permanently disable it. Provider order never selects a winner.
5. Installation and first read are synchronized, non-blocking memory operations. They perform no
   classpath scan, file I/O, serialization, View traversal, listener registration, report work, or
   thread creation.
6. The three activation gates remain independent: optional artifact presence causes only passive
   port installation; a debuggable process permits tooling; an explicit valid IDE request enables
   bounded inspection work.
7. Host and Animation expose Q3 downstream integration hooks with compiled samples. Applications
   and ordinary custom hosts do not call them.

## Alternatives considered

### Keep `ServiceLoader` and allow one startup disk read

Rejected because the measured operation occurs on the first render session's main-thread path and
directly violates the accepted inactive tooling contract. Caching changes frequency, not ownership.

### Move discovery to a worker

Rejected because the first sessions could observe a provider nondeterministically, while joining
the worker would still block rendering. It would also retain classpath discovery for a provider
whose artifact already controls Android manifest merging.

### Add an AndroidX Startup dependency

Rejected because a single non-exported provider supplies the required pre-Application ordering
without adding another runtime dependency or initialization graph. The provider performs only the
two bounded in-memory installations.

## Consequences

- First Host composition and first transition attachment perform no development-tooling discovery
  I/O. The no-tooling path is one frozen nullable read.
- Preview presence is mechanically visible in the merged manifest and remains excluded from normal
  release configurations by the existing classpath guard.
- Tooling implementations remain downstream and cannot be selected by classpath order.
- A tooling artifact that initializes after application rendering began remains disabled for that
  process. This fail-closed rule is intentional and must not be patched with late retry.
- The manifest initializer runs in the application's default process. A future multi-process
  inspection design requires an explicit process contract rather than implicit discovery.

## Validation and rollout

1. Host and Animation unit tests cover absence, one provider, idempotent same-instance install,
   ambiguity, frozen selection, and rejected late installation.
2. Preview unit tests prove both neutral implementations are installed together and that a
   non-debuggable process installs neither port.
3. `verifyDevelopmentToolingIsolation` continues to enforce dependency direction, inactive-path
   restrictions, and release-classpath exclusion.
4. A credentialed Pixel device test wraps first Maps composition and lifecycle/state changes in
   `StrictMode`; no integration-owned violation may remain. Third-party Google SDK violations are
   reported separately and cannot be attributed to ViewCompose.

## Acceptance evidence

On 2026-08-24, the same Pixel 4 XL / API 33 credentialed Maps path reported four Host-owned
`DiskReadViolation` events before this hard cut and zero integration-owned violations afterward, a
100% reduction for the detected defect. The final 19.422-second method covered first composition,
state updates, background/resume, recreation, and release. It separately reported 18
`IncorrectContextUseViolation` and five `UntaggedSocketViolation` events whose first owning frames
were inside Google Maps; the adapter's `MapView` Context was verified as a UI Context.

Host, Animation, and Preview unit suites passed, including slot absence, selection, idempotence,
ambiguity, late-install rejection, paired installation, and the non-debuggable gate.
`verifyDevelopmentToolingIsolation`, documentation/translation verification, and the three affected
Dokka publications passed. The merged Demo manifest contained the initializer in debug and omitted
it in release. The conclusion is **improved**. Limitations: the network-dependent duration is not a
performance measurement; the device path exercised Host startup,
not an animation-timeline request; no frame-performance or power comparison was run because the
removed operation was deterministic startup I/O. The next action is to retain the strict startup
gate during CameraX acceptance.
