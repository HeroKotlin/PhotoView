package com.github.herokotlin.photoview

import android.animation.*
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView

class PhotoView : ImageView {

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

    private var mContentWidth = 0f

        get() {
            return width - contentInset.left - contentInset.right
        }

    private var mContentHeight = 0f

        get() {
            return height - contentInset.top - contentInset.bottom
        }

    private var mImageWidth = 0f

    private var mImageHeight = 0f

    // 初始的矩阵
    private val mBaseMatrix = Matrix()

    // 带有所有变化信息的矩阵，如平移和缩放
    private val mChangeMatrix = Matrix()

    // 最终绘制使用的矩阵
    private val mDrawMatrix = Matrix()

    // 方便读取矩阵的值，创建一个公用的数组
    private val mMatrixValues = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    
    // 当前的位移动画实例
    private var mTranslateAnimator: android.animation.Animator? = null

    // 当前的缩放动画实例
    private var mZoomAnimator: android.animation.Animator? = null

    // 放大时的 focus point，方便再次双击缩小回去时，图片不会突然移动
    private var mFocusPoint = PointF()
    
    // 拖拽方向
    var draggableDirection = DIRECTION_ALL

    // 是否可缩放
    var zoomable = true

    var zoomDuration = 300L

    var zoomInterpolator: TimeInterpolator = DecelerateInterpolator()

    var zoomSlopFactor = 0.4f

    var bounceDirection = DIRECTION_ALL

    var bounceDistance = 0.4f

    var bounceDuration = 250L

    var bounceInterpolator: TimeInterpolator = DecelerateInterpolator()

    var flingInterpolator: TimeInterpolator = LinearInterpolator()

    var contentInset = ContentInset.zero

        set(value) {

            val oldScale = scale
            val oldOrigin = imageOrigin

            // 开始更新
            field = value

            updateBaseMatrix(true)

            // 还原为原来的尺寸
            val newScale = scale
            zoom(oldScale / newScale, true)

            // 还原成原来的位置
            val newOrigin = imageOrigin
            translate(oldOrigin.x - newOrigin.x, oldOrigin.y - newOrigin.y, false, true)

            // 发射
            imageMatrix = mDrawMatrix

        }

    var scaleType = ScaleType.FILL_WIDTH

    // 以下三个外部只读
    var minScale = 1f

    var maxScale = 1f

    var scale: Float

        get() {
            return getValue(mDrawMatrix, Matrix.MSCALE_X)
        }

        set(value) {

        }

    var imageOrigin: PointF

        get() {
            val x = getValue(mDrawMatrix, Matrix.MTRANS_X)
            val y = getValue(mDrawMatrix, Matrix.MTRANS_Y)
            return PointF(x, y)
        }

        set(value) {

            val oldOrigin = imageOrigin

            val dx = value.x - oldOrigin.x
            val dy = value.y - oldOrigin.y

            mChangeMatrix.setTranslate(dx, dy)

            updateDrawMatrix()
            imageMatrix = mDrawMatrix

        }

    var imageSize: Size

        get() {
            return Size(mImageWidth * scale, mImageHeight * scale)
        }

        set(value) {
            // 只读
        }

    var onReset: (() -> Unit)? = null

    var onTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null

