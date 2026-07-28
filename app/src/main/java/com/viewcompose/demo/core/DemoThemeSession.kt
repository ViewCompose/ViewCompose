package com.viewcompose

/**
 * 保存 demo 进程内主题模式选择；这是演示会话状态，不写入持久化存储。
 * Stores the in-process demo theme-mode selection; this is session state and is not persisted.
 */
internal object DemoThemeSession {
    var mode: DemoThemeMode = DemoThemeMode.System
}
