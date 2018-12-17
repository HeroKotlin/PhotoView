package com.github.herokotlin.photoview

import android.content.Context
import android.graphics.PointF
import android.view.GestureDetector as NativeGestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration

/**
 * 封装所有手势逻辑
 */
internal class GestureDetector(private val context: Context, private val listener: GestureListener) {

    companion object {
        private const val INVALID_POINTER_ID = -1
        private const val INVALID_POINTER_INDEX = -1
    }

    private var mPrimaryPointerId = INVALID_POINTER_ID
    private var mPrimaryLastTouchPoint = PointF(0f, 0f)

    private var mSecondaryPointerId = INVALID_POINTER_ID
    private var mSecondaryLastTouchPoint = PointF(0f, 0f)

    private var mScaleFocusPoint = PointF(0f, 0f)
    private var mScaleDistance = 0f

    private var mIsDragging = false
    private var mIsScaling = false

    private var mVelocityTracker: VelocityTracker? = null

    private val mTouchSlop: Float by lazy {
        ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    }

    private val mMinimumVelocity: Float by lazy {
        ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    }

    private val mGestureDetector: NativeGestureDetector by lazy {
        NativeGestureDetector(context, object: NativeGestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(event: MotionEvent?): Boolean {
                if (event != null) {
                    listener.onDoubleTap(event.x, event.y)
                    return true
                }
                return false
            }

            override fun onSingleTapConfirmed(event: MotionEvent?): Boolean {
                if (event != null) {
                    listener.onTap(event.x, event.y)
                    return true
                }
                return false
            }

            override fun onLongPress(event: MotionEvent?) {
                if (event != null) {
                    listener.onLongPress(event.x, event.y)
                }
            }

        })
    }

    fun onTouchEvent(event: MotionEvent): Boolean {

        var handled = true

        mGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                // 记录当前主手指
                setPrimaryPointer(event, event.actionIndex)

                // 重置状态
                setSecondaryPointer(event, INVALID_POINTER_INDEX)

                if (mVelocityTracker == null) {
                    mVelocityTracker = VelocityTracker.obtain()
                }

                mVelocityTracker?.addMovement(event)

            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (mPrimaryPointerId != INVALID_POINTER_ID
                    && mSecondaryPointerId == INVALID_POINTER_ID
                    && mPrimaryPointerId != event.getPointerId(event.actionIndex)
                ) {
                    setSecondaryPointer(event, event.actionIndex)
                }
            }

            MotionEvent.ACTION_MOVE -> {

                val hasSecondaryPointer = mSecondaryPointerId != INVALID_POINTER_ID

                val primaryIndex = event.findPointerIndex(mPrimaryPointerId)
                val primaryPoint = PointF(
                    event.getX(primaryIndex),
                    event.getY(primaryIndex)
                )

                val primaryDeltaX = primaryPoint.x - mPrimaryLastTouchPoint.x
                val primaryDeltaY = primaryPoint.y - mPrimaryLastTouchPoint.y

                val hasPrimaryMoved = hasMoved(primaryDeltaX, primaryDeltaY)

                if (hasSecondaryPointer) {

                    val secondaryIndex = event.findPointerIndex(mSecondaryPointerId)
                    val secondaryPoint = PointF(
                        event.getX(secondaryIndex),
                        event.getY(secondaryIndex)
                    )

                    val secondaryDeltaX = secondaryPoint.x - mSecondaryLastTouchPoint.x
                    val secondaryDeltaY = secondaryPoint.y - mSecondaryLastTouchPoint.y

                    val hasSecondaryMoved = hasMoved(secondaryDeltaX, secondaryDeltaY)

                    if (!mIsScaling && (hasPrimaryMoved || hasSecondaryMoved)) {
                        mIsScaling = listener.onScaleStart()
                    }

                    if (mIsScaling) {

                        val focusPoint = getMidPoint(primaryPoint, secondaryPoint)
                        val distance = getDistance(primaryPoint, secondaryPoint)

                        listener.onScale(
                            distance / mScaleDistance,
                            focusPoint,
                            mScaleFocusPoint
                        )

                        mScaleFocusPoint = focusPoint
                        mScaleDistance = distance

                        mPrimaryLastTouchPoint.set(primaryPoint.x, primaryPoint.y)
                        mSecondaryLastTouchPoint.set(secondaryPoint.x, secondaryPoint.y)

                    }

                }
                else {
                    if (mIsDragging) {
                        // 如果开启了水平或垂直滚动，有可能返回 false
                        handled = listener.onDrag(primaryDeltaX, primaryDeltaY)
                    }
                    else if (hasPrimaryMoved) {
                        mIsDragging = listener.onDragStart() && listener.onDrag(primaryDeltaX, primaryDeltaY)
                        handled = mIsDragging
                    }

                    if (mIsDragging) {
                        mPrimaryLastTouchPoint.set(primaryPoint.x, primaryPoint.y)
                        mVelocityTracker?.addMovement(event)
                    }
                }

            }

            MotionEvent.ACTION_CANCEL -> {
                setSecondaryPointer(event, INVALID_POINTER_INDEX)
                setPrimaryPointer(event, INVALID_POINTER_INDEX)
                if (mVelocityTracker != null) {
                    mVelocityTracker?.recycle()
                    mVelocityTracker = null
                }
            }

            MotionEvent.ACTION_UP -> {
                var isFling = false
                if (mVelocityTracker != null) {

                    if (mIsDragging) {

                        mVelocityTracker?.addMovement(event)
                        mVelocityTracker?.computeCurrentVelocity(1000 / 60)

                        val vx = mVelocityTracker!!.xVelocity
                        val vy = mVelocityTracker!!.yVelocity

                        if (Math.max(Math.abs(vx), Math.abs(vy)) >= mMinimumVelocity) {
                            isFling = listener.onFling(vx, vy)
                        }

                    }

                    mVelocityTracker?.clear()
                    mVelocityTracker?.recycle()
                    mVelocityTracker = null

                }
                setPrimaryPointer(event, INVALID_POINTER_INDEX, isFling)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(event.actionIndex)
                if (pointerId == mPrimaryPointerId) {
                    setPrimaryPointer(event, event.findPointerIndex(mSecondaryPointerId))
                    findSecondaryPointer(event, event.actionIndex)
                }
                else if (pointerId == mSecondaryPointerId) {
                    findSecondaryPointer(event, event.actionIndex)
                }
            }
        }


        return handled

    }

    private fun setPrimaryPointer(event: MotionEvent, pointerIndex: Int, isFling: Boolean = false) {
        if (pointerIndex != INVALID_POINTER_INDEX) {
            mPrimaryPointerId = event.getPointerId(pointerIndex)
            mPrimaryLastTouchPoint.set(
                event.getX(pointerIndex),
                event.getY(pointerIndex)
            )
        }
        else if (mPrimaryPointerId != INVALID_POINTER_ID) {
            mPrimaryPointerId = INVALID_POINTER_ID
            if (mIsDragging) {
                mIsDragging = false
                listener.onDragEnd(isFling)
            }
        }
    }

    private fun setSecondaryPointer(event: MotionEvent, pointerIndex: Int) {
        if (pointerIndex != INVALID_POINTER_INDEX) {
            mSecondaryPointerId = event.getPointerId(pointerIndex)
            mSecondaryLastTouchPoint.set(
                event.getX(pointerIndex),
                event.getY(pointerIndex)
            )
            mScaleFocusPoint = getMidPoint(mPrimaryLastTouchPoint, mSecondaryLastTouchPoint)
            mScaleDistance = getDistance(mPrimaryLastTouchPoint, mSecondaryLastTouchPoint)
        }
        else if (mSecondaryPointerId != INVALID_POINTER_ID) {
            mSecondaryPointerId = INVALID_POINTER_ID
            if (mIsScaling) {
                mIsScaling = false
                listener.onScaleEnd(mIsDragging)
            }
        }
    }

    private fun findSecondaryPointer(event: MotionEvent, excludePointerIndex: Int = INVALID_POINTER_INDEX) {
        for (i in 0..event.pointerCount) {
            if (i < event.pointerCount && i != excludePointerIndex) {
                val pointerId = event.getPointerId(i)
                if (pointerId != mPrimaryPointerId && pointerId != mSecondaryPointerId) {
                    setSecondaryPointer(event, i)
                    return
                }
            }
        }
        setSecondaryPointer(event, INVALID_POINTER_INDEX)
    }

    /**
     * 判断偏移量是否已经移动
     */
    private fun hasMoved(dx: Float, dy: Float): Boolean {
        return getDistance(dx, dy) >= mTouchSlop
    }

    /**
     * 获取两个点的距离
     */
    private fun getDistance(point1: PointF, point2: PointF): Float {
        return getDistance(point2.x - point1.x, point2.y - point1.y)
    }

    private fun getDistance(dx: Float, dy: Float): Float {
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    /**
     * 获取两个点的中间点
     */
    private fun getMidPoint(point1: PointF, point2: PointF): PointF {
        val dx = (point2.x - point1.x) / 2
        val dy = (point2.y - point1.y) / 2
        return PointF(point1.x + dx, point1.y + dy)
    }

}