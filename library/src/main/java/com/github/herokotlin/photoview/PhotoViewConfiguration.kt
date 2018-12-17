package com.github.herokotlin.photoview

import com.github.herokotlin.photoview.easing.Easing
import com.github.herokotlin.photoview.easing.outExpo

/**
 * 图片相关的配置
 */
class PhotoViewConfiguration {

    var minScale = 1f
    var maxScale = 3f

    var zoomDuration = 700
    var zoomSlopFactor = 0.6f
    var zoomEasing: Easing = outExpo

    var bounceDuration = 300
    var bounceDistance = 0.6f
    var bounceEasing: Easing = outExpo

    var flingDampingFactor = 0.9f
    var frameRateUnit = 1000L / 60

}