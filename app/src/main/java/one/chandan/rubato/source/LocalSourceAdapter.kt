package one.chandan.rubato.source

import one.chandan.rubato.domain.MediaAlbum
import one.chandan.rubato.domain.MediaArtist
import one.chandan.rubato.domain.MediaImage
import one.chandan.rubato.domain.MediaPlaylist
import one.chandan.rubato.domain.MediaSong
import one.chandan.rubato.domain.MediaSourceRef
import one.chandan.rubato.domain.MediaSourceType
import one.chandan.rubato.subsonic.models.AlbumID3
import one.chandan.rubato.subsonic.models.ArtistID3
import one.chandan.rubato.subsonic.models.Child
import one.chandan.rubato.subsonic.models.Playlist

class LocalSourceAdapter : MediaSourceAdapter<ArtistID3, AlbumID3, Child, Playlist> {
    override val source: MediaSourceRef =
        MediaSourceRef(MediaSourceType.LOCAL.id, MediaSourceType.LOCAL)

    override fun mapArtist(item: ArtistID3): MediaArtist? {
        val name = item.name?.trim().orEmpty()
        if (name.isEmpty()) return null
        val id = MediaIdResolver.local(item.id, name)
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
        val id = MediaIdResolver.local(item.id, title)
        val artistId = item.artistId?.let { MediaIdResolver.local(it, it) }
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
        val id = MediaIdResolver.local(item.id, title)
        val artistId = item.artistId?.let { MediaIdResolver.local(it, it) }
        val albumId = item.albumId?.let { MediaIdResolver.local(it, it) }
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
        val id = MediaIdResolver.local(item.id, name)
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

    private fun coverArt(coverArtId: String?): MediaImage? {
        val id = coverArtId?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return MediaImage(MediaIdResolver.local(id, id))
    }
}
