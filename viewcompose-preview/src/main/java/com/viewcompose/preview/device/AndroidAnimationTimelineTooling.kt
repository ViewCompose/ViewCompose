package com.viewcompose.preview.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import com.viewcompose.animation.tooling.AnimationTimelineChannelSnapshot
import com.viewcompose.animation.tooling.AnimationTimelineRegistration
import com.viewcompose.animation.tooling.AnimationTimelineSnapshot
import com.viewcompose.animation.tooling.AnimationTimelineSource
import com.viewcompose.animation.tooling.AnimationTimelineStateSummary
import com.viewcompose.animation.tooling.AnimationTimelineTooling
import com.viewcompose.animation.tooling.AnimationTimelineValue
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

internal const val ANIMATION_TIMELINE_REQUEST_ACTION =
    "com.viewcompose.preview.action.REQUEST_ANIMATION_TIMELINE"
internal const val ANIMATION_TIMELINE_REQUEST_ID_EXTRA = "request_id"
internal const val ANIMATION_TIMELINE_MODE_EXTRA = "mode"
internal const val ANIMATION_TIMELINE_TRANSITION_ID_EXTRA = "transition_id"
internal const val ANIMATION_TIMELINE_DISCOVER_MODE = "discover"
internal const val ANIMATION_TIMELINE_CAPTURE_MODE = "capture"
internal const val ANIMATION_TIMELINE_REPORT_RELATIVE_PATH =
    "viewcompose/animation-timeline-v1.json"
internal const val ANIMATION_TIMELINE_PROTOCOL_VERSION = 1

/** Optional read-only animation port installed only from the Preview artifact. */
internal class AndroidAnimationTimelineTooling : AnimationTimelineTooling {
    override fun register(source: AnimationTimelineSource): AnimationTimelineRegistration? {
        // Registration is intentionally only a bounded weak reference. Keeping it before the
        // first receiver request lets an already composed Transition be discovered without
        // forcing recomposition; snapshots and report work remain behind the receiver's
        // debuggable-process and explicit-request gates.
        return AndroidAnimationTimelineRuntime.registry.register(source)
    }
}

/** Receives one bounded discovery or selected-transition capture request from ADB shell. */
internal class AnimationTimelineRequestReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ANIMATION_TIMELINE_REQUEST_ACTION) return
        if (!AndroidDeviceToolingDebugGate.markAndGet(context)) return
        val requestId = intent.getStringExtra(ANIMATION_TIMELINE_REQUEST_ID_EXTRA)
            ?.takeIf(::isValidDeviceDslSourceRequestId)
            ?: return
        val mode = intent.getStringExtra(ANIMATION_TIMELINE_MODE_EXTRA)
            ?.takeIf { candidate ->
                candidate == ANIMATION_TIMELINE_DISCOVER_MODE ||
                    candidate == ANIMATION_TIMELINE_CAPTURE_MODE
            }
            ?: return
        val transitionId = intent.getStringExtra(ANIMATION_TIMELINE_TRANSITION_ID_EXTRA)
            ?.takeIf(::isValidAnimationTimelineIdentity)
        if (mode == ANIMATION_TIMELINE_CAPTURE_MODE && transitionId == null) return

        val pendingResult = goAsync()
        val inspect = Runnable {
            try {
                AndroidAnimationTimelineRuntime.requestHandler.handle(
                    context = context,
                    requestId = requestId,
                    mode = mode,
                    transitionId = transitionId,
                    onFinished = pendingResult::finish,
                )
            } catch (error: Exception) {
                pendingResult.finish()
                Log.w(
                    TIMELINE_TAG,
                    "Animation timeline request failed without affecting the application.",
                    error,
                )
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            inspect.run()
        } else {
            Handler(Looper.getMainLooper()).post(inspect)
        }
    }
}

private object AndroidAnimationTimelineRuntime {
    val registry = AndroidAnimationTimelineRegistry()
    val requestHandler = AnimationTimelineRequestHandler(registry)
}

