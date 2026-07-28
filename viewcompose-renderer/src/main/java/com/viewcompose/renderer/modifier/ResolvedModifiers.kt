package com.viewcompose.renderer.modifier

import com.viewcompose.ui.modifier.*

/**
 * 保存一次 modifier 链解析后的所有可渲染结果，供 patch 阶段按职责分发到 View、LayoutParams 与绘制层。
 * Stores all renderable results from one modifier-chain resolution so patching can fan them out to
 * View state, LayoutParams, and drawing layers by responsibility.
 */
internal class ResolvedModifiers(
    // ViewModifierApplier fields
    var alpha: AlphaModifierElement? = null,
    var drawBehind: DrawBehindModifierElement? = null,
    var drawWithContent: DrawWithContentModifierElement? = null,
    var drawWithCache: DrawWithCacheModifierElement? = null,
    var drawElements: List<ModifierElement> = emptyList(),
    var backgroundColor: BackgroundColorModifierElement? = null,
    var backgroundDrawableRes: BackgroundDrawableResModifierElement? = null,
    var clickable: ClickableModifierElement? = null,
    var semantics: SemanticsConfiguration = SemanticsConfiguration.Empty,
    var testTag: TestTagModifierElement? = null,
    var overlayAnchor: OverlayAnchorModifierElement? = null,
    var layoutId: LayoutIdModifierElement? = null,
    var constraint: ConstraintModifierElement? = null,
    var border: BorderModifierElement? = null,
    var cornerRadius: CornerRadiusModifierElement? = null,
    var shape: ShapeModifierElement? = null,
    var clip: ClipModifierElement? = null,
    var elevation: ElevationModifierElement? = null,
    var offset: OffsetModifierElement? = null,
    var padding: PaddingModifierElement? = null,
    var systemBarsInsetsPadding: SystemBarsInsetsPaddingModifierElement? = null,
    var imeInsetsPadding: ImeInsetsPaddingModifierElement? = null,
    var minHeight: MinHeightModifierElement? = null,
    var minWidth: MinWidthModifierElement? = null,
    var animateContentSize: AnimateContentSizeModifierElement? = null,
    var visibility: VisibilityModifierElement? = null,
    var zIndex: ZIndexModifierElement? = null,
    var graphicsLayer: GraphicsLayerModifierElement? = null,
    var pointerInput: PointerInputModifierElement? = null,
    var combinedClickable: CombinedClickableModifierElement? = null,
    var draggable: DraggableModifierElement? = null,
    var anchoredDraggable: AnchoredDraggableModifierElement? = null,
    var transformable: TransformableModifierElement? = null,
    var gesturePriority: GesturePriorityModifierElement? = null,
    var nestedScroll: NestedScrollModifierElement? = null,
    var focusable: FocusableModifierElement? = null,
    var focusRequester: FocusRequesterModifierElement? = null,
    var focusProperties: com.viewcompose.ui.focus.FocusProperties =
        com.viewcompose.ui.focus.FocusProperties.Default,
    var focusGroup: FocusGroupModifierElement? = null,
    var onFocusChanged: OnFocusChangedModifierElement? = null,
    var previewKeyEvent: PreviewKeyEventModifierElement? = null,
    var keyEvent: KeyEventModifierElement? = null,
    // ViewLayoutParamsFactory fields
    var boxAlign: BoxAlignModifierElement? = null,
    var margin: MarginModifierElement? = null,
    var size: SizeModifierElement? = null,
    var width: WidthModifierElement? = null,
    var height: HeightModifierElement? = null,
    var weight: WeightModifierElement? = null,
    var horizontalAlign: HorizontalAlignModifierElement? = null,
    var verticalAlign: VerticalAlignModifierElement? = null,
)

/**
 * 将声明式 modifier 链折叠成 renderer 可直接应用的稳定结构。
 * Folds the declarative modifier chain into a stable structure that the renderer can apply directly.
 */
