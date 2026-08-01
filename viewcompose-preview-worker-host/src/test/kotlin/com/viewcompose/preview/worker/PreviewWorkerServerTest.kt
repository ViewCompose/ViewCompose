package com.viewcompose.preview.worker

import com.viewcompose.preview.tooling.PreviewArtifacts
import com.viewcompose.preview.tooling.PreviewDiagnostic
import com.viewcompose.preview.tooling.PreviewDiagnosticSeverity
import com.viewcompose.preview.tooling.PreviewRenderResponse
import com.viewcompose.preview.tooling.PreviewRenderStatus
import com.viewcompose.preview.tooling.ViewComposePreviewProtocol
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewWorkerServerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `server executes commands and retires at the bounded request count`() {
        val endpointFile = temporaryFolder.root.resolve("worker.properties")
        val executions = AtomicInteger()
        val serverThread = startServer(
            endpointFile = endpointFile,
            maxCommands = 1,
            commandExecutor = {
                executions.incrementAndGet()
                listOf(successResponse())
            },
        )
        val endpoint = awaitEndpoint(endpointFile)

        val acknowledgement = send(endpoint, OPERATION_EXECUTE, "command.json")

        assertTrue(acknowledgement.success)
        assertTrue(acknowledgement.retiring)
        assertEquals(1, acknowledgement.processedCommands)
        assertEquals(1, executions.get())
        serverThread.join(2_000)
        assertFalse(serverThread.isAlive)
        assertFalse(endpointFile.exists())
    }

    @Test
    fun `failed render retires the process before state can leak`() {
        val endpointFile = temporaryFolder.root.resolve("failed-worker.properties")
        val serverThread = startServer(
            endpointFile = endpointFile,
            maxCommands = 8,
            commandExecutor = { listOf(failureResponse()) },
        )
        val endpoint = awaitEndpoint(endpointFile)

        val acknowledgement = send(endpoint, OPERATION_EXECUTE, "command.json")

        assertTrue(acknowledgement.success)
        assertTrue(acknowledgement.retiring)
        serverThread.join(2_000)
        assertFalse(serverThread.isAlive)
    }

    @Test
    fun `idle server removes its endpoint`() {
        val endpointFile = temporaryFolder.root.resolve("idle-worker.properties")
        val serverThread = startServer(
            endpointFile = endpointFile,
            idleTimeoutMillis = 100,
            maxCommands = 8,
            commandExecutor = { listOf(successResponse()) },
        )
        awaitEndpoint(endpointFile)

        serverThread.join(2_000)

        assertFalse(serverThread.isAlive)
        assertFalse(endpointFile.exists())
    }

    @Test
    fun `worker retires after a successful command when heap budget is exceeded`() {
        val endpointFile = temporaryFolder.root.resolve("memory-worker.properties")
        val serverThread = startServer(
            endpointFile = endpointFile,
            maxCommands = 8,
            maxUsedHeapBytes = 1,
            commandExecutor = { listOf(successResponse()) },
        )
        val endpoint = awaitEndpoint(endpointFile)

        val acknowledgement = send(endpoint, OPERATION_EXECUTE, "command.json")

        assertTrue(acknowledgement.success)
        assertTrue(acknowledgement.retiring)
        serverThread.join(2_000)
        assertFalse(serverThread.isAlive)
        assertFalse(endpointFile.exists())
    }

    private fun startServer(
        endpointFile: File,
        idleTimeoutMillis: Int = 2_000,
        maxCommands: Int,
        maxUsedHeapBytes: Long = Long.MAX_VALUE,
        commandExecutor: (File) -> List<PreviewRenderResponse>,
    ): Thread {
        return Thread {
            PreviewWorkerServer(
                endpointFile = endpointFile,
                compatibilityFingerprint = "a".repeat(64),
                idleTimeoutMillis = idleTimeoutMillis,
                maxCommands = maxCommands,
                maxUsedHeapBytes = maxUsedHeapBytes,
                commandExecutor = commandExecutor,
            ).run()
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun awaitEndpoint(file: File): TestEndpoint {
        repeat(200) {
            if (file.isFile) {
                val properties = Properties().apply { file.inputStream().use(::load) }
                return TestEndpoint(
                    port = properties.getProperty(ENDPOINT_PORT_KEY).toInt(),
                    token = properties.getProperty(ENDPOINT_TOKEN_KEY),
                )
            }
            Thread.sleep(10)
        }
        error("Worker endpoint was not published.")
    }

    private fun send(
        endpoint: TestEndpoint,
        operation: String,
        commandPath: String,
    ): TestAcknowledgement {
        Socket(InetAddress.getLoopbackAddress(), endpoint.port).use { socket ->
            val output = DataOutputStream(socket.getOutputStream().buffered())
            output.writeInt(ViewComposePreviewProtocol.CURRENT_VERSION)
            output.writeUTF(endpoint.token)
            output.writeUTF(operation)
            output.writeUTF(commandPath)
            output.flush()
            val input = DataInputStream(socket.getInputStream().buffered())
            assertEquals(ViewComposePreviewProtocol.CURRENT_VERSION, input.readInt())
            return TestAcknowledgement(
                success = input.readBoolean(),
                message = input.readUTF(),
                retiring = input.readBoolean(),
                processedCommands = input.readInt(),
            )
        }
    }

    private fun successResponse() = PreviewRenderResponse(
        requestId = "request",
        previewId = "preview",
        variantId = "variant",
        status = PreviewRenderStatus.Success,
        artifacts = PreviewArtifacts(imagePath = "preview.png"),
    )

    private fun failureResponse() = PreviewRenderResponse(
        requestId = "request",
        previewId = "preview",
        variantId = "variant",
        status = PreviewRenderStatus.RenderFailure,
        diagnostics = listOf(
            PreviewDiagnostic(
                severity = PreviewDiagnosticSeverity.Error,
                message = "Fixture failure",
                phase = "test",
            ),
        ),
    )
}

private data class TestEndpoint(
    val port: Int,
    val token: String,
)

private data class TestAcknowledgement(
    val success: Boolean,
    val message: String,
    val retiring: Boolean,
    val processedCommands: Int,
)
