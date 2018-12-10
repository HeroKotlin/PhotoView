package com.github.herokotlin.photoview.animator

import com.github.herokotlin.photoview.PhotoView
import com.github.herokotlin.photoview.easing.Easing

class DragAnimator(view: PhotoView,
                   interval: Long,
                   duration: Int,
                   easing: Easing,
                   private val dx: Float,
                   private val dy: Float,
                   listener: AnimatorListener?
): EasingAnimator(view, interval, duration, easing, listener) {

    override fun animate(factor: Float, lastFactor: Float) {

        val deltaX = (factor - lastFactor) * dx
        val deltaY = (factor - lastFactor) * dy

        view.translate(deltaX, deltaY)

        listener?.onStep(deltaX, deltaY)

    }

}