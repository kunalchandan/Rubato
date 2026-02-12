package one.chandan.rubato.ui.state

import one.chandan.rubato.model.LibrarySourceItem
import one.chandan.rubato.subsonic.models.AlbumID3
import one.chandan.rubato.subsonic.models.ArtistID3
import one.chandan.rubato.subsonic.models.Genre
import one.chandan.rubato.subsonic.models.MusicFolder
import one.chandan.rubato.subsonic.models.Playlist

data class LibraryUiState(
    val loading: Boolean,
    val offline: Boolean,
    val error: String? = null,
    val musicFolders: List<MusicFolder> = emptyList(),
    val sources: List<LibrarySourceItem> = emptyList(),
    val albums: List<AlbumID3> = emptyList(),
    val artists: List<ArtistID3> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)
