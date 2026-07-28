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

internal object ModifierInteractionApplier {
    fun applyCommonHostProperties(
        view: View,
        resolved: ResolvedModifiers,
        minHeight: Int,
        minWidth: Int,
    ) {
        val layer = resolved.graphicsLayer
        // Anchor metadata is sourced only from resolved modifier elements.
        applyAnchorId(view, resolved.overlayAnchor?.anchorId)
        applyTestTag(view, resolved.testTag?.tag)
        view.alpha = layer?.alpha ?: resolved.alpha?.alpha ?: 1f
        view.visibility = when (resolved.visibility?.visibility ?: Visibility.Visible) {
            Visibility.Visible -> View.VISIBLE
            Visibility.Invisible -> View.INVISIBLE
            Visibility.Gone -> View.GONE
        }
        view.translationX = layer?.translationX ?: resolved.offset?.x ?: 0f
        view.translationY = layer?.translationY ?: resolved.offset?.y ?: 0f
        view.translationZ = resolved.zIndex?.zIndex ?: 0f
        view.elevation = resolved.elevation?.elevation?.toFloat() ?: 0f
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

    fun applyClickAndFocusState(
        view: View,
        node: VNode,
        resolved: ResolvedModifiers,
    ) {
        if (node.type == NodeType.TextField) {
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

    fun applyTextAppearanceIfTextView(
        view: View,
        textColor: Int?,
        textSizeSp: Int?,
        fontWeight: Int?,
        fontFamily: com.viewcompose.ui.node.spec.UiFontFamily?,
        letterSpacingEm: Float?,
        lineHeightSp: Int?,
        includeFontPadding: Boolean?,
    ) {
        if (view !is TextView) return
        ContentViewBinder.applyTextAppearance(
            view = view,
            textColor = textColor,
            textSizeSp = textSizeSp,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacingEm = letterSpacingEm,
            lineHeightSp = lineHeightSp,
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
