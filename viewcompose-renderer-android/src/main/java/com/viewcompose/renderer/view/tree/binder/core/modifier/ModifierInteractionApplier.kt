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
 * Applies common interaction modifiers including visibility, transforms, anchors, test tags, clicks, and focus.
 * Applies common interaction modifiers such as visibility, transform, anchor, testTag, click, and focus.
 */
internal object ModifierInteractionApplier {
    /**
     * Applies common host properties that do not depend on node semantics.
     * Applies common host properties that do not depend on node semantics.
     */
    fun applyCommonHostProperties(
        view: View,
        resolved: ResolvedModifiers,
        minHeight: Int,
        minWidth: Int,
        offsetX: Float,
        offsetY: Float,
    ) {
        val layer = resolved.graphicsLayer
        val environment = view.requireUiEnvironment()
        // Anchor metadata comes only from explicit modifiers so NodeSpec defaults cannot register overlay anchors accidentally.
        // Anchor metadata is sourced only from resolved modifier elements.
        applyAnchorId(view, resolved.overlayAnchor?.anchorId)
        applyTestTag(view, resolved.testTag?.tag)
        view.alpha = layer?.alpha ?: resolved.alpha?.alpha ?: 1f
        view.visibility = when (resolved.visibility?.visibility ?: Visibility.Visible) {
            Visibility.Visible -> View.VISIBLE
            Visibility.Invisible -> View.INVISIBLE
            Visibility.Gone -> View.GONE
        }
        view.translationX = layer?.translationX ?: offsetX
        view.translationY = layer?.translationY ?: offsetY
        // Parent drawing order owns zIndex; writing translationZ would incorrectly alter platform shadows.
        // Parent-side stable drawing order owns zIndex; translationZ would alter platform shadows.
        view.translationZ = 0f
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
     * Applies click, gesture, and focus state.
     * Applies click, gesture, and focus state.
     */
    fun applyClickAndFocusState(
        view: View,
        node: VNode,
        resolved: ResolvedModifiers,
    ) {
        if (node.type == NodeType.TextField) {
            // EditText keeps native focus and click semantics so text selection and keyboard behavior are not replaced.
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
     * Applies text appearance when the target is a TextView.
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
