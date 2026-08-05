package com.viewcompose.ui.guard

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyImageProtocolGuardTest {
    @Test
    fun `production source does not retain the removed remote image protocol`() {
        val repositoryRoot = resolveRepositoryRoot()
        val forbiddenNames = listOf(
            "RemoteImageLoader",
            "RemoteImageRequest",
            "RemoteImageTarget",
            "PlatformRemoteImageTarget",
            "ProvideRemoteImageLoader",
            "CoilRemoteImageLoader",
        )
        val violations = mutableListOf<String>()
        repositoryRoot.resolve(".").toFile().walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension == "kt" &&
                    "/src/main/" in file.invariantSeparatorsPath
            }
            .forEach { file ->
                val content = file.readText()
                forbiddenNames.forEach { name ->
                    if (name in content) {
                        violations += "${file.relativeTo(repositoryRoot.toFile())} contains $name"
                    }
                }
            }

        assertTrue(
            "Removed remote image protocol references found in production source:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private fun resolveRepositoryRoot(): Path {
        val cwd = Paths.get(requireNotNull(System.getProperty("user.dir")))
            .toAbsolutePath()
            .normalize()
        return when {
            Files.isDirectory(cwd.resolve("viewcompose-ui-contract")) -> cwd
            cwd.fileName.toString() == "viewcompose-ui-contract" -> cwd.parent
            else -> error("Cannot locate repository root from $cwd")
        }
    }
}
