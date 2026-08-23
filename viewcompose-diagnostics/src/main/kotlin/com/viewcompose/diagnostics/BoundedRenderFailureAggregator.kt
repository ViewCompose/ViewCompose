package com.viewcompose.diagnostics

import com.viewcompose.ui.foundation.RenderDiagnosticContext
import com.viewcompose.ui.foundation.RenderDiagnosticEvent
import com.viewcompose.ui.foundation.RenderDiagnosticsSink
import com.viewcompose.ui.foundation.RenderFailure
import com.viewcompose.ui.foundation.RenderFailureObserved
import com.viewcompose.ui.foundation.RenderFailureOperation
import com.viewcompose.ui.foundation.RenderFailurePhase
import com.viewcompose.ui.foundation.RenderFailureRecovery
import java.util.Collections
import java.util.LinkedHashMap

/**
 * One redacted framework stack location retained in a [RenderFailureFingerprint].
 *
 * Values contain only a `com.viewcompose.*` binary class name and method name, each truncated to
 * 256 UTF-16 code units. File names, line numbers, application frames, and throwable references
 * are never retained. Instances are immutable aggregation output created by this module.
 *
 * @property className truncated framework binary class name
 * @property methodName truncated framework method name
 */
@ConsistentCopyVisibility
data class RenderFailureStackFrame internal constructor(
    val className: String,
    val methodName: String,
)

/**
 * Privacy-bounded identity used to group equivalent render failures.
 *
 * Equality includes only [phase], [recovery], [operation], the failing exception's binary type,
 * and up to three class-and-method-only framework frames. It never includes an exception message,
 * cause chain, application frame, file or line, node key, View text, Local value, URL, credential,
 * full stack, or the original `Throwable`. Strings are truncated to 256 UTF-16 code units.
 * Instances are immutable aggregation output created by this module and are valid only for the
 * producing application's interpretation of one process-local snapshot.
 *
 * @property phase render pipeline stage where the failure occurred
 * @property recovery framework state guaranteed after the failure
 * @property operation Android View interoperability operation, when classified
 * @property exceptionType truncated binary name of the direct failing exception type
 * @property frameworkFrames at most three redacted `com.viewcompose.*` stack locations
 */
@ConsistentCopyVisibility
data class RenderFailureFingerprint internal constructor(
    val phase: RenderFailurePhase,
    val recovery: RenderFailureRecovery,
    val operation: RenderFailureOperation?,
    val exceptionType: String,
    val frameworkFrames: List<RenderFailureStackFrame>,
)

/**
 * Immutable summary for one retained failure fingerprint in an aggregation window.
 *
 * Observation times use the aggregator's monotonic clock at receipt and have no wall-clock or
 * cross-process meaning. [latestContext] is the most recently received safe framework context;
 * its trace identifiers remain process-local diagnostics tokens and must not be persisted as user,
 * account, navigation, or analytics identity. Counts saturate at [Long.MAX_VALUE].
 *
 * @property windowId process-local aggregation-window identity
 * @property fingerprint redacted identity shared by the counted failures
 * @property count retained observation count, saturated at [Long.MAX_VALUE]
 * @property firstObservedAtNanos monotonic receipt time of the first retained observation
 * @property lastObservedAtNanos monotonic receipt time of the latest retained observation
 * @property latestContext safe framework context from the latest retained observation
 */
@ConsistentCopyVisibility
data class RenderFailureAggregate internal constructor(
    val windowId: Long,
    val fingerprint: RenderFailureFingerprint,
    val count: Long,
    val firstObservedAtNanos: Long,
    val lastObservedAtNanos: Long,
    val latestContext: RenderDiagnosticContext,
)

