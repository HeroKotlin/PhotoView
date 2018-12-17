package com.github.herokotlin.photoview

import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView

class PhotoView : ImageView, View.OnLayoutChangeListener {

    companion object {

        const val DIRECTION_NO = 0

        const val DIRECTION_LEFT = 1

        const val DIRECTION_TOP = 2

        const val DIRECTION_RIGHT = 4

        const val DIRECTION_BOTTOM = 8

        const val DIRECTION_HORIZONTAL = DIRECTION_LEFT or DIRECTION_RIGHT

        const val DIRECTION_VERTICAL = DIRECTION_TOP or DIRECTION_BOTTOM

        const val DIRECTION_ALL = DIRECTION_HORIZONTAL or DIRECTION_VERTICAL

    }

    private var mViewWidth: Float = 0f

        get() {
            return (width - paddingLeft - paddingRight).toFloat()
        }

    private var mViewHeight: Float = 0f

        get() {
            return (height - paddingTop - paddingBottom).toFloat()
        }

    private var mImageWidth: Float = 0f

        get() {
            if (drawable != null) {
                return drawable.intrinsicWidth.toFloat()
            }
            return 0f
        }

    private var mImageHeight: Float = 0f

        get() {
            if (drawable != null) {
                return drawable.intrinsicHeight.toFloat()
            }
            return 0f
        }

    // 当前的缩放类型，自带的 scaleType 现在是 Matrix，所以要记一个原本的 scaleType
    private var mScaleType: ScaleType? = null

    // 图片的配置
    private val mConfiguration = PhotoViewConfiguration()

    // 初始的矩阵
    private val mBaseMatrix = Matrix()

    // 带有所有变化信息的矩阵，如平移和缩放
    private val mChangeMatrix = Matrix()

    // 最终绘制使用的矩阵
    private val mDrawMatrix = Matrix()

    // 方便读取矩阵的值，创建一个公用的数组
    private val mMatrixValues = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

    // 辅助计算坐标映射，避免频繁创建 RectF 对象
    private val mRect = RectF()

    // 放大时的 focus point，方便再次双击缩小回去时，图片不会突然移动
    private var mImageScaleFocusPoint = PointF()

    // 图片的最小缩放
    private var mImageScaleMin = 1f

    // 图片的最大缩放，这个值取决于图片的尺寸
    private var mImageScaleMax = 0f

    // 当前的动画实例
    private var mAnimator: android.animation.Animator? = null

    // 外部注册的回调
    lateinit var callback: PhotoViewCallback

    // 是否可缩放
    var zoomable = true

    // 拖拽方向
    var draggableDirection = DIRECTION_ALL

    // 弹簧效果的方向
    var bounceDirection = DIRECTION_ALL

    var zoomDuration: Long

        get() {
            return mConfiguration.zoomDuration
        }
        set(value) {
            mConfiguration.zoomDuration = value
        }

    var zoomInterpolator: TimeInterpolator

        get() {
            return mConfiguration.zoomInterpolator
        }
        set(value) {
            mConfiguration.zoomInterpolator = value
        }

    var zoomSlopFactor: Float

        get() {
            return mConfiguration.zoomSlopFactor
        }
        set(value) {
            mConfiguration.zoomSlopFactor = value
        }

    var bounceDistance: Float

        get() {
            return mConfiguration.bounceDistance
        }
        set(value) {
            mConfiguration.bounceDistance = value
        }

    var bounceDuration: Long

        get() {
            return mConfiguration.bounceDuration
        }
        set(value) {
            mConfiguration.bounceDuration = value
        }

    var bounceInterpolator: TimeInterpolator

        get() {
            return mConfiguration.bounceInterpolator
        }
        set(value) {
            mConfiguration.bounceInterpolator = value
        }

    var flingInterpolator: TimeInterpolator

        get() {
            return mConfiguration.flingInterpolator
        }
        set(value) {
            mConfiguration.flingInterpolator = value
        }

    var minScale: Float

        get() {
            return mConfiguration.minScale
        }
        set(value) {
            mConfiguration.minScale = value
        }

    var maxScale: Float

        get() {
            return mConfiguration.maxScale
        }
        set(value) {
            mConfiguration.maxScale = value
        }


