package com.github.herokotlin.photoview

import android.animation.TimeInterpolator
import android.view.animation.LinearInterpolator

/**
 * 图片相关的配置
 */
class PhotoViewConfiguration {

    var minScale = 1f
    var maxScale = 3f

    // 双击放大动画时长
    var zoomDuration = 250L

    var zoomSlopFactor = 0.6f
    var zoomInterpolator: TimeInterpolator = LinearInterpolator()

    var bounceDuration = 250L
    var bounceDistance = 0.4f
    var bounceInterpolator: TimeInterpolator = LinearInterpolator()

    var flingInterpolator: TimeInterpolator = LinearInterpolator()

}