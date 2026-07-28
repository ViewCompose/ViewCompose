package com.viewcompose.renderer.view.tree

import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.HorizontalPagerNodeProps
import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.LazyRowNodeProps
import com.viewcompose.ui.node.spec.LazyVerticalGridNodeProps
import com.viewcompose.ui.node.spec.NavigationBarNodeProps
import com.viewcompose.ui.node.spec.SegmentedControlNodeProps
import com.viewcompose.ui.node.spec.TabRowNodeProps
import com.viewcompose.ui.node.spec.VerticalPagerNodeProps
import com.viewcompose.renderer.view.container.DeclarativeHorizontalPagerLayout
import com.viewcompose.renderer.view.container.DeclarativeLazyVerticalGridLayout
import com.viewcompose.renderer.view.container.DeclarativeNavigationBarLayout
import com.viewcompose.renderer.view.container.DeclarativeSegmentedControlLayout
import com.viewcompose.renderer.view.container.DeclarativeTabRowLayout
import com.viewcompose.renderer.view.container.DeclarativeVerticalPagerLayout
import com.viewcompose.renderer.view.tree.patch.ContainerNodePatchApplier

/**
 * 注册 lazy、pager、tab 和 navigation 类集合节点的 binder/patch 描述。
 * Registers binder/patch descriptors for lazy, pager, tab, and navigation collection nodes.
 */
internal fun MutableList<NodeBinderDescriptor>.addCollectionNodeBinderDescriptors() {
    val lazyColumnPatch = patchDescriptor<LazyColumnNodeProps, LazyColumnNodePatch>(
        factory = { previous, next -> LazyColumnNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyLazyColumnPatch(
                view = view as RecyclerView,
                patch = patch,
            )
        },
    )
    val lazyRowPatch = patchDescriptor<LazyRowNodeProps, LazyRowNodePatch>(
        factory = { previous, next -> LazyRowNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyLazyRowPatch(
                view = view as RecyclerView,
                patch = patch,
            )
        },
    )
    val segmentedControlPatch = patchDescriptor<SegmentedControlNodeProps, SegmentedControlNodePatch>(
        factory = { previous, next -> SegmentedControlNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applySegmentedControlPatch(
                view = view as DeclarativeSegmentedControlLayout,
                patch = patch,
            )
        },
    )
    val navigationBarPatch = patchDescriptor<NavigationBarNodeProps, NavigationBarNodePatch>(
        factory = { previous, next -> NavigationBarNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyNavigationBarPatch(
                view = view as DeclarativeNavigationBarLayout,
                patch = patch,
            )
        },
    )
    val horizontalPagerPatch = patchDescriptor<HorizontalPagerNodeProps, HorizontalPagerNodePatch>(
        factory = { previous, next -> HorizontalPagerNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyHorizontalPagerPatch(
                view = view as DeclarativeHorizontalPagerLayout,
                patch = patch,
            )
        },
    )
    val tabRowPatch = patchDescriptor<TabRowNodeProps, TabRowNodePatch>(
        factory = { previous, next -> TabRowNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyTabRowPatch(
                view = view as DeclarativeTabRowLayout,
                patch = patch,
            )
        },
    )
    val verticalPagerPatch = patchDescriptor<VerticalPagerNodeProps, VerticalPagerNodePatch>(
        factory = { previous, next -> VerticalPagerNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyVerticalPagerPatch(
                view = view as DeclarativeVerticalPagerLayout,
                patch = patch,
            )
        },
    )
    val lazyVerticalGridPatch = patchDescriptor<LazyVerticalGridNodeProps, LazyVerticalGridNodePatch>(
        factory = { previous, next -> LazyVerticalGridNodePatch(previous, next) },
        apply = { view, patch ->
            ContainerNodePatchApplier.applyLazyVerticalGridPatch(
                view = view as DeclarativeLazyVerticalGridLayout,
                patch = patch,
            )
        },
    )

    add(
        descriptor(
            nodeType = NodeType.LazyColumn,
            bind = { view, node ->
                CollectionViewBinder.bindLazyColumn(
                    view = view as RecyclerView,
                    spec = CollectionViewBinder.readLazyColumnSpec(node),
                )
            },
            patch = lazyColumnPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.LazyRow,
            bind = { view, node ->
                CollectionViewBinder.bindLazyRow(
                    view = view as RecyclerView,
                    spec = CollectionViewBinder.readLazyRowSpec(node),
                )
            },
            patch = lazyRowPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.SegmentedControl,
            bind = { view, node ->
                PagerViewBinder.bindSegmentedControl(
                    view = view as DeclarativeSegmentedControlLayout,
                    spec = PagerViewBinder.readSegmentedControlSpec(node),
                )
            },
            patch = segmentedControlPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.NavigationBar,
            bind = { view, node ->
                CollectionViewBinder.bindNavigationBar(
                    view = view as DeclarativeNavigationBarLayout,
                    spec = CollectionViewBinder.readNavigationBarSpec(node),
                )
            },
            patch = navigationBarPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.HorizontalPager,
            bind = { view, node ->
                PagerViewBinder.bindHorizontalPager(
                    view = view as DeclarativeHorizontalPagerLayout,
                    spec = PagerViewBinder.readHorizontalPagerSpec(node),
                )
            },
            patch = horizontalPagerPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.TabRow,
            bind = { view, node ->
                PagerViewBinder.bindTabRow(
                    view = view as DeclarativeTabRowLayout,
                    spec = PagerViewBinder.readTabRowSpec(node),
                )
            },
            patch = tabRowPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.VerticalPager,
            bind = { view, node ->
                PagerViewBinder.bindVerticalPager(
                    view = view as DeclarativeVerticalPagerLayout,
                    spec = PagerViewBinder.readVerticalPagerSpec(node),
                )
            },
            patch = verticalPagerPatch,
        ),
    )
    add(
        descriptor(
            nodeType = NodeType.LazyVerticalGrid,
            bind = { view, node ->
                CollectionViewBinder.bindLazyVerticalGrid(
                    view = view as DeclarativeLazyVerticalGridLayout,
                    spec = CollectionViewBinder.readLazyVerticalGridSpec(node),
                )
            },
            patch = lazyVerticalGridPatch,
        ),
    )
}
