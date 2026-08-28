package com.viewcompose.ui.foundation.samples

import com.viewcompose.graphics.core.Brush
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.runtime.State
import com.viewcompose.text.TextDocument
import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.ui.foundation.BasicButton
import com.viewcompose.ui.foundation.BasicButtonStyle
import com.viewcompose.ui.foundation.BasicTextField
import com.viewcompose.ui.foundation.BasicTextFieldStyle
import com.viewcompose.ui.foundation.BasicSurface
import com.viewcompose.ui.foundation.BasicSurfaceStyle
import com.viewcompose.ui.foundation.Badge
import com.viewcompose.ui.foundation.BadgeOverrides
import com.viewcompose.ui.foundation.BadgedBox
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.BottomAppBar
import com.viewcompose.ui.foundation.BottomAppBarOverrides
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.ButtonOverrides
import com.viewcompose.ui.foundation.Card
import com.viewcompose.ui.foundation.Chip
import com.viewcompose.ui.foundation.CheckboxOverrides
import com.viewcompose.ui.foundation.Checkbox
import com.viewcompose.ui.foundation.CircularProgressIndicator
import com.viewcompose.ui.foundation.CircularProgressIndicatorOverrides
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.CompositionEffectContext
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.Dialog
import com.viewcompose.ui.foundation.Divider
import com.viewcompose.ui.foundation.DropdownMenu
import com.viewcompose.ui.foundation.DropdownMenuItem
import com.viewcompose.ui.foundation.ExtendedFloatingActionButton
import com.viewcompose.ui.foundation.ExtendedFloatingActionButtonOverrides
import com.viewcompose.ui.foundation.FloatingActionButton
import com.viewcompose.ui.foundation.FloatingActionButtonOverrides
import com.viewcompose.ui.foundation.Icon
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.IconButtonOverrides
import com.viewcompose.ui.foundation.Image
import com.viewcompose.ui.foundation.HorizontalPager
import com.viewcompose.ui.foundation.InputControlDefaults
import com.viewcompose.ui.foundation.LaunchedEffect
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.LazyRow
import com.viewcompose.ui.foundation.LazyVerticalGrid
import com.viewcompose.ui.foundation.ListItem
import com.viewcompose.ui.foundation.LinearProgressIndicator
import com.viewcompose.ui.foundation.LinearProgressIndicatorOverrides
import com.viewcompose.ui.foundation.NavigationBar
import com.viewcompose.ui.foundation.NavigationBarOverrides
import com.viewcompose.ui.foundation.AlertDialog
import com.viewcompose.ui.foundation.AlertDialogOverrides
import com.viewcompose.ui.foundation.ModalBottomSheet
import com.viewcompose.ui.foundation.ModalBottomSheetNavigationBarColor
import com.viewcompose.ui.foundation.ModalBottomSheetOverrides
import com.viewcompose.ui.foundation.OverlayRequestContext
import com.viewcompose.ui.foundation.OverlayRequestStore
import com.viewcompose.ui.foundation.PopupAlignment
import com.viewcompose.ui.foundation.PopupBounds
import com.viewcompose.ui.foundation.PopupOverflowPolicy
import com.viewcompose.ui.foundation.PopupPositioner
import com.viewcompose.ui.foundation.PopupSize
import com.viewcompose.ui.foundation.Popup
import com.viewcompose.ui.foundation.PlainTooltip
import com.viewcompose.ui.foundation.ProvideImageLoader
import com.viewcompose.ui.foundation.ProvideLocal
import com.viewcompose.ui.foundation.ProvideButtonOverrides
import com.viewcompose.ui.foundation.ProvideAlertDialogOverrides
import com.viewcompose.ui.foundation.ProvideBadgeOverrides
import com.viewcompose.ui.foundation.ProvideBottomAppBarOverrides
import com.viewcompose.ui.foundation.ProvideCheckboxOverrides
import com.viewcompose.ui.foundation.ProvideCircularProgressIndicatorOverrides
import com.viewcompose.ui.foundation.ProvideLinearProgressIndicatorOverrides
import com.viewcompose.ui.foundation.ProvideExtendedFloatingActionButtonOverrides
import com.viewcompose.ui.foundation.ProvideFloatingActionButtonOverrides
import com.viewcompose.ui.foundation.ProvideModalBottomSheetOverrides
import com.viewcompose.ui.foundation.ProvideNavigationBarOverrides
import com.viewcompose.ui.foundation.ProvideIconButtonOverrides
import com.viewcompose.ui.foundation.ProvideRadioButtonOverrides
import com.viewcompose.ui.foundation.ProvideSaveableStateRegistry
import com.viewcompose.ui.foundation.ProvideSegmentedControlOverrides
import com.viewcompose.ui.foundation.ProvideSliderOverrides
import com.viewcompose.ui.foundation.ProvideSwitchOverrides
import com.viewcompose.ui.foundation.ProvideTabRowOverrides
import com.viewcompose.ui.foundation.ProvideTextFieldOverrides
import com.viewcompose.ui.foundation.ProvideTopAppBarOverrides
import com.viewcompose.ui.foundation.PullToRefresh
import com.viewcompose.ui.foundation.RadioButton
import com.viewcompose.ui.foundation.RadioButtonOverrides
import com.viewcompose.ui.foundation.SegmentedControl
import com.viewcompose.ui.foundation.SegmentedControlOverrides
import com.viewcompose.ui.foundation.SideEffect
import com.viewcompose.ui.foundation.Slider
import com.viewcompose.ui.foundation.SliderOverrides
import com.viewcompose.ui.foundation.StaticContentRevision
import com.viewcompose.ui.foundation.Switch
import com.viewcompose.ui.foundation.SwitchOverrides
import com.viewcompose.ui.foundation.ScrollableColumn
import com.viewcompose.ui.foundation.Scaffold
import com.viewcompose.ui.foundation.SearchBar
import com.viewcompose.ui.foundation.Snackbar
import com.viewcompose.ui.foundation.Spacer
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.RichText
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.FlowRow
import com.viewcompose.ui.foundation.FlowColumn
import com.viewcompose.ui.foundation.TabRow
import com.viewcompose.ui.foundation.TabRowOverrides
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.TextFieldOverrides
import com.viewcompose.ui.foundation.TextFieldInputProfile
import com.viewcompose.ui.foundation.TextFieldLinePolicy
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.TopAppBar
import com.viewcompose.ui.foundation.TopAppBarOverrides
import com.viewcompose.ui.foundation.Toast
import com.viewcompose.ui.foundation.UiStateColor
import com.viewcompose.ui.foundation.UiSwitchSizing
import com.viewcompose.ui.foundation.UiEnvironment
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiThemeDefaults
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.UiDslMarker
import com.viewcompose.ui.foundation.VerticalPager
import com.viewcompose.ui.foundation.buildVNodeTree
import com.viewcompose.ui.foundation.createSaveableStateRegistry
import com.viewcompose.ui.foundation.produceState
import com.viewcompose.ui.foundation.rememberCoroutineScope
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.foundation.rememberUpdatedState
import com.viewcompose.ui.foundation.observedNodeSpec
import com.viewcompose.ui.foundation.observedValue
import com.viewcompose.ui.foundation.map
import com.viewcompose.ui.foundation.toLazyItemsSnapshot
import com.viewcompose.ui.foundation.lazyItemContentFactory
import com.viewcompose.ui.foundation.uiLocalOf
import com.viewcompose.ui.foundation.UiLocals
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.MinHeightModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.LazyItemTable
import com.viewcompose.ui.node.LazyItemTableUpdate
import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.node.policy.GridCells
import com.viewcompose.ui.node.policy.GridItemSpan
import com.viewcompose.ui.node.UiImageDecodeSize
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequestOptions
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.node.UiInteractionIndication
import com.viewcompose.ui.modifier.InteractionIndicationModifierElement
import com.viewcompose.ui.node.spec.ImageNodeSpec
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.LazyRowNodeProps
import com.viewcompose.ui.node.spec.LazyVerticalGridNodeProps
import com.viewcompose.ui.node.spec.NavigationBarNodeProps
import com.viewcompose.ui.node.spec.PullToRefreshNodeProps
import com.viewcompose.ui.node.spec.SegmentedControlNodeProps
import com.viewcompose.ui.node.spec.SliderNodeProps
import com.viewcompose.ui.node.spec.SurfaceNodeProps
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.state.ScrollState
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope

