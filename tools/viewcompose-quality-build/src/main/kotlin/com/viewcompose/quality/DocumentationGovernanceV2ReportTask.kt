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
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Enforces the zero-exception Governance V2 strict gate and writes deterministic audit reports. */
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

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val committedReferenceFile: RegularFileProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:OutputFile
    abstract val humanReportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val repository = repositoryDirectory.get().asFile
        val mutationAudit = DocumentationGovernanceV2GitPolicy.inspect(
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
            committedReferenceFile = committedReferenceFile.get().asFile,
            verificationContext = DocumentationGovernanceV2VerificationContext(
                verificationBase = mutationAudit.verificationBase,
                mutationViolations = mutationAudit.violations,
                addedImpactPaths = mutationAudit.addedImpactPaths,
                changedSourceFiles = mutationAudit.changedSourceFiles,
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
        if (result.contractViolations.isNotEmpty() || result.strictViolations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    if (result.contractViolations.isNotEmpty()) {
                        appendLine("Documentation Governance V2 contract validation failed:")
                        result.contractViolations.sorted().forEach { violation ->
                            appendLine("- $violation")
                        }
                    }
                    if (result.strictViolations.isNotEmpty()) {
                        if (isNotEmpty()) appendLine()
                        appendLine("Documentation Governance V2 strict gate failed:")
                        result.strictViolations.sorted().forEach { violation ->
                            appendLine("- $violation")
                        }
                    }
                }.trimEnd(),
            )
        }
        logger.lifecycle(
            "Documentation Governance V2 strict gate verified against {}: zero issue(s). Report: {}.",
            mutationAudit.verificationBase,
            reportFile.get().asFile,
        )
    }
}

/** Rewrites the committed application-facing Reference catalog from the Governance V2 model. */
abstract class UpdateDocumentationCapabilityReferenceTask : DefaultTask() {
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

    @get:OutputFile
    abstract val referenceFile: RegularFileProperty

    @TaskAction
    fun update() {
        val result = DocumentationGovernanceV2Reporter.generate(
            repository = repositoryDirectory.get().asFile,
            contractFiles = contractFiles.files,
            recordFiles = recordFiles.files,
            sourceSetDirectories = sourceSetDirectories.files,
            activeDocumentationFiles = activeDocumentationFiles.files,
            localeMirrorFiles = localeMirrorFiles.files,
            publishingFiles = publishingFiles.files,
            documentationPolicyFiles = documentationPolicyFiles.files,
        )
        if (result.contractViolations.isNotEmpty() || result.strictViolations.isNotEmpty()) {
            val violations = (result.contractViolations + result.strictViolations).distinct().sorted()
            throw GradleException(
                "Cannot update the capability Reference while the Governance V2 strict gate is invalid:\n" +
                    violations.joinToString("\n") { violation ->
                        "- $violation"
                    },
            )
        }
        referenceFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(result.referenceCatalog)
        }
        logger.lifecycle(
            "Updated the source-derived capability Reference with {} entries: {}.",
            result.referenceEntryCount,
            referenceFile.get().asFile,
        )
    }
}

internal data class DocumentationGovernanceV2ReportResult(
    val report: String,
    val humanReport: String,
    val referenceCatalog: String,
    val referenceEntryCount: Int,
    val contractViolations: List<String>,
    val strictViolations: List<String>,
    val issueCount: Int,
)

internal data class DocumentationGovernanceV2VerificationContext(
    val verificationBase: String? = null,
    val mutationViolations: List<String> = emptyList(),
    val addedImpactPaths: Set<String> = emptySet(),
    val changedSourceFiles: List<DocumentationGovernanceV2SourceChange> = emptyList(),
)

