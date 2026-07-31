package com.viewcompose.studio.preview

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Timer
import java.util.TimerTask
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection

/**
 * Project-scoped Tooling API connection. Gradle still owns compilation and its daemon, while
 * Studio avoids launching a fresh wrapper client JVM for every discovery/render operation.
 */
internal class PersistentPreviewGradleExecutor(
    projectRoot: Path,
) : PreviewGradleExecutor, AutoCloseable {
    private val projectRoot = projectRoot.toAbsolutePath().normalize()
    private val connector = GradleConnector.newConnector()
        .forProjectDirectory(this.projectRoot.toFile())
        .useBuildDistribution()
    private var connection: ProjectConnection? = null
    private var closed = false

    @Synchronized
    override fun execute(
        invocation: PreviewGradleInvocation,
        indicator: ProgressIndicator,
    ): PreviewGradleResult {
        check(!closed) { "Preview Gradle executor is closed." }
        require(invocation.workingDirectory.toAbsolutePath().normalize() == projectRoot)
        val task = invocation.arguments.firstOrNull()
            ?: error("Preview Gradle invocation has no task.")
        val standardOutput = ByteArrayOutputStream()
        val errorOutput = ByteArrayOutputStream()
        val cancellation = GradleConnector.newCancellationTokenSource()
        val cancellationTimer = Timer("ViewCompose preview Gradle cancellation", true)
        cancellationTimer.scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() {
                    if (indicator.isCanceled) cancellation.cancel()
                }
            },
            0L,
            CANCELLATION_POLL_MILLIS,
        )
        val startedAtNanos = System.nanoTime()
        val exitCode = try {
            val activeConnection = connection ?: connector.connect().also { opened ->
                connection = opened
            }
            activeConnection.newBuild()
                .forTasks(task)
                .withArguments(invocation.arguments.drop(1))
                .setColorOutput(false)
                .setStandardOutput(standardOutput)
                .setStandardError(errorOutput)
                .withCancellationToken(cancellation.token())
                .run()
            0
        } catch (error: GradleConnectionException) {
            if (indicator.isCanceled) throw ProcessCanceledException()
            errorOutput.write(error.stackTraceToString().toByteArray(StandardCharsets.UTF_8))
            1
        } finally {
            cancellationTimer.cancel()
        }
        indicator.checkCanceled()
        return PreviewGradleResult(
            exitCode = exitCode,
            standardOutput = standardOutput.toString(StandardCharsets.UTF_8),
            errorOutput = errorOutput.toString(StandardCharsets.UTF_8),
            durationMillis = ((System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND)
                .coerceAtLeast(0L),
        )
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        connection?.close()
        connection = null
        connector.disconnect()
    }
}

private const val CANCELLATION_POLL_MILLIS = 50L
private const val NANOS_PER_MILLISECOND = 1_000_000L
