package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupDigestTest {

    private val files = listOf(
        "Note 1.xnote" to "one".toByteArray(),
        "Notebook/Note 2.xnote" to "two".toByteArray(),
    )

    @Test fun aFreshManifestVerifiesClean() {
        val m = BackupDigest.manifest(files)
        val report = BackupDigest.verify(files, m)
        assertTrue(report.ok)
        assertEquals(2, report.verified)
        assertEquals(0, report.checked - report.verified)
    }

    @Test fun aCorruptedFileIsNamed() {
        val m = BackupDigest.manifest(files)
        val tampered = listOf(files[0], "Notebook/Note 2.xnote" to "TWO!".toByteArray())
        val report = BackupDigest.verify(tampered, m)
        assertFalse(report.ok)
        assertEquals(listOf("Notebook/Note 2.xnote"), report.mismatched)
        assertEquals(1, report.verified)
    }

    @Test fun aMissingFileIsNamed() {
        val m = BackupDigest.manifest(files)
        val report = BackupDigest.verify(listOf(files[0]), m)
        assertFalse(report.ok)
        assertEquals(listOf("Notebook/Note 2.xnote"), report.missing)
        assertEquals(1, report.verified)
    }

    @Test fun anExtraFileIsNamed() {
        val m = BackupDigest.manifest(files)
        val withExtra = files + ("Note 3.xnote" to "three".toByteArray())
        val report = BackupDigest.verify(withExtra, m)
        assertFalse(report.ok)
        assertEquals(listOf("Note 3.xnote"), report.extra)
        assertEquals(2, report.verified)
    }

    @Test fun theDigestIsStableSha256Hex() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            BackupDigest.digest(ByteArray(0)),
        ) // sha256("") — the empty-input anchor
        assertEquals(64, BackupDigest.digest("x".toByteArray()).length)
        assertTrue(BackupDigest.digest("x".toByteArray()).all { it.isDigit() || it in 'a'..'f' })
    }

    @Test fun theSameBytesGiveTheSameDigest() {
        assertEquals(BackupDigest.digest("same".toByteArray()), BackupDigest.digest("same".toByteArray()))
        assertTrue(BackupDigest.digest("same".toByteArray()) != BackupDigest.digest("different".toByteArray()))
    }

    @Test fun serializeThenDeserializeRoundTrips() {
        val m = BackupDigest.manifest(files)
        val back = BackupDigest.deserialize(BackupDigest.serialize(m))
        assertEquals(m, back)
    }

    @Test fun deserializeSkipsGarbageLines() {
        val good = BackupDigest.manifest(files)
        val text = "not a line\n" + BackupDigest.serialize(good) + "\nshort one\n\n"
        assertEquals(good, BackupDigest.deserialize(text))
        assertTrue(BackupDigest.deserialize("all garbage").isEmpty())
    }
}
