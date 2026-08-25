package com.viewcompose.quality

import groovy.json.JsonSlurper
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Produces the non-blocking Governance V2 discovery report and validates its frozen fixtures. */
abstract class ReportDocumentationGovernanceV2Task : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contractFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val recordFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceSetDirectories: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val activeDocumentationFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localeMirrorFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val publishingFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun report() {
        val result = DocumentationGovernanceV2Reporter.generate(
            repository = repositoryDirectory.get().asFile,
            contractFiles = contractFiles.files,
            recordFiles = recordFiles.files,
            sourceSetDirectories = sourceSetDirectories.files,
            activeDocumentationFiles = activeDocumentationFiles.files,
            localeMirrorFiles = localeMirrorFiles.files,
            publishingFiles = publishingFiles.files,
        )
        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(result.report)
        }
        if (result.contractViolations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Documentation Governance V2 contract validation failed:")
                    result.contractViolations.sorted().forEach { violation ->
                        appendLine("- $violation")
                    }
                }.trimEnd(),
            )
        }
        logger.lifecycle(
            "Documentation Governance V2 report written to {} ({} report-only issues).",
            reportFile.get().asFile,
            result.issueCount,
        )
    }
}

internal data class DocumentationGovernanceV2ReportResult(
    val report: String,
    val contractViolations: List<String>,
    val issueCount: Int,
)

internal object DocumentationGovernanceV2Reporter {
    private const val contractRootPath =
        "docs/project/contracts/documentation-governance-v2"
    private const val recordRootPath =
        "docs/project/records/documentation-governance-v2"

    fun generate(
        repository: File,
        contractFiles: Set<File>,
        recordFiles: Set<File>,
        sourceSetDirectories: Set<File>,
        activeDocumentationFiles: Set<File>,
        localeMirrorFiles: Set<File>,
        publishingFiles: Set<File>,
    ): DocumentationGovernanceV2ReportResult {
        val canonicalRepository = repository.canonicalFile
        val manifestFile = contractFiles.singleOrNull { it.name == "contract-set.json" }
            ?: canonicalRepository.resolve("$contractRootPath/contract-set.json")
        val manifest = manifestFile.parseJsonObject()
        val contracts = manifest.listOfObjects("contracts")
        val schemas = contracts.associate { contract ->
            val id = contract.requiredString("id")
            val schemaFile = manifestFile.parentFile.resolve(contract.requiredString("schema"))
            id to schemaFile.parseJsonObject()
        }
        val contractViolations = mutableListOf<String>()
        val fixtureReports = contracts.map { contract ->
            validateFixtures(
                manifestDirectory = manifestFile.parentFile,
                contract = contract,
                schema = schemas.getValue(contract.requiredString("id")),
                contractViolations = contractViolations,
            )
        }.sortedBy { report -> report.getValue("contractId").toString() }

        val declarations = discoverDslAndModifierDeclarations(
            canonicalRepository,
            sourceSetDirectories,
        )
        val localeMirrors = discoverLocaleMirrors(canonicalRepository, localeMirrorFiles)
        val mirrorsBySource = localeMirrors
            .mapNotNull { mirror ->
                mirror["translationSource"]?.toString()?.let { source -> source to mirror }
            }
            .toMap()
        val documents = discoverDocuments(
            repository = canonicalRepository,
            files = activeDocumentationFiles,
            mirrorsBySource = mirrorsBySource,
        )
        val fences = discoverExecutableFences(canonicalRepository, activeDocumentationFiles)
        val records = discoverRecords(
            repository = canonicalRepository,
            files = recordFiles,
            schemas = schemas,
        )
        val publishing = discoverPublishingInputs(canonicalRepository, publishingFiles)
        val issues = buildList {
            documents.filterNot { document -> document["schemaVersion"] == 2 }.forEach { document ->
                add(
                    reportIssue(
                        category = "missing-metadata",
                        target = document.getValue("path").toString(),
                        detail = "canonical active page does not declare schema_version: 2",
                    ),
                )
            }
            fences.filterNot { fence -> fence.getValue("registered") as Boolean }.forEach { fence ->
                add(
                    reportIssue(
                        category = "unclassified-sample",
                        target = "${fence.getValue("path")}:${fence.getValue("line")}",
                        detail = "executable fence has no adjacent compiled-sample registration marker",
                    ),
                )
            }
            records.filterNot { record -> record.getValue("valid") as Boolean }.forEach { record ->
                add(
                    reportIssue(
                        category = "taxonomy-mismatch",
                        target = record.getValue("path").toString(),
                        detail = "record does not satisfy its Governance V2 schema",
                    ),
                )
            }
            publishing.getValue("artifacts").let { artifacts ->
                @Suppress("UNCHECKED_CAST")
                (artifacts as List<Map<String, Any?>>)
                    .filter { artifact -> artifact["versionState"] == "unresolved" }
                    .forEach { artifact ->
                        add(
                            reportIssue(
                                category = "version-conflict",
                                target = artifact.getValue("artifact").toString(),
                                detail = "publishing version has no immutable release record",
                            ),
                        )
                    }
            }
        }.sortedWith(compareBy({ it.getValue("category").toString() }, { it.getValue("target").toString() }))

        val report = linkedMapOf<String, Any?>(
            "schemaVersion" to 1,
            "mode" to "report-only",
            "contractRoot" to contractRootPath,
            "recordRoot" to recordRootPath,
            "fixtureValidation" to fixtureReports,
            "discovery" to linkedMapOf(
                "dslAndModifierDeclarations" to declarations,
                "documents" to documents,
                "executableFences" to fences,
                "localeMirrors" to localeMirrors,
                "records" to records,
                "publishing" to publishing,
            ),
            "summary" to linkedMapOf(
                "contractCount" to contracts.size,
                "contractViolationCount" to contractViolations.size,
                "declarationCount" to declarations.size,
                "documentCount" to documents.size,
                "executableFenceCount" to fences.size,
                "localeMirrorCount" to localeMirrors.size,
                "recordCount" to records.size,
                "reportOnlyIssueCount" to issues.size,
            ),
            "reportOnlyIssues" to issues,
        )
        return DocumentationGovernanceV2ReportResult(
            report = StableJson.encode(report) + "\n",
            contractViolations = contractViolations.sorted(),
            issueCount = issues.size,
        )
    }

