package com.viewcompose.ui.foundation

import com.viewcompose.runtime.composition.CompositionTimingCollector
import com.viewcompose.runtime.composition.CompositionTimingScope
import com.viewcompose.runtime.composition.CompositionTimingSpan
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.tooling.UiSourceCallSite
import java.lang.ref.WeakReference

/** Phase represented by one finite node-timing record. */
enum class RenderNodeTimingPhase {
    /** Declarative composition-scope evaluation. */
    Composition,

    /** Child matching, patch planning, and structural reconciliation. */
    Reconciliation,

    /** Direct native View creation, modifier binding, full binding, or patching. */
    Binding,
}

/** Inclusion rule used by one duration. */
enum class RenderNodeTimingInclusion {
    /** Complete interval including accepted descendant and nested-phase intervals. */
    Inclusive,

    /** Inclusive duration minus accepted nested intervals. */
    Self,

    /** One direct native binding interval; children are never included. */
    Direct,
}

/** Clock contract used by all records in one capture. */
enum class RenderNodeTimingClock {
    /** Process-local monotonic nanoseconds with no wall-clock meaning. */
    MonotonicNanoseconds,
}

/** Work deliberately excluded from the first node-timing contract. */
enum class RenderNodeTimingUnsupportedDomain {
    /** Android View measurement and layout passes. */
    MeasureAndLayout,

    /** View or drawable drawing on the UI thread. */
    Draw,

    /** GPU execution. */
    Gpu,

    /** Android RenderThread execution. */
    RenderThread,

    /** SurfaceFlinger composition. */
    SurfaceFlinger,

    /** Image, media, or other payload decoding. */
    Decode,

    /** Network requests and transfer. */
    Network,

    /** Database access. */
    Database,

    /** Work owned by an external SDK outside direct renderer callbacks. */
    ExternalSdk,
}

/** Terminal reason for one finite timing capture. */
enum class RenderNodeTimingEndReason {
    /** The requested frame count completed. */
    FrameLimit,

    /** The requested monotonic duration elapsed. */
    DurationLimit,

    /** Tooling explicitly stopped the capture. */
    ExplicitStop,

    /** The logical render session ended. */
    SessionEnded,
}

/** Result of attempting to start one session timing capture. */
enum class RenderNodeTimingStartStatus {
    /** A new capture was installed. */
    Started,

    /** This logical session already owns an unfinished capture. */
    AlreadyActive,

    /** The logical session has ended. */
    EndedSession,
}

/**
 * Immutable request for one finite node-timing capture.
 *
 * Values may narrow but never exceed the ADR-0021 hard limits: eight completed frames and two
 * monotonic seconds. [phases] cannot be empty. Starting a request performs no I/O and does not
 * enable unsupported measure, draw, GPU, decoding, network, database, or SDK timing.
 *
 * @sample com.viewcompose.ui.foundation.samples.renderSessionTimingInspectionSample
 * @param phases requested supported phases
 * @param maxFrames positive completed-frame limit, at most eight
 * @param maxDurationNanos positive monotonic duration, at most two seconds
 */
class RenderNodeTimingCaptureRequest(
    phases: Set<RenderNodeTimingPhase> = RenderNodeTimingPhase.entries.toSet(),
    val maxFrames: Int = MAX_TIMING_FRAMES,
    val maxDurationNanos: Long = MAX_TIMING_DURATION_NANOS,
) {
    /** Immutable requested phase set. */
    val phases: Set<RenderNodeTimingPhase> = phases.toSet()

    init {
        require(this.phases.isNotEmpty()) { "Timing capture must request at least one phase." }
        require(maxFrames in 1..MAX_TIMING_FRAMES) {
            "Timing capture maxFrames must be in 1..$MAX_TIMING_FRAMES."
        }
        require(maxDurationNanos in 1..MAX_TIMING_DURATION_NANOS) {
            "Timing capture maxDurationNanos must be in 1..$MAX_TIMING_DURATION_NANOS."
        }
    }
}

