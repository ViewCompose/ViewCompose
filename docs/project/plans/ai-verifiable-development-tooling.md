---
draft: true
schema_version: 2
document_id: plan.ai-verifiable-development-tooling
doc_type: plan
owner:
  kind: project
  id: ai-development-tooling
version_lane: version-agnostic
capability_ids: []
artifact_ids: []
sample_ids: []
status: active
scope: Make ViewCompose reliably discoverable, searchable, generatable, compilable, renderable, and diagnosable by coding agents through one versioned knowledge contract and isolated development-tooling pipeline.
non_goals:
  - Embed an AI model, provider SDK, credential, or network client in any ViewCompose runtime artifact or application process.
  - Claim unrestricted conversion fidelity for arbitrary Android XML, Jetpack Compose, screenshots, or Figma documents.
  - Treat generated code as correct merely because it parses, matches a screenshot, or passes a static symbol check.
  - Use runtime VNode or renderer internals as the interchange representation for migration and design tools.
  - Replace canonical KDoc, compiled samples, module manuals, migration guides, or capability governance with a parallel AI-only documentation system.
  - Market ViewCompose as AI-first before the accepted accuracy, safety, latency, compatibility, and reproducibility gates pass.
baseline: >-
  Release 0.7.0 is published through GitHub OIDC as an attested npm package and immutable GitHub
  Release. Waves A-D are publicly accepted: exact-version one-command onboarding, high-confidence
  project analysis, strict offline Figma import, and attended screenshot repair with a
  non-source-writing preparation tool, separate source-application executable, typed
  single-property edit, secure transaction journal, crash recovery, immutable receipts,
  later-edit protection, explicit rollback, and an eighth Skill. Repository-external
  public-package onboarding, MCP, and transaction-status reproduction pass. Compose migration
  remains unactivated at lowest priority.
ordered_work:
  - Replace the two-command global installation path with one version-explicit public npm bootstrap that durably installs, configures, and diagnoses the selected Agent on macOS, Linux, and Windows.
  - Extend the existing read-only analyze_project surface with a versioned finding contract, measurable high-confidence rules, explicit unsupported coverage, and audited suppressions.
  - Add a provider-neutral Figma design-tree adapter to the same Design IR only after provenance, asset, privacy, and unsupported-semantics contracts are frozen.
  - Publicly activate attended screenshot repair only after a separate authorized source-application transaction, diff, durable outcome, and rollback boundary passes its gates.
  - Freeze and publish a current-version Jetpack Compose-to-ViewCompose semantic mapping and client-neutral conversion Skill only after the higher-value analyzer, Figma, and screenshot-repair waves are complete.
  - Implement a bounded compiler/AST-based Compose converter over the accepted mapping and Design IR, with explicit unsupported results and compile evidence, as the final scheduled wave.
completion:
  - A new or existing Android project can install the exact compatible AI tooling, configure one supported Agent, copy all Skills, and receive a readiness result from one project-root command without a ViewCompose checkout or global npm install.
  - The one-command path passes fresh macOS, Linux, and Windows adoption fixtures and never points MCP configuration at an ephemeral npx directory.
  - Every supported AI-facing answer can identify its ViewCompose artifact/version, canonical source fingerprint, symbols, samples, and validation evidence.
  - Generated snippets use real published APIs and pass the required isolated compilation gate; renderable UI also passes Preview diagnostics and declared semantic or visual checks.
  - MCP, CLI, skills, validators, converters, and model adapters remain downstream development tools with no inactive-path or release-runtime footprint.
  - XML, Compose, screenshot, and Figma paths share one explicit Design IR, preserve provenance and unsupported semantics, and never silently invent application behavior.
  - Accuracy, false-positive, latency, resource, privacy, and security thresholds are frozen before implementation and satisfied by reproducible CI or accepted device evidence.
  - All affected capability, API, sample, module, architecture, tooling, security, migration, release-intent, and localized documentation gates pass before archival.
last_verified: 2026-09-02
next_action: Maintain the exact released 0.7.0 framework profile and collect adoption/support evidence for analyzer, Figma, and attended repair workflows. Keep Compose mapping and conversion unactivated at lowest priority until explicit user demand justifies a new product decision.
maven_release_changesets:
  - release/changes/20260829-preview-worker-jvm21-resolution.json
---

# AI-Verifiable Development Tooling Plan

## Status

Wave A completed on 2026-09-01 with public `@viewcompose/ai-tooling@0.4.1`, exact-version
one-command adoption for all three clients, protected OIDC provenance, and removal of temporary
bootstrap authority and selectors. Product priority was then changed to maximize value for existing
Android View users: enhanced analysis, Figma import, and attended screenshot repair now precede the
lower-probability Compose migration path. Wave B completed with public
`@viewcompose/ai-tooling@0.5.0` and installed-package reproduction of the versioned analyzer. Wave C
completed with public `@viewcompose/ai-tooling@0.6.0`, protected OIDC provenance, offline Figma
inspection and generation, real compilation and Preview comparison, MCP parity, and exact-version
onboarding reproduction for all three clients. Wave D has implemented its bounded property mapper,
secure directory-handle transaction host, crash reconciliation, non-source-writing MCP preparation,
separate attended CLI, and eighth client-neutral Skill in public `0.7.0`. Its exact installed
implementation has passed repository-external human-confirmed apply, crash recovery, later-edit
rollback refusal, successful rollback, protected SLSA publication, three-client onboarding, MCP,
and public transaction-status reproduction. Compose mapping and conversion remain unactivated at
lowest priority. The
chronological handoffs below remain evidence rather than competing next actions.

### Accepted post-0.3.0 execution handoff (2026-08-31)

This section is the current execution authority for the next machine and supersedes earlier
then-current “next action” sentences retained in the chronological evidence below. It is a
planning-only handoff: this documentation change does not modify the AI package, schemas, client
configuration, framework source, publication workflow, or runtime behavior.

Release `0.3.0` is complete. The immutable tag `ai-tooling-v0.3.0` points at
`a5c7de196a92e0d76caae28876fddaa4f3493e5e`, and its successful publication run produced the
tarball, Manifest, and checksum list with GitHub attestations. Public installation, checksum and
attestation verification, the three client profiles, and `project-bound-ready` diagnosis were
accepted on 2026-08-31. Publication is therefore no longer an open action.

The product priority is now adoption cost, followed by capability breadth. Work must execute in
this order unless this plan and the unified roadmap are changed together:

#### Wave A npm identity activation evidence (2026-09-01)

The one-time bootstrap started from clean `main` commit
`e43bcb853d53bc2e1021e1da6fc0a26d04d821d0`. Before dispatch, the protected
`ai-tooling-release` environment contained `NPM_BOOTSTRAP_TOKEN`, npm returned `E404` for
`@viewcompose/ai-tooling`, and the bootstrap workflow had zero prior runs. It was dispatched exactly
once as [run `33461590751`](https://github.com/ViewCompose/ViewCompose/actions/runs/33461590751),
and the protected deployment was approved through the GitHub API. The job ran for 8 minutes 44
seconds; checkout, JDK/Node/Gradle/Android setup, the complete frozen release gate, the empty-package
guard, two byte-identical prerelease seeds, the packed-tree comparison, MCP handshake, and npm
publication all passed. npm accepted `@viewcompose/ai-tooling@0.4.0-bootstrap.0` with public access
and a signed GitHub provenance statement at transparency-log index `2670521028`.

The final workflow step queried the new package approximately 0.34 seconds after npm accepted it and
received `E404`, so the run concluded `failure` rather than masking the unmet observation. The same
immutable version became publicly readable at `2026-09-01T02:24:56Z`, approximately 5 minutes 2
seconds after publication, within npm's publish-time scanning window. The workflow was not rerun:
there is one dispatch, one publication, and no second attempt that could overwrite or ambiguously
accept the identity. Public verification then found exactly one version,
`0.4.0-bootstrap.0`; the `bootstrap` dist-tag points to it; exact stable `0.4.0` remains `E404`; and
`dist.attestations.provenance.predicateType` is exactly `https://slsa.dev/provenance/v1`.

npm also assigned `latest` to the first package version despite the explicit `--tag bootstrap`.
Authenticated removal attempts with npm 11.8.0 and with Node 24.19.0 plus npm 12.0.2 both reached
the registry and returned `400 Bad Request`; the external registry therefore did not permit the
frozen no-`latest` intermediate state. This is a bounded bootstrap limitation, not an accepted
consumer selector: every public command remains pinned to exact `0.4.0`, ordinary stable ranges
exclude the prerelease, and the stable OIDC publication must replace `latest` with `0.4.0`. The
temporary `bootstrap` dist-tag remains until that stable identity is verified, after which it must
be removed while the immutable prerelease stays in version history.

Trusted Publishing was then created with Node 24.19.0 and npm 12.0.2. Trust relationship
`37e878fb-a68c-4633-ad92-d98fdbafeb4a` binds package `@viewcompose/ai-tooling` to GitHub repository
`ViewCompose/ViewCompose`, workflow `ai-tooling-release.yml`, environment
`ai-tooling-release`, and direct publish permission (`createPackage`). `npm trust list` reproduced
that exact record. The GitHub environment secret was deleted and a subsequent API lookup confirmed
its absence. The scope-restricted bootstrap token was revoked after a security-key challenge, and
`npm token list --json` reported zero remaining tokens and no matching bootstrap name.

Compared with the pre-dispatch state, public npm identities changed from 0 to 1, trusted-publisher
relationships from 0 to 1, and temporary publication authorities from 2 to 0; stable consumer
versions remain 0 until the tag release. The conclusion is **mixed**: package creation,
provenance, OIDC binding, and credential withdrawal succeeded, while immediate registry
verification and the no-`latest` intermediate assumption did not match current npm behavior. The
next action is to merge the cleanup that removes the one-time workflow and transformer, tag only
that cleanup merge, publish stable `0.4.0` through OIDC, verify public installation and release
assets, remove `bootstrap`, and record the final Wave A evidence before starting Wave B.

Cleanup acceptance exposed one macOS compatibility defect before the stable tag: an existing
case-insensitive cache whose stored spelling was `viewcompose` was reopened through the newer
`ViewCompose` spelling. Initialization wrote the lexical spelling, while the following integrity
inspection compared it with `realpath` and misclassified the case-only alias as symbolic-link
traversal. The accepted repair canonicalizes only the newly materialized durable-cache path after
proving, prefix by prefix, that both spellings identify the same filesystem objects and that neither
prefix is a symbolic link. Project roots and active package roots retain their original strict
canonical-path check.

Before the repair, 0/1 installed Codex `doctor` checks accepted the real historical-cache fixture;
after it, 1/1 reached `project-bound-ready`, a `+100` percentage-point normalized change. The
focused client suite passed 14/14 cases, including 1/1 case-only APFS alias and 1/1 cache-symlink
rejection. The complete release gate then passed 2/2 reproducible package builds, 1/1 offline
install/uninstall lifecycle, 3/3 installed Agent profiles, 18/18 exact Skill copies, 2/2 MCP
protocol versions, the Kotlin and XML compile lanes, generated Preview and layout comparisons, and
exact RGBA comparison; it also verified all 3/3 Release assets. The conclusion is **improved**
macOS bootstrap compatibility with **no material Android runtime behavior change**. The positive
case-alias evidence is one local case-insensitive macOS filesystem; Linux and Windows remain the
hosted adoption matrix. The next action remains the cleanup PR followed by the immutable stable tag
and final public-install evidence.

#### Wave A stable-publication correction evidence (2026-09-01)

Cleanup pull request [#263](https://github.com/ViewCompose/ViewCompose/pull/263) passed the hosted
Linux, macOS, and Windows adoption matrix plus the complete repository gates and merged as
`9805d58e10d93cfbb6d02745c794182d9c066c14`. Tag `ai-tooling-v0.4.0` points to that cleanup merge.
Protected [release run `33469275204`](https://github.com/ViewCompose/ViewCompose/actions/runs/33469275204)
completed all steps in 9 minutes 11 seconds, including reproducible distribution, the unpublished
identity guard, GitHub attestations, immutable Release creation, and npm Trusted Publishing. The
registry exposes exact stable `0.4.0`, `latest` points to it, the SLSA v1 statement names
`.github/workflows/ai-tooling-release.yml` at the exact stable tag, and all 3/3 GitHub Release assets
passed their SHA-256 and GitHub attestation checks.

The required repository-external acceptance then ran the literal documented command from an empty
physical project with a fresh npm cache. The result was 0/1 successful shorthand launches:
`npx --yes @viewcompose/ai-tooling@0.4.0 init --client codex` exited 1 before project writes because
npm could not determine an executable. The published manifest contains `viewcompose-ai`,
`viewcompose-agent`, and `viewcompose-mcp`, but no binary matching the unscoped package name
`ai-tooling`; npm therefore cannot infer one default among the three. The explicit diagnostic form
using `--package=@viewcompose/ai-tooling@0.4.0 -- viewcompose-agent` reached
`project-bound-ready`, installed 6/6 Skills, passed `doctor`, and removed the configuration plus
6/6 Skills, proving the payload rather than the public one-command contract is at fault.

The normalized public shorthand result remains 0% rather than the required 100%, so the conclusion
is **mixed**: publication, provenance, assets, payload, and explicit lifecycle improved to an
accepted state, while the primary onboarding command regressed from a green packed-candidate test
to a public npm selection failure. The packed adoption verifier had exercised an explicit
`viewcompose-agent` binary and therefore did not measure the documented npm inference boundary.
Release `0.4.0`, its tag, Release, assets, and attestations remain immutable audit history and are
not rewritten or unpublished. Wave A now requires corrective `0.4.1`, which retains all explicit
binaries, adds `ai-tooling` as an alias of the transactional Agent entry point, and changes the
packed adoption gate to execute the same shorthand users copy. Only after 3/3 clients pass that
exact public-registry command may `0.4.0` be deprecated, `bootstrap` removed, Wave A closed, and
Wave B begin.

The corrective packed candidate now passes 3/3 shorthand client lifecycles, 3/3 durable MCP
handshakes, and 4/4 native path cases on local macOS, including a tarball and projects under paths
with spaces and non-ASCII characters. Relative to the published `0.4.0` shorthand result, local
success changed from 0/1 to 3/3, an absolute gain of three accepted client profiles and a normalized
change from 0% to 100%. The conclusion is **improved** default-entry selection with no Android
runtime or framework-API change. This remains packed-candidate evidence on one case-insensitive
macOS host; hosted Linux/macOS/Windows adoption, public `0.4.1` installation, provenance, assets,
`0.4.0` deprecation, and `bootstrap` removal remain required before Wave A closes.

The complete local AI script suite passed 322/322 cases with zero failures. The clean JDK 21,
Node 24, and release-pinned npm 11.8 Gradle run completed 194 tasks in 2 minutes 48 seconds
(11 executed and 183 up-to-date): 2/2 package builds were byte-reproducible, the npm dry-run and
offline lifecycle passed 1/1 each, installed profiles passed 3/3 with 18/18 exact Skill copies,
both MCP protocol versions passed, all compile/Preview/layout/screenshot/XML/pixel lanes retained
their accepted identities, and 3/3 Release assets passed. Documentation verification accepted 132
English pages, 129 Chinese pages, 129/129 current translations, 80/80 documentation-script tests,
and zero Governance V2 issues. Development-tooling isolation passed. Release-intent detected zero
Maven artifacts, ignored artifacts, or shared release paths, so this npm-only correction requires no
Maven release changeset. Compared with the previous 322/322 and 129/129 denominators, failure rates
remain 0% and translation coverage remains 100%; the only normalized behavioral change is the
shorthand adoption result above. The conclusion remains **improved** with no material framework
runtime change; public and hosted evidence is still pending.

#### Wave A corrective-release acceptance evidence (2026-09-01)

Default-entry correction pull request
[#264](https://github.com/ViewCompose/ViewCompose/pull/264) passed all 11 required checks and merged
as `fec75d4842b99cf9a59eaa2ba6d169c2cfc37aa1`. Its hosted
[adoption run `33471574680`](https://github.com/ViewCompose/ViewCompose/actions/runs/33471574680)
executed the npm inferred-binary shorthand rather than naming `viewcompose-agent`: Linux, macOS,
and Windows each passed 3/3 client lifecycles, 3/3 durable MCP handshakes, and 4/4 native path
cases. Across the matrix that is 9/9 client lifecycles, 9/9 handshakes, and 12/12 path cases, with
zero failures.

Annotated tag `ai-tooling-v0.4.1` resolves exactly to that merge. Protected
[release run `33473341135`](https://github.com/ViewCompose/ViewCompose/actions/runs/33473341135)
completed in 10 minutes 31 seconds and passed every setup, reproducible-distribution,
unpublished-identity, attestation, GitHub Release, and npm Trusted Publishing step. The immutable
[GitHub Release](https://github.com/ViewCompose/ViewCompose/releases/tag/ai-tooling-v0.4.1)
contains exactly 3/3 assets. Their GitHub-recorded SHA-256 digests are
`4f1bcd8ab5ebb84a2c6775409511f045f8cb745b1b9576d08930c17318d794e4` for the tarball,
`a465f498e7254d1674d7df487f8ca7caf4cf6faeab27c0a89fa3c9ca23b47901` for
`manifest.json`, and
`e0bc5cd1cd43376cd2c4a1a35a8cdd74f3511012aed08f81a748227b20ebc37f` for
`SHA256SUMS`. The checksum list passed 2/2 entries, GitHub attestation verification passed all 3/3
assets, and a fresh public npm pack reproduced the exact tarball SHA-256.

npm now exposes versions `0.4.0-bootstrap.0`, `0.4.0`, and `0.4.1`, with only
`latest -> 0.4.1`; the temporary `bootstrap` dist-tag is absent while the prerelease remains
immutable history. Stable `0.4.0` carries the actionable deprecation message
`Default npx entry is unavailable; use @viewcompose/ai-tooling@0.4.1.` The `0.4.1` SLSA v1
statement resolves dependency commit `fec75d4842b99cf9a59eaa2ba6d169c2cfc37aa1`, workflow
`.github/workflows/ai-tooling-release.yml`, ref `refs/tags/ai-tooling-v0.4.1`, protected environment
`ai-tooling-release`, invocation
`https://github.com/ViewCompose/ViewCompose/actions/runs/33473341135/attempts/1`, and transparency
log index `2671959148`. The release environment reports zero secrets; the one-time token and
bootstrap workflow had already been removed before the tag.

Repository-external public acceptance used a physical minimal Android project with exact
`com.viewcompose:viewcompose-ui-foundation:0.1.0-alpha02` and independent fresh npm caches. The
literal documented `npx --yes @viewcompose/ai-tooling@0.4.1` command completed
`init -> doctor -> uninstall` for Codex, Claude Code, and Cursor: 3/3 clients reached
`project-bound-ready`, 18/18 Skill copies were ready, all 3/3 knowledge/generation lanes were
`ready`, all 3/3 compile/Preview/layout lanes were `project-bound-ready`, and all 3/3 uninstalls
removed only their managed configuration and 6/6 Skills.

Relative to public `0.4.0`, shorthand success changed from 0/1 to 3/3, a normalized change from 0%
to 100%; hosted coverage adds three operating systems with no failures. The primary conclusion is
**improved** one-command adoption with no material Android runtime or framework-API change. The
public registry lifecycle was exercised on one macOS host, while Linux and Windows used the exact
packed candidate in CI; proprietary Agent UI authentication and discovery remain documented user
checks rather than automated claims. Wave A is complete. The next action is Wave B contract freeze
for the current-version Compose semantic mapping and client-neutral conversion Skill, not converter
implementation.

#### Wave A post-publication gate correction evidence (2026-09-01)

The first attempt to run the frozen release gate after publication passed 0/1 npm inventory checks:
`npm publish --dry-run` correctly refused to overwrite immutable `0.4.1`, so a gate that protected
an unpublished tag could no longer protect `main` after that tag existed. A first published-version
check also passed 0/1 archive comparisons. Investigation found two independent portability facts:
the local checkout materialized `gradlew.bat` with CRLF while the Linux release checkout used LF,
and platform compressors emitted different gzip bytes for an otherwise byte-identical tar payload.
No staged package file differed after extraction.

The package builder now canonicalizes the mapped batch wrapper to LF. The inventory gate queries
the exact registry version; an `E404` retains the original publish dry-run, while a published version
is downloaded by exact identity. That download must match npm's registry SHA-512 identity, and its
uncompressed tar payload plus complete inventory must match the frozen candidate. Authentication,
authorization, and transient registry errors fail closed rather than being reclassified as an
unpublished version. Focused portability and registry tests passed 9/9 cases. The complete installed
distribution then passed 2/2 reproducible builds, 1/1 published-payload inventory, 1/1 offline
install/uninstall lifecycle, 1/1 SPDX/license inventory, 3/3 Agent profiles, 18/18 exact Skill
copies, 2/2 MCP protocol versions, and all accepted Kotlin, XML, screenshot, compile, Preview,
layout, and exact-pixel lanes. The complete Node suite passed 328/328 tests. The root acceptance
passed 194 tasks in 1 minute 17 seconds, including 3/3 Release assets, 129/129 required current
translations, 80/80 documentation-script tests, zero Governance V2 issues, development-tooling
isolation, and zero Maven release artifacts, ignored artifacts, or shared-path classifications.

Relative to the post-publication baseline, the npm inventory result changed from 0/1 to 1/1, a
normalized change from 0% to 100%. The conclusion is **improved** continuous release integrity and
cross-platform reproducibility with no package payload, Maven artifact, framework API, or Android
runtime change; therefore neither `0.4.2` nor a Maven release changeset is required. The check
requires npm registry availability after publication and proves Agent configuration/protocol
behavior rather than proprietary UI authentication. The next action remains the final evidence PR,
followed by Wave B contract freeze only after that PR merges.

#### Wave A contract-freeze evidence (2026-08-31)

The first Wave A slice froze `bootstrap-v1` in
`tools/ai/contracts/bootstrap.schema.json` with a checked-in example and three negative tests.
The contract fixes the exact npm identity `@viewcompose/ai-tooling@0.4.0`, Node `>=24.19.0`, the
three supported clients, physical-current-directory resolution, logical symlink rejection, the
content-addressed durable cache layout, durable-only MCP paths, staged-rename recovery, exact
current-profile selection, and the complete bootstrap result shape. It intentionally changes no
installer or project-write behavior. Against the pre-change Phase 0 contract suite, the focused
post-change suite passed 2/2 test files (schema acceptance plus Phase 0 integration), with the
schema inventory increasing from 27 to 28 and all existing 66 metrics, 74 cases, and 71 fixture
cases unchanged. This is **improved** contract coverage with no runtime-behavior claim; the
remaining limitation was that platform adoption was not yet implemented. The deterministic bootstrap
implementation is now present in `tools/ai/scripts/agent-client-integration.mjs`: omitted roots use
the physical current directory, published packages materialize into a content-addressed user cache,
MCP configuration uses only the durable package, and `init` includes the readiness diagnosis.
Focused post-implementation validation initially passed 13/13 targeted tests, 312/312 total AI Node
tests, and 3/3 deterministic package-distribution tests, including source-removal persistence and
transaction rollback. The package/adoption slice then advanced the candidate identity to public,
script-free `@viewcompose/ai-tooling@0.4.0`, upgraded the client and release contracts to majors 5
and 2, and added `.github/workflows/ai-tooling-adoption.yml`. That matrix runs the real packaged npx
bootstrap on Linux, macOS, and Windows for all three clients from paths containing spaces and
non-ASCII characters; it checks integrated diagnosis, exact re-entry, deletion of the temporary npx
cache, durable MCP handshake, symbolic-link rejection, exact Skill ownership, and uninstall.

Local Linux acceptance passed 3/3 client bootstraps, 3/3 durable MCP handshakes, and 4/4 native path
cases. Durable bootstrap and upgrade caches now re-hash every package byte on reuse; one focused
tamper case changes diagnosis from a false Ready possibility to `repair-required`. Follow-up review
extended that fail-closed boundary from exact `0.4.0` to every later stable package, normalized
missing and malformed integrity markers into repair-required results, made concurrent cache writers
converge only after byte verification, and routed npm upgrade invocations through the Node entry
point so Windows command shims are not executed directly. Prerelease, build-metadata, and malformed
package versions are rejected rather than exempted from the stable-version gate. The complete AI
Node suite increased from 312/312 to 320/320 while retaining a zero-failure rate; the added cases
cover `0.4.0`, `0.4.1`, and `1.0.0` integrity gates, invalid version forms, a malformed marker,
missing marker, changed package bytes, concurrent materialization, and portable npm invocation.
`verifyDocumentationStructure` passed 19 tasks in 18 seconds, including 132 canonical-English
pages, 129/129 current Chinese mirrors, 80/80 documentation-script tests, and zero Governance V2
issues. After adding the npm publication inventory and cache-integrity checks, the combined JDK 21
and Gradle 9.3.1 `verifyDocumentationStructure verifyAiToolingRelease` gate passed 192 tasks in 2
minutes 1 second (9 executed, 183 up-to-date), with 2/2 reproducible packages, 1/1 npm publish
dry-run inventory, 1/1 offline archive lifecycle,
3/3 installed profiles, 18/18 exact Skill copies, 2/2 MCP protocol versions, 3/3 release assets, and
all retained compile, Preview, XML, layout, screenshot, and exact-pixel evidence. Relative to the
two-command global installation path, local setup changed from no one-command denominator to 3/3
clients on one native operating system. Pull request `#261` then ran the exact packed-tarball
adoption workflow on GitHub-hosted Ubuntu, macOS, and Windows in
[run `33378127917`](https://github.com/ViewCompose/ViewCompose/actions/runs/33378127917). All 3/3
jobs passed: Ubuntu in 39 seconds, macOS in 36 seconds, and Windows in 3 minutes 23 seconds. Compared
with the preceding local Linux-only platform denominator, accepted operating-system coverage moved
from 1/3 (33.3%) to 3/3 (100%), an absolute gain of two platforms and 66.7 percentage points. The
conclusion is **improved** and the cross-platform adoption gate is accepted. The remaining limitation
is that hosted adoption verifies the packed candidate rather than a public npm registry identity;
the next action is therefore npm scope ownership, trusted-publisher binding, and exact publication.

The English and Chinese AI Integration pages, repository README, and tooling README now expose only
the exact one-command `0.4.0` path, durable-cache behavior, optional diagnosis, upgrade/removal, and
provenance boundary. The candidate release workflow uses npm Trusted Publishing through GitHub OIDC,
pins npm 11.8.0, creates or byte-verifies the GitHub Release before publishing the exact public npm
tarball with provenance, and contains no long-lived npm token. A retry accepts a pre-existing GitHub
Release only after its bytes and complete asset inventory match the frozen local outputs; a missing,
partial, or divergent identity fails closed. Any pre-existing npm version is rejected because equal
tarball bytes alone cannot prove OIDC provenance. This permits automatic recovery when GitHub
publication succeeds before a temporary npm failure, without permitting overwrite or silently
accepting unverifiable npm provenance. A public registry check on 2026-08-31 returned
`E404` for the package and `Scope not found` for `@viewcompose`; therefore namespace creation and
the npm-side trusted-publisher binding are external account prerequisites, not accepted evidence.
No funding endpoint was declared because the repository has no authoritative funding destination;
the package publishes its repository, documentation home, and issue-support URLs. The next action is
to establish those npm account bindings and only then publish and verify both immutable `0.4.0`
identities.

The `viewcompose` npm organization was created and its owner membership was verified on 2026-08-31.
Current npm policy exposes a first-publication dependency that the original handoff did not model:
trusted-publisher configuration and staged publishing both require an already-existing package, while
the stable `0.4.0` identity must remain unpublished until the GitHub OIDC workflow owns it. The
accepted one-time bridge is therefore the complete `0.4.0-bootstrap.0` prerelease payload under only
the `bootstrap` dist-tag. A fixed, default-branch-only workflow derives it from the twice-reproduced
stable tarball, permits changes only in eleven enumerated JSON version-bearing files, reproduces the
seed archive twice, verifies identical file inventory and an MCP handshake, and publishes it with
GitHub provenance through the protected `ai-tooling-release` environment. The seed is not a consumer
release and cannot become `latest`; ordinary stable semver ranges exclude it. A short-lived,
scope-scoped npm credential restricted to `@viewcompose`, with bypass permission, is allowed only
for this unavoidable package-identity bootstrap, after account 2FA is enabled. Before the stable
tag is created, that credential must be revoked, its GitHub secret deleted, the temporary workflow
removed, and npm Trusted
Publishing bound exactly to `ViewCompose/ViewCompose`, `ai-tooling-release.yml`, and environment
`ai-tooling-release`. Historical GitHub-only `0.3.0` is intentionally not backfilled to npm because
it would become a permanent stable-range target and its private tarball would require a later
metadata repack. After OIDC publishes `0.4.0`, `latest` must point to that stable version and the
temporary `bootstrap` dist-tag must be removed; the immutable prerelease remains registry history.

| Order | Execution wave | User outcome | Entry condition | Exit gate |
| --- | --- | --- | --- | --- |
| 1 | A — public npm bootstrap and cross-platform onboarding | One project-root command installs, configures, and diagnoses ViewCompose AI tooling | Release `0.3.0` remains reproducible and attested | Exact-version npm publication plus fresh macOS, Linux, and Windows adoption passes |
| 2 | B — enhanced ViewCompose analysis | Existing projects receive actionable, evidence-ranked ViewCompose findings | Wave A is released | Per-rule precision/recall, unsupported coverage, safety, read-only, CLI/MCP, and installed-package gates pass |
| 3 | C — Figma design-tree adapter | Exported Figma structure converts through Design IR with provenance and assets preserved | Wave B analyzer payload is stable and released | Offline import, provenance, resource, compile/render/compare, privacy, and installed-package gates pass |
| 4 | D — attended screenshot repair activation | Users can review and explicitly apply a bounded repair with rollback | The source-application transaction boundary is independently accepted | Diff preview, authorization, atomic apply, durable outcome, rollback, replay, and public-surface gates pass |
| 5 | E — Compose semantic map and Agent Skill | Agents translate Compose intent with an explicit supported/partial/manual/unsupported map | Waves B–D are released | Current mapping corpus, compiled target samples, Skill parity, and docs pass |
| 6 | F — bounded Compose AST conversion | A supported stateless Compose subset converts through Design IR to compiled ViewCompose Kotlin | Wave E mapping is frozen | AST fixtures, unsupported honesty, CLI/MCP parity, compilation, and installed-package gates pass |

“Released” in this table means the wave has its accepted contracts, implementation, tests, public
English and Chinese guidance, immutable package evidence, and a merged pull request. A local green
test, draft package, or internal tool mode does not unblock the next wave.

#### Frozen product decisions

1. **Current framework only.** The project currently has no adoption base that justifies building
   or maintaining historical API/Knowledge profiles. Each new framework release produces one
   current released profile. Historical profile generation, migration fixtures, compatibility
   claims, and backfill work are outside this sequence.
2. **Mismatch still fails closed.** “No historical support” does not permit the newest Knowledge
   Bundle to serve an older dependency vector. Initialization and upgrade must detect the exact
   project vector without executing consumer Gradle. A non-current, dynamic, conflicting, or
   unresolved vector is left unchanged and receives an actionable framework/tooling alignment
   message.
3. **No silent framework upgrade.** The AI bootstrap never edits Gradle dependencies merely to make
   its own profile compatible. The user upgrades ViewCompose separately, then reruns initialization.
4. **GitHub remains the immutable evidence origin.** npm is the low-friction discovery and bootstrap
   channel. The release tag, exact asset inventory, Manifest, `SHA256SUMS`, and attestations remain
   the canonical provenance chain until a later ADR deliberately changes it.
5. **No mutable-latest compatibility signal.** Documentation and CI use an exact tooling version.
   Even if npm exposes a `latest` tag, compatibility is decided by the detected framework vector and
   packaged profile, never by package recency.
6. **No provider ownership.** ViewCompose continues to accept provider-neutral inputs and validated
   results. It does not own model credentials, provider SDKs, remote calls, conversation state, or
   billing.
7. **No consumer build execution.** Compile, Preview, and layout evidence continue through the
   packaged allowlisted harness. No wave may execute the consumer wrapper, settings, plugins,
   arbitrary tasks, application process, or device deployment as an implementation shortcut.
8. **Source mutation remains independently gated.** Waves A–C and E–F are read-only with respect to
   application source. Public repair cannot reuse an in-memory authority object as source-write
   permission and cannot activate before the Wave D transaction and rollback contract passes. Its
   earlier priority does not grant authority to the later Compose waves or weaken their read-only
   contracts.
9. **Reconsideration trigger.** Historical profile support requires concrete adoption evidence, a
   named support window, storage/CI budgets, and a separately accepted plan. A single request to
   support an old version is recorded but does not silently expand this plan.

#### Cross-computer resume procedure

The next implementation session starts from a clean clone or clean `main` and performs these steps
before editing production files:

1. Read `docs/README.md`, `docs/project/documentation-governance.md`, this plan, ADR-0009, ADR-0022,
   ADR-0025, `docs/ai/README.md`, and `tools/ai/README.md` in that order.
2. Fetch `origin` and tags, fast-forward local `main`, and confirm both
   `ai-tooling-v0.3.0` and this planning commit are ancestors of `HEAD`. Do not reconstruct the plan
   from chat history.
3. Confirm the worktree is clean. Preserve unrelated user changes if it is not; do not reset or
   overwrite them.
4. Record the local Node, JDK, Android SDK, operating system, architecture, and npm versions in the
   first Wave A evidence update. The currently accepted deep-evidence prerequisites remain Node
   24.19 or newer, JDK 17 or 21, and Android SDK 36 until a contract change says otherwise.
5. Run the documentation structure and current AI Release gates before changing behavior. A
   baseline failure is investigated and recorded; it is not normalized into the new work.
6. Create a dedicated `codex/` branch for Wave A. Do not combine later waves into that branch or
   pull request.
7. Make the Wave A contract/schema/tests the first implementation commit. Only then implement the
   deterministic bootstrap path, followed by platform evidence and public documentation.
8. Update this section after every merged wave with accepted absolute results, normalized change,
   conclusion, limitations, and the next unblocked wave. Earlier chronological evidence remains
   immutable except for factual corrections.

Recommended clean-baseline commands are:

```bash
git fetch origin --tags
git switch main
git pull --ff-only origin main
git merge-base --is-ancestor ai-tooling-v0.3.0 HEAD
./gradlew verifyDocumentationStructure
./gradlew verifyAiToolingRelease
```

The commands above inspect the baseline only. They do not authorize publication, package release,
consumer source writes, or execution of any implementation wave.

### Execution Wave A — public npm bootstrap and one-command onboarding

**Status: complete on 2026-09-01.** The accepted release and public evidence are recorded in
“Wave A corrective-release acceptance evidence” above. Wave B is now unblocked only at its
contract-freeze boundary.

#### Objective and public happy path

Replace the current global GitHub-tarball installation plus explicit
`--project-root "$(pwd -P)"` invocation with one shell-independent command run from the physical root
of a new or existing Android project. The accepted target is corrective release `0.4.1`: stable
`0.4.0` published successfully but failed the public default-executable selection described above.
The feature contract remains the Wave A change over `0.3.0`:

```text
npx --yes @viewcompose/ai-tooling@0.4.1 init --client <codex|claude-code|cursor>
```

The documentation may render separate copy buttons for each client, but all three commands must
exercise the same CLI contract. `--project-root <absolute-path>` remains available for automation;
when omitted, `init`, `doctor`, `upgrade`, and `uninstall` resolve and bind the physical current
directory. No public instruction may require a global install, `sudo`, command substitution,
`curl | sh`, manual archive extraction, manual Skill copying, or manual MCP JSON/TOML editing.

#### Contract-first work

1. Freeze the public npm package name as `@viewcompose/ai-tooling`, its exact semver, supported Node
   engines, binary name, package files, licenses, repository metadata, funding/support links, and
   zero-install-script policy. Verify registry namespace ownership before changing publication code.
   If the namespace is unavailable, stop this wave and update this plan with an explicitly accepted
   name; do not silently publish a lookalike package.
2. Freeze a bootstrap result schema that reports package identity, framework vector, profile ID,
   physical project root, selected client, MCP path, Skill root/count, durable install root, evidence
   prerequisites, readiness state, warnings, and exact next action.
3. Define omitted-root semantics consistently across POSIX shells, PowerShell, `cmd.exe`, real paths,
   symbolic links, UNC paths, spaces, non-ASCII paths, and case-insensitive filesystems. MCP keeps
   the physical root identity already required by `0.3.0`; aliases cannot gain authority.
4. Define a user-cache layout keyed by verified package content and profile identity. The ephemeral
   npm/npx extraction directory may launch the bootstrap, but generated MCP configuration must point
   only to the durable, integrity-checked cache. Interrupted materialization is transactional and
   leaves no selectable partial version.
5. Define ownership, conflict, idempotence, recovery-journal, upgrade, and uninstall rules for the
   durable package, MCP entry, and Skills. Existing unmanaged bytes remain untouched and produce a
   reviewable conflict.
6. Keep exact framework detection and current-only profile selection ahead of every project write.
   A project without ViewCompose dependencies may select the package's single current profile; a
   project with a non-current vector fails closed with upgrade guidance.
7. Make `init` perform the existing `doctor` checks and return the readiness summary in the same
   invocation. `doctor` remains available for troubleshooting but is not a required second setup
   command.
8. Freeze npm publication provenance: protected tag workflow, environment approval, least-privilege
   token or trusted publishing, package dry-run, exact tarball comparison, provenance, immutable
   GitHub asset cross-check, and prevention of duplicate/version-overwrite attempts.

#### Implementation slices and commit boundaries

Wave A uses one pull request with reviewable commits in this order:

1. `test(ai): freeze one-command bootstrap contract` — schemas, negative fixtures, platform path
   cases, current-only version cases, and release expectations; no behavior activation.
2. `feat(ai): materialize durable npx bootstrap` — omitted-root resolution, content-addressed cache,
   transactional initialization, integrated readiness result, upgrade/uninstall ownership, and no
   ephemeral configured paths.
3. `ci(ai): verify npm package adoption across platforms` — package dry-run and fresh project
   fixtures on macOS, Linux, and Windows, including all three clients and paths with spaces and
   non-ASCII characters.
4. `docs(ai): publish one-command client setup` — English and Chinese AI Integration pages, README
   entry points, troubleshooting, exact version, security/provenance, and old two-command removal.
5. `chore(ai): prepare 0.4.1 release evidence` — publication inputs, immutable asset/version checks,
   and release intent required by repository policy. The actual npm/GitHub publication happens only
   after merge and protected-gate success.

Do not squash away contract and evidence separation unless repository policy requires it. Do not
include Compose, analyzer, Figma, or source-repair code in this pull request.

#### Acceptance matrix

| Gate | Required denominator |
| --- | --- |
| One-command setup | 3/3 clients reach `project-bound-ready` from exactly one version-explicit command in a fresh project |
| Operating systems | Fresh macOS, Linux, and Windows runners each pass init, integrated doctor, idempotent re-entry, MCP handshake, and uninstall |
| Paths | Spaces, non-ASCII, physical/symlink mismatch, case behavior, and Windows drive/UNC fixtures fail or pass exactly as contracted |
| Persistence | Every configured server path survives npx cache cleanup and points to the verified durable package |
| Transactions | Injected interruption at every materialization/configuration/Skill step leaves the old integration usable or no integration installed |
| Compatibility | Current exact framework vector and no-dependency new project pass; old/dynamic/conflicting/unresolved vectors fail before writes |
| Security | No lifecycle script, `sudo`, arbitrary consumer Gradle execution, provider credential, path escape, mutable URL, or unverified archive is accepted |
| Reproducibility | Two package builds are byte-identical; npm dry-run contents match the attested GitHub tarball contract and exact version |
| Documentation | README and the standalone AI chapter provide per-client copyable commands, prerequisites, outcomes, upgrade/remove, and troubleshooting in English and Chinese |
| Release | npm `0.4.1` and GitHub `ai-tooling-v0.4.1` identities, checksums, provenance, assets, and public clean-install result are recorded after publication; immutable `0.4.0` failure evidence remains visible |

Wave A closed on 2026-09-01 after users could start in an unrelated Android project with the
documented one command and no repository-local preparation. The accepted external, hosted, npm,
provenance, and Release evidence is recorded above; a green in-repository package test alone would
not have been sufficient.

### Execution Wave E — Compose semantic map and Agent Skill

#### Objective

Use Jetpack Compose familiarity as a safe bridge without pretending identical names imply identical
semantics. This wave publishes knowledge and workflow guidance only; it does not parse or rewrite
Compose source.

#### Required work

1. Freeze one exact Compose API baseline and one exact ViewCompose release vector in the first
   contract commit. If the Compose stable release changes during implementation, update the map and
   fixtures explicitly rather than silently following “latest.”
2. Add one machine-readable semantic map keyed by stable IDs. Each entry records source symbol and
   version, target capability/symbol/artifact, status (`exact`, `translated`, `manual`, or
   `unsupported`), parameter/default differences, lifecycle/state consequences, accessibility,
   imports/dependencies, required resources, evidence, and migration notes.
3. Cover at least layout, modifier ordering, sizing, padding, alignment, text, image, input,
   collections, state, remember/saveable state, effects, lifecycle, coroutines, navigation, theme,
   resources, accessibility, gestures, animation, Android interop, Preview, and testing. Empty
   categories are explicit unsupported records, not omitted rows.
4. Add current-version ViewCompose compiled samples for every accepted target pattern. Compose source
   examples are bounded fixtures for mapping evaluation; they do not become a copied parallel API
   manual.
5. Add a seventh client-neutral Skill, provisionally `viewcompose-convert-compose`, that retrieves
   the exact map, asks for clarification when mapping is partial/manual, generates only real
   ViewCompose APIs, validates the target, and reports unsupported source semantics.
6. Expose map retrieval/search through the existing knowledge surface when possible. A new MCP tool
   requires a demonstrated contract that cannot be represented by current retrieval; do not grow the
   public tool catalog merely for naming symmetry.
7. Publish English and Chinese Compose migration guidance with supported, translated, manual, and
   unsupported examples and a clear statement that this wave is Agent guidance, not automatic
   conversion.

#### Commit and acceptance boundaries

Use separate commits for mapping schema/corpus, compiled target samples, Skill/workflow, and public
documentation. Accept only when every mapped target resolves to the current Knowledge Pack, every
accepted generated target compiles, all six existing Skills plus the new Skill retain installed-byte
and client-profile parity, unsupported categories are queryable, and curated migration prompts do
not fabricate APIs. Publish the resulting AI package before Wave F starts.

### Execution Wave F — bounded Compose AST conversion

#### Objective and v1 boundary

Convert a deliberately small, stateless Compose source subset through the existing tooling-only
Design IR into ViewCompose Kotlin. Parsing must use a maintained Kotlin parser/compiler AST; regex
or model-only source rewriting cannot produce an accepted conversion result.

The v1 corpus begins with local composable function bodies, a direct supported layout/component
tree, literal or resource-backed values, and modifier semantics already marked `exact` or
`translated` by Wave E. State/effect ownership, navigation graphs, arbitrary control flow, custom
composables without an accepted expansion, reflection, generated code, build plugins, and behavior
inference remain unsupported until separately added with fixtures.

#### Required work

1. Freeze source, AST, mapping, Design IR, generated-source, diagnostic, and evidence schemas with
   content-addressed lineage and exact source spans.
2. Parse only explicit caller-supplied source or bounded files under an explicit physical project
   root. Do not execute Gradle, compiler plugins, annotation processors, scripts, or source code.
3. Resolve symbols against the frozen Compose baseline and Wave E map. Unknown overloads,
   expressions, defaults, modifiers, ambient values, state, effects, or custom calls yield typed
   unsupported findings; they never become guessed Design IR.
4. Preserve strings, drawables, dimensions, accessibility, IDs/keys when meaningful, imports,
   source-node provenance, and manual follow-up markers.
5. Generate ViewCompose through the shared Design IR generator, then run static validation and the
   released-artifact compiler. Render/compare is attached only where the accepted configuration and
   semantic expectations exist.
6. Expose one CLI/MCP operation only after local schema/fixture gates pass. Generation mode and
   compile mode remain distinct evidence levels, and installed-package parity is mandatory.
7. Keep application source read-only. The converter returns source, diagnostics, provenance, and
   evidence; it does not replace files or call sites.

#### Commit and acceptance boundaries

Use contract/fixtures, AST adapter, Design IR/generator composition, CLI/MCP/Skill orchestration,
and docs/release commits. The initial corpus must contain positive, boundary, malformed, ambiguous,
unsupported, adversarial path, and API-hallucination cases. Acceptance requires deterministic AST
output, 100% compile success for the declared supported corpus, 100% typed rejection for the
declared unsupported corpus, no consumer execution or writes, installed-package reproduction, and
an immutable released package before this plan can close.

### Execution Wave B — enhanced read-only ViewCompose analysis

#### Objective

Extend the existing `analyze_project` tool instead of creating a duplicate
`analyze_viewcompose` alias. Findings must help an Agent review real ViewCompose code while
remaining read-only, bounded, version-aware, and measurable.

#### Contract and first public rule boundary

1. Keep the existing tool envelope and `analyze_project` name. Add one versioned `analysis` payload
   under the existing result data with resolved profile, bounded scan coverage, applicable catalog,
   corpus quality, typed findings, unsupported coverage, and suppression totals. Existing diagnostics
   remain a mechanically generated, backward-compatible projection of unsuppressed findings.
2. Every finding owns a stable rule ID and rule version, severity, categorical confidence, exact
   source span, mechanism, evidence, manual-safe suggestion, framework applicability, suppression
   state, and optional reason/directive span. Numeric confidence is forbidden; measured precision
   and recall belong to the immutable quality snapshot.
3. The first catalog is limited to the already deterministic unknown ViewCompose import/artifact,
   missing exact owning-artifact declaration, current-profile version mismatch, and exact
   ViewCompose `Image` content-description checks. The Image rule reports only when the governed
   symbol and argument list resolve; explicit `null`, aliases, wrappers, malformed calls, and other
   ambiguous forms produce no finding or an explicit unsupported record.
4. One source directive may suppress only the next analyzable construct for one suppressible rule.
   It requires an exact rule ID and non-empty reason, remains visible in the typed result, and cannot
   suppress profile, dependency-integrity, path, execution, timeout, or other security findings.
5. Kotlin/Gradle extraction masks strings and nested comments and uses balanced delimiters. XML uses
   a bounded tokenizer, and version catalogs use an explicit literal TOML subset. Dynamic Gradle,
   star imports, overload/type inference, aliases, custom wrappers, control/data flow, and unknown
   syntax remain unsupported rather than guessed.
6. Lifecycle pairing, touch-target sizing, modifier ordering, dp/sp preferences, AndroidView commit
   semantics, structural simplification, recomposition/allocation, and performance claims stay out of
   the enabled catalog until a maintained Kotlin AST or semantic layer supplies defensible evidence.
7. Later rule families may cover structure, lifecycle, accessibility, units, theme, and performance,
   but each addition requires an independently versioned rule, mechanism, supported-syntax corpus,
   false-positive corpus, quality result, and installed-package proof before public activation.

#### Commit and acceptance boundaries

Commit schemas/catalog/corpus before implementation, then extraction/evaluation/suppression,
CLI/MCP compatibility, resource limits, and docs/release. Every enabled rule must achieve 100%
observed precision and recall within its documented supported syntax, with at least 25 positive and
50 eligible negative labeled opportunities; unsupported opportunities are reported separately and
cannot inflate true-negative counts. Acceptance also requires zero source or build execution,
traversal/secret/output-limit safety, deterministic ordering, exact current-profile attribution,
legacy-diagnostic compatibility, and successful installed-package analysis of fresh and existing
project fixtures. Wave C cannot begin until this package is publicly released and reverified.

#### Wave B pre-release acceptance evidence (2026-09-01)

The contract and implementation slices now expose five versioned high-confidence rules through the
existing `analyze_project` surface. The comparison context is the `0.4.1` analyzer, which returned
inventory and legacy diagnostics without rule versions, framework applicability, quality
denominators, suppression audit, or explicit unsupported coverage. The `0.5.0` candidate retains
those fields and adds one schema-validated `data.analysis` payload; CLI and MCP produce identical
normalized results, and the packed distribution invokes the same result from an installed binary.

The frozen corpus contains 125 positive, 250 eligible negative, and 25 deliberately unsupported
opportunities across five rules. Absolute results are 125/125 detected positives, 0/250 false
positive eligible negatives, 0/125 false negatives, and 25/25 explicit unsupported results. The
normalized change is from four unmeasured project diagnostics plus one snippet-only Image rule to
five project-level rules with 100% observed precision and recall inside their declared lexical
boundaries. `verifyAiProjectAnalysis` and `verifyDevelopmentToolingIsolation` both passed on JDK
21.0.12.1; the focused Node suites passed 19/19 analyzer/static tests and 17/17 MCP tests.

Conclusion: **improved**. Existing consumers retain their diagnostic codes while newer consumers
receive exact rule, profile, evidence, quality, suppression, and unsupported metadata. Limitations:
the corpus measures only documented literal/lexical forms; it does not substantiate alias, star
import, wrapper, transitive dependency, lifecycle, control/data-flow, performance, theme, unit,
touch-target, Modifier-order, or arbitrary Kotlin semantic claims. The next action at this
pre-release checkpoint was protected `0.5.0` publication and fresh public-install reproduction. The
immutable evidence below supersedes that checkpoint and closes Wave B.

#### Wave B public-release acceptance evidence (2026-09-01)

PR [#266](https://github.com/ViewCompose/ViewCompose/pull/266) merged the four implementation
slices at commit `99894e8220de78421c428a80b1d0f2b01c0f0f24` after hosted Linux, macOS, and
Windows adoption, documentation, `qaPreview`, and `qaQuick` passed; the complete hosted `qaQuick`
work took 29 minutes 14 seconds. Lightweight tag `ai-tooling-v0.5.0` resolves exactly to that merge.
Protected [run `33486262197`](https://github.com/ViewCompose/ViewCompose/actions/runs/33486262197)
completed in 9 minutes 14 seconds through environment `ai-tooling-release`, rebuilt the distribution
twice, attested the three exact assets, created the immutable
[GitHub Release](https://github.com/ViewCompose/ViewCompose/releases/tag/ai-tooling-v0.5.0), and
published npm `@viewcompose/ai-tooling@0.5.0` through the existing GitHub OIDC Trusted Publisher.

At Wave B acceptance, the npm registry exposed `latest -> 0.5.0`; public `0.6.0` now supersedes that
tag without changing the immutable `0.5.0` evidence. The `0.5.0` SLSA v1 statement names repository
`https://github.com/ViewCompose/ViewCompose`, workflow `.github/workflows/ai-tooling-release.yml`,
ref `refs/tags/ai-tooling-v0.5.0`, GitHub-hosted builder, invocation
`https://github.com/ViewCompose/ViewCompose/actions/runs/33486262197/attempts/1`, and subject
`pkg:npm/%40viewcompose/ai-tooling@0.5.0`. The 637,133-byte tarball has SHA-256
`a19e1c5680f34d744e313926af7d9081f51ea97e3ace64b6c732527d7104da04` and npm integrity
`sha512-ffUtj1NwYZWx9JhlJEsw30AE+ZeQIDuMb1WaJ3r4CaOqzu1Y6F6EwO3NBIMSs6NkgSDrsLmi8JWGJ1GijwRSmg==`.
`manifest.json`, `SHA256SUMS`, and the tarball passed checksum verification and 3/3 GitHub
attestation checks; their published SHA-256 digests are respectively
`aac30848374ea8b990157f173841747e6548d4fd3c07a22c777d072b69004452`,
`a8382cb4a60b4a1ed62a68ef818de46cee1533d90518dc0f48c36303e8c8c51e`, and the tarball digest
above.

Repository-external fresh projects then ran the literal public-registry
`npx --yes @viewcompose/ai-tooling@0.5.0` selector. Codex, Claude Code, and Cursor each completed
`init`, reached `project-bound-ready` in `doctor`, installed 6/6 canonical Skills, and completed
`uninstall` with only their managed configuration and 18/18 total Skill copies removed. The Codex
fixture resolved the exact released profile
`895ed1e52e5a9735f87e6d996e77ea43ca34cc2e496854408c40772419129064`. Its durable npm-installed
binary executed `analyze_project` directly and returned `success`, static evidence, schema v1,
exact-profile match, Knowledge Bundle fingerprint
`9ee4560b30f2d26378314d5b8c8acf20343662f5a8c1d5bfc0442944c4d09660`, and one unsuppressed
categorical-high `VC-AI-A11Y-IMAGE-DESCRIPTION` finding for the deliberately incomplete Image call.

Relative to public `0.4.1`, the normalized change is from unversioned inventory/legacy diagnostics
to five installed project-level rules with stable rule versions, exact framework applicability,
quality denominators, audited suppression, and explicit unsupported coverage, while one-command
adoption and all legacy diagnostics remain compatible. Conclusion: **improved**. Limitations: the
public reproduction used one macOS host and did not launch or authenticate proprietary Agent
binaries; the hosted matrix covers OS-native bootstrap behavior, and the analyzer evidence covers
only the documented lexical boundary rather than aliases, wrappers, dynamic expressions, or Kotlin
semantics. Wave B is complete. The next action is Wave C contract freeze; no Figma adapter or source
write is authorized by this evidence.

### Execution Wave C — provider-neutral Figma design-tree adapter

**Status: complete on 2026-09-01.** Public `0.6.0` publication and repository-external reproduction
are recorded below. Wave D may begin only after this evidence update merges.

#### Objective

Transform a caller-exported Figma design tree into the shared Design IR, then reuse generation,
compile, Preview, semantic/geometry comparison, and diagnostics. The ViewCompose package does not
log into Figma, store access tokens, scrape private documents, or select a model/provider.

#### Required work

1. Freeze an offline input envelope containing export format/version, selected node IDs, document and
   component provenance, dimensions, constraints/auto-layout, text/style tokens, asset inventory,
   export settings, and redaction/privacy declarations.
2. Define explicit mappings for frames/auto-layout, text, images, vectors, component instances,
   variants, tokens/styles, visibility, clipping, constraints, accessibility annotations, and
   interactions. Prototype-only interactions and unsupported effects remain typed gaps.
3. Import only caller-provided JSON and asset bytes under bounded sizes/counts. Reject URLs, active
   content, path traversal, external fetches, executable plugin data, and undeclared fonts/assets.
4. Preserve asset hashes, licensing/ownership declarations, token identity, component/instance
   lineage, and every unsupported property through Design IR and generated reports.
5. Reuse the current generator and evidence lanes. Visual comparison distinguishes structural,
   semantic, geometry, style, asset, exact-pixel, and perceptual evidence; no aggregate “Figma
   parity” claim hides missing categories.
6. Publish export instructions for common Figma workflows without requiring a ViewCompose-owned
   credential. Any future direct Figma connector is a separate provider integration and plan.

#### Commit and acceptance boundaries

Use input/schema/security, deterministic adapter, Design IR/resource handling, evidence integration,
CLI/MCP/Skill, and docs/release commits. Acceptance requires supported/unsupported goldens,
deterministic offline import, exact provenance and asset verification, privacy/path/size adversarial
tests, compiled generated output, accepted Preview/semantic/geometry denominators, and
installed-package reproduction. Wave C does not authorize source writes.

#### Wave C pre-release acceptance evidence (2026-09-01)

The contract-first commits freeze `viewcompose-figma-export/1`, Design IR v2, mutually exclusive
`inspect`/`generate`/`verify` requests, adversarial mutations, and exact resource/provenance limits
before exposing `convert_figma_to_viewcompose` through the shared CLI/MCP catalog. The adapter uses a
strict duplicate-key-rejecting parser, accepts only caller-supplied self-contained JSON and embedded
bytes, and rejects credentials, URLs, active content, path traversal, changed hashes, invalid media
signatures, undeclared references, cycles, detached graphs, unsupported render facts, and bounded
size/count violations. It opens no network connection and receives no Figma login or token.

The accepted v1 generation subset is one selected root; non-wrapping Row, Column, and Box; Text with
declared generic system fonts; solid colors; and explicitly accessible, redistributable PNG assets.
Multiple roots are now reported as non-generatable during `inspect`, not deferred to a later
failure. Custom fonts, effects, prototype interactions, vectors, JPEG/WebP emission, and every
undeclared or unsupported fact stay visible and block generation. The new
`viewcompose-import-figma` Skill preserves that audit-first sequence and brings the client-neutral
workflow set from 6 to 7 while the public tool catalog changes from 13 to 14.

On local macOS with Node 26 and JDK 21.0.12.1, the complete AI script suite passed 353/353 cases,
including an adversarial Kotlin-boundary case for document identities and text payloads.
The installed-distribution gate produced 2/2 byte-identical packages, accepted 1/1 npm dry-run
inventory and 1/1 offline install/uninstall lifecycle, exercised 3/3 Agent profiles with 21/21
exact Skill copies, and passed 2/2 MCP protocol versions. The installed package reproduced Figma
inspection fingerprint `a21c75ed7149a30986083cbedc38164d9d510057e4a55c9523a4da906f2a746d`,
generation fingerprint `776a453c3b905bd5509d5862d79f795c0aca84fb0252701ba2d6bfb0fc6675a8`,
and real released-Maven compile/Preview/layout comparison fingerprint
`59058ac5d2a873e6597d874a4e36215fe8396fff470b91064c923fbbdb7544b9`. The comparison passed the
declared structure, semantics, geometry, and asset categories. Style remained `incomplete`; pixel
and perceptual categories were `not-applicable` because no trusted Figma reference render entered
the contract.

Relative to public `0.5.0`, the normalized candidate change is one additional tool (13 to 14), one
additional Skill (6 to 7), and one installed offline Figma flow (0 to 1) with compile, Preview, and
bounded comparison evidence; Android runtime artifacts and application-process work remain
unchanged. Conclusion: **improved** provider-neutral design import with **no material Android
runtime behavior change** and no visual-parity claim. Limitations: the evidence uses one normalized
example on one local macOS host; v1 does not ship a Figma plugin, REST client, `.fig` parser, direct
connector, style comparator, trusted reference render, pixel/perceptual comparison, or consumer
source-write authority. The next action is the Wave C pull request, hosted gates, protected
`ai-tooling-v0.6.0` publication, exact external installation and Figma reproduction, then a final
evidence PR. Wave D remains blocked until those steps merge.

#### Wave C public-release acceptance evidence (2026-09-01)

PR [#268](https://github.com/ViewCompose/ViewCompose/pull/268) passed the Linux, macOS, and Windows
bootstrap matrix, documentation build, `qaPreview`, complete `qaQuick`, release-intent, and plan
checks before merging as `67f99e12c02b36671843a6eb09546178c2760518`. The immutable
`ai-tooling-v0.6.0` tag points at that merge commit. Protected
[run `33498765977`](https://github.com/ViewCompose/ViewCompose/actions/runs/33498765977) completed
in 10 minutes 24 seconds through the `ai-tooling-release` environment and GitHub OIDC Trusted
Publisher. npm exposes `@viewcompose/ai-tooling@0.6.0` with `latest -> 0.6.0`, integrity
`sha512-R3+kHFNVUqfUr1n2EHPmM+L2107DLux35TRGSxBdCenFAqV0dznzUXRcGsbKjjFlRXbrtwJ9ZPMEUy6XgMwwRQ==`,
and SLSA v1 provenance. The immutable
[GitHub Release](https://github.com/ViewCompose/ViewCompose/releases/tag/ai-tooling-v0.6.0)
contains exactly the 663,115-byte tarball, 35,817-byte `manifest.json`, and 179-byte `SHA256SUMS`.
The tarball SHA-256 is
`de4b36df76ab842df18e0449967542b23de017104828700070caedb0e0671934`; all 3/3 assets passed the
published checksum list and GitHub attestation verification.

One repository-external Android project on macOS used the literal public selector with Node
26.8.1, JDK 17.0.17, and Android SDK 36. Codex, Claude Code, and Cursor each completed
`init`/`doctor`/`uninstall`, reached `project-bound-ready`, installed 7/7 exact Skills, and removed
only the managed configuration and 21/21 total Skill copies. The installed Figma CLI reproduced
100% fact coverage (39/39), 100% asset coverage (1/1), inspection fingerprint
`a21c75ed7149a30986083cbedc38164d9d510057e4a55c9523a4da906f2a746d`, and generation fingerprint
`776a453c3b905bd5509d5862d79f795c0aca84fb0252701ba2d6bfb0fc6675a8`. The generated Kotlin and
PNG then compiled and rendered through the released-Maven lane. Structure passed 9/9, semantics
8/8, geometry 8/8, and assets 1/1. Style remained `incomplete`; pixel and perceptual evidence
remained `not-applicable`. A direct MCP request reproduced the same inspection fingerprint and
coverage without an error.

Relative to public `0.5.0`, the normalized public change is one tool (13 to 14), one Skill (6 to 7),
and one offline Figma flow (0 to 1), while Android runtime artifacts remain unchanged. Conclusion:
**improved** provider-neutral design import with **no material Android runtime behavior change** and
no style, pixel, perceptual, or arbitrary-document parity claim. Limitations: public reproduction
used one normalized export and one macOS host; hosted CI covers native onboarding on three operating
systems but did not launch proprietary Agent binaries. The release has no Figma plugin, REST client,
`.fig` parser, provider credential, trusted reference render, style comparator, or source-write
authority. Wave C is complete. The next action is Wave D's transactionally applied, explicitly
attended screenshot-repair boundary; Compose Waves E and F remain lower priority.

### Execution Wave D — public attended screenshot repair

**Status: complete with public `0.7.0` acceptance on 2026-09-02.**

#### Objective

Turn the accepted internal proposal/authorization/host-grant/terminal-outcome/applied-result chain
into a public, explicitly attended source change without granting the MCP process general write
authority.

#### Required source-application boundary

1. Freeze a separate downstream application host with exact project root, file, expected preimage
   hash, source span or structured edit, candidate hash, diff, authorization identity, expiry,
   single-use nonce, and permitted rollback target.
2. Present generated source, evidence, limitations, and a complete diff before authorization. The
   user authorizes that exact candidate only; “repair this screen” is not durable write authority.
3. Re-read the physical target and all lineage immediately before apply. Any preimage, root, symlink,
   profile, evidence, candidate, or authorization drift fails closed.
4. Apply atomically, preserve an integrity-checked recovery copy outside source control, and persist a
   terminal outcome before returning success. Interrupted apply must restore the old bytes or leave
   a recoverable, diagnosed state.
5. Rollback is explicit, single-target, preconditioned, auditable, and refuses to overwrite later
   user edits. Cleanup follows bounded retention and never removes unrelated files.
6. Public CLI/MCP exposure remains request-driven and attended. No background watcher, continuous
   loop, provider credential, application execution, commit, push, or pull request is implied.
7. Re-run static, compile, Preview, semantic/geometry, and eligible pixel evidence against the exact
   applied bytes and report both pre-apply and post-apply results. A worse candidate remains rejected.

#### Commit and acceptance boundaries

Use transaction/rollback schemas and adversarial fixtures, isolated host implementation, durable
outcome/recovery, public orchestration, and docs/release commits. Acceptance requires atomic apply
and rollback under injected crashes, conflict/preimage/symlink/replay/concurrency rejection, no
general MCP write capability, exact post-apply evidence, user-visible diff and recovery steps, and
installed-package reproduction. Only then may public documentation describe “attended automatic
repair”; unattended or arbitrary-source repair remains out of scope.

#### Accepted source-application freeze (2026-09-01)

Wave D uses two authority planes. MCP may prepare and return only one inert, content-addressed
source-application request. It exposes no apply, rollback, approval, grant, shell, build, commit,
push, or source-write operation. A separately invoked `viewcompose-repair` host will reconstruct the
candidate, display the complete bounded diff and evidence summary, and require the controlling TTY
to enter the exact request suffix. No `--yes`, stdin, environment-variable approval, reusable token,
or serialized internal grant is accepted.

The v1 edit is narrower than whole-file replacement. It replaces one generated Kotlin property
value span derived from the already validated one-property Design IR rollback. Callers cannot submit
raw replacement text, unified patches, arbitrary candidate files, imports, declarations, insertions,
or deletions. The complete current generated source must equal the physical preimage, and the CLI
must reconstruct and hash the complete candidate before authorization and again immediately before
commit. Validation failure never triggers automatic rollback because a long compile or Preview run
could race with later user edits; rollback is a separate attended transaction and refuses any target
that no longer equals the committed candidate.

Three versioned schemas now freeze the canonical request, append-only hash-chained journal entry, and
immutable apply/rollback receipt. They bind the physical-root and framework-profile fingerprints,
root-relative Kotlin path, regular single-link file identity, exact UTF-8 preimage and property span,
replacement and reconstructed candidate hashes, displayed diff, Design IR/proposal/authorization/
typed-patch/evidence lineage, ten-minute expiry, one nonce, recovery identities, and post-apply evidence.
Recovery bytes live in an owner-only platform user-state directory outside the project and source
control. Apply is unavailable unless the backend can provide no-follow, beneath-root,
directory-handle-relative atomic replacement and durable sync semantics. The implemented host uses
`SecureDirectoryStream` where the JDK provider exposes it and a fixed JDK Unix directory-descriptor
bridge otherwise; both paths fail closed when those semantics cannot be proven.

The checked-in denominator contains 3/3 schema-valid examples, 8/8 schema-level safety rejections,
12/12 runtime no-write rejection cases, and 10/10 apply/rollback crash boundaries. The required
host outcomes are only unchanged preimage (`NOT_APPLIED`), committed candidate
(`APPLIED_UNVERIFIED` pending reconciliation), or no-write conflict (`APPLIED_CONFLICT`). This
changes the previous informal downstream-write requirement to a machine-checked contract and is
therefore **improved** safety specification with **no material Android runtime behavior change** and
no public source-write capability. The limitations are explicit: controlling-TTY attendance does
not defend against an actor that already controls the user's OS account or terminal; recovery may
contain source bytes; secure filesystem support is platform-dependent; rollback is not a merge
facility. The typed property-span mapper now proves that current and candidate generated Kotlin
differ only at the authorized literal, and the transaction host has passed 2/2 real secure-backend
checks plus 6/6 apply, explicit rollback, single-use replay, validation-failure, crash-recovery, and
concurrency checks. These checks cover exact atomic replacement, stale-preimage rejection,
unsupported-filesystem failure, applied-candidate recovery without automatic rollback, unchanged
preimage recovery, and later-user-edit preservation. Relative to the contract-only baseline, this
is an **improved** executable safety boundary with **no material Android runtime behavior change**.
The evidence is local macOS APFS with JDK 21 and injected process failures rather than sudden power
loss; CI must still exercise the accepted JDK 17 lane and public-package installation. The next
action is the protected tag publication followed by repository-external attended reproduction.

#### Accepted release-candidate implementation (2026-09-01)

The public candidate adds `prepare_screenshot_repair` as tool 15/15, marks its MCP annotation
non-read-only because preparation persists owner-only user state outside the project, and adds
`viewcompose-repair` as the fifth executable plus
`viewcompose-repair-screenshot` as Skill 8/8. `evaluate` reproduces the six ordered gates,
`propose` reproduces the single-property rollback, and `prepare` stores only an inert owner-only
request outside the project. Only the separately launched executable exposes `show`, attended
`apply`, recovery reconciliation, and separately attended `rollback`. Its parser rejects duplicate
project roots and every approval bypass tested.

The complete AI script suite passed 373/373 cases. The secure host and transaction subset passed
10/10 on JDK 17, including real atomic replacement, stale-preimage and unavailable-filesystem
rejection, explicit rollback, validation-failure preservation, missing-confirmation no-write,
pre/post-replacement crash recovery, and concurrency exclusion. The exact package passed 2/2
reproducible builds, 1/1 npm inventory, 1/1 offline install/uninstall lifecycle, 1/1 SPDX/license
inventory, 3/3 client profiles with 24/24 exact Skill copies, and 2/2 installed MCP protocol
versions. Native macOS bootstrap passed 3/3 clients, 3/3 handshakes, and 4/4 path cases. The Skill
structure validator also accepted the eighth Skill.

The final local tarball before the TTY correction was 694,184 bytes with SHA-256
`b394ce612d73e3d76eb03bd9f86e26f4b27810e4aab5654122d5814ff8f0b13b`; all 3/3 candidate Release
assets passed the frozen inventory and checksum verifier. Repository gates passed on JDK 21:
`verifyAiToolingRelease`, documentation structure/language/governance/translation checks,
`verifyDevelopmentToolingIsolation`, and `verifyReleaseIntent`; release intent found 0 Maven
artifacts and required no framework changeset. `qaQuick` and `qaPreview` completed 2,316 tasks in
13 minutes 14 seconds with zero failures.

Relative to public `0.6.0`, the normalized candidate change is one tool (14 to 15), one executable
(4 to 5), one Skill (7 to 8), and one bounded attended source-application flow (0 to 1), while
Android runtime and Maven artifacts remain unchanged. Conclusion: **improved** screenshot repair
utility and source-write containment with **no material Android runtime behavior change**.
Limitations: the real secure-backend evidence is local macOS APFS rather than hosted sudden-power
loss; Windows has no attended host in v1; the public registry package, protected provenance, and
repository-external human-confirmed apply/recovery/rollback are not yet evidence. The next action
is to merge the release candidate, publish only from `ai-tooling-v0.7.0`, reproduce the exact public
package outside this repository, and record immutable evidence before closing Wave D.

The first pre-tag repository-external run installed the exact 694,184-byte candidate from merge
`6edaa4b6e91099c23d7fb96155bcebda82dc61fa`. Package identity and the `viewcompose-repair` binary
were present, and `show` reproduced the prepared target, preimage/candidate hashes, complete
single-line diff, evidence identity, expiry, and confirmation suffix. The required unattended
negative then preserved the preimage byte-for-byte, but 0/1 invocations returned the promised
structured CLI diagnostic: opening `/dev/tty` through an asynchronous stream emitted an unhandled
`ENXIO` event and exited 1 before the CLI boundary could map it. The conclusion is **mixed**:
source-write denial worked, while diagnostic containment regressed from the contract. Publication
remains blocked.

The release candidate now awaits an explicit `FileHandle` opened with `r+` before constructing its
read/write streams, so missing controlling-terminal access is caught and mapped to
`VC-AI-SOURCE-APPLICATION-TTY-REQUIRED`; cleanup also tolerates every partially initialized handle.
A deterministic injected-`ENXIO` case passes, the focused transaction/CLI suite passes 9/9, and the
complete AI suite passes 374/374. This correction has **improved** diagnostic containment with
**no material Android runtime behavior change**. Its limitation is that the repacked installed
candidate must still complete human-confirmed crash apply, recovery, later-edit rollback refusal,
and successful rollback. The repacked 694,233-byte tarball has SHA-256
`3bb47595e70eb8835f234b44e49be628bb2bd61fd03d16a2f9886482e5f5ec15`; its installed transaction
source matched the candidate byte-for-byte. The real no-TTY invocation now passes 1/1: it exits 2
with only `VC-AI-SOURCE-APPLICATION-TTY-REQUIRED`, and the target remains the exact
`1a504684dae2593c74de6f177dd3e57cc825e03b66d98671cdfabe1ca319e104` preimage. The attended
operations recorded below close the remaining pre-tag acceptance boundary.

#### Accepted repository-external attended transaction (2026-09-02)

The repacked candidate was installed into a repository-external temporary Android fixture and the
human-attended transaction completed against request
`e69bf6f6abc28cb7325148c3031c3a7afcb79bd0f437236e8cfab6f8d18edceb`. The exact preimage was
`1a504684dae2593c74de6f177dd3e57cc825e03b66d98671cdfabe1ca319e104`; the prepared one-literal
candidate was `5812c3ccbd0a6f30a0cc4c3ff4e71453006745d5dd76e63e153b2501131252e9`. The controlling terminal
displayed the complete `Hello` to `Welcome` diff and accepted only the exact one-use `APPLY`
confirmation. An injected process stop immediately after the durable atomic replacement left the
candidate bytes in place without reporting success.

Public `recover` then reconciled the journal to `applied-validation-failed`, retained the exact
candidate, and produced apply receipt
`7ae681f690cd7f2bc3bc8c9f6a29b2ef8e5989ee0a71f5e3f4f0e0de1df0b34c`. Static validation passed
1/1; compilation, Preview rendering, and semantic/geometry evidence failed in the intentionally
minimal fixture, so the tool preserved the applied bytes and required an explicit rollback instead
of concealing the validation failure. After a simulated later user edit changed the target to
`339ad2fe6a28d240f16566a6842d063e480b1ed1ecd4fd75bc8e6705960d7338`, attended rollback refused
with `VC-AI-SOURCE-APPLICATION-PREIMAGE-DRIFT` and preserved those bytes. Restoring the exact
candidate and issuing a second attended rollback returned the target to the preimage, changed
status to `rolled-back`, disabled further rollback, and produced receipt
`cbd7e24c8af73410d7cf619003001907848480537b244084ec61f6b3a78ef39f`.

Relative to the pre-correction candidate, repository-external attended coverage changed from 0/4
to 4/4 required stages: atomic apply with injected interruption, durable recovery, later-edit
conflict refusal, and exact successful rollback. The conclusion is **improved** transaction safety
and recovery evidence with **no material Android runtime behavior change**. Limitations: this is a
macOS APFS/JDK 21 process-interruption fixture, not sudden power loss; its deliberately incomplete
Android project cannot establish successful compile, Preview, semantic, or pixel quality; the npm
package and protected provenance are not public yet. The next action is to merge this evidence,
create `ai-tooling-v0.7.0` at that merge commit, and reproduce the immutable public package before
closing Wave D.

#### Accepted public release and repository-external package (2026-09-02)

Pre-tag evidence PR [#272](https://github.com/ViewCompose/ViewCompose/pull/272) passed every required
check and merged as `83e7dd1c4a3e4c0198bf213a4f1ffa4d68a68708`. The immutable
`ai-tooling-v0.7.0` tag points exactly to that commit. Before tag creation, the tag, GitHub Release,
and npm `0.7.0` identity were all absent. Protected
[run `33581729261`](https://github.com/ViewCompose/ViewCompose/actions/runs/33581729261) used the
`ai-tooling-release` environment and GitHub OIDC Trusted Publisher; its complete release contract,
reproducible distribution, identity guards, three asset attestations, GitHub Release, and npm
publication passed in 9 minutes 58 seconds.

npm now exposes only stable `latest -> 0.7.0` for the current release. The npm package provenance
predicate is `https://slsa.dev/provenance/v1`; it binds repository `ViewCompose/ViewCompose`,
workflow `.github/workflows/ai-tooling-release.yml`, ref `refs/tags/ai-tooling-v0.7.0`, commit
`83e7dd1c4a3e4c0198bf213a4f1ffa4d68a68708`, and run `33581729261`. Its integrity is
`sha512-YQkbZ4A2GBbIok2QjENelBgo0IQ4OcBGM+H9bmTFtmUMLR97al9ujWQYA/WwXDEeQxvLAzTrkFqZNkU/MTrOuQ==`.
The GitHub Release contains exactly three assets: the 690,005-byte tarball with SHA-256
`00886678178c2f29b819cc045cbebd040e3519eb0ec6245621d3f637102cf936`, the 38,796-byte Manifest with
SHA-256 `383333a5fe1926ce0593f1d240a11190a8c76f6d3ae8b29439dea71a9650f183`, and the 179-byte checksum
list with SHA-256 `c0cafb9af4518f320463a7cb07d64c23e02ac6be6b2b9700b7a4408ea2eba29f`. Checksums and GitHub
attestations passed 3/3; the npm registry tarball and GitHub Release tarball were byte-identical.

Fresh repository-external projects ran the literal `@viewcompose/ai-tooling@0.7.0` selector for
Codex, Claude Code, and Cursor. `init`, `doctor`, and `uninstall` passed 9/9; all three clients
reached `project-bound-ready`, installed 8/8 Skills each, reported both capability groups ready on
JDK 17 plus Android SDK 36, and removed 24/24 managed Skill copies. The durable public package
completed MCP `2025-11-25` initialization, listed 15/15 tools with
`prepare_screenshot_repair`, and its repair CLI reproduced the earlier attended transaction as
`rolled-back` with receipt
`cbd7e24c8af73410d7cf619003001907848480537b244084ec61f6b3a78ef39f` and exact preimage
`1a504684dae2593c74de6f177dd3e57cc825e03b66d98671cdfabe1ca319e104`.

The protected Linux/Node 24 tarball differs from the pre-tag macOS/Node 26 candidate archive:
690,005 versus 694,233 bytes and a different archive checksum. Manifest comparison found only four
changed archive-identity fields. Direct decompression produced two byte-identical 3,813,376-byte tar
payloads, and `diff -qr` found zero unpacked file differences. The source
transaction implementation hashes matched at
`74aa6e04b97d651428842911c9ece8d4417cb4575d7d3bdb30fa44dedddaf35e`, and repair CLI hashes matched
at `1d92f19de9a2156545c5110ef7b390dcdb1f67716fd00f6dc8ba7febe86beee2`. The pre-tag human test
therefore exercised the exact installed implementation bytes, while the immutable public gzip
archive identity is owned by the pinned release environment. The repository's post-publication
gate was rerun under JDK 21 and passed 187 Gradle tasks in 2 minutes 18 seconds, including 2/2
reproducible local builds, 1/1 registry-integrity-bound `published-payload` comparison, 1/1 offline
install/uninstall lifecycle, 3/3 installed client profiles, 24/24 exact Skill copies, 2/2 MCP
protocol versions, and 3/3 immutable Release assets.

Relative to public `0.6.0`, the normalized result is one tool (14 to 15), one executable (4 to 5),
one Skill (7 to 8), and one attended bounded source-application flow (0 to 1), with public lifecycle
readiness unchanged at 3/3 clients. The primary conclusion is **improved** screenshot-repair utility,
recovery, and source-write containment with **no material Android runtime behavior change**.
Limitations: one macOS APFS/JDK 21 process-interruption fixture is not sudden power loss; public
onboarding used one macOS host and did not launch proprietary Agent binaries; Windows has no
attended source host in v1; and cross-environment gzip archive bytes are not claimed reproducible.
The accepted uncompressed tar payload remains byte-identical, and hosted CI separately covers
Linux, macOS, and Windows onboarding in the pinned environment. Wave D is complete. The next action
is released-profile maintenance and adoption evidence; Compose Waves E and F remain unactivated at
lowest priority.

### Per-wave pull request and release discipline

1. One execution wave owns one branch and one pull request. If a wave becomes too large, split it by
   its listed commit boundaries while keeping later waves blocked until the final wave gate passes.
2. The first commit freezes contracts and negative fixtures; the last implementation commit updates
   active documentation and evidence interpretation. Public pages update English and Simplified
   Chinese together.
3. Every changed public/protected framework API completes capability impact, Q level, contract fields,
   canonical KDoc/Javadoc, compiled Q3 sample, module manual, API dump, and Changeset in the same
   change. Prefer tooling-only changes; do not alter framework API solely to simplify a converter.
4. Every change to a published artifact's source, publication inputs, or compiled API samples adds
   the immutable `release/changes/<unique>.json` disposition required by repository policy. A
   documentation-only handoff such as this one adds no release Changeset.
5. Each pull request runs its focused suites plus `verifyDocumentationStructure`,
   `verifyDevelopmentToolingIsolation`, `verifyAiToolingRelease`, `qaQuick`, and every affected
   installed-distribution/evidence gate. Preview-affecting work also runs `qaPreview` in the accepted
   environment.
6. Record absolute results, normalized change, conclusion, limitations, and next action in this plan.
   Do not advance based on raw output, aggregate green status, or a locally packed archive.
7. Merge before publication. Publish only from the protected tag workflow, verify the public clean
   install, then update this plan and roadmap with immutable URLs and evidence before unblocking the
   next wave.

Active. The audit and Phase 0 contract/security freeze are complete. Phase 1 canonical knowledge
generation, hosted discovery, freshness gates, and full-site acceptance are complete. Phase 2
static validation, pinned compilation, Preview evidence, read-only project findings, and internal
CLI foundations are complete. Phase 3 deterministic Knowledge Bundle retrieval, its CLI surface,
dual-era stdio MCP, deterministic Preview layout diagnosis, and five foundational client-neutral Agent
workflows are complete. The reproducible local distribution, offline lifecycle, SPDX/license
inventory, installed compile example, and protocol compatibility gates complete the Phase 3
foundation.
The missing consumer setup boundary is now also complete: the offline package exposes one
`viewcompose-agent` command with deterministic project profiles for Codex, Claude Code, and Cursor,
installs the six canonical Skills into each client's standard project root, and verifies installed
configuration, exact Skill bytes, idempotence, conflict/path safety, and both MCP protocol versions.
The resumed screenshot-repair lane now has a frozen applied-result handoff contract. It requires the
exact process-local execution outcome and original trusted host, re-reads the durable terminal
record before delivery, binds one content-addressed handoff receipt to the complete result lineage,
and permits only one delivery of the exact frozen in-memory Design IR. Implementation, result
persistence, application source writes, and public repair activation remained off at that freeze.
The internal implementation now meets the frozen denominator against the durable reference store:
it retains only a validated applied result, reopens the terminal record, returns the exact frozen
Design IR beside an immutable handoff receipt, clears its retained reference after delivery, and
rejects serialized authority, receipt drift, concurrent duplicates, and replay. Result persistence,
production-host authentication, source writes, and public activation remain off.
Consumer onboarding is now the higher-priority Phase 6A lane. Its frozen v2 client contract removes
the ViewCompose checkout from all three standalone MCP profiles, caps the public path at one package
installation plus one project initialization command, and requires transactional configuration and
Skill writes with zero manual edits. A separate GitHub Release contract binds the first package to
an immutable tag, three exact assets, SHA-256, and GitHub build-provenance attestations. These
contracts are implemented: all three clients pass project-bound `init`, `doctor`, idempotent re-entry,
and exact `uninstall`; the cold release gate builds the complete Preview producer graph; and the tag
workflow verifies, attests, and publishes the three assets without a mutable selector.
The Phase 6A consumer-project execution v1 boundary is now implemented. `viewcompose-agent init`
binds the physical consumer root into the installed MCP process, but that root remains a
read-only authorization boundary: the tooling never executes the consumer wrapper, settings,
plugins, tasks, or build scripts. A packaged content-addressed Gradle harness owns execution and
selects only fixed Maven Central coordinates, Gradle 9.3.1, AGP 9.1.1, Kotlin 2.2.10, Android 36,
JVM target 11, and JDK 17/21. The first deep-evidence request may visibly resolve the pinned Gradle
distribution and Maven dependencies; later requests may use the verified tool-owned cache.
Compilation, generated Preview, and generated-screen layout diagnosis pass from all three installed
Agent profiles without a ViewCompose checkout. Arbitrary
consumer source discovery, consumer dependency mirroring, manifest merging, custom plugins, Gradle
task selection, application execution, device deployment, and source writes remain outside v1.

The version-bound Phase 6A prerequisite is now implemented for Release `0.3.0`. Initialization and
upgrade detect exact consumer versions without executing consumer Gradle and select only a Released
Knowledge Pack generated from immutable per-Artifact publication revisions. The public upgrader
searches immutable Releases for the newest exact profile match, verifies the three-Asset inventory,
contract majors, Manifest, checksum list, archive size, and SHA-256, installs the candidate
side-by-side, and transactionally migrates the managed MCP entry plus unchanged Skills. It never
uses global tooling recency as a framework compatibility signal. Dynamic, unresolved, conflicting,
unsupported, or unrepresented historical version vectors retain the previous integration.

Acceptance ran on macOS with JDK 17 and Android SDK 36. The first released-Maven Kotlin smoke
compiled in 28,958 ms; the integrity-verified repeat returned in 31 ms. This changes the previous
source-checkout requirement to a source-free installed-package path and is therefore **improved**
for consumer setup. The installed-distribution gate then passed 2/2 reproducible builds, 3/3 Agent
profiles, 18/18 exact Skill copies, 2/2 MCP protocol versions, released-artifact Kotlin compilation,
2/2 XML generated Preview/layout comparisons, 1/1 screenshot render, 1/1 screenshot semantic
comparison, and 1/1 exact RGBA comparison. The cached login Preview and layout comparison completed
in approximately 4.3 seconds with 32/32 checks and a clean five-node diagnosis. These are local
cold/warm functional measurements, not a cross-platform latency guarantee; dependency mirrors,
machine load, and first-use downloads remain limitations. At this acceptance slice, the remaining
release action was publication and attestation of `ai-tooling-v0.3.0`; that action completed on
2026-08-31, and the denominators remain CI inputs.
The same cache-root correction was propagated through the internal screenshot-repair evidence
chain. Its unchanged candidate passed 6/6 released-artifact gates with zero mismatched pixels; the
typed `Welcome` → `Hello` candidate passed compilation, rendering, semantics, and structure while
reporting 2,221/2,523,781 mismatched pixels, 1,516 localized to the changed title. Proposal,
authorization, host grant, terminal outcome, and applied-result handoff fingerprints were resealed
from that evidence. This is **improved** isolation consistency with no public repair activation and
no Android runtime behavior change; the pixel count is lane-specific evidence, not a perceptual
quality threshold.
Phase 4 now has a frozen typed Design IR v1 and a fail-closed Android XML v1 migration subset with
one supported golden and three explicit unsupported denominators. The bounded XML parser now meets
the frozen IR determinism, provenance, resource-preservation, and unsupported-honesty gates. The
IR-to-Kotlin generator now produces the exact login golden and passes the hermetic compiler. The
accepted converter is the ninth shared CLI/MCP tool, works in standalone generation and explicit
source-bound compile modes, ships in the reproducible offline package, and is orchestrated by the
sixth client-neutral consumer workflow. The next increment is now frozen as Android XML project
context v1: explicit-root resource and style resolution plus a read-only, bounded lexical call-site
inventory whose completeness is never claimed. That context is now integrated as an explicit
project input form of the shared CLI/MCP converter, and its styled golden passes the hermetic
compiler without changing standalone source input. Android XML layout v2 is now contract-frozen as
the next compatible subset for `FrameLayout`, `ImageView`, explicit image accessibility, drawable
bindings, image scaling, and visibility. Its parser, IR, generator, project composition, installed
CLI/MCP generation, and hermetic compile gates now pass. The following explicit-root layout
dependency contract is also frozen: it bounds default-layout selection, `include`/`merge`
expansion, dependency cycles, graph identity, and cross-file provenance before implementation. Its
resolver, project-context composition, CLI/MCP distribution, and hermetic compile gate now pass.
The generated-screen Preview contract is now also frozen and implemented: it binds generated
Kotlin, explicit preview values, one fixed configuration, the released-Maven compiler and renderer
lanes, and all accepted artifacts into a content-addressed request while denying inspected-project
build execution. The packaged tool-owned harness, project-bound CLI/MCP render mode, exact artifact gate,
stable cache proof, and installed-package render denominator now pass. Exact embedded PNG bytes now
also become a tool-owned Android resource without any caller path, URL, inspected-project resource
read, or network access; the accepted XML v2 profile-card fixture compiles and renders through that
lane. The exact structured semantic and geometry comparison contract between the converter's Design
IR expectations and accepted render-tree evidence is now implemented. Both generated fixtures pass
the public `compared` evidence gate through the installed package. The provider-neutral screenshot
input, deterministic preprocessing, privacy, and evaluation boundary is now frozen before any
model-backed generation begins. The dependency-free preprocessor is now the tenth shared CLI/MCP
tool and reproduces the same privacy golden through the installed package. It still performs no UI
inference. The provider-neutral screenshot-to-Design-IR request/result, lineage, evidence,
uncertainty, and consent contract is frozen without selecting a provider. Its offline validator is
now the eleventh shared CLI/MCP tool: it deterministically reproduces preprocessing, reconstructs
the exact inference request, validates an externally produced result, and imports Design IR only
after every lineage, evidence, uncertainty, and authorization check passes. It performs no model or
provider execution and no network request. The typed human-resolution patch contract is now also
frozen: it binds every answer to the exact validated import, question, node, pixel region, required
action, reviewer, and review receipt; forbids executable expressions and guessed resources; and
derives code-generation eligibility only after all blocking questions, unsupported semantics, and
placeholder bindings reach zero. Its offline adapter is now the twelfth shared CLI/MCP tool. It
revalidates the imported inference identity, applies only component-compatible typed fields and
caller bindings, persists the complete accessibility review into Design IR, and reproduces the
resolved golden through the installed package with no provider, network, or answer execution. The
next screenshot-specific Kotlin generation contract is now frozen. Its checked-in golden maps the
typed state and event bindings to real public APIs, preserves every accessibility disposition in a
machine-checked report, and passes the hermetic compiler. The thirteenth shared CLI/MCP tool now
reproduces that source and report in generate mode and returns hermetic compiled evidence in compile
mode, including from the installed package. Provider selection remains a separate, explicitly
authorized decision. The source-bound screenshot generated-Preview contract is now implemented with
explicit state and fixed no-source callback bindings, exact rendered evidence, CLI/MCP parity, and
installed-package verification.

Last verified: 2026-08-31.

The 2026-08-30 clean hosted-runner audit exposed that the documented Preview preparation boundary
did not resolve the Android variant-specific runner classpath used by the later offline Gradle
process. The fixed lane now prepares compile/runtime class jars, resources, assets, packaged
symbols, the variant runner, worker host, and both Layoutlib archives for the counter and generated
Preview targets. `qaPreview` owns counter preparation after local Maven publication, while the
installed-distribution gate owns generated-harness preparation; the actual CLI/MCP processes still
run with `--offline` and cannot fill a missing cache. On JDK 21, both preparation tasks resolved
successfully. A clean counter output then passed `qaPreview` in 39 seconds and the independent
offline render produced a cache miss in 29,920 ms with a 1079×2339, 25,755-byte PNG, a 121,271-byte
render tree, zero diagnostics, and unchanged aggregate fingerprint
`bb7eba4f51d1aa4f788b0991b7c8635815d6943c374978b685f92619420841d0`. This is **improved**
reproducibility with **no material runtime behavior change**: dependency acquisition is explicit
before the hermetic boundary, and generated UI, Preview protocol, pixels, and Android artifacts are
unchanged. The installed-distribution gate then passed in 1 minute 29 seconds, including 2/2
reproducible packages, offline install/uninstall, both MCP protocols, compiled screenshot and XML
denominators, three generated Preview results, semantic comparison, and exact pixel comparison.
The next clean Linux run confirmed that dependency preparation completed, then exposed a separate
reference-integrity defect: the rendered PNG and accepted render fingerprint were exact, but native
zlib 1.2 and 1.3 selected different compressed byte streams when the 1079×2339 reference was
canonicalized. The corrected large-image lane now applies Paeth filter type 4 and a repository-owned
fixed-Huffman, distance-one DEFLATE encoder above 4,096 decoded bytes. Node 20.19.5, 24.12.0, and
25.6.0 each reproduce the same 93,032-byte PNG, SHA-256
`69ac5adde66e6f5725a0258987f7f635cb7be333839536f06c0ae6a2ff0596e2`, and result fingerprint
`e874a198d57e64645472dc11dac8e82df35e11117869dd616d33c93a311eb091`. Decoding still proves
2,523,781/2,523,781 exact pixels; the accepted comparison fingerprint is now
`6ad4d53b294bb3e6faba9d39ac8fccf76deb32cb964c7f32553264b18072310f`.

This is **improved** cross-runtime reproducibility with **no material Android runtime behavior
change**: PNG compression identity changed, while the decoded RGBA reference, render, semantics,
geometry, and zero-tolerance policy did not. Small established fixtures retain their frozen
filter-0/zlib-level-9 encoding, so this evidence does not claim a repository-owned compressor for
every sub-4,097-byte image. The focused Node suite passes 266/266 tests, Phase 0 verifies all 21
schemas and 73 cases, and the pixel, repair, proposal, authorization, host-grant, and terminal
outcome chains reproduce their updated content addresses. The next acceptance action remains a
clean Linux hosted rerun of both `qaQuick` and `qaPreview`.

The screenshot semantic and structural comparison and the separate exact RGBA comparison are now
implemented. Pixel comparison admits only canonical, zero-redaction, full-viewport references
whose dimensions, density, font scale, locale, layout direction, color space, alpha mode,
orientation, system bars, and accepted semantic evidence exactly match the render. The original
16×24 inference wireframe is therefore not assigned a pixel score.

The bounded deterministic repair contract and its provider-offline internal orchestrator are now
implemented, including typed Design IR patch application, source-bound candidate evaluation, and
content-addressed structured candidate evidence, but automatic repair is not yet a public tool
mode. The reproduced cross-build persistent Preview worker isolation gap is now closed by binding
worker reuse to one exact build identity. Exact RGBA comparison now retains separate bounded
global mismatch bounds, deepest-containing Design IR node attribution, stable tie-breaking, and
explicit unassigned pixels without deriving a repair value from location. The internal v1 proposer
now consumes only two integrity-verified records from the same resolution and exact reference. It
may roll back exactly one localized `properties` value, and only to the typed value retained by a
strictly better baseline. The real `Hello` regression emits the frozen `Welcome` patch and that
patch passes the typed applier plus all six source-bound gates with zero mismatched pixels. Novel
repair inference and public CLI/MCP activation remain off until accepted baseline provenance and
explicit authorization are separately frozen.

That v1 authorization boundary is now frozen with two purpose-bound human attestations and exact
content-address binding. Its internal validator now reproduces all available evidence and proposal
bindings while fixing `executionAuthorized` to false; every execution mode remains off.
The following host-grant lifecycle now has an internal direct-callback adapter: only an explicitly
registered in-process host may authenticate both reviewer receipts, check revocation, and atomically
reserve one terminal repair attempt. A durable file-backed test host proves concurrent and
cross-instance replay denial. The terminal outcome contract now additionally freezes applied,
failed, cancelled, and indeterminate receipts after reservation; every state is terminal and
non-retryable, while only an exact committed application may expose content-addressed output
identities. An internal attended executor now consumes the process-local grant capability, applies
only the exact typed Design IR patch in memory, and accepts a terminal receipt only through another
direct trusted-host callback. Production host integration, persistent source output, durable
result storage, and public activation remain off. A local file-backed reference store now durably
publishes private terminal receipts, returns identical writes idempotently, rejects conflicts
without overwrite, and reopens receipts for read-only reconciliation without patch re-execution.

## Contract freeze — common AI agent onboarding v1

The next foundation slice interrupts the deeper screenshot-repair handoff because the accepted
MCP and Agent Skills distribution is protocol-neutral but has no supported consumer setup route.
The first onboarding contract is limited to Codex, Claude Code, and Cursor. Their current official
documentation accepts local STDIO MCP servers and standard `SKILL.md` workflows, but each client
uses a different project configuration or Skill discovery path.

`agent-client-integration-v1` freezes three ordered profiles, exact official documentation sources,
project-scoped MCP configuration paths, standard Skill roots, invocation prefixes, verification
actions, the current package identity, and both supported protocol versions. One installed
`viewcompose-agent` executable will generate deterministic configuration without editing a client
file and will copy the six canonical Skills only into an explicit absolute consumer-project root.
Existing different Skill bytes, symbolic-link boundaries, implicit home-directory installation,
and unknown clients fail closed. The package continues to open no network connection and does not
configure, authenticate, or launch a proprietary client.

Acceptance requires 3/3 schema-valid deterministic profiles, exact installed Skill bytes for all
three layouts, idempotent reinstall, conflict and path-safety rejection, two reproducible package
builds, offline installation and uninstallation, and both frozen MCP handshakes from the installed
server. CI does not authenticate or automate proprietary client binaries, so the public matrix
must describe this as configuration and protocol evidence rather than vendor end-to-end
certification. A top-level bilingual AI Integration chapter and both project READMEs must expose
the complete setup and client-owned final connection checks. The previously planned
content-addressed applied-result handoff resumes only after this missing consumer foundation is
accepted.

## Accepted implementation — common AI agent onboarding v1

The 2026-08-30 implementation closes the frozen consumer foundation with one dependency-free
`viewcompose-agent` executable. `config` emits but never writes the exact Codex TOML or Claude
Code/Cursor JSON fragment and binds the installed MCP server plus an explicit physical
`VIEWCOMPOSE_SOURCE_ROOT`. `install-skills` accepts only one explicit physical consumer-project
root, copies all six canonical `SKILL.md` files into `.agents/skills` or `.claude/skills`, returns
identical reinstall results, and rejects unknown clients, relative roots, symbolic links, and
different existing bytes without overwrite. It infers no home directory, opens no network
connection, and does not configure, authenticate, or launch a proprietary client.

The dedicated source gate passed 3/3 deterministic profiles, 18/18 exact Skill copies, 3/3
idempotent reinstalls, and 3/3 conflict/path-safety rejections. The complete Node suite passed
270/270 tests. The installed-distribution gate passed two byte-identical package builds, one
offline install/uninstall lifecycle, one SPDX/license inventory, all three installed profiles,
18/18 installed Skill comparisons, both MCP protocol handshakes, and the existing compile,
Preview, semantic/geometry, and exact-pixel denominators on JDK 21. `verifyAiAgentClients` and the
quality-build test suite also pass. The new top-level bilingual AI Integration chapter publishes
the complete installation and client-owned final checks; CI still does not claim authenticated
vendor-binary end-to-end certification.

The first two-route chapter candidate passed all functional site checks but exceeded the 46.9 MiB
non-API ceiling. Consolidating overview and setup into one route produced 49,238,608 bytes versus
the last accepted 49,042,390-byte output: `+196,218` bytes (`+0.4001%`), a **regressed** size result.
The reviewed 47.1 MiB ceiling leaves 149,321.6 bytes of headroom. This is local uncompressed-output
evidence, not transfer-size, runtime, or vendor-client evidence. The next documentation action is
to hold the ceiling and reuse this route until structural recovery creates measured capacity.

This is **improved** consumer discoverability, repeatability, path safety, and protocol confidence
with **no material Android runtime behavior change**. All implementation remains in downstream
development tooling and documentation, so no published Maven artifact or publication input changed
and no new Maven release changeset is required. The previously frozen applied-result handoff is
again the next action.

## Contract freeze — version-bound AI tooling upgrades v1

The upgrade boundary separates the AI tooling runtime version from framework knowledge identity.
One consumer-selectable profile is a content-addressed vector of exact
`com.viewcompose:<artifact>:<version>` coordinates, their immutable release revisions, one released
Knowledge Bundle fingerprint, and the exact Harness coordinates. Current-source knowledge remains
valid only for its exact checkout and can never satisfy a released consumer profile.

Read-only project detection accepts exact literal coordinates, standard version catalogs, and
dependency lock records. It treats a dependency-free Android project as a new-project case, but
rejects dynamic versions, ranges, unresolved aliases or variables, duplicate conflicts, and a
ViewCompose import whose owning dependency cannot be resolved. It never executes consumer Gradle
settings, plugins, tasks, or build logic.

Candidate selection compares the detected Artifact subset before considering tooling recency. It
chooses the newest immutable AI tooling Release with an exact matching profile, or leaves the old
integration active when no candidate exists. Download verification and side-by-side installation
complete before one transaction replaces only the exact old MCP entry and unchanged canonical
Skill bytes. The frozen denominator covers profile generation, project detection, matching and
non-matching candidates, checksums, contract majors, user conflicts, interruption, rollback, and
recovery across all three supported Agent clients.

This contract adds no public/protected framework API, Maven publication input, or application
runtime behavior, so no Maven release Changeset or module-manual update is required. It changes a
public development-tooling contract and therefore owns ADR-0025, the Phase 0 schema/example, the AI
Integration chapter in both locales, and the installed-package acceptance gate.

### Implementation evidence — read-only consumer Artifact profile

The first detector traverses one explicit physical project root with fixed file, byte, depth, and
per-file ceilings; excludes build, cache, IDE, dependency, credential, and repository metadata
trees; and rejects symbolic links without following them. It recognizes exact literal Gradle
coordinates, used default `libs.versions.toml` library aliases and bundles, and Gradle dependency
lock records. A lock may resolve a dynamic declaration, but a stale lock that disagrees with an
exact declaration remains a conflict. ViewCompose imports without any resolvable dependency are
not classified as a new project.

On 2026-08-30, Node 25.6.0 passed the focused 8/8 detector cases and the complete 290/290 AI-tooling
tests. The exact denominators are 1/1 literal multi-Artifact profile, 1/1 used catalog alias, 1/1
catalog bundle, 1/1 dynamic declaration resolved by a lock, 2/2 unresolved/conflict families, 1/1
empty-versus-import distinction, and 1/1 symbolic-link denial. Relative to the contract-only
baseline, this is **improved** project-version evidence with **no material Android runtime change**;
the detector is downstream Node tooling and never invokes Gradle.

The evidence is local macOS filesystem coverage. It does not resolve custom catalog names,
arbitrary Kotlin/Groovy variables, convention plugins, composite-build dependency substitution,
or a live Gradle graph. Those inputs fail closed. The exact Released Knowledge Pack, runtime
binding, checksummed Release discovery, and side-by-side migration described below now give the
detector a trustworthy candidate without broadening its read-only boundary.

### Implementation evidence — released Knowledge Pack and runtime binding

On 2026-08-31, Node 25.6.0 reproduced one consumer-selectable profile twice from immutable
publication inputs. It contains 38 exact published Artifact identities, 30 knowledge-owning
Artifacts, 70 capabilities, 531 symbols, 187 samples, and 10 rules. The content-addressed profile is
`895ed1e52e5a9735f87e6d996e77ea43ca34cc2e496854408c40772419129064`; its released Knowledge
Bundle fingerprint is
`9ee4560b30f2d26378314d5b8c8acf20343662f5a8c1d5bfc0442944c4d09660`. The generator rejects
publication metadata absent from immutable history and accepts its newest source anchor only after
every included Artifact's recorded release `src/main` tree is byte-identical at that anchor.

Project-bound `init` now selects that profile before any write and persists its ID in the MCP
environment. Retrieval, static validation, project analysis, compilation, and generated Preview all
load the selected Bundle; source-bound contributor setups explicitly retain `current-source`.
The installed-package gate passed 2/2 reproducible packages, 3/3 Agent profiles, 18/18 exact Skill
copies, 2/2 MCP versions, and the existing compile, generated Preview, XML, layout, screenshot, and
pixel-comparison denominators. This is **improved** version compatibility with **no material Android
runtime behavior change**.

The accepted pack represents the newest published Artifact vector and does not yet cover every
historical vector. Projects with unsupported older versions, custom catalog names, arbitrary build
variables, convention plugins, or conflicting evidence fail before mutation. Release discovery,
download integrity, side-by-side installation, migration rollback, and recovery are implemented by
the following acceptance step; unsupported projects still receive no knowledge substitution.

### Implementation evidence — compatible Release upgrade and recovery

Release `0.3.0` exposes one public `viewcompose-agent upgrade` command. Candidate discovery first
classifies the physical consumer project, then scans immutable `ai-tooling-v<semver>` Releases and
selects the highest tooling version containing an exact matching framework profile. A newer
incompatible Release is skipped rather than installed. The selected Release must declare exactly
the Tarball, sidecar Manifest, and checksum list; all three downloads, the archive size and SHA-256,
every installed Package byte, and supported contract majors are verified before project mutation.

The Package installs into a content-addressed side-by-side user-cache directory. Migration accepts
only the exact previously managed MCP entry and canonical Skill bytes, stages replacements and
backups in their destination filesystems, and records path/hash-only recovery state. An interruption
before commit rolls back; an interruption after commit finishes cleanup on the next lifecycle
command. The original globally installed Bootstrap follows the verified managed MCP entry so
`doctor`, later upgrades, and `uninstall` operate on the active side-by-side Package.

On 2026-08-31, Node 25.6.0 passed 12/12 focused upgrade and client-integration cases: 3/3 client
migrations, 3/3 injected interruption rollbacks, 1/1 newer-incompatible Release skip with older
compatible selection, 1/1 unsupported/no-candidate family, 1/1 checksum-tampering rejection, and
1/1 successful orchestration/no-op family, 1/1 GitHub redirect/inventory/path-denial family, and 1/1
exact installed-Package inventory check. Relative to manual replacement or a global-latest installer,
this is **improved** framework compatibility and recovery with **no material Android runtime
behavior change**. The evidence uses mocked Release downloads plus real filesystem validation; the
complete installed distribution exercises npm's offline Package lifecycle separately. The
tag-triggered GitHub workflow and published attestations remain the next external release evidence.

The execution cache now has a contract-versioned root rather than a tooling-version root, while
every request remains content-addressed by Knowledge, Harness, source, and lane fingerprints. This
allows a compatible Runtime upgrade to reuse deep evidence without treating stale output as a hit.
The first local transition to the new empty root was **mixed** cold-start evidence: a 120-second
compile request and then a 180-second Preview request cancelled after safely persisting partial
Gradle output; the subsequent complete installed gate passed in 135 seconds. Deep requests now use
one fixed five-minute ceiling. This is not a clean-host latency guarantee; the tag-triggered Linux
gate must still prove a fresh-cache Release build, and later work should reduce cold compilation
rather than further expanding the bound.

The final local acceptance used Node 25.6.0 and Corretto 21.0.6. It passed 304/304 AI-tooling tests,
27/27 schemas, 2/2 reproducible Packages, 1/1 offline install/uninstall lifecycle, 1/1 SPDX/license
inventory, 3/3 installed Agent profiles, 18/18 exact Skill copies, 2/2 MCP protocol versions, the
released-artifact Kotlin/XML/Screenshot compile lanes, generated Preview and layout comparisons,
and exact RGBA comparison. The root gate passed 194 tasks in 1 minute 58 seconds, with 172 executed
and 22 up-to-date; it also accepted Documentation Governance V2, documentation structure,
development-tooling isolation, Released Knowledge freshness, and all 3 Release assets. The result
is **improved** release readiness with **no material Android runtime behavior change**. Hosted clean
Linux execution and GitHub attestations remain `inconclusive` until the immutable tag workflow runs.

The first hosted #260 `qaQuick` run exposed a clean-checkout defect in that result: Released
Knowledge verification attempted to inspect every immutable Artifact source revision, but assumed
those commit objects already existed in the pull-request checkout. The job correctly failed at
`verifyAiReleasedKnowledgePack` when the shallow clone lacked revision `143b09ac`. The verifier now
checks each exact 40-character release SHA, fetches only a missing immutable object from `origin`,
then retains the same commit-timestamp and `src/main` tree comparisons. The focused suite passes
4/4 cases, including missing-object acquisition and no-fetch reuse, and the complete Released
Knowledge Pack still reproduces profile `895ed1e5`. This is **improved** clean-checkout
reproducibility without relaxing framework-version matching; the next evidence is the hosted rerun.

The same first #260 attempt exposed 49,544,398 non-API documentation bytes, 156,468.4 bytes above
the unchanged 47.1 MiB ceiling. The global generated sidebar repeated all 25 ADR children on the
532-page bilingual site. Compacting that one category to its complete bilingual index retained all
routes, content, search, and source links while reducing the same local corpus to 48,684,356 bytes:
`-860,042` bytes (`-1.7359%`) and 703,573.6 bytes of headroom. All site acceptance checks passed in
30.9 seconds. This is **improved** representation rather than a budget increase; hosted Linux
remains the next acceptance environment.

## Maven release changesets

- `release/changes/20260829-preview-worker-jvm21-resolution.json`

## Release intent rationale

The planning, contracts, Knowledge Bundle, validator, analyzer, and compiler-harness slices do not
change a published artifact. Phase 2 Preview acceptance exposed and fixes one production
configuration mismatch in `viewcompose-preview-gradle-plugin`; its immutable Changeset classifies
that artifact as a fix. Later publication-relevant slices must add one Changeset per affected
published artifact, or record an explicit ignored disposition with a concrete reason.

## Objective

Make ViewCompose an Android UI framework that coding agents can use correctly because the framework
provides machine-readable truth, deterministic retrieval, bounded generation targets, executable
validation, render evidence, and structured repair inputs.

The product direction is **AI-verifiable development**, not an AI model embedded in the framework.
The framework owns facts and deterministic tools. The coding client owns model selection,
conversation, credentials, network access, and repair orchestration. A generated UI is acceptable
only when the evidence required by its lane passes.

The target interaction is:

```text
developer intent or existing UI source
  -> coding agent
  -> versioned ViewCompose knowledge retrieval
  -> bounded source or Design IR generation
  -> static validation
  -> hermetic compilation
  -> optional Preview render and diagnostics
  -> structured comparison and repair
  -> code plus evidence and explicit unsupported semantics
```

This plan deliberately puts the missing foundations ahead of MCP breadth, converters, and visual
generation. A protocol endpoint cannot compensate for stale knowledge, unverifiable snippets, an
unsafe executor, or an undefined measure of correctness.

## Baseline and audit interpretation

### Accepted foundations

The repository already has more reusable infrastructure than a new AI subsystem should recreate:

1. `website/src/data/capability-reference.json` contains 537 owned application-facing entries,
   grouped into 77 stable capabilities and 30 artifacts across 15 groups.
2. Every current capability points to a compiled-region sample. The broader documentation corpus
   contains 185 compiled regions and seven explicitly non-executable historical examples.
3. Governance V2 already connects symbols, `capability_id`, artifacts, samples, generated Reference,
   handwritten owners, public API impact, and release intent.
4. Strict API documentation policy requires canonical English KDoc/Javadoc and compiled Q3 samples
   for new or changed public/protected API.
5. `viewcompose-preview` can compile the native DSL, render through Layoutlib, emit PNG output,
   expose render-tree/source-location data, and return structured diagnostics.
6. Runtime and renderer diagnostics already model frame failure, recomposition reasons, node
   patches, lifecycle state, render trees, and bounded production aggregation.
7. ADR-0009 already requires concrete development tooling to live downstream, activate only for a
   debuggable artifact and explicit request, and own no recurring inactive-path work.

### Missing foundations

| Gap | Why it blocks later work | First owner |
| --- | --- | --- |
| No accepted evaluation corpus or thresholds | “AI works well” cannot be reproduced, compared, or release-gated | Phase 0 |
| No AI-tooling architecture and threat model | Compilation, project inspection, credentials, and file access have undefined boundaries | Phase 0 |
| No canonical versioned AI bundle | An MCP server or skill would scrape several sources and drift from published API truth | Phase 1 |
| No compact `llms.txt` discovery surface | Generic models cannot cheaply locate the canonical Reference, rules, or samples | Phase 1 |
| No generated validator index | Static checks would hand-maintain API names and repeat SceneView-style drift risk | Phase 2 |
| No hermetic snippet compiler | A symbol match cannot prove Kotlin type resolution, overload selection, resources, or dependencies | Phase 2 |
| No stable Preview tool adapter | Visual tools would bypass the existing renderer and diagnostics contract | Phase 2 |
| No consumer-facing MCP, CLI, or skills | Agents cannot retrieve or validate through a supported local protocol | Phase 3 |
| No tooling-only Design IR | XML, Compose, screenshot, Figma, and prompt paths would each invent a different mapping model | Phase 4 |
| No conversion or visual-repair corpus | Migration and screenshot claims would be anecdotal and easy to regress | Phases 4 and 5 |

Conclusion: the repository is **AI-ready in source quality**, but not yet **Agent-ready as a
supported development interface**. Phases 0 through 3 close that gap. Later generation features
must reuse those contracts rather than create parallel truth or validation paths.

## Architecture and safety invariants

### One canonical knowledge lineage

AI-facing data has one lineage:

```text
canonical KDoc and published signatures
  + Governance V2 capability records
  + compiled samples and handwritten rules
  + artifact publication and version metadata
  -> deterministic AI Knowledge Bundle generator
  -> manifest + symbols + capabilities + samples + rules
  -> compact llms.txt / optional full discovery document
  -> CLI, MCP, skills, validator, converters, and evaluations
```

`llms.txt`, an MCP resource, a search index, a mapping table, and validator declarations must not be
hand-maintained copies of the same API. Every generated artifact records its schema version,
framework version lane, artifact coordinates, source revision, and canonical `sourceFingerprint`.
CI regenerates and compares checked-in or packaged outputs and rejects freshness drift.

### Runtime and provider isolation

AI tooling lives under a downstream `tools/` boundary or a separately distributed development
artifact accepted during Phase 0. No runtime, renderer, UI Foundation, design-system, integration,
or application aggregate artifact may depend on MCP, model SDKs, network clients, parsers, image
comparison engines, or conversion code.

The framework does not select or call a model provider. Provider adapters, when Phase 5 needs them,
are optional downstream processes with explicit configuration, bring-your-own credentials, bounded
input disclosure, redaction, and no telemetry by default. Secrets never enter prompts, generated
files, diagnostics, caches, screenshots, or MCP logs.

### Validation is layered evidence

Validation modes are separate and explicit:

1. **Static:** parse the supported Kotlin shape; verify imports, artifacts, known symbols, removed
   names, rules, and dependency requirements against the generated bundle.
2. **Compile:** place only the submitted snippet and declared resources into a fixed harness pinned
   to an accepted JDK, Kotlin, AGP, Android SDK, and ViewCompose version lane; compile without
   executing the inspected project's build logic.
3. **Render:** invoke the accepted Preview runner adapter with bounded configuration, time, memory,
   output size, and diagnostics.
4. **Compare:** evaluate declared structure, text, resources, semantics, accessibility, geometry,
   and only then pixels or vision similarity where applicable.

Passing a shallower mode never implies a deeper mode passed. Results include mode, status, stable
diagnostic code, severity, source span, relevant artifact/version, suggested next action, elapsed
time, cache status, and truncated safe details.

### Untrusted source and project boundaries

- `validate_code` never evaluates arbitrary Gradle scripts, plugins, annotation processors, shell
  commands, or project tests. True compilation uses a tool-owned harness and dependency allowlist.
- `analyze_project` is read-only by default, requires an explicit normalized project root, rejects
  symlink escape and path traversal, observes file/count/size/time limits, ignores secrets and
  build output by policy, and returns findings rather than silently changing files.
- A later migration write mode produces a patch plan first. Applying changes remains an explicit
  client action and preserves unsupported regions rather than deleting them.
- Caches are content-addressed, version-scoped, bounded, evictable, and never mix results across
  tool schema, framework version, SDK, or configuration lanes.
- Network access is absent from the deterministic knowledge, validation, compilation, render, and
  conversion core. Optional provider or remote-asset access is a separately disclosed adapter.

### Design IR is tooling-owned

Conversion uses a tooling-only, versioned Design IR rather than runtime `VNode`, renderer nodes, or
Android `View` instances. The minimum model covers node kind, layout relationship, typed property,
modifier, resource reference, semantic role, event placeholder, state/visibility expression,
source provenance, confidence, unsupported source fragment, and stable identity.

The IR is intentionally more descriptive than the runtime tree: it must represent incomplete
source, design tokens, uncertain visual inference, resource and style indirection, and migration
work that cannot safely become executable behavior. IR schema compatibility is independent of
ViewCompose runtime compatibility.

## Scope and product lanes

| Lane | Included outcome | Explicit boundary |
| --- | --- | --- |
| Foundation MVP | Phases 0–3: knowledge bundle, `llms.txt`, validator/compile/render foundations, local CLI, MCP, and client-neutral skills | No automatic XML, Compose, screenshot, or Figma conversion claim |
| Migration MVP | Phase 4 XML subset through IR, code generation, compile/render verification, and migration report | Unsupported custom Views, data binding expressions, behavior, and call-site rewrites remain explicit |
| Source expansion | Phase 4 bounded Compose subset and deterministic ViewCompose analysis | No promise of arbitrary Kotlin or semantic parity |
| AI-native visual tooling | Phase 5 prompt/screenshot/Figma adapters plus measurable repair loop | No provider dependency in framework runtime and no pixel match as sole correctness proof |
| Stable product | Phase 6 compatibility, packaging, security, operations, longitudinal metrics, and reviewed product language | “AI-first” claim remains gated by evidence |

## Phase 0 — Contract, evaluation, and security freeze

### Purpose

Define what the system is, what it may access, how correctness is measured, and which contracts are
independently versioned before an implementation makes accidental decisions permanent.

### Deliverables

1. An ADR that fixes process isolation, canonical knowledge lineage, Design IR separation,
   provider neutrality, local-first operation, version lanes, cache boundaries, and the relationship
   to ADR-0009.
2. A threat model covering malicious snippets, Gradle/build-script execution, path traversal,
   symlink escape, dependency substitution, resource bombs, zip bombs, image bombs, prompt
   injection in project text, credential disclosure, cache poisoning, denial of service, and unsafe
   patch application.
3. Versioned schemas for the AI Knowledge Bundle, requests, structured diagnostics, evidence, and
   Design IR compatibility policy. Freeze names only after capability review.
4. A checked-in evaluation corpus with positive and negative fixtures for API retrieval,
   nonexistent APIs, overload/default selection, dependencies, lifecycle/resource/layout mistakes,
   compilable UI, failed compilation, Preview render, project analysis, and adversarial paths.
5. Separate future fixture sets for XML migration, Compose mapping, screenshot reconstruction, and
   Figma import, even though later phases activate their gates.
6. Metric definitions and initial thresholds for retrieval accuracy, fabricated-symbol rejection,
   compile success, diagnostic precision/recall, false positives, render success, semantic and
   visual similarity, latency, memory, cache effectiveness, and unsupported-case honesty.
7. Stable `capability_id`, Q level, applicable contract fields, API/documentation impact
   dispositions, and module ownership for every new public/protected or application-facing tooling
   surface.

### Acceptance gate

- Every metric names its corpus, denominator, version/configuration lane, threshold, command, and
  evidence owner; aggregate percentages cannot hide unsupported or failed categories.
- The threat model has automated negative-test owners and an explicit residual-risk decision.
- Tool, bundle, IR, and framework versions can vary independently without an unspecified fallback.
- No MCP or converter implementation begins until this phase is accepted.

### Acceptance evidence (2026-08-29)

Phase 0 is complete at source revision `1de3ceaa` plus this implementation slice. ADR-0009's AI
tooling invariants accept the canonical lineage, provider/runtime isolation, cumulative evidence,
untrusted execution, and Design IR boundary. `tools/ai/` freezes version, Q-level, metric, and
threat contracts in five JSON Schemas, five capability IDs, 17 metrics, 14 evaluation cases, and 11
fixture-backed cases.

The following fresh commands passed on macOS using Android Studio JBR 21.0.10:

```text
npm --prefix tools/ai run verify
./gradlew -p tools/viewcompose-quality-build test --console=plain
./gradlew verifyAiToolingContracts verifyDocumentationStructure \
  verifyDevelopmentToolingIsolation verifyViewComposeReleaseIntent --console=plain
```

The Node suite passed 4/4 tests. The compiled quality-build suite passed, proving
`verifyAiToolingContracts` is registered and owned by `qaQuick`. The combined root gate passed 22
tasks, verified 131 canonical English and 127 current Chinese documents, reported zero Governance
V2 issues, zero release artifacts, and no development-tooling isolation violation.

Comparison context: the baseline had no AI contract gate, schema, metric denominator, or adversarial
corpus; the accepted slice adds all four without a production artifact or release-runtime change.
Normalized runtime and accuracy change are not applicable because Phase 0 intentionally adds no
query, compiler, renderer adapter, converter, or model execution. Conclusion: **improved** contract,
security, and reproducibility readiness with **no material runtime change**. Limitations: the local
Schema validator implements only the frozen subset used by these contracts, future-phase fixtures
are contract denominators rather than passing implementation results, and no AI-facing product
capability is claimed yet. Next action: Phase 1 canonical knowledge generation.

## Phase 1 — Canonical AI Knowledge and discovery

### Purpose

Turn the repository's existing structured documentation into one deterministic, version-aware
machine contract instead of adding another handwritten API reference.

### Deliverables

1. Extend the canonical generator to emit a versioned bundle containing at least:
   - a manifest with schemas, framework/artifact versions, source revision, fingerprints, and
     compatibility;
   - exact application-facing symbol names, owners, declarations/signatures, defaults where
     deterministically available, artifacts, dependency coordinates, deprecation/removal data, and
     canonical source links;
   - capability summaries, rules, lifecycle/platform constraints, related documents, and compiled
     sample metadata plus source regions;
   - compact deterministic search fields and stable IDs suitable for local retrieval.
2. Publish a compact root discovery response at `/llms.txt` that identifies ViewCompose, supported
   version lanes, canonical documents, common rules, bundle locations, and tool entry points without
   duplicating the entire Reference.
3. Provide an optional fuller machine document or downloadable bundle for clients that cannot read
   structured resources. Its size and website budget are explicit and gated.
4. Add schema validation, deterministic ordering, duplicate/stale-link detection, generated-file
   freshness checks, source-fingerprint checks, and golden tests.
5. Add a human-readable guide explaining version selection, evidence levels, unsupported behavior,
   and how AI clients should cite retrieved capability and sample IDs.

### Acceptance gate

- Repeated generation from the same source is byte-for-byte identical.
- Every current capability resolves to a real artifact, canonical source, compiled sample, and
  supported version lane; removed or non-executable examples cannot appear as current copyable code.
- A source, API, sample, artifact, or governance change that should affect the bundle fails CI until
  regenerated.
- The hosted documentation size, link, language, and translation gates remain within accepted
  budgets.

### Acceptance evidence (2026-08-29)

Phase 1 is complete. Generator source revision
`7af858dca1aacf1241106db46021c65fcffa3715` produces bundle fingerprint
`ee1765176164201252fe4f3c0b9839a26ee1d87def028255ae2fc435c6594ec1`. The 1,169,945-byte local
bundle contains 30 artifacts, 77 capabilities, 537 symbols resolved to exact source declarations,
209 registered samples, and 10 reviewed rules. Its compact hosted `llms.txt` is 2,646 bytes; the
173,728-byte text fallback and structured JSON/JSONL stay outside the deployed site.

Fresh Android Studio JBR 21 verification passed:

```text
npm --prefix tools/ai run verify                         # 7/7 tests
npm --prefix tools/ai run verify:knowledge               # exact fingerprint/revision
./gradlew -p tools/viewcompose-quality-build test         # compiled task ownership tests
./gradlew verifyAiToolingContracts verifyAiKnowledgeBundle \
  verifyDocumentationStructure verifyDevelopmentToolingIsolation \
  verifyViewComposeReleaseIntent                         # 23 tasks
./gradlew verifyCompleteViewComposeApiDocs                # 6/6 groups, 9m 1s cold
npm --prefix website run build                            # 37.4s wrapper
```

The root gate reported zero Governance V2 issues, zero release artifacts, and no tooling-isolation
violation. The production site retained 133 immutable API versions and manuals, 133 Chinese
fallback routes, two search indexes, 526 audited pages, 30 redirects, 6.7/8.0 MiB JavaScript,
650/768 KiB maximum JavaScript, and 112/128 KiB CSS. Exact output was 491,946,739 bytes, including
49,175,846 non-API bytes against the unchanged 49,178,214.4-byte ceiling (2,368.4 bytes headroom).

Comparison context: a same-corpus candidate with a separate bilingual AI ADR route produced
49,553,310 non-API bytes. Consolidating its boundary into ADR-0009's machine-readable invariants
and the executable plan/contracts reduced output by 377,464 bytes (`-0.7617%`) without weakening
the decision or raising the ceiling. The result is **improved**
for deterministic AI discoverability and **no material runtime change** because only downstream
tooling and documentation changed. The hosted representation result is **mixed**: all public site
gates pass, but headroom is nearly exhausted.

Limitations: this bundle supports only exact `current-source`; released-version bundles, static
validation, compilation, rendering, project analysis, CLI/MCP, conversion, and model adapters do
not exist yet. KDoc summaries are emitted only when deterministic source adjacency is available,
and registered non-executable samples remain evidence records rather than copyable code. The next
phase must preserve the bundle as its only symbol source and must recover site headroom before
publishing another large route.

## Phase 2 — Validation, compilation, render, and analysis foundations

### Purpose

Create the evidence-producing core before exposing a broad protocol surface.

### Deliverables

1. A generated validator index derived from the Phase 1 bundle, not a handwritten symbol list.
2. Deterministic static rules for unknown/removed APIs, missing artifacts or imports, invalid common
   nesting, modifier misuse, units, lifecycle/effect hazards, accessibility, touch targets,
   resource use, View retention, unnecessary View creation, and bounded performance risks. Each
   rule has a stable code, severity, documented scope, positive/negative fixtures, and false-positive
   budget.
3. A hermetic Kotlin/Android snippet compiler with pinned toolchain lanes, dependency allowlist,
   resource fixture support, content-addressed cache, time/memory/output limits, cancellation, and
   normalized compiler diagnostics.
4. A Preview adapter that accepts a compiled target and bounded device/theme/locale/font-scale
   configuration, then returns PNG, layout/render tree, source locations, structured diagnostics,
   and evidence metadata.
5. A read-only project analyzer that inventories ViewCompose versions, artifacts, imports,
   capability usage, migrations, deprecated/unknown names, samples, and configuration without
   executing project build logic.
6. One local internal CLI used by tests and later transports. The validation core does not depend
   on MCP types.

### Implementation evidence — static and project-safety slice

The first Phase 2 slice now derives a 537-entry validator index directly from the accepted Phase 1
bundle and returns the frozen tool-result envelope. It rejects governed symbols used through an
unavailable import or receiver, requires an explicit `contentDescription` decision for the
ViewCompose `Image` component, masks Kotlin strings and nested comments before rule matching, and
keeps source spans stable. It deliberately does not infer that a supporting public type is absent
only because that type lacks an independent Governance V2 capability entry.

The read-only analyzer accepts one canonical absolute root, rejects path traversal, symbolic links,
requested build execution, and limits beyond fixed hard caps. It excludes common build outputs and
secret-bearing files, bounds file count, bytes, depth, time, and response data, and never executes
the inspected project's Gradle logic. `verifyAiStaticTooling` runs the Phase 2 static/security
corpus from `qaQuick`.

On 2026-08-29, Node 25.6.0 completed 20/20 AI-tooling unit tests in 1.25 seconds and the separate
Phase 2 runner passed 5/5 currently applicable static, project-analysis, and security corpus cases.
The compiled quality-build plugin suite and root `verifyAiStaticTooling`,
`verifyAiToolingContracts`, `verifyAiKnowledgeBundle`, and
`verifyDevelopmentToolingIsolation` tasks also passed. The normalized pass rate is 100%; no prior
Phase 2 implementation existed, so a latency or accuracy delta is not applicable. The conclusion
is **improved** deterministic rejection and project-safety evidence with **no material runtime
change**, because the implementation and gate remain downstream tooling.

Limitations: the static slice is not a Kotlin type checker, its initial rule family is intentionally
narrower than the complete deliverable list, and the project analyzer does not yet resolve
dependencies or produce migration findings. At this slice boundary, compilation, rendering,
cancellation, cache behavior, and internal CLI evidence were pending; the compiler slice below now
closes the applicable compilation, cancellation, and cache requirements. Static rules expand only
when labeled positive and negative fixtures preserve the frozen false-positive budget.

### Implementation evidence — pinned compiler slice

The second Phase 2 slice adds a non-published `:tools:ai-compiler-harness` Android library and a
provider-neutral adapter for the fixed
`current-source/jdk-21/agp-9.1.1/kotlin-2.2.10/android-36/jvm-11` lane. Requests cannot select a
Gradle task, project, script, dependency coordinate, repository, or output directory. The current
allowlist contains only `viewcompose-ui-foundation`; Gradle runs offline with bounded heap, workers,
time, and captured output after CI explicitly resolves that fixed classpath. Static validation must
pass before the process starts, and only successful compilation with bounded, re-fingerprinted class
files advances evidence from `static` to `compiled`.

The content-addressed request key includes source, sorted artifacts, compiler lane, and the accepted
Knowledge Bundle fingerprint. Inputs are create-once, concurrent identical requests use one lock,
and a cache hit is accepted only when its record and current class-file fingerprint agree. Stable
outcomes cover invalid selections, lane mismatch, compiler diagnostics, timeout, cancellation,
captured-output limits, missing/unsafe output, start failure, concurrent work, and poisoned inputs or
caches without exposing the tool-owned absolute request path.

The canonical compiled sample contains test-source helpers that intentionally rely on module friend
paths, so the compile corpus uses a consumer-form extraction of its `ProfileSummary` example instead
of weakening the harness into a test-module compiler. A cold accepted run completed in 11,775 ms and
produced two class files totaling 3,575 bytes with fingerprint
`9877c5a41372f6a77423071dc79cad680daa6febb3f7621cf2b1d755d9481acb`; an integrity-checked repeat
returned the same fingerprint in 5--12 ms. Node 25.6.0 passed 29/29 tooling tests, including real
child-process output, timeout, and cancellation enforcement plus selection, traversal, symbolic-link
output, cache tampering, concurrent-request, and normalized-diagnostic cases. The independent
compiler corpus passed 1/1. This is **improved** executable type-resolution evidence with **no
material runtime change** because the harness is non-published, downstream tooling and is absent
from application dependency graphs.

Limitations: this slice compiles one Kotlin file against UI Foundation and does not yet support
Android resource fixtures or the remaining artifact combinations. A cold result is local macOS/JBR
21 evidence rather than a cross-host latency distribution. At this slice boundary, the next action
was to adapt the existing Layoutlib Preview protocol; the Preview slice below now closes that work.
Both paths still need one internal CLI before compiler lanes widen.

### Implementation evidence — Preview adapter slice

The third Phase 2 slice adapts the existing protocol instead of adding a second renderer. Its first
fixed lane discovers the compiled `samples.counter.CounterPreview` target and selects only a variant
whose declared theme, locale, viewport, density, font scale, and layout direction exactly match the
bounded request. The request cannot choose a project, task, source path, worker class, dependency,
repository, or output path. Both discovery and render use fixed offline Gradle plans on JDK 21; the
result records the Preview compiler lane and
`current-source/preview-protocol-1/paparazzi-2.0.0-alpha05/layoutlib-16.2.1` render lane.

`rendered` evidence requires protocol, module, build fingerprint, entry point, source containment,
response correlation, and exact descriptor/variant identity. Image and tree paths must remain in the
canonical content-addressed directory with no symbolic-link segment. The adapter bounds catalog,
response, PNG, tree, and process output independently; verifies PNG signature/chunk structure and
dimensions; parses the render tree; hashes both artifacts into one output fingerprint; maps
structured Preview diagnostics without absolute paths; and re-runs all artifact checks on a cache
hit. Malformed or replaced cache output fails closed.

The accepted current-source render produced the inspected 1,079 x 2,339 PNG of 25,755 bytes and a
121,271-byte render tree with zero diagnostics. Its image/tree fingerprint is
`bb7eba4f51d1aa4f788b0991b7c8635815d6943c374978b685f92619420841d0`; repeated integrity-checked
corpus runs returned the same fingerprint in 9,809--11,481 ms including isolated Gradle discovery. The
worker response itself reported 220 ms render duration after a 2,315 ms Layoutlib setup. Node 25.6.0
passed 34/34 tooling tests, including target/configuration rejection, inherited-property selection,
source escape, symbolic-link artifacts, cache replacement, timeout, cancellation, and structured
worker-failure normalization. The render corpus passed 1/1. This is **improved** executable visual
evidence. Full `qaPreview` passed 1,216 tasks (363 executed and 853 up-to-date), and documentation,
translation, release-intent, and development-tooling-isolation gates passed. There is **no material
application-runtime change** because the adapter remains downstream and the Preview process is
activated only by an explicit tooling request.

Limitations: the current allowlist contains one target with its light/dark variants and `en-US`
configuration, not arbitrary application builds or a visual-comparison claim. The local macOS/JBR
21 measurements are not a cross-host latency distribution, and a matching render does not prove
interaction behavior. The internal CLI and project-analysis closeout below reuse this fixed lane;
later phases may widen it only with independent corpus evidence.

### Implementation evidence — project findings and internal CLI closeout

The final Phase 2 slice expands the read-only analyzer from inventory signals into bounded facts
derived from the accepted Knowledge Bundle. It recognizes exact ViewCompose Maven and project
coordinates, declared/current-bundle versions, governed and supporting imports, owning artifacts,
capability usage, Android SDK declarations, Preview sources, and Android XML or Jetpack Compose
migration candidates. Unknown namespaces and artifacts, imports whose owning artifact was not
declared in the inspected files, and exact dependency versions outside the current-source bundle
produce stable warnings. Direct secret targets and malformed exclusion policies now fail before
traversal. The analyzer still never invokes Gradle, resolves a plugin, follows a symbolic link,
writes source, or treats a regex-derived candidate as a proven migration.

The provider-neutral internal CLI reads one frozen request envelope from stdin, requires the exact
Knowledge Bundle lane and identity, propagates mandatory input/output/time limits, and dispatches
the same static validator, compiler, Preview, and project analyzer used by their direct corpus
runners. Stdout contains one schema-validated result only; malformed envelopes fail on stderr
without partial JSON. Unsupported tools and framework drift fail before adapter invocation. This is
an internal parity seam, not yet a supported MCP or public distribution contract.

On 2026-08-29, Node 25.6.0 passed 43/43 tooling tests in 1.30 seconds, including internal CLI
process-boundary, identity, dispatch, limit, and malformed-envelope cases plus the expanded project
facts and secret-target cases. The independent static/security corpus passed 5/5, compiler corpus
passed 1/1, and render corpus passed 1/1 through the same adapters. The exact CLI also returned
schema-valid static, compiled, rendered, and project-analysis results in the fixed current-source
lane. Repository documentation, release-intent, quality-build, development-tooling-isolation, and
Phase 2 gates passed; root `qaQuick` completed 2,271 tasks (1,200 executed and 1,071 up-to-date) in
6 minutes 40 seconds. Compared with the inventory-only slice, analyzable framework facts expanded
without changing the fixed traversal or process-execution boundary; the conclusion is **improved**
project evidence and transport consistency with **no material runtime change**.

Limitations: Gradle/TOML discovery is deliberately syntax-bounded and does not resolve version
catalog aliases, convention plugins, transitive dependencies, arbitrary expressions, or released
Knowledge Bundles. Supporting imports without their own governed symbol remain facts rather than
invented API entries. The CLI is repository-internal, has no packaging or compatibility promise,
and exposes no retrieval or MCP transport yet. Compiler resource fixtures, additional artifact
lanes, arbitrary Preview targets, deprecation/removal findings, richer typed analysis, and
cross-host performance distributions remain future work. The next action is Phase 3 deterministic
retrieval over this accepted core, followed by CLI/MCP parity rather than duplicated transport
logic.

### Acceptance gate

- The fabricated-API corpus is rejected at the frozen threshold, while valid compiled samples have
  no unexplained static false positives.
- Every “valid” compile result comes from the hermetic compiler; parser-only or symbol-only success
  is never labeled compiled.
- Golden render fixtures reproduce declared output and diagnostics for every supported lane.
- Adversarial project, path, dependency, resource, timeout, cancellation, and cache tests pass.
- Release artifacts and an inactive application process show zero AI-tooling dependency and no
  recurring work, verified under ADR-0009.

## Phase 3 — CLI, MCP, and Agent workflows

### Purpose

Expose the proven local capabilities through interoperable interfaces while keeping one core and
one knowledge source.

### Initial MCP surface

| Tool or resource | Contract |
| --- | --- |
| `get_api_reference` | Resolve exact symbol/capability/artifact/version facts and canonical links |
| `get_component_reference` | Return one component's parameters, defaults, modifiers, rules, sample, and dependency requirements |
| `search_component` | Deterministic local search with stable ranked results and explicit version filter |
| `get_sample` | Return a compiled source region plus build target, imports, artifacts, capability ID, and fingerprint |
| `validate_code` | Run requested static and/or hermetic compile modes and return structured diagnostics/evidence |
| `render_preview` | Render one allowlisted compiled target through the Preview adapter |
| `diagnose_layout` | Interpret render/layout tree and structured diagnostics using deterministic rules |
| `analyze_project` | Run the bounded, read-only Phase 2 project inventory and findings pipeline |

`generate_ui`, `debug_issue`, and automatic repair are initially client workflows over these
deterministic tools, not opaque model calls inside the server. This keeps providers replaceable and
makes every step inspectable. Conversion tools enter only with Phase 4 evidence.

Before implementing `diagnose_layout`, the Phase 3 corpus freezes one accepted Preview protocol v1
snapshot containing non-expected partial clipping and intentional text ellipsis. The associated
`layout.diagnosis-exactness` metric requires an exact stable-code match ratio of 1.00. The tool may
interpret renderer-produced facts, but it may not infer geometry from source or silently add
model-derived findings to that denominator.

Before publishing consumer skills, the corpus also freezes five distinct workflows for exact API
reference, screen creation, review, layout debugging, and delivery validation. Their required and
conditional tools, minimum and maximum evidence, mutation authority, shared stop condition, and
exact `current-source` selection form the denominator for `workflow.contract-completeness`, whose
required exact-match ratio is 1.00.

Before packaging, the corpus freezes one dependency-free local npm distribution with eight tools,
five skills, two explicit executable modes, SHA-256 archive and file integrity, SPDX 2.3 and MIT
license inventory, repeat-build identity, offline installation and uninstallation, and installed
stdio checks for every supported modern and legacy MCP version. Compile and Preview execution must
remain source-bound and require an explicit matching ViewCompose checkout; packaging cannot relabel
their evidence as standalone. The four distribution metrics require zero archive mismatches and
exact lifecycle, inventory, and protocol ratios of 1.00.

### Additional deliverables

1. A local stdio MCP server with per-request version metadata, capability discovery, explicit
   version selection, legacy-client compatibility, structured errors, cancellation, progress,
   output limits, safe logging, and no default network listener.
2. A stable CLI over the same service/core for CI, debugging, and clients without MCP.
3. Client-neutral consumer skills for creating a screen, retrieving API, reviewing code, debugging
   layout, and validating before delivery. Contributor workflows remain separate from framework
   consumer workflows.
4. Thin documented adapters for supported coding agents. Repository `AGENTS.md` continues to govern
   contribution; provider-specific root files are not added merely as aliases.
5. Packaging, checksums, SBOM/license review, installation/uninstallation, protocol compatibility,
   offline operation, and a minimal end-to-end example in CI.

### Implementation evidence — deterministic retrieval and CLI slice

The first Phase 3 slice adds one shared retrieval core over the accepted Knowledge Bundle. Before
building indexes, it verifies the exact seven-file manifest set, every byte count and SHA-256,
parsed record counts, and the aggregate bundle fingerprint. It exposes fixed input schemas for
`get_api_reference`, `get_component_reference`, `search_component`, and `get_sample`; the internal
CLI dispatches those same functions through the Phase 0 request/result envelope. No retrieval path
reads canonical source outside the bundle or performs network, Gradle, model, or project work.

Exact API retrieval distinguishes symbol, capability, and artifact identities while preserving the
artifact's current published version separately from a capability's recorded version state.
Component retrieval parses overload parameters and defaults, requires explicit disambiguation for
receiver families, attaches artifact/capability ownership, includes the declared compiled or
non-executable sample, and labels signature-derived rule applicability. Sample retrieval never
presents an architecture outline as compilable code. Ranked search supports bounded artifact,
artifact-version, capability, and kind filters, stable lexical scoring, and deterministic tie
breaks in the exact `current-source` lane.

On 2026-08-29, Node 25.6.0 passed 52/52 AI-tooling tests in 1.42 seconds. The two frozen retrieval
cases both passed: the remembered stale `Column` package still resolved the governed current symbol
at rank 1, and the layout intent resolved `modifier.layout` at rank 1. Top-five recall was 1.00
against the frozen 0.95 threshold; exact-symbol reciprocal rank was 1.00 against the 1.00 threshold.
The compiled quality-build suite and root `verifyAiRetrieval` task passed, and the gate is now owned
by `qaQuick`; the root lifecycle completed 2,272 tasks (2,182 executed and 90 up-to-date) in 11
minutes 57 seconds after the local incremental cache was invalidated. Compared with the bundle-only
baseline, retrieval changed from unavailable to deterministic, integrity-checked, and measurable;
the conclusion is **improved** Agent-facing knowledge access with **no material runtime change**
because the code remains downstream tooling.

Limitations: this first ranker is lexical and primarily serves canonical English names and terms;
it does not claim fuzzy, multilingual, embedding, or model-semantic retrieval. Only the exact
`current-source` bundle is selectable. Rule applicability is labeled as general, component, or
signature-derived rather than inferred as typed program behavior. The CLI is still internal and
unpackaged; at this slice, MCP transport parity and client workflows remained pending. The MCP
slice below closes the transport-parity requirement without changing the ranker's stated limits.

### Implementation evidence — dual-era stdio MCP slice

The second Phase 3 slice freezes a local stdio contract and one seven-tool catalog shared by the
CLI and MCP. It implements the current MCP `2026-07-28` stateless model: `server/discover` reports
the supported versions and fixed capabilities, while every modern request independently declares
its protocol version and client capabilities. It also supports the exact `2025-11-25` legacy
`initialize`/`initialized` lifecycle for clients still migrating, but never infers a downgrade or
uses legacy connection state for modern requests. The public render tool is accurately named
`render_preview` because this slice selects an allowlisted compiled Preview target; it does not
claim arbitrary snippet rendering.

Each newline-delimited stdio call creates the same immutable provider-neutral request consumed by
the internal CLI. Results are returned unchanged as MCP structured content and serialized text;
semantic parity excludes only elapsed wall-clock measurement. The server keeps a deterministic
tool order, JSON Schema 2020-12 input contracts, stable protocol versus actionable tool errors,
opt-in bounded progress, cancellation propagation into compiler/Preview child processes, and the
MCP rule that a cancelled call emits no later response. It rejects messages above 4 MiB, limits
concurrent calls to four, bounds tool output to 1 MiB before transport duplication, writes only MCP
JSON to stdout, logs no request content, and opens no network listener.

On 2026-08-29, Node 25.6.0 passed 65/65 AI-tooling tests in 1.35 seconds, including modern discovery,
unsupported-version recovery, deterministic listing, legacy lifecycle, CLI/MCP parity, tool versus
protocol errors, progress, cancellation, external abort propagation, catalog bounds, and an actual
stdio subprocess. The standalone Phase 3 MCP corpus reported seven tools and zero semantic
mismatches for `modifier.layout`; the compiled quality-build suite and root `verifyAiMcp` gate also
passed. The root `qaQuick` lifecycle executed the new gate and completed 2,273 tasks (2,183 executed
and 90 up-to-date) in 6 minutes 42 seconds. Compared with the retrieval-only baseline, a local Agent
can now discover and invoke the same accepted core over two explicit protocol eras with no semantic
fork; the conclusion is **improved** interoperability with **no material runtime change** because
the server remains downstream development tooling.

Limitations at this slice: only stdio was supported; HTTP, authentication, subscriptions,
resources/prompts, released-version Knowledge Bundles, installable packaging, checksums/SBOM, and
client adapters were not claimed. The seven-tool list intentionally omitted `diagnose_layout`
until the following slice could consume accepted Preview tree evidence. `generate_ui`, repair, and
conversion remained inspectable client workflows or later phases, not opaque server-side model
calls.

### Implementation evidence — deterministic layout diagnosis slice

The third Phase 3 slice adds `diagnose_layout` as the eighth shared CLI/MCP tool. A request selects
the same fixed Preview target and bounded configuration as `render_preview`; it cannot provide a
Gradle task, arbitrary file, render tree, image, or project path. The adapter renders or accepts a
verified cache entry, derives the only valid content-addressed tree path from repository-owned
target metadata, and then rechecks every path segment, byte count, SHA-256, render lane, target,
variant, and output identity before interpretation. Cache mutation between render and diagnosis
therefore fails closed.

The interpreter maps only Preview protocol v1 facts already measured after Android layout:
zero-size nodes, partial or full clipping, intentional container clipping, text ellipsis, clipped
text content, bounds, metrics, node identity, and matching source call sites. It does not inspect
pixels or source code, apply model judgment, or invent overlap, accessibility, touch-target, or
design-intent findings. Unknown kinds and malformed geometry are rejected instead of guessed. At
most 100 findings are returned with an explicit truncation diagnostic; a clean result means only
that the renderer emitted no structured layout diagnostic or warning.

On 2026-08-29, Node 25.6.0 passed 71/71 AI-tooling tests in 1.33 seconds. The frozen clipping and
ellipsis fixture produced both expected stable codes in deterministic severity/geometry order,
for an exact-match ratio of 1.00 against the required 1.00 threshold. The MCP parity verifier
reported eight tools and zero semantic mismatches. An end-to-end call over the real Counter target
revalidated the existing render cache, preserved output fingerprint
`bb7eba4f51d1aa4f788b0991b7c8635815d6943c374978b685f92619420841d0`, and returned a clean result
with zero layout findings. The compiled quality-build suite passed all tests in 10 seconds. The root
`qaQuick` lifecycle executed the new gate and completed 2,274 tasks (2,184 executed and 90
up-to-date) in 6 minutes 39 seconds. Compared with raw `render_preview` output, Agents now receive
bounded, source-aware repair facts without parsing renderer internals; the conclusion is
**improved** layout debuggability with **no material runtime change** because the implementation
remains isolated downstream tooling.

Limitations: only one allowlisted Counter Preview target and one labeled layout-diagnosis fixture
are accepted today. The tool reports renderer-owned layout facts, not arbitrary snippet rendering,
pixel comparison, accessibility conformance, overlap detection, or automatic repair. At this
slice, the next action was to publish the client-neutral consumer workflows implemented below.

### Implementation evidence — client-neutral consumer workflow slice

The fourth Phase 3 slice publishes five independently installable `SKILL.md` entrypoints for exact
API reference, screen creation, review, layout debugging, and delivery validation. They orchestrate
the accepted eight-tool core instead of copying framework APIs into prompt text. A machine-readable
manifest freezes each workflow's required and conditional tools, minimum and maximum evidence,
mutation policy, exact version selection, and repeated-diagnostic stop condition against the
pre-implementation corpus.

The entrypoints keep retrieval separate from proof: screen creation requires hermetic compilation;
review remains read-only unless a fix is also requested; layout debugging can use only an
allowlisted Preview that covers the affected code; and validation cannot turn a static pass into a
compiled or rendered claim. No provider-specific metadata or root alias was added, and no skill
grants project writes beyond the user's request. The deterministic gate rejects unknown tools,
evidence upgrades above rendered, manifest or folder drift, symbolic-link/path escape, oversized
entrypoints, missing safety boundaries, local absolute paths, and provider-specific instructions.

On 2026-08-29, the skill-creator structural validator accepted all five entrypoints. Node 25.6.0
passed 73/73 AI-tooling tests in 1.30 seconds, and the frozen workflow gate matched 5/5 contracts
for an exact-match ratio of 1.00 against the required 1.00 threshold. The compiled quality-build
suite passed all tests in 8 seconds. The root `qaQuick` lifecycle executed the new gate and
completed 2,275 tasks (2,185 executed and 90 up-to-date) in 6 minutes 49 seconds. Compared with ad
hoc prompting, the repository now provides bounded, evidence-aware consumer procedures that
preserve review versus mutation authority; the conclusion is **improved** workflow reproducibility
with **no material runtime change** because the skills and verifier remain downstream tooling.

Limitations: deterministic structure checks prove contract presence, not that every model/client
will follow the workflow correctly. No provider adapter or provider-specific behavior claim is made;
the entrypoints remain portable protocol-level workflows. At this slice, the next action was the
packaging and compatibility implementation recorded below.

### Implementation evidence — reproducible distribution and compatibility slice

The fifth Phase 3 slice packages `@viewcompose/ai-tooling` version `0.1.0` as a local npm tarball
with no runtime dependency. Its exact 34-file allowlist contains the eight-tool CLI/MCP core, five
skills, immutable Knowledge Bundle, two required schemas, MIT license, deterministic distribution
metadata, SPDX 2.3 package record, and reviewed empty third-party runtime inventory. The packager
rejects symbolic links, non-regular inputs, path escape, file-set drift, dependency drift, broad
output roots, and disagreement between staged and npm-packed file lists. Every file receives a
SHA-256 record; the archive and external manifest receive an unsigned `SHA256SUMS` sidecar.

The installed executables resolve npm-created symbolic links before entering the CLI or stdio
server. Retrieval, static validation, and project analysis are standalone. Compile and Preview
remain explicitly source-bound through `VIEWCOMPOSE_SOURCE_ROOT`, the pinned JDK/Android/Gradle
lane, and the existing evidence contracts. A configured checkout must contain regular wrapper and
settings files plus the exact Knowledge Bundle source revision in its Git ancestry; mismatch fails
before Gradle, and the package never upgrades those modes implicitly. The verification lifecycle
builds twice, compares full archive bytes, installs from the local tarball with npm offline mode and
an unreachable registry, revalidates every installed byte, retrieves `Column` and its compiled
sample, rejects one mismatched checkout, compiles the frozen UI Foundation example, exercises
modern `2026-07-28` and legacy `2025-11-25` stdio discovery, uninstalls offline, and checks that
package and binary paths are absent.

On 2026-08-29, both clean builds produced the same 236,152-byte archive with SHA-256
`286d97e2f88b9827b45f6bca9c7c2f79c9eb63e859b8bb112009b61835a0eb70`. The offline lifecycle,
SPDX/license inventory, and both installed protocol lanes each matched their complete frozen
denominator for exact-match ratios of 1.00; the installed compile example returned fingerprint
`9877c5a41372f6a77423071dc79cad680daa6febb3f7621cf2b1d755d9481acb`. Node 25.6.0 passed
75/75 AI-tooling tests in 1.40 seconds, the compiled quality-build suite passed all tests in 8
seconds, and the combined tooling, distribution, documentation, isolation, and release-intent gates
passed 23 tasks (9 executed and 14 up-to-date) in 35 seconds. Compared with the source-tree-only
baseline, the result is **improved** distribution reproducibility and interoperability with **no
material runtime change** because the complete package and installation gate remain downstream
development tooling. The first root lifecycle attempt exhausted the local disk after 2,214 tasks
and 7 minutes 47 seconds rather than reporting a code failure. After deleting only reproducible
worktree outputs and three incomplete Gradle transforms, the incremental retry passed all 2,276
tasks (325 executed and 1,951 up-to-date) in 3 minutes 50 seconds. After adding the source-checkout
identity rejection, the final lifecycle rerun again passed all 2,276 tasks (263 executed and 2,013
up-to-date) in 3 minutes 51 seconds; disk capacity is therefore an environmental limitation and the
final successful rerun is the accepted root evidence.

Limitations: the artifact is local and unpublished; `SHA256SUMS` is not signed. The evidence covers
Node 25.6.0 on macOS, not every engine-compatible Node release, Windows npm shims, public-registry
installation, upgrade migration, package signing, vulnerability-feed review, or branded client UI.
Compile and Preview still require the matching source checkout and prepared offline toolchain. The
installed end-to-end example proves retrieval, sample lineage, and compilation; installed rendering
continues to rely on the separately accepted source-bound Preview gate. Phase 4 starts with the
Design IR and XML migration contract freeze.

### Acceptance gate

- CLI and MCP produce semantically identical results for the same request and bundle fingerprint.
- The evaluation corpus proves retrieval, validation, compile, render, cancellation, and error
  behavior across every supported client/version lane.
- A clean sample project can ask an agent for a Material 3 screen, retrieve real APIs and samples,
  compile it, render when supported, repair failures, and deliver evidence without a fabricated API.
- The Foundation MVP is not declared complete until security, packaging, documentation, and
  release-runtime isolation gates pass.

## Phase 4 — Design IR, XML migration, Compose mapping, and analysis

### Purpose

Build deterministic migration value on top of the accepted knowledge and validation loop. Android
XML is first because it has explicit structure and resources and addresses an existing migration
need; automatic Compose conversion follows only after explicit semantic mappings exist.

### Contract freeze — Design IR v1 and Android XML layout v1

The 2026-08-29 Phase 4 contract freeze replaces open property bags with ordered typed IR fields for
literals, resources, dimensions, layout dimensions, enums, caller bindings, and preserved
expressions. Every source has a SHA-256 identity; every emitted node requires a source identity,
source span, confidence, and mapping decision. Unsupported fragments require a stable diagnostic,
preserved source, localization, and an explicit blocked or preserved disposition. Node IDs, field
names, modifier kinds, and modifier argument names are unique within their declared scopes.

The first XML subset is intentionally smaller than the eventual Phase 4B target. It accepts only
`LinearLayout`, `TextView`, `EditText`, and `Button`; fixed layout dimensions, one all-edge integer
`dp` padding, literal or unqualified string resources, four input types, and Android IDs. String
resources become explicit caller `String` parameters and remain named in the migration report;
editable state becomes a caller-owned `TextFieldState`. An absent click listener stays absent.
This keeps the first generated source inside the accepted Foundation compiler harness without
inventing an Android resource environment or application behavior.

Custom Views, Data Binding, unknown elements or attributes, unsupported values or namespaces,
`DOCTYPE`/entities, duplicate IDs, malformed XML, and resource-limit violations fail closed with no
Kotlin output. XML-only input cannot establish ViewBinding references or imperative call-site
listeners, so every successful result must carry that review limitation. The frozen denominator is
one four-node login golden with three string resources and one caller state binding, plus custom
View, Data Binding, and unknown-attribute rejection fixtures. Phase 4 begins with 27 total metrics,
22 evaluation cases, 19 fixture-backed cases, and four XML source fixtures; implementation may not
widen this subset silently.

### Contract freeze — Android XML project context v1

The second Phase 4 contract adds project evidence without weakening the accepted XML source subset.
Callers must provide one canonical project root, one project-relative layout, ordered explicit
resource roots, and ordered Kotlin or Java source roots. The resolver remains read-only, offline,
rejects symbolic links and root escape, never executes inspected-project Gradle logic, and never
chooses a variant implicitly. Only default `values` definitions select generation evidence;
qualified definitions are inventory-only. String identities remain preserved with their default
literals recorded as evidence, while finite non-negative `dp`, `sp`, and `px` dimensions may be
resolved without density conversion.

Style support is bounded to explicit unqualified `@style/name` references and explicit parents,
with a maximum 16-entry chain. Inline attributes override the selected style, which overrides its
nearest parent. Only attributes already owned by Android XML layout v1 are accepted. Cycles,
implicit dotted parents, theme attributes, aliases, package/framework resources, missing default
definitions, duplicate same-precedence definitions, formatted strings, plurals, arrays, markup,
and XLIFF fail closed. This resolves reusable declarations without pretending to reproduce AGP
resource merging or Android runtime selection.

The companion call-site inventory scans only declared Kotlin and Java roots and returns stable
locations plus snippet fingerprints for exact layout, ID, and resource symbols; ViewBinding naming,
listener registration, imperative mutation, and adapter assignment remain explicit candidates when
lexical evidence cannot prove ownership. Raw source is not returned. Coverage is always
`bounded-lexical` and completeness is always `not-proven`, so dynamic, reflective, generated,
excluded, and semantically linked code remains mandatory human/agent review work.

The frozen denominator adds one supported five-file project with four resources, two effective
styles, and seven call-site findings, plus style-cycle and theme-attribute rejection projects. The
public context example is byte-equivalent to the supported golden, and every input file, layout,
and source-line finding carries a SHA-256 identity. Phase 4 now has six schemas, 30 metrics, 25
evaluation cases, 22 fixture-backed cases, four base XML fixtures, and three project-context
fixtures. This is a contract-only **improvement** in measurable migration coverage with **no
material runtime or supported-tool behavior change** until the isolated resolver is implemented.

On 2026-08-29, Node 25.6.0 passed all 91 existing AI-tooling tests with the expanded contract, and
the compiled root contract, distribution, and documentation gates passed 21 actionable tasks. Two
package builds remained byte-identical after adding the project-context schema: the 40-file,
250,839-byte archive has SHA-256
`9167e42d60c77c7474e0e72479a01caa460c781d432b080dbf4c601a7882e1a7` and 1,441,250
declared file bytes. The installed offline lifecycle, both MCP eras, the independent compile
example, and the already accepted XML conversion still pass. This is **improved** distributable
contract visibility with **no material behavior change**; the archive remains local, unsigned, and
unpublished, and the new schema does not imply an implemented resolver.

### Implementation evidence — isolated Android XML project context

The accepted resolver canonicalizes one absolute project root and only normalized project-relative
layout, resource-root, and source-root paths. It rejects missing paths, root escape, symbolic links,
unsafe resource XML, style cycles, theme attributes, duplicate same-precedence resources, and
qualified-only resources before returning context. Traversal, file bytes, definitions, style depth,
call sites, and elapsed time all use frozen ceilings. Resource and source discovery is deterministic;
the context fingerprint covers every scanned layout, values file, and Kotlin or Java file in path
order. No project build, plugin, generated source, network client, or Android resource merger runs.

The resolver parses default string and dimension evidence, resolves explicit style-parent chains,
applies inline-over-style precedence, and rewrites only an internal XML copy. Removing the `style`
attribute uses whitespace preservation, inherited attributes are inserted without adding lines, and
dimension references become their bounded literal values; source-node line provenance therefore
remains stable. String references remain resources. The resolved styled-login source then passed the
existing XML-to-Design-IR converter as a vertical `Column`, retained its title string resource, and
introduced no new element mapping.

The read-only source inventory reports exact `R.layout`, `R.id`, and resource references separately
from candidate ViewBinding, listener, and mutation ownership. Each result records path, one-based
position, evidence kind, confidence, migration action, and a hash of the trimmed source line; raw
source is excluded from the context. Dynamic, reflective, generated, excluded, or semantically
related references remain unproven by design.

On 2026-08-29, Node 25.6.0 passed 96/96 AI-tooling tests. The dedicated project-context gate matched
1/1 deterministic golden with four resources, two styles, and seven call sites, plus 2/2
fail-closed unsupported projects. The compiled quality-build suite and new root lifecycle task
passed 18 actionable tasks (6 executed and 12 up-to-date) in 46 seconds. Compared with the frozen
contract, this is **improved** executable project evidence with **no material runtime or supported
CLI/MCP behavior change**. The result still does not reproduce AGP variant merging, resolve themes,
prove call-site completeness, or modify application files. At that slice boundary, styled
compilation and installed project-aware conversion remained pending; the next evidence section
records their completion.

### Implementation evidence — project-aware XML conversion

`convert_xml_to_viewcompose` now accepts exactly one of two schema-selected inputs. Source input
retains the accepted `source`, logical `path`, and explicit `generate` or `compile` behavior.
Project input requires an absolute `projectRoot`, project-relative `layoutPath`, ordered explicit
`resourceRoots`, optional ordered `sourceRoots`, and the same explicit mode. Mixing both forms or
omitting either form fails schema validation. CLI and both MCP protocol eras continue to share the
same catalog and dispatcher.

Before Design IR conversion, project input runs the accepted resolver, uses only its internal
style-expanded XML, and returns its schema-validated context as evidence. The migration report now
records the context fingerprint, resource/style/call-site counts, `not-proven` completeness, and
the complete bounded call-site inventory without raw source. The outer tool may own a 120-second
compile request while its project scan is independently tightened to the frozen 10-second maximum;
the installed modern MCP test caught and fixed this boundary composition before acceptance.

The supported project generates the exact frozen `StyledLoginView` Kotlin golden with three string
parameters, one caller-owned `TextFieldState`, inherited `16.dp` padding, preserved IDs, and seven
review call sites. The dedicated JDK 21 gate matched 1/1 deterministic context and Kotlin golden,
four resources, two styles, seven call sites, 1/1 hermetic compile, and 2/2 fail-closed unsupported
projects. Its identities are context
`f635c856eab177a37aa29f1eb14bd096ca76e8dc0e3a99892574b00f2c90a14e`, Kotlin
`8698ad4f919b8dbbaf92fc2487972d54c599706a0c5024acd192aa9fd741f4fe`, and classes
`e30210ebcf946e11e4b47327504999bed20c918e164713d1cf0102544cc97987`.

On 2026-08-29, Node 25.6.0 passed 100/100 AI-tooling tests and the full Phase 0 contract verifier.
The root project-context and documentation-structure gates passed 20 actionable tasks (6 executed
and 14 up-to-date). The XML consumer Skill now selects explicit project evidence when the scoped
layout is available, preserves standalone pasted-source input, and reports `not-proven` call-site
completeness; its independent validator passed and the workflow gate retained 6/6 exact contracts.
Two clean distribution builds were byte-identical: the 41-file, 261,076-byte
archive has SHA-256
`2118765a51bcd05450e1f0a0a759f1a55521509f79e926e9183b3a7f599d4cf8` and 1,485,644
declared file bytes. Its offline install/uninstall, SPDX/license inventory, both MCP eras,
standalone generation, installed explicit-project generation, and existing compiled examples all
passed.

Compared with the isolated resolver, this is **improved** end-to-end migration evidence and
consumer interoperability with **no material runtime change** because all work remains in the
downstream package and no published Android artifact changed. Limitations remain explicit: the
subset does not emulate AGP variants or resource merging, resolve themes or qualified defaults,
prove call-site completeness, edit host source, render the generated project screen, or establish
visual/accessibility parity. At that slice boundary the next action was a new basic container,
image, accessibility, and resource contract; the following section records that freeze before
implementation.

### Contract freeze — Android XML layout v2

The next compatible XML subset is frozen separately from `android-xml-layout-v1`, so the accepted
login denominator and existing gates remain immutable while implementation is pending. Layout v2
adds only `FrameLayout` mapped to ordered-overlay `Box`, `ImageView` mapped to `Image`, and the common
`android:visibility` attribute. `FrameLayout` accepts the already bounded all-edge padding.
`ImageView` accepts one unqualified `@drawable/name`, one of `fitCenter`, `centerCrop`, `fitXY`, or
`centerInside`, and a content description that is a literal, an unqualified string resource, or
explicit `@null` for decorative content.

Drawable identity is preserved as a caller-owned `ImageSource` parameter instead of inventing an
Android `R` class inside the hermetic compiler. String identities remain caller-owned `String`
parameters. Visible nodes omit a redundant modifier; `invisible` and `gone` map to ViewCompose's
native visibility modifier. An `ImageView` that omits `android:contentDescription` fails closed with
`VC-AI-XML-ACCESSIBILITY-REQUIRED`; the converter may not silently choose decorative semantics.
Unknown scale types, qualified/package resources, source selectors, tint, layout gravity, and
style-supplied v2 attributes remain outside this increment.

The frozen positive denominator is a three-node profile card with one `FrameLayout`, one cropped
image, one gone text node, one drawable binding, two string bindings, exact Design IR v1 provenance,
and exact intended Kotlin. The negative denominator is an image with a drawable source but no
accessibility decision. This expands Phase 4 to 27 evaluation cases, 24 fixture-backed cases, four
base XML v1 fixtures, two XML v2 fixtures, and three project-context fixtures while retaining the
same 30 metrics.

On 2026-08-29, Node 25.6.0 passed 100/100 AI-tooling tests and the expanded Phase 0 verifier. The
compiled root contract and documentation-structure gates passed 20 actionable tasks (6 executed
and 14 up-to-date). Compared with the previous denominator, this is **improved** measurable basic
container, image, accessibility, and resource coverage with **no material tool or runtime behavior
change**: the v2 fixtures and intended goldens are contract evidence only, are not in the installed
runtime package, and are not yet accepted by the parser or generator. The next action is exact
implementation plus hermetic compilation; no broader XML feature may bypass that boundary.

### Implementation evidence — Android XML layout v2

The dependency-free parser now maps `FrameLayout` to ordered-overlay `Box`, maps `ImageView` to
`Image`, and applies non-visible Android visibility as ViewCompose's native visibility modifier.
`@drawable/name` remains a typed IR resource and becomes a caller-owned `ImageSource`; it never
becomes a fabricated numeric resource ID. The four accepted scale types normalize to typed IR and
the exact `ImageContentScale` values. `visible` is omitted, while `invisible` and `gone` retain
distinct layout behavior.

Accessibility is an input contract, not a post-generation lint suggestion. A non-empty literal or
string resource becomes the `Image` content-description argument and image semantic role. Explicit
`@null` remains a decorative image with no image semantics. A missing or empty description returns
`VC-AI-XML-ACCESSIBILITY-REQUIRED`, preserves the localized source fragment, and emits no Kotlin.
Project mode composes the same v2 mapping with explicit resource roots: a temporary project fixture
resolved both string resources, preserved the drawable identity without pretending to merge it,
returned `not-proven` call-site completeness, and generated the same typed function.

On 2026-08-29, Node 25.6.0 passed 106/106 AI-tooling tests. The Design IR gate matched 2/2 schema
goldens, 2/2 deterministic outputs, 7/7 provenance-complete nodes, 2/2 resource denominators, and
4/4 fail-closed unsupported fixtures. The XML gate matched 2/2 Kotlin goldens, 2/2 resource reports,
and 2/2 hermetic compiles. The profile-card Kotlin fingerprint is
`15b15098e92b62bc9730ab7b3f2bde7715596f22069490a18b1e7830ff92ad35`; its class fingerprint is
`6020181fabf964e19c54c2a9a6ff8034657cb89ec338f48c9de25a41b9af04d4`.

The installed distribution generated v2 through both CLI and modern MCP and compiled it through
the matching source checkout. Two clean builds remained byte-identical: the 41-file,
262,894-byte archive has SHA-256
`f1d2724d17073ce6804ec21b40951b73dc68cd12244546c0d1e70514576e8fab` and 1,494,896
declared file bytes. The combined Design IR, XML compile, distribution, and documentation gates
passed 22 actionable tasks (8 executed and 14 up-to-date).

Compared with the contract-only denominator, this is **improved** executable container, image,
accessibility, and resource fidelity with **no material Android runtime change** because only the
downstream tooling package changed. Limitations remain explicit: no visual or device parity is yet
claimed; source selectors, tint, gravity, qualified drawables, style-supplied v2 attributes,
includes, merge roots, and Android resource merging remain unsupported. The next foundational gap
is a frozen explicit-root layout dependency graph for bounded `include`/`merge` expansion; the
resolver must not traverse layout dependencies before that contract exists.

### Contract freeze — Android XML layout dependencies v1

The project-only dependency contract now freezes the missing boundary before any resolver follows
an `include`. Callers must provide one project root, the root layout path, and ordered explicit
resource roots. Only unqualified `@layout/name` references in default `layout/` directories are
selectable; qualified directories remain inventory evidence, the first declared resource root wins,
and duplicate candidates at the same precedence fail closed. The resolver remains read-only,
offline, rejects symbolic links, and never executes Gradle, AGP, resource merging, or automatic
variant selection.

An `include` accepts only its `layout` attribute and preserves an ordinary included root. A `merge`
root is valid only when reached through an `include`; it splices its ordered children into that
position and accepts no semantic attribute beyond the Android namespace declaration. Source-only
conversion containing an `include`, standalone `merge`, missing layouts, cycles, unsupported
include attributes, or any dependency ceiling violation returns a stable fail-closed diagnostic and
no generated Kotlin. The ceilings are 64 layout files, 16 include levels, 256 dependency edges, and
1 MiB of expanded input.

The schema records the selected path and SHA-256 for every layout, ordered include edges with their
original one-based source positions, explicit selection completeness, and a canonical graph
fingerprint. The positive denominator is a three-file screen: its root includes one ordinary
`FrameLayout` profile header and one `merge` action group. Its intended expanded result contains six
IR nodes and preserves one drawable and four string resources. The negative project denominator is
a two-file cycle; the existing screen is also the source-only rejection denominator. This expands
Phase 4 to seven schemas, 32 metrics, 30 evaluation cases, 27 fixture-backed cases, and three frozen
layout-dependency fixtures while retaining four base XML fixtures, two XML v2 fixtures, and three
project-context fixtures.

On 2026-08-30, Node 25.6.0 passed the expanded Phase 0 contract verifier with exact schema, fixture,
edge-position, file-fingerprint, graph-fingerprint, diagnostic, execution-boundary, and ceiling
checks. The schema also entered the installed offline distribution: two clean 42-file package builds
were byte-identical, with a 263,198-byte archive, 1,497,969 declared file bytes, and archive SHA-256
`3425c259291fac19754c15feaf578a56877deb74c1be4ff27eff50b3453fc482`. Offline install/uninstall,
SPDX/license inventory, both MCP protocol versions, the existing compiled sample, and both XML
compile lanes passed under JDK 21. The repository contract and documentation-structure gates passed
20 actionable tasks (6 executed and 14 up-to-date).

Compared with the v2 implementation denominator, this is **improved** measurable project layout
dependency coverage with **no material conversion or Android runtime behavior change**: the graph,
fixtures, metrics, and installed schema freeze intended behavior only. The next action is exact
resolver and expansion implementation plus hermetic compilation; no layout traversal may bypass
this graph contract.

### Implementation evidence — Android XML layout dependencies v1

The project converter now resolves one deterministic dependency graph before mapping any include.
It selects only default `layout/name.xml` files from ordered explicit resource roots, rejects every
symbolic-link segment, hashes each selected raw file, and traverses no qualified directory. The
first root containing an included layout wins. Ordinary included roots remain ordered nodes with
their namespace declaration removed from the expanded internal tree; included `merge` roots splice
their children at the original edge. Cycles, missing layouts, include overrides, standalone merge,
invalid or exceeded limits, and source-only includes fail before Kotlin generation.

Expansion does not synthesize a concatenated source file. Each parsed node carries its originating
project-relative path and source, so the six-node positive fixture retains exact provenance from
`screen.xml`, `profile_header.xml`, and `profile_actions.xml`. Duplicate IDs are checked after the
cross-file tree is assembled. Project context scans all three selected layouts for referenced
strings and IDs, while the migration report preserves one drawable and four string bindings. The
returned result includes both the schema-validated graph and its project-context evidence; no raw
application source enters either public evidence object. The second bounded expansion pass consumes
only the project resolver's in-memory, source-owned style/dimension results, so accepted v1 style
and string resolution also composes across an included layout without changing the raw graph
fingerprints or executing Android resource tooling.

On 2026-08-30, the dedicated gate matched 1/1 dependency graph, 1/1 exact Kotlin golden, 1/1
include/merge expansion with complete cross-file provenance, 1/1 resource denominator, and 2/2
fail-closed contract inputs. The generated Kotlin fingerprint is
`ac1ecc66785420b08c4bcb2c1486e49f3c651730a71c554c967f9e052c6ff6b8`; JDK 21 hermetic compilation
produced class fingerprint
`0da92c36e83b7f81d73dce57942f7378778b939cfed74052d2f46be43de330c8`.
Node 25.6.0 passed 115/115 AI-tooling tests, including first-root precedence, missing layout,
unsupported include override, standalone merge, symbolic-link, cycle, and runtime-ceiling cases.

The installed CLI generated and compiled the same dependency fixture, and modern MCP returned the
same two-edge graph without exceeding its frozen four-request concurrency ceiling. Two clean local
package builds remained identical: the 43-file, 266,988-byte archive has 1,520,468 declared file
bytes and SHA-256
`d1df410e100eb397681153c327ae5ab5ec105aa8d37780bd787dcf0e525908fd`. Offline lifecycle,
SPDX/license inventory, both MCP protocol versions, and all four installed compile denominators
passed. The quality-build plugin suite passed, and `verifyAiXmlLayoutDependencies` is now an owned
`qaQuick` dependency; its root execution passed 15 actionable tasks (1 executed and 14 up-to-date).
The final complete Design IR/project-context/layout-dependency/base-v2 XML stack passed 18
actionable tasks (4 executed and 14 up-to-date). After bounded cleanup of this worktree's
reconstructible build outputs recovered space for npm's atomic uninstall, the final distribution,
layout-dependency, documentation, and tooling-isolation run passed 22 actionable tasks (8 executed
and 14 up-to-date).

Compared with the graph-contract-only denominator, this is **improved** executable multi-layout
migration evidence with **no material Android runtime change**, because traversal, expansion,
generation, and compilation remain downstream tooling. Limitations remain explicit: style-supplied
v2 image/visibility fields are not an accepted subset, qualified layouts and Android resource
merging remain inventory-only, and compilation does not prove pixels, interaction, accessibility
behavior, or host call-site replacement. The next foundational gap is a generated screen Preview
lane that can render converter output without executing the inspected project build.

### Contract freeze — source-bound generated XML Preview v1

The next migration boundary is now explicit before converter output enters Layoutlib. Render mode
may accept only Kotlin produced by the same successful XML conversion. Its exact bytes, generator
function, artifact set, declared bindings, framework bundle, compiler lane, renderer lane, and one
frozen Preview configuration become a schema-validated, content-addressed request. A source or
function mismatch fails before Gradle runs. Callers cannot submit arbitrary Kotlin, a Gradle task,
a dependency coordinate, a build script, an output directory, or an inspected-project path.

The Preview wrapper is deterministic and has one public-static-compatible
`UiTreeBuilder.GeneratedXmlPreview()` entry point. Every generator-reported parameter must have
exactly one ordered binding with the same parameter, source identity, and type. V1 supports exact
`String` values and fresh `TextFieldState` values with explicit initial text. Missing, extra, or
duplicate bindings fail closed. `ImageSource` remains explicitly unsupported until an isolated,
offline asset-staging contract exists; the harness never fabricates a numeric resource ID, reads
an inspected project's resources, or downloads an image.

The tool-owned `:tools:ai-preview-harness` is the only accepted execution owner. It is fixed to JDK
21, AGP 9.1.1, Kotlin 2.2.10, Android 37/JVM 11 compilation, Preview protocol 1, Paparazzi
2.0.0-alpha05, Layoutlib 16.2.1, a 411 dp auto-height light `en-US`/LTR configuration, offline
dependency resolution, and one concurrent request. Evidence must progress through compilation to
`rendered`, reopen and verify both `preview.png` and `render-tree.json`, and return request,
generated-source, wrapper, PNG, tree, and aggregate output fingerprints without public absolute
paths.

The frozen positive denominator binds the existing four-parameter login Kotlin golden to an exact
816-byte wrapper. Its canonical request fingerprint is
`8b2d5460fea40ee539fc5aba01af5cac97d59002476c17b744fb7baa1144d063`, and the wrapper fingerprint
is `8d4ff9932ada6621a05b486a22d410d79a674db787c29ac22b1e7e4e0dcf8821`. Three negative
denominators preserve a missing state binding, an unsupported image binding, and forbidden caller
build-task selection. Phase 4 therefore has eight schemas, 35 metrics, 34 evaluation cases, 31
fixture-backed cases, and four generated-Preview fixtures in addition to the accepted XML,
project-context, and layout-dependency denominators.

On 2026-08-30, Node 25.6.0 passed the expanded Phase 0 verifier with exact schema, request,
generated-Kotlin, framework-bundle, configuration, lane, binding-set, wrapper, diagnostic, ceiling,
and isolation checks; all 115 Node AI-tooling tests remained green. The request schema entered two
byte-identical local package builds: the 44-file, 267,773-byte archive has 1,525,913 declared file
bytes and SHA-256
`c794cbe42ebc9a01427fdf82e63189990b0c36ec41e80e06f28e1be23460cf2e`.

This is **improved** measurable render readiness with **no material converter, Preview, or Android
runtime behavior change**: the schema and fixtures intentionally make no render success claim until
the harness produces accepted PNG/tree evidence. The next action is the fixed harness plus
`convert_xml_to_viewcompose` render mode; no alternate module or inspected-project build may
satisfy this contract.

### Implementation evidence — source-bound generated XML Preview

The accepted implementation adds one downstream Android application harness,
`:tools:ai-preview-harness`. A generated-screen request is converted into two immutable Kotlin
files under a content-addressed tool-owned directory: the converter's exact output and the
deterministic zero-argument Preview wrapper. The harness validates the request key and exact file
inventory before its debug source set is configured. The adapter alone selects the fixed discovery
and render tasks, aggregate current-source framework dependency, Preview worker, target owner,
method, configuration, and lanes; public CLI/MCP requests cannot select a task, dependency, build
script, project output, or arbitrary Kotlin source.

`convert_xml_to_viewcompose` now exposes `render` beside `generate` and `compile` for both standalone
source and explicit-project inputs. It first runs the same parser and generator, then requires an
ordered explicit binding for every generator-reported parameter. Exact `String` and fresh
`TextFieldState` values enter the wrapper; missing, extra, duplicate, reordered, source-mismatched,
type-mismatched, and image bindings fail before Gradle. The result keeps the migration IR, Kotlin,
report, and provenance while upgrading evidence only when compilation, discovery, Layoutlib render,
artifact reopening, and hash verification all succeed.

On 2026-08-30, the login denominator compiled and rendered at 411 dp, density 2.625, `en-US`, LTR,
and light theme into a 1,079 by 2,339 px, 38,919-byte PNG. Visual inspection showed the expected
title, text field hint, and sign-in button without clipping or corruption. The 202,604-byte render
tree reported five virtual and five mounted nodes, depth three, the expected title/action text, and
no warnings or layout diagnostics. The exact evidence is:

- request: `8b2d5460fea40ee539fc5aba01af5cac97d59002476c17b744fb7baa1144d063`;
- generated Kotlin: `6c4f6dafef9e0b4808eefab440d14e331b1a3b55bc8becff7a05d3669cc73be1`;
- wrapper: `8d4ff9932ada6621a05b486a22d410d79a674db787c29ac22b1e7e4e0dcf8821`;
- PNG: `e1efebaffa1efc19052a3fb1be33a8aa3fd670073a6330e976cd1be4082bb7fe`;
- render tree: `d0373c8499b9d46f9cafa98a04c6f30d41a8ec69743a5ada35496ba0e2e05e85`;
- aggregate render output: `6d2c8a5296db8cc95e5201092e40532f371f1d95621acd7bad343c913b4b9bab`.

The dedicated gate then reproduced 1/1 exact render, 1/1 second-request cache hit with the same
output, and 3/3 fail-closed missing-binding, image-binding, and caller-build-selection inputs. Node
25.6.0 passed 125/125 AI-tooling tests. The quality-build plugin suite passed, and root
`verifyAiGeneratedPreview` passed 15 actionable tasks (1 executed and 14 up-to-date). The packaged
CLI invoked the same render path from an offline isolated installation; two clean package builds
were byte-identical. The 45-file archive is 273,531 bytes, contains 1,550,545 declared file bytes,
and has SHA-256
`47a711ae59e1cd4bc03e29a521884ad53262ec4227006ecb984a7c160efe7742`. Offline installation and
uninstallation, SPDX/license inventory, both MCP protocol versions, four installed compile
denominators, and the generated Preview render all passed. The final combined generated-Preview,
documentation structure and translation, development-tooling isolation, and release-intent run
passed 22 actionable tasks (8 executed and 14 up-to-date).

Compared with compile-only XML migration, this is **improved** executable visual evidence with
**no material Android runtime behavior change** because the harness, adapter, CLI/MCP path, and
quality gate remain downstream development tooling. The result does not prove pixel parity against
the original XML, interaction, state restoration, accessibility traversal, alternate
configurations, or inspected-application integration. `ImageSource` was deliberately blocked,
which made bounded offline asset staging the next foundational increment rather than a broader
prompt or screenshot generator. The following contract and implementation close that gap.

### Contract freeze — isolated embedded PNG asset staging

The first generated-Preview asset lane is now frozen without broadening project or network access.
An `ImageSource` binding may carry only canonical RFC 4648 base64 for exact `image/png` bytes plus
the decoded byte count, SHA-256, and IHDR width and height. It accepts no filesystem path, URL, URI,
Android resource ID, project `R` symbol, XML/vector drawable, alternate media type, or loader model.
The converter and harness therefore cannot silently substitute an inspected project's drawable or
invent an asset when the binding is absent.

Before staging, the adapter must re-decode and re-encode canonical base64, verify byte count and
SHA-256, parse bounded PNG chunks, validate every CRC, require exactly one leading IHDR and terminal
IEND, and match the declared dimensions. Each image is limited to 524,288 decoded bytes and 1,024
by 1,024 pixels; one request permits at most 16 unique assets, 1,048,576 total asset bytes, and 256
chunks per PNG. Identical bytes deduplicate by full SHA-256. Accepted bytes are written once beneath
the request's immutable `res/drawable` directory as `vc_ai_<full-sha256>.png`; the deterministic
wrapper alone maps that generated resource through
`ImageSource.Resource(R.drawable.<resourceName>)`.

The contract-positive denominator uses the existing XML v2 `ProfileCardView` golden with one
70-byte, 1 by 1 px PNG. Its asset SHA-256 is
`4ff6ab670a58c14270e034e2090d9a432caa263a14e0a25785386b0c12f880b5`, canonical request
fingerprint is `1d81d2ed9db84ee022d806042cd883c426f4fe0061aa65c757ef0de3a91225f6`, generated Kotlin
fingerprint remains `15b15098e92b62bc9730ab7b3f2bde7715596f22069490a18b1e7830ff92ad35`, and the 917-byte
wrapper fingerprint is `461d7c9e7b9898b9b9f7373775fa10c8a180097664627b442d36a8b2abd2a4b2`.
The negative denominator preserves the same generator binding without bytes and requires
`VC-AI-PREVIEW-ASSET-MISSING` before Gradle execution.

Phase 0 now contains eight schemas, 36 metrics, 35 evaluation cases, 32 fixture-backed cases, and
five generated-Preview fixtures. The embedded asset is schema-valid, byte/hash/dimension exact,
CRC-valid, bounded, and source-matched. At contract freeze, the existing login render remained the
only implemented positive render. Node 25.6.0 passed 125/125 AI-tooling tests. Two clean
package builds remained byte-identical; the 45-file, 273,818-byte archive contains 1,551,859
declared file bytes and has SHA-256
`4522557fafe2351627371a169540e0572fdebd265f32a91b244cdf0cdbe68362`. Its offline lifecycle,
SPDX/license inventory, both MCP protocol versions, four installed compile denominators, and the
previous login Preview render all remained green.

The contract-only result was **improved** safety and measurability with
**no material Android runtime or accepted render behavior change**. Current limitations are
intentional: only embedded PNG is frozen, the contract does not read application resources or prove
the source XML's original pixels, and the profile-card request remained static unsupported evidence
until the following tool-owned resource staging and real Layoutlib gate passed.

### Implementation evidence — isolated embedded PNG asset staging

The generated Preview adapter now canonicalizes every embedded asset field, verifies the complete
bounded PNG contract, deduplicates exact bytes by full SHA-256, and persists assets with create-only
semantics under the request's content-addressed `res/drawable` directory. Existing bytes are
reopened and compared before reuse. Unexpected request-root, resource-root, drawable, or input
entries and any file or directory symbolic link produce cache-poison evidence before Gradle. Raw
base64 never enters the public result; only resource name, byte count, hash, and dimensions remain.

The harness mounts that request-owned directory as its debug Android resource source and validates
the exact directory and filename grammar before compilation. The wrapper imports only the harness
`R` class and constructs `ImageSource.Resource`; no image loader, inspected-project resource table,
project build logic, filesystem path, URI, or network client is added. Image-bearing requests carry
the exact `image.foundation` and drawing capability identities, while text-only login requests keep
their prior narrower capability set.

On 2026-08-30, the profile-card request compiled and rendered on the pinned lane. Its 1 by 1 px red
fixture expanded through the declared 96 dp cropped image region without corruption; the screenshot
was 1,079 by 2,339 px and 15,217 bytes. The 120,988-byte render tree contained three virtual and
three mounted nodes at depth two, preserved `Profile photo` as the image content description and
`Available` as the hidden text node, and reported no warnings or layout diagnostics. Exact accepted
evidence is:

- build: `76b256d15f1801358b009127e50467c5936af8b99714f6895e06dddef7a7b990`;
- aggregate output: `31fb45a13a4d35badee2cf61ce7760a0540b60ed2e0def2d3e3910cfdb4268f5`;
- PNG: `bb130675ac0de5df6ad6ff93ded020cbe93704a80030301da3a2d57a56b9cd3f`;
- render tree: `58bbd8da9df6295da2419dc85bf4c7d4636419f8022237740b694966763b31e9`.

The dedicated gate now reproduces 2/2 exact generated renders, 2/2 second-request cache hits, and
3/3 fail-closed missing binding, missing asset, and caller build-selection inputs. Node 25.6.0
passes 128/128 tests, including exact asset request/wrapper/resource planning, public schema
acceptance with path rejection, CRC tampering, immutable resource reuse, raw-byte redaction, and
symbolic-link cache poisoning.

The installed-package lifecycle exercises both generated screens through the same public CLI. Two
clean builds produced the same 45-file, 275,681-byte archive with 1,559,691 declared file bytes and
SHA-256 `11ce6376f6c0d5df91b74b3e0756200c222c9e2680752075793a4badb6f2d607`.
Offline installation, uninstall cleanup, SPDX inventory, both MCP protocol eras, compilation
fixtures, the login render, and the image render all passed. The quality-build plugin suite passed;
the combined generated-Preview, documentation-structure, development-tooling-isolation, and
release-intent root gate passed 22 actionable tasks, with eight executed and 14 up-to-date.

Compared with the text-only Preview lane, this is **improved** executable image evidence with
**no material Android runtime behavior change**: all new work remains in the downstream adapter,
harness, package, and quality gate. It still accepts only embedded PNG, not XML/vector drawables,
JPEG/WebP, arbitrary files, remote images, or application resource merging. It proves the generated
screen and its declared semantics, not pixel parity against the original XML or complete
accessibility behavior. The next foundational step is an exact semantic and geometry comparison
contract over Design IR and render-tree evidence before screenshot-driven generation or repair.

### Contract freeze — exact generated layout comparison

The first comparison contract is now frozen around evidence already owned by the XML conversion
request. Callers cannot submit a replacement Design IR, render tree, policy, artifact path, or
threshold. The comparator must reopen the exact content-addressed render tree, verify its SHA-256
and aggregate Preview identity, reject symbolic links, and compare it only with the canonical
compact fingerprint of the Design IR generated in the same request. A passing conversion advances
from `rendered` to `compared`; any mismatch retains only `rendered` evidence.

Node identity is exact and intentionally narrow. One leading `id:` is removed from a Design IR ID;
all other IDs are preserved and must resolve to exactly one authored virtual-node key. The only
v1 semantic-host exception is the current one-child `Column` wrapper around `TextField`; its
keyless child must have the same bounds. Kinds, parents, child order, visible text, content
descriptions, declared roles, and visibility are separate exact checks. String values resolve only
through the exact Preview binding source. Placeholder rendering absent from the tree, state and
event behavior, focus traversal, complete accessibility behavior, style, typography, and pixels
remain explicit non-claims.

Geometry uses integer render-tree coordinates in the accepted screenshot viewport. Declared dp is
rounded to the nearest pixel at the frozen density, with zero tolerance. V1 checks only applicable
exact dimensions, root and padded-child match-parent spans, observable uniform padding anchors,
containment, and vertical sibling order. Wrap-content records its observed bounds without claiming
a target size; `GONE` requires zero or absent visible bounds and makes size comparison
not-applicable. No aggregate score can hide a failed identity, structure, semantic, or geometry
check.

The two frozen denominators bind directly to the accepted login and profile-card renders. Login
maps 4/4 Design IR nodes and freezes 32 required checks, including the one allowlisted text-field
wrapper. Profile card maps 3/3 nodes and freezes 24 required checks plus one hidden-geometry
non-applicable result. Changed IR, changed render bytes or fingerprint, duplicate keys, kind drift,
and one-pixel exact-dimension drift are named failure denominators.

On 2026-08-30, Node 25.6.0 passed 128/128 tooling tests. Phase 0 now verifies nine schemas, 38
metrics, 37 cases, 34 fixture-backed cases, and two layout-comparison fixtures. Two clean package
builds produced the same 46-file, 276,927-byte archive with 1,567,175 declared file bytes and
SHA-256 `c45ff5c2431944f5501ee53428f21e055b882f017288a3203116ca7501a58a26`.
The offline lifecycle, installed compilation fixtures, both installed generated renders, SPDX
inventory, and both MCP protocol eras remained green.

This contract-only slice is **improved** comparison safety and measurability with **no material
Android runtime or accepted render behavior change**. It does not yet emit a comparison result or
upgrade the public conversion evidence. The next step is the bounded comparator, exact golden
results for both screens, and corruption, ambiguity, semantic, structure, and one-pixel failure
tests before any screenshot, prompt, or repair adapter is considered.

### Implementation evidence — exact generated layout comparison

The comparator now reopens the accepted render-tree artifact inside the configured ViewCompose
source root, rejects absolute or escaping paths and every symbolic-link segment, and verifies the
declared byte count and SHA-256 before parsing. It bounds Design IR, virtual, native, depth, check,
finding, and artifact denominators. Virtual node IDs, authored keys, native node IDs, bounds,
visibility, properties, and child arrays are validated before mapping; duplicate node identities,
unknown kinds, extra authored keys, unsupported synthetic nodes, and ambiguous mappings fail
closed.

Every comparison result keeps four separate check categories. Design IR IDs normalize to exact
authored keys, parent and child order are preserved, observable string resources resolve only from
the matching explicit Preview binding, and roles and visibility remain exact. Geometry derives
integer bounds from the accepted native tree, converts dp with the frozen density, accounts for
parent padding in match-parent and containment checks, and never assigns a target size to
wrap-content. The sole `TextField` wrapper exception requires one keyless semantic child and equal
identity/semantic bounds. `GONE` geometry is non-applicable only after both zero bounds and absent or
zero visible bounds are proven.

On the real pinned Layoutlib lane, the login input Design IR fingerprint is
`a938f6c0bd8333e195414353766d7e577bbcab0584c219cf4d123869192964d4`; all 4/4 nodes and
32/32 required checks pass, producing comparison fingerprint
`470b4e23384479ff29528fe311058618b6ace6536465aeaf08bb477a10cc737d`. The profile-card input
Design IR fingerprint is `8a860b20a34b87d0eae3918f12d1968e3653e0fe46da0cceffa68f70e9c25b09`;
all 3/3 nodes and 24/24 required checks pass, with one hidden-geometry non-applicable check,
producing comparison fingerprint
`6be3406d341e7e208501b95d1a42bfe15633f928c3b8cdc5cdc0d9ac6474752c`.
Both repeated conversions hit the existing render cache without weakening comparison.

Unit denominators independently fail a one-pixel exact-dp drift, changed text, authored child-order
drift, duplicate key ambiguity, changed render-tree identity, and symbolic-link evidence. Existing
missing binding, missing asset, and caller build-selection inputs remain 3/3 fail-closed before
comparison. Node 25.6.0 passes 135/135 tooling tests, and Phase 0 continues to verify nine schemas,
38 metrics, 37 cases, 34 fixture-backed cases, and two implemented comparison fixtures.

The public CLI/MCP conversion now advances to `compared` only on a complete pass. A mismatch returns
reason-coded findings while preserving the accepted render fingerprint and only `rendered`
evidence; the comparison fingerprint becomes the outer result identity only after success. The
installed-package lifecycle exercised both exact comparisons. Two clean builds produced the same
47-file, 283,631-byte archive with 1,596,570 declared file bytes and SHA-256
`b109ee20fbde9e2f891b1b414e15a63ae7a59d38f923a48633ba5dee90a90bcc`.
The quality-build plugin suite passed, and the combined generated-comparison,
documentation-structure, development-tooling-isolation, and release-intent root gate passed 22
actionable tasks, with eight executed and 14 up-to-date.

Compared with render-only evidence, this is **improved** semantic and geometry verification with
**no material Android runtime behavior change**: the implementation is a downstream, read-only
development tool over artifacts the Preview adapter already accepted. It does not establish
placeholder rendering, state/event behavior, full accessibility, style, typography, pixels, touch
targets, alternate configurations, or source-XML screenshot parity. Those limitations remain
separate denominators rather than being hidden by the passing deterministic checks.

### Contract freeze — deterministic screenshot preprocessing

The first Phase 5 input boundary is now frozen without adding a model, provider SDK, credential,
network call, or provider-specific request shape. Screenshot preprocessing v1 accepts only an
embedded canonical-base64 PNG with exact byte count, SHA-256, and dimensions. The caller must
declare density, font scale, locale, layout direction, sRGB color space, straight alpha, upright
orientation, system-bar insets, and a source-image-pixel crop. Paths, URLs, URIs, credentials,
provider transfer, persistence, and content-bearing logs remain schema-invalid.

Processing is deterministic and ordered: verify, decode, crop, apply explicit caller redactions in
cropped-output pixel coordinates, then encode. Version 1 accepts only non-interlaced 8-bit RGBA PNG,
bounds compressed input and output to 1.25 MiB, dimensions to 4,096 px, decoded data to 16 MiB, PNG
chunks to 256, and redactions to 64. It performs no resize or automatic system-bar/sensitive-content
inference. Canonical output contains only `IHDR`, `IDAT`, and `IEND`, uses PNG filter 0 and zlib level
9, and carries the output-byte SHA-256 plus key-order-independent canonical request and result
fingerprints. The 1.25 MiB image ceiling and 2,000,000-byte tool-result ceiling keep the duplicated
structured/text MCP response below the frozen 4 MiB stdio message limit with 194,304 bytes of
headroom.

The accepted 4×4 privacy-grid input is 112 bytes with SHA-256
`ff96bfc58337301e15ff1515d39a2653a855a46ef74e50f8884889cd28f21cc0`. Cropping the full image
and replacing the explicit central 2×2 rectangle with opaque black produces the exact 106-byte PNG
with SHA-256 `201c08259fb2891c57c3f85e0f9e1157ad9df9ae8303c4f8d679735cf2850b99`, request
fingerprint `e9db4c486dbcaa59cd214b557cca19fb4878f66eb668f9b94cbd14a4ca6dd77f`, and result
fingerprint `74d3e3190dca4157d07cefd51f9a3a809094dad93785cef3c327f566a6e832b1`. The contract verifier
checks canonical base64, PNG signature/chunks/CRC, image format, bounded inflate size, every PNG
filter reconstruction, crop/redaction bounds, exact output pixels, transformation order, privacy
record, and both fingerprints. Separate fixtures keep absolute-path input and provider transfer
schema-invalid with reason-coded expected diagnostics.

Node 25.6.0 remains at 135/135 passing tooling tests. Phase 0 now verifies 10 schemas, 41 metrics,
40 cases, 37 fixture-backed cases, and three screenshot-preprocessing fixtures. The installed
package lifecycle still passes 2/2 reproducible builds, offline installation/uninstallation,
SPDX/license inventory, both MCP protocol eras, compiled migrations, and both exact generated-layout
comparisons. Relative to the preceding comparison slice, the package adds one contract file: file
count increases from 47 to 48 (+2.13%), declared bytes from 1,596,570 to 1,604,880 (+0.52%), and
archive bytes from 283,631 to 284,567 (+0.33%). The new archive SHA-256 is
`ffe2c17ada8c13267047ca6b01a47f9a5387441afc8570f9ca1375c150ae22a1`. The quality-build
plugin test suite passed, and the combined AI-contract, installed-distribution,
documentation-structure, development-tooling-isolation, and release-intent root gate passed 23
actionable tasks, with nine executed and 14 up-to-date.

This is **improved** input integrity, privacy, and evaluation measurability with **no material
execution or Android runtime behavior change**. At this contract-only point, no screenshot had been
converted to Design IR, compiled, rendered, compared, repaired, or sent to a provider. The following
implementation closes only deterministic preprocessing.

### Implementation evidence — deterministic screenshot preprocessing

`prepare_screenshot` is now the tenth public CLI/MCP tool. Its dependency-free Node adapter parses
PNG chunks in memory, verifies canonical base64, bytes, SHA-256, ordering, critical-chunk support,
CRC, dimensions, bounded decompression, complete zlib consumption, color/animation semantics, and
zero-or-one valid sRGB intent, then reverses filter types 0–4. Embedded profiles, conflicting color
chunks, auxiliary transparency, and APNG are rejected rather than silently flattened. It applies
the declared source crop, fills only explicit cropped-output redaction rectangles
with opaque black, and re-encodes a metadata-free `IHDR`/`IDAT`/`IEND` PNG with filter 0 and zlib
level 9. It never accepts a file locator, opens a project, writes an image/cache, calls a network,
or transfers content to a provider.

Node 25.6.0 passes 143/143 tooling tests. The focused acceptance gate reproduces 1/1 golden,
3/3 repeated/key-order-independent runs, 2/2 privacy denials, 1/1 changed-identity denial, and 1/1
cancellation. Unit denominators additionally exercise all five PNG filters, ancillary metadata
stripping, changed CRC, unsupported color type/profile, APNG semantics, out-of-bounds crop, and
out-of-bounds redaction.
Phase 0 remains at 10 schemas, 41 metrics, 40 cases, 37 fixture-backed cases, and three implemented
screenshot-preprocessing fixtures.

The installed package reproduces result fingerprint
`74d3e3190dca4157d07cefd51f9a3a809094dad93785cef3c327f566a6e832b1` through both the CLI and
the preferred MCP protocol while the legacy protocol lists the same ten-tool catalog. The complete
offline lifecycle still passes 2/2 reproducible builds, SPDX/license inventory, compilation and
render comparisons, install, and uninstall. Relative to the contract-only slice, package file count
increases from 48 to 50 (+4.17%), declared bytes from 1,604,880 to 1,627,459 (+1.41%), and archive
bytes from 284,567 to 290,571 (+2.11%). The implemented archive SHA-256 is
`b158057876bfb3d756038c4f0d525464df32dec85b00d142be982b8f4bd61968`. The quality-build
plugin suite passed. The combined AI-contract, screenshot, installed-distribution,
documentation-structure, development-tooling-isolation, and release-intent root gate passed 24
actionable tasks, with 10 executed and 14 up-to-date.

This is **improved** deterministic screenshot integrity, redaction, transport safety, and installed
tool usability with **no material Android runtime behavior change**. It does not infer nodes, text,
resources, semantics, state, behavior, or confidence; generate Kotlin; render/compare a reconstructed
screen; call a model; or authorize provider transfer. The next action is a provider-neutral
screenshot-to-Design-IR request/result and consent contract that keeps those uncertainties visible.

### Contract freeze — screenshot-to-Design-IR inference

Screenshot-to-Design-IR inference v1 now freezes the provider-neutral request, result, evidence,
uncertainty, and authorization boundary without exposing a public tool or selecting a model. The
request accepts only the exact canonical PNG, preprocessing request fingerprint, and preprocessing
output fingerprint produced by `prepare_screenshot`. Its authorization binds the reviewed input to
that exact output fingerprint. A changed lineage identity fails even when the mutated request still
conforms to JSON Schema; caller paths and credentials remain outside the accepted shape.

The offline human-reviewed golden begins with a 16×24, 130-byte preprocessed PNG whose SHA-256 is
`db28e5a95b48fcbdde009f078295db924a48fde252ed5205a266b187b980f6d3` and whose preprocessing
result fingerprint is `58c45a3ce39b74fc9585132ac912fb8c915ac0a0334f4151f4cd1b1f51a87bb3`. The inference request
fingerprint is `f789490fa61fa8d6a74e546b8defa536a78c9cebc83a123ba70da9967030a62b`. It yields four Design IR
nodes and exactly four evidence records. Every node owns one in-bounds pixel rectangle, and its
Design IR `sourceId`/`sourceSpan` must match the screenshot SHA-256 and exact
`pixels:x,y,width,height` evidence region. The Design IR fingerprint is
`585b3d1761cc47f9718ff48e09216899faa470ca662e4e98ad705c8686109b5a`.

Confidence remains dimension-specific for asset, content, geometry, semantics, structure, and
style; version 1 defines no aggregate score. The golden deliberately preserves six unsupported
semantics and six blocking questions for text, field purpose/state/behavior, button
label/behavior, and accessibility. Unknown values use placeholder bindings, all unsupported
entries remain blocked, all questions forbid invented defaults, and code generation stays false.
The incomplete result fingerprint is
`4bd30960cccdfe3b9a4402293b3739a3238a25fcef12fb2911c595a3df7a66c0`.

Human-golden authorization performs zero provider transfers, zero network requests, zero input or
output persistence, and metadata-only logging. The future provider-adapter shape requires an
explicit provider ID, an exact-input consent receipt, the approved `screenshot-to-design-ir`
purpose, completed retention review, immutable model and provider request/response identities, and
no raw request or response persistence. This schema permits a future authorized adapter contract;
the current execution contract keeps provider selection and execution false. Dedicated invalid
denominators reject provider transfer without consent, any credential-shaped input, and a changed
preprocessing output fingerprint.

Node 25.6.0 passes 144/144 tooling tests. The focused preprocessing gate now reproduces 2/2
goldens, 5/5 repeated or key-order-independent runs, 2/2 privacy denials, 1/1 changed-identity
denial, and 1/1 cancellation. The inference gate accepts 1/1 human golden, verifies 4/4
node/evidence records and all six blocking questions, rejects 3/3 failure denominators, and records
zero provider executions and zero network requests. Phase 0 verifies 11 schemas, 45 metrics, 45
cases, 42 fixture-backed cases, four screenshot-preprocessing fixtures, and four screenshot-
inference fixtures. The fixed generated-Preview lane remains green at 2/2 exact renders, 2/2 stable
cache hits, and 3/3 unsafe or unsupported failures.

The complete offline distribution lifecycle passes 2/2 reproducible builds, installation and
uninstallation, SPDX/license inventory, both MCP protocol eras, compilation, and both exact layout
comparisons. Relative to the implemented preprocessor slice, the package adds the frozen inference
schema and updates its existing README contract: file count increases from 50 to 51 (+2.00%),
declared bytes from 1,627,459 to
1,643,944 (+1.01%), and archive bytes from 290,571 to 292,512 (+0.67%). The archive SHA-256 is
`535eb785b54b117db47ea2f38adca14d0048ab86f791675727b71aac06357d72`. The quality-build plugin
suite passed. The combined AI-contract, preprocessing, inference, installed-distribution,
documentation-structure, development-tooling-isolation, and release-intent root gate passed 25
actionable tasks, with 11 executed and 14 up-to-date.

This is **improved** lineage integrity, evidence completeness, uncertainty honesty, consent safety,
and evaluation measurability with **no material Android runtime behavior change**. It is still a
reviewed contract fixture, not a claim that pixels prove text, behavior, state, resources,
accessibility, or production visual fidelity. It does not call a model, generate Kotlin, compile a
reconstructed screen, render or compare that screen, or authorize any provider. The next action is
an offline validator/import adapter that can accept externally produced results only after all
frozen schema, lineage, evidence, uncertainty, and authorization checks pass.

### Implementation evidence — offline screenshot inference validation

`validate_screenshot_inference` is now the eleventh public CLI/MCP tool and remains an entirely
offline import boundary. Its input contains the original preprocessing request, a compact inference
declaration, and an externally produced raw result. The adapter reruns `prepare_screenshot` and
reconstructs the full inference request internally instead of accepting a caller-supplied duplicate
of the preprocessed PNG. It verifies both request and result schemas, the accepted framework bundle
and lane, canonical request/result/Design-IR fingerprints, exact preprocessing lineage, every
node/evidence/source-region relationship, dimension-specific confidence, blocking questions,
forbidden defaults, unsupported semantics, summary counts, code-generation status, and producer
authorization before returning imported Design IR. Behavior, executable expressions, resolved
resource bindings, changed lineage, missing evidence, out-of-bounds regions, and malformed consent
fail closed with stable diagnostics.

The human-reviewed golden imports deterministically twice with validation fingerprint
`556c13d133d63e34fa81d1c04df3bee938509c5ced1d244ccf2366d48cb6e845`. The focused gate also
accepts one externally produced provider-provenance result only when its immutable provider/model
identities and consent receipt bind to the exact preprocessed input. This proves import validation,
not provider operation: the adapter performs zero provider executions and zero network requests.
Credential-shaped input, missing consent, and changed preprocessing lineage supply 3/3 explicit
failure denominators. The imported golden remains incomplete with six blocking questions and
`codeGenerationAllowed` false; successful validation never upgrades uncertain pixels into behavior,
state, resource, accessibility, or production-fidelity claims.

Node 25.6.0 passes 153/153 tooling tests. Phase 0 verifies 11 schemas, 46 metrics, 45 cases, 42
fixture-backed cases, four screenshot-preprocessing fixtures, and four screenshot-inference
fixtures. The installed package returns the same validation fingerprint through both the CLI and
preferred MCP protocol, retains both supported MCP protocol eras, and passes the complete 2/2
reproducible-build and offline install/uninstall lifecycle. Relative to the contract-only inference
slice, package file count increases from 51 to 53 (+3.92%), declared bytes from 1,643,944 to
1,672,552 (+1.74%), and archive bytes from 292,512 to 298,393 (+2.01%). The archive SHA-256 is
`4769a85cd7e65ef7d4747c31b2e5344634aff514c2e8cab8f2c859cb51ea1933`. The quality-build
plugin suite passed. The combined AI-contract, preprocessing, inference, installed-distribution,
documentation-structure, development-tooling-isolation, and release-intent root gate passed 25
actionable tasks, with 11 executed and 14 up-to-date.

This is **improved** deterministic import integrity, consent enforcement, unsupported-case honesty,
and installed-tool parity with **no material Android runtime behavior change**. It neither calls a
model nor makes the incomplete IR compilable. The next action is a typed, human-supplied resolution
patch that can answer only the exact blocking questions, preserve provenance, forbid arbitrary
executable content, and derive code-generation eligibility mechanically before any provider is
selected.

### Contract freeze — typed screenshot inference resolution

Screenshot inference resolution v1 now freezes the provider-independent boundary between one
validated, incomplete inference import and a Design IR that may enter a future generator. The
request carries the exact validation, inference-result, and input-Design-IR fingerprints. Its human
authorization binds reviewer identity, an immutable review receipt, the exact validation
fingerprint, completed source inspection, and the `resolve-screenshot-inference` purpose while
keeping provider execution, network access, and content-bearing logs false. Every answer must match
one imported question's ID, optional node, pixel region, category, and required action; duplicate,
unknown, or missing answers cannot silently resolve anything.

The answer surface is intentionally typed and data-only. Content answers may update only properties
or state with a literal string, one bounded Android input profile, or a resolved caller-owned
binding. Behavior answers may name only `click`, `focus-change`, or `keyboard-action` callback
bindings and cannot contain callback source. Accessibility review must cover every imported node
exactly once with an explicit role, label source, traversal index, and decorative decision.
Expressions, guessed resources, arbitrary executable source, and provider credentials do not fit
the schema. An unsupported semantic may disappear only through its question-bound resolution
record and the same review receipt.

The exact wireframe golden answers all six imported blocking questions: title and button labels,
input purpose/state, field keyboard behavior, button click ownership, and the four-node
accessibility review. It adds two resolved caller event bindings and persists all 14 explicit
accessibility fields—including two semantic roles—while preserving all four nodes, kinds,
hierarchy, screenshot source identity, and pixel provenance. The
resolved Design IR fingerprint is
`6137e04205c3bec89b5e4480e0448e45c6ab55905a0ea3d9fdc55ef2b3e52603`; the resolution request
fingerprint is `c2712d96b7f1e821e18c0952dcd31becafb48eea0df848e2983efb319dd3fea6`, and the result
fingerprint is `61426e6904d9ffbdf1b29ec77fd8e6e0ee345494a0aad3b18028781f20ef981a`. The result has zero
remaining questions, unsupported semantics, and placeholder bindings, so
`codeGenerationAllowed` is mechanically true. That flag is eligibility only: this contract makes no
compilation, render, pixel, visual-parity, or production-behavior claim.

The focused gate passes 1/1 golden, 6/6 typed answers, 6/6 resolved unsupported semantics, 2/2
event bindings, 14/14 accessibility fields, and 3/3 fail-closed denominators for missing coverage,
expression injection, and changed validation lineage. It performs zero provider executions and zero
network requests. Node 25.6.0 passes 154/154 tooling tests. Phase 0 now verifies 12 schemas, 48
metrics, 49 cases, 46 fixture-backed cases, and four screenshot-resolution fixtures in addition to
the earlier screenshot evidence. The complete offline distribution retains its installed CLI/MCP,
compile, render-comparison, SPDX, and 2/2 reproducible-build lifecycle. Relative to the implemented
inference validator, package file count increases from 53 to 54 (+1.89%), declared bytes from
1,672,552 to 1,686,032 (+0.81%), and archive bytes from 298,393 to 299,922 (+0.51%). The archive
SHA-256 is `0a8b6ee752687c9b3b590d57ef82a6a149118ef0012abcba2890bee52cb672dd`.
The quality-build plugin suite passed. The combined AI-contract, screenshot preprocessing,
inference, resolution, installed-distribution, documentation-structure,
development-tooling-isolation, and release-intent root gate passed 26 actionable tasks, with 12
executed and 14 up-to-date.

This is **improved** resolution provenance, executable-content safety, accessibility review
coverage, and code-generation-gate measurability with **no material Android runtime behavior
change**. The resolution is still a frozen fixture rather than a public mutation tool. The next
action is an offline adapter that reproduces this patch from the validated import, exposes it through
the shared CLI/MCP package, and preserves the same fail-closed boundary before screenshot-specific
Kotlin generation begins.

### Implementation evidence — typed screenshot inference resolution

`resolve_screenshot_inference` is now the twelfth public CLI/MCP tool. It accepts only the unchanged
data returned by `validate_screenshot_inference` plus the frozen human-resolution request. Before
mutation it recomputes the validation and input-Design-IR fingerprints and verifies the request,
authorization, inference result, Design IR, question set, and summary share one lineage. It then
requires exact answer coverage and matches every answer to the imported question's node, category,
required action, and pixel rectangle. The adapter applies only component-compatible text,
text-field, button, caller-state, and caller-event decisions. It never evaluates a string as code,
loads a resource, calls a provider, opens a network connection, or accepts callback source.

The implementation review found that the contract-only golden retained accessibility roles but not
the equally explicit label-source, traversal, and decorative decisions. The result was hardened
before the public adapter was accepted: all four fields now live in each applicable node's Design IR
semantics, producing 14/14 persisted accessibility fields. The adapter also preserves the original
document ID, four-node hierarchy, kinds, screenshot source identity, and pixel provenance. It
reproduces the exact resolved Design IR fingerprint
`6137e04205c3bec89b5e4480e0448e45c6ab55905a0ea3d9fdc55ef2b3e52603` and result fingerprint
`61426e6904d9ffbdf1b29ec77fd8e6e0ee345494a0aad3b18028781f20ef981a` twice. Missing answers,
expression injection, and changed validation lineage return their exact three fail-closed
diagnostics; additional unit denominators cover moved pixel regions, component-incompatible fields,
partial accessibility review, and cancellation.

Node 25.6.0 passes 163/163 tooling tests. Phase 0 remains at 12 schemas, 48 metrics, 49 cases, 46
fixture-backed cases, and four screenshot-resolution fixtures. Direct CLI and MCP results are
semantically identical, and the installed package reproduces the same resolved fingerprint through
both supported MCP protocol eras. The complete distribution still passes 2/2 reproducible builds,
offline install/uninstall, SPDX/license inventory, compilation, and exact generated-layout
comparisons. Relative to the contract-only resolution slice, package file count increases from 54
to 56 (+3.70%), declared bytes from 1,686,032 to 1,711,367 (+1.50%), and archive bytes from 299,922
to 305,305 (+1.79%). The archive SHA-256 is
`12a9148c1992b163d9861202176ed2f32c35af96a2f3c6eaf450d73500e8c7a6`. The quality-build
plugin suite passed. The combined AI-contract, screenshot preprocessing, inference, resolution,
installed-distribution, documentation-structure, development-tooling-isolation, and release-intent
root gate passed 26 actionable tasks, with 12 executed and 14 up-to-date.

This is **improved** deterministic resolution, review-evidence preservation, executable-content
safety, and installed transport parity with **no material Android runtime behavior change**. The
resolved IR is eligible for generation but has not yet produced, compiled, rendered, or visually
compared screenshot-derived Kotlin. The next action is to freeze and implement a screenshot-specific
IR-to-Kotlin mapping for its typed state, event, and accessibility bindings, using hermetic
compilation as the first acceptance boundary.

### Contract freeze — screenshot Design IR to Kotlin and hermetic compilation

Screenshot Kotlin generation v1 now freezes the first executable boundary after typed resolution.
The request binds the exact resolution-result fingerprint
`61426e6904d9ffbdf1b29ec77fd8e6e0ee345494a0aad3b18028781f20ef981a` and resolved Design IR
fingerprint `6137e04205c3bec89b5e4480e0448e45c6ab55905a0ea3d9fdc55ef2b3e52603` and requires resolved
status, `codeGenerationAllowed = true`, zero remaining questions, zero unsupported semantics, and
zero placeholder bindings. Expressions, resources, callback source, project build execution, and
network access are outside the accepted surface.

The mapping is separate from the XML generator because screenshot resolution owns typed behavior
and accessibility review that the bounded XML subset deliberately rejects. The four-node golden
maps `emailState` to a caller-owned `TextFieldState`, `onEmailSubmit` to
`(TextFieldImeAction) -> Boolean`, and `onContinue` to `() -> Unit`. It emits real `Column`, `Text`,
`TextField`, and `Button` calls with stable node keys and `TextFieldInputProfile.Email`. The exact
Kotlin fingerprint is `5812c3ccbd0a6f30a0cc4c3ff4e71453006745d5dd76e63e153b2501131252e9`.

Every reviewed node also has one report record for role, label source, traversal index, decorative
status, and emission disposition. `Button` and `TextField` roles use component defaults; visible
text and the field placeholder carry labels. ViewCompose currently exposes no public
`traversalIndex` modifier, so the contract requires the reviewed ascending order to equal generated
hierarchy order and records that no explicit modifier was emitted. This is a deliberate honesty
boundary, not a fabricated API or a claim that all Android accessibility services traverse every
configuration identically. The report fingerprint is
`51c09b75e1a8bec953191e50388795c61fff6c45841de1f7832e050d2824752d`.

The dedicated JDK 21/Kotlin 2.3.10 source lane passes 1/1 golden compile with class-output
fingerprint `7f42dcfd35573559c8c4c2bc62047a57085e01f4c78f2625299349b00440ae67`.
The contract gate also passes 4/4 node mappings, 1/1 state binding, 2/2 event bindings, 4/4
accessibility records, and 3/3 fail-closed denominators for ineligible resolution, changed lineage,
and an unsupported event. Phase 0 now verifies 13 schemas, 51 metrics, 53 cases, 50 fixture-backed
cases, and four screenshot-generation fixtures. The previously implemented screenshot-resolution
gate and the new generation gate are both dependencies of `qaQuick`; this closes the lifecycle gap
where the resolution task existed but was not part of that aggregate.

The complete offline distribution still passes 2/2 reproducible builds, one offline
install/uninstall lifecycle, SPDX/license inventory, both MCP protocol eras, the installed compiler,
and the existing generated-layout comparison denominators. Only the generation schema is shipped at
this contract-only step; no unimplemented tool is advertised. Relative to the implemented
resolution slice, package file count increases from 56 to 57 (+1.79%), declared bytes from
1,711,367 to 1,720,941 (+0.56%), and archive bytes from 305,305 to 306,601 (+0.42%). The archive
SHA-256 is `336a81e18c666241b4fadae770bb4fcac0ec3bb14f002b7ac188bec79f2ebede`.
The quality-build plugin suite, documentation structure, development-tooling isolation, and release
intent gates pass. The combined AI-contract, screenshot preprocessing, inference, resolution,
generation, installed-distribution, documentation-structure, development-tooling-isolation, and
release-intent root gate passes 27 actionable tasks, with 13 executed and 14 up-to-date.

This is **improved** executable-contract precision and compile evidence with **no material Android
runtime behavior change**. It proves only that the frozen source is schema-valid, lineage-bound,
deterministic by bytes, and compilable against the accepted artifact. It does not yet prove a
generator can reproduce the source, expose it through CLI/MCP, render it, match the screenshot, or
behave correctly in an application. The next action is to implement the frozen generator and a
bounded generate/compile tool before any render or visual-comparison claim.

### Implementation evidence — screenshot Kotlin generation and compilation

`generate_screenshot_viewcompose` is now the thirteenth public CLI/MCP tool. It accepts the complete
resolved result plus the generation request; callers cannot replace the resolved IR with an
unbound object or select a package, function body, callback source, classpath, capability, compiler,
Gradle task, project path, model, or provider. The implementation revalidates the resolution and
Design IR schemas, recomputes both fingerprints, confirms the request identity and mechanical
eligibility, and accepts only the four frozen component kinds and their exact typed fields.

The generator allocates deterministic Kotlin identifiers, keeps state parameters before event
parameters, merges repeated compatible caller bindings, rejects signature conflicts, and escapes
Kotlin string templates. It reproduces the exact source fingerprint
`5812c3ccbd0a6f30a0cc4c3ff4e71453006745d5dd76e63e153b2501131252e9` and report fingerprint
`51c09b75e1a8bec953191e50388795c61fff6c45841de1f7832e050d2824752d` twice. The same mapper
also enforces the complete accessibility review and rejects traversal that cannot be represented by
the generated hierarchy order. Generate mode returns `static` evidence; compile mode alone invokes
the existing fixed UI Foundation compiler and reproduces class-output fingerprint
`7f42dcfd35573559c8c4c2bc62047a57085e01f4c78f2625299349b00440ae67`.

Node 25.6.0 passes 174/174 tooling tests. The focused gate passes 4/4 nodes, 1/1 state binding, 2/2
event bindings, 4/4 accessibility records, 3/3 fail-closed denominators, 2/2 deterministic
generations, and 1/1 hermetic compile. Phase 0 remains at 13 schemas, 51 metrics, 53 cases, 50
fixture-backed cases, and four screenshot-generation fixtures. Direct CLI and MCP generate results
are semantically identical. The installed package reproduces both the exact Kotlin fingerprint and
the exact compiled class fingerprint; both supported MCP eras list the thirteen-tool catalog, and
the modern protocol reproduces generation through the installed server.

The complete distribution passes 2/2 reproducible builds, offline install/uninstall, SPDX/license
inventory, installed compilation, and the existing generated-layout comparison denominators.
Relative to the contract-only slice, package file count increases from 57 to 60 (+5.26%), declared
bytes from 1,720,941 to 1,752,068 (+1.81%), and archive bytes from 306,601 to 312,952 (+2.07%). The
archive SHA-256 is `749ae2ca07a8cd269326b673ab9e7ad62517431721bf9be62571b3e92374e236`.
The quality-build plugin suite passes. The combined AI-contract, screenshot preprocessing,
inference, resolution, generation, installed-distribution, documentation-structure,
development-tooling-isolation, and release-intent root gate passes 27 actionable tasks, with 13
executed and 14 up-to-date.

This is **improved** deterministic source generation, typed behavior binding, accessibility
disposition preservation, hermetic compile evidence, and installed transport parity with **no
material Android runtime behavior change**. The result is compilable code, not a render or visual
parity claim. The following contract freezes explicit Preview values for caller state and callbacks
before the source enters the existing isolated harness.

### Contract freeze — source-bound screenshot generated Preview

Screenshot generated Preview v1 now freezes the next boundary after hermetic Kotlin compilation.
The request carries `sourceKind: "screenshot"` plus the exact resolution result, resolved Design
IR, generation request, generation report, generated Kotlin, framework bundle, configuration, and
compiler/render lane lineage. It uses a dedicated `tools.ai.GeneratedScreenshotPreview` identity,
`UiTreeBuilder.GeneratedScreenshotPreview()` wrapper, `Generated Screenshot ·` annotation prefix,
and `AI/Screenshot` group so screenshot evidence cannot be mislabeled or cached as XML evidence.

Every state and event parameter reported by the generator must have one binding in the same order
with the same parameter, source, and type. `TextFieldState` accepts explicit initial text.
`() -> Unit` and `(Boolean) -> Unit` map only to fixed no-op callbacks, while
`(TextFieldImeAction) -> Boolean` maps to an explicit Boolean return. None accepts lambda source,
expressions, project code, a build task, dependency, path, provider, or network selection. The
four-node wireframe golden therefore produces `TextFieldState()`, `{ _ -> false }`, and `{ }`
without executing caller content. Its request fingerprint is
`3bd5fe6b172856fd4e45cb30d8d301968f14353a549057c7e87041b30352b77c`; its 811-byte wrapper
fingerprint is `7b0d004f650248f2108e960385efa7e9a324acc600bfcd142f71c4a8b8d5c65b`.

The contract gate passes 1/1 exact wrapper and 3/3 fail-closed callback-source, missing-callback,
and wrong-callback-kind denominators. Phase 0 now verifies 13 schemas, 53 metrics, 57 cases, 54
fixture-backed cases, and four screenshot-Preview fixtures. Node 25.6.0 passes 175/175 AI-tooling
tests. The quality-build plugin suite passes, and `verifyAiScreenshotRender` is a `qaQuick`
dependency even at this contract-only stage so future activation cannot bypass the aggregate.

The schema update remains inside the existing 60-file offline package. Relative to screenshot
generation implementation, declared bytes increase from 1,752,068 to 1,754,433 (+0.13%) and archive
bytes from 312,952 to 313,203 (+0.08%); file count is unchanged. The archive SHA-256 is
`8f9b8037d603e2c0aea533eb937a488bb24ddf0e2a31fb81e20832ab603dbdfa`.
The distribution gate passes 2/2 reproducible builds, offline install/uninstall, SPDX/license
inventory, both MCP protocol eras, all installed compile denominators, and both existing XML
generated-layout comparisons. The combined screenshot-Preview, distribution, documentation,
tooling-isolation, and release-intent gate passes 23 actionable tasks, with 9 executed and 14
up-to-date.
No published ViewCompose artifact, public/protected API, Android runtime, provider boundary, or
application-process behavior changes, so this slice requires no Maven release changeset or module
manual update. The changed active plan and tooling README own the documentation impact.

This is **improved** render-contract precision and callback-source safety with **no material runtime
behavior change**. The wrapper has not yet been compiled or rendered, so the contract makes no PNG,
render-tree, semantic, geometry, or pixel claim. The next action is to implement this frozen profile
in `generate_screenshot_viewcompose` render mode, reproduce exact rendered evidence through the
installed package, and only then bind the result to semantic comparison.

### Implementation evidence — source-bound screenshot generated Preview

`generate_screenshot_viewcompose` now exposes `render` beside `generate` and `compile`. Render mode
requires the exact resolved result, a render-specific generation request, and explicit bounded
Preview bindings. It regenerates Kotlin and its report first, then passes only those tool-owned
bytes and values into the existing content-addressed `:tools:ai-preview-harness`. The shared Preview
adapter recognizes the screenshot report separately from XML, preserves every existing XML request
and wrapper fingerprint, and selects only `tools.ai.GeneratedScreenshotPreview`. The public tool
schema requires `previewBindings` only for render mode; CLI and MCP return the same result shape.

The callback implementation is deliberately non-executable input. `() -> Unit` becomes `{ }`,
`(Boolean) -> Unit` becomes `{ _ -> }`, and `(TextFieldImeAction) -> Boolean` becomes a lambda with
one explicit Boolean result. An extra callback source/value field, a missing callback, or a wrong
callback kind fails before Gradle. The adapter still accepts no inspected-project task, dependency,
build script, project path, output path, provider, credential, or network operation. Render mode's
generation request and report fingerprints are
`17a785a25672a8a2a2998618dab80015081347e29c601201638666bf8ec4f068` and
`c62b30e811ad8c68f7ef454f441bd52744ea49b9238c49816513787294ed16ea`.

Under the fixed 411 dp, density 2.625, `en-US`, LTR, light configuration, the generated wireframe
compiled and rendered into a 1,079 by 2,339 px, 30,984-byte PNG. Visual inspection showed the
expected `Welcome` title, `Email address` field placeholder, and `Continue` button without clipping
or corruption. The 203,290-byte render tree contains five virtual and five mounted nodes at depth
three, the expected observable title/action text, and zero warnings or layout diagnostics. Exact
evidence is:

- build: `2a92748798bad30d22e6a1a2160f7bebccfe58f9dcf19b4b9f7be6c90b471512`;
- aggregate render: `ba78a4047cad992e43b801a6b93a632a72543f383521172364d69b28fccf5076`;
- PNG: `072787b8fa78026425577e7159494b9841850c4366ac1aa62010b4342919e5fd`;
- render tree: `5228e401662349d9142cf695c42e21805c7c332ac36bc09334a32251d2f27000`.

The dedicated gate reproduces 1/1 exact render, 1/1 stable cache hit, and 3/3 fail-closed unsafe
bindings. Node 25.6.0 passes 180/180 AI-tooling tests. Phase 0 verifies 13 schemas, 54 metrics, 57
cases, 54 fixture-backed cases, and four screenshot-Preview fixtures. The installed CLI reproduces
the exact rendered fingerprint, while shared CLI/MCP render requests retain transport parity.

The 60-file offline package has 1,761,601 declared bytes and a 314,713-byte archive, SHA-256
`555f3faae7561d953896a729380bb0978a111a31e9a0d2559a9074f546d3c602`. Relative to the
contract-only package, declared bytes increase by 7,168 (+0.41%) and archive bytes by 1,510 (+0.48%)
with no runtime dependency added. The distribution gate passes 2/2 reproducible builds, offline
install/uninstall, SPDX/license inventory, two MCP protocol eras, all installed compile lanes, the
new installed screenshot render, and both prior XML comparisons.
The quality-build plugin suite passes. The combined screenshot-Preview, distribution,
documentation-structure, development-tooling-isolation, and release-intent gate passes 23
actionable tasks, with 9 executed and 14 up-to-date. No published artifact or public/protected API
changed, so the contract slice's no-Maven-changeset and no-module-manual disposition remains valid.

This is **improved** source-bound render evidence, callback safety, cache determinism, and installed
transport coverage with **no material Android runtime behavior change**. Limitations remain
explicit: this acceptance covers one configuration and confirms render integrity plus a human
sanity inspection; it does not yet prove semantic/geometry agreement with Design IR, accessibility
runtime behavior, interaction, responsive variants, or pixel similarity to the input screenshot.
The next action is to bind this accepted tree to an exact semantic and geometry comparison before
adding any pixel metric or repair loop.

### Contract freeze — screenshot semantic and structural geometry comparison

Screenshot layout comparison v1 freezes the exact boundary between accepted screenshot rendering
and any visual claim. It reuses the existing schema-validated generated-layout comparator but binds
its inputs to the resolved screenshot Design IR fingerprint
`6137e04205c3bec89b5e4480e0448e45c6ab55905a0ea3d9fdc55ef2b3e52603`, generated Preview request
fingerprint `3bd5fe6b172856fd4e45cb30d8d301968f14353a549057c7e87041b30352b77c`, aggregate render
fingerprint `ba78a4047cad992e43b801a6b93a632a72543f383521172364d69b28fccf5076`, and render-tree
fingerprint `5228e401662349d9142cf695c42e21805c7c332ac36bc09334a32251d2f27000`. Callers cannot replace
the Design IR, render tree, comparison policy, project build, task, dependency, path, provider, or
network boundary.

The frozen positive denominator maps all four authored nodes and requires 27/27 checks: exact node
keys; declared parent, child, and sibling order; `Column`, `Text`, `TextField`, and `Button` kinds;
the field and button roles; visibility; exact `Welcome` and `Continue` text; containment; vertical
order; and the allowlisted single-child text-field wrapper with equal wrapper and semantic-host
bounds. It uses zero tolerance for facts that the Design IR actually declares and has no aggregate
similarity score. The expected comparison fingerprint is
`ad5831b8af7895b85f84651e23284555a54911696868f70c70829974f7a50f31`. Separate semantic-text
and sibling-order mutations must downgrade evidence to `rendered` with category-specific
diagnostics.

This contract explicitly does **not** compare the `Email address` placeholder because the accepted
render-tree properties do not expose it. The resolved screenshot IR also declares no dp size or
padding modifiers, so containment and order are real runtime geometry evidence but not a claim that
rendered nodes match the screenshot's source pixel regions or exact source geometry. Pixel or
perceptual similarity, style, color, typography, draw order, accessibility traversal, state
mutation, event execution, focus, interaction behavior, and responsive configurations remain
outside the denominator.

The contract-only gate verifies 1/1 positive denominator and 2/2 fail-closed mutations. Phase 0 now
contains 13 schemas, 57 metrics, 60 cases, 57 fixture-backed cases, and three screenshot-comparison
fixtures; Node 25.6.0 passes 181/181 AI-tooling tests. `verifyAiScreenshotComparison` is part of
`qaQuick`, but the public tool still exposes only `generate`, `compile`, and `render`: activation is
intentionally frozen as `publicCompareMode = false` and `implementation = false` until the adapter
can reproduce this exact result.

The offline package remains at 60 files and has 1,762,156 declared bytes plus a 314,886-byte
archive, SHA-256 `bcae69502515df08617a5a2b1b92e8086d0df43e5699dbb8276711fc24a471e8`. Relative to the
screenshot-render implementation, the tooling README adds 555 declared bytes (+0.03%) and 173
archive bytes (+0.05%); no runtime dependency or executable contract file enters the package. The
distribution gate passes 2/2 reproducible builds, offline install/uninstall, license inventory,
both MCP protocol eras, all prior installed screenshot and XML compile/render denominators, and
both XML layout comparisons. The focused quality-build suite passes seven tasks, and the combined
comparison, documentation, development-tooling-isolation, and release-intent gate passes 22
actionable tasks, with eight executed and 14 up-to-date.

No published ViewCompose artifact, public/protected API, Android runtime, application process, or
provider boundary changes, so this slice needs no Maven release changeset or module-manual update.
This is **improved** comparison precision and claim honesty with **no material runtime behavior
change**. The next action is to implement a source-bound `compare` mode, reproduce the exact
comparison through CLI, MCP, and the installed package, and keep pixel metrics separate.

### Implementation evidence — screenshot semantic and structural geometry comparison

`generate_screenshot_viewcompose` now exposes `compare` beside `generate`, `compile`, and `render`.
It regenerates the screenshot-derived Kotlin and report from the exact resolved result, enters the
same source-free generated Preview profile with the same explicit state/callback bindings, and
invokes comparison only after rendering succeeds. The dispatcher supplies no independent Design
IR, render tree, or comparison policy: comparison receives the Design IR already accepted by
generation plus the Preview data and evidence returned in that request. Render failure returns no
comparison; comparison failure preserves the aggregate render fingerprint and `rendered` evidence;
only 27/27 passing checks publish the comparison fingerprint and `compared` evidence.

The compare-specific generation request and report fingerprints are
`c27e01b9980e5667ee526c22541d0eb4ccc59affd2004473453b36ec19c3bd9b` and
`2e1014bdfee846643799f3e75e7c7d68f6e62cd957aec3d81f264185fda86c35`. The accepted result maps
4/4 Design IR nodes, passes 27/27 required checks with zero failures or not-applicable checks, and
reproduces comparison fingerprint
`ad5831b8af7895b85f84651e23284555a54911696868f70c70829974f7a50f31`. A second complete call
revalidates the exact PNG and render-tree artifacts through a stable cache hit. The semantic-text
and sibling-order mutations each return their frozen diagnostic and remain at `rendered` evidence.

Node 25.6.0 passes 185/185 AI-tooling tests, including adapter evidence upgrade/downgrade, public
argument schema, shared dispatcher, and direct CLI/MCP semantic parity. Phase 0 remains at 13
schemas, 57 metrics, 60 cases, 57 fixture-backed cases, and three screenshot-comparison fixtures.
The dedicated Gradle gate reproduces 1/1 exact comparison, 1/1 cache hit, and 2/2 fail-closed
mutations. The distribution contract classifies `generate_screenshot_viewcompose:compare` as
source-bound, so installation may not silently fall back to the package's own source tree.

The offline package remains at 60 files with no runtime dependency. It now contains 1,763,721
declared bytes and a 315,363-byte archive, SHA-256
`cb5057892826b402cf4cadbf65495cf86573fa0dce5f1ae0d0f65000681b64cc`. Relative to the frozen
comparison contract, declared bytes increase by 1,565 (+0.09%) and archive bytes by 477 (+0.15%).
The installed CLI reproduces the exact comparison, and the installed modern MCP path completes the
same source-bound compare request; both protocol eras retain the same thirteen-tool catalog. The
distribution gate passes 2/2 reproducible builds, offline install/uninstall, license inventory, all
prior compile/render/compare denominators, and the new installed screenshot comparison in 1 minute
7 seconds (15 actionable tasks, one executed and 14 up-to-date).

The first installed MCP comparison correctly failed with `VC-AI-PREVIEW-START-FAILED` because the
new source-bound call had not passed an explicit source root into the installed server process. The
distribution verifier now supplies `VIEWCOMPOSE_SOURCE_ROOT` for that call instead of allowing an
implicit package-directory fallback; the repeated installed MCP comparison then passed. The final
combined screenshot render, comparison, distribution, documentation, development-tooling
isolation, and release-intent gate passes 24 actionable tasks, with ten executed and 14 up-to-date.

The evidence boundary is unchanged from the frozen contract: placeholder text, source screenshot
regions, exact source geometry, pixels, style, color, typography, draw order, accessibility
traversal, state/event behavior, focus, interaction, and responsive configurations remain
unclaimed. This is **improved** closed-loop semantic and structural validation with **no material
Android runtime behavior change**. The next action is to define a separate pixel/perceptual metric
and bounded repair contract without weakening these exact checks.

### Contract freeze — screenshot pixel-reference eligibility and exact metrics

Screenshot pixel comparison v1 freezes a separate gate after semantic and structural comparison.
It does not reinterpret the original inference image as a visual golden. A reference is eligible
only when its screenshot preprocessing request and result reproduce exactly, contain no redaction,
cover the full rendered viewport, and match the accepted render in width, height, density, font
scale, locale, layout direction, `sRGB` color space, straight alpha, upright orientation, zero
system-bar insets, and crop coordinates. A passing semantic comparison from the same render is
mandatory. Callers cannot supply a comparison policy or artifact path.

The accepted infrastructure reference is the 1079×2339 rendered PNG re-entered through canonical
screenshot preprocessing at density 2.625, font scale 1, `en-US`, and LTR. Its preprocessing
request fingerprint is
`06ded39bf3588193305ba1574c43ca3a6b6d0ff9c4cd19ec3e12eb75afdefefd`; its canonical result and
PNG fingerprints are `e874a198d57e64645472dc11dac8e82df35e11117869dd616d33c93a311eb091` and
`69ac5adde66e6f5725a0258987f7f635cb7be333839536f06c0ae6a2ff0596e2`. The render PNG retains
fingerprint `072787b8fa78026425577e7159494b9841850c4366ac1aa62010b4342919e5fd`; differing encoded PNG
bytes are permitted only because preprocessing deterministically strips metadata and re-encodes
the same RGBA image. The implementation denominator is 2,523,781 pixels with zero dimension or
channel tolerance. It will report exact pixel ratio, mismatched pixels, RGBA mean absolute error,
RGBA root mean square error, and maximum channel delta separately; no aggregate similarity score
exists.

The old 16×24 inference wireframe is explicitly ineligible because its viewport and density differ
and one user-declared redaction is present. Missing semantic evidence and changed reference output
identity are separate fail-closed denominators. The gate verifies 1/1 eligible reference and 3/3
ineligible cases, while `publicPixelCompareMode = false` and `implementation = false` prevent the
contract from being mistaken for executed pixel evidence. Perceptual similarity, cross-device or
cross-renderer equivalence, font equivalence, motion, interactions, design intent, aesthetic
quality, and automatic repair remain unclaimed.

Phase 0 now verifies 14 schemas, 60 metrics, 64 cases, 61 fixture-backed cases, and four
screenshot-pixel fixtures. Node 25.6.0 passes 186/186 AI-tooling tests. The new
`verifyAiScreenshotPixelComparison` task is part of `qaQuick`. The offline package ships the result
schema as its 61st file and has no new runtime dependency. It contains 1,770,597 declared bytes and
a 316,305-byte archive, SHA-256
`41dd31d8630e5f7c022b960010b9ffbdd252c8ad1d4fe1d268f0ac7c2514d209`. Relative to the semantic
comparison implementation, this adds one schema file, 6,876 declared bytes (+0.39%), and 942
archive bytes (+0.30%).

The distribution gate passes 2/2 reproducible builds, offline install/uninstall, SPDX/license
inventory, both MCP protocol eras, and every prior installed screenshot and XML denominator. The
focused quality-build suite passes seven tasks. The combined pixel-contract, distribution,
documentation, development-tooling-isolation, and release-intent gate passes 23 actionable tasks,
with nine executed and 14 up-to-date.

No published ViewCompose artifact, public/protected API, Android runtime, application process, or
provider boundary changes, so this slice needs no Maven changeset or module-manual update. This is
**improved** visual-claim integrity and eligibility coverage with **no material Android runtime
behavior change**. The next action is to implement the frozen exact comparator, prove decoded RGBA
identity and mismatches through CLI/MCP and the installed package, and only then freeze bounded
repair behavior.

### Implementation evidence — exact screenshot RGBA comparison

`generate_screenshot_viewcompose` now exposes `compare-pixels` after `compare`. The mode requires
the exact canonical preprocessing request/result pair in addition to the resolved screenshot result
and explicit Preview bindings. It regenerates and source-binds the same Kotlin, renders in the fixed
Preview lane, and must first reproduce all 27/27 semantic and structural checks. Pixel comparison is
never reached when rendering or semantic comparison fails.

The comparator reproduces the reference preprocessing result, rejects any changed lineage or
redaction, and requires exact viewport, density, font scale, locale, layout direction, `sRGB`,
straight alpha, orientation, system-bar, and crop identity. It then reopens only the contained
regular rendered PNG, rejects symbolic links and changed bytes, and uses the same strict bounded
non-interlaced 8-bit RGBA decoder as screenshot preprocessing. The preprocessing-compatible limit
is 1,310,720 compressed bytes, 16 MiB decoded bytes, and 4,194,304 pixels per image. Cancellation is
checked before and during reference reproduction, decoding, artifact reads, and channel comparison.

The accepted 1079×2339 denominator compares 2,523,781 pixels and 10,095,124 RGBA channels. All
pixels match at zero tolerance: exact pixel ratio 1, zero mismatched pixels, zero RGBA mean absolute
error, zero RGBA root mean square error, and zero maximum channel delta. The comparison fingerprint
is `5ac4341b880376f4f7c4e54c316a115d5d2ba448b8502d4cafdc76a50c875c5b`. The pixel-specific
generation request and report fingerprints are
`7dca8567dfc551fc1ea3e708535b361a783ec805c466ce8655d1a657ab5d6a8b` and
`98599de109dcc98ff978326bf9a906dc9b131549f2dce665cc04639adce61c78`. A second end-to-end run
revalidates the content-addressed artifacts through a stable cache hit.

Four fail-closed denominators remain separate: the configuration-mismatched redacted wireframe,
missing semantic evidence, changed canonical reference identity, and one red-channel unit changed
in the render. The last case reports exactly one mismatched pixel and maximum channel delta 1. It
does not collapse that result into a perceptual or aggregate score. Pixel mismatch preserves the
accepted render fingerprint and `rendered` evidence; only an exact pass publishes the pixel
comparison fingerprint at `compared` evidence.

Phase 0 remains at 14 schemas and 60 metrics and now contains 65 cases, 62 fixture-backed cases,
and five screenshot-pixel fixtures. Node 25.6.0 passes 194/194 AI-tooling tests. The dedicated gate
reproduces 1/1 exact comparison, 1/1 cache hit, and 4/4 fail-closed denominators. Installed CLI and
modern MCP calls reproduce the same pixel fingerprint after explicit
`VIEWCOMPOSE_SOURCE_ROOT` binding, while both MCP protocol eras retain the same thirteen-tool
catalog.

The dependency-free offline package now contains 62 files and 1,789,505 declared bytes; its
320,125-byte archive has SHA-256
`b58ad3bad5b58e96e00b1ed819f017496fd9c0d8c5d24a6685ffac7fdf107eb3`. Relative to the frozen
pixel contract, this adds the comparator as one file, 18,908 declared bytes (+1.07%), and 3,820
archive bytes (+1.21%). It adds no runtime dependency or provider boundary.

The first combined distribution run exposed a verifier-only timeout classification gap:
`compare-pixels` still received the 10-second static-request budget and correctly returned
`VC-AI-PIXEL-CANCELLED` when a cache replay exceeded it. The verifier now classifies
`compare-pixels` with the existing source-bound `compile`/`render`/`compare` 120-second budget.
The repeated distribution gate passes 2/2 reproducible builds, offline install/uninstall,
SPDX/license inventory, both MCP protocol eras, and every prior plus exact-pixel installed
denominator. The focused quality-build suite passes seven tasks, with two executed and five
up-to-date. The final combined pixel, distribution, documentation, development-tooling-isolation,
and release-intent gate passes 23 actionable tasks, with nine executed and 14 up-to-date.

No published ViewCompose artifact, public/protected API, Android runtime, or application process
changes, so this slice needs no Maven changeset or module-manual update. This is **improved** exact
visual evidence, artifact integrity, cancellation, installed transport coverage, and claim honesty
with **no material Android runtime behavior change**. Perceptual similarity, cross-device or
cross-renderer equivalence, font equivalence, design intent, aesthetic quality, interaction,
motion, and repair remain unclaimed. The next action is to freeze a bounded repair contract whose
iterations cannot bypass compilation, semantic, structural, exact-pixel, or safety failures.

### Contract evidence — bounded screenshot repair

The provider-offline screenshot repair contract is now frozen before implementation. It permits at
most five reason-coded attempts over typed Design IR patches derived from the accepted resolved
result; it does not accept caller-supplied Kotlin, arbitrary project source edits, provider calls,
network access, symbolic-link traversal, inspected-project build logic, automatic threshold
relaxation, or reference mutation. No public `repair` mode is exposed at this stage.

Every candidate is evaluated in fixed `safety` → `compilation` → `render` → `semantics` →
`structure` → `exact-pixels` order. The first failing gate owns the repair reason and prevents
later gates from running. Candidate acceptance requires strict improvement at that gate, while
every previously passed deterministic gate must remain passed. Repeated candidate or change
fingerprints terminate as oscillation; a regression terminates rather than silently rolling
forward. Pixel evidence is kept as separate exact counts and cannot override any earlier failure or
become an aggregate score.

The result schema retains initial, attempted, and final candidate fingerprints; gate evidence;
reason-coded change fingerprints; accepted/rejected dispositions; termination reason; and a safe
`incomplete`, `blocked`, or `cancelled` result when convergence is not established. The frozen
zero-iteration golden converges because all six gates already pass and has repair fingerprint
`a6f92b031f387d30eea9d52ed84b91182149751dfb72e8603d5a4de1ba99d9ee`. Five fail-closed
denominators cover a pixel mismatch with no eligible typed change, semantic regression, candidate
oscillation, exhaustion at five iterations, and an initial safety failure.

Phase 0 now verifies 15 schemas, 64 metrics, 71 cases, 68 fixture-backed cases, and six screenshot
repair fixtures. The focused contract gate reproduces 1/1 zero-iteration convergence and 5/5
fail-closed stops, while Node 25.6.0 passes 195/195 AI-tooling tests. The dependency-free offline
package now contains 63 files and 1,798,448 declared
bytes; its 321,255-byte archive has SHA-256
`46930ae893be74549e98073715b5249b6d783a4b809e22ee349b4c611e07fcba`. Relative to the exact-pixel
implementation package, the schema-only distribution addition is one file, 8,943 declared bytes
(+0.50%), and 1,130 archive bytes (+0.35%), with no runtime dependency or provider boundary. The
combined repair, Phase 0, reproducible distribution, documentation, development-tooling-isolation,
and release-intent gate passes 24 actionable tasks, with 12 executed and 12 up-to-date.

No published ViewCompose artifact, public/protected API, Android runtime, or application process
changes, so this contract slice needs no Maven changeset or module-manual update. This is
**improved** repair measurability, failure honesty, and resource/safety bounding with **no material
Android runtime behavior change**. Automatic repair, arbitrary source mutation, accessibility
completeness, perceptual similarity, interaction, animation, design intent, and universal
convergence remain unclaimed. The next action is to implement the frozen deterministic orchestrator
and reproduce all contract outcomes before considering a public tool mode.

### Implementation evidence — bounded screenshot repair orchestrator

The packaged provider-offline repair core now executes the frozen state machine without exposing a
new CLI or MCP mode. It accepts one schema-valid initial candidate, a typed patch producer, and a
deterministic candidate evaluator. Patch input is limited to `replace-field`,
`replace-modifier-argument`, `replace-node-kind`, and `reorder-children` operations over stable node
IDs. It rejects expression values, unknown or duplicate operation targets, more than 64 operations,
more than 262,144 encoded patch or candidate bytes, more than 10,000 non-pixel checks, and any
changed or malformed patch/evaluation fingerprint before accepting evidence.

The orchestrator short-circuits an initial safety failure and then owns a maximum of five attempts.
It propagates cancellation before and after both injected boundaries; records each accepted or
rejected attempt; rejects repeated change, candidate, or Design IR fingerprints as oscillation;
rejects any regression of a previously passed gate; and accepts a candidate only when it strictly
improves the first failing gate. For exact pixels, strict improvement means equal compared-pixel
denominator, fewer mismatched pixels, and no larger maximum channel delta. A non-improving candidate
is retained only as rejected evidence and returns `incomplete`; the prior accepted candidate remains
the final result.

The implementation reproduces the exact zero-iteration golden fingerprint
`a6f92b031f387d30eea9d52ed84b91182149751dfb72e8603d5a4de1ba99d9ee` and all five frozen
fail-closed outcomes. Additional tests cover one-iteration exact convergence, a valid but
non-improving candidate, executable and duplicate patch rejection, cancellation, and a schema-valid
blocked result for invalid initial evidence. Proposal and evaluation are deliberately still
internal injected boundaries: the current public tool cannot trigger them, and no provider,
credential, network client, arbitrary Kotlin source, project source mutation, or inspected-project
build selection was added.

The dependency-free offline package now contains 64 files and 1,816,541 declared bytes; its
325,028-byte archive has SHA-256
`499415ece0b68487f78b58b17f91154ef59b817e923d4ae11f3e397274d72fb5`. Relative to the frozen
repair-contract package, the internal orchestrator and its packaged documentation add one file,
18,093 declared bytes (+1.01%), and 3,773 archive bytes (+1.17%). It adds no runtime dependency or
provider boundary. Node 25.6.0 passes 206/206 AI-tooling tests. The combined repair, Phase 0,
reproducible distribution, documentation, development-tooling-isolation, and release-intent gate
passes 24 actionable tasks, with 12 executed and 12 up-to-date.

No published ViewCompose artifact, public/protected API, Android runtime, or application process
changes, so this implementation slice needs no Maven changeset or module-manual update. This is
**improved** deterministic repair control, evidence retention, cancellation, and safety with **no
material Android runtime behavior change**. A compile/render evaluator, repair policy that can
derive an eligible patch from structured findings, public repair mode,
perceptual comparison, and accessibility/interaction completeness remain unclaimed. The next action
is to connect the repaired candidate to existing source-bound compile, render, and comparison lanes
before any public activation.

### Implementation evidence — typed Design IR repair patches

The repair core now owns deterministic application of its four typed patch operations rather than
delegating mutation to an arbitrary source callback. Every request binds one immutable patch to the
exact canonical fingerprint of a resolved screenshot Design IR. The applier first validates the
complete IR, requires `source.kind: screenshot`, rejects unresolved/unsupported entries and any
expression value, bounds the tree to 1,000 nodes and depth 64, and rejects duplicate node IDs or
field names before cloning the candidate.

`replace-field` may replace only an existing `properties`, `semantics`, or `state` value;
`replace-modifier-argument` requires the exact existing modifier index and argument;
`replace-node-kind` accepts only the seven currently generated component kinds; and
`reorder-children` must be an exact permutation of the target's existing child IDs. Missing targets,
non-permutations, executable values, changed lineage, duplicate operation targets, and no-op values
fail before an output identity is published. The caller's IR is never mutated. The final candidate
is schema-validated again and exposes only canonical input/output Design IR fingerprints, immutable
change fingerprint, operation count, changed logical paths, and a compact output fingerprint.

The accepted title-text fixture has change fingerprint
`b1a8fb0a331181bd5cbc93230e7a8cf288163ed4285e4a876ee64c39ad231371`, repaired Design IR
fingerprint `442747e46f1a1bd35b0e4c5107a0b04d2962203819183cf4193ff1e37b46107d`, and output
fingerprint `ea77e571ae5977da628cdb40f12d83f664c2ed43b9375c42743ecd574098c219`. The focused gate
reproduces it twice and retains the original accepted IR unchanged. Seven applier tests cover all
four operations, deterministic replay, missing targets, invalid permutations, no-op changes,
executable content, changed lineage, unsupported input, and cancellation.

Phase 0 remains at 15 schemas and 64 metrics and now contains 72 cases, 69 fixture-backed cases,
and seven screenshot-repair fixtures. The focused repair gate now reproduces 1/1 zero-iteration
convergence, 1/1 typed patch golden, and 5/5 fail-closed orchestration denominators. Node 25.6.0
passes 213/213 AI-tooling tests.

The dependency-free offline package now contains 65 files and 1,824,726 declared bytes; its
326,747-byte archive has SHA-256
`5a617743fd1a71c605e445cddbdf28ad957d620e3615517d9541f7d214bda60d`. Relative to the internal
orchestrator package, the patch applier and packaged documentation add one file, 8,185 declared
bytes (+0.45%), and 1,719 archive bytes (+0.53%), with no runtime dependency or provider boundary.

The first combined acceptance run completed every patch, orchestration, and Phase 0 denominator but
the installed CLI's unrelated frozen screenshot Preview replay returned one
`VC-AI-PREVIEW-BUILD-FAILED`. An immediate full `verifyAiDistribution --rerun-tasks` replay with no
source change passed 2/2 reproducible builds, offline install/uninstall, SPDX/license inventory,
both MCP protocol versions, and all installed compile/render/comparison flows. The initial result is
therefore **inconclusive** transient build-environment evidence rather than a repair regression; the
clean rerun is accepted for this slice. Limitation and next action: if the Preview build failure
recurs, retain its worker log and add a dedicated reproducible denominator instead of masking it with
automatic retry.

No published ViewCompose artifact, public/protected API, Android runtime, or application process
changes, so this slice needs no Maven changeset or module-manual update. This is **improved** typed
mutation integrity, lineage, determinism, and failure localization with **no material Android runtime
behavior change**. Compilation, rendering, semantic/structural/pixel evaluation of the repaired
candidate, finding-to-patch policy, and public activation remain unclaimed. The next action is to
bind patched Design IR candidates to the existing source-bound evidence lanes.

### Contract correction — independent screenshot render gate

Before connecting the evaluator, the repair result contract advances from v1 to v2 to correct one
evidence-classification gap: generated Kotlin may compile while its source-bound Android Preview
still fails to render. Render is therefore an independent gate between `compilation` and
`semantics`; a render failure owns its own reason and short-circuits semantic, structural, and
pixel acceptance. The candidate patch format remains v1 because its typed mutation surface did not
change.

The corrected zero-iteration golden now records all six gates and has repair fingerprint
`54e68f7a8129bcf1da26053917a6ad769f71e32729ac416ea792f3d5fec610cb`. The focused verifier
reproduces 1/1 convergence, 1/1 typed patch, and 5/5 fail-closed outcomes; Phase 0 remains at 15
schemas, 64 metrics, 72 cases, 69 fixture-backed cases, and seven repair fixtures; and Node 25.6.0
passes 213/213 tests. The full offline distribution gate passes 2/2 byte-reproducible builds,
install/uninstall, license/SBOM inventory, both installed MCP protocol lanes, and every installed
compile/render/comparison flow. Documentation, development-tooling isolation, and release-intent
verification also pass in the same 23-task acceptance run.

The dependency-free package remains at 65 files and increases by 716 declared bytes to 1,825,442
bytes. Its 326,775-byte archive has SHA-256
`fd3d2ca0b1b1c47bc3faf8e3ffd3a51e91d4b2e36c25c961d01957889a899dc7`, 28 archive bytes above
the typed-patch baseline. No public ViewCompose API, published Android artifact, runtime path,
provider boundary, or release changeset is involved. This is **improved** failure localization and
claim accuracy with **no material Android runtime behavior change**. The next action remains the
deterministic candidate evaluator over the now-complete ordered gate set.

### Implementation evidence — source-bound screenshot repair candidate evaluation

The packaged candidate evaluator now connects the typed patch applier to the existing hermetic
Kotlin compiler, generated-Preview adapter, semantic/structural comparator, and zero-tolerance RGBA
comparator. It rebuilds the ephemeral resolution and generation lineage from the patched Design IR
instead of accepting caller-selected fingerprints. Compilation and Preview rendering are separate
gates: successful generation or compilation cannot upgrade a render failure. The accepted render is
then categorized into 12 semantic and 15 identity/structure/geometry checks; pixels run only after
both categories pass. A small binding factory adapts this evaluator directly to the orchestrator's
existing `evaluatePatch` boundary while candidate proposal remains injected and non-public.

The real source-bound denominator evaluates two candidates under the same 411 dp, density 2.625,
`en-US`, LTR, light configuration and the same immutable 1079×2339 reference. The unchanged
candidate passes all six gates, compiles to fingerprint
`7f42dcfd35573559c8c4c2bc62047a57085e01f4c78f2625299349b00440ae67`, renders to
`ba78a4047cad992e43b801a6b93a632a72543f383521172364d69b28fccf5076`, and retains the exact
2,523,781-pixel pass. The typed `Welcome` → `Hello` candidate also passes safety, compilation,
rendering, all 12 semantic checks, and all 15 structural checks, but correctly fails exact pixels:
5,102 of 2,523,781 pixels differ (0.2022%) with maximum channel delta 217. Its candidate evaluation
fingerprint is `8f0a65ef59dfe39b42aa25342994ae22cdbb5cede1cffcfaa0d6cadfa95586d9`.

The first exploratory patched run reused pre-existing Preview build output and reported 3,345
changed pixels. After deleting only the ignored, reproducible Preview harness build directory, a
cold rebuild reported 5,102; a second explicit cold rebuild and the Gradle task then reproduced
5,102 and every render/comparison fingerprint exactly. The cache-context result is therefore
**inconclusive** and is not accepted as a golden; the two matching cold rebuilds are the accepted
evidence. Limitation and next action: if clean rebuild evidence drifts again, preserve both render
artifacts and expand the content address to the missing build or environment input before accepting
another value.

Phase 0 now verifies 15 schemas, 64 metrics, 73 cases, 70 fixture-backed cases, and nine screenshot
repair denominators. Node 25.6.0 passes 226/226 tests, including one evaluator-bound orchestration
iteration, unavailable pixel evidence, cancellation inside injected boundaries, and all compile,
render, comparison, integrity, and distribution unit paths. The dedicated Gradle gates pass 2/2
source-bound candidates; the offline distribution gate passes 2/2 byte-reproducible package builds,
install/uninstall, SPDX/license inventory, both installed MCP protocol eras, and every prior
installed compile/render/comparison flow.

The dependency-free offline package now contains 66 files and 1,841,152 declared bytes. Its
330,190-byte archive has SHA-256
`12887e65602e31d4097281d8aa26776687fe2c6b02ff19d8a122dbd4cc1b7857`. Relative to the render-gate
correction baseline, the evaluator adds one file, 15,710 declared bytes (+0.86%), and 3,415 archive
bytes (+1.05%), with no runtime dependency or provider boundary. No published ViewCompose artifact,
public/protected API, Android runtime, or application process changes, so no Maven changeset or
module-manual update is required. This is **improved** repair evidence fidelity, failure
localization, cache honesty, and orchestration readiness with **no material Android runtime behavior
change**. Automatic finding-to-patch policy, arbitrary source repair, and public repair activation
remain unclaimed; the next action is a bounded deterministic proposer over the accepted structured
findings.

### Implementation evidence — content-addressed screenshot repair candidates

The candidate evaluator now retains one bounded evidence record for every schema-valid candidate
that reaches source generation. The v1 record binds the base and candidate resolution identities,
input and candidate Design IR identities, optional typed-change identity, complete six-gate
evaluation, immutable candidate Design IR, gate-specific diagnostic codes, structured layout
comparison, and structured exact-pixel comparison. It excludes generated Kotlin and PNG bytes,
has a 16 MiB internal ceiling, and is fingerprinted over canonical JSON. A session stores evidence
by candidate fingerprint and returns defensive clones, so a proposer can inspect deterministic
findings without mutating accepted evaluation state. Safety failures before a valid candidate
identity retain no partial evidence.

The real source-bound denominator reproduces two complete records. The unchanged exact candidate
has evidence fingerprint
`9325dcf8955a3edc492226a8b45da4825eaa08d132e15f7f142597d6a58fccec`, no diagnostic codes,
and all six gates pass. The typed `Welcome` → `Hello` candidate has evidence fingerprint
`26ff69bf21775b201d840668b5facf1d0041b553083bcd113008e769c157aa3b`; compilation, rendering,
12 semantic checks, and 15 structural checks pass, while its sole retained diagnostic is
`VC-AI-PIXEL-MISMATCH` for the frozen 5,102-of-2,523,781 exact-pixel difference. The dedicated
verifier independently validates the record schema and every nested Design IR, candidate
evaluation, layout comparison, and pixel comparison contract before recomputing the evidence
fingerprint and lineage.

Phase 0 now verifies 16 schemas, 64 metrics, 73 cases, 70 fixture-backed cases, and nine screenshot
repair denominators. Node 25.6.0 passes 228/228 AI-tooling tests, including immutable session lookup,
content-address recomputation, raw-source/image exclusion, compile/render short-circuiting, and all
prior orchestration denominators. The dependency-free package now contains 67 files and 1,848,041
declared bytes; its 331,563-byte archive has SHA-256
`d239b6c00a8210e12e906f2c003e71a726378d8089b27e5b179d0ce03430910c`. Relative to the
source-bound evaluator package, the evidence contract and implementation add one file, 6,889
declared bytes (+0.37%), and 1,373 archive bytes (+0.42%), with no runtime dependency or provider
boundary.

The candidate-specific Gradle gates pass 2/2 source-bound candidates, and the dedicated Phase 4
generated-Preview gate independently passes 2/2 exact cold renders plus 2/2 stable cache hits.
However, two full Gradle distribution replays and one direct distribution replay consistently
produced a different, still schema-valid and semantically exact XML Preview only after the same
persistent worker had rendered the screenshot target: output
`e4d6eabbe698970fd2faac2f3ff0b4363c4221bdff29c2965d107c6927a8f4f1`, PNG
`ccd9e8a1a8cb0ff3ff98dce4f1e7eda2f771eb98a44aa9fcfb6279dfc0d4b343`, and render tree
`03298986d5e5519227183a649d8ebe4ebd07e71a1e60f1d600ee685e83015929`. Removing only that
request's ignored render cache and running XML Preview in a cold worker restored the accepted
`6d2c8a5296db8cc95e5201092e40532f371f1d95621acd7bad343c913b4b9bab` output exactly. This
acceptance result is therefore **mixed**: candidate evidence is reproducible, while the combined
installed sequence exposes a pre-existing cross-build worker-isolation defect. The worker
compatibility fingerprint currently omits the build-manifest input fingerprint; the next action is
to bind those identities and add a screenshot-to-XML switch denominator before rerunning the full
distribution gate.

No published ViewCompose artifact, public/protected API, Android runtime, or application process
changes, so no Maven changeset or module-manual update is required. This is **improved** candidate
traceability, structured finding availability, immutable session state, and claim accuracy with
**no material Android runtime behavior change**. The record is internal and does not prove a repair
policy, perceptual equivalence, arbitrary source repair, or public convergence. The next action is
to close the cross-build Preview worker isolation gap, then implement a bounded deterministic
proposer that consumes only this accepted evidence and emits eligible typed Design IR patches.

### Implementation correction — exact-build Preview worker isolation

The repeated installed-distribution failure was a real cross-build isolation defect rather than a
candidate-evidence regression. Running the screenshot target before the XML target in one
persistent Preview worker produced a schema-valid, semantically exact XML comparison but changed
the rendered output to
`e4d6eabbe698970fd2faac2f3ff0b4363c4221bdff29c2965d107c6927a8f4f1`, its PNG to
`ccd9e8a1a8cb0ff3ff98dce4f1e7eda2f771eb98a44aa9fcfb6279dfc0d4b343`, and its render tree to
`03298986d5e5519227183a649d8ebe4ebd07e71a1e60f1d600ee685e83015929`. Removing only the XML
render cache and starting a cold worker restored the accepted output
`6d2c8a5296db8cc95e5201092e40532f371f1d95621acd7bad343c913b4b9bab`. The failure was therefore
history-dependent pixel and render-tree evidence, not a stale golden.

The Gradle plugin previously derived worker compatibility only from the narrow Layoutlib
environment and render-runtime identities. It now also includes the complete build-manifest input
fingerprint. Project bytecode or resource changes therefore retire the persistent worker before a
new render, while identical-build batches and repeated requests may still reuse it. The child class
loader remains fresh for every command; the stronger process key additionally contains AndroidX
and Layoutlib caches that live outside that loader. A focused unit denominator proves sensitivity
to each of build input, Layoutlib environment, and render runtime identity.

From an empty Preview harness, the full installed distribution now passes 2/2 byte-reproducible
package builds, offline install/uninstall, SPDX/license inventory, both MCP protocol eras, the
screenshot compile/render/layout/exact-pixel lanes, and then the XML, image XML, and layout-
dependency compile/render/comparison lanes. In particular, XML after screenshot again returns the
accepted comparison fingerprint
`470b4e23384479ff29528fe311058618b6ace6536465aeaf08bb477a10cc737d`. The 23 non-TestKit plugin
tests pass. Two complete 24-test plugin-suite attempts reached the functional TestKit case but
failed while writing Gradle lock/result files after the host volume exhausted its remaining space;
those attempts are **inconclusive** and are not treated as functional evidence. The cold installed
distribution is the accepted end-to-end cross-build denominator.

This correction changes production source in the published
`viewcompose-preview-gradle-plugin`, so
`release/changes/20260829-preview-worker-jvm21-resolution.json` classifies it as a fix and the owning
English/Chinese module manuals define the new reuse boundary. The result is **mixed** operationally:
cross-build determinism and evidence integrity are improved, while a changed build now pays cold
Layoutlib setup instead of risking process-cache contamination. Application runtime behavior is
unchanged. Any later attempt to recover cross-build warm reuse must first pass the same cold-start,
screenshot-to-XML pixel and render-tree denominator. The next action returns to the bounded
deterministic screenshot repair proposer.

### Implementation evidence — exact pixel localization and Design IR attribution

The exact RGBA comparator now emits a separate screenshot pixel-localization v1 result without
changing the existing pixel-comparison v1 fingerprint or replacing its independent metrics. The
same bounded pixel traversal records the global mismatch rectangle and assigns each changed pixel
to the deepest mapped Design IR node whose render bounds contain it. Coordinates use left/top
inclusive and right/bottom exclusive viewport bounds; equal-depth overlaps use stable node-ID
ordering, and pixels outside all mapped nodes remain in a separate unassigned denominator. The
result binds the exact pixel-comparison fingerprint and is content-addressed over canonical JSON.
It contains no generated source or image bytes and derives no repair value from location.

The real 1079×2339 source-bound denominator again evaluates the unchanged candidate and the typed
`Welcome` → `Hello` candidate under density 2.625, `en-US`, LTR, and the light theme. The unchanged
candidate remains an exact 2,523,781-pixel pass with localization fingerprint
`214c69da3a51a1ad521d3e605c681ab8d42e3787526fe95703b7399c80042716`. The changed candidate
has 3,345 mismatched pixels (0.1325%) with maximum channel delta 217 and localization fingerprint
`05ee59a64778fb9ca3727aa81cc6b27965ceb6cd4de86b906e558d493db28433`. Its global mismatch
rectangle is `(1, 8, 198 × 37)`: 2,267 pixels are attributed to `wireframe-title` within
`(1, 8, 111 × 37)`, 1,078 spill into the containing `wireframe-root` within
`(112, 18, 87 × 27)`, and zero are unassigned. The candidate evidence fingerprints are
`ce8555c98b3febf00cdd23978da5c5af685efcddb17c0f2110b229ec26a7605a` for the exact input and
`e0bd2617d05017bf9fa864139ecc03535b35a3b8b7bbbf491c28884be0c60068` for the changed input.

The prior 5,102-pixel result belongs to the earlier Preview worker/build context. After exact-build
worker isolation and fresh local Gradle state, the current 3,345-pixel result was reproduced once
with existing outputs and again after deleting only the ignored Preview harness build directory.
The absolute difference is -1,757 pixels (-34.44%), but it is **not** interpreted as a visual
improvement because the evidence context changed; the new pair of matching runs establishes the
current golden. One intervening attempt failed at the render gate with `No space left on device`.
It was rejected as **inconclusive** host-capacity evidence, sufficient space was restored by
removing only stale Gradle 8.13 daemon logs, and the same empty-harness run then passed. Host volume
headroom remains an operational limitation for future clean render lanes.

Node 25.6.0 passes 230/230 AI-tooling tests, including exact localization, one-pixel attribution,
deepest-node overlap, and explicit unassigned-pixel cases. The dedicated pixel gate passes 1/1
exact result, 1/1 stable cache replay, and 4/4 fail-closed denominators. The candidate gate passes
2/2 real candidates in both warm and empty-harness runs. The full installed distribution passes
2/2 byte-reproducible package builds, offline install/uninstall, SPDX/license inventory, both MCP
protocol eras, the packaged screenshot localization path, and all subsequent XML compile/render/
comparison flows. The dependency-free package now contains 68 files and 1,857,971 declared bytes;
its 333,603-byte archive has SHA-256
`d610f4f5af7b78469a24c4fde7b18928c9d96ed908ddbfb9afc8164e9d694795`. Relative to the prior
candidate-evidence package, localization adds one file, 9,930 declared bytes (+0.54%), and 2,040
archive bytes (+0.62%), with no runtime dependency or provider boundary.

This result is **improved** visual-failure localization, candidate traceability, deterministic
ownership, and installed-tool parity with **no material Android runtime behavior change**. It does
not prove that an attributed node caused every changed pixel, that a mismatch rectangle determines
a valid modifier/value patch, or that exact pixels express perceptual or design quality. The next
action at this slice boundary was to define the bounded proposer eligibility policy implemented
below and emit only typed Design IR patches that can be verified by the existing six-gate loop.

### Contract evidence — rollback-only screenshot repair proposer

The screenshot repair proposal v1 contract freezes the first evidence-to-patch boundary without
pretending that pixel localization supplies a target value. It accepts two complete,
integrity-verified candidate evidence records: a current candidate that passes safety through
structure but fails exact pixels, and a prior baseline with the same lineage and pixel denominator
that has strictly fewer mismatches and no larger maximum channel delta. The records must differ in
exactly one existing non-expression `properties` field, and the current localization must attribute
at least one mismatched pixel to that node. Only the exact typed baseline value may become one
`replace-field` operation. Caller targets, OCR/vision guesses, modifier and structure changes,
behavior/state/semantics changes, multiple differences, and novel mismatches have no eligible
proposal. Public repair activation remains false, and every eventual patch must re-enter the typed
applier and complete six-gate evaluator.

The frozen real denominator maps the accepted `Hello` regression back to the exact baseline
`Welcome` value with change fingerprint
`7a126542aa952fc46f0859d530d72c8fd7e93d268c696e5b514e4cc2c3f9f945` and proposal
fingerprint `47bffb223b1503cb603f77840ea46ec9ae375bc7efa5637c5a3635adbcecce68`. Six no-change
denominators cover an already exact candidate, an earlier failed gate, a non-improving baseline,
multiple differences, an unlocalized changed node, and localization without a baseline value
difference. Two invalid denominators cover evidence-integrity and lineage drift, and cancellation
has its own result. The contract verifier passes 1/1 supported rollback, 6/6 no-change, 2/2 invalid,
and 1/1 cancelled cases while explicitly reporting `implementation: false` at the contract-only
boundary.

Phase 0 now verifies 18 schemas, and Node 25.6.0 passes 231/231 AI-tooling tests. The
dependency-free package contains 69 files and 1,863,534 declared bytes; its 334,488-byte archive
has SHA-256 `3facf1c0273e6a4a4cf309f9c66d9c8679abdf9c29e1bac3bed0e394e541d549`. Relative to the
localization package, the frozen proposal schema and guidance add one file, 5,563 declared bytes
(+0.30%), and 885 archive bytes (+0.27%), with no runtime dependency or provider boundary. This is
**improved** repair-scope honesty, deterministic rollback eligibility, and fail-closed coverage with
**no material Android runtime behavior change**. At that contract-only boundary it did not yet
prove executable proposal output or end-to-end rollback convergence; the implementation evidence
below closes exactly those two claims.

### Implementation evidence — deterministic single-property regression rollback

The internal screenshot repair proposer now verifies each input against the candidate-evidence,
repair-evaluation, Design IR, layout-comparison, pixel-comparison, localization, and proposal
schemas before considering a change. It reproduces canonical evidence, Design IR, localization,
and proposal identities; reproduces compact evaluation, layout, pixel, and patch identities; binds
the retained layout nodes and paths back to the exact Design IR; reconciles layout check totals,
pixel denominators, node attributions, and unassigned pixels; and requires the same base resolution,
input Design IR, pixel-reference request/output/PNG, viewport, and interpretation on both records.
Malformed, oversized, internally inconsistent, cross-lineage, and cancelled input fails closed.

Eligibility is deliberately not a general visual-repair heuristic. The current candidate must pass
the first five ordered gates and fail exact pixels. The baseline must pass the same first five gates,
use the same exact reference and compared-pixel count, contain strictly fewer mismatched pixels,
and have no larger maximum channel delta. A bounded 1,000-node, depth-64 comparison permits exactly
one existing non-expression field in `properties` to differ; replacing that current value with the
baseline value must make the complete canonical Design IR equal to the baseline. The changed node
must also own at least one current mismatched pixel. The proposer then seals one `replace-field`
operation and runs it through the existing typed patch validator and applier before publishing the
proposal. No pixel, OCR, vision, model, caller target, aggregate score, or network path supplies the
value.

The real source-bound 1079×2339 denominator reproduces the exact baseline evidence fingerprint
`ce8555c98b3febf00cdd23978da5c5af685efcddb17c0f2110b229ec26a7605a` and the `Hello`
regression evidence fingerprint
`e0bd2617d05017bf9fa864139ecc03535b35a3b8b7bbbf491c28884be0c60068`. The proposer emits
only `wireframe-title.properties.text = Welcome`, with change fingerprint
`7a126542aa952fc46f0859d530d72c8fd7e93d268c696e5b514e4cc2c3f9f945` and proposal
fingerprint `47bffb223b1503cb603f77840ea46ec9ae375bc7efa5637c5a3635adbcecce68`. Rebased onto the
current `Hello` resolution, that emitted patch passes safety, compilation, render, all 12 semantic
checks, all 15 structural checks, and the complete 2,523,781-pixel comparison, reducing the current
3,345 mismatches to zero. The rollback evaluation fingerprint is
`020019c2483980dcbcd3d6c3ca5148228d6330a46f6ca9dc48d4acc849ffc7f3`; its complete evidence
fingerprint is `f655efb37838921c557fe0455a0424a311ed9847af3e7de273ed805236d8263c`.

The focused suite covers deterministic proposal replay, typed-applier equality, exact candidates,
earlier-gate short circuits, non-improving baselines, multiple property differences, unlocalized
changes, localization without a baseline value difference, evidence-integrity drift, base-lineage
drift, exact-reference drift, and cancellation. The dedicated real verifier passes 1/1 supported
rollback, 6/6 no-change, 2/2 invalid, and 1/1 cancelled contract denominators, then evaluates three
real candidates and proves the emitted rollback through all six gates. Public repair activation
remains false.

Node 25.6.0 passes 238/238 AI-tooling tests, and Phase 0 remains at 18 schemas, 64 metrics,
73 cases, 70 fixture-backed cases, and nine screenshot-repair fixtures. The dependency-free offline
package now contains 70 files and 1,886,105 declared bytes; its 338,952-byte archive has SHA-256
`83ad316c9fba96da952c6fef195b3a949b3247405bb859f23af8a795e256c619`. Relative to the
contract-only package, the internal proposer adds one file, 22,571 declared bytes (+1.21%), and
4,464 archive bytes (+1.33%), with no runtime dependency, provider, network, or public tool mode.

This is **improved** deterministic rollback capability, evidence integrity, and end-to-end repair
verification with **no material Android runtime behavior change**. It proves only recovery of one
known single-property regression against an accepted better baseline. It does not prove how a
baseline becomes trusted, that arbitrary localized pixels reveal causality, novel value inference,
perceptual equivalence, or safe unattended repair. The next prerequisite is an explicit contract
for accepted baseline provenance and human authorization before any CLI/MCP repair workflow can
bind this internal proposer to the orchestrator.

### Contract evidence — human baseline acceptance and rollback authorization

Screenshot repair authorization v1 freezes the trust handoff that must precede any executable
repair workflow. One baseline-acceptance attestation binds an identified reviewer and receipt to the
exact baseline evidence fingerprint, a 40- or 64-hex immutable Git commit, an exact-evidence-only
scope, and completed visual and semantic review. A separate repair-approval attestation binds an
identified approver and receipt to the exact current candidate evidence, proposal, and typed change
fingerprints for one application with unattended execution disabled. The enclosing record also
binds baseline/current Design IR identities and the canonical exact pixel-reference identity.

The policy denies credentials, provider calls, network access, non-metadata logs, authorization
reuse, and more than one application. It deliberately treats reviewer trust and pre-application
revocation as host responsibilities. Receipt values are purpose-bound opaque content addresses,
not signatures: v1 does not authenticate a person or receipt, decide that a source revision or
baseline is trustworthy, or turn a successful proposal into authorization. Public repair mode and
execution authorization both remain false.

The frozen real record accepts baseline evidence
`924f462673b39e8c9f00352f3517144d433894fde5d4985e20f62f448925babf` at source revision
`a2faf25dc206b428936a42b3d0872007371592b3`, approves current evidence
`f2b2b21f846ff99024fcb8a4d6bee129697da8b387802c6c086254febc47484c`, proposal
`2d77def7c5582719c648797d4aaaf3ae551e92a0c4cb7f1d7eb60dbdaba2aeee`, and change
`7a126542aa952fc46f0859d530d72c8fd7e93d268c696e5b514e4cc2c3f9f945`, and binds exact-reference
fingerprint `1df87bf9bc879ba5d809b1c9e3c5a4a051a1f4fa088ef3b5ec6edf7e648790e4`. Its authorization
fingerprint is `7ee3a6296b55b6ae58585ffba93527dcd49d372e6a3daf403eb9f95ce02ad859`.

The contract verifier passes 1/1 human-attested record, 10/10 invalid denominators, and 1/1
cancelled denominator. Invalid classes freeze baseline, candidate, proposal, change, and exact
pixel-reference lineage drift; movable source revisions; missing reviewers; unattended execution;
authorization-integrity drift; and credential-shaped fields. Phase 0 now verifies 19 schemas, and
Node 25.6.0 passes 239/239 AI-tooling tests. The dependency-free package contains 71 files and
1,891,779 declared bytes; its 339,834-byte archive has SHA-256
`6a5ad34cf5b9ad18cfda2f10ef365b44ccc3c9b877fddb3d5055e24695953d1e`. Relative to the proposer
package, the authorization contract adds one file, 5,674 declared bytes (+0.30%), and 882 archive
bytes (+0.26%), with no runtime dependency or public tool mode.

This is **improved** trust-boundary clarity and exact authorization lineage with **no material
Android runtime behavior change**. At the contract-only boundary it was not authenticated identity
infrastructure or an executable repair grant; the implementation below closes deterministic
validation while deliberately leaving both limitations in place.

### Implementation evidence — exact repair authorization validation

The packaged internal validator now consumes the complete baseline evidence, current evidence,
proposal, and authorization record. It validates the authorization and proposal schemas, byte
ceiling, typed patch, canonical fingerprints, immutable source-revision syntax, and distinct
purpose-bound receipts. It then re-runs the bounded proposer over the supplied evidence. Only an
exactly reproduced proposal may proceed to binding checks for baseline/current evidence, their
Design IR identities, canonical exact-reference identity, proposal, typed change, both reviewer
attestations, single-application limit, and unattended-execution denial.

The result is separately content-addressed and distinguishes `validated`, `invalid`, and
`cancelled`. Even a validated result carries `executionAuthorized: false`, external reviewer trust,
and unclaimed receipt authentication. The validator never applies the patch, contacts a provider or
network, accepts credentials, authenticates a person, checks host revocation state, or consumes an
authorization. Thus deterministic structural validation cannot silently become an execution grant.

The real source-bound gate evaluates the exact baseline and `Hello` regression, reproduces proposal
`2d77def7c5582719c648797d4aaaf3ae551e92a0c4cb7f1d7eb60dbdaba2aeee`, validates authorization
`7ee3a6296b55b6ae58585ffba93527dcd49d372e6a3daf403eb9f95ce02ad859`, and emits validation
fingerprint `ef6ee08150a287d334c799ca30c4b0d4bb3aa8d94839fd143bf30071ecd00b1f` with execution disabled.
The same run executes all 10 invalid mutations and one pre-validation cancellation: evidence,
proposal, change, and pixel-reference drift stay distinct from schema and authorization-integrity
failures.

Node 25.6.0 passes 243/243 AI-tooling tests, including deterministic validation replay, schema-valid
result identity, integrity versus lineage classification, ineligible proposal rejection, and
cancellation before proposal reproduction. Phase 0 remains at 19 schemas. The dependency-free
offline package now contains 72 files and 1,904,153 declared bytes; its 341,652-byte archive has
SHA-256 `5d155668d634a10da8a18cd843e5146f321c710043492c405edd82b1a4b3c649`. Relative to the
contract package, the validator adds one file, 12,374 declared bytes (+0.65%), and 1,818 archive
bytes (+0.53%), with no runtime dependency or public tool mode.

This is **improved** authorization integrity, proposal reproducibility, and claim separation with
**no material Android runtime behavior change**. Reviewer authentication, receipt revocation,
cross-process single-use consumption, patch execution, and public CLI/MCP activation remain
unclaimed. At that implementation boundary, the next prerequisite was the host interface that
would authenticate and consume a validated attestation exactly once; the contract below now freezes
that interface without implementing it.

### Contract evidence — trusted host repair grant lifecycle

Screenshot repair host grant v1 freezes that dynamic trust interface as a content-addressed request
and a separate host decision. The request binds validation, authorization, baseline/current
evidence, candidate Design IR, exact pixel reference, proposal, typed change, and immutable baseline
source revision identities. It also requires a named trust domain, out-of-band credential transport,
fingerprint-only logs, no tool-owned provider or network call, attended execution, and no public
tool mode.

A structurally granted decision must arrive through `trusted-host-callback-only`. It contains two
purpose-distinct authenticated principals and review receipts, two active revocation checks made
immediately before reservation, and one durable `atomic-single-use-reservation`. Attempt number and
maximum attempts are both one; reuse, retry after failure, caller-supplied decisions, credential
input, and unattended execution are all forbidden. The exact validation, authorization, candidate
evidence, proposal, change, and target Design IR identities are rebound into the grant. Reserving an
attempt is terminal even when a later patch application fails, avoiding a check-then-write reuse
window.

The checked-in request fingerprint is
`ab8134e2be383dbe8c2b376aceb172d2132f0268e0c4870999a682c9fc660dbd`; its synthetic granted
decision fingerprint is
`8f5953ee7fec99c15d446d3adb1877ef1dd95a2ff5dbffbab27de119d6974c2e`. The word
`synthetic` is material: a JSON file cannot authenticate its own host provenance. The contract
explicitly gives no authority to a decision loaded from a file, stdin, CLI argument, MCP argument,
or network payload. Only a future trusted callback boundary can supply such authority.

At contract-freeze commit
`a9e06c168746015902acb029a319075ee13bb53d`, the verifier passed 1/1 structurally valid synthetic
grant, 17/17 invalid denominators,
4/4 denied decisions, and 1/1 cancelled decision. It keeps authentication failure, revocation,
already-consumed state, policy denial, integrity drift, lineage drift, and malformed input distinct.
Phase 0 now verifies 20 schemas, and Node 25.6.0 passes 244/244 AI-tooling tests. The
dependency-free offline package contains 73 files and 1,916,933 declared bytes; its 343,045-byte
archive has SHA-256 `684f8991f1d4e9856dd099c170f818b3d20b9514d9edc0e0bf4bd3270c5dda25`.
Relative to the authorization-validator package, the host-grant contract adds one file, 12,780
declared bytes (+0.67%), and 1,393 archive bytes (+0.41%), with no runtime dependency or public tool
mode.

This is **improved** dynamic trust-boundary precision and fail-closed single-use semantics with
**no material Android runtime behavior change**. It does not implement or locally verify host
identity, authentication receipts, revocation checks, durable reservation, patch application,
failure recovery, or public execution. At this boundary the next prerequisite was an isolated
callback adapter plus a deterministic durable test host; the implementation below supplies those
two pieces without creating an executor.

### Implementation evidence — isolated trusted-host grant adapter

The packaged internal adapter accepts exactly one structurally validated authorization result and
its exact authorization record. It revalidates both schemas and content addresses, binds reviewer,
receipt, evidence, proposal, source-revision, reference, change, and Design IR identities, and then
builds the frozen host-grant request. The host reservation callback is retained in a private
process-local registry behind an immutable handle. Serializing that handle preserves only the trust
domain label and loses callback authority; extra `decision` input is rejected before the callback.
No file, stdin, CLI, MCP, or network-supplied decision can enter the trusted path.

The returned host decision is accepted only after schema, byte ceiling, content address, trust
domain, purpose-distinct principal and review-receipt, active revocation, unique host-proof receipt,
atomic reservation, attended-use, and complete repair-lineage checks pass. Host exceptions become
non-authorizing `host-failed` decisions. Cancellation before the callback makes no reservation;
cancellation or validation failure after the callback never retains its grant and does not imply
that a host reservation can be reused.

The deterministic file-backed test host creates one mode-`0600` reservation record with exclusive
creation and synchronizes it before returning the synthetic grant. Two concurrent requests produce
exactly one grant and one `already-consumed` denial. Reopening the same store through a new host
instance also denies replay. Separate tests reject serialized handles, caller-injected decisions,
validly rehashed lineage drift, changed validation identity, host failure, and cancellation before
and after the callback. The contract verifier now passes 1/1 synthetic grant, 17/17 invalid, 5/5
denied, and 1/1 cancelled denominators; its adapter replay reports one direct-callback grant, zero
replayed grants, and zero accepted serialized decisions.

Node 25.6.0 passes 251/251 AI-tooling tests, and Phase 0 remains at 20 schemas. The dependency-free
offline package contains 74 files and 1,928,701 declared bytes; its 345,328-byte archive has SHA-256
`c7981315ccf7760baee42d9fdc9619eaf9b6fa188bf8ff86dc66bc956d6d1425`. Relative to the
contract package, the adapter adds one file, 11,768 declared bytes (+0.61%), and 2,283 archive bytes
(+0.67%), with no runtime dependency or public tool mode.

This is **improved** callback isolation, decision integrity, and single-use replay resistance with
**no material Android runtime behavior change**. The test host demonstrates storage semantics; it
does not authenticate real people or constitute a production host. No patch is applied, no source
or Design IR is persisted, no execution receipt exists, and public CLI/MCP repair remains disabled.
At this implementation boundary, the next prerequisite was a terminal outcome contract covering
success, failure, cancellation, and unknown effects after reservation; the contract below supplies
that boundary without adding an executor.

### Contract evidence — terminal execution outcomes and receipts

Screenshot repair execution outcome v1 freezes the boundary after a trusted host has atomically
reserved one authorization. Each outcome binds the exact host-grant decision and request,
authorization, proposal, typed change, input Design IR, reservation receipt, and trust domain. Its
attempt record is always consumed, attempt one of one, attended, terminal, non-reusable, and
non-retryable. The executor profile is restricted to a typed in-memory Design IR patch with no
persistent source write, caller-supplied outcome, public mode, credential input, provider call,
tool network access, or content-bearing log.

Four disjoint schema branches keep effect claims honest. `applied` requires `committed` plus exact
result Design IR and patch-output fingerprints and is the only output-bearing state. `failed` and
`cancelled` require `not-committed` and null output identities. `indeterminate` requires `unknown`
and covers the crash window where an effect cannot be proved; it is still terminal and cannot be
executed again. Every branch carries a host-issued terminal receipt bound to the same trust domain
and reservation, and the outcome receipt must differ from the reservation receipt.

At contract-freeze commit
`2b87d2ad1dfc33bb35721d4ab7e75f2502850062`, the verifier passed 4/4 terminal outcomes and 24/24
invalid mutations. It separately rejected
fingerprint drift; every grant, request, authorization, proposal, change, Design IR, reservation,
and trust-domain mismatch; second or non-terminal attempts; retry or unattended flags; source-write,
public, or caller-outcome activation; applied/failed/cancelled/indeterminate effect mismatches;
receipt issuer or reservation drift; receipt reuse; and raw Design IR output. Exactly 1/1 outcome
exposes output fingerprints and 0/0 outcomes are retryable.

Node 25.6.0 passes 252/252 AI-tooling tests, and Phase 0 verifies 21 schemas. The dependency-free
offline package contains 75 files and 1,939,636 declared bytes; its 346,442-byte archive has SHA-256
`5f25a417c4c85c020f7f2e499319fb99449eb28a55fa4cd4de3dc34bd8d337e6`. Relative to the host-adapter
package, the contract schema and packaged explanation add one file, 10,935 declared bytes (+0.57%),
and 1,114 archive bytes (+0.32%), with no runtime dependency or executable registration.

This was **improved** terminal-state completeness, crash-window honesty, and replay resistance with
**no material Android runtime behavior change**. It does not implement an executor, authenticate a
production outcome receipt, make effect and receipt persistence atomic, recover an indeterminate
attempt, write application source, or activate CLI/MCP repair. The next prerequisite is an isolated
attended in-memory executor plus trusted-host terminal callback; the implementation below supplies
that boundary without adding a production store or public mode.

### Implementation evidence — attended in-memory repair execution

The host-grant adapter now attaches execution authority only to the exact granted object it returns
from a validated direct callback. The authority lives in a private process-local registry, is
consumed synchronously once, and is absent from structured clones or reconstructed JSON. A wrong
trust domain cannot consume it; concurrent consumers produce exactly one application; replay of the
original object is denied as already consumed. Calling the exported consumption primitive can only
burn an existing capability, never create or transfer one.

The packaged executor accepts exactly the authoritative grant, its bound screenshot Design IR, and
one typed patch whose content address equals the authorized change. It revalidates the grant schema,
decision fingerprint, trust domain, attended policy, input size, target Design IR identity, patch
schema, and change identity. Cancellation after reservation and invalid patch input both consume the
attempt and become terminal non-committed drafts. A successful application uses the existing typed
Design IR patcher entirely in memory; it does not write source, return raw Design IR to the host, or
expose the result before receipt validation.

A separate immutable host handle retains the terminal-record callback privately. The callback sees
only lineage, executor, effect, diagnostic, and content-address metadata. Its returned outcome must
pass the frozen schema, content address, byte ceiling, complete lineage, attempt, executor,
trust-domain, reservation, receipt, and exact effect checks. The host may conservatively downgrade
an uncertain effect to `indeterminate`. A callback exception or malformed receipt produces the
schema-checked `unrecordedResult`: effect `unknown`, no output, execution authorization false, and
retry forbidden. The consumed grant remains unusable.

At executor implementation commit
`1c55e7fc6ca2af7f8f44719f85d7c13658c36e20`, the verifier reported one direct application, zero
replayed applications, zero
accepted serialized grants, and zero outputs exposed without a terminal receipt. Dedicated tests
also cover authorized-change drift, invalid typed input, cancellation, trusted indeterminate
downgrade, missing or invalid receipts, concurrent consumption, serialized host handles, and
process-local authority loss. Node 25.6.0 passes 261/261 AI-tooling tests; Phase 0 remains at 21
schemas.

The dependency-free offline package contains 76 files and 1,953,674 declared bytes; its
349,027-byte archive has SHA-256
`1178c9d6a198edb6db0125e7431853530229f555ba4f8fc46cccbcf69c7b12cb`. Relative to the outcome-
contract package, this slice adds one adapter file, 14,038 declared bytes (+0.72%), and 2,585 archive
bytes (+0.75%), with no runtime dependency or executable tool registration.

This was **improved** authority isolation, exact-change enforcement, terminal receipt integrity, and
fail-closed output handling with **no material Android runtime behavior change**. It does not supply
a production durable terminal store, make effect and receipt persistence atomic, authenticate real
host receipts, reconcile uncertain storage writes, persist or publish the output Design IR, write
application source, or activate CLI/MCP repair. At this boundary the next prerequisite was a durable
idempotent terminal store that resolves one receipt per reservation without ever re-executing the
patch; the reference implementation below supplies that local storage boundary.

### Implementation evidence — durable terminal reference store

The packaged local reference store accepts one explicit absolute dedicated directory, rejects the
filesystem root and user home, creates the directory as mode `0700`, and requires it to remain a
private current-user-owned non-symbolic-link directory. Terminal record names derive only from the
validated 64-hex reservation receipt. Each outcome is schema-checked and content-addressed before
storage, and its deterministic outcome receipt binds the store identity, trust domain, reservation,
and exact draft fingerprint without claiming reviewer authentication.

Publication writes a complete mode-`0600` temporary file, synchronizes it, and atomically hard-links
that inode to the final non-overwriting record name before synchronizing the directory. A crash
before publication leaves no final claim; a crash after publication may leave a private temporary
link but the complete final record remains reconcilable. Cleanup failure never weakens or removes a
committed terminal record. Existing final records must remain bounded regular private files and
pass JSON, schema, fingerprint, trust-domain, reservation, issuer, and receipt checks on every read.

Repeating an identical terminal draft across independently opened store instances returns the exact
existing outcome. A different applied, failed, cancelled, or indeterminate draft for the same
reservation is rejected as a conflict and never overwrites the winner. Read-only reconciliation
accepts only the reservation receipt, executes no patch callback, and returns no outcome when no
record exists. Tests also reject an unsafe symbolic-link root and a record whose permissions have
drifted. The formal verifier now proves 1/1 durable outcome, 1/1 reopened reconciliation, and 1/1
idempotent replay in addition to the existing four terminal outcomes and 24 invalid mutations.

Node 25.6.0 passes 265/265 AI-tooling tests, and Phase 0 remains at 21 schemas. The dependency-free
offline package contains 77 files and 1,963,525 declared bytes; its 351,296-byte archive has SHA-256
`8f6fd4963900e250704b5980fcbab868d7c2df5c326cb7157f4a18fb130dd9e5`. Relative to the executor
package, the reference store and packaged explanation add one file, 9,851 declared bytes (+0.50%),
and 2,269 archive bytes (+0.65%), with no runtime dependency or executable tool registration.

This is **improved** local durability, idempotence, conflict safety, and restart reconciliation with
**no material Android runtime behavior change**. It is not a production multi-host store, reviewer
or receipt authenticator, transactional application-source writer, portability claim for arbitrary
filesystems, or automatic recovery service. It persists only terminal metadata; the successful
in-memory Design IR is still discarded after its fingerprint is recorded. The next prerequisite is
a content-addressed applied-result handoff that exposes that exact Design IR only after a validated
committed receipt, without writing application source or enabling public repair.

### Contract evidence — content-addressed applied-result handoff

Screenshot repair applied-result handoff v1 freezes the final process-local boundary after a typed
patch has produced an applied Design IR and the trusted host has returned a committed terminal
receipt. Handoff authority belongs only to the exact outcome object returned by attended execution
and the exact trusted host that recorded it. A serialized or reconstructed outcome or host handle
has no authority. Before any result is exposed, that host must read the terminal outcome again; the
reconciled record must remain schema-valid, content-addressed, committed, and byte-for-byte equal to
the accepted execution outcome.

The successful return separates one immutable content-addressed handoff receipt from the exact
frozen in-memory Design IR object. The receipt binds the outcome and terminal receipt, reservation,
trust domain, input and result Design IR, authorized change, and patch-output identities. The Design
IR itself remains subject to Design IR v1 validation and exact fingerprint reproduction. It is not
sent to the terminal host or persisted in the terminal store. Delivery is process-local and single
use; concurrent requests may produce at most one successful delivery.

The frozen denominator requires 1/1 successful handoff, 1/1 durable receipt re-read, 1/1 exact
object delivery, 0/0 accepted serialized authorities, 0/0 non-applied results, 0/0 mismatched
receipts, and 0/0 replayed deliveries. The contract and its twenty-third Phase 0 schema pass under
Node 25.6.0. This is **improved** output-lineage and delivery safety with **no material Android
runtime behavior change**. It does not implement handoff, persist the applied Design IR, write or
roll back application source, authenticate a production receipt, or activate CLI/MCP repair. The
next action is the isolated process-local implementation against the durable reference store.

### Implementation evidence — content-addressed applied-result handoff

The attended executor now retains its exact typed-patch Design IR only when the validated outcome is
`applied`, the effect is `committed`, both output identities match the patcher result, and the
original trusted host registered a direct reconciliation callback. The authority is attached to the
exact frozen outcome object in a private process-local weak registry. Ordinary in-memory receipt
hosts, non-applied states, structured clones, reconstructed JSON, and another host handle receive no
handoff authority.

Handoff marks the retained result in progress before its first asynchronous operation, preventing a
second concurrent consumer. It revalidates the live outcome, reads the terminal record again by the
exact reservation receipt, and requires the reopened outcome to reproduce its schema, content
address, trust domain, committed identities, and complete accepted bytes. The retained Design IR
then passes Design IR v1 and exact fingerprint validation again. The returned immutable envelope
contains one content-addressed receipt and the same frozen in-memory object; successful delivery
clears the registry's Design IR reference. A failed read may retry handoff without retrying execution,
while receipt drift, result drift, replay, and a concurrent request remain fail-closed.

On 2026-08-30, Node 25.6.0 passed 276/276 AI-tooling tests. The dedicated verifier reports 1/1
successful handoff, 1/1 durable re-read, 1/1 exact frozen-object delivery, 0/0 accepted serialized
authorities, 0/0 delivered non-applied results, 0/0 accepted mismatched receipts, 0/0 replays, and
exactly 1/2 successful concurrent requests. Phase 0 remains at 23 schemas. The dependency-free
offline package contains 81 files and 2,022,193 declared bytes; its 362,591-byte archive has SHA-256
`a5dbd013cf2a8a382d32a01c104418ff40675fef27519bc66bd51c57d074e473`. Relative to the contract
package, implementation changes only existing packaged adapters: `+7,373` declared bytes
(`+0.366%`) and `+1,408` archive bytes (`+0.390%`), with no runtime dependency or executable tool
registration. The full installed-package gate remains at 2/2 reproducible builds, 1/1 offline
install/uninstall lifecycle, both MCP protocol versions, all three Agent profiles, and the existing
compiled/rendered/compared screenshot and XML lanes.

The isolated root `qaQuick` acceptance passed 2,310 actionable tasks: 2,107 executed and 203
up-to-date, in 16 minutes 11 seconds. The comparison context is the immediately preceding
common-agent onboarding acceptance at 2,309 tasks, 2,061 executed, 248 up-to-date, and 10 minutes 51
seconds. This adds one task (`+0.0433%`); elapsed time increases by 5 minutes 20 seconds (`+49.16%`)
while the isolated worktree executes 46 more tasks because it has a colder output cache. The timing
is therefore not a performance comparison. The accepted functional conclusion is **no material
change** across the repository gate: every task completed and the new handoff gate added no failure.
The evidence is one isolated macOS/JDK 21 run rather than a clean Linux or device measurement. The
next action remains the frozen production-host and source-application transaction boundary, with no
source write or public repair activation inferred from this result.

This is **improved** durable-result correlation, output integrity, authority isolation, and replay
resistance with **no material Android runtime behavior change**. The evidence is local reference-
store and synthetic-host evidence; it does not authenticate production reviewers or receipts,
persist the Design IR across process restart, transactionally update application source, provide
rollback or crash recovery for source changes, or activate repair through CLI/MCP. No published
Maven artifact, publication input, public/protected framework API, or application process changed,
so no Maven release changeset or module-manual update is required. The next prerequisite is a frozen
production-host and source-application transaction boundary before any write or public activation.

### Accepted implementation — zero-friction public distribution and Agent onboarding

Agent client integration v2 makes source-free standalone operation the default for Codex, Claude
Code, and Cursor. Each profile retains an explicit source-bound configuration only for contributor
or legacy full-evidence lanes. The public lifecycle owns `init`, `doctor`, and `uninstall`; one
explicit `init` command must transactionally merge the exact installed MCP entry and six canonical
Skills into a physical absolute consumer project. Exact re-entry is idempotent. Unknown clients,
relative or symbolic-link roots, malformed configuration, different existing MCP ownership, and
different Skill bytes fail without partial writes. `uninstall` may remove only bytes and one MCP
entry that still reproduce the installed package identity.

The onboarding denominator is 3/3 deterministic profiles, 3/3 source-free standalone profiles,
zero manual configuration edits, no more than two primary commands from package installation to an
initialized project, exact Skill bytes, transactional rollback, an idempotent second initialization,
and a `doctor` result that distinguishes `standalone-ready` from unavailable source-bound evidence.
Client proprietary binaries, credentials, authentication, and UI automation remain outside the
package boundary.

AI tooling GitHub Release v1 binds `@viewcompose/ai-tooling` `0.1.0` to immutable tag
`ai-tooling-v0.1.0` and exactly three downloadable assets: the npm tarball, `manifest.json`, and
`SHA256SUMS`. The release workflow must build from the tagged commit, pass the installed package
gate, reproduce the package contract, create GitHub artifact attestations, and publish no mutable
`latest` installation URL. Users install the exact release asset; they do not clone ViewCompose or
run the distribution packager.

The implementation passes 3/3 standalone client profiles, 18/18 exact Skill copies, 3/3 idempotent
re-entry checks, 3/3 `standalone-ready` doctor checks, 3/3 clean uninstalls, and 3/3 path/conflict
safety rejections. The installed distribution gate reproduces 2/2 packages, completes 1/1 offline
install/uninstall lifecycle, and exercises 2/2 MCP protocol versions. The cold Preview lane changed
from 0/1 successful runs to 1/1 (`+100` percentage points) after declaring the exact transformed
classpath inputs; it completed 184 tasks (170 executed, 14 up-to-date) in 23 seconds. The complete
release gate then passed 186 tasks (3 executed, 183 up-to-date) in 3 minutes 28 seconds. This is
**improved** cold release readiness, onboarding cost, lifecycle safety, and evidence honesty with
**no material Android runtime behavior change**.

The cold-run evidence is one local macOS/JDK 21 producer graph, while the complete gate reused its
new Android outputs; neither time predicts a clean hosted Linux runner. Publication and attestation
remain unaccepted until the first tag-triggered workflow succeeds. The implementation changes no
Maven artifact, Android runtime, application source, or public screenshot-repair activation, so no
Maven release changeset is required. Compilation, Preview, and layout diagnosis stay visibly
source-bound until the next Phase 6A contract binds an exact released Knowledge Bundle to Maven
coordinates and an explicitly authorized consumer project. That consumer-project execution
contract is the next action.

Source-bound Preview verification also requires complete Git history in hosted CI. The generated
Knowledge Bundle records an immutable source revision, and that revision must resolve as an
ancestor of the checkout before Gradle execution is allowed. Therefore `qaPreviewWork` uses
`fetch-depth: 0`; a shallow synthetic pull-request merge checkout is not accepted as framework
identity evidence, and the runtime verifier remains fail-closed rather than weakening this check.

The final isolated-worktree `qaQuick` acceptance passed 2,311 tasks: 2,078 executed, 233 up-to-date,
and 15 minutes 12 seconds. The comparison context is the immediately preceding accepted full
onboarding run recorded above at 2,309 tasks, 2,061 executed, 248 up-to-date, and 10 minutes 51
seconds. This change adds two tasks (`+0.0866%`), executes 17 more (`+0.8248%`), and takes 4 minutes
21 seconds longer (`+40.09%`) while carrying a different local output-cache state and the complete
release distribution gate. The elapsed-time difference is therefore not accepted as a performance
regression. Functional conclusion: **no material change** to repository quality—the new release and
transactional-onboarding gates pass alongside documentation, tooling isolation, Release Intent,
R8, Lint, Paparazzi, unit tests, and samples. Limitations are one local macOS/JDK 21 run and no
tag-triggered Linux publication at that slice. The first attested Release and consumer-project
execution contract subsequently completed; the current next action is Execution Wave A above.

### Implementation evidence — bounded XML to Design IR

The first Phase 4 implementation uses a dependency-free scanner and tree builder rather than
executing Android resource tooling or application Gradle code. It enforces the frozen byte, depth,
node, attribute, and unsupported-fragment ceilings; accepts only a bounded repository-relative
source identity; checks tag matching and duplicate attributes; and rejects `DOCTYPE`, declared
entities, CDATA, unsupported processing instructions, unknown namespaces, duplicate Android IDs,
and malformed input before any generation claim. Attribute values are parsed into typed IR values,
not retained as an untyped property bag.

On 2026-08-29, Node 25.6.0 passed 81/81 AI-tooling tests in 1.33 seconds. The dedicated Phase 4 gate
matched 1/1 schema golden, 1/1 repeated deterministic conversion, 4/4 complete node provenance,
1/1 resource-preservation denominator, and 3/3 unsupported fixtures. The compiled quality-build
suite plus root `verifyAiDesignIr` passed 18 tasks (4 executed and 14 up-to-date) in 14 seconds.
Compared with the contract-only baseline, the result is **improved** deterministic migration and
unsupported-source localization with **no material runtime change** because the parser and gate are
downstream development tooling and execute no application code.

Limitations: this evidence proves only IR conversion for the four-element XML v1 subset. It does
not yet generate Kotlin, compile a converted result, render a migrated layout, inspect call sites,
resolve styles/resources, or support `include`, `merge`, `FrameLayout`, ConstraintLayout, lists,
custom Views, Data Binding, or behavior. The next action is deterministic IR-to-Kotlin generation
with the existing hermetic compiler as its acceptance boundary.

### Implementation evidence — deterministic Kotlin generation and compilation

The second Phase 4 implementation validates Design IR v1 again before generation, accepts only the
normalized five target node kinds, and rejects unknown properties, semantics, state, events,
modifiers, expressions, or resource types. It emits sorted imports, escaped Kotlin literals,
deterministically deconflicted parameter identifiers, stable keys, caller `String` resource
bindings, caller-owned `TextFieldState`, and a migration report that always requires resource,
state/restoration, ViewBinding, listener, adapter, and imperative-mutation review. Blocked IR never
receives Kotlin output.

The first real compile correctly rejected an assumed
`com.viewcompose.ui.foundation.TextFieldState` import. The generator was corrected to the canonical
`com.viewcompose.text.TextFieldState` declaration and compiled again; this demonstrates why
generation is not accepted on string comparison alone. The accepted cache-miss compile completed
in 10.85 seconds, produced two class files totaling 5,484 bytes, and returned class fingerprint
`f46767ea9e87195cc74237a2cac1b230dbe76fa94cc9107caf134dcedc9518cd`. The deterministic Kotlin
fingerprint is `6c4f6dafef9e0b4808eefab440d14e331b1a3b55bc8becff7a05d3669cc73be1`.

On 2026-08-29, Node 25.6.0 passed 86/86 AI-tooling tests in 1.34 seconds. The dedicated XML gate
matched 1/1 Kotlin golden, 1/1 resource migration report, and 1/1 hermetic compile. The compiled
quality-build suite plus both Phase 4 root tasks passed 19 tasks (7 executed and 12 up-to-date) in
32 seconds. Compared with IR-only conversion, the result is **improved** executable fidelity with
**no material runtime change** because generation and compilation remain downstream tooling.

Limitations: the generated function deliberately accepts resolved strings instead of directly
calling Android `stringResource`; the migration report makes that host-boundary work explicit.
Compilation proves API and type correctness in the frozen artifact lane, not runtime rendering,
resource resolution, call-site completeness, visual parity, or behavior. The accepted core is not
an application rewrite and does not remove XML or modify call sites.

### Implementation evidence — CLI, MCP, distribution, and migration workflow

The accepted converter now enters the same immutable request/result envelope as every other AI
tool. `convert_xml_to_viewcompose` requires callers to choose `generate` or `compile`: generation
remains dependency-free and standalone, while compilation requires the exact source checkout and
the existing hermetic compiler. CLI and MCP share one dispatcher and catalog. The installed modern
MCP lane executes the frozen conversion rather than merely listing its schema; legacy discovery
still returns the same ordered nine-tool catalog without implicit downgrade.

The source-identity gate rejects a mismatched checkout before Gradle. The accepted offline
lifecycle then uses the matching checkout to compile the generated login function and returns class
fingerprint `f46767ea9e87195cc74237a2cac1b230dbe76fa94cc9107caf134dcedc9518cd`.
The `viewcompose-convert-xml` consumer workflow requires generation review, compile evidence, final
code validation after integration, and explicit ownership of resources, state, listeners,
ViewBinding, and imperative call sites. It raises no automatic-conversion claim for unsupported
source.

On 2026-08-29, Node 25.6.0 passed 91/91 AI-tooling tests in 1.37 seconds and the client-neutral
workflow gate matched 6/6 exact contracts. Two clean package builds produced the same 39-file,
249,646-byte archive with SHA-256
`7b7c8c9f6a108effd992e30cb4ede0b256f0a84ea62663fb2f568cf00d6ea57b`. The offline
install/uninstall lifecycle, SPDX/license inventory, modern and legacy MCP versions, standalone XML
generation, mismatched-checkout rejection, and real XML compilation each met their complete frozen
denominator.

Compared with the source-only generator, the result is **improved** consumer interoperability and
compile-backed migration evidence with **no material runtime change** because the converter,
protocol adapters, skills, and distribution remain downstream development tooling. The evidence is
still limited to one supported four-node layout, three unsupported fixture classes, macOS, and the
local unpublished npm artifact. It does not prove application call-site completeness, resource
resolution, runtime behavior, rendering, visual parity, accessibility, Windows installation, or a
public-registry lifecycle. The current user-facing boundary and accepted evidence are documented in
[`tools/ai/README.md`](../../../tools/ai/README.md), which is already linked from the canonical
documentation index.

A candidate dedicated bilingual migration route passed generation, version-history routes, site
shell, and the 528-page accessibility audit but produced 49,373,569 non-API bytes, 195,354.6 bytes
above the unchanged 49,178,214.4-byte ceiling. The candidate was rejected instead of raising the
budget or duplicating the local tooling contract. This is **no material change** to public site
behavior and preserves the existing capacity ratchet; a future dedicated route must first recover
at least that measured headroom structurally. Consolidating the owning site-operations contract
then reduced the route-free 49,195,449-byte attempt by 24,110 bytes to 49,171,339, leaving 6,875.4
bytes under the unchanged ceiling. The 526-page bilingual build, accessibility audit, 133 immutable
API/manual routes, both search indexes, and all site budgets passed; accepted warm retries took
34.2–59.8 seconds. The accepted
representation is **improved** while XML-tool behavior remains **no material change**.

Repository-wide acceptance was initially **inconclusive** because the clean `qaQuick` run exhausted
the local volume after 2,059 actionable tasks (2,042 executed and 17 up-to-date). The failing
Preview Gradle plugin functional test reported `No space left on device` while writing its nested
build cache; its isolated retry then passed all 23 actionable tasks. A first incremental root retry
again exhausted the volume while serializing `viewcompose-preview` test results, so the resulting
`EOFException` and 25 temporary-directory failures were classified as the same environmental
failure rather than framework regressions. Only reproducible worktree outputs, Docusaurus caches,
the lockfile-reconstructible `website/node_modules` tree, and the corrupted test-task output were
removed. The recovered `viewcompose-preview` suite passed all 171 actionable tasks, and the final
root `qaQuick` passed all 2,278 actionable tasks (362 executed and 1,916 up-to-date) in 3 minutes.
The accepted conclusion is **no material regression** across the repository gate; low local disk
capacity remains an evidence limitation and should be provisioned before the next clean lifecycle.

### Phase 4A: Design IR and code generation

1. Freeze the tooling-only IR schema and provenance/unsupported representation from Phase 0.
2. Implement deterministic IR validation, normalization, stable serialization, and ViewCompose code
   generation using the current knowledge bundle.
3. Preserve stable resource references, IDs/test tags, semantic roles, event placeholders, source
   spans, and generation decisions.
4. Require generated Kotlin to pass Phase 2 compilation; renderable fixtures also pass Preview
   diagnostics before success.

### Phase 4B: XML to ViewCompose

1. Parse layout XML, includes, merge roots, style/theme references, dimensions, strings, colors,
   drawables, IDs, layout parameters, common containers, ConstraintLayout relations, visibility,
   content descriptions, and supported state selectors into IR.
2. Inventory call-site dependencies such as ViewBinding references, listeners, adapters, custom
   Views, data-binding expressions, and imperative mutations. Do not invent replacements for
   behavior that source analysis cannot establish.
3. Return ViewCompose code, required dependencies/imports/resources, an unsupported-semantics report,
   call-site migration checklist, source-to-output mapping, compile/render evidence, and optional
   explicit patch plan.
4. Add structural, resource-preservation, compile, render, accessibility, and selected screenshot
   goldens for a versioned XML fixture corpus.

The initial supported subset prioritizes `LinearLayout`, `FrameLayout`, common ConstraintLayout
relationships, `TextView`, `ImageView`, `Button`, simple lists, Material controls, and resource
references. Recycler adapters, arbitrary custom Views, data binding, animations, and behavior-heavy
screens remain unsupported until separately evaluated.

### Phase 4C: Compose mapping and deterministic analysis

1. Publish a versioned semantic mapping table for supported Compose concepts, including differences
   in layout, modifier ordering, state, effects, lifecycle, lazy content, navigation, theming,
   resources, accessibility, and Android interop.
2. Deliver guidance/skills before an automatic converter so agents can use Compose familiarity
   without pretending APIs are identical.
3. If activated by corpus evidence, parse a bounded Kotlin/Compose subset into IR with compiler or
   AST-backed semantics rather than regex replacement. Unsupported Kotlin, receiver ambiguity,
   custom composables, state ownership, and side effects remain explicit.
4. Expand `analyze_viewcompose` rules over typed structure and diagnostics for nesting, duplicate or
   conflicting modifiers, state-driven View recreation, lifecycle/resource leakage, accessibility,
   touch target, unit, theme, and performance risks.

### Acceptance gate

- Supported XML and Compose fixtures preserve declared structure, resources, semantics, and
  behavior placeholders at the frozen thresholds; every output compiles.
- Unsupported input is localized and reported. The tool never silently drops a custom node,
  expression, listener, resource, or state/effect contract.
- Generated output remains readable and reviewable; minimized line count or visual similarity alone
  cannot win the evaluation.
- `convert_xml_to_viewcompose` is added to CLI/MCP only after Phase 4B acceptance;
  `convert_compose_to_viewcompose` is added only after Phase 4C acceptance.

## Phase 5 — Prompt, screenshot, and Figma visual loop

### Purpose

Add AI-native input adapters only after deterministic IR, code generation, compilation, rendering,
and comparison are independently trustworthy.

### Deliverables

1. A provider-neutral request and response boundary that converts prompt, screenshot, or Figma
   design data into Design IR with per-node provenance, confidence, and unresolved questions.
2. Screenshot preprocessing and bounded image handling for density, crop, system bars, font scale,
   color space, transparency, and sensitive-content redaction.
3. A Figma adapter that preserves frames, auto layout, components/variants, variables/tokens,
   typography, assets, constraints, and explicit access provenance without importing provider
   credentials into the core.
4. An evaluation order that checks IR validity, compilation, render diagnostics, text/content,
   resources, semantics/accessibility, layout tree and geometry, then perceptual or pixel similarity.
5. A bounded repair loop with maximum iterations, reason-coded changes, before/after evidence,
   convergence/oscillation detection, and a safe incomplete result when the threshold is not met.
6. Human-reviewed phone/tablet, light/dark, locale, RTL, density, and font-scale corpus lanes with
   privacy and asset-license records.

### Acceptance gate

- A screenshot or Figma similarity score cannot override compile, semantic, accessibility,
  unsupported-content, or safety failure.
- Evaluation separates text/content correctness, structure, geometry, style, assets, and pixels so a
  single aggregate score cannot hide a product defect.
- Provider-offline deterministic stages remain fully usable; optional provider calls are explicit,
  cancelable, redactable, auditable, and excluded from default logs and caches.
- `generate_ui` enters MCP only after its orchestration and evidence contract is stable; it reports
  which stages and provider-dependent operations actually ran.

## Phase 6 — Stabilization, distribution, and evidence-gated positioning

### Purpose

Turn accepted experiments into a maintainable product rather than leaving a demo server tied to
repository internals.

### Deliverables

1. Stable tool/bundle/IR/protocol compatibility policy, deprecation window, migration tests, and
   released-version fixture matrix.
2. Reproducible packages, signed checksums, dependency and license inventory, vulnerability review,
   update/uninstall path, support matrix, and offline installation documentation.
3. Performance and reliability budgets for cold start, query, compile, render, conversion, memory,
   cache, cancellation, concurrent clients, and long-running server cleanup.
4. Privacy, security, logging, retention, disclosure, incident, and external-contribution policy for
   prompts, screenshots, design documents, source, diagnostics, and caches.
5. Public setup, tutorials, troubleshooting, API/tool reference, migration guides, limitations,
   release notes, and current Chinese mirrors for every active public page.
6. Longitudinal evaluation reports comparing the same corpus, versions, configurations, and model
   conditions. Model-dependent and deterministic results remain separate.

### Acceptance gate

- Supported released ViewCompose lanes reproduce their indexed API, samples, compile, render, and
  conversion results after tool upgrades.
- Security, resource, compatibility, and reliability gates run in CI or a documented scheduled
  environment with named owners and triage policy.
- Product language distinguishes “AI-ready,” “Agent-ready,” “AI-assisted,” and “AI-native.” The
  stronger “AI-first” statement is used only after the accepted longitudinal thresholds pass and
  limitations remain visible.

## Evaluation and verification matrix

Phase 0 freezes exact thresholds; later phases must at minimum own these gates:

| Concern | Required evidence | Earliest phase |
| --- | --- | --- |
| Knowledge freshness | Deterministic regeneration, schema validation, fingerprint drift, stable IDs, broken-link and removed-symbol tests | 1 |
| Retrieval correctness | Versioned positive/negative queries with top-k relevance and exact artifact/capability attribution | 1 |
| API hallucination | Fabricated, removed, wrong-artifact, wrong-overload, wrong-default, and wrong-version fixtures | 2 |
| Static diagnostic quality | Rule-level precision/recall, false-positive budget, severity/source-span goldens | 2 |
| Compilation | Pinned clean/failed snippets, dependency/resource lanes, normalized diagnostics, cache isolation | 2 |
| Render and diagnostics | Phone/tablet, theme, locale, RTL, font scale, failure, timeout, cancellation, and deterministic output lanes | 2 |
| Project safety | Traversal, symlink, secret, output-size, file-count, prompt-injection text, cancellation, and read-only tests | 2 |
| Runtime isolation | Dependency graph, release packaging, startup/hot-path, network, thread, allocation, and ADR-0009 gates | 2 onward |
| Protocol parity | CLI/MCP schema and semantic parity, compatibility negotiation, malformed request, output limits | 3 |
| Agent workflow | End-to-end retrieve/generate/compile/render/repair fixtures with step evidence | 3 |
| XML migration | Structural/resource/call-site preservation, unsupported honesty, compile, render, accessibility, screenshot goldens | 4 |
| Compose migration | Semantic mapping coverage, compiler/AST fixture support, state/effect differences, unsupported honesty | 4 |
| Visual generation | Content, semantics, structure, geometry, style, assets, pixel/perceptual, repair convergence, human review | 5 |
| Operations | Packaging, upgrade, compatibility, security, privacy, licenses, cleanup, concurrency, longitudinal reports | 6 |

Accepted evidence must record comparison context, absolute results, normalized change, conclusion,
limitations, and next action. Raw benchmark or evaluation output does not close a phase.

## Metric contract candidates for Phase 0

Phase 0 may adjust exact values, but it must explicitly accept or replace these candidate release
gates rather than leaving them qualitative:

1. zero stale or nondeterministic knowledge-bundle output for a supported source revision;
2. every indexed current capability has a valid artifact, signature source, compiled sample, and
   canonical link;
3. all deliberately fabricated or removed public symbols in the curated corpus are rejected before
   delivery, with no unexplained rejection of the canonical compiled samples;
4. every result labeled “compiled” is reproduced by the hermetic compiler in the declared lane;
5. every result labeled “rendered” includes a successful compile, configuration, runner version,
   output fingerprint, and diagnostics summary;
6. all path escape, arbitrary build execution, secret-read, resource-limit, timeout, cancellation,
   and cache-cross-lane adversarial fixtures fail closed;
7. zero AI-tooling dependency, provider SDK, background worker, network request, or recurring work in
   supported release-runtime artifacts when the tooling is absent;
8. supported converter fixtures compile at 100%, while preservation, unsupported-case, diagnostic,
   semantic, visual, latency, and resource thresholds remain separately visible;
9. model-dependent metrics report provider/model/configuration/date and cannot substitute for
   deterministic retrieval, compile, render, security, or compatibility gates.

## Documentation and ownership contract

Each phase updates the relevant active documents in the same implementation slice:

- architecture and ADRs own boundaries, isolation, versioning, trust, and IR decisions;
- `docs/ai/` owns user-facing Agent installation, MCP/Skill setup, client support, and first-use
  troubleshooting; `docs/tooling/` owns implementation operation, security, validation modes,
  MCP/CLI reference, and evidence interpretation;
- `docs/modules/` owns any separately published artifact's dependency, API, compatibility, and
  operational contract;
- `docs/migration/` owns XML and Compose supported subsets, semantic differences, generated output,
  call-site work, and unsupported cases;
- compiled samples own copyable code; generated AI data references them rather than duplicating
  uncompiled snippets;
- this plan and the unified roadmap own current phase/status and next action until archival;
- every active handwritten public English page receives a current Simplified Chinese mirror in the
  same change. Temporary plan details remain English-only under repository policy.

New or changed public/protected API must complete capability identity, structured impact
dispositions, Q level, applicable contract fields, canonical KDoc/Javadoc, compiled Q3 samples,
owning-module documentation, public API dumps, and immutable Changesets before merge. A tooling-only
change still records why published artifacts have no documentation or release impact.

## Ordering and parallelism constraints

1. Phase 0 blocks all implementation phases.
2. Phase 1 is the only knowledge source for Phases 2–6.
3. Phase 2 must accept static, compile, render, project safety, and runtime-isolation foundations
   before Phase 3 exposes them as supported public tools.
4. Phase 3 Foundation MVP may ship before conversion, but its evaluation and compatibility
   contracts cannot be weakened to accelerate Phase 4.
5. Phase 4A Design IR and generator block XML and Compose converters. XML is implemented and
   accepted before automatic Compose conversion.
6. Phase 5 may prototype fixture adapters after Phase 4A, but no supported visual tool ships until
   Phase 2 render evidence and Phase 4 generator evidence pass.
7. Phase 6 begins operational hardening during Phase 3 and closes only after every activated lane
   has longitudinal evidence. Unactivated later lanes do not block a deliberately scoped earlier
   MVP.

Within a phase, schema/test fixtures, deterministic implementation, documentation, and security
work may proceed in parallel only after their owning contract is frozen. Work does not bypass an
earlier acceptance gate by publishing an “experimental” alias on a supported path.

## Completion and archival

This plan is complete only when every activated lane has passed its declared gate, all published
and tool schemas have compatibility evidence, the active roadmap reflects the stable state, release
intent is closed, and no unresolved phase is hidden behind aggregate metrics.

If work intentionally stops after the Foundation or Migration MVP, split the unactivated later
lanes into a newly accepted plan, state the deferred triggers in the roadmap, and archive this plan
with the completed evidence. Do not keep a permanently active umbrella plan or convert incomplete
future ideas into implied current support.
