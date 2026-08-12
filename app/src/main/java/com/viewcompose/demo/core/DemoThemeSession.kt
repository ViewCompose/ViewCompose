package com.viewcompose

import com.viewcompose.runtime.MutableState
import com.viewcompose.runtime.mutableStateOf

/**
 * Stores the observable in-process Demo theme choice shared by independent Activity sessions.
 *
 * This remains application Demo policy and is intentionally not persisted across process death.
 */
internal object DemoThemeSession {
    val modeState: MutableState<DemoThemeMode> = mutableStateOf(DemoThemeMode.System)

    var mode: DemoThemeMode
        get() = modeState.value
        set(value) {
            modeState.value = value
        }
}
