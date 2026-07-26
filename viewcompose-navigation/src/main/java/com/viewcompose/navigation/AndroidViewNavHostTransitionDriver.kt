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

    private fun resetProperties(view: View) {
        view.alpha = 1f
        view.translationX = 0f
    }
}

internal fun navTransitionDirection(
    command: NavCommand,
    layoutDirection: Int,
): Float {
    val layoutMultiplier = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f
    val commandMultiplier = if (command == NavCommand.Pop) -1f else 1f
    return layoutMultiplier * commandMultiplier
}
