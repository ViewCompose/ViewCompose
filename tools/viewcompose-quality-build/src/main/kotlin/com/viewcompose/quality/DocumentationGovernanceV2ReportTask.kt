package com.viewcompose.quality

import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Enforces the Governance V2 no-new-debt ratchet and writes its deterministic audit reports. */
abstract class VerifyDocumentationGovernanceV2Task : DefaultTask() {
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

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val documentationPolicyFiles: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val baseRevision: Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:OutputFile
    abstract val humanReportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val repository = repositoryDirectory.get().asFile
        val mutationAudit = DocumentationGovernanceV2GitRatchet.inspect(
            repository = repository,
            explicitBaseRevision = baseRevision.orNull,
        )
        val result = DocumentationGovernanceV2Reporter.generate(
            repository = repository,
            contractFiles = contractFiles.files,
            recordFiles = recordFiles.files,
            sourceSetDirectories = sourceSetDirectories.files,
            activeDocumentationFiles = activeDocumentationFiles.files,
            localeMirrorFiles = localeMirrorFiles.files,
            publishingFiles = publishingFiles.files,
            documentationPolicyFiles = documentationPolicyFiles.files,
            ratchetContext = DocumentationGovernanceV2RatchetContext(
                verificationBase = mutationAudit.verificationBase,
                mutationViolations = mutationAudit.violations,
            ),
        )
        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(result.report)
        }
        humanReportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(result.humanReport)
        }
        if (result.contractViolations.isNotEmpty() || result.ratchetViolations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    if (result.contractViolations.isNotEmpty()) {
                        appendLine("Documentation Governance V2 contract validation failed:")
                        result.contractViolations.sorted().forEach { violation ->
                            appendLine("- $violation")
                        }
                    }
                    if (result.ratchetViolations.isNotEmpty()) {
                        if (isNotEmpty()) appendLine()
                        appendLine("Documentation Governance V2 no-new-debt ratchet failed:")
                        result.ratchetViolations.sorted().forEach { violation ->
                            appendLine("- $violation")
                        }
                    }
                }.trimEnd(),
            )
        }
        logger.lifecycle(
            "Documentation Governance V2 verified against {}: {} issue(s), all within the exact baseline. Report: {}.",
            mutationAudit.verificationBase,
            result.issueCount,
            reportFile.get().asFile,
        )
    }
}

internal data class DocumentationGovernanceV2ReportResult(
    val report: String,
    val humanReport: String,
    val contractViolations: List<String>,
    val ratchetViolations: List<String>,
    val issueCount: Int,
)

internal data class DocumentationGovernanceV2RatchetContext(
    val verificationBase: String? = null,
    val mutationViolations: List<String> = emptyList(),
)

internal object DocumentationGovernanceV2Reporter {
    private const val contractRootPath =
        "docs/project/contracts/documentation-governance-v2"
    private const val recordRootPath =
        "docs/project/records/documentation-governance-v2"
    private val issueCategories = listOf(
        "duplicate-owner",
        "missing-metadata",
        "orphan-document",
        "orphan-symbol",
        "stale-generated-output",
        "taxonomy-mismatch",
        "unclassified-sample",
        "version-conflict",
    )

    fun generate(
        repository: File,
        contractFiles: Set<File>,
        recordFiles: Set<File>,
        sourceSetDirectories: Set<File>,
        activeDocumentationFiles: Set<File>,
        localeMirrorFiles: Set<File>,
        publishingFiles: Set<File>,
        documentationPolicyFiles: Set<File>,
        ratchetContext: DocumentationGovernanceV2RatchetContext =
            DocumentationGovernanceV2RatchetContext(),
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

        val translationPolicyFile = documentationPolicyFiles.singleOrNull {
            it.name == "translation-policy.json"
        } ?: canonicalRepository.resolve("website/i18n/translation-policy.json")
        val moduleCatalogFile = documentationPolicyFiles.singleOrNull {
            it.relativePathWithin(canonicalRepository) == "docs/modules/README.md"
        } ?: canonicalRepository.resolve("docs/modules/README.md")
        val publicPagePaths = translationPolicyFile.parseJsonObject().stringList("required").toSet()
        val moduleFamilies = discoverModuleFamilies(moduleCatalogFile)
        val publishing = discoverPublishingInputs(canonicalRepository, publishingFiles)
        @Suppress("UNCHECKED_CAST")
        val publishingArtifacts = publishing.getValue("artifacts") as List<Map<String, Any?>>
        val activeArtifacts = publishingArtifacts
            .map { artifact -> artifact.getValue("artifact").toString() }
            .toSet()
        val declarations = discoverCapabilityDeclarations(
            repository = canonicalRepository,
            sourceSetDirectories = sourceSetDirectories,
            activeArtifacts = activeArtifacts,
            moduleFamilies = moduleFamilies,
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
            publicPagePaths = publicPagePaths,
            mirrorsBySource = mirrorsBySource,
            documentSchema = schemas["document"],
        )
        val fences = discoverExecutableFences(
            repository = canonicalRepository,
            files = activeDocumentationFiles,
            publicPagePaths = publicPagePaths,
            documents = documents,
            mirrorsBySource = mirrorsBySource,
            sourceSetDirectories = sourceSetDirectories,
        )
        val records = discoverRecords(
            repository = canonicalRepository,
            files = recordFiles,
            schemas = schemas,
        )
        val issues = buildIssues(
            declarations = declarations,
            documents = documents,
            fences = fences,
            records = records,
            publishing = publishing,
        )
        val baseline = correlateDebtBaseline(issues, records)
        val ratchetViolations = (
            ratchetViolations(
                issues = issues,
                records = records,
                baseline = baseline,
            ) + ratchetContext.mutationViolations
        ).distinct().sorted()
        val reportIssues = issues.map { issue ->
            val matchingBaseline = baseline.matches[issue.baselineKey]
            issue.toReportMap(matchingBaseline?.exceptionId)
        }
        val issueCounts = issueCategories.associateWith { category ->
            issues.count { issue -> issue.category == category }
        }

        val report = linkedMapOf<String, Any?>(
            "schemaVersion" to 1,
            "mode" to "no-new-debt",
            "contractRoot" to contractRootPath,
            "recordRoot" to recordRootPath,
            "fixtureValidation" to fixtureReports,
            "discovery" to linkedMapOf(
                "capabilityDeclarations" to declarations,
                "documents" to documents,
                "executableFences" to fences,
                "localeMirrors" to localeMirrors.map { mirror -> mirror - "absoluteFile" },
                "moduleFamilies" to moduleFamilies.toSortedMap(),
                "publicPagePolicy" to linkedMapOf(
                    "path" to translationPolicyFile.relativePathWithin(canonicalRepository),
                    "requiredPageCount" to publicPagePaths.size,
                ),
                "records" to records.map(GovernanceRecord::report),
                "publishing" to publishing,
            ),
            "debtBaseline" to baseline.report(),
            "gate" to linkedMapOf(
                "status" to if (ratchetViolations.isEmpty() && contractViolations.isEmpty()) "passed" else "failed",
                "verificationBase" to ratchetContext.verificationBase,
                "contractViolationCount" to contractViolations.size,
                "ratchetViolationCount" to ratchetViolations.size,
                "contractViolations" to contractViolations.sorted(),
                "ratchetViolations" to ratchetViolations,
            ),
            "summary" to linkedMapOf(
                "blockingViolationCount" to (contractViolations.size + ratchetViolations.size),
                "contractCount" to contracts.size,
                "contractViolationCount" to contractViolations.size,
                "declarationCount" to declarations.size,
                "documentCount" to documents.size,
                "executableFenceCount" to fences.size,
                "localeMirrorCount" to localeMirrors.size,
                "recordCount" to records.size,
                "issueCount" to issues.size,
                "unbaselinedIssueCount" to baseline.unbaselinedIssueCount,
                "issueCounts" to issueCounts,
            ),
            "issues" to reportIssues,
        )
        return DocumentationGovernanceV2ReportResult(
            report = StableJson.encode(report) + "\n",
            humanReport = humanReport(
                issues = issues,
                baseline = baseline,
                declarationCount = declarations.size,
                documentCount = documents.size,
                fenceCount = fences.size,
                contractViolations = contractViolations.sorted(),
                ratchetViolations = ratchetViolations,
            ),
            contractViolations = contractViolations.sorted(),
            ratchetViolations = ratchetViolations,
            issueCount = issues.size,
        )
    }