/**
 * One bounded aggregate for a frame, node, phase, and inclusion rule.
 *
 * Durations use the capture's process-local monotonic clock and are not wall-clock timestamps.
 * Repeated intervals with the same identity are summed; [repetitions] states that count. Application
 * keys, text, semantics, state values, Locals, URLs, messages, and arbitrary `toString()` output are
 * absent.
 *
 * @property frameId render-session frame attempt associated with this aggregate
 * @property nodeToken capture-scoped opaque node identity
 * @property parentNodeToken capture-scoped parent when retained
 * @property nodeType renderer dispatch type, or `null` for a scope/virtual root
 * @property depth zero-based timing depth
 * @property synthetic whether renderer infrastructure introduced the subject
 * @property sourceCallSites bounded source hints captured without a timing-time stack trace
 * @property phase measured supported phase
 * @property inclusion duration inclusion rule
 * @property durationNanos summed raw monotonic nanoseconds
 * @property repetitions number of intervals included in [durationNanos]
 * @property truncated whether saturation or capture limits make this aggregate incomplete
 */
data class RenderNodeTimingRecord(
    val frameId: Long,
    val nodeToken: RenderNodeToken,
    val parentNodeToken: RenderNodeToken?,
    val nodeType: NodeType?,
    val depth: Int,
    val synthetic: Boolean,
    val sourceCallSites: List<UiSourceCallSite>,
    val phase: RenderNodeTimingPhase,
    val inclusion: RenderNodeTimingInclusion,
    val durationNanos: Long,
    val repetitions: Long,
    val truncated: Boolean,
)

/**
 * Immutable current or terminal capture snapshot.
 *
 * At most 512 records are retained. [attemptedClockReads] counts interval start/end reads requested
 * after phase/depth/node admission; [retainedClockReads] counts reads whose interval contributed at
 * least one retained aggregate. Calibration and capture-boundary reads are excluded from both.
 * [emptyPairOverheadNanos] is the minimum of eight back-to-back clock pairs measured at start.
 *
 * @property context owning logical session context with no application identity
 * @property records bounded aggregates in first-retained deterministic order
 * @property completedFrames frames completed while the request was active
 * @property startedAtNanos process-local monotonic capture start
 * @property endedAtNanos process-local monotonic terminal time, or `null` while active
 * @property attemptedClockReads admitted interval clock reads, including later-dropped records
 * @property retainedClockReads clock reads contributing to at least one retained record
 * @property emptyPairOverheadNanos calibrated minimum empty clock-pair cost
 * @property droppedTimedNodes distinct over-limit subjects omitted across frames
 * @property droppedRecords new aggregates omitted after the record cap
 * @property droppedStrings source strings omitted after the distinct-string cap
 * @property truncated whether any limit or arithmetic saturation made output incomplete
 * @property unsupportedDomains domains explicitly absent from this capture
 * @property complete whether the capture is terminal
 * @property endReason terminal reason, or `null` while active
 */
data class RenderNodeTimingCaptureResult(
    val context: RenderDiagnosticContext,
    val records: List<RenderNodeTimingRecord>,
    val completedFrames: Int,
    val startedAtNanos: Long,
    val endedAtNanos: Long?,
    val attemptedClockReads: Long,
    val retainedClockReads: Long,
    val emptyPairOverheadNanos: Long,
    val droppedTimedNodes: Int,
    val droppedRecords: Int,
    val droppedStrings: Int,
    val truncated: Boolean,
    val unsupportedDomains: Set<RenderNodeTimingUnsupportedDomain>,
    val complete: Boolean,
    val endReason: RenderNodeTimingEndReason?,
)

/**
 * Handle for one started finite timing request.
 *
 * [snapshot] and [stop] run on the owning platform render thread. Snapshot does no rendering; it
 * checks the duration limit and returns immutable bounded output. Stop is idempotent and prevents
 * later frames from adding records.
 */
interface RenderNodeTimingCapture {
    /** Returns current bounded output, automatically ending an expired capture. */
    fun snapshot(): RenderNodeTimingCaptureResult

    /** Explicitly ends the capture and returns its terminal output. */
    fun stop(): RenderNodeTimingCaptureResult
}

/**
 * Outcome of [RenderSessionTimingInspection.startCapture].
 *
 * @property status terminal explanation when the request was rejected, or `Started` when a handle
 * was installed
 * @property capture finite request handle when [status] is `Started`; otherwise `null`
 */
data class RenderNodeTimingCaptureStart(
    val status: RenderNodeTimingStartStatus,
    val capture: RenderNodeTimingCapture?,
)

