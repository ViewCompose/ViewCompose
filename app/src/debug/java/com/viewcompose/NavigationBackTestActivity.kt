package com.viewcompose

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.navigation.NavFailure
import com.viewcompose.navigation.NavHost
import com.viewcompose.navigation.NavHostController
import com.viewcompose.navigation.NavResult
import com.viewcompose.navigation.NavTransitionSpec
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.rememberNavHostController
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.widget.core.OverlayHostDefaults
import com.viewcompose.widget.core.Text

/**
 * Debug-only real-device host for the incubating system-navigation stack.
 */
class NavigationBackTestActivity : AppCompatActivity() {
    private val systemBackEnabledState = mutableStateOf(true)
    private val failures = mutableListOf<NavFailure>()

    lateinit var navController: NavHostController
        private set

    var delegatedBackCount: Int = 0
        private set

    val failureCount: Int
        get() = failures.size

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

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    delegatedBackCount += 1
                }
            },
        )
        setUiContent(
            debug = true,
            debugTag = "NavigationBackDeviceTest",
        ) {
            val controller = rememberNavHostController(
                startDestination = NavRoute(HOME_ROUTE),
            )
            navController = controller
            NavHost(
                controller = controller,
                transitionSpec = NavTransitionSpec(
                    durationMillis = TRANSITION_DURATION_MILLIS,
                    travelFraction = 0.12f,
                    fadeEnabled = true,
                ),
                systemBackEnabled = systemBackEnabledState.value,
                overlayHostFactory = { OverlayHostDefaults.noOp },
                onFailure = failures::add,
            ) { entry ->
                Text(
                    text = destinationText(entry.route.name),
                    modifier = Modifier.testTag(destinationTag(entry.route.name)),
                )
            }
        }
    }

    fun push(routeName: String): NavResult {
        return navController.navigate(NavRoute(routeName))
    }

    fun setSystemBackEnabled(enabled: Boolean) {
        systemBackEnabledState.value = enabled
    }

    fun routeNames(): List<String> {
        return navController.snapshot.entries.map { entry -> entry.route.name }
    }

    fun entryIds(): List<String> {
        return navController.snapshot.entries.map { entry -> entry.id.value }
    }

    companion object {
        const val HOME_ROUTE = "home"
        const val DETAILS_ROUTE = "details"
        const val CONFIRMATION_ROUTE = "confirmation"
        const val TRANSITION_DURATION_MILLIS = 120L
        private const val DESTINATION_PREFIX = "Destination:"
        private const val DESTINATION_TAG_PREFIX = "navigation-back-destination-"

        fun destinationText(routeName: String): String {
            return "$DESTINATION_PREFIX$routeName"
        }

        fun destinationTag(routeName: String): String {
            return "$DESTINATION_TAG_PREFIX$routeName"
        }
    }
}
