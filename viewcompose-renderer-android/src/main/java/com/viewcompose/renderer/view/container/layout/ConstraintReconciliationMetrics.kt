package com.viewcompose.renderer.view.container

/** Mutually exclusive path selected for one logical ConstraintLayout reconciliation. */
internal enum class ConstraintReconciliationClass {
    NoOp,
    ContentOnly,
    Scalar,
    Environment,
    Topology,
}

/** Source hint accumulated while multiple rebuild requests are coalesced. */
internal enum class ConstraintRebuildReason(
    internal val mask: Int,
) {
    Explicit(1 shl 0),
    ContentOnly(1 shl 1),
    ScalarInput(1 shl 2),
    TopologyInput(1 shl 3),
}

/** Immutable structural evidence for one explicitly enabled, container-local collection window. */
internal data class ConstraintReconciliationSnapshot(
    val noOpClassifications: Long,
    val contentOnlyClassifications: Long,
    val scalarClassifications: Long,
    val environmentClassifications: Long,
    val topologyClassifications: Long,
    val graphCompilations: Long,
    val environmentResolutions: Long,
    val nativeCommits: Long,
    val helperCreates: Long,
    val helperRemoves: Long,
    val helperWrites: Long,
    val adapterLayoutRequests: Long,
    val adapterAllocationBatches: Long,
    val liveLayoutClones: Long,
    val topologyPublishes: Long,
    val rollbacks: Long,
)

/** Mutable counter owner retained only by the explicitly instrumented container. */
internal class ConstraintReconciliationMetrics {
    private var noOpClassifications = 0L
    private var contentOnlyClassifications = 0L
    private var scalarClassifications = 0L
    private var environmentClassifications = 0L
    private var topologyClassifications = 0L
    private var graphCompilations = 0L
    private var environmentResolutions = 0L
    private var nativeCommits = 0L
    private var helperCreates = 0L
    private var helperRemoves = 0L
    private var helperWrites = 0L
    private var adapterLayoutRequests = 0L
    private var adapterAllocationBatches = 0L
    private var liveLayoutClones = 0L
    private var topologyPublishes = 0L
    private var rollbacks = 0L

    internal fun recordClassification(value: ConstraintReconciliationClass) {
        when (value) {
            ConstraintReconciliationClass.NoOp -> noOpClassifications += 1L
            ConstraintReconciliationClass.ContentOnly -> contentOnlyClassifications += 1L
            ConstraintReconciliationClass.Scalar -> scalarClassifications += 1L
            ConstraintReconciliationClass.Environment -> environmentClassifications += 1L
            ConstraintReconciliationClass.Topology -> topologyClassifications += 1L
        }
    }

    internal fun recordGraphCompilation() {
        graphCompilations += 1L
    }

    internal fun recordEnvironmentResolution() {
        environmentResolutions += 1L
    }

    internal fun recordNativeCommit() {
        nativeCommits += 1L
    }

    internal fun recordHelperCreate() {
        helperCreates += 1L
    }

    internal fun recordHelperRemove() {
        helperRemoves += 1L
    }

    internal fun recordHelperWrite(count: Int = 1) {
        helperWrites += count
    }

    internal fun recordAdapterLayoutRequest() {
        adapterLayoutRequests += 1L
    }

    internal fun recordAdapterAllocationBatch() {
        adapterAllocationBatches += 1L
    }

    internal fun recordLiveLayoutClone() {
        liveLayoutClones += 1L
    }

    internal fun recordTopologyPublish() {
        topologyPublishes += 1L
    }

    internal fun recordRollback() {
        rollbacks += 1L
    }

    internal fun snapshot(): ConstraintReconciliationSnapshot = ConstraintReconciliationSnapshot(
        noOpClassifications = noOpClassifications,
        contentOnlyClassifications = contentOnlyClassifications,
        scalarClassifications = scalarClassifications,
        environmentClassifications = environmentClassifications,
        topologyClassifications = topologyClassifications,
        graphCompilations = graphCompilations,
        environmentResolutions = environmentResolutions,
        nativeCommits = nativeCommits,
        helperCreates = helperCreates,
        helperRemoves = helperRemoves,
        helperWrites = helperWrites,
        adapterLayoutRequests = adapterLayoutRequests,
        adapterAllocationBatches = adapterAllocationBatches,
        liveLayoutClones = liveLayoutClones,
        topologyPublishes = topologyPublishes,
        rollbacks = rollbacks,
    )
}
