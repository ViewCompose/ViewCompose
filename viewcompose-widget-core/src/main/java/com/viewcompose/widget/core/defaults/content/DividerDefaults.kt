package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp

/**
 * Divider DSL 的默认线条 token。
 * Default line tokens for the Divider DSL.
 */
object DividerDefaults {
    fun color(): Int = Theme.colors.outlineVariant

    fun thickness(): UiDp = 1.dp
}
