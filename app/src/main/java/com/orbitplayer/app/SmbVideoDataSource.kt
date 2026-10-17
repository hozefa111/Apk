package com.orbitplayer.app

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import java.io.IOException
import java.util.EnumSet

/** Credentials never appear in playback URIs, exported playlists, preferences or diagnostic logs. */
object SmbSession {
    class Account(val host: String, val share: String, val username: String,
                  val secret: CharArray, val domain: String)
    @Volatile private var selected: Account? = null
    @Synchronized fun connect(config: Account) {
        selected?.secret?.fill('\u0000')
        selected = config
    }
    fun current(): Account? = selected
    @Synchronized fun disconnect() {
        selected?.secret?.fill('\u0000')
        selected = null
    }
    fun mediaUri(path: String): Uri = Uri.Builder().scheme("orbit-smb")
        .authority("active").appendPath(path).build()
}

/** Random-access SMB2/3 reads used by Media3; seeks do not download the entire file. */
class SmbVideoDataSource : BaseDataSource(true) {
    private var requestedUri: Uri? = null
    private var offset = 0L
    private var remaining = 0L
    private var live = false
    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null
    private var handle: File? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val account = SmbSession.current() ?: throw IOException("SMB session expired. Reopen the share.")
        val uri = dataSpec.uri
        if (uri.scheme != "orbit-smb") throw IOException("Unsupported SMB address")
        val path = uri.path?.trimStart('/')?.replace('/', '\\').orEmpty()
        if (path.isBlank() || path.split('\\').any { it == "." || it == ".." })
            throw IOException("Invalid remote file path")
        try {
            val smb = SMBClient()
            client = smb
            val connected = smb.connect(account.host)
            connection = connected
            val authenticated = connected.authenticate(
                AuthenticationContext(account.username, account.secret, account.domain)
            )
            session = authenticated
            val disk = authenticated.connectShare(account.share) as DiskShare
            share = disk
            val opened = disk.openFile(path,
                EnumSet.of(AccessMask.GENERIC_READ), null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN, null)
            handle = opened
            val length = opened.getFileInformation(
                com.hierynomus.msfscc.fileinformation.FileStandardInformation::class.java
            ).getEndOfFile()
            if (dataSpec.position > length) throw IOException("Seek position exceeds file size")
            offset = dataSpec.position
            remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong())
                length - offset else minOf(dataSpec.length, length - offset)
            requestedUri = uri
            live = true
            transferStarted(dataSpec)
            return remaining
        } catch (error: Exception) {
            close()
            throw IOException("Could not open SMB video. Check server, network and login.", error)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining <= 0) return C.RESULT_END_OF_INPUT
        val source = handle ?: return C.RESULT_END_OF_INPUT
        val desired = minOf(length.toLong(), remaining, 65536L).toInt()
        val count = try { source.read(buffer, this.offset, offset, desired) }
                    catch (err: Exception) { throw IOException("SMB video stream interrupted", err) }
        if (count <= 0) return C.RESULT_END_OF_INPUT
        this.offset += count
        remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = requestedUri

    override fun close() {
        val wasOpen = live
        live = false
        runCatching { handle?.close() }
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        handle = null
        share = null
        session = null
        connection = null
        client = null
        requestedUri = null
        remaining = 0L
        if (wasOpen) transferEnded()
    }
}

/** Delegates local/HTTPS media to Android's normal source; uses random-access SMB only on demand. */
class OrbitDataSourceFactory(context: android.content.Context) : DataSource.Factory {
    private val standard = DefaultDataSource.Factory(context)
    override fun createDataSource(): DataSource = object : DataSource {
        private var delegate: DataSource? = null
        private val listeners = mutableListOf<TransferListener>()

        override fun addTransferListener(transferListener: TransferListener) {
            listeners.add(transferListener)
            delegate?.addTransferListener(transferListener)
        }
        override fun open(dataSpec: DataSpec): Long {
            val source = if (dataSpec.uri.scheme == "orbit-smb")
                SmbVideoDataSource() else standard.createDataSource()
            delegate = source
            listeners.forEach(source::addTransferListener)
            return source.open(dataSpec)
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

        override fun getUri(): Uri? = delegate?.uri
        override fun close() {
            delegate?.close()
            delegate = null
        }
    }
}
