---
title: Tune collection reuse and inspect render diagnostics
sidebar_position: 7
---

# Tune collection reuse and inspect render diagnostics

This final chapter makes performance decisions explicit and measurable. You will keep stable item
identity and content types, set bounded prefetch/cache hints, opt compatible containers into a
shared native pool, and sample immutable render counters without creating a feedback loop.

## Prerequisites and module baseline

Complete [Animate completion and add bounded gestures](./task-list-animation-and-gestures.md). The
policy types come from `viewcompose-ui-contract` and the public diagnostics type comes from
`viewcompose-widget-core`, both `0.1.0-alpha01` in the verified sample. Renderer-internal diagnostic
types are not application API.

## 1. Keep semantic identity separate from tuning

The complete screen still declares:

```kotlin
LazyColumn(
    items = tasks.value,
    key = TaskItem::id,
    contentType = { "task" },
    prefetchPolicy = TaskListPrefetchPolicy,
    reusePolicy = TaskListReusePolicy,
) { task ->
    AnimatedTaskRow(/* ... */)
}
```

`key` and `contentType` are correctness and compatibility inputs. Prefetch, native view cache size,
and shared pooling are performance hints: a renderer may clamp them, and changing them must never
change task content or ownership.

## 2. Sample diagnostics outside observable callbacks

The Activity stores each `onRenderStats` snapshot in an `AtomicReference`. That callback does not
write ViewCompose state, so it cannot schedule another frame. A user action copies the latest
snapshot into observable display text when inspection is requested.

{/* tutorial-sample source="samples/task-list/src/main/java/com/viewcompose/samples/tasklist/TaskListScreens.kt" region="task-list-performance-diagnostics" */}
```kotlin
private val TaskListPrefetchPolicy = LazyLayoutPrefetchPolicy(
    initialPrefetchItemCount = 4,
    itemViewCacheSize = 4,
)
private val TaskListReusePolicy = CollectionReusePolicy(sharePool = true)

private fun UiTreeBuilder.RenderDiagnosticsControl(
    latestStats: () -> RenderStats,
    diagnostics: String,
    onDiagnosticsChange: (String) -> Unit,
) {
    Button(
        text = "Sample render stats",
        variant = ButtonVariant.Outlined,
        onClick = {
            val stats = latestStats()
            onDiagnosticsChange(
                "Render stats: ${stats.inserts} inserts, " +
                    "${stats.reuses} reuses, ${stats.patchedNodes} patches",
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(text = diagnostics)
}
```
{/* tutorial-sample-end */}

`inserts`, `reuses`, and `patchedNodes` describe one host frame; they are not elapsed-time metrics.
The root Activity callback measures the root render session. The navigation destination uses its own
session and emits debug diagnostics under `TaskListNavigation`, so always state which host and
interaction a measurement covers.

## 3. Run a repeatable inspection

Build and install the same debug variant, then repeat a fixed sequence: launch, sample the initial
frame, complete `Read the tutorial`, add one task, and sample again. Record device model, Android
version, build type, and interaction sequence with any counters.

```bash
./gradlew :samples:task-list:assembleDebug
adb logcat -s TaskListHost TaskListNavigation
./gradlew :samples:task-list:connectedDebugAndroidTest
```

Use Android Studio profiling or a benchmark for time, allocation, and frame pacing. Render counters
explain reconciliation work but do not replace those measurements.

## Series result

The same `:samples:task-list` application now covers state and layout, input and lazy collections,
semantic theming and navigation, overlays and native Views, animation and gestures, and bounded
performance diagnostics. `qaQuick` compiles every documented stage and checks the exact snippets;
`qaFull` runs the final behavior test on a connected Android target.
