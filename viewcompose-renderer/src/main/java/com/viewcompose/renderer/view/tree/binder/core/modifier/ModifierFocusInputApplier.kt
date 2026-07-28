package com.viewcompose.renderer.view.tree

import android.os.Build
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.viewcompose.renderer.R
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.ui.focus.FocusDirection
import com.viewcompose.ui.focus.FocusProperties
import com.viewcompose.ui.focus.FocusRequester
import com.viewcompose.ui.focus.FocusRequesterConnector
import com.viewcompose.ui.focus.FocusState
import com.viewcompose.ui.input.Key
import com.viewcompose.ui.input.KeyEvent
import com.viewcompose.ui.input.KeyEventType
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode

/**
 * 应用 focus、focus requester、focus observer 和 key input modifier。
 * Applies focus, focus requester, focus observer, and key input modifiers.
 */
internal object ModifierFocusInputApplier {
    /**
     * 将焦点和键盘输入配置绑定到目标 View。
     * Binds focus and keyboard input configuration to the target View.
     */
    fun apply(
        view: View,
        node: VNode,
        resolved: ResolvedModifiers,
    ) {
        applyFocusable(view, resolved)
        applyFocusGroup(view, resolved)
        applyFocusRequester(
            view = view,
            node = node,
            requester = resolved.focusRequester?.requester,
        )
        applyFocusObserver(
            view = view,
            callback = resolved.onFocusChanged?.onFocusChanged,
        )
        applyKeyInput(
            view = view,
            nodeType = node.type,
            resolved = resolved,
        )
    }

    /**
     * 释放 focus/key input 相关 listener 和 requester 绑定。
     * Releases focus/key-input listeners and requester bindings.
     */
    fun dispose(view: View) {
        (view.getTag(R.id.viewcompose_focus_requester_binding) as? FocusRequesterBinding)
            ?.dispose()
        view.setTag(R.id.viewcompose_focus_requester_binding, null)

        (view.getTag(R.id.viewcompose_focus_observer) as? FocusObserverBinding)
            ?.dispose()
        view.setTag(R.id.viewcompose_focus_observer, null)

        restoreFocusGroup(view)

        if (view.getTag(R.id.viewcompose_key_input_listener) != null) {
            view.setOnKeyListener(null)
        }
        view.setTag(R.id.viewcompose_key_input_binding, null)
        view.setTag(R.id.viewcompose_key_input_listener, null)
    }

    private fun applyFocusable(
        view: View,
        resolved: ResolvedModifiers,
    ) {
        val canFocus = resolved.focusProperties.canFocus
            ?: resolved.focusable?.enabled
            ?: return
        view.isFocusable = canFocus
        view.isFocusableInTouchMode = canFocus
        if (!canFocus && view.isFocused) {
            view.clearFocus()
        }
    }

    private fun applyFocusGroup(
        view: View,
        resolved: ResolvedModifiers,
    ) {
        val group = view as? ViewGroup ?: return
        if (resolved.focusGroup?.enabled == true) {
            if (group.getTag(R.id.viewcompose_focus_group_original_state) == null) {
                group.setTag(
                    R.id.viewcompose_focus_group_original_state,
                    FocusGroupOriginalState(
                        descendantFocusability = group.descendantFocusability,
                        isKeyboardNavigationCluster = if (Build.VERSION.SDK_INT >= 26) {
                            group.isKeyboardNavigationCluster
                        } else {
                            false
                        },
                    ),
                )
            }
            group.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            if (Build.VERSION.SDK_INT >= 26) {
                group.isKeyboardNavigationCluster = true
            }
        } else {
            restoreFocusGroup(group)
        }
    }

    private fun restoreFocusGroup(view: View) {
        val group = view as? ViewGroup ?: return
        val original = group.getTag(
            R.id.viewcompose_focus_group_original_state,
        ) as? FocusGroupOriginalState ?: return
        group.descendantFocusability = original.descendantFocusability
        if (Build.VERSION.SDK_INT >= 26) {
            group.isKeyboardNavigationCluster = original.isKeyboardNavigationCluster
        }
        group.setTag(R.id.viewcompose_focus_group_original_state, null)
    }

    private fun applyFocusRequester(
        view: View,
        node: VNode,
        requester: FocusRequester?,
    ) {
        val previous = view.getTag(
            R.id.viewcompose_focus_requester_binding,
        ) as? FocusRequesterBinding
        val restorationKey = node.key ?: view
        // 有 key 时用 key 作为恢复身份；无 key 时退回 View 实例避免不同无 key 节点互相复用焦点。
        // Use key as restoration identity when present; otherwise fall back to the View to avoid sharing focus across unkeyed nodes.
        if (previous != null &&
            previous.requester === requester &&
            previous.connector.restorationKey == restorationKey
        ) {
            return
        }
        previous?.dispose()
        if (requester == null) {
            view.setTag(R.id.viewcompose_focus_requester_binding, null)
            return
        }
        val binding = FocusRequesterBinding(
            requester = requester,
            connector = AndroidViewFocusRequesterConnector(
                view = view,
                restorationKey = restorationKey,
            ),
        )
        requester.attach(binding.connector)
        view.setTag(R.id.viewcompose_focus_requester_binding, binding)
    }

