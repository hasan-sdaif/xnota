package com.xnota.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteCryptoTest {

    // PBKDF2 at the full 120k iterations runs ~4 times across this class; well under a second each.
    private val secret = "ذهب المستند السري".toByteArray(Charsets.UTF_8)

    @Test fun encryptThenDecryptRoundTrips() {
        val blob = NoteCrypto.encrypt(secret, "open sesame")
        assertTrue(NoteCrypto.isEncrypted(blob))
        val back = NoteCrypto.decrypt(blob, "open sesame").getOrThrow()
        assertArrayEquals(secret, back)
    }

    @Test fun blobCarriesTheFullHeader() {
        val blob = NoteCrypto.encrypt(secret, "pass")
        assertEquals("XN1", String(blob, 0, 3, Charsets.US_ASCII))
        assertEquals(NoteCrypto.HEADER_BYTES + secret.size + 16, blob.size) // + GCM tag
    }

    @Test fun aFreshEncryptionIsNeverTwiceTheSameBytes() {
        val a = NoteCrypto.encrypt(secret, "pass")
        val b = NoteCrypto.encrypt(secret, "pass")
        assertFalse(a.contentEquals(b)) // fresh salt + iv every time
    }

    @Test fun wrongPassphraseFailsRatherThanReturningGarbage() {
        val blob = NoteCrypto.encrypt(secret, "the right one")
        assertTrue(NoteCrypto.decrypt(blob, "the wrong one").isFailure)
    }

    @Test fun aTamperedByteFails() {
        val blob = NoteCrypto.encrypt(secret, "pass")
        val tampered = blob.copyOf().also { it[it.size - 3] = (it[it.size - 3].toInt() xor 0x41).toByte() }
        assertTrue(NoteCrypto.decrypt(tampered, "pass").isFailure)
    }

    @Test fun truncatedInputFails() {
        val blob = NoteCrypto.encrypt(secret, "pass")
        assertTrue(NoteCrypto.decrypt(blob.copyOfRange(0, blob.size - 20), "pass").isFailure)
        assertTrue(NoteCrypto.decrypt(blob.copyOfRange(0, 20), "pass").isFailure) // less than a header
    }

    @Test fun plainBytesAreNotSeenAsEncrypted() {
        assertFalse(NoteCrypto.isEncrypted(secret))
        assertFalse(NoteCrypto.isEncrypted(ByteArray(0)))
        assertTrue(NoteCrypto.decrypt(secret, "pass").isFailure)
    }

    @Test fun theSamePassphraseDerivesTheSameKey() {
        val salt = ByteArray(16) { (it * 7).toByte() }
        val a = NoteCrypto.deriveKey("phrase".toCharArray(), salt, 1000)
        val b = NoteCrypto.deriveKey("phrase".toCharArray(), salt, 1000)
        val c = NoteCrypto.deriveKey("other".toCharArray(), salt, 1000)
        assertTrue(a == b) // SecretKeySpec equality is content equality
        assertFalse(a == c)
    }
}
