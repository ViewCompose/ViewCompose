---
schema_version: 2
document_id: module.viewcompose-preview
doc_type: module
owner:
  kind: module
  id: viewcompose-preview
version_lane: released
capability_ids:
  - preview.integration
artifact_ids:
  - viewcompose-preview
sample_ids:
  - module.preview-theme-provider
  - module.preview-compose-bridge
coordinate: com.viewcompose:viewcompose-preview:0.1.0-alpha04
minimal_usage_sample_id: module.preview-theme-provider
---

# Preview Integration

`viewcompose-preview` is the optional Android API used by application-owned preview themes, Compose
Preview bridging, Paparazzi catalog tests, and request-driven running-device inspection. Keep
`com.viewcompose:viewcompose-preview:0.1.0-alpha04` in `debugImplementation`, tests, or a dedicated
tooling source set. Runtime support starts at API 24.

## Application theme provider

The native runner receives a configuration-qualified Context. One implementation marked with
`@ViewComposePreviewThemeProvider` returns that themed Context plus matching `UiThemeTokens`; this
keeps native Views and ViewCompose components in the same theme. Providers must be stateless,
preserve the supplied configuration, avoid machine-specific inputs, and not retain the Context.

{/* compiled-region source="viewcompose-preview/src/test/samples/com/viewcompose/preview/samples/PreviewSamples.kt" region="preview-theme-provider" sample_id="module.preview-theme-provider" build_target=":viewcompose-preview:compileDebugUnitTestKotlin" */}
```kotlin
@ViewComposePreviewThemeProvider
object ApplicationPreviewThemeProvider : PreviewThemeProvider {
    override fun resolve(
        context: Context,
        theme: PreviewTheme,
    ): PreviewThemeResolution {
        val tokens = when (theme) {
            PreviewTheme.Light -> UiThemeDefaults.light()
            PreviewTheme.Dark -> UiThemeDefaults.dark()
        }
        return PreviewThemeResolution(context = context, tokens = tokens)
    }
}
```

## Compose Preview bridge

`ViewComposePreview` hosts root-independent DSL content; `ViewComposePreviewWithRoot` exposes the
bridge-owned root for interop; `ViewComposePreviewHost` is the low-level host with overlay and
diagnostic configuration. One Android root/session is remembered across content-only recomposition.
Theme, debug, overlay, diagnostics, or container changes recreate the session, and leaving the
Compose composition disposes it. Content must not retain or remove the root.

{/* compiled-region source="viewcompose-preview/src/test/samples/com/viewcompose/preview/samples/PreviewSamples.kt" region="preview-compose-bridge" sample_id="module.preview-compose-bridge" build_target=":viewcompose-preview:compileDebugUnitTestKotlin" */}
```kotlin
@Preview
@Composable
fun composePreviewBridgeSample() {
    val diagnostics = remember {
        RenderDiagnostics(
            collection = RenderDiagnosticCollection(),
            sink = { event -> println(event) },
        )
    }
    ViewComposePreview(
        options = ViewComposePreviewOptions(diagnostics = diagnostics),
    ) {
        Text("ViewCompose")
    }
}
```

The bridge installs `AndroidResourceEnvironment` from the same Context used by native Views and
uses `UiThemeDefaults`; it is convenient for existing Compose tooling but is not the source of
application-theme screenshot truth or static runner artifacts.

## Running-device development tooling

This artifact is the application-process implementation for Studio's explicit **Inspect Device
Diagnostics** and **Inspect Device Animation Timeline** actions. It follows ADR-0009: activation
requires artifact presence, a debuggable process, and a valid request; a non-debuggable or idle path
owns no report polling, recurring View traversal, frame observer, per-node timer, or report write.

Diagnostics protocol v7 returns privacy-bounded session/frame/failure summaries, mounted-node
snapshots/highlights, and a finite timing capture. Requests require Android `DUMP`, a one-use nonce,
foreground package and live-process validation. Node traversal, depth, strings, timing records,
response bytes, and highlight lifetime are bounded; stale, hidden, recycled, clipped, ended, or
unsupported nodes fail closed. Timing covers ViewCompose composition, reconciliation and direct
binding—not Android measure/layout/draw, GPU, RenderThread, decoding, network, database, or SDK work.

Animation inspection is read-only. It discovers committed transitions and captures one selected
timeline for at most 500 ms with bounded samples/channels/bytes. It never writes private transition
state; interactive control remains the public `SeekableTransitionState.seekTo` contract inside
preview-owned content.

## Verification and compatibility

- Stability: **Alpha**; Preview Core owns wire compatibility.
- Run `:viewcompose-preview:testDebugUnitTest` and `:viewcompose-preview:verifyPaparazziDebug`.
- Catalog IDs are immutable snapshot identities; record a new Golden only after reviewing the
  visual difference.
- Device tooling must prove release-classpath exclusion, zero idle work, one response per valid
  request, stale-nonce rejection, privacy bounds, and fail-closed disposal.

See [Preview tooling](../../tooling/preview.md), [ADR-0009](../../architecture/decisions/0009-development-tooling-isolation.md),
and the [generated API reference](https://docs.viewcompose.com/api/viewcompose-preview/current/).
