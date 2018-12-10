package com.github.herokotlin.photoview.easing

object outCubic: Easing {
    override fun interpolate(input: Float): Float {
        return 1 - Math.pow((1 - input).toDouble(), 3.toDouble()).toFloat()
    }
}