package com.viewcompose.widget.core

import kotlin.math.roundToInt

/**
 * 将整数 dp 转换为当前 Environment 密度下的像素。
 * Converts integer dp to pixels using the current Environment density.
 */
val Int.dp: Int
    get() = Environment.density.dp(this)

/**
 * 将浮点 dp 四舍五入后转换为像素。
 * Rounds float dp and converts it to pixels.
 */
val Float.dp: Int
    get() = Environment.density.dp(roundToInt())

/**
 * 整数 sp token，实际像素换算在渲染层按文本语义处理。
 * Integer sp token; pixel conversion is handled by the renderer according to text semantics.
 */
val Int.sp: Int
    get() = this

/**
 * 浮点 sp 四舍五入为整数 sp token。
 * Rounds float sp into an integer sp token.
 */
val Float.sp: Int
    get() = roundToInt()
