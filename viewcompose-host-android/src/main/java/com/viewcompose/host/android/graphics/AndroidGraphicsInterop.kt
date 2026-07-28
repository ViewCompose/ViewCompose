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
 * Android 专属图形互操作工具，供业务代码显式接入原生 View 行为。
 * Android-specific graphics interop for business code that explicitly opts into native View behavior.
 *
 * 该 API 放在 host-android 中，确保 graphics 主线 API 保持平台中立。
 * This API is intentionally hosted in `host-android` to keep graphics mainline APIs platform-neutral.
 */
object AndroidGraphicsInterop {
    /**
     * 在支持的 Android 版本上给 View 应用 RenderEffect。
     * Applies a RenderEffect to a View on supported Android versions.
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

    /**
     * 清除 View 上的 RenderEffect。
     * Clears the RenderEffect applied to a View.
     */
    fun clearRenderEffect(target: View): Boolean {
        return applyRenderEffect(
            target = target,
            effect = null,
        )
    }

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

    fun chainRenderEffects(
        outer: RenderEffect,
        inner: RenderEffect,
    ): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null
        }
        return RenderEffect.createChainEffect(outer, inner)
    }

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

    fun createRuntimeShader(shaderSource: String): RuntimeShader? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return null
        }
        return RuntimeShader(shaderSource)
    }

    fun createRuntimeShaderEffect(
        shader: RuntimeShader,
        inputShaderName: String,
    ): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return null
        }
        return RenderEffect.createRuntimeShaderEffect(shader, inputShaderName)
    }

    fun renderToBitmap(
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888,
        draw: Canvas.() -> Unit,
    ): Bitmap {
        require(width > 0) { "width must be > 0" }
        require(height > 0) { "height must be > 0" }
        // 由调用方提供 Canvas DSL 绘制内容，这里只负责创建位图和绑定 Canvas。
        // The caller supplies Canvas DSL drawing; this helper only creates the bitmap and binds the Canvas.
        return Bitmap.createBitmap(width, height, config).apply {
            Canvas(this).draw()
        }
    }

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
 * 将 Android 图形配置作为 nativeView modifier 接入声明式树。
 * Attaches Android graphics configuration to the declarative tree as a nativeView modifier.
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