internal class AndroidAnimationTimelineRegistry(
    private val processId: () -> Int = Process::myPid,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val sources = linkedMapOf<String, WeakReference<AnimationTimelineSource>>()
    private var activeCapture: ActiveTimelineCapture? = null
    private var nextCaptureToken: Long = 0L
    @Volatile
    private var requestedTransitionId: String? = null

    fun register(source: AnimationTimelineSource): AnimationTimelineRegistration {
        synchronized(this) {
            pruneLocked()
            sources[source.identity] = WeakReference(source)
            while (sources.size > MAX_TRACKED_TIMELINES) {
                sources.remove(sources.keys.first())
            }
        }
        return RegistryAnimationTimelineRegistration(this, source.identity)
    }

    fun discover(
        packageName: String,
        requestId: String,
    ): AnimationTimelineReport {
        requireMainThread()
        val transitions = synchronized(this) {
            pruneLocked()
            sources.values
                .mapNotNull(WeakReference<AnimationTimelineSource>::get)
                .takeLast(MAX_REPORTED_TIMELINES)
                .mapNotNull { source ->
                    runCatching {
                        AnimationTimelineCapture(
                            identity = source.identity,
                            label = source.label,
                            samples = listOf(source.snapshot()),
                        )
                    }.getOrNull()
                }
        }
        return report(
            packageName = packageName,
            requestId = requestId,
            mode = AnimationTimelineReportMode.Discover,
            status = AnimationTimelineReportStatus.Success,
            transitions = transitions,
        )
    }

    fun beginCapture(
        packageName: String,
        requestId: String,
        transitionId: String,
    ): TimelineCaptureStart {
        requireMainThread()
        return synchronized(this) {
            pruneLocked()
            if (activeCapture != null) {
                return@synchronized TimelineCaptureStart.Immediate(
                    report(
                        packageName = packageName,
                        requestId = requestId,
                        mode = AnimationTimelineReportMode.Capture,
                        status = AnimationTimelineReportStatus.Busy,
                        transitions = emptyList(),
                    ),
                )
            }
            val source = sources[transitionId]?.get()
                ?: return@synchronized TimelineCaptureStart.Immediate(
                    report(
                        packageName = packageName,
                        requestId = requestId,
                        mode = AnimationTimelineReportMode.Capture,
                        status = AnimationTimelineReportStatus.Missing,
                        transitions = emptyList(),
                    ),
                )
            val token = ++nextCaptureToken
            val capture = ActiveTimelineCapture(
                token = token,
                packageName = packageName,
                requestId = requestId,
                transitionId = transitionId,
                label = source.label,
            )
            runCatching(source::snapshot).getOrNull()?.let(capture::record)
            activeCapture = capture
            requestedTransitionId = transitionId
            TimelineCaptureStart.Active(token)
        }
    }

    fun finishCapture(
        token: Long,
        packageName: String,
        requestId: String,
    ): AnimationTimelineReport {
        requireMainThread()
        return synchronized(this) {
            val capture = activeCapture?.takeIf { candidate -> candidate.token == token }
                ?: return@synchronized report(
                    packageName = packageName,
                    requestId = requestId,
                    mode = AnimationTimelineReportMode.Capture,
                    status = AnimationTimelineReportStatus.Stale,
                    transitions = emptyList(),
                )
            sources[capture.transitionId]?.get()?.let { source ->
                runCatching(source::snapshot).getOrNull()?.let(capture::record)
            }
            activeCapture = null
            requestedTransitionId = null
            report(
                packageName = capture.packageName,
                requestId = capture.requestId,
                mode = AnimationTimelineReportMode.Capture,
                status = AnimationTimelineReportStatus.Success,
                transitions = listOf(
                    AnimationTimelineCapture(
                        identity = capture.transitionId,
                        label = capture.label,
                        samples = capture.samples.toList(),
                    ),
                ),
            )
        }
    }

    fun captureRequested(transitionId: String): Boolean = requestedTransitionId == transitionId

    fun record(
        transitionId: String,
        snapshot: AnimationTimelineSnapshot,
    ) {
        synchronized(this) {
            activeCapture
                ?.takeIf { capture -> capture.transitionId == transitionId }
                ?.let { capture ->
                    capture.record(snapshot)
                    if (capture.samples.size >= MAX_TIMELINE_SAMPLES) {
                        requestedTransitionId = null
                    }
                }
        }
    }

    fun unregister(transitionId: String) {
        synchronized(this) {
            sources.remove(transitionId)
        }
    }

    internal fun sourceCountForTest(): Int = synchronized(this) {
        pruneLocked()
        sources.size
    }

    private fun report(
        packageName: String,
        requestId: String,
        mode: AnimationTimelineReportMode,
        status: AnimationTimelineReportStatus,
        transitions: List<AnimationTimelineCapture>,
    ): AnimationTimelineReport {
        return AnimationTimelineReport(
            requestId = requestId,
            packageName = packageName,
            processId = processId(),
            generatedAtEpochMillis = currentTimeMillis(),
            mode = mode,
            status = status,
            transitions = transitions,
        )
    }

    private fun pruneLocked() {
        sources.entries.removeAll { (_, source) -> source.get() == null }
    }

    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Animation timeline inspection must run on the Android main thread."
        }
    }
}

