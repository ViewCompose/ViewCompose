package com.viewcompose.navigation

import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.ExperimentalActivityApi
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.findViewTreeNavigationEventDispatcherOwner

/**
 * Connects NavHost predictive/system Back to the nearest AndroidX NavigationEvent dispatcher.
 *
 * The Activity Back dispatcher remains a compatibility fallback for custom hosts that do not
 * install a NavigationEvent owner. Both inputs share one preview state machine and are never
 * registered at the same time.
 */
@OptIn(ExperimentalActivityApi::class)
internal class AndroidNavHostBackAdapter(
    private val hostView: NavHostView,
    private val canHandleBack: () -> Boolean,
    private val isPreviewActive: (NavHostBackPreviewId) -> Boolean,
    private val onBackPressed: () -> Unit,
    private val onBackStarted: (NavHostBackEvent) -> NavHostBackPreviewId?,
    private val onBackProgressed: (NavHostBackPreviewId, NavHostBackEvent) -> Unit,
    private val onBackCancelled: (NavHostBackPreviewId) -> Unit,
    private val onBackCommitted: (NavHostBackPreviewId) -> Unit,
) : View.OnAttachStateChangeListener {
    private var lifecycleOwner: LifecycleOwner? = null
    private var navigationEventOwner: NavigationEventDispatcherOwner? = null
    private var navigationEventHandlerRegistered = false
    private var backPressedDispatcherOwner: OnBackPressedDispatcherOwner? = null
    private var activePreviewId: NavHostBackPreviewId? = null
    private var ignorePendingBackTerminal = false
    private var systemBackEnabled = true
    private var attached = false
    private var destroyed = false
    private val lifecycleObserver = LifecycleEventObserver { _, _ ->
        if (!isLifecycleStarted()) {
            cancelActivePreview()
        }
        registerWithViewTreeOwner()
        syncEnabled()
    }
    private val navigationEventHandler = object :
        NavigationEventHandler<NavigationEventInfo.None>(
            initialInfo = NavigationEventInfo.None,
            isBackEnabled = false,
        ) {
        override fun onBackStarted(event: NavigationEvent) {
            handleBackStarted(event.toNavHostBackEvent())
        }

        override fun onBackProgressed(event: NavigationEvent) {
            handleBackProgressed(event.toNavHostBackEvent())
        }

        override fun onBackCancelled() {
            handleBackCancelled()
        }

        override fun onBackCompleted() {
            handleBackCompleted()
        }
    }
    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            handleBackStarted(backEvent.toNavHostBackEvent())
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            handleBackProgressed(backEvent.toNavHostBackEvent())
        }

        override fun handleOnBackCancelled() {
            handleBackCancelled()
        }

        override fun handleOnBackPressed() {
            handleBackCompleted()
        }
    }

    fun attach(owner: LifecycleOwner) {
        check(!destroyed) {
            "A destroyed navigation back adapter cannot attach."
        }
        if (attached && lifecycleOwner === owner) {
            // The view-tree dispatcher owner can change after window reattach, so re-query it.
            registerWithViewTreeOwner()
            syncEnabled()
            return
        }
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        cancelActivePreview()
        unregisterNavigationEventHandler()
        backPressedCallback.remove()
        lifecycleOwner = owner
        navigationEventOwner = null
        backPressedDispatcherOwner = null
        if (!attached) {
            attached = true
            hostView.addOnAttachStateChangeListener(this)
        }
        owner.lifecycle.addObserver(lifecycleObserver)
        registerWithViewTreeOwner()
        syncEnabled()
    }

    fun updateEnabled(enabled: Boolean) {
        if (destroyed) {
            return
        }
        systemBackEnabled = enabled
        if (!enabled) {
            // Disabling system back must cancel an active preview so visuals do not remain mid-progress.
            cancelActivePreview()
        }
        syncEnabled()
    }

    fun onNavigationStateChanged() {
        val previewId = activePreviewId
        if (previewId != null && !isPreviewActive(previewId)) {
            activePreviewId = null
            ignorePendingBackTerminal = true
        }
        syncEnabled()
    }

    fun destroy() {
        if (destroyed) {
            return
        }
        destroyed = true
        cancelActivePreview()
        navigationEventHandler.isBackEnabled = false
        backPressedCallback.isEnabled = false
        unregisterNavigationEventHandler()
        backPressedCallback.remove()
        navigationEventOwner = null
        backPressedDispatcherOwner = null
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = null
        if (attached) {
            attached = false
            hostView.removeOnAttachStateChangeListener(this)
        }
    }

    override fun onViewAttachedToWindow(view: View) {
        registerWithViewTreeOwner()
        syncEnabled()
    }

    override fun onViewDetachedFromWindow(view: View) {
        // After detach no dispatcher owns gesture callbacks, so terminate the preview explicitly.
        cancelActivePreview()
        unregisterNavigationEventHandler()
        backPressedCallback.remove()
        navigationEventOwner = null
        backPressedDispatcherOwner = null
    }

    private fun registerWithViewTreeOwner() {
        if (destroyed) {
            return
        }
        val directOwner = hostView.findViewTreeNavigationEventDispatcherOwner()
        if (directOwner != null) {
            if (directOwner !== navigationEventOwner || backPressedDispatcherOwner != null) {
                cancelActivePreview()
                unregisterNavigationEventHandler()
                backPressedCallback.remove()
                navigationEventOwner = directOwner
                backPressedDispatcherOwner = null
            }
            if (isLifecycleStarted() && !navigationEventHandlerRegistered) {
                directOwner.navigationEventDispatcher.addHandler(navigationEventHandler)
                navigationEventHandlerRegistered = true
            } else if (!isLifecycleStarted()) {
                unregisterNavigationEventHandler()
            }
            return
        }

        if (navigationEventOwner != null || navigationEventHandlerRegistered) {
            cancelActivePreview()
            unregisterNavigationEventHandler()
            navigationEventOwner = null
        }
        val owner = hostView.findViewTreeOnBackPressedDispatcherOwner()
        if (owner === backPressedDispatcherOwner) {
            return
        }
        backPressedCallback.remove()
        backPressedDispatcherOwner = owner
        val currentLifecycleOwner = lifecycleOwner
        if (owner != null && currentLifecycleOwner != null) {
            owner.onBackPressedDispatcher.addCallback(
                owner = currentLifecycleOwner,
                onBackPressedCallback = backPressedCallback,
            )
        }
    }

    private fun syncEnabled() {
        val enabled = !destroyed &&
            isLifecycleStarted() &&
            systemBackEnabled &&
            canHandleBack()
        navigationEventHandler.isBackEnabled = enabled
        backPressedCallback.isEnabled = enabled
    }

    private fun unregisterNavigationEventHandler() {
        if (!navigationEventHandlerRegistered) {
            return
        }
        navigationEventHandlerRegistered = false
        navigationEventHandler.remove()
    }

    private fun isLifecycleStarted(): Boolean {
        return lifecycleOwner
            ?.lifecycle
            ?.currentState
            ?.isAtLeast(Lifecycle.State.STARTED) == true
    }

    private fun handleBackStarted(event: NavHostBackEvent) {
        // Inputs may begin a new gesture before terminating the old one; preserve one preview.
        cancelActivePreview()
        ignorePendingBackTerminal = false
        activePreviewId = onBackStarted(event)
        syncEnabled()
    }

    private fun handleBackProgressed(event: NavHostBackEvent) {
        activePreviewId?.let { previewId ->
            onBackProgressed(previewId, event)
        }
    }

    private fun handleBackCancelled() {
        if (ignorePendingBackTerminal) {
            ignorePendingBackTerminal = false
            syncEnabled()
            return
        }
        val previewId = activePreviewId ?: return
        activePreviewId = null
        try {
            onBackCancelled(previewId)
        } finally {
            syncEnabled()
        }
    }

    private fun handleBackCompleted() {
        if (ignorePendingBackTerminal) {
            ignorePendingBackTerminal = false
            onNavigationStateChanged()
            return
        }
        val previewId = activePreviewId
        activePreviewId = null
        try {
            if (previewId == null) {
                onBackPressed()
            } else {
                onBackCommitted(previewId)
            }
        } finally {
            onNavigationStateChanged()
        }
    }

    private fun cancelActivePreview() {
        val previewId = activePreviewId ?: return
        activePreviewId = null
        // The input owns its terminal callback and may deliver it after host-side cancellation.
        ignorePendingBackTerminal = true
        if (isPreviewActive(previewId)) {
            onBackCancelled(previewId)
        }
    }
}

private fun BackEventCompat.toNavHostBackEvent(): NavHostBackEvent {
    return NavHostBackEvent(
        touchX = touchX,
        touchY = touchY,
        progress = progress
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: 0f,
        swipeEdge = when (swipeEdge) {
            BackEventCompat.EDGE_LEFT -> NavHostBackSwipeEdge.Left
            BackEventCompat.EDGE_RIGHT -> NavHostBackSwipeEdge.Right
            else -> NavHostBackSwipeEdge.None
        },
        frameTimeMillis = frameTimeMillis,
    )
}

private fun NavigationEvent.toNavHostBackEvent(): NavHostBackEvent {
    return NavHostBackEvent(
        touchX = touchX,
        touchY = touchY,
        progress = progress
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: 0f,
        swipeEdge = when (swipeEdge) {
            NavigationEvent.EDGE_LEFT -> NavHostBackSwipeEdge.Left
            NavigationEvent.EDGE_RIGHT -> NavHostBackSwipeEdge.Right
            else -> NavHostBackSwipeEdge.None
        },
        frameTimeMillis = frameTimeMillis,
    )
}
