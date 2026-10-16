package com.orbitplayer.app

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore

/** Reads only videos Android allows this app to access. Performs synchronous I/O: call off the UI thread. */
class VideoRepository(private val context: Context) {
    fun load(): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        val columns = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            columns.add(MediaStore.Video.Media.RELATIVE_PATH)
        }
        val projection = columns.toTypedArray()
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthIndex = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
            val heightIndex = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val bucketIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val relativeIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH) else -1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                val rawFolder = if (relativeIndex >= 0) cursor.getString(relativeIndex) else null
                val folder = rawFolder?.trim('/')?.takeIf { it.isNotBlank() }
                    ?: cursor.getString(bucketIndex)?.takeIf { it.isNotBlank() } ?: "Other"
                result.add(
                    VideoItem(
                        uri = uri,
                        title = cursor.getString(nameIndex) ?: "Untitled video",
                        durationMs = cursor.getLong(durationIndex),
                        sizeBytes = cursor.getLong(sizeIndex),
                        folder = folder,
                        modifiedSeconds = cursor.getLong(dateIndex),
                        width = if (widthIndex >= 0) cursor.getInt(widthIndex) else 0,
                        height = if (heightIndex >= 0) cursor.getInt(heightIndex) else 0
                    )
                )
            }
        }
        return result
    }
}
