package org.telegram.tgnet

import org.junit.Assert
import org.junit.Test
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Arrays

class TlsHelloHandshakeTest {
    @Test
    fun clientHelloIsStructurallyValid() {
        val hello: ByteArray = ConnectionsManager.nativeTestGenerateClientHello(DOMAIN)

        Assert.assertTrue("empty hello", hello.size > 5)
        Assert.assertEquals("record type", 0x16, (hello[0].toInt() and 0xff).toLong())
        Assert.assertEquals("record version major", 0x03, (hello[1].toInt() and 0xff).toLong())
        Assert.assertEquals("record version minor", 0x01, (hello[2].toInt() and 0xff).toLong())

        val recordLength = u16(hello, 3)
        Assert.assertEquals(
            "record length mismatch",
            (hello.size - 5).toLong(),
            recordLength.toLong()
        )

        Assert.assertEquals("handshake type", 0x01, (hello[5].toInt() and 0xff).toLong())
        val handshakeLength =
            (hello[6].toInt() and 0xff) shl 16 or ((hello[7].toInt() and 0xff) shl 8) or (hello[8].toInt() and 0xff)
        Assert.assertEquals(
            "handshake length mismatch",
            (hello.size - 9).toLong(),
            handshakeLength.toLong()
        )

        Assert.assertEquals("session id length", 32, (hello[43].toInt() and 0xff).toLong())
    }

    @Test
    @Throws(IOException::class)
    fun googleAcceptsGeneratedClientHello() {
        val hello: ByteArray = ConnectionsManager.nativeTestGenerateClientHello(DOMAIN)
        val sessionId = clientSessionId(hello)
        val offeredCiphers = clientCipherSuites(hello)

        Socket().use { socket ->
            socket.connect(InetSocketAddress(DOMAIN, PORT), TIMEOUT_MS)
            socket.setSoTimeout(TIMEOUT_MS)

            val out = socket.getOutputStream()
            out.write(hello)
            out.flush()

            val `in` = socket.getInputStream()
            val header = readFully(`in`, 5)
            val recordType = header[0].toInt() and 0xff
            val payload = readFully(`in`, u16(header, 3))

            if (recordType == 0x15) {
                Assert.fail("server rejected the hello with alert: " + describeAlert(payload))
            }
            Assert.assertEquals(
                "expected a handshake record, got type " + recordType,
                0x16,
                recordType.toLong()
            )
            Assert.assertEquals(
                "expected ServerHello",
                0x02,
                (payload[0].toInt() and 0xff).toLong()
            )

            val serverRandom = Arrays.copyOfRange(payload, 6, 38)
            if (serverRandom.contentEquals(HRR_RANDOM)) {
                Assert.fail(
                    "server answered with HelloRetryRequest — key_share groups in the template "
                            + "do not match the advertised supported_groups"
                )
            }

            var pos = 38
            val sessionIdLength = payload[pos++].toInt() and 0xff
            val echoedSessionId = Arrays.copyOfRange(payload, pos, pos + sessionIdLength)
            pos += sessionIdLength

            Assert.assertArrayEquals(
                "server did not echo legacy_session_id",
                sessionId,
                echoedSessionId
            )

            val chosenCipher = u16(payload, pos)
            Assert.assertTrue(
                "server picked a cipher we never offered: 0x" + Integer.toHexString(chosenCipher),
                offeredCiphers.contains(chosenCipher)
            )
        }
    }

    companion object {
        private const val DOMAIN = "www.google.com"
        private const val PORT = 443
        private const val TIMEOUT_MS = 15000

        // TLS 1.3 HelloRetryRequest sentinel (RFC 8446, 4.1.3).
        private val HRR_RANDOM = hex(
            "CF21AD74E59A6111BE1D8C021E65B891C2A211167ABB8C5E079E09E2C8A8339C"
        )

        private fun clientSessionId(hello: ByteArray): ByteArray {
            return Arrays.copyOfRange(hello, 44, 44 + (hello[43].toInt() and 0xff))
        }

        private fun clientCipherSuites(hello: ByteArray): MutableSet<Int?> {
            var pos = 44 + (hello[43].toInt() and 0xff)
            val length = u16(hello, pos)
            pos += 2
            val result: MutableSet<Int?> = HashSet<Int?>()
            var i = 0
            while (i < length) {
                result.add(u16(hello, pos + i))
                i += 2
            }
            return result
        }

        @Throws(IOException::class)
        private fun readFully(`in`: InputStream, length: Int): ByteArray {
            val buffer = ByteArray(length)
            DataInputStream(`in`).readFully(buffer)
            return buffer
        }

        private fun u16(data: ByteArray, offset: Int): Int {
            return (data[offset].toInt() and 0xff) shl 8 or (data[offset + 1].toInt() and 0xff)
        }

        private fun describeAlert(payload: ByteArray): String {
            val level = payload[0].toInt() and 0xff
            val description = payload[1].toInt() and 0xff
            val name: String?
            when (description) {
                40 -> name = "handshake_failure"
                47 -> name = "illegal_parameter"
                50 -> name = "decode_error"
                51 -> name = "decrypt_error"
                70 -> name = "protocol_version"
                80 -> name = "internal_error"
                112 -> name = "unrecognized_name"
                else -> name = "unknown"
            }
            return "level=" + level + " description=" + description + " (" + name + ")"
        }

        private fun hex(value: String): ByteArray {
            val result = ByteArray(value.length / 2)
            for (i in result.indices) {
                result[i] = value.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return result
        }
    }
}