    private fun validateFixtures(
        manifestDirectory: File,
        contract: Map<String, Any?>,
        schema: Map<String, Any?>,
        contractViolations: MutableList<String>,
    ): Map<String, Any?> {
        val contractId = contract.requiredString("id")
        val accepted = contract.stringList("accepted").map { relativePath ->
            val fixture = manifestDirectory.resolve(relativePath)
            val violations = FrozenJsonSchemaValidator.validate(fixture.parseJsonValue(), schema)
            if (violations.isNotEmpty()) {
                contractViolations +=
                    "$contractId accepted fixture $relativePath was rejected: ${violations.joinToString("; ")}"
            }
            linkedMapOf<String, Any?>(
                "path" to "$contractRootPath/$relativePath",
                "valid" to violations.isEmpty(),
                "violations" to violations,
            )
        }
        val rejected = contract.listOfObjects("rejected").map { rejectedFixture ->
            val relativePath = rejectedFixture.requiredString("fixture")
            val fixture = manifestDirectory.resolve(relativePath)
            val violations = FrozenJsonSchemaValidator.validate(fixture.parseJsonValue(), schema)
            if (violations.isEmpty()) {
                contractViolations +=
                    "$contractId rejected fixture $relativePath was unexpectedly accepted"
            }
            linkedMapOf<String, Any?>(
                "path" to "$contractRootPath/$relativePath",
                "expectedRule" to rejectedFixture.requiredString("rule"),
                "valid" to violations.isEmpty(),
                "violations" to violations,
            )
        }
        return linkedMapOf(
            "contractId" to contractId,
            "accepted" to accepted,
            "rejected" to rejected,
        )
    }

