# Compose Migration Paired Samples Plan

## Status

Completed on 2026-08-03.

## Delivered scope

The repository now has one non-published `:samples:compose-migration` Android library that
compiles Jetpack Compose and ViewCompose starting points for four migration domains:

1. remembered mutable state and input;
2. Row layout, Modifier ordering, and scoped environment values;
3. Activity root hosting and Android View interop; and
4. a minimal Navigation 2 controller, host, route, and navigation action.

The eight focused source files use the repository's Compose 1.7.8 executable baseline,
Navigation 2.9.8, and public ViewCompose APIs. They do not claim Compose 1.11.4 or Navigation 3
executable parity.

## Documentation and permanent gates

The four canonical English migration pages and their required Chinese mirrors contain the same
paired snippets. Each code block names a marked source region in the sample module.

The root `verifyMigrationPairedSamples` task:

1. compiles `:samples:compose-migration`;
2. requires the expected two snippets in every owning English and Chinese page;
3. rejects missing, extra, or reordered pairs; and
4. compares each Markdown block exactly with its marked compiled source region.

This task is part of `qaQuick`. The durable sample rules are recorded in `samples/README.md`, and
the migration entry page identifies the module and verification gate as executable anchors.

## Validation evidence

The completed change passed:

```bash
./gradlew :samples:compose-migration:compileDebugKotlin
./gradlew verifyMigrationPairedSamples verifyDocumentationStructure
./gradlew qaQuick
cd website
npm run test:scripts
npm run verify:translations
npm run typecheck
npm run build
```

The production site build verified both locales, all 25 versioned API/manual routes, site-owned
page accessibility, and the configured output budgets. An initial build rejected HTML snippet
comments under MDX; the permanent marker format uses MDX-native comments and is covered by the
successful build.

## Explicit non-goals retained for later work

This completed batch did not add Lazy collections, text input, theming, animation, gestures,
Navigation 3 samples, a second runnable application, public APIs, or runtime behavior changes.
Those domains remain separate migration or tutorial work.