/**
 * Q3 request-only timing control owned by one logical render session.
 *
 * Calls run on the owning platform render thread. Starting installs at most one finite collector,
 * requests one structural frame through the session's existing scheduler, and adds only a nullable
 * request-state check to inactive frames. The handle auto-stops after its requested frame or
 * duration limit and is terminal after session disposal. Tooling failures cannot alter rendering.
 *
 * Process-wide single-capture arbitration belongs to the installed debug tooling because this
 * object deliberately owns only one logical session.
 *
 * @sample com.viewcompose.ui.foundation.samples.renderSessionTimingInspectionSample
 */
interface RenderSessionTimingInspection {
    /** Starts one capture or reports the existing/ended state without replacing it. */
    fun startCapture(request: RenderNodeTimingCaptureRequest): RenderNodeTimingCaptureStart
}

internal class RenderSessionTimingState {
    var startCapture:
        ((RenderNodeTimingCaptureRequest) -> RenderNodeTimingCaptureStart)? = null
    var ended: Boolean = false
}

internal class DefaultRenderSessionTimingInspection(
    state: RenderSessionTimingState,
) : RenderSessionTimingInspection {
    private val state = WeakReference(state)

    override fun startCapture(
        request: RenderNodeTimingCaptureRequest,
    ): RenderNodeTimingCaptureStart {
        val current = state.get()
        if (current == null || current.ended) {
            return RenderNodeTimingCaptureStart(
                status = RenderNodeTimingStartStatus.EndedSession,
                capture = null,
            )
        }
        return current.startCapture?.invoke(request) ?: RenderNodeTimingCaptureStart(
            status = RenderNodeTimingStartStatus.EndedSession,
            capture = null,
        )
    }
}

