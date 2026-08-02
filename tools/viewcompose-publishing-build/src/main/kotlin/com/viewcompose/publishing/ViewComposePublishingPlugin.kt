package com.viewcompose.publishing

import com.android.build.api.dsl.LibraryExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import java.io.File
import java.util.Properties
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.plugins.signing.SigningExtension
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

class ViewComposePublishingRootPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        check(project == project.rootProject) {
            "com.viewcompose.publishing.root must be applied to the root project."
        }
        val metadata = PublishingMetadata.load(project)
        val publishedProjects = metadata.moduleVersions.map { (module, _) ->
            project.findProject(":$module")
                ?: throw GradleException(
                    "Published module '$module' is not included in settings.gradle.kts.",
                )
        }

        publishedProjects.forEach { publishedProject ->
            publishedProject.pluginManager.apply(ViewComposeLibraryPublishingPlugin::class.java)
        }

        val documentationModules = project.providers.gradleProperty("viewComposeDocsModules")
            .map { value -> value.split(',').map(String::trim).filter(String::isNotEmpty) }
            .orElse(metadata.moduleVersions.keys.sorted())
        val verifyDocumentationSelection =
            project.tasks.register<VerifyPublishingSelectionTask>(
                "verifyViewComposeDocumentationModules",
            ) {
                group = "documentation"
                description =
                    "Validates -PviewComposeDocsModules before generating API documentation."
                modules.set(documentationModules)
                availableModules.set(metadata.moduleVersions.keys.sorted())
            }
        val apiOutputDirectory = project.layout.projectDirectory.dir("website/generated/api")
        val assembleApiDocs = project.tasks.register<Sync>("assembleViewComposeApiDocs") {
            group = "documentation"
            description =
                "Generates versioned Dokka HTML for all published modules or " +
                    "-PviewComposeDocsModules."
            dependsOn(verifyDocumentationSelection)
            val selectedModules = documentationModules.get()
            selectedModules.forEach { module ->
                val publishedProject = requireNotNull(project.findProject(":$module"))
                val version = requireNotNull(metadata.moduleVersions[module])
                dependsOn("${publishedProject.path}:dokkaGeneratePublicationHtml")
                from(publishedProject.layout.buildDirectory.dir("dokka/html")) {
                    into("$module/$version")
                }
            }
            into(apiOutputDirectory)
            doLast {
                val outputRoot = apiOutputDirectory.asFile
                val manifest =
                    selectedModules.sorted().joinToString(
                        prefix = "[\n",
                        postfix = "\n]\n",
                        separator = ",\n",
                    ) { module ->
                        val version = requireNotNull(metadata.moduleVersions[module])
                        "  {\"artifact\":\"$module\",\"version\":\"$version\"}"
                    }
                outputRoot.resolve("manifest.json").writeText(manifest)
                selectedModules.forEach { module ->
                    val version = requireNotNull(metadata.moduleVersions[module])
                    writeApiRedirect(
                        directory = outputRoot.resolve("$module/current"),
                        module = module,
                        version = version,
                    )
                    if (version.isStableRelease()) {
                        writeApiRedirect(
                            directory = outputRoot.resolve("$module/latest"),
                            module = module,
                            version = version,
                        )
                    }
                }
            }
        }
        project.tasks.register("auditViewComposeApiDocs") {
            group = "documentation"
            description =
                "Generates selected API docs and reports undocumented public/protected APIs."
            dependsOn(assembleApiDocs)
        }

        val localRepository = project.layout.buildDirectory.dir("maven-repository")
        val verifyConfiguration =
            project.tasks.register<VerifyPublishingConfigurationTask>(
                "verifyViewComposePublishingConfiguration",
            ) {
                group = "publishing"
                description =
                    "Verifies formal coordinates and independent versions for every published module."
                mavenGroup.set(metadata.groupId)
                moduleVersions.set(metadata.moduleVersions)
                strictApiDocsModules.set(metadata.strictApiDocsModules)
            }
        val publishLocal = project.tasks.register("publishViewComposeToLocalRepository") {
            group = "publishing"
            description =
                "Publishes every ViewCompose artifact to build/maven-repository."
            dependsOn(verifyConfiguration)
            dependsOn(
                publishedProjects.map { publishedProject ->
                    "${publishedProject.path}:publishAllPublicationsToViewComposeLocalRepository"
                },
            )
        }
        val selectedModules = project.providers.gradleProperty("viewComposePublishModules")
            .map { value -> value.split(',').map(String::trim).filter(String::isNotEmpty) }
            .orElse(emptyList())
        val verifySelection = project.tasks.register<VerifyPublishingSelectionTask>(
            "verifySelectedViewComposeModules",
        ) {
            group = "publishing"
            description = "Validates -PviewComposePublishModules before a selective publication."
            modules.set(selectedModules)
            availableModules.set(metadata.moduleVersions.keys.sorted())
        }
        val verifyCentralSelection = project.tasks.register<VerifyCentralPublishingSelectionTask>(
            "verifySelectedViewComposeCentralRelease",
        ) {
            group = "publishing"
            description =
                "Requires explicitly selected stable module versions before a Central upload."
            modules.set(selectedModules)
            moduleVersions.set(metadata.moduleVersions)
        }
        val publishSelectedLocal =
            project.tasks.register("publishSelectedViewComposeToLocalRepository") {
            group = "publishing"
            description =
                "Publishes only -PviewComposePublishModules to the generated local Maven repository."
            dependsOn(verifyConfiguration, verifySelection)
            dependsOn(
                selectedModules.get().map { module ->
                    ":$module:publishAllPublicationsToViewComposeLocalRepository"
                },
            )
        }
        val publishCentral = project.tasks.register("publishSelectedViewComposeToMavenCentral") {
            group = "publishing"
            description =
                "Uploads selected stable artifacts to a manual Central Portal deployment."
            dependsOn(verifyConfiguration, verifySelection, verifyCentralSelection)
            dependsOn(
                selectedModules.get().map { module ->
                    ":$module:publishAllPublicationsToMavenCentralRepository"
                },
            )
        }
        publishedProjects.forEach { publishedProject ->
            publishedProject.tasks.matching { task ->
                task.name.endsWith("ToMavenCentralRepository")
            }.configureEach {
                dependsOn(verifyConfiguration, verifySelection, verifyCentralSelection)
            }
            publishedProject.tasks.named("publishAndReleaseToMavenCentral").configure {
                // A public Maven Central release is irreversible. Keep the plugin-provided shortcut
                // unavailable so every deployment is reviewed and released from Central Portal.
                setDependsOn(emptyList<Any>())
                doFirst {
                    throw GradleException(
                        "Automatic Maven Central release is disabled. Run " +
                            "${publishCentral.name}, review the deployment in Central Portal, " +
                            "then publish it explicitly.",
                    )
                }
            }
        }
        project.tasks.register<Delete>("cleanViewComposeLocalRepository") {
            group = "publishing"
            description = "Deletes the generated ViewCompose local Maven repository."
            delete(localRepository)
        }
        val inspectLocal = project.tasks.register<VerifyLocalRepositoryTask>(
            "inspectViewComposeLocalRepository",
        ) {
            group = "publishing"
            description =
                "Validates existing artifacts, POM metadata, sources, docs, and core dependencies."
            mustRunAfter(publishLocal)
            mavenGroup.set(metadata.groupId)
            modules.set(metadata.moduleVersions.keys.sorted())
            moduleVersions.set(metadata.moduleVersions)
            repositoryDirectory.set(localRepository)
        }
        val verifyLocal = project.tasks.register("verifyViewComposeLocalRepository") {
            group = "publishing"
            description =
                "Publishes and validates artifacts, POM metadata, sources, docs, and core dependencies."
            dependsOn(publishLocal, inspectLocal)
        }
        val inspectSelectedLocal = project.tasks.register<VerifyLocalRepositoryTask>(
            "inspectSelectedViewComposeLocalRepository",
        ) {
            group = "publishing"
            description =
                "Validates the selected artifacts in the generated local Maven repository."
            dependsOn(verifySelection)
            mustRunAfter(publishSelectedLocal)
            mavenGroup.set(metadata.groupId)
            modules.set(selectedModules)
            moduleVersions.set(metadata.moduleVersions)
            repositoryDirectory.set(localRepository)
        }
        project.tasks.register("verifySelectedViewComposeLocalRepository") {
            group = "publishing"
            description =
                "Publishes and validates only -PviewComposePublishModules in the local repository."
            dependsOn(publishSelectedLocal, inspectSelectedLocal)
        }
        project.tasks.register<GradleBuild>("verifyViewComposePublishedConsumption") {
            group = "publishing"
            description =
                "Builds isolated feature and core consumers using only the generated Maven repository."
            dependsOn(verifyLocal)
            dir = project.rootProject.file("tools/viewcompose-publishing-smoke")
            tasks = listOf("clean", "assemble")
        }
    }
}

