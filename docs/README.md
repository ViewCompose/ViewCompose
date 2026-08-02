---
title: ViewCompose Documentation
slug: /documentation
---

# ViewCompose Documentation

This directory is the canonical documentation entrance for ViewCompose. It is organized for both
human readers and AI-assisted maintenance, and it is also the content boundary for the future
GitHub-hosted documentation site.

The repository state and active documents below are authoritative. Files under
[`archive/`](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/README.md) are
historical evidence only.

## Choose a reading path

| Goal | Start here |
| --- | --- |
| Understand the framework | [Architecture overview](./architecture/overview.md) → [Modifier model](./architecture/modifier.md) → [NodeSpec model](./architecture/node-spec.md) |
| Choose or maintain a published artifact | [Published module catalog](./modules/README.md) → the owning module manual when available |
| Build with a feature | Select the relevant document under [Guides](#guides) |
| Work on previews or performance | [Preview](./tooling/preview.md) → [Diagnostics](./tooling/diagnostics.md) → [Performance](./tooling/performance.md) |
| Contribute a change | [Development workflow](./project/workflow.md) → [Documentation governance](./project/documentation-governance.md) |
| Prepare a release | [Publishing](./project/publishing.md) → [Capability verification](./project/capability-verification.md) |
| Restore project context | [Roadmap](./project/roadmap.md) and the active document for the affected area; do not start from archived plans |

## Architecture

Long-lived contracts, boundaries, and runtime semantics:

- [Architecture overview](./architecture/overview.md)
- [Architecture decisions](./architecture/decisions/README.md)
- [Modifier model](./architecture/modifier.md)
- [NodeSpec model](./architecture/node-spec.md)
- [State snapshots](./architecture/state-snapshots.md)
- [Lifecycle and SavedState](./architecture/lifecycle-and-saved-state.md)
- [Render failures](./architecture/render-failures.md)
- [Session containers](./architecture/session-containers.md)

## Guides

Feature behavior and platform integration:

- [Theming](./guides/theming.md)
- [Text input](./guides/text-input.md)
- [Lazy collections](./guides/lazy-collections.md)
- [Focus and input](./guides/focus-and-input.md)
- [Nested scrolling](./guides/nested-scroll.md)
- [Navigation](./guides/navigation.md)
- [Overlays](./guides/overlays.md)
- [Shadows](./guides/shadows.md)

## Published modules

The [published module catalog](./modules/README.md) is kept in lockstep with Maven publication
metadata. Dedicated module manuals will live below `docs/modules/<artifact-id>/` and will be able to
evolve with each artifact independently.

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
10. Follow the canonical-first [localization workflow](./project/localization.md) when changing
    translated public content; never refresh a translation fingerprint without reviewing meaning.

The complete contract, naming rules, lifecycle, and review checklist are defined in
[Documentation governance](./project/documentation-governance.md).
