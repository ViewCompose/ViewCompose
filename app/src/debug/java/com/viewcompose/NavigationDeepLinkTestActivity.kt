package com.viewcompose

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.viewcompose.android.setUiContent
import com.viewcompose.navigation.NavDeepLinkResult
import com.viewcompose.navigation.NavHost
import com.viewcompose.navigation.NavResult
import com.viewcompose.navigation.core.NavDeepLink
import com.viewcompose.navigation.core.NavDeepLinkArgumentType
import com.viewcompose.navigation.core.NavRootBackBehavior
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavStackConfiguration
import com.viewcompose.navigation.core.NavStackId
import com.viewcompose.navigation.core.NavStackSetSnapshot
import com.viewcompose.navigation.core.NavStackSpec
import com.viewcompose.navigation.core.NavValue
import com.viewcompose.navigation.core.navGraph
import com.viewcompose.navigation.rememberNavHostController
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.foundation.OverlayHostDefaults
import com.viewcompose.ui.foundation.Text

/**
 * 导航 deep link 设备测试的 debug-only 宿主。
 * Debug-only host for navigation deep-link device tests.
 *
 * 它把导航结果和多 back stack 状态序列化为单行文本，方便 UiAutomator 稳定读取。
 * It serializes navigation outcome and multi-back-stack state into one text line for stable UiAutomator reads.
 */
class NavigationDeepLinkTestActivity : ComponentActivity() {
    private val outcome = mutableStateOf(OUTCOME_NONE)
    private var controller: com.viewcompose.navigation.NavHostController? = null

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
            debugTag = "NavigationDeepLinkDeviceTest",
        ) {
            val rememberedController = rememberNavHostController(
                stackConfiguration = StackConfiguration,
                graph = Graph,
            )
            controller = rememberedController
            NavHost(
                controller = rememberedController,
                systemBackEnabled = true,
                overlayHostFactory = { OverlayHostDefaults.noOp },
            ) {
                val state = rememberedController.navigationState.value
                Text(
                    text = statusText(
                        state = state,
                        outcome = outcome.value,
                    ),
                    modifier = Modifier.testTag(STATUS_TAG),
                )
            }
        }
        window.decorView.post {
            consumeDeepLink(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDeepLink(intent)
    }

    /**
     * 消费当前 Intent 中的 deep link，并记录导航结果。
     * Consumes the deep link in the current Intent and records the navigation outcome.
     */
    private fun consumeDeepLink(intent: Intent) {
        outcome.value = when (
            val result = checkNotNull(controller).navigateDeepLink(intent)
        ) {
            is NavDeepLinkResult.Navigated -> {
                when (result.navigationResult) {
                    is NavResult.Committed -> OUTCOME_NAVIGATED
                    is NavResult.NoChange -> OUTCOME_NO_CHANGE
                    is NavResult.Queued -> OUTCOME_QUEUED
                    is NavResult.Failed -> OUTCOME_FAILED
                }
            }

            NavDeepLinkResult.NoMatch -> OUTCOME_NO_MATCH
            is NavDeepLinkResult.Rejected -> OUTCOME_REJECTED
            NavDeepLinkResult.Unsupported -> OUTCOME_UNSUPPORTED
        }
    }

    /**
     * 生成测试可解析的导航状态串。
     * Builds a navigation status string that tests can parse.
     */
    private fun statusText(
        state: NavStackSetSnapshot,
        outcome: String,
    ): String {
        val argument = (
            state.activeStack.top.route[USER_ID_ARGUMENT] as? NavValue.LongValue
        )?.value
            ?.toString()
            ?: NO_ARGUMENT
        return "$STATUS_PREFIX" +
            "outcome=$outcome|" +
            "active=${state.activeStackId.value}|" +
            "history=${state.selectionHistory.joinToString(";") { stackId -> stackId.value }}|" +
            "top=${state.activeStack.top.route.name}|" +
            "userId=$argument|" +
            "home=${state.stackRoutes(HomeStack)}|" +
            "account=${state.stackRoutes(AccountStack)}"
    }

    private fun NavStackSetSnapshot.stackRoutes(stackId: NavStackId): String {
        return checkNotNull(this[stackId]).entries.joinToString(separator = ";") { entry ->
            entry.route.name
        }
    }

    private companion object {
        val HomeStack = NavStackId("deep-link-home")
        val AccountStack = NavStackId("deep-link-account")

        const val STATUS_PREFIX = "DEEP_LINK|"
        const val STATUS_TAG = "navigation-deep-link-status"
        const val USER_ID_ARGUMENT = "userId"
        const val NO_ARGUMENT = "none"
        const val OUTCOME_NONE = "none"
        const val OUTCOME_NAVIGATED = "navigated"
        const val OUTCOME_NO_CHANGE = "no-change"
        const val OUTCOME_QUEUED = "queued"
        const val OUTCOME_FAILED = "failed"
        const val OUTCOME_NO_MATCH = "no-match"
        const val OUTCOME_REJECTED = "rejected"
        const val OUTCOME_UNSUPPORTED = "unsupported"

        val StackConfiguration = NavStackConfiguration(
            initialStackId = HomeStack,
            stacks = listOf(
                NavStackSpec(HomeStack, NavRoute("home")),
                NavStackSpec(AccountStack, NavRoute("account")),
            ),
            rootBackBehavior = NavRootBackBehavior.PreviousStack,
        )

        val Graph = navGraph(
            route = "deep-link-root",
            startDestination = NavRoute("home"),
        ) {
            destination(
                route = "home",
                deepLinks = listOf(
                    NavDeepLink(
                        uriPattern = "viewcompose://navigation/home",
                        targetStackId = HomeStack,
                    ),
                ),
            )
            navigation(
                route = "account",
                startDestination = NavRoute("profile"),
            ) {
                destination(
                    route = "profile",
                    deepLinks = listOf(
                        NavDeepLink(
                            uriPattern = "viewcompose://navigation/account/profile",
                            targetStackId = AccountStack,
                        ),
                    ),
                )
                destination(
                    route = "security",
                    deepLinks = listOf(
                        NavDeepLink(
                            uriPattern = "viewcompose://navigation/account/{userId}",
                            argumentTypes = mapOf(
                                USER_ID_ARGUMENT to NavDeepLinkArgumentType.Long,
                            ),
                            targetStackId = AccountStack,
                        ),
                    ),
                )
            }
        }
    }
}
