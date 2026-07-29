package com.viewcompose.ui.node.spec

import com.viewcompose.text.TextDocument
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextDecoration
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.unit.UiSp

/**
 * Text/RichText 节点的文本内容、排版和字体属性。
 * Text content, typography, and font properties for Text/RichText nodes.
 */
data class TextNodeProps(
    val text: CharSequence?,
    val maxLines: Int,
    val overflow: TextOverflow,
    val textAlign: TextAlign,
    val textColor: Int,
    val textSizeSp: UiSp,
    val fontWeight: Int? = null,
    val fontFamily: UiFontFamily? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightSp: UiSp? = null,
    val includeFontPadding: Boolean = false,
    val textDecoration: TextDecoration = TextDecoration.None,
    val document: TextDocument = TextDocument.plain(text?.toString().orEmpty()),
) : NodeSpec

interface UiFontFamily

interface PlatformUiFontFamily : UiFontFamily {
    val font: Any
}

class GenericUiFontFamily(
    override val font: Any,
) : PlatformUiFontFamily

fun uiFontFamily(font: Any?): UiFontFamily? {
    return if (font == null) {
        null
    } else {
        GenericUiFontFamily(font)
    }
}
