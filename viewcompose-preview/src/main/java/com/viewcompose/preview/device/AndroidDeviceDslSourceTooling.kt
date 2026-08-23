package com.viewcompose.preview.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.viewcompose.ui.foundation.RenderSessionSourceRegistration
import com.viewcompose.ui.foundation.RenderSessionSourceTooling
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.nativeContainer
import com.viewcompose.ui.tooling.UiSourceCallSite
import com.viewcompose.ui.tooling.UiSourceSessionContainerHandle
import com.viewcompose.ui.tooling.UiSourceSessionRole
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal const val DEVICE_DSL_SOURCE_REQUEST_ACTION =
    "com.viewcompose.preview.action.REQUEST_DEVICE_DSL_SOURCE"
internal const val DEVICE_DSL_SOURCE_REQUEST_ID_EXTRA = "request_id"
internal const val DEVICE_DSL_SOURCE_REPORT_RELATIVE_PATH =
    "viewcompose/device-dsl-source-v3.json"
internal const val DEVICE_DSL_SOURCE_PROTOCOL_VERSION = 3

/** Optional debug-scoped source-session service discovered by the Android Host. */
internal class AndroidDeviceDslSourceTooling : RenderSessionSourceTooling {
    override fun shouldCapture(container: RenderContainerHandle): Boolean {
        val role = (container as? UiSourceSessionContainerHandle)?.sourceSessionRole
        if (role != UiSourceSessionRole.Host && role != UiSourceSessionRole.Page) return false
        val viewGroup = container.nativeContainer as? ViewGroup ?: return false
        return AndroidDeviceToolingDebugGate.markAndGet(viewGroup.context)
    }

    override fun register(
        container: RenderContainerHandle,
        sourceCandidates: List<List<UiSourceCallSite>>,
    ): RenderSessionSourceRegistration? {
        val viewGroup = container.nativeContainer as? ViewGroup ?: return null
        if (!AndroidDeviceToolingDebugGate.markAndGet(viewGroup.context)) return null
        return AndroidDeviceDslSourceRuntime.registry.register(
            container = viewGroup,
            sourceCandidates = sourceCandidates,
        )
    }
}

/** Receives one explicit, ADB-authorized request and leaves no recurring View observation behind. */
internal class DeviceDslSourceRequestReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != DEVICE_DSL_SOURCE_REQUEST_ACTION) return
        if (!context.isDebuggableApplication()) return
        AndroidDeviceToolingDebugGate.markAndGet(context)
        val requestId = intent.getStringExtra(DEVICE_DSL_SOURCE_REQUEST_ID_EXTRA)
            ?.takeIf(::isValidDeviceDslSourceRequestId)
            ?: return
        val pendingResult = goAsync()
        val inspect = Runnable {
            runCatching {
                AndroidDeviceDslSourceRuntime.requestHandler.handle(
                    context = context,
                    requestId = requestId,
                    onFinished = pendingResult::finish,
                )
            }.onFailure { error ->
                pendingResult.finish()
                Log.w(TAG, "Device DSL source request failed without affecting the application.", error)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            inspect.run()
        } else {
            Handler(Looper.getMainLooper()).post(inspect)
        }
    }
}

private object AndroidDeviceDslSourceRuntime {
    val registry = AndroidDeviceDslSourceRegistry()
    val requestHandler = DeviceDslSourceRequestHandler(registry)
}

