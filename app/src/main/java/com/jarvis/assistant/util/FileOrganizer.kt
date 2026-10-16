package com.jarvis.assistant.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/**
 * DEV STYLE: File Organizer — the boss picks a folder (usually Downloads),
 * Jarvis sorts every file into Images / Videos / Music / Docs / APKs / Others.
 * Runs entirely through SAF, so it works on modern Android without root.
 */
object FileOrganizer {

    private const val TAG = "FileOrganizer"

    private val FOLDERS = linkedMapOf(
        "Images" to setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic"),
        "Videos" to setOf("mp4", "mkv", "mov", "avi", "3gp", "webm"),
        "Music" to setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus"),
        "Docs" to setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md"),
        "APKs" to setOf("apk")
    )

    fun organize(context: Context, treeUri: Uri): String {
    return try {
        val resolver = context.contentResolver
        val rootDoc = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDoc)
        val proj = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val kids = mutableListOf<Pair<String, String>>() // docId to name
        resolver.query(childrenUri, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val mime = c.getString(2) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val name = c.getString(1) ?: continue
                kids.add(c.getString(0) to name)
            }
        }
        if (kids.isEmpty()) return "Boss, oi folder e kono file nai — ar organize korar kichu nai."

        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDoc)
        val dirIds = mutableMapOf<String, String>()
        for (name in FOLDERS.keys + "Others") {
            runCatching {
                val existing = findChildDir(resolver, treeUri, rootDoc, name)
                val created = existing
                    ?: DocumentsContract.createDocument(resolver, rootUri, DocumentsContract.Document.MIME_TYPE_DIR, name)
                        ?.let { DocumentsContract.getDocumentId(it) }
                if (created != null) dirIds[name] = created
            }.onFailure { Log.w(TAG, "Create folder " + name + " failed: " + it.message) }
        }

        val counts = linkedMapOf<String, Int>()
        var moved = 0
        for ((docId, name) in kids) {
            val folder = folderFor(name)
            val targetId = dirIds[folder] ?: continue
            if (targetId == rootDoc) continue
            runCatching {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val sourceParent = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDoc)
                val targetUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, targetId)
                DocumentsContract.moveDocument(resolver, docUri, sourceParent, targetUri)
                moved++
                counts[folder] = (counts[folder] ?: 0) + 1
            }.onFailure { Log.w(TAG, "Move " + name + " failed: " + it.message) }
        }

        if (moved == 0) {
            "Boss, file gosto korte parlam na — oi folder er permission thakle abar try korbo."
        } else {
            val summary = counts.entries.joinToString(", ") { it.value.toString() + " " + it.key.lowercase() }
            "Done boss! " + moved + " file organize korlam — " + summary + "."
        }
    } catch (e: Exception) {
        Log.e(TAG, "organize failed", e)
        "File organize korte giye problem holo boss: " + (e.message ?: "unknown error")
    }
    }

    private fun folderFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return FOLDERS.entries.firstOrNull { it.value.contains(ext) }?.key ?: "Others"
    }

    private fun findChildDir(resolver: android.content.ContentResolver, treeUri: Uri, rootDoc: String, name: String): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDoc)
        resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val mime = c.getString(2) ?: ""
                val label = c.getString(1) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR && label.equals(name, ignoreCase = true)) {
                    return c.getString(0)
                }
            }
        }
        return null
    }
}