// DOCS_REGION_START(ui-foundation-profile-summary)
fun UiTreeBuilder.ProfileSummary(name: String, role: String) {
    UiTheme {
        Column(spacing = 8.dp) {
            Text(name, style = TextDefaults.titleMediumStyle())
            Text(role, color = TextDefaults.secondaryColor())
        }
    }
}
// DOCS_REGION_END(ui-foundation-profile-summary)

private val AccountRole = uiLocalOf(debugName = "AccountRole") { "guest" }

fun UiTreeBuilder.profileEnvironmentSample() {
    // DOCS_REGION_START(ui-foundation-environment)
UiEnvironment {
    ProvideLocal(AccountRole, "admin") {
        Text(UiLocals.current(AccountRole))
    }
}
    // DOCS_REGION_END(ui-foundation-environment)
}

fun UiTreeBuilder.layoutDslSample() {
    Scaffold(
        topBar = { Text("Account") },
        bottomBar = { Divider() },
        floatingActionButton = { Button("Add", onClick = {}) },
    ) {
        Surface(onClick = {}) {
            Column {
                Card {
                    ListItem(headlineText = "Profile", supportingText = "Signed in")
                }
                Row {
                    Box(contentAlignment = BoxAlignment.Center) { Text("A") }
                    Spacer(modifier = Modifier.size(width = 8.dp, height = 8.dp))
                }
                FlowRow(maxItemsInEachRow = 2) {
                    Text("One")
                    Text("Two")
                }
                FlowColumn(maxItemsInEachColumn = 2) {
                    Text("Three")
                    Text("Four")
                }
            }
        }
    }
}

fun UiTreeBuilder.contentDslSample(document: com.viewcompose.text.TextDocument) {
    BadgedBox(badge = { Badge(count = 3) }) {
        Column {
            Text("Inbox")
            RichText(document)
        }
    }
}

fun UiTreeBuilder.actionCompositeSample() {
    Chip(label = "Filter", selected = true, onClick = {})
}

fun UiTreeBuilder.searchBarSample(state: TextFieldState) {
    SearchBar(state = state, onSearch = { query -> check(query == state.text) })
}

fun UiTreeBuilder.lazyListDslSample() {
    LazyColumn {
        stickyHeader("header", StaticContentRevision) { Text("Header") }
        item("row", 1, contentType = "text") { Text("Row") }
    }
    LazyRow {
        item("chip", 1, contentType = "text") { Text("Chip") }
    }
}