    private fun buildIssues(
        declarations: List<Map<String, Any?>>,
        documents: List<Map<String, Any?>>,
        fences: List<Map<String, Any?>>,
        records: List<GovernanceRecord>,
        publishing: Map<String, Any?>,
    ): List<GovernanceIssue> {
        val validCapabilities = records.filter { record ->
            record.contractId == "capability" && record.valid
        }
        val capabilitySymbols = validCapabilities.flatMap { record ->
            record.value.listOfObjects("symbols").map { symbol ->
                symbol.requiredString("symbol_id") to record.recordId.orEmpty()
            }
        }
        val capabilityOwnersBySymbol = capabilitySymbols.groupBy(
            keySelector = Pair<String, String>::first,
            valueTransform = Pair<String, String>::second,
        )
        val capabilityIds = validCapabilities.mapNotNull(GovernanceRecord::recordId).toSet()

        return buildList {
            documents.filterNot { document -> document["schemaVersion"] == 2 }.forEach { document ->
                add(
                    GovernanceIssue.file(
                        category = "missing-metadata",
                        target = document.getValue("path").toString(),
                        detail = "canonical active page does not declare schema_version: 2",
                    ),
                )
            }
            documents.filter { document -> document["schemaVersion"] == 2 }.forEach { document ->
                @Suppress("UNCHECKED_CAST")
                val violations = document.getValue("violations") as List<String>
                if (violations.isNotEmpty()) {
                    add(
                        GovernanceIssue.file(
                            category = "taxonomy-mismatch",
                            target = document.getValue("path").toString(),
                            detail = violations.joinToString("; "),
                        ),
                    )
                }
                @Suppress("UNCHECKED_CAST")
                val declaredCapabilities = document["capabilityIds"] as? List<String> ?: emptyList()
                declaredCapabilities.filterNot(capabilityIds::contains).forEach { capabilityId ->
                    add(
                        GovernanceIssue.file(
                            category = "orphan-document",
                            target = document.getValue("path").toString(),
                            identitySuffix = capabilityId,
                            detail = "document references unknown capability $capabilityId",
                        ),
                    )
                }
            }
            fences.filterNot { fence -> fence.getValue("registered") as Boolean }.forEach { fence ->
                val path = fence.getValue("path").toString()
                add(
                    GovernanceIssue.file(
                        category = "unclassified-sample",
                        target = "${fence.getValue("path")}:${fence.getValue("line")}",
                        baselineTarget = path,
                        identitySuffix = fence.getValue("stableFenceIdentity").toString(),
                        detail = "executable fence has no adjacent compiled-sample registration marker",
                    ),
                )
            }
            fences.forEach { fence ->
                val path = fence.getValue("path").toString()
                @Suppress("UNCHECKED_CAST")
                val violations = fence["classificationViolations"] as? List<String> ?: emptyList()
                if (violations.isNotEmpty()) {
                    add(
                        GovernanceIssue.file(
                            category = "taxonomy-mismatch",
                            target = "${fence.getValue("path")}:${fence.getValue("line")}",
                            baselineTarget = path,
                            identitySuffix =
                                "sample-contract|${fence.getValue("stableFenceIdentity")}",
                            detail = violations.joinToString("; "),
                        ),
                    )
                }
                @Suppress("UNCHECKED_CAST")
                val mirror = fence["languageMirror"] as? Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val mirrorViolations =
                    mirror?.get("classificationViolations") as? List<String> ?: emptyList()
                if (mirrorViolations.isNotEmpty()) {
                    add(
                        GovernanceIssue.file(
                            category = "taxonomy-mismatch",
                            target = "${mirror?.get("path")}:${mirror?.get("line")}",
                            baselineTarget = path,
                            identitySuffix =
                                "mirror-sample-contract|${fence.getValue("stableFenceIdentity")}",
                            detail = mirrorViolations.joinToString("; "),
                        ),
                    )
                }
            }
            fences.forEach { fence ->
                val path = fence.getValue("path").toString()
                @Suppress("UNCHECKED_CAST")
                val mirror = fence["languageMirror"] as? Map<String, Any?>
                when {
                    mirror == null -> add(
                        GovernanceIssue.file(
                            category = "taxonomy-mismatch",
                            target = "${fence.getValue("path")}:${fence.getValue("line")}",
                            baselineTarget = path,
                            identitySuffix = "missing-mirror|${fence.getValue("stableFenceIdentity")}",
                            detail = "executable fence has no corresponding locale-mirror fence",
                        ),
                    )
                    mirror["contentMatches"] != true -> add(
                        GovernanceIssue.file(
                            category = "taxonomy-mismatch",
                            target = "${fence.getValue("path")}:${fence.getValue("line")}",
                            baselineTarget = path,
                            identitySuffix = "mirror-content|${fence.getValue("stableFenceIdentity")}",
                            detail = "canonical and locale-mirror executable fence content differs",
                        ),
                    )
                    mirror["sourceMarkerMatches"] != true -> add(
                        GovernanceIssue.file(
                            category = "taxonomy-mismatch",
                            target = "${fence.getValue("path")}:${fence.getValue("line")}",
                            baselineTarget = path,
                            identitySuffix = "mirror-marker|${fence.getValue("stableFenceIdentity")}",
                            detail = "canonical and locale-mirror source markers differ",
                        ),
                    )
                }
            }
            records.filterNot(GovernanceRecord::valid).forEach { record ->
                add(
                    GovernanceIssue.file(
                        category = "taxonomy-mismatch",
                        target = record.path,
                        detail = record.violations.joinToString("; "),
                    ),
                )
            }
            declarations.forEach { declaration ->
                val symbol = declaration.getValue("symbol").toString()
                if (symbol !in capabilityOwnersBySymbol) {
                    @Suppress("UNCHECKED_CAST")
                    val locations = declaration.getValue("locations") as List<Map<String, Any?>>
                    add(
                        GovernanceIssue.symbol(
                            category = "orphan-symbol",
                            target = symbol,
                            baselineFile = locations.first().getValue("path").toString(),
                            detail = "public production entry has no Governance V2 capability owner",
                        ),
                    )
                }
            }
            capabilityOwnersBySymbol.filterValues { owners -> owners.distinct().size > 1 }
                .forEach { (symbol, owners) ->
                    add(
                        GovernanceIssue.symbol(
                            category = "duplicate-owner",
                            target = symbol,
                            detail = "symbol is owned by ${owners.distinct().sorted().joinToString()}",
                        ),
                    )
                }
            publishing.getValue("artifacts").let { artifacts ->
                @Suppress("UNCHECKED_CAST")
                (artifacts as List<Map<String, Any?>>)
                    .filter { artifact -> artifact["versionState"] == "unresolved" }
                    .forEach { artifact ->
                        add(
                            GovernanceIssue.symbol(
                                category = "version-conflict",
                                target = artifact.getValue("artifact").toString(),
                                detail = artifact["resolutionProblem"]?.toString()
                                    ?: "publishing version has no immutable release record",
                            ),
                        )
                    }
            }
        }.distinctBy(GovernanceIssue::id)
            .sortedWith(compareBy(GovernanceIssue::category, GovernanceIssue::target, GovernanceIssue::id))
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

    private fun discoverCapabilityDeclarations(
        repository: File,
        sourceSetDirectories: Set<File>,
        activeArtifacts: Set<String>,
        moduleFamilies: Map<String, String>,
    ): List<Map<String, Any?>> = sourceSetDirectories
        .asSequence()
        .filter(File::isDirectory)
        .filter { sourceRoot -> sourceRoot.parentFile.name in activeArtifacts }
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
            if (packageName == "internal" || ".internal." in ".$packageName.") {
                return@flatMap emptySequence()
            }
            val artifact = file.relativePathWithin(repository).substringBefore('/')
            val family = moduleFamilies[artifact].orEmpty()
            val sanitized = sanitizeKotlin(source)
            val braceDepths = braceDepths(sanitized)
            val entries = mutableListOf<CapabilityDeclaration>()
            functionDeclaration.findAll(sanitized).forEach declaration@{ match ->
                if (braceDepths[match.range.first] != 0) return@declaration
                val modifiers = match.groupValues[1].trim().split(Regex("\\s+"))
                    .filter(String::isNotEmpty)
                    .toSet()
                if ("private" in modifiers || "internal" in modifiers) return@declaration
                val receiver = match.groupValues[2].removeSuffix(".").trim()
                val receiverName = receiver.substringAfterLast('.').substringBefore('<').removeSuffix("?")
                val name = match.groupValues[3]
                val path = file.relativePathWithin(repository)
                val kind = classifyCapability(
                    artifact = artifact,
                    family = family,
                    receiverName = receiverName,
                    name = name,
                    path = path,
                )
                if (kind == null) return@declaration
                val canonicalReceiver = receiver.withoutTypeArguments().removeSuffix("?")
                entries += CapabilityDeclaration(
                    artifact = artifact,
                    kind = kind,
                    line = source.lineNumberAt(match.range.first),
                    path = path,
                    receiver = receiver,
                    sourcePackage = packageName,
                    symbol = listOf(packageName, canonicalReceiver, name)
                        .filter(String::isNotBlank)
                        .joinToString("."),
                    visibility = if ("protected" in modifiers) "protected" else "public",
                )
            }
            if (family == "Integration" || family == "Preview tooling" || artifact == "viewcompose-host-android") {
                topLevelCallableDeclaration.findAll(sanitized).forEach declaration@{ match ->
                    if (braceDepths[match.range.first] != 0) return@declaration
                    val modifiers = match.groupValues[1].trim().split(Regex("\\s+"))
                        .filter(String::isNotEmpty)
                        .toSet()
                    if ("private" in modifiers || "internal" in modifiers) return@declaration
                    val name = match.groupValues[2]
                    if (functionDeclaration.find(match.value) != null) return@declaration
                    if (!isApplicationEntry(artifact, family, packageName, name)) return@declaration
                    val path = file.relativePathWithin(repository)
                    entries += CapabilityDeclaration(
                        artifact = artifact,
                        kind = when {
                            family == "Preview tooling" -> "tooling"
                            artifact == "viewcompose-host-android" -> "host"
                            else -> "integration"
                        },
                        line = source.lineNumberAt(match.range.first),
                        path = path,
                        receiver = null,
                        sourcePackage = packageName,
                        symbol = "$packageName.$name",
                        visibility = if ("protected" in modifiers) "protected" else "public",
                    )
                }
                topLevelTypeDeclaration.findAll(sanitized).forEach declaration@{ match ->
                    if (braceDepths[match.range.first] != 0) return@declaration
                    val modifiers = match.groupValues[1].trim().split(Regex("\\s+"))
                        .filter(String::isNotEmpty)
                        .toSet()
                    if ("private" in modifiers || "internal" in modifiers) return@declaration
                    val name = match.groupValues[3]
                    if (!isApplicationEntry(artifact, family, packageName, name)) return@declaration
                    val path = file.relativePathWithin(repository)
                    entries += CapabilityDeclaration(
                        artifact = artifact,
                        kind = when {
                            family == "Preview tooling" -> "tooling"
                            artifact == "viewcompose-host-android" -> "host"
                            else -> "integration"
                        },
                        line = source.lineNumberAt(match.range.first),
                        path = path,
                        receiver = null,
                        sourcePackage = packageName,
                        symbol = "$packageName.$name",
                        visibility = if ("protected" in modifiers) "protected" else "public",
                    )
                }
            }
            entries.asSequence()
        }
        .groupBy(CapabilityDeclaration::symbol)
        .values
        .map { overloads ->
            val first = overloads.sortedWith(compareBy(CapabilityDeclaration::path, CapabilityDeclaration::line)).first()
            linkedMapOf<String, Any?>(
                "artifact" to first.artifact,
                "kind" to first.kind,
                "locations" to overloads
                    .distinctBy { declaration -> declaration.path to declaration.line }
                    .sortedWith(compareBy(CapabilityDeclaration::path, CapabilityDeclaration::line))
                    .map { declaration ->
                        linkedMapOf<String, Any?>(
                            "line" to declaration.line,
                            "path" to declaration.path,
                        )
                    },
                "overloadCount" to overloads.size,
                "receiver" to first.receiver,
                "sourcePackage" to first.sourcePackage,
                "symbol" to first.symbol,
                "visibility" to first.visibility,
            )
        }
        .sortedWith(compareBy({ it.getValue("artifact").toString() }, { it.getValue("symbol").toString() }))
        .toList()

