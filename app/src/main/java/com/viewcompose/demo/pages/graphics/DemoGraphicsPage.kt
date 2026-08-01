package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.graphics.Canvas
import com.viewcompose.graphics.drawBehind
import com.viewcompose.graphics.drawWithCache
import com.viewcompose.graphics.drawWithContent
import com.viewcompose.graphics.core.BlendMode
import com.viewcompose.graphics.core.Brush
import com.viewcompose.graphics.core.ColorStop
import com.viewcompose.graphics.core.DrawCommand
import com.viewcompose.graphics.core.DrawPaint
import com.viewcompose.graphics.core.DrawStyle
import com.viewcompose.graphics.core.ImageFilterModel
import com.viewcompose.graphics.core.Offset
import com.viewcompose.graphics.core.PathFillType
import com.viewcompose.graphics.core.Radius
import com.viewcompose.graphics.core.Rect
import com.viewcompose.graphics.core.RoundRect
import com.viewcompose.graphics.core.TextStyle
import com.viewcompose.graphics.core.path
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.widget.core.remember
import com.viewcompose.ui.unit.sp
import kotlin.math.min

@ViewComposePreview(name = "Graphics · Drawing", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewGraphicsDrawing() {
    GraphicsPage(initialPageIndex = 0)
}

@ViewComposePreview(name = "Graphics · Outer shadow", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewGraphicsOuterShadow() {
    GraphicsPage(initialPageIndex = 1)
}

@ViewComposePreview(name = "Graphics · Inner shadow", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewGraphicsInnerShadow() {
    GraphicsPage(initialPageIndex = 2)
}

@ViewComposePreview(name = "Graphics · Lazy diagnostics", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewGraphicsLazyDiagnostics() {
    GraphicsPage(initialPageIndex = 3)
}

internal fun UiTreeBuilder.GraphicsPage(
    initialPageIndex: Int = GRAPHICS_PAGE_DRAWING,
) {
    val selectedPageState = remember {
        mutableStateOf(
            initialPageIndex.coerceIn(
                GRAPHICS_PAGE_DRAWING,
                GRAPHICS_PAGE_SHADOW_DIAGNOSTICS,
            ),
        )
    }
    val blendMultiplyState = remember { mutableStateOf(false) }
    val drawContentVisibleState = remember { mutableStateOf(true) }
    val cacheKeyState = remember { mutableStateOf(0) }
    val cacheAccentState = remember { mutableStateOf(false) }
    val shadowPageState = rememberGraphicsShadowPageState()

    InstallGraphicsShadowLifecycle()
    val sections = graphicsPageItems(selectedPageState.value)
    LazyColumn(
        items = sections,
        key = { it },
        contentType = ::graphicsPageContentType,
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "overview" -> ChapterPageOverviewSection(
                title = "Graphics",
                goal = "验证 Canvas、draw modifier 与高级阴影装饰层是否稳定，并覆盖精确外阴影、内阴影、Lazy 缓存和后端诊断。",
                modules = listOf(
                    "viewcompose-graphics-core",
                    "viewcompose-graphics",
                    "viewcompose-shadow-android",
                    "viewcompose-renderer",
                ),
            )

            "page_filter" -> ChapterPageFilterSection(
                pages = listOf("绘制", "外阴影", "内阴影", "Lazy/诊断"),
                selectedIndex = selectedPageState.value,
                onSelectionChange = { selectedPageState.value = it },
            )

            "primitives" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "基础图元",
                subtitle = "Rect / Line / Circle / Text 组合，确认 Canvas 节点可稳定自绘。",
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                        .backgroundColor(0xFFF8FAFC.toInt())
                        .cornerRadius(16.dp)
                        .padding(8.dp)
                        .testTag(DemoTestTags.GRAPHICS_PRIMITIVES_CANVAS),
                ) { context ->
                    val width = context.size.width
                    val height = context.size.height
                    drawRoundRect(
                        roundRect = RoundRect(
                            rect = Rect(10f, 10f, width - 10f, height - 10f),
                            topLeft = Radius(18f, 18f),
                            topRight = Radius(18f, 18f),
                            bottomRight = Radius(18f, 18f),
                            bottomLeft = Radius(18f, 18f),
                        ),
                        paint = DrawPaint(
                            brush = Brush.SolidColor(0xFFE2E8F0.toInt()),
                        ),
                    )
                    drawLine(
                        from = Offset(26f, 34f),
                        to = Offset(width - 26f, 34f),
                        paint = DrawPaint(
                            brush = Brush.SolidColor(0xFF2563EB.toInt()),
                            style = DrawStyle.Stroke(width = 4f),
                        ),
                    )
                    drawCircle(
                        center = Offset(x = width * 0.27f, y = height * 0.63f),
                        radius = min(width, height) * 0.16f,
                        paint = DrawPaint(brush = Brush.SolidColor(0xFF0EA5E9.toInt())),
                    )
                    drawRect(
                        rect = Rect(
                            left = width * 0.50f,
                            top = height * 0.50f,
                            right = width * 0.88f,
                            bottom = height * 0.76f,
                        ),
                        paint = DrawPaint(brush = Brush.SolidColor(0xFF22C55E.toInt())),
                    )
                    drawText(
                        text = "Canvas primitives",
                        origin = Offset(22f, height - 20f),
                        style = TextStyle(textSizePx = 30f, isBold = true),
                        paint = DrawPaint(brush = Brush.SolidColor(0xFF0F172A.toInt())),
                    )
                }
            }

            "path_clip" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = "Path + Clip",
                subtitle = "路径裁剪与路径描边叠加，验证 clipPath 与 path draw 顺序。",
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(164.dp)
                        .backgroundColor(0xFFF1F5F9.toInt())
                        .cornerRadius(16.dp)
                        .padding(8.dp)
                        .testTag(DemoTestTags.GRAPHICS_PATH_CLIP_CANVAS),
                ) { context ->
                    val width = context.size.width
                    val height = context.size.height
                    val wavePath = path {
                        fillType(PathFillType.EvenOdd)
                        moveTo(width * 0.12f, height * 0.20f)
                        cubicTo(
                            width * 0.32f,
                            height * 0.04f,
                            width * 0.68f,
                            height * 0.46f,
                            width * 0.88f,
                            height * 0.24f,
                        )
                        lineTo(width * 0.88f, height * 0.82f)
                        lineTo(width * 0.12f, height * 0.82f)
                        close()
                    }
                    save()
                    clipPath(wavePath)
                    drawRect(
                        rect = Rect(0f, 0f, width, height),
                        paint = DrawPaint(
                            brush = Brush.LinearGradient(
                                from = Offset.Zero,
                                to = Offset(width, height),
                                colorStops = listOf(
                                    ColorStop(0f, 0xFFDBEAFE.toInt()),
                                    ColorStop(1f, 0xFF86EFAC.toInt()),
                                ),
                            ),
                        ),
                    )
                    restore()
                    drawPath(
                        path = wavePath,
                        paint = DrawPaint(
                            brush = Brush.SolidColor(0xFF334155.toInt()),
                            style = DrawStyle.Stroke(width = 4f),
                        ),
                    )
                }
            }

            "gradient_blend" -> ScenarioSection(
                kind = ScenarioKind.Stress,
                title = "Gradient + Blend/Filter",
                subtitle = "切换 Multiply，观察高对比重叠层变化；同时附带 blur imageFilter 降级路径。",
            ) {
                Button(
                    text = if (blendMultiplyState.value) "Blend: Multiply" else "Blend: SrcOver",
                    onClick = { blendMultiplyState.value = !blendMultiplyState.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.GRAPHICS_BLEND_TOGGLE),
                )
                Text(
                    text = if (blendMultiplyState.value) {
                        "当前混合模式：Multiply"
                    } else {
                        "当前混合模式：SrcOver"
                    },
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(top = 6.dp, bottom = 8.dp)
                        .testTag(DemoTestTags.GRAPHICS_BLEND_STATUS),
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .backgroundColor(0xFFF8FAFC.toInt())
                        .cornerRadius(16.dp)
                        .padding(8.dp)
                        .testTag(DemoTestTags.GRAPHICS_BLEND_CANVAS),
                ) { context ->
                    val width = context.size.width
                    val height = context.size.height
                    drawRect(
                        rect = Rect(0f, 0f, width, height),
                        paint = DrawPaint(
                            brush = Brush.LinearGradient(
                                from = Offset(0f, 0f),
                                to = Offset(width, height),
                                colorStops = listOf(
                                    ColorStop(0f, 0xFFFFFFFF.toInt()),
                                    ColorStop(1f, 0xFFE2E8F0.toInt()),
                                ),
                            ),
                        ),
                    )
                    // Base layer: vivid yellow circle.
                    drawCircle(
                        center = Offset(width * 0.46f, height * 0.56f),
                        radius = min(width, height) * 0.23f,
                        paint = DrawPaint(
                            brush = Brush.SolidColor(0xFFFACC15.toInt()),
                            alpha = 0.96f,
                        ),
                    )
                    // Blend target layer: magenta circle with toggle-able blend mode.
                    drawCircle(
                        center = Offset(width * 0.60f, height * 0.56f),
                        radius = min(width, height) * 0.23f,
                        paint = DrawPaint(
                            brush = Brush.SolidColor(0xFFEC4899.toInt()),
                            alpha = 0.96f,
                            blendMode = if (blendMultiplyState.value) {
                                BlendMode.Multiply
                            } else {
                                BlendMode.SrcOver
                            },
                        ),
                    )
                    // Filter sample: keep a small blur block for imageFilter regression path.
                    drawRoundRect(
                        roundRect = RoundRect(
                            rect = Rect(width * 0.70f, height * 0.16f, width * 0.90f, height * 0.34f),
                            topLeft = Radius(14f, 14f),
                            topRight = Radius(14f, 14f),
                            bottomRight = Radius(14f, 14f),
                            bottomLeft = Radius(14f, 14f),
                        ),
                        paint = DrawPaint(
                            brush = Brush.SolidColor(0xFF1E293B.toInt()),
                            alpha = 0.62f,
                            imageFilter = ImageFilterModel.Blur(radiusX = 2f, radiusY = 2f),
                        ),
                    )
                    drawText(
                        text = "Blend target",
                        origin = Offset(20f, height - 16f),
                        style = TextStyle(textSizePx = 28f, isBold = true),
                        paint = DrawPaint(brush = Brush.SolidColor(0xFF0F172A.toInt())),
                    )
                }
            }

            "draw_modifiers" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = "Draw Modifier 顺序",
                subtitle = "drawBehind + drawWithContent：切换是否透传内容，验证 modifier 链路顺序稳定。",
            ) {
                Button(
                    text = if (drawContentVisibleState.value) "隐藏内容层" else "显示内容层",
                    onClick = { drawContentVisibleState.value = !drawContentVisibleState.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.GRAPHICS_DRAW_CONTENT_TOGGLE),
                )
                Text(
                    text = if (drawContentVisibleState.value) "drawWithContent 已透传内容" else "drawWithContent 已拦截内容",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(top = 6.dp, bottom = 8.dp)
                        .testTag(DemoTestTags.GRAPHICS_DRAW_CONTENT_STATUS),
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(146.dp)
                        .cornerRadius(16.dp)
                        .drawBehind {
                            drawRoundRect(
                                roundRect = RoundRect(
                                    rect = Rect(0f, 0f, 1000f, 1000f),
                                    topLeft = Radius(20f, 20f),
                                    topRight = Radius(20f, 20f),
                                    bottomRight = Radius(20f, 20f),
                                    bottomLeft = Radius(20f, 20f),
                                ),
                                paint = DrawPaint(brush = Brush.SolidColor(0xFFE2E8F0.toInt())),
                            )
                        }
                        .drawWithContent(key = drawContentVisibleState.value) { _ ->
                            if (drawContentVisibleState.value) {
                                drawContent()
                            }
                        }
                        .padding(8.dp)
                        .testTag(DemoTestTags.GRAPHICS_DRAW_CONTENT_CANVAS),
                ) { context ->
                    val width = context.size.width
                    val height = context.size.height
                    drawRect(
                        rect = Rect(16f, 16f, width - 16f, height - 16f),
                        paint = DrawPaint(brush = Brush.SolidColor(0xFF0EA5E9.toInt())),
                    )
                    drawText(
                        text = "Canvas content layer",
                        origin = Offset(26f, height * 0.60f),
                        style = TextStyle(textSizePx = 30f, isBold = true),
                        paint = DrawPaint(brush = Brush.SolidColor(0xFFFFFFFF.toInt())),
                    )
                }
            }

            "cache" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = "drawWithCache 语义",
                subtitle = "切换 cache key 才重建缓存命令，切换 accent 只影响内容层，验证 cache 命中路径。",
            ) {
                Button(
                    text = "切换 Cache Key (${cacheKeyState.value})",
                    onClick = { cacheKeyState.value += 1 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.GRAPHICS_CACHE_KEY_BUMP),
                )
                Button(
                    text = if (cacheAccentState.value) "Accent: Orange" else "Accent: Indigo",
                    onClick = { cacheAccentState.value = !cacheAccentState.value },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "cacheKey=${cacheKeyState.value} · accent=${if (cacheAccentState.value) "orange" else "indigo"}",
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .margin(top = 6.dp, bottom = 8.dp)
                        .testTag(DemoTestTags.GRAPHICS_CACHE_STATUS),
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .drawWithCache(key = cacheKeyState.value) { context ->
                            cache(key = cacheKeyState.value) {
                                val width = context.size.width
                                val height = context.size.height
                                listOf(
                                    DrawCommand.DrawRoundRect(
                                        roundRect = RoundRect(
                                            rect = Rect(0f, 0f, width, height),
                                            topLeft = Radius(24f, 24f),
                                            topRight = Radius(24f, 24f),
                                            bottomRight = Radius(24f, 24f),
                                            bottomLeft = Radius(24f, 24f),
                                        ),
                                        paint = DrawPaint(
                                            brush = Brush.SolidColor(0xFFE2E8F0.toInt()),
                                        ),
                                    ),
                                    DrawCommand.DrawText(
                                        text = "cacheKey=${cacheKeyState.value}",
                                        origin = Offset(20f, height - 18f),
                                        style = TextStyle(textSizePx = 30f, isBold = true),
                                        paint = DrawPaint(brush = Brush.SolidColor(0xFF334155.toInt())),
                                    ),
                                )
                            }
                        }
                        .padding(8.dp)
                        .testTag(DemoTestTags.GRAPHICS_CACHE_CANVAS),
                ) { context ->
                    val width = context.size.width
                    val height = context.size.height
                    drawCircle(
                        center = Offset(width * 0.56f, height * 0.45f),
                        radius = min(width, height) * 0.20f,
                        paint = DrawPaint(
                            brush = Brush.SolidColor(
                                if (cacheAccentState.value) 0xFFF97316.toInt() else 0xFF4F46E5.toInt(),
                            ),
                            alpha = 0.82f,
                        ),
                    )
                }
            }

            "verify" -> GraphicsVerificationNotes(selectedPageState.value)

            else -> RenderGraphicsShadowSection(
                section = section,
                state = shadowPageState,
            )
        }
    }
}
