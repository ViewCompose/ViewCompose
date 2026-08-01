package com.viewcompose.preview.gradle

import com.viewcompose.preview.tooling.ViewComposePreviewProtocol
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersistentPreviewWorkerClientTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `compatible live endpoint is reused without starting another process`() {
        val endpointFile = temporaryFolder.newFile("endpoint.properties")
        val token = "test-token"
        val fingerprint = "a".repeat(64)
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val serverThread = Thread {
            server.use {
                it.accept().use { socket ->
                    val input = DataInputStream(socket.getInputStream().buffered())
                    assertEquals(ViewComposePreviewProtocol.CURRENT_VERSION, input.readInt())
                    assertEquals(token, input.readUTF())
                    assertEquals("execute", input.readUTF())
                    assertEquals("command.json", input.readUTF().substringAfterLast('/'))
                    DataOutputStream(socket.getOutputStream().buffered()).use { output ->
                        output.writeInt(ViewComposePreviewProtocol.CURRENT_VERSION)
                        output.writeBoolean(true)
                        output.writeUTF("fixture rendered")
                        output.writeBoolean(false)
                        output.writeInt(3)
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        Properties().apply {
            setProperty("protocolVersion", ViewComposePreviewProtocol.CURRENT_VERSION.toString())
            setProperty("port", server.localPort.toString())
            setProperty("processId", ProcessHandle.current().pid().toString())
            setProperty("token", token)
            setProperty("compatibilityFingerprint", fingerprint)
        }.also { properties ->
            endpointFile.outputStream().use { output -> properties.store(output, null) }
        }
        val commandFile = temporaryFolder.newFile("command.json")
        val client = PersistentPreviewWorkerClient(
            endpointFile = endpointFile,
            logFile = temporaryFolder.root.resolve("worker.log"),
            compatibilityFingerprint = fingerprint,
            processClasspath = listOf(temporaryFolder.newFile("worker.jar")),
            mainClass = "fixture.Worker",
        )

        val execution = client.execute(commandFile)

        assertEquals(ProcessHandle.current().pid(), execution.processId)
        assertEquals(3, execution.processedCommands)
        assertEquals("fixture rendered", execution.message)
        assertFalse(execution.retiring)
        serverThread.join(2_000)
        assertFalse(serverThread.isAlive)
    }
}
