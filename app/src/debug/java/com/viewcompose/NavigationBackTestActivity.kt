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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.material3.android.setMaterial3UiContent
import com.viewcompose.navigation.NavFailure
import com.viewcompose.navigation.NavHost
import com.viewcompose.navigation.NavHostController
import com.viewcompose.navigation.NavResult
import com.viewcompose.navigation.NavTransitionSpec
import com.viewcompose.navigation.LocalNavGraphOwnerScope
import com.viewcompose.navigation.ProvideNavGraphOwner
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavRootBackBehavior
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavStackConfiguration
import com.viewcompose.navigation.core.NavStackId
import com.viewcompose.navigation.core.NavStackSpec
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.navigation.core.navGraph
import com.viewcompose.navigation.rememberNavHostController
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.foundation.OverlayHostDefaults
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.viewmodel.viewModel

/**
 * 孵化中的系统导航栈在真机上的 debug-only 宿主。
 * Debug-only real-device host for the incubating system-navigation stack.
 *
 * 该 Activity 暴露路由、entry id、转场采样和进程重建状态，用于 androidTest 验证真实 View 行为。
 * This Activity exposes routes, entry ids, transition samples, and process-recreation state so
 * androidTest can verify real View behavior.
 */
class NavigationBackTestActivity : AppCompatActivity() {
    private val systemBackEnabledState = mutableStateOf(true)
    private val failures = mutableListOf<NavFailure>()
    private val destinationLifecycleOwners = linkedMapOf<String, LifecycleOwner>()
    private val processDeathRecords = linkedMapOf<NavEntryId, ProcessDeathRecord>()
    private val processDeathGraphRecords = linkedMapOf<NavEntryId, ProcessDeathRecord>()
    /**
     * 进程重建认证专用图，覆盖普通 destination、嵌套 graph owner 和多 back stack。
     * Process-recreation certification graph covering destinations, nested graph owners, and multiple stacks.
     */
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
            destination(PROCESS_DEATH_SECURITY_ROUTE)
        }
        destination(CONFIRMATION_ROUTE)
    }
    private val processDeathHomeStackId = NavStackId(PROCESS_DEATH_HOME_STACK)
    private val processDeathAccountStackId = NavStackId(PROCESS_DEATH_ACCOUNT_STACK)
    private val processDeathStackConfiguration = NavStackConfiguration(
        initialStackId = processDeathHomeStackId,
        stacks = listOf(
            NavStackSpec(
                id = processDeathHomeStackId,
                startDestination = NavRoute(HOME_ROUTE),
            ),
            NavStackSpec(
                id = processDeathAccountStackId,
                startDestination = NavRoute(
                    name = PROCESS_DEATH_ACCOUNT_GRAPH_ROUTE,
                    arguments = mapOf(
                        PROCESS_DEATH_GRAPH_ARGUMENT_KEY to
                            NavValue.LongValue(PROCESS_DEATH_GRAPH_ARGUMENT_VALUE),
                    ),
                ),
            ),
        ),
        rootBackBehavior = NavRootBackBehavior.PreviousStack,
    )
    private val destinationViewSamples = mutableListOf<DestinationViewSample>()
    private var destinationViewSampling = false
    /**
     * 按帧采集转场期间两个 destination 容器的真实 View 属性。
     * Samples real View properties for the two destination containers during transitions.
     */
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
        setMaterial3UiContent(
            debug = true,
            debugTag = "NavigationBackDeviceTest",
        ) {
            val controller = if (processDeathCertificationEnabled()) {
                rememberNavHostController(
                    stackConfiguration = processDeathStackConfiguration,
                    graph = processDeathGraph,
                )
            } else {
                rememberNavHostController(
                    startDestination = NavRoute(HOME_ROUTE),
                )
            }
            navController = controller
            NavHost(
                controller = controller,
                transitionSpec = NavTransitionSpec.Default,
                systemBackEnabled = systemBackEnabledState.value,
                overlayHostFactory = { OverlayHostDefaults.noOp },
                onFailure = failures::add,
            ) { entry ->
                destinationLifecycleOwners[entry.route.name] = checkNotNull(
                    LocalLifecycleOwner.current,
                ) {
                    "Navigation destination ${entry.route.name} has no LifecycleOwner."
                }
                if (processDeathCertificationEnabled()) {
                    val saveableValue = rememberSaveable(
                        key = PROCESS_DEATH_SAVEABLE_KEY,
                    ) {
                        mutableStateOf(PROCESS_DEATH_UNSEEDED_VALUE)
                    }
                    val model = viewModel<ProcessDeathStateViewModel>(
                        key = PROCESS_DEATH_HANDLE_OWNER_KEY,
                    ) {
                        ProcessDeathStateViewModel(createSavedStateHandle())
                    }
                    processDeathRecords[entry.id] = ProcessDeathRecord(
                        saveableValue = saveableValue,
                        savedStateHandle = model.handle,
                    )
                    val accountOwner = LocalNavGraphOwnerScope.current
                        ?.get(PROCESS_DEATH_ACCOUNT_GRAPH_ROUTE)
                    if (
                        accountOwner == null ||
                        entry.route.name != DETAILS_ROUTE
                    ) {
                        Text(
                            text = processDeathStatus(),
                            modifier = Modifier.testTag(destinationTag(entry.route.name)),
                        )
                    } else {
                        ProvideNavGraphOwner(PROCESS_DEATH_ACCOUNT_GRAPH_ROUTE) {
                            val graphSaveableValue = rememberSaveable(
                                key = PROCESS_DEATH_GRAPH_SAVEABLE_KEY,
                            ) {
                                mutableStateOf(PROCESS_DEATH_UNSEEDED_VALUE)
                            }
                            val graphModel = viewModel<ProcessDeathStateViewModel>(
                                key = PROCESS_DEATH_GRAPH_HANDLE_OWNER_KEY,
                            ) {
                                ProcessDeathStateViewModel(createSavedStateHandle())
                            }
                            processDeathGraphRecords[accountOwner.entry.id] = ProcessDeathRecord(
                                saveableValue = graphSaveableValue,
                                savedStateHandle = graphModel.handle,
                            )
                            Text(
                                text = processDeathStatus(),
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

    /**
     * 从测试线程请求一次导航 push。
     * Requests one navigation push from the test thread.
     */
    fun push(routeName: String): NavResult {
        return navController.navigate(NavRoute(routeName))
    }

    /**
     * 动态控制系统 Back 是否交给 NavHost 处理。
     * Dynamically controls whether system Back is handled by NavHost.
     */
    fun setSystemBackEnabled(enabled: Boolean) {
        systemBackEnabledState.value = enabled
    }

    /**
     * 返回当前 back stack 中所有 route 名称。
     * Returns all route names currently in the back stack.
     */
    fun routeNames(): List<String> {
        return navController.snapshot.entries.map { entry -> entry.route.name }
    }

    /**
     * 返回当前 back stack 中所有 entry id，用于重建前后身份对比。
     * Returns all entry ids in the current back stack for identity checks across recreation.
     */
    fun entryIds(): List<String> {
        return navController.snapshot.entries.map { entry -> entry.id.value }
    }

    /**
     * 返回 DSL 内容实际捕获的最近 destination Lifecycle 状态。
     * Returns the nearest destination Lifecycle state actually captured by DSL content.
     */
    fun destinationLifecycleState(routeName: String): Lifecycle.State? {
        return destinationLifecycleOwners[routeName]?.lifecycle?.currentState
    }

    /**
     * 开始逐帧采集 destination View 状态。
     * Starts frame-by-frame destination View sampling.
     */
    fun beginDestinationViewSampling() {
        destinationViewSamples.clear()
        destinationViewSampling = true
        captureDestinationViewSample()?.let(destinationViewSamples::add)
        Choreographer.getInstance().postFrameCallback(destinationViewSampleFrameCallback)
    }

    /**
     * 停止采样并返回已捕获的 destination View 状态。
     * Stops sampling and returns captured destination View states.
     */
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
            homeScaleX = home.scaleX,
            detailsScaleX = details.scaleX,
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

    /**
     * 播种进程重建认证状态，确保每种 owner 范围都有可保存数据。
     * Seeds process-recreation certification state so each owner scope has saveable data.
     */
    private fun seedProcessDeathCertificationState() {
        check(
            navController.navigate(NavRoute(CONFIRMATION_ROUTE)) is NavResult.Committed,
        ) {
            "Unable to extend the process-death home stack."
        }
        check(
            navController.selectStack(processDeathAccountStackId) is NavResult.Committed,
        ) {
            "Unable to select the process-death account stack."
        }
        check(
            navController.navigate(NavRoute(PROCESS_DEATH_SECURITY_ROUTE)) is NavResult.Committed,
        ) {
            "Unable to extend the process-death account stack."
        }
        val entriesByRoute = navController.stackState.stacks
            .values
            .flatMap { snapshot -> snapshot.entries }
            .associateBy { entry ->
                entry.route.name
            }
        val homeRecord = checkNotNull(
            processDeathRecords[checkNotNull(entriesByRoute[HOME_ROUTE]).id],
        )
        val confirmationRecord = checkNotNull(
            processDeathRecords[checkNotNull(entriesByRoute[CONFIRMATION_ROUTE]).id],
        )
        val detailsRecord = checkNotNull(
            processDeathRecords[checkNotNull(entriesByRoute[DETAILS_ROUTE]).id],
        )
        val securityRecord = checkNotNull(
            processDeathRecords[checkNotNull(entriesByRoute[PROCESS_DEATH_SECURITY_ROUTE]).id],
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
        confirmationRecord.savedStateHandle[PROCESS_DEATH_HANDLE_VALUE_KEY] =
            PROCESS_DEATH_CONFIRMATION_HANDLE_VALUE
        detailsRecord.savedStateHandle[PROCESS_DEATH_HANDLE_VALUE_KEY] =
            PROCESS_DEATH_DETAILS_HANDLE_VALUE
        securityRecord.savedStateHandle[PROCESS_DEATH_HANDLE_VALUE_KEY] =
            PROCESS_DEATH_SECURITY_HANDLE_VALUE
        homeRecord.saveableValue.value = PROCESS_DEATH_HOME_SAVEABLE_VALUE
        confirmationRecord.saveableValue.value = PROCESS_DEATH_CONFIRMATION_SAVEABLE_VALUE
        detailsRecord.saveableValue.value = PROCESS_DEATH_DETAILS_SAVEABLE_VALUE
        securityRecord.saveableValue.value = PROCESS_DEATH_SECURITY_SAVEABLE_VALUE
        accountGraphRecord.savedStateHandle[PROCESS_DEATH_HANDLE_VALUE_KEY] =
            PROCESS_DEATH_GRAPH_HANDLE_VALUE
        accountGraphRecord.saveableValue.value = PROCESS_DEATH_GRAPH_SAVEABLE_VALUE
    }

    /**
     * 序列化当前导航和保存状态，供测试在进程重建前后做字符串级对比。
     * Serializes navigation and saved state so tests can compare before and after process recreation.
     */
    private fun processDeathStatus(): String {
        val stackState = navController.stackState
        val stacks = stackState.stacks.entries.joinToString(separator = "/") { stack ->
            val entries = stack.value.entries.joinToString(separator = ";") { entry ->
                val record = processDeathRecords[entry.id]
                val saveableValue = record?.saveableValue?.value ?: PROCESS_DEATH_MISSING_VALUE
                val handleValue = record?.savedStateHandle
                    ?.get<Int>(PROCESS_DEATH_HANDLE_VALUE_KEY)
                    ?: PROCESS_DEATH_MISSING_VALUE
                    "${entry.route.name}@${entry.id.value}" +
                    "[saveable=$saveableValue,handle=$handleValue]"
            }
            "${stack.key.value}{$entries}"
        }
        val graphScopes = stackState.stacks
            .values
            .flatMap { snapshot -> snapshot.entries }
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
            "active=${stackState.activeStackId.value}|" +
            "history=${stackState.selectionHistory.joinToString(";") { it.value }}|" +
            "top=${navController.snapshot.top.route.name}|" +
            "stacks=$stacks|" +
            "graphs=$graphScopes"
    }

    /**
     * 单帧 destination View 属性快照。
     * One-frame snapshot of destination View properties.
     */
    data class DestinationViewSample(
        val homeVisibility: Int,
        val detailsVisibility: Int,
        val homeTranslationX: Float,
        val detailsTranslationX: Float,
        val homeAlpha: Float,
        val detailsAlpha: Float,
        val homeScaleX: Float,
        val detailsScaleX: Float,
    )

    /**
     * 进程重建认证中绑定到 entry 或 graph owner 的保存状态句柄。
     * Saved-state handles bound to an entry or graph owner during process-recreation certification.
     */
    private data class ProcessDeathRecord(
        val saveableValue: MutableState<Int>,
        val savedStateHandle: SavedStateHandle,
    )

    private class ProcessDeathStateViewModel(
        val handle: SavedStateHandle,
    ) : ViewModel()

    companion object {
        const val HOME_ROUTE = "home"
        const val DETAILS_ROUTE = "details"
        const val CONFIRMATION_ROUTE = "confirmation"
        const val TRANSITION_DURATION_MILLIS = 450L
        const val EXTRA_PROCESS_DEATH_CERTIFICATION =
            "com.viewcompose.extra.PROCESS_DEATH_CERTIFICATION"
        const val PROCESS_DEATH_STATUS_PREFIX = "PROCESS_DEATH|"
        const val PROCESS_DEATH_HOME_SAVEABLE_VALUE = 11
        const val PROCESS_DEATH_HOME_HANDLE_VALUE = 101
        const val PROCESS_DEATH_CONFIRMATION_SAVEABLE_VALUE = 17
        const val PROCESS_DEATH_CONFIRMATION_HANDLE_VALUE = 151
        const val PROCESS_DEATH_DETAILS_SAVEABLE_VALUE = 29
        const val PROCESS_DEATH_DETAILS_HANDLE_VALUE = 202
        const val PROCESS_DEATH_SECURITY_SAVEABLE_VALUE = 31
        const val PROCESS_DEATH_SECURITY_HANDLE_VALUE = 252
        const val PROCESS_DEATH_GRAPH_SAVEABLE_VALUE = 37
        const val PROCESS_DEATH_GRAPH_HANDLE_VALUE = 303
        const val PROCESS_DEATH_GRAPH_ARGUMENT_VALUE = 42L
        private const val DESTINATION_PREFIX = "Destination:"
        private const val DESTINATION_TAG_PREFIX = "navigation-back-destination-"
        private const val PROCESS_DEATH_ROOT_GRAPH_ROUTE = "process-death-root"
        private const val PROCESS_DEATH_ACCOUNT_GRAPH_ROUTE = "process-death-account"
        private const val PROCESS_DEATH_SECURITY_ROUTE = "process-death-security"
        private const val PROCESS_DEATH_HOME_STACK = "process-death-home-stack"
        private const val PROCESS_DEATH_ACCOUNT_STACK = "process-death-account-stack"
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
