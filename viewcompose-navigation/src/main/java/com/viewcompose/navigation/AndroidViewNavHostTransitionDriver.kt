package com.viewcompose.navigation

import android.view.View
import android.view.animation.DecelerateInterpolator
import com.viewcompose.navigation.core.NavCommand

internal class AndroidViewNavHostTransitionDriver(
    private val sessionStore: NavDestinationSessionStore,
    private val specProvider: () -> NavTransitionSpec,
) : NavHostTransitionDriver {
    override fun start(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle {
        val outgoing = checkNotNull(
            sessionStore.sessionOrNull(transition.outgoingEntry.id),
        ) {
            "Outgoing destination ${transition.outgoingEntry.id} has no committed View."
        }.container
        val incoming = checkNotNull(
            sessionStore.sessionOrNull(transition.incomingEntry.id),
        ) {
            "Incoming destination ${transition.incomingEntry.id} has no committed View."
        }.container
        val spec = specProvider()
        val hostWidth = sessionStore.hostView.width
        if (
            spec.durationMillis == 0L ||
            (spec.travelFraction == 0f && !spec.fadeEnabled) ||
            !sessionStore.hostView.isLaidOut ||
            !sessionStore.hostView.isAttachedToWindow ||
            hostWidth <= 0
        ) {
            resetProperties(outgoing)
            resetProperties(incoming)
            onCompleted()
            return NavHostTransitionHandle {}
        }

        outgoing.animate().cancel()
        incoming.animate().cancel()
        resetProperties(outgoing)
        resetProperties(incoming)

        val direction = navTransitionDirection(
            command = transition.command,
            layoutDirection = sessionStore.hostView.layoutDirection,
        )
        val travel = hostWidth * spec.travelFraction
        incoming.translationX = direction * travel
        if (spec.fadeEnabled) {
            incoming.alpha = 0f
        }

        var terminal = false
        val finish = Runnable {
            if (!terminal) {
                terminal = true
                resetProperties(outgoing)
                resetProperties(incoming)
                onCompleted()
            }
        }
        val interpolator = DecelerateInterpolator()
        try {
            outgoing.animate()
                .translationX(-direction * travel)
                .alpha(if (spec.fadeEnabled) 0f else 1f)
                .setDuration(spec.durationMillis)
                .setInterpolator(interpolator)
                .start()
            incoming.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(spec.durationMillis)
                .setInterpolator(interpolator)
                .withEndAction(finish)
                .start()
        } catch (throwable: Throwable) {
            terminal = true
            outgoing.animate().cancel()
            incoming.animate().cancel()
            resetProperties(outgoing)
            resetProperties(incoming)
            throw throwable
        }

        return NavHostTransitionHandle {
            if (!terminal) {
                terminal = true
                outgoing.animate().cancel()
                incoming.animate().cancel()
                resetProperties(outgoing)
                resetProperties(incoming)
            }
        }
    }

    override fun startBackPreview(
        preview: NavHostBackPreview,
        initialEvent: NavHostBackEvent,
    ): NavHostBackPreviewHandle {
        val outgoing = checkNotNull(
            sessionStore.sessionOrNull(preview.outgoingEntry.id),
        ) {
            "Back-preview outgoing destination ${preview.outgoingEntry.id} has no committed View."
        }.container
        val incoming = checkNotNull(
            sessionStore.sessionOrNull(preview.incomingEntry.id),
        ) {
            "Back-preview incoming destination ${preview.incomingEntry.id} has no committed View."
        }.container
        outgoing.animate().cancel()
        incoming.animate().cancel()
        resetProperties(outgoing)
        resetProperties(incoming)
        return AndroidBackPreviewHandle(
            preview = preview,
            outgoing = outgoing,
            incoming = incoming,
            spec = specProvider(),
            hostWidth = sessionStore.hostView.width,
            layoutDirection = sessionStore.hostView.layoutDirection,
            canAnimate = sessionStore.hostView.isLaidOut &&
                sessionStore.hostView.isAttachedToWindow &&
                sessionStore.hostView.width > 0,
        ).also { handle ->
            handle.update(initialEvent)
        }
    }

    private fun resetProperties(view: View) {
        view.alpha = 1f
        view.translationX = 0f
    }
}

private class AndroidBackPreviewHandle(
    private val preview: NavHostBackPreview,
    private val outgoing: View,
    private val incoming: View,
    private val spec: NavTransitionSpec,
    private val hostWidth: Int,
    private val layoutDirection: Int,
    private val canAnimate: Boolean,
) : NavHostBackPreviewHandle {
    private var latestEvent = NavHostBackEvent(
        touchX = 0f,
        touchY = 0f,
        progress = 0f,
        swipeEdge = NavHostBackSwipeEdge.None,
        frameTimeMillis = 0L,
    )
    private var terminal = false
    private var committing = false

    override fun update(event: NavHostBackEvent) {
        if (terminal || committing) {
            return
        }
        latestEvent = event
        if (!canAnimate) {
            return
        }
        applyProgress(event)
    }

    override fun cancel() {
        if (terminal) {
            return
        }
        terminal = true
        outgoing.animate().cancel()
        incoming.animate().cancel()
        reset()
    }

    override fun commit(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle {
        check(!terminal && !committing) {
            "A predictive-back preview can be committed only once."
        }
        check(
            transition.command == preview.command &&
                transition.before == preview.snapshot &&
                transition.outgoingEntry == preview.outgoingEntry &&
                transition.incomingEntry == preview.incomingEntry,
        ) {
            "Predictive-back preview does not match the committed pop transition."
        }
        committing = true
        val remainingFraction = 1f - latestEvent.progress
        if (
            !canAnimate ||
            spec.durationMillis == 0L ||
            (spec.travelFraction == 0f && !spec.fadeEnabled) ||
            remainingFraction <= 0f
        ) {
            terminal = true
            reset()
            onCompleted()
            return NavHostTransitionHandle {}
        }

        val direction = backPreviewOutgoingDirection(
            swipeEdge = latestEvent.swipeEdge,
            layoutDirection = layoutDirection,
        )
        val travel = hostWidth * spec.travelFraction
        val duration = (spec.durationMillis * remainingFraction)
            .toLong()
            .coerceAtLeast(1L)
        val interpolator = DecelerateInterpolator()
        val finish = Runnable {
            if (!terminal) {
                terminal = true
                reset()
                onCompleted()
            }
        }
        try {
            outgoing.animate()
                .translationX(direction * travel)
                .alpha(if (spec.fadeEnabled) 0f else 1f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .start()
            incoming.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction(finish)
                .start()
        } catch (throwable: Throwable) {
            terminal = true
            outgoing.animate().cancel()
            incoming.animate().cancel()
            reset()
            throw throwable
        }
        return NavHostTransitionHandle {
            if (!terminal) {
                terminal = true
                outgoing.animate().cancel()
                incoming.animate().cancel()
                reset()
            }
        }
    }

    private fun applyProgress(event: NavHostBackEvent) {
        val direction = backPreviewOutgoingDirection(
            swipeEdge = event.swipeEdge,
            layoutDirection = layoutDirection,
        )
        val travel = hostWidth * spec.travelFraction
        outgoing.translationX = direction * travel * event.progress
        incoming.translationX = -direction * travel * (1f - event.progress)
        if (spec.fadeEnabled) {
            outgoing.alpha = 1f - event.progress
            incoming.alpha = event.progress
        }
    }

    private fun reset() {
        outgoing.alpha = 1f
        outgoing.translationX = 0f
        incoming.alpha = 1f
        incoming.translationX = 0f
    }
}

internal fun navTransitionDirection(
    command: NavCommand,
    layoutDirection: Int,
): Float {
    val layoutMultiplier = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f
    val commandMultiplier = when (command) {
        NavCommand.Pop,
        NavCommand.PopStackHistory,
        -> -1f

        is NavCommand.Push,
        is NavCommand.ReplaceTop,
        is NavCommand.Reset,
        is NavCommand.SelectStack,
        -> 1f
    }
    return layoutMultiplier * commandMultiplier
}

internal fun backPreviewOutgoingDirection(
    swipeEdge: NavHostBackSwipeEdge,
    layoutDirection: Int,
): Float {
    return when (swipeEdge) {
        NavHostBackSwipeEdge.Left -> 1f
        NavHostBackSwipeEdge.Right -> -1f
        NavHostBackSwipeEdge.None -> {
            -navTransitionDirection(
                command = NavCommand.Pop,
                layoutDirection = layoutDirection,
            )
        }
    }
}
