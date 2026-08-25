package com.viewcompose.quality

import java.io.File
import java.util.ArrayDeque
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Verifies documentation placement, reachability, relative links, and module-catalog coverage. */
abstract class VerifyDocumentationStructureTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rootMarkdownFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val activeDocumentationFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val checkedMarkdownFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val governanceFiles: ConfigurableFileCollection

    @get:Input
    abstract val documentationTopLevelDirectories: ListProperty<String>

    @TaskAction
    fun verifyStructure() {
        DocumentationQualityVerifiers.verifyDocumentationStructure(
            repository = repositoryDirectory.get().asFile,
            rootMarkdownFiles = rootMarkdownFiles.files,
            activeDocumentationFiles = activeDocumentationFiles.files,
            checkedMarkdownFiles = checkedMarkdownFiles.files,
            governanceFiles = governanceFiles.files,
            documentationTopLevelDirectories = documentationTopLevelDirectories.get(),
        ).failOnDocumentationQualityViolation()
    }
}

/** Verifies the compact DSL surface, neutral interaction contract, and Q3 KDoc shape. */
abstract class VerifyDslApiContractsTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val foundationDslFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val forbiddenContractFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val animationFiles: ConfigurableFileCollection

    @TaskAction
    fun verifyContracts() {
        DocumentationQualityVerifiers.verifyDslApiContracts(
            repository = repositoryDirectory.get().asFile,
            foundationDslFiles = foundationDslFiles.files,
            forbiddenContractFiles = forbiddenContractFiles.files,
            animationFiles = animationFiles.files,
        ).failOnDocumentationQualityViolation()
    }
}

