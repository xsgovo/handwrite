package com.xsgovo.handwrite.core.rendering

import org.junit.Assert.assertEquals
import org.junit.Test

class InkDocumentRendererTest {
    @Test
    fun stableIdsReusePreparedValuesUntilTheyLeaveTheDocument() {
        val cache = StableIdCache<Int, String>()
        var createCount = 0
        val create: (Int) -> String = { id ->
            createCount++
            "prepared-$id"
        }

        assertEquals(
            listOf("prepared-1", "prepared-2"),
            cache.update(listOf(1, 2), { it }, create),
        )
        assertEquals(
            listOf("prepared-2", "prepared-1", "prepared-3"),
            cache.update(listOf(2, 1, 3), { it }, create),
        )
        assertEquals(3, createCount)

        cache.update(listOf(2, 3), { it }, create)
        cache.update(listOf(1, 2, 3), { it }, create)

        assertEquals(4, createCount)
    }
}
