package com.viewcompose.quality

import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element

/** Verifies concrete development tooling remains downstream of production runtime modules. */
abstract class VerifyDevelopmentToolingIsolationTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeSourceDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val toolingSourceDirectories: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appBuildFile: RegularFileProperty

    @get:Input
    abstract val toolingModules: SetProperty<String>

    @get:Input
    abstract val releaseRuntimeComponents: ListProperty<String>

    @TaskAction
    fun verifyIsolation() {
        DemoQualityVerifiers.verifyDevelopmentToolingIsolation(
            repository = repositoryDirectory.get().asFile,
            runtimeSourceDirectories = runtimeSourceDirectories.files,
            toolingSourceDirectories = toolingSourceDirectories.files,
            appBuildFile = appBuildFile.get().asFile,
            toolingModules = toolingModules.get(),
            releaseRuntimeComponents = releaseRuntimeComponents.get(),
        ).failOnDemoQualityViolation()
    }
}

/** Verifies the optimized Demo release archive contains no concrete development tooling. */
abstract class VerifyDemoReleaseToolingApkTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val releaseApk: RegularFileProperty

    @TaskAction
    fun verifyArchive() {
        DemoQualityVerifiers.verifyDemoReleaseToolingApk(
            repository = repositoryDirectory.get().asFile,
            releaseApk = releaseApk.get().asFile,
        ).failOnDemoQualityViolation()
    }
}

/** Prevents Demo automation from selecting app-owned UI through localized visible copy. */
abstract class VerifyDemoAutomationSelectorsTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectories: ConfigurableFileCollection

    @TaskAction
    fun verifySelectors() {
        DemoQualityVerifiers.verifyDemoAutomationSelectors(
            repository = repositoryDirectory.get().asFile,
            sourceDirectories = sourceDirectories.files,
        ).failOnDemoQualityViolation()
    }
}

/** Verifies Demo default-English and Simplified-Chinese resource parity and format contracts. */
abstract class VerifyDemoLocalizationResourcesTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val defaultResourcesDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val chineseResourcesDirectory: DirectoryProperty

    @TaskAction
    fun verifyResources() {
        DemoQualityVerifiers.verifyDemoLocalizationResources(
            repository = repositoryDirectory.get().asFile,
            defaultResourcesDirectory = defaultResourcesDirectory.get().asFile,
            chineseResourcesDirectory = chineseResourcesDirectory.get().asFile,
        ).failOnDemoQualityViolation()
    }
}

/** Prevents hard-coded visible copy in Demo source domains already migrated to resources. */
abstract class VerifyDemoLocalizedVisibleCopyTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val migratedSources: ConfigurableFileCollection

    @TaskAction
    fun verifyVisibleCopy() {
        DemoQualityVerifiers.verifyDemoLocalizedVisibleCopy(
            repository = repositoryDirectory.get().asFile,
            migratedSources = migratedSources.files,
        ).failOnDemoQualityViolation()
    }
}

