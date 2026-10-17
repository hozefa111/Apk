package com.orbitplayer.app

/**
 * A small, allocation-conscious cue index for external SRT and basic WebVTT files.
 * This enables real per-file subtitle timing shifts without modifying Media3's internals.
 */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

class SubtitleTimeline private constructor(private val cues: List<SubtitleCue>) {
    val size: Int get() = cues.size

    fun textAt(positionMs: Long, delayMs: Long = 0L): String {
        val target = positionMs - delayMs
        var low = 0
        var high = cues.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startMs <= target) low = mid + 1 else high = mid - 1
        }
        // Consecutive cues may overlap slightly. Check a small backward window.
        for (i in high downTo (high - 4).coerceAtLeast(0)) {
            val cue = cues[i]
            if (target >= cue.startMs && target < cue.endMs) return cue.text
        }
        return ""
    }

    companion object {
        private val stamp = Regex("""(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")
        private fun ms(value: String): Long? {
            val m = stamp.matchEntire(value.trim()) ?: return null
            val hours = m.groupValues[1].toLongOrNull() ?: 0L
            val minutes = m.groupValues[2].toLongOrNull() ?: return null
            val seconds = m.groupValues[3].toLongOrNull() ?: return null
            val fraction = m.groupValues[4].padEnd(3, '0').take(3).toLongOrNull() ?: return null
            if (minutes >= 60 || seconds >= 60) return null
            return (hours * 3600 + minutes * 60 + seconds) * 1000 + fraction
        }

        fun parse(data: String): SubtitleTimeline {
            val blocks = data.replace("\r\n", "\n").replace('\r', '\n')
                .removePrefix("\uFEFF").split(Regex("\\n\\s*\\n"))
            val parsed = ArrayList<SubtitleCue>(blocks.size.coerceAtMost(30000))
            for (block in blocks) {
                if (parsed.size >= 30000) break
                val lines = block.lines()
                val timeIndex = lines.indexOfFirst { "-->" in it }
                if (timeIndex < 0 || timeIndex == lines.lastIndex) continue
                val pair = lines[timeIndex].split("-->", limit = 2)
                if (pair.size != 2) continue
                val start = ms(pair[0]) ?: continue
                val end = ms(pair[1].trim().substringBefore(' ')) ?: continue
                if (end <= start) continue
                val text = lines.drop(timeIndex + 1).joinToString("\n")
                    .replace(Regex("<[^>]*>"), "").trim()
                if (text.isNotBlank()) parsed.add(SubtitleCue(start, end, text))
            }
            return SubtitleTimeline(parsed.sortedBy(SubtitleCue::startMs))
        }
    }
}