internal class AndroidDeviceDslSourceRegistry(
    private val processId: () -> Int = Process::myPid,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val nextSessionId = AtomicLong(0L)
    private val sessions = linkedMapOf<Long, DeviceDslSourceSession>()

    fun register(
        container: ViewGroup,
        sourceCandidates: List<List<UiSourceCallSite>>,
    ): RenderSessionSourceRegistration? {
        val boundedCandidates = sourceCandidates.boundedSourceCandidates()
        if (boundedCandidates.isEmpty()) return null
        val sessionId = nextSessionId.incrementAndGet()
        synchronized(this) {
            sessions.entries.removeAll { (_, session) -> session.container.get() == null }
            while (sessions.size >= MAX_TRACKED_SESSIONS) {
                sessions.remove(sessions.keys.first())
            }
            sessions[sessionId] = DeviceDslSourceSession(
                id = sessionId,
                container = WeakReference(container),
                sourceCandidates = boundedCandidates,
            )
        }
        return RegistrySourceRegistration(this, sessionId)
    }

    fun snapshot(
        packageName: String,
        requestId: String,
    ): DeviceDslSourceReport {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Device DSL source inspection must run on the Android main thread."
        }
        return synchronized(this) {
            sessions.entries.removeAll { (_, session) -> session.container.get() == null }
            DeviceDslSourceReport(
                requestId = requestId,
                packageName = packageName,
                processId = processId(),
                generatedAtEpochMillis = currentTimeMillis(),
                sessions = sessions.values.toList()
                    .takeLast(MAX_REPORTED_SESSIONS)
                    .mapNotNull(DeviceDslSourceSession::snapshot)
                    .sortedWith(
                        compareByDescending<DeviceDslSourceSessionSnapshot> { it.renderingActive }
                            .thenByDescending { it.attachedToWindow && it.shown }
                            .thenByDescending { it.hasWindowFocus }
                            .thenByDescending { it.viewDepth }
                            .thenByDescending { it.sessionId },
                    ),
            )
        }
    }

    fun setRenderingActive(
        sessionId: Long,
        active: Boolean,
    ) {
        synchronized(this) {
            sessions[sessionId]?.renderingActive = active
        }
    }

    fun unregister(sessionId: Long) {
        synchronized(this) {
            sessions.remove(sessionId)
        }
    }

    internal fun sessionCountForTest(): Int = synchronized(this) { sessions.size }
}

internal class DeviceDslSourceRequestHandler(
    private val registry: AndroidDeviceDslSourceRegistry,
    private val execute: (Runnable) -> Unit = DeviceDslSourceResponseWriter::execute,
    private val writeResponse: (File, String) -> Unit = DeviceDslSourceResponseWriter::write,
) {
    fun handle(
        context: Context,
        requestId: String,
        onFinished: () -> Unit,
    ) {
        check(isValidDeviceDslSourceRequestId(requestId)) {
            "Device DSL source request ID is invalid."
        }
        val appContext = context.applicationContext ?: context
        val packageName = appContext.packageName
        val report = registry.snapshot(
            packageName = packageName,
            requestId = requestId,
        )
        val target = File(appContext.cacheDir, DEVICE_DSL_SOURCE_REPORT_RELATIVE_PATH)
        execute(
            Runnable {
                try {
                    runCatching { writeResponse(target, report.toJson()) }
                        .onFailure { error ->
                            Log.w(
                                TAG,
                                "Device DSL source response could not be written.",
                                error,
                            )
                        }
                } finally {
                    onFinished()
                }
            },
        )
    }
}

private class RegistrySourceRegistration(
    private val registry: AndroidDeviceDslSourceRegistry,
    private val sessionId: Long,
) : RenderSessionSourceRegistration {
    private val disposed = AtomicBoolean(false)

    override fun setRenderingActive(active: Boolean) {
        if (!disposed.get()) {
            registry.setRenderingActive(sessionId, active)
        }
    }

    override fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            registry.unregister(sessionId)
        }
    }
}

private class DeviceDslSourceSession(
    val id: Long,
    val container: WeakReference<ViewGroup>,
    val sourceCandidates: List<List<UiSourceCallSite>>,
    var renderingActive: Boolean = true,
) {
    fun snapshot(): DeviceDslSourceSessionSnapshot? {
        val view = container.get() ?: return null
        return DeviceDslSourceSessionSnapshot(
            sessionId = id,
            renderingActive = renderingActive,
            attachedToWindow = view.isAttachedToWindow,
            shown = view.isVisibleToUser(),
            hasWindowFocus = view.hasWindowFocus(),
            windowVisibility = view.windowVisibility,
            viewDepth = view.depthInHierarchy(),
            sourceCandidates = sourceCandidates,
        )
    }
}

internal data class DeviceDslSourceSessionSnapshot(
    val sessionId: Long,
    val renderingActive: Boolean,
    val attachedToWindow: Boolean,
    val shown: Boolean,
    val hasWindowFocus: Boolean,
    val windowVisibility: Int,
    val viewDepth: Int,
    val sourceCandidates: List<List<UiSourceCallSite>>,
)

internal data class DeviceDslSourceReport(
    val requestId: String,
    val packageName: String,
    val processId: Int,
    val generatedAtEpochMillis: Long,
    val sessions: List<DeviceDslSourceSessionSnapshot>,
)

