---
draft: true
schema_version: 2
document_id: plan.framework-contract-hardening
doc_type: plan
owner:
  kind: project
  id: framework-contract-hardening
version_lane: version-agnostic
capability_ids: []
artifact_ids: []
sample_ids: []
status: completed
scope: Correct the twelve accepted module-audit findings and converge snapshot, subscription, native restoration, and resource cleanup contracts.
non_goals:
  - Replace the five-layer architecture or introduce a compiler plugin.
  - Change the documented default concurrent-write conflict policy.
  - Claim device or performance acceptance from JVM-only evidence.
baseline: The accepted 2026-09-06 audit examined main revision d64710459df73f3b42067767bb2f4f273b9eff33; ten findings were executed against source and two were confirmed statically.
ordered_work:
  - Freeze execution ownership and regression criteria before production changes.
  - Correct runtime snapshot identity, nested baselines, subscription lifetime, and notification finality.
  - Make editing history transactional and restore removed native focus modifiers.
  - Align image resource cache identity and finish all owned overlay cleanup after failures.
  - Bound process-tree termination and correct frame cancellation and nullable drawing caches.
  - Run applicable module, integration, documentation, release-intent, and tooling-isolation checks; record limitations with their owners.
completion:
  - All twelve findings have implementation, regression evidence, and owning documentation dispositions.
  - Applicable repository gates pass, and unavailable device evidence remains explicitly tracked rather than claimed as passed.
  - Durable conclusions have moved into active architecture and module manuals before archival.
last_verified: 2026-09-08
next_action: Review the implementation pull request; retain device and performance follow-ups with the owning manuals.
maven_release_changesets:
  - release/changes/20260906-framework-contract-hardening.json
---

# Framework Contract Hardening Plan

## Status

Completed. The user accepted the audit on 2026-09-06 and authorized integration of the latest
main, correction of remaining gate failures, and pull-request submission on 2026-09-08. All twelve
fixes, complete Quick/Preview verification, and documentation acceptance are complete. Durable
contracts and interpreted results are recorded in the owning architecture and module manuals.

The [accepted audit record](../project/records/framework-contract-audit/20260906-audit.json) preserves
finding IDs, source locations, and the observed/expected baseline independently of preview tooling.

The audit covered key paths in 39 published modules and the internal benchmark module, with
additional AI, Studio, and publishing-tool review. It was not a line-by-line or device-performance
acceptance. Existing standalone JVM tests passed (818 tests in 13 modules), as did 39 AI tool
tests; the new probes nevertheless reproduced ten defects. Resource image caching and focus
restoration were confirmed from source paths without device reproduction.

## Maven release changesets

- `release/changes/20260906-framework-contract-hardening.json`

## Execution table

| Phase | Finding | Priority | Implementation owner | Required change and acceptance | State |
| --- | --- | --- | --- | --- | --- |
| 0 | Plan and baseline | — | Project | Record this sequence, freeze the audit baseline, and preserve the default version-based conflict policy. | Complete |
| 1 | F01: derived cache aliases sibling snapshots | P1 | runtime | Distinguish snapshot views and visible dependency versions; siblings returning 10/10 must return 10/20, including nullable and nested reads. | Complete |
| 1 | F02: parent writes cause false child conflicts | P1 | runtime | Capture the child's visible parent baseline, retain genuine conflict detection, and test sequential nesting, later parent writes, abandonment, and policy merge. | Complete |
| 1 | F03: derived dependencies outlive consumers | P1 | runtime | Release upstream ownership after the final observer exits; independent reads and re-observation must remain fresh, including chains and conditional dependencies. | Complete |
| 1 | F04: observer failure interrupts committed delivery | P1 | runtime | Mark successful apply terminal before callbacks, attempt every affected notification, and report callback failures without presenting a committed transaction as retryable. | Complete |
| 2 | F05: same-document IME completion loses undo | P2 | text-core / renderer | Finalize composition history before the document-equality fast path; verify commit, selection-only updates, cancellation, and the following edit. | Complete |
| 2 | F06: abandoned snapshots mutate text history | P2 | text-core | Publish text and undo/redo/composition history as one snapshot-owned state; abandoning or conflicting cannot change committed history. | Complete |
| 2 | F08: removed focus modifiers leave native flags | P2 | renderer-android | Restore original focus flags on removal and release; verify native input defaults, custom AndroidView, and reapplication. | Complete |
| 3 | F07: image resource cache identity is host-local | P2 | ui-contract / ui-foundation / host-android / renderer-android / image adapters | Define cache-scope identity, preserve SDK resource semantics, and test equal resource IDs across environments; separate memory identity from stable disk identity. | Complete |
| 3 | F09: one overlay cleanup failure skips siblings | P2 | ui-foundation / overlay integrations | Give every owned entry/delegate a terminal cleanup attempt and aggregate failures; window teardown must survive child cleanup failure. | Complete |
| 4 | F10: task timeout does not bound descendants | P2 | tools/ai | Own and terminate the process tree and bound completion despite inherited pipes; test timeout, cancellation, output overflow, and parent exit. | Complete |
| 4 | F11: stale main-queue work revives a frame | P3 | host-android | Fence queued requests by generation; test cancel-before-post and cancel followed by a new request. | Complete |
| 4 | F12: nullable draw cache misses | P3 | graphics-core | Separate initialization from null values; test null values/keys, clear, and builder failure. | Complete |
| 5 | Verification and documentation closure | — | Each owning module | Execute applicable gates and interpret before/after evidence in owning manuals; retain explicit device/performance limitations. | Complete |

