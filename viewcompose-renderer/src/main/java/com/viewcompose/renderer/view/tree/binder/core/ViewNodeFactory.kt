package com.viewcompose.renderer.view.tree

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.renderer.view.container.DeclarativeBoxLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedVisibilityHostLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedSizeHostLayout
import com.viewcompose.renderer.view.container.DeclarativeNestedScrollHostLayout
import com.viewcompose.renderer.view.container.DeclarativeCanvasLayout
import com.viewcompose.renderer.view.container.DeclarativeConstraintLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowColumnLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowRowLayout
import com.viewcompose.renderer.view.container.DeclarativeHorizontalPagerLayout
import com.viewcompose.renderer.view.container.DeclarativeLazyVerticalGridLayout
import com.viewcompose.renderer.view.container.DeclarativeLazyListView
import com.viewcompose.renderer.view.container.DeclarativeLinearLayout
import com.viewcompose.renderer.view.container.DeclarativeNavigationBarLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.viewcompose.renderer.view.container.DeclarativeScrollableColumnLayout
import com.viewcompose.renderer.view.container.DeclarativeScrollableRowLayout
import com.viewcompose.renderer.view.container.DeclarativeSegmentedControlLayout
import com.viewcompose.renderer.view.container.DeclarativeTabRowLayout
import com.viewcompose.renderer.view.container.DeclarativeVerticalPagerLayout
import com.viewcompose.renderer.view.lazy.focus.LazyLinearLayoutManager
import com.viewcompose.renderer.view.lazy.adapter.LazyListAdapter
import com.viewcompose.renderer.view.lazy.reuse.FrameworkRecyclerViewDefaults

/**
 * 根据 NodeType 创建对应的 Android View 实例。
 * Creates the Android View instance for each NodeType.
 *
 * 这里只负责实例化和基础平台默认值；具体属性绑定由 NodeViewBinderRegistry 完成。
 * This only handles instantiation and basic platform defaults; concrete property binding is handled by NodeViewBinderRegistry.
 */
internal object ViewNodeFactory {
    /**
     * 创建一个新 View。AndroidView 节点必须通过 createAndroidView 调用业务 factory。
     * Creates a new View. AndroidView nodes must invoke the business factory through createAndroidView.
     */
    fun createView(
        context: Context,
        node: VNode,
        createAndroidView: ((Any) -> Any)?,
    ): View {
        return when (node.type) {
            NodeType.Text -> TextView(context)
            NodeType.TextField -> ViewComposeEditText(context).apply {
                background = null
            }
            NodeType.Checkbox -> CheckBox(context)
            NodeType.Switch -> Switch(context)
            NodeType.RadioButton -> RadioButton(context)
            NodeType.Slider -> SeekBar(context)
            NodeType.LinearProgressIndicator -> LinearProgressIndicator(context)
            NodeType.CircularProgressIndicator -> CircularProgressIndicator(context)
            NodeType.Button -> Button(context)
            NodeType.IconButton -> ImageButton(context)
            NodeType.Row -> DeclarativeLinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            NodeType.Column -> DeclarativeLinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            NodeType.Box, NodeType.Surface -> DeclarativeBoxLayout(context)
            NodeType.ConstraintLayout -> DeclarativeConstraintLayout(context)
            NodeType.AnimatedVisibilityHost -> DeclarativeAnimatedVisibilityHostLayout(context)
            NodeType.AnimatedSizeHost -> DeclarativeAnimatedSizeHostLayout(context)
            NodeType.NestedScrollHost -> DeclarativeNestedScrollHostLayout(context)
            NodeType.Spacer, NodeType.Divider -> View(context)
            NodeType.Canvas -> DeclarativeCanvasLayout(context)
            NodeType.Image -> ImageView(context)
            NodeType.AndroidView -> {
                val factory = requireNotNull(createAndroidView) {
                    "AndroidView node requires a factory."
                }
                val created = factory(context)
                // AndroidView 是唯一允许业务返回平台 View 的节点，必须在边界做类型校验。
                // AndroidView is the only node where business code returns a platform View, so validate it at the boundary.
                require(created is View) {
                    "AndroidView factory must return android.view.View, but returned ${created::class.java.name}."
                }
                created
            }
            NodeType.LazyColumn -> DeclarativeLazyListView(context).apply {
                layoutManager = LazyLinearLayoutManager(context)
                adapter = LazyListAdapter()
                // RecyclerView 默认项动画/复用池策略由框架统一配置，避免各 binder 重复设置。
                // RecyclerView default animation/reuse-pool policy is centralized here to avoid repeated binder setup.
                FrameworkRecyclerViewDefaults.applyLazyColumnDefaults(this)
            }
            NodeType.LazyRow -> DeclarativeLazyListView(context).apply {
                layoutManager = LazyLinearLayoutManager(
                    context = context,
                    orientation = LinearLayoutManager.HORIZONTAL,
                    reverseLayout = false,
                )
                adapter = LazyListAdapter(LinearLayoutManager.HORIZONTAL)
                FrameworkRecyclerViewDefaults.applyLazyRowDefaults(this)
            }
            NodeType.SegmentedControl -> DeclarativeSegmentedControlLayout(context)
            NodeType.ScrollableColumn -> DeclarativeScrollableColumnLayout(context)
            NodeType.ScrollableRow -> DeclarativeScrollableRowLayout(context)
            NodeType.FlowRow -> DeclarativeFlowRowLayout(context)
            NodeType.FlowColumn -> DeclarativeFlowColumnLayout(context)
            NodeType.NavigationBar -> DeclarativeNavigationBarLayout(context)
            NodeType.HorizontalPager -> DeclarativeHorizontalPagerLayout(context)
            NodeType.VerticalPager -> DeclarativeVerticalPagerLayout(context)
            NodeType.TabRow -> DeclarativeTabRowLayout(context)
            NodeType.LazyVerticalGrid -> DeclarativeLazyVerticalGridLayout(context)
            NodeType.PullToRefresh -> SwipeRefreshLayout(context)
        }
    }
}