    private fun discoverDocuments(
        repository: File,
        files: Set<File>,
        publicPagePaths: Set<String>,
        mirrorsBySource: Map<String, Map<String, Any?>>,
        documentSchema: Map<String, Any?>?,
    ): List<Map<String, Any?>> = files
        .filter(File::isFile)
        .filter { file -> file.extension in setOf("md", "mdx") }
        .filter { file -> file.relativePathWithin(repository).removePrefix("docs/") in publicPagePaths }
        .map { file ->
            val metadata = parseFrontMatter(file.readText())
            val path = file.relativePathWithin(repository)
            val sourceKey = path.removePrefix("docs/")
            val schemaVersion = metadata["schema_version"].asIntegerOrNull()
            val violations = if (schemaVersion == 2 && documentSchema != null) {
                FrozenJsonSchemaValidator.validate(metadata, documentSchema) +
                    documentPlacementViolations(path, metadata["doc_type"]?.toString())
            } else {
                emptyList()
            }
            linkedMapOf<String, Any?>(
                "path" to path,
                "schemaVersion" to schemaVersion,
                "documentId" to metadata["document_id"],
                "docType" to metadata["doc_type"],
                "versionLane" to metadata["version_lane"],
                "capabilityIds" to metadata.stringList("capability_ids"),
                "artifactIds" to metadata.stringList("artifact_ids"),
                "sampleIds" to metadata.stringList("sample_ids"),
                "metadataKeys" to metadata.keys.sorted(),
                "localeMirror" to mirrorsBySource[sourceKey]?.get("path"),
                "violations" to violations.distinct().sorted(),
            )
        }
        .sortedBy { document -> document.getValue("path").toString() }

    private fun discoverExecutableFences(
        repository: File,
        files: Set<File>,
        publicPagePaths: Set<String>,
        documents: List<Map<String, Any?>>,
        mirrorsBySource: Map<String, Map<String, Any?>>,
        sourceSetDirectories: Set<File>,
    ): List<Map<String, Any?>> {
        val versionLaneByPath = documents.associate { document ->
            document.getValue("path").toString() to document["versionLane"]?.toString()
        }
        val canonicalFiles = files
            .filter(File::isFile)
            .filter { file -> file.extension in setOf("md", "mdx") }
            .filter { file -> file.relativePathWithin(repository).removePrefix("docs/") in publicPagePaths }
        return canonicalFiles.flatMap { file ->
            val path = file.relativePathWithin(repository)
            val sourceKey = path.removePrefix("docs/")
            val canonicalFences = parseExecutableFences(
                repository = repository,
                path = path,
                source = file.readText(),
                versionLane = versionLaneByPath[path],
                sourceSetDirectories = sourceSetDirectories,
            )
            val mirror = mirrorsBySource[sourceKey]
            val mirrorFences = mirror?.get("absoluteFile")
                ?.let { absoluteFile -> absoluteFile as? File }
                ?.let { mirrorFile ->
                    parseExecutableFences(
                        repository = repository,
                        path = mirrorFile.relativePathWithin(repository),
                        source = mirrorFile.readText(),
                        versionLane = versionLaneByPath[path],
                        sourceSetDirectories = sourceSetDirectories,
                    )
                }
                .orEmpty()
            val mirrorAssociations = associateMirrorFences(canonicalFences, mirrorFences)
            canonicalFences.mapIndexed { index, fence -> fence.report(mirrorAssociations[index]) }
        }
        .sortedWith(compareBy({ it.getValue("path").toString() }, { it.getValue("line") as Int }))
    }

