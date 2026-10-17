package com.orbitplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.util.concurrent.Executors

/**
 * Browse SMB2/SMB3 shares directly. Credentials remain only in RAM and are never logged/stored.
 * A selected video is streamed with seek support through SmbVideoDataSource.
 */
class SmbBrowserActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private var destroyed = false
    private val recognized = setOf("mp4","mkv","avi","mov","webm","m4v","ts","3gp","flv","mpeg","mpg")
    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val parent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24),dp(20),dp(24),dp(18))
            setBackgroundColor(0xFF080B12.toInt())
        }
        fun input(label: String, masked: Boolean = false): EditText {
            parent.addView(TextView(this).apply {
                text = label
                setTextColor(0xFFC2CDE3.toInt())
                textSize = 13f
                setPadding(0,dp(13),0,0)
            })
            return EditText(this).also {
                it.setTextColor(Color.WHITE)
                it.setHintTextColor(0xFF7687A1.toInt())
                it.textSize = 15f
                it.setSingleLine(true)
                it.inputType = if (masked)
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
                parent.addView(it, LinearLayout.LayoutParams(-1,dp(51)))
            }
        }
        parent.addView(TextView(this).apply {
            text = "Network folders  ·  SMB2 / SMB3"
            setTextColor(Color.WHITE)
            textSize = 22f
        })
        parent.addView(TextView(this).apply {
            text = "Connect to a share on your own network. Password stays in memory and is never saved."
            setTextColor(0xFF91A5C2.toInt())
            textSize = 12f
            setPadding(0,dp(8),0,0)
        })
        val host = input("Server address (e.g. 192.168.1.20)")
        val shareName = input("Shared folder name")
        val username = input("Username")
        val password = input("Password", true)
        val domain = input("Windows domain (optional)")
        val go = TextView(this).apply {
            text = "CONNECT & BROWSE"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF4259C0.toInt())
            gravity = Gravity.CENTER
            setOnClickListener {
                val h = host.text.toString().trim()
                val sh = shareName.text.toString().trim()
                val name = username.text.toString().trim()
                if (h.isBlank() || sh.isBlank() || name.isBlank() ||
                    h.contains('/') || h.contains('\\') || sh.contains('/') || sh.contains('\\') ||
                    sh == "." || sh == "..") {
                    Toast.makeText(this@SmbBrowserActivity, "Enter a valid server, share and username", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val account = SmbSession.Account(h, sh, name,
                    password.text.toString().toCharArray(), domain.text.toString().trim())
                SmbSession.connect(account)
                password.text.clear()
                browse("")
            }
        }
        parent.addView(go, LinearLayout.LayoutParams(-1,dp(50)).apply { topMargin = dp(25) })
        setContentView(android.widget.ScrollView(this).apply { addView(parent) })
    }

    private data class Item(val name: String, val directory: Boolean)

    private fun browse(path: String) {
        if (destroyed) return
        Toast.makeText(this,"Loading network folder…",Toast.LENGTH_SHORT).show()
        io.execute {
            val outcome = runCatching {
                val active = SmbSession.current() ?: error("Not connected")
                SMBClient().use { client ->
                    client.connect(active.host).use { connection ->
                        connection.authenticate(AuthenticationContext(
                            active.username, active.secret, active.domain)).use { session ->
                            (session.connectShare(active.share) as DiskShare).use { disk ->
                                disk.list(path).asSequence()
                                    .filter { it.fileName != "." && it.fileName != ".." }
                                    .map { Item(it.fileName, it.fileAttributes and 0x10L != 0L) }
                                    .filter { it.directory || it.name.substringAfterLast('.', "").lowercase() in recognized }
                                    .sortedWith(compareByDescending<Item> { it.directory }
                                        .thenBy { it.name.lowercase() })
                                    .take(250).toList()
                            }
                        }
                    }
                }
            }
            runOnUiThread {
                if (destroyed || isFinishing) return@runOnUiThread
                val entries = outcome.getOrElse {
                    AlertDialog.Builder(this).setTitle("Network unavailable")
                        .setMessage("Could not open the SMB share. Check the server, Wi-Fi, login or permissions.")
                        .setPositiveButton("OK",null).show()
                    return@runOnUiThread
                }
                val parent = path.substringBeforeLast('\\', "")
                val prefixes = if (path.isBlank()) emptyList() else listOf("‹  Parent folder")
                val names = prefixes + entries.map { (if (it.directory) "📁  " else "▶  ") + it.name }
                AlertDialog.Builder(this)
                    .setTitle(if (path.isBlank()) "SMB · Network videos" else path)
                    .setItems(names.toTypedArray()) { _, which ->
                        if (which < prefixes.size) browse(parent)
                        else {
                            val item = entries[which-prefixes.size]
                            val selected = if (path.isBlank()) item.name else "$path\\${item.name}"
                            if (item.directory) browse(selected)
                            else {
                                PlaybackQueue.replace(emptyList())
                                PlaybackQueue.persist(this)
                                startActivity(Intent(this, PlayerActivity::class.java).apply {
                                    putExtra(PlayerActivity.EXTRA_URI, SmbSession.mediaUri(selected).toString())
                                    putExtra(PlayerActivity.EXTRA_TITLE, item.name)
                                })
                            }
                        }
                    }
                    .setNegativeButton("Disconnect") { _, _ ->
                        SmbSession.disconnect()
                        finish()
                    }
                    .setPositiveButton("Close", null).show()
            }
        }
    }

    override fun onDestroy() {
        destroyed = true
        io.shutdownNow()
        super.onDestroy()
    }
}
