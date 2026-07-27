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
import androidx.lifecycle.SavedStateHandle
import com.viewcompose.host.android.setUiContent
import com.viewcompose.navigation.NavFailure
import com.viewcompose.navigation.NavHost
import com.viewcompose.navigation.NavHostController
import com.viewcompose.navigation.NavResult
import com.viewcompose.navigation.NavTransitionSpec
import com.viewcompose.navigation.LocalNavGraphOwnerScope
import com.viewcompose.navigation.ProvideNavGraphOwner
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.navigation.core.navGraph
import com.viewcompose.navigation.rememberNavHostController
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.widget.core.OverlayHostDefaults
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.rememberSaveable
import com.viewcompose.viewmodel.savedStateHandle

/**
 * Debug-only real-device host for the incubating system-navigation stack.
 */
class NavigationBackTestActivity : AppCompatActivity() {
    private val systemBackEnabledState = mutableStateOf(true)
    private val failures = mutableListOf<NavFailure>()
    private val processDeathRecords = linkedMapOf<NavEntryId, ProcessDeathRecord>()
    private val processDeathGraphRecords = linkedMapOf<NavEntryId, ProcessDeathRecord>()
    private val processDeathGraph = navGraph(
        route = PROCESS_DEATH_ROOT_GRAPH_ROUTE,
        startDestination = NavRoute(HOME_ROUTE),
    ) {
        destination(HOME_ROUTE)
        navigation(
            route = PROCESS_DEATH_ACCOUNT_GRAPH_ROUTE,
            startDestination = NavRoute(DETAILS_ROUTE),
        ) {
            destination(DETAILS_ROUTE)
        }
    }
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
            val controller = if (processDeathCertificationEnabled()) {
                rememberNavHostController(processDeathGraph)
            } else {
                rememberNavHostController(
                    startDestination = NavRoute(HOME_ROUTE),
                )
            }
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
                if (processDeathCertificationEnabled()) {
                    val saveableValue = rememberSaveable(
                        key = PROCESS_DEATH_SAVEABLE_KEY,
                    ) {
                        mutableStateOf(PROCESS_DEATH_UNSEEDED_VALUE)
                    }
                    val handle = savedStateHandle(
                        key = PROCESS_DEATH_HANDLE_OWNER_KEY,
                    )
                    processDeathRecords[entry.id] = ProcessDeathRecord(
                        saveableValue = saveableValue,
                        savedStateHandle = handle,
                    )
                    val accountOwner = LocalNavGraphOwnerScope.current
                        ?.get(PROCESS_DEATH_ACCOUNT_GRAPH_ROUTE)
                    if (accountOwner == null) {
                        Text(
                            text = processDeathStatus(entry.route.name),
                            modifier = Modifier.testTag(destinationTag(entry.route.name)),
                        )
                    } else {
                        ProvideNavGraphOwner(PROCESS_DEATH_ACCOUNT_GRAPH_ROUTE) {
                            val graphSaveableValue = rememberSaveable(
                                key = PROCESS_DEATH_GRAPH_SAVEABLE_KEY,
                            ) {
                                mutableStateOf(PROCESS_DEATH_UNSEEDED_VALUE)
                            }
                            val graphHandle = savedStateHandle(
                                key = PROCESS_DEATH_GRAPH_HANDLE_OWNER_KEY,
                            )
                            processDeathGraphRecords[accountOwner.entry.id] = ProcessDeathRecord(
                                saveableValue = graphSaveableValue,
                                savedStateHandle = graphHandle,
                            )
                            Text(
                                text = processDeathStatus(entry.route.name),
                                modifier = Modifier.testTag(destinationTag(entry.route.name)),
                            )
                        }
                    }
                } else {
                    Text(
                        text = destinationText(entry.route.name),
                        modifier = Modifier.testTag(destinationTag(entry.route.name)),
                    )
                }
            }
        }
        if (savedInstanceState == null && processDeathCertificationEnabled()) {
            window.decorView.post(::seedProcessDeathCertificationState)
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

    private fun processDeathCertificationEnabled(): Boolean {
        return intent.getBooleanExtra(EXTRA_PROCESS_DEATH_CERTIFICATION, false)
    }

    private fun seedProcessDeathCertificationState() {
        check(
            navController.navigate(
                NavRoute(
                    name = PROCESS_DEATH_ACCOUNT_GRAPH_ROUTE,
                    arguments = mapOf(
                        PROCESS_DEATH_GRAPH_ARGUMENT_KEY to
                            NavValue.LongValue(PROCESS_DEATH_GRAPH_ARGUMENT_VALUE),
                    ),
                ),
            ) is NavResult.Committed,
        ) {
            "Unable to create the process-death certification back stack."
        }
        val entriesByRoute = navController.snapshot.entries.associateBy { entry ->
            entry.route.name
        }
        val homeRecord = checkNotNull(
            processDeathRecords[checkNotNull(entriesByRoute[HOME_ROUTE]).id],
        )
        val detailsRecord = checkNotNull(
            processDeathRecords[checkNotNull(entriesByRoute[DETAILS_ROUTE]).id],
        )
        val accountGraphEntry = checkNotNull(
            checkNotNull(entriesByRoute[DETAILS_ROUTE])
                .graphEntries
                .singleOrNull { graphEntry ->
                    graphEntry.route.name == PROCESS_DEATH_ACCOUNT_GRAPH_ROUTE
                },
        )
        val accountGraphRecord = checkNotNull(
            processDeathGraphRecords[accountGraphEntry.id],
        )
        homeRecord.savedStateHandle[PROCESS_DEATH_HANDLE_VALUE_KEY] =
            PROCESS_DEATH_HOME_HANDLE_VALUE
        detailsRecord.savedStateHandle[PROCESS_DEATH_HANDLE_VALUE_KEY] =
            PROCESS_DEATH_DETAILS_HANDLE_VALUE
        homeRecord.saveableValue.value = PROCESS_DEATH_HOME_SAVEABLE_VALUE
        detailsRecord.saveableValue.value = PROCESS_DEATH_DETAILS_SAVEABLE_VALUE
        accountGraphRecord.savedStateHandle[PROCESS_DEATH_HANDLE_VALUE_KEY] =
            PROCESS_DEATH_GRAPH_HANDLE_VALUE
        accountGraphRecord.saveableValue.value = PROCESS_DEATH_GRAPH_SAVEABLE_VALUE
    }

    private fun processDeathStatus(topRouteName: String): String {
        val stack = navController.snapshot.entries.joinToString(separator = ";") { entry ->
            val record = processDeathRecords[entry.id]
            val saveableValue = record?.saveableValue?.value ?: PROCESS_DEATH_MISSING_VALUE
            val handleValue = record?.savedStateHandle
                ?.get<Int>(PROCESS_DEATH_HANDLE_VALUE_KEY)
                ?: PROCESS_DEATH_MISSING_VALUE
            "${entry.route.name}@${entry.id.value}" +
                "[saveable=$saveableValue,handle=$handleValue]"
        }
        val graphScopes = navController.snapshot.entries
            .flatMap { entry -> entry.graphEntries }
            .distinctBy { graphEntry -> graphEntry.id }
            .joinToString(separator = ";") { graphEntry ->
                val record = processDeathGraphRecords[graphEntry.id]
                val saveableValue = record?.saveableValue?.value ?: PROCESS_DEATH_MISSING_VALUE
                val handleValue = record?.savedStateHandle
                    ?.get<Int>(PROCESS_DEATH_HANDLE_VALUE_KEY)
                    ?: PROCESS_DEATH_MISSING_VALUE
                val argumentValue = graphEntry.route[
                    PROCESS_DEATH_GRAPH_ARGUMENT_KEY
                ]?.let { value ->
                    (value as? NavValue.LongValue)?.value
                } ?: PROCESS_DEATH_MISSING_VALUE.toLong()
                "${graphEntry.route.name}@${graphEntry.id.value}" +
                    "[saveable=$saveableValue,handle=$handleValue,arg=$argumentValue]"
            }
        return "$PROCESS_DEATH_STATUS_PREFIX" +
            "pid=${android.os.Process.myPid()}|" +
            "top=$topRouteName|" +
            "stack=$stack|" +
            "graphs=$graphScopes"
    }

    data class DestinationViewSample(
        val homeVisibility: Int,
        val detailsVisibility: Int,
        val homeTranslationX: Float,
        val detailsTranslationX: Float,
        val homeAlpha: Float,
        val detailsAlpha: Float,
    )

    private data class ProcessDeathRecord(
        val saveableValue: MutableState<Int>,
        val savedStateHandle: SavedStateHandle,
    )

    companion object {
        const val HOME_ROUTE = "home"
        const val DETAILS_ROUTE = "details"
        const val CONFIRMATION_ROUTE = "confirmation"
        const val TRANSITION_DURATION_MILLIS = 120L
        const val EXTRA_PROCESS_DEATH_CERTIFICATION =
            "com.viewcompose.extra.PROCESS_DEATH_CERTIFICATION"
        const val PROCESS_DEATH_STATUS_PREFIX = "PROCESS_DEATH|"
        const val PROCESS_DEATH_HOME_SAVEABLE_VALUE = 11
        const val PROCESS_DEATH_HOME_HANDLE_VALUE = 101
        const val PROCESS_DEATH_DETAILS_SAVEABLE_VALUE = 29
        const val PROCESS_DEATH_DETAILS_HANDLE_VALUE = 202
        const val PROCESS_DEATH_GRAPH_SAVEABLE_VALUE = 37
        const val PROCESS_DEATH_GRAPH_HANDLE_VALUE = 303
        const val PROCESS_DEATH_GRAPH_ARGUMENT_VALUE = 42L
        private const val DESTINATION_PREFIX = "Destination:"
        private const val DESTINATION_TAG_PREFIX = "navigation-back-destination-"
        private const val PROCESS_DEATH_ROOT_GRAPH_ROUTE = "process-death-root"
        private const val PROCESS_DEATH_ACCOUNT_GRAPH_ROUTE = "process-death-account"
        private const val PROCESS_DEATH_SAVEABLE_KEY = "process-death-state"
        private const val PROCESS_DEATH_HANDLE_OWNER_KEY = "process-death-handle-owner"
        private const val PROCESS_DEATH_GRAPH_SAVEABLE_KEY = "process-death-graph-state"
        private const val PROCESS_DEATH_GRAPH_HANDLE_OWNER_KEY =
            "process-death-graph-handle-owner"
        private const val PROCESS_DEATH_HANDLE_VALUE_KEY = "process-death-handle-value"
        private const val PROCESS_DEATH_GRAPH_ARGUMENT_KEY = "userId"
        private const val PROCESS_DEATH_UNSEEDED_VALUE = -1
        private const val PROCESS_DEATH_MISSING_VALUE = -2

        fun destinationText(routeName: String): String {
            return "$DESTINATION_PREFIX$routeName"
        }

        fun destinationTag(routeName: String): String {
            return "$DESTINATION_TAG_PREFIX$routeName"
        }
    }
}
