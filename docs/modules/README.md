# Published Module Catalog

This catalog is the canonical documentation registry for public ViewCompose Maven artifacts. It is
kept in lockstep with `gradle/viewcompose-publishing.properties` and is verified by
`verifyDocumentationStructure`.

Module-specific manuals will be added under `docs/modules/<artifact-id>/README.md` as the hosted
documentation system is built. Until then, the architecture and guide links in `docs/README.md`
remain the current sources of truth. `Planned` means that the dedicated module manual has not yet
been published; it does not describe implementation or Maven Central availability.

| Artifact | Family | Runtime role | Manual |
| --- | --- | --- | --- |
| `viewcompose-runtime` | Foundation | Platform-neutral state and observation runtime | [Available](./viewcompose-runtime/README.md) |
| `viewcompose-text-core` | Foundation | Platform-neutral text editing model | Planned |
| `viewcompose-ui-contract` | Foundation | Platform-neutral UI contracts and node specifications | [Available](./viewcompose-ui-contract/README.md) |
| `viewcompose-navigation-core` | Navigation | Platform-neutral navigation state and transactions | Planned |
| `viewcompose-navigation` | Navigation | Android navigation host integration | Planned |
| `viewcompose-renderer` | Rendering | Android View renderer and reconciliation engine | [Available](./viewcompose-renderer/README.md) |
| `viewcompose-widget-core` | UI | Core DSL, components, theme, and local values | [Available](./viewcompose-widget-core/README.md) |
| `viewcompose-host-android` | Android host | Activity, Fragment, and View host integration | Planned |
| `viewcompose-overlay-android` | Android host | Android overlay presentation backend | Planned |
| `viewcompose-image-coil` | Integration | Coil-backed remote image loading | Planned |
| `viewcompose-lifecycle` | Integration | Lifecycle-aware state collection | Planned |
| `viewcompose-viewmodel` | Integration | ViewModel and SavedStateHandle integration | Planned |
| `viewcompose-preview-core` | Preview tooling | Preview annotations and tooling protocol | Planned |
| `viewcompose-preview-gradle-plugin` | Preview tooling | Preview discovery and Gradle tasks | Planned |
| `viewcompose-preview-runner` | Preview tooling | Layoutlib preview rendering runtime | Planned |
| `viewcompose-preview-worker-host` | Preview tooling | Isolated preview worker host | Planned |
| `viewcompose-preview` | Preview tooling | Development preview and snapshot integration | Planned |
| `viewcompose-animation-core` | Animation | Platform-neutral animation engine contracts | Planned |
| `viewcompose-animation` | Animation | Animation DSL and composition integration | Planned |
| `viewcompose-gesture-core` | Gesture | Platform-neutral gesture policies | Planned |
| `viewcompose-gesture` | Gesture | Gesture DSL and state APIs | Planned |
| `viewcompose-graphics-core` | Graphics | Platform-neutral graphics model | Planned |
| `viewcompose-graphics` | Graphics | Drawing DSL and composition integration | Planned |
| `viewcompose-shadow-android` | Optional Android backend | Advanced Android shadow rendering | Planned |
| `viewcompose-widget-constraintlayout` | Optional widget | ConstraintLayout DSL | Planned |

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
