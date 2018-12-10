package com.github.herokotlin.photoview.easing

object outExpo: Easing {
    override fun interpolate(input: Float): Float {
        return if (input == 1f) 1f else (-Math.pow(2.toDouble(), -10 * input.toDouble()) + 1).toFloat()
    }
}

