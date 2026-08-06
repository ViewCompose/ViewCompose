package com.viewcompose.host.android.animation

import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.transition.Transition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.viewcompose.host.android.AndroidView
import com.viewcompose.host.android.nativeView
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * Starts Android platform animations for Views mounted in a ViewCompose tree.
 *
 * Every start function begins the returned animation before returning it. The caller owns that
 * animation and may cancel it or add listeners. These helpers do not participate in ViewCompose
 * state or render transactions; use them only for deliberately platform-specific effects.
 */
object AndroidAnimationInterop {
    /**
     * Begins a delayed platform transition on [targetView] or its nearest parent container.
     *
     * @param targetView container to transition, or a child whose direct parent is transitioned
     * @param transition optional platform transition; `null` selects the platform default
     * @return `true` when a scene root was found and the transition was scheduled
     */
    fun beginDelayedTransition(
        targetView: View,
        transition: Transition? = null,
    ): Boolean {
        val sceneRoot = when (targetView) {
            is ViewGroup -> targetView
            else -> targetView.parent as? ViewGroup
        } ?: return false
        if (transition == null) {
            TransitionManager.beginDelayedTransition(sceneRoot)
        } else {
            TransitionManager.beginDelayedTransition(sceneRoot, transition)
        }
        return true
    }

    /**
     * Creates and immediately starts an [ObjectAnimator] for a float View property.
     *
     * [onEnd] runs for both normal completion and cancellation, following platform animator
     * semantics. The caller remains responsible for cancelling the returned animator when its
     * owning lifecycle ends.
     *
     * @param target View whose property is animated
     * @param propertyName platform property name understood by [ObjectAnimator]
     * @param values ordered property values; platform validation applies
     * @param durationMillis animation duration in milliseconds
     * @param startDelayMillis delay before the animation starts
     * @param interpolator optional platform timing interpolator
     * @param onEnd optional callback invoked when the animator ends
     * @return the already-started platform animator
     */
    fun startObjectAnimator(
        target: View,
        propertyName: String,
        vararg values: Float,
        durationMillis: Long = 300L,
        startDelayMillis: Long = 0L,
        interpolator: TimeInterpolator? = null,
        onEnd: (() -> Unit)? = null,
    ): ObjectAnimator {
        return ObjectAnimator.ofFloat(target, propertyName, *values).apply {
            duration = durationMillis
            startDelay = startDelayMillis
            interpolator?.let { this.interpolator = it }
            if (onEnd != null) {
                doOnAnimationEnd(onEnd)
            }
            start()
        }
    }

    /**
     * Creates and immediately starts a float [ValueAnimator].
     *
     * Updates and completion are delivered on the thread that owns the animator, normally the main
     * thread. The caller must cancel the returned animator when its lifecycle ends.
     *
     * @param from initial animated value
     * @param to final animated value
     * @param durationMillis animation duration in milliseconds
     * @param startDelayMillis delay before the animation starts
     * @param interpolator optional platform timing interpolator
     * @param onUpdate callback for every platform animation update
     * @param onEnd optional callback invoked when the animator ends
     * @return the already-started platform animator
     */
    fun startValueAnimator(
        from: Float,
        to: Float,
        durationMillis: Long = 300L,
        startDelayMillis: Long = 0L,
        interpolator: TimeInterpolator? = null,
        onUpdate: (Float) -> Unit,
        onEnd: (() -> Unit)? = null,
    ): ValueAnimator {
        return ValueAnimator.ofFloat(from, to).apply {
            duration = durationMillis
            startDelay = startDelayMillis
            interpolator?.let { this.interpolator = it }
            addUpdateListener { animator ->
                onUpdate((animator.animatedValue as? Float) ?: from)
            }
            if (onEnd != null) {
                doOnAnimationEnd(onEnd)
            }
            start()
        }
    }

    /**
     * Configures and immediately starts [target]'s reusable [ViewPropertyAnimator].
     *
     * Invoking this while another property animation is active follows Android's ordinary
     * replacement rules. [configure] runs synchronously before the animation starts.
     *
     * @param target View whose properties are animated
     * @param durationMillis animation duration in milliseconds
     * @param startDelayMillis delay before the animation starts
     * @param interpolator optional platform timing interpolator
     * @param configure receiver block that selects target properties and values
     * @param onEnd optional action invoked by the platform end action
     * @return the target's already-started property animator
     */
    fun startViewPropertyAnimator(
        target: View,
        durationMillis: Long = 300L,
        startDelayMillis: Long = 0L,
        interpolator: TimeInterpolator? = null,
        configure: ViewPropertyAnimator.() -> Unit,
        onEnd: (() -> Unit)? = null,
    ): ViewPropertyAnimator {
        return target.animate().apply {
            duration = durationMillis
            startDelay = startDelayMillis
            interpolator?.let { this.interpolator = it }
            configure()
            if (onEnd != null) {
                withEndAction(onEnd)
            }
            start()
        }
    }

