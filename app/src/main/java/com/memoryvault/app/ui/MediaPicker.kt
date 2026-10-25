package com.memoryvault.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * One chooser used everywhere a picture is needed: gallery, camera photo or camera video.
 * With multiple = true the camera keeps going ("Capture another?") until the user taps Done.
 * Camera capture uses the system camera app, so no CAMERA permission is needed.
 */
class MediaPicker(caller: ActivityResultCaller, private val context: () -> Context) {

    private var multiple = false
    private var imagesOnly = false
    private var callback: ((List<Uri>) -> Unit)? = null
    private val captured = mutableListOf<Uri>()
    private var pendingUri: Uri? = null

    private val pickMany = caller.registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { deliver(it) }

    private val pickOne = caller.registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { deliver(listOfNotNull(it)) }

    private val takePhoto = caller.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> afterCapture(ok) }

    private val takeVideo = caller.registerForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { ok -> afterCapture(ok) }

    fun pick(multiple: Boolean, imagesOnly: Boolean = false, onResult: (List<Uri>) -> Unit) {
        this.multiple = multiple
        this.imagesOnly = imagesOnly
        callback = onResult
        captured.clear()
        val labels = mutableListOf("Choose from gallery", "Take a photo")
        if (!imagesOnly) labels.add("Record a video")
        MaterialAlertDialogBuilder(context())
            .setTitle(if (imagesOnly) "Choose a photo" else "Choose a photo or video")
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    0 -> openGallery()
                    1 -> capture(false)
                    else -> capture(true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openGallery() {
        val type = if (imagesOnly) {
            ActivityResultContracts.PickVisualMedia.ImageOnly
        } else {
            ActivityResultContracts.PickVisualMedia.ImageAndVideo
        }
        if (multiple) pickMany.launch(PickVisualMediaRequest(type)) else pickOne.launch(PickVisualMediaRequest(type))
    }

    private fun capture(video: Boolean) {
        val ctx = context()
        val dir = File(ctx.cacheDir, "capture").also { it.mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}.${if (video) "mp4" else "jpg"}")
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        pendingUri = uri
        try {
            if (video) takeVideo.launch(uri) else takePhoto.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No camera app found on this phone", Toast.LENGTH_LONG).show()
        }
    }

    private fun afterCapture(ok: Boolean) {
        val uri = pendingUri
        pendingUri = null
        if (ok && uri != null) captured.add(uri)
        if (!multiple) {
            deliver(captured.toList())
            return
        }
        if (captured.isEmpty()) return
        val dialog = MaterialAlertDialogBuilder(context())
            .setTitle("${captured.size} captured")
            .setMessage("Capture another?")
            .setCancelable(false)
            .setPositiveButton("Photo") { _, _ -> capture(false) }
            .setNeutralButton("Done") { _, _ -> deliver(captured.toList()) }
        if (!imagesOnly) dialog.setNegativeButton("Video") { _, _ -> capture(true) }
        dialog.show()
    }

    private fun deliver(uris: List<Uri>) {
        val cb = callback
        callback = null
        if (uris.isNotEmpty()) cb?.invoke(uris)
    }
}
