package com.viewcompose.host.android.resources

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Looper
import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.foundation.DisposableEffect
import com.viewcompose.ui.foundation.ProvideLocal
import com.viewcompose.ui.foundation.UiEnvironment
import com.viewcompose.ui.foundation.UiLocals
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberUpdatedState
import com.viewcompose.ui.foundation.uiLocalOf
import com.viewcompose.host.android.environment.AndroidEnvironmentBridge

/**
 * Notifies one mounted Android resource environment after an imperative resource mutation.
 *
 * Standard hosts already refresh after Android configuration callbacks. Call [refresh] after a
 * host-scoped mutation such as replacing a stable Context wrapper's base resources when Android
 * does not dispatch a configuration change. Active environments are notified synchronously in
 * registration order; the controller retains no environment after its provider is disposed.
 *
 * Calls are confined to the Android main thread. Create one controller per root host; sharing a
 * controller intentionally refreshes every currently mounted environment using that instance.
 *
 * @sample com.viewcompose.host.android.samples.androidResourceRefreshSample
 */
class AndroidResourceRefreshController {
    private val listeners = linkedSetOf<() -> Unit>()

    /**
     * Requests every active environment using this controller to resolve a new resource snapshot.
     *
     * Calling this method with no active environment has no effect. A listener failure propagates
     * synchronously and stops later listener delivery; an environment publishes no new snapshot
     * when its pre-refresh callback or resource resolution fails.
     *
     * @throws IllegalStateException when called off the Android main thread
     */
    fun refresh() {
        requireAndroidMainThread("AndroidResourceRefreshController.refresh()")
        listeners.toList().forEach { listener -> listener() }
    }

    internal fun subscribe(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }
}

private val LocalAndroidResourceContext = uiLocalOf<Context?>(
    debugName = "AndroidResources.Context",
    debugValueFormatter = { context -> context?.javaClass?.name ?: "missing" },
    defaultFactory = { null },
)

/** Provides access to the Android Context owned by the current resource environment. */
object LocalAndroidContext {
    /**
     * Returns the themed Context used by the current root and its Android resource lookups.
     *
     * Read this value only while synchronously building ViewCompose content. The Context is owned
     * by the mounted host and must not be retained beyond that host's render session.
     *
     * @throws IllegalStateException when no [AndroidResourceEnvironment] provider is active
     */
    val current: Context
        get() = requireAndroidResourceContext()
}

/** Provides access to Resources resolved from the current Android resource Context. */
object LocalAndroidResources {
    /**
     * Returns the current host's live Resources object.
     *
     * Read and consume it synchronously during content construction. Prefer the typed resource
     * functions for common values so resource dependencies remain visible at the call site.
     *
     * @throws IllegalStateException when no [AndroidResourceEnvironment] provider is active
     */
    val current: Resources
        get() = requireAndroidResourceContext().resources
}

/**
 * Provides Android resources and configuration-aware environment values to [content].
 *
 * The provider observes Android configuration callbacks while mounted, resolves one immutable
 * environment snapshot from [context], and advances its resource revision after each callback or
 * [refreshController] request. [onBeforeRefresh] runs first so a stable themed Context wrapper may
 * replace its base before resources, density, locales, and direction are read.
 *
 * When [environmentValues] is non-null, its density, locales, and layout direction remain fixed;
 * only its resource revision advances. This supports deterministic preview hosts whose Android
 * bridge owns those values separately. Standard application hosts leave it `null`.
 *
 * Registration, refresh callbacks, content construction, and disposal are Android-main-thread
 * work. If pre-refresh or resolution fails, the previous snapshot remains active and the exception
 * propagates to the caller that initiated refresh.
 *
 * @sample com.viewcompose.host.android.samples.androidResourceEnvironmentSample
 * @receiver builder receiving the Android resource and environment scope
 * @param context stable themed Context used by root Views, resources, and overlays
 * @param refreshController optional host-scoped imperative refresh source
 * @param environmentValues optional fixed non-revision environment values for deterministic hosts
 * @param observeConfigurationChanges whether to register Android configuration callbacks while
 * mounted
 * @param onBeforeRefresh callback invoked before every non-initial resource resolution
 * @param content declarative subtree that resolves Android resources synchronously
 */
