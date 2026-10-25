package com.memoryvault.app.data

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import com.memoryvault.app.R
import com.memoryvault.app.widget.StoryWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Single entry point for data + files. Every photo/video the user adds is COPIED into the
 * app's private storage, so shared content from WhatsApp, Gallery, Camera etc. never disappears.
 *
 * A story's cover is an item of kind KIND_COVER. The stories table keeps a copy of its path/thumb
 * (for the albums grid and the widget), refreshed by [syncCover].
 */
class Repository private constructor(private val app: Context) {

    private class Imported(val path: String, val thumb: String?, val video: Boolean)

    private val resolver = app.contentResolver
    private val db = DbHelper.get(app)
    private val mediaDir = File(app.filesDir, "media").also { it.mkdirs() }
    private val thumbDir = File(app.filesDir, "thumbs").also { it.mkdirs() }
    private val tmpDir = File(app.cacheDir, "tmp").also { it.mkdirs() }

    companion object {
        @Volatile
        private var inst: Repository? = null

        fun get(ctx: Context): Repository =
            inst ?: synchronized(this) {
                inst ?: Repository(ctx.applicationContext).also { inst = it }
            }
    }

    // ------------------------------------------------------------ reads
    fun stories(): List<Story> = db.allStories()
    fun story(id: Long): Story? = db.storyById(id)
    fun items(storyId: Long, kind: Int): List<Item> = db.itemsOf(storyId, kind)
    fun item(id: Long): Item? = db.itemById(id)
    fun coverItem(storyId: Long): Item? = db.itemsOf(storyId, KIND_COVER).firstOrNull()

    // ----------------------------------------------------------- writes
    fun renameStory(id: Long, name: String) {
        db.updateStoryName(id, name)
        notifyWidgets()
    }

    fun setLoop(id: Long, loop: Boolean) = db.updateStoryLoop(id, loop)

    fun saveOrder(ids: List<Long>) {
        db.saveStoryOrder(ids)
        notifyWidgets()
    }

    fun updateItemText(id: Long, name: String, description: String, notes: String) =
        db.updateItemText(id, name.ifBlank { null }, description.ifBlank { null }, notes.ifBlank { null })

    fun newMediaFile(ext: String): File = File(mediaDir, "${UUID.randomUUID()}.$ext")

    fun addTag(storyId: Long, path: String, name: String) {
        db.insertItem(storyId, KIND_TAG, path, path, false, name.ifBlank { null })
    }

    /** An edited copy of a photo, saved as a new memory. */
    fun addCopy(storyId: Long, path: String) {
        db.insertItem(storyId, KIND_MEDIA, path, path, false, null)
    }

    suspend fun createStory(name: String, uris: List<Uri>): Long = withContext(Dispatchers.IO) {
        val files = uris.mapNotNull { importOne(it) }
        val id = db.insertStory(name.ifBlank { "Untitled" }, null, null, false)
        files.forEachIndexed { i, f ->
            db.insertItem(id, if (i == 0) KIND_COVER else KIND_MEDIA, f.path, f.thumb, f.video, null)
        }
        syncCover(id)
        id
    }

    suspend fun addMedia(storyId: Long, uris: List<Uri>): Unit = withContext(Dispatchers.IO) {
        val files = uris.mapNotNull { importOne(it) }
        var needCover = coverItem(storyId) == null
        files.forEach { f ->
            db.insertItem(storyId, if (needCover) KIND_COVER else KIND_MEDIA, f.path, f.thumb, f.video, null)
            needCover = false
        }
        syncCover(storyId)
    }

    /** New cover from outside the story. The old cover becomes a normal memory in the story. */
    suspend fun setCoverFromUri(storyId: Long, uri: Uri): Unit = withContext(Dispatchers.IO) {
        val f = importOne(uri)
        if (f != null) {
            demoteCover(storyId)
            db.insertItem(storyId, KIND_COVER, f.path, f.thumb, f.video, null)
            syncCover(storyId)
        }
    }

    /** Promotes one of the story's own memories to cover; the old cover drops into the memories row. */
    fun setCoverFromItem(storyId: Long, itemId: Long) {
        demoteCover(storyId)
        db.setItemKind(itemId, KIND_COVER)
        syncCover(storyId)
    }