    private fun discoverDslAndModifierDeclarations(
        repository: File,
        sourceSetDirectories: Set<File>,
    ): List<Map<String, Any?>> = sourceSetDirectories
        .asSequence()
        .filter(File::isDirectory)
        .flatMap { sourceRoot ->
            sourceRoot.resolve("main").takeIf(File::isDirectory)
                ?.walkTopDown()
                ?.filter(File::isFile)
                ?.filter { file -> file.extension == "kt" }
                ?: emptySequence()
        }
        .flatMap { file ->
            val source = file.readText()
            val packageName = packageDeclaration.find(source)?.groupValues?.get(1).orEmpty()
            functionDeclaration.findAll(source).mapNotNull declaration@{ match ->
                val modifiers = match.groupValues[1].trim().split(Regex("\\s+"))
                    .filter(String::isNotEmpty)
                    .toSet()
                if ("private" in modifiers || "internal" in modifiers) return@declaration null
                val receiver = match.groupValues[2].removeSuffix(".").trim()
                val receiverName = receiver.substringAfterLast('.').substringBefore('<').removeSuffix("?")
                val kind = when {
                    receiverName == "Modifier" -> "modifier"
                    receiverName == "UiTreeBuilder" || receiverName.endsWith("Scope") -> "dsl"
                    else -> return@declaration null
                }
                val name = match.groupValues[3]
                val path = file.relativePathWithin(repository)
                linkedMapOf<String, Any?>(
                    "artifact" to path.substringBefore('/'),
                    "kind" to kind,
                    "line" to source.lineNumberAt(match.range.first),
                    "path" to path,
                    "receiver" to receiver,
                    "symbol" to listOf(packageName, receiver, name)
                        .filter(String::isNotBlank)
                        .joinToString("."),
                    "visibility" to if ("protected" in modifiers) "protected" else "public",
                )
            }
        }
        .sortedWith(
            compareBy(
                { it.getValue("path").toString() },
                { it.getValue("line") as Int },
                { it.getValue("symbol").toString() },
            ),
        )
        .toList()

    private fun discoverDocuments(
        repository: File,
        files: Set<File>,
        mirrorsBySource: Map<String, Map<String, Any?>>,
    ): List<Map<String, Any?>> = files
        .filter(File::isFile)
        .filter { file -> file.extension in setOf("md", "mdx") }
        .map { file ->
            val metadata = parseFrontMatter(file.readText())
            val path = file.relativePathWithin(repository)
            val sourceKey = path.removePrefix("docs/")
            linkedMapOf<String, Any?>(
                "path" to path,
                "schemaVersion" to metadata["schema_version"].asIntegerOrNull(),
                "documentId" to metadata["document_id"],
                "docType" to metadata["doc_type"],
                "versionLane" to metadata["version_lane"],
                "metadataKeys" to metadata.keys.sorted(),
                "localeMirror" to mirrorsBySource[sourceKey]?.get("path"),
            )
        }
        .sortedBy { document -> document.getValue("path").toString() }

    private fun discoverExecutableFences(
        repository: File,
        files: Set<File>,
    ): List<Map<String, Any?>> = files
        .filter(File::isFile)
        .filter { file -> file.extension in setOf("md", "mdx") }
        .flatMap { file ->
            val lines = file.readLines()
            lines.mapIndexedNotNull { index, line ->
                val match = executableFence.matchEntire(line.trim()) ?: return@mapIndexedNotNull null
                val registrationContext = lines.subList(maxOf(0, index - 5), index).joinToString("\n")
                linkedMapOf<String, Any?>(
                    "language" to match.groupValues[1].lowercase(),
                    "line" to index + 1,
                    "path" to file.relativePathWithin(repository),
                    "registered" to sampleRegistrationMarker.containsMatchIn(registrationContext),
                )
            }
        }
        .sortedWith(compareBy({ it.getValue("path").toString() }, { it.getValue("line") as Int }))

    private fun discoverLocaleMirrors(
        repository: File,
        files: Set<File>,
    ): List<Map<String, Any?>> = files
        .filter(File::isFile)
        .filter { file -> file.extension in setOf("md", "mdx") }
        .map { file ->
            val metadata = parseFrontMatter(file.readText())
            linkedMapOf<String, Any?>(
                "path" to file.relativePathWithin(repository),
                "translationSource" to metadata["translation_source"],
                "translationSourceHash" to metadata["translation_source_hash"],
                "translationStatus" to metadata["translation_status"],
            )
        }
        .sortedBy { mirror -> mirror.getValue("path").toString() }

    private fun discoverRecords(
        repository: File,
        files: Set<File>,
        schemas: Map<String, Map<String, Any?>>,
    ): List<Map<String, Any?>> = files
        .filter(File::isFile)
        .filter { file -> file.extension == "json" }
        .map { file ->
            val path = file.relativePathWithin(repository)
            val contractId = when {
                "/capabilities/" in "/$path" -> "capability"
                "/samples/" in "/$path" -> "sample"
                "/impacts/" in "/$path" -> "capability-impact"
                "/exceptions/" in "/$path" -> "exception"
                else -> null
            }
            val violations = contractId?.let { id ->
                schemas[id]?.let { schema ->
                    FrozenJsonSchemaValidator.validate(file.parseJsonValue(), schema)
                }
            } ?: listOf("$ -> record is not stored in a recognized contract directory")
            linkedMapOf<String, Any?>(
                "path" to path,
                "contractId" to contractId,
                "valid" to violations.isEmpty(),
                "violations" to violations,
            )
        }
        .sortedBy { record -> record.getValue("path").toString() }

