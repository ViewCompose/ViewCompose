package com.viewcompose

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.viewcompose.host.android.setUiContent
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.navigation.NavHost
import com.viewcompose.navigation.NavHostController
import com.viewcompose.navigation.NavPanePolicy
import com.viewcompose.navigation.NavTransitionSpec
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.rememberNavHostController
import com.viewcompose.widget.core.OverlayHostDefaults
import com.viewcompose.widget.core.Text

/**
 * 自适应原生导航窗格的 debug-only 设备认证宿主。
 * Debug-only device certification host for adaptive native navigation panes.
 *
 * 测试通过它读取路由、可见窗格、生命周期和真实 View 边界，避免只断言纯模型状态。
 * Tests read routes, visible panes, lifecycles, and real View bounds from this host instead of
 * asserting only pure model state.
 */
class NavigationAdaptivePaneTestActivity : ComponentActivity() {
    /**
     * 按 route 记录 destination 的 lifecycle owner，供旋转后断言 CREATED/RESUMED 状态。
     * Records destination lifecycle owners by route so rotation tests can assert CREATED/RESUMED states.
     */
    private val lifecycleByRoute = linkedMapOf<String, androidx.lifecycle.LifecycleOwner>()

    lateinit var navController: NavHostController
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)

        setUiContent(
            debug = true,
            debugTag = "NavigationAdaptivePaneDeviceTest",
        ) {
            val controller = rememberNavHostController(
                startDestination = NavRoute(HOME_ROUTE),
            )
            navController = controller
            NavHost(
                controller = controller,
                transitionSpec = NavTransitionSpec.None,
                panePolicy = NavPanePolicy(
                    minPaneWidthDp = MIN_PANE_WIDTH_DP,
                    maxPaneCount = 3,
                    paneSpacingDp = PANE_SPACING_DP,
                ),
                systemBackEnabled = true,
                overlayHostFactory = { OverlayHostDefaults.noOp },
            ) { entry ->
                lifecycleByRoute[entry.route.name] = checkNotNull(LocalLifecycleOwner.current)
                Text(statusText(entry.route.name))
            }
        }
        if (savedInstanceState == null) {
            window.decorView.post {
                navController.navigate(NavRoute(DETAILS_ROUTE))
                navController.navigate(NavRoute(CONFIRMATION_ROUTE))
            }
        }
    }

    /**
     * 返回当前 back stack 中所有 route 名称。
     * Returns all route names currently in the back stack.
     */
    fun routeNames(): List<String> {
        return navController.snapshot.entries.map { entry -> entry.route.name }
    }

    /**
     * 返回当前真实可见的 destination route 名称。
     * Returns route names whose destination containers are actually visible.
     */
    fun visibleRouteNames(): List<String> {
        return routes.mapNotNull { route ->
            destinationContainerOrNull(route)
                ?.takeIf { container -> container.visibility == View.VISIBLE }
                ?.let { route }
        }
    }

    /**
     * 返回指定 route 当前绑定的 lifecycle 状态。
     * Returns the lifecycle state currently bound to the given route.
     */
    fun lifecycleState(route: String): Lifecycle.State? {
        return lifecycleByRoute[route]?.lifecycle?.currentState
    }

    /**
     * 返回指定 route 对应真实 View 容器的屏幕内布局边界。
     * Returns layout bounds for the real View container of the given route.
     */
    fun destinationBounds(route: String): Rect? {
        val container = destinationContainerOrNull(route) ?: return null
        return Rect(
            container.left,
            container.top,
            container.right,
            container.bottom,
        )
    }

    /**
     * 执行一次 pop 并返回是否提交成功。
     * Performs one pop and returns whether it was committed.
     */
    fun pop(): Boolean {
        return navController.popBackStack() is com.viewcompose.navigation.NavResult.Committed
    }

    private fun destinationContainerOrNull(route: String): View? {
        val root = findViewById<ViewGroup>(android.R.id.content)
        return findTextViewByText(
            root = root,
            text = statusText(route),
        )?.parent as? View
    }

    private fun findTextViewByText(
        root: View,
        text: String,
    ): TextView? {
        if (root is TextView && root.text.toString() == text) {
            return root
        }
        if (root !is ViewGroup) {
            return null
        }
        for (index in 0 until root.childCount) {
            findTextViewByText(root.getChildAt(index), text)?.let { return it }
        }
        return null
    }

    private companion object {
        const val HOME_ROUTE = "adaptive-home"
        const val DETAILS_ROUTE = "adaptive-details"
        const val CONFIRMATION_ROUTE = "adaptive-confirmation"
        const val MIN_PANE_WIDTH_DP = 220f
        const val PANE_SPACING_DP = 8f
        const val STATUS_PREFIX = "ADAPTIVE_PANE|"

        val routes = listOf(
            HOME_ROUTE,
            DETAILS_ROUTE,
            CONFIRMATION_ROUTE,
        )

        fun statusText(route: String): String = "$STATUS_PREFIX$route"
    }
}