internal class ActiveRenderNodeTimingCapture(
    private val request: RenderNodeTimingCaptureRequest,
    private val context: RenderDiagnosticContext,
    private val clock: () -> Long,
    private var onFinished: (() -> Unit)?,
) : RenderNodeTimingCapture, CompositionTimingCollector, CoreRenderTimingCollector {
    private val ownerThread = Thread.currentThread()
    private val startedAtNanos = clock()
    private val emptyPairOverheadNanos = calibrateEmptyPairOverhead(clock)
    private val nodes = LinkedHashMap<TimingNodeKey, MutableTimingNode>()
    private val records = LinkedHashMap<TimingRecordKey, MutableTimingRecord>()
    private val intervalStack = ArrayDeque<ActiveInterval>()
    private val frameNodes = LinkedHashSet<TimingNodeKey>()
    private val droppedFrameNodes = LinkedHashSet<TimingNodeKey>()
    private val retainedStrings = LinkedHashSet<String>()
    private var currentFrameId: Long? = null
    private var completedFrames = 0
    private var attemptedClockReads = 0L
    private var retainedClockReads = 0L
    private var droppedTimedNodes = 0
    private var droppedRecords = 0
    private var droppedStrings = 0
    private var truncated = false
    private var endedAtNanos: Long? = null
    private var endReason: RenderNodeTimingEndReason? = null

    val isComplete: Boolean
        get() = endReason != null

    val capturesRendererTiming: Boolean
        get() = RenderNodeTimingPhase.Reconciliation in request.phases ||
            RenderNodeTimingPhase.Binding in request.phases

    fun beginFrame(frameId: Long): Boolean {
        requireOwnerThread()
        if (finishIfExpired()) return false
        if (isComplete) return false
        check(currentFrameId == null) { "A timing frame is already active." }
        currentFrameId = frameId
        frameNodes.clear()
        droppedFrameNodes.clear()
        intervalStack.clear()
        return true
    }

    fun completeFrame() {
        requireOwnerThread()
        if (currentFrameId == null) return
        intervalStack.clear()
        currentFrameId = null
        completedFrames += 1
        if (completedFrames >= request.maxFrames) {
            finish(RenderNodeTimingEndReason.FrameLimit)
        } else {
            finishIfExpired()
        }
    }

    fun endSession() {
        requireOwnerThread()
        finish(RenderNodeTimingEndReason.SessionEnded)
    }

    override fun beginScope(scope: CompositionTimingScope): CompositionTimingSpan? {
        requireOwnerThread()
        val key = TimingNodeKey(scope.identity.value)
        val accepted = retainNode(
            key = key,
            parentKey = scope.parentIdentity?.let { parent -> TimingNodeKey(parent.value) },
            nodeType = null,
            depth = scope.depth,
            synthetic = false,
            sourceCallSites = scope.sourceCallSites.map { source ->
                UiSourceCallSite(
                    className = source.className,
                    methodName = source.methodName,
                    fileName = source.fileName,
                    lineNumber = source.lineNumber,
                )
            },
        )
        if (
            !accepted ||
            RenderNodeTimingPhase.Composition !in request.phases ||
            currentFrameId == null
        ) {
            return identityExposureSpan
        }
        return beginMeasuredInterval(
            key = key,
            phase = RenderNodeTimingPhase.Composition,
        ) ?: identityExposureSpan
    }

    override fun beginInterval(
        subject: CoreRenderTimingSubject,
        phase: CoreRenderTimingPhase,
    ): CoreRenderTimingSpan? {
        requireOwnerThread()
        val publicPhase = when (phase) {
            CoreRenderTimingPhase.Reconciliation -> RenderNodeTimingPhase.Reconciliation
            CoreRenderTimingPhase.Binding -> RenderNodeTimingPhase.Binding
        }
        if (publicPhase !in request.phases || currentFrameId == null) return null
        val key = TimingNodeKey(subject.nodeIdentity)
        if (!retainNode(
                key = key,
                parentKey = null,
                nodeType = subject.nodeType,
                depth = subject.depth,
                synthetic = subject.synthetic,
                sourceCallSites = emptyList(),
            )
        ) return null
        val interval = beginMeasuredInterval(key, publicPhase) ?: return null
        return CoreRenderTimingSpan(interval::close)
    }

    override fun snapshot(): RenderNodeTimingCaptureResult {
        requireOwnerThread()
        finishIfExpired()
        return buildResult()
    }

    override fun stop(): RenderNodeTimingCaptureResult {
        requireOwnerThread()
        finish(RenderNodeTimingEndReason.ExplicitStop)
        return buildResult()
    }

    private fun beginMeasuredInterval(
        key: TimingNodeKey,
        phase: RenderNodeTimingPhase,
    ): ActiveInterval? {
        if (isComplete || currentFrameId == null) return null
        attemptedClockReads = saturatingIncrement(attemptedClockReads)
        val interval = ActiveInterval(
            owner = this,
            key = key,
            frameId = checkNotNull(currentFrameId),
            phase = phase,
            startNanos = clock(),
        )
        intervalStack.addLast(interval)
        return interval
    }

    private fun closeInterval(interval: ActiveInterval) {
        requireOwnerThread()
        if (interval.closed) return
        interval.closed = true
        attemptedClockReads = saturatingIncrement(attemptedClockReads)
        val duration = (clock() - interval.startNanos).coerceAtLeast(0L)
        if (intervalStack.lastOrNull() === interval) {
            intervalStack.removeLast()
        } else {
            intervalStack.remove(interval)
            truncated = true
        }
        intervalStack.lastOrNull()?.let { parent ->
            parent.childNanos = saturatingAdd(parent.childNanos, duration).also { total ->
                if (total == Long.MAX_VALUE) truncated = true
            }
        }
        val retained = when (interval.phase) {
            RenderNodeTimingPhase.Binding -> retainRecord(
                key = TimingRecordKey(
                    frameId = interval.frameId,
                    nodeKey = interval.key,
                    phase = interval.phase,
                    inclusion = RenderNodeTimingInclusion.Direct,
                ),
                durationNanos = duration,
            )
            RenderNodeTimingPhase.Composition,
            RenderNodeTimingPhase.Reconciliation,
            -> {
                val inclusive = retainRecord(
                    key = TimingRecordKey(
                        frameId = interval.frameId,
                        nodeKey = interval.key,
                        phase = interval.phase,
                        inclusion = RenderNodeTimingInclusion.Inclusive,
                    ),
                    durationNanos = duration,
                )
                val self = retainRecord(
                    key = TimingRecordKey(
                        frameId = interval.frameId,
                        nodeKey = interval.key,
                        phase = interval.phase,
                        inclusion = RenderNodeTimingInclusion.Self,
                    ),
                    durationNanos = (duration - interval.childNanos).coerceAtLeast(0L),
                )
                inclusive || self
            }
        }
        if (retained) {
            retainedClockReads = saturatingAdd(retainedClockReads, 2L)
        }
    }

    private fun retainNode(
        key: TimingNodeKey,
        parentKey: TimingNodeKey?,
        nodeType: NodeType?,
        depth: Int,
        synthetic: Boolean,
        sourceCallSites: List<UiSourceCallSite>,
    ): Boolean {
        if (currentFrameId == null || isComplete) return false
        if (depth > MAX_TIMING_DEPTH) {
            dropNode(key)
            return false
        }
        if (key !in frameNodes && frameNodes.size >= MAX_TIMED_NODES_PER_FRAME) {
            dropNode(key)
            return false
        }
        frameNodes += key
        val existing = nodes[key]
        if (existing == null) {
            val boundedSources = sourceCallSites.take(MAX_TIMING_SOURCE_CALL_SITES).mapNotNull { source ->
                val className = retainString(source.className) ?: return@mapNotNull null
                val methodName = retainString(source.methodName) ?: return@mapNotNull null
                val fileName = retainString(source.fileName) ?: return@mapNotNull null
                UiSourceCallSite(
                    className = className,
                    methodName = methodName,
                    fileName = fileName,
                    lineNumber = source.lineNumber,
                )
            }
            nodes[key] = MutableTimingNode(
                token = RenderNodeToken(nextRenderNodeTokenValue()),
                parentKey = parentKey,
                nodeType = nodeType,
                depth = depth,
                synthetic = synthetic,
                sourceCallSites = boundedSources,
            )
        } else {
            if (existing.parentKey == null && parentKey != null) existing.parentKey = parentKey
            if (existing.nodeType == null && nodeType != null) existing.nodeType = nodeType
            existing.depth = minOf(existing.depth, depth)
            existing.synthetic = existing.synthetic || synthetic
            if (existing.sourceCallSites.isEmpty() && sourceCallSites.isNotEmpty()) {
                existing.sourceCallSites = sourceCallSites
                    .take(MAX_TIMING_SOURCE_CALL_SITES)
                    .mapNotNull { source ->
                        val className = retainString(source.className) ?: return@mapNotNull null
                        val methodName = retainString(source.methodName) ?: return@mapNotNull null
                        val fileName = retainString(source.fileName) ?: return@mapNotNull null
                        UiSourceCallSite(className, methodName, fileName, source.lineNumber)
                    }
            }
        }
        return true
    }

    private fun dropNode(key: TimingNodeKey) {
        if (droppedFrameNodes.add(key)) {
            droppedTimedNodes = saturatingIncrement(droppedTimedNodes)
        }
        truncated = true
    }

    private fun retainRecord(
        key: TimingRecordKey,
        durationNanos: Long,
    ): Boolean {
        val existing = records[key]
        if (existing != null) {
            val nextDuration = saturatingAdd(existing.durationNanos, durationNanos)
            val nextRepetitions = saturatingIncrement(existing.repetitions)
            if (nextDuration == Long.MAX_VALUE || nextRepetitions == Long.MAX_VALUE) {
                existing.truncated = true
                truncated = true
            }
            existing.durationNanos = nextDuration
            existing.repetitions = nextRepetitions
            return true
        }
        if (records.size >= MAX_TIMING_RECORDS) {
            droppedRecords = saturatingIncrement(droppedRecords)
            truncated = true
            return false
        }
        records[key] = MutableTimingRecord(durationNanos = durationNanos)
        return true
    }

    private fun retainString(raw: String): String? {
        val bounded = raw.take(MAX_TIMING_STRING_LENGTH)
        if (bounded.length != raw.length) truncated = true
        if (bounded in retainedStrings) return bounded
        if (retainedStrings.size >= MAX_TIMING_DISTINCT_STRINGS) {
            droppedStrings = saturatingIncrement(droppedStrings)
            truncated = true
            return null
        }
        retainedStrings += bounded
        return bounded
    }

    private fun finishIfExpired(): Boolean {
        if (isComplete) return true
        val now = clock()
        if (now - startedAtNanos >= request.maxDurationNanos) {
            finish(RenderNodeTimingEndReason.DurationLimit, now)
            return true
        }
        return false
    }

    private fun finish(
        reason: RenderNodeTimingEndReason,
        now: Long = clock(),
    ) {
        if (isComplete) return
        currentFrameId = null
        intervalStack.clear()
        endedAtNanos = now
        endReason = reason
        val callback = onFinished
        onFinished = null
        callback?.invoke()
    }

    private fun buildResult(): RenderNodeTimingCaptureResult {
        val immutableRecords = records.mapNotNull { (key, aggregate) ->
            val node = nodes[key.nodeKey] ?: return@mapNotNull null
            RenderNodeTimingRecord(
                frameId = key.frameId,
                nodeToken = node.token,
                parentNodeToken = node.parentKey?.let(nodes::get)?.token,
                nodeType = node.nodeType,
                depth = node.depth,
                synthetic = node.synthetic,
                sourceCallSites = node.sourceCallSites,
                phase = key.phase,
                inclusion = key.inclusion,
                durationNanos = aggregate.durationNanos,
                repetitions = aggregate.repetitions,
                truncated = aggregate.truncated,
            )
        }
        return RenderNodeTimingCaptureResult(
            context = context,
            records = immutableRecords,
            completedFrames = completedFrames,
            startedAtNanos = startedAtNanos,
            endedAtNanos = endedAtNanos,
            attemptedClockReads = attemptedClockReads,
            retainedClockReads = retainedClockReads,
            emptyPairOverheadNanos = emptyPairOverheadNanos,
            droppedTimedNodes = droppedTimedNodes,
            droppedRecords = droppedRecords,
            droppedStrings = droppedStrings,
            truncated = truncated,
            unsupportedDomains = RenderNodeTimingUnsupportedDomain.entries.toSet(),
            complete = isComplete,
            endReason = endReason,
        )
    }

    private fun requireOwnerThread() {
        check(Thread.currentThread() === ownerThread) {
            "Timing capture calls must run on the owning render thread."
        }
    }

    private inner class ActiveInterval(
        val owner: ActiveRenderNodeTimingCapture,
        val key: TimingNodeKey,
        val frameId: Long,
        val phase: RenderNodeTimingPhase,
        val startNanos: Long,
        var childNanos: Long = 0L,
        var closed: Boolean = false,
    ) : CompositionTimingSpan {
        override fun close() {
            owner.closeInterval(this)
        }
    }

    private data class TimingNodeKey(val identity: Long?)

    private data class TimingRecordKey(
        val frameId: Long,
        val nodeKey: TimingNodeKey,
        val phase: RenderNodeTimingPhase,
        val inclusion: RenderNodeTimingInclusion,
    )

    private data class MutableTimingNode(
        val token: RenderNodeToken,
        var parentKey: TimingNodeKey?,
        var nodeType: NodeType?,
        var depth: Int,
        var synthetic: Boolean,
        var sourceCallSites: List<UiSourceCallSite>,
    )

    private data class MutableTimingRecord(
        var durationNanos: Long,
        var repetitions: Long = 1L,
        var truncated: Boolean = false,
    )

    private companion object {
        val identityExposureSpan = CompositionTimingSpan { }
    }
}

