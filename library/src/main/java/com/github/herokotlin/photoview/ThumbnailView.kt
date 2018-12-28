package com.github.herokotlin.photoview

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.ImageView
import java.lang.ref.WeakReference

open class ThumbnailView: ImageView {

    var bgColor = 0

        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (borderRadius > 0) {
                setBackgroundColor(0)
                invalidate()
            }
            else {
                setBackgroundColor(bgColor)
            }
        }

    var borderRadius = 0

        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (bgColor != 0) {
                if (value > 0) {
                    setBackgroundColor(0)
                }
                else {
                    setBackgroundColor(bgColor)
                }
            }
            borderRadiusPixel = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics)
            updateBitmap()
        }

    private var borderRadiusPixel = 0f

    private var drawableBitmap: WeakReference<Bitmap>? = null

    private var drawableCanvas: WeakReference<Canvas>? = null

    private var drawableWidth = 0

    private var drawableHeight = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

    private var clipRect = RectF()

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
        scaleType = ScaleType.CENTER_CROP
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        updateBitmap()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        updateBitmap()
    }

    override fun setImageURI(uri: Uri?) {
        super.setImageURI(uri)
        updateBitmap()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipRect.right = w.toFloat()
        clipRect.bottom = h.toFloat()
        updateBitmap()
    }

    private fun updateBitmap() {

        drawableBitmap = null

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (viewWidth > 0 && viewHeight > 0 && drawable != null) {

            val intrinsicWidth = drawable.intrinsicWidth.toFloat()
            val intrinsicHeight = drawable.intrinsicHeight.toFloat()

            if (intrinsicWidth > 0 && intrinsicHeight > 0) {

                val scale = Math.max(viewWidth / intrinsicWidth, viewHeight / intrinsicHeight)

                drawableWidth = (intrinsicWidth * scale).toInt()
                drawableHeight = (intrinsicHeight * scale).toInt()

            }

        }

        drawableBitmap?.clear()
        drawableCanvas?.clear()

        invalidate()

    }

    override fun onDraw(canvas: Canvas) {

        if (borderRadiusPixel == 0f) {
            super.onDraw(canvas)
            return
        }

        paint.style = Paint.Style.FILL

        if (bgColor != 0) {
            paint.color = bgColor
            if (borderRadiusPixel > 0) {
                canvas.drawRoundRect(clipRect, borderRadiusPixel, borderRadiusPixel, paint)
            }
            else {
                canvas.drawRect(clipRect, paint)
            }
            paint.color = Color.BLACK
        }

        // 用弱引用确保不会 OOM
        var cacheBitmap = drawableBitmap?.get()
        if (cacheBitmap == null) {
            cacheBitmap = Bitmap.createBitmap(drawableWidth, drawableHeight, Bitmap.Config.ARGB_8888)
            drawableBitmap = WeakReference(cacheBitmap)
        }

        var cacheCanvas = drawableCanvas?.get()
        if (cacheCanvas == null) {
            cacheCanvas = Canvas(cacheBitmap)
            drawableCanvas = WeakReference(cacheCanvas)
        }

        val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            canvas.saveLayer(null, null)
        } else {
            canvas.saveLayer(null, null, Canvas.ALL_SAVE_FLAG)
        }

        canvas.drawRoundRect(clipRect, borderRadiusPixel, borderRadiusPixel, paint)

        paint.xfermode = xfermode

        // gif 会不停的调 onDraw，因此只有在这里不停的 drawable.draw(cacheCanvas) 才会有动画
        drawable.setBounds(0, 0, drawableWidth, drawableHeight)
        drawable.draw(cacheCanvas)

        val left = (width - drawableWidth).toFloat() / 2
        val top = (height - drawableHeight).toFloat() / 2
        canvas.drawBitmap(cacheBitmap, left, top, paint)

        paint.xfermode = null

        canvas.restoreToCount(saved)

    }

}