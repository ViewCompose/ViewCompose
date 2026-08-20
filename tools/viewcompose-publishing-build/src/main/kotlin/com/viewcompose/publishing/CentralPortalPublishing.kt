package com.viewcompose.publishing

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "The bundle is cheap to rebuild from release staging output.")
internal abstract class CreateViewComposeCentralBundleTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val mavenGroup: Property<String>

    @get:Input
    abstract val selectedVersions: MapProperty<String, String>

    @get:OutputFile
    abstract val bundleFile: RegularFileProperty

    @TaskAction
    fun createBundle() {
        val summary = ViewComposeCentralBundleBuilder.create(
            repositoryDirectory = repositoryDirectory.get().asFile,
            bundleFile = bundleFile.get().asFile,
            mavenGroup = mavenGroup.get(),
            selectedVersions = selectedVersions.get(),
        )
        logger.lifecycle(
            "Created Central Portal bundle with ${summary.fileCount} files and " +
                "${summary.selectedArtifactCount} selected artifacts: ${summary.bundleFile}",
        )
    }
}

@DisableCachingByDefault(because = "Uploading a Central Portal deployment is an external mutation.")
internal abstract class UploadViewComposeCentralBundleTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bundleFile: RegularFileProperty

    @get:Input
    abstract val deploymentName: Property<String>

    @get:Internal
    abstract val username: Property<String>

    @get:Internal
    abstract val password: Property<String>

    @get:Input
    abstract val connectTimeoutSeconds: Property<Int>

    @get:Input
    abstract val validationTimeoutSeconds: Property<Int>

    @get:Input
    abstract val pollIntervalSeconds: Property<Int>

    @get:OutputFile
    abstract val deploymentRecordFile: RegularFileProperty

    @TaskAction
    fun uploadBundle() {
        val username = username.get().trim()
        val password = password.get()
        check(username.isNotEmpty() && password.isNotEmpty()) {
            "Maven Central credentials are missing. Configure mavenCentralUsername and " +
                "mavenCentralPassword in user-level Gradle properties or CI secrets."
        }
        val connectTimeoutSeconds = connectTimeoutSeconds.get()
        val validationTimeoutSeconds = validationTimeoutSeconds.get()
        val pollIntervalSeconds = pollIntervalSeconds.get()
        check(connectTimeoutSeconds > 0) {
            "SONATYPE_CONNECT_TIMEOUT_SECONDS must be positive."
        }
        check(validationTimeoutSeconds > 0) {
            "SONATYPE_CLOSE_TIMEOUT_SECONDS must be positive."
        }
        check(pollIntervalSeconds > 0) {
            "SONATYPE_POLL_INTERVAL_SECONDS must be positive."
        }

        val bundle = bundleFile.get().asFile
        val record = deploymentRecordFile.get().asFile
        if (record.exists() && !record.delete()) {
            throw GradleException("Cannot replace stale Central deployment record: $record")
        }
        val publisher = CentralPortalPublisher(
            baseUrl = CENTRAL_PORTAL_BASE_URL,
            username = username,
            password = password,
            connectTimeoutMillis = connectTimeoutSeconds * 1_000,
            readTimeoutMillis = connectTimeoutSeconds * 1_000,
        )
        val bundleSha256 = bundle.sha256()
        val deploymentId = publisher.uploadUserManaged(
            bundleFile = bundle,
            deploymentName = deploymentName.get(),
        )
        var latestStatus = CentralDeploymentStatus(
            deploymentId = deploymentId,
            deploymentState = "UPLOADED",
            responseBody = "",
        )
        writeDeploymentRecord(record, bundle, bundleSha256, latestStatus, error = null)
        logger.lifecycle("Central Portal deployment created: $deploymentId")

        try {
            latestStatus = publisher.awaitValidation(
                deploymentId = deploymentId,
                timeoutMillis = validationTimeoutSeconds * 1_000L,
                pollIntervalMillis = pollIntervalSeconds * 1_000L,
            ) { status ->
                latestStatus = status
                writeDeploymentRecord(record, bundle, bundleSha256, status, error = null)
                logger.lifecycle(
                    "Central Portal deployment $deploymentId state: ${status.deploymentState}",
                )
            }
        } catch (error: Exception) {
            writeDeploymentRecord(record, bundle, bundleSha256, latestStatus, error.message)
            throw error
        }

        logger.lifecycle(
            "Central Portal deployment $deploymentId reached " +
                "${latestStatus.deploymentState}. Review it before publishing.",
        )
    }

    private fun writeDeploymentRecord(
        recordFile: File,
        bundleFile: File,
        bundleSha256: String,
        status: CentralDeploymentStatus,
        error: String?,
    ) {
        recordFile.parentFile.mkdirs()
        val record = linkedMapOf<String, Any>(
            "schemaVersion" to 1,
            "deploymentId" to status.deploymentId,
            "deploymentName" to deploymentName.get(),
            "deploymentState" to status.deploymentState,
            "bundleFile" to bundleFile.name,
            "bundleSha256" to bundleSha256,
        )
        if (!error.isNullOrBlank()) {
            record["error"] = error.take(MAX_RECORDED_ERROR_LENGTH)
        }
        recordFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(record)) + "\n")
    }
}

