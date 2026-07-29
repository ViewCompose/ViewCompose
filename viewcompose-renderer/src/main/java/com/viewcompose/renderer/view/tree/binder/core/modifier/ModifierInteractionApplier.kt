package com.viewcompose.renderer.view.tree

import android.view.View
import android.widget.TextView
import com.viewcompose.renderer.R
import com.viewcompose.ui.modifier.NativeViewElement
import com.viewcompose.ui.modifier.TransformOrigin
import com.viewcompose.ui.modifier.Visibility
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.overlay.OVERLAY_ANCHOR_TAG_KEY
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.view.requireUiEnvironment
import com.viewcompose.renderer.view.toPx

/**
 * 应用可见性、transform、anchor、testTag、点击和焦点等通用交互 modifier。
 * Applies common interaction modifiers such as visibility, transform, anchor, testTag, click, and focus.
 */
internal object ModifierInteractionApplier {
    /**
     * 应用不依赖节点语义的通用宿主属性。
     * Applies common host properties that do not depend on node semantics.
     */
    fun applyCommonHostProperties(
        view: View,
        resolved: ResolvedModifiers,
        minHeight: Int,
        minWidth: Int,
    ) {
        val layer = resolved.graphicsLayer
        val environment = view.requireUiEnvironment()
        // anchor 元数据只来自显式 modifier，避免 NodeSpec 默认值误注册 overlay 锚点。
        // Anchor metadata is sourced only from resolved modifier elements.
        applyAnchorId(view, resolved.overlayAnchor?.anchorId)
        applyTestTag(view, resolved.testTag?.tag)
        view.alpha = layer?.alpha ?: resolved.alpha?.alpha ?: 1f
        view.visibility = when (resolved.visibility?.visibility ?: Visibility.Visible) {
            Visibility.Visible -> View.VISIBLE
            Visibility.Invisible -> View.INVISIBLE
            Visibility.Gone -> View.GONE
        }
        view.translationX = layer?.translationX ?: resolved.offset?.x?.let(environment::toPx) ?: 0f
        view.translationY = layer?.translationY ?: resolved.offset?.y?.let(environment::toPx) ?: 0f
        view.translationZ = resolved.zIndex?.zIndex ?: 0f
        view.elevation = resolved.elevation?.elevation?.let(environment::toPx) ?: 0f
        view.scaleX = layer?.scaleX ?: 1f
        view.scaleY = layer?.scaleY ?: 1f
        view.rotation = layer?.rotationZ ?: 0f
        view.rotationX = layer?.rotationX ?: 0f
        view.rotationY = layer?.rotationY ?: 0f
        applyTransformOrigin(view, layer?.transformOrigin)
        view.minimumHeight = minHeight
        view.minimumWidth = minWidth
        view.setTag(
            R.id.viewcompose_constraint_layout_id,
            resolved.layoutId?.layoutId ?: resolved.constraint?.referenceId,
        )
        view.setTag(
            R.id.viewcompose_constraint_item_spec,
            resolved.constraint?.constraint,
        )
    }

    /**
     * 应用点击、手势和焦点状态。
     * Applies click, gesture, and focus state.
     */
    fun applyClickAndFocusState(
        view: View,
        node: VNode,
        resolved: ResolvedModifiers,
    ) {
        if (node.type == NodeType.TextField) {
            // EditText 需要保留原生 focus/click 语义，避免覆盖文本选择和键盘行为。
            // EditText should keep its intrinsic focus/click semantics.
            view.setTag(R.id.viewcompose_modifier_click_listener, null)
            view.setOnClickListener(null)
            ModifierGestureApplier.applyGestureState(
                view = view,
                resolved = resolved,
            )
            ModifierFocusInputApplier.apply(
                view = view,
                node = node,
                resolved = resolved,
            )
            return
        }
        val clickableElement = resolved.clickable
        val hasClickListener = clickableElement != null
        val keepIntrinsicInteraction = shouldKeepIntrinsicInteraction(node.type)
        if (clickableElement == null) {
            view.setTag(R.id.viewcompose_modifier_click_listener, null)
            view.setOnClickListener(null)
        } else {
            val listener = (view.getTag(R.id.viewcompose_modifier_click_listener) as? ModifierClickListenerBinding)
                ?: ModifierClickListenerBinding().also {
                    view.setTag(R.id.viewcompose_modifier_click_listener, it)
                }
            listener.onClick = clickableElement.onClick
            view.setOnClickListener(listener)
        }
        view.isClickable = hasClickListener || keepIntrinsicInteraction
        view.isFocusable = hasClickListener || keepIntrinsicInteraction
        view.isFocusableInTouchMode = false
        ModifierGestureApplier.applyGestureState(
            view = view,
            resolved = resolved,
        )
        ModifierFocusInputApplier.apply(
            view = view,
            node = node,
            resolved = resolved,
        )
    }

