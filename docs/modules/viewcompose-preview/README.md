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

## Running-device DSL location, highlighting, and timing

This optional artifact owns the application-process half of Android Studio's **Locate Device DSL**,
**Highlight Device DSL Node**, **Clear Device DSL Highlight**, and **Inspect Device Node Timing**
actions. In a debuggable process
it retains bounded source candidates for Host, navigation-destination, and pager-page sessions and
registers a weak, request-only mounted-node inspector for every supported logical session role.
Lazy-item, overlay, and preview sessions therefore remain selectable without composition-time source
stack capture. Protocol v6 carries the same process-local
trace ID, optional parent ID, and typed role used by runtime diagnostics. It does not continuously
publish a report or observe scroll, global layout, draw, touch, frames, or recomposition.

Source location sends one `DUMP`-permission-protected source request. Highlighting first selects a
visible session, then requests one mounted-tree snapshot capped at 2,048 visited nodes, 512 returned
nodes, and depth 64. A fresh opaque token identifies each retained node. The response excludes
application keys, View text, semantics, state, Local values, and arbitrary `toString()` output.
Synthetic renderer hosts are reported but cannot be selected as application content.

Selection resolves the weak current Android View on the main thread, records full screen and
clipped-visible bounds, and draws one non-interactive process-wide overlay. Partial clipping is
explicit. Missing, stale, recycled, hidden, fully clipped, unsupported, ended-session, and rejected
requests fail closed. Replacement, explicit clear, View detach, session disposal, or a five-second
timeout removes the overlay. It does not recompose, invoke application callbacks, change focus or
accessibility focus, intercept input, or mutate layout parameters.

Every response echoes a 1--128 character ASCII nonce and is lazily serialized to at most 256 KiB,
then atomically written in application-private cache. The IDE accepts only a response with the
matching operation, nonce, foreground package, and live process. Invalid requests, missing
services, writer or overlay failures, and session disposal cannot fail application rendering.

The timing action selects one visible correlated Session and starts one finite request while the
developer triggers the workload. Protocol v6 carries executed composition, reconciliation, and
direct-binding aggregates with opaque capture-scoped node tokens, inclusive/self or direct
semantics, clock-read counts, empty-pair overhead, drops, truncation, unsupported domains, and
terminal reason. A process accepts only one active capture. It stops after at most eight completed
frame attempts or two monotonic seconds, retains at most 64 timed nodes per frame and 512 aggregates
to depth 32, and reuses bounded source metadata rather than taking a timing-path stack trace.

Ordinary rendering supplies no collector: the artifact performs no per-node clock reads, timing
record allocation, polling, or report write until the explicit request. Measure/layout/draw, GPU,
RenderThread, SurfaceFlinger, decode, network, database, and external-SDK work are outside this first
contract. The Diagnostics → Renderer Demo fixture exposes a visible eight-frame workload so manual
acceptance can confirm both UI progression and the terminal report.

Keep this artifact in `debugImplementation`, test, or a dedicated tooling configuration. It is the
artifact-presence gate required in addition to a debuggable process and an explicit IDE request.
See [ADR-0009](../../architecture/decisions/0009-development-tooling-isolation.md) for the zero-pay
runtime and performance contract.

## Running-device animation timeline inspector

The same debug-scoped artifact is the only application-process implementation of
`AnimationTimelineTooling`. Its provider may weakly register committed transition sources before
the first request so already composed transitions remain discoverable, without retaining
application values, installing a listener, or taking a snapshot. The receiver rejects
non-debuggable processes. Android Studio's **Inspect Device Animation Timeline** action first sends one discovery
request, lets the developer select a transition, and then opens one 500 ms capture capped at 64
distinct samples, 32 channels per sample, and a 256 KiB response.

The report exposes bounded transition labels, privacy-safe logical state summaries, segment time,
unequal channel durations, specification families, safe numeric values and velocities, physical
terminal conditions, and interruption/retarget samples. Custom application values are shown as
unsupported/private; the implementation never calls their `toString`. Every response must match
the request nonce, foreground package, live process, request mode, and selected identity. Missing,
busy, stale, malformed, oversized, disposed, and writer-failure paths fail closed without changing
the application.

Running-device inspection is strictly read-only. The Studio dialog states that control is limited
to synthetic or interactive Preview content that already owns a `SeekableTransitionState` and
calls its public `seekTo` API. The device receiver has no mutation command and cannot write private
transition fields. With no valid request, transition publication performs only the provider's
bounded selected-identity check and produces no sample or report.

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
the session and request another ViewCompose render. Theme, debug configuration, overlay backend,
diagnostics configuration, or container changes recreate the session. Leaving the Compose
composition disposes it. Content must
not remove or retain the bridge-owned root.

The bridge installs `AndroidResourceEnvironment` from the same container Context used to create
native Views. Android resource lookup functions therefore resolve the active Compose-preview
configuration, and configuration callbacks advance the same revision used by ordinary Android
hosts rather than a preview-only resolver.

`ViewComposePreviewOptions` selects light or dark `UiThemeDefaults` and an optional correlated
`RenderDiagnostics` root. Interactive and static Preview sessions use the `Preview` role.
These options are intentionally small; static-preview configuration matrices belong to preview-core.

## Catalog and snapshot coverage

The internal catalog groups representative component, input, container, collection, navigation,
feedback, modifier, animation, gesture, and graphics scenes. Parameterized Compose previews and
Paparazzi snapshots share the same specifications, while guard tests enforce unique IDs, groups,
titles, and the declared coverage target list.

The `animation-layout-bounds` catalog entry fixes a settled start rectangle for position, size, and
combined motion. Its reviewed light-theme Golden protects the initial geometry and styling; the
interactive specification can then toggle the real bounds endpoint without introducing a separate
preview-only renderer path.

The `navigation-shared-content-endpoints` entry renders the compact/expanded bounds endpoints and
the source/target element markers with the production modifier transport. Its static Golden checks
endpoint geometry and styling; real cross-session progress, cancellation, and cleanup remain owned
by the Demo/device navigation fixtures rather than a preview-only coordinator.

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
Running-device source location, node highlighting, and timing are request-driven and owned entirely
by this optional artifact; Android Host retains only a neutral nullable session-inspection port.
Protocol v6 hard-cuts older reports by adding the timing operation and finite result to operation
validation, request-scoped opaque node tokens, bounded node snapshots, structured highlight states,
clipping bounds, and explicit clear.