internal data class CentralBundleSummary(
    val bundleFile: File,
    val fileCount: Int,
    val selectedArtifactCount: Int,
)

internal object ViewComposeCentralBundleBuilder {
    fun create(
        repositoryDirectory: File,
        bundleFile: File,
        mavenGroup: String,
        selectedVersions: Map<String, String>,
    ): CentralBundleSummary {
        check(repositoryDirectory.isDirectory) {
            "Central staging repository does not exist: $repositoryDirectory"
        }
        check(selectedVersions.isNotEmpty()) {
            "At least one artifact must be selected for a Central bundle."
        }
        val groupDirectory = repositoryDirectory.resolve(mavenGroup.replace('.', '/'))
        selectedVersions.toSortedMap().forEach { (artifact, version) ->
            val expectedPom = groupDirectory
                .resolve(artifact)
                .resolve(version)
                .resolve("$artifact-$version.pom")
            check(expectedPom.isFile) {
                "Central staging is missing selected artifact POM: " +
                    expectedPom.relativeTo(repositoryDirectory).invariantSeparatorsPath
            }
        }
        val stagedFiles = repositoryDirectory.walkTopDown()
            .filter(File::isFile)
            .filterNot { file -> file.name.contains("maven-metadata") }
            .map { file ->
                file to file.relativeTo(repositoryDirectory).invariantSeparatorsPath
            }
            .sortedBy(Pair<File, String>::second)
            .toList()
        check(stagedFiles.isNotEmpty()) {
            "Central staging repository contains no publishable files."
        }
        check(stagedFiles.map(Pair<File, String>::second).distinct().size == stagedFiles.size) {
            "Central staging repository contains duplicate bundle paths."
        }

        bundleFile.parentFile.mkdirs()
        if (bundleFile.exists() && !bundleFile.delete()) {
            throw GradleException("Cannot replace stale Central bundle: $bundleFile")
        }
        try {
            ZipOutputStream(BufferedOutputStream(bundleFile.outputStream())).use { output ->
                stagedFiles.forEach { (file, relativePath) ->
                    output.putNextEntry(
                        ZipEntry(relativePath).apply {
                            time = REPRODUCIBLE_ZIP_TIMESTAMP_MILLIS
                        },
                    )
                    file.inputStream().buffered().use { input -> input.copyTo(output) }
                    output.closeEntry()
                }
            }
        } catch (error: Exception) {
            bundleFile.delete()
            throw error
        }
        val actualEntries = ZipFile(bundleFile).use { zip ->
            zip.entries().asSequence()
                .filterNot(ZipEntry::isDirectory)
                .map(ZipEntry::getName)
                .toList()
        }
        check(actualEntries == stagedFiles.map(Pair<File, String>::second)) {
            "Central bundle entries do not match the staged repository."
        }
        return CentralBundleSummary(
            bundleFile = bundleFile,
            fileCount = stagedFiles.size,
            selectedArtifactCount = selectedVersions.size,
        )
    }
}

internal data class CentralDeploymentStatus(
    val deploymentId: String,
    val deploymentState: String,
    val responseBody: String,
)