internal data class DocumentationGovernanceV2SourceChange(
    val basePath: String?,
    val baseSource: String?,
    val currentPath: String?,
    val currentSource: String?,
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
        committedReferenceFile: File? = null,
        verificationContext: DocumentationGovernanceV2VerificationContext =
            DocumentationGovernanceV2VerificationContext(),
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
        val referenceCatalog = generateCapabilityReferenceCatalog(
            declarations = declarations,
            documents = documents,
            records = records,
            publishing = publishing,
        )
        val encodedReferenceCatalog = StableJson.encode(referenceCatalog) + "\n"
        val publicApiChanges = DocumentationGovernanceV2PublicApiChanges.detect(
            baseDeclarations = discoverCapabilityDeclarations(
                sourceFiles = verificationContext.changedSourceFiles.mapNotNull { change ->
                    change.baseSource?.let { source ->
                        GovernanceSourceFile(change.basePath.orEmpty(), source)
                    }
                },
                activeArtifacts = activeArtifacts,
                moduleFamilies = moduleFamilies,
            ),
            currentDeclarations = discoverCapabilityDeclarations(
                sourceFiles = verificationContext.changedSourceFiles.mapNotNull { change ->
                    change.currentSource?.let { source ->
                        GovernanceSourceFile(change.currentPath.orEmpty(), source)
                    }
                },
                activeArtifacts = activeArtifacts,
                moduleFamilies = moduleFamilies,
            ),
            currentInventory = declarations,
        )
        val publicApiImpactViolations = DocumentationGovernanceV2PublicApiChanges.verifyImpacts(
            changes = publicApiChanges,
            addedImpactPaths = verificationContext.addedImpactPaths,
            records = records,
        )
        val issues = buildIssues(
            declarations = declarations,
            documents = documents,
            fences = fences,
            records = records,
            publishing = publishing,
            committedReferenceFile = committedReferenceFile,
            encodedReferenceCatalog = encodedReferenceCatalog,
        )
        val strictViolations = (
            strictViolations(issues = issues, records = records) +
                verificationContext.mutationViolations +
                publicApiImpactViolations
        ).distinct().sorted()
        val reportIssues = issues.map(GovernanceIssue::toReportMap)
        val issueCounts = issueCategories.associateWith { category ->
            issues.count { issue -> issue.category == category }
        }

        val report = linkedMapOf<String, Any?>(
            "schemaVersion" to 1,
            "mode" to "strict",
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
                "publicApiChanges" to publicApiChanges.map(
                    DocumentationGovernanceV2PublicApiChange::report,
                ),
                "publishing" to publishing,
                "referenceCatalog" to referenceCatalog.getValue("summary"),
            ),
            "gate" to linkedMapOf(
                "status" to if (strictViolations.isEmpty() && contractViolations.isEmpty()) "passed" else "failed",
                "verificationBase" to verificationContext.verificationBase,
                "contractViolationCount" to contractViolations.size,
                "strictViolationCount" to strictViolations.size,
                "contractViolations" to contractViolations.sorted(),
                "strictViolations" to strictViolations,
            ),
            "summary" to linkedMapOf(
                "blockingViolationCount" to (contractViolations.size + strictViolations.size),
                "contractCount" to contracts.size,
                "contractViolationCount" to contractViolations.size,
                "declarationCount" to declarations.size,
                "documentCount" to documents.size,
                "executableFenceCount" to fences.size,
                "localeMirrorCount" to localeMirrors.size,
                "recordCount" to records.size,
                "publicApiChangeCount" to publicApiChanges.size,
                "issueCount" to issues.size,
                "issueCounts" to issueCounts,
            ),
            "issues" to reportIssues,
        )
        return DocumentationGovernanceV2ReportResult(
            report = StableJson.encode(report) + "\n",
            humanReport = humanReport(
                issues = issues,
                declarationCount = declarations.size,
                documentCount = documents.size,
                fenceCount = fences.size,
                publicApiChanges = publicApiChanges,
                contractViolations = contractViolations.sorted(),
                strictViolations = strictViolations,
            ),
            referenceCatalog = encodedReferenceCatalog,
            referenceEntryCount = declarations.size,
            contractViolations = contractViolations.sorted(),
            strictViolations = strictViolations,
            issueCount = issues.size,
        )
    }

    private fun buildIssues(
        declarations: List<Map<String, Any?>>,
        documents: List<Map<String, Any?>>,
        fences: List<Map<String, Any?>>,
        records: List<GovernanceRecord>,
        publishing: Map<String, Any?>,
        committedReferenceFile: File?,
        encodedReferenceCatalog: String,
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
                add(
                    GovernanceIssue.file(
                        category = "unclassified-sample",
                        target = "${fence.getValue("path")}:${fence.getValue("line")}",
                        identitySuffix = fence.getValue("stableFenceIdentity").toString(),
                        detail = "executable fence has no adjacent compiled-sample registration marker",
                    ),
                )
            }
            fences.forEach { fence ->
                @Suppress("UNCHECKED_CAST")
                val violations = fence["classificationViolations"] as? List<String> ?: emptyList()
                if (violations.isNotEmpty()) {
                    add(
                        GovernanceIssue.file(
                            category = "taxonomy-mismatch",
                            target = "${fence.getValue("path")}:${fence.getValue("line")}",
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
                            identitySuffix =
                                "mirror-sample-contract|${fence.getValue("stableFenceIdentity")}",
                            detail = mirrorViolations.joinToString("; "),
                        ),
                    )
                }
            }
            fences.forEach { fence ->
                @Suppress("UNCHECKED_CAST")
                val mirror = fence["languageMirror"] as? Map<String, Any?>
                when {
                    mirror == null -> add(
                        GovernanceIssue.file(
                            category = "taxonomy-mismatch",
                            target = "${fence.getValue("path")}:${fence.getValue("line")}",
                            identitySuffix = "missing-mirror|${fence.getValue("stableFenceIdentity")}",
                            detail = "executable fence has no corresponding locale-mirror fence",
                        ),
                    )
                    mirror["contentMatches"] != true -> add(
                        GovernanceIssue.file(
                            category = "taxonomy-mismatch",
                            target = "${fence.getValue("path")}:${fence.getValue("line")}",
                            identitySuffix = "mirror-content|${fence.getValue("stableFenceIdentity")}",
                            detail = "canonical and locale-mirror executable fence content differs",
                        ),
                    )
                    mirror["sourceMarkerMatches"] != true -> add(
                        GovernanceIssue.file(
                            category = "taxonomy-mismatch",
                            target = "${fence.getValue("path")}:${fence.getValue("line")}",
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
            records.filter { record -> record.valid && record.recordId != null }
                .groupBy { record -> record.contractId to record.recordId }
                .filterValues { matching -> matching.size > 1 }
                .forEach { (identity, matching) ->
                    add(
                        GovernanceIssue.file(
                            category = "taxonomy-mismatch",
                            target = matching.map(GovernanceRecord::path).sorted().joinToString(","),
                            detail =
                                "${identity.first} record id ${identity.second} is declared more than once",
                        ),
                    )
                }
            declarations.forEach { declaration ->
                val symbol = declaration.getValue("symbol").toString()
                if (symbol !in capabilityOwnersBySymbol) {
                    @Suppress("UNCHECKED_CAST")
                    add(
                        GovernanceIssue.symbol(
                            category = "orphan-symbol",
                            target = symbol,
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
            if (committedReferenceFile != null &&
                (!committedReferenceFile.isFile || committedReferenceFile.readText() != encodedReferenceCatalog)
            ) {
                add(
                    GovernanceIssue.file(
                        category = "stale-generated-output",
                        target = "website/src/data/capability-reference.json",
                        detail =
                            "committed capability Reference differs from the source-derived Governance V2 model; " +
                                "run ./gradlew updateDocumentationCapabilityReference",
                    ),
                )
            }
        }.distinctBy(GovernanceIssue::id)
            .sortedWith(compareBy(GovernanceIssue::category, GovernanceIssue::target, GovernanceIssue::id))
    }

    private fun generateCapabilityReferenceCatalog(
        declarations: List<Map<String, Any?>>,
        documents: List<Map<String, Any?>>,
        records: List<GovernanceRecord>,
        publishing: Map<String, Any?>,
    ): Map<String, Any?> {
        val capabilitiesBySymbol = records
            .filter { record -> record.contractId == "capability" && record.valid }
            .flatMap { record ->
                record.value.listOfObjects("symbols").map { symbol ->
                    symbol.requiredString("symbol_id") to record
                }
            }
            .groupBy(Pair<String, GovernanceRecord>::first)
            .mapValues { (_, matches) ->
                matches.map(Pair<String, GovernanceRecord>::second)
                    .sortedBy(GovernanceRecord::path)
                    .first()
            }
        val samplesById = records
            .filter { record -> record.contractId == "sample" && record.valid && record.recordId != null }
            .associateBy { record -> record.recordId.orEmpty() }
        val documentsById = documents
            .filter { document -> document["schemaVersion"] == 2 && document["documentId"] != null }
            .associateBy { document -> document.getValue("documentId").toString() }
        @Suppress("UNCHECKED_CAST")
        val publishingByArtifact = (publishing.getValue("artifacts") as List<Map<String, Any?>>)
            .associateBy { artifact -> artifact.getValue("artifact").toString() }
        val referenceArtifacts = declarations
            .map { declaration -> declaration.getValue("artifact").toString() }
            .distinct()
            .sorted()
            .map { artifact ->
                val publishingArtifact = publishingByArtifact[artifact].orEmpty()
                val versionState = publishingArtifact["versionState"]?.toString() ?: "unresolved"
                val version = publishingArtifact["version"]?.toString()
                val documentationVersion = if (versionState == "released" && version != null) {
                    version
                } else {
                    "current"
                }
                linkedMapOf<String, Any?>(
                    "apiReference" to "/api/$artifact/$documentationVersion/",
                    "artifact" to artifact,
                    "moduleManual" to if (versionState == "released" && version != null) {
                        "/modules/$artifact/$version/"
                    } else {
                        "/modules/$artifact/"
                    },
                    "version" to version,
                    "versionState" to versionState,
                )
            }

        val referenceCapabilityRecords = capabilitiesBySymbol.values
            .distinctBy(GovernanceRecord::path)
            .sortedBy { capability -> capability.recordId }
        val relatedDocumentTypesById = referenceCapabilityRecords
            .flatMap { capability ->
                val documentOwners = capability.value.objectValue("document_owners").orEmpty()
                referenceDocumentTypes.flatMap { documentType ->
                    documentOwners.stringList(documentType).map { documentId ->
                        documentId to documentType
                    }
                }
            }
            .groupBy(
                keySelector = Pair<String, String>::first,
                valueTransform = Pair<String, String>::second,
            )
            .mapValues { (_, documentTypes) -> documentTypes.distinct().sorted() }
            .toSortedMap()
        val referenceDocuments = relatedDocumentTypesById.mapValues { (documentId, ownerTypes) ->
            val document = documentsById[documentId]
            linkedMapOf<String, Any?>(
                "documentType" to (document?.get("docType")?.toString() ?: ownerTypes.first()),
                "path" to document?.referenceRoute(),
            )
        }
        val referenceCapabilities = referenceCapabilityRecords
            .map { capability ->
                val capabilityValue = capability.value
                val referenceOwner = capabilityValue.objectValue("reference_owner").orEmpty()
                val sampleOwner = capabilityValue.objectValue("sample_owner").orEmpty()
                val sampleId = sampleOwner["sample_id"]?.toString()
                val sampleRecord = sampleId?.let(samplesById::get)
                val documentOwners = capabilityValue.objectValue("document_owners").orEmpty()
                val relatedDocumentIds = referenceDocumentTypes.flatMap { documentType ->
                    documentOwners.stringList(documentType)
                }.distinct()
                linkedMapOf<String, Any?>(
                    "capabilityId" to capability.recordId,
                ).apply {
                    referenceOwner["reference_id"]?.let { referenceId ->
                        put("referenceId", referenceId)
                    }
                    if (relatedDocumentIds.isNotEmpty()) {
                        put("relatedDocumentIds", relatedDocumentIds)
                    }
                    val sample = when {
                        sampleRecord != null -> linkedMapOf<String, Any?>(
                            "sampleClass" to sampleRecord.value["sample_class"],
                            "sampleId" to sampleId,
                            "versionLane" to sampleRecord.value["version_lane"],
                        )
                        else -> null
                    }
                    if (sample != null) put("sample", sample)
                }
            }

        val entries = declarations.map { declaration ->
            val artifact = declaration.getValue("artifact").toString()
            val symbol = declaration.getValue("symbol").toString()
            val group = referenceGroup(declaration)
            val capability = capabilitiesBySymbol[symbol]
            linkedMapOf<String, Any?>(
                "artifact" to artifact,
                "kind" to declaration.getValue("kind"),
                "namespace" to declaration.getValue("sourcePackage"),
                "overloadCount" to declaration.getValue("overloadCount"),
                "symbol" to symbol,
            ).apply {
                capability?.recordId?.let { capabilityId -> put("capabilityId", capabilityId) }
                if (declaration["deprecated"] == true) put("deprecated", true)
                declaration["receiver"]?.let { receiver -> put("receiver", receiver) }
                put("catalogOwner", group)
            }
        }.sortedWith(
            compareBy<Map<String, Any?>>(
                { entry -> referenceGroups.indexOf(entry.getValue("catalogOwner").toString()) },
                { entry -> entry.getValue("artifact").toString() },
                { entry -> entry.getValue("symbol").toString() },
            ),
        )
        val groupedEntries = entries.groupBy { entry -> entry.getValue("catalogOwner").toString() }
        val groups = referenceGroups.mapNotNull { groupId ->
            groupedEntries[groupId]?.let { matching ->
                linkedMapOf<String, Any?>(
                    "entries" to matching.map { entry -> entry - "catalogOwner" },
                    "entryCount" to matching.size,
                    "groupId" to groupId,
                )
            }
        }
        val countsByKind = entries.groupingBy { entry -> entry.getValue("kind").toString() }
            .eachCount()
            .toSortedMap()

        return linkedMapOf(
            "artifacts" to referenceArtifacts,
            "capabilities" to referenceCapabilities,
            "documents" to referenceDocuments,
            "generatedBy" to "updateDocumentationCapabilityReference",
            "groups" to groups,
            "schemaVersion" to 3,
            "summary" to linkedMapOf(
                "artifactCount" to referenceArtifacts.size,
                "countsByKind" to countsByKind,
                "entryCount" to entries.size,
                "groupCount" to groups.size,
                "ownedEntryCount" to entries.count { entry -> entry["capabilityId"] != null },
                "sourceFingerprint" to sha256(
                    declarations.joinToString("\u0000") { declaration ->
                        declaration.getValue("symbol").toString() + "\u0000" +
                            StableJson.encode(declaration.getValue("signatureHashes"))
                    },
                ),
            ),
        )
    }

    private fun referenceGroup(declaration: Map<String, Any?>): String {
        val kind = declaration.getValue("kind").toString()
        val searchable = listOfNotNull(
            declaration["artifact"],
            declaration["sourcePackage"],
            declaration["receiver"],
            declaration["symbol"],
        ).joinToString(" ").lowercase()
        return when {
            kind == "tooling" -> "tooling"
            kind == "integration" -> "integrations"
            kind == "host" -> "android-interop"
            kind in setOf("modifier", "dsl") &&
                referenceAndroidInterop.containsMatchIn(searchable) -> "android-interop"
            referenceAnimation.containsMatchIn(searchable) -> "animation"
            referenceGesture.containsMatchIn(searchable) -> "gesture"
            referenceDesignSystem.containsMatchIn(searchable) -> "design-systems"
            referenceCollection.containsMatchIn(searchable) -> "collections"
            referenceNavigation.containsMatchIn(searchable) -> "navigation"
            referenceFeedback.containsMatchIn(searchable) -> "feedback"
            referenceAction.containsMatchIn(searchable) -> "actions"
            referenceInput.containsMatchIn(searchable) -> "input"
            referenceAppearance.containsMatchIn(searchable) -> "appearance"
            referenceLayout.containsMatchIn(searchable) -> "layout"
            referenceContent.containsMatchIn(searchable) -> "content"
            else -> "general"
        }
    }

    private val referenceGroups = listOf(
        "layout",
        "appearance",
        "input",
        "gesture",
        "animation",
        "content",
        "actions",
        "collections",
        "feedback",
        "navigation",
        "design-systems",
        "integrations",
        "android-interop",
        "tooling",
        "general",
    )
    private val referenceDocumentTypes = listOf(
        "tutorial",
        "guide",
        "architecture",
        "module",
        "tooling",
        "migration",
    )
    private val referenceAndroidInterop = Regex("android|nativeview|native-view|viewfactory|viewinterop")
    private val referenceAnimation = Regex("animat|transition|shared.?content|keyframe|spring|tween")
    private val referenceGesture = Regex("gesture|drag|pointer|nested.?scroll|fling|swipe|transformable")
    private val referenceDesignSystem = Regex("material3|fluent2|cupertino|design.?system")
    private val referenceCollection = Regex("lazy|collection|paging|pager|grid|recycler")
    private val referenceNavigation = Regex("navigation|navigator|navhost|route|scaffold|tabrow|tab-row")
    private val referenceFeedback = Regex("progress|snackbar|toast|dialog|sheet|tooltip|popup|badge")
    private val referenceAction = Regex("button|chip|fab|action|clickable|toggle")
    private val referenceInput = Regex("textfield|text-field|checkbox|switch|slider|radio|search|focus|keyevent|key-event|semantics|testtag|test-tag|click")
    private val referenceAppearance = Regex("background|border|clip|alpha|shape|visible|visibility|layer|graphics|draw|shadow|elevation|color|brush")
    private val referenceLayout = Regex("layout|width|height|size|padding|margin|offset|align|weight|fill|wrap|inset|position|constraint|aspect|spacer|divider|row|column|box|flow")
    private val referenceContent = Regex("text|image|icon|surface|card|content|richtext|rich-text")

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
    ): List<Map<String, Any?>> = discoverCapabilityDeclarations(
        sourceFiles = sourceSetDirectories
            .asSequence()
            .filter(File::isDirectory)
            .filter { sourceRoot -> sourceRoot.parentFile.name in activeArtifacts }
            .flatMap { sourceRoot ->
                sourceRoot.resolve("main").takeIf(File::isDirectory)
                    ?.walkTopDown()
                    ?.filter(File::isFile)
                    ?.filter { file -> file.extension == "kt" }
                    ?.map { file ->
                        GovernanceSourceFile(
                            path = file.relativePathWithin(repository),
                            source = file.readText(),
                        )
                    }
                    ?: emptySequence()
            }
            .toList(),
        activeArtifacts = activeArtifacts,
        moduleFamilies = moduleFamilies,
    )

    private fun discoverCapabilityDeclarations(
        sourceFiles: List<GovernanceSourceFile>,
        activeArtifacts: Set<String>,
        moduleFamilies: Map<String, String>,
    ): List<Map<String, Any?>> = sourceFiles
        .asSequence()
        .filter { sourceFile -> sourceFile.path.substringBefore('/') in activeArtifacts }
        .filter { sourceFile -> "/src/main/" in "/${sourceFile.path}" }
        .filter { sourceFile -> sourceFile.path.endsWith(".kt") }
        .flatMap { sourceFile ->
            val source = sourceFile.source
            val packageName = packageDeclaration.find(source)?.groupValues?.get(1).orEmpty()
            if (packageName == "internal" || ".internal." in ".$packageName.") {
                return@flatMap emptySequence()
            }
            val artifact = sourceFile.path.substringBefore('/')
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
                val path = sourceFile.path
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
                    signatureHash = sha256(
                        declarationMetadata(source, match.range.first) + "\u0000" +
                            declarationSignature(source, sanitized, match.range.first),
                    ),
                    deprecated = hasDeprecatedAnnotation(source, match.range.first),
                    visibility = if ("protected" in modifiers) "protected" else "public",
                )
            }
            if (
                family == "Integration" ||
                family == "Preview tooling" ||
                artifact == "viewcompose-host-android" ||
                artifact == "viewcompose-renderer-android" ||
                artifact == "viewcompose-ui-foundation"
            ) {
                topLevelCallableDeclaration.findAll(sanitized).forEach declaration@{ match ->
                    if (braceDepths[match.range.first] != 0) return@declaration
                    val modifiers = match.groupValues[1].trim().split(Regex("\\s+"))
                        .filter(String::isNotEmpty)
                        .toSet()
                    if ("private" in modifiers || "internal" in modifiers) return@declaration
                    val name = match.groupValues[2]
                    if (functionDeclaration.find(match.value) != null) return@declaration
                    if (!isApplicationEntry(artifact, family, packageName, name)) return@declaration
                    val path = sourceFile.path
                    entries += CapabilityDeclaration(
                        artifact = artifact,
                        kind = classifyApplicationEntry(
                            artifact = artifact,
                            family = family,
                            packageName = packageName,
                            name = name,
                        ),
                        line = source.lineNumberAt(match.range.first),
                        path = path,
                        receiver = null,
                        sourcePackage = packageName,
                        symbol = "$packageName.$name",
                        signatureHash = sha256(
                            declarationMetadata(source, match.range.first) + "\u0000" +
                                declarationSignature(source, sanitized, match.range.first),
                        ),
                        deprecated = hasDeprecatedAnnotation(source, match.range.first),
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
                    val path = sourceFile.path
                    entries += CapabilityDeclaration(
                        artifact = artifact,
                        kind = classifyApplicationEntry(
                            artifact = artifact,
                            family = family,
                            packageName = packageName,
                            name = name,
                        ),
                        line = source.lineNumberAt(match.range.first),
                        path = path,
                        receiver = null,
                        sourcePackage = packageName,
                        symbol = "$packageName.$name",
                        signatureHash = sha256(
                            declarationMetadata(source, match.range.first) + "\u0000" +
                                typeDeclarationSignature(source, sanitized, match.range.first),
                        ),
                        deprecated = hasDeprecatedAnnotation(source, match.range.first),
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
                "signatureHashes" to overloads.map(CapabilityDeclaration::signatureHash)
                    .distinct()
                    .sorted(),
                "deprecated" to overloads.any(CapabilityDeclaration::deprecated),
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
                "slug" to metadata["slug"],
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

    private fun Map<String, Any?>.referenceRoute(): String? {
        val declaredSlug = this["slug"]?.toString()?.takeIf { slug -> slug.startsWith('/') }
        if (declaredSlug != null) return declaredSlug

        val sourcePath = this["path"]?.toString() ?: return null
        val route = sourcePath.removePrefix("docs/")
            .removeSuffix(".md")
            .removeSuffix(".mdx")
            .removeSuffix("/README")
            .removeSuffix("/index")
        return "/$route"
    }

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
        """(?m)^\s*((?:(?:public|protected|private|internal|abstract|open|data|sealed|enum|annotation|value|fun|actual|expect)\s+)*)(class|interface|object|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)""",
    )
    private val rendererPublicCapabilityPackages = setOf(
        "com.viewcompose.renderer.decoration",
        "com.viewcompose.renderer.reconcile",
        "com.viewcompose.renderer.view.tree",
    )
    private val rendererToolingEntryNames = setOf(
        "LayoutPassEntry",
        "LayoutPassSnapshot",
        "LayoutPassTracker",
        "NodeTypeBindingStats",
        "RenderPatchOperation",
        "RenderPatchRecord",
        "RenderStats",
        "RenderStructureStats",
        "RenderTreeNode",
        "RenderTreeTimingCollector",
        "RenderTreeTimingPhase",
        "RenderTreeTimingSpan",
        "RenderTreeTimingSubject",
        "ReuseBindingResult",
        "ViewNodeToolingRegistry",
    )
    private val foundationDiagnosticsEntryNames = setOf(
        "NodeTypeBindingStats",
        "RenderDiagnosticCollection",
        "RenderDiagnosticContext",
        "RenderDiagnosticEvent",
        "RenderDiagnostics",
        "RenderDiagnosticsSink",
        "RenderFailure",
        "RenderFailureObserved",
        "RenderFailureOperation",
        "RenderFailurePhase",
        "RenderFailureRecovery",
        "RenderFrameCompleted",
        "RenderFrameDiagnosticLevel",
        "RenderFrameReport",
        "RenderFrameStatus",
        "RenderInspectedNode",
        "RenderInspectedNodeKind",
        "RenderNodeInspectionSnapshot",
        "RenderNodePlatformTarget",
        "RenderNodeTimingCapture",
        "RenderNodeTimingCaptureRequest",
        "RenderNodeTimingCaptureResult",
        "RenderNodeTimingCaptureStart",
        "RenderNodeTimingClock",
        "RenderNodeTimingEndReason",
        "RenderNodeTimingInclusion",
        "RenderNodeTimingPhase",
        "RenderNodeTimingRecord",
        "RenderNodeTimingStartStatus",
        "RenderNodeTimingUnsupportedDomain",
        "RenderNodeToken",
        "RenderPatchOperation",
        "RenderPatchRecord",
        "RenderSessionActivityChanged",
        "RenderSessionDiagnosticInspection",
        "RenderSessionDiagnosticSnapshot",
        "RenderSessionEnded",
        "RenderSessionInspectedFailure",
        "RenderSessionInspectedFrame",
        "RenderSessionInspectionPolicy",
        "RenderSessionInspectionRegistration",
        "RenderSessionInspectionTooling",
        "RenderSessionNodeInspection",
        "RenderSessionPlatformDiagnostics",
        "RenderSessionRole",
        "RenderSessionStarted",
        "RenderSessionTimingInspection",
        "RenderSessionTraceId",
        "RenderStats",
        "RenderStructureStats",
        "RenderTreeNode",
        "RenderTreeResult",
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

    private fun declarationSignature(source: String, sanitized: String, start: Int): String {
        val opening = sanitized.indexOf('(', start).takeIf { index -> index >= 0 }
            ?: return source.substring(start).lineSequence().firstOrNull().orEmpty()
                .canonicalPublicSignature()
        var parenthesisDepth = 0
        var index = opening
        while (index < sanitized.length) {
            when (sanitized[index]) {
                '(' -> parenthesisDepth += 1
                ')' -> {
                    parenthesisDepth -= 1
                    if (parenthesisDepth == 0) {
                        index += 1
                        break
                    }
                }
            }
            index += 1
        }
        while (index < sanitized.length) {
            when (sanitized[index]) {
                '{', '=' -> break
                '\n' -> {
                    val prefix = sanitized.substring(start, index).trimEnd()
                    if (!prefix.endsWith(":") && !prefix.endsWith(",")) break
                }
            }
            index += 1
        }
        return source.substring(start, index).canonicalPublicSignature()
    }

    private fun typeDeclarationSignature(source: String, sanitized: String, start: Int): String {
        val lineEnd = sanitized.indexOf('\n', start).takeIf { index -> index >= 0 } ?: sanitized.length
        val firstLine = sanitized.substring(start, lineEnd)
        if (Regex("""\btypealias\b""").containsMatchIn(firstLine)) {
            return source.substring(start, lineEnd).canonicalPublicSignature()
        }
        val paragraphEnd = sanitized.indexOf("\n\n", start).takeIf { index -> index >= 0 }
        val bodyStart = sanitized.indexOf('{', start).takeIf { index ->
            index >= 0 && (paragraphEnd == null || index < paragraphEnd)
        }
        val end = bodyStart ?: lineEnd
        return source.substring(start, end).canonicalPublicSignature()
    }

    private fun String.normalizedSignature(): String = trim().replace(Regex("\\s+"), " ")

    private fun String.canonicalPublicSignature(): String =
        normalizedSignature().removePrefix("public ")

    private fun declarationMetadata(source: String, declarationStart: Int): String =
        adjacentDeclarationMetadata(source, declarationStart).lines().let { lines ->
            val annotationStart = lines.indexOfFirst { line -> line.trimStart().startsWith('@') }
            if (annotationStart < 0) "" else lines.drop(annotationStart).joinToString(" ")
        }.normalizedSignature()

    private fun adjacentDeclarationMetadata(source: String, declarationStart: Int): String {
        val prefix = source.substring(maxOf(0, declarationStart - 2_048), declarationStart)
        return prefix.substringAfterLast("\n\n")
    }

    private fun hasDeprecatedAnnotation(source: String, declarationStart: Int): Boolean {
        return Regex("""@(?:kotlin\.)?Deprecated\b""")
            .containsMatchIn(adjacentDeclarationMetadata(source, declarationStart))
    }

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
        artifact == "viewcompose-renderer-android" -> packageName in rendererPublicCapabilityPackages
        artifact == "viewcompose-ui-foundation" -> name in foundationDiagnosticsEntryNames
        family == "Integration" -> true
        else -> false
    }

    private fun classifyApplicationEntry(
        artifact: String,
        family: String,
        packageName: String,
        name: String,
    ): String = when {
        family == "Preview tooling" -> "tooling"
        artifact == "viewcompose-host-android" -> "host"
        artifact == "viewcompose-renderer-android" &&
            packageName == "com.viewcompose.renderer.decoration" -> "integration"
        artifact == "viewcompose-renderer-android" && name in rendererToolingEntryNames -> "tooling"
        artifact == "viewcompose-renderer-android" -> "host"
        artifact == "viewcompose-ui-foundation" -> "tooling"
        else -> "integration"
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
        val expected = source.substring(start, end).trimIndent().trim()
        return if (content.trimIndent().trim() == expected) {
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

    private fun strictViolations(
        issues: List<GovernanceIssue>,
        records: List<GovernanceRecord>,
    ): List<String> = buildList {
        records.filter { record -> record.contractId == "exception" }.forEach { record ->
            add(
                "${record.path} is a forbidden debt exception; strict mode requires zero exception records",
            )
        }
        issues.forEach { issue ->
            add("${issue.id} is blocking: ${issue.target} (${issue.category})")
        }
    }.distinct().sorted()

    private fun humanReport(
        issues: List<GovernanceIssue>,
        declarationCount: Int,
        documentCount: Int,
        fenceCount: Int,
        publicApiChanges: List<DocumentationGovernanceV2PublicApiChange>,
        contractViolations: List<String>,
        strictViolations: List<String>,
    ): String = buildString {
        appendLine("Documentation Governance V2 — strict gate")
        appendLine("Inventory: $declarationCount production entries; $documentCount public pages; $fenceCount executable fences")
        appendLine("Public API changes: ${publicApiChanges.size}")
        publicApiChanges.forEach { change ->
            val previous = change.previousSymbol?.let { symbol -> " (from $symbol)" }.orEmpty()
            appendLine("- ${change.change} ${change.artifact}:${change.symbol}$previous")
        }
        appendLine("Issues: ${issues.size}; exception records: 0 required")
        val blockingViolationCount = contractViolations.size + strictViolations.size
        appendLine("Gate: ${if (blockingViolationCount == 0) "passed" else "failed"}; violations: $blockingViolationCount")
        if (contractViolations.isNotEmpty()) {
            appendLine()
            appendLine("[contract-violations] ${contractViolations.size}")
            contractViolations.forEach { violation -> appendLine("- $violation") }
        }
        if (strictViolations.isNotEmpty()) {
            appendLine()
            appendLine("[strict-violations] ${strictViolations.size}")
            strictViolations.forEach { violation -> appendLine("- $violation") }
        }
        issueCategories.forEach { category ->
            val matching = issues.filter { issue -> issue.category == category }
            appendLine()
            appendLine("[$category] ${matching.size}")
            matching.forEach { issue ->
                appendLine("- ${issue.id} ${issue.target}: ${issue.detail}")
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
    val addedImpactPaths: Set<String>,
    val changedSourceFiles: List<DocumentationGovernanceV2SourceChange>,
)

internal data class DocumentationGovernanceV2PublicApiChange(
    val artifact: String,
    val change: String,
    val previousSymbol: String?,
    val symbol: String,
) {
    fun report(): Map<String, Any?> = linkedMapOf(
        "artifact" to artifact,
        "change" to change,
        "previousSymbol" to previousSymbol,
        "symbol" to symbol,
    )
}

/** Correlates source-derived capability-surface changes with immutable ownership evidence. */
internal object DocumentationGovernanceV2PublicApiChanges {
    fun detect(
        baseDeclarations: List<Map<String, Any?>>,
        currentDeclarations: List<Map<String, Any?>>,
        currentInventory: List<Map<String, Any?>> = currentDeclarations,
    ): List<DocumentationGovernanceV2PublicApiChange> {
        val base = baseDeclarations.associateBy { declaration ->
            declaration.getValue("symbol").toString()
        }
        val current = currentDeclarations.associateBy { declaration ->
            declaration.getValue("symbol").toString()
        }
        val completeCurrent = currentInventory.associateBy { declaration ->
            declaration.getValue("symbol").toString()
        }
        val direct = (base.keys + current.keys).sorted().mapNotNull { symbol ->
            val previous = base[symbol]
            val next = current[symbol]
            when {
                previous == null && next != null -> next.toChange(
                    if (completeCurrent.getValue(symbol).overloadCount() > next.overloadCount()) {
                        "changed"
                    } else {
                        "added"
                    },
                )
                previous != null && next == null -> previous.toChange(
                    if (symbol in completeCurrent) "changed" else "deleted",
                )
                previous != null && next != null &&
                    previous["deprecated"] != true && next["deprecated"] == true ->
                    next.toChange("deprecated")
                previous != null && next != null &&
                    previous["artifact"] != next["artifact"] &&
                    previous["signatureHashes"] == next["signatureHashes"] ->
                    DocumentationGovernanceV2PublicApiChange(
                        artifact = next.getValue("artifact").toString(),
                        change = "moved",
                        previousSymbol = symbol,
                        symbol = symbol,
                    )
                previous != null && next != null &&
                    (previous["artifact"] != next["artifact"] ||
                        previous["signatureHashes"] != next["signatureHashes"]) ->
                    next.toChange("changed")
                else -> null
            }
        }.toMutableList()

        val additions = direct.filter { change -> change.change == "added" }
        val deletions = direct.filter { change -> change.change == "deleted" }
        val movePairs = deletions.mapNotNull { deletion ->
            val previousDeclaration = base.getValue(deletion.symbol)
            val candidates = additions.filter { addition ->
                val currentDeclaration = current.getValue(addition.symbol)
                deletion.symbol.substringAfterLast('.') == addition.symbol.substringAfterLast('.') &&
                    previousDeclaration["signatureHashes"] == currentDeclaration["signatureHashes"]
            }
            candidates.singleOrNull()?.let { addition -> deletion to addition }
        }.filter { (_, addition) ->
            deletions.count { deletion ->
                val previousDeclaration = base.getValue(deletion.symbol)
                deletion.symbol.substringAfterLast('.') == addition.symbol.substringAfterLast('.') &&
                    previousDeclaration["signatureHashes"] ==
                    current.getValue(addition.symbol)["signatureHashes"]
            } == 1
        }
        movePairs.forEach { (deletion, addition) ->
            direct.remove(deletion)
            direct.remove(addition)
            direct += DocumentationGovernanceV2PublicApiChange(
                artifact = addition.artifact,
                change = "moved",
                previousSymbol = deletion.symbol,
                symbol = addition.symbol,
            )
        }
        return direct.sortedWith(compareBy({ it.artifact }, { it.symbol }, { it.change }))
    }

    fun verifyImpacts(
        changes: List<DocumentationGovernanceV2PublicApiChange>,
        addedImpactPaths: Set<String>,
        records: List<GovernanceRecord>,
    ): List<String> {
        val impacts = records.filter { record ->
            record.path in addedImpactPaths && record.contractId == "capability-impact" && record.valid
        }
        val capabilities = records.filter { record ->
            record.contractId == "capability" && record.valid
        }
        val samples = records.filter { record ->
            record.contractId == "sample" && record.valid
        }.associateBy(GovernanceRecord::recordId)
        return buildList {
            changes.forEach { change ->
                val matching = impacts.filter { impact -> impact.matches(change) }
                if (matching.size != 1) {
                    add(
                        "${change.artifact}:${change.symbol} ${change.change} public API change " +
                            "requires exactly one newly added matching capability-impact record; " +
                            "found ${matching.size}",
                    )
                    return@forEach
                }
                val impact = matching.single()
                val capabilityId = impact.value["capability_id"]?.toString().orEmpty()
                val owners = capabilities.filter { capability ->
                    capability.recordId == capabilityId
                }
                if (owners.size != 1) {
                    add(
                        "${impact.path} references capability '$capabilityId', but exactly one valid " +
                            "capability owner is required; found ${owners.size}",
                    )
                    return@forEach
                }
                val owner = owners.single()
                if (owner.value["artifact"]?.toString() != change.artifact) {
                    add("${impact.path} capability '$capabilityId' does not own artifact ${change.artifact}")
                }
                if (!owner.ownsSymbol(change.symbol)) {
                    add("${impact.path} capability '$capabilityId' does not own symbol ${change.symbol}")
                }
                @Suppress("UNCHECKED_CAST")
                val sampleOwner = owner.value["sample_owner"] as? Map<String, Any?>
                val sampleId = sampleOwner?.get("sample_id")?.toString()
                when {
                    sampleId != null && sampleId !in samples ->
                        add("${owner.path} references missing valid sample '$sampleId'")
                    sampleId != null &&
                        samples.getValue(sampleId).value["capability_id"]?.toString() != capabilityId ->
                        add("${owner.path} sample '$sampleId' belongs to a different capability")
                }
            }
            impacts.forEach { impact ->
                val matching = changes.filter { change -> impact.matches(change) }
                if (matching.size != 1) {
                    add(
                        "${impact.path} must classify exactly one detected public API change; " +
                            "found ${matching.size}",
                    )
                }
            }
        }.distinct().sorted()
    }

    private fun Map<String, Any?>.toChange(change: String) =
        DocumentationGovernanceV2PublicApiChange(
            artifact = getValue("artifact").toString(),
            change = change,
            previousSymbol = null,
            symbol = getValue("symbol").toString(),
        )

    private fun Map<String, Any?>.overloadCount(): Int =
        (get("overloadCount") as? Number)?.toInt()
            ?: (get("signatureHashes") as? List<*>)?.size
            ?: 0

    private fun GovernanceRecord.matches(change: DocumentationGovernanceV2PublicApiChange): Boolean =
        value["artifact"]?.toString() == change.artifact &&
            value["symbol_id"]?.toString() == change.symbol &&
            value["change"]?.toString() == change.change

    private fun GovernanceRecord.ownsSymbol(symbol: String): Boolean =
        value.listOfObjects("symbols").any { owned -> owned["symbol_id"]?.toString() == symbol }
}

internal data class DocumentationGovernanceV2GitCommandResult(
    val exitCode: Int,
    val output: String,
)

internal fun interface DocumentationGovernanceV2GitCommandExecutor {
    fun execute(arguments: List<String>): DocumentationGovernanceV2GitCommandResult
}

internal object DocumentationGovernanceV2GitPolicy {
    private const val exceptionRoot =
        "docs/project/records/documentation-governance-v2/exceptions"
    private const val impactRoot =
        "docs/project/records/documentation-governance-v2/impacts"

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
        ).lineSequence().filter(String::isNotBlank).map(::parseChange).toMutableList()
        git.execute(
            "ls-files",
            "--others",
            "--exclude-standard",
            "--",
        ).lineSequence().filter(String::isNotBlank).forEach { path ->
            changes += GitFileChange(
                status = 'A',
                oldPath = null,
                newPath = path.replace('\\', '/'),
            )
        }
        val exceptionChanges = changes.filter { change ->
            change.paths.any { path -> path.startsWith("$exceptionRoot/") }
        }
        val violations = exceptionChanges.distinct().sortedBy { change -> change.displayPath }.mapNotNull { change ->
            when (change.status) {
                'D' -> null
                else ->
                    "${change.displayPath} retains or introduces a debt exception; strict mode only permits deletion"
            }
        }.toMutableList()
        val impactChanges = changes.filter { change ->
            change.paths.any { path -> path.startsWith("$impactRoot/") && path.endsWith(".json") }
        }
        impactChanges.filter { change -> change.status != 'A' }.forEach { change ->
            violations +=
                "${change.displayPath} mutates an immutable capability-impact record; add a new record instead"
        }
        val addedImpactPaths = impactChanges.filter { change -> change.status == 'A' }
            .mapNotNull(GitFileChange::newPath)
            .toSet()
        val sourceChanges = changes.filter { change ->
            change.paths.any { path -> path.isPublishedKotlinSource() }
        }.map { change ->
            DocumentationGovernanceV2SourceChange(
                basePath = change.oldPath?.takeIf { path -> path.isPublishedKotlinSource() },
                baseSource = change.oldPath
                    ?.takeIf { path -> path.isPublishedKotlinSource() }
                    ?.let { path -> git.execute("show", "$base:$path") },
                currentPath = change.newPath?.takeIf { path -> path.isPublishedKotlinSource() },
                currentSource = change.newPath
                    ?.takeIf { path -> path.isPublishedKotlinSource() }
                    ?.let { path -> repository.resolve(path).takeIf(File::isFile)?.readText() },
            )
        }
        return DocumentationGovernanceV2MutationAudit(
            verificationBase = base,
            violations = violations.sorted(),
            addedImpactPaths = addedImpactPaths,
            changedSourceFiles = sourceChanges,
        )
    }

    private fun parseChange(line: String): GitFileChange {
        val fields = line.split('\t')
        val status = fields.first().first()
        val paths = fields.drop(1).map { path -> path.replace('\\', '/') }
        return when (status) {
            'R', 'C' -> GitFileChange(status, paths.getOrNull(0), paths.getOrNull(1))
            'A' -> GitFileChange(status, null, paths.singleOrNull())
            'D' -> GitFileChange(status, paths.singleOrNull(), null)
            else -> GitFileChange(status, paths.singleOrNull(), paths.singleOrNull())
        }
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

    private data class GitFileChange(
        val status: Char,
        val oldPath: String?,
        val newPath: String?,
    ) {
        val paths: List<String> get() = listOfNotNull(oldPath, newPath).distinct()
        val displayPath: String get() = newPath ?: oldPath.orEmpty()
    }

    private fun String.isPublishedKotlinSource(): Boolean =
        startsWith("viewcompose-") && "/src/main/" in "/$this" && endsWith(".kt")

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
    val deprecated: Boolean,
    val kind: String,
    val line: Int,
    val path: String,
    val receiver: String?,
    val signatureHash: String,
    val sourcePackage: String,
    val symbol: String,
    val visibility: String,
)

private data class GovernanceSourceFile(
    val path: String,
    val source: String,
)

internal data class GovernanceRecord(
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
) {
    fun toReportMap(): Map<String, Any?> = linkedMapOf(
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
            identitySuffix: String = target,
        ): GovernanceIssue = create(category, target, detail, identitySuffix)

        fun symbol(
            category: String,
            target: String,
            detail: String,
            identitySuffix: String = target,
        ): GovernanceIssue = create(category, target, detail, identitySuffix)

        private fun create(
            category: String,
            target: String,
            detail: String,
            identitySuffix: String,
        ): GovernanceIssue = GovernanceIssue(
            id = "gov2-${sha256("$category\u0000$identitySuffix").take(16)}",
            category = category,
            target = target,
            detail = detail,
        )
    }
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
