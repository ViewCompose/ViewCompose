package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core theme 中的 Theme Token Usage Audit 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Theme Token Usage Audit behavior in widget-core theme and guards DSL, state, or theme contracts against regressions.
 */

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ThemeTokenUsageAuditTest {
    private val repoRoot: Path = findRepoRoot()
    private val themeTokensFile: Path = repoRoot.resolve(
        "viewcompose-widget-core/src/main/java/com/viewcompose/widget/core/context/ThemeTokens.kt",
    )
    private val componentStylesFile: Path = repoRoot.resolve(
        "viewcompose-widget-core/src/main/java/com/viewcompose/widget/core/context/ComponentStyles.kt",
    )
    private val sourceRoots: List<Path> = listOf(
        repoRoot.resolve("viewcompose-widget-core/src/main/java"),
        repoRoot.resolve("viewcompose-overlay-android/src/main/java"),
    )

    @Test
    fun `color tokens are consumed or explicitly whitelisted`() {
        val expected = parseDataClassFields(themeTokensFile, "UiColors").toSet()
        val used = scanThemeUsage(
            roots = sourceRoots,
            regex = Regex("""Theme\.colors\.([A-Za-z0-9_]+)"""),
        )
        val allowed = reservedColorTokens
        val missing = expected - used - allowed

        assertTrue(
            "Unconsumed color tokens: $missing. Used=$used Allowed=$allowed",
            missing.isEmpty(),
        )
    }

    @Test
    fun `typography tokens are consumed or explicitly whitelisted`() {
        val expected = parseDataClassFields(themeTokensFile, "UiTypography").toSet()
        val used = scanThemeUsage(
            roots = sourceRoots,
            regex = Regex("""Theme\.typography\.([A-Za-z0-9_]+)"""),
        )
        val missing = expected - used

        assertTrue(
            "Unconsumed typography tokens: $missing. Used=$used",
            missing.isEmpty(),
        )
    }

    @Test
    fun `state color tokens are consumed by component defaults`() {
        val expected = parseDataClassFields(themeTokensFile, "UiStateColors").toSet()
        val used = scanThemeUsage(
            roots = sourceRoots,
            regex = Regex("""Theme\.stateColors\.([A-Za-z0-9_]+)"""),
        )
        val missing = expected - used

        assertTrue(
            "Unconsumed state color tokens: $missing. Used=$used",
            missing.isEmpty(),
        )
    }

    @Test
    fun `shape tokens are consumed or explicitly whitelisted`() {
        val expected = parseDataClassFields(themeTokensFile, "UiShapes").toSet()
        val used = scanThemeUsage(
            roots = sourceRoots,
            regex = Regex("""Theme\.shapes\.([A-Za-z0-9_]+)"""),
        )
        val missing = expected - used

        assertTrue(
            "Unconsumed shape tokens: $missing. Used=$used",
            missing.isEmpty(),
        )
    }

    @Test
    fun `control sizing tokens are consumed by defaults or composites`() {
        val expected = expectedControlTokenPaths(componentStylesFile)
        val used = scanThemeUsage(
            roots = sourceRoots,
            regex = Regex("""Theme\.controls\.([A-Za-z0-9_\.]+)"""),
        )
        val missing = expected - used

        assertTrue(
            "Unconsumed control sizing tokens: $missing. Used=$used",
            missing.isEmpty(),
        )
    }

    @Test
    fun `overlay tokens are consumed`() {
        val expected = parseDataClassFields(themeTokensFile, "UiOverlays").toSet()
        val used = scanThemeUsage(
            roots = sourceRoots,
            regex = Regex("""Theme\.overlays\.([A-Za-z0-9_]+)"""),
        )
        val missing = expected - used

        assertTrue(
            "Unconsumed overlay tokens: $missing. Used=$used",
            missing.isEmpty(),
        )
    }

    private fun expectedControlTokenPaths(componentStylesFile: Path): Set<String> {
        val controlFamilies = parseFieldTypeMap(
            file = componentStylesFile,
            className = "UiControlSizing",
            typePattern = Regex("""Ui[A-Za-z0-9_]+Sizing"""),
        )
        return controlFamilies.flatMapTo(linkedSetOf()) { (family, typeName) ->
            parseDataClassFields(componentStylesFile, typeName).map { field ->
                "$family.$field"
            }
        }
    }

    private fun scanThemeUsage(
        roots: List<Path>,
        regex: Regex,
    ): Set<String> {
        val usages = linkedSetOf<String>()
        roots.filter(Files::exists).forEach { root ->
            val paths = Files.walk(root)
            try {
                paths
                    .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                    .forEach { file ->
                        regex.findAll(readText(file)).forEach { match ->
                            usages += match.groupValues[1]
                        }
                    }
            } finally {
                paths.close()
            }
        }
        return usages
    }

    private fun parseFieldTypeMap(
        file: Path,
        className: String,
        typePattern: Regex,
    ): Map<String, String> {
        val block = dataClassParameterBlock(file, className)
        val regex = Regex("""val\s+([A-Za-z0-9_]+)\s*:\s*(${typePattern.pattern})""")
        return regex.findAll(block).associate { match ->
            match.groupValues[1] to match.groupValues[2]
        }
    }

    private fun parseDataClassFields(
        file: Path,
        className: String,
    ): List<String> {
        val block = dataClassParameterBlock(file, className)
        val regex = Regex("""val\s+([A-Za-z0-9_]+)\s*:""")
        return regex.findAll(block).map { it.groupValues[1] }.toList()
    }

    private fun dataClassParameterBlock(
        file: Path,
        className: String,
    ): String {
        val text = readText(file)
        val declaration = "data class $className("
        val declarationIndex = text.indexOf(declaration)
        require(declarationIndex >= 0) { "Unable to find declaration for $className in $file" }
        val openParenIndex = text.indexOf('(', declarationIndex)
        var depth = 0
        var closeParenIndex = -1
        for (index in openParenIndex until text.length) {
            when (text[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) {
                        closeParenIndex = index
                        break
                    }
                }
            }
        }
        require(closeParenIndex > openParenIndex) { "Unable to parse constructor block for $className" }
        return text.substring(openParenIndex + 1, closeParenIndex)
    }

    private fun readText(file: Path): String = String(Files.readAllBytes(file))

    private fun findRepoRoot(start: Path = Paths.get("").toAbsolutePath()): Path {
        var current: Path? = start
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current
            }
            current = current.parent
        }
        error("Unable to locate repository root from $start")
    }

    companion object {
        private val reservedColorTokens = setOf(
            "onBackground",
            "surfaceDim",
            "surfaceBright",
            "surfaceContainerLowest",
            "surfaceContainerLow",
            "surfaceContainer",
            "surfaceContainerHigh",
            "surfaceContainerHighest",
            "tertiary",
            "onTertiary",
            "tertiaryContainer",
            "onTertiaryContainer",
            "inversePrimary",
            "scrim",
            "success",
            "warning",
            "info",
            "surfaceTint",
        )

    }
}
