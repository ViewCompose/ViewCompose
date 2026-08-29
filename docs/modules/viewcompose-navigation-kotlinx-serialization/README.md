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

This optional JVM 11 adapter creates a Core `NavRouteSpec<T>` from a Kotlinx `KSerializer<T>`. The
source-registered `0.1.0-alpha01` exposes Navigation Core and Serialization Core, has no platform
dependencies, and is intended for flat route schemas.

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

The reified overload obtains the serializer; other callers pass it. Exact mapping is Boolean to
`BooleanValue`, Byte/Short/Int to `IntValue`, Long to `LongValue`, Float/Double to their matching
value, Char/String/enum to `Text`, and nullable scalars to their value or `Null`.

The root must be a class or object with scalar, enum, nullable-scalar, or supported inline-scalar
fields. Construction rejects nested objects, collections, maps, polymorphic/contextual shapes,
unsigned fields, non-object roots, and non-finite floats. Call-local JSON bridges the serializer;
only immutable `NavRoute`/`NavValue` is retained. Decode rejects unknown names, invalid nulls, and
wrong variants; omitted defaults are reconstructed. Keep schema restore-compatible; alternative
JSON names are disabled.

See [Navigation Core](../viewcompose-navigation-core/README.md), [architecture](../../architecture/navigation.md),
[Compose migration](../../migration/compose-navigation.md), and the [generated API](https://docs.viewcompose.com/api/viewcompose-navigation-kotlinx-serialization/current/).