    private val mGestureDetector: GestureDetector by lazy {
        GestureDetector(context, object: GestureListener {

            override fun onDrag(x: Float, y: Float): Boolean {

                var dx = x
                var dy = y

                if (dx > 0) {
                    if (draggableDirection and DIRECTION_RIGHT == 0) {
                        dx = 0f
                    }
                }
                else if (dx < 0) {
                    if (draggableDirection and DIRECTION_LEFT == 0) {
                        dx = 0f
                    }
                }

                if (dy > 0) {
                    if (draggableDirection and DIRECTION_BOTTOM == 0) {
                        dy = 0f
                    }
                }
                else if (dy < 0) {
                    if (draggableDirection and DIRECTION_TOP == 0) {
                        dy = 0f
                    }
                }

                return translate(dx, dy, true)

            }

            override fun onDragEnd(isScaling: Boolean, isFling: Boolean) {
                callback.onDragEnd()
                if (!isScaling && !isFling) {
                    bounceIfNeeded()
                }
            }

            override fun onDragStart(): Boolean {
                if (draggableDirection != DIRECTION_NO && mImageWidth > 0) {
                    callback.onDragStart()
                    return true
                }
                return false
            }

            override fun onScale(scale: Float, focusPoint: PointF, lastFocusPoint: PointF) {

                var scaleFactor = scale

                // 缩放之后的值
                val scaleValue = getScale() * scale

                // 缩放后比最大尺寸还大时
                // 需要逐渐加大阻尼效果，到达一个阈值后不可再放大
                if (scaleValue > maxScale && scaleFactor > 1) {

                    // 获取一个 0-1 之间的数
                    val ratio = Math.min(1f, (scaleValue - maxScale) / (maxScale * zoomSlopFactor))

                    scaleFactor -= (scaleFactor - 1) * bounceInterpolator.getInterpolation(ratio)

                }
                // 缩放后比最小尺寸还小时
                // 需要逐渐加大阻尼效果，到达一个阈值后不可再缩小
                else if (scaleValue < minScale && scaleFactor < 1) {

                    // 获取一个 0-1 之间的数
                    val ratio = Math.min(1f, (minScale - scaleValue) / (minScale * zoomSlopFactor))

                    scaleFactor += (1 - scaleFactor) * bounceInterpolator.getInterpolation(ratio)

                }

                mAnimator?.cancel()

                zoom(scaleFactor, focusPoint.x, focusPoint.y, true)

                translate(focusPoint.x - lastFocusPoint.x, focusPoint.y - lastFocusPoint.y, true, 0f, true)

                refresh()

            }

            override fun onScaleStart(): Boolean {
                return zoomable && mImageWidth > 0
            }

            override fun onScaleEnd() {

                val from = getScale()
                var to = from

                if (from > maxScale) {
                    to = maxScale
                }
                else if (from < minScale) {
                    to = minScale
                }
Log.d("photoview", "onscaleend $from $to")
                if (to != from) {
                    startZoomAnimator(from, to, bounceDuration, zoomInterpolator)
                }

            }

            override fun onFling(velocityX: Float, velocityY: Float): Boolean {
                val imageRect = getImageRect()
                if (imageRect != null) {

                    var vx = 0f
                    var vy = 0f

                    if (velocityX > 0) {
                        // 往右滑
                        if (imageRect.left < 0) {
                            vx = velocityX
                        }
                    }
                    else if (velocityX < 0) {
                        // 往左滑
                        if (mViewWidth - imageRect.right < 0) {
                            vx = velocityX
                        }
                    }

                    if (velocityY > 0) {
                        // 往下滑
                        if (imageRect.top < 0) {
                            vy = velocityY
                        }
                    }
                    else if (velocityY < 0) {
                        // 往上滑
                        if (mViewHeight - imageRect.bottom < 0) {
                            vy = velocityY
                        }
                    }

                    if (vx != 0f || vy != 0f) {
                        startFlingAnimator(vx, vy, flingInterpolator)
                        return true
                    }

                }
                return false
            }

            override fun onLongPress(x: Float, y: Float) {
                callback.onLongPress(x, y)
            }

            override fun onTap(x: Float, y: Float) {
                mAnimator?.cancel()
                callback.onTap(x, y)
            }

            override fun onDoubleTap(x: Float, y: Float) {

                if (!zoomable) {
                    return
                }

                val from = getScale()

                // 当与最小缩放值很近时，下次缩放到最大
                val to = if (Math.abs(maxScale - from) > 0.1) {
                    mImageScaleFocusPoint.set(x, y)
                    maxScale
                }
                else {
                    minScale
                }

                startZoomAnimator(from, to, zoomDuration, zoomInterpolator)

            }
        })
    }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init()
    }

    private fun init() {

        if (mScaleType == null) {
            mScaleType = ScaleType.CENTER_INSIDE
        }

        // 这句非常重要，相当于拦截了 image view 的默认实现
        // 把 scaleType 配置或通过用户交互产生的平移、缩放，内部均通过矩阵来完成
        super.setScaleType(ScaleType.MATRIX)

        addOnLayoutChangeListener(this)

    }

    override fun setScaleType(scaleType: ScaleType) {
        mScaleType = scaleType
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        updateBaseMatrix()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        updateBaseMatrix()
    }

    override fun setImageURI(uri: Uri?) {
        super.setImageURI(uri)
        updateBaseMatrix()
    }



    private fun startZoomAnimator(from: Float, to: Float, duration: Long, interpolator: TimeInterpolator) {

        mAnimator?.cancel()

        val animator = ValueAnimator.ofFloat(from, to)
        var lastValue = from

        animator.duration = duration
        animator.interpolator = interpolator
        animator.addUpdateListener {

            val value = it.animatedValue as Float

            zoom(value / lastValue, mImageScaleFocusPoint.x, mImageScaleFocusPoint.y)

            checkImageBounds { dx, dy ->
                translate(dx, dy)
            }

            lastValue = value

        }
        animator.addListener(object: AnimatorListenerAdapter() {
            // 动画被取消，onAnimationEnd() 也会被调用
            override fun onAnimationEnd(animation: android.animation.Animator?) {
                if (animation == mAnimator) {
                    mAnimator = null
                }
            }
        })
        animator.start()

        mAnimator = animator

    }

    private fun startFlingAnimator(vx: Float, vy: Float, interpolator: TimeInterpolator) {

        mAnimator?.cancel()

        val animatorX = ValueAnimator.ofFloat(vx, 0f)
        val animatorY = ValueAnimator.ofFloat(vy, 0f)
        val animatorSet = AnimatorSet()

        animatorX.addUpdateListener {
            translate(it.animatedValue as Float, 0f, true, 10f)
        }
        animatorY.addUpdateListener {
            translate(0f, it.animatedValue as Float, true, 10f)
        }

        animatorSet.interpolator = interpolator
        animatorSet.playTogether(animatorX, animatorY)
        animatorSet.start()

        animatorSet.addListener(object: AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator?) {
                if (animation == mAnimator) {
                    mAnimator = null
                    bounceIfNeeded()
                }
            }
        })

        mAnimator = animatorSet

    }

    private fun startBounceAnimator(deltaX: Float, deltaY: Float) {

//        mAnimator?.cancel()

        val animatorX = ValueAnimator.ofFloat(deltaX, 0f)
        val animatorY = ValueAnimator.ofFloat(deltaY, 0f)
        val animatorSet = AnimatorSet()

        var lastX = deltaX
        var lastY = deltaY

        animatorX.addUpdateListener {
            val value = it.animatedValue as Float
            translate(lastX - value, 0f)
            lastX = value
        }
        animatorY.addUpdateListener {
            val value = it.animatedValue as Float
            translate(0f, lastY - value)
            lastY = value
        }

        animatorSet.interpolator = bounceInterpolator
        animatorSet.playTogether(animatorX, animatorY)
        animatorSet.start()

        animatorSet.addListener(object: AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator?) {
                if (animation == mAnimator) {
                    mAnimator = null
                }
            }
        })

        mAnimator = animatorSet

    }


    /**
     * 平移，如果超出允许平移的边界或没移动，返回 false
     */
    fun translate(x: Float, y: Float, checkBounds: Boolean = false, limit: Float = 0f, silent: Boolean = false): Boolean {

        var deltaX = x
        var deltaY = y

        if (deltaX != 0f || deltaY != 0f) {

            // 拖出边界要加阻力，离 dragBounceDistance 距离越近阻力越大
            if (checkBounds && bounceDirection != DIRECTION_NO) {
                checkImageBounds { dx, dy ->

                    // 按短边比例取值
                    val distance = bounceDistance * Math.min(mViewWidth, mViewHeight)

                    if (dx != 0f) {
                        val ratioX = Math.min(1f, Math.abs(dx) / distance)
                        deltaX -= bounceInterpolator.getInterpolation(ratioX) * deltaX
                    }

                    if (dy != 0f) {
                        val ratioY = Math.min(1f, Math.abs(dy) / distance)
                        deltaY -= bounceInterpolator.getInterpolation(ratioY) * deltaY
                    }

                }
            }

            if (deltaX != 0f || deltaY != 0f) {

                mChangeMatrix.postTranslate(deltaX, deltaY)
                updateDrawMatrix()

                if (bounceDirection != DIRECTION_ALL) {
                    checkImageBounds { dx, dy ->

                        var offsetX = dx
                        var offsetY = dy

                        if (offsetX > 0) {
                            if (bounceDirection and DIRECTION_RIGHT != 0) {
                                offsetX = 0f
                            }
                        }
                        else if (offsetX < 0) {
                            if (bounceDirection and DIRECTION_LEFT != 0) {
                                offsetX = 0f
                            }
                        }

                        if (offsetY > 0) {
                            if (bounceDirection and DIRECTION_BOTTOM != 0) {
                                offsetY = 0f
                            }
                        }
                        else if (offsetY < 0) {
                            if (bounceDirection and DIRECTION_TOP != 0) {
                                offsetY = 0f
                            }
                        }

                        if (offsetX != 0f || offsetY != 0f) {

                            deltaX += offsetX
                            deltaY += offsetY

                            mChangeMatrix.postTranslate(offsetX, offsetY)
                            updateDrawMatrix()

                        }

                    }
                }

                if (!silent) {
                    refresh()
                }

                callback.onDrag(
                    getValue(mChangeMatrix, Matrix.MTRANS_X),
                    getValue(mChangeMatrix, Matrix.MTRANS_Y)
                )

            }
        }

        return Math.abs(deltaX) > limit || Math.abs(deltaY) > limit

    }

    fun zoom(scale: Float, focusX: Float, focusY: Float, silent: Boolean = false): Boolean {

        if (scale != 1f) {

            // 记录中心点，方便双击缩小时，不会位移
            mImageScaleFocusPoint.set(focusX, focusY)

            // 缩放
            mChangeMatrix.postScale(scale, scale, focusX, focusY)

            // 更新最后起作用的矩阵
            updateDrawMatrix()

            if (!silent) {
                refresh()
            }

            callback.onScale(getScale())

            return true
        }

        return false

    }

    /**
     * 隐藏 action bar, status bar 之类的会触发布局变化
     */
    override fun onLayoutChange(view: View?,
                                left: Int, top: Int, right: Int, bottom: Int,
                                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
    ) {
        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
            updateBaseMatrix()
        }
    }

    /**
     * 手势全部交给 GestureDetector 处理
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return mGestureDetector.onTouchEvent(event)
    }

    /**
     * 获取图片的真实尺寸
     */
    fun getImageRect(): RectF? {
        if (mImageWidth > 0f && mImageHeight > 0f) {
            mRect.set(0f, 0f, mImageWidth, mImageHeight)
            mDrawMatrix.mapRect(mRect)
            return mRect
        }
        return null
    }

    /**
     * 根据当前的绘制矩阵刷新视图
     */
    fun refresh() {
        imageMatrix = mDrawMatrix
        invalidate()
    }

    /**
     * 检测当前图片的实际位置和尺寸是否越界
     */
    private fun checkImageBounds(imageRect: RectF, action: (dx: Float, dy: Float) -> Unit) {

        val imageWidth = imageRect.width()
        val imageHeight = imageRect.height()

        val viewWidth = mViewWidth
        val viewHeight = mViewHeight

        var deltaX = 0f
        var deltaY = 0f

        if (imageWidth <= viewWidth) {
            when (mScaleType) {
                ScaleType.FIT_START -> {
                    deltaX = -imageRect.left
                }
                ScaleType.FIT_END -> {
                    deltaX = viewWidth - imageWidth - imageRect.left
                }
                else -> {
                    deltaX = (viewWidth - imageWidth) / 2 - imageRect.left
                }
            }
        }
        else if (imageRect.left > 0) {
            deltaX = -imageRect.left
        }
        else if (imageRect.right < viewWidth) {
            deltaX = viewWidth - imageRect.right
        }

        if (imageHeight <= viewHeight) {
            when (mScaleType) {
                ScaleType.FIT_START -> {
                    deltaY = -imageRect.top
                }
                ScaleType.FIT_END -> {
                    deltaY = viewHeight - imageHeight - imageRect.top
                }
                else -> {
                    deltaY = (viewHeight - imageHeight) / 2 - mRect.top
                }
            }
        }
        else if (imageRect.top > 0) {
            deltaY = -imageRect.top
        }
        else if (imageRect.bottom < viewHeight) {
            deltaY = viewHeight - imageRect.bottom
        }

        if (deltaX != 0f || deltaY != 0f) {
            action(deltaX, deltaY)
        }

    }

    private fun checkImageBounds(action: (dx: Float, dy: Float) -> Unit) {
        val imageRect = getImageRect()
        if (imageRect != null) {
            checkImageBounds(imageRect, action)
        }
    }

    private fun bounceIfNeeded() {

        var deltaX = 0f
        var deltaY = 0f

        checkImageBounds { dx, dy ->
            deltaX = dx
            deltaY = dy
        }

        if (deltaX != 0f || deltaY != 0f) {
            startBounceAnimator(deltaX, deltaY)
        }

    }

    private fun updateBaseMatrix() {

        if (mImageWidth > 0 && mImageHeight > 0) {

            mBaseMatrix.reset()
            mChangeMatrix.reset()

            val widthScale = mViewWidth / mImageWidth
            val heightScale = mViewHeight / mImageHeight

            val minScale = Math.min(widthScale, heightScale)
            val maxScale = Math.max(widthScale, heightScale)

            when (mScaleType) {
                ScaleType.CENTER -> {
                    mBaseMatrix.postTranslate(
                        (mViewWidth - mImageWidth) / 2,
                        (mViewHeight - mImageHeight) / 2
                    )
                }
                ScaleType.CENTER_CROP -> {
                    mBaseMatrix.postScale(maxScale, maxScale)
                    mBaseMatrix.postTranslate(
                        (mViewWidth - mImageWidth * maxScale) / 2,
                        (mViewHeight - mImageHeight * maxScale) / 2
                    )
                }
                ScaleType.CENTER_INSIDE -> {
                    mBaseMatrix.postScale(minScale, minScale)
                    mBaseMatrix.postTranslate(
                        (mViewWidth - mImageWidth * minScale) / 2,
                        (mViewHeight - mImageHeight * minScale) / 2
                    )
                }
                else -> {

                    val srcRect = RectF(0f, 0f, mImageWidth, mImageHeight)
                    val dstRect = RectF(0f, 0f, mViewWidth, mViewHeight)

                    when (mScaleType) {
                        ScaleType.FIT_CENTER -> {
                            mBaseMatrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.CENTER)
                        }
                        ScaleType.FIT_START -> {
                            mBaseMatrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.START)
                        }
                        ScaleType.FIT_END -> {
                            mBaseMatrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.END)
                        }
                        ScaleType.FIT_XY -> {
                            mBaseMatrix.setRectToRect(srcRect, dstRect, Matrix.ScaleToFit.FILL)
                        }
                    }

                }
            }

            imageMatrix = mBaseMatrix

            val baseScale = getValue(mBaseMatrix, Matrix.MSCALE_X)
            mImageScaleMin = minScale / baseScale
            mImageScaleMax = maxScale / baseScale

            callback.onReset()

        }
    }

    private fun updateDrawMatrix() {
        mDrawMatrix.set(mBaseMatrix)
        mDrawMatrix.postConcat(mChangeMatrix)
    }

    private fun getValue(matrix: Matrix, whichValue: Int): Float {
        matrix.getValues(mMatrixValues)
        return mMatrixValues[ whichValue ]
    }

    private fun getScale(): Float {
        return getValue(mChangeMatrix, Matrix.MSCALE_X)
    }

}
