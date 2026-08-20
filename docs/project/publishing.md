# ViewCompose Publishing

This document defines the local release contract for ViewCompose Maven artifacts and the Android
Studio plugin. Remote Maven Central and JetBrains Marketplace uploads are deliberately separate
from local preparation so a release can be inspected before any irreversible publication.

## Maven identity and version model

The public Maven namespace is:

```text
com.viewcompose
```

Every published module owns its version and immutable API source revision in
[`gradle/viewcompose-publishing.properties`](../../gradle/viewcompose-publishing.properties). Equal
version values do not form one atomic release train: changing one entry releases only that
artifact and the artifacts whose dependency metadata must point to the new version.

The matching `module.<artifact>.sourceRevision` is a full 40-character commit SHA. Freeze the
module's source in one commit, then update the version and source revision together in a second,
metadata-only release commit. This two-step rule avoids self-referential hashes and guarantees that
generated Dokka line links resolve to immutable source matching the released module.

Ownership of `viewcompose.com` and the `com.viewcompose` namespace is verified in Central Portal.
Maven Central releases are immutable, so the namespace and coordinates must be reviewed before the
first upload and must not be treated as provisional afterward.

## Maven release tags

Every Maven Central publication must have an immutable Git tag. Because modules evolve
independently, ViewCompose does not use a repository-wide `v<version>` tag for Maven releases. Tag
each published artifact using:

```text
maven/<artifact-id>/<version>
```

For example:

```text
maven/viewcompose-runtime/0.1.0-alpha02
maven/viewcompose-navigation-core/0.2.0
maven/viewcompose-navigation-android/0.2.0
```

All tags in one Central deployment may point to the same metadata-only release commit. The tag
target must be that release commit—not the frozen source commit—because it is the exact repository
state that contains the published version and `sourceRevision`. The signed annotation must record
the artifact, version, and frozen source revision so both commits remain auditable.

The annotation contains exactly one `sourceRevision=<full-lowercase-40-character-SHA>` token. The
token may appear in the descriptive sentence shown below or on its own line; the planner accepts
both layouts but rejects a missing, malformed, uppercase, or duplicate token. This grammar keeps
already-published inline annotations valid without weakening provenance checks for future tags.

Create and push the signed annotated tag only after Central Portal reports the deployment as
`Published`. Do this before starting another release or changing publication metadata:

```bash
git tag -s "maven/viewcompose-runtime/0.1.0-alpha02" \
  <release-metadata-commit> \
  -m "Maven Central: viewcompose-runtime 0.1.0-alpha02; sourceRevision=<frozen-source-commit>"
git push origin "refs/tags/maven/viewcompose-runtime/0.1.0-alpha02"
git ls-remote --exit-code origin \
  "refs/tags/maven/viewcompose-runtime/0.1.0-alpha02"
```

A release is not operationally complete until every artifact tag exists on the remote and resolves
to the intended release commit. Never move, delete, or reuse a published tag. Never tag a dirty
worktree, a later documentation commit, or a commit whose checked-in metadata differs from the
published artifact. If Central publication fails, do not create the final release tag.

### First Maven Central release record

The first Maven Central release published all registered artifacts as `0.1.0-alpha01` from commit
`dc07ff6189eeab89644e3f9f792e1d7316240812` (`build: prepare Maven Central publishing`). No
Maven-specific tag was created at publication time. On 2026-08-04, after the release checkout was
reconstructed from the merged publishing branch and local release chronology, signed annotated
tags were created retrospectively for every registered artifact using
`maven/<artifact-id>/0.1.0-alpha01`. Every tag points to that commit and records
`sourceRevision=dc07ff6189eeab89644e3f9f792e1d7316240812` together with
`provenance=retrospective`.

The unrelated `navigation-demo-20260727`, `navigation-demo-20260727-r2`,
`navigation-demo-20260727-r3`, and `v0.1.0` repository tags were removed during that remediation;
none represented a Maven Central release. A retrospective release tag is allowed only when
independent artifact provenance identifies one exact release commit, and its annotation must state
that it was reconstructed. Never silently present a retrospective tag as one created during the
original publication.