internal fun deviceDslSourceReportJson(
    requestId: String,
    packageName: String,
    processId: Int,
    generatedAtEpochMillis: Long,
    sessions: List<DeviceDslSourceSessionSnapshot>,
): String {
    return DeviceDslSourceReport(
        requestId = requestId,
        packageName = packageName,
        processId = processId,
        generatedAtEpochMillis = generatedAtEpochMillis,
        sessions = sessions,
    ).toJson()
}

private fun DeviceDslSourceReport.toJson(): String = buildString {
    append('{')
    append("\"protocolVersion\":")
    append(DEVICE_DSL_SOURCE_PROTOCOL_VERSION)
    append(",\"requestId\":")
    appendJsonString(requestId)
    append(",\"packageName\":")
    appendJsonString(packageName)
    append(",\"processId\":")
    append(processId)
    append(",\"generatedAtEpochMillis\":")
    append(generatedAtEpochMillis)
    append(",\"sessions\":[")
    var emittedSessions = 0
    sessions.forEach { session ->
        val encodedSession = session.toBoundedJson()
        val separatorBytes = if (emittedSessions == 0) 0 else 1
        val closingBytes = 2
        if (
            utf8Size() + separatorBytes + encodedSession.utf8Size() + closingBytes >
            MAX_REPORT_BYTES
        ) {
            return@forEach
        }
        if (emittedSessions > 0) append(',')
        append(encodedSession)
        emittedSessions += 1
    }
    append("]}")
}

private fun DeviceDslSourceSessionSnapshot.toBoundedJson(): String = buildString {
    append('{')
    append("\"sessionId\":")
    append(sessionId)
    append(",\"renderingActive\":")
    append(renderingActive)
    append(",\"attachedToWindow\":")
    append(attachedToWindow)
    append(",\"shown\":")
    append(shown)
    append(",\"hasWindowFocus\":")
    append(hasWindowFocus)
    append(",\"windowVisibility\":")
    append(windowVisibility)
    append(",\"viewDepth\":")
    append(viewDepth)
    append(",\"sourceCandidates\":[")
    var emittedCandidates = 0
    sourceCandidates.preferredResponseOrder().forEach { candidate ->
        val encodedCandidate = candidate.toBoundedJson() ?: return@forEach
        val separatorBytes = if (emittedCandidates == 0) 0 else 1
        val closingBytes = 2
        if (
            utf8Size() + separatorBytes + encodedCandidate.utf8Size() + closingBytes >
            MAX_SESSION_BYTES
        ) {
            return@forEach
        }
        if (emittedCandidates > 0) append(',')
        append(encodedCandidate)
        emittedCandidates += 1
    }
    append("]}")
}

private fun List<UiSourceCallSite>.toBoundedJson(): String? {
    val encoded = buildString {
        append("{\"callSites\":[")
        var emittedCallSites = 0
        this@toBoundedJson.forEach { source ->
            val encodedSource = source.toBoundedJson()
            val separatorBytes = if (emittedCallSites == 0) 0 else 1
            val closingBytes = 2
            if (
                utf8Size() + separatorBytes + encodedSource.utf8Size() + closingBytes >
                MAX_CANDIDATE_BYTES
            ) {
                return@forEach
            }
            if (emittedCallSites > 0) append(',')
            append(encodedSource)
            emittedCallSites += 1
        }
        append("]}")
    }
    return encoded.takeIf { json -> json != "{\"callSites\":[]}" }
}

private fun UiSourceCallSite.toBoundedJson(): String = buildString {
    append('{')
    append("\"className\":")
    appendJsonString(className.take(MAX_SOURCE_STRING_LENGTH))
    append(",\"methodName\":")
    appendJsonString(methodName.take(MAX_SOURCE_STRING_LENGTH))
    append(",\"fileName\":")
    appendJsonString(fileName.take(MAX_SOURCE_STRING_LENGTH))
    append(",\"lineNumber\":")
    append(lineNumber)
    append('}')
}

private fun List<List<UiSourceCallSite>>.preferredResponseOrder(): List<List<UiSourceCallSite>> {
    return (takeLast(RESPONSE_RECENT_CANDIDATES) + take(RESPONSE_FIRST_CANDIDATES)).distinct()
}

