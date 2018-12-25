package com.github.herokotlin.photoview

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.ImageView

class ThumbnailView: ImageView {

    var borderRadius = 0

        set(value) {
            if (field == value) {
                return
            }
            field = value
            borderRadiusPixel = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics)
            updateBitmap()
        }

    private var borderRadiusPixel = 0f

    private var drawableBitmap: Bitmap? = null

    private var drawableCanvas: Canvas? = null

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

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (drawable != null) {

            val intrinsicWidth = drawable.intrinsicWidth.toFloat()
            val intrinsicHeight = drawable.intrinsicHeight.toFloat()

            if (width > 0 && height > 0 && intrinsicWidth > 0 && intrinsicHeight > 0) {
                val scale = Math.max(viewWidth / intrinsicWidth, viewHeight / intrinsicHeight)

                val bitmapWidth = (intrinsicWidth * scale).toInt()
                val bitmapHeight = (intrinsicHeight * scale).toInt()

                drawableBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)

                drawableCanvas = Canvas(drawableBitmap)

                drawable.setBounds(0, 0, bitmapWidth, bitmapHeight)

            }
            else {
                this.drawableBitmap = null
            }

        }
        else {
            this.drawableBitmap = null
        }

        invalidate()

    }

    override fun onDraw(canvas: Canvas) {

        val cacheBitmap = drawableBitmap
        val cacheCanvas = drawableCanvas

        if (cacheBitmap == null || cacheCanvas == null) {
            super.onDraw(canvas)
            return
        }

        val left = (width - cacheBitmap.width.toFloat()) / 2
        val top = (height - cacheBitmap.height.toFloat()) / 2

        if (borderRadiusPixel > 0) {

            val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                canvas.saveLayer(null, null)
            } else {
                canvas.saveLayer(null, null, Canvas.ALL_SAVE_FLAG)
            }

            canvas.drawRoundRect(clipRect, borderRadiusPixel, borderRadiusPixel, paint)

            paint.xfermode = xfermode

            drawable.draw(cacheCanvas)

            canvas.drawBitmap(cacheBitmap, left, top, paint)

            paint.xfermode = null

            canvas.restoreToCount(saved)

        }
        else {
            canvas.drawBitmap(cacheBitmap, left, top, paint)
        }

    }

}