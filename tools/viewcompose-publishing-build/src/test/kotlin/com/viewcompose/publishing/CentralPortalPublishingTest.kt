package com.viewcompose.publishing

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile
import org.gradle.api.GradleException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CentralPortalPublishingTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `bundle is deterministic and excludes repository metadata`() {
        val repository = temporaryFolder.newFolder("repository")
        val artifactDirectory = repository.resolve(
            "com/viewcompose/viewcompose-runtime/0.1.0-alpha03",
        ).apply { mkdirs() }
        artifactDirectory.resolve("viewcompose-runtime-0.1.0-alpha03.pom")
            .writeText("<project />")
        artifactDirectory.resolve("viewcompose-runtime-0.1.0-alpha03.jar")
            .writeBytes(byteArrayOf(3, 1, 4, 1, 5))
        artifactDirectory.resolve("viewcompose-runtime-0.1.0-alpha03.jar.asc")
            .writeText("signature")
        val secondArtifactDirectory = repository.resolve(
            "com/viewcompose/viewcompose-ui-contract/0.1.0-alpha04",
        ).apply { mkdirs() }
        secondArtifactDirectory.resolve("viewcompose-ui-contract-0.1.0-alpha04.pom")
            .writeText("<project />")
        secondArtifactDirectory.resolve("viewcompose-ui-contract-0.1.0-alpha04.jar")
            .writeBytes(byteArrayOf(9, 2, 6, 5))
        repository.resolve("com/viewcompose/viewcompose-runtime/maven-metadata.xml")
            .apply {
                parentFile.mkdirs()
                writeText("stale metadata")
            }
        val firstBundle = temporaryFolder.newFile("first.zip")
        val secondBundle = temporaryFolder.newFile("second.zip")
        val selectedVersions = mapOf(
            "viewcompose-runtime" to "0.1.0-alpha03",
            "viewcompose-ui-contract" to "0.1.0-alpha04",
        )

        val first = ViewComposeCentralBundleBuilder.create(
            repositoryDirectory = repository,
            bundleFile = firstBundle,
            mavenGroup = "com.viewcompose",
            selectedVersions = selectedVersions,
        )
        val second = ViewComposeCentralBundleBuilder.create(
            repositoryDirectory = repository,
            bundleFile = secondBundle,
            mavenGroup = "com.viewcompose",
            selectedVersions = selectedVersions,
        )

        assertEquals(5, first.fileCount)
        assertEquals(2, first.selectedArtifactCount)
        assertEquals(first.fileCount, second.fileCount)
        assertEquals(first.selectedArtifactCount, second.selectedArtifactCount)
        assertArrayEquals(firstBundle.readBytes(), secondBundle.readBytes())
        val entries = ZipFile(firstBundle).use { zip ->
            zip.entries().asSequence().map { it.name }.toList()
        }
        assertEquals(entries.sorted(), entries)
        assertFalse(entries.any { it.contains("maven-metadata") })
        assertTrue(entries.any { it.endsWith("viewcompose-runtime-0.1.0-alpha03.pom") })
        assertTrue(entries.any { it.endsWith("viewcompose-ui-contract-0.1.0-alpha04.pom") })
    }

    @Test
    fun `bundle rejects a selected coordinate missing from staging`() {
        val repository = temporaryFolder.newFolder("missing-coordinate")

        val error = assertThrows(IllegalStateException::class.java) {
            ViewComposeCentralBundleBuilder.create(
                repositoryDirectory = repository,
                bundleFile = temporaryFolder.newFile("missing.zip"),
                mavenGroup = "com.viewcompose",
                selectedVersions = mapOf("viewcompose-runtime" to "0.1.0-alpha03"),
            )
        }

        assertTrue(error.message.orEmpty().contains("missing selected artifact POM"))
    }

    @Test
    fun `publisher uploads one user managed bundle and waits for validation`() {
        val deploymentId = "4e2abd14-589d-4396-8ebc-88c3024fd446"
        val statusRequests = AtomicInteger()
        var uploadAuthorization = ""
        var uploadQuery = ""
        var uploadBody = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/v1/publisher/upload") { exchange ->
                uploadAuthorization = exchange.requestHeaders.getFirst("Authorization")
                uploadQuery = exchange.requestURI.rawQuery
                uploadBody = exchange.requestBody.readBytes()
                    .toString(StandardCharsets.ISO_8859_1)
                exchange.respond(201, deploymentId)
            }
            createContext("/api/v1/publisher/status") { exchange ->
                val state = if (statusRequests.incrementAndGet() == 1) {
                    "VALIDATING"
                } else {
                    "VALIDATED"
                }
                exchange.respond(
                    200,
                    """{"deploymentId":"$deploymentId","deploymentState":"$state"}""",
                )
            }
            start()
        }
        try {
            val bundle = temporaryFolder.newFile("central-bundle.zip")
                .apply { writeText("bundle-payload") }
            val publisher = CentralPortalPublisher(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                username = "token-user",
                password = "token-password",
                connectTimeoutMillis = 2_000,
                readTimeoutMillis = 2_000,
                sleeper = {},
            )

            val uploadedId = publisher.uploadUserManaged(bundle, "viewcompose release")
            val status = publisher.awaitValidation(
                deploymentId = uploadedId,
                timeoutMillis = 5_000,
                pollIntervalMillis = 1,
            )

            val expectedToken = Base64.getEncoder().encodeToString(
                "token-user:token-password".toByteArray(StandardCharsets.UTF_8),
            )
            assertEquals(deploymentId, uploadedId)
            assertEquals("VALIDATED", status.deploymentState)
            assertEquals(2, statusRequests.get())
            assertEquals("Bearer $expectedToken", uploadAuthorization)
            assertTrue(uploadQuery.contains("publishingType=USER_MANAGED"))
            assertTrue(uploadQuery.contains("name=viewcompose+release"))
            assertTrue(uploadBody.contains("name=\"bundle\""))
            assertTrue(uploadBody.contains("bundle-payload"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `publisher reports Central validation errors`() {
        val deploymentId = "4e2abd14-589d-4396-8ebc-88c3024fd446"
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/v1/publisher/status") { exchange ->
                exchange.respond(
                    200,
                    """{"deploymentId":"$deploymentId","deploymentState":"FAILED","errors":{"pom":"bad signature"}}""",
                )
            }
            start()
        }
        try {
            val publisher = CentralPortalPublisher(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                username = "token-user",
                password = "token-password",
                connectTimeoutMillis = 2_000,
                readTimeoutMillis = 2_000,
                sleeper = {},
            )

            val error = assertThrows(GradleException::class.java) {
                publisher.awaitValidation(
                    deploymentId = deploymentId,
                    timeoutMillis = 5_000,
                    pollIntervalMillis = 1,
                )
            }

            assertTrue(error.message.orEmpty().contains("bad signature"))
        } finally {
            server.stop(0)
        }
    }

    private fun com.sun.net.httpserver.HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
