package com.viewcompose.studio.preview

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ViewComposeProjectDetectorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `detects applied preview Gradle plugin`() {
        val root = temporaryFolder.newFolder("plugin-project").toPath()
        val module = Files.createDirectories(root.resolve("feature"))
        Files.writeString(
            module.resolve("build.gradle.kts"),
            """id("com.viewcompose.preview")""",
        )

        val detection = ViewComposeProjectDetector().detect(root)

        assertTrue(detection.isViewComposeProject)
        assertEquals(ViewComposeProjectEvidenceKind.GradlePlugin, detection.evidenceKind)
        assertEquals(module.resolve("build.gradle.kts"), detection.evidencePath)
    }

    @Test
    fun `detects an exported descriptor catalog without Gradle model access`() {
        val root = temporaryFolder.newFolder("catalog-project").toPath()
        val catalog = root.resolve(
            "app/build/viewcompose-preview/debug/descriptors.json",
        )
        catalog.createParentDirectories()
        Files.writeString(catalog, "{}")

        val detection = ViewComposeProjectDetector().detect(root)

        assertTrue(detection.isViewComposeProject)
        assertEquals(ViewComposeProjectEvidenceKind.DescriptorCatalog, detection.evidenceKind)
        assertEquals(catalog, detection.evidencePath)
    }

    @Test
    fun `does not expose the tool window for unrelated projects`() {
        val root = temporaryFolder.newFolder("unrelated-project").toPath()
        Files.writeString(
            root.resolve("build.gradle.kts"),
            """plugins { kotlin("jvm") }""",
        )

        val detection = ViewComposeProjectDetector().detect(root)

        assertFalse(detection.isViewComposeProject)
        assertEquals(null, detection.evidenceKind)
        assertEquals(null, detection.evidencePath)
    }

    @Test
    fun `ignores generated markers outside the preview artifact directory`() {
        val root = temporaryFolder.newFolder("generated-project").toPath()
        val generated = root.resolve(".gradle/generated/build.gradle.kts")
        generated.createParentDirectories()
        Files.writeString(generated, """id("com.viewcompose.preview")""")

        assertFalse(ViewComposeProjectDetector().detect(root).isViewComposeProject)
    }
}

private fun Path.createParentDirectories() {
    Files.createDirectories(checkNotNull(parent))
}