fun staticContentRevisionSample() {
    buildVNodeTree {
        LazyColumn {
            stickyHeader("header", StaticContentRevision, contentType = "header") {
                Text("Header")
            }
            item("row", StaticContentRevision, contentType = "row") {
                Text("Static row")
            }
        }
        LazyVerticalGrid {
            stickyHeader("grid-header", StaticContentRevision, contentType = "grid-header") {
                Text("Grid header")
            }
            item("grid-row", StaticContentRevision, contentType = "grid-row") {
                Text("Static grid row")
            }
        }
        HorizontalPager(currentPage = 0, onPageChanged = {}) {
            Page("page", StaticContentRevision, contentType = "page") {
                Text("Static page")
            }
        }
        TabRow(selectedIndex = 0, onTabSelected = {}) {
            Tab("tab", StaticContentRevision) { Text("Static tab") }
        }
    }
}

fun UiTreeBuilder.feedbackDslSample() {
    Snackbar(visible = true, message = "Saved", requestKey = "save")
    Toast(visible = true, message = "Connected", requestKey = "connection")
    Dialog(visible = true, requestKey = "confirm", onDismissRequest = {}) {
        Text("Confirm")
    }
    Popup(visible = true, anchorId = "account-anchor", onDismissRequest = {}) {
        Text("Account")
    }
    PlainTooltip(text = "Open account", visible = true, anchorId = "account-anchor")
    DropdownMenu(expanded = true, anchorId = "account-anchor", onDismissRequest = {}) {
        DropdownMenuItem(text = "Settings", onClick = {})
    }
}

fun emittedContentClosureSample() {
    val status = "Ready"
    val node = buildVNodeTree {
        emit(
            type = NodeType.Box,
            key = "status-container",
            spec = BoxNodeProps(contentAlignment = BoxAlignment.Center),
        ) {
            Text(status)
        }
    }.single()

    check(node.type == NodeType.Box)
    check(node.key == "status-container")
    check(node.children.single().type == NodeType.Text)
}

@UiDslMarker
private class SampleContainerScope : UiTreeBuilder() {
    var contentAlignment: BoxAlignment = BoxAlignment.TopStart
        private set

    fun alignContent(alignment: BoxAlignment) {
        contentAlignment = alignment
    }
}

private fun UiTreeBuilder.SampleScopedContainer(content: SampleContainerScope.() -> Unit) {
    emitScoped(
        type = NodeType.Box,
        scopeFactory = ::SampleContainerScope,
        spec = { BoxNodeProps(contentAlignment = contentAlignment) },
        content = content,
    )
}

fun scopedContainerEmissionSample() {
    val node = buildVNodeTree {
        SampleScopedContainer {
            alignContent(BoxAlignment.Center)
            Text("Centered")
        }
    }.single()

    check((node.spec as BoxNodeProps).contentAlignment == BoxAlignment.Center)
    check(node.children.single().type == NodeType.Text)
}

fun UiTreeBuilder.observedTextValueSample(counter: State<Int>) {
    Text(
        text = observedValue { "Count: ${counter.value}" },
        key = "counter",
    )
}

fun UiTreeBuilder.observedNodeSpecSample(
    label: State<String>,
    prefix: String,
) {
    emit(
        type = NodeType.Text,
        key = "status",
        spec = observedNodeSpec(inputs = listOf(prefix)) {
            TextNodeProps(
                document = TextDocument.plain("$prefix: ${label.value}"),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Start,
                textColor = 0xFF000000.toInt(),
                textSizeSp = 16.sp,
            )
        },
    )
}

private data class RevisionSampleRow(
    val id: Long,
    val version: Int,
    val label: String,
)

fun lazyCollectionRevisionSample() {
    val rows = listOf(RevisionSampleRow(id = 7L, version = 3, label = "Ready"))
    val compact = true
    val list = buildVNodeTree {
        LazyColumn(
            items = rows,
            key = RevisionSampleRow::id,
            contentType = { "status-row" },
            // Ordinary captures that affect item content also belong in its semantic revision.
            contentRevision = { row -> row.version to compact },
        ) { row ->
            Text(if (compact) row.label else "Status: ${row.label}")
        }
    }.single()
    val item = (list.spec as LazyColumnNodeProps).items.single()

    check(item.key == 7L)
    check(item.contentType == "status-row")
    check(item.contentRevision == (3 to true))

    val topLevelVariants = buildVNodeTree {
        LazyRow(
            items = rows,
            key = RevisionSampleRow::id,
            contentRevision = RevisionSampleRow::version,
        ) { row ->
            Text(row.label)
        }
        LazyVerticalGrid(
            items = rows,
            key = RevisionSampleRow::id,
            contentRevision = RevisionSampleRow::version,
        ) { row ->
            Text(row.label)
        }
        LazyColumn {
            items(
                items = rows,
                key = RevisionSampleRow::id,
                contentRevision = RevisionSampleRow::version,
            ) { row ->
                Text(row.label)
            }
        }
        LazyVerticalGrid {
            items(
                items = rows,
                key = RevisionSampleRow::id,
                contentRevision = RevisionSampleRow::version,
            ) { row ->
                Text(row.label)
            }
        }
    }

    val nestedCollections = buildVNodeTree {
        PullToRefresh(isRefreshing = false, onRefresh = {}) {
            LazyColumn(
                items = rows,
                key = RevisionSampleRow::id,
                contentRevision = RevisionSampleRow::version,
            ) { row ->
                Text(row.label)
            }
        }
        PullToRefresh(isRefreshing = false, onRefresh = {}) {
            LazyRow(
                items = rows,
                key = RevisionSampleRow::id,
                contentRevision = RevisionSampleRow::version,
            ) { row ->
                Text(row.label)
            }
        }
        PullToRefresh(isRefreshing = false, onRefresh = {}) {
            LazyVerticalGrid(
                items = rows,
                key = RevisionSampleRow::id,
                contentRevision = RevisionSampleRow::version,
            ) { row ->
                Text(row.label)
            }
        }
    }

    check(topLevelVariants.size == 4)
    check((nestedCollections[1].children.single().spec as LazyRowNodeProps).items.single().key == 7L)
}

