---
schema_version: 2
document_id: architecture.version-bound-ai-tooling-upgrades
doc_type: architecture
slug: /architecture/decisions/version-bound-ai-tooling-upgrades
owner:
  kind: project
  id: ai-development-tooling
version_lane: next
capability_ids: []
artifact_ids: []
sample_ids: []
invariants:
  - An AI tooling upgrade never selects framework knowledge by tooling recency alone.
  - Every consumer-selectable Knowledge Pack is bound to exact ViewCompose artifact versions and immutable release revisions.
  - Unresolved or conflicting consumer dependency versions fail closed without changing the installed integration.
evidence:
  - tools/ai/contracts/framework-compatibility-profile.schema.json
  - tools/ai/contracts/examples/framework-compatibility-profile.json
  - ./gradlew verifyAiToolingContracts
---

# ADR-0025: Version-bound AI tooling upgrades

- Status: Accepted
- Date: 2026-08-30

## Context

The AI tooling package has its own release cadence, while ViewCompose publishes independently
versioned Maven artifacts. A newer Agent executable or Skill workflow does not imply that its
embedded API knowledge is correct for an existing Android project. Selecting the newest AI tooling
Release without inspecting the project's ViewCompose coordinates can expose APIs added after the
project's dependencies, causing generated Kotlin to fail or, more dangerously, to express the wrong
contract while still looking plausible.

The first public tooling package contains an exact `current-source` Knowledge Bundle and a Harness
with exact released Maven coordinates. Those identities are individually deterministic, but they do
not establish that the Knowledge Bundle represents the consumer project's dependency set. The
upgrade path therefore needs a compatibility identity before it needs automatic download logic.

ViewCompose cannot use one synthetic framework version for this purpose. Each published artifact
owns its version and immutable source revision, so compatibility is a set of exact
`com.viewcompose:<artifact>:<version>` entries rather than one scalar.

## Decision

1. AI tooling runtime version and framework knowledge identity are independent. A runtime may be
   newer than its Knowledge Pack, but the active pack cannot change unless the consumer project
   matches the candidate framework profile.
2. Every consumer-selectable framework profile records each supported ViewCompose coordinate,
   exact version, immutable 40-character release revision, Knowledge Bundle fingerprint, and the
   exact Maven coordinates used by the compile/render Harness. The profile ID is content-addressed
   from this canonical data.
3. Only a `released` Knowledge Pack generated from the recorded artifact release revisions may be
   consumer-selectable. A `current-source` bundle remains valid for an exact source checkout and
   contributor workflows, but it is never inferred to represent a released consumer project.
4. Project detection is bounded and read-only. It may resolve exact literal Gradle coordinates,
   standard version-catalog declarations, and dependency lock records without executing consumer
   Gradle settings, plugins, tasks, or arbitrary build logic.
5. Every detected ViewCompose artifact must have one exact version. Dynamic versions, ranges,
   unresolved variables or aliases, and conflicting declarations reject initialization or upgrade
   unless a later explicit, separately governed resolution mechanism proves the exact graph.
6. A project with no ViewCompose dependency is a new-project case and may select the newest stable
   consumer profile. A project with ViewCompose imports or coordinates whose versions cannot be
   resolved is not treated as empty.
7. Upgrade selection chooses the newest AI tooling Release whose framework profile exactly matches
   every detected ViewCompose artifact and version. It never selects the newest Release first and
   never silently changes the project's framework dependencies.
8. Downloaded candidates must reproduce their immutable tag, declared asset inventory, SHA-256
   checksums, package metadata, framework profile, and supported contract majors before project
   mutation begins.
9. MCP configuration and canonical Skills migrate as one transaction. Only exact previously
   managed bytes may be replaced; user-edited content, an unknown MCP owner, an incompatible
   profile, or any failed write leaves the prior integration active.
10. Package installation is versioned and side-by-side so the running upgrader and last known-good
    package remain available until migration succeeds. Cache cleanup is a separate recoverable
    operation and is not part of the upgrade transaction.

## Consequences

- Framework API updates require a new released Knowledge Pack and compatibility profile before an
  Agent upgrade can use those APIs in a consumer project.
- Runtime or Skill fixes can advance without changing framework knowledge when a newer tooling
  Release declares the same exact framework profile.
- Projects using a supported older framework stay on the newest tooling Release compatible with
  that profile rather than following a global latest pointer.
- Some projects that hide versions behind arbitrary convention logic will require an explicit
  future resolution path. Failing closed costs one actionable setup step but prevents silent API
  drift.
- Independent module versions make the compatibility manifest larger, but preserve the framework's
  actual publication model and allow projects to use only a matching subset.

## Rejected alternatives

### Always install the newest AI tooling Release

Rejected because tooling recency says nothing about the framework APIs available to an existing
project. Compile repair cannot make an unavailable or semantically changed API correct.

### Compare only one primary ViewCompose artifact version

Rejected because modules are independently versioned and applications commonly combine UI,
Material, navigation, lifecycle, image, and Preview artifacts from different release revisions.

### Ask the model to infer compatibility from compiler errors

Rejected because compilation occurs after incorrect knowledge has already influenced generation,
does not cover every semantic contract, and cannot prove that an apparently compiling replacement
preserves the requested behavior.

### Execute the consumer Gradle build to obtain the resolved graph by default

Rejected because it would execute untrusted project settings, plugins, and build logic, violating
the read-only consumer boundary. A future opt-in resolver would require its own authorization,
isolation, and evidence contract.

## Validation and rollout

1. The framework compatibility profile schema and example remain in the Phase 0 contract gate.
2. The released-pack generator proves artifact versions and source revisions against immutable
   publication history and rejects unpublished or movable identities.
3. Project-detection fixtures cover literals, version catalogs, lock records, new projects,
   dynamic versions, conflicting declarations, unsupported artifacts, traversal, and symbolic
   links without invoking Gradle.
4. Candidate-resolution tests prove exact subset matching, same-profile runtime upgrades, older
   profile retention, no-candidate behavior, checksum failure, and contract-major rejection.
5. Installed-package tests reproduce success, conflict, interruption, rollback, and recovery for
   Codex, Claude Code, and Cursor before the public `upgrade` command is enabled.
6. Public documentation must distinguish the currently installed runtime version, active framework
   profile, Knowledge Bundle fingerprint, and achieved evidence lane.
