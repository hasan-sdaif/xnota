package com.xnota.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * File-level encryption for XNota's own containers (encrypted backups, export bundles):
 * AES-256-GCM with a passphrase-derived key. A blob is self-describing so a restore can
 * tell it apart from a plain file by its header alone —
 *
 * ```
 * "XN1" (3) | salt (16) | iv (12) | ciphertext+tag
 * ```
 *
 * The salt and IV are fresh for every encryption, so the same plain bytes encrypt to a
 * different blob each time and no two files share a key stream. The passphrase never
 * travels with the data; GCM's tag is what turns a wrong passphrase or a tampered byte
 * into a hard failure rather than a silent read of garbage.
 */
object NoteCrypto {

    /** The header every encrypted blob starts with. */
    const val MAGIC = "XN1"

    /** Salt bytes carried in the header; a per-encryption PBKDF2 salt. */
    const val SALT_BYTES = 16

    /** GCM's recommended nonce width; unique per encryption by construction. */
    const val IV_BYTES = 12

    /** AES-256. */
    const val KEY_BITS = 256

    /** PBKDF2 work factor — about a quarter second of derivation on a phone. */
    const val ITERATIONS = 120_000

    /** Bytes before the ciphertext: magic + salt + iv. */
    val HEADER_BYTES = MAGIC.length + SALT_BYTES + IV_BYTES

    private val rng = SecureRandom()

    /**
     * Derives the AES key from [passphrase] and [salt] with PBKDF2-HMAC-SHA256. Pure with
     * respect to the app: same inputs, same key, on any JVM.
     */
    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** [bytes] fresh from the RNG. */
    private fun fresh(n: Int): ByteArray = ByteArray(n).also(rng::nextBytes)

    /**
     * Encrypts [plain] for [passphrase]: a fresh salt, a fresh IV, and the full header above.
     * Caller-owned keys (a key kept for a session) re-derive per call; a single-phrase
     * encrypt-and-drop use is the intended shape.
     */
    fun encrypt(plain: ByteArray, passphrase: String): ByteArray {
        val salt = fresh(SALT_BYTES)
        val iv = fresh(IV_BYTES)
        val key = deriveKey(passphrase.toCharArray(), salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val out = cipher.doFinal(plain)
        val blob = ByteArray(HEADER_BYTES + out.size)
        System.arraycopy(MAGIC.toByteArray(Charsets.US_ASCII), 0, blob, 0, MAGIC.length)
        System.arraycopy(salt, 0, blob, MAGIC.length, SALT_BYTES)
        System.arraycopy(iv, 0, blob, MAGIC.length + SALT_BYTES, IV_BYTES)
        System.arraycopy(out, 0, blob, HEADER_BYTES, out.size)
        return blob
    }

    /** True when [blob] carries this scheme's header — the plain/encrypted dispatch a restore uses. */
    fun isEncrypted(blob: ByteArray): Boolean =
        blob.size > HEADER_BYTES && String(blob, 0, MAGIC.length, Charsets.US_ASCII) == MAGIC

    /**
     * Decrypts a blob made by [encrypt] with [passphrase]. Failure — a wrong passphrase, a
     * truncated blob, a flipped byte (AEADBadTagException) — is a [Result.failure] rather
     * than an exception through the caller, because every caller only wants to say "could
     * not decrypt" and move on.
     */
    fun decrypt(blob: ByteArray, passphrase: String): Result<ByteArray> = runCatching {
        if (!isEncrypted(blob)) throw IllegalArgumentException("not an encrypted XNota blob")
        val salt = blob.copyOfRange(MAGIC.length, MAGIC.length + SALT_BYTES)
        val iv = blob.copyOfRange(MAGIC.length + SALT_BYTES, HEADER_BYTES)
        val key = deriveKey(passphrase.toCharArray(), salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.doFinal(blob, HEADER_BYTES, blob.size - HEADER_BYTES)
    }
}
