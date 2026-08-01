package com.viewcompose.studio.preview

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtNamedFunction

class ViewComposePreviewLineMarkerProviderTest : BasePlatformTestCase() {
    fun testImportedPreviewAnnotationCreatesMarker() {
        val function = configureFunction(
            """
            package sample

            import com.viewcompose.preview.tooling.ViewComposePreview

            @ViewComposePreview
            fun ExamplePreview() = Unit
            """.trimIndent(),
            symbolName = "ExamplePreview",
        )

        assertNotNull(
            ViewComposePreviewLineMarkerProvider()
                .getLineMarkerInfo(checkNotNull(function.nameIdentifier)),
        )
    }

    fun testSourceMetaAnnotationCreatesMarker() {
        val function = configureFunction(
            """
            package sample

            import com.viewcompose.preview.tooling.ViewComposePreview

            @ViewComposePreview
            annotation class CatalogPreview

            @CatalogPreview
            fun ExamplePreview() = Unit
            """.trimIndent(),
            symbolName = "ExamplePreview",
        )

        assertNotNull(
            ViewComposePreviewLineMarkerProvider()
                .getLineMarkerInfo(checkNotNull(function.nameIdentifier)),
        )
    }

    fun testUnrelatedAnnotationWithSameShortNameDoesNotCreateMarker() {
        val function = configureFunction(
            """
            package sample

            annotation class ViewComposePreview

            @ViewComposePreview
            fun ExamplePreview() = Unit
            """.trimIndent(),
            symbolName = "ExamplePreview",
        )

        assertNull(
            ViewComposePreviewLineMarkerProvider()
                .getLineMarkerInfo(checkNotNull(function.nameIdentifier)),
        )
    }

    fun testCaretInsidePreviewFunctionBuildsSourceSelection() {
        myFixture.configureByText(
            "Sample.kt",
            """
            package sample

            import com.viewcompose.preview.tooling.ViewComposePreview

            @ViewComposePreview
            fun ExamplePreview() {
                val title = "Pre<caret>view"
            }
            """.trimIndent(),
        )

        val selection = myFixture.file.previewSelectionAtOffset(myFixture.caretOffset)

        assertNotNull(selection)
        assertEquals("ExamplePreview", selection?.symbolName)
        assertEquals(6, selection?.line)
        assertTrue(selection?.filePath?.endsWith("/Sample.kt") == true)
    }

    fun testCaretOutsidePreviewFunctionDoesNotBuildSourceSelection() {
        myFixture.configureByText(
            "Sample.kt",
            """
            package sample

            fun RegularFunction() {
                Unit<caret>
            }
            """.trimIndent(),
        )

        assertNull(myFixture.file.previewSelectionAtOffset(myFixture.caretOffset))
    }

    fun testFileFollowChoosesThePreviewNearestToTheCaret() {
        myFixture.configureByText(
            "Sample.kt",
            """
            package sample

            import com.viewcompose.preview.tooling.ViewComposePreview

            @ViewComposePreview
            fun FirstPreview() = Unit

            fun Helper() {
                Unit<caret>
            }

            @ViewComposePreview
            fun SecondPreview() = Unit
            """.trimIndent(),
        )

        val selection = myFixture.file.previewSelectionNearestToOffset(myFixture.caretOffset)

        assertEquals("SecondPreview", selection?.symbolName)
    }

    private fun configureFunction(
        source: String,
        symbolName: String,
    ): KtNamedFunction {
        val file = myFixture.configureByText("Sample.kt", source)
        return PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java)
            .single { function -> function.name == symbolName }
    }
}