internal object DemoQualityVerifiers {
    fun verifyDevelopmentToolingIsolation(
        repository: File,
        runtimeSourceDirectories: Set<File>,
        toolingSourceDirectories: Set<File>,
        appBuildFile: File,
        toolingModules: Set<String>,
        releaseRuntimeComponents: List<String>,
    ): QualityGateOutcome {
        val canonicalRepository = repository.canonicalFile
        val selectedPaths = mutableSetOf<String>()
        val violations = mutableListOf<String>()
        val toolingTextExtensions = setOf("", "kt", "java", "xml", "json", "properties", "txt")
        val concreteToolingMarkers = listOf(
            "DeviceDslSource",
            "device-dsl-source",
            "ViewCompose-DeviceDslSource",
            "AndroidAnimationTimeline",
            "animation-timeline-v1",
            "REQUEST_ANIMATION_TIMELINE",
        )
        val prohibitedToolingHotPathMarkers = listOf(
            "ViewTreeObserver.OnScrollChangedListener",
            "ViewTreeObserver.OnGlobalLayoutListener",
            "ViewTreeObserver.OnDrawListener",
            "ViewTreeObserver.OnPreDrawListener",
            "View.OnLayoutChangeListener",
            "View.OnTouchListener",
            "Choreographer.FrameCallback",
            "RecyclerView.OnScrollListener",
            "addOnScrollChangedListener",
            "addOnScrollListener",
            "addOnGlobalLayoutListener",
            "addOnDrawListener",
            "addOnPreDrawListener",
            "addOnLayoutChangeListener",
            "setOnTouchListener",
            "postFrameCallback",
        )

        scanTextFiles(runtimeSourceDirectories, toolingTextExtensions).forEach { file ->
            val path = file.repositoryRelativePath(canonicalRepository)
            selectedPaths += path
            val content = file.readText()
            concreteToolingMarkers.filter(content::contains).forEach { marker ->
                violations +=
                    "$path -> concrete tooling marker '$marker' is forbidden in runtime production source"
            }
        }
        scanTextFiles(toolingSourceDirectories, toolingTextExtensions).forEach { file ->
            val path = file.repositoryRelativePath(canonicalRepository)
            selectedPaths += path
            val content = file.readText()
            prohibitedToolingHotPathMarkers.filter(content::contains).forEach { marker ->
                violations +=
                    "$path -> tooling hot-path listener '$marker' requires an ADR-backed allowlist and benchmark"
            }
        }

        selectedPaths += appBuildFile.repositoryRelativePath(canonicalRepository)
        val appBuild = appBuildFile.readText()
        val previewProjectPattern = Regex(
            """(?m)^\s*(implementation|api|releaseImplementation)\s*\(\s*project\(\s*\":viewcompose-preview\"""",
        )
        previewProjectPattern.findAll(appBuild).forEach { match ->
            violations +=
                "app/build.gradle.kts -> viewcompose-preview must be debug/test scoped, found " +
                    match.groupValues[1]
        }

        toolingModules
            .filterNot { module -> module == "viewcompose-benchmark" }
            .sorted()
            .forEach { toolingModule ->
                releaseRuntimeComponents
                    .filter { component ->
                        component == "project :$toolingModule" ||
                            component.contains(":$toolingModule:")
                    }
                    .forEach { component ->
                        violations +=
                            "app releaseRuntimeClasspath -> forbidden tooling component '$component'"
                    }
            }

        return outcome(
            violations = violations.distinct().sorted(),
            selectedPaths = selectedPaths,
            header = "Development-tooling isolation verification failed:",
        )
    }

