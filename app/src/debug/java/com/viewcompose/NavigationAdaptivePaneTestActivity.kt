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
 * Debug-only device certification host for adaptive native navigation panes.
 */
class NavigationAdaptivePaneTestActivity : ComponentActivity() {
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

    fun routeNames(): List<String> {
        return navController.snapshot.entries.map { entry -> entry.route.name }
    }

    fun visibleRouteNames(): List<String> {
        return routes.mapNotNull { route ->
            destinationContainerOrNull(route)
                ?.takeIf { container -> container.visibility == View.VISIBLE }
                ?.let { route }
        }
    }

    fun lifecycleState(route: String): Lifecycle.State? {
        return lifecycleByRoute[route]?.lifecycle?.currentState
    }

    fun destinationBounds(route: String): Rect? {
        val container = destinationContainerOrNull(route) ?: return null
        return Rect(
            container.left,
            container.top,
            container.right,
            container.bottom,
        )
    }

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