### Registered first releases

An artifact must be registered before its first Central publication, but its final release tag is
created only after Central reports `Published`. Record that temporary state in
`release.unpublishedModules` inside `gradle/viewcompose-publishing.properties`. Only artifacts in
that explicit set may lack a Maven release tag. For them, the planner scans Changesets from
repository inception, requires a direct release declaration, and recommends the already-registered
initial version and source revision without advancing or duplicating documentation history.

After the first signed tag is pushed, remove the artifact from `release.unpublishedModules` in the
next repository change. Planning fails if an unmarked artifact has no tag, if a marked artifact
already has a tag, or if checked-in version metadata has advanced beyond the latest tag. These
failures distinguish a genuine first release from missing fetched tags and stale release state.

## Per-pull-request release intent

ViewCompose records release intent as one immutable Changeset per pull request rather than a
shared mutable list of changed modules. A production change to a published artifact is incomplete
until a new `release/changes/<unique>.json` file classifies it. The machine-readable schema is
[`release/changes.schema.json`](../../release/changes.schema.json).

```json
{
  "schemaVersion": 1,
  "summary": "Correct saved-state restoration after process recreation.",
  "changes": [
    { "artifact": "viewcompose-runtime", "impact": "fix" }
  ],
  "ignored": [
    {
      "artifact": "viewcompose-ui-foundation",
      "reason": "Only a test fixture changed; no published source or metadata changed."
    }
  ]
}
```

Direct impact is exactly one of `breaking`, `feature`, or `fix`. Contributors never write
`dependency`: the planner derives it when an independently published dependent must be republished
against a changed dependency. `ignored` is a reviewed exception for an automatically detected
artifact and requires a concrete reason. A `shared` entry may classify an ambiguous root build
input such as `build.gradle.kts` as release-neutral; otherwise the Changeset must declare the
artifacts affected by that shared input.

Automatic ownership covers each registered artifact's `src/main`, `src/commonMain`,
`src/androidMain`, `src/jvmMain`, and `src/release` trees, its publication-relevant module build
files, and `src/test/samples` because compiled API samples affect generated documentation. Ordinary
unit/instrumentation tests, Demo code, benchmarks, and handwritten documentation do not request a
Maven release by default. Root build files and the version catalog require explicit intent because
their effect cannot be inferred safely from a path alone.

Changesets are append-only. Do not modify, rename, delete, or reuse one after merge. Squashed,
rebased, and fixup commits remain safe because the release unit is the merged pull request, not an
individual commit. A conservative declaration for an artifact that automatic ownership did not
detect is allowed; omitting a detected artifact is not.

Verify the current branch against its merge base with `origin/main`:

```bash
./gradlew verifyViewComposeReleaseIntent
```

CI passes the pull request base SHA through `VIEWCOMPOSE_RELEASE_BASE_REVISION`. An exceptional
local comparison can use `-PviewComposeReleaseBaseRevision=<commit>`. The task is part of
`qaQuick` and also rejects changes to an already-recorded Changeset.

## Deterministic independent release planning

Run the planner only on a clean, fully fetched checkout whose GPG keyring trusts the ViewCompose
release public key:

```bash
git fetch origin main --tags
./gradlew planViewComposeRelease
```

For every previously published artifact, the planner selects the highest semantic version tag
matching `maven/<artifact-id>/<version>`, cryptographically verifies the signed annotation, and
reads its single strict `sourceRevision` token for source and API-documentation provenance. The tag
target's immutable release commit—not mutable current publishing metadata—is the comparison and
Changeset-consumption boundary because it is the exact repository state used for publication. A
Changeset or publication input merged after source freeze but included in that release is therefore
not replayed as a new release. Explicit first releases use the registered source revision and
repository-history rule above. The planner then:

