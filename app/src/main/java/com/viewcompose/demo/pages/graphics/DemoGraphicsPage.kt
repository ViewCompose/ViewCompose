package com.viewcompose

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.registry.DemoScenarioIds
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
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.preview.tooling.ViewComposePreview
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
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.unit.sp
import kotlin.math.min

@ViewComposePreview(name = "Graphics · Drawing", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewGraphicsDrawing() {
    GraphicsPage(GraphicsFixture.Drawing)
}

@ViewComposePreview(name = "Graphics · Outer shadow", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewGraphicsOuterShadow() {
    GraphicsPage(GraphicsFixture.OuterShadow)
}

@ViewComposePreview(name = "Graphics · Inner shadow", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewGraphicsInnerShadow() {
    GraphicsPage(GraphicsFixture.InnerShadow)
}

@ViewComposePreview(name = "Graphics · Lazy diagnostics", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewGraphicsLazyDiagnostics() {
    GraphicsPage(GraphicsFixture.ShadowList)
}

internal enum class GraphicsFixture(
    val scenarioId: DemoScenarioId,
) {
    Drawing(DemoScenarioIds.GraphicsDrawing),
    OuterShadow(DemoScenarioIds.GraphicsOuterShadow),
    InnerShadow(DemoScenarioIds.GraphicsInnerShadow),
    ShadowList(DemoScenarioIds.GraphicsShadowList),
    ;

    companion object {
        fun from(scenarioId: DemoScenarioId): GraphicsFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported graphics scenario: $scenarioId")
    }
}

internal fun UiTreeBuilder.GraphicsPage(
    fixture: GraphicsFixture,
    scenario: DemoScenarioSpec? = null,
) {
    // Graphics fixtures intentionally do not share one state holder: the shadow backend policy,
    // diagnostics snapshot, and drawing controls have different ownership and disposal rules.
    when (fixture) {
        GraphicsFixture.Drawing -> GraphicsDrawingFixture(scenario)
        GraphicsFixture.OuterShadow -> GraphicsOuterShadowFixture(scenario)
        GraphicsFixture.InnerShadow -> GraphicsInnerShadowFixture(scenario)
        GraphicsFixture.ShadowList -> GraphicsShadowListFixture(scenario)
    }
}

private fun UiTreeBuilder.GraphicsDrawingFixture(scenario: DemoScenarioSpec?) {
    val blendMultiplyState = remember { mutableStateOf(false) }
    val drawContentVisibleState = remember { mutableStateOf(true) }
    val cacheKeyState = remember { mutableStateOf(0) }
    val cacheAccentState = remember { mutableStateOf(false) }
    val primitivesCanvasLabel = stringResource(R.string.demo_graphics_primitives_canvas_label)
    val blendCanvasLabel = stringResource(R.string.demo_graphics_blend_canvas_label)
    val drawCanvasLabel = stringResource(R.string.demo_graphics_draw_canvas_label)

    fun reset() {
        blendMultiplyState.value = false
        drawContentVisibleState.value = true
        cacheKeyState.value = 0
        cacheAccentState.value = false
    }

    val sections = listOf(
        "primitives",
        "path_clip",
        "gradient_blend",
        "draw_modifiers",
        "cache",
    )
    LazyColumn(
        items = sections,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "primitives" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_graphics_primitives_title),
                subtitle = stringResource(R.string.demo_graphics_primitives_summary),
            ) {
                Text(
                    text = stringResource(
                        R.string.demo_graphics_drawing_state,
                        stringResource(
                            if (blendMultiplyState.value) {
                                R.string.demo_graphics_state_blend_multiply
                            } else {
                                R.string.demo_graphics_state_blend_src_over
                            },
                        ),
                        stringResource(
                            if (drawContentVisibleState.value) {
                                R.string.demo_graphics_state_content_visible
                            } else {
                                R.string.demo_graphics_state_content_hidden
                            },
                        ),
                        cacheKeyState.value,
                        stringResource(
                            if (cacheAccentState.value) {
                                R.string.demo_graphics_state_accent_orange
                            } else {
                                R.string.demo_graphics_state_accent_indigo
                            },
                        ),
                    ),
                    modifier = Modifier.graphicsScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Button(
                    text = stringResource(R.string.demo_graphics_drawing_action),
                    onClick = {
                        blendMultiplyState.value = !blendMultiplyState.value
                        drawContentVisibleState.value = !drawContentVisibleState.value
                        cacheKeyState.value += 1
                        cacheAccentState.value = !cacheAccentState.value
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .graphicsScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                )
                Button(
                    text = stringResource(R.string.demo_graphics_reset),
                    onClick = ::reset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .graphicsScenarioTarget(scenario, DemoAutomationRole.Reset),
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                        .backgroundColor(0xFFF8FAFC.toInt())
                        .cornerRadius(16.dp)
                        .padding(8.dp)
                        .testTag(DemoTestTags.GRAPHICS_PRIMITIVES_CANVAS)
                        .graphicsScenarioTarget(scenario, DemoAutomationRole.Target),
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
                        text = primitivesCanvasLabel,
                        origin = Offset(22f, height - 20f),
                        style = TextStyle(textSizePx = 30f, isBold = true),
                        paint = DrawPaint(brush = Brush.SolidColor(0xFF0F172A.toInt())),
                    )
                }
            }

            "path_clip" -> ScenarioSection(
                kind = ScenarioKind.Visual,
                title = stringResource(R.string.demo_graphics_path_clip_title),
                subtitle = stringResource(R.string.demo_graphics_path_clip_summary),
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
                title = stringResource(R.string.demo_graphics_blend_title),
                subtitle = stringResource(R.string.demo_graphics_blend_summary),
            ) {
                Button(
                    text = stringResource(
                        if (blendMultiplyState.value) {
                            R.string.demo_graphics_blend_button_multiply
                        } else {
                            R.string.demo_graphics_blend_button_src_over
                        },
                    ),
                    onClick = { blendMultiplyState.value = !blendMultiplyState.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.GRAPHICS_BLEND_TOGGLE),
                )
                Text(
                    text = stringResource(
                        if (blendMultiplyState.value) {
                            R.string.demo_graphics_blend_status_multiply
                        } else {
                            R.string.demo_graphics_blend_status_src_over
                        },
                    ),
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
                        text = blendCanvasLabel,
                        origin = Offset(20f, height - 16f),
                        style = TextStyle(textSizePx = 28f, isBold = true),
                        paint = DrawPaint(brush = Brush.SolidColor(0xFF0F172A.toInt())),
                    )
                }
            }

            "draw_modifiers" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_graphics_draw_modifiers_title),
                subtitle = stringResource(R.string.demo_graphics_draw_modifiers_summary),
            ) {
                Button(
                    text = stringResource(
                        if (drawContentVisibleState.value) {
                            R.string.demo_graphics_draw_hide_content
                        } else {
                            R.string.demo_graphics_draw_show_content
                        },
                    ),
                    onClick = { drawContentVisibleState.value = !drawContentVisibleState.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.GRAPHICS_DRAW_CONTENT_TOGGLE),
                )
                Text(
                    text = stringResource(
                        if (drawContentVisibleState.value) {
                            R.string.demo_graphics_draw_status_visible
                        } else {
                            R.string.demo_graphics_draw_status_hidden
                        },
                    ),
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
                        text = drawCanvasLabel,
                        origin = Offset(26f, height * 0.60f),
                        style = TextStyle(textSizePx = 30f, isBold = true),
                        paint = DrawPaint(brush = Brush.SolidColor(0xFFFFFFFF.toInt())),
                    )
                }
            }

            "cache" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = stringResource(R.string.demo_graphics_cache_title),
                subtitle = stringResource(R.string.demo_graphics_cache_summary),
            ) {
                val cacheCanvasLabel = stringResource(
                    R.string.demo_graphics_cache_canvas_key,
                    cacheKeyState.value,
                )
                Button(
                    text = stringResource(
                        R.string.demo_graphics_cache_key_action,
                        cacheKeyState.value,
                    ),
                    onClick = { cacheKeyState.value += 1 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .testTag(DemoTestTags.GRAPHICS_CACHE_KEY_BUMP),
                )
                Button(
                    text = stringResource(
                        if (cacheAccentState.value) {
                            R.string.demo_graphics_cache_accent_orange
                        } else {
                            R.string.demo_graphics_cache_accent_indigo
                        },
                    ),
                    onClick = { cacheAccentState.value = !cacheAccentState.value },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.demo_graphics_cache_state,
                        cacheKeyState.value,
                        stringResource(
                            if (cacheAccentState.value) {
                                R.string.demo_graphics_state_accent_orange
                            } else {
                                R.string.demo_graphics_state_accent_indigo
                            },
                        ),
                    ),
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
                                        text = cacheCanvasLabel,
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

            else -> error("Unsupported graphics drawing section: $section")
        }
    }
}

private fun Modifier.graphicsScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier {
    val target = scenario?.automation?.get(role) ?: return this
    return demoAutomationTarget(target)
}
