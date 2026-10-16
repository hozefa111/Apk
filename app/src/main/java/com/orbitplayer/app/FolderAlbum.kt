package com.orbitplayer.app

/** A logical album grouped by the full MediaStore relative path, never a fuzzy name. */
data class FolderAlbum(
    val path: String,
    val count: Int,
    val cover: VideoItem,
    val items: List<VideoItem>,
    val pinned: Boolean = false
) {
    val displayName: String get() = path.trim('/').substringAfterLast('/').ifBlank { "Other videos" }
}