private fun writeApiRedirect(
    directory: File,
    module: String,
    version: String,
) {
    directory.mkdirs()
    directory.resolve("index.html").writeText(
        """
        <!doctype html>
        <html lang="en">
          <head>
            <meta charset="utf-8">
            <meta http-equiv="refresh" content="0; url=../$version/">
            <link rel="canonical" href="../$version/">
            <title>ViewCompose $module API</title>
          </head>
          <body>
            <p><a href="../$version/">Open $module $version API reference</a></p>
          </body>
        </html>
        """.trimIndent() + "\n",
    )
}

private fun String.isStableRelease(): Boolean {
    val qualifier = lowercase()
    return listOf("-alpha", "-beta", "-rc", "-snapshot", "-dev", "-preview", "-eap")
        .none(qualifier::contains)
}

private class ViewComposeLibraryPublishingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val metadata = PublishingMetadata.load(project.rootProject)
        val moduleVersion = metadata.moduleVersions[project.name]
            ?: throw GradleException("No publication version registered for '${project.name}'.")
        project.group = metadata.groupId
        project.version = moduleVersion
        project.pluginManager.apply("org.jetbrains.dokka")
        project.pluginManager.apply("com.vanniktech.maven.publish.base")
        project.pluginManager.apply("signing")

        val auditTaskRequested = project.gradle.startParameter.taskNames.any { taskName ->
            taskName.substringAfterLast(':') == "auditViewComposeApiDocs"
        }
        val strictApiDocs = project.name in metadata.strictApiDocsModules
        val reportUndocumented = project.providers
            .gradleProperty("viewComposeApiDocsReportUndocumented")
            .map { value ->
                val requested = value.toBooleanStrictOrNull()
                    ?: throw GradleException(
                        "viewComposeApiDocsReportUndocumented must be true or false.",
                    )
                requested || strictApiDocs
            }
            .orElse(auditTaskRequested || strictApiDocs)
        val failOnWarning = project.providers
            .gradleProperty("viewComposeApiDocsFailOnWarning")
            .map { value ->
                val requested = value.toBooleanStrictOrNull()
                    ?: throw GradleException(
                        "viewComposeApiDocsFailOnWarning must be true or false.",
                    )
                requested || strictApiDocs
            }
            .orElse(strictApiDocs)
        project.extensions.getByType(DokkaExtension::class.java).apply {
            dokkaPublications.named("html") {
                this.failOnWarning.set(failOnWarning)
                suppressObviousFunctions.set(true)
            }
            dokkaSourceSets.configureEach {
                documentedVisibilities.set(
                    setOf(
                        VisibilityModifier.Public,
                        VisibilityModifier.Protected,
                    ),
                )
                this.reportUndocumented.set(
                    reportUndocumented.zip(failOnWarning) { report, strict -> report || strict },
                )
                skipDeprecated.set(false)
                suppressGeneratedFiles.set(true)
                samples.from(project.layout.projectDirectory.dir("src/test/samples"))
            }
        }

        project.extensions.getByType(MavenPublishBaseExtension::class.java)
            .publishToMavenCentral(automaticRelease = false)

        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        publishing.repositories.maven(Action {
            name = "viewComposeLocal"
            url = project.rootProject.layout.buildDirectory
                .dir("maven-repository")
                .get()
                .asFile
                .toURI()
        })

        project.pluginManager.withPlugin("com.android.library") {
            project.extensions.getByType(LibraryExtension::class.java).publishing {
                singleVariant("release") {
                    withSourcesJar()
                    withJavadocJar()
                }
            }
        }
        project.pluginManager.withPlugin("java") {
            project.extensions.getByType(JavaPluginExtension::class.java).apply {
                withSourcesJar()
                withJavadocJar()
            }
        }

        project.gradle.projectsEvaluated(Action {
            when {
                project.pluginManager.hasPlugin("com.android.library") -> {
                    publishing.publications.create<MavenPublication>("release") {
                        artifactId = project.name
                        from(project.components.getByName("release"))
                    }
                }
                project.pluginManager.hasPlugin("java-gradle-plugin") -> {
                    // java-gradle-plugin owns pluginMaven and marker publications.
                }
                project.pluginManager.hasPlugin("java") -> {
                    publishing.publications.create<MavenPublication>("mavenJava") {
                        artifactId = project.name
                        from(project.components.getByName("java"))
                    }
                }
                else -> throw GradleException(
                    "Published module '${project.path}' must apply an Android library or Java plugin.",
                )
            }

            publishing.publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set("ViewCompose $artifactId")
                    description.set(
                        "$artifactId module for the ViewCompose declarative Android View framework.",
                    )
                    url.set(metadata.projectUrl)
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/license/mit")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("viewcompose")
                            name.set("ViewCompose Contributors")
                            organization.set("ViewCompose")
                            organizationUrl.set(metadata.projectUrl)
                        }
                    }
                    scm {
                        connection.set(metadata.scmConnection)
                        developerConnection.set(metadata.scmDeveloperConnection)
                        url.set(metadata.scmUrl)
                    }
                    issueManagement {
                        system.set("GitHub Issues")
                        url.set("${metadata.projectUrl}/issues")
                    }
                }
            }

            val signing = project.extensions.getByType(SigningExtension::class.java)
            val signingKey = project.providers.gradleProperty("viewComposeSigningKey")
                .orElse(project.providers.environmentVariable("VIEWCOMPOSE_SIGNING_KEY"))
                .orNull
            val signingPassword = project.providers.gradleProperty("viewComposeSigningPassword")
                .orElse(project.providers.environmentVariable("VIEWCOMPOSE_SIGNING_PASSWORD"))
                .orNull
            if (!signingKey.isNullOrBlank()) {
                signing.useInMemoryPgpKeys(signingKey, signingPassword)
            } else {
                // Local releases use the OS GPG agent and pinentry. CI supplies the in-memory key
                // above, so neither workflow needs a private key file inside the repository.
                if (!project.hasProperty("signing.gnupg.executable")) {
                    project.extensions.extraProperties["signing.gnupg.executable"] = "gpg"
                }
                signing.useGpgCmd()
            }
            signing.isRequired = !moduleVersion.endsWith("-SNAPSHOT")
            signing.sign(publishing.publications)
        })
    }
}

