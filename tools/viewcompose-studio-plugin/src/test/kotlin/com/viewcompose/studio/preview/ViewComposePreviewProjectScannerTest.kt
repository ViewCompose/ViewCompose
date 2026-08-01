package com.viewcompose.studio.preview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewComposePreviewProjectScannerTest {
    @Test
    fun `scanner accepts production source sets and excludes test fixtures`() {
        assertTrue(isSupportedPreviewSourcePath("/project/app/src/main/java/sample/Preview.kt"))
        assertTrue(isSupportedPreviewSourcePath("/project/app/src/debug/java/sample/Preview.kt"))
        assertFalse(isSupportedPreviewSourcePath("/project/core/src/test/kotlin/sample/Fixture.kt"))
        assertFalse(isSupportedPreviewSourcePath("/project/app/src/androidTest/java/sample/Fixture.kt"))
        assertFalse(isSupportedPreviewSourcePath("/project/app/build/generated/Preview.kt"))
    }
}
