package com.xnota.core.util

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The app lock's PIN hashing (the fork). The PIN itself is never stored anywhere — only the
 * "salt$hash" pair [hash] produces: 8 random salt bytes as hex, a '$', then the lowercase hex
 * SHA-256 of (salt bytes + PIN bytes). A fresh random salt per [hash] call means the same PIN
 * hashes differently every time, so a stolen settings file cannot be searched against a list
 * of likely PINs without re-hashing each candidate per salt.
 *
 * Pure Kotlin (no Android imports), so it is unit-tested on the JVM and usable from both the
 * gate UI and the preferences pane.
 */
object PinHash {

    /** Salt length in bytes; 16 hex characters once encoded. */
    const val SALT_BYTES = 8

    private val random = SecureRandom()

    /** The lowercase hex SHA-256 of [bytes]. */
    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(s: String): ByteArray {
        val out = ByteArray(s.length / 2)
        for (i in out.indices) out[i] = ((Character.digit(s[2 * i], 16) shl 4) or Character.digit(s[2 * i + 1], 16)).toByte()
        return out
    }

    private fun isHex(s: String): Boolean = s.isNotEmpty() && s.all { it.isDigit() || it in 'a'..'f' }

    /** Whether [stored] is a well-formed "salt$hash" value (16 hex chars, '$', 64 hex chars). */
    fun isValidStored(stored: String): Boolean {
        val i = stored.indexOf('$')
        if (i != 2 * SALT_BYTES) return false
        val salt = stored.substring(0, i)
        val hash = stored.substring(i + 1)
        return isHex(salt) && hash.length == 64 && isHex(hash)
    }

    /** The stored form of [pin]: a fresh "salt$hash". */
    fun hash(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        return hex(salt) + "$" + sha256(salt + pin.toByteArray(Charsets.UTF_8))
    }

    /**
     * True iff [pin] matches [stored]. A malformed or empty [stored] matches nothing, so a
     * corrupted settings file degrades to "no lock" rather than "any PIN works" — the salt is
     * parsed more leniently than [isValidStored] (any even hex length), so a value written by
     * a future salt size still verifies. The final digest compare is constant-time
     * ([MessageDigest.isEqual]).
     */
    fun verify(pin: String, stored: String): Boolean {
        val i = stored.indexOf('$')
        if (i <= 0 || i % 2 != 0) return false
        val salt = stored.substring(0, i)
        val want = stored.substring(i + 1)
        if (!isHex(salt) || want.length != 64 || !isHex(want)) return false
        val got = sha256(unhex(salt) + pin.toByteArray(Charsets.UTF_8))
        return MessageDigest.isEqual(got.toByteArray(Charsets.ISO_8859_1), want.toByteArray(Charsets.ISO_8859_1))
    }

    /**
     * The new stored value given the current [stored] and the [pin] the user typed: a null or
     * blank PIN clears the lock (empty string = none), anything else re-hashes under a fresh
     * salt. [stored] is accepted for call-site symmetry and never read — the old digest must
     * not influence the new one.
     */
    fun set(stored: String, pin: String?): String = if (pin.isNullOrBlank()) "" else hash(pin)
}
