package com.neko.video.util

/**
 * Incremental bookkeeping for the persisted local library index.
 *
 * Pure Kotlin so the "what changed since last launch" rules are unit tested without Android.
 * Entries scanned from `MediaStore` are identified as [MEDIA_STORE_ID_PREFIX] + media id;
 * videos imported through the SAF picker use a different prefix and are never compared against
 * `MediaStore`, otherwise every launch would treat them as deleted.
 */
object MediaIndex {
    const val MEDIA_STORE_ID_PREFIX = "local_"
    private const val SAF_ID_PREFIX = "saf_"

    fun isMediaStoreEntry(id: String): Boolean = id.startsWith(MEDIA_STORE_ID_PREFIX)

    fun isSafEntry(id: String): Boolean = id.startsWith(SAF_ID_PREFIX)

    /**
     * Ids whose full row has to be re-queried: everything when [force] is set (or when nothing
     * is indexed yet), otherwise only new entries and entries whose modified time moved.
     *
     * @param current id → modified time as reported by `MediaStore` right now.
     * @param known id → modified time as stored in the index.
     */
    fun changedIds(
        current: Map<String, Long>,
        known: Map<String, Long>,
        force: Boolean = false,
    ): Set<String> {
        if (force || known.isEmpty()) return current.keys
        val changed = LinkedHashSet<String>()
        current.forEach { (id, stamp) ->
            val indexed = known[id]
            if (indexed == null || indexed != stamp) changed += id
        }
        return changed
    }

    /**
     * MediaStore entries that disappeared since the last scan. SAF imports are excluded on
     * purpose: they never appear in `MediaStore`, so they would always look deleted.
     */
    fun removedIds(current: Set<String>, known: Set<String>): Set<String> {
        val gone = LinkedHashSet<String>()
        known.forEach { id ->
            if (isMediaStoreEntry(id) && id !in current) gone += id
        }
        return gone
    }
}
