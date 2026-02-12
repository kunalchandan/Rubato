package one.chandan.rubato.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicInteger
import one.chandan.rubato.App
import one.chandan.rubato.domain.MediaAlbum
import one.chandan.rubato.domain.MediaArtist
import one.chandan.rubato.domain.MediaDedupeUtil
import one.chandan.rubato.domain.MediaPlaylist
import one.chandan.rubato.domain.MediaSong
import one.chandan.rubato.domain.MediaSourceRef
import one.chandan.rubato.domain.MediaSourceType
import one.chandan.rubato.domain.LegacyMediaMapper
import one.chandan.rubato.source.JellyfinSourceAdapter
import one.chandan.rubato.source.LocalSourceAdapter
import one.chandan.rubato.source.MediaSourceRegistry
import one.chandan.rubato.source.SubsonicSourceAdapter
import one.chandan.rubato.subsonic.models.AlbumID3
import one.chandan.rubato.subsonic.models.AlbumInfo
import one.chandan.rubato.subsonic.models.ArtistID3
import one.chandan.rubato.subsonic.models.ArtistInfo2
import one.chandan.rubato.subsonic.models.Child
import one.chandan.rubato.subsonic.models.Genre
import one.chandan.rubato.subsonic.models.Playlist
import one.chandan.rubato.subsonic.models.SearchResult3
import one.chandan.rubato.subsonic.base.ApiResponse
import one.chandan.rubato.sync.JellyfinSyncProvider
import one.chandan.rubato.sync.SyncMode
import one.chandan.rubato.util.AutoLog
import one.chandan.rubato.util.JellyfinTagUtil
import one.chandan.rubato.util.NetworkUtil
import one.chandan.rubato.util.OfflinePolicy
import one.chandan.rubato.util.Preferences
import one.chandan.rubato.util.SearchIndexUtil
import retrofit2.Response

