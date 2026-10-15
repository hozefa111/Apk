package com.orbitplayer.app

import android.net.Uri

/** Immutable media metadata. URI access is provided by MediaStore or user-granted SAF. */
data class VideoItem(
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val folder: String,
    val modifiedSeconds: Long
)
