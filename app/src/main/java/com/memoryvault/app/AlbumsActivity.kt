package com.memoryvault.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.memoryvault.app.data.Repository
import com.memoryvault.app.data.Story
import com.memoryvault.app.databinding.ActivityAlbumsBinding
import com.memoryvault.app.databinding.DialogNewStoryBinding
import com.memoryvault.app.ui.AlbumsAdapter
import com.memoryvault.app.ui.MediaPicker
import com.memoryvault.app.ui.VoiceInput
import com.memoryvault.app.ui.runBusy

/** Home screen: the grid of stories. Long-press and drag to reorder; the button toggles 3x3 / 4x4. */
class AlbumsActivity : AppCompatActivity() {

    companion object {
        const val ACTION_HIGHLIGHT = "com.memoryvault.app.HIGHLIGHT"
        const val EXTRA_HIGHLIGHT_ID = "highlight_id"
    }

    private lateinit var b: ActivityAlbumsBinding
    private lateinit var grid: GridLayoutManager
    private val repo by lazy { Repository.get(this) }
    private val voice = VoiceInput(this)
    private val prefs by lazy { getSharedPreferences("vault", MODE_PRIVATE) }
    private val adapter = AlbumsAdapter { openStory(it) }

    private var picked: List<Uri> = emptyList()
    private var pickBtn: Button? = null

    private val media = MediaPicker(this) { this }
    private val handler = Handler(Looper.getMainLooper())
    private var pendingHighlight = -1L

    private val touch = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder?.itemView?.animate()?.scaleX(1.07f)?.scaleY(1.07f)?.setDuration(120)?.start()
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            repo.saveOrder(adapter.items.map { it.id })
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAlbumsBinding.inflate(layoutInflater)
        setContentView(b.root)
        pendingHighlight = intent.getLongExtra(EXTRA_HIGHLIGHT_ID, -1L)

        val cols = prefs.getInt("cols", 3)
        grid = GridLayoutManager(this, cols)
        adapter.columns = cols
        b.list.layoutManager = grid
        b.list.adapter = adapter
        touch.attachToRecyclerView(b.list)
        updateGridButton()

        b.btnGrid.setOnClickListener {
            val n = if (grid.spanCount == 3) 4 else 3
            prefs.edit().putInt("cols", n).apply()
            grid.spanCount = n
            adapter.columns = n
            adapter.notifyDataSetChanged()
            updateGridButton()
        }
        b.fab.setOnClickListener { showNewStory() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingHighlight = intent.getLongExtra(EXTRA_HIGHLIGHT_ID, -1L)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val list = repo.stories()
        if (pendingHighlight > 0) {
            val index = list.indexOfFirst { it.id == pendingHighlight }
            if (index >= 0) {
                adapter.highlightId = pendingHighlight
                b.list.scrollToPosition(index)
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({ adapter.clearHighlight() }, 4000)
            }
            pendingHighlight = -1L
        }
        adapter.submit(list)
        b.empty.isVisible = list.isEmpty()
        b.subtitle.text = "${list.size} ${if (list.size == 1) "story" else "stories"}"
    }

    private fun updateGridButton() {
        b.btnGrid.text = "${grid.spanCount}×${grid.spanCount}"
    }

    private fun openStory(s: Story) {
        startActivity(Intent(this, StoryActivity::class.java).putExtra(StoryActivity.EXTRA_STORY_ID, s.id))
    }

    private fun showNewStory() {
        picked = emptyList()
        val v = DialogNewStoryBinding.inflate(layoutInflater)
        pickBtn = v.pickBtn
        v.nameLayout.setEndIconOnClickListener { voice.listen(v.nameInput) }
        v.pickBtn.setOnClickListener {
            media.pick(multiple = true) { uris ->
                picked = uris
                pickBtn?.text = "${uris.size} selected · tap to change"
            }
        }
        MaterialAlertDialogBuilder(this)
            .setView(v.root)
            .setPositiveButton("Create") { _, _ ->
                val name = v.nameInput.text?.toString().orEmpty().trim()
                val uris = picked
                runBusy("Creating story…", onDone = { refresh() }) { repo.createStory(name, uris) }
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { pickBtn = null }
            .show()
    }
}
