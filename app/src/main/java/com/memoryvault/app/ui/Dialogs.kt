package com.memoryvault.app.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.memoryvault.app.R
import com.memoryvault.app.databinding.DialogTextBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

fun Context.showBusy(msg: String): AlertDialog {
    val v = LayoutInflater.from(this).inflate(R.layout.dialog_busy, null)
    v.findViewById<TextView>(R.id.busyText).text = msg
    return MaterialAlertDialogBuilder(this).setView(v).setCancelable(false).show()
}

/** Shows a busy dialog, runs [block] off the main thread-safe coroutine, then calls [onDone]. */
fun AppCompatActivity.runBusy(msg: String, onDone: () -> Unit = {}, block: suspend () -> Unit) {
    val dlg = showBusy(msg)
    lifecycleScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast("Something went wrong: ${e.message}")
        } finally {
            dlg.dismiss()
            onDone()
        }
    }
}

/** A dialog with one text field that can be typed into or dictated with the mic button. */
fun promptText(
    activity: AppCompatActivity,
    title: String,
    hint: String,
    initial: String,
    voice: VoiceInput,
    positive: String = "Save",
    onCancel: (() -> Unit)? = null,
    onOk: (String) -> Unit
) {
    val v = DialogTextBinding.inflate(activity.layoutInflater)
    v.textLayout.hint = hint
    v.textInput.setText(initial)
    v.textInput.setSelection(initial.length)
    v.textLayout.setEndIconOnClickListener { voice.listen(v.textInput) }
    val dlg = MaterialAlertDialogBuilder(activity)
        .setTitle(title)
        .setView(v.root)
        .setPositiveButton(positive) { _, _ -> onOk(v.textInput.text?.toString().orEmpty()) }
        .setNegativeButton("Cancel") { _, _ -> onCancel?.invoke() }
        .setOnCancelListener { onCancel?.invoke() }
        .create()
    dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    dlg.show()
    v.textInput.requestFocus()
}