    private fun associateMirrorFences(
        canonical: List<GovernanceFence>,
        mirrors: List<GovernanceFence>,
    ): List<GovernanceFence?> {
        val result = MutableList<GovernanceFence?>(canonical.size) { null }
        val usedMirrorIndexes = mutableSetOf<Int>()
        canonical.forEachIndexed { index, fence ->
            if (fence.sourceMarker == null) return@forEachIndexed
            val match = mirrors.indices.singleOrNull { mirrorIndex ->
                mirrorIndex !in usedMirrorIndexes &&
                    mirrors[mirrorIndex].sourceMarker == fence.sourceMarker &&
                    mirrors[mirrorIndex].language == fence.language
            }
            if (match != null) {
                result[index] = mirrors[match]
                usedMirrorIndexes += match
            }
        }
        canonical.forEachIndexed { index, fence ->
            if (result[index] != null) return@forEachIndexed
            val match = mirrors.indices.firstOrNull { mirrorIndex ->
                mirrorIndex !in usedMirrorIndexes &&
                    mirrors[mirrorIndex].contentHash == fence.contentHash &&
                    mirrors[mirrorIndex].language == fence.language
            }
            if (match != null) {
                result[index] = mirrors[match]
                usedMirrorIndexes += match
            }
        }
        val remainingCanonical = canonical.indices.filter { index -> result[index] == null }
        val remainingMirrors = mirrors.indices.filterNot(usedMirrorIndexes::contains)
        remainingCanonical.zip(remainingMirrors).forEach { (canonicalIndex, mirrorIndex) ->
            result[canonicalIndex] = mirrors[mirrorIndex]
        }
        return result
    }

