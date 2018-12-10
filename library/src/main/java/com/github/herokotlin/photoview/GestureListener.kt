package com.github.herokotlin.photoview

import android.graphics.PointF

/**
 * 支持的所有手势
 */
interface GestureListener {

    fun onDrag(x: Float, y: Float): Boolean {
        return true
    }

    fun onDragStart(): Boolean {
        return true
    }

    fun onDragEnd(isScaling: Boolean, isFling: Boolean) {

    }

    fun onScale(scale: Float, focusPoint: PointF, lastFocusPoint: PointF) {

    }

    fun onScaleStart(): Boolean {
        return true
    }

    fun onScaleEnd() {

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