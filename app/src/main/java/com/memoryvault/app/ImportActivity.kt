package com.memoryvault.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.memoryvault.app.data.Repository
import com.memoryvault.app.ui.VoiceInput
import com.memoryvault.app.ui.promptText
import com.memoryvault.app.ui.runBusy
import com.memoryvault.app.ui.toast

/** Target of the system Share sheet: pick photos/videos anywhere, share to Memory Vault. */
class ImportActivity : AppCompatActivity() {

    private val voice = VoiceInput(this)
    private val repo by lazy { Repository.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = extractUris(intent)
        if (uris.isEmpty()) {
            toast("Nothing to import")
            finish()
            return
        }
        val stories = repo.stories()
        if (stories.isEmpty()) {
            askNewStory(uris)
            return
        }
        val labels = arrayOf("＋  New story") + stories.map { it.name }
        MaterialAlertDialogBuilder(this)
            .setTitle("Add ${uris.size} ${if (uris.size == 1) "item" else "items"} to")
            .setItems(labels) { _, which ->
                if (which == 0) askNewStory(uris) else importInto(stories[which - 1].id, uris)
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun extractUris(i: Intent): List<Uri> {
        val list = ArrayList<Uri>()
        when (i.action) {
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)?.let { list.add(it) }
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java)?.let { list.addAll(it) }
        }
        return list.filter { u ->
            val type = try {
                contentResolver.getType(u) ?: ""
            } catch (e: Exception) {
                ""
            }
            type.isEmpty() || type.startsWith("image/") || type.startsWith("video/")
        }
    }

    private fun askNewStory(uris: List<Uri>) {
        promptText(
            this, "New story", "Story name", "", voice,
            positive = "Create",
            onCancel = { finish() }
        ) { name ->
            var id = -1L
            runBusy("Importing…", onDone = { openStory(id) }) { id = repo.createStory(name, uris) }
        }
    }

    private fun importInto(storyId: Long, uris: List<Uri>) {
        runBusy("Importing…", onDone = { openStory(storyId) }) { repo.addMedia(storyId, uris) }
    }

    private fun openStory(id: Long) {
        if (id > 0) {
            startActivity(Intent(this, StoryActivity::class.java).putExtra(StoryActivity.EXTRA_STORY_ID, id))
        }
        finish()
    }
}
