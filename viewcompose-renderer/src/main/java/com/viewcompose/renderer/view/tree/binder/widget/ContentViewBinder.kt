package com.viewcompose.renderer.view.tree

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.viewcompose.renderer.R
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.TextDecoration
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.CanvasNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.spec.UiFontFamily
import com.viewcompose.renderer.interop.toTypefaceOrNull
import com.viewcompose.renderer.view.container.DeclarativeCanvasLayout
import com.viewcompose.text.TextDocument
import com.viewcompose.ui.graphics.DrawBlock

/**
 * 绑定文本、按钮和画布等内容节点，集中处理 Android TextView/Button 的样式还原与事件桥接。
 * Binds text, button, and canvas content nodes while centralizing Android TextView/Button style
 * restoration and event bridging.
 */
internal object ContentViewBinder {
    data class TextSpec(
        val document: TextDocument,
        val maxLines: Int,
        val overflow: TextOverflow,
        val gravity: Int,
        val fontWeight: Int? = null,
        val fontFamily: UiFontFamily? = null,
        val letterSpacingEm: Float? = null,
        val lineHeightSp: Int? = null,
        val includeFontPadding: Boolean = false,
        val textDecoration: TextDecoration = TextDecoration.None,
    )

    data class ButtonSpec(
        val text: CharSequence?,
        val enabled: Boolean,
        val iconSpacing: Int,
        val leadingIcon: ImageSource.Resource?,
        val trailingIcon: ImageSource.Resource?,
        val iconTint: Int,
        val iconSize: Int,
        val onClick: (() -> Unit)?,
    )

    data class CanvasSpec(
        val onDraw: DrawBlock,
    )

    fun bindText(
        view: TextView,
        spec: TextSpec,
    ) {
        view.text = AndroidTextDocumentAdapter.toCharSequence(view, spec.document)
        view.maxLines = spec.maxLines
        view.ellipsize = when (spec.overflow) {
            TextOverflow.Clip -> null
            TextOverflow.Ellipsis -> TextUtils.TruncateAt.END
        }
        view.gravity = spec.gravity
        applyTextAppearance(
            view = view,
            fontWeight = spec.fontWeight,
            fontFamily = spec.fontFamily,
            letterSpacingEm = spec.letterSpacingEm,
            lineHeightSp = spec.lineHeightSp,
            includeFontPadding = spec.includeFontPadding,
        )
        applyTextDecoration(view, spec.textDecoration)
    }

    fun bindButton(
        view: Button,
        spec: ButtonSpec,
    ) {
        view.text = spec.text
        view.isEnabled = spec.enabled
        view.isAllCaps = false
        view.stateListAnimator = null
        view.setSingleLine(false)
        view.maxLines = 2
        view.ellipsize = TextUtils.TruncateAt.END
        view.gravity = Gravity.CENTER
        view.minimumWidth = 0
        view.minWidth = 0
        view.compoundDrawablePadding = spec.iconSpacing
        view.setCompoundDrawablesRelative(
            resolveButtonIconDrawable(
                view = view,
                source = spec.leadingIcon,
                tint = spec.iconTint,
                size = spec.iconSize,
            ),
            null,
            resolveButtonIconDrawable(
                view = view,
                source = spec.trailingIcon,
                tint = spec.iconTint,
                size = spec.iconSize,
            ),
            null,
        )
        updateButtonClickListener(
            view = view,
            enabled = spec.enabled,
            onClick = spec.onClick,
        )
    }

    fun bindCanvas(
        view: DeclarativeCanvasLayout,
        spec: CanvasSpec,
    ) {
        view.setCanvasDrawBlock(spec.onDraw)
    }

    fun readTextSpec(node: VNode): TextSpec {
        val spec = node.requireSpec<TextNodeProps>()
        return TextSpec(
            document = spec.document,
            maxLines = spec.maxLines,
            overflow = spec.overflow,
            gravity = spec.textAlign.toTextGravity(),
            fontWeight = spec.fontWeight,
            fontFamily = spec.fontFamily,
            letterSpacingEm = spec.letterSpacingEm,
            lineHeightSp = spec.lineHeightSp,
            includeFontPadding = spec.includeFontPadding,
            textDecoration = spec.textDecoration,
        )
    }

    fun readButtonSpec(node: VNode): ButtonSpec {
        val spec = node.requireSpec<ButtonNodeProps>()
        return ButtonSpec(
            text = spec.text,
            enabled = spec.enabled,
            iconSpacing = spec.iconSpacing,
            leadingIcon = spec.leadingIcon,
            trailingIcon = spec.trailingIcon,
            iconTint = spec.iconTint,
            iconSize = spec.iconSize,
            onClick = spec.onClick,
        )
    }

    fun readCanvasSpec(node: VNode): CanvasSpec {
        val spec = node.requireSpec<CanvasNodeProps>()
        return CanvasSpec(
            onDraw = spec.onDraw,
        )
    }

    internal fun toTextGravity(alignment: com.viewcompose.ui.node.TextAlign): Int {
        return alignment.toTextGravity()
    }

