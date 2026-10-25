package com.memoryvault.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Free-form crop frame: drag corners, edges, or the whole frame. */
class CropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        const val L = 1
        const val T = 2
        const val R = 4
        const val B = 8
        const val MOVE = 16
    }

    private val d = resources.displayMetrics.density
    private val accent = 0xFF3DE0FF.toInt()
    private var bmp: Bitmap? = null
    private val img = RectF()
    private val box = RectF()

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint().apply { color = 0xCC000000.toInt() }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent; style = Paint.Style.STROKE; strokeWidth = 1.5f * d
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44FFFFFF; style = Paint.Style.STROKE; strokeWidth = d
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent; style = Paint.Style.STROKE; strokeWidth = 4f * d; strokeCap = Paint.Cap.ROUND
    }
    private val minSize = 72f * d
    private val grab = 36f * d
    private val bracket = 22f * d

    private var mode = 0
    private var lastX = 0f
    private var lastY = 0f

    fun setBitmap(b: Bitmap) {
        bmp = b
        layoutImage()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutImage()
    }

    private fun layoutImage() {
        val b = bmp ?: return
        if (width == 0 || height == 0) return
        val pad = 24f * d
        val s = min((width - 2 * pad) / b.width, (height - 2 * pad) / b.height)
        val w = b.width * s
        val h = b.height * s
        val l = (width - w) / 2f
        val t = (height - h) / 2f
        img.set(l, t, l + w, t + h)
        val ix = img.width() * 0.12f
        val iy = img.height() * 0.12f
        box.set(img.left + ix, img.top + iy, img.right - ix, img.bottom - iy)
    }

    override fun onDraw(c: Canvas) {
        val b = bmp ?: return
        c.drawBitmap(b, null, img, bmpPaint)
        c.drawRect(img.left, img.top, img.right, box.top, dimPaint)
        c.drawRect(img.left, box.bottom, img.right, img.bottom, dimPaint)
        c.drawRect(img.left, box.top, box.left, box.bottom, dimPaint)
        c.drawRect(box.right, box.top, img.right, box.bottom, dimPaint)

        val w3 = box.width() / 3f
        val h3 = box.height() / 3f
        for (i in 1..2) {
            c.drawLine(box.left + w3 * i, box.top, box.left + w3 * i, box.bottom, gridPaint)
            c.drawLine(box.left, box.top + h3 * i, box.right, box.top + h3 * i, gridPaint)
        }
        c.drawRoundRect(box, 10f * d, 10f * d, framePaint)

        corner(c, box.left, box.top, 1, 1)
        corner(c, box.right, box.top, -1, 1)
        corner(c, box.left, box.bottom, 1, -1)
        corner(c, box.right, box.bottom, -1, -1)
    }

    private fun corner(c: Canvas, x: Float, y: Float, sx: Int, sy: Int) {
        c.drawLine(x, y, x + sx * bracket, y, cornerPaint)
        c.drawLine(x, y, x, y + sy * bracket, cornerPaint)
    }

    private fun hit(x: Float, y: Float): Int {
        var m = 0
        val nearX = x > box.left - grab && x < box.right + grab
        val nearY = y > box.top - grab && y < box.bottom + grab
        if (nearY && abs(x - box.left) < grab) m = m or L
        if (nearY && abs(x - box.right) < grab) m = m or R
        if (nearX && abs(y - box.top) < grab) m = m or T
        if (nearX && abs(y - box.bottom) < grab) m = m or B
        if ((m and L) != 0 && (m and R) != 0) {
            m = if (abs(x - box.left) < abs(x - box.right)) m and R.inv() else m and L.inv()
        }
        if ((m and T) != 0 && (m and B) != 0) {
            m = if (abs(y - box.top) < abs(y - box.bottom)) m and B.inv() else m and T.inv()
        }
        if (m == 0 && box.contains(x, y)) m = MOVE
        return m
    }

    private fun clamp(v: Float, lo: Float, hi: Float) = max(lo, min(v, hi))

    private fun drag(dx: Float, dy: Float) {
        if (mode == MOVE) {
            val nx = clamp(dx, img.left - box.left, img.right - box.right)
            val ny = clamp(dy, img.top - box.top, img.bottom - box.bottom)
            box.offset(nx, ny)
            return
        }
        if ((mode and L) != 0) box.left = clamp(box.left + dx, img.left, box.right - minSize)
        if ((mode and R) != 0) box.right = clamp(box.right + dx, box.left + minSize, img.right)
        if ((mode and T) != 0) box.top = clamp(box.top + dy, img.top, box.bottom - minSize)
        if ((mode and B) != 0) box.bottom = clamp(box.bottom + dy, box.top + minSize, img.bottom)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = hit(e.x, e.y)
                lastX = e.x
                lastY = e.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return mode != 0
            }
            MotionEvent.ACTION_MOVE -> {
                drag(e.x - lastX, e.y - lastY)
                lastX = e.x
                lastY = e.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mode = 0
        }
        return true
    }

    /** Returns the framed region of the photo as a new bitmap. */
    fun cropped(): Bitmap? {
        val b = bmp ?: return null
        val s = b.width / img.width()
        val x = ((box.left - img.left) * s).toInt().coerceIn(0, b.width - 1)
        val y = ((box.top - img.top) * s).toInt().coerceIn(0, b.height - 1)
        val w = (box.width() * s).toInt().coerceIn(1, b.width - x)
        val h = (box.height() * s).toInt().coerceIn(1, b.height - y)
        return Bitmap.createBitmap(b, x, y, w, h)
    }
}
