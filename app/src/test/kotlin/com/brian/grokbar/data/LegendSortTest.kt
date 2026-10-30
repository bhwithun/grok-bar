package com.brian.grokbar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LegendSortTest {
    @Test
    fun usageComesFirstThenName() {
        val sorted = listOf(
            ProductShare(4, "Chat", 0f),
            ProductShare(2, "Grok Build", 22f),
            ProductShare(9, "Grok Bot", 0f),
            ProductShare(1, "API", 22f),
            ProductShare(6, "Voice", 5f)
        ).sortedForLegend()

        assertEquals(listOf("API", "Grok Build", "Voice", "Chat", "Grok Bot"), sorted.map { it.label })
    }
}
