package com.neko.video

import com.neko.video.util.MediaIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIndexTest {
    @Test
    fun detectsNewAndMovedEntries() {
        val current = mapOf("local_1" to 100L, "local_2" to 200L, "local_3" to 300L)
        val known = mapOf("local_1" to 100L, "local_2" to 150L)

        assertEquals(setOf("local_2", "local_3"), MediaIndex.changedIds(current, known))
    }

    @Test
    fun unchangedEntriesAreSkipped() {
        val stamps = mapOf("local_1" to 100L, "local_2" to 200L)

        assertTrue(MediaIndex.changedIds(stamps, stamps).isEmpty())
    }

    @Test
    fun emptyIndexFallsBackToFullScan() {
        val current = mapOf("local_1" to 100L, "local_2" to 200L)

        assertEquals(current.keys, MediaIndex.changedIds(current, emptyMap()))
    }

    @Test
    fun forceRescansEverything() {
        val stamps = mapOf("local_1" to 100L, "local_2" to 200L)

        assertEquals(stamps.keys, MediaIndex.changedIds(stamps, stamps, force = true))
    }

    @Test
    fun removedOnlyCoversMediaStoreEntries() {
        val current = setOf("local_1", "local_2")
        val known = setOf("local_1", "local_2", "local_99", "saf_1234", "saf_5678")

        // SAF imports never show up in MediaStore, so they must survive the comparison.
        assertEquals(setOf("local_99"), MediaIndex.removedIds(current, known))
    }

    @Test
    fun recognisesIdPrefixes() {
        assertTrue(MediaIndex.isMediaStoreEntry("local_42"))
        assertFalse(MediaIndex.isMediaStoreEntry("saf_42"))
        assertTrue(MediaIndex.isSafEntry("saf_42"))
    }
}
