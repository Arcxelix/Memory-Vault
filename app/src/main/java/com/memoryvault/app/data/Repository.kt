package com.memoryvault.app.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Single entry point for data + files. Every photo/video the user adds is COPIED into the
 * app's private storage, so shared content from WhatsApp, Gallery etc. never disappears.
 */
class Repository private constructor(app: Context) {

    private class Imported(val path: String, val thumb: String?, val video: Boolean)

    private val resolver = app.contentResolver
    private val db = DbHelper.get(app)
    private val mediaDir = File(app.filesDir, "media").also { it.mkdirs() }
    private val thumbDir = File(app.filesDir, "thumbs").also { it.mkdirs() }

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

    // ----------------------------------------------------------- writes
    fun renameStory(id: Long, name: String) = db.updateStoryName(id, name)
    fun setLoop(id: Long, loop: Boolean) = db.updateStoryLoop(id, loop)
    fun saveOrder(ids: List<Long>) = db.saveStoryOrder(ids)
    fun updateItemText(id: Long, name: String, description: String, notes: String) =
        db.updateItemText(id, name.ifBlank { null }, description.ifBlank { null }, notes.ifBlank { null })

    fun newMediaFile(ext: String): File = File(mediaDir, "${UUID.randomUUID()}.$ext")

    fun addTag(storyId: Long, path: String, name: String) {
        db.insertItem(storyId, KIND_TAG, path, path, false, name.ifBlank { null })
    }

    suspend fun createStory(name: String, uris: List<Uri>): Long = withContext(Dispatchers.IO) {
        val files = uris.mapNotNull { importOne(it) }
        val cover = files.firstOrNull()
        val id = db.insertStory(name.ifBlank { "Untitled" }, cover?.path, cover?.thumb, cover?.video ?: false)
        files.drop(1).forEach { db.insertItem(id, KIND_MEDIA, it.path, it.thumb, it.video, null) }
        id
    }

    suspend fun addMedia(storyId: Long, uris: List<Uri>): Unit = withContext(Dispatchers.IO) {
        var files = uris.mapNotNull { importOne(it) }
        val story = db.storyById(storyId)
        if (story != null && story.coverPath == null && files.isNotEmpty()) {
            val c = files.first()
            db.updateStoryCover(storyId, c.path, c.thumb, c.video)
            files = files.drop(1)
        }
        files.forEach { db.insertItem(storyId, KIND_MEDIA, it.path, it.thumb, it.video, null) }
    }

    suspend fun changeCover(storyId: Long, uri: Uri): Unit = withContext(Dispatchers.IO) {
        val f = importOne(uri)
        val old = db.storyById(storyId)
        if (f != null) {
            db.updateStoryCover(storyId, f.path, f.thumb, f.video)
            if (old != null) removeFiles(old.coverPath, old.coverThumb)
        }
    }

    fun deleteItem(id: Long) {
        val item = db.itemById(id) ?: return
        db.deleteItem(id)
        removeFiles(item.path, item.thumb)
    }

    fun deleteStory(id: Long) {
        val story = db.storyById(id)
        val all = db.itemsOf(id, KIND_MEDIA) + db.itemsOf(id, KIND_TAG)
        db.deleteStory(id)
        all.forEach { removeFiles(it.path, it.thumb) }
        if (story != null) removeFiles(story.coverPath, story.coverThumb)
    }

    // ------------------------------------------------------------ files
    private fun importOne(uri: Uri): Imported? {
        return try {
            val mime = resolver.getType(uri) ?: ""
            val video = mime.startsWith("video/")
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: if (video) "mp4" else "jpg"
            val out = newMediaFile(ext)
            val input = resolver.openInputStream(uri) ?: return null
            input.use { src -> out.outputStream().use { dst -> src.copyTo(dst) } }
            val thumb = if (video) makeVideoThumb(out) else out.absolutePath
            Imported(out.absolutePath, thumb, video)
        } catch (e: Exception) {
            null
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
