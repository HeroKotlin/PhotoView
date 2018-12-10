package com.github.herokotlin.photoview.easing

object outQuart: Easing {
    override fun interpolate(input: Float): Float {
        return 1 - Math.pow((1 - input).toDouble(), 4.toDouble()).toFloat()
    }
}