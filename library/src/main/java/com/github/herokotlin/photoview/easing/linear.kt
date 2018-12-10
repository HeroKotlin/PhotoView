package com.github.herokotlin.photoview.easing

object linear: Easing {
    override fun interpolate(input: Float): Float {
        return input
    }
}

