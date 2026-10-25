package com.memoryvault.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import java.io.File

/** Shared image maths for the editor: preview and full-quality save use the same steps. */
object BitmapOps {

    /** What the user did: flip (first), then rotate by rot*90° clockwise, then crop (as fractions 0..1). */
    class Spec(val rot: Int, val flip: Boolean, val l: Float, val t: Float, val r: Float, val b: Float)

    fun transform(src: Bitmap, rot: Int, flip: Boolean): Bitmap {
        if (rot == 0 && !flip) return src
        val m = Matrix()
        if (flip) m.postScale(-1f, 1f)
        m.postRotate(rot * 90f)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun filter(brightness: Float, contrast: Float, saturation: Float): ColorMatrixColorFilter? {
        if (brightness == 0f && contrast == 1f && saturation == 1f) return null
        val cm = ColorMatrix()
        cm.setSaturation(saturation)
        val t = (-0.5f * contrast + 0.5f) * 255f + brightness
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, t,
                    0f, contrast, 0f, 0f, t,
                    0f, 0f, contrast, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        return ColorMatrixColorFilter(cm)
    }

    /** Loads an image file at most [max] px on its longest side (EXIF rotation handled by Glide). */
    fun load(ctx: Context, path: String, max: Int): Bitmap =
        Glide.with(ctx.applicationContext)
            .asBitmap()
            .load(File(path))
            .disallowHardwareConfig()
            .downsample(DownsampleStrategy.AT_MOST)
            .submit(max, max)
            .get()

    fun render(source: Bitmap, s: Spec, cf: ColorFilter?): Bitmap {
        val tr = transform(source, s.rot, s.flip)
        val x = (s.l * tr.width).toInt().coerceIn(0, tr.width - 1)
        val y = (s.t * tr.height).toInt().coerceIn(0, tr.height - 1)
        val w = ((s.r - s.l) * tr.width).toInt().coerceIn(1, tr.width - x)
        val h = ((s.b - s.t) * tr.height).toInt().coerceIn(1, tr.height - y)
        val cut = Bitmap.createBitmap(tr, x, y, w, h)
        if (cf == null) return cut
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = cf
        Canvas(out).drawBitmap(cut, 0f, 0f, paint)
        return out
    }
}
