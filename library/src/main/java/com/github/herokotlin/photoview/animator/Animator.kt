package com.github.herokotlin.photoview.animator

import com.github.herokotlin.photoview.PhotoView

open class Animator(val view: PhotoView, val interval: Long, val listener: AnimatorListener?): Runnable {

    private var isCanceled = false

    override fun run() {

        if (isCanceled) {
            return
        }

        animate()

        if (isComplete()) {
            listener?.onComplete()
        }
        else {
            view.postDelayed(this, interval)
        }
    }

    // 中断动画
    fun cancel() {
        isCanceled = true
    }

    open fun animate() {

    }

    open fun isComplete(): Boolean {
        return true
    }

}