internal sealed interface TimelineCaptureStart {
    data class Active(val token: Long) : TimelineCaptureStart

    data class Immediate(val report: AnimationTimelineReport) : TimelineCaptureStart
}

internal class AnimationTimelineRequestHandler(
    private val registry: AndroidAnimationTimelineRegistry,
    private val postDelayed: (Long, Runnable) -> Unit = { delayMillis, action ->
        Handler(Looper.getMainLooper()).postDelayed(action, delayMillis)
    },
    private val execute: (Runnable) -> Unit = DeviceDslSourceResponseWriter::execute,
    private val writeResponse: (File, String) -> Unit = DeviceDslSourceResponseWriter::write,
) {
    fun handle(
        context: Context,
        requestId: String,
        mode: String,
        transitionId: String?,
        onFinished: () -> Unit,
    ) {
        check(isValidDeviceDslSourceRequestId(requestId)) {
            "Animation timeline request ID is invalid."
        }
        val appContext = context.applicationContext ?: context
        val packageName = appContext.packageName
        val target = File(appContext.cacheDir, ANIMATION_TIMELINE_REPORT_RELATIVE_PATH)
        when (mode) {
            ANIMATION_TIMELINE_DISCOVER_MODE -> publish(
                target = target,
                report = registry.discover(packageName, requestId),
                onFinished = onFinished,
            )
            ANIMATION_TIMELINE_CAPTURE_MODE -> {
                val identity = checkNotNull(transitionId) {
                    "Animation timeline capture requires a transition identity."
                }
                check(isValidAnimationTimelineIdentity(identity)) {
                    "Animation timeline transition identity is invalid."
                }
                when (val start = registry.beginCapture(packageName, requestId, identity)) {
                    is TimelineCaptureStart.Immediate -> publish(
                        target = target,
                        report = start.report,
                        onFinished = onFinished,
                    )
                    is TimelineCaptureStart.Active -> postDelayed(
                        CAPTURE_DURATION_MILLIS,
                        Runnable {
                            publish(
                                target = target,
                                report = registry.finishCapture(
                                    token = start.token,
                                    packageName = packageName,
                                    requestId = requestId,
                                ),
                                onFinished = onFinished,
                            )
                        },
                    )
                }
            }
            else -> error("Unsupported animation timeline request mode '$mode'.")
        }
    }

    private fun publish(
        target: File,
        report: AnimationTimelineReport,
        onFinished: () -> Unit,
    ) {
        execute(
            Runnable {
                try {
                    runCatching { writeResponse(target, report.toJson()) }
                        .onFailure { error ->
                            Log.w(TIMELINE_TAG, "Animation timeline response could not be written.", error)
                        }
                } finally {
                    onFinished()
                }
            },
        )
    }
}