internal object DocumentationQualityVerifiers {
    fun verifyDocumentationStructure(
        repository: File,
        rootMarkdownFiles: Set<File>,
        activeDocumentationFiles: Set<File>,
        checkedMarkdownFiles: Set<File>,
        governanceFiles: Set<File>,
        documentationTopLevelDirectories: List<String>,
    ): QualityGateOutcome {
        val canonicalRepository = repository.canonicalFile
        val documentationRoot = canonicalRepository.resolve("docs")
        val documentationIndex = documentationRoot.resolve("README.md")
        val violations = mutableListOf<String>()
        val selectedPaths =
            (rootMarkdownFiles + activeDocumentationFiles + checkedMarkdownFiles + governanceFiles)
                .map { file -> file.repositoryRelativeDocumentationPath(canonicalRepository) }
                .toSortedSet()

        val rootMarkdown = rootMarkdownFiles
            .filter(File::isFile)
            .filter { file -> file.extension.equals("md", ignoreCase = true) }
            .map(File::getName)
            .toSet()
        (rootMarkdown - ALLOWED_ROOT_MARKDOWN).sorted().forEach { fileName ->
            violations += "$fileName -> Markdown is not allowed at the repository root"
        }

        documentationTopLevelDirectories
            .filterNot(ALLOWED_DOCUMENTATION_DIRECTORIES::contains)
            .sorted()
            .forEach { directory ->
                violations += "docs/$directory -> undocumented top-level documentation category"
            }

        val activeDocuments = activeDocumentationFiles
            .filter(File::isFile)
            .filter { file -> file.extension.equals("md", ignoreCase = true) }
            .filterNot { file ->
                file.canonicalFile.relativeTo(documentationRoot).invariantSeparatorsPath
                    .startsWith("archive/")
            }
        activeDocuments.forEach { file ->
            if (file.name != "README.md" && !ACTIVE_DOCUMENT_NAME.matches(file.name)) {
                violations +=
                    "${file.repositoryRelativeDocumentationPath(canonicalRepository)} -> " +
                        "active document names must use lowercase kebab-case"
            }
        }

        if (!documentationIndex.isFile) {
            violations += "docs/README.md -> canonical documentation index is missing"
        } else {
            val activeDocumentFiles = activeDocuments.map(File::getCanonicalFile).toSet()
            val reachableDocuments = mutableSetOf<File>()
            val pendingDocuments = ArrayDeque<File>()
            pendingDocuments.add(documentationIndex.canonicalFile)

            while (pendingDocuments.isNotEmpty()) {
                val currentDocument = pendingDocuments.removeFirst()
                if (!reachableDocuments.add(currentDocument)) continue

                documentationLinkTargets(currentDocument.readText()).forEach targetLoop@{ rawTarget ->
                    val target = rawTarget.trim().removePrefix("<").removeSuffix(">")
                    if (
                        target.isEmpty() ||
                        target.startsWith("#") ||
                        target.startsWith("mailto:") ||
                        target.contains("://") ||
                        target.startsWith("/") ||
                        WINDOWS_ABSOLUTE_PATH.containsMatchIn(target)
                    ) {
                        return@targetLoop
                    }
                    val path = target.substringBefore('#').substringBefore('?')
                    if (path.isEmpty()) return@targetLoop
                    val linkedDocument = currentDocument.parentFile.resolve(path).normalize()
                    if (linkedDocument.isFile) {
                        val canonicalDocument = linkedDocument.canonicalFile
                        if (
                            canonicalDocument in activeDocumentFiles &&
                            canonicalDocument !in reachableDocuments
                        ) {
                            pendingDocuments.add(canonicalDocument)
                        }
                    }
                }
            }

            activeDocuments
                .filterNot { file -> file.canonicalFile == documentationIndex.canonicalFile }
                .filterNot { file -> file.canonicalFile in reachableDocuments }
                .sortedBy(File::getPath)
                .forEach { file ->
                    violations +=
                        "${file.repositoryRelativeDocumentationPath(canonicalRepository)} -> " +
                            "active document is not reachable from docs/README.md"
                }
        }

        verifyModuleCatalog(
            repository = canonicalRepository,
            documentationRoot = documentationRoot,
            governanceFiles = governanceFiles,
            violations = violations,
        )

        checkedMarkdownFiles
            .filter(File::isFile)
            .filter { file -> file.extension.equals("md", ignoreCase = true) }
            .forEach { file ->
                documentationLinkTargets(file.readText()).forEach targetLoop@{ rawTarget ->
                    val target = rawTarget.trim().removePrefix("<").removeSuffix(">")
                    if (target.isEmpty() || target.startsWith("#") || target.startsWith("mailto:")) {
                        return@targetLoop
                    }
                    if (target.contains("://")) {
                        if (target.startsWith("file://")) {
                            violations +=
                                "${file.repositoryRelativeDocumentationPath(canonicalRepository)} -> " +
                                    "local file link is forbidden: $target"
                        }
                        return@targetLoop
                    }
                    if (target.startsWith("/") || WINDOWS_ABSOLUTE_PATH.containsMatchIn(target)) {
                        violations +=
                            "${file.repositoryRelativeDocumentationPath(canonicalRepository)} -> " +
                                "absolute link is forbidden: $target"
                        return@targetLoop
                    }
                    val path = target.substringBefore('#').substringBefore('?')
                    if (path.isNotEmpty() && !file.parentFile.resolve(path).normalize().exists()) {
                        violations +=
                            "${file.repositoryRelativeDocumentationPath(canonicalRepository)} -> " +
                                "broken relative link: $target"
                    }
                }
            }

        return documentationOutcome(
            violations = violations,
            selectedPaths = selectedPaths,
            header = "Documentation structure verification failed:",
        )
    }

