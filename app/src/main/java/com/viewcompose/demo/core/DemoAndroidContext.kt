package com.viewcompose

import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity

/**
 * 沿 ContextWrapper 链查找当前 AppCompatActivity，供 demo 页面在 DSL 内安全访问宿主能力。
 * Walks the ContextWrapper chain to find the current AppCompatActivity so demo pages can safely access host features.
 */
internal fun Context.findAppCompatActivity(): AppCompatActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is AppCompatActivity) {
            return current
        }
        val next = current.baseContext
        if (next === current) {
            break
        }
        current = next
    }
    return current as? AppCompatActivity
}
