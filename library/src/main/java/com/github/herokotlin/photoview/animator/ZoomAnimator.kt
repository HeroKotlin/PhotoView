package com.github.herokotlin.photoview.animator

import com.github.herokotlin.photoview.PhotoView
import com.github.herokotlin.photoview.easing.Easing

class ZoomAnimator(view: PhotoView,
                   interval: Long,
                   duration: Int,
                   easing: Easing,
                   val fromScale: Float,
                   val toScale: Float,
                   var focusX: Float,
                   var focusY: Float,
                   listener: AnimatorListener?
): EasingAnimator(view, interval, duration, easing, listener) {

    private var lastScale = 1f

    private val scaleFactor = (toScale / fromScale) - 1

    override fun animate(factor: Float, lastFactor: Float) {

        val nextScale = 1 + factor * scaleFactor
        val ratio = nextScale / lastScale

        view.zoom(ratio, focusX, focusY)

        listener?.onStep(ratio)

        lastScale = nextScale

    }

}