package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.unit.sp

/*
 * 契约测试职责：锁定 renderer view/tree 中的 Node Binder Descriptor Guard 边界，防止节点协议或依赖关系在重构中漂移。
 * Contract test responsibility: locks down the Node Binder Descriptor Guard boundary in renderer view/tree and prevents node protocol or dependency drift.
 */

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.BoxNodeProps
import com.viewcompose.ui.node.spec.ButtonNodeProps
import com.viewcompose.ui.node.spec.ColumnNodeProps
import com.viewcompose.ui.node.spec.ConstraintLayoutNodeProps
import com.viewcompose.ui.node.spec.DividerNodeProps
import com.viewcompose.ui.node.spec.AnimatedSizeHostNodeProps
import com.viewcompose.ui.node.spec.AnimatedVisibilityHostNodeProps
import com.viewcompose.ui.node.spec.CanvasNodeProps
import com.viewcompose.ui.node.spec.FlowColumnNodeProps
import com.viewcompose.ui.node.spec.FlowRowNodeProps
import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.IconButtonNodeProps
import com.viewcompose.ui.node.spec.ImageNodeProps
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.LazyRowNodeProps
import com.viewcompose.ui.node.spec.LazyVerticalGridNodeProps
import com.viewcompose.ui.node.spec.NavigationBarNodeProps
import com.viewcompose.ui.node.spec.ProgressIndicatorNodeProps
import com.viewcompose.ui.node.spec.PullToRefreshNodeProps
import com.viewcompose.ui.node.spec.RowNodeProps
import com.viewcompose.ui.node.spec.ScrollableColumnNodeProps
import com.viewcompose.ui.node.spec.ScrollableRowNodeProps
import com.viewcompose.ui.node.spec.SegmentedControlNodeProps
import com.viewcompose.ui.node.spec.SliderNodeProps
import com.viewcompose.ui.node.spec.TabRowNodeProps
import com.viewcompose.ui.node.spec.TextFieldNodeProps
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.node.spec.ToggleNodeProps
import com.viewcompose.ui.node.spec.VerticalPagerNodeProps
import kotlin.reflect.KClass
import org.junit.Assert.assertEquals
import org.junit.Test

class NodeBinderDescriptorGuardTest {
    @Test
    fun `descriptors cover every node type exactly once`() {
        val descriptors = NodeViewBinderRegistry.descriptorsForTest()
        val actual = descriptors.map { it.nodeType }.toSet()
        val expected = setOf(
            NodeType.Text,
            NodeType.TextField,
            NodeType.Checkbox,
            NodeType.Switch,
            NodeType.RadioButton,
            NodeType.Slider,
            NodeType.LinearProgressIndicator,
            NodeType.CircularProgressIndicator,
            NodeType.Button,
            NodeType.IconButton,
            NodeType.Row,
            NodeType.Column,
            NodeType.Box,
            NodeType.Surface,
            NodeType.ConstraintLayout,
            NodeType.AnimatedVisibilityHost,
            NodeType.AnimatedSizeHost,
            NodeType.NestedScrollHost,
            NodeType.Spacer,
            NodeType.Divider,
            NodeType.Canvas,
            NodeType.Image,
            NodeType.AndroidView,
            NodeType.LazyColumn,
            NodeType.LazyRow,
            NodeType.SegmentedControl,
            NodeType.ScrollableColumn,
            NodeType.ScrollableRow,
            NodeType.FlowRow,
            NodeType.FlowColumn,
            NodeType.NavigationBar,
            NodeType.HorizontalPager,
            NodeType.VerticalPager,
            NodeType.TabRow,
            NodeType.LazyVerticalGrid,
            NodeType.PullToRefresh,
        )
        assertEquals(expected, actual)
        assertEquals(expected.size, descriptors.size)
    }

    @Test
    fun `patch descriptors cover every patch subtype`() {
        val descriptorPatches = NodeViewBinderRegistry.descriptorsForTest()
            .mapNotNull { it.patch }
            .map { it.patchClass }
            .toSet()
        assertEquals(expectedPatchTypes(), descriptorPatches)
    }

