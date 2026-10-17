package com.orbitplayer.app

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import org.junit.Assert.*
import org.junit.Test
import java.util.EnumSet

/**
 * Runs only in the SMB integration CI job, with a temporary Samba share.
 * Proves authentication, enumeration and random-access reads on actual SMB2/3 protocol.
 */
class SmbIntegrationTest {
    @Test fun browseAndReadAtOffset() {
        if (System.getenv("ORBIT_TEST_SMB") != "1") return
        SMBClient().use { client ->
            client.connect("127.0.0.1").use { connection ->
                connection.authenticate(AuthenticationContext(
                    "orbitci", "orbitpass".toCharArray(), ""
                )).use { session ->
                    (session.connectShare("OrbitTest") as DiskShare).use { share ->
                        val names = share.list("").map { it.fileName }
                        assertTrue(names.contains("sample.mp4"))
                        share.openFile("sample.mp4", EnumSet.of(AccessMask.GENERIC_READ),
                            null, EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                            SMB2CreateDisposition.FILE_OPEN, null).use { file ->
                            val bytes = ByteArray(5)
                            val length = file.read(bytes, 3, 0, 5)
                            assertEquals(5, length)
                            assertEquals("34567", String(bytes, Charsets.US_ASCII))
                        }
                    }
                }
            }
        }
    }
}
