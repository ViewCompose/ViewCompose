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

    @Test
    fun `group fingerprints allow code changes without invalidating retained resources`() {
        val root = temporaryFolder.newFolder("grouped")
        val classes = root.resolve("classes").apply { mkdirs() }
        val resources = root.resolve("res").apply { mkdirs() }
        classes.resolve("Preview.class").writeText("first")
        resources.resolve("values.xml").writeText("<resources />")
        val first = PreviewInputFingerprint.calculateByGroup(
            mapOf("classes" to listOf(classes), "resources" to listOf(resources)),
        )

        classes.resolve("Preview.class").writeText("second")
        val second = PreviewInputFingerprint.calculateByGroup(
            mapOf("classes" to listOf(classes), "resources" to listOf(resources)),
        )

        assertNotEquals(first.getValue("classes"), second.getValue("classes"))
        assertEquals(first.getValue("resources"), second.getValue("resources"))
        assertEquals(
            PreviewInputFingerprint.combine(mapOf("resources" to first.getValue("resources"))),
            PreviewInputFingerprint.combine(mapOf("resources" to second.getValue("resources"))),
        )
    }

    @Test
    fun `persistent worker compatibility includes the exact build input identity`() {
        val layoutlib = "a".repeat(64)
        val build = "b".repeat(64)
        val runtime = "c".repeat(64)
        val baseline = previewWorkerCompatibilityFingerprint(layoutlib, build, runtime)

        assertEquals(
            baseline,
            previewWorkerCompatibilityFingerprint(layoutlib, build, runtime),
        )
        assertNotEquals(
            baseline,
            previewWorkerCompatibilityFingerprint(layoutlib, "d".repeat(64), runtime),
        )
        assertNotEquals(
            baseline,
            previewWorkerCompatibilityFingerprint("e".repeat(64), build, runtime),
        )
        assertNotEquals(
            baseline,
            previewWorkerCompatibilityFingerprint(layoutlib, build, "f".repeat(64)),
        )
    }
}
