package com.viewcompose.preview.gradle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewInputFingerprintTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `fingerprint is relocation and input-order independent`() {
        val first = temporaryFolder.newFolder("first").resolve("classes").apply {
            mkdirs()
            resolve("sample/A.class").apply {
                parentFile.mkdirs()
                writeText("same-bytecode")
            }
        }
        val second = temporaryFolder.newFolder("second").resolve("classes").apply {
            mkdirs()
            resolve("sample/A.class").apply {
                parentFile.mkdirs()
                writeText("same-bytecode")
            }
        }

        val firstFingerprint = PreviewInputFingerprint.calculate(
            linkedMapOf(
                "sources" to emptyList(),
                "classes" to listOf(first),
            ),
        )
        val secondFingerprint = PreviewInputFingerprint.calculate(
            linkedMapOf(
                "classes" to listOf(second),
                "sources" to emptyList(),
            ),
        )

        assertEquals(firstFingerprint, secondFingerprint)
        second.resolve("sample/A.class").writeText("changed-bytecode")
        assertNotEquals(
            firstFingerprint,
            PreviewInputFingerprint.calculate(mapOf("classes" to listOf(second))),
        )
    }
}
