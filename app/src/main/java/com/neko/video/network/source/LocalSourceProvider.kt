package com.neko.video.network.source

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.DocumentsContract
import com.neko.video.model.MediaFolder
import com.neko.video.model.MediaItemInfo
import com.neko.video.model.SourceType
import com.neko.video.model.SubtitleCandidate
import com.neko.video.util.FormatUtils
import com.neko.video.util.MediaIndex
import com.neko.video.util.commonAncestorDirectory
import com.neko.video.util.directoryNameOf
import com.neko.video.util.isStorageVolumeRoot
import com.neko.video.util.isUnderDirectory
import com.neko.video.util.parentDirectoryOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Outcome of an incremental scan: the merged library plus what the index should persist. */
data class LocalSyncResult(
    val videos: List<MediaItemInfo>,
    /** Rows whose stored copy is missing or outdated. */
    val upserts: List<MediaItemInfo>,
    /** MediaStore ids that no longer exist and must be dropped from the index. */
    val removedIds: List<String>,
)

class LocalSourceProvider(
    private val context: Context,
) : MediaSourceProvider {
    override val sourceType: SourceType = SourceType.LOCAL

    override suspend fun list(path: String): List<MediaItemInfo> = loadAllVideos()

    suspend fun loadAllVideos(): List<MediaItemInfo> =
        withContext(Dispatchers.IO) { queryRows(null, VIDEO_PROJECTION_ORDER) }

    /**
     * Looks at `MediaStore` only to find out what changed: one cheap `_id`/`date_modified` pass,
     * then full rows for the new or modified ids only. SAF imports are carried over untouched —
     * they are not in `MediaStore`, so comparing them would delete them on every launch.
     *
     * @param knownVideos what the persisted index currently holds.
     * @param force re-read every row, e.g. when the user pulls to refresh.
     */
    suspend fun sync(knownVideos: List<MediaItemInfo>, force: Boolean = false): LocalSyncResult =
        withContext(Dispatchers.IO) {
            val currentStamps = queryStamps()
            val knownStamps = knownVideos
                .filter { MediaIndex.isMediaStoreEntry(it.id) }
                .associate { it.id to it.modifiedTime }
            val changed = MediaIndex.changedIds(
                current = currentStamps,
                known = knownStamps,
                force = force || knownStamps.isEmpty(),
            )
            val removed = MediaIndex.removedIds(currentStamps.keys, knownStamps.keys)
            val upserts = if (changed.isEmpty()) emptyList() else queryRows(changed, null)
            val untouched = knownVideos.filterNot { it.id in changed || it.id in removed }
            LocalSyncResult(
                videos = untouched + upserts,
                upserts = upserts,
                removedIds = removed.toList(),
            )
        }

    /** `id → modified time` for every video MediaStore knows about. Two columns only, so it is cheap. */
    private fun queryStamps(): Map<String, Long> {
        val stamps = LinkedHashMap<String, Long>()
        context.contentResolver.query(
            videoCollection(),
            arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_MODIFIED),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                stamps["${MediaIndex.MEDIA_STORE_ID_PREFIX}$id"] = cursor.getLong(1) * 1000L
            }
        }
        return stamps
    }

    /** Full rows for [ids] (`local_<mediaId>`), queried in chunks to stay under SQLite's limit. */
    private fun queryRows(ids: Set<String>?, sortOrder: String?): List<MediaItemInfo> {
        val numericIds = ids?.mapNotNull {
            it.removePrefix(MediaIndex.MEDIA_STORE_ID_PREFIX).toLongOrNull()
        }
        if (numericIds != null && numericIds.isEmpty()) return emptyList()
        val subtitleCache = SubtitleDirCache()
        val result = ArrayList<MediaItemInfo>(numericIds?.size ?: 512)
        val selection = numericIds?.chunked(ID_CHUNK_SIZE)?.map { chunk ->
            "${MediaStore.Video.Media._ID} IN (${chunk.joinToString(",")})"
        }
        // A null selection list means "everything", which is the first-launch / forced rescan path.
        (selection ?: listOf(null)).forEach { chunkSelection ->
            context.contentResolver.query(
                videoCollection(),
                VIDEO_PROJECTION,
                chunkSelection,
                null,
                sortOrder,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    result += readRow(cursor, subtitleCache)
                }
            }
        }
        return result
    }

    /**
     * Column order matches [VIDEO_PROJECTION]; reading by index keeps the loop free of repeated
     * `getColumnIndexOrThrow` lookups, which dominated the scan on large libraries.
     */
    private fun readRow(cursor: Cursor, subtitleCache: SubtitleDirCache): MediaItemInfo {
        val mediaId = cursor.getLong(COLUMN_ID)
        val path = cursor.getString(COLUMN_PATH)
        val name = cursor.getString(COLUMN_NAME) ?: "video_$mediaId"
        val contentUri = ContentUris.withAppendedId(videoCollection(), mediaId)
        return MediaItemInfo(
            id = "${MediaIndex.MEDIA_STORE_ID_PREFIX}$mediaId",
            name = name,
            uri = contentUri.toString(),
            path = path,
            sourceType = SourceType.LOCAL,
            size = cursor.getLong(COLUMN_SIZE),
            modifiedTime = cursor.getLong(COLUMN_MODIFIED) * 1000L,
            mimeType = cursor.getString(COLUMN_MIME),
            duration = cursor.getLong(COLUMN_DURATION),
            width = cursor.getInt(COLUMN_WIDTH),
            height = cursor.getInt(COLUMN_HEIGHT),
            thumbnailUri = contentUri.toString(),
            subtitleCandidates = subtitleCache.candidatesFor(path),
        )
    }

    private fun videoCollection(): Uri =
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    /**
     * Builds the browsable folder tree for [videos].
     *
     * Every folder that directly holds videos becomes a node, together with the ancestors needed
     * to reach it, so nested chapters are entered from their course folder instead of being
     * flattened into the same list. The shared root of the tree is shown as one top-level entry;
     * storage volume roots (`/`, `/storage/emulated/0`, …) never are.
     */
    suspend fun loadFolders(videos: List<MediaItemInfo>): List<MediaFolder> =
        withContext(Dispatchers.Default) {
            val videosByDir = LinkedHashMap<String, MutableList<MediaItemInfo>>()
            videos.forEach { video ->
                val dir = video.path?.let(::parentDirectoryOf)?.takeIf { it.isNotBlank() }
                    ?: return@forEach
                videosByDir.getOrPut(dir) { mutableListOf() }.add(video)
            }
            if (videosByDir.isEmpty()) return@withContext emptyList()

            val root = commonAncestorDirectory(videosByDir.keys)
            val rootIsBrowsable = root.isNotBlank() && !isStorageVolumeRoot(root)

            // Node set: every video folder plus the ancestors between it and the root.
            val nodeDirs = LinkedHashSet<String>()
            videosByDir.keys.forEach { dir ->
                var current: String? = dir
                while (current != null && current.isNotBlank()) {
                    // A volume root stops the walk but never becomes a browsable entry.
                    if (current == root && !rootIsBrowsable) break
                    nodeDirs += current
                    if (current == root) break
                    current = parentDirectoryOf(current)
                }
            }

            // Collapse a single-child top chain (e.g. "Download/课程" when only one course exists),
            // so the first screen starts at a branch point instead of a pointless wrapper folder.
            var collapsed = true
            while (collapsed) {
                collapsed = false
                val tops = nodeDirs.filter { dir ->
                    parentDirectoryOf(dir)?.let { it in nodeDirs } != true
                }
                if (tops.size == 1) {
                    val only = tops.first()
                    val childCount = nodeDirs.count { parentDirectoryOf(it) == only }
                    val hasDirectVideos = videosByDir[only].orEmpty().isNotEmpty()
                    if (childCount == 1 && !hasDirectVideos) {
                        nodeDirs.remove(only)
                        collapsed = true
                    }
                }
            }

            nodeDirs.map { dir ->
                val direct = videosByDir[dir].orEmpty()
                val recursive = videos.filter { item ->
                    item.path?.let { isUnderDirectory(it, dir) } == true
                }
                MediaFolder(
                    id = dir,
                    name = directoryNameOf(dir),
                    path = dir,
                    videoCount = direct.size,
                    thumbnailUri = recursive.firstOrNull()?.thumbnailUri,
                    parentPath = parentDirectoryOf(dir)?.takeIf { it in nodeDirs },
                    totalVideoCount = recursive.size,
                    latestModifiedTime = recursive.maxOfOrNull { it.modifiedTime } ?: 0L,
                    totalSize = recursive.sumOf { it.size },
                    totalDuration = recursive.sumOf { it.duration },
                )
            }
        }

    suspend fun loadFromTreeUri(treeUri: Uri): List<MediaItemInfo> = withContext(Dispatchers.IO) {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val found = mutableListOf<MediaItemInfo>()
        walkTreeDocuments(
            treeUri = treeUri,
            parentDocId = rootDocId,
            parentPath = documentDisplayPath(treeUri, rootDocId),
            out = found,
        )
        found.sortedBy { it.name.lowercase() }
    }

    private fun walkTreeDocuments(
        treeUri: Uri,
        parentDocId: String,
        parentPath: String?,
        out: MutableList<MediaItemInfo>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedCol =
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val docId = cursor.getString(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val mime = cursor.getString(mimeCol)
                val childPath = if (parentPath.isNullOrBlank()) name else "$parentPath/$name"
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walkTreeDocuments(treeUri, docId, childPath, out)
                    continue
                }
                if (!FormatUtils.isVideoFile(name, mime)) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                out += MediaItemInfo(
                    id = "saf_${uri.hashCode()}",
                    name = name,
                    uri = uri.toString(),
                    // Synthetic display path for folder grouping (not a real filesystem path).
                    path = childPath,
                    sourceType = SourceType.LOCAL,
                    size = cursor.getLong(sizeCol),
                    modifiedTime = cursor.getLong(modifiedCol),
                    mimeType = mime,
                    thumbnailUri = uri.toString(),
                )
            }
        }
    }

    private fun documentDisplayPath(treeUri: Uri, docId: String): String? {
        // Prefer a stable folder label for grouping; SAF paths are not filesystem paths.
        val last = docId.substringAfterLast(':').substringAfterLast('/')
        return last.takeIf { it.isNotBlank() }
            ?: runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
    }

    /**
     * Lists each directory once and answers subtitle lookups from memory. Scanning used to call
     * `listFiles()` per video, which is what made the first launch slow on large libraries.
     */
    private class SubtitleDirCache {
        private val filesByDir = HashMap<String, List<File>>()

        fun candidatesFor(videoPath: String?): List<SubtitleCandidate> {
            if (videoPath.isNullOrBlank()) return emptyList()
            val videoFile = File(videoPath)
            val parent = videoFile.parentFile ?: return emptyList()
            val files = filesByDir.getOrPut(parent.absolutePath) {
                parent.listFiles()?.toList().orEmpty()
            }
            if (files.isEmpty()) return emptyList()
            val base = videoFile.nameWithoutExtension
            return files.asSequence()
                .filter { file ->
                    FormatUtils.isSubtitleFile(file.name) &&
                        file.name.startsWith(base, ignoreCase = true)
                }
                .map { file ->
                    SubtitleCandidate(
                        name = file.name,
                        uri = Uri.fromFile(file).toString(),
                    )
                }
                .toList()
        }
    }

    private companion object {
        const val ID_CHUNK_SIZE = 400

        /** Must stay in sync with the [COLUMN_*] indexes below. */
        val VIDEO_PROJECTION = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATA,
        )
        const val VIDEO_PROJECTION_ORDER = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        const val COLUMN_ID = 0
        const val COLUMN_NAME = 1
        const val COLUMN_SIZE = 2
        const val COLUMN_DURATION = 3
        const val COLUMN_WIDTH = 4
        const val COLUMN_HEIGHT = 5
        const val COLUMN_MODIFIED = 6
        const val COLUMN_MIME = 7
        const val COLUMN_PATH = 8
    }
}
