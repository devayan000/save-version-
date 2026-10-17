package com.jarvis.assistant.media

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.assistant.share.ShareReceiverActivity

/** Local media library. Uses Photo Picker on Android 13+, SAF for documents, MediaStore for older versions. */
class MediaLibraryActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var resultsText: TextView
    private lateinit var typeFilterText: TextView
    private var currentType: String = "all" // all, image, video, audio, document

    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) showMediaItem(uri)
    }

    private val multiPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) loadMedia()
        else Toast.makeText(this, "Media permission denied. Use the Photo Picker for individual files.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 40, 28, 28) }
        val title = TextView(this).apply { textSize = 18f; text = "Local Media Library" }
        container.addView(title)
        typeFilterText = TextView(this).apply { textSize = 14f; text = "Filter: All types"; setPadding(0, 12, 0, 12) }
        container.addView(typeFilterText)
        searchInput = EditText(this).apply { hint = "Search by name..."; isSingleLine = true }
        container.addView(searchInput)
        resultsText = TextView(this).apply { textSize = 14f; setTextIsSelectable(true); setPadding(12, 16, 12, 16) }
        val scroll = ScrollView(this).apply { addView(resultsText) }
        container.addView(scroll)
        fun btn(label: String, action: () -> Unit) {
            container.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        btn("Search") { loadMedia() }
        btn("Filter: All") { currentType = "all"; typeFilterText.text = "Filter: All types" }
        btn("Filter: Images") { currentType = "image"; typeFilterText.text = "Filter: Images" }
        btn("Filter: Videos") { currentType = "video"; typeFilterText.text = "Filter: Videos" }
        btn("Filter: Audio") { currentType = "audio"; typeFilterText.text = "Filter: Audio" }
        btn("Filter: Documents (SAF)") { openDocumentSaf() }
        btn("Photo Picker") { openPhotoPicker() }
        btn("Find Duplicates") { findDuplicates() }
        btn("Close") { finish() }
        setContentView(container)
        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(container)
    }

    private fun hasMediaPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMediaPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            multiPermission.launch(arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            ))
        } else {
            multiPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    private fun loadMedia() {
        if (!hasMediaPermission()) { requestMediaPermission(); return }
        val query = searchInput.text.toString().trim()
        val items = MediaScanner.queryMedia(this, currentType, query)
        if (items.isEmpty()) { resultsText.text = "No media found" + (if (query.isNotBlank()) " matching '$query'" else "") + "."; return }
        val sb = StringBuilder("Found " + items.size + " items:\n\n")
        for (item in items.take(50)) {
            sb.append("• ").append(item.name).append("\n  ").append(item.type).append(" | ").append(item.sizeLabel).append(" | ").append(item.date).append("\n")
        }
        if (items.size > 50) sb.append("\n...and ").append(items.size - 50).append(" more (narrow your search to see them)")
        resultsText.text = sb.toString()
    }

    private fun openPhotoPicker() {
        // Photo Picker requires no permission on Android 13+; falls back to gallery intent on older.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        } else {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply { type = "image/*" }
            try { startActivity(intent) } catch (_: Exception) { Toast.makeText(this, "No gallery app available.", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun openDocumentSaf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }
        try { startActivityForResult(Intent.createChooser(intent, "Select a document"), 100) }
        catch (_: Exception) { Toast.makeText(this, "No document picker available.", Toast.LENGTH_SHORT).show() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            data?.data?.let { showMediaItem(it) }
        }
    }

    private fun showMediaItem(uri: Uri) {
        val item = MediaScanner.resolveUri(this, uri)
        val sb = StringBuilder("Selected item:\n")
        sb.append("Name: ").append(item.name).append("\n")
        sb.append("Type: ").append(item.type).append("\n")
        sb.append("Size: ").append(item.sizeLabel).append("\n")
        sb.append("URI: ").append(uri.toString()).append("\n\n")
        sb.append("Use Share to Jarvis to send this item's name/path to Jarvis for processing.")
        resultsText.text = sb.toString()
    }

    private fun findDuplicates() {
        if (!hasMediaPermission()) { requestMediaPermission(); return }
        val dupes = MediaScanner.findDuplicates(this, currentType)
        if (dupes.isEmpty()) { resultsText.text = "No duplicates found."; return }
        val sb = StringBuilder("Possible duplicates (" + dupes.size + " groups):\n\n")
        for ((name, count) in dupes.take(20)) sb.append("• ").append(name).append(" (").append(count).append(" copies)\n")
        resultsText.text = sb.toString()
    }
}
