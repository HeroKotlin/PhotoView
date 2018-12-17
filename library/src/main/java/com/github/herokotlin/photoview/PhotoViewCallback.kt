package com.github.herokotlin.photoview

// 外部回调
interface PhotoViewCallback {

    fun onReset() {

    }

    fun onTap(x: Float, y: Float) {

    }

    fun onLongPress(x: Float, y: Float) {

    }

    fun onDragStart() {

    }

    fun onDrag(x: Float, y: Float) {

    }

    fun onDragEnd() {

    }

    fun onScaleChange(scale: Float) {

    }

}