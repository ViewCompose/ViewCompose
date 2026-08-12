package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core widget/extra 中的 Additional Widget Coverage 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Additional Widget Coverage behavior in widget-core widget/extra and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextFieldImeAction
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.FlowRowNodeProps
import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.LazyRowNodeProps
import com.viewcompose.ui.node.spec.LazyVerticalGridNodeProps
import com.viewcompose.ui.node.spec.NavigationBarNodeProps
import com.viewcompose.ui.node.spec.RowNodeProps
import com.viewcompose.ui.node.spec.ScrollableColumnNodeProps
import com.viewcompose.ui.node.spec.ScrollableRowNodeProps
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.spec.VerticalPagerNodeProps
import com.viewcompose.ui.node.policy.CollectionMotionPolicy
import com.viewcompose.ui.node.policy.CollectionReusePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdditionalWidgetCoverageTest {
    @Test
    fun `chip composes row with label and icon slots`() {
        val tree = buildVNodeTree {
            Chip(
                label = "Sync",
                onClick = {},
                variant = ChipVariant.Filter,
                selected = true,
                leadingIcon = ImageSource.Resource(10),
                onTrailingIconClick = {},
            )
        }

        val node = tree.single()
        val spec = node.spec as RowNodeProps
        val textChildren = collectTextNodes(node)

        assertEquals(NodeType.Row, node.type)
        assertEquals(ChipDefaults.iconSpacing(), spec.spacing)
        assertEquals(VerticalAlignment.Center, spec.verticalAlignment)
        assertEquals(ChipDefaults.pressedColor(), spec.rippleColor)
        assertEquals(
            stateLayerColorsFor(
                ChipDefaults.contentColor(
                    variant = ChipVariant.Filter,
                    selected = true,
                    enabled = true,
                ),
            ),
            spec.stateLayerColors,
        )
        assertTrue(textChildren.any { it.document.text == "Sync" })
        assertTrue(node.children.size >= 2)
    }

    @Test
    fun `search bar emits text field child with search ime action`() {
        val tree = buildVNodeTree {
            SearchBar(
                state = TextFieldState(TextFieldValue("query")),
                onSearch = {},
                placeholder = "Type keyword",
                leadingIcon = ImageSource.Resource(11),
                trailingIcon = {
                    Icon(source = ImageSource.Resource(12))
                },
            )
        }

        val node = tree.single()
        val rowSpec = node.spec as RowNodeProps
        val textField = node.children.first { it.type == NodeType.TextField }.spec as TextFieldNodeProps

        assertEquals(NodeType.Row, node.type)
        assertEquals(SearchBarDefaults.iconSpacing(), rowSpec.spacing)
        assertEquals(VerticalAlignment.Center, rowSpec.verticalAlignment)
        assertEquals(TextFieldValue("query"), textField.value)
        assertEquals("Type keyword", textField.placeholder)
        assertEquals(TextFieldImeAction.Search, textField.keyboardOptions.imeAction)
    }

    @Test
    fun `navigation bar emits items and selection props`() {
        val theme = UiThemeDefaults.light().copy(
            typography = UiTypography(
                titleMedium = UiTextStyle(fontSizeSp = 30.sp),
                bodyMedium = UiTextStyle(fontSizeSp = 18.sp),
                labelMedium = UiTextStyle(fontSizeSp = 14.sp),
                labelSmall = UiTextStyle(
                    fontSizeSp = 12.sp,
                    fontWeight = 600,
                    letterSpacingEm = 0.04f,
                    lineHeightSp = 18.sp,
                    includeFontPadding = true,
                ),
            ),
        )
        val tree = buildVNodeTree {
            UiTheme(theme) {
                NavigationBar(
                    selectedIndex = 1,
                    onItemSelected = {},
                ) {
                    Item(label = "Home", icon = ImageSource.Resource(1), badgeCount = 2)
                    Item(label = "Profile", icon = ImageSource.Resource(2))
                }
            }
        }

        val node = tree.single()
        val spec = node.spec as NavigationBarNodeProps

        assertEquals(NodeType.NavigationBar, node.type)
        assertEquals(1, spec.selectedIndex)
        assertEquals(2, spec.items.size)
        assertEquals("Home", spec.items[0].label)
        assertEquals(2, spec.items[0].badgeCount)
        assertEquals(theme.typography.labelSmall.fontWeight, spec.labelFontWeight)
        assertEquals(theme.typography.labelSmall.letterSpacingEm, spec.labelLetterSpacingEm)
        assertEquals(theme.typography.labelSmall.lineHeightSp, spec.labelLineHeightSp)
        assertEquals(theme.typography.labelSmall.includeFontPadding, spec.labelIncludeFontPadding)
    }

    @Test
    fun `scaffold composes top content fab and bottom slots`() {
        val tree = buildVNodeTree {
            Scaffold(
                topBar = { Text("Top") },
                bottomBar = { Text("Bottom") },
                floatingActionButton = { Text("Fab") },
            ) {
                Text("Body")
            }
        }

        val root = tree.single()
        val textChildren = collectTextNodes(root)

        assertEquals(NodeType.Column, root.type)
        assertEquals(3, root.children.size)
        assertTrue(textChildren.any { it.document.text == "Top" })
        assertTrue(textChildren.any { it.document.text == "Body" })
        assertTrue(textChildren.any { it.document.text == "Fab" })
        assertTrue(textChildren.any { it.document.text == "Bottom" })
    }

    @Test
    fun `lazy row emits content padding spacing and keyed items`() {
        val reusePolicy = CollectionReusePolicy(sharePool = true)
        val motionPolicy = CollectionMotionPolicy(
            disableItemAnimator = true,
            animateInsert = false,
            animateRemove = true,
            animateMove = false,
            animateChange = true,
        )
        val tree = buildVNodeTree {
            LazyRow(
                contentPadding = com.viewcompose.ui.node.policy.LazyContentPadding.all(14.dp),
                spacing = 6.dp,
                reusePolicy = reusePolicy,
                motionPolicy = motionPolicy,
            ) {
                items(
                    items = listOf("A", "B"),
                    key = { item -> item },
                ) { item ->
                    Text(item)
                }
            }
        }

        val node = tree.single()
        val spec = node.spec as LazyRowNodeProps

        assertEquals(NodeType.LazyRow, node.type)
        assertEquals(
            com.viewcompose.ui.node.policy.LazyContentPadding.all(14.dp),
            spec.contentPadding,
        )
        assertEquals(6.dp, spec.spacing)
        assertEquals("A", spec.items[0].key)
        assertEquals("B", spec.items[1].key)
        assertEquals(reusePolicy, spec.reusePolicy)
        assertEquals(motionPolicy, spec.motionPolicy)
    }

    @Test
    fun `flow row emits spacing and max items props`() {
        val tree = buildVNodeTree {
            FlowRow(
                horizontalSpacing = 10.dp,
                verticalSpacing = 4.dp,
                maxItemsInEachRow = 3,
            ) {
                Text("1")
                Text("2")
                Text("3")
            }
        }

        val node = tree.single()
        val spec = node.spec as FlowRowNodeProps

        assertEquals(NodeType.FlowRow, node.type)
        assertEquals(10.dp, spec.horizontalSpacing)
        assertEquals(4.dp, spec.verticalSpacing)
        assertEquals(3, spec.maxItemsInEachRow)
    }

    @Test
    fun `scrollable column emits arrangement alignment and spacing`() {
        val tree = buildVNodeTree {
            ScrollableColumn(
                spacing = 9.dp,
                arrangement = MainAxisArrangement.SpaceBetween,
                horizontalAlignment = HorizontalAlignment.Center,
                focusFollowKeyboard = true,
            ) {
                Text("A")
            }
        }

        val node = tree.single()
        val spec = node.spec as ScrollableColumnNodeProps

        assertEquals(NodeType.ScrollableColumn, node.type)
        assertEquals(9.dp, spec.spacing)
        assertEquals(MainAxisArrangement.SpaceBetween, spec.arrangement)
        assertEquals(HorizontalAlignment.Center, spec.horizontalAlignment)
        assertTrue(spec.focusFollowKeyboard)
    }

    @Test
    fun `lazy grid and pagers emit reuse motion and focus policies`() {
        val reusePolicy = CollectionReusePolicy(sharePool = true)
        val motionPolicy = CollectionMotionPolicy(
            disableItemAnimator = true,
            animateInsert = false,
            animateRemove = false,
            animateMove = true,
            animateChange = false,
        )
        val gridTree = buildVNodeTree {
            LazyVerticalGrid(
                spanCount = 2,
                reusePolicy = reusePolicy,
                motionPolicy = motionPolicy,
                focusFollowKeyboard = true,
            ) {
                items(
                    items = listOf("A", "B"),
                    key = { item -> item },
                ) { item ->
                    Text(item)
                }
            }
        }
        val gridSpec = gridTree.single().spec as LazyVerticalGridNodeProps
        assertEquals(reusePolicy, gridSpec.reusePolicy)
        assertEquals(motionPolicy, gridSpec.motionPolicy)
        assertTrue(gridSpec.focusFollowKeyboard)

        val spannedGridTree = buildVNodeTree {
            LazyVerticalGrid(spanCount = 3) {
                stickyHeader(key = "header") { Text("Header") }
                items(
                    items = listOf(1, 2),
                    key = { item -> item },
                    span = { item -> item },
                ) { item ->
                    Text(item.toString())
                }
            }
        }
        val spannedItems =
            (spannedGridTree.single().spec as LazyVerticalGridNodeProps).items
        assertEquals(listOf(Int.MAX_VALUE, 1, 2), spannedItems.map { item -> item.span })

        val horizontalPagerTree = buildVNodeTree {
            HorizontalPager(
                currentPage = 0,
                onPageChanged = {},
                reusePolicy = reusePolicy,
                motionPolicy = motionPolicy,
            ) {
                Page(key = "p1") { Text("P1") }
                Page(key = "p2") { Text("P2") }
            }
        }
        val horizontalSpec = horizontalPagerTree.single().spec as HorizontalPagerNodeProps
        assertEquals(reusePolicy, horizontalSpec.reusePolicy)
        assertEquals(motionPolicy, horizontalSpec.motionPolicy)

        val verticalPagerTree = buildVNodeTree {
            VerticalPager(
                currentPage = 0,
                onPageChanged = {},
                reusePolicy = reusePolicy,
                motionPolicy = motionPolicy,
                focusFollowKeyboard = true,
            ) {
                Page(key = "p1") { Text("P1") }
                Page(key = "p2") { Text("P2") }
            }
        }
        val verticalSpec = verticalPagerTree.single().spec as VerticalPagerNodeProps
        assertEquals(reusePolicy, verticalSpec.reusePolicy)
        assertEquals(motionPolicy, verticalSpec.motionPolicy)
        assertTrue(verticalSpec.focusFollowKeyboard)
    }

    @Test
    fun `scrollable row emits arrangement alignment and spacing`() {
        val tree = buildVNodeTree {
            ScrollableRow(
                spacing = 7.dp,
                arrangement = MainAxisArrangement.End,
                verticalAlignment = VerticalAlignment.Bottom,
            ) {
                Text("A")
                Text("B")
            }
        }

        val node = tree.single()
        val spec = node.spec as ScrollableRowNodeProps

        assertEquals(NodeType.ScrollableRow, node.type)
        assertEquals(7.dp, spec.spacing)
        assertEquals(MainAxisArrangement.End, spec.arrangement)
        assertEquals(VerticalAlignment.Bottom, spec.verticalAlignment)
    }

    private fun collectTextNodes(node: VNode): List<TextNodeProps> {
        val result = mutableListOf<TextNodeProps>()
        fun visit(current: VNode) {
            val spec = current.spec
            if (spec is TextNodeProps) {
                result += spec
            }
            current.children.forEach(::visit)
        }
        visit(node)
        return result
    }
}