    private fun discoverPublishingInputs(
        repository: File,
        files: Set<File>,
    ): Map<String, Any?> {
        val publishingFile = files.singleOrNull { it.name == "viewcompose-publishing.properties" }
            ?: repository.resolve("gradle/viewcompose-publishing.properties")
        val releasesFile = files.singleOrNull {
            it.name == "viewcompose-documentation-releases.properties"
        } ?: repository.resolve("gradle/viewcompose-documentation-releases.properties")
        val publishing = publishingFile.loadProperties()
        val releases = releasesFile.loadProperties()
        val unpublished = publishing.csvProperty("release.unpublishedModules")
        val releasePairs = buildSet {
            val count = releases.getProperty("release.count")?.toIntOrNull() ?: 0
            repeat(count) { index ->
                val version = releases.getProperty("release.$index.version").orEmpty()
                releases.csvProperty("release.$index.modules").forEach { module ->
                    add(module to version)
                }
            }
        }
        val artifacts = publishing.stringPropertyNames()
            .filter { key -> key.startsWith("module.") && key.endsWith(".version") }
            .map { versionKey ->
                val artifact = versionKey.removePrefix("module.").removeSuffix(".version")
                val version = publishing.getProperty(versionKey)
                val state = when {
                    artifact in unpublished -> "next"
                    artifact to version in releasePairs -> "released"
                    else -> "unresolved"
                }
                linkedMapOf<String, Any?>(
                    "artifact" to artifact,
                    "sourceRevision" to publishing.getProperty("module.$artifact.sourceRevision"),
                    "version" to version,
                    "versionState" to state,
                )
            }
            .sortedBy { artifact -> artifact.getValue("artifact").toString() }
        return linkedMapOf(
            "artifacts" to artifacts,
            "documentationReleaseCount" to (releases.getProperty("release.count")?.toIntOrNull() ?: 0),
            "publishingFile" to publishingFile.relativePathWithin(repository),
            "releaseRegistryFile" to releasesFile.relativePathWithin(repository),
        )
    }

    private fun reportIssue(
        category: String,
        target: String,
        detail: String,
    ): Map<String, Any?> = linkedMapOf(
        "category" to category,
        "target" to target,
        "detail" to detail,
    )

