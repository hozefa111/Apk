package com.orbitplayer.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import java.lang.ref.WeakReference
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Activity-owned cache. All bookkeeping runs on main; only two decodes run concurrently. */
class ThumbnailLoader(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val handler = Handler(Looper.getMainLooper())
    private val pool = ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue(64))
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private class Target(view: ImageView, val ready: () -> Unit) { val view = WeakReference(view) }
    private class Request(val key: String) {
        val targets = mutableListOf<Target>()
        val signal = CancellationSignal()
        lateinit var task: FutureTask<Unit>
    }
    private val requests = mutableMapOf<String, Request>()
    private var disposed = false

    fun load(view: ImageView, item: VideoItem, ready: () -> Unit = {}) {
        val key = "${item.uri}|${item.modifiedSeconds}|${item.sizeBytes}"
        if (view.tag == key) {
            cache.get(key)?.let { view.setImageBitmap(it); ready(); return }
            if (requests.containsKey(key)) return
        }
        clear(view)
        if (disposed) return
        view.tag = key
        cache.get(key)?.let { view.setImageBitmap(it); ready(); return }
        requests[key]?.let { it.targets.add(Target(view, ready)); return }
        val request = Request(key)
        request.targets.add(Target(view, ready))
        request.task = FutureTask {
            val image = try {
                if (Build.VERSION.SDK_INT >= 29)
                    resolver.loadThumbnail(item.uri, Size(384, 216), request.signal)
                else {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(resolver,
                        item.uri.lastPathSegment?.toLongOrNull() ?: -1,
                        MediaStore.Video.Thumbnails.MINI_KIND, null)
                }
            } catch (_: Exception) { null }
            handler.post {
                if (!disposed && requests[key] === request) {
                    requests.remove(key)
                    if (image != null) {
                        cache.put(key, image)
                        request.targets.forEach { target ->
                            target.view.get()?.takeIf { it.tag == key }?.let {
                                it.setImageBitmap(image)
                                target.ready()
                            }
                        }
                    }
                }
            }
        }
        requests[key] = request
        try { pool.execute(request.task) }
        catch (_: java.util.concurrent.RejectedExecutionException) { requests.remove(key) }
    }

    fun clear(view: ImageView) {
        val key = view.tag as? String
        requests[key]?.let { request ->
            request.targets.removeAll { it.view.get() == null || it.view.get() === view }
            if (request.targets.isEmpty()) {
                requests.remove(key)
                request.signal.cancel()
                request.task.cancel(true)
                pool.remove(request.task)
            }
        }
        view.tag = null
        view.setImageDrawable(null)
    }

    fun dispose() {
        disposed = true
        requests.values.forEach { it.signal.cancel(); it.task.cancel(true) }
        requests.clear()
        pool.shutdownNow()
        cache.evictAll()
        handler.removeCallbacksAndMessages(null)
    }
}
