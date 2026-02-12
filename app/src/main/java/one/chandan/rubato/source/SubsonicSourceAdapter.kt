package one.chandan.rubato.source

import one.chandan.rubato.domain.MediaAlbum
import one.chandan.rubato.domain.MediaArtist
import one.chandan.rubato.domain.MediaId
import one.chandan.rubato.domain.MediaImage
import one.chandan.rubato.domain.MediaPlaylist
import one.chandan.rubato.domain.MediaSong
import one.chandan.rubato.domain.MediaSourceRef
import one.chandan.rubato.domain.MediaSourceType
import one.chandan.rubato.subsonic.models.AlbumID3
import one.chandan.rubato.subsonic.models.ArtistID3
import one.chandan.rubato.subsonic.models.Child
import one.chandan.rubato.subsonic.models.Playlist

class SubsonicSourceAdapter(
    sourceId: String,
    displayName: String? = null
) : MediaSourceAdapter<ArtistID3, AlbumID3, Child, Playlist> {
    private val resolvedSourceId = sourceId.trim().ifEmpty { "default" }

    override val source: MediaSourceRef =
        MediaSourceRef(resolvedSourceId, MediaSourceType.SUBSONIC, displayName)

    override fun mapArtist(item: ArtistID3): MediaArtist? {
        val name = item.name?.trim().orEmpty()
        if (name.isEmpty()) return null
        val id = mediaId(item.id, name)
        return MediaArtist(
            id = id,
            name = name,
            albumCount = if (item.albumCount > 0) item.albumCount else null,
            coverArt = coverArt(item.coverArtId),
            source = source,
            sourceItem = item
        )
    }

    override fun mapAlbum(item: AlbumID3): MediaAlbum? {
        val title = item.name?.trim().orEmpty()
        if (title.isEmpty()) return null
        val id = mediaId(item.id, title)
        val artistId = item.artistId?.let { MediaId(MediaSourceType.SUBSONIC, resolvedSourceId, it) }
        return MediaAlbum(
            id = id,
            title = title,
            artist = item.artist?.trim(),
            artistId = artistId,
            year = if (item.year > 0) item.year else null,
            trackCount = item.songCount?.takeIf { it > 0 },
            coverArt = coverArt(item.coverArtId),
            source = source,
            sourceItem = item
        )
    }

    override fun mapSong(item: Child): MediaSong? {
        val title = item.title?.trim().orEmpty()
        if (title.isEmpty()) return null
        val id = MediaId(MediaSourceType.SUBSONIC, resolvedSourceId, item.id)
        val artistId = item.artistId?.let { MediaId(MediaSourceType.SUBSONIC, resolvedSourceId, it) }
        val albumId = item.albumId?.let { MediaId(MediaSourceType.SUBSONIC, resolvedSourceId, it) }
        return MediaSong(
            id = id,
            title = title,
            artist = item.artist?.trim(),
            album = item.album?.trim(),
            artistId = artistId,
            albumId = albumId,
            durationMs = item.duration?.takeIf { it > 0 }?.toLong()?.times(1000L),
            trackNumber = item.track?.takeIf { it > 0 },
            discNumber = item.discNumber?.takeIf { it > 0 },
            year = item.year?.takeIf { it > 0 },
            coverArt = coverArt(item.coverArtId),
            source = source,
            sourceItem = item
        )
    }

    override fun mapPlaylist(item: Playlist): MediaPlaylist? {
        val name = item.name?.trim().orEmpty()
        if (name.isEmpty()) return null
        val id = MediaId(MediaSourceType.SUBSONIC, resolvedSourceId, item.id)
        return MediaPlaylist(
            id = id,
            name = name,
            owner = item.owner?.trim(),
            songCount = item.songCount.takeIf { it > 0 },
            durationMs = item.duration.takeIf { it > 0 }?.times(1000L),
            coverArt = coverArt(item.coverArtId),
            source = source,
            sourceItem = item
        )
    }

    private fun mediaId(rawId: String?, fallback: String): MediaId {
        val externalId = rawId?.trim().takeUnless { it.isNullOrEmpty() } ?: fallback
        return MediaId(MediaSourceType.SUBSONIC, resolvedSourceId, externalId)
    }

    private fun coverArt(coverArtId: String?): MediaImage? {
        val id = coverArtId?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return MediaImage(MediaId(MediaSourceType.SUBSONIC, resolvedSourceId, id))
    }
}
