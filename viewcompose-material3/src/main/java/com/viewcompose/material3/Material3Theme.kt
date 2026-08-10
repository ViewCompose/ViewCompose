package com.viewcompose.material3

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.DesignSystemAttributionProvider
import com.viewcompose.ui.foundation.ProvideLocal
import com.viewcompose.ui.foundation.UiComponentAttribution
import com.viewcompose.ui.foundation.UiComponentBackend
import com.viewcompose.ui.foundation.UiDesignConformance
import com.viewcompose.ui.foundation.UiDesignSystemAttribution
import com.viewcompose.ui.foundation.UiIntegrationAttribution
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiThemeTokens
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.uiLocalOf
import com.viewcompose.ui.foundation.remember
import java.lang.ref.WeakReference

/**
 * Invalidates active Material 3 theme providers after an imperative Android theme mutation.
 *
 * Create one controller for each host that changes theme resources without an Android
 * configuration change. [refresh] is main-thread confined and affects only currently active
 * [Material3Theme] providers that use this controller.
 *
 * @sample com.viewcompose.material3.samples.material3ThemeRefreshSample
 */
class Material3ThemeRefreshController {
    private val listeners = linkedSetOf<() -> Unit>()

    /**
     * Requests every active provider to reread its Android theme on the main thread.
     *
     * The refresh completes synchronously. Calling it when no matching provider is active has no
     * effect.
     *
     * @throws IllegalStateException when called off the Android main thread
     */
    fun refresh() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Material3ThemeRefreshController.refresh() must be called on the main thread."
        }
        listeners.toList().forEach { listener -> listener() }
    }

    internal fun subscribe(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }
}

/**
 * Resolves Material 3 Android theme resources and provides the resulting framework tokens.
 *
 * The resolved context must also be used to create the tree's Views and overlays so native and
 * declarative surfaces observe the same dynamic-color and configuration state.
 *
 * The provider registers for Android configuration changes while it is composed and unregisters
 * when disposed. An optional refresh controller supports imperative theme mutations.
 *
 * @sample com.viewcompose.material3.samples.material3ThemeSample
 * @receiver builder receiving the scoped Material 3 tokens
 * @param resolvedTheme resolved Android theme whose stable context creates the tree's Views
 * @param refreshController optional host controller for theme changes that do not recreate or
 * dispatch a configuration change
 * @param content declarative subtree that reads the resolved framework theme tokens
 */
fun UiTreeBuilder.Material3Theme(
    resolvedTheme: Material3ResolvedTheme,
    refreshController: Material3ThemeRefreshController? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    val lifecycle = remember(resolvedTheme) {
        Material3ThemeTokenLifecycle(resolvedTheme)
    }
    DisposableEffect(lifecycle, refreshController) {
        lifecycle.start()
        val unsubscribe = refreshController?.subscribe(lifecycle::refresh)
        val disposeEffect: () -> Unit = {
            unsubscribe?.invoke()
            lifecycle.close()
        }
        disposeEffect
    }
    provideMaterial3Snapshot(tokens = lifecycle.tokens.value, content = content)
}

/**
 * Provides a static Material 3 token, recipe, and diagnostic snapshot without Android resources.
 *
 * Use the resolved-theme overload or `viewcompose-material3-android` when Android XML or dynamic
 * color should participate. This overload is suitable for previews, deterministic screenshots,
 * and products that supply a copied Material token snapshot.
 *
 * @sample com.viewcompose.material3.samples.material3ComponentsSample
 * @receiver builder receiving the scoped Material 3 snapshot
 * @param tokens immutable Material semantic values used to derive component recipes
 * @param content subtree built synchronously under the same tokens, recipes, and attribution
 */
fun UiTreeBuilder.Material3Theme(
    tokens: UiThemeTokens = Material3ThemeDefaults.light(),
    content: UiTreeBuilder.() -> Unit,
) {
    provideMaterial3Snapshot(tokens = tokens, content = content)
}

private val LocalMaterial3Recipes = uiLocalOf<Material3Recipes?>(
    debugName = "Material3Recipes",
    debugValueFormatter = { recipes -> if (recipes == null) "missing" else Material3Reference.recipeSet },
    defaultFactory = { null },
)

private fun UiTreeBuilder.provideMaterial3Snapshot(
    tokens: UiThemeTokens,
    content: UiTreeBuilder.() -> Unit,
) {
    UiTheme(tokens) {
        DesignSystemAttributionProvider(Material3Attribution) {
            ProvideLocal(LocalMaterial3Recipes, Material3Recipes.from(tokens), content)
        }
    }
}

