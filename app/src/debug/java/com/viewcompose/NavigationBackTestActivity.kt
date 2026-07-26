package com.viewcompose

import android.os.Build
import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
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
    private val destinationViewSamples = mutableListOf<DestinationViewSample>()
    private var destinationViewSampling = false
    private val destinationViewSampleFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!destinationViewSampling) {
                return
            }
            captureDestinationViewSample()?.let(destinationViewSamples::add)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

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

    fun beginDestinationViewSampling() {
        destinationViewSamples.clear()
        destinationViewSampling = true
        captureDestinationViewSample()?.let(destinationViewSamples::add)
        Choreographer.getInstance().postFrameCallback(destinationViewSampleFrameCallback)
    }

    fun endDestinationViewSampling(): List<DestinationViewSample> {
        destinationViewSampling = false
        Choreographer.getInstance().removeFrameCallback(destinationViewSampleFrameCallback)
        captureDestinationViewSample()?.let(destinationViewSamples::add)
        return destinationViewSamples.toList()
    }

    private fun captureDestinationViewSample(): DestinationViewSample? {
        val home = destinationContainerOrNull(HOME_ROUTE) ?: return null
        val details = destinationContainerOrNull(DETAILS_ROUTE) ?: return null
        return DestinationViewSample(
            homeVisibility = home.visibility,
            detailsVisibility = details.visibility,
            homeTranslationX = home.translationX,
            detailsTranslationX = details.translationX,
            homeAlpha = home.alpha,
            detailsAlpha = details.alpha,
        )
    }

    private fun destinationContainerOrNull(routeName: String): View? {
        val root = findViewById<ViewGroup>(android.R.id.content)
        return findTextViewByText(
            root = root,
            text = destinationText(routeName),
        )?.parent as? View
    }

    private fun findTextViewByText(
        root: View,
        text: String,
    ): TextView? {
        if (root is TextView && root.text?.toString() == text) {
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

    data class DestinationViewSample(
        val homeVisibility: Int,
        val detailsVisibility: Int,
        val homeTranslationX: Float,
        val detailsTranslationX: Float,
        val homeAlpha: Float,
        val detailsAlpha: Float,
    )

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