    fun verifyDemoReleaseToolingApk(
        repository: File,
        releaseApk: File,
    ): QualityGateOutcome {
        check(releaseApk.isFile) {
            "Demo release APK was not produced at ${releaseApk.relativeTo(repository)}"
        }
        val forbiddenMarkers = listOf(
            "com.viewcompose.preview.action.REQUEST_DEVICE_DSL_SOURCE",
            "viewcompose/device-dsl-source-v7.json",
            "com/viewcompose/preview/device/AndroidDeviceDslInspectionTooling",
            "DeviceDslSourceRequestReceiver",
            "META-INF/services/com.viewcompose.ui.foundation.RenderSessionInspectionTooling",
        )
        val violations = mutableListOf<String>()
        ZipFile(releaseApk).use { archive ->
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val content = archive.getInputStream(entry).use { input ->
                    input.readBytes().toString(Charsets.ISO_8859_1)
                }
                forbiddenMarkers.forEach { marker ->
                    if (entry.name.contains(marker) || content.contains(marker)) {
                        violations += "${entry.name} -> forbidden tooling marker '$marker'"
                    }
                }
            }
        }
        return outcome(
            violations = violations.distinct().sorted(),
            selectedPaths = setOf(releaseApk.repositoryRelativePath(repository.canonicalFile)),
            header = "Release APK tooling-isolation verification failed:",
        )
    }

    fun verifyDemoAutomationSelectors(
        repository: File,
        sourceDirectories: Set<File>,
    ): QualityGateOutcome {
        val canonicalRepository = repository.canonicalFile
        val selectorPattern = Regex(
            """\b(?:By\.text|waitForText|waitForTextGone|scrollUntilText|clickVisibleText|""" +
                """tapVisibleText|tapText|scrollTabStripUntilText|assertDeviceTextVisible|""" +
                """clickDeviceText|waitForDeviceText|findObjectByText)\s*\(""",
        )
        val files = scanTextFiles(sourceDirectories, setOf("kt"))
        val actualCounts = files.associate { file ->
            file.repositoryRelativePath(canonicalRepository) to
                selectorPattern.findAll(file.readText()).count()
        }.filterValues { count -> count > 0 }
        val diagnostics = if (actualCounts.isEmpty()) {
            emptyList()
        } else {
            listOf(
                buildString {
                    appendLine("Demo automation selector verification failed:")
                    actualCounts.toSortedMap().forEach { (path, count) ->
                        appendLine("- $path -> found $count visible-text selector usages")
                    }
                    appendLine("Use scenario-owned Android resource IDs.")
                },
            )
        }
        return QualityGateOutcome(
            succeeded = diagnostics.isEmpty(),
            diagnostics = diagnostics,
            selectedPaths = files.map { it.repositoryRelativePath(canonicalRepository) }.sorted(),
        )
    }

    fun verifyDemoLocalizationResources(
        repository: File,
        defaultResourcesDirectory: File,
        chineseResourcesDirectory: File,
    ): QualityGateOutcome {
        val canonicalRepository = repository.canonicalFile
        val selectedPaths = mutableSetOf<String>()
        val defaultResources = readResources(
            repository = canonicalRepository,
            directory = defaultResourcesDirectory,
            selectedPaths = selectedPaths,
        )
        val chineseResources = readResources(
            repository = canonicalRepository,
            directory = chineseResourcesDirectory,
            selectedPaths = selectedPaths,
        )
        val violations = mutableListOf<String>()
        (defaultResources.keys - chineseResources.keys).sorted().forEach { key ->
            violations += "$key is missing from values-zh-rCN"
        }
        (chineseResources.keys - defaultResources.keys).sorted().forEach { key ->
            violations += "$key has no canonical default-English resource"
        }
        (defaultResources.keys intersect chineseResources.keys).sorted().forEach { key ->
            val canonical = defaultResources.getValue(key)
            val localized = chineseResources.getValue(key)
            if (canonical.keys != localized.keys) {
                violations +=
                    "$key selectors differ: default=${canonical.keys.sorted()}, " +
                        "zh-rCN=${localized.keys.sorted()}"
                return@forEach
            }
            canonical.keys.sorted().forEach { selector ->
                val canonicalFormat = formatSignature(canonical.getValue(selector))
                val localizedFormat = formatSignature(localized.getValue(selector))
                if (canonicalFormat != localizedFormat) {
                    violations +=
                        "$key[$selector] format differs: default=$canonicalFormat, " +
                            "zh-rCN=$localizedFormat"
                }
            }
        }
        return outcome(
            violations = violations,
            selectedPaths = selectedPaths,
            header = "Demo localization resource verification failed:",
        )
    }

    fun verifyDemoLocalizedVisibleCopy(
        repository: File,
        migratedSources: Set<File>,
    ): QualityGateOutcome {
        val canonicalRepository = repository.canonicalFile
        val visibleLiteral = Regex(
            """(?:\b(?:text|title|subtitle|label|supportingText|placeholder|""" +
                """contentDescription|what|goal)\s*=\s*|""" +
                """\b(?:Text|Button|Chip|SearchBar)\s*\(\s*)\"""",
        )
        val files = scanTextFiles(migratedSources, setOf("kt"))
        val violations = files.flatMap { file ->
            val source = file.readText()
            visibleLiteral.findAll(source).map { match ->
                val lineNumber = source.take(match.range.first).count { character ->
                    character == '\n'
                } + 1
                val line = source.lineSequence().drop(lineNumber - 1).first().trim()
                "${file.repositoryRelativePath(canonicalRepository)}:$lineNumber -> $line"
            }.toList()
        }
        val diagnostics = if (violations.isEmpty()) {
            emptyList()
        } else {
            listOf(
                buildString {
                    appendLine("Demo localized visible-copy verification failed:")
                    violations.sorted().forEach { violation -> appendLine("- $violation") }
                    appendLine("Resolve visible copy through Android resources in migrated domains.")
                },
            )
        }
        return QualityGateOutcome(
            succeeded = diagnostics.isEmpty(),
            diagnostics = diagnostics,
            selectedPaths = files.map { it.repositoryRelativePath(canonicalRepository) }.sorted(),
        )
    }

    private fun readResources(
        repository: File,
        directory: File,
        selectedPaths: MutableSet<String>,
    ): Map<String, Map<String, String>> {
        val parserFactory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
        }
        return directory.listFiles()
            .orEmpty()
            .filter { file -> file.isFile && file.extension == "xml" }
            .sortedBy { file -> file.name }
            .flatMap { file ->
                selectedPaths += file.repositoryRelativePath(repository)
                val document = parserFactory.newDocumentBuilder().parse(file)
                val root = document.documentElement
                (0 until root.childNodes.length).mapNotNull { index ->
                    val element = root.childNodes.item(index) as? Element ?: return@mapNotNull null
                    val kind = element.tagName
                    if (kind !in setOf("string", "plurals", "string-array")) {
                        return@mapNotNull null
                    }
                    val name = element.getAttribute("name")
                    require(name.isNotBlank()) {
                        "Missing resource name in ${file.repositoryRelativePath(repository)}"
                    }
                    val values = when (kind) {
                        "string" -> mapOf("value" to element.textContent.trim())
                        else -> {
                            var ordinal = 0
                            (0 until element.childNodes.length).mapNotNull itemLoop@{ childIndex ->
                                val item = element.childNodes.item(childIndex) as? Element
                                    ?: return@itemLoop null
                                if (item.tagName != "item") return@itemLoop null
                                val selector = if (kind == "plurals") {
                                    item.getAttribute("quantity")
                                } else {
                                    (ordinal++).toString()
                                }
                                selector to item.textContent.trim()
                            }.toMap()
                        }
                    }
                    "$kind:$name" to values
                }
            }
            .toMap()
    }

    private fun formatSignature(value: String): List<String> {
        var implicitIndex = 1
        return FORMAT_PATTERN.findAll(value).mapNotNull { match ->
            val conversion = match.groupValues[2]
            if (conversion == "%") return@mapNotNull null
            val explicitIndex = match.groupValues[1]
            val argumentIndex = explicitIndex.ifBlank { (implicitIndex++).toString() }
            "$argumentIndex:${conversion.lowercase()}"
        }.toList()
    }

    private fun scanTextFiles(
        sources: Set<File>,
        extensions: Set<String>,
    ): List<File> = sources.asSequence()
        .flatMap { source ->
            when {
                source.isDirectory -> source.walkTopDown().asSequence()
                source.isFile -> sequenceOf(source)
                else -> emptySequence()
            }
        }
        .filter { file -> file.isFile && file.extension in extensions }
        .distinctBy { file -> file.canonicalPath }
        .sortedBy { file -> file.canonicalPath }
        .toList()

    private fun outcome(
        violations: List<String>,
        selectedPaths: Set<String>,
        header: String,
    ): QualityGateOutcome = QualityGateOutcome(
        succeeded = violations.isEmpty(),
        diagnostics = if (violations.isEmpty()) {
            emptyList()
        } else {
            listOf(
                buildString {
                    appendLine(header)
                    violations.forEach { violation -> appendLine("- $violation") }
                },
            )
        },
        selectedPaths = selectedPaths.sorted(),
    )

    private val FORMAT_PATTERN = Regex(
        """%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([a-zA-Z%])""",
    )
}

