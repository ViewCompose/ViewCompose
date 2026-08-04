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
maven/viewcompose-navigation/0.2.0
```

All tags in one Central deployment may point to the same metadata-only release commit. The tag
target must be that release commit—not the frozen source commit—because it is the exact repository
state that contains the published version and `sourceRevision`. The signed annotation must record
the artifact, version, and frozen source revision so both commits remain auditable.

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
      "artifact": "viewcompose-widget-core",
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

For every registered artifact, the planner selects the highest semantic version tag matching
`maven/<artifact-id>/<version>`, cryptographically verifies the signed annotation, and reads its
`sourceRevision`. That immutable revision—not mutable current publishing metadata—is the artifact's
comparison boundary. It then:

1. loads Changesets introduced between that revision and `HEAD`;
2. verifies that every publication-relevant direct path has a matching declaration;
3. takes the highest direct impact recorded for the artifact;
4. derives the current project dependency graph from Gradle `api`, `implementation`,
   `compileOnly`, and `runtimeOnly` project dependencies;
5. propagates a `dependency` release transitively to every published reverse dependent; and
6. writes deterministic `build/release-plan.json` and `build/release-plan.md` files that separate
   direct changes from dependency propagation.

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

## Dependency shape

Feature artifacts expose their platform-neutral core artifact transitively:

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-navigation:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-animation:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-gesture:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-graphics:0.1.0-alpha01")
}
```

Core artifacts are also independently consumable from Kotlin/JVM modules:

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-navigation-core:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-animation-core:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-gesture-core:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-graphics-core:0.1.0-alpha01")
}
```

Gradle Module Metadata preserves `api`/`implementation` variant semantics. Maven POMs are also
generated for other build tools. Every artifact publishes a sources JAR for IDE source navigation
and a javadoc JAR for repository requirements.

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
  -PviewComposePublishModules=viewcompose-navigation-core,viewcompose-navigation
```

Selective publication never deletes the repository, so it can resolve already staged independent
versions. The all-module task is intended for snapshot QA; public stable releases must use an
explicit module selection so unchanged immutable versions are never uploaded again.

Publish and validate only that independent release set:

```bash
./gradlew verifySelectedViewComposeLocalRepository \
  -PviewComposePublishModules=viewcompose-navigation-core,viewcompose-navigation
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

Build two isolated consumers that know nothing about project modules—one Android feature consumer
and one pure JVM core consumer:

```bash
./gradlew verifyViewComposePublishedConsumption
```

The full publication tasks are intentionally not part of `qaQuick`; only the cheap coordinate and
version validation runs during normal project QA.

## Version overrides and signing

The checked-in versions are the source of truth. A CI dry run may override one module without
editing the file:

```bash
./gradlew publishViewComposeToLocalRepository \
  -PviewComposeVersion.viewcompose-navigation=0.2.0-SNAPSHOT
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

The first Marketplace release must be uploaded through JetBrains Marketplace for initial review.
After the plugin is approved, later releases can use:

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
6. Upload to a Central staging deployment and verify consumption from that staging repository.
7. After Central reports `Published`, create, push, and remotely verify one signed
   `maven/<artifact-id>/<version>` tag for every published artifact.
8. Run `prepareMarketplaceRelease`, install the ZIP into the target Android Studio build, and do a
   final preview smoke test.
9. Upload the first plugin release manually; enable token-based automation only after approval.