1. loads Changesets and publication-relevant direct paths introduced between the release-tag target
   and `HEAD`;
2. verifies that every direct change has a matching unconsumed declaration;
3. takes the highest direct impact recorded for the artifact;
4. derives the current project dependency graph from Gradle `api`, `implementation`,
   `compileOnly`, and `runtimeOnly` project dependencies;
5. propagates a `dependency` release transitively to every published reverse dependent; and
6. writes deterministic `build/release-plan.json` and `build/release-plan.md` files that separate
   direct changes from dependency propagation.

Historical Changesets may name coordinates listed in `release.retiredModules`. The planner treats
those identifiers as valid immutable history while computing a first-release baseline, but it never
creates a baseline, version recommendation, or dependency-propagated release for a retired
coordinate. A retired identifier cannot simultaneously remain an active publication.

The plan recommends, but does not silently choose, versions. Stable lines use semantic versioning:
`fix` and `dependency` increment patch, `feature` increments minor, and `breaking` increments major
after `1.0` or minor on a `0.x` line. A prerelease increments its existing numeric channel, for
example `0.1.0-alpha01` to `0.1.0-alpha02`, regardless of impact. The release owner reviews and
confirms every exact version.

After the source commit is reviewed and frozen, apply only the confirmed plan:

```bash
./gradlew prepareViewComposeRelease \
  -PviewComposeReleaseVersions=viewcompose-runtime=0.1.0-alpha02,viewcompose-ui-contract=0.1.0-alpha02
```

The confirmed artifact set must exactly match the plan and every version must advance. The task
updates only those modules in `gradle/viewcompose-publishing.properties`, pins their
`sourceRevision` to the clean planning commit, and appends immutable entries to
`gradle/viewcompose-documentation-releases.properties`. Review and commit that diff as the
metadata-only release commit. Publication selection must match `build/release-plan.json`; after
Central reports `Published`, create the signed per-artifact tags described above.

The backfill Changeset dated 2026-08-04 classifies publication-relevant changes made after the
first Central boundary and before this workflow existed. It is a one-time migration record, not a
precedent for reconstructing release intent after merge.

## Active-plan archival gate

Implementation completion is not by itself the Maven publication boundary. Every document under
`docs/project/plans/`, except its directory index, contains exactly one machine-readable
`## Maven release changesets` section:

```md
## Maven release changesets

- `release/changes/example-feature.json`
```

A plan that has not started publication-relevant implementation uses one `- None.` entry instead.
This prevents a future plan that happens to mention the same artifact from blocking an earlier,
unrelated release. Replace `None` in the same pull request that adds the plan's first production
Changeset, and list every later Changeset owned by that plan. One Changeset cannot belong to two
active plans.

Before a public Central upload, `verifyArchivedViewComposeReleasePlans` parses every active plan,
loads its declared immutable Changesets, and derives both their direct artifacts and every
transitive reverse-dependent release from the current project dependency graph. The task rejects
the upload when that derived set intersects `-PviewComposePublishModules` and reports the active
plans that must close. Moving the completed plan to `docs/archive/`, updating the active and archive
indexes, preserving its final evidence, and passing documentation verification remove the blocker.

The gate intentionally does not block `planViewComposeRelease`, `prepareViewComposeRelease`, or
local Maven publication because those operations are part of release validation. It is a dependency
of the root `publishSelectedViewComposeToMavenCentral` task and every module-specific Central upload
task, so bypassing the root convenience task does not bypass plan acceptance. It can be run alone:

```bash
./gradlew verifyArchivedViewComposeReleasePlans \
  -PviewComposePublishModules=viewcompose-runtime,viewcompose-navigation-core
```

## Dependency exposure contract