private class RegistryAnimationTimelineRegistration(
    private val registry: AndroidAnimationTimelineRegistry,
    private val transitionId: String,
) : AnimationTimelineRegistration {
    private val disposed = AtomicBoolean(false)

    override fun captureRequested(): Boolean {
        return !disposed.get() && registry.captureRequested(transitionId)
    }

    override fun record(snapshot: AnimationTimelineSnapshot) {
        if (!disposed.get() && snapshot.identity == transitionId) {
            registry.record(transitionId, snapshot)
        }
    }

    override fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            registry.unregister(transitionId)
        }
    }
}

private class ActiveTimelineCapture(
    val token: Long,
    val packageName: String,
    val requestId: String,
    val transitionId: String,
    val label: String,
) {
    val samples = mutableListOf<AnimationTimelineSnapshot>()

    fun record(snapshot: AnimationTimelineSnapshot) {
        val lastIndex = samples.lastIndex
        val last = samples.getOrNull(lastIndex)
        if (
            last != null &&
            last.segmentVersion == snapshot.segmentVersion &&
            last.playTimeNanos == snapshot.playTimeNanos
        ) {
            samples[lastIndex] = snapshot
        } else if (samples.size < MAX_TIMELINE_SAMPLES && last != snapshot) {
            samples.add(snapshot)
        }
    }
}

internal enum class AnimationTimelineReportMode {
    Discover,
    Capture,
}

internal enum class AnimationTimelineReportStatus {
    Success,
    Missing,
    Busy,
    Stale,
}

internal data class AnimationTimelineCapture(
    val identity: String,
    val label: String,
    val samples: List<AnimationTimelineSnapshot>,
)

internal data class AnimationTimelineReport(
    val requestId: String,
    val packageName: String,
    val processId: Int,
    val generatedAtEpochMillis: Long,
    val mode: AnimationTimelineReportMode,
    val status: AnimationTimelineReportStatus,
    val transitions: List<AnimationTimelineCapture>,
)

private fun AnimationTimelineReport.toJson(): String = buildString {
    append('{')
    append("\"protocolVersion\":")
    append(ANIMATION_TIMELINE_PROTOCOL_VERSION)
    append(",\"requestId\":")
    appendJsonString(requestId)
    append(",\"packageName\":")
    appendJsonString(packageName)
    append(",\"processId\":")
    append(processId)
    append(",\"generatedAtEpochMillis\":")
    append(generatedAtEpochMillis)
    append(",\"mode\":")
    appendJsonString(mode.name.lowercase())
    append(",\"status\":")
    appendJsonString(status.name.lowercase())
    append(",\"transitions\":[")
    var writtenBytes = utf8Size()
    var emitted = 0
    transitions.forEach { transition ->
        val itemStart = length
        if (emitted > 0) append(',')
        appendTimelineCapture(transition)
        val encodedBytes = utf8Size(itemStart, length)
        if (
            writtenBytes + encodedBytes + JSON_ARRAY_OBJECT_SUFFIX_BYTES >
            MAX_TIMELINE_REPORT_BYTES
        ) {
            delete(itemStart, length)
            return@forEach
        }
        writtenBytes += encodedBytes
        emitted += 1
    }
    append("]}")
}

private fun StringBuilder.appendTimelineCapture(capture: AnimationTimelineCapture) {
    val captureStart = length
    append('{')
    append("\"identity\":")
    appendJsonString(capture.identity.take(MAX_TIMELINE_STRING_LENGTH))
    append(",\"label\":")
    appendJsonString(capture.label.take(MAX_TIMELINE_STRING_LENGTH))
    append(",\"samples\":[")
    var writtenBytes = utf8Size(captureStart, length)
    var emitted = 0
    capture.samples.forEach { sample ->
        val itemStart = length
        if (emitted > 0) append(',')
        appendTimelineSnapshot(sample)
        val encodedBytes = utf8Size(itemStart, length)
        if (
            writtenBytes + encodedBytes + JSON_ARRAY_OBJECT_SUFFIX_BYTES >
            MAX_TIMELINE_CAPTURE_BYTES
        ) {
            delete(itemStart, length)
            return@forEach
        }
        writtenBytes += encodedBytes
        emitted += 1
    }
    append("]}")
}