    /**
     * 当目标 View 是 TextView 时应用文本外观。
     * Applies text appearance when the target View is a TextView.
     */
    fun applyTextAppearanceIfTextView(
        view: View,
        textColor: Int?,
        textSizePx: Float?,
        fontWeight: Int?,
        fontFamily: com.viewcompose.ui.node.spec.UiFontFamily?,
        letterSpacingEm: Float?,
        lineHeightPx: Int?,
        includeFontPadding: Boolean?,
    ) {
        if (view !is TextView) return
        ContentViewBinder.applyTextAppearance(
            view = view,
            textColor = textColor,
            textSizePx = textSizePx,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacingEm = letterSpacingEm,
            lineHeightPx = lineHeightPx,
            includeFontPadding = includeFontPadding,
        )
    }

    fun applyNativeViewConfigs(
        view: View,
        node: VNode,
    ) {
        for (element in node.modifier.elements) {
            if (element is NativeViewElement) {
                element.configure(view)
            }
        }
    }

    private fun shouldKeepIntrinsicInteraction(type: NodeType): Boolean {
        return type == NodeType.Checkbox ||
            type == NodeType.Switch ||
            type == NodeType.RadioButton ||
            type == NodeType.Slider
    }

    private fun applyAnchorId(
        view: View,
        anchorId: String?,
    ) {
        view.setTag(OVERLAY_ANCHOR_TAG_KEY, anchorId)
    }

    private fun applyTestTag(
        view: View,
        testTag: String?,
    ) {
        view.setTag(R.id.viewcompose_test_tag, testTag)
    }

    private fun applyTransformOrigin(
        view: View,
        origin: TransformOrigin?,
    ) {
        val existing = view.getTag(R.id.viewcompose_transform_origin_listener) as? View.OnLayoutChangeListener
        if (origin == null) {
            if (existing != null) {
                view.removeOnLayoutChangeListener(existing)
                view.setTag(R.id.viewcompose_transform_origin_listener, null)
            }
            view.setTag(R.id.viewcompose_transform_origin, null)
            return
        }
        view.setTag(R.id.viewcompose_transform_origin, origin)
        applyPivotFromTransformOrigin(view, origin)
        if (existing != null) return
        val listener = View.OnLayoutChangeListener { changedView, _, _, _, _, _, _, _, _ ->
            val currentOrigin = changedView.getTag(R.id.viewcompose_transform_origin) as? TransformOrigin ?: return@OnLayoutChangeListener
            applyPivotFromTransformOrigin(changedView, currentOrigin)
        }
        view.addOnLayoutChangeListener(listener)
        view.setTag(R.id.viewcompose_transform_origin_listener, listener)
    }

    private fun applyPivotFromTransformOrigin(
        view: View,
        origin: TransformOrigin,
    ) {
        view.pivotX = view.width * origin.pivotFractionX
        view.pivotY = view.height * origin.pivotFractionY
    }
}

private class ModifierClickListenerBinding : View.OnClickListener {
    var onClick: (() -> Unit)? = null

    override fun onClick(view: View?) {
        onClick?.invoke()
    }
}
