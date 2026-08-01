package com.viewcompose.preview.gradle

import com.viewcompose.preview.tooling.ViewComposePreviewProtocol
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Properties

/** Connects Gradle to one bounded external Layoutlib worker without loading Layoutlib into Gradle. */
internal class PersistentPreviewWorkerClient(
    private val endpointFile: File,
    private val logFile: File,
    private val compatibilityFingerprint: String,
    private val processClasspath: List<File>,
    private val mainClass: String,
    private val javaExecutable: File = defaultJavaExecutable(),
    private val startupTimeoutMillis: Long = DEFAULT_STARTUP_TIMEOUT_MILLIS,
    private val responseTimeoutMillis: Int = DEFAULT_RESPONSE_TIMEOUT_MILLIS,
) {
    init {
        require(compatibilityFingerprint.isNotBlank()) {
            "Preview worker compatibility fingerprint must not be blank."
        }
        require(processClasspath.isNotEmpty()) { "Preview worker process classpath is empty." }
        require(mainClass.isNotBlank()) { "Preview worker main class must not be blank." }
    }

    fun execute(commandFile: File): PreviewPersistentExecution {
        endpointFile.parentFile?.let { parent ->
            check(parent.isDirectory || parent.mkdirs()) {
                "Could not create preview worker directory '${parent.absolutePath}'."
            }
        }
        val lockFile = File(checkNotNull(endpointFile.parentFile), "worker.lock")
        return RandomAccessFile(lockFile, "rw").channel.use { channel ->
            channel.lock().use {
                val endpoint = compatibleEndpointOrNull() ?: startWorker()
                runCatching { send(endpoint, OPERATION_EXECUTE, commandFile.absolutePath) }
                    .getOrElse { firstError ->
                        invalidate(endpoint, stopProcess = true)
                        val replacement = startWorker()
                        runCatching {
                            send(replacement, OPERATION_EXECUTE, commandFile.absolutePath)
                        }.getOrElse { secondError ->
                            invalidate(replacement, stopProcess = true)
                            secondError.addSuppressed(firstError)
                            throw secondError
                        }
                    }
            }
        }
    }

    private fun compatibleEndpointOrNull(): PreviewPersistentEndpoint? {
        val endpoint = readEndpoint() ?: return null
        if (endpoint.compatibilityFingerprint != compatibilityFingerprint) {
            runCatching { send(endpoint, OPERATION_SHUTDOWN, "") }
            invalidate(endpoint, stopProcess = true)
            return null
        }
        if (!ProcessHandle.of(endpoint.processId).map(ProcessHandle::isAlive).orElse(false)) {
            invalidate(endpoint, stopProcess = false)
            return null
        }
        return endpoint
    }

    private fun startWorker(): PreviewPersistentEndpoint {
        if (endpointFile.exists()) check(endpointFile.delete()) {
            "Could not remove stale preview worker endpoint '${endpointFile.absolutePath}'."
        }
        logFile.parentFile?.let { parent ->
            check(parent.isDirectory || parent.mkdirs()) {
                "Could not create preview worker log directory '${parent.absolutePath}'."
            }
        }
        val classpath = processClasspath
            .map(File::getAbsoluteFile)
            .distinctBy(File::getPath)
            .joinToString(File.pathSeparator)
        val process = ProcessBuilder(
            javaExecutable.absolutePath,
            "-Djava.awt.headless=true",
            "-Xmx${WORKER_MAX_HEAP_MEBIBYTES}m",
            "-cp",
            classpath,
            mainClass,
            "--server",
            endpointFile.absolutePath,
            compatibilityFingerprint,
        )
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            .redirectError(ProcessBuilder.Redirect.appendTo(logFile))
            .start()
        val deadline = System.nanoTime() + startupTimeoutMillis * NANOS_PER_MILLISECOND
        while (System.nanoTime() < deadline) {
            readEndpoint()?.let { endpoint ->
                if (
                    endpoint.processId == process.pid() &&
                    endpoint.compatibilityFingerprint == compatibilityFingerprint
                ) {
                    return endpoint
                }
            }
            if (!process.isAlive) {
                error(
                    "Preview worker exited with code ${process.exitValue()} during startup. " +
                        "Worker log: ${logFile.absolutePath}",
                )
            }
            Thread.sleep(STARTUP_POLL_MILLIS)
        }
        process.destroyForcibly()
        error(
            "Timed out waiting for the preview worker endpoint. Worker log: ${logFile.absolutePath}",
        )
    }

    private fun send(
        endpoint: PreviewPersistentEndpoint,
        operation: String,
        commandPath: String,
    ): PreviewPersistentExecution {
        Socket().use { socket ->
            socket.connect(
                InetSocketAddress(InetAddress.getLoopbackAddress(), endpoint.port),
                CONNECT_TIMEOUT_MILLIS,
            )
            socket.soTimeout = responseTimeoutMillis
            val output = DataOutputStream(socket.getOutputStream().buffered())
            output.writeInt(ViewComposePreviewProtocol.CURRENT_VERSION)
            output.writeUTF(endpoint.token)
            output.writeUTF(operation)
            output.writeUTF(commandPath)
            output.flush()
            val input = DataInputStream(socket.getInputStream().buffered())
            val protocolVersion = input.readInt()
            require(protocolVersion == ViewComposePreviewProtocol.CURRENT_VERSION) {
                "Preview worker acknowledgement protocol mismatch: $protocolVersion."
            }
            val success = input.readBoolean()
            val message = input.readUTF()
            val retiring = input.readBoolean()
            val processedCommands = input.readInt()
            check(success) { "Preview worker rejected the request: $message" }
            return PreviewPersistentExecution(
                processId = endpoint.processId,
                retiring = retiring,
                processedCommands = processedCommands,
                message = message,
            )
        }
    }

    private fun readEndpoint(): PreviewPersistentEndpoint? {
        if (!endpointFile.isFile) return null
        return runCatching {
            val properties = Properties().apply { endpointFile.inputStream().use(::load) }
            require(
                properties.getProperty(ENDPOINT_PROTOCOL_KEY).toInt() ==
                    ViewComposePreviewProtocol.CURRENT_VERSION,
            )
            PreviewPersistentEndpoint(
                port = properties.getProperty(ENDPOINT_PORT_KEY).toInt(),
                processId = properties.getProperty(ENDPOINT_PROCESS_ID_KEY).toLong(),
                token = properties.getProperty(ENDPOINT_TOKEN_KEY),
                compatibilityFingerprint = properties.getProperty(ENDPOINT_FINGERPRINT_KEY),
            )
        }.getOrNull()
    }

    private fun invalidate(
        endpoint: PreviewPersistentEndpoint,
        stopProcess: Boolean,
    ) {
        val current = readEndpoint()
        if (current?.token == endpoint.token) endpointFile.delete()
        if (stopProcess) {
            ProcessHandle.of(endpoint.processId).ifPresent { process ->
                if (process.isAlive) process.destroyForcibly()
            }
        }
    }
}

