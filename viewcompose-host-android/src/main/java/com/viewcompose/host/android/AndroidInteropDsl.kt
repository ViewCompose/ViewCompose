package com.viewcompose.host.android

import android.content.Context
import android.view.View
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.NativeViewElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.widget.core.UiTreeBuilder

fun UiTreeBuilder.AndroidView(
    factory: (Context) -> View,
    update: (View) -> Unit = {},
    key: Any? = null,
    modifier: Modifier = Modifier,
    onReset: ((View) -> Unit)? = null,
    onRelease: ((View) -> Unit)? = null,
) {
    emit(
        type = NodeType.AndroidView,
        key = key,
        spec = AndroidViewNodeProps(
            factory = { context ->
                factory(context as Context)
            },
            update = { view ->
                update(view as View)
            },
            onReset = onReset?.let { reset ->
                { view -> reset(view as View) }
            },
            onRelease = onRelease?.let { release ->
                { view -> release(view as View) }
            },
        ),
        modifier = modifier,
    )
}

fun Modifier.nativeView(
    key: Any = Unit,
    configure: (View) -> Unit,
): Modifier {
    return then(
        NativeViewElement(
            stableKey = key,
            configure = { view ->
                configure(view as View)
            },
        ),
    )
}
