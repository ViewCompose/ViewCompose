package com.viewcompose.navigation

import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.ExperimentalActivityApi
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.LifecycleOwner

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
    private var dispatcherOwner: OnBackPressedDispatcherOwner? = null
    private var activePreviewId: NavHostBackPreviewId? = null
    private var systemBackEnabled = true
    private var attached = false
    private var destroyed = false
    private val callback = object : OnBackPressedCallback(false) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            activePreviewId?.let(onBackCancelled)
            activePreviewId = onBackStarted(backEvent.toNavHostBackEvent())
            syncEnabled()
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            activePreviewId?.let { previewId ->
                onBackProgressed(
                    previewId,
                    backEvent.toNavHostBackEvent(),
                )
            }
        }

        override fun handleOnBackCancelled() {
            val previewId = activePreviewId ?: return
            activePreviewId = null
            try {
                onBackCancelled(previewId)
            } finally {
                syncEnabled()
            }
        }

        override fun handleOnBackPressed() {
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
    }

    fun attach(owner: LifecycleOwner) {
        check(!destroyed) {
            "A destroyed navigation back adapter cannot attach."
        }
        if (attached && lifecycleOwner === owner) {
            registerWithViewTreeOwner()
            syncEnabled()
            return
        }
        callback.remove()
        lifecycleOwner = owner
        dispatcherOwner = null
        if (!attached) {
            attached = true
            hostView.addOnAttachStateChangeListener(this)
        }
        registerWithViewTreeOwner()
        syncEnabled()
    }

    fun updateEnabled(enabled: Boolean) {
        if (destroyed) {
            return
        }
        systemBackEnabled = enabled
        if (!enabled) {
            activePreviewId?.let { previewId ->
                activePreviewId = null
                onBackCancelled(previewId)
            }
        }
        syncEnabled()
    }

    fun onNavigationStateChanged() {
        val previewId = activePreviewId
        if (previewId != null && !isPreviewActive(previewId)) {
            activePreviewId = null
        }
        syncEnabled()
    }

    fun destroy() {
        if (destroyed) {
            return
        }
        destroyed = true
        activePreviewId = null
        callback.isEnabled = false
        callback.remove()
        dispatcherOwner = null
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
        val previewId = activePreviewId
        activePreviewId = null
        try {
            if (previewId != null && isPreviewActive(previewId)) {
                onBackCancelled(previewId)
            }
        } finally {
            callback.remove()
            dispatcherOwner = null
        }
    }

    private fun registerWithViewTreeOwner() {
        if (destroyed) {
            return
        }
        val owner = hostView.findViewTreeOnBackPressedDispatcherOwner()
        if (owner === dispatcherOwner) {
            return
        }
        callback.remove()
        dispatcherOwner = owner
        val currentLifecycleOwner = lifecycleOwner
        if (owner != null && currentLifecycleOwner != null) {
            owner.onBackPressedDispatcher.addCallback(
                owner = currentLifecycleOwner,
                onBackPressedCallback = callback,
            )
        }
    }

    private fun syncEnabled() {
        callback.isEnabled = !destroyed &&
            systemBackEnabled &&
            canHandleBack()
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