internal data class PreviewPersistentExecution(
    val processId: Long,
    val retiring: Boolean,
    val processedCommands: Int,
    val message: String,
)

private data class PreviewPersistentEndpoint(
    val port: Int,
    val processId: Long,
    val token: String,
    val compatibilityFingerprint: String,
)

private fun defaultJavaExecutable(): File {
    val executableName = if (System.getProperty("os.name").startsWith("Windows", true)) {
        "java.exe"
    } else {
        "java"
    }
    return File(System.getProperty("java.home"), "bin/$executableName")
}

private const val OPERATION_EXECUTE = "execute"
private const val OPERATION_SHUTDOWN = "shutdown"
private const val ENDPOINT_PROTOCOL_KEY = "protocolVersion"
private const val ENDPOINT_PORT_KEY = "port"
private const val ENDPOINT_PROCESS_ID_KEY = "processId"
private const val ENDPOINT_TOKEN_KEY = "token"
private const val ENDPOINT_FINGERPRINT_KEY = "compatibilityFingerprint"
private const val CONNECT_TIMEOUT_MILLIS = 2_000
private const val DEFAULT_STARTUP_TIMEOUT_MILLIS = 30_000L
private const val DEFAULT_RESPONSE_TIMEOUT_MILLIS = 180_000
private const val STARTUP_POLL_MILLIS = 25L
private const val WORKER_MAX_HEAP_MEBIBYTES = 1024
private const val NANOS_PER_MILLISECOND = 1_000_000L
