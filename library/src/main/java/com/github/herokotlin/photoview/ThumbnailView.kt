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

    private var bitmap: Bitmap? = null

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

                val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)

                val canvas = Canvas(bitmap)

                drawable.setBounds(0, 0, bitmapWidth, bitmapHeight)
                drawable.draw(canvas)

                this.bitmap = bitmap
            }
            else {
                this.bitmap = null
            }

        }
        else {
            this.bitmap = null
        }

        invalidate()

    }

    override fun onDraw(canvas: Canvas) {

        val cacheBitmap = bitmap

        if (cacheBitmap == null) {
            return
        }

        val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            canvas.saveLayer(null, null)
        }
        else {
            canvas.saveLayer(null, null, Canvas.ALL_SAVE_FLAG)
        }

        val left = (width - cacheBitmap.width.toFloat()) / 2
        val top = (height - cacheBitmap.height.toFloat()) / 2

        canvas.drawRoundRect(clipRect, borderRadiusPixel, borderRadiusPixel, paint)

        paint.xfermode = xfermode

        canvas.drawBitmap(cacheBitmap, left, top, paint)

        paint.xfermode = null

        canvas.restoreToCount(saved)

    }

}