package com.orbitplayer.app

import java.util.Locale

internal fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

internal fun formatSize(bytes: Long): String = when {
    bytes < 0 -> "Unknown size"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
}

/**
 * API 26-28 do not expose MediaStore RELATIVE_PATH. Derive a stable full album
 * path from DATA *only* on those old Android versions. No file is opened or moved.
 */
internal fun legacyMediaFolder(absoluteFilePath: String?): String? {
    val parent = absoluteFilePath?.substringBeforeLast('/', "")?.trimEnd('/')
        ?.takeIf { it.isNotBlank() } ?: return null
    val relative = when {
        parent == "/storage/emulated/0" -> ""
        parent.startsWith("/storage/emulated/0/") -> parent.removePrefix("/storage/emulated/0/")
        parent == "/sdcard" -> ""
        parent.startsWith("/sdcard/") -> parent.removePrefix("/sdcard/")
        parent.startsWith("/storage/") -> parent.removePrefix("/storage/")
        else -> parent.trim('/')
    }
    return relative.trim('/').takeIf { it.isNotBlank() }
}

// Only recognized file extensions are offered in the library's Format filter.
// Case-insensitive and independent of URI/provider display-name capitalization.
internal fun videoExtension(filename: String): String {
    val extension = filename.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return extension.takeIf {
        it.length in 2..6 && it.all(Char::isLetterOrDigit)
    } ?: ""
}
