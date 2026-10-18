package com.orbitplayer.app

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleExtractor

/** Uses Media3's own parsers, with eager preparation for explicitly chosen sidecars. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object LocalMediaSource {
    fun create(context: Context, video: MediaItem, subtitle: Uri?, mime: String): MediaSource {
        val dataSources = DefaultDataSource.Factory(context.applicationContext)
        val primary = DefaultMediaSourceFactory(dataSources).createMediaSource(video)
        if (subtitle == null) return primary
        val format = Format.Builder()
            .setId("orbit_external_subtitle")
            .setLabel("External subtitles")
            .setSampleMimeType(mime)
            .setLanguage("und")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val parsers = DefaultSubtitleParserFactory()
        val extractors = ExtractorsFactory {
            arrayOf<Extractor>(SubtitleExtractor(parsers.create(format), format))
        }
        // Unlike the default sidecar path's enableLazyLoadingWithSingleTrack,
        // start loading the chosen subtitle while preparing, including when paused.
        // Parsing and file I/O remain on Media3's loader thread.
        val captions = ProgressiveMediaSource.Factory(dataSources, extractors)
            .createMediaSource(MediaItem.fromUri(subtitle))
        return MergingMediaSource(primary, captions)
    }
}
