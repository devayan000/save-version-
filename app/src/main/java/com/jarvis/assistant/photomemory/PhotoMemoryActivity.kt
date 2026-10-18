package com.jarvis.assistant.photomemory

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.scanner.OcrProcessor
import com.jarvis.assistant.util.ThemeManager
import java.io.File

/** Photo Memory library: save, view, search, edit, delete photos with metadata. */
class PhotoMemoryActivity : AppCompatActivity() {

    private lateinit var store: PhotoMemoryStore
    private lateinit var listContainer: LinearLayout
    private var pendingPhotoPath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        store = PhotoMemoryStore(this)
        buildUi()
        refresh()
    }

    private fun buildUi() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        TextView(this).apply { text = "Photo Memory Library"; textSize = 20f; setPadding(0, 0, 0, 16); container.addView(this) }
        container.addView(Button(this).apply { com.jarvis.assistant.util.PremiumUi.premiumButton(this); text = "Add Photo from Gallery"; setOnClickListener { openGalleryPicker() } })
        val searchBox = EditText(this).apply { hint = "Search by description, tags, date..."; setSingleLine() }
        container.addView(searchBox)
        container.addView(Button(this).apply { com.jarvis.assistant.util.PremiumUi.premiumButton(this); text = "Search"; setOnClickListener { refresh(searchBox.text.toString()) } })
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 0) }
        val scroll = ScrollView(this).apply { addView(listContainer); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }
        container.addView(scroll)
        container.addView(Button(this).apply { com.jarvis.assistant.util.PremiumUi.premiumButton(this); text = "Close"; setOnClickListener { finish() } })
        setContentView(container)
    }

    private fun openGalleryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }
        startActivityForResult(intent, 4001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            4001 -> {
                // Copy to a temp file for processing
                val tempFile = File(cacheDir, "temp_photo_" + System.currentTimeMillis() + ".jpg")
                contentResolver.openInputStream(uri)?.use { input -> tempFile.outputStream().use { input.copyTo(it) } }
                pendingPhotoPath = tempFile.absolutePath
                showSaveDialog(tempFile.absolutePath)
            }
        }
    }

    private fun showSaveDialog(photoPath: String) {
        val ctx = this
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }

        // Preview
        val imageView = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400)
            scaleType = ImageView.ScaleType.CENTER_CROP
            val bmp = BitmapFactory.decodeFile(photoPath)
            setImageBitmap(bmp)
        }
        container.addView(imageView)

        val description = EditText(ctx).apply { hint = "Description (what you told me about this)"; container.addView(this) }
        val contextInput = EditText(ctx).apply { hint = "Context (where, why)"; container.addView(this) }
        val date = EditText(ctx).apply { hint = "Date (e.g. 2026-10-17)"; container.addView(this) }
        val tags = EditText(ctx).apply { hint = "Tags (comma-separated)"; container.addView(this) }
        val notes = EditText(ctx).apply { hint = "Personal notes"; container.addView(this) }

        // OCR toggle
        val runOcr = booleanArrayOf(false)
        container.addView(Button(ctx).apply { com.jarvis.assistant.util.PremiumUi.premiumButton(this); text = "Run OCR (on-device)"; setOnClickListener {
            val bmp = BitmapFactory.decodeFile(photoPath)
            OcrProcessor.recognize(bmp, { text ->
                runOnUiThread { Toast.makeText(ctx, "OCR: " + text.take(100), Toast.LENGTH_LONG).show() }
            }, { err -> runOnUiThread { Toast.makeText(ctx, "OCR failed: $err", Toast.LENGTH_SHORT).show() } })
        } })

        MaterialAlertDialogBuilder(ctx).setTitle("Save to Photo Memory").setView(ScrollView(ctx).apply { addView(container) })
            .setPositiveButton("Save") { _, _ ->
                val tagList = tags.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }
                store.savePhoto(photoPath, description.text.toString().trim(), contextInput.text.toString().trim(),
                    date.text.toString().trim(), tagList, notes.text.toString().trim(), "", false)
                Toast.makeText(ctx, "Photo memory saved.", Toast.LENGTH_SHORT).show()
                refresh()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun refresh(searchQuery: String = "") {
        listContainer.removeAllViews()
        val memories = if (searchQuery.isBlank()) store.getAll() else store.search(searchQuery)
        if (memories.isEmpty()) {
            listContainer.addView(TextView(this).apply { text = "No photo memories found."; setPadding(0, 16, 0, 0) })
            return
        }
        for (mem in memories) {
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24); setBackgroundResource(android.R.color.white) }
            // Thumbnail
            val photoFile = File(mem.filePath)
            if (photoFile.exists()) {
                val bmp = BitmapFactory.decodeFile(mem.filePath)
                if (bmp != null) {
                    val imageView = ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 300)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageBitmap(bmp)
                    }
                    card.addView(imageView)
                }
            }
            card.addView(TextView(this).apply { text = mem.description.ifBlank { "(No description)" }; textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD) })
            if (mem.context.isNotBlank()) card.addView(TextView(this).apply { text = "Context: " + mem.context; textSize = 13f; setPadding(0, 4, 0, 0) })
            if (mem.date.isNotBlank()) card.addView(TextView(this).apply { text = "Date: " + mem.date; textSize = 13f; setPadding(0, 2, 0, 0) })
            if (mem.tags.isNotEmpty()) card.addView(TextView(this).apply { text = "Tags: " + mem.tags.joinToString(", "); textSize = 13f; setPadding(0, 2, 0, 0) })
            if (mem.personalNotes.isNotBlank()) card.addView(TextView(this).apply { text = "Notes: " + mem.personalNotes; textSize = 13f; setPadding(0, 2, 0, 0) })
            if (mem.ocrText.isNotBlank()) card.addView(TextView(this).apply { text = (if (mem.ocrDerived) "[OCR-derived] " else "") + mem.ocrText.take(100); textSize = 12f; setPadding(0, 2, 0, 0) })

            val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0) }
            btnRow.addView(Button(this).apply { com.jarvis.assistant.util.PremiumUi.premiumButton(this); text = "Edit"; setOnClickListener { showEditDialog(mem.id) } })
            btnRow.addView(Button(this).apply { com.jarvis.assistant.util.PremiumUi.premiumButton(this); text = "Delete"; setOnClickListener {
                MaterialAlertDialogBuilder(this@PhotoMemoryActivity).setTitle("Delete Photo Memory").setMessage("Permanently delete this photo and its metadata?")
                    .setPositiveButton("Delete") { _, _ -> store.deleteMemory(mem.id); refresh(searchQuery) }.setNegativeButton("Cancel", null).show()
            } })
            card.addView(btnRow)
            listContainer.addView(card)
        }
    }

    private fun showEditDialog(id: String) {
        val mem = store.get(id) ?: return
        val ctx = this
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        val description = EditText(ctx).apply { hint = "Description"; setText(mem.description); container.addView(this) }
        val contextInput = EditText(ctx).apply { hint = "Context"; setText(mem.context); container.addView(this) }
        val date = EditText(ctx).apply { hint = "Date"; setText(mem.date); container.addView(this) }
        val tags = EditText(ctx).apply { hint = "Tags (comma-separated)"; setText(mem.tags.joinToString(",")); container.addView(this) }
        val notes = EditText(ctx).apply { hint = "Personal notes"; setText(mem.personalNotes); container.addView(this) }
        MaterialAlertDialogBuilder(ctx).setTitle("Edit Photo Memory").setView(ScrollView(ctx).apply { addView(container) })
            .setPositiveButton("Save") { _, _ ->
                val tagList = tags.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }
                store.updateMemory(id, description.text.toString().trim(), contextInput.text.toString().trim(),
                    date.text.toString().trim(), tagList, notes.text.toString().trim())
                refresh()
            }.setNegativeButton("Cancel", null).show()
    }
}
