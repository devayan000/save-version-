package com.jarvis.assistant.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Queries MediaStore for on-device media. Never deletes or moves files. */
object MediaScanner {

    data class MediaItem(
        val name: String,
        val type: String,
        val sizeBytes: Long,
        val dateAdded: Long,
        val uri: String
    ) {
        val sizeLabel: String get() = when {
            sizeBytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", sizeBytes / 1_000_000.0)
            sizeBytes >= 1_000 -> String.format(Locale.US, "%.1f KB", sizeBytes / 1_000.0)
            else -> "$sizeBytes B"
        }
        val date: String get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dateAdded * 1000))
    }

    fun queryMedia(ctx: Context, type: String, search: String): List<MediaItem> {
        val pair: Pair<Uri, String?> = when (type) {
            "image" -> (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI) to "image/"
            "video" -> (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI) to "video/"
            "audio" -> (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) to "audio/"
            else -> (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Files.getContentUri("external")) to null
        }
        val collection = pair.first
        val mimeFilter = pair.second
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val selection = if (search.isNotBlank()) MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?" else null
        val selectionArgs = if (search.isNotBlank()) arrayOf("%" + search + "%") else null
        val sortOrder = MediaStore.MediaColumns.DATE_ADDED + " DESC"
        val items = mutableListOf<MediaItem>()
        try {
            ctx.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor: Cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val mime = cursor.getString(mimeCol) ?: ""
                    if (mimeFilter != null && !mime.startsWith(mimeFilter) && type != "all") continue
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                    items.add(MediaItem(cursor.getString(nameCol), mime.ifBlank { "file" }, cursor.getLong(sizeCol), cursor.getLong(dateCol), uri.toString()))
                }
            }
        } catch (_: Exception) { /* permission or device issue */ }
        return items
    }

    fun resolveUri(ctx: Context, uri: Uri): MediaItem {
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED)
        var name = "Unknown"; var mime = "file"; var size = 0L; var date = 0L
        try {
            ctx.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: name
                    mime = cursor.getString(1) ?: mime
                    size = cursor.getLong(2)
                    date = cursor.getLong(3)
                }
            }
        } catch (_: Exception) { }
        return MediaItem(name, mime, size, date, uri.toString())
    }

    /** Detects duplicates by name + size. Content-hash detection is optional and only when access is permitted. */
    fun findDuplicates(ctx: Context, type: String): List<Pair<String, Int>> {
        val items = queryMedia(ctx, type, "")
        val groups = items.groupBy { it.name + "|" + it.sizeBytes }
        return groups.filter { it.value.size > 1 }.map { it.key.substringBefore("|") to it.value.size }.sortedByDescending { it.second }
    }
}
