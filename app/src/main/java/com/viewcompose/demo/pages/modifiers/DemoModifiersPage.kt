package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.widget.core.LazyColumn
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.remember

@ViewComposePreview(name = "Modifiers · Visual", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewModifiersVisual() {
    ModifiersPage(initialPageIndex = 0)
}

@ViewComposePreview(name = "Modifiers · Size", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewModifiersSize() {
    ModifiersPage(initialPageIndex = 1)
}

@ViewComposePreview(name = "Modifiers · Accessibility", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewModifiersAccessibility() {
    ModifiersPage(initialPageIndex = 2)
}

internal fun UiTreeBuilder.ModifiersPage(
    initialPageIndex: Int = 0,
) {
    val selectedPageState = remember { mutableStateOf(initialPageIndex.coerceIn(0, 2)) }
    val pageItems = when (selectedPageState.value) {
        0 -> listOf("page", "page_filter", "elevation", "border_clip", "background_drawable", "alpha_ripple", "corner", "verify")
        1 -> listOf("page", "page_filter", "size_constraints", "verify")
        else -> listOf("page", "page_filter", "accessibility", "native_view", "offset_zindex", "verify")
    }

    LazyColumn(
        items = pageItems,
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        RenderModifiersSection(
            section = section,
            selectedPageState = selectedPageState,
        )
    }
}

private fun UiTreeBuilder.RenderModifiersSection(
    section: String,
    selectedPageState: MutableState<Int>,
) {
    when (section) {
        "page" -> ChapterPageOverviewSection(
            title = "Modifier 展示",
            goal = "覆盖全部 Modifier 函数：视觉效果、尺寸约束和辅助功能。",
            modules = listOf("elevation", "border", "clip", "alpha", "rippleColor", "cornerRadius", "minWidth", "minHeight", "fillMaxHeight", "contentDescription", "nativeView", "offset", "zIndex"),
        )

        "page_filter" -> ChapterPageFilterSection(
            pages = listOf("视觉", "尺寸", "辅助"),
            selectedIndex = selectedPageState.value,
            onSelectionChange = { selectedPageState.value = it },
        )

        "elevation" -> ModifierElevationSection()
        "border_clip" -> ModifierBorderClipSection()
        "background_drawable" -> ModifierBackgroundDrawableSection()
        "alpha_ripple" -> ModifierAlphaRippleSection()
        "corner" -> ModifierCornerSection()
        "size_constraints" -> ModifierSizeConstraintsSection()
        "accessibility" -> ModifierAccessibilitySection()
        "native_view" -> ModifierNativeViewSection()
        "offset_zindex" -> ModifierOffsetZIndexSection()
        else -> ModifierVerificationSection()
    }
}