    private fun discoverLocaleMirrors(
        repository: File,
        files: Set<File>,
    ): List<Map<String, Any?>> = files
        .filter(File::isFile)
        .filter { file -> file.extension in setOf("md", "mdx") }
        .map { file ->
            val metadata = parseFrontMatter(file.readText())
            linkedMapOf<String, Any?>(
                "absoluteFile" to file,
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
    ): List<GovernanceRecord> = files
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
            val value = file.parseJsonObject()
            GovernanceRecord(
                path = path,
                contractId = contractId,
                valid = violations.isEmpty(),
                violations = violations,
                value = value,
                recordId = when (contractId) {
                    "capability" -> value["capability_id"]?.toString()
                    "sample" -> value["sample_id"]?.toString()
                    "capability-impact" -> value["impact_id"]?.toString()
                    "exception" -> value["exception_id"]?.toString()
                    else -> null
                },
            )
        }
        .sortedBy(GovernanceRecord::path)

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
        val releaseRecords = buildList {
            val count = releases.getProperty("release.count")?.toIntOrNull() ?: 0
            repeat(count) { index ->
                val version = releases.getProperty("release.$index.version").orEmpty()
                val sourceRevision = releases.getProperty("release.$index.sourceRevision").orEmpty()
                releases.csvProperty("release.$index.modules").forEach { module ->
                    add(Triple(module, version, sourceRevision))
                }
            }
        }
        val artifacts = publishing.stringPropertyNames()
            .filter { key -> key.startsWith("module.") && key.endsWith(".version") }
            .map { versionKey ->
                val artifact = versionKey.removePrefix("module.").removeSuffix(".version")
                val version = publishing.getProperty(versionKey)
                val sourceRevision = publishing.getProperty("module.$artifact.sourceRevision")
                val release = releaseRecords.singleOrNull { record ->
                    record.first == artifact && record.second == version
                }
                val state = when {
                    artifact in unpublished -> "next"
                    release != null && release.third == sourceRevision -> "released"
                    else -> "unresolved"
                }
                val resolutionProblem = when {
                    state != "unresolved" -> null
                    release == null -> "publishing version has no immutable release record"
                    else -> "publishing source revision differs from the immutable release record"
                }
                linkedMapOf<String, Any?>(
                    "artifact" to artifact,
                    "releaseSourceRevision" to release?.third,
                    "sourceRevision" to sourceRevision,
                    "version" to version,
                    "versionState" to state,
                    "resolutionProblem" to resolutionProblem,
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

    private val packageDeclaration = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)""")
    private val functionDeclaration = Regex(
        """(?m)^\s*((?:(?:public|protected|private|internal|inline|infix|operator|suspend|tailrec|external|actual|expect)\s+)*)fun\s+(?:<[^>\n]+>\s+)?([A-Za-z_][A-Za-z0-9_.<>?, ]*\.)\s*([A-Za-z_][A-Za-z0-9_]*)\s*\(""",
    )
    private val executableFence = Regex("""```(kotlin|java)(?:\s+.*)?""", RegexOption.IGNORE_CASE)
    private val topLevelCallableDeclaration = Regex(
        """(?m)^\s*((?:(?:public|protected|private|internal|inline|infix|operator|suspend|tailrec|external|actual|expect)\s+)*)fun\s+(?:<[^>\n]+>\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*\(""",
    )
    private val topLevelTypeDeclaration = Regex(
        """(?m)^\s*((?:(?:public|protected|private|internal|data|sealed|enum|annotation|value|actual|expect)\s+)*)(class|interface|object|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)""",
    )
    private val moduleFamilyRow = Regex(
        """^\|\s*`(viewcompose-[a-z0-9-]+)`\s*\|\s*([^|]+?)\s*\|""",
    )
    private val marker = Regex(
        """\{/\*\s*(tutorial-sample(?:-end)?|paired-sample|compiled-region|non-executable|generated-signature)\b([^*]*)\*/}""",
        RegexOption.IGNORE_CASE,
    )
    private val mavenDependency = Regex(
        """com\.viewcompose:(viewcompose-[a-z0-9-]+):([A-Za-z0-9._+${'$'}{}-]+)""",
    )
    private val projectDependency = Regex(
        """project\(\s*[\"']:(viewcompose-[a-z0-9-]+)[\"']\s*\)""",
    )

    private fun discoverModuleFamilies(moduleCatalogFile: File): Map<String, String> =
        moduleCatalogFile.readLines().mapNotNull { line ->
            moduleFamilyRow.find(line)?.let { match ->
                match.groupValues[1] to match.groupValues[2].trim()
            }
        }.toMap()

    private fun classifyCapability(
        artifact: String,
        family: String,
        receiverName: String,
        name: String,
        path: String,
    ): String? = when {
        receiverName == "Modifier" -> "modifier"
        family == "Preview tooling" -> "tooling"
        artifact == "viewcompose-host-android" -> "host"
        family == "Integration" -> "integration"
        receiverName == "UiTreeBuilder" || receiverName.endsWith("Scope") -> {
            if (isComponentEntry(name, path)) "component" else "dsl"
        }
        else -> null
    }

    private fun isComponentEntry(name: String, path: String): Boolean =
        ("/dsl/" in path || "Components" in path || path.endsWith("AnimatedContent.kt") ||
            path.endsWith("AnimatedVisibility.kt") || path.endsWith("GraphicsDsl.kt")) &&
            !name.startsWith("Provide") && !name.startsWith("create") &&
            !name.endsWith("Theme") && name != "lazyItemContentFactory"

    private fun isApplicationEntry(
        artifact: String,
        family: String,
        packageName: String,
        name: String,
    ): Boolean = when {
        ".runtime" in packageName -> false
        family == "Preview tooling" ->
            name.startsWith("Preview") || name.startsWith("ViewComposePreview") || name.endsWith("Plugin")
        artifact == "viewcompose-host-android" -> true
        family == "Integration" -> true
        else -> false
    }

    private fun documentPlacementViolations(path: String, docType: String?): List<String> {
        val expected = expectedDocumentType(path)
        return if (expected != null && docType != expected) {
            listOf("$.doc_type -> $path requires $expected")
        } else {
            emptyList()
        }
    }

    private fun expectedDocumentType(path: String): String? = when {
            path == "docs/README.md" || path == "docs/modules/README.md" -> "project"
            path.startsWith("docs/tutorials/") -> "tutorial"
            "/tutorials/" in path && path.startsWith("website/i18n/") -> "tutorial"
            path.startsWith("docs/guides/") -> "guide"
            "/guides/" in path && path.startsWith("website/i18n/") -> "guide"
            path.startsWith("docs/architecture/") -> "architecture"
            "/architecture/" in path && path.startsWith("website/i18n/") -> "architecture"
            path.startsWith("docs/migration/") -> "migration"
            "/migration/" in path && path.startsWith("website/i18n/") -> "migration"
            path.matches(Regex("docs/modules/[^/]+/README\\.md")) -> "module"
            path.matches(Regex("website/i18n/[^/]+/[^/]+/modules/[^/]+/README\\.md")) -> "module"
            path.startsWith("docs/tooling/") -> "tooling"
            "/tooling/" in path && path.startsWith("website/i18n/") -> "tooling"
            path.startsWith("docs/project/") -> "project"
            else -> null
        }

    private fun parseExecutableFences(
        repository: File,
        path: String,
        source: String,
        versionLane: String?,
        sourceSetDirectories: Set<File>,
    ): List<GovernanceFence> {
        val lines = source.lines()
        val dependencies = discoverDependencies(source)
        val fences = mutableListOf<GovernanceFence>()
        var activeTutorialMarker: SourceMarker? = null
        var pendingMarker: SourceMarker? = null
        var index = 0
        while (index < lines.size) {
            marker.find(lines[index])?.let { match ->
                val markerName = match.groupValues[1].lowercase()
                if (markerName == "tutorial-sample-end") {
                    activeTutorialMarker = null
                    pendingMarker = null
                } else {
                    val parsed = SourceMarker.from(markerName, match.groupValues[2])
                    if (markerName == "tutorial-sample") {
                        activeTutorialMarker = parsed
                    } else {
                        pendingMarker = parsed
                    }
                }
            }
            val opening = executableFence.matchEntire(lines[index].trim())
            if (opening == null) {
                index += 1
                continue
            }
            val language = opening.groupValues[1].lowercase()
            val contentStart = index + 1
            var closing = contentStart
            while (closing < lines.size && !lines[closing].trimStart().startsWith("```")) {
                closing += 1
            }
            val content = lines.subList(contentStart, minOf(closing, lines.size)).joinToString("\n")
            val sourceMarker = activeTutorialMarker ?: pendingMarker
            val contentHash = sha256(content.trimEnd())
            val duplicateOrdinal = fences.count { fence ->
                fence.language == language && fence.contentHash == contentHash
            }
            fences += GovernanceFence(
                path = path,
                line = index + 1,
                language = language,
                versionLane = versionLane,
                dependencies = dependencies,
                sourceMarker = sourceMarker,
                contentHash = contentHash,
                duplicateOrdinal = duplicateOrdinal,
                classificationViolations = validateFenceClassification(
                    repository = repository,
                    sourceSetDirectories = sourceSetDirectories,
                    documentType = expectedDocumentType(path),
                    content = content,
                    marker = sourceMarker,
                ),
            )
            pendingMarker = null
            index = if (closing < lines.size) closing + 1 else lines.size
        }
        return fences
    }

    private fun validateFenceClassification(
        repository: File,
        sourceSetDirectories: Set<File>,
        documentType: String?,
        content: String,
        marker: SourceMarker?,
    ): List<String> {
        if (marker == null) return emptyList()
        return buildList {
            when (marker.marker) {
                "tutorial-sample" -> {
                    if (documentType != "tutorial") {
                        add("tutorial-sample is only valid in Tutorial documents")
                    }
                    if (marker.sampleId.isNullOrBlank()) {
                        add("tutorial-sample requires sample_id")
                    }
                    addAll(
                        compiledRegionViolations(
                            repository = repository,
                            sourceSetDirectories = sourceSetDirectories,
                            content = content,
                            marker = marker,
                        ),
                    )
                }
                "paired-sample" -> addAll(
                    compiledRegionViolations(
                        repository = repository,
                        sourceSetDirectories = sourceSetDirectories,
                        content = content,
                        marker = marker,
                    ),
                )
                "compiled-region" -> {
                    if (marker.sampleId.isNullOrBlank()) add("compiled-region requires sample_id")
                    if (marker.buildTarget.isNullOrBlank()) {
                        add("compiled-region requires build_target")
                    }
                    addAll(
                        compiledRegionViolations(
                            repository = repository,
                            sourceSetDirectories = sourceSetDirectories,
                            content = content,
                            marker = marker,
                        ),
                    )
                }
                "generated-signature" -> {
                    if (marker.symbolId.isNullOrBlank()) {
                        add("generated-signature requires symbol_id")
                    }
                    if (marker.generator.isNullOrBlank()) {
                        add("generated-signature requires generator")
                    }
                }
                "non-executable" -> {
                    if (documentType == "tutorial") {
                        add("non-executable is forbidden in Tutorial documents")
                    }
                    if (marker.reason.isNullOrBlank()) add("non-executable requires reason")
                    if (marker.visibleExplanation.isNullOrBlank()) {
                        add("non-executable requires visible_explanation")
                    }
                }
            }
        }.distinct().sorted()
    }

    private fun compiledRegionViolations(
        repository: File,
        sourceSetDirectories: Set<File>,
        content: String,
        marker: SourceMarker,
    ): List<String> {
        val sourcePath = marker.source
            ?: return listOf("${marker.marker} requires source")
        val region = marker.region
            ?: return listOf("${marker.marker} requires region")
        val canonicalRepository = repository.canonicalFile
        val sourceFile = canonicalRepository.resolve(sourcePath).canonicalFile
        if (!sourceFile.toPath().startsWith(canonicalRepository.toPath())) {
            return listOf("source must stay inside the repository")
        }
        val registeredRoots = sourceSetDirectories
            .filter(File::isDirectory)
            .map(File::getCanonicalFile)
        if (registeredRoots.none { root -> sourceFile.toPath().startsWith(root.toPath()) }) {
            return listOf("source is not inside a registered source-set input: $sourcePath")
        }
        if (!sourceFile.isFile) return listOf("source file does not exist: $sourcePath")
        val source = sourceFile.readText().replace("\r\n", "\n")
        val startMarker = "// DOCS_REGION_START($region)"
        val endMarker = "// DOCS_REGION_END($region)"
        if (source.windowed(startMarker.length).count { candidate -> candidate == startMarker } != 1) {
            return listOf("source must contain exactly one '$startMarker': $sourcePath")
        }
        if (source.windowed(endMarker.length).count { candidate -> candidate == endMarker } != 1) {
            return listOf("source must contain exactly one '$endMarker': $sourcePath")
        }
        val start = source.indexOf(startMarker) + startMarker.length
        val end = source.indexOf(endMarker, start)
        if (end < start) return listOf("'$endMarker' must follow '$startMarker': $sourcePath")
        val expected = source.substring(start, end).trim()
        return if (content.trim() == expected) {
            emptyList()
        } else {
            listOf("fence content differs from $sourcePath region $region")
        }
    }

    private fun discoverDependencies(source: String): List<Map<String, Any?>> = buildList {
        mavenDependency.findAll(source).forEach { match ->
            add(
                linkedMapOf<String, Any?>(
                    "artifact" to match.groupValues[1],
                    "kind" to "maven",
                    "version" to match.groupValues[2],
                ),
            )
        }
        projectDependency.findAll(source).forEach { match ->
            add(
                linkedMapOf<String, Any?>(
                    "artifact" to match.groupValues[1],
                    "kind" to "project",
                    "version" to null,
                ),
            )
        }
    }.distinctBy(StableJson::encode).sortedBy(StableJson::encode)

    private fun correlateDebtBaseline(
        issues: List<GovernanceIssue>,
        records: List<GovernanceRecord>,
    ): DebtBaselineResult {
        val issueGroups = issues.groupBy(GovernanceIssue::baselineKey)
        val exceptionRecords = records.filter { record -> record.contractId == "exception" && record.valid }
        val matches = mutableMapOf<String, DebtBaselineMatch>()
        val entries = exceptionRecords.map { record ->
            val target = record.value.objectValue("target").orEmpty()
            val targetKind = if ("file" in target) "file" else "symbol"
            val targetValue = target[targetKind]?.toString().orEmpty()
            val category = record.value.requiredString("category")
            val key = GovernanceIssue.baselineKey(category, targetKind, targetValue)
            val actualCount = issueGroups[key].orEmpty().size
            val expectedCount = record.value["violation_count"].asIntegerOrNull() ?: 0
            val status = when {
                actualCount == expectedCount -> "exact"
                actualCount == 0 -> "stale"
                actualCount < expectedCount -> "reduced"
                else -> "broadened"
            }
            val match = DebtBaselineMatch(
                exceptionId = record.recordId.orEmpty(),
                key = key,
                expectedCount = expectedCount,
                actualCount = actualCount,
                status = status,
                path = record.path,
            )
            matches.putIfAbsent(key, match)
            match
        }.sortedBy(DebtBaselineMatch::exceptionId)
        val unbaselined = issues.count { issue -> issue.baselineKey !in matches }
        return DebtBaselineResult(entries, matches, unbaselined)
    }

    private fun ratchetViolations(
        issues: List<GovernanceIssue>,
        records: List<GovernanceRecord>,
        baseline: DebtBaselineResult,
    ): List<String> = buildList {
        val exceptionRecords = records.filter { record -> record.contractId == "exception" }
        exceptionRecords.groupBy(GovernanceRecord::recordId)
            .filterKeys { recordId -> !recordId.isNullOrBlank() }
            .filterValues { matching -> matching.size > 1 }
            .forEach { (recordId, matching) ->
                add(
                    "exception id $recordId is duplicated by " +
                        matching.map(GovernanceRecord::path).sorted().joinToString(),
                )
            }
        exceptionRecords.filter(GovernanceRecord::valid)
            .groupBy { record ->
                val target = record.value.objectValue("target").orEmpty()
                val targetKind = if ("file" in target) "file" else "symbol"
                GovernanceIssue.baselineKey(
                    category = record.value.requiredString("category"),
                    targetKind = targetKind,
                    target = target[targetKind]?.toString().orEmpty(),
                )
            }
            .filterValues { matching -> matching.size > 1 }
            .forEach { (key, matching) ->
                add(
                    "baseline target ${key.replace('\u0000', '|')} is duplicated by " +
                        matching.mapNotNull(GovernanceRecord::recordId).sorted().joinToString(),
                )
            }
        issues.filter { issue -> issue.baselineKey !in baseline.matches }.forEach { issue ->
            add("${issue.id} is unbaselined: ${issue.target} (${issue.category})")
        }
        baseline.entries.filterNot { entry -> entry.status == "exact" }.forEach { entry ->
            val action = when (entry.status) {
                "stale" -> "delete the resolved exception"
                "reduced" -> "lower violation_count to ${entry.actualCount} or finish and delete the exception"
                else -> "repair the added debt; increasing violation_count is forbidden"
            }
            add(
                "${entry.exceptionId} is ${entry.status}: expected ${entry.expectedCount}, " +
                    "actual ${entry.actualCount}; $action",
            )
        }
    }.distinct().sorted()

    private fun humanReport(
        issues: List<GovernanceIssue>,
        baseline: DebtBaselineResult,
        declarationCount: Int,
        documentCount: Int,
        fenceCount: Int,
        contractViolations: List<String>,
        ratchetViolations: List<String>,
    ): String = buildString {
        appendLine("Documentation Governance V2 — Phase 2 no-new-debt gate")
        appendLine("Inventory: $declarationCount production entries; $documentCount public pages; $fenceCount executable fences")
        appendLine("Issues: ${issues.size}; baseline entries: ${baseline.entries.size}; unbaselined: ${baseline.unbaselinedIssueCount}")
        val blockingViolationCount = contractViolations.size + ratchetViolations.size
        appendLine("Gate: ${if (blockingViolationCount == 0) "passed" else "failed"}; violations: $blockingViolationCount")
        if (contractViolations.isNotEmpty()) {
            appendLine()
            appendLine("[contract-violations] ${contractViolations.size}")
            contractViolations.forEach { violation -> appendLine("- $violation") }
        }
        if (ratchetViolations.isNotEmpty()) {
            appendLine()
            appendLine("[ratchet-violations] ${ratchetViolations.size}")
            ratchetViolations.forEach { violation -> appendLine("- $violation") }
        }
        issueCategories.forEach { category ->
            val matching = issues.filter { issue -> issue.category == category }
            appendLine()
            appendLine("[$category] ${matching.size}")
            matching.forEach { issue ->
                val baselineId = baseline.matches[issue.baselineKey]?.exceptionId ?: "unbaselined"
                appendLine("- ${issue.id} [$baselineId] ${issue.target}: ${issue.detail}")
            }
        }
        if (baseline.entries.isNotEmpty()) {
            appendLine()
            appendLine("[baseline-status] ${baseline.entries.size}")
            baseline.entries.forEach { entry ->
                appendLine(
                    "- ${entry.exceptionId} ${entry.status}: expected ${entry.expectedCount}, actual ${entry.actualCount} (${entry.path})",
                )
            }
        }
    }

    private fun sanitizeKotlin(source: String): String {
        val result = source.toCharArray()
        var index = 0
        var blockCommentDepth = 0
        var state = KotlinLexicalState.Code
        while (index < result.size) {
            val current = result[index]
            val next = result.getOrNull(index + 1)
            when (state) {
                KotlinLexicalState.Code -> when {
                    current == '/' && next == '/' -> {
                        result[index] = ' '
                        result[index + 1] = ' '
                        index += 2
                        state = KotlinLexicalState.LineComment
                    }
                    current == '/' && next == '*' -> {
                        result[index] = ' '
                        result[index + 1] = ' '
                        index += 2
                        blockCommentDepth = 1
                        state = KotlinLexicalState.BlockComment
                    }
                    current == '"' && next == '"' && result.getOrNull(index + 2) == '"' -> {
                        repeat(3) { offset -> result[index + offset] = ' ' }
                        index += 3
                        state = KotlinLexicalState.TripleString
                    }
                    current == '"' -> {
                        result[index] = ' '
                        index += 1
                        state = KotlinLexicalState.String
                    }
                    current == '\'' -> {
                        result[index] = ' '
                        index += 1
                        state = KotlinLexicalState.Character
                    }
                    else -> index += 1
                }
                KotlinLexicalState.LineComment -> {
                    if (current == '\n') {
                        state = KotlinLexicalState.Code
                    } else {
                        result[index] = ' '
                    }
                    index += 1
                }
                KotlinLexicalState.BlockComment -> when {
                    current == '/' && next == '*' -> {
                        result[index] = ' '
                        result[index + 1] = ' '
                        blockCommentDepth += 1
                        index += 2
                    }
                    current == '*' && next == '/' -> {
                        result[index] = ' '
                        result[index + 1] = ' '
                        blockCommentDepth -= 1
                        index += 2
                        if (blockCommentDepth == 0) state = KotlinLexicalState.Code
                    }
                    else -> {
                        if (current != '\n') result[index] = ' '
                        index += 1
                    }
                }
                KotlinLexicalState.String, KotlinLexicalState.Character -> {
                    if (current == '\\') {
                        result[index] = ' '
                        result.getOrNull(index + 1)?.let { result[index + 1] = if (it == '\n') '\n' else ' ' }
                        index += 2
                    } else {
                        val closing = (state == KotlinLexicalState.String && current == '"') ||
                            (state == KotlinLexicalState.Character && current == '\'')
                        if (current != '\n') result[index] = ' '
                        index += 1
                        if (closing) state = KotlinLexicalState.Code
                    }
                }
                KotlinLexicalState.TripleString -> {
                    if (current == '"' && next == '"' && result.getOrNull(index + 2) == '"') {
                        repeat(3) { offset -> result[index + offset] = ' ' }
                        index += 3
                        state = KotlinLexicalState.Code
                    } else {
                        if (current != '\n') result[index] = ' '
                        index += 1
                    }
                }
            }
        }
        return result.concatToString()
    }

    private fun braceDepths(source: String): IntArray {
        val result = IntArray(source.length)
        var depth = 0
        source.indices.forEach { index ->
            result[index] = depth
            when (source[index]) {
                '{' -> depth += 1
                '}' -> depth = maxOf(0, depth - 1)
            }
        }
        return result
    }
}