    @Test
    fun `descriptor derived mappings are complete and consistent`() {
        val patchDescriptors = NodeViewBinderRegistry.descriptorsForTest()
            .mapNotNull { it.patch }
        val patchTypes = patchDescriptors.map { it.patchClass }.toSet()
        val specTypes = patchDescriptors.map { it.specClass }.toSet()
        val patchAppliers = NodeViewBinderRegistry.patchAppliersForTest()
        val patchFactories = NodeViewBinderRegistry.patchFactoriesForTest()

        assertEquals(patchTypes, patchAppliers.keys)
        assertEquals(specTypes, patchFactories.keys)
        assertEquals(patchTypes.size, patchAppliers.size)
        assertEquals(specTypes.size, patchFactories.size)
    }

    private fun expectedPatchTypes(): Set<KClass<out NodeViewPatch>> = setOf(
        ButtonNodePatch::class,
        TextNodePatch::class,
        TextFieldNodePatch::class,
        SegmentedControlNodePatch::class,
        LazyColumnNodePatch::class,
        LazyRowNodePatch::class,
        ToggleNodePatch::class,
        SliderNodePatch::class,
        ProgressIndicatorNodePatch::class,
        RowNodePatch::class,
        ColumnNodePatch::class,
        BoxNodePatch::class,
        ConstraintLayoutNodePatch::class,
        AnimatedVisibilityHostNodePatch::class,
        AnimatedSizeHostNodePatch::class,
        ImageNodePatch::class,
        IconButtonNodePatch::class,
        DividerNodePatch::class,
        CanvasNodePatch::class,
        ScrollableColumnNodePatch::class,
        ScrollableRowNodePatch::class,
        FlowRowNodePatch::class,
        FlowColumnNodePatch::class,
        NavigationBarNodePatch::class,
        HorizontalPagerNodePatch::class,
        TabRowNodePatch::class,
        VerticalPagerNodePatch::class,
        LazyVerticalGridNodePatch::class,
        PullToRefreshNodePatch::class,
    )

    @Suppress("unused")
    private fun nodeTypeExhaustiveGuard(type: NodeType): String = when (type) {
        NodeType.Text -> "Text"
        NodeType.TextField -> "TextField"
        NodeType.Checkbox -> "Checkbox"
        NodeType.Switch -> "Switch"
        NodeType.RadioButton -> "RadioButton"
        NodeType.Slider -> "Slider"
        NodeType.LinearProgressIndicator -> "LinearProgressIndicator"
        NodeType.CircularProgressIndicator -> "CircularProgressIndicator"
        NodeType.Button -> "Button"
        NodeType.IconButton -> "IconButton"
        NodeType.Row -> "Row"
        NodeType.Column -> "Column"
        NodeType.Box -> "Box"
        NodeType.Surface -> "Surface"
        NodeType.ConstraintLayout -> "ConstraintLayout"
        NodeType.AnimatedVisibilityHost -> "AnimatedVisibilityHost"
        NodeType.AnimatedSizeHost -> "AnimatedSizeHost"
        NodeType.NestedScrollHost -> "NestedScrollHost"
        NodeType.Spacer -> "Spacer"
        NodeType.Divider -> "Divider"
        NodeType.Canvas -> "Canvas"
        NodeType.Image -> "Image"
        NodeType.AndroidView -> "AndroidView"
        NodeType.LazyColumn -> "LazyColumn"
        NodeType.LazyRow -> "LazyRow"
        NodeType.SegmentedControl -> "SegmentedControl"
        NodeType.ScrollableColumn -> "ScrollableColumn"
        NodeType.ScrollableRow -> "ScrollableRow"
        NodeType.FlowRow -> "FlowRow"
        NodeType.FlowColumn -> "FlowColumn"
        NodeType.NavigationBar -> "NavigationBar"
        NodeType.HorizontalPager -> "HorizontalPager"
        NodeType.VerticalPager -> "VerticalPager"
        NodeType.TabRow -> "TabRow"
        NodeType.LazyVerticalGrid -> "LazyVerticalGrid"
        NodeType.PullToRefresh -> "PullToRefresh"
    }

