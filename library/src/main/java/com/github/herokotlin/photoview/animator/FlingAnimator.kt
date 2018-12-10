package com.github.herokotlin.photoview.animator

import com.github.herokotlin.photoview.PhotoView

class FlingAnimator(view: PhotoView,
                    interval: Long,
                    private var velocityX: Float,
                    private var velocityY: Float,
                    private var flingDampingFactor: Float,
                    listener: AnimatorListener?
): Animator(view, interval, listener) {

    private val minVelocity = 1

    private var hasMoved = false

    override fun animate() {

        hasMoved = view.translate(velocityX, velocityY, true, 10f)

        listener?.onStep(velocityX, velocityY)

        velocityX *= flingDampingFactor
        velocityY *= flingDampingFactor

    }

    override fun isComplete(): Boolean {
        return !hasMoved || (Math.abs(velocityX) <= minVelocity || Math.abs(velocityY) <= minVelocity)
    }

}