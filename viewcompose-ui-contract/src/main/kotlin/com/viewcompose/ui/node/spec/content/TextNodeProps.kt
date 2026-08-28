package com.viewcompose.ui.node.spec

import com.viewcompose.text.TextDocument
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextDecoration
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.unit.UiSp

/**
 * Immutable renderer properties shared by plain-text and rich-text nodes.
 *
 * [document] is the single text-content snapshot. Plain text uses [TextDocument.plain], while
 * styled text supplies its immutable spans, paragraphs, and inline attachments through the same
 * model. Platform `CharSequence` values belong at renderer interop boundaries and are not retained
 * by this specification.
 *
 * @property document structured text, paragraph, span, and inline-content snapshot
 * @property maxLines maximum laid-out line count
 * @property overflow behavior when content exceeds [maxLines] or the available bounds
 * @property textAlign logical horizontal alignment
 * @property textColor default text color
 * @property textSizeSp default text size
 * @property fontWeight optional platform font weight override
 * @property fontFamily optional renderer-compatible font family
 * @property letterSpacingEm optional letter spacing in em units
 * @property lineHeightSp optional line height
 * @property includeFontPadding whether platform font top and bottom padding is included
 * @property textDecoration default text decoration
 */
data class TextNodeProps(
    val document: TextDocument,
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
) : NodeSpec

/** Platform-neutral marker for a renderer-compatible font-family value. */
interface UiFontFamily

/** Font family that exposes an opaque platform font object to a platform renderer. */
interface PlatformUiFontFamily : UiFontFamily {
    /** Opaque platform font object. */
    val font: Any
}

/**
 * Default immutable wrapper for an opaque platform [font].
 *
 * Two wrappers compare equal only when they reference the same platform font object. This keeps
 * repeated declarative snapshots stable without assuming that an opaque platform type provides a
 * renderer-compatible value-equality contract.
 */
class GenericUiFontFamily(
    override val font: Any,
) : PlatformUiFontFamily {
    override fun equals(other: Any?): Boolean =
        this === other || other is GenericUiFontFamily && font === other.font

    override fun hashCode(): Int = System.identityHashCode(font)
}

/**
 * Wraps a platform font object as a [UiFontFamily].
 *
 * @param font opaque platform font object, or `null`
 * @return a new font-family wrapper, or `null` when [font] is `null`
 */
fun uiFontFamily(font: Any?): UiFontFamily? {
    return if (font == null) {
        null
    } else {
        GenericUiFontFamily(font)
    }
}
