package com.viewcompose.host.android.tooling

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.viewcompose.ui.foundation.RenderSessionSourceRegistration
import com.viewcompose.ui.tooling.UiSourceCallSite
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal const val DEVICE_DSL_SOURCE_REPORT_RELATIVE_PATH =
    "viewcompose/device-dsl-source-v2.json"
internal const val DEVICE_DSL_SOURCE_PROTOCOL_VERSION = 2

internal object AndroidDeviceDslSourceRegistry {
    private val nextSessionId = AtomicLong(0L)
    private val sessions = linkedMapOf<Long, DeviceDslSourceSession>()
    private var lastReportFile: File? = null

    fun shouldCapture(context: Context): Boolean {
        return runCatching {
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        }.getOrDefault(false)
    }

    fun register(
        container: ViewGroup,
        sourceCandidates: List<List<UiSourceCallSite>>,
    ): AndroidDeviceDslSourceRegistration? {
        if (!shouldCapture(container.context)) return null
        val boundedCandidates = sourceCandidates
            .asSequence()
            .map { candidate ->
                candidate
                    .asSequence()
                    .filter { source -> source.lineNumber > 0 }
                    .take(MAX_SOURCE_CALL_SITES_PER_CANDIDATE)
                    .toList()
            }
            .filter(List<UiSourceCallSite>::isNotEmpty)
            .distinct()
            .take(MAX_SOURCE_CANDIDATES)
            .toList()
        if (boundedCandidates.isEmpty()) return null
        val reportTarget = reportTarget(container.context) ?: return null
        val sessionId = nextSessionId.incrementAndGet()
        val listener = DeviceDslSourceViewListener(sessionId, container)
        synchronized(this) {
            sessions[sessionId] = DeviceDslSourceSession(
                id = sessionId,
                container = WeakReference(container),
                sourceCandidates = boundedCandidates,
                reportTarget = reportTarget,
                listener = listener,
            )
            lastReportFile = reportTarget.file
        }
        listener.attach()
        publish()
        return AndroidDeviceDslSourceRegistration(sessionId)
    }

    fun setRenderingActive(
        sessionId: Long,
        active: Boolean,
    ) {
        val changed = synchronized(this) {
            val session = sessions[sessionId] ?: return
            if (session.renderingActive == active) {
                false
            } else {
                session.renderingActive = active
                true
            }
        }
        if (changed) publish()
    }

    fun unregister(sessionId: Long) {
        val removed = synchronized(this) { sessions.remove(sessionId) } ?: return
        removed.listener.detach()
        publish()
    }

    fun viewStateChanged(sessionId: Long) {
        if (synchronized(this) { sessionId in sessions }) {
            publish()
        }
    }

    private fun publish() {
        val (target, report) = synchronized(this) {
            sessions.entries.removeAll { (_, session) -> session.container.get() == null }
            val target = sessions.values.lastOrNull()?.reportTarget
            if (target == null) {
                null to null
            } else {
                val snapshots = sessions.values
                    .filter { session -> session.reportTarget == target }
                    .takeLast(MAX_REPORTED_SESSIONS)
                    .mapNotNull(DeviceDslSourceSession::snapshot)
                target to DeviceDslSourceReport(
                    packageName = target.packageName,
                    processId = Process.myPid(),
                    generatedAtEpochMillis = System.currentTimeMillis(),
                    sessions = snapshots,
                )
            }
        }
        if (target == null || report == null) {
            lastReportFile?.let(DeviceDslSourceReportWriter::deleteAsync)
        } else {
            DeviceDslSourceReportWriter.writeAsync(target.file, report.toJson())
        }
    }

    private fun reportTarget(context: Context): DeviceDslSourceReportTarget? {
        val appContext = context.applicationContext ?: context
        return runCatching {
            val packageName = appContext.packageName.takeIf(String::isNotBlank)
                ?: return@runCatching null
            DeviceDslSourceReportTarget(
                packageName = packageName,
                file = File(appContext.cacheDir, DEVICE_DSL_SOURCE_REPORT_RELATIVE_PATH),
            )
        }.getOrNull()
    }

