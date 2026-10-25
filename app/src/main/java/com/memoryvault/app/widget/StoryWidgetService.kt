package com.memoryvault.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.memoryvault.app.AlbumsActivity
import com.memoryvault.app.R
import com.memoryvault.app.data.Repository
import java.io.File
import kotlin.math.max

/**
 * Feeds the widget's flipper. The flipper advances once per second, so:
 *  - a photo cover fills 20 slots  = shown for 20 s
 *  - a video cover becomes one frame per second of video (max 30) = plays through once
 * Widgets cannot run a real video player, so a video is shown as a frame-by-frame preview;
 * tapping the widget opens the app, where the video plays with sound.
 */
class StoryWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(
            applicationContext,
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        )

    private class Slot(val storyId: Long, val name: String, val image: String)

    private class Size(val w: Int, val h: Int, val radius: Int)

    private class Factory(private val ctx: Context, private val widgetId: Int) : RemoteViewsFactory {

        private companion object {
            const val PHOTO_SECONDS = 20
            const val MAX_VIDEO_SECONDS = 30
        }

        private var slots: List<Slot> = emptyList()
        private val frameDir = File(ctx.cacheDir, "widget").also { it.mkdirs() }

        override fun onCreate() {}
        override fun onDestroy() {}

        override fun onDataSetChanged() {
            slots = buildSlots()
        }

        override fun getCount() = slots.size

        override fun getViewAt(position: Int): RemoteViews? {
            val slot = slots.getOrNull(position) ?: return null
            val rv = RemoteViews(ctx.packageName, R.layout.widget_item)
            val size = targetSize()
            try {
                val bmp = Glide.with(ctx)
                    .asBitmap()
                    .load(File(slot.image))
                    .transform(CenterCrop(), RoundedCorners(size.radius))
                    .disallowHardwareConfig()
                    .submit(size.w, size.h)
                    .get()
                rv.setImageViewBitmap(R.id.wImage, bmp)
            } catch (e: Exception) {
                // leave the slide with just its caption
            }
            rv.setTextViewText(R.id.wName, slot.name)
            rv.setOnClickFillInIntent(
                R.id.wRoot,
                Intent().putExtra(AlbumsActivity.EXTRA_HIGHLIGHT_ID, slot.storyId)
            )
            return rv
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false

        private fun targetSize(): Size {
            val o = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(widgetId)
            val d = ctx.resources.displayMetrics.density
            var w = (o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) * d).toInt()
            var h = (o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180) * d).toInt()
            val k = 560f / max(w, h).coerceAtLeast(1)
            if (k < 1f) {
                w = (w * k).toInt()
                h = (h * k).toInt()
            }
            val scale = if (k < 1f) k else 1f
            return Size(max(w, 120), max(h, 120), (20 * d * scale).toInt())
        }

        private fun buildSlots(): List<Slot> {
            val repo = Repository.get(ctx)
            val out = ArrayList<Slot>()
            val keep = HashSet<String>()
            for (s in repo.stories()) {
                val path = s.coverPath ?: continue
                if (!s.coverVideo) {
                    val image = s.coverThumb ?: path
                    repeat(PHOTO_SECONDS) { out.add(Slot(s.id, s.name, image)) }
                } else {
                    val frames = videoFrames(s.id, path, keep)
                    if (frames.isEmpty()) {
                        val poster = s.coverThumb
                        if (poster != null) repeat(3) { out.add(Slot(s.id, s.name, poster)) }
                    } else {
                        frames.forEach { out.add(Slot(s.id, s.name, it)) }
                    }
                }
            }
            frameDir.listFiles()?.forEach { if (it.absolutePath !in keep) it.delete() }
            return out
        }

        /** One cached JPEG per second of video, so the widget can "play" it once. */
        private fun videoFrames(storyId: Long, path: String, keep: MutableSet<String>): List<String> {
            val r = MediaMetadataRetriever()
            val frames = ArrayList<String>()
            try {
                r.setDataSource(path)
                val durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val count = ((durationMs + 999) / 1000).toInt().coerceIn(1, MAX_VIDEO_SECONDS)
                val tag = "${storyId}_${File(path).name.hashCode()}"
                for (i in 0 until count) {
                    val f = File(frameDir, "${tag}_$i.jpg")
                    if (!f.exists()) {
                        val bmp = r.getFrameAtTime(i * 1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                        val small = scaleDown(bmp, 640)
                        f.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                    }
                    keep.add(f.absolutePath)
                    frames.add(f.absolutePath)
                }
            } catch (e: Exception) {
                // fall back to the poster frame
            } finally {
                try {
                    r.release()
                } catch (_: Exception) {
                }
            }
            return frames
        }

        private fun scaleDown(bmp: Bitmap, maxEdge: Int): Bitmap {
            val k = maxEdge / max(bmp.width, bmp.height).toFloat()
            if (k >= 1f) return bmp
            return Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
        }
    }
}