/** Emits a million logical positions without allocating a million item models or key entries. */
fun compactLazyItemTableSample() {
    val list = buildVNodeTree {
        val factory = lazyItemContentFactory<Int>(retainedKeys = emptySet()) { index ->
            Text("Row $index")
        }
        val table = object : LazyItemTable {
            override val size: Int = 1_000_000

            override fun get(index: Int) = factory.createItem(
                key = index,
                contentRevision = index,
                payload = index,
            )

            override fun indexOfKey(key: Any): Int =
                (key as? Int)?.takeIf { it in 0 until size } ?: -1

            override fun updatesFrom(previous: LazyItemTable): List<LazyItemTableUpdate>? =
                if (previous === this) emptyList() else listOf(LazyItemTableUpdate.ReloadAll)
        }
        emit(
            type = NodeType.LazyColumn,
            spec = LazyColumnNodeProps(
                contentPadding = com.viewcompose.ui.node.policy.LazyContentPadding.None,
                spacing = com.viewcompose.ui.unit.UiDp.Zero,
                items = table,
            ),
        )
    }.single()

    val table = (list.spec as LazyColumnNodeProps).items
    check(table.size == 1_000_000)
    check(table.indexOfKey(42) == 42)
}

fun lazyItemsSnapshotSample() {
    val source = mutableListOf(RevisionSampleRow(id = 7L, version = 3, label = "Ready"))
    val snapshot = source.toLazyItemsSnapshot()
    source += RevisionSampleRow(id = 8L, version = 1, label = "Added later")
    val status = mutableStateOf("Online")

    val list = buildVNodeTree {
        LazyColumn(
            items = snapshot,
            key = RevisionSampleRow::id,
            contentType = { "status-row" },
            contentRevision = RevisionSampleRow::version,
        ) { row ->
            // State read by item content remains independently observable after an exact hit.
            Text("${row.label}: ${status.value}")
        }
    }.single()
    val items = (list.spec as LazyColumnNodeProps).items

    check(items.size == 1)
    check(items.single().key == 7L)
    check(items.single().contentRevision == 3)
}

fun observedLazyItemsSnapshotSample() {
    val rows = mutableStateOf(
        listOf(RevisionSampleRow(id = 7L, version = 3, label = "Ready"))
            .toLazyItemsSnapshot(),
    )
    val list = buildVNodeTree {
        LazyColumn(
            items = observedValue { rows.value },
            key = RevisionSampleRow::id,
            contentRevision = RevisionSampleRow::version,
        ) { _, row ->
            Text(row.map(transform = RevisionSampleRow::label))
        }
    }.single()

    check((list.spec as LazyColumnNodeProps).items.single().key == 7L)
}

fun pagerAndTabIdentitySample() {
    val tree = buildVNodeTree {
        HorizontalPager(currentPage = 0, onPageChanged = {}) {
            Page("account", 4, contentType = "account-page") {
                Text("Account")
            }
        }
        TabRow(selectedIndex = 0, onTabSelected = {}) {
            Tab("overview", 2) { selected ->
                Text(if (selected) "Overview selected" else "Overview")
            }
        }
    }
    val page = (tree.first().spec as HorizontalPagerNodeProps).pages.single()

    check(page.key == "account")
    check(page.contentRevision == 4)
    check(tree.last().children.single().key == "overview")
}

fun delayedContentSingleRootSample() {
    buildVNodeTree {
        LazyColumn {
            item("account", StaticContentRevision) {
                Column {
                    Text("Account")
                    Text("Signed in")
                }
            }
        }
        HorizontalPager(currentPage = 0, onPageChanged = {}) {
            Page("details", StaticContentRevision) {
                Column {
                    Text("Details")
                    Button("Continue", onClick = {})
                }
            }
        }
    }
}

fun focusVisibilityOwnershipSample() {
    buildVNodeTree {
        LazyColumn {
            item("lazy-editor", StaticContentRevision) {
                TextField(state = TextFieldState(), placeholder = "Search messages")
            }
        }
        LazyVerticalGrid {
            item("grid-editor", StaticContentRevision) {
                TextField(state = TextFieldState(), placeholder = "Search products")
            }
        }
        ScrollableColumn {
            Text("Account settings")
            TextField(state = TextFieldState(), placeholder = "Account name")
        }
        VerticalPager(currentPage = 0, onPageChanged = {}) {
            Page("form", StaticContentRevision) {
                ScrollableColumn {
                    Text("Page-local form")
                    TextField(state = TextFieldState(), placeholder = "Page value")
                }
            }
        }
    }
}

