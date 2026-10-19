package com.orbitplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** User-initiated update checks only. No background tracking or automatic APK installation. */
object UpdateManager {
    private const val LATEST_URL = "https://api.github.com/repos/hozefa111/Apk/releases/latest"
    private const val PREFS = "orbit_player_updates"
    private const val DOWNLOAD_ID = "download_id"
    private const val DOWNLOAD_VERSION = "download_version"
    private const val APK_MIME = "application/vnd.android.package-archive"

    private data class Release(val version: String, val apkUrl: String?, val pageUrl: String)

    fun check(activity: Activity) {
        if (showDownloadedIfReady(activity)) return
        Toast.makeText(activity, "Checking Orbit Player updates...", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching { fetchLatestRelease() }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                result.onSuccess { release ->
                    val currentVersion = BuildConfig.VERSION_NAME
                    if (!isNewer(release.version, currentVersion)) {
                        AlertDialog.Builder(activity)
                            .setTitle("Orbit Player is up to date")
                            .setMessage("Installed: v$currentVersion\nLatest published: v${release.version}")
                            .setPositiveButton("OK", null).show()
                    } else if (release.apkUrl == null) {
                        AlertDialog.Builder(activity)
                            .setTitle("Update available: v${release.version}")
                            .setMessage("The release has no installable APK attached. Open its webpage?")
                            .setPositiveButton("Open release") { _, _ ->
                                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)))
                            }.setNegativeButton("Cancel", null).show()
                    } else {
                        AlertDialog.Builder(activity)
                            .setTitle("Orbit Player v${release.version} available")
                            .setMessage("Installed: v$currentVersion\n\nDownload the signed update? Android will ask permission before installation. Updates must use the same signing key.")
                            .setPositiveButton("Download") { _, _ -> download(activity, release) }
                            .setNegativeButton("Later", null).show()
                    }
                }.onFailure { error ->
                    val message = error.message?.take(300) ?: "Unable to check GitHub Releases."
                    AlertDialog.Builder(activity)
                        .setTitle("Unable to check updates")
                        .setMessage(message + "\n\nThe offline video player will continue to work normally.")
                        .setPositiveButton("OK", null).show()
                }
            }
        }.start()
    }

    private fun fetchLatestRelease(): Release {
        val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "OrbitPlayer-Android")
            val status = conn.responseCode
            if (status == 404) throw IllegalStateException("No signed update release has been published yet.")
            if (status !in 200..299) throw IllegalStateException("GitHub could not be reached (HTTP $status).")
            val data = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(data)
            val tag = json.optString("tag_name", "").removePrefix("v")
            if (tag.isBlank()) throw IllegalStateException("Release version is missing.")
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    if (asset.optString("name").equals("OrbitPlayer-v$tag.apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                        if (apkUrl != null) break
                    }
                }
            }
            val page = json.optString("html_url").takeIf { it.startsWith("https://") }
                ?: "https://github.com/hozefa111/Apk/releases/latest"
            return Release(tag, apkUrl, page)
        } finally {
            conn.disconnect()
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val a = latestParts.getOrElse(i) { 0 }
            val b = currentParts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun download(activity: Activity, release: Release) {
        val url = release.apkUrl ?: return
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Orbit Player v${release.version}")
                .setDescription("Official GitHub release APK")
                .setMimeType(APK_MIME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, "OrbitPlayer-v${release.version}.apk")
            val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(request)
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(DOWNLOAD_ID, id).putString(DOWNLOAD_VERSION, release.version).apply()
            AlertDialog.Builder(activity)
                .setTitle("Download started")
                .setMessage("Android will notify you when the APK is downloaded. Then open Settings → Check for updates again to install it, or open the download notification.")
                .setPositiveButton("OK", null).show()
        } catch (error: Exception) {
            AlertDialog.Builder(activity).setTitle("Download failed")
                .setMessage(error.message ?: "Could not download the APK.")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun showDownloadedIfReady(activity: Activity): Boolean {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(DOWNLOAD_ID, -1L)
        if (id < 0) return false
        val version = prefs.getString(DOWNLOAD_VERSION, "") ?: ""
        if (!isNewer(version, BuildConfig.VERSION_NAME)) {
            prefs.edit().clear().apply()
            return false
        }
        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return false
        val status = cursor.use {
            if (!it.moveToFirst()) -1
            else it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }
        if (status == DownloadManager.STATUS_FAILED) {
            prefs.edit().clear().apply()
            return false
        }
        if (status != DownloadManager.STATUS_SUCCESSFUL) {
            Toast.makeText(activity, "Your update is still downloading.", Toast.LENGTH_SHORT).show()
            return true
        }
        AlertDialog.Builder(activity)
            .setTitle("Install Orbit Player v$version")
            .setMessage("The update has downloaded. Android will ask for your approval before installing it.")
            .setPositiveButton("Install") { _, _ -> promptInstall(activity, manager, id) }
            .setNegativeButton("Later", null).show()
        return true
    }

    private fun promptInstall(activity: Activity, manager: DownloadManager, id: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle("Allow Orbit Player updates")
                .setMessage("Android requires you to allow app installs from Orbit Player once. After enabling it, return and open Settings → Check for updates → Install again.")
                .setPositiveButton("Open settings") { _, _ ->
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${activity.packageName}")))
                }.setNegativeButton("Cancel", null).show()
            return
        }
        val uri = manager.getUriForDownloadedFile(id)
        if (uri == null) {
            Toast.makeText(activity, "Downloaded APK is not available. Please check Downloads.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (error: Exception) {
            Toast.makeText(activity, "Cannot open installer: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }
}