package one.chandan.rubato.domain

import one.chandan.rubato.model.LibrarySearchEntry
import one.chandan.rubato.source.MediaIdResolver
import one.chandan.rubato.source.MediaSourceRegistry
import one.chandan.rubato.util.SearchIndexUtil

object LibrarySearchEntryMapper {
    @JvmStatic
    fun toArtist(entry: LibrarySearchEntry?, subsonicSourceId: String): MediaArtist? {
        if (entry == null) return null
        val name = entry.title?.trim().orEmpty()
        if (name.isEmpty()) return null
        val id = resolveId(entry, entry.itemId, name, subsonicSourceId)
        val cover = entry.coverArt?.let { MediaImage(resolveId(entry, it, it, subsonicSourceId)) }
        return MediaArtist(
            id = id,
            name = name,
            albumCount = null,
            coverArt = cover,
            source = resolveSource(id),
            sourceItem = null
        )
    }

    @JvmStatic
    fun toAlbum(entry: LibrarySearchEntry?, subsonicSourceId: String): MediaAlbum? {
        if (entry == null) return null
        val title = entry.title?.trim().orEmpty()
        if (title.isEmpty()) return null
        val id = resolveId(entry, entry.itemId, title, subsonicSourceId)
        val artistId = entry.artistId?.let { resolveId(entry, it, it, subsonicSourceId) }
        val cover = entry.coverArt?.let { MediaImage(resolveId(entry, it, it, subsonicSourceId)) }
        return MediaAlbum(
            id = id,
            title = title,
            artist = entry.artist?.trim(),
            artistId = artistId,
            year = null,
            trackCount = null,
            coverArt = cover,
            source = resolveSource(id),
            sourceItem = null
        )
    }

    @JvmStatic
    fun toSong(entry: LibrarySearchEntry?, subsonicSourceId: String): MediaSong? {
        if (entry == null) return null
        val title = entry.title?.trim().orEmpty()
        if (title.isEmpty()) return null
        val id = resolveId(entry, entry.itemId, title, subsonicSourceId)
        val artistId = entry.artistId?.let { resolveId(entry, it, it, subsonicSourceId) }
        val albumId = entry.albumId?.let { resolveId(entry, it, it, subsonicSourceId) }
        val cover = entry.coverArt?.let { MediaImage(resolveId(entry, it, it, subsonicSourceId)) }
        return MediaSong(
            id = id,
            title = title,
            artist = entry.artist?.trim(),
            album = entry.album?.trim(),
            artistId = artistId,
            albumId = albumId,
            durationMs = null,
            trackNumber = null,
            discNumber = null,
            year = null,
            coverArt = cover,
            source = resolveSource(id),
            sourceItem = null
        )
    }

    @JvmStatic
    fun toPlaylist(entry: LibrarySearchEntry?, subsonicSourceId: String): MediaPlaylist? {
        if (entry == null) return null
        val name = entry.title?.trim().orEmpty()
        if (name.isEmpty()) return null
        val id = resolveId(entry, entry.itemId, name, subsonicSourceId)
        val cover = entry.coverArt?.let { MediaImage(resolveId(entry, it, it, subsonicSourceId)) }
        return MediaPlaylist(
            id = id,
            name = name,
            owner = null,
            songCount = null,
            durationMs = null,
            coverArt = cover,
            source = resolveSource(id),
            sourceItem = null
        )
    }

    private fun resolveId(
        entry: LibrarySearchEntry,
        rawId: String?,
        fallback: String,
        subsonicSourceId: String
    ): MediaId {
        return when (entry.source) {
            SearchIndexUtil.SOURCE_LOCAL -> MediaIdResolver.local(rawId, fallback)
            SearchIndexUtil.SOURCE_JELLYFIN -> MediaIdResolver.jellyfin(MediaSourceType.JELLYFIN.id, rawId, fallback)
            else -> MediaIdResolver.subsonic(subsonicSourceId, rawId, fallback)
        }
    }

    private fun resolveSource(id: MediaId): MediaSourceRef {
        return MediaSourceRegistry.get(id.sourceId)
            ?: MediaSourceRef(id.sourceId, id.sourceType, id.sourceType.id)
    }
}
