# Versioned Documentation Retention Plan

## Status

In progress.

## Scope

Preserve every released ViewCompose API reference and module manual at immutable per-artifact
version routes while keeping `current` and stable-only `latest` aliases mechanically correct.

This plan covers:

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

## Current baseline

As verified on 2026-08-03:

- all 25 published artifacts currently register `0.1.0-alpha01` at source revision
  `fbe1614dd2a278f06517d775c373cb88ce5674a2`;
- `assembleViewComposeApiDocs` is a `Sync` task, so a clean deployment contains only versions
  generated from the current publishing metadata;
- module manuals have mutable `/modules/<artifact>/` routes but no version snapshots;
- Maven Central Javadoc artifacts are not a complete archive source because some platform-neutral
  and Gradle-plugin Javadoc JARs contain no generated reference;
- the recorded source revision contains the Gradle/Dokka pipeline and all module manuals, so the
  released state can be rebuilt from immutable Git history.

## Completion criteria

1. A clean checkout can build every recorded API version and module-manual snapshot without using
   a previous Pages deployment.
2. Advancing a module version or source revision without a matching history entry fails a required
   repository gate.
3. Every recorded API route and module-manual route exists after a production build.
4. `current` targets current publishing metadata; `latest` targets only the most recent recorded
   stable release and remains absent when no stable release exists.
5. Selected-module iteration and complete-catalog CI both remain available.
6. Generated snapshots remain ignored build output, and the hosted-site operations guide documents
   release preparation and recovery.
7. `qaQuick`, selected and complete API verification, translation checks, type-checking, and the
   production site build pass.

## Ordered work

1. Add and validate the release-documentation history against publishing metadata.
2. Split current-checkout Dokka generation from complete historical assembly.
3. Rebuild versioned API trees from recorded Git revisions and verify source links.
4. Generate immutable module-manual pages from the same revisions.
5. Expose version history from the API/module site presentation and update operations guidance.
6. Run complete gates, archive this plan after durable conclusions move to active documentation,
   and publish through the protected pull-request workflow.

## Validation

```bash
./gradlew verifyViewComposePublishingConfiguration
./gradlew verifyAssembledViewComposeApiDocs \
  -PviewComposeDocsModules=viewcompose-runtime
./gradlew verifyCompleteViewComposeApiDocs
./gradlew qaQuick
cd website
npm run test:translations
npm run verify:translations
npm run typecheck
npm run build
```

## Last verified

2026-08-03: baseline and immutable source availability confirmed; implementation has not started.

## Next action

Introduce the history manifest and repository gate, then assemble one selected module from its
recorded revision before expanding to the complete catalog.