fun UiTreeBuilder.AndroidResourceEnvironment(
    context: Context,
    refreshController: AndroidResourceRefreshController? = null,
    environmentValues: UiEnvironmentValues? = null,
    observeConfigurationChanges: Boolean = true,
    onBeforeRefresh: (() -> Unit)? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    val currentBeforeRefresh = rememberUpdatedState(onBeforeRefresh)
    val lifecycle = remember(context, refreshController, environmentValues, observeConfigurationChanges) {
        AndroidResourceEnvironmentLifecycle(
            context = context,
            refreshController = refreshController,
            fixedEnvironmentValues = environmentValues,
            observeConfigurationChanges = observeConfigurationChanges,
            onBeforeRefresh = { currentBeforeRefresh.value?.invoke() },
        )
    }
    DisposableEffect(lifecycle) {
        lifecycle.start()
        onDispose(lifecycle::close)
    }
    val snapshot = lifecycle.snapshot.value
    ProvideLocal(LocalAndroidResourceContext, context) {
        UiEnvironment(snapshot.environment) {
            content()
        }
    }
}

internal data class AndroidResourceSnapshot(
    val environment: UiEnvironmentValues,
)

@Suppress("DEPRECATION")
internal class AndroidResourceEnvironmentLifecycle(
    private val context: Context,
    private val refreshController: AndroidResourceRefreshController?,
    private val fixedEnvironmentValues: UiEnvironmentValues?,
    private val observeConfigurationChanges: Boolean,
    private val onBeforeRefresh: () -> Unit,
) : ComponentCallbacks {
    private val callbackContext = context.applicationContext ?: context
    private var started = false
    private var unsubscribeRefresh: (() -> Unit)? = null
    private var revision = fixedEnvironmentValues?.resourceRevision ?: 0L

    val snapshot: MutableState<AndroidResourceSnapshot> = mutableStateOf(
        AndroidResourceSnapshot(resolveEnvironment()),
    )

    fun start() {
        requireAndroidMainThread("AndroidResourceEnvironment")
        if (started) return
        started = true
        if (observeConfigurationChanges) {
            callbackContext.registerComponentCallbacks(this)
        }
        unsubscribeRefresh = refreshController?.subscribe(::refresh)
    }

    fun close() {
        requireAndroidMainThread("AndroidResourceEnvironment")
        if (!started) return
        started = false
        unsubscribeRefresh?.invoke()
        unsubscribeRefresh = null
        if (observeConfigurationChanges) {
            callbackContext.unregisterComponentCallbacks(this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = refresh()

    fun refresh() {
        requireAndroidMainThread("AndroidResourceEnvironment refresh")
        onBeforeRefresh()
        val nextRevision = checkNotNull(revision.takeUnless { it == Long.MAX_VALUE }) {
            "Android resource revision overflowed for the mounted host."
        } + 1L
        revision = nextRevision
        snapshot.value = AndroidResourceSnapshot(resolveEnvironment())
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() = Unit

    private fun resolveEnvironment(): UiEnvironmentValues {
        val values = fixedEnvironmentValues ?: AndroidEnvironmentBridge.fromContext(context)
        return values.copy(resourceRevision = revision)
    }
}

internal fun requireAndroidResourceContext(): Context {
    return checkNotNull(UiLocals.current(LocalAndroidResourceContext)) {
        "Android resources require an active AndroidResourceEnvironment. " +
            "Standard setUiContent hosts install it automatically; custom renderInto hosts must " +
            "provide it explicitly."
    }
}

internal fun requireAndroidMainThread(operation: String) {
    check(Looper.myLooper() == Looper.getMainLooper()) {
        "$operation must run on the Android main thread."
    }
}
