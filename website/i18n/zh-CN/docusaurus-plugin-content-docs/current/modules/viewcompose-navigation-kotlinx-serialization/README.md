---
translation_source: modules/viewcompose-navigation-kotlinx-serialization/README.md
translation_source_hash: b669c840a269cccf167a4c2acd3dcbcc08026792395e862e44ab5a6eaae6a71c
translation_status: current
---

# Navigation Kotlinx Serialization 模块

`viewcompose-navigation-kotlinx-serialization` 是可选 JVM Adapter，用 Kotlinx
`KSerializer<T>` 创建 Core `NavRouteSpec<T>`。应用 Route Value 已采用 Kotlinx Serialization
时使用它；Route 存在结构化或领域特定 Wire Shape 时继续使用自定义 Core Codec。

该源码已登记的 `0.1.0-alpha01` 制品要等首次签名发布后才会进入 Maven Central。它面向 JVM 11，
向调用方公开 `viewcompose-navigation-core` 与 `kotlinx-serialization-core`，JSON Tree Bridge 保持
私有；不依赖 Android、Host、Lifecycle、View 或 Saved State。

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

Reified Overload 会取得生成的 Serializer；Java 调用方和自定义 Serializer 使用
`serializableNavRouteSpec(name, serializer)`。返回的 Spec 可直接用于 Core Graph 声明、Android
类型化命令与 `NavEntry.toRoute`。

## Schema 与存储契约

| 声明字段 | 存储值 |
| --- | --- |
| Boolean | `BooleanValue` |
| Byte、Short、Int | `IntValue` |
| Long | `LongValue` |
| Float / Double | `FloatValue` / `DoubleValue` |
| Char、String、Enum | `Text` |
| Nullable Scalar | 对应值或 `Null` |

Root 必须是 Class 或 Object，字段仅可为 Scalar、Enum、Nullable Scalar 或受支持的 Inline
Scalar。创建阶段会拒绝 Nested Object、Collection、Map、Polymorphic/Contextual Shape、Unsigned
字段和非 Object Root。Float 与 Double 必须有限。Serializer 省略的 Default 会保持缺席，并在
Decode 时重建。

映射依据 Descriptor，而不是当前值。Long 即使落在 Int 范围内也始终为 `LongValue`。Decode 会
拒绝未知 Name、非法 Null 与不匹配的 `NavValue` Variant。Adapter 只在一次调用内使用严格 JSON
Object Tree；Snapshot 与 Host 仍只保留不可变 `NavRoute` 和 `NavValue` 数据。

## 兼容性与验证

请保持显式 Route Name、序列化字段名、字段类型、Nullability 与 Default 对恢复状态兼容。Rename
需要应用迁移；Alternative JSON Name 被刻意禁用。测试覆盖支持的 Scalar Mapping、Object/Inline
Route、Default、存储稳定性、错误参数与每种被拒绝的 Schema Family。

另见 [Navigation Core 手册](https://docs.viewcompose.com/zh-CN/modules/viewcompose-navigation-core)、
[导航架构](https://docs.viewcompose.com/zh-CN/architecture/navigation)与
[Compose 迁移对比](https://docs.viewcompose.com/zh-CN/migration/compose-navigation)。生成符号位于
[`viewcompose-navigation-kotlinx-serialization` API 树](https://docs.viewcompose.com/api/viewcompose-navigation-kotlinx-serialization/current/)。
