package com.magen.player.utils.sort

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalOrderComparatorTest {
    @Test
    fun numericFilenamePartsSortByNumericValue() {
        val names = listOf("1.mp4", "10.mp4", "100.mp4", "2.mp4", "20.mp4", "3.mp4")
        val sorted = names.sortedWith(SortUtils.NaturalOrderComparator.DEFAULT)

        assertEquals(
            listOf("1.mp4", "2.mp4", "3.mp4", "10.mp4", "20.mp4", "100.mp4"),
            sorted,
        )
    }
}
