package com.viewcompose.material3.android

import android.content.Context
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import com.viewcompose.android.setUiContent as setNeutralUiContent
import com.viewcompose.material3.Material3DynamicColorPolicy
import com.viewcompose.material3.Material3Theme
import com.viewcompose.material3.Material3ThemeBridge
import com.viewcompose.host.android.resources.AndroidResourceRefreshController
import com.viewcompose.overlay.material3.android.host.AndroidOverlayHost as Material3AndroidOverlayHost
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.foundation.RenderFailure
import com.viewcompose.ui.foundation.RenderStats
import com.viewcompose.ui.foundation.RenderTreeResult
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * Creates a Fragment ViewCompose root with Android Material 3 context and token integration.
 *
 * The resolved Material context creates the root, every native descendant, and default overlays;
 * [Material3Theme] provides tokens from that same resolution. Return the result from
 * `onCreateView`. Repeating the call reconstructs the root and disposes the previous session.
 *
 * @sample com.viewcompose.material3.android.samples.material3FragmentHostSample
 * @param debug enables render diagnostics and logging
 * @param debugTag log tag used by debug rendering
 * @param dynamicColorPolicy policy selecting Android dynamic-color context resolution
 * @param rootContext source Context used to resolve the stable Material root Context
 * @param resourceRefreshController optional unified controller for imperative resource or theme
 * mutations that do not dispatch a configuration change
 * @param overlayHostFactory creates the overlay host for the resolved Material root
 * @param onRenderStats optional callback after every attempted frame
 * @param onRenderResult optional callback for collected render diagnostics
 * @param onRenderFailure optional callback when a frame fails
 * @param content declarative content; its ViewGroup argument is the returned root
 * @return the newly created full-size Fragment root
 * @throws IllegalStateException when the Fragment lifecycle is already destroyed
 */
fun Fragment.setMaterial3UiContent(
    debug: Boolean = false,
    debugTag: String = "ViewCompose",
    dynamicColorPolicy: Material3DynamicColorPolicy = Material3DynamicColorPolicy.UseIfAvailable,
    rootContext: Context = requireContext(),
    resourceRefreshController: AndroidResourceRefreshController? = null,
    overlayHostFactory: (ViewGroup) -> OverlayHost = { root -> Material3AndroidOverlayHost(root) },
    onRenderStats: ((RenderStats) -> Unit)? = null,
    onRenderResult: ((RenderTreeResult) -> Unit)? = null,
    onRenderFailure: ((RenderFailure) -> Unit)? = null,
    content: UiTreeBuilder.(ViewGroup) -> Unit,
): ViewGroup {
    val resolvedTheme = Material3ThemeBridge.resolveContext(
        context = rootContext,
        dynamicColorPolicy = dynamicColorPolicy,
    )
    return setNeutralUiContent(
        rootContext = resolvedTheme.context,
        resourceRefreshController = resourceRefreshController,
        onBeforeResourceRefresh = resolvedTheme::refresh,
        debug = debug,
        debugTag = debugTag,
        overlayHostFactory = overlayHostFactory,
        onRenderStats = onRenderStats,
        onRenderResult = onRenderResult,
        onRenderFailure = onRenderFailure,
    ) { root ->
        Material3Theme(
            resolvedTheme = resolvedTheme,
        ) {
            content(root)
        }
    }
}

/**
 * Installs a ViewCompose Activity root with Android Material 3 context and token integration.
 *
 * The resolved Material context creates the root, every native descendant, and default overlays;
 * [Material3Theme] provides tokens from that same resolution. Repeating the call reconstructs the
 * root and disposes the previous session.
 *
 * @sample com.viewcompose.material3.android.samples.material3ActivityHostSample
 * @param debug enables render diagnostics and logging
 * @param debugTag log tag used by debug rendering
 * @param dynamicColorPolicy policy selecting Android dynamic-color context resolution
 * @param rootContext source Context used to resolve the stable Material root Context
 * @param resourceRefreshController optional unified controller for imperative resource or theme
 * mutations that do not dispatch a configuration change
 * @param overlayHostFactory creates the overlay host for the resolved Material root
 * @param onRenderStats optional callback after every attempted frame
 * @param onRenderResult optional callback for collected render diagnostics
 * @param onRenderFailure optional callback when a frame fails
 * @param content declarative content; its ViewGroup argument is the installed root
 * @return the newly installed full-size Activity root
 * @throws IllegalStateException when the Activity lifecycle is already destroyed
 */
fun ComponentActivity.setMaterial3UiContent(
    debug: Boolean = false,
    debugTag: String = "ViewCompose",
    dynamicColorPolicy: Material3DynamicColorPolicy = Material3DynamicColorPolicy.UseIfAvailable,
    rootContext: Context = this,
    resourceRefreshController: AndroidResourceRefreshController? = null,
    overlayHostFactory: (ViewGroup) -> OverlayHost = { root -> Material3AndroidOverlayHost(root) },
    onRenderStats: ((RenderStats) -> Unit)? = null,
    onRenderResult: ((RenderTreeResult) -> Unit)? = null,
    onRenderFailure: ((RenderFailure) -> Unit)? = null,
    content: UiTreeBuilder.(ViewGroup) -> Unit,
): ViewGroup {
    val resolvedTheme = Material3ThemeBridge.resolveContext(
        context = rootContext,
        dynamicColorPolicy = dynamicColorPolicy,
    )
    return setNeutralUiContent(
        rootContext = resolvedTheme.context,
        resourceRefreshController = resourceRefreshController,
        onBeforeResourceRefresh = resolvedTheme::refresh,
        debug = debug,
        debugTag = debugTag,
        overlayHostFactory = overlayHostFactory,
        onRenderStats = onRenderStats,
        onRenderResult = onRenderResult,
        onRenderFailure = onRenderFailure,
    ) { root ->
        Material3Theme(
            resolvedTheme = resolvedTheme,
        ) {
            content(root)
        }
    }
}
