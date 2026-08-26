---
title: ViewCompose Documentation
slug: /documentation
---

# ViewCompose Documentation

This directory is the canonical documentation entrance for ViewCompose. It is organized for both
human readers and AI-assisted maintenance, and it is also the content boundary for the published
GitHub-hosted documentation site.

The repository state and active documents below are authoritative. Files under
[`archive/`](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/README.md) are
historical evidence only.

## Choose a reading path

| Goal | Start here |
| --- | --- |
| Build the first application | [Build your first application](./tutorials/getting-started.md) |
| Learn one capability | [Capability tutorials](./tutorials/README.md) → choose any topic; chapters have no ordering requirement |
| Understand the framework | [Architecture overview](./architecture/overview.md) → [Multi-design-system standard](./architecture/design-systems.md) → [Modifier model](./architecture/modifier.md) → [NodeSpec model](./architecture/node-spec.md) |
| Migrate from Jetpack Compose | [Compose migration overview](./migration/README.md) → choose the state, layout, host, or navigation path |
| Choose or maintain a published artifact | [Published module catalog](./modules/README.md) → the owning module manual |
| Look up an application-facing entry | [Capability Reference](https://docs.viewcompose.com/reference/) → [versioned API/KDoc](https://docs.viewcompose.com/api/) → the owning module manual |
| Build with a feature | Select the relevant document under [Guides](#guides) |
| Work on previews or performance | [Preview](./tooling/preview.md) → [Diagnostics](./tooling/diagnostics.md) → [Performance](./tooling/performance.md) |
| Contribute a change | [Development workflow](./project/workflow.md) → [Documentation governance](./project/documentation-governance.md) |
| Prepare a release | [Publishing](./project/publishing.md) → [Capability verification](./project/capability-verification.md) |
| Restore project context | [Roadmap](./project/roadmap.md) and the active document for the affected area; do not start from archived plans |

## Architecture

Long-lived contracts, boundaries, and runtime semantics:

- [Architecture overview](./architecture/overview.md)
- [Navigation runtime architecture](./architecture/navigation.md)
- [Theme runtime architecture](./architecture/theming.md)
- [Multi-design-system architecture and integration standard](./architecture/design-systems.md)
- [Architecture decisions](./architecture/decisions/README.md)
- [Modifier model](./architecture/modifier.md)
- [NodeSpec model](./architecture/node-spec.md)
- [State snapshots](./architecture/state-snapshots.md)
- [Transactional effects and structured work](./architecture/effects.md)
- [Lifecycle and SavedState](./architecture/lifecycle-and-saved-state.md)
- [Render failures](./architecture/render-failures.md)
- [Session containers](./architecture/session-containers.md)

## Tutorials

Independently runnable learning pages backed by one compiled source file per capability:

- [Build your first application](./tutorials/getting-started.md) — create the smallest native-View
  counter and optional static preview.
- [Capability tutorial catalog](./tutorials/README.md) — choose state, layout, text input, lazy
  lists, theming, navigation, overlays, Android View interop, animation, gestures, performance, or
  diagnostics without completing another chapter first.

## Guides

Feature behavior and platform integration:

- [Switch application theme mode](./guides/theming.md)
- [Enable Material 3 dynamic color](./guides/theming-dynamic-color.md)
- [Override theme tokens for one subtree](./guides/theming-local-overrides.md)
- [Text input](./guides/text-input.md)
- [Lazy collections](./guides/lazy-collections.md)
- [Focus and input](./guides/focus-and-input.md)
- [Nested scrolling](./guides/nested-scroll.md)
- [Configure a production navigation host](./guides/navigation.md)
- [Overlays](./guides/overlays.md)
- [Shadows](./guides/shadows.md)
- [Image loading](./guides/image-loading.md)

## Migration from Jetpack Compose

Semantic comparisons and migration paths with explicit source and target versions:

- [Compose migration overview and consolidated capability matrix](./migration/README.md)
- [State, recomposition, and restoration](./migration/compose-state-recomposition-and-restoration.md)
- [Layout, Modifier, and environment](./migration/compose-layout-modifier-and-environment.md)
- [Hosts, lifecycle, and Android interop](./migration/compose-host-lifecycle-and-android-interop.md)
- [Navigation 2 and Navigation 3](./migration/compose-navigation.md)
- [Image loading](./migration/image-loading.md)

## Published modules

The [published module catalog](./modules/README.md) is kept in lockstep with Maven publication
metadata. Every published artifact has a dedicated manual under `docs/modules/<artifact-id>/` and
can evolve independently.

## Capability and API Reference

The source-derived [Capability Reference](https://docs.viewcompose.com/reference/) groups application-facing DSL, Modifier,
component, integration, host, and tooling entries by user capability. Its counts, versions, and
routes are freshness-gated. Use the [versioned API Reference](https://docs.viewcompose.com/api/) for exhaustive signatures and
KDoc/Javadoc, then follow the entry's module-manual link for artifact contracts.

## Tooling

Development-time tooling, inspection, and performance:

- [Preview](./tooling/preview.md)
- [Diagnostics](./tooling/diagnostics.md)
- [Performance](./tooling/performance.md)

## Project maintenance

Current process, release, and planning information:

- [Development workflow](./project/workflow.md)
- [Documentation governance](./project/documentation-governance.md)
- [Localization workflow](./project/localization.md)
- [Source documentation and API comments](./project/api-documentation-quality.md)
- [Documentation site operations](./project/documentation-site.md)
- [Publishing](./project/publishing.md)
- [Roadmap](./project/roadmap.md)
- [Capability verification](./project/capability-verification.md)
- [Active execution plans](./project/plans/README.md)

## Documentation rules

1. Keep the repository root limited to landing pages and community governance files.
2. Separate cross-module concepts from artifact-specific installation, compatibility, and API
   contracts.
3. Update KDoc/Javadoc and the owning module manual with public API changes.
4. Apply the documentation change impact matrix in every code pull request; `No documentation
   impact` requires a rationale.
5. Put cross-session execution plans under `docs/project/plans/`; move completed plans to
   `docs/archive/`.
6. Use repository-relative links. Never commit a local absolute path.
7. Make every active document reachable from this index through a section index.
8. Do not use archived documents as current requirements.
9. Run `./gradlew verifyDocumentationStructure` before committing documentation changes. The same
   check is included in `qaQuick`.
10. Keep titles, headings, and narrative English in `docs/`, and Simplified Chinese in the matching
    `zh-CN` mirror; mark foreign-language UI literals as inline code.
11. Follow the canonical-first [localization workflow](./project/localization.md) for every public
    content change; never refresh a translation fingerprint without reviewing meaning.

The complete contract, naming rules, lifecycle, and review checklist are defined in
[Documentation governance](./project/documentation-governance.md).
