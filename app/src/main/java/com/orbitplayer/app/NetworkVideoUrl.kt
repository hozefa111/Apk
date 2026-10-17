package com.orbitplayer.app

import java.net.URI

/** Explicit opt-in remote HTTPS streams; never embed credentials in a media URL. */
object NetworkVideoUrl {
    fun validated(raw: String): String? = try {
        val uri = URI(raw.trim())
        if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() ||
            uri.userInfo != null || uri.fragment != null || uri.port !in -1..65535) null
        else uri.toASCIIString()
    } catch (_: Exception) { null }
}