fun themeStateColorSample() {
    val colors = UiStateColor(
        defaultColor = 0xFF333333.toInt(),
        disabledColor = 0x61333333,
        pressedColor = 0xFF111111.toInt(),
        selectedColor = 0xFF0066CC.toInt(),
    )

    check(colors.resolve(selected = true) == 0xFF0066CC.toInt())
    check(colors.resolve(enabled = false, pressed = true) == 0x61333333)
}

fun themeProviderSample() {
    val customPrimary = 0xFF336699.toInt()
    val tokens = UiThemeDefaults.light().let { defaults ->
        defaults.copy(colors = defaults.colors.copy(primary = customPrimary))
    }
    var observedPrimary = 0

    buildVNodeTree {
        UiTheme(tokens = tokens) {
            observedPrimary = Theme.colors.primary
        }
    }

    check(observedPrimary == customPrimary)
}

fun buttonSample() {
    val tokens = UiThemeDefaults.light().let { defaults ->
        defaults.copy(
            controls = defaults.controls.copy(
                button = defaults.controls.button.copy(
                    mediumHeight = 48.dp,
                    mediumVisualHeight = 40.dp,
                ),
            ),
        )
    }

    val node = buildVNodeTree {
        UiTheme(tokens) {
            Button(
                text = "Confirm",
                onClick = {},
                overrides = ButtonOverrides(
                    stateLayerColors = UiStateLayerColors(
                        pressedColor = 0x1AFFFFFF,
                        focusedColor = 0x1AFFFFFF,
                        hoveredColor = 0x14FFFFFF,
                    ),
                ),
            )
        }
    }.single()
    val spec = node.spec as ButtonNodeProps

    check(spec.minHeight == 48.dp)
    check(spec.visualHeight == 40.dp)
    val indication = node.modifier.elements
        .filterIsInstance<InteractionIndicationModifierElement>()
        .single()
        .indication
    check((indication as UiInteractionIndication.StateLayer).colors.hoveredColor == 0x14FFFFFF)
}

fun componentOverridesSample() {
    val node = buildVNodeTree {
        ProvideButtonOverrides(ButtonOverrides(contentColor = 0xFFFFFFFF.toInt())) {
            ProvideButtonOverrides(ButtonOverrides(containerColor = 0xFF0055AA.toInt())) {
                Button(
                    text = "Scoped action",
                    overrides = ButtonOverrides(borderWidth = 2.dp),
                )
            }
        }
    }.single()
    val spec = node.spec as ButtonNodeProps

    check(spec.textColor == 0xFFFFFFFF.toInt())
    check(spec.backgroundColor == 0xFF0055AA.toInt())
    check(spec.borderWidth == 2.dp)

    val actionNodes = buildVNodeTree {
        Column {
            Button(text = "Learn more", onClick = {}, variant = ButtonVariant.Text)
            ProvideIconButtonOverrides(IconButtonOverrides(contentColor = 0xFF0055AA.toInt())) {
                IconButton(
                    icon = ImageSource.Resource(1),
                    contentDescription = "Close",
                    onClick = {},
                )
            }
        }
    }.single()
    check(actionNodes.children.size == 2)

    val inputNodes = buildVNodeTree {
        ProvideTextFieldOverrides(TextFieldOverrides(containerColor = 0xFFF4F6F8.toInt())) {
            ProvideCheckboxOverrides(CheckboxOverrides(uncheckedColor = 0xFF667788.toInt())) {
                ProvideSwitchOverrides(
                    SwitchOverrides(
                        checkedThumbColor = 0xFFFFFFFF.toInt(),
                        checkedTrackColor = 0xFF0055AA.toInt(),
                    ),
                ) {
                    ProvideRadioButtonOverrides(
                        RadioButtonOverrides(checkedColor = 0xFF0055AA.toInt()),
                    ) {
                        ProvideSliderOverrides(
                            SliderOverrides(activeTrackColor = 0xFF0055AA.toInt()),
                        ) {
                            Column {
                                TextField(state = TextFieldState(), placeholder = "Name")
                                Checkbox(text = "Email updates", checked = false, onCheckedChange = {})
                                Switch(text = "Sync", checked = true, onCheckedChange = {})
                                RadioButton(text = "Primary", checked = true, onCheckedChange = {})
                                Slider(value = 50, onValueChange = {})
                            }
                        }
                    }
                }
            }
        }
    }.single()
    check(inputNodes.children.size == 5)

    val navigationNodes = buildVNodeTree {
        Column {
            ProvideSegmentedControlOverrides(
                SegmentedControlOverrides(indicatorColor = 0xFF0055AA.toInt()),
            ) {
                SegmentedControl(
                    items = listOf(
                        SegmentedControlItem(key = "day", label = "Day"),
                        SegmentedControlItem(key = "week", label = "Week"),
                    ),
                    selectedIndex = 0,
                    onSelectionChange = {},
                )
            }
            ProvideTabRowOverrides(TabRowOverrides(indicatorColor = 0xFF0055AA.toInt())) {
                TabRow(selectedIndex = 0, onTabSelected = {}) {
                    Tab(
                        key = "summary",
                        contentRevision = StaticContentRevision,
                    ) { Text("Summary") }
                }
            }
            ProvideNavigationBarOverrides(
                NavigationBarOverrides(indicatorColor = 0xFFCCE4FF.toInt()),
            ) {
                NavigationBar(selectedIndex = 0, onItemSelected = {}) {
                    Item(key = "home", label = "Home", icon = ImageSource.Resource(1))
                }
            }
            ProvideLinearProgressIndicatorOverrides(
                LinearProgressIndicatorOverrides(indicatorColor = 0xFF0055AA.toInt()),
            ) {
                LinearProgressIndicator(progress = 0.5f)
            }
            ProvideCircularProgressIndicatorOverrides(
                CircularProgressIndicatorOverrides(indicatorColor = 0xFF0055AA.toInt()),
            ) {
                CircularProgressIndicator(progress = 0.5f)
            }
        }
    }.single()
    check(navigationNodes.children.size == 5)
}

