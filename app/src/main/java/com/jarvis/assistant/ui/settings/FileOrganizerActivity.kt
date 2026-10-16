package com.jarvis.assistant.ui.settings

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.util.FileOrganizer

/**
 * DEV STYLE: File Organizer entry — asks the boss which folder to organize
 * (usually Downloads), then hands the tree to FileOrganizer and reports back
 * through Jarvis's own voice when the live session is on.
 */
class FileOrganizerActivity : AppCompatActivity() {

    private val treePicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val summary = FileOrganizer.organize(this, uri)
            Toast.makeText(this, summary, Toast.LENGTH_LONG).show()
            if (JarvisConversationService.isServiceRunning()) {
                runCatching {
                    JarvisConversationService.sendTextMessage(
                        "[DEV STYLE SYSTEM NOTE: Tell the boss this result in one short natural line: $summary]"
                    )
                }
            }
        } else {
            Toast.makeText(this, "Folder select kora holo na, boss", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        treePicker.launch(null)
    }
}