    private const val MAX_SOURCE_CANDIDATES = 32
    private const val MAX_SOURCE_CALL_SITES_PER_CANDIDATE = 24
    private const val MAX_REPORTED_SESSIONS = 64
}

internal class AndroidDeviceDslSourceRegistration(
    private val sessionId: Long,
) : RenderSessionSourceRegistration {
    private val disposed = AtomicBoolean(false)

    override fun setRenderingActive(active: Boolean) {
        if (!disposed.get()) {
            AndroidDeviceDslSourceRegistry.setRenderingActive(sessionId, active)
        }
    }

    override fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            AndroidDeviceDslSourceRegistry.unregister(sessionId)
        }
    }
}

private data class DeviceDslSourceReportTarget(
    val packageName: String,
    val file: File,
)

private class DeviceDslSourceSession(
    val id: Long,
    val container: WeakReference<ViewGroup>,
    val sourceCandidates: List<List<UiSourceCallSite>>,
    val reportTarget: DeviceDslSourceReportTarget,
    val listener: DeviceDslSourceViewListener,
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

private class DeviceDslSourceViewListener(
    private val sessionId: Long,
    container: ViewGroup,
) : View.OnAttachStateChangeListener,
    ViewTreeObserver.OnWindowFocusChangeListener,
    ViewTreeObserver.OnScrollChangedListener,
    ViewTreeObserver.OnGlobalLayoutListener {
    private val container = WeakReference(container)
    private var observedTree: ViewTreeObserver? = null

    fun attach() {
        val view = container.get() ?: return
        view.addOnAttachStateChangeListener(this)
        observeWindowFocus(view)
    }

    fun detach() {
        val view = container.get()
        view?.removeOnAttachStateChangeListener(this)
        observedTree?.takeIf(ViewTreeObserver::isAlive)?.removeOnWindowFocusChangeListener(this)
        observedTree?.takeIf(ViewTreeObserver::isAlive)?.removeOnScrollChangedListener(this)
        observedTree?.takeIf(ViewTreeObserver::isAlive)?.removeOnGlobalLayoutListener(this)
        observedTree = null
    }

    override fun onViewAttachedToWindow(view: View) {
        observeWindowFocus(view)
        AndroidDeviceDslSourceRegistry.viewStateChanged(sessionId)
    }

    override fun onViewDetachedFromWindow(view: View) {
        observedTree?.takeIf(ViewTreeObserver::isAlive)?.removeOnWindowFocusChangeListener(this)
        observedTree?.takeIf(ViewTreeObserver::isAlive)?.removeOnScrollChangedListener(this)
        observedTree?.takeIf(ViewTreeObserver::isAlive)?.removeOnGlobalLayoutListener(this)
        observedTree = null
        AndroidDeviceDslSourceRegistry.viewStateChanged(sessionId)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        AndroidDeviceDslSourceRegistry.viewStateChanged(sessionId)
    }

    override fun onScrollChanged() {
        AndroidDeviceDslSourceRegistry.viewStateChanged(sessionId)
    }

    override fun onGlobalLayout() {
        AndroidDeviceDslSourceRegistry.viewStateChanged(sessionId)
    }

    private fun observeWindowFocus(view: View) {
        val nextTree = view.viewTreeObserver
        if (observedTree === nextTree) return
        observedTree?.takeIf(ViewTreeObserver::isAlive)?.removeOnWindowFocusChangeListener(this)
        observedTree?.takeIf(ViewTreeObserver::isAlive)?.removeOnScrollChangedListener(this)
        observedTree?.takeIf(ViewTreeObserver::isAlive)?.removeOnGlobalLayoutListener(this)
        if (nextTree.isAlive) {
            nextTree.addOnWindowFocusChangeListener(this)
            nextTree.addOnScrollChangedListener(this)
            nextTree.addOnGlobalLayoutListener(this)
            observedTree = nextTree
        } else {
            observedTree = null
        }
    }
}

private data class DeviceDslSourceReport(
    val packageName: String,
    val processId: Int,
    val generatedAtEpochMillis: Long,
    val sessions: List<DeviceDslSourceSessionSnapshot>,
)

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

internal fun deviceDslSourceReportJson(
    packageName: String,
    processId: Int,
    generatedAtEpochMillis: Long,
    sessions: List<DeviceDslSourceSessionSnapshot>,
): String {
    return DeviceDslSourceReport(
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
    append(",\"packageName\":")
    appendJsonString(packageName)
    append(",\"processId\":")
    append(processId)
    append(",\"generatedAtEpochMillis\":")
    append(generatedAtEpochMillis)
    append(",\"sessions\":[")
    sessions.forEachIndexed { sessionIndex, session ->
        if (sessionIndex > 0) append(',')
        append('{')
        append("\"sessionId\":")
        append(session.sessionId)
        append(",\"renderingActive\":")
        append(session.renderingActive)
        append(",\"attachedToWindow\":")
        append(session.attachedToWindow)
        append(",\"shown\":")
        append(session.shown)
        append(",\"hasWindowFocus\":")
        append(session.hasWindowFocus)
        append(",\"windowVisibility\":")
        append(session.windowVisibility)
        append(",\"viewDepth\":")
        append(session.viewDepth)
        append(",\"sourceCandidates\":[")
        session.sourceCandidates.forEachIndexed { candidateIndex, candidate ->
            if (candidateIndex > 0) append(',')
            append("{\"callSites\":[")
            candidate.forEachIndexed { sourceIndex, source ->
                if (sourceIndex > 0) append(',')
                append('{')
                append("\"className\":")
                appendJsonString(source.className)
                append(",\"methodName\":")
                appendJsonString(source.methodName)
                append(",\"fileName\":")
                appendJsonString(source.fileName)
                append(",\"lineNumber\":")
                append(source.lineNumber)
                append('}')
            }
            append("]}")
        }
        append("]}")
    }
    append("]}")
}

private fun StringBuilder.appendJsonString(value: String) {
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

private object DeviceDslSourceReportWriter {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ViewCompose-DeviceDslSource").apply { isDaemon = true }
    }
    private val pendingRequest = AtomicReference<DeviceDslSourceFileRequest?>()
    private val drainScheduled = AtomicBoolean(false)

    fun writeAsync(
        file: File,
        content: String,
    ) {
        submit(DeviceDslSourceFileRequest(file, content))
    }

    fun deleteAsync(file: File) {
        submit(DeviceDslSourceFileRequest(file, content = null))
    }

    private fun submit(request: DeviceDslSourceFileRequest) {
        pendingRequest.set(request)
        if (drainScheduled.compareAndSet(false, true)) {
            executor.execute(::drain)
        }
    }

    private fun drain() {
        while (true) {
            val request = pendingRequest.getAndSet(null) ?: break
            runCatching { request.apply() }
        }
        drainScheduled.set(false)
        if (
            pendingRequest.get() != null &&
            drainScheduled.compareAndSet(false, true)
        ) {
            executor.execute(::drain)
        }
    }
}

private data class DeviceDslSourceFileRequest(
    val file: File,
    val content: String?,
) {
    fun apply() {
        if (content == null) {
            file.delete()
            return
        }
        val parent = checkNotNull(file.parentFile)
        parent.mkdirs()
        val temporary = File(parent, ".${file.name}.${System.nanoTime()}.tmp")
        try {
            temporary.writeText(content, Charsets.UTF_8)
            if (!temporary.renameTo(file)) {
                file.delete()
                check(temporary.renameTo(file)) {
                    "Unable to replace device DSL source report."
                }
            }
        } finally {
            temporary.delete()
        }
    }
}
