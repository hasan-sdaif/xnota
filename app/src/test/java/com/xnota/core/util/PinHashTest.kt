package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHashTest {

    @Test fun aStoredPinRoundTrips() {
        val stored = PinHash.hash("1234")
        assertTrue(PinHash.verify("1234", stored))
    }

    @Test fun theSaltIsRandomPerHash() {
        val a = PinHash.hash("9999")
        val b = PinHash.hash("9999")
        assertNotEquals(a, b) // same PIN, different salts…
        assertTrue(PinHash.verify("9999", a)) // …but both verify
        assertTrue(PinHash.verify("9999", b))
    }

    @Test fun aWrongPinDoesNotVerify() {
        val stored = PinHash.hash("1234")
        assertFalse(PinHash.verify("1235", stored))
        assertFalse(PinHash.verify("123", stored))
        assertFalse(PinHash.verify("12345", stored))
        assertFalse(PinHash.verify("", stored))
    }

    @Test fun theStoredFormIsSaltHexDollarSha256Hex() {
        val stored = PinHash.hash("4242")
        val i = stored.indexOf('$')
        assertEquals(2 * PinHash.SALT_BYTES, i)
        assertTrue(PinHash.isValidStored(stored))
        assertEquals(64, stored.length - i - 1)
        assertTrue(stored.all { it.isDigit() || it in 'a'..'f' || it == '$' })
    }

    @Test fun malformedStoredValuesMatchNothing() {
        for (bad in listOf("", "1234", "abcd", "1234\$notahex", "zz\$" + "a".repeat(64),
                "12345678\$" + "g".repeat(64), "\$" + "a".repeat(64), "abcd\$1234")) {
            assertFalse("stored='$bad' should not verify", PinHash.verify("1234", bad))
        }
        assertFalse(PinHash.isValidStored(""))
        assertFalse(PinHash.isValidStored("abcd\$" + "a".repeat(64))) // salt too short
    }

    @Test fun setWithAPinHashesAndVerifies() {
        val stored = PinHash.set("", "5678")
        assertTrue(PinHash.isValidStored(stored))
        assertTrue(PinHash.verify("5678", stored))
    }

    @Test fun setWithNullOrBlankClearsToEmpty() {
        assertEquals("", PinHash.set("whatever", null))
        assertEquals("", PinHash.set("whatever", ""))
        assertEquals("", PinHash.set("whatever", "   "))
        assertEquals("", PinHash.set("", null))
    }

    @Test fun setIgnoresTheOldStoredValue() {
        val old = PinHash.hash("1111")
        val next = PinHash.set(old, "2222")
        assertTrue(PinHash.verify("2222", next))
        assertFalse(PinHash.verify("1111", next)) // the old PIN is gone, not merged
    }

    @Test fun longAndUnicodePinsVerify() {
        for (pin in listOf("000000", "99", "٩٧٣١", "passe-partout")) {
            val stored = PinHash.hash(pin)
            assertTrue(PinHash.verify(pin, stored))
            assertFalse(PinHash.verify(pin + "x", stored))
        }
    }
}
