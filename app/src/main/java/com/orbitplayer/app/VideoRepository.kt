package com.orbitplayer.app

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
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            columns.add(MediaStore.Video.Media.RELATIVE_PATH)
            columns.add(MediaStore.MediaColumns.VOLUME_NAME)
        } else {
            // Only legacy Android exposes full DATA paths for album grouping.
            columns.add(MediaStore.Video.Media.DATA)
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
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val bucketIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val volumeIndex = if (Build.VERSION.SDK_INT >= 29) cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME) else -1
            val relativeIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH) else -1
            val legacyPathIndex = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.Video.Media.DATA) else -1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                val rawFolder = if (relativeIndex >= 0) cursor.getString(relativeIndex) else null
                val legacyFolder = if (legacyPathIndex >= 0)
                    legacyMediaFolder(cursor.getString(legacyPathIndex)) else null
                val folder = if (Build.VERSION.SDK_INT >= 29) mediaFolder(rawFolder,
                    if (volumeIndex >= 0) cursor.getString(volumeIndex) else null, cursor.getString(bucketIndex))
                    else legacyFolder ?: cursor.getString(bucketIndex)?.takeIf { it.isNotBlank() } ?: "Other"
                result.add(
                    VideoItem(
                        uri = uri,
                        title = cursor.getString(nameIndex) ?: "Untitled video",
                        durationMs = cursor.getLong(durationIndex),
                        sizeBytes = cursor.getLong(sizeIndex),
                        folder = folder,
                        modifiedSeconds = cursor.getLong(dateIndex),
                        width = cursor.getInt(widthIndex), height = cursor.getInt(heightIndex)
                    )
                )
            }
        }
        return result
    }
}
