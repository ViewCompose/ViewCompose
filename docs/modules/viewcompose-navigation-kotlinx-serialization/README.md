---
schema_version: 2
document_id: module.viewcompose-navigation-kotlinx-serialization
doc_type: module
owner:
  kind: module
  id: viewcompose-navigation-kotlinx-serialization
version_lane: next
capability_ids:
  - navigation.kotlinx-serialization-routes
artifact_ids:
  - viewcompose-navigation-kotlinx-serialization
sample_ids:
  - module.navigation-kotlinx-serialization-route
coordinate: com.viewcompose:viewcompose-navigation-kotlinx-serialization:0.1.0-alpha01
minimal_usage_sample_id: module.navigation-kotlinx-serialization-route
---

# Navigation Kotlinx Serialization

`viewcompose-navigation-kotlinx-serialization` is the optional JVM adapter that creates a Core
`NavRouteSpec<T>` from a Kotlinx `KSerializer<T>`. Use it when application route values already use
Kotlinx Serialization. Keep custom Core codecs when a route has a structured or domain-specific
wire shape.

This source-registered `0.1.0-alpha01` artifact is not Maven Central-published until its first
signed release. It targets JVM 11, exposes `viewcompose-navigation-core` and
`kotlinx-serialization-core` to callers, and keeps its JSON tree bridge private. It has no Android,
host, lifecycle, View, or saved-state dependency.

## Create a route spec

{/* compiled-region source="viewcompose-navigation-kotlinx-serialization/src/test/samples/com/viewcompose/navigation/serialization/samples/NavigationSerializationSamples.kt" region="navigation-kotlinx-serialization-route" sample_id="module.navigation-kotlinx-serialization-route" build_target=":viewcompose-navigation-kotlinx-serialization:compileTestKotlin" */}
```kotlin
@Serializable
data class ProfileRoute(
    val userId: Long,
    val tab: String = "posts",
)

val ProfileDestination: NavRouteSpec<ProfileRoute> =
    serializableNavRouteSpec(name = "profile")

fun serializableRouteSample(): ProfileRoute {
    val route = ProfileDestination.encode(ProfileRoute(userId = 42L))
    return ProfileDestination.decode(route)
}
```

The reified overload obtains the generated serializer. Java callers and custom serializers use
`serializableNavRouteSpec(name, serializer)`. The returned spec works unchanged with Core graph
declarations, Android typed commands, and `NavEntry.toRoute`.

## Schema and storage contract

| Declared field | Stored value |
| --- | --- |
| Boolean | `BooleanValue` |
| Byte, Short, Int | `IntValue` |
| Long | `LongValue` |
| Float / Double | `FloatValue` / `DoubleValue` |
| Char, String, enum | `Text` |
| Nullable scalar | matching value or `Null` |

The root must be a class or object with scalar, enum, nullable-scalar, or supported inline-scalar
fields. Construction rejects nested objects, collections, maps, polymorphic/contextual shapes,
unsigned fields, and non-object roots. Float and Double values must be finite. Defaults omitted by
the serializer remain absent and are reconstructed on decode.

Mapping follows the descriptor, not the current value. A Long remains `LongValue` even when it fits
inside Int. Decode rejects unknown names, invalid nulls, and mismatched `NavValue` variants. The
adapter uses a strict JSON object tree only during a call; snapshots and hosts still retain only
immutable `NavRoute` and `NavValue` data.

## Compatibility and verification

Keep the explicit route name, serialized field names, field types, nullability, and defaults
compatible with restored state. Renames require an application migration; alternative JSON names
are deliberately disabled. Tests cover supported scalar mappings, object and inline routes,
defaults, storage stability, malformed arguments, and every rejected schema family.

See the [Navigation Core manual](../viewcompose-navigation-core/README.md),
[navigation architecture](../../architecture/navigation.md), and
[Compose migration comparison](../../migration/compose-navigation.md). The generated symbols are
in the [`viewcompose-navigation-kotlinx-serialization` API tree](https://docs.viewcompose.com/api/viewcompose-navigation-kotlinx-serialization/current/).
