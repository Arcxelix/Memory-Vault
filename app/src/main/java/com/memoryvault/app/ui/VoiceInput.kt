package com.memoryvault.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale

/**
 * Speech-to-text using the phone's own recogniser (Google voice typing, Samsung, etc.).
 * Create it as a field of an Activity or Fragment, then call listen(editText).
 */
class VoiceInput(caller: ActivityResultCaller) {
    private var target: EditText? = null

    private val launcher = caller.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val field = target
        if (field != null && res.resultCode == Activity.RESULT_OK) {
            val heard = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!heard.isNullOrBlank()) {
                val current = field.text?.toString().orEmpty()
                val merged = if (current.isBlank()) heard else current.trimEnd() + " " + heard
                field.setText(merged)
                field.setSelection(merged.length)
            }
        }
    }

    fun listen(field: EditText) {
        target = field
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(field.context, "Speech recognition isn't available on this phone", Toast.LENGTH_LONG).show()
        }
    }
}