/**
 * Immutable point-in-time output from a [BoundedRenderFailureAggregator].
 *
 * [aggregates] is ordered from least recently updated to most recently updated. Its values and
 * nested lists are defensive copies and do not change after a later record, expiration, or reset.
 * [droppedFailureCount] counts observations removed by capacity eviction or omitted after a
 * retained count saturated. [evictedFingerprintCount] counts distinct retained entries removed by
 * capacity. Both counters and all aggregate counts saturate at [Long.MAX_VALUE]. Window and time
 * values are process-local and are not persistence or wall-clock identities.
 *
 * @property windowId process-local identity of the captured aggregation window
 * @property windowStartedAtNanos monotonic time when that window opened
 * @property capturedAtNanos monotonic time when this snapshot was copied
 * @property aggregates retained redacted summaries in deterministic update order
 * @property droppedFailureCount observations no longer represented by retained counts
 * @property evictedFingerprintCount distinct fingerprints evicted because capacity was full
 */
@ConsistentCopyVisibility
data class RenderFailureAggregationSnapshot internal constructor(
    val windowId: Long,
    val windowStartedAtNanos: Long,
    val capturedAtNanos: Long,
    val aggregates: List<RenderFailureAggregate>,
    val droppedFailureCount: Long,
    val evictedFingerprintCount: Long,
)

/**
 * Collects redacted render-failure counts in one explicitly installed, bounded process-local sink.
 *
 * Install this instance as a [RenderDiagnosticsSink] with failure collection enabled. Non-failure
 * events return immediately without reading the clock or allocating aggregation state. A failure
 * record performs no I/O and retains no `Throwable`; the caller remains responsible for consent,
 * persistence, account association, cross-process sampling, backpressure, network transport, and
 * vendor SDK behavior. Slow export must consume [snapshot] or [snapshotAndReset] outside sink
 * delivery rather than block rendering.
 *
 * Calls to [onEvent], [snapshot], and [snapshotAndReset] are synchronized and may come from
 * multiple session threads. Concurrent events are serialized in lock-acquisition order; sessions
 * do not otherwise gain a total order. Calling these APIs from an export callback is supported,
 * but no application callback runs while aggregation state is locked. Capacity evicts the least
 * recently updated fingerprint deterministically. A window expires only on the next failure,
 * [snapshot], or [snapshotAndReset]; no timer, worker, listener, or process-global singleton is
 * installed.
 *
 * The public constructor rejects a [capacity] outside `1..128` and a [windowDurationNanos] outside
 * one minute through 24 hours before retaining state. Reset returns the completed immutable window,
 * starts a new window, and never changes a live render-session trace identity.
 *
 * @sample com.viewcompose.diagnostics.samples.boundedFailureAggregationSample
 * @property capacity maximum distinct fingerprints retained at once; defaults to 64
 * @property windowDurationNanos monotonic window duration in nanoseconds; defaults to 15 minutes
 * @throws IllegalArgumentException when either constructor argument is outside its valid range
 */
