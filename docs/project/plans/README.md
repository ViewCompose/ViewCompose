---
schema_version: 2
document_id: project.plan-index
doc_type: project
owner:
  kind: project
  id: planning
version_lane: version-agnostic
capability_ids: []
artifact_ids: []
sample_ids: []
workflow: Index active multi-session execution plans and preserve their release-blocking Changeset ownership until archival.
validation:
  - ./gradlew verifyDocumentationStructure verifyViewComposeReleaseIntent
lifecycle: Update whenever an execution plan starts, changes status, becomes blocked, completes, or moves to the archive.
---

# Active Execution Plans

This directory contains multi-step work that is currently active and must survive across sessions.

## Active plans

The first two plans form one coordinated program. Freeze and extract the existing quality gates
before adding Governance V2 scanners; establish the generated reference core before broad content
movement; then use the documentation migration pull requests to observe the selective CI rollout.
Detailed plan pages remain repository-only drafts linked from this index because they contain
temporary contributor execution state rather than user guidance. They do not enter the public
site, search index, or sitemap.

- [Pull-request gate scaling and build-logic modularization](./pull-request-gate-scaling-and-build-logic-modularization.md) —
  split the 2,582-line root gate implementation into compiled, testable build logic, then add
  conservative pull-request impact selection, verified immutable-API caching, and affected-module
  verification without weakening required checks or full `main` validation.
- [Documentation system governance V2 and capability restructure](./documentation-system-governance-v2.md) —
  process-first remediation for capability ownership, document-type and version-lane contracts,
  complete executable-sample discovery, a no-new-debt ratchet, generated DSL/Modifier reference,
  information-architecture repair, and prioritized standalone Tutorial/Guide coverage.
- [Demo post-release verification closeout](./demo-post-release-verification-closeout.md) —
  hardware-deferred because no currently available physical device can prove the required stable
  CPU, GPU, and display-pipeline control. All other phases are complete; resume only to recapture
  the unchanged collection-stress revision-3 scroll baseline when a qualifying device is available.

Completed architecture, animation-capability, design-system, theme-propagation, native-widget,
component-appearance, tutorial, language-consistency, migration-sample, hosted-documentation, and
version-retention, and Paging-integration plans are retained in the
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