internal data class DocumentationGovernanceV2MutationAudit(
    val verificationBase: String,
    val violations: List<String>,
)

internal data class DocumentationGovernanceV2GitCommandResult(
    val exitCode: Int,
    val output: String,
)

internal fun interface DocumentationGovernanceV2GitCommandExecutor {
    fun execute(arguments: List<String>): DocumentationGovernanceV2GitCommandResult
}

internal object DocumentationGovernanceV2GitRatchet {
    private const val exceptionRoot =
        "docs/project/records/documentation-governance-v2/exceptions"

    fun inspect(
        repository: File,
        explicitBaseRevision: String?,
        executor: DocumentationGovernanceV2GitCommandExecutor = processExecutor(repository),
    ): DocumentationGovernanceV2MutationAudit {
        val git = GitCommands(executor)
        val base = when {
            !explicitBaseRevision.isNullOrBlank() ->
                git.execute("rev-parse", "--verify", "$explicitBaseRevision^{commit}").trim()
            git.executeOrNull("rev-parse", "--verify", "origin/main") != null ->
                git.execute("merge-base", "HEAD", "origin/main").trim()
            else -> git.execute("rev-parse", "HEAD^").trim()
        }
        val changes = git.execute(
            "diff",
            "--name-status",
            "--find-renames",
            base,
            "--",
            exceptionRoot,
        ).lineSequence().filter(String::isNotBlank).map { line ->
            val fields = line.split('\t')
            ExceptionChange(
                status = fields.first().first(),
                path = fields.last().replace('\\', '/'),
            )
        }.toMutableList()
        git.execute(
            "ls-files",
            "--others",
            "--exclude-standard",
            "--",
            exceptionRoot,
        ).lineSequence().filter(String::isNotBlank).forEach { path ->
            changes += ExceptionChange('A', path.replace('\\', '/'))
        }
        val violations = changes.distinct().sortedBy(ExceptionChange::path).mapNotNull { change ->
            when (change.status) {
                'D' -> null
                'A' -> "${change.path} adds a debt exception; the frozen baseline cannot grow or re-add a removed id"
                'M' -> verifyReducedException(repository, git, base, change.path)
                'R', 'C' -> "${change.path} renames or copies a debt exception; baseline identity is immutable"
                else -> "${change.path} has unsupported debt-baseline status ${change.status}"
            }
        }
        return DocumentationGovernanceV2MutationAudit(
            verificationBase = base,
            violations = violations.sorted(),
        )
    }

