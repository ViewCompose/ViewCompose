# Compose Migration Paired Samples Plan

## Status

In progress.

## Scope

Create one non-published Android sample module that compiles equivalent Jetpack Compose and
ViewCompose starting points for the four current migration domains: state, layout/environment,
hosting/Android interop, and Navigation 2.

This plan covers:

1. a dedicated `:samples:compose-migration` module using the repository's executable Compose
   baseline and public ViewCompose APIs;
2. eight focused source files, one Compose and one ViewCompose implementation per domain;
3. mechanically verified Markdown snippets whose code must exactly match the compiled sources;
4. English canonical migration pages, current Chinese mirrors, sample catalog wiring, and
   `qaQuick` integration.

## Non-goals

1. Claiming Compose 1.11.4 or Navigation 3 executable parity from the local 1.7.8/Navigation 2
   baseline.
2. Building a second runnable showcase application or depending on the large `:app` demo.
3. Changing public ViewCompose APIs, runtime behavior, or Maven publication metadata.
4. Covering Lazy collections, text input, theming, animation, or gestures; those remain the next
   migration batches.

## Completion criteria

1. All eight paired sources compile from a clean checkout through `qaQuick`.
2. Each of the four English migration pages contains a Compose and ViewCompose code block copied
   from the owning source region.
3. A required verification task rejects missing, stale, uncompiled, or translation-divergent
   paired snippets.
4. Required Chinese mirrors contain the same verified code and reviewed explanatory text.
5. Documentation structure, translation freshness, focused sample compilation, and `qaQuick` pass.
6. Durable sample and verification rules move to active documentation before this plan is archived.

## Ordered work

1. Register the sample module and any missing executable-baseline dependencies.
2. Add the four Compose/ViewCompose source pairs and compile them.
3. Add generic source-region-to-Markdown verification and wire it into `qaQuick`.
4. Embed verified pairs into English and Chinese migration pages and update the sample catalog.
5. Run full gates, archive this plan, and publish through the protected pull-request workflow.

## Validation

```bash
./gradlew :samples:compose-migration:compileDebugKotlin
./gradlew verifyMigrationPairedSamples
./gradlew verifyDocumentationStructure
./gradlew qaQuick
cd website
npm run test:scripts
npm run verify:translations
npm run typecheck
npm run build
```

## Last verified

2026-08-03: Compose 1.7.8 and the Kotlin Compose plugin are already available in the repository;
the four migration pages have no common compiled paired-sample source set.

## Next action

Register `:samples:compose-migration`, add the executable Navigation 2 baseline, and compile the
first state pair before expanding to the remaining domains.