fun remainingComponentOverridesSample() {
    val overlayStore = OverlayRequestStore()
    OverlayRequestContext.withStore(overlayStore) {
        buildVNodeTree {
            ProvideFloatingActionButtonOverrides(
                FloatingActionButtonOverrides(containerColor = 0xFF0055AA.toInt()),
            ) {
                FloatingActionButton(onClick = {}) {
                    Text("+")
                }
            }
            ProvideExtendedFloatingActionButtonOverrides(
                ExtendedFloatingActionButtonOverrides(contentColor = 0xFFFFFFFF.toInt()),
            ) {
                ExtendedFloatingActionButton(text = "Create", onClick = {})
            }
            ProvideTopAppBarOverrides(
                TopAppBarOverrides(actionIconColor = 0xFF0055AA.toInt()),
            ) {
                TopAppBar(title = "Library")
            }
            ProvideBottomAppBarOverrides(
                BottomAppBarOverrides(contentColor = 0xFF0055AA.toInt()),
            ) {
                BottomAppBar { Text("Actions") }
            }
            ProvideBadgeOverrides(BadgeOverrides(containerColor = 0xFFB3261E.toInt())) {
                Badge(count = 3)
            }
            ProvideAlertDialogOverrides(
                AlertDialogOverrides(containerColor = 0xFFF7F2FA.toInt()),
            ) {
                AlertDialog(
                    visible = true,
                    title = "Discard draft?",
                    text = "This action cannot be undone.",
                    confirmButtonText = "Discard",
                    onConfirm = {},
                )
            }
        }
    }
    check(overlayStore.currentRequests().size == 1)
}

fun modalBottomSheetAppearanceSample() {
    val overlayStore = OverlayRequestStore()
    OverlayRequestContext.withStore(overlayStore) {
        buildVNodeTree {
            ProvideModalBottomSheetOverrides(
                ModalBottomSheetOverrides(
                    containerColor = 0xFFF7F2FA.toInt(),
                    contentColor = 0xFF1D1B20.toInt(),
                    navigationBarColor = ModalBottomSheetNavigationBarColor.PlatformDefault,
                ),
            ) {
                ModalBottomSheet(
                    visible = true,
                    requestKey = "account-actions",
                    overrides = ModalBottomSheetOverrides(scrimOpacity = 0.4f),
                ) {
                    Text("Account actions")
                }
            }
        }
    }
    check(overlayStore.currentRequests().size == 1)
}

fun basicTextFieldStyleSample() {
    val style = BasicTextFieldStyle(
        cursorColor = 0xFF0055AA.toInt(),
        textColor = 0xFF101820.toInt(),
        textStyle = com.viewcompose.ui.foundation.UiTextStyle(fontSizeSp = 16.sp),
        placeholderColor = 0xFF667788.toInt(),
        containerColor = 0xFFF4F6F8.toInt(),
        borderWidth = 1.dp,
        borderColor = 0xFF8899AA.toInt(),
        shape = UiShape.rounded(8.dp),
        minimumHeight = 48.dp,
        horizontalPadding = 12.dp,
        verticalPadding = 8.dp,
    )
    val node = buildVNodeTree {
        BasicTextField(
            state = TextFieldState(TextFieldValue("Ready")),
            style = style,
        )
    }.single()
    val spec = node.spec as TextFieldNodeProps

    check(spec.backgroundColor == style.containerColor)
    check(spec.cursorColor == style.cursorColor)
    check(spec.minHeight == style.minimumHeight)
}

fun textFieldVariantsSample() {
    val tree = buildVNodeTree {
        ProvideTextFieldOverrides(TextFieldOverrides(horizontalPadding = 14.dp)) {
            Column {
                TextField(
                    state = TextFieldState(TextFieldValue("Ada")),
                    label = "Name",
                    overrides = TextFieldOverrides(containerColor = 0xFFF4F6F8.toInt()),
                )
                TextField(
                    state = TextFieldState(),
                    label = "Password",
                    inputProfile = TextFieldInputProfile.Password,
                )
                TextField(
                    state = TextFieldState(),
                    label = "Email",
                    inputProfile = TextFieldInputProfile.Email,
                )
                TextField(
                    state = TextFieldState(),
                    label = "Age",
                    inputProfile = TextFieldInputProfile.Number,
                )
                TextField(
                    state = TextFieldState(),
                    label = "Notes",
                    linePolicy = TextFieldLinePolicy.MultiLine(),
                )
            }
        }
    }.single()

    check(tree.children.size == 5)
}

