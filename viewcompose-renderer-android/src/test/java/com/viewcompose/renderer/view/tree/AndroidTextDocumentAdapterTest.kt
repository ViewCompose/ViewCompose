package com.viewcompose.renderer.view.tree

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import com.viewcompose.text.TextDocument
import com.viewcompose.text.TextSpanStyle
import com.viewcompose.text.textDocument
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidTextDocumentAdapterTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `plain document reuses its platform String`() {
        val document = TextDocument.plain("plain")

        val result = AndroidTextDocumentAdapter.toCharSequence(
            view = TextView(context),
            document = document,
        )

        assertSame(document.text, result)
    }

    @Test
    fun `styled document remains a platform Spanned`() {
        val document = textDocument {
            append(
                value = "styled",
                style = TextSpanStyle(fontWeight = 700),
            )
        }

        val result = AndroidTextDocumentAdapter.toCharSequence(
            view = TextView(context),
            document = document,
        )

        assertTrue(result is Spanned)
    }
}