internal class CentralPortalPublisher(
    baseUrl: String,
    username: String,
    password: String,
    private val connectTimeoutMillis: Int,
    private val readTimeoutMillis: Int,
    private val sleeper: (Long) -> Unit = Thread::sleep,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val baseUrl = baseUrl.trimEnd('/')
    private val authorization = "Bearer " + Base64.getEncoder().encodeToString(
        "$username:$password".toByteArray(StandardCharsets.UTF_8),
    )

    fun uploadUserManaged(bundleFile: File, deploymentName: String): String {
        check(bundleFile.isFile && bundleFile.length() > 0L) {
            "Central Portal bundle is missing or empty: $bundleFile"
        }
        check(deploymentName.isNotBlank()) { "Central deployment name must not be blank." }
        val boundary = "ViewComposeCentral${UUID.randomUUID()}"
        val safeFileName = bundleFile.name.replace('"', '_')
        val prefix = buildString {
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"bundle\"; ")
            append("filename=\"$safeFileName\"\r\n")
            append("Content-Type: application/octet-stream\r\n\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
        val encodedName = URLEncoder.encode(deploymentName, StandardCharsets.UTF_8)
        val connection = openConnection(
            "/api/v1/publisher/upload?name=$encodedName&publishingType=USER_MANAGED",
        ).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setFixedLengthStreamingMode(prefix.size.toLong() + bundleFile.length() + suffix.size)
        }
        return try {
            connection.outputStream.buffered().use { output ->
                output.write(prefix)
                bundleFile.inputStream().buffered().use { input -> input.copyTo(output) }
                output.write(suffix)
            }
            val responseCode = connection.responseCode
            val responseBody = connection.readResponseBody()
            if (responseCode != HttpURLConnection.HTTP_CREATED) {
                throw GradleException(
                    "Central Portal upload failed with HTTP $responseCode: " +
                        responseBody.take(MAX_RECORDED_ERROR_LENGTH),
                )
            }
            val deploymentId = responseBody.trim()
            try {
                UUID.fromString(deploymentId)
            } catch (error: IllegalArgumentException) {
                throw GradleException(
                    "Central Portal returned an invalid deployment id: " +
                        deploymentId.take(MAX_RECORDED_ERROR_LENGTH),
                    error,
                )
            }
            deploymentId
        } finally {
            connection.disconnect()
        }
    }

    fun awaitValidation(
        deploymentId: String,
        timeoutMillis: Long,
        pollIntervalMillis: Long,
        onStatus: (CentralDeploymentStatus) -> Unit = {},
    ): CentralDeploymentStatus {
        check(timeoutMillis > 0L) { "Central validation timeout must be positive." }
        check(pollIntervalMillis > 0L) { "Central validation poll interval must be positive." }
        val deadline = nanoTime() + timeoutMillis * NANOS_PER_MILLISECOND
        while (true) {
            val status = status(deploymentId)
            onStatus(status)
            when (status.deploymentState) {
                "VALIDATED", "PUBLISHED" -> return status
                "FAILED" -> throw GradleException(
                    "Central Portal deployment $deploymentId failed validation: " +
                        status.responseBody.take(MAX_RECORDED_ERROR_LENGTH),
                )
                "PENDING", "VALIDATING", "PUBLISHING" -> Unit
                else -> throw GradleException(
                    "Central Portal deployment $deploymentId returned unknown state " +
                        "'${status.deploymentState}': " +
                        status.responseBody.take(MAX_RECORDED_ERROR_LENGTH),
                )
            }
            if (nanoTime() >= deadline) {
                throw GradleException(
                    "Central Portal deployment $deploymentId did not finish validation within " +
                        "$timeoutMillis ms; last state was ${status.deploymentState}.",
                )
            }
            sleeper(pollIntervalMillis)
        }
    }

    private fun status(deploymentId: String): CentralDeploymentStatus {
        val encodedId = URLEncoder.encode(deploymentId, StandardCharsets.UTF_8)
        val connection = openConnection("/api/v1/publisher/status?id=$encodedId").apply {
            requestMethod = "POST"
            doOutput = true
            setFixedLengthStreamingMode(0)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { }
            val responseCode = connection.responseCode
            val responseBody = connection.readResponseBody()
            if (responseCode !in 200..299) {
                throw GradleException(
                    "Central Portal status request failed with HTTP $responseCode: " +
                        responseBody.take(MAX_RECORDED_ERROR_LENGTH),
                )
            }
            val response = JsonSlurper().parseText(responseBody) as? Map<*, *>
                ?: throw GradleException("Central Portal returned a non-object status response.")
            val returnedId = response["deploymentId"] as? String
                ?: throw GradleException("Central Portal status response has no deploymentId.")
            val state = response["deploymentState"] as? String
                ?: throw GradleException("Central Portal status response has no deploymentState.")
            check(returnedId == deploymentId) {
                "Central Portal returned deployment '$returnedId' while polling '$deploymentId'."
            }
            CentralDeploymentStatus(
                deploymentId = returnedId,
                deploymentState = state,
                responseBody = responseBody,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(path: String): HttpURLConnection {
        return (URI.create("$baseUrl$path").toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            useCaches = false
            setRequestProperty("Authorization", authorization)
            setRequestProperty("User-Agent", "ViewCompose-Publishing")
        }
    }

    private fun HttpURLConnection.readResponseBody(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        return stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}

private const val CENTRAL_PORTAL_BASE_URL = "https://central.sonatype.com"
private const val MAX_RECORDED_ERROR_LENGTH = 8_000
private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val REPRODUCIBLE_ZIP_TIMESTAMP_MILLIS = 0L
