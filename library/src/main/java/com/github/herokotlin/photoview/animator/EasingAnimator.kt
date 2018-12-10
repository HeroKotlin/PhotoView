package com.github.herokotlin.photoview.animator

import com.github.herokotlin.photoview.PhotoView
import com.github.herokotlin.photoview.easing.Easing

open class EasingAnimator(view: PhotoView,
                    interval: Long,
                    private val duration: Int,
                    private val easing: Easing,
                    listener: AnimatorListener?
): Animator(view, interval, listener) {

    private val startTime = System.currentTimeMillis()
    private var lastFactor = 0f

    override fun animate() {

        var factor = (System.currentTimeMillis() - startTime).toFloat() / duration
        if (factor > 1) {
            factor = 1f
        }

        factor = easing.interpolate(factor)

        animate(factor, lastFactor)

        lastFactor = factor

    }

    open fun animate(factor: Float, lastFactor: Float) {

    }

    override fun isComplete(): Boolean {
        return lastFactor >= 1
    }

}