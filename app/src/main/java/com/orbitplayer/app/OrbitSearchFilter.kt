package com.orbitplayer.app

/** Search filter state is kept separate from UI and allows deterministic unit tests. */
data class OrbitSearchFilter(
    val extension: String = "",
    val minimumHeight: Int = 0,
    val durationGroup: Int = 0,
    val sizeGroup: Int = 0
) {
    fun matches(item: VideoItem): Boolean =
        matchesMetadata(item.title, item.height, item.durationMs, item.sizeBytes)

    fun matchesMetadata(title: String, height: Int, durationMs: Long, sizeBytes: Long): Boolean {
        if (extension.isNotBlank() && !title.endsWith(".$extension", true)) return false
        if (minimumHeight > 0 && height < minimumHeight) return false
        val minutes = durationMs / 60000L
        if (durationGroup == 1 && minutes >= 5) return false
        if (durationGroup == 2 && (minutes < 5 || minutes >= 30)) return false
        if (durationGroup == 3 && minutes < 30) return false
        if (sizeGroup == 1 && sizeBytes >= 100L * 1024 * 1024) return false
        if (sizeGroup == 2 && sizeBytes < 1024L * 1024 * 1024) return false
        return true
    }
    val active get() = extension.isNotBlank() || minimumHeight > 0 ||
            durationGroup != 0 || sizeGroup != 0
}