class LibraryRepository(
    private val cacheRepository: CacheRepository = CacheRepository(),
    private val jellyfinCacheRepository: JellyfinCacheRepository = JellyfinCacheRepository(),
    private val jellyfinServerRepository: JellyfinServerRepository = JellyfinServerRepository()
) {
    fun interface Result<T> {
        fun onResult(items: List<T>)
    }

    private val searchRepository = SearchRepository()
    private val albumRepository = AlbumRepository()
    private val artistRepository = ArtistRepository()
    private val songRepository = SongRepository()
    private val playlistRepository = PlaylistRepository()
    private val genreRepository = GenreRepository()
    private val searchIndexRepository = LibrarySearchIndexRepository()

    private val albumPageSize = 500

    init {
        registerDefaultSources()
    }

    fun loadArtists(callback: Result<MediaArtist>) {
        val remaining = AtomicInteger(3)
        var subsonic: List<MediaArtist> = emptyList()
        var local: List<MediaArtist> = emptyList()
        var jellyfin: List<MediaArtist> = emptyList()

        loadSubsonicArtists { subsonic = it; if (remaining.decrementAndGet() == 0) finishArtists(subsonic, local, jellyfin, callback) }
        loadLocalArtists { local = it; if (remaining.decrementAndGet() == 0) finishArtists(subsonic, local, jellyfin, callback) }
        loadJellyfinArtists { jellyfin = it; if (remaining.decrementAndGet() == 0) finishArtists(subsonic, local, jellyfin, callback) }
    }

    fun loadArtistsLegacy(callback: Result<one.chandan.rubato.subsonic.models.ArtistID3>) {
        loadArtists { items ->
            val mapped = items.mapNotNull { LegacyMediaMapper.toArtistId3(it) }
            callback.onResult(mapped)
        }
    }

    fun loadAlbumsLegacy(callback: Result<one.chandan.rubato.subsonic.models.AlbumID3>) {
        loadAlbums { items ->
            val mapped = items.mapNotNull { LegacyMediaMapper.toAlbumId3(it) }
            callback.onResult(mapped)
        }
    }

    fun loadSongsLegacy(callback: Result<one.chandan.rubato.subsonic.models.Child>) {
        loadSongs { items ->
            val mapped = items.mapNotNull { LegacyMediaMapper.toSong(it) }
            callback.onResult(mapped)
        }
    }

    fun loadPlaylistsLegacy(callback: Result<one.chandan.rubato.subsonic.models.Playlist>) {
        loadPlaylists { items ->
            val mapped = items.mapNotNull { LegacyMediaMapper.toPlaylist(it) }
            callback.onResult(mapped)
        }
    }

    fun loadGenresLegacy(callback: Result<Genre>) {
        val remaining = AtomicInteger(3)
        var subsonic: List<Genre> = emptyList()
        var local: List<Genre> = emptyList()
        var jellyfin: List<Genre> = emptyList()

        loadSubsonicGenres { subsonic = it; if (remaining.decrementAndGet() == 0) finishGenres(subsonic, local, jellyfin, callback) }
        loadLocalGenres { local = it; if (remaining.decrementAndGet() == 0) finishGenres(subsonic, local, jellyfin, callback) }
        loadJellyfinGenres { jellyfin = it; if (remaining.decrementAndGet() == 0) finishGenres(subsonic, local, jellyfin, callback) }
    }

    fun searchLegacy(query: String): LiveData<SearchResult3> {
        return searchRepository.search3(query)
    }

    fun searchPlaylistsLegacy(query: String): LiveData<List<Playlist>> {
        return searchRepository.searchPlaylists(query)
    }

    fun getAlbums(type: String, size: Int, fromYear: Int?, toYear: Int?): LiveData<List<AlbumID3>> {
        return albumRepository.getAlbums(type, size, fromYear, toYear)
    }

    fun getStarredAlbums(random: Boolean, size: Int): LiveData<List<AlbumID3>> {
        return albumRepository.getStarredAlbums(random, size)
    }

    fun getAlbumTracks(id: String): LiveData<List<Child>> {
        return albumRepository.getAlbumTracks(id)
    }

    fun getAlbumTracks(album: AlbumID3?): LiveData<List<Child>> {
        return albumRepository.getAlbumTracks(album)
    }

    fun getAlbum(id: String): LiveData<AlbumID3> {
        return albumRepository.getAlbum(id)
    }

    fun getAlbumInfo(id: String): LiveData<AlbumInfo> {
        return albumRepository.getAlbumInfo(id)
    }

    fun getArtistAlbums(artist: ArtistID3): LiveData<List<AlbumID3>> {
        return albumRepository.getArtistAlbums(artist)
    }

    fun getArtists(random: Boolean, size: Int): LiveData<List<ArtistID3>> {
        return artistRepository.getArtists(random, size)
    }

    fun getStarredArtists(random: Boolean, size: Int): LiveData<List<ArtistID3>> {
        return artistRepository.getStarredArtists(random, size)
    }

    fun getArtistInfo(id: String): LiveData<ArtistID3> {
        return artistRepository.getArtistInfo(id)
    }

    fun getArtistFullInfo(id: String): LiveData<ArtistInfo2> {
        return artistRepository.getArtistFullInfo(id)
    }

    fun getArtistTopSongs(artistName: String, count: Int): MutableLiveData<List<Child>> {
        return artistRepository.getTopSongs(artistName, count)
    }

    fun getArtistShuffleSongs(artist: ArtistID3, count: Int): MutableLiveData<List<Child>> {
        return artistRepository.getRandomSong(artist, count)
    }

    fun getArtistInstantMix(artist: ArtistID3, count: Int): MutableLiveData<List<Child>> {
        return artistRepository.getInstantMix(artist, count)
    }

    fun getPlaylists(random: Boolean, size: Int): LiveData<List<Playlist>> {
        return playlistRepository.getPlaylists(random, size)
    }

    fun getPlaylistSongs(id: String): MutableLiveData<List<Child>> {
        return playlistRepository.getPlaylistSongs(id)
    }

    fun addSongToPlaylist(playlistId: String, songsId: ArrayList<String>) {
        playlistRepository.addSongToPlaylist(playlistId, songsId)
    }

    fun createPlaylist(playlistId: String?, name: String, songsId: ArrayList<String>) {
        playlistRepository.createPlaylist(playlistId, name, songsId)
    }

    fun deletePlaylist(playlistId: String) {
        playlistRepository.deletePlaylist(playlistId)
    }

    fun updatePlaylist(playlistId: String, name: String, songsId: ArrayList<String>) {
        playlistRepository.updatePlaylist(playlistId, name, songsId)
    }

    fun getPinnedPlaylists(): LiveData<List<Playlist>> {
        return playlistRepository.getPinnedPlaylists()
    }

    fun setPinned(playlist: Playlist, pinned: Boolean) {
        if (pinned) {
            playlistRepository.insert(playlist)
        } else {
            playlistRepository.delete(playlist)
        }
    }

    fun updatePlaylist(
        playlistId: String,
        name: String,
        isPublic: Boolean,
        songIdToAdd: ArrayList<String>,
        songIndexToRemove: ArrayList<Int>
    ) {
        playlistRepository.updatePlaylist(playlistId, name, isPublic, songIdToAdd, songIndexToRemove)
    }

    fun getStarredSongs(random: Boolean, size: Int): MutableLiveData<List<Child>> {
        return songRepository.getStarredSongs(random, size)
    }

    fun getSong(id: String): LiveData<Child> {
        return songRepository.getSong(id)
    }

    fun getSongLyrics(song: Child): LiveData<String> {
        return songRepository.getSongLyrics(song)
    }

    fun getSongInstantMix(id: String, count: Int): MutableLiveData<List<Child>> {
        return songRepository.getInstantMix(id, count)
    }

    fun getRandomSample(number: Int, fromYear: Int?, toYear: Int?): MutableLiveData<List<Child>> {
        return songRepository.getRandomSample(number, fromYear, toYear)
    }

    fun getSongsByGenre(genre: String, page: Int): MutableLiveData<List<Child>> {
        return songRepository.getSongsByGenre(genre, page)
    }

    fun getSongsByGenres(genresId: ArrayList<String>): MutableLiveData<List<Child>> {
        return songRepository.getSongsByGenres(genresId)
    }

    fun getRelatedGenres(genreName: String, limit: Int): MutableLiveData<List<Genre>> {
        return genreRepository.getRelatedGenres(genreName, limit)
    }

    fun setSongRating(id: String, rating: Int) {
        songRepository.setRating(id, rating)
    }

    fun setAlbumRating(id: String, rating: Int) {
        albumRepository.setRating(id, rating)
    }

    fun setArtistRating(id: String, rating: Int) {
        artistRepository.setRating(id, rating)
    }

    fun loadAlbums(callback: Result<MediaAlbum>) {
        val remaining = AtomicInteger(3)
        var subsonic: List<MediaAlbum> = emptyList()
        var local: List<MediaAlbum> = emptyList()
        var jellyfin: List<MediaAlbum> = emptyList()

        loadSubsonicAlbums { subsonic = it; if (remaining.decrementAndGet() == 0) finishAlbums(subsonic, local, jellyfin, callback) }
        loadLocalAlbums { local = it; if (remaining.decrementAndGet() == 0) finishAlbums(subsonic, local, jellyfin, callback) }
        loadJellyfinAlbums { jellyfin = it; if (remaining.decrementAndGet() == 0) finishAlbums(subsonic, local, jellyfin, callback) }
    }

    fun loadSongs(callback: Result<MediaSong>) {
        val remaining = AtomicInteger(3)
        var subsonic: List<MediaSong> = emptyList()
        var local: List<MediaSong> = emptyList()
        var jellyfin: List<MediaSong> = emptyList()

        loadSubsonicSongs { subsonic = it; if (remaining.decrementAndGet() == 0) finishSongs(subsonic, local, jellyfin, callback) }
        loadLocalSongs { local = it; if (remaining.decrementAndGet() == 0) finishSongs(subsonic, local, jellyfin, callback) }
        loadJellyfinSongs { jellyfin = it; if (remaining.decrementAndGet() == 0) finishSongs(subsonic, local, jellyfin, callback) }
    }

    fun loadPlaylists(callback: Result<MediaPlaylist>) {
        val remaining = AtomicInteger(2)
        var subsonic: List<MediaPlaylist> = emptyList()
        var jellyfin: List<MediaPlaylist> = emptyList()

        loadSubsonicPlaylists { subsonic = it; if (remaining.decrementAndGet() == 0) finishPlaylists(subsonic, jellyfin, callback) }
        loadJellyfinPlaylists { jellyfin = it; if (remaining.decrementAndGet() == 0) finishPlaylists(subsonic, jellyfin, callback) }
    }

    private fun finishArtists(
        subsonic: List<MediaArtist>,
        local: List<MediaArtist>,
        jellyfin: List<MediaArtist>,
        callback: Result<MediaArtist>
    ) {
        val merged = MediaDedupeUtil.mergeArtists(local, subsonic, jellyfin)
        val sorted = merged.sortedWith(compareBy<MediaArtist> { it.name?.lowercase() ?: "" })
        callback.onResult(sorted)
    }

    private fun finishAlbums(
        subsonic: List<MediaAlbum>,
        local: List<MediaAlbum>,
        jellyfin: List<MediaAlbum>,
        callback: Result<MediaAlbum>
    ) {
        val merged = MediaDedupeUtil.mergeAlbums(local, subsonic, jellyfin)
        val sorted = merged.sortedWith(compareBy<MediaAlbum> { it.title?.lowercase() ?: "" }
            .thenBy { it.artist?.lowercase() ?: "" })
        AutoLog.event(
            "library_albums_merge",
            mapOf(
                "subsonic" to subsonic.size,
                "jellyfin" to jellyfin.size,
                "local" to local.size,
                "offline" to NetworkUtil.isOffline()
            )
        )
        callback.onResult(sorted)
    }

    private fun finishSongs(
        subsonic: List<MediaSong>,
        local: List<MediaSong>,
        jellyfin: List<MediaSong>,
        callback: Result<MediaSong>
    ) {
        callback.onResult(MediaDedupeUtil.mergeSongs(local, subsonic, jellyfin))
    }

    private fun finishPlaylists(
        subsonic: List<MediaPlaylist>,
        jellyfin: List<MediaPlaylist>,
        callback: Result<MediaPlaylist>
    ) {
        callback.onResult(MediaDedupeUtil.mergePlaylists(subsonic, jellyfin))
    }

    private fun finishGenres(
        subsonic: List<Genre>,
        local: List<Genre>,
        jellyfin: List<Genre>,
        callback: Result<Genre>
    ) {
        callback.onResult(mergeGenres(subsonic, local, jellyfin))
    }

    private fun loadSubsonicArtists(callback: (List<MediaArtist>) -> Unit) {
        val adapter = subsonicAdapter()
        val type: Type = object : TypeToken<List<ArtistID3>>() {}.type
        cacheRepository.loadOrNull("artists_all", type) { artists: List<ArtistID3>? ->
            if (!artists.isNullOrEmpty()) {
                callback(artists.mapNotNull { adapter.mapArtist(it) })
                return@loadOrNull
            }
            if (OfflinePolicy.isOffline()) {
                callback(emptyList())
                return@loadOrNull
            }
            val fetched = fetchSubsonicArtistsSnapshot()
            if (fetched.isNotEmpty()) {
                cacheRepository.save("artists_all", fetched)
            }
            callback(fetched.mapNotNull { adapter.mapArtist(it) })
        }
    }

    private fun loadSubsonicAlbums(callback: (List<MediaAlbum>) -> Unit) {
        val adapter = subsonicAdapter()
        val type: Type = object : TypeToken<List<AlbumID3>>() {}.type
        cacheRepository.loadOrNull("albums_all", type) { albums: List<AlbumID3>? ->
            if (!albums.isNullOrEmpty()) {
                AutoLog.event("library_subsonic_albums_cache_hit", mapOf("count" to albums.size))
                callback(albums.mapNotNull { adapter.mapAlbum(it) })
                return@loadOrNull
            }
            if (OfflinePolicy.isOffline()) {
                AutoLog.warn("library_subsonic_albums_offline")
                callback(emptyList())
                return@loadOrNull
            }
            val fetched = fetchSubsonicAlbumsSnapshot()
            if (!fetched.isNullOrEmpty()) {
                AutoLog.event("library_subsonic_albums_fetched", mapOf("count" to fetched.size))
                cacheRepository.save("albums_all", fetched)
            }
            callback(fetched?.mapNotNull { adapter.mapAlbum(it) } ?: emptyList())
        }
    }

    private fun loadSubsonicSongs(callback: (List<MediaSong>) -> Unit) {
        val adapter = subsonicAdapter()
        val type: Type = object : TypeToken<List<Child>>() {}.type
        cacheRepository.loadOrNull("songs_all", type) { songs: List<Child>? ->
            callback(songs?.mapNotNull { adapter.mapSong(it) } ?: emptyList())
        }
    }

    private fun loadSubsonicPlaylists(callback: (List<MediaPlaylist>) -> Unit) {
        val adapter = subsonicAdapter()
        val type: Type = object : TypeToken<List<Playlist>>() {}.type
        cacheRepository.loadOrNull("playlists", type) { playlists: List<Playlist>? ->
            callback(playlists?.mapNotNull { adapter.mapPlaylist(it) } ?: emptyList())
        }
    }

    private fun loadLocalArtists(callback: (List<MediaArtist>) -> Unit) {
        val adapter = LocalSourceAdapter()
        LocalMusicRepository.loadLibrary(App.getContext()) { library ->
            callback(library.artists.mapNotNull { adapter.mapArtist(it) })
        }
    }

    private fun loadLocalAlbums(callback: (List<MediaAlbum>) -> Unit) {
        val adapter = LocalSourceAdapter()
        LocalMusicRepository.loadLibrary(App.getContext()) { library ->
            callback(library.albums.mapNotNull { adapter.mapAlbum(it) })
        }
    }

    private fun loadLocalSongs(callback: (List<MediaSong>) -> Unit) {
        val adapter = LocalSourceAdapter()
        LocalMusicRepository.loadLibrary(App.getContext()) { library ->
            callback(library.songs.mapNotNull { adapter.mapSong(it) })
        }
    }

    private fun loadJellyfinArtists(callback: (List<MediaArtist>) -> Unit) {
        jellyfinCacheRepository.loadAllArtists { artists ->
            callback(mapJellyfinArtists(artists))
        }
    }

    private fun loadJellyfinAlbums(callback: (List<MediaAlbum>) -> Unit) {
        jellyfinCacheRepository.loadAllAlbums { albums ->
            if (!albums.isNullOrEmpty()) {
                AutoLog.event("library_jellyfin_albums_cache_hit", mapOf("count" to albums.size))
                callback(mapJellyfinAlbums(albums))
                return@loadAllAlbums
            }
            if (OfflinePolicy.isOffline()) {
                AutoLog.warn("library_jellyfin_albums_offline")
                callback(emptyList())
                return@loadAllAlbums
            }
            val servers = jellyfinServerRepository.getServersSnapshot()
            if (servers.isNullOrEmpty()) {
                AutoLog.warn("library_jellyfin_albums_no_servers")
                callback(emptyList())
                return@loadAllAlbums
            }
            val didSync = JellyfinSyncProvider.sync(cacheRepository, searchIndexRepository, null, SyncMode.FULL)
            if (!didSync) {
                AutoLog.warn("library_jellyfin_albums_sync_failed")
                callback(emptyList())
                return@loadAllAlbums
            }
            jellyfinCacheRepository.loadAllAlbums { refreshed ->
                AutoLog.event("library_jellyfin_albums_fetched", mapOf("count" to (refreshed?.size ?: 0)))
                callback(mapJellyfinAlbums(refreshed))
            }
        }
    }

    private fun loadJellyfinSongs(callback: (List<MediaSong>) -> Unit) {
        jellyfinCacheRepository.loadAllSongs { songs ->
            callback(mapJellyfinSongs(songs))
        }
    }

    private fun loadJellyfinPlaylists(callback: (List<MediaPlaylist>) -> Unit) {
        jellyfinCacheRepository.loadAllPlaylists { playlists ->
            callback(mapJellyfinPlaylists(playlists))
        }
    }

    private fun fetchSubsonicAlbumsSnapshot(): List<AlbumID3> {
        val allAlbums = mutableListOf<AlbumID3>()
        var offset = 0
        while (true) {
            if (OfflinePolicy.isOffline()) break
            try {
                val response: Response<ApiResponse> = App.getSubsonicClientInstance(false)
                    .getAlbumSongListClient()
                    .getAlbumList2("alphabeticalByName", albumPageSize, offset, null, null)
                    .execute()
                if (!response.isSuccessful) break
                val page = response.body()?.subsonicResponse?.albumList2?.albums
                if (page.isNullOrEmpty()) break
                allAlbums.addAll(page)
                offset += page.size
                if (page.size < albumPageSize) break
            } catch (_: Exception) {
                break
            }
        }
        return allAlbums
    }

    private fun fetchSubsonicArtistsSnapshot(): List<ArtistID3> {
        if (OfflinePolicy.isOffline()) return emptyList()
        val allArtists = mutableListOf<ArtistID3>()
        return try {
            val response: Response<ApiResponse> = App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtists()
                .execute()
            if (!response.isSuccessful) return emptyList()
            val indices = response.body()?.subsonicResponse?.artists?.indices
            if (indices.isNullOrEmpty()) return emptyList()
            for (index in indices) {
                val artists = index?.artists
                if (!artists.isNullOrEmpty()) {
                    allArtists.addAll(artists)
                }
            }
            allArtists
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadSubsonicGenres(callback: (List<Genre>) -> Unit) {
        val type: Type = object : TypeToken<List<Genre>>() {}.type
        cacheRepository.loadOrNull("genres_all", type) { genres: List<Genre>? ->
            callback(genres ?: emptyList())
        }
    }

    private fun loadLocalGenres(callback: (List<Genre>) -> Unit) {
        LocalMusicRepository.loadLibrary(App.getContext()) { library ->
            callback(library.genres)
        }
    }

    private fun loadJellyfinGenres(callback: (List<Genre>) -> Unit) {
        jellyfinCacheRepository.loadAllSongs { songs ->
            if (songs.isNullOrEmpty()) {
                callback(emptyList())
                return@loadAllSongs
            }
            val byName = LinkedHashMap<String, GenreAccumulator>()
            for (song in songs) {
                val name = song?.genre?.trim().orEmpty()
                if (name.isEmpty()) continue
                val key = SearchIndexUtil.normalize(name)
                val accumulator = byName.getOrPut(key) { GenreAccumulator(name) }
                accumulator.songCount += 1
                val albumId = song.albumId
                if (!albumId.isNullOrBlank()) {
                    accumulator.albumIds.add(albumId)
                }
            }
            val merged = byName.values.map {
                Genre(it.name, it.songCount, it.albumIds.size)
            }
            callback(merged)
        }
    }

    private fun mapJellyfinArtists(artists: List<ArtistID3>?): List<MediaArtist> {
        if (artists.isNullOrEmpty()) return emptyList()
        val grouped = artists.groupBy { JellyfinTagUtil.extractServerId(it.id) ?: MediaSourceType.JELLYFIN.id }
        val mapped = mutableListOf<MediaArtist>()
        for ((serverId, entries) in grouped) {
            val adapter = JellyfinSourceAdapter(serverId, resolveJellyfinName(serverId))
            mapped.addAll(entries.mapNotNull { adapter.mapArtist(it) })
        }
        return mapped
    }

    private fun mapJellyfinAlbums(albums: List<AlbumID3>?): List<MediaAlbum> {
        if (albums.isNullOrEmpty()) return emptyList()
        val grouped = albums.groupBy { JellyfinTagUtil.extractServerId(it.id) ?: MediaSourceType.JELLYFIN.id }
        val mapped = mutableListOf<MediaAlbum>()
        for ((serverId, entries) in grouped) {
            val adapter = JellyfinSourceAdapter(serverId, resolveJellyfinName(serverId))
            mapped.addAll(entries.mapNotNull { adapter.mapAlbum(it) })
        }
        return mapped
    }

    private fun mapJellyfinSongs(songs: List<Child>?): List<MediaSong> {
        if (songs.isNullOrEmpty()) return emptyList()
        val grouped = songs.groupBy { JellyfinTagUtil.extractServerId(it.id) ?: MediaSourceType.JELLYFIN.id }
        val mapped = mutableListOf<MediaSong>()
        for ((serverId, entries) in grouped) {
            val adapter = JellyfinSourceAdapter(serverId, resolveJellyfinName(serverId))
            mapped.addAll(entries.mapNotNull { adapter.mapSong(it) })
        }
        return mapped
    }

    private fun mapJellyfinPlaylists(playlists: List<Playlist>?): List<MediaPlaylist> {
        if (playlists.isNullOrEmpty()) return emptyList()
        val grouped = playlists.groupBy { JellyfinTagUtil.extractServerId(it.id) ?: MediaSourceType.JELLYFIN.id }
        val mapped = mutableListOf<MediaPlaylist>()
        for ((serverId, entries) in grouped) {
            val adapter = JellyfinSourceAdapter(serverId, resolveJellyfinName(serverId))
            mapped.addAll(entries.mapNotNull { adapter.mapPlaylist(it) })
        }
        return mapped
    }

    private fun mergeGenres(vararg sources: List<Genre>?): List<Genre> {
        val merged = LinkedHashMap<String, Genre>()
        for (source in sources) {
            if (source.isNullOrEmpty()) continue
            for (genre in source) {
                val name = genre.genre?.trim().orEmpty()
                if (name.isEmpty()) continue
                val key = SearchIndexUtil.normalize(name)
                val existing = merged[key]
                if (existing == null) {
                    merged[key] = Genre(name, genre.songCount, genre.albumCount)
                } else {
                    val songCount = maxOf(existing.songCount, genre.songCount)
                    val albumCount = maxOf(existing.albumCount, genre.albumCount)
                    val mergedName = existing.genre?.takeIf { it.isNotBlank() } ?: name
                    merged[key] = Genre(mergedName, songCount, albumCount)
                }
            }
        }
        val result = merged.values.toMutableList()
        result.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.genre ?: "" })
        return result
    }

    private data class GenreAccumulator(
        val name: String,
        var songCount: Int = 0,
        val albumIds: MutableSet<String> = mutableSetOf()
    )

    private fun resolveJellyfinName(serverId: String): String? {
        val server = jellyfinServerRepository.findById(serverId)
        return server?.libraryName?.takeIf { it.isNotBlank() } ?: server?.name
    }

    private fun subsonicAdapter(): SubsonicSourceAdapter {
        val serverId = Preferences.getServerId()?.trim().takeUnless { it.isNullOrEmpty() } ?: MediaSourceType.SUBSONIC.id
        return SubsonicSourceAdapter(serverId, MediaSourceType.SUBSONIC.id)
    }

    private fun registerDefaultSources() {
        val subsonicId = Preferences.getServerId()?.trim().takeUnless { it.isNullOrEmpty() } ?: MediaSourceType.SUBSONIC.id
        MediaSourceRegistry.register(MediaSourceRef(subsonicId, MediaSourceType.SUBSONIC, MediaSourceType.SUBSONIC.id))
        MediaSourceRegistry.register(MediaSourceRef(MediaSourceType.LOCAL.id, MediaSourceType.LOCAL, MediaSourceType.LOCAL.id))

        val jellyfinServers = jellyfinServerRepository.getServersSnapshot()
        for (server in jellyfinServers) {
            if (server == null) continue
            val name = server.libraryName.takeIf { it.isNotBlank() } ?: server.name
            MediaSourceRegistry.register(MediaSourceRef(server.id, MediaSourceType.JELLYFIN, name))
        }
    }
}
