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
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * Android 专属动画桥接，用于业务代码显式接入 View 互操作。
 * Android-specific animation bridge for cases where business code explicitly opts into View interop.
 *
 * 常见用法：
 * Typical usage:
 *
 * - 使用 [UiTreeBuilder.MotionLayoutView] 承载 MotionLayout transition。
 * - Use [UiTreeBuilder.MotionLayoutView] for MotionLayout-driven transitions.
 * - 使用 [Modifier.androidAnimation] 配置 host View，并在回调中调用 [AndroidAnimationInterop]。
 * - Use [Modifier.androidAnimation] to configure a host view and call [AndroidAnimationInterop].
 */
object AndroidAnimationInterop {
    /**
     * 对目标 View 或其父级 ViewGroup 开始 delayed transition。
     * Begins a delayed transition on the target View or its parent ViewGroup.
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
     * 启动 ObjectAnimator 并返回原生 animator，调用方可继续取消或监听。
     * Starts an ObjectAnimator and returns the native animator for further cancellation or observation.
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

    fun animateToState(
        motionLayout: MotionLayout,
        endState: Int,
        durationMillis: Int? = null,
    ) {
        durationMillis?.let { motionLayout.setTransitionDuration(it) }
        motionLayout.transitionToState(endState)
    }

    fun animateToStart(
        motionLayout: MotionLayout,
        durationMillis: Int? = null,
    ) {
        durationMillis?.let { motionLayout.setTransitionDuration(it) }
        motionLayout.transitionToStart()
    }

    fun animateToEnd(
        motionLayout: MotionLayout,
        durationMillis: Int? = null,
    ) {
        durationMillis?.let { motionLayout.setTransitionDuration(it) }
        motionLayout.transitionToEnd()
    }
}

/**
 * 将 Android 动画配置作为 nativeView modifier 接入声明式树。
 * Attaches Android animation configuration to the declarative tree as a nativeView modifier.
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
 * 挂载 MotionLayout，供 Android 原生 motion 场景与 UIFramework 树协作。
 * Mounts a MotionLayout so native Android motion scenes can cooperate with the UIFramework tree.
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