abstract class VerifyPublishingConfigurationTask : DefaultTask() {
    @get:Input
    abstract val mavenGroup: Property<String>

    @get:Input
    abstract val moduleVersions: MapProperty<String, String>

    @get:Input
    abstract val strictApiDocsModules: ListProperty<String>

    @TaskAction
    fun verifyConfiguration() {
        val group = mavenGroup.get()
        check(GROUP_PATTERN.matches(group)) {
            "Maven group '$group' is not a formal reverse-domain coordinate."
        }
        check(!group.startsWith("io.github.")) {
            "Maven group '$group' must use the project identity, not a personal GitHub namespace."
        }
        val versions = moduleVersions.get()
        check(versions.isNotEmpty()) { "No ViewCompose publication modules are registered." }
        versions.forEach { (module, version) ->
            check(MODULE_PATTERN.matches(module)) { "Invalid artifact id '$module'." }
            check(VERSION_PATTERN.matches(version)) {
                "Module '$module' has invalid publication version '$version'."
            }
        }
        val unknownStrictModules = strictApiDocsModules.get().toSet() - versions.keys
        check(unknownStrictModules.isEmpty()) {
            "Unknown strict API documentation modules: ${unknownStrictModules.sorted().joinToString()}."
        }
    }
}

abstract class VerifyPublishingSelectionTask : DefaultTask() {
    @get:Input
    abstract val modules: ListProperty<String>

