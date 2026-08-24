# Published Module Catalog

This catalog is the canonical documentation registry for public ViewCompose Maven artifacts. It is
kept in lockstep with `gradle/viewcompose-publishing.properties` and is verified by
`verifyDocumentationStructure`.

Every artifact links its available `docs/modules/<artifact-id>/README.md`. Publication and site
verification reject a missing manual, a catalog-only artifact, or a published artifact omitted from
this table. Architecture and guide pages remain the source of truth for cross-module concepts.

| Artifact | Family | Runtime role | Manual |
| --- | --- | --- | --- |
| `viewcompose-runtime` | Kernel | Platform-neutral state and observation runtime | [Available](./viewcompose-runtime/README.md) |
| `viewcompose-text-core` | Kernel | Platform-neutral text editing model | [Available](./viewcompose-text-core/README.md) |
| `viewcompose-ui-contract` | Kernel | Platform-neutral UI contracts and node specifications | [Available](./viewcompose-ui-contract/README.md) |
| `viewcompose-navigation-core` | Kernel | Platform-neutral navigation state and transactions | [Available](./viewcompose-navigation-core/README.md) |
| `viewcompose-renderer-android` | Android Engine | Android View renderer and reconciliation engine | [Available](./viewcompose-renderer-android/README.md) |
| `viewcompose-ui-foundation` | UI Foundation | Core DSL, components, tokens, and local values | [Available](./viewcompose-ui-foundation/README.md) |
| `viewcompose-diagnostics` | Integration | Bounded, privacy-safe production failure aggregation | [Available](./viewcompose-diagnostics/README.md) |
| `viewcompose-host-android` | Android Engine | Low-level View host, session, state, and interop engine | [Available](./viewcompose-host-android/README.md) |
| `viewcompose-material3` | Design System | Material 3 theme and dynamic-color adapter | [Available](./viewcompose-material3/README.md) |
| `viewcompose-material3-android` | Aggregate | Named Material 3 Android application integration | [Available](./viewcompose-material3-android/README.md) |
| `viewcompose-oneui7` | Design System | One UI 7 five-component alpha token and component set | [Available](./viewcompose-oneui7/README.md) |
| `viewcompose-android` | Aggregate | Neutral Android application entry dependency | [Available](./viewcompose-android/README.md) |
| `viewcompose-navigation-android` | Integration | Android navigation host integration | [Available](./viewcompose-navigation-android/README.md) |
| `viewcompose-overlay-android` | Integration | Material-free Android overlay transport | [Available](./viewcompose-overlay-android/README.md) |
| `viewcompose-overlay-material3-android` | Integration | Material-backed Android overlay presentation | [Available](./viewcompose-overlay-material3-android/README.md) |
| `viewcompose-overlay-oneui7-android` | Integration | Material-free One UI Snackbar and bottom-dialog presentation | [Available](./viewcompose-overlay-oneui7-android/README.md) |
| `viewcompose-image-coil` | Integration | Coil-backed general image loading | [Available](./viewcompose-image-coil/README.md) |
| `viewcompose-image-glide` | Integration | Glide-backed general image loading | [Available](./viewcompose-image-glide/README.md) |
| `viewcompose-lifecycle-androidx` | Integration | AndroidX lifecycle-aware state, effects, and committed native-View coordination | [Available](./viewcompose-lifecycle-androidx/README.md) |
| `viewcompose-viewmodel-androidx` | Integration | AndroidX ViewModel and SavedStateHandle integration | [Available](./viewcompose-viewmodel-androidx/README.md) |
| `viewcompose-preview-core` | Preview tooling | Preview annotations and tooling protocol | [Available](./viewcompose-preview-core/README.md) |
| `viewcompose-preview-gradle-plugin` | Preview tooling | Preview discovery and Gradle tasks | [Available](./viewcompose-preview-gradle-plugin/README.md) |
| `viewcompose-preview-runner` | Preview tooling | Layoutlib preview rendering runtime | [Available](./viewcompose-preview-runner/README.md) |
| `viewcompose-preview-worker-host` | Preview tooling | Isolated preview worker host | [Available](./viewcompose-preview-worker-host/README.md) |
| `viewcompose-preview` | Preview tooling | Development preview and snapshot integration | [Available](./viewcompose-preview/README.md) |
| `viewcompose-animation-core` | Kernel | Platform-neutral animation engine contracts | [Available](./viewcompose-animation-core/README.md) |
| `viewcompose-animation` | UI Foundation | Animation DSL and composition integration | [Available](./viewcompose-animation/README.md) |
| `viewcompose-gesture-core` | Kernel | Platform-neutral gesture policies | [Available](./viewcompose-gesture-core/README.md) |
| `viewcompose-gesture` | UI Foundation | Gesture DSL and state APIs | [Available](./viewcompose-gesture/README.md) |
| `viewcompose-graphics-core` | Kernel | Platform-neutral graphics model | [Available](./viewcompose-graphics-core/README.md) |
| `viewcompose-graphics` | UI Foundation | Drawing DSL and composition integration | [Available](./viewcompose-graphics/README.md) |
| `viewcompose-shadow-android` | Integration | Advanced Android shadow rendering | [Available](./viewcompose-shadow-android/README.md) |
| `viewcompose-constraintlayout-androidx` | Integration | AndroidX ConstraintLayout DSL | [Available](./viewcompose-constraintlayout-androidx/README.md) |
| `viewcompose-media3-androidx` | Integration | Lifecycle-safe AndroidX Media3 PlayerView hosting | [Available](./viewcompose-media3-androidx/README.md) |

## Catalog rules

1. The artifact ID is the directory name and future public URL key. It must match its Maven
   `artifactId` exactly.
2. A module manual becomes `Available` only when its linked `README.md` satisfies the module
   documentation contract.
3. New, renamed, or retired artifacts update this catalog and publishing metadata together.
4. Internal modules such as the demo application and benchmark harness are documented in
   architecture or tooling pages, not added as Maven artifacts here.
5. Current module versions are read from publishing metadata during site generation. Immutable
   released versions are appended to `gradle/viewcompose-documentation-releases.properties`; do
   not duplicate either registry in a hand-maintained table.
