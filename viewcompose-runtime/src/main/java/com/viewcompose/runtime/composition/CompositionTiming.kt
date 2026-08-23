package com.viewcompose.runtime.composition

/**
 * Identifies one composition scope for the lifetime of its owning composer process.
 *
 * The identity is allocated lazily only when a finite timing capture visits the scope. It has no
 * application, persistence, saveable-state, analytics, or cross-process meaning. Consumers use it
 * only to correlate the same scope with downstream timing phases during one requested capture.
 *
 * @property value opaque non-zero process-local value whose numeric order has no semantic meaning
 */
@JvmInline
value class CompositionTimingNodeIdentity internal constructor(val value: Long)

/**
 * Describes one privacy-safe composition scope offered to a finite timing collector.
 *
 * [path] is the composer's structural path and contains no raw application key. Source call sites
 * are the bounded locations already captured when the scope was created; this timing boundary does
 * not allocate a stack trace. New properties may be added in compatible releases.
 *
 * @property identity lazy process-local identity shared with the emitted node while this scope runs
 * @property parentIdentity owning composition scope identity, or `null` for the root
 * @property path bounded structural path assigned by the composer
 * @property depth number of ancestors between this scope and the root
 * @property sourceCallSites bounded source locations already owned by the scope
 */
data class CompositionTimingScope(
    val identity: CompositionTimingNodeIdentity,
    val parentIdentity: CompositionTimingNodeIdentity?,
    val path: String,
    val depth: Int,
    val sourceCallSites: List<CompositionSourceCallSite>,
)

/**
 * Closes one composition-scope timing interval.
 *
 * [close] runs exactly once on the composer owner thread after the observed scope body returns or
 * throws. Implementations must be fast, non-blocking, and failure-isolated; [ComposerLite] ignores
 * an implementation exception so optional diagnostics cannot alter composition recovery.
 */
fun interface CompositionTimingSpan {
    /** Finishes the interval and publishes any bounded measurement owned by the collector. */
    fun close()
}

/**
 * Q3 request-scoped port for finite composition evaluation timing.
 *
 * [beginScope] is called only for scope bodies that actually execute during
 * [ComposerLite.prepareRootWithTiming]. Skipped scopes perform no callback and no clock read. The
 * collector owns the monotonic clock, nested inclusive/self accounting, record caps, and any
 * instrumentation-overhead estimate. Returning `null` declines the scope without changing its
 * composition result.
 *
 * Calls are serialized on the composer owner thread and may nest. Implementations cannot retain a
 * [RecomposeScope], invoke application code, block, perform I/O, or re-enter the composer. A thrown
 * callback is isolated and treated as a declined or incomplete timing interval.
 *
 * @sample com.viewcompose.runtime.samples.compositionTimingCollectorSample
 */
fun interface CompositionTimingCollector {
    /**
     * Starts one executed scope interval.
     *
     * @param scope immutable privacy-safe scope descriptor for the current evaluation
     * @return interval closed after evaluation, or `null` to omit this scope
     */
    fun beginScope(scope: CompositionTimingScope): CompositionTimingSpan?
}