    @get:Input
    abstract val availableModules: ListProperty<String>

    @TaskAction
    fun verifySelection() {
        val selected = modules.get()
        check(selected.isNotEmpty()) {
            "Select at least one artifact with " +
                "-PviewComposePublishModules=viewcompose-runtime,viewcompose-navigation-core"
        }
        val available = availableModules.get().toSet()
        val unknown = selected.filterNot(available::contains)
        check(unknown.isEmpty()) {
            "Unknown ViewCompose publication modules: ${unknown.sorted().joinToString()}"
        }
    }
}

abstract class VerifyCentralPublishingSelectionTask : DefaultTask() {
    @get:Input
    abstract val modules: ListProperty<String>

    @get:Input
    abstract val moduleVersions: MapProperty<String, String>

    @TaskAction
    fun verifySelection() {
        val versions = moduleVersions.get()
        val selectedVersions = modules.get().associateWith { module ->
            checkNotNull(versions[module]) { "Unknown ViewCompose publication module '$module'." }
        }
        val snapshots = selectedVersions.filterValues { version ->
            version.endsWith("-SNAPSHOT")
        }
        check(snapshots.isEmpty()) {
            "Maven Central releases require stable versions. Snapshot selections: " +
                snapshots.entries.joinToString { (module, version) -> "$module:$version" }
        }
    }
}

