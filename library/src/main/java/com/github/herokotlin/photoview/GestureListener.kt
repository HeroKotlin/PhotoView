package com.github.herokotlin.photoview

import android.graphics.PointF

/**
 * 支持的所有手势
 */
internal interface GestureListener {

    fun onDrag(x: Float, y: Float): Boolean {
        return true
    }

    fun onDragStart(): Boolean {
        return true
    }

    fun onDragEnd(isFling: Boolean) {

    }

    fun onScale(scaleFactor: Float, focusPoint: PointF, lastFocusPoint: PointF) {

    }

    fun onScaleStart(): Boolean {
        return true
    }

    fun onScaleEnd(isDragging: Boolean) {

    }

    fun onFling(velocityX: Float, velocityY: Float): Boolean {
        return false
    }

    fun onTap(x: Float, y: Float) {

    }

    fun onDoubleTap(x: Float, y: Float) {

    }

    fun onLongPress(x: Float, y: Float) {

    }

}