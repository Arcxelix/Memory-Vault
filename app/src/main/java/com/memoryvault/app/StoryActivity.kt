package com.memoryvault.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.memoryvault.app.data.Item
import com.memoryvault.app.data.KIND_MEDIA
import com.memoryvault.app.data.KIND_TAG
import com.memoryvault.app.data.Repository
import com.memoryvault.app.data.Story
import com.memoryvault.app.databinding.ActivityStoryBinding
import com.memoryvault.app.ui.ItemAdapter
import com.memoryvault.app.ui.ItemSheet
import com.memoryvault.app.ui.MediaPicker
import com.memoryvault.app.ui.PickAdapter
import com.memoryvault.app.ui.VoiceInput
import com.memoryvault.app.ui.dp
import com.memoryvault.app.ui.promptText
import com.memoryvault.app.ui.runBusy
import com.memoryvault.app.ui.toast
import java.io.File

/** One story: big cover (photo or autoplaying video), memories row, tags row. */
@OptIn(UnstableApi::class)
class StoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STORY_ID = "story_id"
    }

    /** A photo waiting to be cut into a tag. Temp copies are deleted after use. */
    private class TagSource(val path: String, val temp: Boolean)

    private lateinit var b: ActivityStoryBinding
    private val repo by lazy { Repository.get(this) }
    private val voice = VoiceInput(this)
    private val media = MediaPicker(this) { this }
    private val handler = Handler(Looper.getMainLooper())
    private val hideChrome = Runnable { setChrome(false) }

    private var storyId = -1L
    private var story: Story? = null
    private var player: ExoPlayer? = null
    private var isBound = false
    private var boundPath: String? = null

    private val tagQueue = ArrayDeque<TagSource>()
    private var currentSource: TagSource? = null

    private val memAdapter = ItemAdapter(140, 1.3f) { openItem(it) }
    private val tagAdapter = ItemAdapter(96, 1f) { openItem(it) }

    private val editorLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val src = currentSource
        currentSource = null
        if (src != null && src.temp) repo.deleteTemp(src.path)
        val out = res.data?.getStringExtra(EditorActivity.EXTRA_OUT)
        if (res.resultCode == RESULT_OK && out != null) askTagName(out) else clearQueue()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityStoryBinding.inflate(layoutInflater)
        setContentView(b.root)
        storyId = intent.getLongExtra(EXTRA_STORY_ID, -1L)

        val coverWidth = resources.displayMetrics.widthPixels - dp(24)
        b.coverCard.layoutParams.height = (coverWidth * 1.2f).toInt()

        b.memList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        b.memList.adapter = memAdapter
        b.tagList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        b.tagList.adapter = tagAdapter

        b.backBtn.setOnClickListener { finish() }
        b.menuBtn.setOnClickListener { showMenu() }
        b.coverImage.setOnClickListener { setChrome(b.menuBtn.visibility != View.VISIBLE) }
        b.coverPlayer.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility -> setChrome(visibility == View.VISIBLE) }
        )
        b.addMem.setOnClickListener { addToStory() }
        b.addTag.setOnClickListener { startTagFlow() }
    }

    override fun onStart() {
        super.onStart()
        reload()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
        isBound = false
    }

    // ------------------------------------------------------------ data
    private fun reload() {
        val s = repo.story(storyId)
        if (s == null) {
            finish()
            return
        }
        story = s
        val memories = repo.items(storyId, KIND_MEDIA)
        val tags = repo.items(storyId, KIND_TAG)
        memAdapter.submit(memories)
        tagAdapter.submit(tags)
        b.emptyMem.isVisible = memories.isEmpty()
        b.emptyTags.isVisible = tags.isEmpty()
        b.title.text = s.name
        b.subtitle.text = "${memories.size} memories · ${tags.size} tags"
        bindCover(s)
    }

    /** Called by the item drawer when it closes. */
    fun onSheetClosed() {
        reload()
        player?.play()
    }

    private fun openItem(item: Item) {
        player?.pause()
        ItemSheet.show(supportFragmentManager, item.id)
    }

    // ------------------------------------------------------------ cover
    private fun bindCover(s: Story) {
        val path = s.coverPath
        if (isBound && path == boundPath && (!s.coverVideo || player != null)) {
            player?.repeatMode = if (s.loopVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            return
        }
        isBound = true
        boundPath = path
        releasePlayer()
        b.coverPlayer.visibility = View.GONE

        if (path == null) {
            b.coverImage.scaleType = ImageView.ScaleType.CENTER
            b.coverImage.setImageResource(R.drawable.ic_image)
            return
        }
        b.coverImage.scaleType = ImageView.ScaleType.CENTER_CROP
        Glide.with(this).load(File(s.coverThumb ?: path)).centerCrop().into(b.coverImage)
        if (s.coverVideo) {
            b.coverPlayer.visibility = View.VISIBLE
            startPlayer(path, s.loopVideo)
        }
    }

    private fun startPlayer(path: String, loop: Boolean) {
        val p = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        p.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        p.volume = 1f
        p.prepare()
        p.playWhenReady = true
        b.coverPlayer.player = p
        player = p
    }

    private fun releasePlayer() {
        b.coverPlayer.player = null
        player?.release()
        player = null
    }

    /** Menu button (and, for videos, the title) follow the player controls; images auto-hide after a moment. */
    private fun setChrome(visible: Boolean) {
        handler.removeCallbacks(hideChrome)
        b.menuBtn.visibility = if (visible) View.VISIBLE else View.GONE
        val video = story?.coverVideo == true
        if (video) b.titleBox.animate().alpha(if (visible) 0f else 1f).setDuration(180).start()
        if (visible && !video) handler.postDelayed(hideChrome, 3500)
    }

    // ------------------------------------------------------------ menu
    private fun showMenu() {
        val s = story ?: return
        val pm = PopupMenu(this, b.menuBtn)
        pm.menu.add(0, 1, 0, "Rename story")
        pm.menu.add(0, 2, 1, "Change thumbnail")
        pm.menu.add(0, 3, 2, "Thumbnail details & edit")
        if (s.coverVideo) {
            pm.menu.add(0, 4, 3, "Loop video").apply {
                isCheckable = true
                isChecked = s.loopVideo
            }
        }
        pm.menu.add(0, 5, 4, "Add photos & videos")
        pm.menu.add(0, 6, 5, "Delete story")
        pm.setOnMenuItemClickListener { m ->
            when (m.itemId) {
                1 -> rename(s)
                2 -> changeCover()
                3 -> {
                    val cover = repo.coverItem(storyId)
                    if (cover != null) openItem(cover) else toast("This story has no thumbnail yet")
                }
                4 -> {
                    repo.setLoop(storyId, !s.loopVideo)
                    story = repo.story(storyId)
                    player?.repeatMode = if (!s.loopVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                }
                5 -> addToStory()
                6 -> confirmDelete()
            }
            true
        }
        pm.show()
    }

    private fun rename(s: Story) {
        promptText(this, "Rename story", "Story name", s.name, voice) { text ->
            if (text.isNotBlank()) {
                repo.renameStory(storyId, text.trim())
                reload()
            }
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete this story?")
            .setMessage("The story, its photos, videos and tags will be removed for good.")
            .setPositiveButton("Delete") { _, _ ->
                releasePlayer()
                repo.deleteStory(storyId)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------ add to story
    private fun addToStory() {
        media.pick(multiple = true) { uris ->
            runBusy("Adding to story…", onDone = { reload() }) { repo.addMedia(storyId, uris) }
        }
    }

    // ------------------------------------------------------------ change cover
    private fun changeCover() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Change thumbnail")
            .setItems(arrayOf("Pick from this story", "Gallery or camera")) { _, which ->
                if (which == 0) coverFromStory() else coverFromOutside()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun coverFromStory() {
        val list = repo.items(storyId, KIND_MEDIA)
        if (list.isEmpty()) {
            toast("No memories in this story yet. Use gallery or camera.")
            return
        }
        showGrid("Pick the new thumbnail", list.map { it.thumb ?: it.path }) { index ->
            repo.setCoverFromItem(storyId, list[index].id)
            isBound = false
            reload()
        }
    }

    private fun coverFromOutside() {
        media.pick(multiple = false) { uris ->
            uris.firstOrNull()?.let { uri ->
                isBound = false
                runBusy("Changing thumbnail…", onDone = { reload() }) { repo.setCoverFromUri(storyId, uri) }
            }
        }
    }

    // ------------------------------------------------------------ tags
    private fun startTagFlow() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cut a tag from")
            .setItems(arrayOf("This story's photos", "Other photos (gallery or camera)")) { _, which ->
                if (which == 0) tagFromStory() else tagFromOutside()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun tagFromStory() {
        val photos = (listOfNotNull(repo.coverItem(storyId)) + repo.items(storyId, KIND_MEDIA)).filter { !it.video }
        if (photos.isEmpty()) {
            toast("Add a photo first, or cut from other photos.")
            return
        }
        showGrid("Pick a photo to cut from", photos.map { it.path }) { index ->
            tagQueue.add(TagSource(photos[index].path, false))
            nextTag()
        }
    }

    private fun tagFromOutside() {
        media.pick(multiple = true, imagesOnly = true) { uris ->
            var copies = emptyList<String>()
            runBusy("Opening photos…", onDone = {
                copies.forEach { tagQueue.add(TagSource(it, true)) }
                nextTag()
            }) {
                copies = uris.mapNotNull { repo.copyToTemp(it) }
            }
        }
    }

    private fun nextTag() {
        val src = tagQueue.removeFirstOrNull() ?: return
        currentSource = src
        editorLauncher.launch(
            Intent(this, EditorActivity::class.java)
                .putExtra(EditorActivity.EXTRA_IN, src.path)
                .putExtra(EditorActivity.EXTRA_MODE, EditorActivity.MODE_TAG)
        )
    }

    private fun clearQueue() {
        tagQueue.forEach { if (it.temp) repo.deleteTemp(it.path) }
        tagQueue.clear()
    }

    private fun askTagName(path: String) {
        promptText(
            this, "Name this tag", "Tag name (optional)", "", voice,
            onCancel = {
                runCatching { File(path).delete() }
                nextTag()
            }
        ) { name ->
            repo.addTag(storyId, path, name.trim())
            reload()
            nextTag()
        }
    }

    // ------------------------------------------------------------ helpers
    private fun showGrid(title: String, thumbs: List<String>, onPick: (Int) -> Unit) {
        val rv = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, 3)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            clipToPadding = false
        }
        lateinit var dlg: AlertDialog
        rv.adapter = PickAdapter(thumbs) { index, _ ->
            dlg.dismiss()
            onPick(index)
        }
        dlg = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(rv)
            .setNegativeButton("Cancel", null)
            .create()
        dlg.show()
    }
}