fun switchSizingTokenSample() {
    val compactSwitch = UiSwitchSizing(
        trackWidth = 44.dp,
        trackHeight = 24.dp,
        thumbDiameter = 18.dp,
        trackPadding = 3.dp,
        labelSpacing = 14.dp,
    )
    val tokens = UiThemeDefaults.light().let { defaults ->
        defaults.copy(
            controls = defaults.controls.copy(
                switch = compactSwitch,
                minimumInteractiveHeight = 48.dp,
            ),
        )
    }

    var resolvedSizing = UiSwitchSizing.default()
    buildVNodeTree {
        UiTheme(tokens) {
            resolvedSizing = InputControlDefaults.switchSizing()
        }
    }

    check(resolvedSizing == compactSwitch)
    check(tokens.controls.minimumInteractiveHeight == 48.dp)
}

fun basicSurfaceSample() {
    val node = buildVNodeTree {
        BasicSurface(
            style = BasicSurfaceStyle(
                fill = Brush.SolidColor(0xFF1E4D5A.toInt()),
                shape = UiShape.continuous(16.dp),
                borderWidth = 1.dp,
                borderColor = 0xFF8FD8E8.toInt(),
                clipContent = true,
                interactionIndication = UiInteractionIndication.StateLayer(
                    UiStateLayerColors(
                        pressedColor = 0x33FFFFFF,
                        focusedColor = 0x2AFFFFFF,
                        hoveredColor = 0x1FFFFFFF,
                    ),
                ),
            ),
            contentColor = 0xFFFFFFFF.toInt(),
            onClick = {},
            minimumHeight = 48.dp,
            visualHeight = 40.dp,
        ) {
            Text("Open")
        }
    }.single()

    val surface = node.spec as SurfaceNodeProps
    check(surface.minimumHeight == 48.dp)
    check(surface.visualHeight == 40.dp)
}

fun basicButtonSample() {
    val tree = buildVNodeTree {
        BasicButton(
            text = "Continue",
            onClick = {},
            style = BasicButtonStyle(
                surface = BasicSurfaceStyle(
                    fill = Brush.SolidColor(0xFF244C5A.toInt()),
                    shape = UiShape.continuous(20.dp),
                    clipContent = true,
                ),
                contentColor = 0xFFFFFFFF.toInt(),
                textStyle = com.viewcompose.ui.foundation.UiTextStyle(fontSizeSp = 14.sp),
                stateLayerColors = UiStateLayerColors(
                    pressedColor = 0x33FFFFFF,
                    focusedColor = 0x2AFFFFFF,
                    hoveredColor = 0x1FFFFFFF,
                ),
                minimumHeight = 48.dp,
                visualHeight = 40.dp,
                paddingHorizontal = 16.dp,
            ),
        )
    }

    check(tree.single().type == com.viewcompose.ui.node.NodeType.Surface)
}

fun compactInputTargetSample() {
    val tokens = UiThemeDefaults.light().let { defaults ->
        defaults.copy(
            controls = defaults.controls.copy(minimumInteractiveHeight = 48.dp),
        )
    }

    val nodes = buildVNodeTree {
        UiTheme(tokens) {
            Checkbox(text = "Share diagnostics", checked = true, onCheckedChange = {})
            Slider(
                value = 50,
                onValueChange = {},
                overrides = SliderOverrides(
                    thumbColor = 0xFF6750A4.toInt(),
                    activeTrackColor = 0xFF6750A4.toInt(),
                    inactiveTrackColor = 0xFFE8DEF8.toInt(),
                ),
            )
        }
    }

    nodes.forEach { node ->
        val target = node.modifier.elements.first() as MinHeightModifierElement
        check(target.minHeight == 48.dp)
    }
}

fun sliderInteractionSample() {
    val events = mutableListOf<String>()
    val node = buildVNodeTree {
        Slider(
            value = 4,
            onValueChange = { value -> events += "change:$value" },
            min = 0,
            max = 12,
            step = 4,
            onValueChangeStarted = { events += "start" },
            onValueChangeFinished = { events += "finish" },
        )
    }.single()
    val slider = node.spec as SliderNodeProps

    slider.onValueChangeStarted?.invoke()
    slider.onValueChange?.invoke(8)
    slider.onValueChangeFinished?.invoke()
    check(events == listOf("start", "change:8", "finish"))
}

fun eagerScrollStateSample() {
    val state = ScrollState()
    buildVNodeTree {
        ScrollableColumn(state = state, userScrollEnabled = true) {
            Text("Scrollable content")
        }
    }

    state.scrollTo(24)
    check(state.value == 24)
}

fun adaptiveGridSample() {
    val node = buildVNodeTree {
        LazyVerticalGrid(cells = GridCells.Adaptive(minSize = 120.dp)) {
            item(
                "heading",
                StaticContentRevision,
                span = GridItemSpan.FullLine,
            ) {
                Text("Gallery")
            }
            items(items = listOf("one", "two"), key = { it }) { label ->
                Text(label)
            }
        }
    }.single()
    val grid = node.spec as LazyVerticalGridNodeProps

    check(grid.cells == GridCells.Adaptive(120.dp))
    check(grid.items.first().span == GridItemSpan.FullLine)
}

fun stableSelectionItemIdentitySample() {
    val nodes = buildVNodeTree {
        NavigationBar(selectedIndex = 0, onItemSelected = {}) {
            Item(key = "inbox", label = "Inbox", icon = ImageSource.Resource(1))
            Item(
                key = "archive",
                label = "Archive",
                icon = ImageSource.Resource(2),
                enabled = false,
            )
        }
        SegmentedControl(
            items = listOf(
                SegmentedControlItem(key = "day", label = "Day"),
                SegmentedControlItem(key = "week", label = "Week", enabled = false),
            ),
            selectedIndex = 0,
            onSelectionChange = {},
        )
    }

    val navigation = nodes.first().spec as NavigationBarNodeProps
    val segments = nodes.last().spec as SegmentedControlNodeProps
    check(navigation.items.map { it.key } == listOf("inbox", "archive"))
    check(!segments.items.last().enabled)
}

