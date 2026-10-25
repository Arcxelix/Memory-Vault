package com.memoryvault.app.ui

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.memoryvault.app.StoryActivity
import com.memoryvault.app.data.Item
import com.memoryvault.app.data.Repository
import com.memoryvault.app.databinding.SheetItemBinding
import java.io.File

/** The drawer that slides up when a photo, video or tag is tapped. The pen edits name, description and notes. */
@OptIn(UnstableApi::class)
class ItemSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_ID = "id"

        fun show(fm: FragmentManager, id: Long) {
            ItemSheet().apply { arguments = bundleOf(ARG_ID to id) }.show(fm, "item")
        }
    }

    private var _b: SheetItemBinding? = null
    private val b get() = _b!!
    private val voice = VoiceInput(this)
    private val repo by lazy { Repository.get(requireContext()) }
    private var item: Item? = null
    private var player: ExoPlayer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = SheetItemBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val loaded = repo.item(requireArguments().getLong(ARG_ID))
        if (loaded == null) {
            dismiss()
            return
        }
        item = loaded
        bindMedia(loaded)
        showView()

        b.btnEdit.setOnClickListener { startEdit() }
        b.btnDelete.setOnClickListener { confirmDelete() }
        b.btnCancel.setOnClickListener {
            showView()
            hideKeyboard()
        }
        b.btnSave.setOnClickListener { save() }
        b.nameLayout.setEndIconOnClickListener { voice.listen(b.nameInput) }
        b.descLayout.setEndIconOnClickListener { voice.listen(b.descInput) }
        b.notesLayout.setEndIconOnClickListener { voice.listen(b.notesInput) }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroyView() {
        b.player.player = null
        player?.release()
        player = null
        _b = null
        super.onDestroyView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? StoryActivity)?.onSheetClosed()
    }

    private fun bindMedia(i: Item) {
        if (i.video) {
            b.image.visibility = View.GONE
            b.player.visibility = View.VISIBLE
            val p = ExoPlayer.Builder(requireContext())
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .build()
            p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(i.path))))
            p.prepare()
            p.playWhenReady = true
            b.player.player = p
            player = p
        } else {
            b.player.visibility = View.GONE
            b.image.visibility = View.VISIBLE
            Glide.with(this).load(File(i.path)).into(b.image)
        }
    }

    private fun showView() {
        val i = item ?: return
        val name = i.name
        val desc = i.description
        val notes = i.notes
        b.name.text = if (name.isNullOrBlank()) "Untitled" else name
        b.name.alpha = if (name.isNullOrBlank()) 0.45f else 1f
        b.desc.text = if (desc.isNullOrBlank()) "No description yet. Tap the pen to add one." else desc
        b.desc.alpha = if (desc.isNullOrBlank()) 0.45f else 1f
        b.notes.text = if (notes.isNullOrBlank()) "No notes yet. Tap the pen to write about this moment." else notes
        b.notes.alpha = if (notes.isNullOrBlank()) 0.45f else 1f
        setEditing(false)
    }

    private fun startEdit() {
        val i = item ?: return
        b.nameInput.setText(i.name.orEmpty())
        b.descInput.setText(i.description.orEmpty())
        b.notesInput.setText(i.notes.orEmpty())
        setEditing(true)
    }

    private fun setEditing(on: Boolean) {
        b.name.isVisible = !on
        b.nameLayout.isVisible = on
        b.desc.isVisible = !on
        b.descLayout.isVisible = on
        b.notes.isVisible = !on
        b.notesLayout.isVisible = on
        b.editBar.isVisible = on
        b.btnEdit.isVisible = !on
        b.btnDelete.isVisible = !on
    }

    private fun save() {
        val i = item ?: return
        repo.updateItemText(
            i.id,
            b.nameInput.text?.toString().orEmpty().trim(),
            b.descInput.text?.toString().orEmpty().trim(),
            b.notesInput.text?.toString().orEmpty().trim()
        )
        item = repo.item(i.id)
        showView()
        hideKeyboard()
    }

    private fun confirmDelete() {
        val i = item ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete this ${if (i.kind == 1) "tag" else "memory"}?")
            .setMessage("It will be removed from the story for good.")
            .setPositiveButton("Delete") { _, _ ->
                repo.deleteItem(i.id)
                dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(b.root.windowToken, 0)
    }
}