class BoundedRenderFailureAggregator internal constructor(
    val capacity: Int,
    val windowDurationNanos: Long,
    private val monotonicTimeNanos: () -> Long,
) : RenderDiagnosticsSink {
    /**
     * Creates an inactive aggregator that starts its first window on the first record or query.
     *
     * Construction performs no clock read and installs no recurring work. The instance remains
     * application-owned until callers release all references to it.
     *
     * @param capacity maximum distinct fingerprints retained at once; defaults to 64 and must be
     * in `1..128`
     * @param windowDurationNanos monotonic window duration in nanoseconds; defaults to 15 minutes
     * and must be between one minute and 24 hours, inclusive
     * @throws IllegalArgumentException when either argument is outside its valid range
     */
    @JvmOverloads
    constructor(
        capacity: Int = DEFAULT_CAPACITY,
        windowDurationNanos: Long = DEFAULT_WINDOW_DURATION_NANOS,
    ) : this(
        capacity = capacity,
        windowDurationNanos = windowDurationNanos,
        monotonicTimeNanos = System::nanoTime,
    )

    private val lock = Any()
    private val records = LinkedHashMap<RenderFailureFingerprint, MutableAggregate>(16, 0.75f, true)
    private var currentWindowId = 1L
    private var currentWindowStartedAtNanos: Long? = null
    private var lastClockReadingNanos: Long? = null
    private var droppedFailureCount = 0L
    private var evictedFingerprintCount = 0L

    /**
     * Records a [RenderFailureObserved] event and ignores every other event subtype.
     *
     * Failure recording synchronously reads the aggregator clock once, derives a bounded
     * fingerprint from the direct exception, and updates one entry. It performs no callback, I/O,
     * tree collection, or recursive diagnostic publication. Non-failure events read no clock and
     * allocate no aggregation record.
     *
     * @param event immutable correlated event delivered by a render session
     */
    override fun onEvent(event: RenderDiagnosticEvent) {
        if (event !is RenderFailureObserved) return
        record(event)
    }

    /**
     * Records one correlated render failure in the current bounded window.
     *
     * This is equivalent to passing [event] to [onEvent], but expresses the failure-only path when
     * an application already dispatches event types itself. Calls are thread-safe and synchronous.
     * The original failure and `Throwable` are not retained after this call returns.
     *
     * @param event correlated failure whose redacted fingerprint and safe context are aggregated
     */
    fun record(event: RenderFailureObserved) {
        val fingerprint = event.failure.toSafeFingerprint()
        synchronized(lock) {
            val now = readMonotonicTimeLocked()
            advanceExpiredWindowLocked(now)
            val existing = records[fingerprint]
            if (existing != null) {
                if (existing.count == Long.MAX_VALUE) {
                    droppedFailureCount = droppedFailureCount.saturatedPlus(1L)
                } else {
                    existing.count += 1L
                }
                existing.lastObservedAtNanos = now
                existing.latestContext = event.context
                return
            }

            if (records.size == capacity) {
                val eldest = records.entries.iterator().next()
                records.remove(eldest.key)
                droppedFailureCount = droppedFailureCount.saturatedPlus(eldest.value.count)
                evictedFingerprintCount = evictedFingerprintCount.saturatedPlus(1L)
            }
            records[fingerprint] = MutableAggregate(
                count = 1L,
                firstObservedAtNanos = now,
                lastObservedAtNanos = now,
                latestContext = event.context,
            )
        }
    }

    /**
     * Returns an immutable copy of the active window without clearing retained state.
     *
     * The call reads the monotonic clock once and expires an elapsed window before copying. If no
     * record or query has occurred, this call opens the first empty window. Repeated snapshots do
     * not extend the window or alter record order.
     *
     * @return defensive snapshot of the non-expired current window
     */
    fun snapshot(): RenderFailureAggregationSnapshot = synchronized(lock) {
        val now = readMonotonicTimeLocked()
        advanceExpiredWindowLocked(now)
        snapshotLocked(now)
    }

    /**
     * Returns the current immutable snapshot and immediately starts a new empty window.
     *
     * The call reads the monotonic clock once and first applies normal expiration. Reset clears
     * retained summaries and counters but does not mutate any [RenderDiagnosticContext] or live
     * render-session identity. Concurrent records are ordered wholly before or after the reset.
     *
     * @return defensive snapshot captured immediately before reset
     */
    fun snapshotAndReset(): RenderFailureAggregationSnapshot = synchronized(lock) {
        val now = readMonotonicTimeLocked()
        advanceExpiredWindowLocked(now)
        val snapshot = snapshotLocked(now)
        openNextWindowLocked(now)
        snapshot
    }

    private fun snapshotLocked(now: Long): RenderFailureAggregationSnapshot {
        val windowStartedAtNanos = checkNotNull(currentWindowStartedAtNanos)
        return RenderFailureAggregationSnapshot(
            windowId = currentWindowId,
            windowStartedAtNanos = windowStartedAtNanos,
            capturedAtNanos = now,
            aggregates = Collections.unmodifiableList(
                records.map { (fingerprint, record) ->
                    RenderFailureAggregate(
                        windowId = currentWindowId,
                        fingerprint = fingerprint.copy(
                            frameworkFrames = Collections.unmodifiableList(
                                fingerprint.frameworkFrames.toList(),
                            ),
                        ),
                        count = record.count,
                        firstObservedAtNanos = record.firstObservedAtNanos,
                        lastObservedAtNanos = record.lastObservedAtNanos,
                        latestContext = record.latestContext.copy(),
                    )
                },
            ),
            droppedFailureCount = droppedFailureCount,
            evictedFingerprintCount = evictedFingerprintCount,
        )
    }

    private fun readMonotonicTimeLocked(): Long {
        val reading = monotonicTimeNanos()
        val previous = lastClockReadingNanos
        val normalized = if (previous != null && reading < previous) previous else reading
        lastClockReadingNanos = normalized
        if (currentWindowStartedAtNanos == null) {
            currentWindowStartedAtNanos = normalized
        }
        return normalized
    }

    private fun advanceExpiredWindowLocked(now: Long) {
        val startedAt = checkNotNull(currentWindowStartedAtNanos)
        if (now - startedAt >= windowDurationNanos) {
            openNextWindowLocked(now)
        }
    }

    private fun openNextWindowLocked(now: Long) {
        records.clear()
        droppedFailureCount = 0L
        evictedFingerprintCount = 0L
        currentWindowId = currentWindowId.saturatedPlus(1L)
        currentWindowStartedAtNanos = now
    }

    private data class MutableAggregate(
        var count: Long,
        val firstObservedAtNanos: Long,
        var lastObservedAtNanos: Long,
        var latestContext: RenderDiagnosticContext,
    )

    private companion object {
        const val DEFAULT_CAPACITY = 64
        const val MAXIMUM_CAPACITY = 128
        const val NANOS_PER_MINUTE = 60_000_000_000L
        const val MINIMUM_WINDOW_DURATION_NANOS = NANOS_PER_MINUTE
        const val DEFAULT_WINDOW_DURATION_NANOS = 15L * NANOS_PER_MINUTE
        const val MAXIMUM_WINDOW_DURATION_NANOS = 24L * 60L * NANOS_PER_MINUTE
        const val MAXIMUM_STRING_LENGTH = 256
        const val MAXIMUM_FRAME_COUNT = 3

        init {
            // These checks guard later edits to defaults as well as caller-supplied values.
            require(DEFAULT_CAPACITY in 1..MAXIMUM_CAPACITY)
            require(DEFAULT_WINDOW_DURATION_NANOS in
                MINIMUM_WINDOW_DURATION_NANOS..MAXIMUM_WINDOW_DURATION_NANOS)
        }
    }

    init {
        require(capacity in 1..MAXIMUM_CAPACITY) {
            "capacity must be in 1..$MAXIMUM_CAPACITY, but was $capacity."
        }
        require(windowDurationNanos in
            MINIMUM_WINDOW_DURATION_NANOS..MAXIMUM_WINDOW_DURATION_NANOS) {
            "windowDurationNanos must be in " +
                "$MINIMUM_WINDOW_DURATION_NANOS..$MAXIMUM_WINDOW_DURATION_NANOS, " +
                "but was $windowDurationNanos."
        }
    }

    private fun RenderFailure.toSafeFingerprint(): RenderFailureFingerprint {
        val safeFrames = ArrayList<RenderFailureStackFrame>(MAXIMUM_FRAME_COUNT)
        cause.stackTrace.forEach { frame ->
            if (safeFrames.size == MAXIMUM_FRAME_COUNT) return@forEach
            if (!frame.className.startsWith(FRAMEWORK_PACKAGE_PREFIX)) return@forEach
            safeFrames += RenderFailureStackFrame(
                className = frame.className.truncated(),
                methodName = frame.methodName.truncated(),
            )
        }
        return RenderFailureFingerprint(
            phase = phase,
            recovery = recovery,
            operation = operation,
            exceptionType = cause.javaClass.name.truncated(),
            frameworkFrames = Collections.unmodifiableList(safeFrames),
        )
    }

    private fun String.truncated(): String = if (length <= MAXIMUM_STRING_LENGTH) {
        this
    } else {
        take(MAXIMUM_STRING_LENGTH)
    }

    private fun Long.saturatedPlus(value: Long): Long {
        return if (this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value
    }
}

private const val FRAMEWORK_PACKAGE_PREFIX = "com.viewcompose."
