# Versioned Documentation Retention Plan

## Status

Completed on 2026-08-03.

## Scope

Preserve every released ViewCompose API reference and module manual at immutable per-artifact
version routes while keeping `current` and stable-only `latest` aliases mechanically correct.

This plan covered:

1. a canonical release-documentation history keyed by artifact, version, and immutable source
   revision;
2. reproducible Dokka generation from each recorded source revision;
3. generated module-manual snapshots under `/modules/<artifact>/<version>/`;
4. complete-history verification, site navigation, CI checkout requirements, and release workflow
   documentation.

## Non-goals

1. Committing generated Dokka HTML or Docusaurus output.
2. Translating generated historical snapshots; they follow the English-only generated-reference
   policy.
3. Changing Maven coordinates, module versions, public APIs, or runtime behavior.
4. Retrofitting Dokka template accessibility coverage.

## Baseline

At the start of the work on 2026-08-03:

- all 25 published artifacts registered `0.1.0-alpha01` at source revision
  `fbe1614dd2a278f06517d775c373cb88ce5674a2`;
- `assembleViewComposeApiDocs` was a `Sync` task, so a clean deployment contained only versions
  generated from current publishing metadata;
- module manuals had mutable `/modules/<artifact>/` routes but no version snapshots;
- Maven Central Javadoc artifacts were not a complete archive source because some platform-neutral
  and Gradle-plugin Javadoc JARs contained no generated reference;
- the recorded source revision contained the Gradle/Dokka pipeline and all module manuals, so the
  released state could be rebuilt from immutable Git history.

## Durable result

1. `gradle/viewcompose-documentation-releases.properties` is the append-only registry of released
   artifact/version/source-revision triples, and publishing configuration rejects current metadata
   without an exact history entry.
2. Complete API assembly groups history by revision, extracts frozen source into temporary
   workspaces, injects the current maintained documentation tooling, and generates every recorded
   Dokka tree with immutable source links.
3. Docusaurus generates an immutable manual from the same revision for every release and exposes
   the version list from the API catalog. Historical snapshots remain canonical English at both
   locale route trees.
4. `current` follows current publishing metadata; `latest` is emitted only for the most recent
   stable history entry. No current prerelease creates `latest`.
5. CI uses full Git history, runs documentation script tests, rebuilds the complete API catalog,
   and verifies every API/manual route in the production site.
6. The permanent release and recovery procedure is maintained in
   `docs/project/documentation-site.md` and `docs/project/documentation-governance.md`.

## Validation

Passed on 2026-08-03:

```bash
./gradlew verifyViewComposePublishingConfiguration
./gradlew verifyAssembledViewComposeApiDocs \
  -PviewComposeDocsModules=viewcompose-runtime
./gradlew verifyCompleteViewComposeApiDocs
./gradlew qaQuick
cd website
npm run test:scripts
npm run verify:translations
npm run typecheck
npm run build
```

The complete API build reconstructed and verified all 25 recorded artifact versions from the
frozen revision. The production site verified 25 API versions, 25 module manuals, 25 `zh-CN`
English-fallback manual routes, 182 site-owned accessibility pages, and all size/build-time budgets.
