package com.viewcompose.studio.preview

import java.awt.Point
import java.lang.reflect.Proxy
import javax.swing.JComponent

/**
 * Installs JetBrains Runtime's native macOS magnification listener without linking Apple-only
 * classes into the plugin bytecode. Other runtimes simply use the Ctrl+wheel fallback installed by
 * the preview canvas.
 */
internal fun installNativePreviewMagnificationListener(
    component: JComponent,
    onMagnification: (magnification: Double, anchorPoint: Point?) -> Unit,
): AutoCloseable? {
    return runCatching {
        val listenerClass = Class.forName("com.apple.eawt.event.MagnificationListener")
        val gestureListenerClass = Class.forName("com.apple.eawt.event.GestureListener")
        val utilitiesClass = Class.forName("com.apple.eawt.event.GestureUtilities")
        val addListener = utilitiesClass.getMethod(
            "addGestureListenerTo",
            JComponent::class.java,
            gestureListenerClass,
        )
        val removeListener = utilitiesClass.getMethod(
            "removeGestureListenerFrom",
            JComponent::class.java,
            gestureListenerClass,
        )
        val listener = Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass),
        ) { proxy, method, arguments ->
            when (method.name) {
                "magnify" -> {
                    val event = arguments?.singleOrNull()
                    val magnification = event
                        ?.javaClass
                        ?.getMethod("getMagnification")
                        ?.invoke(event) as? Number
                    magnification
                        ?.toDouble()
                        ?.takeIf(Double::isFinite)
                        ?.let { value ->
                            onMagnification(
                                value,
                                runCatching { component.mousePosition?.let(::Point) }.getOrNull(),
                            )
                        }
                    event?.javaClass?.getMethod("consume")?.invoke(event)
                    null
                }

                "toString" -> "ViewComposePreviewMagnificationListener"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === arguments?.singleOrNull()
                else -> null
            }
        }
        addListener.invoke(null, component, listener)
        AutoCloseable {
            runCatching {
                removeListener.invoke(null, component, listener)
            }
        }
    }.getOrNull()
}