    var onDragStart: (() -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    var onScaleChange: (() -> Unit)? = null
    var onOriginChange: (() -> Unit)? = null

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

            override fun onDragEnd(isFling: Boolean) {

                onDragEnd?.invoke()

                if (!isFling) {
                    updateImageScaleAndPosition(true)
                }

            }

            override fun onDragStart(): Boolean {
                if (draggableDirection != DIRECTION_NO && mImageWidth > 0) {
                    mTranslateAnimator?.cancel()
                    onDragStart?.invoke()
                    return true
                }
                return false
            }

            override fun onScale(scaleFactor: Float, focusPoint: PointF, lastFocusPoint: PointF) {

                var zoomFactor = scaleFactor

                // 缩放之后的值
                val scaleValue = scale * scaleFactor

                // 缩放后比最大尺寸还大时
                // 需要逐渐加大阻尼效果，到达一个阈值后不可再放大
                if (scaleValue > maxScale && zoomFactor > 1) {

                    // 获取一个 0-1 之间的数
                    val ratio = Math.min(1f, (scaleValue - maxScale) / (maxScale * zoomSlopFactor))

                    zoomFactor -= (zoomFactor - 1) * bounceInterpolator.getInterpolation(ratio)

                }
                // 缩放后比最小尺寸还小时
                // 需要逐渐加大阻尼效果，到达一个阈值后不可再缩小
                else if (scaleValue < minScale && zoomFactor < 1) {

                    // 获取一个 0-1 之间的数
                    val ratio = Math.min(1f, (minScale - scaleValue) / (minScale * zoomSlopFactor))

                    zoomFactor += (1 - zoomFactor) * bounceInterpolator.getInterpolation(ratio)

                }

                setFocusPoint(focusPoint.x, focusPoint.y)

                zoom(zoomFactor, true)

                translate(focusPoint.x - lastFocusPoint.x, focusPoint.y - lastFocusPoint.y, true, true)

                imageMatrix = mDrawMatrix

            }

            override fun onScaleStart(): Boolean {
                if (zoomable && mImageWidth > 0) {
                    mZoomAnimator?.cancel()
                    return true
                }
                return false
            }

            override fun onScaleEnd(isDragging: Boolean) {
                updateImageScaleAndPosition(!isDragging)
            }

            override fun onFling(velocityX: Float, velocityY: Float): Boolean {
                if (mZoomAnimator == null) {

                    val origin = imageOrigin
                    val size = imageSize

                    var vx = 0f
                    var vy = 0f

                    if (velocityX > 0) {
                        // 往右滑
                        if (origin.x < 0) {
                            vx = velocityX
                        }
                    }
                    else if (velocityX < 0) {
                        // 往左滑
                        if (mContentWidth - (origin.x + size.width) < 0) {
                            vx = velocityX
                        }
                    }

                    if (velocityY > 0) {
                        // 往下滑
                        if (origin.y < 0) {
                            vy = velocityY
                        }
                    }
                    else if (velocityY < 0) {
                        // 往上滑
                        if (mContentHeight - (origin.y + size.height) < 0) {
                            vy = velocityY
                        }
                    }

                    if (vx != 0f || vy != 0f) {
                        startFlingAnimation(vx, vy, flingInterpolator)
                        return true
                    }

                }
                return false
            }

            override fun onLongPress(x: Float, y: Float) {
                if (mZoomAnimator != null) {
                    return
                }
                onLongPress?.invoke()
            }

            override fun onTap(x: Float, y: Float) {
                if (mZoomAnimator != null) {
                    return
                }
                onTap?.invoke()
            }

            override fun onDoubleTap(x: Float, y: Float) {

                if (!zoomable && mZoomAnimator != null) {
                    return
                }

                val from = scale

                // 距离谁比较远就去谁
                val to = if (maxScale - scale > scale - minScale) {
                    maxScale
                }
                else {
                    minScale
                }

                setFocusPoint(x, y)

                startZoomAnimation(from, to, zoomDuration, zoomInterpolator)

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

        // 这句非常重要，相当于拦截了 image view 的默认实现
        // 把 scaleType 配置或通过用户交互产生的平移、缩放，内部均通过矩阵来完成
        super.setScaleType(ImageView.ScaleType.MATRIX)

    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        updateImage()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        updateImage()
    }

    override fun setImageURI(uri: Uri?) {
        super.setImageURI(uri)
        updateImage()
    }

    private fun updateImage() {

        mImageWidth = if (drawable != null) drawable.intrinsicWidth.toFloat() else 0f
        mImageHeight = if (drawable != null) drawable.intrinsicHeight.toFloat() else 0f

        updateBaseMatrix(false)

    }

    private fun updateImageScaleAndPosition(bounce: Boolean) {

        val from = scale
        val to = when {
            from > maxScale -> {
                maxScale
            }
            from < minScale -> {
                minScale
            }
            else -> {
                from
            }
        }

        if (to != from) {
            startZoomAnimation(from, to, bounceDuration, bounceInterpolator)
        }
        else if (bounce) {
            checkImageBounds { dx, dy ->
                startTranslateAnimation(dx, dy, bounceInterpolator)
            }
        }

    }

    fun temp(update: (Matrix, Matrix) -> Unit, read: () -> Unit) {

        val baseMatrix = Matrix(mBaseMatrix)
        val changeMatrix = Matrix(mChangeMatrix)

        update(mBaseMatrix, mChangeMatrix)

        updateDrawMatrix()

        read()

        mBaseMatrix.set(baseMatrix)
        mChangeMatrix.set(changeMatrix)

        updateDrawMatrix()

    }

    fun startZoomAnimation(from: Float, to: Float) {

        startZoomAnimation(from, to, zoomDuration, zoomInterpolator)

    }

    private fun startZoomAnimation(from: Float, to: Float, duration: Long, interpolator: TimeInterpolator) {

        mZoomAnimator?.cancel()

        val animator = ValueAnimator.ofFloat(from, to)
        var lastValue = from

        animator.duration = duration
        animator.interpolator = interpolator

        animator.addUpdateListener {
            val value = it.animatedValue as Float
            zoom(value / lastValue)
            lastValue = value
        }

        animator.addListener(object: AnimatorListenerAdapter() {
            // 动画被取消，onAnimationEnd() 也会被调用
            override fun onAnimationEnd(animation: android.animation.Animator?) {
                if (animation == mZoomAnimator) {
                    mZoomAnimator = null
                }
            }
        })

        var deltaX = 0f
        var deltaY = 0f

        // 计算缩放后的位移
        temp({ _, changeMatrix ->
            val scale = to / from
            changeMatrix.postScale(scale, scale, mFocusPoint.x, mFocusPoint.y)
        }) {
            checkImageBounds { dx, dy ->
                deltaX = dx
                deltaY = dy
            }
        }

        if (deltaX != 0f || deltaY != 0f) {
            startTranslateAnimation(deltaX, deltaY, interpolator)
        }

        animator.start()

        mZoomAnimator = animator

    }

    private fun startFlingAnimation(vx: Float, vy: Float, interpolator: TimeInterpolator) {

        mTranslateAnimator?.cancel()

        val animatorX = ValueAnimator.ofFloat(vx, 0f)
        val animatorY = ValueAnimator.ofFloat(vy, 0f)
        val animatorSet = AnimatorSet()

        animatorX.addUpdateListener {
            translate(it.animatedValue as Float, 0f, true)
        }
        animatorY.addUpdateListener {
            translate(0f, it.animatedValue as Float, true)
        }

        animatorSet.interpolator = interpolator
        animatorSet.playTogether(animatorX, animatorY)
        animatorSet.start()

        animatorSet.addListener(object: AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator?) {
                if (animation == mTranslateAnimator) {
                    mTranslateAnimator = null
                    checkImageBounds { dx, dy ->
                        startTranslateAnimation(dx, dy, interpolator)
                    }
                }
            }
        })

