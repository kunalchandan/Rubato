package one.chandan.rubato.source

import one.chandan.rubato.domain.MediaAlbum
import one.chandan.rubato.domain.MediaArtist
import one.chandan.rubato.domain.MediaPlaylist
import one.chandan.rubato.domain.MediaSong
import one.chandan.rubato.domain.MediaSourceRef

interface MediaSourceAdapter<Artist, Album, Song, Playlist> {
    val source: MediaSourceRef

    fun mapArtist(item: Artist): MediaArtist?

    fun mapAlbum(item: Album): MediaAlbum?

    fun mapSong(item: Song): MediaSong?

    fun mapPlaylist(item: Playlist): MediaPlaylist?
}
