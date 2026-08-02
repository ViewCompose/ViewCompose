# Published Module Catalog

This catalog is the canonical documentation registry for public ViewCompose Maven artifacts. It is
kept in lockstep with `gradle/viewcompose-publishing.properties` and is verified by
`verifyDocumentationStructure`.

Every artifact links its available `docs/modules/<artifact-id>/README.md`. Publication and site
verification reject a missing manual, a catalog-only artifact, or a published artifact omitted from
this table. Architecture and guide pages remain the source of truth for cross-module concepts.

| Artifact | Family | Runtime role | Manual |
| --- | --- | --- | --- |
| `viewcompose-runtime` | Foundation | Platform-neutral state and observation runtime | [Available](./viewcompose-runtime/README.md) |
| `viewcompose-text-core` | Foundation | Platform-neutral text editing model | [Available](./viewcompose-text-core/README.md) |
| `viewcompose-ui-contract` | Foundation | Platform-neutral UI contracts and node specifications | [Available](./viewcompose-ui-contract/README.md) |
| `viewcompose-navigation-core` | Navigation | Platform-neutral navigation state and transactions | [Available](./viewcompose-navigation-core/README.md) |
| `viewcompose-navigation` | Navigation | Android navigation host integration | [Available](./viewcompose-navigation/README.md) |
| `viewcompose-renderer` | Rendering | Android View renderer and reconciliation engine | [Available](./viewcompose-renderer/README.md) |
| `viewcompose-widget-core` | UI | Core DSL, components, theme, and local values | [Available](./viewcompose-widget-core/README.md) |
| `viewcompose-host-android` | Android host | Activity, Fragment, and View host integration | [Available](./viewcompose-host-android/README.md) |
| `viewcompose-overlay-android` | Android host | Android overlay presentation backend | [Available](./viewcompose-overlay-android/README.md) |
| `viewcompose-image-coil` | Integration | Coil-backed remote image loading | [Available](./viewcompose-image-coil/README.md) |
| `viewcompose-lifecycle` | Integration | Lifecycle-aware state collection | [Available](./viewcompose-lifecycle/README.md) |
| `viewcompose-viewmodel` | Integration | ViewModel and SavedStateHandle integration | [Available](./viewcompose-viewmodel/README.md) |
| `viewcompose-preview-core` | Preview tooling | Preview annotations and tooling protocol | [Available](./viewcompose-preview-core/README.md) |
| `viewcompose-preview-gradle-plugin` | Preview tooling | Preview discovery and Gradle tasks | [Available](./viewcompose-preview-gradle-plugin/README.md) |
| `viewcompose-preview-runner` | Preview tooling | Layoutlib preview rendering runtime | [Available](./viewcompose-preview-runner/README.md) |
| `viewcompose-preview-worker-host` | Preview tooling | Isolated preview worker host | [Available](./viewcompose-preview-worker-host/README.md) |
| `viewcompose-preview` | Preview tooling | Development preview and snapshot integration | [Available](./viewcompose-preview/README.md) |
| `viewcompose-animation-core` | Animation | Platform-neutral animation engine contracts | [Available](./viewcompose-animation-core/README.md) |
| `viewcompose-animation` | Animation | Animation DSL and composition integration | [Available](./viewcompose-animation/README.md) |
| `viewcompose-gesture-core` | Gesture | Platform-neutral gesture policies | [Available](./viewcompose-gesture-core/README.md) |
| `viewcompose-gesture` | Gesture | Gesture DSL and state APIs | [Available](./viewcompose-gesture/README.md) |
| `viewcompose-graphics-core` | Graphics | Platform-neutral graphics model | [Available](./viewcompose-graphics-core/README.md) |
| `viewcompose-graphics` | Graphics | Drawing DSL and composition integration | [Available](./viewcompose-graphics/README.md) |
| `viewcompose-shadow-android` | Optional Android backend | Advanced Android shadow rendering | [Available](./viewcompose-shadow-android/README.md) |
| `viewcompose-widget-constraintlayout` | Optional widget | ConstraintLayout DSL | [Available](./viewcompose-widget-constraintlayout/README.md) |

## Catalog rules

1. The artifact ID is the directory name and future public URL key. It must match its Maven
   `artifactId` exactly.
2. A module manual becomes `Available` only when its linked `README.md` satisfies the module
   documentation contract.
3. New, renamed, or retired artifacts update this catalog and publishing metadata together.
4. Internal modules such as the demo application and benchmark harness are documented in
   architecture or tooling pages, not added as Maven artifacts here.
5. Module versions are read from publishing metadata during site generation; do not duplicate a
   mutable current-version table by hand.