    private fun applyFocusObserver(
        view: View,
        callback: ((FocusState) -> Unit)?,
    ) {
        val previous = view.getTag(R.id.viewcompose_focus_observer) as? FocusObserverBinding
        if (callback == null) {
            previous?.dispose()
            view.setTag(R.id.viewcompose_focus_observer, null)
            return
        }
        if (previous != null) {
            previous.update(callback)
            return
        }
        view.setTag(
            R.id.viewcompose_focus_observer,
            FocusObserverBinding(
                view = view,
                callback = callback,
            ),
        )
    }

    private fun applyKeyInput(
        view: View,
        nodeType: NodeType,
        resolved: ResolvedModifiers,
    ) {
        val binding = KeyInputBinding(
            preview = resolved.previewKeyEvent?.onPreviewKeyEvent,
            bubble = resolved.keyEvent?.onKeyEvent,
            focusProperties = resolved.focusProperties,
        )
        view.setTag(R.id.viewcompose_key_input_binding, binding)

        val hasOwnInputContract =
            binding.preview != null ||
                binding.bubble != null ||
                binding.focusProperties != FocusProperties.Default
        val shouldInstall = nodeType != NodeType.AndroidView || hasOwnInputContract
        // AndroidView 默认保留业务 View 的 key listener，只有声明了输入契约才接管。
        // AndroidView keeps the business View's key listener by default and is intercepted only when an input contract is declared.
        val installed = view.getTag(R.id.viewcompose_key_input_listener) as? View.OnKeyListener
        if (!shouldInstall) {
            if (installed != null) {
                view.setOnKeyListener(null)
            }
            view.setTag(R.id.viewcompose_key_input_listener, null)
            return
        }
        if (installed != null) {
            return
        }
        val listener = View.OnKeyListener { target, _, event ->
            dispatchKeyEvent(
                target = target,
                nativeEvent = event,
            )
        }
        view.setOnKeyListener(listener)
        view.setTag(R.id.viewcompose_key_input_listener, listener)
    }

    private fun dispatchKeyEvent(
        target: View,
        nativeEvent: AndroidKeyEvent,
    ): Boolean {
        val event = nativeEvent.toContractEvent()
        val hierarchy = generateSequence(target as View?) { current ->
            current.parent as? View
        }.toList()

        // 先捕获阶段从 root 到 target，再冒泡阶段从 target 到 root。
        // Run preview from root to target first, then bubble from target back to root.
        hierarchy.asReversed().forEach { current ->
            val binding = current.getTag(
                R.id.viewcompose_key_input_binding,
            ) as? KeyInputBinding
            if (binding?.preview?.invoke(event) == true) {
                return true
            }
        }
        hierarchy.forEach { current ->
            val binding = current.getTag(
                R.id.viewcompose_key_input_binding,
            ) as? KeyInputBinding
            if (binding?.bubble?.invoke(event) == true) {
                return true
            }
        }
        if (event.type != KeyEventType.KeyDown || event.repeatCount != 0) {
            return false
        }
        val direction = event.focusDirection() ?: return false
        val targetBinding = target.getTag(
            R.id.viewcompose_key_input_binding,
        ) as? KeyInputBinding ?: return false
        val requester = targetBinding.focusProperties.requesterFor(direction) ?: return false
        return runCatching {
            requester.requestFocus(direction)
        }.getOrDefault(false)
    }

    private data class FocusGroupOriginalState(
        val descendantFocusability: Int,
        val isKeyboardNavigationCluster: Boolean,
    )

    private class FocusRequesterBinding(
        val requester: FocusRequester,
        val connector: FocusRequesterConnector,
    ) {
        fun dispose() {
            requester.detach(connector)
        }
    }

    private class AndroidViewFocusRequesterConnector(
        private val view: View,
        override val restorationKey: Any,
    ) : FocusRequesterConnector {
        override val identity: Any
            get() = view

        override val focusState: FocusState
            get() = FocusState(
                isFocused = view.isFocused,
                hasFocus = view.hasFocus(),
            )

        override fun requestFocus(direction: FocusDirection): Boolean {
            return view.requestFocus(direction.toAndroidFocusDirection())
        }
    }