abstract class VerifyLocalRepositoryTask : DefaultTask() {
    @get:Input
    abstract val mavenGroup: Property<String>

    @get:Input
    abstract val modules: ListProperty<String>

    @get:Input
    abstract val moduleVersions: MapProperty<String, String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val repositoryDirectory: DirectoryProperty

    @TaskAction
    fun verifyRepository() {
        val group = mavenGroup.get()
        val versions = moduleVersions.get()
        val selectedModules = modules.get().toSet()
        val groupDirectory = repositoryDirectory.get().asFile.resolve(group.replace('.', '/'))
        val failures = mutableListOf<String>()
        selectedModules.sorted().forEach { module ->
            val version = versions[module]
            if (version == null) {
                failures += "$module -> no registered publication version"
                return@forEach
            }
            val artifactDirectory = groupDirectory.resolve(module).resolve(version)
            val pomCandidates = artifactDirectory.listFiles()
                .orEmpty()
                .filter { file ->
                    file.isFile && file.name.startsWith("$module-") && file.name.endsWith(".pom")
                }
            if (pomCandidates.isEmpty()) {
                failures += "$module -> no published POM found"
                return@forEach
            }
            val prefix = pomCandidates.maxBy { file -> file.name }.name.removeSuffix(".pom")
            val pom = artifactDirectory.resolve("$prefix.pom")
            val sources = artifactDirectory.resolve("$prefix-sources.jar")
            val javadoc = artifactDirectory.resolve("$prefix-javadoc.jar")
            val gradleMetadata = artifactDirectory.resolve("$prefix.module")
            val primaryArtifacts = listOf(
                artifactDirectory.resolve("$prefix.aar"),
                artifactDirectory.resolve("$prefix.jar"),
            ).filter(File::isFile)
            listOf(pom, sources, javadoc).filterNot(File::isFile).forEach { missing ->
                failures += "$module -> missing ${missing.name}"
            }
            if (primaryArtifacts.size != 1) {
                failures += "$module -> expected exactly one primary AAR/JAR, found ${primaryArtifacts.size}"
            }
            val publishedArtifacts =
                (listOf(pom, sources, javadoc, gradleMetadata) + primaryArtifacts)
                    .filter(File::isFile)
            publishedArtifacts.forEach { artifact ->
                listOf("sha256", "sha512").forEach { extension ->
                    val checksum = artifactDirectory.resolve("${artifact.name}.$extension")
                    if (!checksum.isFile) {
                        failures += "$module -> missing ${checksum.name}"
                    }
                }
            }
            if (!version.endsWith("-SNAPSHOT")) {
                publishedArtifacts.forEach { artifact ->
                    val signature = artifactDirectory.resolve("${artifact.name}.asc")
                    if (!signature.isFile) {
                        failures += "$module -> missing ${signature.name}"
                    }
                }
            }
            if (sources.isFile && countSourceEntries(sources) == 0) {
                failures += "$module -> sources JAR contains no Kotlin or Java source"
            }
            if (pom.isFile) {
                val content = pom.readText()
                REQUIRED_POM_ELEMENTS.filterNot(content::contains).forEach { element ->
                    failures += "$module -> POM missing $element"
                }
                if ("unspecified" in content) {
                    failures += "$module -> POM contains an unspecified dependency version"
                }
            }
        }

        FEATURE_CORE_DEPENDENCIES.filterKeys(selectedModules::contains).forEach { (feature, core) ->
            val featureVersion = versions[feature] ?: return@forEach
            val coreVersion = versions[core] ?: return@forEach
            val pom = groupDirectory.resolve(feature).resolve(featureVersion)
                .listFiles()
                .orEmpty()
                .filter { file ->
                    file.isFile && file.name.startsWith("$feature-") && file.name.endsWith(".pom")
                }
                .maxByOrNull { file -> file.name }
            pom?.takeIf(File::isFile)?.let { pomFile ->
                val content = pomFile.readText()
                if ("<artifactId>$core</artifactId>" !in content ||
                    "<version>$coreVersion</version>" !in content
                ) {
                    failures += "$feature -> published metadata does not carry $core:$coreVersion"
                }
            }
        }

        if (failures.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("ViewCompose local publication verification failed:")
                    failures.sorted().forEach { appendLine("- $it") }
                },
            )
        }
    }

    private fun countSourceEntries(sourceJar: File): Int = ZipFile(sourceJar).use { zip ->
        zip.entries().asSequence().count { entry ->
            !entry.isDirectory && (entry.name.endsWith(".kt") || entry.name.endsWith(".java"))
        }
    }
}