private fun calibrateEmptyPairOverhead(clock: () -> Long): Long {
    var minimum = Long.MAX_VALUE
    repeat(EMPTY_PAIR_CALIBRATION_COUNT) {
        val start = clock()
        val duration = (clock() - start).coerceAtLeast(0L)
        minimum = minOf(minimum, duration)
    }
    return if (minimum == Long.MAX_VALUE) 0L else minimum
}

private fun saturatingAdd(first: Long, second: Long): Long {
    if (first == Long.MAX_VALUE || second == Long.MAX_VALUE) return Long.MAX_VALUE
    return if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second
}

private fun saturatingIncrement(value: Long): Long =
    if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1L

private fun saturatingIncrement(value: Int): Int =
    if (value == Int.MAX_VALUE) Int.MAX_VALUE else value + 1

internal const val MAX_TIMING_FRAMES = 8
internal const val MAX_TIMING_DURATION_NANOS = 2_000_000_000L
internal const val MAX_TIMED_NODES_PER_FRAME = 64
internal const val MAX_TIMING_RECORDS = 512
internal const val MAX_TIMING_DEPTH = 32
private const val MAX_TIMING_DISTINCT_STRINGS = 128
private const val MAX_TIMING_STRING_LENGTH = 256
private const val MAX_TIMING_SOURCE_CALL_SITES = 24
private const val EMPTY_PAIR_CALIBRATION_COUNT = 8
