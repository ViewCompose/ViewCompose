package com.viewcompose.renderer.view.tree

/*
 * Test responsibility: keeps every core NodeType assigned to one concrete Android backend and one
 * architecture owner before design-system-specific component backends are migrated.
 */

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView
import com.viewcompose.renderer.view.container.DeclarativeAnimatedSizeHostLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedVisibilityHostLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedContentHostLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedContentItemLayout
import com.viewcompose.renderer.view.container.DeclarativeBoxLayout
import com.viewcompose.renderer.view.container.DeclarativeCanvasLayout
import com.viewcompose.renderer.view.container.DeclarativeConstraintLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowColumnLayout
import com.viewcompose.renderer.view.container.DeclarativeFlowRowLayout
import com.viewcompose.renderer.view.container.DeclarativeHorizontalPagerLayout
import com.viewcompose.renderer.view.container.DeclarativeLazyListView
import com.viewcompose.renderer.view.container.DeclarativeLazyVerticalGridLayout
import com.viewcompose.renderer.view.container.DeclarativeLayoutConstraintHost
import com.viewcompose.renderer.view.container.DeclarativeLinearLayout
import com.viewcompose.renderer.view.container.DeclarativeNavigationBarLayout
import com.viewcompose.renderer.view.container.DeclarativeNestedScrollHostLayout
import com.viewcompose.renderer.view.container.DeclarativePullToRefreshLayout
import com.viewcompose.renderer.view.container.DeclarativeScrollableColumnLayout
import com.viewcompose.renderer.view.container.DeclarativeScrollableRowLayout
import com.viewcompose.renderer.view.container.DeclarativeSegmentedControlLayout
import com.viewcompose.renderer.view.container.DeclarativeTabRowLayout
import com.viewcompose.renderer.view.container.DeclarativeVerticalPagerLayout
import com.viewcompose.renderer.view.feedback.DeclarativeProgressIndicatorView
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ViewNodeBackendInventoryTest {
    private val context = ContextThemeWrapper(
        RuntimeEnvironment.getApplication(),
        androidx.appcompat.R.style.Theme_AppCompat,
    )

    @Test
    fun `every node type has one verified backend owner and concrete view`() {
        val declaredTypes = NodeType::class.java.declaredClasses.mapNotNull { nested ->
            runCatching { nested.getField("INSTANCE").get(null) as? NodeType }.getOrNull()
        }.toSet()

        assertEquals(declaredTypes, inventory.keys)
        inventory.forEach { (type, entry) ->
            val actual = ViewNodeFactory.createView(
                context = context,
                node = VNode(type = type, spec = EmptyNodeSpec),
                createAndroidView = { View(context) },
            )
            assertEquals("Unexpected backend for $type", entry.viewClass, actual.javaClass)
            assertTrue("Missing retained behavior rationale for $type", entry.behaviorContract.isNotBlank())
        }
    }

    private enum class BackendOwner {
        NativeBehavioralCore,
        DslComposite,
        NeutralCustomView,
        NamedAndroidIntegration,
    }

    private data class BackendEntry(
        val owner: BackendOwner,
        val viewClass: Class<out View>,
        val behaviorContract: String,
    )

    private companion object {
        fun native(
            viewClass: Class<out View>,
            behavior: String,
        ) = BackendEntry(BackendOwner.NativeBehavioralCore, viewClass, behavior)

        fun composite(
            viewClass: Class<out View>,
            behavior: String,
        ) = BackendEntry(BackendOwner.DslComposite, viewClass, behavior)

        fun neutral(
            viewClass: Class<out View>,
            behavior: String,
        ) = BackendEntry(BackendOwner.NeutralCustomView, viewClass, behavior)

        val inventory = mapOf(
            NodeType.Text to native(TextView::class.java, "platform text measurement and accessibility"),
            NodeType.TextField to native(ViewComposeEditText::class.java, "IME, selection, autofill, and undo"),
            NodeType.Checkbox to native(CheckBox::class.java, "toggle semantics and platform input"),
            NodeType.Switch to native(Switch::class.java, "toggle semantics, click, and platform animation"),
            NodeType.RadioButton to native(RadioButton::class.java, "selection semantics and platform input"),
            NodeType.Slider to native(ViewComposeSeekBar::class.java, "drag, key input, and range accessibility"),
            NodeType.LinearProgressIndicator to composite(
                DeclarativeProgressIndicatorView::class.java,
                "resolved progress geometry and drawing",
            ),
            NodeType.CircularProgressIndicator to composite(
                DeclarativeProgressIndicatorView::class.java,
                "resolved progress geometry and drawing",
            ),
            NodeType.Button to native(Button::class.java, "click, focus, keyboard, and accessibility action"),
            NodeType.IconButton to native(ImageButton::class.java, "click, focus, keyboard, and image semantics"),
            NodeType.Row to neutral(DeclarativeLinearLayout::class.java, "horizontal measurement and placement"),
            NodeType.Column to neutral(DeclarativeLinearLayout::class.java, "vertical measurement and placement"),
            NodeType.Box to neutral(DeclarativeBoxLayout::class.java, "stacked measurement and placement"),
            NodeType.Surface to composite(DeclarativeBoxLayout::class.java, "surface decoration and content stacking"),
            NodeType.ConstraintLayout to neutral(
                DeclarativeConstraintLayout::class.java,
                "constraint measurement and placement",
            ),
            NodeType.AnimatedVisibilityHost to neutral(
                DeclarativeAnimatedVisibilityHostLayout::class.java,
                "visibility transition clipping and measurement",
            ),
            NodeType.AnimatedContentHost to neutral(
                DeclarativeAnimatedContentHostLayout::class.java,
                "bounded replacement-pair measurement and placement",
            ),
            NodeType.AnimatedContentItemHost to neutral(
                DeclarativeAnimatedContentItemLayout::class.java,
                "replacement-item transforms and interaction ownership",
            ),
            NodeType.AnimatedSizeHost to neutral(
                DeclarativeAnimatedSizeHostLayout::class.java,
                "animated measurement bounds",
            ),
            NodeType.LayoutConstraintHost to neutral(
                DeclarativeLayoutConstraintHost::class.java,
                "portable maximum-size and aspect-ratio measurement",
            ),
            NodeType.NestedScrollHost to neutral(
                DeclarativeNestedScrollHostLayout::class.java,
                "nested-scroll parent and child dispatch",
            ),
            NodeType.Spacer to neutral(View::class.java, "modifier-owned empty measurement"),
            NodeType.Divider to neutral(View::class.java, "modifier-owned line measurement and drawing"),
            NodeType.Canvas to neutral(DeclarativeCanvasLayout::class.java, "recorded draw-command execution"),
            NodeType.Image to native(ImageView::class.java, "drawable lifecycle, scaling, and image semantics"),
            NodeType.AndroidView to BackendEntry(
                BackendOwner.NamedAndroidIntegration,
                View::class.java,
                "caller-owned Android View behavior",
            ),
            NodeType.LazyColumn to native(
                DeclarativeLazyListView::class.java,
                "RecyclerView virtualization, recycling, focus, and scroll",
            ),
            NodeType.LazyRow to native(
                DeclarativeLazyListView::class.java,
                "RecyclerView virtualization, recycling, focus, and scroll",
            ),
            NodeType.SegmentedControl to composite(
                DeclarativeSegmentedControlLayout::class.java,
                "segment selection semantics and item layout",
            ),
            NodeType.ScrollableColumn to neutral(
                DeclarativeScrollableColumnLayout::class.java,
                "eager vertical scroll range and nested dispatch",
            ),
            NodeType.ScrollableRow to neutral(
                DeclarativeScrollableRowLayout::class.java,
                "eager horizontal scroll range and nested dispatch",
            ),
            NodeType.FlowRow to neutral(DeclarativeFlowRowLayout::class.java, "wrapping row measurement and placement"),
            NodeType.FlowColumn to neutral(
                DeclarativeFlowColumnLayout::class.java,
                "wrapping column measurement and placement",
            ),
            NodeType.NavigationBar to composite(
                DeclarativeNavigationBarLayout::class.java,
                "destination selection semantics and item layout",
            ),
            NodeType.HorizontalPager to native(
                DeclarativeHorizontalPagerLayout::class.java,
                "paged drag, focus, recycling, and restoration",
            ),
            NodeType.VerticalPager to native(
                DeclarativeVerticalPagerLayout::class.java,
                "paged drag, focus, recycling, and restoration",
            ),
            NodeType.TabRow to composite(
                DeclarativeTabRowLayout::class.java,
                "tab selection semantics, indicator, and item layout",
            ),
            NodeType.LazyVerticalGrid to native(
                DeclarativeLazyVerticalGridLayout::class.java,
                "grid virtualization, recycling, focus, and scroll",
            ),
            NodeType.PullToRefresh to composite(
                DeclarativePullToRefreshLayout::class.java,
                "pull gesture arbitration and refresh semantics",
            ),
        )
    }
}
