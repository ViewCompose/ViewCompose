# Preview Core

`viewcompose-preview-core` defines the platform-neutral annotation, configuration, discovery, render,
worker, and diagnostic protocols shared by ViewCompose's Gradle plugin, Layoutlib workers, Android
Studio plugin, tests, and CI. It contains no Android or IDE runtime dependency.

## Artifact and stability

```kotlin
dependencies {
    debugImplementation("com.viewcompose:viewcompose-preview-core:0.1.0-alpha03")
}
```

- Stability: **Alpha**. Annotation source shape is established; the tooling wire protocol requires
  exact version equality and may advance before stable.
- Platform: JVM 11; protocol models are platform-neutral.
- Kotlinx Serialization JSON is an API dependency because public protocol models carry
  serialization metadata and `PreviewProtocolJson` is the supported wire-format codec.
- Packaging: apply preview metadata to debuggable source sets. The ViewCompose preview Gradle plugin
  removes it from non-debuggable Android output.
- Boundary: this module does not load Android Views, Layoutlib, Gradle, or IDE classes.

## Preview entry annotations

`@ViewComposePreview` marks a top-level or static DSL function. The compiled method must be public
and static, accept exactly one `UiTreeBuilder` receiver/parameter, and return `Unit`. Additional
parameters remain unsupported even when Kotlin gives them default values, because the worker invokes
the exact JVM method rather than Kotlin's synthetic default bridge.

Annotations may repeat or be composed into custom multi-preview annotations. Built-ins cover
light/dark, phone/tablet, LTR/RTL, and common font scales. Auto height (`-1`) begins with a reference
viewport and grows within safety limits; use a positive height when clipping or scrolling is the
behavior under test.

## Deterministic configuration

`PreviewConfiguration` resolves width, height, density, font scale, locales, direction, application
theme mode, and optional API level without consulting the host system. Configuration matrices form
a deterministic Cartesian product. Axes and options preserve declaration order; later axes win if
several override the same field.

Stable IDs use lowercase ASCII words joined by `-`, with `__` reserved for matrix composition. They
are validated before becoming cache keys or artifact paths.

## Build and worker protocol

The Gradle bridge exports a canonical `PreviewBuildManifest`, sorted build inputs, and lowercase
SHA-256 fingerprints. Project bytecode is separated from Layoutlib-compatible inputs so a warm host
can retain the expensive platform runtime while creating a fresh child class loader for each render.

Worker batches contain at most eight commands and execute sequentially in one short-lived host.
Response paths must be unique. This amortizes JVM startup without allowing mutable Layoutlib or
application state to escape indefinitely.

Protocol version checks require exact equality. Paths remain opaque strings across the boundary;
the process that owns a filesystem operation is responsible for resolving and constraining them.

## Responses and diagnostics

Worker failures cross the boundary as structured response data. Successful responses require an
image artifact; failures require at least one diagnostic. Optional phase timings use unique names
and non-negative durations.

Render snapshots deliberately contain only primitive, string, collection, and serializable protocol
values. They expose VNode structure, native View bounds, clipping, common View properties, layout
problems, patches, composition scopes, and source call sites without retaining runtime-owned Views,
VNodes, class loaders, or exceptions.

## JSON compatibility

`PreviewProtocolJson` encodes defaults for deterministic artifacts, omits explicit nulls, and uses
pretty output. Readers ignore unknown keys to permit additive fields, but still execute model
validation and reject unsupported protocol versions or invalid identities.

## Testing and operations

- Treat protocol model changes as cross-process compatibility changes and update every producer and
  consumer together.
- Keep ordered inputs canonical before hashing; do not fingerprint nondeterministic collection order.
- Validate annotation discovery against compiled JVM signatures, not Kotlin source appearance.
- Bound externally supplied snapshot size and recursive structures in the consuming process.
- Test protocol round trips, invalid input rejection, variant ordering, and old/new worker mismatch.

## Related documentation

- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-preview-core` API tree](https://docs.viewcompose.com/api/viewcompose-preview-core/current/).

## Compatibility notes

The `0.1.0-alpha02` line uses protocol version 1, exact-version negotiation, deterministic JSON and
fingerprints, bounded worker batches, auto-height configuration, application-owned theme providers,
and source-aware render diagnostics. The wire format is not yet promised stable across alpha lines.
