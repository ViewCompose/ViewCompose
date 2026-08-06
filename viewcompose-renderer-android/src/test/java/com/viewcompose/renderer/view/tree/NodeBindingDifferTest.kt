package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.unit.sp

import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.UiDp

/*
 * 测试职责：覆盖 renderer view/tree 中的 Node Binding Differ 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Node Binding Differ behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import com.viewcompose.text.TextFieldState
import com.viewcompose.text.TextFieldValue
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionFactory
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.NavigationBarItem
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.collection.TabIndicatorPosition
import com.viewcompose.ui.node.collection.TabIndicatorWidthMode
import com.viewcompose.ui.node.collection.TabRowTab
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.ColumnNodeProps
import com.viewcompose.ui.node.spec.ConstraintGuidelineDirection
import com.viewcompose.ui.node.spec.ConstraintGuidelinePosition
import com.viewcompose.ui.node.spec.ConstraintGuidelineSpec
import com.viewcompose.ui.node.spec.ConstraintHelpersSpec
import com.viewcompose.ui.node.spec.ConstraintLayoutNodeProps
import com.viewcompose.ui.node.spec.CanvasNodeProps
import com.viewcompose.ui.node.spec.DividerNodeProps
import com.viewcompose.ui.node.spec.FlowColumnNodeProps
import com.viewcompose.ui.node.spec.FlowRowNodeProps
import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.IconButtonNodeProps
import com.viewcompose.ui.node.spec.ImageNodeProps
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.LazyVerticalGridNodeProps
import com.viewcompose.ui.node.spec.NavigationBarNodeProps
import com.viewcompose.ui.node.spec.ProgressIndicatorNodeProps
import com.viewcompose.ui.node.spec.RowNodeProps
import com.viewcompose.ui.node.spec.SegmentedControlNodeProps
import com.viewcompose.ui.node.spec.SliderNodeProps
import com.viewcompose.ui.node.spec.TabRowNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import com.viewcompose.ui.node.spec.ToggleNodeProps
import com.viewcompose.ui.node.spec.VerticalPagerNodeProps
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.node.spec.NodeSpec
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeBindingDifferTest {
    private data class UnknownNodeSpec(
        val value: Int,
    ) : NodeSpec

    @Test
    fun `returns skip self only when child tree changes`() {
        val previous = textNode(
            children = listOf(
                VNode(
                    type = NodeType.Text,
                    spec = TextNodeProps(
                        text = "child-1",
                        maxLines = 1,
                        overflow = com.viewcompose.ui.node.TextOverflow.Clip,
                        textAlign = com.viewcompose.ui.node.TextAlign.Start,
                        textColor = 0xFF000000.toInt(),
                        textSizeSp = 14.sp,
                    ),
                ),
            ),
        )
        val next = textNode(
            children = listOf(
                VNode(
                    type = NodeType.Text,
                    spec = TextNodeProps(
                        text = "child-2",
                        maxLines = 1,
                        overflow = com.viewcompose.ui.node.TextOverflow.Clip,
                        textAlign = com.viewcompose.ui.node.TextAlign.Start,
                        textColor = 0xFF000000.toInt(),
                        textSizeSp = 14.sp,
                    ),
                ),
            ),
        )

        assertSame(NodeBindingPlan.SkipSelfOnly, NodeBindingDiffer.plan(previous, next))
    }

    @Test
    fun `returns subtree skip when self and children are unchanged`() {
        val previous = textNode(text = "stable")
        val next = textNode(text = "stable")

        assertSame(NodeBindingPlan.SkipSubtree, NodeBindingDiffer.plan(previous, next))
    }

    @Test
    fun `returns subtree skip when equivalent modifier chains are rebuilt`() {
        val previous = textNode(
            text = "stable",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        val next = textNode(
            text = "stable",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )

        assertSame(NodeBindingPlan.SkipSubtree, NodeBindingDiffer.plan(previous, next))
    }

    @Test
    fun `returns subtree skip when vnode instance is reused`() {
        val node = textNode(text = "stable")

        assertSame(NodeBindingPlan.SkipSubtree, NodeBindingDiffer.plan(node, node))
    }

    @Test
    fun `rebinds when node environment changes`() {
        val previous = textNode()
        val next = textNode(
            environment = UiEnvironmentValues.Default.copy(
                layoutDirection = UiLayoutDirection.Rtl,
            ),
        )

        assertSame(NodeBindingPlan.Rebind, NodeBindingDiffer.plan(previous, next))
    }

    @Test
    fun `patches when node spec changes`() {
        val previous = textNode(text = "before")
        val next = textNode(text = "after")

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is TextNodePatch)
    }

    @Test
    fun `rebinds when modifier changes`() {
        val previous = textNode()
        val next = textNode(modifier = Modifier.padding(8.dp))

        assertSame(NodeBindingPlan.Rebind, NodeBindingDiffer.plan(previous, next))
    }

    @Test
    fun `rebinds when node spec changes but no patch factory exists`() {
        val previous = VNode(
            type = NodeType.Spacer,
            spec = UnknownNodeSpec(value = 1),
            modifier = Modifier,
        )
        val next = VNode(
            type = NodeType.Spacer,
            spec = UnknownNodeSpec(value = 2),
            modifier = Modifier,
        )

        assertSame(NodeBindingPlan.Rebind, NodeBindingDiffer.plan(previous, next))
    }

    @Test
    fun `patches button when text changes`() {
        val previous = buttonNode(text = "Continue")
        val next = buttonNode(text = "Continue now")

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is ButtonNodePatch)
    }

    @Test
    fun `patches button when style changes instead of rebinding`() {
        val previous = buttonNode(textColor = 0xFF000000.toInt())
        val next = buttonNode(textColor = 0xFFFF0000.toInt())

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is ButtonNodePatch)
    }

    @Test
    fun `patches text field semantic updates`() {
        val previous = textFieldNode(value = "before")
        val next = textFieldNode(value = "after")

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is TextFieldNodePatch)
    }

    @Test
    fun `patches text field when style changes instead of rebinding`() {
        val previous = textFieldNode(textColor = 0xFF000000.toInt())
        val next = textFieldNode(textColor = 0xFFFF0000.toInt())

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is TextFieldNodePatch)
    }

    @Test
    fun `patches segmented control semantic updates`() {
        val previous = segmentedControlNode(selectedIndex = 0)
        val next = segmentedControlNode(selectedIndex = 1)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is SegmentedControlNodePatch)
    }

    @Test
    fun `patches navigation bar semantic updates`() {
        val previous = navigationBarNode(selectedIndex = 0)
        val next = navigationBarNode(selectedIndex = 1)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is NavigationBarNodePatch)
    }

    @Test
    fun `patches tab row semantic updates`() {
        val previous = tabRowNode(selectedIndex = 0)
        val next = tabRowNode(selectedIndex = 1)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is TabRowNodePatch)
    }

    @Test
    fun `patches tab row when tab session closures refresh with stable tokens`() {
        val previous = tabRowNodeWithSessionNonce(selectedIndex = 0, sessionNonce = "A")
        val next = tabRowNodeWithSessionNonce(selectedIndex = 0, sessionNonce = "B")

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is TabRowNodePatch)
    }

    @Test
    fun `patches lazy column semantic updates`() {
        val previous = lazyColumnNode(spacing = 8.dp)
        val next = lazyColumnNode(spacing = 16.dp)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is LazyColumnNodePatch)
    }

    @Test
    fun `patches lazy vertical grid semantic updates`() {
        val previous = lazyVerticalGridNode(spanCount = 2)
        val next = lazyVerticalGridNode(spanCount = 3)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is LazyVerticalGridNodePatch)
    }

    @Test
    fun `patches horizontal pager semantic updates`() {
        val previous = horizontalPagerNode(currentPage = 0)
        val next = horizontalPagerNode(currentPage = 1)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is HorizontalPagerNodePatch)
    }

    @Test
    fun `patches horizontal pager when page session closures refresh with stable tokens`() {
        val previous = horizontalPagerNodeWithSessionNonce(currentPage = 0, sessionNonce = "A")
        val next = horizontalPagerNodeWithSessionNonce(currentPage = 0, sessionNonce = "B")

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is HorizontalPagerNodePatch)
    }

    @Test
    fun `patches vertical pager semantic updates`() {
        val previous = verticalPagerNode(currentPage = 0)
        val next = verticalPagerNode(currentPage = 1)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is VerticalPagerNodePatch)
    }

    @Test
    fun `patches vertical pager when page session closures refresh with stable tokens`() {
        val previous = verticalPagerNodeWithSessionNonce(currentPage = 0, sessionNonce = "A")
        val next = verticalPagerNodeWithSessionNonce(currentPage = 0, sessionNonce = "B")

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is VerticalPagerNodePatch)
    }

    @Test
    fun `patches toggle semantic updates`() {
        val previous = toggleNode(checked = false)
        val next = toggleNode(checked = true)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is ToggleNodePatch)
    }

    @Test
    fun `patches slider semantic updates`() {
        val previous = sliderNode(value = 10)
        val next = sliderNode(value = 50)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is SliderNodePatch)
    }

    @Test
    fun `patches progress indicator semantic updates`() {
        val previous = progressNode(progress = 0.3f)
        val next = progressNode(progress = 0.7f)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is ProgressIndicatorNodePatch)
    }

    @Test
    fun `patches divider semantic updates`() {
        val previous = dividerNode(color = 0xFFCCCCCC.toInt())
        val next = dividerNode(color = 0xFF000000.toInt())

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is DividerNodePatch)
    }

    @Test
    fun `patches canvas semantic updates`() {
        val previous = canvasNode(token = "before")
        val next = canvasNode(token = "after")

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is CanvasNodePatch)
    }

    @Test
    fun `patches row semantic updates`() {
        val previous = rowNode(spacing = 8.dp)
        val next = rowNode(spacing = 16.dp)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is RowNodePatch)
    }

    @Test
    fun `patches column semantic updates`() {
        val previous = columnNode(spacing = 8.dp)
        val next = columnNode(spacing = 16.dp)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is ColumnNodePatch)
    }

    @Test
    fun `patches box semantic updates`() {
        val previous = boxNode(contentAlignment = com.viewcompose.ui.layout.BoxAlignment.TopStart)
        val next = boxNode(contentAlignment = com.viewcompose.ui.layout.BoxAlignment.Center)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is BoxNodePatch)
    }

    @Test
    fun `patches constraint layout semantic updates`() {
        val previous = constraintLayoutNode(guidelineId = "guide-a")
        val next = constraintLayoutNode(guidelineId = "guide-b")

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is ConstraintLayoutNodePatch)
    }

    @Test
    fun `rebinds box when ripple changes`() {
        val previous = boxNode(
            contentAlignment = com.viewcompose.ui.layout.BoxAlignment.TopStart,
            rippleColor = 0x11000000,
        )
        val next = boxNode(
            contentAlignment = com.viewcompose.ui.layout.BoxAlignment.TopStart,
            rippleColor = 0x22000000,
        )

        val plan = NodeBindingDiffer.plan(previous, next)

        assertSame(NodeBindingPlan.Rebind, plan)
    }

    @Test
    fun `patches image semantic updates`() {
        val previous = imageNode(tint = 0xFF000000.toInt())
        val next = imageNode(tint = 0xFFFF0000.toInt())

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is ImageNodePatch)
    }

    @Test
    fun `patches image when equal loaders have different identities`() {
        val previous = imageNode(imageLoader = EqualLoader("first"))
        val next = imageNode(imageLoader = EqualLoader("second"))

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is ImageNodePatch)
    }

    @Test
    fun `patches icon button semantic updates`() {
        val previous = iconButtonNode(enabled = true)
        val next = iconButtonNode(enabled = false)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is IconButtonNodePatch)
    }

    @Test
    fun `patches flow row semantic updates`() {
        val previous = flowRowNode(horizontalSpacing = 8.dp)
        val next = flowRowNode(horizontalSpacing = 16.dp)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is FlowRowNodePatch)
    }

    @Test
    fun `patches flow column semantic updates`() {
        val previous = flowColumnNode(verticalSpacing = 8.dp)
        val next = flowColumnNode(verticalSpacing = 16.dp)

        val plan = NodeBindingDiffer.plan(previous, next)

        assertTrue(plan is NodeBindingPlan.Patch)
        assertTrue((plan as NodeBindingPlan.Patch).patch is FlowColumnNodePatch)
    }

    private fun textNode(
        text: String = "value",
        modifier: Modifier = Modifier,
        children: List<VNode> = emptyList(),
        environment: UiEnvironmentValues = UiEnvironmentValues.Default,
    ): VNode {
        return VNode(
            type = NodeType.Text,
            spec = TextNodeProps(
                text = text,
                maxLines = 1,
                overflow = com.viewcompose.ui.node.TextOverflow.Clip,
                textAlign = com.viewcompose.ui.node.TextAlign.Start,
                textColor = 0xFF000000.toInt(),
                textSizeSp = 14.sp,
            ),
            modifier = modifier,
            children = children,
            environment = environment,
        )
    }

    private fun buttonNode(
        text: String = "Continue",
        textColor: Int = 0xFF000000.toInt(),
    ): VNode {
        return VNode(
            type = NodeType.Button,
            spec = ButtonNodeProps(
                text = text,
                enabled = true,
                onClick = null,
                textColor = textColor,
                textSizeSp = 14.sp,
                backgroundColor = 0xFF0000FF.toInt(),
                borderWidth = 0.dp,
                borderColor = 0,
                shape = UiShape.rounded(8.dp),
                rippleColor = 0x33000000,
                minHeight = 48.dp,
                paddingHorizontal = 16.dp,
                paddingVertical = 8.dp,
                leadingIcon = null,
                trailingIcon = null,
                iconTint = textColor,
                iconSize = 18.dp,
                iconSpacing = 8.dp,
            ),
            modifier = Modifier,
        )
    }

    private fun textFieldNode(
        value: String = "hello",
        textColor: Int = 0xFF000000.toInt(),
    ): VNode {
        return VNode(
            type = NodeType.TextField,
            spec = TextFieldNodeProps(
                state = TextFieldState(TextFieldValue(value)),
                value = TextFieldValue(value),
                placeholder = "Hint",
                enabled = true,
                singleLine = true,
                minLines = 1,
                maxLines = 1,
                keyboardOptions = com.viewcompose.ui.node.TextFieldKeyboardOptions(
                    keyboardType = com.viewcompose.ui.node.TextFieldType.Text,
                    imeAction = com.viewcompose.ui.node.TextFieldImeAction.Done,
                ),
                inputTransformation = null,
                onKeyboardAction = null,
                onFocusChange = null,
                autofillHints = emptySet(),
                hintColor = 0xFF888888.toInt(),
                readOnly = false,
                textColor = textColor,
                textSizeSp = 16.sp,
                backgroundColor = 0xFFEEEEEE.toInt(),
                borderWidth = 0.dp,
                borderColor = 0,
                shape = UiShape.rounded(8.dp),
                minHeight = 56.dp,
                paddingHorizontal = 16.dp,
                paddingVertical = 12.dp,
            ),
            modifier = Modifier,
        )
    }

    private fun segmentedControlNode(
        selectedIndex: Int = 0,
    ): VNode {
        return VNode(
            type = NodeType.SegmentedControl,
            spec = SegmentedControlNodeProps(
                items = emptyList(),
                selectedIndex = selectedIndex,
                onSelectionChange = null,
                enabled = true,
                backgroundColor = 1,
                indicatorColor = 2,
                shape = UiShape.cut(3.dp),
                textColor = 4,
                selectedTextColor = 5,
                rippleColor = 6,
                textSizeSp = 14.sp,
                paddingHorizontal = 8.dp,
                paddingVertical = 6.dp,
            ),
            modifier = Modifier,
        )
    }

    private fun lazyColumnNode(
        spacing: UiDp = 8.dp,
    ): VNode {
        return VNode(
            type = NodeType.LazyColumn,
            spec = LazyColumnNodeProps(
                contentPadding = com.viewcompose.ui.node.policy.LazyContentPadding.all(12.dp),
                spacing = spacing,
                items = emptyList(),
            ),
            modifier = Modifier,
        )
    }

    private fun lazyVerticalGridNode(
        spanCount: Int = 2,
    ): VNode {
        return VNode(
            type = NodeType.LazyVerticalGrid,
            spec = LazyVerticalGridNodeProps(
                spanCount = spanCount,
                contentPadding = com.viewcompose.ui.node.policy.LazyContentPadding.all(8.dp),
                horizontalSpacing = 8.dp,
                verticalSpacing = 8.dp,
                items = listOf(
                    lazyItem("grid-1"),
                    lazyItem("grid-2"),
                ),
                state = null,
            ),
            modifier = Modifier,
        )
    }

    private fun horizontalPagerNode(
        currentPage: Int = 0,
    ): VNode {
        return VNode(
            type = NodeType.HorizontalPager,
            spec = HorizontalPagerNodeProps(
                pages = listOf(
                    lazyItem("page-1"),
                    lazyItem("page-2"),
                ),
                currentPage = currentPage,
                onPageChanged = null,
                offscreenPageLimit = 1,
                pagerState = null,
                userScrollEnabled = true,
            ),
            modifier = Modifier,
        )
    }

    private fun horizontalPagerNodeWithSessionNonce(
        currentPage: Int = 0,
        sessionNonce: Any,
    ): VNode {
        return VNode(
            type = NodeType.HorizontalPager,
            spec = HorizontalPagerNodeProps(
                pages = listOf(
                    lazyItemWithSessionNonce("page-1", sessionNonce),
                    lazyItemWithSessionNonce("page-2", sessionNonce),
                ),
                currentPage = currentPage,
                onPageChanged = null,
                offscreenPageLimit = 1,
                pagerState = null,
                userScrollEnabled = true,
            ),
            modifier = Modifier,
        )
    }

    private fun verticalPagerNode(
        currentPage: Int = 0,
    ): VNode {
        return VNode(
            type = NodeType.VerticalPager,
            spec = VerticalPagerNodeProps(
                pages = listOf(
                    lazyItem("v-page-1"),
                    lazyItem("v-page-2"),
                ),
                currentPage = currentPage,
                onPageChanged = null,
                offscreenPageLimit = 1,
                pagerState = null,
                userScrollEnabled = true,
            ),
            modifier = Modifier,
        )
    }

    private fun verticalPagerNodeWithSessionNonce(
        currentPage: Int = 0,
        sessionNonce: Any,
    ): VNode {
        return VNode(
            type = NodeType.VerticalPager,
            spec = VerticalPagerNodeProps(
                pages = listOf(
                    lazyItemWithSessionNonce("v-page-1", sessionNonce),
                    lazyItemWithSessionNonce("v-page-2", sessionNonce),
                ),
                currentPage = currentPage,
                onPageChanged = null,
                offscreenPageLimit = 1,
                pagerState = null,
                userScrollEnabled = true,
            ),
            modifier = Modifier,
        )
    }

    private fun lazyItem(
        key: String,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentToken = key,
            sessionFactory = LazyListItemSessionFactory {
                object : LazyListItemSession {
                    override fun render() = Unit

                    override fun dispose() = Unit
                }
            },
        )
    }

    private fun lazyItemWithSessionNonce(
        key: String,
        sessionNonce: Any,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentToken = key,
            sessionFactory = LazyListItemSessionFactory {
                sessionNonce.hashCode()
                object : LazyListItemSession {
                    override fun render() = Unit

                    override fun dispose() = Unit
                }
            },
            sessionUpdater = {
                sessionNonce.hashCode()
            },
        )
    }

    private fun toggleNode(
        checked: Boolean = false,
    ): VNode {
        return VNode(
            type = NodeType.Checkbox,
            spec = ToggleNodeProps(
                text = "Toggle",
                enabled = true,
                checked = checked,
                controlColor = 0xFF000000.toInt(),
                onCheckedChange = null,
                textColor = 0xFF000000.toInt(),
                textSizeSp = 14.sp,
                rippleColor = 0x33000000,
            ),
            modifier = Modifier,
        )
    }

    private fun sliderNode(
        value: Int = 50,
    ): VNode {
        return VNode(
            type = NodeType.Slider,
            spec = SliderNodeProps(
                min = 0,
                max = 100,
                value = value,
                enabled = true,
                thumbColor = 0xFF000000.toInt(),
                trackColor = 0xFF000000.toInt(),
                onValueChange = null,
            ),
            modifier = Modifier,
        )
    }

    private fun progressNode(
        progress: Float? = 0.5f,
    ): VNode {
        return VNode(
            type = NodeType.LinearProgressIndicator,
            spec = ProgressIndicatorNodeProps(
                enabled = true,
                progress = progress,
                indicatorColor = 0xFF000000.toInt(),
                trackColor = 0x33000000,
                trackThickness = 4.dp,
                indicatorSize = 32.dp,
            ),
            modifier = Modifier,
        )
    }

    private fun dividerNode(
        color: Int = 0xFFCCCCCC.toInt(),
    ): VNode {
        return VNode(
            type = NodeType.Divider,
            spec = DividerNodeProps(
                color = color,
                thickness = 1.dp,
            ),
            modifier = Modifier,
        )
    }

    private fun canvasNode(
        token: String,
    ): VNode {
        return VNode(
            type = NodeType.Canvas,
            spec = CanvasNodeProps(
                onDraw = { _ ->
                    if (token.isEmpty()) {
                        Unit
                    }
                },
            ),
            modifier = Modifier,
        )
    }

    private fun rowNode(
        spacing: UiDp = 8.dp,
    ): VNode {
        return VNode(
            type = NodeType.Row,
            spec = RowNodeProps(
                spacing = spacing,
                arrangement = com.viewcompose.ui.layout.MainAxisArrangement.Start,
                verticalAlignment = com.viewcompose.ui.layout.VerticalAlignment.Top,
            ),
            modifier = Modifier,
        )
    }

    private fun columnNode(
        spacing: UiDp = 8.dp,
    ): VNode {
        return VNode(
            type = NodeType.Column,
            spec = ColumnNodeProps(
                spacing = spacing,
                arrangement = com.viewcompose.ui.layout.MainAxisArrangement.Start,
                horizontalAlignment = com.viewcompose.ui.layout.HorizontalAlignment.Start,
            ),
            modifier = Modifier,
        )
    }

    private fun boxNode(
        contentAlignment: com.viewcompose.ui.layout.BoxAlignment = com.viewcompose.ui.layout.BoxAlignment.TopStart,
        rippleColor: Int? = null,
    ): VNode {
        return VNode(
            type = NodeType.Box,
            spec = BoxNodeProps(
                contentAlignment = contentAlignment,
                rippleColor = rippleColor,
            ),
            modifier = Modifier,
        )
    }

    private fun constraintLayoutNode(
        guidelineId: String = "guide",
    ): VNode {
        return VNode(
            type = NodeType.ConstraintLayout,
            spec = ConstraintLayoutNodeProps(
                constraintSet = null,
                helpers = ConstraintHelpersSpec(
                    guidelines = listOf(
                        ConstraintGuidelineSpec(
                            id = guidelineId,
                            direction = ConstraintGuidelineDirection.FromTop,
                            position = ConstraintGuidelinePosition.Fraction(0.2f),
                        ),
                    ),
                ),
            ),
            modifier = Modifier,
        )
    }

    private fun imageNode(
        tint: Int? = null,
        imageLoader: UiImageLoader? = null,
    ): VNode {
        return VNode(
            type = NodeType.Image,
            spec = ImageNodeProps(
                contentDescription = null,
                contentScale = com.viewcompose.ui.node.ImageContentScale.Fit,
                tint = tint,
                source = null,
                placeholder = null,
                error = null,
                fallback = null,
                imageLoader = imageLoader,
            ),
            modifier = Modifier,
        )
    }

    private class EqualLoader(
        private val label: String,
    ) : UiImageLoader {
        override fun load(
            target: com.viewcompose.ui.node.UiImageTarget,
            request: com.viewcompose.ui.node.UiImageRequest,
        ): UiImageLoadHandle = UiImageLoadHandle {}

        override fun equals(other: Any?): Boolean = other is EqualLoader

        override fun hashCode(): Int = 0

        override fun toString(): String = "EqualLoader($label)"
    }

    private fun iconButtonNode(
        enabled: Boolean = true,
    ): VNode {
        return VNode(
            type = NodeType.IconButton,
            spec = IconButtonNodeProps(
                contentDescription = null,
                contentScale = com.viewcompose.ui.node.ImageContentScale.Fit,
                tint = null,
                source = null,
                placeholder = null,
                error = null,
                fallback = null,
                imageLoader = null,
                enabled = enabled,
                backgroundColor = 0xFF0000FF.toInt(),
                borderWidth = 0.dp,
                borderColor = 0,
                shape = UiShape.rounded(8.dp),
                rippleColor = 0x33000000,
                contentPadding = 8.dp,
            ),
            modifier = Modifier,
        )
    }

    private fun flowRowNode(
        horizontalSpacing: UiDp = 8.dp,
    ): VNode {
        return VNode(
            type = NodeType.FlowRow,
            spec = FlowRowNodeProps(
                horizontalSpacing = horizontalSpacing,
                verticalSpacing = 4.dp,
                maxItemsInEachRow = Int.MAX_VALUE,
            ),
            modifier = Modifier,
        )
    }

    private fun flowColumnNode(
        verticalSpacing: UiDp = 8.dp,
    ): VNode {
        return VNode(
            type = NodeType.FlowColumn,
            spec = FlowColumnNodeProps(
                horizontalSpacing = 4.dp,
                verticalSpacing = verticalSpacing,
                maxItemsInEachColumn = Int.MAX_VALUE,
            ),
            modifier = Modifier,
        )
    }

    private fun navigationBarNode(
        selectedIndex: Int = 0,
    ): VNode {
        return VNode(
            type = NodeType.NavigationBar,
            spec = NavigationBarNodeProps(
                items = listOf(
                    NavigationBarItem(
                        label = "Home",
                        icon = ImageSource.Resource(1),
                    ),
                    NavigationBarItem(
                        label = "Search",
                        icon = ImageSource.Resource(2),
                        badgeCount = 1,
                    ),
                ),
                selectedIndex = selectedIndex,
                onItemSelected = null,
                containerColor = 0xFFFFFFFF.toInt(),
                selectedIconColor = 0xFF000000.toInt(),
                unselectedIconColor = 0xFF666666.toInt(),
                selectedLabelColor = 0xFF000000.toInt(),
                unselectedLabelColor = 0xFF666666.toInt(),
                indicatorColor = 0x22000000,
                rippleColor = 0x11000000,
                iconSize = 24.dp,
                labelSizeSp = 12.sp,
                badgeColor = 0xFFFF0000.toInt(),
                badgeTextColor = 0xFFFFFFFF.toInt(),
            ),
            modifier = Modifier,
        )
    }

    private fun tabRowNode(
        selectedIndex: Int = 0,
    ): VNode {
        return VNode(
            type = NodeType.TabRow,
            spec = TabRowNodeProps(
                tabs = listOf(
                    TabRowTab(lazyItem("tab-1")),
                    TabRowTab(lazyItem("tab-2")),
                ),
                selectedIndex = selectedIndex,
                onTabSelected = null,
                pagerState = null,
                indicatorColor = 0xFF000000.toInt(),
                indicatorHeight = 4.dp,
                indicatorCornerRadius = 2.dp,
                indicatorPosition = TabIndicatorPosition.Bottom,
                indicatorWidthMode = TabIndicatorWidthMode.MatchItem,
                indicatorFixedWidth = 0.dp,
                containerColor = 0xFFFFFFFF.toInt(),
                scrollable = true,
                equalWidth = false,
                rippleColor = 0x11000000,
                itemSpacing = 8.dp,
                itemPaddingHorizontal = 12.dp,
                itemPaddingVertical = 8.dp,
                minItemWidth = 64.dp,
            ),
            modifier = Modifier,
        )
    }

    private fun tabRowNodeWithSessionNonce(
        selectedIndex: Int = 0,
        sessionNonce: Any,
    ): VNode {
        return VNode(
            type = NodeType.TabRow,
            spec = TabRowNodeProps(
                tabs = listOf(
                    TabRowTab(lazyItemWithSessionNonce("tab-1", sessionNonce)),
                    TabRowTab(lazyItemWithSessionNonce("tab-2", sessionNonce)),
                ),
                selectedIndex = selectedIndex,
                onTabSelected = null,
                pagerState = null,
                indicatorColor = 0xFF000000.toInt(),
                indicatorHeight = 4.dp,
                indicatorCornerRadius = 2.dp,
                indicatorPosition = TabIndicatorPosition.Bottom,
                indicatorWidthMode = TabIndicatorWidthMode.MatchItem,
                indicatorFixedWidth = 0.dp,
                containerColor = 0xFFFFFFFF.toInt(),
                scrollable = true,
                equalWidth = false,
                rippleColor = 0x11000000,
                itemSpacing = 8.dp,
                itemPaddingHorizontal = 12.dp,
                itemPaddingVertical = 8.dp,
                minItemWidth = 64.dp,
            ),
            modifier = Modifier,
        )
    }
}
