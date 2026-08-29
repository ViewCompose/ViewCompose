---
translation_source: modules/viewcompose-navigation-kotlinx-serialization/README.md
translation_source_hash: c95e19966d9f418fcc8d496ee734ab2d7075f7b182b0ffec06848237edb709f5
translation_status: current
---

# Navigation Kotlinx Serialization 模块

这个可选 JVM 11 Adapter 用 Kotlinx `KSerializer<T>` 创建 Core `NavRouteSpec<T>`。源码已登记的
`0.1.0-alpha01` 公开 Navigation Core 与 Serialization Core，不含平台依赖，用于扁平 Route Schema。

## 创建 Route Spec

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

Reified Overload 会取得 Serializer，其他调用方显式传入。精确映射为：Boolean 对应
`BooleanValue`，Byte/Short/Int 对应 `IntValue`，Long 对应 `LongValue`，Float/Double 对应同名
Value，Char/String/Enum 对应 `Text`，Nullable Scalar 对应其值或 `Null`。

Root 必须是 Class 或 Object，字段仅可为 Scalar、Enum、Nullable Scalar 或受支持的 Inline
Scalar。创建阶段会拒绝 Nested Object、Collection、Map、Polymorphic/Contextual Shape、Unsigned
字段、非 Object Root 与非有限浮点值。单次调用内的 JSON 桥接 Serializer；系统只保留不可变
`NavRoute`/`NavValue`。Decode 拒绝未知 Name、非法 Null 和错误 Variant，省略的 Default 会被重建。
Schema 必须保持可恢复；Alternative JSON Name 已禁用。

另见 [Navigation Core](../viewcompose-navigation-core/README.md)、[架构](../../architecture/navigation.md)、
[Compose 迁移](../../migration/compose-navigation.md)与[生成 API](https://docs.viewcompose.com/api/viewcompose-navigation-kotlinx-serialization/current/)。
