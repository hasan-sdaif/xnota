package com.xnota.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolCatalogTest {

    @Test fun everyCategoryHoldsSymbolsAndHasALabel() {
        for (c in SymbolCatalog.CATEGORIES) {
            assertTrue("category ${c.label} is empty", c.symbols.isNotEmpty())
            assertTrue(c.label.startsWith("symbols_"))
        }
    }

    @Test fun allFlattensTheCategoriesInOrder() {
        assertEquals(SymbolCatalog.CATEGORIES.sumOf { it.symbols.size }, SymbolCatalog.ALL.size)
        assertEquals(SymbolCatalog.ARABIC_PUNCTUATION.first(), SymbolCatalog.ALL.first())
    }

    @Test fun theArabicPunctuationAnArabicReaderMissesIsThere() {
        for (s in listOf("،", "؛", "؟", "«", "»", "ـ", "۞", "۝", "؞", "٭")) {
            assertTrue("missing $s", s in SymbolCatalog.ARABIC_PUNCTUATION)
        }
    }

    @Test fun theCommonSymbolsAreThere() {
        for (s in listOf("✓", "★", "♥", "→", "✦", "…", "—", "©", "™")) {
            assertTrue("missing $s", s in SymbolCatalog.COMMON)
        }
    }

    @Test fun noSymbolDuplicatesInsideItsCategory() {
        for (c in SymbolCatalog.CATEGORIES) {
            assertEquals("category ${c.label} repeats a symbol", c.symbols.size, c.symbols.distinct().size)
        }
    }
}
