package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp

/** Default visual tokens for divider components. */
object DividerDefaults {
    /** Returns the divider color for the current theme. */
    fun color(): Int = Theme.colors.outlineVariant

    /** Returns the default divider thickness. */
    fun thickness(): UiDp = 1.dp
}