private fun StringBuilder.appendTimelineSnapshot(snapshot: AnimationTimelineSnapshot) {
    append('{')
    append("\"identity\":")
    appendJsonString(snapshot.identity)
    append(",\"label\":")
    appendJsonString(snapshot.label)
    append(",\"currentState\":")
    appendTimelineStateSummary(snapshot.currentState)
    append(",\"targetState\":")
    appendTimelineStateSummary(snapshot.targetState)
    append(",\"segmentInitialState\":")
    appendTimelineStateSummary(snapshot.segmentInitialState)
    append(",\"segmentTargetState\":")
    appendTimelineStateSummary(snapshot.segmentTargetState)
    append(",\"segmentVersion\":")
    append(snapshot.segmentVersion)
    append(",\"playTimeNanos\":")
    append(snapshot.playTimeNanos)
    append(",\"durationNanos\":")
    append(snapshot.durationNanos)
    append(",\"runState\":")
    appendJsonString(snapshot.runState.name.lowercase())
    append(",\"channels\":[")
    snapshot.channels.forEachIndexed { index, channel ->
        if (index > 0) append(',')
        appendTimelineChannel(channel)
    }
    append("]}")
}

private fun StringBuilder.appendTimelineStateSummary(summary: AnimationTimelineStateSummary) {
    val safeDisplayValue = summary.displayValue
    append("{\"typeName\":")
    appendJsonString(summary.typeName)
    append(",\"displayValue\":")
    if (safeDisplayValue == null) append("null") else appendJsonString(safeDisplayValue)
    append('}')
}

private fun StringBuilder.appendTimelineChannel(channel: AnimationTimelineChannelSnapshot) {
    append('{')
    append("\"identity\":")
    appendJsonString(channel.identity)
    append(",\"name\":")
    appendJsonString(channel.name)
    append(",\"specFamily\":")
    appendJsonString(channel.specFamily.name.lowercase())
    append(",\"startValue\":")
    appendNullableValue(channel.startValue)
    append(",\"currentValue\":")
    appendNullableValue(channel.currentValue)
    append(",\"targetValue\":")
    appendNullableValue(channel.targetValue)
    append(",\"velocity\":")
    appendNullableValue(channel.velocity)
    append(",\"durationNanos\":")
    append(channel.durationNanos)
    append(",\"finished\":")
    append(channel.finished)
    append(",\"terminalCondition\":")
    appendJsonString(channel.terminalCondition.name.lowercase())
    append('}')
}

private fun StringBuilder.appendNullableValue(value: AnimationTimelineValue?) {
    if (value == null) {
        append("null")
        return
    }
    append("{\"kind\":")
    appendJsonString(value.kind.name.lowercase())
    append(",\"components\":[")
    value.components.forEachIndexed { index, component ->
        if (index > 0) append(',')
        append(component)
    }
    append("]}")
}

internal fun isValidAnimationTimelineIdentity(identity: String): Boolean {
    return identity.length in 1..MAX_TIMELINE_STRING_LENGTH &&
        identity.all { character -> character.isLetterOrDigit() || character == '-' }
}

private const val TIMELINE_TAG = "ViewCompose"
private const val CAPTURE_DURATION_MILLIS = 500L
private const val MAX_TRACKED_TIMELINES = 64
private const val MAX_REPORTED_TIMELINES = 64
private const val MAX_TIMELINE_SAMPLES = 64
private const val MAX_TIMELINE_REPORT_BYTES = 256 * 1024
private const val MAX_TIMELINE_CAPTURE_BYTES = 192 * 1024
private const val MAX_TIMELINE_STRING_LENGTH = 256
private const val JSON_ARRAY_OBJECT_SUFFIX_BYTES = 2