    /**
     * Creates and immediately starts a spring animation for one View property.
     *
     * [stiffness] must be positive and [dampingRatio] non-negative as required by [SpringForce].
     * The returned animation is independent of ViewCompose animation state.
     *
     * @param target View whose property is animated
     * @param property dynamic-animation property to update
     * @param finalPosition spring equilibrium value
     * @param startVelocity initial velocity in property units per second
     * @param stiffness spring stiffness passed to [SpringForce]
     * @param dampingRatio spring damping ratio passed to [SpringForce]
     * @param onEnd optional callback invoked when the animation ends or is cancelled
     * @return the already-started spring animation
     */
    fun startSpringAnimation(
        target: View,
        property: DynamicAnimation.ViewProperty,
        finalPosition: Float,
        startVelocity: Float = 0f,
        stiffness: Float = SpringForce.STIFFNESS_MEDIUM,
        dampingRatio: Float = SpringForce.DAMPING_RATIO_NO_BOUNCY,
        onEnd: (() -> Unit)? = null,
    ): SpringAnimation {
        return SpringAnimation(target, property).apply {
            spring = SpringForce(finalPosition).apply {
                this.stiffness = stiffness
                this.dampingRatio = dampingRatio
            }
            setStartVelocity(startVelocity)
            onEnd?.let {
                addEndListener { _, _, _, _ ->
                    it()
                }
            }
            start()
        }
    }

    /**
     * Creates and immediately starts a bounded fling animation for one View property.
     *
     * @param target View whose property is animated
     * @param property dynamic-animation property to update
     * @param startVelocity initial velocity in property units per second
     * @param friction positive platform friction scalar
     * @param minValue inclusive lower bound for the animated property
     * @param maxValue inclusive upper bound for the animated property
     * @param onEnd optional callback invoked when the animation ends or is cancelled
     * @return the already-started fling animation
     */
    fun startFlingAnimation(
        target: View,
        property: DynamicAnimation.ViewProperty,
        startVelocity: Float,
        friction: Float = 1.1f,
        minValue: Float = -Float.MAX_VALUE,
        maxValue: Float = Float.MAX_VALUE,
        onEnd: (() -> Unit)? = null,
    ): FlingAnimation {
        return FlingAnimation(target, property).apply {
            setStartVelocity(startVelocity)
            setFriction(friction)
            setMinValue(minValue)
            setMaxValue(maxValue)
            onEnd?.let {
                addEndListener { _, _, _, _ ->
                    it()
                }
            }
            start()
        }
    }

    /**
     * Requests [motionLayout] to transition to [endState].
     *
     * @param motionLayout target MotionLayout
     * @param endState destination constraint-set resource ID
     * @param durationMillis optional transition duration override in milliseconds
     */
    fun animateToState(
        motionLayout: MotionLayout,
        endState: Int,
        durationMillis: Int? = null,
    ) {
        durationMillis?.let { motionLayout.setTransitionDuration(it) }
        motionLayout.transitionToState(endState)
    }

    /**
     * Requests [motionLayout] to transition to its current transition's start state.
     *
     * @param motionLayout target MotionLayout
     * @param durationMillis optional transition duration override in milliseconds
     */
    fun animateToStart(
        motionLayout: MotionLayout,
        durationMillis: Int? = null,
    ) {
        durationMillis?.let { motionLayout.setTransitionDuration(it) }
        motionLayout.transitionToStart()
    }

    /**
     * Requests [motionLayout] to transition to its current transition's end state.
     *
     * @param motionLayout target MotionLayout
     * @param durationMillis optional transition duration override in milliseconds
     */
    fun animateToEnd(
        motionLayout: MotionLayout,
        durationMillis: Int? = null,
    ) {
        durationMillis?.let { motionLayout.setTransitionDuration(it) }
        motionLayout.transitionToEnd()
    }
}

/**
 * Adds replay-safe Android animation configuration to a mounted native View.
 *
 * [configure] can run again during patching or rollback. It should configure durable View state and
 * must not start one-shot animations or mutate external state.
 *
 * @param key stable identity for this modifier operation
 * @param configure replay-safe View configuration
 * @return this modifier followed by the native View operation
 */
fun Modifier.androidAnimation(
    key: Any = Unit,
    configure: (View) -> Unit,
): Modifier {
    return this.nativeView(
        key = key,
        configure = configure,
    )
}

/**
 * Mounts a platform [MotionLayout] inside the declarative tree.
 *
 * [factory] runs only when a new native node is required. [update] is replay-safe and may run during
 * patching or rollback. Use [key] to preserve the MotionLayout across sibling reordering.
 *
 * @param factory creates the MotionLayout for the Android context
 * @param update applies replay-safe state to the mounted MotionLayout
 * @param key optional declarative identity
 * @param modifier modifiers applied to the native node
 */
fun UiTreeBuilder.MotionLayoutView(
    factory: (Context) -> MotionLayout,
    update: (MotionLayout) -> Unit = {},
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            factory(context)
        },
        update = { view ->
            update(view as MotionLayout)
        },
        key = key,
        modifier = modifier,
    )
}

private fun ValueAnimator.doOnAnimationEnd(
    onEnd: () -> Unit,
) {
    addListener(
        object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onEnd()
            }
        },
    )
}
