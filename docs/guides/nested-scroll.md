---
schema_version: 2
document_id: guide.nested-scroll-coordination
doc_type: guide
owner:
  kind: capability
  id: nested.scroll
version_lane: released
capability_ids:
  - nested.scroll
artifact_ids:
  - viewcompose-ui-contract
  - viewcompose-gesture
  - viewcompose-renderer-android
sample_ids:
  - guide.nested-scroll-toolbar
task: Coordinate an ancestor effect with child scrolling while preserving bounded consumption.
success_checks:
  - The connection remains stable and reads the latest callback or state.
  - Every returned delta or velocity has the offered sign and does not exceed the offered magnitude.
  - Imperative code dispatches pre, consumes only the remainder locally, then dispatches post.
  - Native children either implement Android nested scrolling or enter through an explicit dispatcher.
failure_checks:
  - A parent reports more consumption than was offered or returns a non-finite value.
  - Post-scroll receives the original available value instead of the remainder after local consumption.
  - A plain AndroidView child is assumed to emit nested-scroll phases automatically.
  - Legacy native fling Boolean consumption is interpreted as an exact partial velocity.
---

# Coordinate nested scrolling

## Consume an ancestor effect

Attach one stable `NestedScrollConnection` to the ancestor that owns the effect. Pre callbacks run
before child consumption; post callbacks receive what the child consumed and what remains. Return
only the signed distance or velocity actually consumed.

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/FocusAndNestedScrollGuideSamples.kt" region="nested-scroll-toolbar" sample_id="guide.nested-scroll-toolbar" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.CollapsingToolbar(
    collapseBy: (deltaY: Float) -> Float,
) {
    val latestCollapseBy = rememberUpdatedState(collapseBy)
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: ScrollDelta,
                source: NestedScrollSource,
            ): ScrollDelta {
                return ScrollDelta(
                    x = 0f,
                    y = latestCollapseBy.value(available.y),
                )
            }
        }
    }

    Column(modifier = Modifier.nestedScroll(connection)) {
        Text("Collapsing toolbar")
        ScrollableColumn {
            repeat(40) { index -> Text("Row $index") }
        }
    }
}
```

Clamp application state inside `collapseBy`; the Android renderer also rejects non-finite or
over-consumed results. Use `NestedScrollSource` when user input, fling continuation, and imperative
side effects require different policy.

## Dispatch a custom scroll source

Pass a stable `NestedScrollDispatcher` to `nestedScroll` when custom gesture or programmatic code
must enter the same parent chain. Dispatch pre-scroll first, consume the remainder locally, then
dispatch post-scroll with the local consumption and remainder. Use the equivalent pre/post fling
pair for velocity.

Lazy collections, pagers, eager scroll containers, PullToRefresh, and framework drag or transform
pan participate in the same chain. A native `AndroidView` joins automatically only when its View
implements Android nested scrolling; otherwise dispatch explicitly. See
[Modifier architecture](../architecture/modifier.md) for phase order, modifier ordering, AndroidX
mapping, and the legacy native-fling limitation.