internal fun Modifier.resolve(): ResolvedModifiers {
    val result = ResolvedModifiers()
    val drawElements = mutableListOf<ModifierElement>()
    for (element in elements) {
        when (element) {
            is AlphaModifierElement -> result.alpha = element
            is DrawBehindModifierElement -> {
                result.drawBehind = element
                drawElements += element
            }
            is DrawWithContentModifierElement -> {
                result.drawWithContent = element
                drawElements += element
            }
            is DrawWithCacheModifierElement -> {
                result.drawWithCache = element
                drawElements += element
            }
            is BackgroundColorModifierElement -> result.backgroundColor = element
            is BackgroundDrawableResModifierElement -> result.backgroundDrawableRes = element
            is ClickableModifierElement -> result.clickable = element
            is SemanticsModifierElement -> {
                result.semantics = result.semantics.merge(element.configuration)
            }
            is TestTagModifierElement -> result.testTag = element
            is OverlayAnchorModifierElement -> result.overlayAnchor = element
            is LayoutIdModifierElement -> result.layoutId = element
            is ConstraintModifierElement -> result.constraint = element
            is BorderModifierElement -> result.border = element
            is CornerRadiusModifierElement -> {
                result.cornerRadius = element
                result.shape = null
            }
            is ShapeModifierElement -> {
                result.shape = element
                result.cornerRadius = null
            }
            is ClipModifierElement -> result.clip = element
            is ElevationModifierElement -> result.elevation = element
            is OffsetModifierElement -> result.offset = element
            is PaddingModifierElement -> result.padding = element
            is SystemBarsInsetsPaddingModifierElement -> result.systemBarsInsetsPadding = element
            is ImeInsetsPaddingModifierElement -> result.imeInsetsPadding = element
            is MinHeightModifierElement -> result.minHeight = element
            is MinWidthModifierElement -> result.minWidth = element
            is AnimateContentSizeModifierElement -> result.animateContentSize = element
            is VisibilityModifierElement -> result.visibility = element
            is ZIndexModifierElement -> result.zIndex = element
            is GraphicsLayerModifierElement -> result.graphicsLayer = element
            is PointerInputModifierElement -> result.pointerInput = element
            is CombinedClickableModifierElement -> result.combinedClickable = element
            is DraggableModifierElement -> result.draggable = element
            is AnchoredDraggableModifierElement -> result.anchoredDraggable = element
            is TransformableModifierElement -> result.transformable = element
            is GesturePriorityModifierElement -> result.gesturePriority = element
            is NestedScrollModifierElement -> result.nestedScroll = element
            is FocusableModifierElement -> result.focusable = element
            is FocusRequesterModifierElement -> result.focusRequester = element
            is FocusPropertiesModifierElement -> {
                result.focusProperties = result.focusProperties.merge(element.properties)
            }
            is FocusGroupModifierElement -> result.focusGroup = element
            is OnFocusChangedModifierElement -> result.onFocusChanged = element
            is PreviewKeyEventModifierElement -> result.previewKeyEvent = element
            is KeyEventModifierElement -> result.keyEvent = element
            is BoxAlignModifierElement -> result.boxAlign = element
            is MarginModifierElement -> result.margin = element
            is SizeModifierElement -> result.size = element
            is WidthModifierElement -> result.width = element
            is HeightModifierElement -> result.height = element
            is WeightModifierElement -> result.weight = element
            is HorizontalAlignModifierElement -> result.horizontalAlign = element
            is VerticalAlignModifierElement -> result.verticalAlign = element
            is NativeViewElement -> { /* handled separately in applyNativeViewConfigs */ }
        }
    }
    result.drawElements = drawElements.toList()
    return result
}

/**
 * 判断哪些 modifier 变化必须触发布局参数重算，而不是只更新 View 的外观状态。
 * Detects modifier changes that require LayoutParams recomputation instead of a View-only visual update.
 */
internal fun layoutModifiersChanged(previous: ResolvedModifiers, next: ResolvedModifiers): Boolean {
    return previous.boxAlign != next.boxAlign ||
        previous.margin != next.margin ||
        previous.size != next.size ||
        previous.width != next.width ||
        previous.height != next.height ||
        previous.weight != next.weight ||
        previous.horizontalAlign != next.horizontalAlign ||
        previous.verticalAlign != next.verticalAlign ||
        previous.layoutId != next.layoutId ||
        previous.constraint != next.constraint
}