    private val packageDeclaration = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)""")
    private val functionDeclaration = Regex(
        """(?m)^\s*((?:(?:public|protected|private|internal|inline|infix|operator|suspend|tailrec|external|actual|expect)\s+)*)fun\s+(?:<[^>\n]+>\s+)?([A-Za-z_][A-Za-z0-9_.<>?, ]*\.)\s*([A-Za-z_][A-Za-z0-9_]*)\s*\(""",
    )
    private val executableFence = Regex("""```(kotlin|java)(?:\s+.*)?""", RegexOption.IGNORE_CASE)
    private val sampleRegistrationMarker = Regex(
        """tutorial-sample|migration-pair|sample[_-]id|compiled-region""",
        RegexOption.IGNORE_CASE,
    )
}

internal object FrozenJsonSchemaValidator {
    fun validate(value: Any?, schema: Map<String, Any?>): List<String> =
        validateNode(value, schema, schema, "$").distinct().sorted()

    private fun validateNode(
        value: Any?,
        schema: Map<String, Any?>,
        rootSchema: Map<String, Any?>,
        path: String,
    ): List<String> {
        schema["\$ref"]?.toString()?.let { reference ->
            if (!reference.startsWith("#/\$defs/")) {
                return listOf("$path -> unsupported reference $reference")
            }
            val definitionName = reference.removePrefix("#/\$defs/")
            val definition = rootSchema.objectValue("\$defs")?.objectValue(definitionName)
                ?: return listOf("$path -> missing definition $definitionName")
            return validateNode(value, definition, rootSchema, path)
        }

        val violations = mutableListOf<String>()
        schema["type"]?.toString()?.let { type ->
            if (!matchesType(value, type)) {
                return listOf("$path -> expected $type")
            }
        }
        if (schema.containsKey("const") && !jsonEquals(value, schema["const"])) {
            violations += "$path -> expected constant ${schema["const"]}"
        }
        schema["enum"]?.let { enumValue ->
            val allowed = enumValue as? List<*> ?: emptyList<Any?>()
            if (allowed.none { candidate -> jsonEquals(value, candidate) }) {
                violations += "$path -> value is not in enum"
            }
        }
        schema["oneOf"]?.let { alternativesValue ->
            val alternatives = alternativesValue.asObjectList()
            val results = alternatives.map { alternative ->
                validateNode(value, alternative, rootSchema, path)
            }
            if (results.count(List<String>::isEmpty) != 1) {
                val representative = results.filter(List<String>::isNotEmpty)
                    .minByOrNull(List<String>::size)
                    .orEmpty()
                violations += "$path -> expected exactly one oneOf alternative"
                violations += representative
            }
        }
        schema["allOf"]?.asObjectList()?.forEach { rule ->
            violations += validateNode(value, rule, rootSchema, path)
        }
        schema.objectValue("if")?.let { condition ->
            if (validateNode(value, condition, rootSchema, path).isEmpty()) {
                schema.objectValue("then")?.let { consequence ->
                    violations += validateNode(value, consequence, rootSchema, path)
                }
            }
        }

        when (value) {
            is Map<*, *> -> {
                val objectValue = value.entries.associate { entry ->
                    entry.key.toString() to entry.value
                }
                schema["required"]?.let { requiredValue ->
                    (requiredValue as? List<*>).orEmpty().map(Any?::toString).forEach { required ->
                        if (required !in objectValue) violations += "$path.$required -> required"
                    }
                }
                val properties = schema.objectValue("properties").orEmpty()
                objectValue.forEach { (name, childValue) ->
                    properties.objectValue(name)?.let { childSchema ->
                        violations += validateNode(childValue, childSchema, rootSchema, "$path.$name")
                    }
                }
                if (schema["additionalProperties"] == false) {
                    (objectValue.keys - properties.keys).sorted().forEach { extra ->
                        violations += "$path.$extra -> additional property is forbidden"
                    }
                }
            }
            is List<*> -> {
                schema["minItems"].asIntegerOrNull()?.let { minimum ->
                    if (value.size < minimum) violations += "$path -> expected at least $minimum items"
                }
                schema["maxItems"].asIntegerOrNull()?.let { maximum ->
                    if (value.size > maximum) violations += "$path -> expected at most $maximum items"
                }
                if (schema["uniqueItems"] == true && value.distinctBy(StableJson::encode).size != value.size) {
                    violations += "$path -> items must be unique"
                }
                schema.objectValue("items")?.let { itemSchema ->
                    value.forEachIndexed { index, item ->
                        violations += validateNode(item, itemSchema, rootSchema, "$path[$index]")
                    }
                }
            }
            is String -> {
                schema["minLength"].asIntegerOrNull()?.let { minimum ->
                    if (value.length < minimum) violations += "$path -> expected length >= $minimum"
                }
                schema["pattern"]?.toString()?.let { pattern ->
                    if (!Regex(pattern).matches(value)) violations += "$path -> pattern mismatch"
                }
                if (schema["format"] == "date") {
                    try {
                        LocalDate.parse(value)
                    } catch (_: DateTimeParseException) {
                        violations += "$path -> expected ISO-8601 date"
                    }
                }
            }
        }
        schema["minimum"].asBigDecimalOrNull()?.let { minimum ->
            val actual = value.asBigDecimalOrNull()
            if (actual != null && actual < minimum) violations += "$path -> expected value >= $minimum"
        }
        return violations
    }

    private fun matchesType(value: Any?, type: String): Boolean = when (type) {
        "object" -> value is Map<*, *>
        "array" -> value is List<*>
        "string" -> value is String
        "boolean" -> value is Boolean
        "integer" -> value is Byte || value is Short || value is Int || value is Long ||
            value is java.math.BigInteger
        else -> false
    }

    private fun jsonEquals(first: Any?, second: Any?): Boolean {
        val firstNumber = first.asBigDecimalOrNull()
        val secondNumber = second.asBigDecimalOrNull()
        return if (firstNumber != null && secondNumber != null) {
            firstNumber.compareTo(secondNumber) == 0
        } else {
            first == second
        }
    }
}

internal object StableJson {
    fun encode(value: Any?): String = encode(value, 0)

    private fun encode(value: Any?, depth: Int): String {
        return when (value) {
            null -> "null"
            is String -> value.asJsonString()
            is Boolean, is Number -> value.toString()
            is Map<*, *> -> {
                val entries = value.entries.sortedBy { entry -> entry.key.toString() }
                if (entries.isEmpty()) {
                    "{}"
                } else {
                    entries.joinToString(
                        prefix = "{\n",
                        postfix = "\n${indent(depth)}}",
                        separator = ",\n",
                    ) { entry ->
                        "${indent(depth + 1)}${entry.key.toString().asJsonString()}: " +
                            encode(entry.value, depth + 1)
                    }
                }
            }
            is Iterable<*> -> {
                val values = value.toList()
                if (values.isEmpty()) {
                    "[]"
                } else {
                    values.joinToString(
                        prefix = "[\n",
                        postfix = "\n${indent(depth)}]",
                        separator = ",\n",
                    ) { item -> "${indent(depth + 1)}${encode(item, depth + 1)}" }
                }
            }
            else -> value.toString().asJsonString()
        }
    }

    private fun indent(depth: Int): String = "  ".repeat(depth)

    private fun String.asJsonString(): String = buildString {
        append('"')
        this@asJsonString.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u${character.code.toString(16).padStart(4, '0')}")
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
}

private fun File.parseJsonValue(): Any? = JsonSlurper().parse(this)

private fun File.parseJsonObject(): Map<String, Any?> =
    parseJsonValue().asObject() ?: error("$path must contain a JSON object")

private fun Any?.asObject(): Map<String, Any?>? = (this as? Map<*, *>)?.entries?.associate { entry ->
    entry.key.toString() to entry.value
}

private fun Any?.asObjectList(): List<Map<String, Any?>> =
    (this as? List<*>).orEmpty().mapNotNull(Any?::asObject)

private fun Map<String, Any?>.objectValue(name: String): Map<String, Any?>? = get(name).asObject()

private fun Map<String, Any?>.listOfObjects(name: String): List<Map<String, Any?>> =
    get(name).asObjectList()

private fun Map<String, Any?>.stringList(name: String): List<String> =
    (get(name) as? List<*>).orEmpty().map(Any?::toString)

private fun Map<String, Any?>.requiredString(name: String): String =
    get(name)?.toString() ?: error("Missing required string '$name'")

private fun Any?.asIntegerOrNull(): Int? = when (this) {
    is Number -> toInt()
    is String -> toIntOrNull()
    else -> null
}

private fun Any?.asBigDecimalOrNull(): BigDecimal? = when (this) {
    is Number -> toString().toBigDecimalOrNull()
    is String -> toBigDecimalOrNull()
    else -> null
}

private fun File.relativePathWithin(repository: File): String =
    canonicalFile.relativeTo(repository.canonicalFile).invariantSeparatorsPath

private fun String.lineNumberAt(index: Int): Int = substring(0, index).count { it == '\n' } + 1

private fun parseFrontMatter(source: String): Map<String, Any?> {
    val lines = source.lineSequence().toList()
    if (lines.firstOrNull()?.trim() != "---") return emptyMap()
    val closingIndex = lines.drop(1).indexOfFirst { line -> line.trim() == "---" }
        .takeIf { index -> index >= 0 }
        ?.plus(1)
        ?: return emptyMap()
    val result = linkedMapOf<String, Any?>()
    var index = 1
    while (index < closingIndex) {
        val line = lines[index]
        if (line.isBlank() || line.trimStart().startsWith("#") || line.firstOrNull()?.isWhitespace() == true) {
            index += 1
            continue
        }
        val separator = line.indexOf(':')
        if (separator <= 0) {
            index += 1
            continue
        }
        val key = line.substring(0, separator).trim()
        val rawValue = line.substring(separator + 1).trim()
        result[key] = when {
            rawValue.isEmpty() -> null
            rawValue == "[]" -> emptyList<String>()
            rawValue == "true" -> true
            rawValue == "false" -> false
            rawValue.toIntOrNull() != null -> rawValue.toInt()
            else -> rawValue.removeSurrounding("\"").removeSurrounding("'")
        }
        index += 1
    }
    return result
}

private fun File.loadProperties(): Properties = Properties().apply {
    inputStream().use(::load)
}

private fun Properties.csvProperty(name: String): Set<String> =
    getProperty(name).orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).toSet()
