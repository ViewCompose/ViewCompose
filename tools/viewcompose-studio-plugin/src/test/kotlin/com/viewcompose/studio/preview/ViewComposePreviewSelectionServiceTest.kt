package com.viewcompose.studio.preview

import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ViewComposePreviewSelectionServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `full refresh reruns the complete known preview task graph`() {
        assertFalse(PreviewRefreshPolicy.Full.useStudioCache)
        assertTrue(PreviewRefreshPolicy.Full.useKnownDescriptor)
        assertTrue(PreviewRefreshPolicy.Full.forceGradleRerender)
        assertTrue(PreviewRefreshPolicy.Full.forceFullRebuild)
        assertFalse(PreviewRefreshPolicy.Manual.forceFullRebuild)
    }

    @Test
    fun `explicit document save is consumed once instead of scheduling automatic refresh`() {
        val source = temporaryFolder.newFile("Preview.kt").toPath().toAbsolutePath().normalize()
        val suppressions = ConcurrentHashMap.newKeySet<String>().apply {
            add(source.toString())
        }

        assertTrue(
            unsuppressedSavedPreviewPaths(
                changedPaths = listOf(source.toString(), source.toString()),
                explicitSaveSuppressions = suppressions,
            ).isEmpty(),
        )
        assertEquals(
            listOf(source.toString()),
            unsuppressedSavedPreviewPaths(
                changedPaths = listOf(source.toString()),
                explicitSaveSuppressions = suppressions,
            ),
        )
    }

    @Test
    fun `hidden preview retains one fingerprinted invalidation until it is shown`() {
        val selection = PreviewSourceSelection(
            filePath = "/project/Preview.kt",
            symbolName = "Preview",
            line = 1,
        )
        val request = ActivePreviewRequest(selection, "light")
        val store = SavedPreviewInvalidationStore()

        assertTrue(
            store.record(
                PendingSavedPreviewRefresh(
                    request = request,
                    refreshPolicy = PreviewRefreshPolicy.SavedSourceInput,
                    inputFingerprint = "first",
                ),
            ),
        )
        assertFalse(
            store.record(
                PendingSavedPreviewRefresh(
                    request = request,
                    refreshPolicy = PreviewRefreshPolicy.SavedSourceInput,
                    inputFingerprint = "first",
                ),
            ),
        )
        assertTrue(
            store.record(
                PendingSavedPreviewRefresh(
                    request = request,
                    refreshPolicy = PreviewRefreshPolicy.SavedInput,
                    inputFingerprint = "second",
                ),
            ),
        )

        val pending = store.consume(request)
        assertEquals(PreviewRefreshPolicy.SavedInput, pending?.refreshPolicy)
        assertEquals("second", pending?.inputFingerprint)
        assertNull(store.consume(request))
    }

    @Test
    fun `saved source fingerprint changes only when source content changes`() {
        val source = temporaryFolder.newFile("Fingerprint.kt").toPath()
        Files.writeString(source, "Text(\"first\")")
        val first = savedPreviewInputFingerprint(listOf(source.toString()))
        val unchanged = savedPreviewInputFingerprint(listOf(source.toString()))
        Files.writeString(source, "Text(\"second\")")
        val second = savedPreviewInputFingerprint(listOf(source.toString()))

        assertEquals(first, unchanged)
        assertNotEquals(first, second)
    }

    @Test
    fun `automatic refresh only runs for the visible active preview`() {
        assertTrue(
            shouldRunAutomaticPreviewRefresh(
                projectDisposed = false,
                activeRequestMatches = true,
                toolWindowVisible = true,
            ),
        )
        assertFalse(
            shouldRunAutomaticPreviewRefresh(
                projectDisposed = false,
                activeRequestMatches = true,
                toolWindowVisible = false,
            ),
        )
        assertFalse(
            shouldRunAutomaticPreviewRefresh(
                projectDisposed = false,
                activeRequestMatches = false,
                toolWindowVisible = true,
            ),
        )
    }

    @Test
    fun `refreshes when the selected preview source or project source changes`() {
        val projectRoot = temporaryFolder.newFolder("project").toPath()
        val appRoot = projectRoot.resolve("app").also { directory ->
            Files.createDirectories(directory)
        }
        Files.writeString(appRoot.resolve("build.gradle.kts"), "")
        val source = projectRoot.resolve("app/src/main/kotlin/SamplePreview.kt").also { path ->
            Files.createDirectories(checkNotNull(path.parent))
            Files.writeString(path, "")
        }
        val sibling = projectRoot.resolve("app/src/main/kotlin/Other.kt").also { path ->
            Files.writeString(path, "")
        }
        val selection = PreviewSourceSelection(
            filePath = source.toString(),
            symbolName = "SamplePreview",
            line = 12,
        )

        assertTrue(
            savedPreviewInputMatches(
                projectRoot = projectRoot,
                selection = selection,
                changedPaths = listOf(source.toString()),
            ),
        )
        assertTrue(
            savedPreviewInputMatches(
                projectRoot = projectRoot,
                selection = selection,
                changedPaths = listOf(sibling.toString()),
            ),
        )
    }

    @Test
    fun `refreshes for dependency resources but ignores generated output and unrelated files`() {
        val projectRoot = temporaryFolder.newFolder("dependency-project").toPath()
        val appRoot = projectRoot.resolve("app").also { directory ->
            Files.createDirectories(directory)
        }
        Files.writeString(appRoot.resolve("build.gradle.kts"), "")
        val source = projectRoot.resolve("app/src/main/kotlin/SamplePreview.kt").also { path ->
            Files.createDirectories(checkNotNull(path.parent))
            Files.writeString(path, "")
        }
        val selection = PreviewSourceSelection(
            filePath = source.toString(),
            symbolName = "SamplePreview",
            line = 12,
        )
        val dependencyResource =
            projectRoot.resolve("ui-theme/src/main/res/values/colors.xml").also { path ->
                Files.createDirectories(checkNotNull(path.parent))
                Files.writeString(path, "")
            }
        val generatedSource =
            projectRoot.resolve("app/build/generated/SamplePreview.kt").also { path ->
                Files.createDirectories(checkNotNull(path.parent))
                Files.writeString(path, "")
            }
        val notes = projectRoot.resolve("notes.md").also { path ->
            Files.writeString(path, "")
        }
        val unrelatedSource = projectRoot.resolve("unrelated/src/main/kotlin/Other.kt").also { path ->
            Files.createDirectories(checkNotNull(path.parent))
            Files.writeString(path, "")
        }
        val versionCatalog = projectRoot.resolve("gradle/libs.versions.toml").also { path ->
            Files.createDirectories(checkNotNull(path.parent))
            Files.writeString(path, "")
        }
        val scope = PreviewInputScope.create(
            projectRoot = projectRoot,
            moduleRoot = appRoot,
            manifestInputPaths = listOf(
                projectRoot.resolve("ui-theme/build/intermediates/runtime.jar").toString(),
            ),
        )

        assertTrue(
            savedPreviewInputMatches(
                projectRoot = projectRoot,
                selection = selection,
                changedPaths = listOf(dependencyResource.toString()),
                inputScope = scope,
            ),
        )
        assertFalse(
            savedPreviewInputMatches(
                projectRoot = projectRoot,
                selection = selection,
                changedPaths = listOf(generatedSource.toString()),
                inputScope = scope,
            ),
        )
        assertFalse(
            savedPreviewInputMatches(
                projectRoot = projectRoot,
                selection = selection,
                changedPaths = listOf(unrelatedSource.toString()),
                inputScope = scope,
            ),
        )
        assertTrue(
            savedPreviewInputMatches(
                projectRoot = projectRoot,
                selection = selection,
                changedPaths = listOf(versionCatalog.toString()),
                inputScope = scope,
            ),
        )
        assertFalse(
            savedPreviewInputMatches(
                projectRoot = projectRoot,
                selection = selection,
                changedPaths = listOf(notes.toString()),
                inputScope = scope,
            ),
        )
    }

    @Test
    fun `fast refresh is limited to one saved Kotlin or Java preview source`() {
        val projectRoot = temporaryFolder.newFolder("fast-source-project").toPath()
        val source = projectRoot.resolve("app/src/main/kotlin/SamplePreview.kt").also { path ->
            Files.createDirectories(checkNotNull(path.parent))
            Files.writeString(path, "")
        }
        val sibling = source.resolveSibling("Other.kt").also { path -> Files.writeString(path, "") }
        val resource = projectRoot.resolve("app/src/main/res/values/colors.xml").also { path ->
            Files.createDirectories(checkNotNull(path.parent))
            Files.writeString(path, "")
        }
        val selection = PreviewSourceSelection(
            filePath = source.toString(),
            symbolName = "SamplePreview",
            line = 1,
        )

        assertTrue(
            savedPreviewFastRefreshEligible(selection, listOf(source.toString())),
        )
        assertFalse(
            savedPreviewFastRefreshEligible(
                selection,
                listOf(source.toString(), sibling.toString()),
            ),
        )
        assertFalse(
            savedPreviewFastRefreshEligible(selection, listOf(resource.toString())),
        )
    }
}
