package one.chandan.rubato.domain

import one.chandan.rubato.subsonic.models.AlbumID3
import one.chandan.rubato.subsonic.models.ArtistID3
import one.chandan.rubato.subsonic.models.Child
import one.chandan.rubato.subsonic.models.Playlist
import one.chandan.rubato.util.Constants
import one.chandan.rubato.util.SearchIndexUtil

object LegacyMediaMapper {
    fun toArtistId3(item: MediaArtist): ArtistID3? {
        val sourceItem = item.sourceItem
        if (sourceItem is ArtistID3) return sourceItem
        val artist = ArtistID3()
        artist.id = legacyId(item.id)
        artist.name = item.name
        artist.albumCount = item.albumCount ?: 0
        artist.coverArtId = item.coverArt?.id?.let { legacyId(it) }
        return artist
    }

    fun toAlbumId3(item: MediaAlbum): AlbumID3? {
        val sourceItem = item.sourceItem
        if (sourceItem is AlbumID3) return sourceItem
        val album = AlbumID3()
        album.id = legacyId(item.id)
        album.name = item.title
        album.artist = item.artist
        album.artistId = item.artistId?.let { legacyId(it) }
        album.year = item.year ?: 0
        album.songCount = item.trackCount ?: 0
        album.coverArtId = item.coverArt?.id?.let { legacyId(it) }
        return album
    }

    fun toSong(item: MediaSong): Child? {
        val sourceItem = item.sourceItem
        if (sourceItem is Child) return sourceItem
        val song = Child(legacyId(item.id))
        song.title = item.title
        song.artist = item.artist
        song.album = item.album
        song.artistId = item.artistId?.let { legacyId(it) }
        song.albumId = item.albumId?.let { legacyId(it) }
        song.duration = item.durationMs?.takeIf { it > 0 }?.div(1000L)?.toInt()
        song.track = item.trackNumber
        song.discNumber = item.discNumber
        song.year = item.year
        song.coverArtId = item.coverArt?.id?.let { legacyId(it) }
        song.type = if (item.id.sourceType == MediaSourceType.LOCAL) {
            Constants.MEDIA_TYPE_LOCAL
        } else {
            Constants.MEDIA_TYPE_MUSIC
        }
        return song
    }

    fun toPlaylist(item: MediaPlaylist): Playlist? {
        val sourceItem = item.sourceItem
        if (sourceItem is Playlist) return sourceItem
        val playlist = Playlist(legacyId(item.id))
        playlist.name = item.name
        playlist.owner = item.owner
        playlist.songCount = item.songCount ?: 0
        playlist.duration = item.durationMs?.takeIf { it > 0 }?.div(1000L) ?: 0L
        playlist.coverArtId = item.coverArt?.id?.let { legacyId(it) }
        return playlist
    }

    private fun legacyId(id: MediaId): String {
        return when (id.sourceType) {
            MediaSourceType.SUBSONIC -> id.externalId
            MediaSourceType.LOCAL -> id.externalId
            MediaSourceType.JELLYFIN -> {
                val raw = "${id.sourceId}:${id.externalId}"
                SearchIndexUtil.tagSourceId(SearchIndexUtil.SOURCE_JELLYFIN, raw)
            }
            MediaSourceType.OTHER -> id.externalId
        }
    }
}
