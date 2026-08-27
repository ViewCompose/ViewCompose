---
schema_version: 2
document_id: module.viewcompose-diagnostics
doc_type: module
owner:
  kind: module
  id: viewcompose-diagnostics
version_lane: released
capability_ids:
  - diagnostics.failure-aggregation
artifact_ids:
  - viewcompose-diagnostics
sample_ids:
  - module.diagnostics-dependency
  - module.diagnostics-failure-aggregation
  - module.diagnostics-snapshot-export
coordinate: com.viewcompose:viewcompose-diagnostics:0.1.0-alpha01
minimal_usage_sample_id: module.diagnostics-dependency
---

# Diagnostics

`viewcompose-diagnostics` is the optional production-observability layer for ViewCompose render
failures. It consumes the correlated failure events owned by `viewcompose-ui-foundation` and turns
them into bounded, redacted, immutable summaries. It contains no telemetry vendor, database,
worker, manifest component, network client, file writer, debug inspector, View traversal, or
process-global sink.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="diagnostics-module-dependency" sample_id="module.diagnostics-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-diagnostics:0.1.0-alpha01")
}
```

- Stability: **Alpha**. The privacy, cardinality, synchronization, and reset contracts are reviewed
  and tested; names may still change between alphas.
- Platform: Android Library with `minSdk 24`, `compileSdk 36`, and Java 11 bytecode. Aggregation
  code itself uses no Android framework API; the AAR shape follows its public dependency on
  `viewcompose-ui-foundation`.
- `viewcompose-ui-foundation` is exposed transitively because `RenderDiagnostics`,
  `RenderFailureObserved`, and `RenderDiagnosticContext` appear in the public API.
- The public package root is `com.viewcompose.diagnostics`.

## Failure-only installation

Create one application-owned aggregator and pass it as the root diagnostics sink:

{/* compiled-region source="viewcompose-diagnostics/src/test/samples/com/viewcompose/diagnostics/samples/RenderFailureAggregationSamples.kt" region="diagnostics-failure-aggregation" sample_id="module.diagnostics-failure-aggregation" build_target=":viewcompose-diagnostics:compileDebugUnitTestKotlin" */}
```kotlin
fun boundedFailureAggregationSample(
    install: (RenderDiagnostics) -> Unit,
): BoundedRenderFailureAggregator {
    val aggregator = BoundedRenderFailureAggregator()
    val diagnostics = RenderDiagnostics(
        collection = RenderDiagnosticCollection(
            lifecycle = false,
            failures = true,
            frameLevel = RenderFrameDiagnosticLevel.None,
        ),
        sink = aggregator,
    )
    install(diagnostics)
    return aggregator
}
```

This configuration collects only structured failures. It does not build frame statistics or trees,
activate Preview tooling, traverse mounted Views, read per-node clocks, install a timer, or create a
background worker. When the aggregator is not constructed and installed, it owns no runtime path.
Non-failure events sent to the aggregator return before a clock read or aggregation allocation.

The same aggregator may receive events inherited by Host, navigation, lazy, pager, and overlay
sessions. Calls from different session threads are synchronized; there is no cross-session total
order beyond the aggregator's lock-acquisition order.

## Redaction and fingerprint identity

`RenderFailureFingerprint` includes only:

1. `RenderFailurePhase` and `RenderFailureRecovery`;
2. an optional `RenderFailureOperation`;
3. the direct exception's binary type, truncated to 256 UTF-16 code units; and
4. at most three `com.viewcompose.*` stack locations containing only truncated binary class and
   method names.

It excludes exception messages and causes, application frames, file names, line numbers, complete
stacks, `nodeKey`, View text, Composition Local values, URLs, media, credentials, arbitrary
`toString()` output, and the original `Throwable`. The immutable result is safe by construction,
but applications still own consent, retention, account association, and any extra metadata they
join during export.

## Windows, capacity, and loss reporting

`BoundedRenderFailureAggregator` defaults to 64 distinct fingerprints and accepts `1..128`. Its
default monotonic window is 15 minutes and accepts one minute through 24 hours. A window starts on
the first record or query and expires only on the next record, `snapshot`, or `snapshotAndReset`;
there is no timer.

Each retained `RenderFailureAggregate` exposes a process-local window ID, saturated count,
monotonic first and last receipt times, fingerprint, and latest safe diagnostic context. Snapshot
entries are ordered from least recently updated to most recently updated. At capacity, a new
fingerprint evicts the least recently updated entry. `droppedFailureCount` adds the evicted entry's
count or an observation omitted after count saturation, while `evictedFingerprintCount` counts
distinct evicted entries. All counters saturate at `Long.MAX_VALUE`.

`snapshot()` returns a defensive immutable copy and retains the live window. `snapshotAndReset()`
returns the same kind of copy, then atomically opens an empty next window. Neither operation changes
any live `RenderSessionTraceId`; window IDs and trace IDs are separate process-local concepts.

## Export and failure isolation

Snapshot and reset are synchronous memory operations. Export outside render-sink delivery and keep
slow I/O behind application-owned scheduling and backpressure:

{/* compiled-region source="viewcompose-diagnostics/src/test/samples/com/viewcompose/diagnostics/samples/RenderFailureAggregationSamples.kt" region="diagnostics-snapshot-export" sample_id="module.diagnostics-snapshot-export" build_target=":viewcompose-diagnostics:compileDebugUnitTestKotlin" */}
```kotlin
fun exportFailureAggregationSnapshotSample(
    aggregator: BoundedRenderFailureAggregator,
    forward: (RenderFailureAggregationSnapshot) -> Unit,
) {
    val completedWindow = aggregator.snapshotAndReset()
    forward(completedWindow)
}
```

The module never invokes an exporter, so export failure cannot recursively publish a render
failure. Throwing from the root sink remains governed by the Foundation contract: the session
records a local `DiagnosticsSink` failure and disables that sink without changing render recovery.
Applications that fan out one root sink must isolate their own downstream consumers and decide
whether a failing exporter is retried, disabled, or reported through a separate channel.

## API quality and verification

`BoundedRenderFailureAggregator`, `record`, `snapshot`, and `snapshotAndReset` are Q3 APIs. Their
compiled sample demonstrates failure-only installation and forwarding an immutable snapshot.
Fingerprint, stack-frame, aggregate, and snapshot values are Q2 immutable output contracts.

The focused suite covers constructor limits, zero-work ignored events, redaction, deduplication,
deterministic least-recently-updated eviction, count saturation, expiration, reset immutability,
1,000 high-cardinality fingerprints, concurrent multi-session publication, disposal
classification, export failure isolation, and application-owned process reset. The module performs
no Android UI work, so Phase 2 adds no visual Demo acceptance surface. Device highlighting and
timing remain separate request-driven Preview capabilities; their completed cross-module evidence
is retained in the
[archived diagnostics plan](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/diagnostics-correlation-inspection-observability.md).
