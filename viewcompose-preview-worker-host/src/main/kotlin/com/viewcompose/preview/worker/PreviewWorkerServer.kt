package com.viewcompose.preview.worker

import com.viewcompose.preview.tooling.PreviewProtocolJson
import com.viewcompose.preview.tooling.PreviewRenderResponse
import com.viewcompose.preview.tooling.PreviewRenderStatus
import com.viewcompose.preview.tooling.ViewComposePreviewProtocol
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.Properties
import java.util.UUID
import kotlinx.serialization.json.jsonObject

/**
 * A bounded, loopback-only worker server. Layoutlib remains outside Gradle and Android Studio, and
 * the process retires after an idle timeout, a failed render, a request limit, or memory pressure.
 */
internal class PreviewWorkerServer(
    private val endpointFile: File,
    private val compatibilityFingerprint: String,
    private val idleTimeoutMillis: Int = DEFAULT_IDLE_TIMEOUT_MILLIS,
    private val maxCommands: Int = DEFAULT_MAX_COMMANDS,
    private val maxUsedHeapBytes: Long = DEFAULT_MAX_USED_HEAP_BYTES,
    private val commandExecutor: (File) -> List<PreviewRenderResponse> = ::executeCommandFile,
) {
    init {
        require(compatibilityFingerprint.isNotBlank()) {
            "Preview worker compatibility fingerprint must not be blank."
        }
        require(idleTimeoutMillis > 0) { "Preview worker idle timeout must be positive." }
        require(maxCommands > 0) { "Preview worker command limit must be positive." }
        require(maxUsedHeapBytes > 0L) { "Preview worker heap limit must be positive." }
    }

    fun run() {
        val token = UUID.randomUUID().toString()
        ServerSocket(0, SERVER_BACKLOG, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = idleTimeoutMillis
            writeEndpoint(
                PreviewWorkerEndpoint(
                    port = server.localPort,
                    processId = ProcessHandle.current().pid(),
                    token = token,
                    compatibilityFingerprint = compatibilityFingerprint,
                ),
            )
            var processedCommands = 0
            try {
                while (processedCommands < maxCommands) {
                    val socket = try {
                        server.accept()
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                    var retire = false
                    socket.use { client ->
                        client.soTimeout = CLIENT_READ_TIMEOUT_MILLIS
                        val input = DataInputStream(client.getInputStream().buffered())
                        val output = DataOutputStream(client.getOutputStream().buffered())
                        val protocolVersion = input.readInt()
                        val requestToken = input.readUTF()
                        val operation = input.readUTF()
                        val commandPath = input.readUTF()
                        val result = runCatching {
                            require(protocolVersion == ViewComposePreviewProtocol.CURRENT_VERSION) {
                                "Unsupported worker client protocol $protocolVersion."
                            }
                            require(requestToken == token) { "Preview worker token mismatch." }
                            when (operation) {
                                OPERATION_EXECUTE -> {
                                    val responses = commandExecutor(File(commandPath))
                                    processedCommands += responses.size
                                    retire = responses.any { response ->
                                        response.status != PreviewRenderStatus.Success
                                    } || processedCommands >= maxCommands || usedHeapBytes() >= maxUsedHeapBytes
                                    "Rendered ${responses.size} preview command(s)."
                                }

                                OPERATION_SHUTDOWN -> {
                                    retire = true
                                    "Worker shutdown accepted."
                                }

                                else -> error("Unknown preview worker operation '$operation'.")
                            }
                        }
                        output.writeInt(ViewComposePreviewProtocol.CURRENT_VERSION)
                        output.writeBoolean(result.isSuccess)
                        output.writeUTF(
                            result.fold(
                                onSuccess = { message -> message },
                                onFailure = { error ->
                                    error.rethrowIfFatal()
                                    (error.stackTraceToString()).take(MAX_ACK_MESSAGE_LENGTH)
                                },
                            ),
                        )
                        output.writeBoolean(retire || result.isFailure)
                        output.writeInt(processedCommands)
                        output.flush()
                        if (result.isFailure) retire = true
                    }
                    if (retire) break
                }
            } finally {
                deleteEndpointIfOwned(token)
            }
        }
    }

    private fun writeEndpoint(endpoint: PreviewWorkerEndpoint) {
        endpointFile.parentFile?.let { parent ->
            check(parent.isDirectory || parent.mkdirs()) {
                "Could not create worker endpoint directory '${parent.absolutePath}'."
            }
        }
        val properties = Properties().apply {
            setProperty(ENDPOINT_PROTOCOL_KEY, ViewComposePreviewProtocol.CURRENT_VERSION.toString())
            setProperty(ENDPOINT_PORT_KEY, endpoint.port.toString())
            setProperty(ENDPOINT_PROCESS_ID_KEY, endpoint.processId.toString())
            setProperty(ENDPOINT_TOKEN_KEY, endpoint.token)
            setProperty(ENDPOINT_FINGERPRINT_KEY, endpoint.compatibilityFingerprint)
        }
        val temporary = File(checkNotNull(endpointFile.parentFile), "${endpointFile.name}.tmp")
        temporary.outputStream().use { output -> properties.store(output, null) }
        if (endpointFile.exists()) {
            check(endpointFile.delete()) {
                "Could not replace preview worker endpoint '${endpointFile.absolutePath}'."
            }
        }
        check(temporary.renameTo(endpointFile)) {
            "Could not publish preview worker endpoint '${endpointFile.absolutePath}'."
        }
    }

    private fun deleteEndpointIfOwned(token: String) {
        val owner = runCatching {
            Properties().apply { endpointFile.inputStream().use(::load) }
                .getProperty(ENDPOINT_TOKEN_KEY)
        }.getOrNull()
        if (owner == token) endpointFile.delete()
    }
}

internal data class PreviewWorkerEndpoint(
    val port: Int,
    val processId: Long,
    val token: String,
    val compatibilityFingerprint: String,
)

private fun executeCommandFile(commandFile: File): List<PreviewRenderResponse> {
    val commandJson = commandFile.readText()
    return if (
        PreviewProtocolJson.format.parseToJsonElement(commandJson).jsonObject.containsKey("commands")
    ) {
        PreviewWorkerHost.executeBatch(commandFile)
    } else {
        listOf(PreviewWorkerHost.execute(commandFile))
    }
}

private fun usedHeapBytes(): Long {
    val runtime = Runtime.getRuntime()
    return runtime.totalMemory() - runtime.freeMemory()
}

private fun Throwable.rethrowIfFatal() {
    if (this is ThreadDeath || this is OutOfMemoryError) throw this
}

internal const val OPERATION_EXECUTE = "execute"
internal const val OPERATION_SHUTDOWN = "shutdown"
internal const val ENDPOINT_PROTOCOL_KEY = "protocolVersion"
internal const val ENDPOINT_PORT_KEY = "port"
internal const val ENDPOINT_PROCESS_ID_KEY = "processId"
internal const val ENDPOINT_TOKEN_KEY = "token"
internal const val ENDPOINT_FINGERPRINT_KEY = "compatibilityFingerprint"
private const val SERVER_BACKLOG = 4
private const val DEFAULT_IDLE_TIMEOUT_MILLIS = 120_000
private const val CLIENT_READ_TIMEOUT_MILLIS = 180_000
private const val DEFAULT_MAX_COMMANDS = 24
private const val MAX_ACK_MESSAGE_LENGTH = 32_000
private const val DEFAULT_MAX_USED_HEAP_BYTES = 768L * 1024L * 1024L
