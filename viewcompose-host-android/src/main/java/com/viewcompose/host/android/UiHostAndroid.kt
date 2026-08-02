package com.viewcompose.host.android

import com.viewcompose.ui.UiContract

/** Identifies the Android host artifact in runtime diagnostics. */
object UiHostAndroid {
    /** Ordered diagnostic dependency chain ending with `host-android`. */
    val dependencyChain: List<String> = UiContract.dependencyChain + "host-android"
}
