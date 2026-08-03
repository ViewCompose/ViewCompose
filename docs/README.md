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
| Grow a realistic application | [Task-list state and layout](./tutorials/task-list-foundations.md) → [input and lazy collections](./tutorials/task-list-input-and-lists.md) → [theme and navigation](./tutorials/task-list-theme-and-navigation.md) → [overlays and Android Views](./tutorials/task-list-overlays-and-android-views.md) → [animation and gestures](./tutorials/task-list-animation-and-gestures.md) → [performance and diagnostics](./tutorials/task-list-performance-and-diagnostics.md) |
| Understand the framework | [Architecture overview](./architecture/overview.md) → [Modifier model](./architecture/modifier.md) → [NodeSpec model](./architecture/node-spec.md) |
| Migrate from Jetpack Compose | [Compose migration overview](./migration/README.md) → choose the state, layout, host, or navigation path |
| Choose or maintain a published artifact | [Published module catalog](./modules/README.md) → the owning module manual |
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

## Tutorials

End-to-end learning paths backed by compiled repository samples:

- [Build your first application](./tutorials/getting-started.md) — install the published modules and
  create a native-View counter from one Activity.
- [Build a task list with state and layout](./tutorials/task-list-foundations.md) — start the
  progressive application with immutable data, snapshot state, layout, modifiers, and events.
- [Add task input and a keyed lazy list](./tutorials/task-list-input-and-lists.md) — evolve the same
  application with editable text, immutable collection updates, stable keys, and device tests.
- [Add semantic theming and list-detail navigation](./tutorials/task-list-theme-and-navigation.md) —
  add host-resolved tokens, typed routes, and a framework-owned back stack.
- [Confirm deletion and host a native Android View](./tutorials/task-list-overlays-and-android-views.md)
  — integrate a custom dialog overlay and a state-driven native `TextView`.
- [Animate completion and add bounded gestures](./tutorials/task-list-animation-and-gestures.md) —
  connect animation and row gestures to the same deterministic application actions.
- [Tune collection reuse and inspect render diagnostics](./tutorials/task-list-performance-and-diagnostics.md)
  — make collection hints explicit and sample immutable host counters without render loops.

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

## Migration from Jetpack Compose

Semantic comparisons and migration paths with explicit source and target versions:

- [Compose migration overview and consolidated capability matrix](./migration/README.md)
- [State, recomposition, and restoration](./migration/compose-state-recomposition-and-restoration.md)
- [Layout, Modifier, and environment](./migration/compose-layout-modifier-and-environment.md)
- [Hosts, lifecycle, and Android interop](./migration/compose-host-lifecycle-and-android-interop.md)
- [Navigation 2 and Navigation 3](./migration/compose-navigation.md)

## Published modules

The [published module catalog](./modules/README.md) is kept in lockstep with Maven publication
metadata. Every published artifact has a dedicated manual under `docs/modules/<artifact-id>/` and
can evolve independently.

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
10. Follow the canonical-first [localization workflow](./project/localization.md) when changing
    translated public content; never refresh a translation fingerprint without reviewing meaning.

The complete contract, naming rules, lifecycle, and review checklist are defined in
[Documentation governance](./project/documentation-governance.md).
