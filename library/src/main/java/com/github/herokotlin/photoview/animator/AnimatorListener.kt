package com.github.herokotlin.photoview.animator

interface AnimatorListener {

    fun onStep(a: Float, b: Float = 0f) {

    }

    fun onComplete() {

    }

}