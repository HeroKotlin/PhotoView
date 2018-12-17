package com.github.herokotlin.photoview

import android.animation.*
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
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

    // 当前的位移动画实例
    private var mTranslateAnimator: android.animation.Animator? = null

    // 当前的缩放动画实例
    private var mZoomAnimator: android.animation.Animator? = null

    private var mMinScale = 1f

    private var mMaxScale = 1f

    private var mScale = 1f

    // 外部注册的回调
    lateinit var callback: PhotoViewCallback

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

    var flingInterpolator: TimeInterpolator = DecelerateInterpolator()

    var scaleType = ScaleType.FILL_WIDTH


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

                callback.onDragEnd()

                if (!isFling) {
                    updateImageScaleAndPosition(true)
                }

            }

            override fun onDragStart(): Boolean {
                if (draggableDirection != DIRECTION_NO && mImageWidth > 0) {
                    mTranslateAnimator?.cancel()
                    callback.onDragStart()
                    return true
                }
                return false
            }

            override fun onScale(scale: Float, focusPoint: PointF, lastFocusPoint: PointF) {

                var scaleFactor = scale

                // 缩放之后的值
                val scaleValue = mScale * scale

                // 缩放后比最大尺寸还大时
                // 需要逐渐加大阻尼效果，到达一个阈值后不可再放大
                if (scaleValue > mMaxScale && scaleFactor > 1) {

                    // 获取一个 0-1 之间的数
                    val ratio = Math.min(1f, (scaleValue - mMaxScale) / (mMaxScale * zoomSlopFactor))

                    scaleFactor -= (scaleFactor - 1) * bounceInterpolator.getInterpolation(ratio)

                }
                // 缩放后比最小尺寸还小时
                // 需要逐渐加大阻尼效果，到达一个阈值后不可再缩小
                else if (scaleValue < mMinScale && scaleFactor < 1) {

                    // 获取一个 0-1 之间的数
                    val ratio = Math.min(1f, (mMinScale - scaleValue) / (mMinScale * zoomSlopFactor))

                    scaleFactor += (1 - scaleFactor) * bounceInterpolator.getInterpolation(ratio)

                }

                zoom(scaleFactor, focusPoint.x, focusPoint.y, true)

                translate(focusPoint.x - lastFocusPoint.x, focusPoint.y - lastFocusPoint.y, true, 0f, true)

                refresh()

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
                val imageRect = getImageRect()
                if (imageRect != null && mZoomAnimator == null) {

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
                if (mZoomAnimator != null) {
                    return
                }
                callback.onLongPress(x, y)
            }

            override fun onTap(x: Float, y: Float) {
                if (mZoomAnimator != null) {
                    return
                }
                callback.onTap(x, y)
            }

            override fun onDoubleTap(x: Float, y: Float) {

                if (!zoomable && mZoomAnimator != null) {
                    return
                }

                val from = mScale

                // 当与最小缩放值很近时，下次缩放到最大
                val to = if (mMaxScale - from > 0.001) {
                    mImageScaleFocusPoint.set(x, y)
                    mMaxScale
                }
                else {
                    mMinScale
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

        // 这句非常重要，相当于拦截了 image view 的默认实现
        // 把 scaleType 配置或通过用户交互产生的平移、缩放，内部均通过矩阵来完成
        super.setScaleType(ImageView.ScaleType.MATRIX)

        addOnLayoutChangeListener(this)

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



    private fun updateImageScaleAndPosition(bounce: Boolean) {

        val from = mScale
        val to = when {
            from > mMaxScale -> {
                mMaxScale
            }
            from < mMinScale -> {
                mMinScale
            }
            else -> {
                from
            }
        }

        if (to != from) {
            startZoomAnimator(from, to, bounceDuration, bounceInterpolator)
        }
        else if (bounce) {
            checkImageBounds { dx, dy ->
                startBounceAnimator(dx, dy, bounceInterpolator)
            }
        }

    }

    private fun startZoomAnimator(from: Float, to: Float, duration: Long, interpolator: TimeInterpolator) {

        mZoomAnimator?.cancel()

        val animator = ValueAnimator.ofFloat(from, to)
        var lastValue = from

        animator.duration = duration
        animator.interpolator = interpolator

        animator.addUpdateListener {
            val value = it.animatedValue as Float
            zoom(value / lastValue, mImageScaleFocusPoint.x, mImageScaleFocusPoint.y)
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


        // 计算缩放后的位移
        val tempMatrix = Matrix(mChangeMatrix)

        val scale = to / from
        mChangeMatrix.postScale(scale, scale, mImageScaleFocusPoint.x, mImageScaleFocusPoint.y)
        updateDrawMatrix()

        var deltaX = 0f
        var deltaY = 0f

        checkImageBounds { dx, dy ->
            deltaX = dx
            deltaY = dy
        }

        mChangeMatrix.set(tempMatrix)
        updateDrawMatrix()

        if (deltaX != 0f || deltaY != 0f) {
            startBounceAnimator(deltaX, deltaY, interpolator)
        }


        animator.start()

        mZoomAnimator = animator

    }

    private fun startFlingAnimator(vx: Float, vy: Float, interpolator: TimeInterpolator) {

        mTranslateAnimator?.cancel()

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
                if (animation == mTranslateAnimator) {
                    mTranslateAnimator = null
                    checkImageBounds { dx, dy ->
                        startBounceAnimator(dx, dy, interpolator)
                    }
                }
            }
        })

        mTranslateAnimator = animatorSet

    }

    private fun startBounceAnimator(deltaX: Float, deltaY: Float, interpolator: TimeInterpolator) {

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
    fun translate(x: Float, y: Float, checkBounds: Boolean = false, limit: Float = 0f, silent: Boolean = false): Boolean {

        var deltaX = x
        var deltaY = y

        if (deltaX != 0f || deltaY != 0f) {

            // 拖出边界要加阻力，离 dragBounceDistance 距离越近阻力越大
            if (checkBounds && bounceDirection != DIRECTION_NO) {
                checkImageBounds { dx, dy ->

                    if (dx != 0f) {
                        val ratioX = Math.min(1f, Math.abs(dx) / (mViewWidth * bounceDistance))
                        deltaX -= bounceInterpolator.getInterpolation(ratioX) * deltaX
                    }

                    if (dy != 0f) {
                        val ratioY = Math.min(1f, Math.abs(dy) / (mViewHeight * bounceDistance))
                        deltaY -= bounceInterpolator.getInterpolation(ratioY) * deltaY
                    }

                }
            }

            if (deltaX != 0f || deltaY != 0f) {

                mImageScaleFocusPoint.x += deltaX
                mImageScaleFocusPoint.y += deltaY

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

            mScale *= scale

            callback.onScaleChange(mScale / mMinScale)

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

        val deltaX = when {
            imageWidth <= viewWidth -> {
                (viewWidth - imageWidth) / 2 - imageRect.left
            }
            imageRect.left > 0 -> {
                -imageRect.left
            }
            imageRect.right < viewWidth -> {
                viewWidth - imageRect.right
            }
            else -> {
                0f
            }
        }

        val deltaY = when {
            imageHeight <= viewHeight -> {
                (viewHeight - imageHeight) / 2 - mRect.top
            }
            imageRect.top > 0 -> {
                -imageRect.top
            }
            imageRect.bottom < viewHeight -> {
                viewHeight - imageRect.bottom
            }
            else -> {
                0f
            }
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

    private fun updateBaseMatrix() {

        if (mImageWidth > 0 && mImageHeight > 0) {

            mBaseMatrix.reset()
            mChangeMatrix.reset()

            val widthScale = mViewWidth / mImageWidth
            val heightScale = mViewHeight / mImageHeight

            val scale = when (scaleType) {
                ScaleType.FILL_WIDTH -> {
                    widthScale
                }
                ScaleType.FILL_HEIGHT -> {
                    heightScale
                }
                else -> {
                    Math.min(widthScale, heightScale)
                }
            }

            mBaseMatrix.postScale(scale, scale)

            val imageWidth = mImageWidth * scale
            val imageHeight = mImageHeight * scale

            val deltaX = if (mViewWidth > imageWidth) {
                (mViewWidth - imageWidth) / 2
            }
            else {
                0f
            }

            val deltaY = if (mViewHeight > imageHeight) {
                (mViewHeight - imageHeight) / 2
            }
            else {
                0f
            }

            mBaseMatrix.postTranslate(deltaX, deltaY)

            imageMatrix = mBaseMatrix

            mMaxScale = 3 * scale
            mMinScale = scale
            mScale = scale

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

    enum class ScaleType {
        FIT,
        FILL_WIDTH,
        FILL_HEIGHT,
    }

}