private fun QualityGateOutcome.failOnDemoQualityViolation() {
    if (!succeeded) error(diagnostics.joinToString("\n"))
}

private fun File.repositoryRelativePath(repository: File): String =
    canonicalFile.relativeTo(repository.canonicalFile).invariantSeparatorsPath

internal val demoLocalizedSourcePaths = listOf(
    "app/src/main/java/com/viewcompose/demo/automation",
    "app/src/main/java/com/viewcompose/demo/contract",
    "app/src/main/java/com/viewcompose/demo/core",
    "app/src/main/java/com/viewcompose/demo/registry",
    "app/src/main/java/com/viewcompose/demo/pages/state/DemoStatePage.kt",
    "app/src/main/java/com/viewcompose/demo/pages/diagnostics/DemoDiagnosticsPage.kt",
    "app/src/main/java/com/viewcompose/demo/pages/diagnostics/DemoDiagnosticsThemeSections.kt",
    "app/src/main/java/com/viewcompose/demo/pages/collections/DemoCollectionsPage.kt",
    "app/src/main/java/com/viewcompose/demo/pages/layouts/DemoLayoutsPage.kt",
    "app/src/main/java/com/viewcompose/demo/pages/input/DemoInputPage.kt",
    "app/src/main/java/com/viewcompose/demo/pages/gestures/DemoGesturesPage.kt",
    "app/src/main/java/com/viewcompose/demo/pages/graphics/DemoGraphicsPage.kt",
    "app/src/main/java/com/viewcompose/demo/pages/graphics/DemoGraphicsShadowSections.kt",
    "app/src/main/java/com/viewcompose/demo/pages/animation/DemoAnimationPage.kt",
    "app/src/main/java/com/viewcompose/demo/pages/modifiers",
    "app/src/main/java/com/viewcompose/demo/pages/interop",
    "app/src/main/java/com/viewcompose/demo/pages/feedback",
    "app/src/main/java/com/viewcompose/demo/pages/navigation/DemoSystemNavigationPage.kt",
    "app/src/main/java/com/viewcompose/demo/pages/navigation/DemoSystemNavigationDestination.kt",
    "app/src/main/java/com/viewcompose/activity/demo/pages/interaction/SystemNavigationActivity.kt",
    "app/src/main/java/com/viewcompose/demo/pages/settings/DemoMaterial3DefaultThemePage.kt",
    "app/src/main/java/com/viewcompose/activity/demo/pages/quality/Material3DefaultThemeActivity.kt",
    "app/src/main/java/com/viewcompose/demo/pages/settings/DemoDesignSystemVerificationPage.kt",
    "app/src/main/java/com/viewcompose/activity/demo/pages/quality/DemoDesignSystemVerificationActivity.kt",
    "app/src/main/java/com/viewcompose/demo/pages/settings/DemoOneUi7VerificationPage.kt",
    "app/src/main/java/com/viewcompose/activity/demo/pages/quality/OneUi7VerificationActivity.kt",
    "app/src/main/java/com/viewcompose/demo/pages/actions",
    "app/src/main/java/com/viewcompose/activity/demo/pages/core/ActionsActivity.kt",
    "app/src/main/java/com/viewcompose/demo/pages/navigation/DemoNavigationPage.kt",
    "app/src/main/java/com/viewcompose/activity/demo/pages/interaction/NavigationActivity.kt",
    "app/src/main/java/com/viewcompose/demo/pages/components",
    "app/src/main/java/com/viewcompose/activity/demo/pages/advanced/ComponentShowcaseActivity.kt",
    "app/src/main/java/com/viewcompose/demo/pages/foundations",
    "app/src/main/java/com/viewcompose/activity/demo/pages/core/FoundationsActivity.kt",
    "app/src/main/java/com/viewcompose/activity/demo/pages/quality/ThemeSwitchActivity.kt",
    "app/src/main/java/com/viewcompose/performance",
    "app/src/main/java/com/viewcompose/demo/designsystem/DemoContrastDesignSystem.kt",
)
