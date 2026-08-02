package com.viewcompose.host.android.graphics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import com.viewcompose.host.android.nativeView
import com.viewcompose.ui.modifier.Modifier

/**
 * Provides opt-in Android graphics operations without leaking platform types into graphics-core.
 *
 * API-level-dependent factories return `null` or `false` when unavailable so callers can install a
 * deterministic fallback. Returned bitmaps, shaders, effects, and paints are owned by the caller.
 */
object AndroidGraphicsInterop {
    /**
     * Applies [effect] to [target] on Android 12 and later.
     *
     * @param target View whose render effect is replaced
     * @param effect new effect, or `null` to clear the current effect
     * @return `true` when the platform supports View render effects
     */
    fun applyRenderEffect(
        target: View,
        effect: RenderEffect?,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false
        }
        target.setRenderEffect(effect)
        return true
    }

    /** Clears [target]'s render effect when supported by the platform. */
    fun clearRenderEffect(target: View): Boolean {
        return applyRenderEffect(
            target = target,
            effect = null,
        )
    }

    /**
     * Creates a platform blur effect on Android 12 and later.
     *
     * @param radiusX horizontal blur radius in pixels
     * @param radiusY vertical blur radius in pixels
     * @param tileMode sampling behavior outside the input bounds
     * @return the effect, or `null` on unsupported Android versions
     */
    fun createBlurEffect(
        radiusX: Float,
        radiusY: Float,
        tileMode: Shader.TileMode = Shader.TileMode.CLAMP,
    ): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null
        }
        return RenderEffect.createBlurEffect(radiusX, radiusY, tileMode)
    }

    /**
     * Creates an effect that applies [inner] first and [outer] second.
     *
     * @return the chained effect, or `null` before Android 12
     */
    fun chainRenderEffects(
        outer: RenderEffect,
        inner: RenderEffect,
    ): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null
        }
        return RenderEffect.createChainEffect(outer, inner)
    }

    /**
     * Creates a render effect backed by [colorFilter] on Android 12 and later.
     *
     * @param colorFilter filter applied to the input
     * @param input optional effect whose output feeds the filter
     * @return the color-filter effect, or `null` on unsupported Android versions
     */
    fun createColorFilterEffect(
        colorFilter: ColorFilter,
        input: RenderEffect? = null,
    ): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null
        }
        return if (input == null) {
            RenderEffect.createColorFilterEffect(colorFilter)
        } else {
            RenderEffect.createColorFilterEffect(colorFilter, input)
        }
    }

    /**
     * Compiles AGSL [shaderSource] into a [RuntimeShader] on Android 13 and later.
     *
     * Platform shader compilation errors propagate to the caller.
     *
     * @return the compiled shader, or `null` on unsupported Android versions
     */
    fun createRuntimeShader(shaderSource: String): RuntimeShader? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return null
        }
        return RuntimeShader(shaderSource)
    }

    /**
     * Wraps [shader] in a render effect on Android 13 and later.
     *
     * @param shader compiled AGSL runtime shader
     * @param inputShaderName uniform name that receives the source image shader
     * @return the effect, or `null` on unsupported Android versions
     */
    fun createRuntimeShaderEffect(
        shader: RuntimeShader,
        inputShaderName: String,
    ): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return null
        }
        return RenderEffect.createRuntimeShaderEffect(shader, inputShaderName)
    }

    /**
     * Allocates a bitmap and executes [draw] against a Canvas backed by it.
     *
     * The callback runs synchronously. The returned bitmap is mutable and owned by the caller.
     *
     * @param width bitmap width in pixels; must be positive
     * @param height bitmap height in pixels; must be positive
     * @param config pixel storage configuration
     * @param draw drawing commands executed against the new bitmap
     * @return the rendered mutable bitmap
     * @throws IllegalArgumentException when either dimension is not positive
     */
    fun renderToBitmap(
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888,
        draw: Canvas.() -> Unit,
    ): Bitmap {
        require(width > 0) { "width must be > 0" }
        require(height > 0) { "height must be > 0" }
        // The caller supplies Canvas drawing; this helper owns only allocation and Canvas binding.
        return Bitmap.createBitmap(width, height, config).apply {
            Canvas(this).draw()
        }
    }

    /**
     * Draws [drawable] into a newly allocated bitmap of the requested size.
     *
     * This replaces the drawable's bounds and does not restore them.
     *
     * @param drawable drawable rendered synchronously
     * @param width bitmap width in pixels; must be positive
     * @param height bitmap height in pixels; must be positive
     * @param config pixel storage configuration
     * @return the rendered mutable bitmap
     */
    fun drawDrawableToBitmap(
        drawable: android.graphics.drawable.Drawable,
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888,
    ): Bitmap {
        return renderToBitmap(
            width = width,
            height = height,
            config = config,
        ) {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(this)
        }
    }

    /**
     * Enables a hardware layer on [target] using a newly configured [Paint].
     *
     * The layer remains enabled until the caller changes the View layer type.
     *
     * @param target View whose layer paint is replaced
     * @param configure synchronous paint configuration
     */
    fun setLayerPaint(
        target: View,
        configure: Paint.() -> Unit,
    ) {
        target.setLayerType(
            View.LAYER_TYPE_HARDWARE,
            Paint().apply(configure),
        )
    }
}

/**
 * Adds replay-safe Android graphics configuration to a mounted native View.
 *
 * [configure] may run during patching and rollback; it must configure only durable View state and
 * must not perform external one-shot effects.
 *
 * @param key stable identity for this modifier operation
 * @param configure replay-safe native View configuration
 * @return this modifier followed by the native View operation
 */
fun Modifier.androidGraphics(
    key: Any = Unit,
    configure: (View) -> Unit,
): Modifier {
    return nativeView(
        key = key,
        configure = configure,
    )
}
