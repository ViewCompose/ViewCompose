# Preview Integration

`viewcompose-preview` connects ViewCompose UI code to development-time preview hosts. It provides
the application theme-provider contract used by the static Layoutlib runner, a convenient
Compose `AndroidView` bridge, and the first-party preview catalog and Paparazzi snapshot harness.

## Artifact and stability

```kotlin
dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview:0.1.0-alpha04")
}
```

- Stability: **Alpha**. The public bridge and theme-provider contracts may evolve with preview
  tooling before the stable line.
- Runtime: Android API 24 or newer.
- Recommended scope: debug, test, or dedicated preview source sets. Application release code does
  not need the Compose bridge or catalog.
- Transitive API: preview-core annotations/protocol, UI Foundation DSL and theme types, and the Compose
  runtime, UI, and preview annotations required by the bridge.

## Choose the preview path

ViewCompose supports two complementary paths:

1. The native static-preview path uses `@ViewComposePreview`, the Gradle plugin, an isolated
   Layoutlib worker, and `PreviewThemeProvider`. It produces deterministic PNG and diagnostic
   artifacts and is the source of truth for production-theme fidelity, source navigation, layout
   diagnostics, galleries, and CI rendering.
2. `ViewComposePreview` and `ViewComposePreviewWithRoot` embed a ViewCompose render session in
   Compose Preview through `AndroidView`. They are convenient for existing Compose preview surfaces
   but use `UiThemeDefaults` rather than an application theme provider and do not export the static
   runner's diagnostic artifacts.

The similarly named APIs live in different packages: the static annotation is in
`com.viewcompose.preview.tooling`; the Compose bridge function is in `com.viewcompose.preview`.

## Running-device DSL locator

This optional artifact also owns the application-process half of Android Studio's **Locate Device
DSL** action. In a debuggable process it supplies the neutral Host source-inspection service and
retains bounded source candidates for eligible Host and Page sessions. It does not observe scroll,
global layout, draw, touch, frames, or recomposition and does not continuously publish a report.

When the developer clicks the action, Android Studio sends one `DUMP`-permission-protected request
with a 32-character nonce. The receiver samples current weakly held session Views once on the main
thread, then lazily serializes and atomically writes one bounded response in the application's
private cache. The IDE accepts only a response with the matching nonce, foreground package, and
live process. The report contains JVM source identity and View eligibility only; it contains no
source text, VNode tree, application state, or user data. Invalid requests, missing services,
writer failures, and session disposal cannot fail application rendering.

Keep this artifact in `debugImplementation`, test, or a dedicated tooling configuration. It is the
artifact-presence gate required in addition to a debuggable process and an explicit IDE request.
See [ADR-0009](../../architecture/decisions/0009-development-tooling-isolation.md) for the zero-pay
runtime and performance contract.

## Application theme provider

Implement `PreviewThemeProvider` and mark exactly one implementation in a previewed module with
`@ViewComposePreviewThemeProvider`. The provider receives a context that already contains the
requested density, font scale, viewport, locale, direction, and night-mode qualifiers. It returns
one `PreviewThemeResolution` containing:

- a themed Context used to construct the native root and Android Views;
- matching `UiThemeTokens` installed around the ViewCompose DSL tree.

Keeping both results in one resolution prevents native Views and DSL components from silently using
different themes. Providers should be stateless, preserve the supplied configuration, avoid dynamic
machine-specific inputs, and not retain the context. The worker can instantiate a Kotlin object or a
public no-argument class.

## Compose Preview bridge

`ViewComposePreview` is the default bridge for root-independent DSL content.
`ViewComposePreviewWithRoot` supplies the bridge-owned Android `ViewGroup` for interop anchors.
`ViewComposePreviewHost` is the lower-level form that also accepts an overlay backend.

The bridge remembers one Android root and render session. Content-only Compose recompositions reuse
the session and request another ViewCompose render. Theme, debug configuration, overlay backend, or
container changes recreate the session. Leaving the Compose composition disposes it. Content must
not remove or retain the bridge-owned root.

The bridge installs `AndroidResourceEnvironment` from the same container Context used to create
native Views. Android resource lookup functions therefore resolve the active Compose-preview
configuration, and configuration callbacks advance the same revision used by ordinary Android
hosts rather than a preview-only resolver.

`ViewComposePreviewOptions` selects light or dark `UiThemeDefaults` and optional render diagnostics.
These options are intentionally small; static-preview configuration matrices belong to preview-core.

## Catalog and snapshot coverage

The internal catalog groups representative component, input, container, collection, navigation,
feedback, modifier, animation, gesture, and graphics scenes. Parameterized Compose previews and
Paparazzi snapshots share the same specifications, while guard tests enforce unique IDs, groups,
titles, and the declared coverage target list.

Catalog types are internal test infrastructure rather than a public component-gallery API. Extend
them when a new module or visual contract needs regression coverage, but keep application examples
in the demo and user-facing documentation.

## Testing and extension rules

- Prefer the static runner for production-theme and source-diagnostic acceptance tests.
- Keep Compose bridge dependencies out of release configurations unless the application truly uses
  them at runtime.
- Test theme providers in light/dark, locale, RTL, density, and font-scale variants.
- Do not retain the provider context or the root supplied by `ViewComposePreviewWithRoot`.
- Give every catalog specification a stable, unique ID; changing it renames snapshot history.
- Pair new visual domains with coverage guard entries and Paparazzi snapshots.
- Run `qaPreview` before merge. Record a changed baseline only after reviewing the rendered image
  and its difference report; an unexplained mismatch is a regression, not a baseline update.
- Treat renderer or provider exceptions as preview failures; do not hide them with placeholder UI.
- Device-locator changes must prove zero writes during idle scrolling, one response per valid
  request, stale-nonce rejection, and release-classpath exclusion.

## Related documentation

- [Preview Core module](../viewcompose-preview-core/README.md)
- [Preview Runner module](../viewcompose-preview-runner/README.md)
- [Preview Gradle Plugin module](../viewcompose-preview-gradle-plugin/README.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-preview` API tree](https://docs.viewcompose.com/api/viewcompose-preview/current/).

## Compatibility notes

The `0.1.0-alpha03` line establishes the coherent native/DSL theme resolution, retained Compose
bridge session, explicit root-access overload, and shared catalog/snapshot coverage model. Static
preview protocol compatibility remains owned by preview-core.
The running-device DSL locator is now request-driven and owned entirely by this optional artifact;
the Android Host retains only its neutral nullable inspection port.