    private fun verifyReducedException(
        repository: File,
        git: GitCommands,
        base: String,
        path: String,
    ): String? = runCatching {
        val previous = JsonSlurper().parseText(git.execute("show", "$base:$path")).asObject()
            ?: error("base content is not a JSON object")
        val current = repository.resolve(path).parseJsonObject()
        val previousCount = previous["violation_count"].asIntegerOrNull()
            ?: error("base violation_count is missing")
        val currentCount = current["violation_count"].asIntegerOrNull()
            ?: error("current violation_count is missing")
        val previousIdentity = previous - "violation_count"
        val currentIdentity = current - "violation_count"
        when {
            previousIdentity != currentIdentity ->
                "$path changes immutable exception identity or rationale; only a lower violation_count is allowed"
            currentCount >= previousCount ->
                "$path changes violation_count from $previousCount to $currentCount; it must decrease"
            else -> null
        }
    }.getOrElse { failure ->
        "$path cannot be validated as a monotonic exception reduction: ${failure.message}"
    }

    private fun processExecutor(repository: File) = DocumentationGovernanceV2GitCommandExecutor { arguments ->
        val output = ByteArrayOutputStream()
        val process = ProcessBuilder(listOf("git") + arguments)
            .directory(repository)
            .redirectErrorStream(true)
            .start()
        process.inputStream.use { input -> input.copyTo(output) }
        DocumentationGovernanceV2GitCommandResult(
            exitCode = process.waitFor(),
            output = output.toString(StandardCharsets.UTF_8.name()),
        )
    }

    private data class ExceptionChange(
        val status: Char,
        val path: String,
    )

    private class GitCommands(
        private val executor: DocumentationGovernanceV2GitCommandExecutor,
    ) {
        fun execute(vararg arguments: String): String {
            val result = executor.execute(arguments.toList())
            check(result.exitCode == 0) {
                "git ${arguments.joinToString(" ")} failed (${result.exitCode}): ${result.output.trim()}"
            }
            return result.output
        }

        fun executeOrNull(vararg arguments: String): String? =
            executor.execute(arguments.toList()).takeIf { result -> result.exitCode == 0 }?.output
    }
}

private data class CapabilityDeclaration(
    val artifact: String,
    val kind: String,
    val line: Int,
    val path: String,
    val receiver: String?,
    val sourcePackage: String,
    val symbol: String,
    val visibility: String,
)

private data class GovernanceRecord(
    val path: String,
    val contractId: String?,
    val valid: Boolean,
    val violations: List<String>,
    val value: Map<String, Any?>,
    val recordId: String?,
) {
    fun report(): Map<String, Any?> = linkedMapOf(
        "path" to path,
        "contractId" to contractId,
        "recordId" to recordId,
        "valid" to valid,
        "violations" to violations,
    )
}

private data class SourceMarker(
    val marker: String,
    val sampleClass: String,
    val source: String?,
    val region: String?,
    val sampleId: String?,
    val buildTarget: String?,
    val symbolId: String?,
    val generator: String?,
    val reason: String?,
    val visibleExplanation: String?,
) {
    fun report(): Map<String, Any?> = linkedMapOf(
        "marker" to marker,
        "sampleClass" to sampleClass,
        "source" to source,
        "region" to region,
        "sampleId" to sampleId,
        "buildTarget" to buildTarget,
        "symbolId" to symbolId,
        "generator" to generator,
        "reason" to reason,
        "visibleExplanation" to visibleExplanation,
    )

    companion object {
        fun from(marker: String, attributeSource: String): SourceMarker {
            val attributes = Regex(
                """([a-zA-Z][a-zA-Z0-9_-]*)\s*=\s*[\"']([^\"']+)[\"']""",
            ).findAll(attributeSource)
                .associate { match -> match.groupValues[1] to match.groupValues[2] }
            return SourceMarker(
                marker = marker,
                sampleClass = when (marker) {
                    "generated-signature" -> "generated-signature"
                    "non-executable" -> "non-executable"
                    else -> "compiled-region"
                },
                source = attributes["source"],
                region = attributes["region"],
                sampleId = attributes["sample_id"],
                buildTarget = attributes["build_target"],
                symbolId = attributes["symbol_id"],
                generator = attributes["generator"],
                reason = attributes["reason"],
                visibleExplanation = attributes["visible_explanation"],
            )
        }
    }
}