    /** Switches the photo/video out for another one; name, description and notes stay as they were. */
    suspend fun replaceMedia(itemId: Long, uri: Uri): Unit = withContext(Dispatchers.IO) {
        val f = importOne(uri)
        if (f != null) swapFiles(itemId, f)
    }

    /** An edited photo (already in app storage) takes the place of the original. */
    fun replaceFile(itemId: Long, path: String) {
        swapFiles(itemId, Imported(path, path, false))
    }

    fun deleteItem(id: Long) {
        val item = db.itemById(id) ?: return
        db.deleteItem(id)
        removeFiles(item.path, item.thumb)
        if (item.kind == KIND_COVER) syncCover(item.storyId)
    }

    fun deleteStory(id: Long) {
        val all = db.itemsOf(id, KIND_COVER) + db.itemsOf(id, KIND_MEDIA) + db.itemsOf(id, KIND_TAG)
        db.deleteStory(id)
        all.forEach { removeFiles(it.path, it.thumb) }
        notifyWidgets()
    }

    // ------------------------------------------------------- temp copies
    /** Copies a picked or captured photo into the cache so the editor can open it as a plain file. */
    suspend fun copyToTemp(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val mime = resolver.getType(uri) ?: ""
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
            val out = File(tmpDir, "${UUID.randomUUID()}.$ext")
            val input = resolver.openInputStream(uri)
            if (input == null) {
                null
            } else {
                input.use { src -> out.outputStream().use { dst -> src.copyTo(dst) } }
                cleanupCapture(uri)
                out.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    fun deleteTemp(path: String) {
        if (path.startsWith(tmpDir.absolutePath)) runCatching { File(path).delete() }
    }

    // ------------------------------------------------------------ internals
    private fun demoteCover(storyId: Long) {
        db.itemsOf(storyId, KIND_COVER).forEach { db.setItemKind(it.id, KIND_MEDIA) }
    }

    private fun syncCover(storyId: Long) {
        val c = coverItem(storyId)
        db.updateStoryCover(storyId, c?.path, c?.thumb, c?.video ?: false)
        notifyWidgets()
    }

    private fun swapFiles(itemId: Long, f: Imported) {
        val old = db.itemById(itemId) ?: return
        db.updateItemMedia(itemId, f.path, f.thumb, f.video)
        removeFiles(old.path, old.thumb)
        if (old.kind == KIND_COVER) syncCover(old.storyId)
    }

    /** Tells the home-screen widget to rebuild its slideshow. */
    fun notifyWidgets() {
        try {
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, StoryWidgetProvider::class.java))
            if (ids.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(ids, R.id.flipper)
        } catch (_: Exception) {
        }
    }

    private fun importOne(uri: Uri): Imported? {
        return try {
            val mime = resolver.getType(uri) ?: ""
            val video = mime.startsWith("video/")
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: if (video) "mp4" else "jpg"
            val out = newMediaFile(ext)
            val input = resolver.openInputStream(uri) ?: return null
            input.use { src -> out.outputStream().use { dst -> src.copyTo(dst) } }
            cleanupCapture(uri)
            val thumb = if (video) makeVideoThumb(out) else out.absolutePath
            Imported(out.absolutePath, thumb, video)
        } catch (e: Exception) {
            null
        }
    }

    /** Camera captures land in cache/capture through our FileProvider; delete them once copied. */
    private fun cleanupCapture(uri: Uri) {
        if (uri.authority == app.packageName + ".fileprovider") {
            val p = uri.path?.trimStart('/') ?: return
            runCatching { File(app.cacheDir, p).delete() }
        }
    }

    private fun makeVideoThumb(file: File): String? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            val frame = r.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: r.getFrameAtTime(0)
            if (frame == null) {
                null
            } else {
                val out = File(thumbDir, "${UUID.randomUUID()}.jpg")
                out.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                out.absolutePath
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun removeFiles(vararg paths: String?) {
        paths.filterNotNull().distinct().forEach { runCatching { File(it).delete() } }
    }
}
