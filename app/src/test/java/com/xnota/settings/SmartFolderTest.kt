package com.xnota.settings

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartFolderTest {

    @Test fun matchesOnQueryCaseInsensitively() {
        val f = SmartFolder("Uni", query = "lecture")
        assertTrue(SmartFolders.matches(f, "Math Lecture 3", emptyList()))
        assertFalse(SmartFolders.matches(f, "math exercise", emptyList()))
    }

    @Test fun blankQueryMatchesEveryName() {
        val f = SmartFolder("All")
        assertTrue(SmartFolders.matches(f, "anything", emptyList()))
    }

    @Test fun tagMustBeCarriedWhenSet() {
        val f = SmartFolder("Work", query = "report", tag = "work")
        assertTrue(SmartFolders.matches(f, "Annual Report", listOf("Work", "draft")))
        assertFalse(SmartFolders.matches(f, "Annual Report", listOf("home")))
        assertFalse(SmartFolders.matches(f, "Annual Report", emptyList()))
    }

    @Test fun blankTagMeansAnyTag() {
        val f = SmartFolder("Tagless", tag = "  ")
        assertTrue(SmartFolders.matches(f, "any note", emptyList()))
    }

    @Test fun jsonRoundTripKeepsNameQueryAndTag() {
        val list = listOf(SmartFolder("Ideas", "idea", null), SmartFolder("Work", "report", "work"))
        val back = SmartFolders.of(SmartFolders.toJson(list))
        assertEquals(list, back)
    }

    @Test fun blankNameEntryDropsOut() {
        val a = JSONArray().put(JSONObject().put("name", "  ").put("query", "x"))
        assertTrue(SmartFolders.of(a).isEmpty())
    }

    @Test fun saveReplacesSameNameAndCapsTheList() {
        val start = (1..SmartFolders.MAX_FOLDERS).map { SmartFolder("f$it") }
        val saved = SmartFolders.save(start, SmartFolder("new one"))
        assertEquals(SmartFolders.MAX_FOLDERS, saved.size)
        assertEquals("new one", saved.last().name)
        assertEquals("f2", saved.first().name) // the oldest fell off

        val replaced = SmartFolders.save(start, SmartFolder("f1", query = "q"))
        assertEquals(start.size, replaced.size)
        assertEquals("q", replaced.first().query)
    }

    @Test fun saveRejectsABlankNameAndDeleteRemovesByName() {
        val list = listOf(SmartFolder("A"), SmartFolder("B"))
        assertEquals(list, SmartFolders.save(list, SmartFolder("  ")))
        assertEquals(listOf<SmartFolder>(SmartFolder("B")), SmartFolders.delete(list, "A"))
    }

    @Test fun tagOptionsAreDistinctAndSorted() {
        val options = SmartFolders.tagOptions(listOf(listOf("zeta", "Alpha"), listOf("alpha", "beta")))
        assertEquals(listOf("Alpha", "beta", "zeta"), options)
    }
}
