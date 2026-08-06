package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView base class used by LazyColumn and LazyRow.
 * RecyclerView base used by LazyColumn/LazyRow.
 */
internal class DeclarativeLazyListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : RecyclerView(context, attrs) {
    /**
     * Whether touch-driven scrolling is enabled; programmatic scrolling and layout remain available.
     * Whether user touch scrolling is enabled while programmatic scroll and layout keep working.
     */
    var userScrollEnabled: Boolean = true

    override fun dispatchDraw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipRect(0, 0, width, height)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saveCount)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return userScrollEnabled && super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return userScrollEnabled && super.onTouchEvent(event)
    }
}