    private class FocusObserverBinding(
        private val view: View,
        callback: (FocusState) -> Unit,
    ) : ViewTreeObserver.OnGlobalFocusChangeListener,
        View.OnAttachStateChangeListener {
        private var callback: (FocusState) -> Unit = callback
        private var observer: ViewTreeObserver? = null
        private var lastState: FocusState? = null

        init {
            view.addOnAttachStateChangeListener(this)
            if (view.isAttachedToWindow) {
                register()
            }
            dispatch(force = true)
        }

        fun update(nextCallback: (FocusState) -> Unit) {
            callback = nextCallback
            dispatch(force = false)
        }

        override fun onGlobalFocusChanged(
            oldFocus: View?,
            newFocus: View?,
        ) {
            dispatch(force = false)
        }

        override fun onViewAttachedToWindow(attachedView: View) {
            register()
            dispatch(force = false)
        }

        override fun onViewDetachedFromWindow(detachedView: View) {
            unregister()
            dispatch(force = false)
        }

        fun dispose() {
            unregister()
            view.removeOnAttachStateChangeListener(this)
        }

        private fun register() {
            val nextObserver = view.viewTreeObserver
            if (observer === nextObserver) {
                return
            }
            unregister()
            if (nextObserver.isAlive) {
                nextObserver.addOnGlobalFocusChangeListener(this)
                observer = nextObserver
            }
        }

        private fun unregister() {
            observer?.takeIf(ViewTreeObserver::isAlive)
                ?.removeOnGlobalFocusChangeListener(this)
            observer = null
        }

        private fun dispatch(force: Boolean) {
            val next = FocusState(
                isFocused = view.isFocused,
                hasFocus = view.hasFocus(),
            )
            if (!force && lastState == next) {
                return
            }
            lastState = next
            callback(next)
        }
    }

    private data class KeyInputBinding(
        val preview: ((KeyEvent) -> Boolean)?,
        val bubble: ((KeyEvent) -> Boolean)?,
        val focusProperties: FocusProperties,
    )
}

private fun FocusDirection.toAndroidFocusDirection(): Int {
    return when (this) {
        FocusDirection.Next,
        FocusDirection.Enter,
        -> View.FOCUS_FORWARD
        FocusDirection.Previous -> View.FOCUS_BACKWARD
        FocusDirection.Left -> View.FOCUS_LEFT
        FocusDirection.Right -> View.FOCUS_RIGHT
        FocusDirection.Up -> View.FOCUS_UP
        FocusDirection.Down -> View.FOCUS_DOWN
        FocusDirection.Exit -> View.FOCUS_BACKWARD
    }
}

private fun AndroidKeyEvent.toContractEvent(): KeyEvent {
    return KeyEvent(
        key = keyCode.toContractKey(),
        type = when (action) {
            AndroidKeyEvent.ACTION_DOWN -> KeyEventType.KeyDown
            AndroidKeyEvent.ACTION_UP -> KeyEventType.KeyUp
            else -> KeyEventType.Unknown
        },
        nativeKeyCode = keyCode,
        unicodeCodePoint = unicodeChar,
        repeatCount = repeatCount,
        isAltPressed = isAltPressed,
        isCtrlPressed = isCtrlPressed,
        isMetaPressed = isMetaPressed,
        isShiftPressed = isShiftPressed,
    )
}

private fun Int.toContractKey(): Key {
    return when (this) {
        AndroidKeyEvent.KEYCODE_ENTER -> Key.Enter
        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> Key.NumPadEnter
        AndroidKeyEvent.KEYCODE_TAB -> Key.Tab
        AndroidKeyEvent.KEYCODE_SPACE -> Key.Spacebar
        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> Key.DirectionLeft
        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> Key.DirectionRight
        AndroidKeyEvent.KEYCODE_DPAD_UP -> Key.DirectionUp
        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> Key.DirectionDown
        AndroidKeyEvent.KEYCODE_PAGE_UP -> Key.PageUp
        AndroidKeyEvent.KEYCODE_PAGE_DOWN -> Key.PageDown
        AndroidKeyEvent.KEYCODE_MOVE_HOME -> Key.MoveHome
        AndroidKeyEvent.KEYCODE_MOVE_END -> Key.MoveEnd
        AndroidKeyEvent.KEYCODE_BACK -> Key.Back
        AndroidKeyEvent.KEYCODE_ESCAPE -> Key.Escape
        AndroidKeyEvent.KEYCODE_DEL -> Key.Backspace
        AndroidKeyEvent.KEYCODE_FORWARD_DEL -> Key.Delete
        in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_Z -> Key.Character
        else -> Key.Unknown
    }
}

private fun KeyEvent.focusDirection(): FocusDirection? {
    return when (key) {
        Key.Tab -> if (isShiftPressed) FocusDirection.Previous else FocusDirection.Next
        Key.DirectionLeft -> FocusDirection.Left
        Key.DirectionRight -> FocusDirection.Right
        Key.DirectionUp -> FocusDirection.Up
        Key.DirectionDown -> FocusDirection.Down
        else -> null
    }
}