private fun List<List<UiSourceCallSite>>.boundedSourceCandidates(): List<List<UiSourceCallSite>> {
    return asSequence()
        .map { candidate ->
            candidate.asSequence()
                .filter { source -> source.lineNumber > 0 }
                .take(MAX_SOURCE_CALL_SITES_PER_CANDIDATE)
                .map { source ->
                    source.copy(
                        className = source.className.take(MAX_SOURCE_STRING_LENGTH),
                        methodName = source.methodName.take(MAX_SOURCE_STRING_LENGTH),
                        fileName = source.fileName.take(MAX_SOURCE_STRING_LENGTH),
                    )
                }
                .toList()
        }
        .filter(List<UiSourceCallSite>::isNotEmpty)
        .distinct()
        .take(MAX_SOURCE_CANDIDATES)
        .toList()
}

internal fun StringBuilder.appendJsonString(value: String) {
    append('"')
    value.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
    append('"')
}

internal fun CharSequence.utf8Size(): Int = utf8Size(0, length)

internal fun CharSequence.utf8Size(
    startIndex: Int,
    endIndex: Int,
): Int {
    require(startIndex in 0..endIndex && endIndex <= length)
    var byteCount = 0
    var index = startIndex
    while (index < endIndex) {
        val character = this[index]
        byteCount += when {
            character.code <= 0x7f -> 1
            character.code <= 0x7ff -> 2
            character.isHighSurrogate() &&
                index + 1 < endIndex &&
                this[index + 1].isLowSurrogate() -> {
                index += 1
                4
            }
            character.isSurrogate() -> 1
            else -> 3
        }
        index += 1
    }
    return byteCount
}

private fun View.depthInHierarchy(): Int {
    var depth = 0
    var current = parent
    while (current is View) {
        depth += 1
        current = current.parent
    }
    return depth
}

private fun View.isVisibleToUser(): Boolean {
    if (!isShown) return false
    val visibleBounds = Rect()
    return getGlobalVisibleRect(visibleBounds) &&
        visibleBounds.width() > 0 &&
        visibleBounds.height() > 0
}

internal fun Context.isDebuggableApplication(): Boolean {
    return runCatching {
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }.getOrDefault(false)
}

internal fun isValidDeviceDslSourceRequestId(requestId: String): Boolean {
    return requestId.length == REQUEST_ID_LENGTH &&
        requestId.all { character -> character in 'a'..'f' || character in '0'..'9' }
}

internal object DeviceDslSourceResponseWriter {
    val executor: Executor by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ViewCompose-DeviceDslResponse").apply { isDaemon = true }
        }
    }

    fun execute(runnable: Runnable) {
        executor.execute(runnable)
    }

    fun write(
        file: File,
        content: String,
    ) {
        val parent = checkNotNull(file.parentFile)
        check(parent.isDirectory || parent.mkdirs()) {
            "Unable to create device DSL source response directory."
        }
        val temporary = File(parent, ".${file.name}.${System.nanoTime()}.tmp")
        try {
            temporary.writeText(content, Charsets.UTF_8)
            if (!temporary.renameTo(file)) {
                file.delete()
                check(temporary.renameTo(file)) {
                    "Unable to replace device DSL source response."
                }
            }
        } finally {
            temporary.delete()
        }
    }
}

internal object AndroidDeviceToolingDebugGate {
    private val debuggable = AtomicBoolean(false)

    fun markAndGet(context: Context): Boolean {
        if (debuggable.get()) return true
        val permitted = context.isDebuggableApplication()
        if (permitted) debuggable.set(true)
        return permitted
    }

    fun isDebuggable(): Boolean = debuggable.get()

    internal fun resetForTest() {
        debuggable.set(false)
    }
}

private const val REQUEST_ID_LENGTH = 32
private const val TAG = "ViewCompose"
private const val MAX_SOURCE_CANDIDATES = 32
private const val MAX_SOURCE_CALL_SITES_PER_CANDIDATE = 24
private const val MAX_TRACKED_SESSIONS = 64
private const val MAX_REPORTED_SESSIONS = 64
private const val MAX_REPORT_BYTES = 256 * 1024
private const val MAX_SESSION_BYTES = 48 * 1024
private const val MAX_CANDIDATE_BYTES = 12 * 1024
private const val MAX_SOURCE_STRING_LENGTH = 1024
private const val RESPONSE_FIRST_CANDIDATES = 16
private const val RESPONSE_RECENT_CANDIDATES = 16