Published dependencies follow an AndroidX-style capability contract: an application declares the
entry-point or optional-feature artifacts it intentionally uses, while those artifacts expose the
ViewCompose types required by their public API. A minimal Android application therefore needs only
one ViewCompose coordinate:

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-material3-android:<version-with-this-contract>")
}
```

`viewcompose-material3-android` exposes the neutral `viewcompose-android` aggregate plus the
Material 3 adapter. The neutral aggregate exposes UI Foundation, Android Engine, Lifecycle, and
ViewModel integrations without Material. `viewcompose-host-android` remains a low-level engine
artifact for advanced mounting and custom integrations. Adding a second direct dependency on an
already transitive artifact is harmless Gradle deduplication, but it is redundant and should
communicate deliberate direct API usage rather than compensate for incorrect publication metadata.

Feature artifacts expose every ViewCompose module required to compile their public surface,
including their platform-neutral core artifact:

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-navigation-android:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha03")
    implementation("com.viewcompose:viewcompose-gesture:0.1.0-alpha03")
    implementation("com.viewcompose:viewcompose-graphics:0.1.0-alpha03")
}
```

Core artifacts are also independently consumable from Kotlin/JVM modules:

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-navigation-core:0.1.0-alpha02")
    implementation("com.viewcompose:viewcompose-animation-core:0.1.0-alpha03")
    implementation("com.viewcompose:viewcompose-gesture-core:0.1.0-alpha03")
    implementation("com.viewcompose:viewcompose-graphics-core:0.1.0-alpha02")
}
```

Classify every direct dependency using these rules:

1. Use `api` when a dependency type appears in a public or protected signature, receiver, generic
   bound, supertype, type alias, or compiled public sample, or when the artifact intentionally acts
   as the supported entry point for that capability. The only exception is a documented
   caller-owned platform integration that consumers must already declare to author that platform
   entry point; its module manual and consumer smoke test must name the direct dependency.
2. Use `implementation` only when consumers can compile and use the supported public surface
   without resolving that dependency on their compile classpath.
3. Treat ViewCompose and third-party dependencies identically. An import in production source is
   neither sufficient nor necessary evidence for `api`; the published contract is the deciding
   factor.
4. Do not ask users to declare internal coordinates merely to repair a missing compile edge. Fix
   the owning artifact's metadata and add a consumer regression instead.
5. A new published module must define its intended entry-point role and exact dependency exposure
   before its first release. It must not silently copy the dependency shape of a neighboring module.

[`gradle/viewcompose-dependency-contracts.properties`](https://github.com/ViewCompose/ViewCompose/blob/main/gradle/viewcompose-dependency-contracts.properties)
is the machine-readable allowlist for every registered artifact's direct ViewCompose dependencies.
`verifyViewComposeDependencyContracts` compares it with Gradle declarations, and local repository
inspection verifies that `api` dependencies become Maven compile scope while `implementation`
dependencies become runtime scope. Published-consumption smoke projects then compile the minimal
host, optional-feature, and pure-JVM core paths against the generated repository. These checks are
part of the publishing configuration and repository verification workflows; changing a dependency
edge requires updating the contract, the owning module manual, and release intent together.

Repository Maven samples may adopt an unpublished coordinate only when the same gate first
publishes the current checkout to `build/maven-repository` and then compiles the sample through the
generated POM. Public release notes must still distinguish this source-verified state from Maven
Central availability. After Central publication succeeds, verify the installation path again from
a clean checkout without `build/maven-repository`.

Gradle Module Metadata preserves `api`/`implementation` variant semantics. Maven POMs are also
generated for other build tools. Every artifact publishes a sources JAR for IDE source navigation
and a javadoc JAR for repository requirements.

ViewCompose does not currently publish a BOM. Modules release independently, and the release
planner may propagate dependency-only releases, so a BOM would promise compatibility that has not
yet been established for independently versioned combinations. Continue to use explicit versions.
Evaluate a BOM only after compatibility tests cover supported mixed-version sets and release
automation can update the platform atomically; do not introduce a manually maintained version
catalog as a substitute.

The Android host deliberately does not act as an AndroidX or Material version catalog. Applications
continue to declare Activity/Fragment and Material dependencies they directly use for their class
hierarchy, theme, or optional native interop. This caller-owned exception does not permit hiding a
ViewCompose foundation module required by the advertised host DSL.

## Local Maven workflow

Fast metadata validation, without compiling artifacts:

```bash
./gradlew verifyViewComposePublishingConfiguration
```

Publish all registered modules to `build/maven-repository`:

```bash
./gradlew cleanViewComposeLocalRepository publishViewComposeToLocalRepository
```

Publish only the artifacts that are independently evolving in the current release:

```bash
./gradlew publishSelectedViewComposeToLocalRepository \
  -PviewComposePublishModules=viewcompose-navigation-core,viewcompose-navigation-android