    fun verifyDslApiContracts(
        repository: File,
        foundationDslFiles: Set<File>,
        forbiddenContractFiles: Set<File>,
        animationFiles: Set<File>,
    ): QualityGateOutcome {
        val canonicalRepository = repository.canonicalFile
        val selectedPaths = (foundationDslFiles + forbiddenContractFiles + animationFiles)
            .map { file -> file.repositoryRelativeDocumentationPath(canonicalRepository) }
            .toSortedSet()
        val violations = mutableListOf<String>()
        val forbiddenDeclaration = Regex(
            """fun\s+(?:<[^>]+>\s+)?(?:UiTreeBuilder\.)?(${FORBIDDEN_DSL_ALIASES.joinToString("|")})\s*\(""",
        )
        val builderDeclaration = Regex(
            """fun\s+(?:<[^>]+>\s+)?UiTreeBuilder\.([A-Za-z_][A-Za-z0-9_]*)\s*\(""",
        )

        forbiddenContractFiles.kotlinSourceFiles().forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if ("rippleColor" in line) {
                    violations +=
                        "${file.repositoryRelativeDocumentationPath(canonicalRepository)}:${index + 1} -> " +
                            "rippleColor is a renderer detail"
                }
                if (Regex("""\bval\s+ripple\s*:\s*Int\b""").containsMatchIn(line)) {
                    violations +=
                        "${file.repositoryRelativeDocumentationPath(canonicalRepository)}:${index + 1} -> " +
                            "ripple is not a public semantic theme token"
                }
                if (
                    Regex("""\bval\s+controlHighlight\s*:\s*UiStateColor\b""")
                        .containsMatchIn(line)
                ) {
                    violations +=
                        "${file.repositoryRelativeDocumentationPath(canonicalRepository)}:${index + 1} -> " +
                            "controlHighlight is an Android theme detail"
                }
            }
        }

        (foundationDslFiles + animationFiles).kotlinSourceFiles().forEach { file ->
            val source = file.readText()
            forbiddenDeclaration.findAll(source).forEach { match ->
                violations +=
                    "${file.repositoryRelativeDocumentationPath(canonicalRepository)} -> " +
                        "redundant DSL alias " +
                        match.groupValues[1]
            }
        }

        foundationDslFiles.kotlinSourceFiles().forEach { file ->
            val source = file.readText()
            builderDeclaration.findAll(source).forEach declaration@{ match ->
                val functionName = match.groupValues[1]
                val lineStart = source.lastIndexOf('\n', match.range.first).let { it + 1 }
                val declarationPrefix = source.substring(lineStart, match.range.first)
                if (Regex("""\b(?:private|internal)\s*$""").containsMatchIn(declarationPrefix)) {
                    return@declaration
                }
                val openParenthesis = source.indexOf('(', match.range.first)
                var cursor = openParenthesis + 1
                var depth = 1
                while (cursor < source.length && depth > 0) {
                    when (source[cursor]) {
                        '(' -> depth += 1
                        ')' -> depth -= 1
                    }
                    cursor += 1
                }
                if (depth != 0) {
                    violations +=
                        "${file.repositoryRelativeDocumentationPath(canonicalRepository)} -> " +
                            "cannot parse $functionName parameters"
                    return@declaration
                }
                val declarationStart = match.range.first
                val kdocStart = source.lastIndexOf("/**", declarationStart)
                val kdocEnd = if (kdocStart >= 0) source.indexOf("*/", kdocStart) else -1
                val hasAdjacentKdoc =
                    kdocStart >= 0 &&
                        kdocEnd >= 0 &&
                        source.substring(kdocEnd + 2, declarationStart).isBlank()
                if (!hasAdjacentKdoc) {
                    violations +=
                        "${file.repositoryRelativeDocumentationPath(canonicalRepository)} -> " +
                            "$functionName has no adjacent KDoc"
                    return@declaration
                }
                val kdoc = source.substring(kdocStart, kdocEnd + 2)
                if ("@receiver" !in kdoc) {
                    violations +=
                        "${file.repositoryRelativeDocumentationPath(canonicalRepository)} -> " +
                            "$functionName KDoc misses @receiver"
                }
                if ("@sample" !in kdoc) {
                    violations +=
                        "${file.repositoryRelativeDocumentationPath(canonicalRepository)} -> " +
                            "$functionName KDoc misses compiled @sample"
                }
                val parameterBlock = source.substring(openParenthesis + 1, cursor - 1)
                Regex(
                    """(?m)^\s*(?:crossinline\s+|noinline\s+|vararg\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*:""",
                ).findAll(parameterBlock).forEach { parameter ->
                    val parameterName = parameter.groupValues[1]
                    if ("@param $parameterName" !in kdoc) {
                        violations +=
                            "${file.repositoryRelativeDocumentationPath(canonicalRepository)} -> " +
                                "$functionName KDoc misses @param $parameterName"
                    }
                }
            }
        }

        return documentationOutcome(
            violations = violations,
            selectedPaths = selectedPaths,
            header = "DSL API contract verification failed:",
        )
    }

    private fun verifyModuleCatalog(
        repository: File,
        documentationRoot: File,
        governanceFiles: Set<File>,
        violations: MutableList<String>,
    ) {
        val governanceFilesByPath = governanceFiles.associateBy { file ->
            file.repositoryRelativeDocumentationPath(repository)
        }
        val publishingPropertiesFile =
            governanceFilesByPath["gradle/viewcompose-publishing.properties"]
                ?: repository.resolve("gradle/viewcompose-publishing.properties")
        val moduleCatalog = governanceFilesByPath["docs/modules/README.md"]
            ?: documentationRoot.resolve("modules/README.md")
        if (!publishingPropertiesFile.isFile) {
            violations += "gradle/viewcompose-publishing.properties -> publishing metadata is missing"
            return
        }
        if (!moduleCatalog.isFile) {
            violations += "docs/modules/README.md -> published module catalog is missing"
            return
        }

        val publishingProperties = Properties()
        publishingPropertiesFile.inputStream().use(publishingProperties::load)
        val publishedModules = publishingProperties.stringPropertyNames()
            .filter { property -> property.startsWith("module.") && property.endsWith(".version") }
            .map { property -> property.removePrefix("module.").removeSuffix(".version") }
            .toSet()
        val moduleRow = Regex(
            pattern = """^\|\s*`(viewcompose-[a-z0-9-]+)`\s*\|""",
            option = RegexOption.MULTILINE,
        )
        val moduleCatalogContent = moduleCatalog.readText()
        val catalogModules = moduleRow.findAll(moduleCatalogContent)
            .map { match -> match.groupValues[1] }
            .toList()
        catalogModules.groupingBy { module -> module }.eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sorted()
            .forEach { module ->
                violations += "docs/modules/README.md -> duplicate published module row: $module"
            }
        (publishedModules - catalogModules.toSet()).sorted().forEach { module ->
            violations += "docs/modules/README.md -> published module is missing from catalog: $module"
        }
        (catalogModules.toSet() - publishedModules).sorted().forEach { module ->
            violations += "docs/modules/README.md -> catalog artifact has no publishing version: $module"
        }
        publishedModules.sorted().forEach { module ->
            val expectedManual = documentationRoot.resolve("modules/$module/README.md")
            if (!expectedManual.isFile) {
                violations += "docs/modules/$module/README.md -> published module manual is missing"
            }
            val expectedLink = "[Available](./$module/README.md)"
            val row = moduleCatalogContent.lineSequence().firstOrNull { line ->
                line.trimStart().startsWith("| `$module` |")
            }
            if (row == null || expectedLink !in row) {
                violations +=
                    "docs/modules/README.md -> $module must link its available module manual"
            }
        }
    }

    private fun documentationLinkTargets(content: String): List<String> = buildList {
        MARKDOWN_LINK.findAll(content).forEach { match -> add(match.groupValues[1]) }
        HTML_LINK.findAll(content).forEach { match -> add(match.groupValues[1]) }
    }

    private fun Set<File>.kotlinSourceFiles(): List<File> =
        asSequence()
            .flatMap { input ->
                if (input.isDirectory) input.walkTopDown() else sequenceOf(input)
            }
            .filter { file -> file.isFile && file.extension == "kt" }
            .distinctBy { file -> file.canonicalFile }
            .sortedBy(File::getPath)
            .toList()

    private fun documentationOutcome(
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
                    violations.distinct().sorted().forEach { violation -> appendLine("- $violation") }
                },
            )
        },
        selectedPaths = selectedPaths.toList(),
    )

    private val ALLOWED_ROOT_MARKDOWN = setOf(
        "AGENTS.md",
        "CODE_OF_CONDUCT.md",
        "CONTRIBUTING.md",
        "README.md",
        "README.zh-CN.md",
        "THIRD_PARTY_NOTICES.md",
    )
    private val ALLOWED_DOCUMENTATION_DIRECTORIES = setOf(
        "architecture",
        "archive",
        "getting-started",
        "guides",
        "migration",
        "modules",
        "project",
        "tooling",
        "tutorials",
    )
    private val FORBIDDEN_DSL_ALIASES = setOf(
        "TextButton",
        "ElevatedCard",
        "OutlinedCard",
        "PasswordField",
        "EmailField",
        "NumberField",
        "TextArea",
    )
    private val ACTIVE_DOCUMENT_NAME = Regex("[a-z0-9]+(?:-[a-z0-9]+)*\\.md")
    private val MARKDOWN_LINK = Regex("""\]\(([^)]+)\)""")
    private val HTML_LINK = Regex("""href=["']([^"']+)["']""")
    private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:[/\\\\]")
}

private fun QualityGateOutcome.failOnDocumentationQualityViolation() {
    if (!succeeded) error(diagnostics.joinToString("\n"))
}

private fun File.repositoryRelativeDocumentationPath(repository: File): String =
    canonicalFile.relativeTo(repository.canonicalFile).invariantSeparatorsPath
