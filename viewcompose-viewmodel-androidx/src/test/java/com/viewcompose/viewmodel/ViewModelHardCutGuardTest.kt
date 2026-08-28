package com.viewcompose.viewmodel

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewModelHardCutGuardTest {
    @Test
    fun `removed handle APIs stay absent from runtime and production source`() {
        val repositoryRoot = resolveRepositoryRoot()
        val moduleSource = repositoryRoot.resolve(
            "viewcompose-viewmodel-androidx/src/main/java",
        )
        val violations = mutableListOf<String>()
        val forbiddenText = listOf(
            "SavedStateHandleHolderViewModel",
            "__viewcompose_saved_state_handle__",
        )

        kotlinFiles(moduleSource).forEach { file ->
            val source = file.toFile().readText()
            forbiddenText.forEach { text ->
                if (text in source) {
                    violations += "${repositoryRoot.relativize(file)} contains $text"
                }
            }
            if (Regex("""\bfun\s+savedStateHandle\s*\(""").containsMatchIn(source)) {
                violations += "${repositoryRoot.relativize(file)} declares savedStateHandle()"
            }
        }

        val removedRuntimeTypes = listOf(
            "com.viewcompose.viewmodel.SavedStateHandleHolderViewModel",
            "com.viewcompose.viewmodel.SavedStateHandleCompositionKt",
        )
        removedRuntimeTypes.forEach { className ->
            val failure = runCatching { Class.forName(className) }.exceptionOrNull()
            if (failure !is ClassNotFoundException) {
                violations += "$className remains loadable from the compiled runtime"
            }
        }

        assertTrue(
            "Removed SavedStateHandle APIs were resurrected:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `store resolution and navigation retain one authoritative allocator`() {
        val repositoryRoot = resolveRepositoryRoot()
        val resolver = repositoryRoot.resolve(
            "viewcompose-viewmodel-androidx/src/main/java/com/viewcompose/viewmodel/" +
                "ViewModelComposition.kt",
        )
        val resolverSource = resolver.toFile().readText()
        val navigationSource = repositoryRoot.resolve(
            "viewcompose-navigation-android/src/main/java",
        )
        val violations = mutableListOf<String>()

        if (Regex("""\bremember\s*\(""").containsMatchIn(resolverSource)) {
            violations += "ViewModelComposition.kt retains a composition-level instance cache"
        }
        if (Regex("""\bkey\s*\.\s*(?:isBlank|isNullOrBlank)\s*\(""")
                .containsMatchIn(resolverSource)
        ) {
            violations += "ViewModelComposition.kt treats a blank key as the default identity"
        }
        kotlinFiles(navigationSource).forEach { file ->
            val source = file.toFile().readText()
            if (Regex("""\bViewModelStore\s*\(""").containsMatchIn(source)) {
                violations += "${repositoryRoot.relativize(file)} allocates a private ViewModelStore"
            }
        }

        assertTrue(
            "Duplicate ViewModel ownership paths found:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun kotlinFiles(root: Path): Sequence<Path> {
        return root.toFile().walkTopDown()
            .asSequence()
            .filter { file -> file.isFile && file.extension == "kt" }
            .map { file -> file.toPath() }
    }

    private fun resolveRepositoryRoot(): Path {
        val cwd = Paths.get(requireNotNull(System.getProperty("user.dir")))
            .toAbsolutePath()
            .normalize()
        return when {
            Files.isDirectory(cwd.resolve("viewcompose-viewmodel-androidx")) -> cwd
            cwd.fileName.toString() == "viewcompose-viewmodel-androidx" -> cwd.parent
            else -> error("Cannot locate repository root from $cwd")
        }
    }
}