internal fun material3Recipes(): Material3Recipes = checkNotNull(
    com.viewcompose.ui.foundation.UiLocals.current(LocalMaterial3Recipes),
) {
    "Material 3 components require an active Material3Theme provider."
}

private val Material3Attribution = UiDesignSystemAttribution(
    designSystemId = Material3Reference.designSystem,
    recipeSetId = Material3Reference.recipeSet,
    components = listOf(
        UiComponentAttribution(
            familyId = "surface-card",
            recipeId = "material3-surface-card-v1",
            backend = UiComponentBackend.DslComposite,
            conformance = UiDesignConformance.Exact,
            capabilityPath = "basic-surface",
        ),
        UiComponentAttribution(
            familyId = "button",
            recipeId = "material3-button-v1",
            backend = UiComponentBackend.DslComposite,
            conformance = UiDesignConformance.Exact,
            capabilityPath = "basic-button",
        ),
        UiComponentAttribution(
            familyId = "switch",
            recipeId = "material3-switch-native-v1",
            backend = UiComponentBackend.NativeBehavioralCore,
            conformance = UiDesignConformance.Equivalent,
            capabilityPath = "android-switch",
        ),
        UiComponentAttribution(
            familyId = "text-field",
            recipeId = "material3-text-field-v1",
            backend = UiComponentBackend.NativeBehavioralCore,
            conformance = UiDesignConformance.Equivalent,
            capabilityPath = "android-edit-text",
        ),
        UiComponentAttribution(
            familyId = "navigation-bar",
            recipeId = "material3-navigation-bar-v1",
            backend = UiComponentBackend.NeutralCustomView,
            conformance = UiDesignConformance.Equivalent,
            capabilityPath = "renderer-navigation-bar",
        ),
    ),
    integrations = listOf(
        UiIntegrationAttribution(
            capabilityId = "overlay.dialog",
            transportId = "viewcompose-overlay-android/dialog",
            presenterId = "viewcompose-material3/captured-dialog-content",
            conformance = UiDesignConformance.Equivalent,
        ),
        UiIntegrationAttribution(
            capabilityId = "overlay.popup",
            transportId = "viewcompose-overlay-android/popup",
            presenterId = "viewcompose-material3/captured-popup-content",
            conformance = UiDesignConformance.Equivalent,
        ),
        UiIntegrationAttribution(
            capabilityId = "overlay.snackbar",
            transportId = "viewcompose-overlay-android/transient-queue",
            presenterId = "material-components/snackbar",
            conformance = UiDesignConformance.Equivalent,
        ),
        UiIntegrationAttribution(
            capabilityId = "overlay.modal-bottom-sheet",
            transportId = "viewcompose-overlay-android/modal-session",
            presenterId = "material-components/bottom-sheet-dialog",
            conformance = UiDesignConformance.Equivalent,
        ),
        UiIntegrationAttribution(
            capabilityId = "overlay.toast",
            transportId = "viewcompose-overlay-android/transient-queue",
            presenterId = "android.widget.Toast",
            conformance = UiDesignConformance.Degraded,
            fallback = "platform-toast",
        ),
    ),
)

/** Observes Android configuration changes and advances the immutable theme-token revision. */
@Suppress("DEPRECATION")
internal class Material3ThemeTokenLifecycle(
    context: Context,
    private val dynamicColorPolicy: Material3DynamicColorPolicy,
    private val resolvedTheme: Material3ResolvedTheme? = null,
) : ComponentCallbacks {
    private val contextReference = WeakReference(context)
    private val callbackContext = context.applicationContext
    private var started = false
    private var revision = 0L
    val tokens: MutableState<UiThemeTokens> = mutableStateOf(readTokens(context))

    constructor(resolvedTheme: Material3ResolvedTheme) : this(
        context = resolvedTheme.context,
        dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
        resolvedTheme = resolvedTheme,
    )

    fun start() {
        if (started) return
        started = true
        callbackContext.registerComponentCallbacks(this)
    }

    fun close() {
        if (!started) return
        started = false
        callbackContext.unregisterComponentCallbacks(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) = refresh()

    fun refresh() {
        val context = contextReference.get() ?: run {
            close()
            return
        }
        resolvedTheme?.refreshContext()
        revision += 1
        val resolved = resolvedTheme?.let(Material3ThemeBridge::fromResolvedTheme)
            ?: Material3ThemeBridge.fromContext(context, dynamicColorPolicy)
        tokens.value = resolved.copy(metadata = resolved.metadata.copy(revision = revision))
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() = Unit

    private fun readTokens(context: Context): UiThemeTokens {
        return resolvedTheme?.let(Material3ThemeBridge::fromResolvedTheme)
            ?: Material3ThemeBridge.fromContext(context, dynamicColorPolicy)
    }
}
