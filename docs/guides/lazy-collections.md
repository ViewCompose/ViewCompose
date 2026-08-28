---
schema_version: 2
document_id: guide.lazy-collections
doc_type: guide
owner:
  kind: capability
  id: lazy.collections
version_lane: released
capability_ids:
  - lazy.collections
artifact_ids:
  - viewcompose-ui-foundation
  - viewcompose-ui-contract
sample_ids:
  - guide.lazy-collections-state
  - guide.lazy-collections-grid
  - guide.lazy-collections-pager
task: Choose and control a keyed lazy list, grid, or pager without mixing logical item identity with native presentation reuse.
success_checks:
  - Every item, page, and tab has a stable key and an accurate content revision.
  - LazyListState or PagerState remains stable and is used only for observation and commands.
  - Grid spans and adaptive cells change physical layout without changing logical identity.
  - Performance policies are introduced only after a same-build, same-device measurement.
failure_checks:
  - A position or mutable display value is used as the logical key.
  - Changing ordinary lambda captures are omitted from contentRevision.
  - contentType contains a model ID, revision, or another unbounded value.
  - A new LazyItemsSnapshot is created on every composition or retained after its ordinary data changes.
---

# Choose and control lazy collections

Choose the native-backed container that matches the interaction, then provide stable logical
identity. Session, recycling, and renderer invariants are defined by the
[lazy collection architecture](../architecture/lazy-collections.md).

| Task | Container |
| --- | --- |
| vertically scrolling rows or sticky sections | `LazyColumn` |
| horizontally scrolling chips or cards | `LazyRow` |
| fixed or width-adaptive cells | `LazyVerticalGrid` |
| discrete horizontal pages, commonly paired with tabs | `HorizontalPager` |
| discrete full-height vertical pages | `VerticalPager` |

## Observe and control a list

Keep one `LazyListState` for the logical container. Its observable properties update composition;
its commands target the attached native list. Animated commands are no-ops while detached, while
`scrollToItem` also retains its requested anchor.

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyCollectionsGuideSamples.kt" region="lazy-collections-state" sample_id="guide.lazy-collections-state" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
data class InboxMessage(
    val id: Long,
    val subject: String,
    val version: Int,
)

fun UiTreeBuilder.MessageList(messages: List<InboxMessage>) {
    val listState = rememberLazyListState()

    Column {
        Text(
            "visible=${listState.firstVisibleItemIndex}..${listState.lastVisibleItemIndex}",
        )
        Button(
            text = "Go to latest",
            enabled = messages.isNotEmpty(),
            onClick = { listState.animateScrollToItem(messages.lastIndex) },
        )
        LazyColumn(
            items = messages,
            key = InboxMessage::id,
            contentType = { "message" },
            contentRevision = InboxMessage::version,
            state = listState,
            spacing = 8.dp,
        ) { message ->
            Text(message.subject, modifier = Modifier.fillMaxWidth())
        }
    }
}
```

The key is durable message identity. The revision changes only when displayed ordinary data
changes. `contentType` is a small structural category shared by compatible holders; it is not a
message ID.

Host recreation saves the first visible index and pixel offset. Visible geometry, direction, and
active scrolling belong to the current native layout and are observed again after attachment.

## Build an adaptive grid

Use `GridCells.Fixed` when product design requires an exact column count. Use
`GridCells.Adaptive` when the cell owns a minimum width and the available width should decide the
count. A full-line heading follows column-count changes without acquiring a new logical identity.

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyCollectionsGuideSamples.kt" region="lazy-collections-grid" sample_id="guide.lazy-collections-grid" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
data class GalleryCard(
    val id: Long,
    val title: String,
)

fun UiTreeBuilder.AdaptiveGallery(cards: List<GalleryCard>) {
    LazyVerticalGrid(
        cells = GridCells.Adaptive(minSize = 120.dp),
        horizontalSpacing = 12.dp,
        verticalSpacing = 12.dp,
    ) {
        item(
            key = "gallery-heading",
            contentRevision = StaticContentRevision,
            span = GridItemSpan.FullLine,
        ) {
            Text("Gallery")
        }
        items(
            items = cards,
            key = GalleryCard::id,
            contentType = { "gallery-card" },
            span = { GridItemSpan.Single },
        ) { card ->
            Text(card.title)
        }
    }
}
```

The bulk revision defaults to each immutable `GalleryCard`; its equality must cover every ordinary
value rendered by the item. Supply an explicit selector when that is not true. A single static
heading uses `StaticContentRevision` because it captures no changing ordinary value.

## Coordinate pager and tabs

Pager selection stays controlled by application state. `PagerState` observes native motion and
sends page commands; passing it to `TabRow` also lets the indicator follow drag progress. A page is
independently mounted and must emit exactly one root node.

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/LazyCollectionsGuideSamples.kt" region="lazy-collections-pager" sample_id="guide.lazy-collections-pager" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.PagerWithTabs() {
    val titles = listOf("Overview", "Activity", "Settings")
    val selectedPage = remember { mutableStateOf(0) }
    val pagerState = remember { PagerState() }

    Column {
        TabRow(
            selectedIndex = selectedPage.value,
            onTabSelected = { page ->
                selectedPage.value = page
                pagerState.animateScrollToPage(page)
            },
            pagerState = pagerState,
        ) {
            titles.forEach { title ->
                Tab(key = title, contentRevision = title) {
                    Text(title)
                }
            }
        }
        HorizontalPager(
            currentPage = selectedPage.value,
            onPageChanged = { page -> selectedPage.value = page },
            pagerState = pagerState,
        ) {
            titles.forEach { title ->
                Page(key = title, contentRevision = title) {
                    Text(title)
                }
            }
        }
    }
}
```

`onPageChanged` runs only after a different page settles. `VerticalPager` uses the same keyed
`Page` contract. When its content can be hidden by the IME, put a vertical scroll owner inside the
page because the pager owns discrete page movement rather than form scrolling.

## Choose the submission and tuning path

- Use an ordinary immutable `List` by default. Selectors run on each parent composition, then
  unchanged keyed Sessions are reused.
- Use `remember(dataVersion) { source.toLazyItemsSnapshot() }` only when measurement shows the
  selector/key scan matters and the application has a real immutable snapshot boundary. Replace it
  for structural, retained-data, selector-capture, or ordinary content-capture changes.
- Use the observed `LazyColumn` overload when a State-backed immutable snapshot changes frequently
  while the surrounding screen remains structurally stable. Its item content receives the stable
  key and an `ObservedValue<T>`; use `map` for leaf properties, and keep conditional structure in
  ordinary composition or an explicit revisioned declaration.
- Use [`viewcompose-paging-androidx`](../modules/viewcompose-paging-androidx/README.md) when loading,
  invalidation, retry, refresh, placeholders, or page dropping are product requirements. Do not
  rebuild those policies in the UI layer.
- Use prefetch and reuse policies only after profiling the same release build and device. Continue
  with [the performance tutorial](../tutorials/lazy-list-performance.md).

## Verify the result

Insert and move keyed items and confirm their local state follows the key. Resize or rotate an
adaptive grid and confirm full-line content still spans the row. Swipe and tap tabs in both
directions and confirm controlled selection, settled callbacks, and indicator progress agree.
Finally, exercise TalkBack and keyboard focus on the real Android-backed screen; compile-only
samples cannot establish platform accessibility or focus conformance.
