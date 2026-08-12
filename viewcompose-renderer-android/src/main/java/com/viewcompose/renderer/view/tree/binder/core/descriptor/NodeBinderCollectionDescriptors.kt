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
 * Registers binder and patch descriptors for lazy, pager, tab, and navigation collection nodes.
 * Registers binder/patch descriptors for lazy, pager, tab, and navigation collection nodes.
 */
internal fun MutableList<NodeBinderDescriptor>.addCollectionNodeBinderDescriptors() {
    val lazyColumnPatch = retainedPatchDescriptor<LazyColumnNodeProps, LazyColumnNodePatch>(
        factory = { previous, next -> LazyColumnNodePatch(previous, next) },
        apply = { view, patch, submission ->
            ContainerNodePatchApplier.applyLazyColumnPatch(
                view = view as RecyclerView,
                patch = patch,
                submission = submission,
            )
        },
    )
    val lazyRowPatch = retainedPatchDescriptor<LazyRowNodeProps, LazyRowNodePatch>(
        factory = { previous, next -> LazyRowNodePatch(previous, next) },
        apply = { view, patch, submission ->
            ContainerNodePatchApplier.applyLazyRowPatch(
                view = view as RecyclerView,
                patch = patch,
                submission = submission,
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
    val horizontalPagerPatch = retainedPatchDescriptor<HorizontalPagerNodeProps, HorizontalPagerNodePatch>(
        factory = { previous, next -> HorizontalPagerNodePatch(previous, next) },
        apply = { view, patch, submission ->
            ContainerNodePatchApplier.applyHorizontalPagerPatch(
                view = view as DeclarativeHorizontalPagerLayout,
                patch = patch,
                submission = submission,
            )
        },
    )
    val tabRowPatch = retainedPatchDescriptor<TabRowNodeProps, TabRowNodePatch>(
        factory = { previous, next -> TabRowNodePatch(previous, next) },
        apply = { view, patch, submission ->
            ContainerNodePatchApplier.applyTabRowPatch(
                view = view as DeclarativeTabRowLayout,
                patch = patch,
                submission = submission,
            )
        },
    )
    val verticalPagerPatch = retainedPatchDescriptor<VerticalPagerNodeProps, VerticalPagerNodePatch>(
        factory = { previous, next -> VerticalPagerNodePatch(previous, next) },
        apply = { view, patch, submission ->
            ContainerNodePatchApplier.applyVerticalPagerPatch(
                view = view as DeclarativeVerticalPagerLayout,
                patch = patch,
                submission = submission,
            )
        },
    )
    val lazyVerticalGridPatch = retainedPatchDescriptor<LazyVerticalGridNodeProps, LazyVerticalGridNodePatch>(
        factory = { previous, next -> LazyVerticalGridNodePatch(previous, next) },
        apply = { view, patch, submission ->
            ContainerNodePatchApplier.applyLazyVerticalGridPatch(
                view = view as DeclarativeLazyVerticalGridLayout,
                patch = patch,
                submission = submission,
            )
        },
    )

    add(
        retainedDescriptor(
            nodeType = NodeType.LazyColumn,
            bind = { view, node, submission ->
                CollectionViewBinder.bindLazyColumn(
                    view = view as RecyclerView,
                    spec = CollectionViewBinder.readLazyColumnSpec(node),
                    submission = submission,
                )
            },
            patch = lazyColumnPatch,
        ),
    )
    add(
        retainedDescriptor(
            nodeType = NodeType.LazyRow,
            bind = { view, node, submission ->
                CollectionViewBinder.bindLazyRow(
                    view = view as RecyclerView,
                    spec = CollectionViewBinder.readLazyRowSpec(node),
                    submission = submission,
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
        retainedDescriptor(
            nodeType = NodeType.HorizontalPager,
            bind = { view, node, submission ->
                PagerViewBinder.bindHorizontalPager(
                    view = view as DeclarativeHorizontalPagerLayout,
                    spec = PagerViewBinder.readHorizontalPagerSpec(node),
                    submission = submission,
                )
            },
            patch = horizontalPagerPatch,
        ),
    )
    add(
        retainedDescriptor(
            nodeType = NodeType.TabRow,
            bind = { view, node, submission ->
                PagerViewBinder.bindTabRow(
                    view = view as DeclarativeTabRowLayout,
                    spec = PagerViewBinder.readTabRowSpec(node),
                    submission = submission,
                )
            },
            patch = tabRowPatch,
        ),
    )
    add(
        retainedDescriptor(
            nodeType = NodeType.VerticalPager,
            bind = { view, node, submission ->
                PagerViewBinder.bindVerticalPager(
                    view = view as DeclarativeVerticalPagerLayout,
                    spec = PagerViewBinder.readVerticalPagerSpec(node),
                    submission = submission,
                )
            },
            patch = verticalPagerPatch,
        ),
    )
    add(
        retainedDescriptor(
            nodeType = NodeType.LazyVerticalGrid,
            bind = { view, node, submission ->
                CollectionViewBinder.bindLazyVerticalGrid(
                    view = view as DeclarativeLazyVerticalGridLayout,
                    spec = CollectionViewBinder.readLazyVerticalGridSpec(node),
                    submission = submission,
                )
            },
            patch = lazyVerticalGridPatch,
        ),
    )
}
