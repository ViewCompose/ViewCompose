---
translation_source: modules/viewcompose-graphics-core/README.md
translation_source_hash: 71b9db253be583a2e2387f0a8add8870f144771a2b45561ff3cd506eec13f030
translation_status: current
---

# Graphics Core 模块

`viewcompose-graphics-core` 是 ViewCompose 的平台无关即时图形模型。它定义几何、Path、Brush、
Paint 与 Filter、有序绘制命令、经过验证且可复用的 Scene、可变 Recorder 和单条目 Draw Cache。
它不依赖 Android Canvas、Bitmap、View 或组合系统。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-graphics-core:0.1.0-alpha02")
}
```

- 稳定性：**Alpha**。命令顺序、Scene 验证和当前 Geometry/Paint 模型已经审查并测试；更完整的
  Text、Image 所有权和 Filter 能力契约仍可能演进。
- 平台：Kotlin/JVM，不依赖 Android Framework。
- 该产物没有运行时依赖，可用于确定性单元测试与工具。
- 应用通常通过 `viewcompose-graphics` 或核心 UI 模块间接获得它。

## 坐标与颜色约定

坐标使用当前 Renderer 绘制空间。Android 初始是物理像素、x 向右、y 向下、顺时针角度为正；
Transform 可以改变该空间。Geometry 值保持轻量，不拒绝负数、反转或非有限输入。跨 Renderer
输出必须确定时，应在录制前验证应用数据。

`UiColor` 是打包且非预乘的 `0xAARRGGBB` Int 源码别名。`DrawPaint.alpha` 是额外乘数，约定为
`0f..1f`；Android Renderer 会限制它。Gradient Stop 不排序也不验证，应按 `0f..1f` Offset
顺序提供平台支持数量的 Stop。

## Geometry 与 Matrix

`Offset`、`Size`、`Rect`、`Radius` 和 `RoundRect` 对齐常见 Canvas Geometry，并保留调用方输入。
`Rect.width` 与 `height` 是有符号的边界差；Corner Radius 不会预先按 Bounds 限制。

`Matrix3` 保存九个 Row-major 系数，按内容比较。构造时复制输入数组，但公开的 `values` 数组仍可
变。把 Matrix 放入 Command 或 Cache Key 后应视为冻结；修改会改变插入后的相等性和 Hash。

## Path 路径模型

`PathModel` 是有序命令列表加 `NonZero` 或 `EvenOdd` Fill Rule。`PathBuilder` 在 `build` 时复制
命令，之后复用 Builder 不影响旧模型。直接构造 `PathModel` 会保留传入列表，应传不可变 List。

```kotlin
val triangle = path {
    moveTo(8f, 8f)
    lineTo(56f, 8f)
    lineTo(32f, 48f)
    close()
}
```

Arc 角度遵循 Android 默认约定。`forceMoveTo` 会在弧线起点开始新 Contour，否则 Renderer 从
当前点连接。Path 数字输入不做验证。

## Brush、Style 与 Filter

Brush 支持纯色、Linear、Radial 与 Sweep Gradient。`DrawStyle` 选择 Fill 或 Stroke，包括 Width、
Cap、Join 和 Miter Limit。它们是 Renderer 请求而非保证；平台能力和 API Level 决定精确的 Blend
与 Filter 输出。

`ColorFilterModel` 支持 Tint 和 4x5 Matrix。Matrix 必须包含 20 个元素，但数组会被保留，数据类
相等性按数组身份比较。Filter 相等性影响缓存时，应复用一个不可变数组或稳定 Wrapper。
`ImageFilterModel` 支持 Blur 和 `inner` 先于 `outer` 的有序 Chain。

`DrawPaint` 是浅不可变：嵌套 Stop List 与 Filter Array 不会防御性复制，录制前应冻结。Alpha、
Stroke 尺寸、Radius 和 Gradient Stop 不验证，以便自定义 Renderer 定义平台策略。

## Command 与 Scene

`DrawCommand` 分离状态操作、Transform、Clip、嵌套 Scene、Geometry、Image 与 Text。Renderer 严格
按 List 顺序回放。`Save` 和 `SaveLayer` 压入状态；`Restore` 弹出。

`DrawScene` 复制 Command List，并拒绝无匹配 Restore 或遗留 Save Depth。嵌套 Scene 独立验证，
可以在不同 Transform 与 Clip 下复用：

```kotlin
val badge = drawScene {
    save()
    clipRect(Rect(0f, 0f, 64f, 48f))
    drawPath(triangle, DrawPaint(brush = Brush.SolidColor(0xFF6750A4.toInt())))
    restore()
}
```

`ImageRef` 携带 Host 定义的稳定 ID 和声明的固有像素尺寸，不拥有或加载像素。`TextStyle` 有意只
覆盖像素尺寸、Bold 与 Italic，不提供 Font Family、Shaping、Wrapping、Locale、Alignment 或富文本。

## Recorder 与 Cache

`DrawRecorder` 是可变且线程受限的 Builder。`toCommands` 复制但不验证 Save/Restore；`toScene`
复制并验证。`group` 构建单独验证的嵌套 Scene。导出后 `clear` 可复用 Recorder，不影响旧快照。

`DrawCache<T>` 在基于相等性的一个 Key 下保留一个非空 Value；不同 Key 替换条目。它不观察
State、不同步线程，也不推断 Size、Density、Theme 输入；Key 必须包含所有语义依赖，外部输入变化
时应 Clear。`null` 结果永远不会命中缓存。Builder 异常会传播并保留旧条目。

## 测试自定义图形代码

- 用 `toScene` 验证 Save/Restore，包括嵌套 Scene 边界。
- 在复用 Recorder 与 Builder 后测试命令顺序和快照不变性。
- Cache Key 测试应包含 Size、Density、Theme 和 Resource Identity。
- 显式测试 Gradient Stop 顺序、Matrix Array 修改和 Filter Array 身份。
- Android BlendMode、Shader、Bitmap、Text 和 Image Filter 保真测试放在 Renderer 或
  `viewcompose-graphics` 集成层。

模块测试覆盖 Cache 命中与替换、命令录制、嵌套 Scene 复用、Save/Restore 拒绝、Path 命令顺序
与 Fill Type。

## 相关文档

- [UI Contract 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-ui-contract)
- [Renderer 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-renderer)
- [架构概览](https://docs.viewcompose.com/zh-CN/architecture/overview)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-graphics-core` API 树](https://docs.viewcompose.com/api/viewcompose-graphics-core/current/)。

## 兼容性说明

`0.1.0-alpha02` 建立 Android 对齐坐标与颜色约定、有序命令回放、平衡不可变 Scene、浅不可变
Paint 模型、轻量 Image Reference 与单条目显式 Key Cache。平台执行属于 Renderer，组合 Modifier
属于 `viewcompose-graphics`。
