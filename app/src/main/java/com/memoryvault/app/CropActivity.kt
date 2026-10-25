package com.memoryvault.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.memoryvault.app.data.Repository
import com.memoryvault.app.databinding.ActivityCropBinding
import com.memoryvault.app.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Lets the user frame part of a photo; returns the path of the cut-out image. */
class CropActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IN = "in"
        const val EXTRA_OUT = "out"
    }

    private lateinit var b: ActivityCropBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCropBinding.inflate(layoutInflater)
        setContentView(b.root)

        val path = intent.getStringExtra(EXTRA_IN)
        if (path == null) {
            finish()
            return
        }
        b.cancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        b.confirm.setOnClickListener { save() }
        b.confirm.isEnabled = false

        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { loadBitmap(path) }
            if (bmp == null) {
                toast("Couldn't open that photo")
                finish()
            } else {
                b.crop.setBitmap(bmp)
                b.confirm.isEnabled = true
            }
        }
    }

    private fun loadBitmap(path: String): Bitmap? = try {
        Glide.with(applicationContext)
            .asBitmap()
            .load(File(path))
            .disallowHardwareConfig()
            .fitCenter()
            .submit(2048, 2048)
            .get()
    } catch (e: Exception) {
        null
    }

    private fun save() {
        val cut = b.crop.cropped() ?: return
        b.confirm.isEnabled = false
        lifecycleScope.launch {
            val outPath = withContext(Dispatchers.IO) {
                val f = Repository.get(this@CropActivity).newMediaFile("jpg")
                f.outputStream().use { cut.compress(Bitmap.CompressFormat.JPEG, 93, it) }
                f.absolutePath
            }
            setResult(RESULT_OK, Intent().putExtra(EXTRA_OUT, outPath))
            finish()
        }
    }
}