private data class PublishingMetadata(
    val groupId: String,
    val projectUrl: String,
    val scmConnection: String,
    val scmDeveloperConnection: String,
    val scmUrl: String,
    val strictApiDocsModules: List<String>,
    val moduleVersions: Map<String, String>,
) {
    companion object {
        fun load(project: Project): PublishingMetadata {
            val propertiesFile = project.rootProject.file("gradle/viewcompose-publishing.properties")
            check(propertiesFile.isFile) {
                "Missing publication metadata: ${propertiesFile.absolutePath}"
            }
            val properties = Properties().apply {
                propertiesFile.inputStream().use(::load)
            }
            val groupId = project.providers.gradleProperty("viewComposeGroup")
                .orElse(properties.required("maven.group"))
                .get()
            val versions = properties.stringPropertyNames()
                .filter { key -> key.startsWith("module.") && key.endsWith(".version") }
                .associate { key ->
                    val module = key.removePrefix("module.").removeSuffix(".version")
                    val version = project.providers
                        .gradleProperty("viewComposeVersion.$module")
                        .orElse(properties.required(key))
                        .get()
                    module to version
                }
                .toSortedMap()
            return PublishingMetadata(
                groupId = groupId,
                projectUrl = properties.required("project.url"),
                scmConnection = properties.required("project.scm.connection"),
                scmDeveloperConnection = properties.required("project.scm.developerConnection"),
                scmUrl = properties.required("project.scm.url"),
                strictApiDocsModules = properties.required("apiDocs.strictModules")
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                    .sorted(),
                moduleVersions = versions,
            )
        }
    }
}

private fun Properties.required(key: String): String =
    getProperty(key)?.takeIf(String::isNotBlank)
        ?: throw GradleException("Missing publication property '$key'.")

private val GROUP_PATTERN = Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+")
private val MODULE_PATTERN = Regex("viewcompose-[a-z0-9-]+")
private val VERSION_PATTERN = Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")
private val REQUIRED_POM_ELEMENTS = listOf(
    "<name>",
    "<description>",
    "<url>",
    "<licenses>",
    "<developers>",
    "<scm>",
)
private val FEATURE_CORE_DEPENDENCIES = mapOf(
    "viewcompose-animation" to "viewcompose-animation-core",
    "viewcompose-gesture" to "viewcompose-gesture-core",
    "viewcompose-graphics" to "viewcompose-graphics-core",
    "viewcompose-navigation" to "viewcompose-navigation-core",
    "viewcompose-preview" to "viewcompose-preview-core",
)
