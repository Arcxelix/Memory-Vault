package com.memoryvault.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.memoryvault.app.data.Repository
import com.memoryvault.app.databinding.ActivityEditorBinding
import com.memoryvault.app.ui.BitmapOps
import com.memoryvault.app.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Crop + basic editing (rotate, flip, brightness, contrast, colour) for any photo.
 * MODE_TAG: cut a piece out of a photo for a tag.  MODE_EDIT: edit a photo, save over it or as a copy.
 * Returns the path of a NEW file; the original is never touched here.
 */
class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IN = "in"
        const val EXTRA_OUT = "out"
        const val EXTRA_MODE = "mode"
        const val EXTRA_AS_COPY = "as_copy"
        const val MODE_TAG = "tag"
        const val MODE_EDIT = "edit"
    }

    private lateinit var b: ActivityEditorBinding
    private var source = ""
    private var tagMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(b.root)

        val path = intent.getStringExtra(EXTRA_IN)
        if (path == null) {
            finish()
            return
        }
        source = path
        tagMode = intent.getStringExtra(EXTRA_MODE) != MODE_EDIT

        b.editorTitle.text = if (tagMode) "Cut out a tag" else "Edit photo"
        b.editorHint.text = if (tagMode) "Frame the part you want. Drag corners, edges or the frame." else "Crop, rotate, flip or adjust the look."
        b.saveCopy.isVisible = !tagMode
        b.confirm.text = if (tagMode) "Cut tag" else "Save"

        b.btnRotL.setOnClickListener { b.crop.rotate(-1) }
        b.btnRotR.setOnClickListener { b.crop.rotate(1) }
        b.btnFlip.setOnClickListener { b.crop.flip() }
        b.btnAdjust.setOnClickListener {
            b.adjustPanel.visibility = if (b.adjustPanel.isVisible) View.GONE else View.VISIBLE
        }
        val live = Slider.OnChangeListener { _, _, _ -> b.crop.setFilter(currentFilter()) }
        b.sBright.addOnChangeListener(live)
        b.sContrast.addOnChangeListener(live)
        b.sSat.addOnChangeListener(live)

        b.cancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        b.confirm.setOnClickListener { save(false) }
        b.saveCopy.setOnClickListener { save(true) }
        setButtonsEnabled(false)

        lifecycleScope.launch {
            val preview = withContext(Dispatchers.IO) {
                try {
                    BitmapOps.load(this@EditorActivity, source, 1600)
                } catch (t: Throwable) {
                    null
                }
            }
            if (preview == null) {
                toast("Couldn't open that photo")
                setResult(RESULT_CANCELED)
                finish()
            } else {
                b.crop.setSource(preview, full = !tagMode)
                setButtonsEnabled(true)
            }
        }
    }

    private fun currentFilter() = BitmapOps.filter(b.sBright.value, b.sContrast.value, b.sSat.value)

    private fun setButtonsEnabled(on: Boolean) {
        b.confirm.isEnabled = on
        b.saveCopy.isEnabled = on
    }

    private fun save(asCopy: Boolean) {
        val spec = b.crop.spec()
        val filter = currentFilter()
        setButtonsEnabled(false)
        lifecycleScope.launch {
            val outPath = withContext(Dispatchers.IO) {
                try {
                    // re-load at higher quality and replay exactly what the user did on the preview
                    val hi = BitmapOps.load(this@EditorActivity, source, 3072)
                    val result = BitmapOps.render(hi, spec, filter)
                    val f = Repository.get(this@EditorActivity).newMediaFile("jpg")
                    f.outputStream().use { result.compress(android.graphics.Bitmap.CompressFormat.JPEG, 93, it) }
                    f.absolutePath
                } catch (t: Throwable) {
                    null
                }
            }
            if (outPath == null) {
                toast("Couldn't save that photo")
                setButtonsEnabled(true)
            } else {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_OUT, outPath).putExtra(EXTRA_AS_COPY, asCopy))
                finish()
            }
        }
    }
}