    internal fun resolveButtonIconDrawable(
        view: TextView,
        source: ImageSource.Resource?,
        tint: Int,
        size: Int,
    ): Drawable? {
        return createButtonIconDrawable(
            view = view,
            source = source,
            tint = tint,
            size = size,
        )
    }

    internal fun updateButtonClickListener(
        view: Button,
        enabled: Boolean,
        onClick: (() -> Unit)?,
    ) {
        val binding = (view.getTag(R.id.viewcompose_button_click_listener) as? ButtonClickBinding)
            ?: ButtonClickBinding().also {
                view.setTag(R.id.viewcompose_button_click_listener, it)
                view.setOnClickListener(it)
            }
        binding.enabled = enabled
        binding.onClick = onClick
    }

    private fun com.viewcompose.ui.node.TextAlign.toTextGravity(): Int {
        return when (this) {
            com.viewcompose.ui.node.TextAlign.Start -> Gravity.START or Gravity.CENTER_VERTICAL
            com.viewcompose.ui.node.TextAlign.Center -> Gravity.CENTER
            com.viewcompose.ui.node.TextAlign.End -> Gravity.END or Gravity.CENTER_VERTICAL
        }
    }

    private fun createButtonIconDrawable(
        view: TextView,
        source: ImageSource.Resource?,
        tint: Int,
        size: Int,
    ): Drawable? {
        val drawable = source
            ?.let { ContextCompat.getDrawable(view.context, it.resId) }
            ?.mutate()
            ?: return null
        drawable.setTint(tint)
        drawable.setBounds(0, 0, size, size)
        return drawable
    }

    internal fun applyTypeface(
        view: TextView,
        fontWeight: Int?,
        fontFamily: UiFontFamily?,
    ) {
        val originalTypeface = ensureOriginalTextAppearanceCached(view)
        if (fontWeight == null && fontFamily == null) {
            view.typeface = originalTypeface
            return
        }
        val base = fontFamily.toTypefaceOrNull() ?: originalTypeface
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && fontWeight != null) {
            view.typeface = Typeface.create(base, fontWeight, false)
        } else {
            val style = when {
                fontWeight != null && fontWeight >= 700 -> Typeface.BOLD
                else -> Typeface.NORMAL
            }
            view.setTypeface(base, style)
        }
    }

    internal fun applyTextAppearance(
        view: TextView,
        textColor: Int? = null,
        textSizeSp: Int? = null,
        fontWeight: Int? = null,
        fontFamily: UiFontFamily? = null,
        letterSpacingEm: Float? = null,
        lineHeightSp: Int? = null,
        includeFontPadding: Boolean? = null,
    ) {
        ensureOriginalTextAppearanceCached(view)
        if (textColor != null) {
            view.setTextColor(textColor)
        }
        if (textSizeSp != null) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat())
        }
        applyTypeface(view, fontWeight, fontFamily)
        val originalLetterSpacing = view.getTag(R.id.viewcompose_original_letter_spacing) as? Float ?: 0f
        view.letterSpacing = letterSpacingEm ?: originalLetterSpacing
        val originalLineHeight = view.getTag(R.id.viewcompose_original_line_height) as? Int ?: view.lineHeight
        if (lineHeightSp != null) {
            TextViewCompat.setLineHeight(
                view,
                TypedValue.COMPLEX_UNIT_SP,
                lineHeightSp.toFloat(),
            )
        } else {
            TextViewCompat.setLineHeight(view, originalLineHeight)
        }
        includeFontPadding?.let { view.includeFontPadding = it }
    }

    private fun ensureOriginalTextAppearanceCached(view: TextView): Typeface {
        val cachedTypeface = view.getTag(R.id.viewcompose_original_typeface) as? Typeface
        if (cachedTypeface != null) {
            return cachedTypeface
        }
        val originalTypeface = view.typeface ?: Typeface.DEFAULT
        view.setTag(R.id.viewcompose_original_typeface, originalTypeface)
        view.setTag(R.id.viewcompose_original_letter_spacing, view.letterSpacing)
        view.setTag(R.id.viewcompose_original_line_height, view.lineHeight)
        return originalTypeface
    }

    internal fun applyTextDecoration(view: TextView, decoration: TextDecoration) {
        val flags = view.paintFlags and
            (Paint.UNDERLINE_TEXT_FLAG or Paint.STRIKE_THRU_TEXT_FLAG).inv()
        view.paintFlags = flags or when (decoration) {
            TextDecoration.None -> 0
            TextDecoration.Underline -> Paint.UNDERLINE_TEXT_FLAG
            TextDecoration.LineThrough -> Paint.STRIKE_THRU_TEXT_FLAG
            TextDecoration.UnderlineLineThrough ->
                Paint.UNDERLINE_TEXT_FLAG or Paint.STRIKE_THRU_TEXT_FLAG
        }
    }
}

private class ButtonClickBinding : android.view.View.OnClickListener {
    var enabled: Boolean = true
    var onClick: (() -> Unit)? = null

    override fun onClick(view: android.view.View?) {
        if (enabled) {
            onClick?.invoke()
        }
    }
}