private data class GovernanceFence(
    val path: String,
    val line: Int,
    val language: String,
    val versionLane: String?,
    val dependencies: List<Map<String, Any?>>,
    val sourceMarker: SourceMarker?,
    val contentHash: String,
    val duplicateOrdinal: Int,
    val classificationViolations: List<String>,
) {
    val registered: Boolean
        get() = sourceMarker?.let { marker ->
            marker.sampleClass != "compiled-region" ||
                (marker.source != null && marker.region != null)
        } == true

    fun report(mirror: GovernanceFence?): Map<String, Any?> = linkedMapOf(
        "contentHash" to contentHash,
        "classificationViolations" to classificationViolations,
        "dependencies" to dependencies,
        "language" to language,
        "languageMirror" to mirror?.let { mirrorFence ->
            linkedMapOf<String, Any?>(
                "contentMatches" to (contentHash == mirrorFence.contentHash),
                "classificationViolations" to mirrorFence.classificationViolations,
                "language" to mirrorFence.language,
                "line" to mirrorFence.line,
                "path" to mirrorFence.path,
                "sourceMarkerMatches" to (sourceMarker == mirrorFence.sourceMarker),
            )
        },
        "line" to line,
        "path" to path,
        "registered" to registered,
        "sourceMarker" to sourceMarker?.report(),
        "stableFenceIdentity" to "$path|$language|$contentHash|$duplicateOrdinal",
        "versionLane" to versionLane,
    )
}

private data class GovernanceIssue(
    val id: String,
    val category: String,
    val target: String,
    val detail: String,
    val baselineTargetKind: String,
    val baselineTarget: String,
    val baselineKey: String,
) {
    fun toReportMap(baselineExceptionId: String?): Map<String, Any?> = linkedMapOf(
        "baselineExceptionId" to baselineExceptionId,
        "baselineTarget" to linkedMapOf(baselineTargetKind to baselineTarget),
        "category" to category,
        "detail" to detail,
        "id" to id,
        "target" to target,
    )

    companion object {
        fun file(
            category: String,
            target: String,
            detail: String,
            baselineTarget: String = target,
            identitySuffix: String = target,
        ): GovernanceIssue = create(category, target, detail, "file", baselineTarget, identitySuffix)

        fun symbol(
            category: String,
            target: String,
            detail: String,
            identitySuffix: String = target,
            baselineFile: String? = null,
        ): GovernanceIssue = create(
            category,
            target,
            detail,
            if (baselineFile == null) "symbol" else "file",
            baselineFile ?: target,
            identitySuffix,
        )

        fun baselineKey(category: String, targetKind: String, target: String): String =
            "$category\u0000$targetKind\u0000$target"

        private fun create(
            category: String,
            target: String,
            detail: String,
            baselineTargetKind: String,
            baselineTarget: String,
            identitySuffix: String,
        ): GovernanceIssue = GovernanceIssue(
            id = "gov2-${sha256("$category\u0000$identitySuffix").take(16)}",
            category = category,
            target = target,
            detail = detail,
            baselineTargetKind = baselineTargetKind,
            baselineTarget = baselineTarget,
            baselineKey = baselineKey(category, baselineTargetKind, baselineTarget),
        )
    }
}

private data class DebtBaselineMatch(
    val exceptionId: String,
    val key: String,
    val expectedCount: Int,
    val actualCount: Int,
    val status: String,
    val path: String,
) {
    fun report(): Map<String, Any?> = linkedMapOf(
        "actualCount" to actualCount,
        "exceptionId" to exceptionId,
        "expectedCount" to expectedCount,
        "path" to path,
        "status" to status,
    )
}

private data class DebtBaselineResult(
    val entries: List<DebtBaselineMatch>,
    val matches: Map<String, DebtBaselineMatch>,
    val unbaselinedIssueCount: Int,
) {
    fun report(): Map<String, Any?> = linkedMapOf(
        "entries" to entries.map(DebtBaselineMatch::report),
        "entryCount" to entries.size,
        "exactEntryCount" to entries.count { entry -> entry.status == "exact" },
        "unbaselinedIssueCount" to unbaselinedIssueCount,
    )
}

private enum class KotlinLexicalState {
    Code,
    LineComment,
    BlockComment,
    String,
    TripleString,
    Character,
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

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }

private fun String.withoutTypeArguments(): String = buildString {
    var depth = 0
    this@withoutTypeArguments.forEach { character ->
        when (character) {
            '<' -> depth += 1
            '>' -> depth = maxOf(0, depth - 1)
            else -> if (depth == 0) append(character)
        }
    }
}

private fun parseFrontMatter(source: String): Map<String, Any?> {
    val lines = source.lineSequence().toList()
    if (lines.firstOrNull()?.trim() != "---") return emptyMap()
    val closingIndex = lines.drop(1).indexOfFirst { line -> line.trim() == "---" }
        .takeIf { index -> index >= 0 }
        ?.plus(1)
        ?: return emptyMap()
    val yamlLines = lines.subList(1, closingIndex).mapNotNull { line ->
        val content = line.trimEnd()
        if (content.isBlank() || content.trimStart().startsWith("#")) {
            null
        } else {
            FrontMatterLine(
                indent = content.indexOfFirst { character -> !character.isWhitespace() }
                    .coerceAtLeast(0),
                content = content.trimStart(),
            )
        }
    }
    return parseFrontMatterMap(yamlLines, 0, 0).first
}

private data class FrontMatterLine(val indent: Int, val content: String)

private fun parseFrontMatterMap(
    lines: List<FrontMatterLine>,
    start: Int,
    indent: Int,
): Pair<Map<String, Any?>, Int> {
    val result = linkedMapOf<String, Any?>()
    var index = start
    while (index < lines.size) {
        val line = lines[index]
        if (line.indent < indent) break
        if (line.indent > indent || line.content.startsWith("- ")) {
            index += 1
            continue
        }
        val separator = line.content.indexOf(':')
        if (separator <= 0) {
            index += 1
            continue
        }
        val key = line.content.substring(0, separator).trim()
        val rawValue = line.content.substring(separator + 1).trim()
        if (rawValue.isNotEmpty()) {
            result[key] = parseFrontMatterScalar(rawValue)
            index += 1
            continue
        }
        val child = lines.getOrNull(index + 1)
        if (child == null || child.indent <= indent) {
            result[key] = null
            index += 1
        } else if (child.content.startsWith("- ")) {
            val values = mutableListOf<Any?>()
            index += 1
            while (index < lines.size && lines[index].indent == child.indent &&
                lines[index].content.startsWith("- ")
            ) {
                values += parseFrontMatterScalar(lines[index].content.removePrefix("- ").trim())
                index += 1
            }
            result[key] = values
        } else {
            val (childMap, nextIndex) = parseFrontMatterMap(lines, index + 1, child.indent)
            result[key] = childMap
            index = nextIndex
        }
    }
    return result to index
}

private fun parseFrontMatterScalar(rawValue: String): Any? = when {
    rawValue == "[]" -> emptyList<String>()
    rawValue.startsWith('[') && rawValue.endsWith(']') -> rawValue
        .removePrefix("[")
        .removeSuffix("]")
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { value -> value.removeSurrounding("\"").removeSurrounding("'") }
    rawValue == "true" -> true
    rawValue == "false" -> false
    rawValue == "null" -> null
    rawValue.toIntOrNull() != null -> rawValue.toInt()
    else -> rawValue.removeSurrounding("\"").removeSurrounding("'")
}

private fun File.loadProperties(): Properties = Properties().apply {
    inputStream().use(::load)
}

private fun Properties.csvProperty(name: String): Set<String> =
    getProperty(name).orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).toSet()
