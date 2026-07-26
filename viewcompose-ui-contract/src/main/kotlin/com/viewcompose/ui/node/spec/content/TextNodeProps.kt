package com.viewcompose.ui.node.spec

import com.viewcompose.text.TextDocument
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextDecoration
import com.viewcompose.ui.node.TextOverflow

data class TextNodeProps(
    val text: CharSequence?,
    val maxLines: Int,
    val overflow: TextOverflow,
    val textAlign: TextAlign,
    val textColor: Int,
    val textSizeSp: Int,
    val fontWeight: Int? = null,
    val fontFamily: UiFontFamily? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightSp: Int? = null,
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
