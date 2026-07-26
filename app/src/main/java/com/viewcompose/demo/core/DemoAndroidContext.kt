package com.viewcompose

import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity

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
