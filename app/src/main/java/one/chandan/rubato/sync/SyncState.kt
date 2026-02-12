package one.chandan.rubato.sync

import one.chandan.rubato.util.MetadataSyncLogEntry

data class SyncState(
    val active: Boolean,
    val stage: String?,
    val progressCurrent: Int,
    val progressTotal: Int,
    val coverArtCurrent: Int,
    val coverArtTotal: Int,
    val lyricsCurrent: Int,
    val lyricsTotal: Int,
    val startedAt: Long,
    val lastCompletedAt: Long,
    val storageBytes: Long,
    val storageUpdatedAt: Long,
    val logs: List<MetadataSyncLogEntry>,
    val offline: Boolean,
    val dataSaving: Boolean
)
