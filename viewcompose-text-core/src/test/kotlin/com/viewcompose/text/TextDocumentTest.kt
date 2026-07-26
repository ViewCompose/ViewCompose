package com.viewcompose.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDocumentTest {
    @Test
    fun `builder records spans paragraphs and inline attachments`() {
        val attachment = InlineTextAttachment(
            id = "image-1",
            mimeType = "image/png",
            uri = "content://images/1",
        )
        val document = textDocument {
            append(
                value = "Hello",
                style = TextSpanStyle(
                    fontWeight = 700,
                    color = 0xFF00FF00.toInt(),
                ),
            )
            append(" ")
            appendAttachment(attachment)
            addParagraphStyle(
                range = TextRange(0, length),
                style = ParagraphStyle(
                    alignment = ParagraphTextAlignment.Center,
                ),
            )
        }

        assertEquals("Hello $INLINE_ATTACHMENT_CHARACTER", document.text)
        assertEquals(TextRange(0, 5), document.spanStyles.single().range)
        assertEquals(TextRange(0, 7), document.paragraphStyles.single().range)
        assertEquals(6, document.inlineAttachments.single().offset)
        assertEquals(attachment, document.inlineAttachments.single().attachment)
    }

    @Test
    fun `replacement preserves unaffected annotations and shifts following content`() {
        val attachment = InlineTextAttachment(
            id = "file",
            mimeType = "application/pdf",
        )
        val original = textDocument {
            append("ab", TextSpanStyle(underline = true))
            append("cd")
            appendAttachment(attachment)
        }
        val replacement = textDocument {
            append("XY", TextSpanStyle(fontStyle = TextFontStyle.Italic))
        }

        val result = original.replace(
            range = TextRange(1, 3),
            replacement = replacement,
        )

        assertEquals("aXYd$INLINE_ATTACHMENT_CHARACTER", result.text)
        assertEquals(4, result.inlineAttachments.single().offset)
        assertTrue(
            result.spanStyles.any { span ->
                span.range == TextRange(1, 3) &&
                    span.style.fontStyle == TextFontStyle.Italic
            },
        )
        assertTrue(
            result.spanStyles.any { span ->
                span.range == TextRange(0, 1) &&
                    span.style.underline
            },
        )
    }

    @Test
    fun `text field buffer preserves document annotations across plain edit`() {
        val original = textDocument {
            append("bold", TextSpanStyle(fontWeight = 700))
            append(" tail")
        }
        val buffer = TextFieldBuffer(
            originalValue = TextFieldValue(original),
            proposedValue = TextFieldValue(original),
        )

        buffer.replace(5, 9, "end")
        val result = buffer.toTextFieldValue()

        assertEquals("bold end", result.text)
        assertEquals(
            listOf(
                TextSpanRange(
                    TextRange(0, 4),
                    TextSpanStyle(fontWeight = 700),
                ),
            ),
            result.document.spanStyles,
        )
    }
}
