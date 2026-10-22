package com.atuy.note.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentSpreadTest {
    @Test
    fun twoPageGroupsHandleEvenAndOddPageCounts() {
        assertEquals(0, displayedGroupCount(0, true))
        assertEquals(1, displayedGroupCount(1, true))
        assertEquals(1, displayedGroupCount(2, true))
        assertEquals(2, displayedGroupCount(3, true))
        assertEquals(2, displayedGroupCount(4, true))
        assertEquals(3, displayedGroupCount(5, true))
        assertEquals(5, displayedGroupCount(5, false))
    }

    @Test
    fun groupIndexAndFirstPageStayConsistent() {
        // Pages 0 and 1 are shown together, then 2 and 3, etc.
        assertEquals(0, displayedGroupIndex(0, true))
        assertEquals(0, displayedGroupIndex(1, true))
        assertEquals(1, displayedGroupIndex(2, true))
        assertEquals(1, displayedGroupIndex(3, true))
        assertEquals(2, displayedGroupIndex(4, true))
        assertEquals(0, displayedGroupFirstPage(0, true))
        assertEquals(2, displayedGroupFirstPage(1, true))
        assertEquals(4, displayedGroupFirstPage(2, true))
    }

    @Test
    fun singlePageDisplayHasIdentityIndexMapping() {
        for (index in 0..20) {
            assertEquals(index, displayedGroupIndex(index, false))
            assertEquals(index, displayedGroupFirstPage(index, false))
        }
    }
}