```

Selective publication never deletes the repository, so it can resolve already staged independent
versions. The all-module task is intended for snapshot QA; public stable releases must use an
explicit module selection so unchanged immutable versions are never uploaded again.

Publish and validate only that independent release set:

```bash
./gradlew verifySelectedViewComposeLocalRepository \
  -PviewComposePublishModules=viewcompose-navigation-core,viewcompose-navigation-android
```

Inspect an already generated repository without publishing again:

```bash
./gradlew inspectViewComposeLocalRepository
```

Publish and validate primary artifacts, sources, docs, POM metadata, SHA-256/SHA-512 checksums,
stable-version signatures, and feature-to-core dependencies:

```bash
./gradlew verifyViewComposeLocalRepository
```

Build four isolated consumers that know nothing about project modules—a neutral Android host, a
named Material Android host, an Android feature consumer, and a pure JVM core consumer:

```bash
./gradlew verifyViewComposePublishedConsumption
```

`qaQuick` publishes the complete current artifact set to the generated local repository so stable
signatures and Maven metadata are exercised before merge. `qaPreview` performs the same local
publication first because the Counter preview sample deliberately consumes the public
`viewcompose-material3-android` coordinate instead of a project dependency. Repository inspection and the
isolated published-consumer builds remain explicit deeper checks; Maven Central upload tasks are
never part of either QA gate.

## Version overrides and signing

The checked-in versions are the source of truth. A CI dry run may override one module without
editing the file:

```bash
./gradlew publishViewComposeToLocalRepository \
  -PviewComposeVersion.viewcompose-navigation-android=0.2.0-SNAPSHOT
```

The group can be overridden for namespace validation with `-PviewComposeGroup=...`.
An exceptional documentation dry run may override the pinned source with
`-PviewComposeSourceRevision.<artifact>=<full-commit-sha>`; public release metadata must remain
checked in and must not rely on this override.

Local stable releases use the machine GPG keyring and OS pinentry window. The release key's public
half must be distributed to a Central-supported keyserver; no private key path or passphrase is
stored in the project.

CI releases use in-memory PGP signing:

```text
VIEWCOMPOSE_SIGNING_KEY
VIEWCOMPOSE_SIGNING_PASSWORD
```

Pull-request CI does not have a trusted release key. Each `qaQuick` and `qaPreview` job generates a
short-lived, unprotected test key inside its disposable runner solely to exercise local stable
artifact signing. That key and its artifacts are never uploaded or trusted for a public release.
Maven Central workflows must use the in-memory release credentials above.

Stable versions always require signatures; `-SNAPSHOT` versions may remain unsigned for local QA.
Secrets must remain outside the repository.

The Central Portal uploader reads its generated user token from standard private Gradle
properties. Put these only in the user-level `~/.gradle/gradle.properties`, or inject them through
CI secret environment variables:

```text
mavenCentralUsername=<generated token username>
mavenCentralPassword=<generated token password>
```

For CI, use `ORG_GRADLE_PROJECT_mavenCentralUsername` and
`ORG_GRADLE_PROJECT_mavenCentralPassword`. Never add either value to this repository.

After changing the selected module versions to stable values and completing the local release
checks, create a manual Central Portal deployment with:

```bash
./gradlew publishSelectedViewComposeToMavenCentral \
  -PviewComposePublishModules=viewcompose-runtime,viewcompose-navigation-core