    @Suppress("unused")
    private fun nodeViewPatchExhaustiveGuard(patch: NodeViewPatch): String = when (patch) {
        is ButtonNodePatch -> "ButtonNodePatch"
        is TextNodePatch -> "TextNodePatch"
        is TextFieldNodePatch -> "TextFieldNodePatch"
        is SegmentedControlNodePatch -> "SegmentedControlNodePatch"
        is LazyColumnNodePatch -> "LazyColumnNodePatch"
        is LazyRowNodePatch -> "LazyRowNodePatch"
        is ToggleNodePatch -> "ToggleNodePatch"
        is SliderNodePatch -> "SliderNodePatch"
        is ProgressIndicatorNodePatch -> "ProgressIndicatorNodePatch"
        is RowNodePatch -> "RowNodePatch"
        is ColumnNodePatch -> "ColumnNodePatch"
        is BoxNodePatch -> "BoxNodePatch"
        is ConstraintLayoutNodePatch -> "ConstraintLayoutNodePatch"
        is AnimatedVisibilityHostNodePatch -> "AnimatedVisibilityHostNodePatch"
        is AnimatedSizeHostNodePatch -> "AnimatedSizeHostNodePatch"
        is ImageNodePatch -> "ImageNodePatch"
        is IconButtonNodePatch -> "IconButtonNodePatch"
        is DividerNodePatch -> "DividerNodePatch"
        is CanvasNodePatch -> "CanvasNodePatch"
        is ScrollableColumnNodePatch -> "ScrollableColumnNodePatch"
        is ScrollableRowNodePatch -> "ScrollableRowNodePatch"
        is FlowRowNodePatch -> "FlowRowNodePatch"
        is FlowColumnNodePatch -> "FlowColumnNodePatch"
        is NavigationBarNodePatch -> "NavigationBarNodePatch"
        is HorizontalPagerNodePatch -> "HorizontalPagerNodePatch"
        is TabRowNodePatch -> "TabRowNodePatch"
        is VerticalPagerNodePatch -> "VerticalPagerNodePatch"
        is LazyVerticalGridNodePatch -> "LazyVerticalGridNodePatch"
        is PullToRefreshNodePatch -> "PullToRefreshNodePatch"
    }

    @Suppress("unused")
    private fun nodeSpecPatchFactoryExhaustiveGuard(spec: Any): String = when (spec) {
        is ButtonNodeProps -> "ButtonNodeProps"
        is TextNodeProps -> "TextNodeProps"
        is TextFieldNodeProps -> "TextFieldNodeProps"
        is SegmentedControlNodeProps -> "SegmentedControlNodeProps"
        is LazyColumnNodeProps -> "LazyColumnNodeProps"
        is LazyRowNodeProps -> "LazyRowNodeProps"
        is ToggleNodeProps -> "ToggleNodeProps"
        is SliderNodeProps -> "SliderNodeProps"
        is ProgressIndicatorNodeProps -> "ProgressIndicatorNodeProps"
        is RowNodeProps -> "RowNodeProps"
        is ColumnNodeProps -> "ColumnNodeProps"
        is BoxNodeProps -> "BoxNodeProps"
        is ConstraintLayoutNodeProps -> "ConstraintLayoutNodeProps"
        is AnimatedVisibilityHostNodeProps -> "AnimatedVisibilityHostNodeProps"
        is AnimatedSizeHostNodeProps -> "AnimatedSizeHostNodeProps"
        is ImageNodeProps -> "ImageNodeProps"
        is IconButtonNodeProps -> "IconButtonNodeProps"
        is DividerNodeProps -> "DividerNodeProps"
        is CanvasNodeProps -> "CanvasNodeProps"
        is ScrollableColumnNodeProps -> "ScrollableColumnNodeProps"
        is ScrollableRowNodeProps -> "ScrollableRowNodeProps"
        is FlowRowNodeProps -> "FlowRowNodeProps"
        is FlowColumnNodeProps -> "FlowColumnNodeProps"
        is NavigationBarNodeProps -> "NavigationBarNodeProps"
        is HorizontalPagerNodeProps -> "HorizontalPagerNodeProps"
        is TabRowNodeProps -> "TabRowNodeProps"
        is VerticalPagerNodeProps -> "VerticalPagerNodeProps"
        is LazyVerticalGridNodeProps -> "LazyVerticalGridNodeProps"
        is PullToRefreshNodeProps -> "PullToRefreshNodeProps"
        else -> "Unknown"
    }
}
