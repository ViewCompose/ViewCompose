package com.viewcompose

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.viewcompose.host.android.setUiContent
import com.viewcompose.navigation.NavDeepLinkResult
import com.viewcompose.navigation.NavHostController
import com.viewcompose.navigation.core.NavStackSetSnapshot
import com.viewcompose.overlay.android.host.AndroidOverlayHost
import com.viewcompose.runtime.mutableStateOf

/**
 * Interactive acceptance host for the framework-owned navigation engine.
 *
 * The Activity only supplies the Android window and forwards native VIEW intents. Page ownership,
 * retained stacks, lifecycle, saved state, ViewModels, Back, and adaptive panes belong to NavHost.
 */
class SystemNavigationActivity : AppCompatActivity() {
    private val externalDeepLinkOutcome = mutableStateOf("尚未接收外部 Deep Link")
    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val diagnosticsEnabled = intent.getBooleanExtra(
            EXTRA_RENDER_DIAGNOSTICS,
            false,
        )
        setUiContent(
            debug = diagnosticsEnabled,
            debugTag = "SystemNavigationDemo",
            overlayHostFactory = ::AndroidOverlayHost,
            onRenderResult = if (diagnosticsEnabled) {
                DemoRenderDiagnosticsStore::record
            } else {
                null
            },
        ) { root ->
            SystemNavigationDemoPage(
                root = root,
                externalDeepLinkOutcome = externalDeepLinkOutcome,
                diagnosticsEnabled = diagnosticsEnabled,
                onControllerReady = { controller -> navController = controller },
                onExit = ::finish,
            )
        }
        dispatchDeepLinkWhenReady(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchDeepLinkWhenReady(intent)
    }

    internal fun navigationSnapshot(): NavStackSetSnapshot {
        return checkNotNull(navController).stackState
    }

    internal fun controllerForTest(): NavHostController = checkNotNull(navController)

    internal fun externalDeepLinkOutcomeForTest(): String = externalDeepLinkOutcome.value

    private fun dispatchDeepLinkWhenReady(
        intent: Intent,
        attempt: Int = 0,
    ) {
        if (intent.action != Intent.ACTION_VIEW || intent.data == null) {
            return
        }
        window.decorView.post {
            val controller = navController
            if (controller == null) {
                if (attempt < MAX_CONTROLLER_WAIT_ATTEMPTS) {
                    dispatchDeepLinkWhenReady(intent, attempt + 1)
                } else {
                    externalDeepLinkOutcome.value = "外部 Deep Link 失败：NavHost 尚未就绪"
                }
                return@post
            }
            externalDeepLinkOutcome.value = controller
                .navigateDeepLink(intent)
                .toDemoDescription(prefix = "外部")
        }
    }

    private companion object {
        const val MAX_CONTROLLER_WAIT_ATTEMPTS = 10
        const val EXTRA_RENDER_DIAGNOSTICS = "system_navigation_render_diagnostics"
    }
}

internal fun NavDeepLinkResult.toDemoDescription(prefix: String = "页内"): String {
    return when (this) {
        is NavDeepLinkResult.Navigated -> {
            "$prefix Deep Link：${match.deepLink.uriPattern} → " +
                "${match.route} · ${navigationResult.toDemoDescription()}"
        }

        NavDeepLinkResult.NoMatch -> "$prefix Deep Link：未匹配，导航状态未改变"
        is NavDeepLinkResult.Rejected -> {
            "$prefix Deep Link：已拒绝 ${rejection.reason}" +
                (rejection.argumentName?.let { "（参数 $it）" } ?: "")
        }

        NavDeepLinkResult.Unsupported -> "$prefix Deep Link：当前控制器未安装图"
    }
}
