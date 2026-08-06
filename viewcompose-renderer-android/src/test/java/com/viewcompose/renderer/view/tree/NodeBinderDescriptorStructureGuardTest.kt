package com.viewcompose.renderer.view.tree

/*
 * 契约测试职责：锁定 renderer view/tree 中的 Node Binder Descriptor Structure Guard 边界，防止节点协议或依赖关系在重构中漂移。
 * Contract test responsibility: locks down the Node Binder Descriptor Structure Guard boundary in renderer view/tree and prevents node protocol or dependency drift.
 */

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeBinderDescriptorStructureGuardTest {
    @Test
    fun `node binder descriptor sources stay under descriptor subdirectory`() {
        val moduleDir = resolveRendererModuleDir()
        val coreDir = moduleDir.resolve("src/main/java/com/viewcompose/renderer/view/tree/binder/core")
        val descriptorDir = coreDir.resolve("descriptor")

        assertTrue("descriptor directory missing: $descriptorDir", Files.isDirectory(descriptorDir))

        val flattened = nodeBinderFileNames(coreDir).sorted()
        assertEquals(
            "NodeBinder* files must not be flattened under binder/core",
            emptyList<String>(),
            flattened,
        )
    }

    @Test
    fun `descriptor directory contains expected descriptor source set`() {
        val moduleDir = resolveRendererModuleDir()
        val descriptorDir = moduleDir.resolve(
            "src/main/java/com/viewcompose/renderer/view/tree/binder/core/descriptor",
        )
        val actual = nodeBinderFileNames(descriptorDir)
        val expected = setOf(
            "NodeBinderDescriptors.kt",
            "NodeBinderDescriptorModel.kt",
            "NodeBinderContentDescriptors.kt",
            "NodeBinderInputDescriptors.kt",
            "NodeBinderFeedbackDescriptors.kt",
            "NodeBinderMediaDescriptors.kt",
            "NodeBinderContainerDescriptors.kt",
            "NodeBinderCollectionDescriptors.kt",
        )
        assertEquals(expected, actual)
    }

    private fun resolveRendererModuleDir(): Path {
        val cwd = Paths.get("").toAbsolutePath().normalize()
        val moduleLocalMain = cwd.resolve("src/main")
        if (Files.exists(moduleLocalMain)) {
            return cwd
        }
        return cwd.resolve("viewcompose-renderer-android")
    }

    private fun nodeBinderFileNames(dir: Path): Set<String> {
        Files.newDirectoryStream(dir).use { entries ->
            return entries.asSequence()
                .filter { Files.isRegularFile(it) }
                .map { it.fileName.toString() }
                .filter { it.startsWith("NodeBinder") && it.endsWith(".kt") }
                .toSet()
        }
    }
}