        mTranslateAnimator = animatorSet

    }

    fun startTranslateAnimation(deltaX: Float, deltaY: Float, interpolator: TimeInterpolator) {

        mTranslateAnimator?.cancel()

        val animatorX = ValueAnimator.ofFloat(deltaX, 0f)
        val animatorY = ValueAnimator.ofFloat(deltaY, 0f)

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

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(animatorX, animatorY)
        animatorSet.interpolator = interpolator
        animatorSet.start()

        animatorSet.addListener(object: AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator?) {
                if (animation == mTranslateAnimator) {
                    mTranslateAnimator = null
                }
            }
        })

        mTranslateAnimator = animatorSet

    }

    /**
     * 平移，如果超出允许平移的边界或没移动，返回 false
     */
    fun translate(dx: Float, dy: Float, checkBounds: Boolean = false, silent: Boolean = false): Boolean {

        var deltaX = dx
        var deltaY = dy

        if (deltaX != 0f || deltaY != 0f) {

            // 拖出边界要加阻力，离 dragBounceDistance 距离越近阻力越大
            if (checkBounds && bounceDirection != DIRECTION_NO) {
                checkImageBounds { offsetX, offsetY ->

                    if (offsetX != 0f) {
                        val ratioX = Math.min(1f, Math.abs(offsetX) / (mContentWidth * bounceDistance))
                        deltaX -= bounceInterpolator.getInterpolation(ratioX) * deltaX
                    }

                    if (offsetY != 0f) {
                        val ratioY = Math.min(1f, Math.abs(offsetY) / (mContentHeight * bounceDistance))
                        deltaY -= bounceInterpolator.getInterpolation(ratioY) * deltaY
                    }

                }
            }

            if (deltaX != 0f || deltaY != 0f) {

                mFocusPoint.x += deltaX
                mFocusPoint.y += deltaY

                mChangeMatrix.postTranslate(deltaX, deltaY)
                updateDrawMatrix()

                if (bounceDirection != DIRECTION_ALL) {
                    checkImageBounds { x, y ->

                        var offsetX = x
                        var offsetY = y

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
                    imageMatrix = mDrawMatrix
                }

                onOriginChange?.invoke()

            }
        }

        return Math.abs(deltaX) > 0 || Math.abs(deltaY) > 0

    }

    fun zoom(factor: Float, silent: Boolean = false) {

        if (factor == 1f) {
            return
        }

        // 缩放
        mChangeMatrix.postScale(factor, factor, mFocusPoint.x, mFocusPoint.y)

        // 更新最后起作用的矩阵
        updateDrawMatrix()

        if (!silent) {
            imageMatrix = mDrawMatrix
        }

        onScaleChange?.invoke()
        onOriginChange?.invoke()

    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateBaseMatrix(false)
    }

    /**
     * 手势全部交给 GestureDetector 处理
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return mGestureDetector.onTouchEvent(event)
    }

    /**
     * 检测当前图片的实际位置和尺寸是否越界
     */
    private fun checkImageBounds(action: (dx: Float, dy: Float) -> Unit) {

        val origin = imageOrigin
        val size = imageSize

        val viewWidth = width
        val viewHeight = height

        val contentWidth = mContentWidth
        val contentHeight = mContentHeight

        val left = contentInset.left
        val top = contentInset.top

        val right = viewWidth - contentInset.right
        val bottom = viewHeight - contentInset.bottom

        val deltaX = when {
            size.width <= contentWidth -> {
                left + (contentWidth - size.width) / 2 - origin.x
            }
            origin.x > left -> {
                left - origin.x
            }
            origin.x + size.width < right -> {
                right - (origin.x + size.width)
            }
            else -> {
                0f
            }
        }

        val deltaY = when {
            size.height <= contentHeight -> {
                top + (contentHeight - size.height) / 2 - origin.y
            }
            origin.y > top -> {
                top - origin.y
            }
            origin.y + size.height < bottom -> {
                bottom - (origin.y + size.height)
            }
            else -> {
                0f
            }
        }

        if (deltaX != 0f || deltaY != 0f) {
            action(deltaX, deltaY)
        }

    }

    fun resetMatrix(baseMatrix: Matrix, changeMatrix: Matrix) {

        baseMatrix.reset()
        changeMatrix.reset()

        val viewWidth = width
        val viewHeight = height

        val contentWidth = mContentWidth
        val contentHeight = mContentHeight

        val widthScale = contentWidth / mImageWidth
        val heightScale = contentHeight / mImageHeight

        val zoomScale = when (scaleType) {
            ScaleType.FILL_WIDTH -> {
                widthScale
            }
            ScaleType.FILL_HEIGHT -> {
                heightScale
            }
            ScaleType.FILL -> {
                Math.max(widthScale, heightScale)
            }
            else -> {
                Math.min(widthScale, heightScale)
            }
        }

        baseMatrix.postScale(zoomScale, zoomScale)

        val imageWidth = mImageWidth * zoomScale
        val imageHeight = mImageHeight * zoomScale

        val deltaX = if (viewWidth > imageWidth) {
            (viewWidth - imageWidth) / 2
        }
        else {
            0f
        }

        val deltaY = if (viewHeight > imageHeight) {
            (viewHeight - imageHeight) / 2
        }
        else {
            0f
        }

        baseMatrix.postTranslate(deltaX, deltaY)

    }

    fun updateLimitScale() {
        maxScale = if (3 * scale < 1) 1f else (3 * scale)
        minScale = scale
    }

    fun setFocusPoint(x: Float, y: Float) {

        // 经过测试，图片四角的 focusPoint 如下：
        val minX = contentInset.left
        val minY = contentInset.top
        val maxX = width - contentInset.right
        val maxY = height - contentInset.bottom

        // 当用户在 photo view 上点击时
        // 坐标落在 (0, 0) 到 (width, height) 范围内
        // 此时要把相对 photo view 的坐标转换成相对图片的坐标

        val origin = imageOrigin
        val size = imageSize

        val scaleX = (x - origin.x) / size.width
        val scaleY = (y - origin.y) / size.height

        // 最后的换算
        var focusX = Math.min(Math.max(scaleX * maxX, minX), maxX)
        var focusY = Math.min(Math.max(scaleY * maxY, minY), maxY)

        // 如果距离四角很近，可优化体验
        val threshold = 80f

        if (focusX - minX < threshold) {
            // 左上
            if (focusY - minY < threshold) {
                focusX = minX
                focusY = minY
            }
            // 左下
            else if (maxY - focusY < threshold) {
                focusX = minX
                focusY = maxY
            }
        }
        else if (maxX - focusX < threshold) {
            // 右上
            if (focusY - minY < threshold) {
                focusX = maxX
                focusY = minY
            }
            // 右下
            else if (maxY - focusY < threshold) {
                focusX = maxX
                focusY = maxY
            }
        }

        mFocusPoint.set(focusX, focusY)

    }

    private fun updateBaseMatrix(silent: Boolean) {

        if (mImageWidth > 0 && mImageHeight > 0) {

            resetMatrix(mBaseMatrix, mChangeMatrix)
            updateDrawMatrix()
            updateLimitScale()

            if (!silent) {
                imageMatrix = mDrawMatrix
                onReset?.invoke()
            }

        }
    }

    private fun updateDrawMatrix() {
        mDrawMatrix.set(mBaseMatrix)
        mDrawMatrix.postConcat(mChangeMatrix)
    }

    // getValue(imageMatrix, Matrix.MTRANS_X)
    private fun getValue(matrix: Matrix, whichValue: Int): Float {
        matrix.getValues(mMatrixValues)
        return mMatrixValues[ whichValue ]
    }

    data class Size(
        val width: Float,
        val height: Float
    )

    data class ContentInset(
        val top: Float,
        val left: Float,
        val bottom: Float,
        val right: Float
    ) {
        companion object {
            val zero = ContentInset(0f, 0f, 0f, 0f)
        }
    }

    enum class ScaleType {
        FIT,
        FILL,
        FILL_WIDTH,
        FILL_HEIGHT,
    }

}