fun pullToRefreshEnablementSample() {
    val node = buildVNodeTree {
        PullToRefresh(
            isRefreshing = false,
            onRefresh = {},
            enabled = false,
        ) {
            ScrollableColumn {
                Text("Refresh disabled; content input remains available")
            }
        }
    }.single()

    check(!(node.spec as PullToRefreshNodeProps).enabled)
}

fun popupPositioningSample() {
    val position = PopupPositioner.calculate(
        anchorBounds = PopupBounds(left = 40, top = 40, right = 80, bottom = 64),
        popupSize = PopupSize(width = 72, height = 48),
        viewportBounds = PopupBounds(left = 0, top = 0, right = 120, bottom = 100),
        alignment = PopupAlignment.BelowStart,
        layoutDirection = UiLayoutDirection.Ltr,
        overflowPolicy = PopupOverflowPolicy.FlipThenClamp,
        windowMargin = 8,
    )

    check(position.x == 40)
    check(position.resolvedAlignment == PopupAlignment.AboveStart)
}

fun saveableStateRegistrySample() {
    val registry = createSaveableStateRegistry(
        restoredValues = mapOf("counter" to 3),
        canBeSaved = { value -> value == null || value is Int },
    )
    val restored = checkNotNull(registry.claimRestored("counter"))
    check(restored.value == 3)
    restored.commit()

    var counter = 4
    val entry = registry.registerProvider("counter") { counter }
    counter = 5
    check(registry.performSave()["counter"] == 5)
    entry.unregister()
}

fun scopedRememberSaveableSample() {
    val registry = createSaveableStateRegistry()
    buildVNodeTree {
        ProvideSaveableStateRegistry(registry) {
            LazyColumn {
                items(
                    items = listOf("inbox", "archive"),
                    key = { mailbox -> mailbox },
                ) { mailbox ->
                    val expanded = rememberSaveable(key = "expanded") {
                        mutableStateOf(false)
                    }
                    Text("$mailbox expanded=${expanded.value}")
                }
            }
        }
    }
}

fun imageLoadingSample() {
    val loader = UiImageLoader { _, _ -> UiImageLoadHandle {} }
    val nodes = buildVNodeTree {
        ProvideImageLoader(loader) {
            Image(
                source = ImageSource.Resource(1),
                contentDescription = "Profile photo",
                requestOptions = UiImageRequestOptions(
                    decodeSize = UiImageDecodeSize.Fixed(width = 320.dp, height = 180.dp),
                ),
            )
            Icon(
                source = ImageSource.Url("https://example.com/status.png"),
                contentDescription = "Online",
            )
            IconButton(
                icon = ImageSource.Resource(2),
                contentDescription = "Close",
                onClick = {},
                overrides = IconButtonOverrides(
                    stateLayerColors = UiStateLayerColors(
                        pressedColor = 0x1A000000,
                        focusedColor = 0x1A000000,
                        hoveredColor = 0x14000000,
                    ),
                ),
            )
        }
    }

    check(nodes.size == 3)
    check((nodes.first().spec as ImageNodeSpec).imageLoader === loader)
}

/** Publishes a committed value only when its explicit revision changes. */
fun UiTreeBuilder.sideEffectSample(
    revision: Long,
    publish: (Long) -> Unit,
) {
    SideEffect(revision) {
        publish(revision)
    }
}

/** Registers one listener and pairs it with mandatory cleanup. */
fun UiTreeBuilder.disposableEffectSample(
    source: Any,
    subscribe: () -> (() -> Unit),
) {
    DisposableEffect(source) {
        val unsubscribe = subscribe()
        onDispose(unsubscribe)
    }
}

/** Loads suspending data for one request identity. */
fun UiTreeBuilder.launchedEffectSample(
    requestId: String,
    load: suspend CoroutineScope.() -> Unit,
) {
    LaunchedEffect(requestId, block = load)
}

/** Keeps a long-lived subscription pointed at the latest event callback. */
fun UiTreeBuilder.rememberUpdatedStateSample(
    source: Any,
    onEvent: (String) -> Unit,
    subscribe: ((String) -> Unit) -> (() -> Unit),
) {
    val currentOnEvent = rememberUpdatedState(onEvent)
    DisposableEffect(source) {
        val unsubscribe = subscribe { value ->
            currentOnEvent.value(value)
        }
        onDispose(unsubscribe)
    }
}

/** Exposes a composition-owned scope to an event-binding adapter after commit. */
fun UiTreeBuilder.rememberCoroutineScopeSample(
    bind: (CoroutineScope) -> Unit,
) {
    val scope = rememberCoroutineScope()
    SideEffect(scope) {
        bind(scope)
    }
}

/** Produces observable state from one keyed suspending request. */
fun UiTreeBuilder.produceStateSample(
    requestId: String,
    load: suspend () -> String,
) = produceState(initialValue = "Loading", requestId) {
    value = load()
}

/** Marks a callback owned by a custom effect integration without restoring composition locals. */
fun compositionEffectContextSample(
    callback: () -> Unit,
) {
    CompositionEffectContext.run(callback)
}
