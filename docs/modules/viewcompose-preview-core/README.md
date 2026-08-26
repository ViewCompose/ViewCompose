---
schema_version: 2
document_id: module.viewcompose-preview-core
doc_type: module
owner:
  kind: module
  id: viewcompose-preview-core
version_lane: released
capability_ids:
  - preview.core.annotations
  - preview.core.protocol
artifact_ids:
  - viewcompose-preview-core
sample_ids:
  - tooling.preview-entry
  - module.preview-core-matrix
  - module.preview-core-protocol
coordinate: com.viewcompose:viewcompose-preview-core:0.1.0-alpha03
minimal_usage_sample_id: tooling.preview-entry
---

# Preview Core

`viewcompose-preview-core` is the JVM-only contract shared by preview annotations, Gradle discovery,
the Layoutlib runner and worker, Android Studio, tests, and CI. Add
`com.viewcompose:viewcompose-preview-core:0.1.0-alpha03` to a debuggable or dedicated preview source
set; the Gradle plugin strips root and composed preview annotations from non-debuggable output.

## Entry and configuration contract

`@ViewComposePreview` marks a public static JVM method that accepts exactly one `UiTreeBuilder`
receiver or parameter and returns `Unit`. It is repeatable and supports source-visible custom
multi-preview annotations. The built-ins cover light/dark, phone/tablet, LTR/RTL, and common font
scales.

{/* compiled-region source="samples/counter/src/debug/java/com/viewcompose/samples/counter/CounterPreview.kt" region="preview-entry" sample_id="tooling.preview-entry" build_target=":samples:counter:compileDebugKotlin" */}
```kotlin
@ViewComposePreview(
    name = "Counter · Light",
    group = "Samples/Getting started",
)
@ViewComposePreview(
    name = "Counter · Dark",
    group = "Samples/Getting started",
    theme = PreviewTheme.Dark,
)
fun UiTreeBuilder.CounterPreview() {
    CounterScreen()
}
```

`PreviewConfiguration` deterministically owns viewport, density, font scale, locales, direction,
theme, and optional API level. Matrix axes preserve declaration order; later axes win when several
override the same field. IDs are validated lowercase cache/artifact identities.

{/* compiled-region source="viewcompose-preview-core/src/test/samples/com/viewcompose/preview/tooling/samples/PreviewCoreSamples.kt" region="preview-configuration-matrix" sample_id="module.preview-core-matrix" build_target=":viewcompose-preview-core:compileTestKotlin" */}
```kotlin
fun previewConfigurationMatrixSample(): List<PreviewVariant> {
    return PreviewConfigurationMatrix(
        axes = listOf(
            PreviewConfigurationPresets.Theme,
            PreviewConfigurationPresets.LayoutDirection,
        ),
    ).variants()
}
```

## Protocol and snapshot contract

The core model separates project bytecode from Layoutlib-compatible inputs, sorts inputs before
SHA-256 fingerprinting, caps worker batches at eight sequential commands, requires unique response
paths, and requires exact protocol-version equality. Filesystem paths remain opaque until the
process responsible for access resolves and constrains them.

{/* compiled-region source="viewcompose-preview-core/src/test/samples/com/viewcompose/preview/tooling/samples/PreviewCoreSamples.kt" region="preview-protocol-round-trip" sample_id="module.preview-core-protocol" build_target=":viewcompose-preview-core:compileTestKotlin" */}
```kotlin
fun previewProtocolRoundTripSample(): PreviewRenderRequest {
    val variant = PreviewVariant(
        id = "phone-light",
        displayName = "Phone / Light",
        configuration = PreviewConfiguration(),
    )
    val request = PreviewRenderRequest(
        requestId = "render-1",
        descriptor = PreviewDescriptor(
            id = "account-preview",
            displayName = "Account preview",
            entryPoint = PreviewJvmEntryPoint(
                ownerClassName = "com.example.AccountPreviewsKt",
                methodName = "accountPreview",
                methodDescriptor = "(Lcom/viewcompose/ui/foundation/UiTreeBuilder;)V",
            ),
            variants = listOf(variant),
        ),
        variantId = variant.id,
        modulePath = ":app",
        buildVariant = "debug",
        buildFingerprint = "0".repeat(64),
        outputDirectory = "build/viewcompose-preview/account-preview/phone-light",
    )
    return PreviewProtocolJson.decodeRequest(PreviewProtocolJson.encodeRequest(request))
}
```

Failures cross process boundaries as structured diagnostics. Success requires an image artifact;
timings use unique names and non-negative durations. Immutable snapshots contain only bounded,
serializable structure, native bounds/properties, clipping, layout diagnostics, patches,
composition information, and source call sites—never live Views, VNodes, class loaders, or
exceptions. JSON writes defaults, omits explicit nulls, accepts additive unknown keys, and still
validates identities and protocol compatibility.

## Compatibility and verification

- Stability: **Alpha**; annotation shape is established, but the wire protocol may advance between
  alpha lines.
- Runtime: JVM 11, no Android, Gradle, Layoutlib, or IDE dependency.
- Serialization JSON is an API dependency because protocol models and `PreviewProtocolJson` are
  public.
- Verify configuration ordering, protocol round trips, invalid-input rejection, deterministic
  fingerprints, snapshot bounds, and old/new worker mismatch with `:viewcompose-preview-core:test`.

See [ViewCompose Preview tooling](../../tooling/preview.md) for installation and the
[generated API reference](https://docs.viewcompose.com/api/viewcompose-preview-core/current/) for
the exhaustive symbol inventory.