Phase 2 depends on the runtime contract from phase 1. Phase 3 applies the same ownership and
failure rules at platform boundaries. Phase 4 fixes independent bounded defects. Each phase updates
its row and evidence when completed; passing existing tests alone does not close a finding.

## Protocol decisions

1. A snapshot view has an identity and a stable visible baseline. A child inherits the parent's
   visible values at creation; writes after that point are destination changes to consider at
   apply. Strict version-based conflicts remain the default, including equal concurrent values.
2. One public logical state includes every value needed to interpret its observable properties.
   Text history is state, while native handles and callback queues are separately owned facilities.
3. Observations own subscriptions. Removing the final consumer releases upstream subscriptions;
   caches without subscriptions validate their dependencies before reuse.
4. Publication, terminal state, and callback delivery are distinct steps. Publish atomically,
   establish terminal state, release locks, attempt all callbacks, then report failures. Cleanup
   follows the same attempt-all rule without moving resource ownership into an unowned manager.
5. A native property override has a matching release operation. A cache key's identity must be
   valid in the cache's scope; a host-local invalidation counter is not global resource identity.

## Documentation and compatibility ownership

Before changing a public behavior, record the stable capability, Q level, applicable contract
fields, and exact documentation/sample dispositions in immutable impact records. Detected capability-surface changes use Governance V2
`impacts/`. Author-reviewed behavior and low-level types outside that detector retain the same
structured Q3 dispositions in [audit impact records](../project/records/framework-contract-audit/impacts/);
these records supplement, rather than bypass, structural verification. The strict gate rejects
non-detected records in its structural-impact namespace, as confirmed during this implementation.
No verifier rule was weakened. The UI Contract constructor/copy binary change is explicitly
classified breaking even though that low-level type is outside the structural detector. Runtime state is `runtime.state`, Q3, with behavior, state, lifecycle, concurrency,
callbacks, failure, performance, and compatibility fields. Snapshot architecture, Runtime manual,
KDoc, and compiled Runtime samples own phase 1. Later phases resolve their capability owners
before implementation and update their module manuals and Chinese mirrors together.

These are corrections to intended behavior. Do not add new public API merely to expose an internal
fix. Any necessary resource-identity API change receives an explicit compatibility classification,
compiled sample, and Changeset. Changesets list direct artifact changes; dependency propagation
remains the release planner's responsibility.

## Verification strategy and evidence log

Use deterministic regression tests beside the owning code, then run its existing suite and relevant
cross-module tests. Prefer the repository's Gradle tasks so source-set and dependency boundaries
are verified. The required final gates include `verifyDocumentationStructure`,
`verifyDevelopmentToolingIsolation`, and `verifyViewComposeReleaseIntent`, plus applicable module
tests and compiled samples. Run `qaQuick` where the environment permits. Do not weaken gates to
accommodate a missing environment prerequisite.

Every accepted result records baseline/candidate, exact command and environment, absolute outcome,
normalized change when meaningful, interpretation, limitation, and next action in the owning active
manual. Logical correctness counts are not latency benchmarks. CameraX rotation, high-fan-out
invalidation, spring setup cost, shallow graphics immutability, and device rendering performance
remain separate audit risks; they do not become speculative rewrites in this plan.

| Step | Evidence | Interpretation |
| --- | --- | --- |
| Baseline | Revision `d64710459df73f3b42067767bb2f4f273b9eff33`; 818 standalone JVM and 39 AI tests pass; ten source probes demonstrate defects. | Existing coverage misses composition and failure boundaries. This is a correctness baseline, not a performance claim. |

## Final implementation and integration evidence

The [initial implementation record](../project/records/framework-contract-audit/20260906-implementation.json)
preserves the original source hashes and partial acceptance. The
[integration record](../project/records/framework-contract-audit/20260908-integration.json) supersedes
its outstanding gate status after integrating main `048b63af03ca51d8f44a042fc7b26d9bae85ed01`.
All 40 selected Framework, app, and integration tasks pass 2118 tests, including 1270 tests in
the twelve changed artifacts; overlapping populations are not added together. AI passes 397 tests.
Complete `qaQuick`, `qaPreview`, fixed compiler/render corpora, complete immutable API generation,
and documentation-site verification pass. These are correctness and coverage results, not latency claims.

The initial full-QA attempt reported eight failed AI tasks. Static and actual rendered evidence
were recaptured against the refreshed source identity. Only identity strings changed in 37 visual
fixture files; PNG/tree bytes, comparison denominators, and strict rejection thresholds are unchanged.
The exact baseline has 0 mismatches among 2,523,781 pixels; the deliberate 2221-pixel regression
remains rejected, and the derived rollback restores 0 with all six gates passing.

Integration also fixes physical/aliased temporary Node detection and separates generated Preview
Gradle task histories, preventing another request's output cleanup from removing resource classes.
The distribution fixture uses a physical simulated HOME, preserving the production cache-integrity
guard. A cold image-binding render and the corresponding regression suites pass. Upstream's
unpublished `0.8.0` candidate supersedes the initial local `0.7.1`; public consumers remain on `0.7.0`.

Correctness is **improved** at the accepted boundaries; released-harness visual results show
**no material change**. The active capability-verification and AI manuals interpret the results.
Host, Renderer, and image-adapter owners retain device resource/OEM IME acceptance; AI tooling
retains Windows descendant validation. Frame and cache performance need controlled measurements.

## Completion and handoff

The user authorized a commit, branch push, and pull request after passing verification. Local Maven
publication is a QA prerequisite using an ephemeral signing key. Remote package publication and
deployment remain separate release actions. This plan is archived after moving its conclusions
into the active manuals and updating both plan indexes.
