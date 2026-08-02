# Graphics 跨平台分层落地执行计划（2026-03）

## 基线
- 当前 graphics 章节仍为 placeholder，尚无正式图形 API。
- 现有能力仅包含 `graphicsLayer`（变换层）与 `backgroundDrawableRes`（背景资源），缺少统一 draw/canvas 管线。
- 架构已具备 animation/gesture 双层分层模式，可复用到 graphics。

## 本轮范围与目标
- 新增双层模块：
  - `:viewcompose-graphics-core`（纯 Kotlin/JVM）
  - `:viewcompose-graphics`（DSL/集成层）
- API 对齐 Compose：
  - `UiTreeBuilder.Canvas(...)`
  - `Modifier.drawBehind { ... }`
  - `Modifier.drawWithContent { ... }`
  - `Modifier.drawWithCache { ... }`
  - `Modifier.draw { ... }`、`Modifier.drawCache { ... }`（短写）
- 渲染范围：Android View 2D 图形主能力（Canvas/Paint/Path/Brush/Blend/Clip/Transform/Text/Image/RenderEffect），不含 3D/OpenGL/Vulkan。
- 硬切：graphics demo 从 Planned 转为 Available，不保留 placeholder 兼容路径。

## 90% 能力矩阵（目标）
1. 基础图元：line/rect/roundRect/circle/oval/arc/path
2. 画笔与样式：fill/stroke/cap/join/miter/alpha
3. 着色：solid/linear/radial/sweep gradient
4. 变换与裁剪：translate/scale/rotate/skew + clipRect/clipPath
5. 图像与文本：bitmap/image + text（基础）
6. 混合与特效：blend mode/color filter/image filter（blur/chain）
7. 绘制阶段：drawBehind/drawWithContent/drawWithCache
8. 缓存语义：cache 命中/失效与依赖变更
9. Android 特有高阶图形：`host-android` interop（RuntimeShader/RenderEffect/Drawable）

## Checklist
- [x] Step 1: 执行文档与阻塞文档落地
- [x] Step 2: 新增 graphics-core / graphics 模块与守卫接入
- [x] Step 3: graphics-core 图形模型与绘制指令落地
- [x] Step 4: ui-contract 新增 Canvas 节点与 draw modifier 契约
- [x] Step 5: viewcompose-graphics API（Canvas + draw modifiers）落地
- [x] Step 6: renderer 接入 Canvas 节点与 draw modifier 管线
- [x] Step 7: host-android 新增 AndroidGraphicsInterop
- [x] Step 8: demo + preview + instrumentation 覆盖
- [x] Step 9: 文档收口与归档

## 门禁
- 每步：`./gradlew qaQuick` + `./gradlew qaPreview`
- 里程碑：
  - Step 6 后：`ANDROID_SERIAL=98101FFBA003AE ./gradlew qaFull`
  - Step 8 后：`ANDROID_SERIAL=98101FFBA003AE ./gradlew qaFull`

## 提交记录
1. `docs: add graphics execution plan and blocker context`
2. `build: add graphics modules and purity guardrails`
3. `feat: add graphics-core geometry paint path command and cache primitives`
4. `feat: add canvas node and draw modifier contracts in ui-contract`
5. `feat: add graphics dsl canvas and draw modifier wrappers`
6. `feat: integrate canvas node and draw modifier pipeline in renderer`
7. `feat: add android graphics interop bridge in host-android`
8. `demo: add graphics showcase scenarios and visual regression coverage`
9. `test: add graphics preview catalog and paparazzi baseline coverage`
10. `docs: codify graphics architecture boundaries and workflow guardrails`
11. `docs: close and archive graphics execution plan`

## 阻塞记录
- 见 [GRAPHICS_BLOCKER_CONTEXT_2026-03.md](../../GRAPHICS_BLOCKER_CONTEXT_2026-03.md)