```

The task deliberately has no all-module default, rejects `-SNAPSHOT` versions, and uploads as a
user-managed deployment. Inspect Central validation results before clicking Publish in the Portal.
Public release is therefore kept separate from the Gradle upload command.

## Android Studio plugin

The Marketplace plugin version is independently managed by
`plugin.viewcompose-studio.version` in the shared publication properties file. It can be overridden
with `-PviewComposeStudioPluginVersion=...`.

Prepare and verify an installable ZIP without uploading:

```bash
cd tools/viewcompose-studio-plugin
./gradlew prepareMarketplaceRelease
```

The first release targets Android Studio build family `261` only and declares
`com.intellij.modules.androidstudio`, preventing Marketplace from advertising the plugin for
IntelliJ IDEA products. Both the lower compatibility boundary and `untilBuild = 261.*` are
explicit, so an untested future Android Studio platform is not advertised as compatible by
accident. `prepareMarketplaceRelease` verifies the lower-bound local Quail 2 Patch 1 installation,
the current Quail 3 release, and the latest Quail 4 Canary within that advertised window. The first
matrix run downloads the latter two IDE distributions; later runs reuse Gradle's IDE cache.

The artifact is written to `build/distributions/`. Marketplace publishing and signing read only
environment variables:

```text
JETBRAINS_MARKETPLACE_TOKEN
JETBRAINS_CERTIFICATE_CHAIN
JETBRAINS_PRIVATE_KEY
JETBRAINS_PRIVATE_KEY_PASSWORD
```

The standard JetBrains environment names `CERTIFICATE_CHAIN`, `PRIVATE_KEY`,
`PRIVATE_KEY_PASSWORD`, and `PUBLISH_TOKEN` are supported as aliases. Local releases may instead
keep `chain.crt` and `private.pem` under the default private directory
`~/.config/viewcompose/marketplace-signing/`. Custom locations can be configured by putting only
their absolute paths in user-level `~/.gradle/gradle.properties`:

```text
viewComposeMarketplaceCertificateChainFile=/absolute/private/path/chain.crt
viewComposeMarketplacePrivateKeyFile=/absolute/private/path/private.pem
viewComposeMarketplacePrivateKeyPassword=<private key password, only when encrypted>
```

Build, sign, and verify the author signature before a manual upload:

```bash
cd tools/viewcompose-studio-plugin
./gradlew prepareSignedMarketplaceRelease
```

The Marketplace listing is approved. After the release owner reviews the prepared ZIP, signature,
compatibility report, and change notes, follow-up releases can use:

```bash
./gradlew publishPlugin
```

Use `-PviewComposeMarketplaceChannels=default,eap` to select channels; the default is `default`.

## First public release checklist

1. Confirm the `com.viewcompose` Central namespace remains verified.
2. Freeze the selected module source in a reviewed commit.
3. Update each selected version and its `sourceRevision` to the frozen commit in a metadata-only
   release commit.
4. Run `qaQuick`, `verifyCompleteViewComposeApiDocs`, `verifyViewComposePublishedConsumption`, and
   the relevant release tests.
5. Require PGP signing and inspect every generated POM, sources JAR, javadoc JAR, and checksum.
6. Archive every active execution plan linked to the selected release Changesets and run
   `verifyArchivedViewComposeReleasePlans` with the exact publication selection.
7. Upload to a Central staging deployment and verify consumption from that staging repository.
8. After Central reports `Published`, create, push, and remotely verify one signed
   `maven/<artifact-id>/<version>` tag for every published artifact.
9. Run `prepareMarketplaceRelease`, install the ZIP into the target Android Studio build, and do a
   final preview smoke test.
10. For the approved plugin listing, review the signed ZIP and compatibility report, then publish
    the follow-up release with the Marketplace token; a new listing still requires manual review.
