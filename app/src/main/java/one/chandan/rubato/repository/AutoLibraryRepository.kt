package one.chandan.rubato.repository

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.appcompat.content.res.AppCompatResources
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import one.chandan.rubato.App
import one.chandan.rubato.R
import one.chandan.rubato.jellyfin.JellyfinMediaUtil
import one.chandan.rubato.database.AppDatabase
import one.chandan.rubato.database.dao.ChronologyDao
import one.chandan.rubato.database.dao.SessionMediaItemDao
import one.chandan.rubato.domain.LegacyMediaMapper
import one.chandan.rubato.domain.MediaAlbum
import one.chandan.rubato.domain.MediaArtist
import one.chandan.rubato.domain.MediaId
import one.chandan.rubato.domain.MediaImage
import one.chandan.rubato.domain.MediaPlaylist
import one.chandan.rubato.domain.MediaSong
import one.chandan.rubato.domain.MediaSourceType
import one.chandan.rubato.glide.CustomGlideRequest
import one.chandan.rubato.model.Chronology
import one.chandan.rubato.model.SearchSongLite
import one.chandan.rubato.model.SessionMediaItem
import one.chandan.rubato.provider.AutoArtworkProvider
import one.chandan.rubato.subsonic.models.Playlist
import one.chandan.rubato.util.AppExecutors
import one.chandan.rubato.util.AutoLog
import one.chandan.rubato.util.Constants
import one.chandan.rubato.util.JellyfinTagUtil
import one.chandan.rubato.util.MappingUtil
import one.chandan.rubato.util.NetworkUtil
import one.chandan.rubato.util.Preferences
import one.chandan.rubato.util.SearchIndexUtil
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.ArrayDeque
import java.util.Random
import kotlin.math.min
import java.util.concurrent.atomic.AtomicReference

class AutoLibraryRepository(
    private val libraryRepository: LibraryRepository = LibraryRepository(),
    private val searchIndexRepository: LibrarySearchIndexRepository = LibrarySearchIndexRepository(),
    private val automotiveRepository: AutomotiveRepository = AutomotiveRepository(),
    private val sessionMediaItemDao: SessionMediaItemDao = AppDatabase.getInstance().sessionMediaItemDao(),
    private val chronologyDao: ChronologyDao = AppDatabase.getInstance().chronologyDao(),
    private val executor: ExecutorService = AppExecutors.newSingleThreadExecutor("rubato-auto")
) {
    companion object {
        const val AUTO_SHUFFLE_ALL_ID = "[autoShuffleAll]"
        const val AUTO_MADE_FOR_YOU_MIX_ID = "[autoMadeForYouMix]"
        private const val AUTO_MADE_FOR_YOU_MIX_PREFIX = "[autoMadeForYouMix]:"
        private const val SHUFFLE_INITIAL_COUNT = 100
        private const val SHUFFLE_APPEND_COUNT = 50
        private const val SHUFFLE_APPEND_THRESHOLD = 50
        private const val RECENT_HISTORY_LIMIT = 20
        private const val MADE_FOR_YOU_TILE_LIMIT = 4
    }

    private val closed = AtomicBoolean(false)
    private val shuffleLock = Any()
    @Volatile private var shuffleState: AutoShuffleState? = null
    @Volatile private var shuffleEntries: List<SearchSongLite> = emptyList()
    @Volatile private var shuffledEntries: List<SearchSongLite> = emptyList()

    private data class AutoShuffleState(
        var seed: Long,
        var nextIndex: Int,
        var consumedSinceAppend: Int,
        val recentlyPlayed: ArrayDeque<String>,
        var sessionId: Long,
        var active: Boolean
    )
    fun getAlbums(prefix: String, type: String, size: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        libraryRepository.loadAlbums { albums ->
            val ordered = when (type) {
                "newest", "recent" -> albums.sortedByDescending { it.year ?: 0 }
                "frequent" -> albums.sortedByDescending { it.trackCount ?: 0 }
                "alpha" -> albums.sortedBy { it.title?.lowercase() ?: "" }
                else -> albums
            }
            val trimmed = if (size > 0) ordered.take(size) else ordered
            val items = trimmed.map { albumToMediaItem(prefix, it) }
            if (items.isNotEmpty()) {
                AutoLog.event("auto_cache_hit", mapOf("section" to "albums", "count" to items.size, "type" to type))
                future.set(libraryResult(items, size))
            } else {
                AutoLog.warn("auto_cache_miss", mapOf("section" to "albums", "type" to type))
                fallbackToNetwork(future, "albums", networkCall = {
                    automotiveRepository.getAlbums(prefix, type, size)
                })
            }
        }
        return future
    }

    fun getArtists(prefix: String, size: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        libraryRepository.loadArtists { artists ->
            val ordered = artists.sortedBy { it.name?.lowercase() ?: "" }
            val trimmed = if (size > 0) ordered.take(size) else ordered
            val items = trimmed.map { artistToMediaItem(prefix, it) }
            if (items.isNotEmpty()) {
                AutoLog.event("auto_cache_hit", mapOf("section" to "artists", "count" to items.size))
                future.set(libraryResult(items, size))
            } else {
                AutoLog.warn("auto_cache_miss", mapOf("section" to "artists"))
                fallbackToNetwork(future, "artists", networkCall = null)
            }
        }
        return future
    }

    fun getSongs(size: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        libraryRepository.loadSongs { songs ->
            val ordered = songs.sortedBy { it.title?.lowercase() ?: "" }
            val trimmed = if (size > 0) ordered.take(size) else ordered
            val mediaItems = songsToMediaItems(trimmed)
            if (mediaItems.isNotEmpty() && hasRemoteSongs(songs)) {
                AutoLog.event("auto_cache_hit", mapOf("section" to "songs", "count" to mediaItems.size))
                future.set(libraryResult(mediaItems, size))
            } else {
                AutoLog.warn("auto_cache_miss", mapOf("section" to "songs"))
                buildFromSearchIndex(size, emptyList()) { items ->
                    when {
                        items.isNotEmpty() -> {
                            AutoLog.event("auto_search_index_hit", mapOf("section" to "songs", "count" to items.size))
                            future.set(libraryResult(items, size))
                        }
                        mediaItems.isNotEmpty() -> future.set(libraryResult(mediaItems, size))
                        else -> fallbackToNetwork(future, "songs", networkCall = null)
                    }
                }
            }
        }
        return future
    }

    fun getStarredSongs(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        libraryRepository.loadSongs { songs ->
            val starred = songs.filter { it.sourceItem is one.chandan.rubato.subsonic.models.Child && (it.sourceItem as one.chandan.rubato.subsonic.models.Child).starred != null }
            val mediaItems = songsToMediaItems(starred)
            if (mediaItems.isNotEmpty()) {
                AutoLog.event("auto_cache_hit", mapOf("section" to "starred_songs", "count" to mediaItems.size))
                future.set(libraryResult(mediaItems, mediaItems.size))
            } else {
                AutoLog.warn("auto_cache_miss", mapOf("section" to "starred_songs"))
                fallbackToNetwork(future, "starred_songs", networkCall = {
                    automotiveRepository.starredSongs
                })
            }
        }
        return future
    }

    fun getRandomSongs(count: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        libraryRepository.loadSongs { songs ->
            if (songs.isNotEmpty()) {
                val shuffled = songs.shuffled()
                val mediaItems = songsToMediaItems(shuffled)
                AutoLog.event("auto_cache_hit", mapOf("section" to "random_songs", "count" to mediaItems.size))
                future.set(libraryResult(mediaItems, count))
            } else {
                AutoLog.warn("auto_cache_miss", mapOf("section" to "random_songs"))
                fallbackToNetwork(future, "random_songs", networkCall = {
                    automotiveRepository.getRandomSongs(count)
                })
            }
        }
        return future
    }

    fun getRecentlyPlayedSongs(serverId: String?, count: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        runOnExecutor {
            val server = serverId ?: Preferences.getServerId()
            val entries = if (server == null) emptyList() else chronologyDao.getLastPlayedSimple(server, count)
            if (!entries.isNullOrEmpty()) {
                val mediaItems = MappingUtil.mapMediaItems(entries)
                storeSessionItemsFromChronology(entries)
                AutoLog.event("auto_cache_hit", mapOf("section" to "recent_songs", "count" to mediaItems.size))
                future.set(libraryResult(mediaItems, count))
            } else {
                AutoLog.warn("auto_cache_miss", mapOf("section" to "recent_songs"))
                libraryRepository.loadSongs { songs ->
                    val mediaItems = songsToMediaItems(songs)
                    if (mediaItems.isNotEmpty()) {
                        future.set(libraryResult(mediaItems, count))
                    } else {
                        fallbackToNetwork(future, "recent_songs", networkCall = null)
                    }
                }
            }
        }
        return future
    }

    fun getStarredAlbums(prefix: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        libraryRepository.loadAlbums { albums ->
            val starred = albums.filter { it.sourceItem is one.chandan.rubato.subsonic.models.AlbumID3 && (it.sourceItem as one.chandan.rubato.subsonic.models.AlbumID3).starred != null }
            val items = starred.map { albumToMediaItem(prefix, it) }
            if (items.isNotEmpty()) {
                AutoLog.event("auto_cache_hit", mapOf("section" to "starred_albums", "count" to items.size))
                future.set(libraryResult(items, items.size))
            } else {
                AutoLog.warn("auto_cache_miss", mapOf("section" to "starred_albums"))
                fallbackToNetwork(future, "starred_albums", networkCall = {
                    automotiveRepository.getStarredAlbums(prefix)
                })
            }
        }
        return future
    }

    fun getStarredArtists(prefix: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        libraryRepository.loadArtists { artists ->
            val starred = artists.filter { it.sourceItem is one.chandan.rubato.subsonic.models.ArtistID3 && (it.sourceItem as one.chandan.rubato.subsonic.models.ArtistID3).starred != null }
            val items = starred.map { artistToMediaItem(prefix, it) }
            if (items.isNotEmpty()) {
                AutoLog.event("auto_cache_hit", mapOf("section" to "starred_artists", "count" to items.size))
                future.set(libraryResult(items, items.size))
            } else {
                AutoLog.warn("auto_cache_miss", mapOf("section" to "starred_artists"))
                fallbackToNetwork(future, "starred_artists", networkCall = {
                    automotiveRepository.getStarredArtists(prefix)
                })
            }
        }
        return future
    }

    fun getMadeForYou(artistId: String, count: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        val matchId = normalizeExternalId(artistId)
        libraryRepository.loadSongs { songs ->
            val filtered = songs.filter { it.artistId?.externalId == matchId }
            val mediaItems = songsToMediaItems(filtered.shuffled())
            if (mediaItems.isNotEmpty()) {
                AutoLog.event("auto_cache_hit", mapOf("section" to "made_for_you", "count" to mediaItems.size))
                future.set(libraryResult(mediaItems, count))
            } else {
                AutoLog.warn("auto_cache_miss", mapOf("section" to "made_for_you"))
                fallbackToNetwork(future, "made_for_you", networkCall = {
                    automotiveRepository.getMadeForYou(artistId, count)
                })
            }
        }
        return future
    }

    fun getPlaylists(prefix: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        libraryRepository.loadPlaylists { playlists ->
            val items = playlists.map { playlistToMediaItem(prefix, it) }
            if (items.isNotEmpty()) {
                AutoLog.event("auto_cache_hit", mapOf("section" to "playlists", "count" to items.size))
                future.set(libraryResult(items, items.size))
            } else {
                AutoLog.warn("auto_cache_miss", mapOf("section" to "playlists"))
                fallbackToNetwork(future, "playlists", networkCall = {
                    automotiveRepository.getPlaylists(prefix)
                })
            }
        }
        return future
    }

    fun getHomePlaylists(prefix: String, limit: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        libraryRepository.loadPlaylists { playlists ->
            val ordered = orderPlaylistsForHome(playlists)
            val trimmed = if (limit > 0) ordered.take(limit) else ordered
            val items = trimmed.map { playlistToMediaItem(prefix, it) }
            if (items.isNotEmpty()) {
                AutoLog.event("auto_cache_hit", mapOf("section" to "home_playlists", "count" to items.size))
                future.set(libraryResult(items, limit))
            } else {
                AutoLog.warn("auto_cache_miss", mapOf("section" to "home_playlists"))
                future.set(LibraryResult.ofItemList(ImmutableList.of(), null))
            }
        }
        return future
    }

    fun getDiscoveryItems(prefix: String, limit: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        libraryRepository.loadAlbums { albums ->
            val ordered = albums.sortedByDescending { it.year ?: 0 }
            val trimmed = if (limit > 0) ordered.take(limit) else ordered
            val items = ArrayList<MediaItem>()
            items.add(buildShuffleAllItem())
            items.addAll(trimmed.map { albumToMediaItem(prefix, it) })
            future.set(libraryResult(items, items.size))
        }
        return future
    }

    fun getMadeForYouTiles(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        libraryRepository.loadSongs { songs ->
            if (songs.isEmpty()) {
                buildMadeForYouTilesFromSearchIndex(future)
                return@loadSongs
            }
            if (!hasRemoteSongs(songs)) {
                buildMadeForYouTilesFromSearchIndex(future)
                return@loadSongs
            }
            runOnExecutor {
                val recent = loadRecentChronology()
                val seeds = selectMadeForYouSeeds(songs, recent, MADE_FOR_YOU_TILE_LIMIT)
                val items = seeds.map { song ->
                    val title = "Mix from ${song.title}"
                    val baseArtwork = buildArtworkUri(song.coverArt)
                    val artworkUri = AutoArtworkProvider.buildMixUri(baseArtwork) ?: baseArtwork ?: drawableUri("ic_auto_song")
                    MediaItem.Builder()
                        .setMediaId(AUTO_MADE_FOR_YOU_MIX_PREFIX + legacyId(song.id))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(title)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                .setArtworkUri(artworkUri)
                                .build()
                        )
                        .build()
                }
                AutoLog.event("auto_made_for_you_tiles", mapOf("count" to items.size))
                future.set(libraryResult(items, items.size))
            }
        }
        return future
    }

    fun getLibraryAlbums(prefix: String, limit: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        libraryRepository.loadAlbums { albums ->
            runOnExecutor {
                val recent = loadRecentChronology()
                val recentIds = recent.mapNotNull { it.albumId }
                val ordered = sortByRecentOrLiked(
                    albums,
                    recentIds,
                    { legacyId(it.id) },
                    { isStarred(it.sourceItem) },
                    { it.title }
                )
                val trimmed = if (limit > 0) ordered.take(limit) else ordered
                val items = trimmed.map { albumToMediaItem(prefix, it) }
                future.set(libraryResult(items, limit))
            }
        }
        return future
    }

    fun getLibraryArtists(prefix: String, limit: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        libraryRepository.loadArtists { artists ->
            runOnExecutor {
                val recent = loadRecentChronology()
                val recentIds = recent.mapNotNull { it.artistId }
                val ordered = sortByRecentOrLiked(
                    artists,
                    recentIds,
                    { legacyId(it.id) },
                    { isStarred(it.sourceItem) },
                    { it.name }
                )
                val trimmed = if (limit > 0) ordered.take(limit) else ordered
                val items = trimmed.map { artistToMediaItem(prefix, it) }
                future.set(libraryResult(items, limit))
            }
        }
        return future
    }

    fun getLibrarySongs(limit: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        libraryRepository.loadSongs { songs ->
            runOnExecutor {
                val recent = loadRecentChronology()
                val recentIds = recent.map { it.id }
                val ordered = sortByRecentOrLiked(
                    songs,
                    recentIds,
                    { legacyId(it.id) },
                    { isStarred(it.sourceItem) },
                    { it.title }
                )
                val trimmed = if (limit > 0) ordered.take(limit) else ordered
                val mediaItems = songsToMediaItems(trimmed)
                if (mediaItems.isNotEmpty() && hasRemoteSongs(songs)) {
                    future.set(libraryResult(mediaItems, limit))
                    return@runOnExecutor
                }
                buildFromSearchIndex(limit, recentIds) { items ->
                    future.set(libraryResult(if (items.isNotEmpty()) items else mediaItems, limit))
                }
            }
        }
        return future
    }

    fun getLibraryGenres(prefix: String, limit: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        libraryRepository.loadGenresLegacy { genres ->
            runOnExecutor {
                val recent = loadRecentChronology()
                val recentByGenre = recent.mapNotNull { it.genre?.trim() }
                val recentRank = rankByRecency(recentByGenre)
                val ordered = genres.sortedWith { a, b ->
                    val nameA = a.genre ?: ""
                    val nameB = b.genre ?: ""
                    val rankA = recentRank[SearchIndexUtil.normalize(nameA)]
                    val rankB = recentRank[SearchIndexUtil.normalize(nameB)]
                    if (rankA != null || rankB != null) {
                        return@sortedWith (rankA ?: Int.MAX_VALUE) - (rankB ?: Int.MAX_VALUE)
                    }
                    val countA = a.songCount
                    val countB = b.songCount
                    if (countA != countB) {
                        return@sortedWith countB - countA
                    }
                    nameA.compareTo(nameB, ignoreCase = true)
                }
                val trimmed = if (limit > 0) ordered.take(limit) else ordered
                val items = trimmed.map { genre ->
                    val name = genre.genre ?: ""
                    MediaItem.Builder()
                        .setMediaId(prefix + name)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(name)
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_GENRE)
                                .build()
                        )
                        .build()
                }
                future.set(libraryResult(items, limit))
            }
        }
        return future
    }

    fun getGenreSongs(genreName: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        val liveData = libraryRepository.getSongsByGenre(genreName, 0)
        val observer = object : androidx.lifecycle.Observer<List<one.chandan.rubato.subsonic.models.Child>> {
            override fun onChanged(value: List<one.chandan.rubato.subsonic.models.Child>) {
                liveData.removeObserver(this)
                if (value.isEmpty()) {
                    future.set(LibraryResult.ofItemList(ImmutableList.of(), null))
                    return
                }
                storeSessionItemsFromChildren(value)
                future.set(libraryResult(MappingUtil.mapMediaItems(value), value.size))
            }
        }
        liveData.observeForever(observer)
        return future
    }

    private fun buildShuffleAllItem(): MediaItem {
        val title = App.getContext().getString(R.string.home_title_discovery_shuffle_all_button)
        return MediaItem.Builder()
            .setMediaId(AUTO_SHUFFLE_ALL_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .build()
            )
            .build()
    }

    private fun orderPlaylistsForHome(playlists: List<MediaPlaylist>): List<MediaPlaylist> {
        val preferred = Preferences.getLibraryPlaylistOrder()
        if (preferred.isNullOrEmpty()) {
            return playlists.sortedBy { it.name?.lowercase() ?: "" }
        }
        val rank = preferred.withIndex().associate { it.value to it.index }
        return playlists.sortedWith { a, b ->
            val rankA = rank[legacyId(a.id)] ?: Int.MAX_VALUE
            val rankB = rank[legacyId(b.id)] ?: Int.MAX_VALUE
            if (rankA != rankB) return@sortedWith rankA - rankB
            (a.name ?: "").compareTo(b.name ?: "", ignoreCase = true)
        }
    }

    private fun loadRecentChronology(): List<Chronology> {
        val server = Preferences.getServerId()
        if (server.isNullOrBlank()) return emptyList()
        return chronologyDao.getLastPlayedSimple(server, RECENT_HISTORY_LIMIT)
    }

    private fun rankByRecency(values: List<String>): Map<String, Int> {
        val rank = LinkedHashMap<String, Int>()
        for (value in values) {
            val key = SearchIndexUtil.normalize(value)
            if (key.isNotBlank() && !rank.containsKey(key)) {
                rank[key] = rank.size
            }
        }
        return rank
    }

    private fun <T> sortByRecentOrLiked(
        items: List<T>,
        recentIds: List<String>,
        idProvider: (T) -> String?,
        likedProvider: (T) -> Boolean,
        nameProvider: (T) -> String?
    ): List<T> {
        if (items.isEmpty()) return items
        val recentRank = LinkedHashMap<String, Int>()
        for (id in recentIds) {
            if (id.isNotBlank() && !recentRank.containsKey(id)) {
                recentRank[id] = recentRank.size
            }
        }
        return items.sortedWith { a, b ->
            val rankA = recentRank[idProvider(a)]
            val rankB = recentRank[idProvider(b)]
            if (rankA != null || rankB != null) {
                return@sortedWith (rankA ?: Int.MAX_VALUE) - (rankB ?: Int.MAX_VALUE)
            }
            val likedA = likedProvider(a)
            val likedB = likedProvider(b)
            if (likedA != likedB) {
                return@sortedWith if (likedA) -1 else 1
            }
            val nameA = nameProvider(a) ?: ""
            val nameB = nameProvider(b) ?: ""
            nameA.compareTo(nameB, ignoreCase = true)
        }
    }

    private fun isStarred(sourceItem: Any?): Boolean {
        return when (sourceItem) {
            is one.chandan.rubato.subsonic.models.Child -> sourceItem.starred != null
            is one.chandan.rubato.subsonic.models.AlbumID3 -> sourceItem.starred != null
            is one.chandan.rubato.subsonic.models.ArtistID3 -> sourceItem.starred != null
            else -> false
        }
    }

    private fun pickMadeForYouSeed(songs: List<MediaSong>, recent: List<Chronology>): MediaSong? {
        if (songs.isEmpty()) return null
        val liked = songs.filter { isStarred(it.sourceItem) }
        if (liked.isNotEmpty()) {
            return liked.random()
        }
        if (recent.isNotEmpty()) {
            val recentId = recent.first().id
            songs.firstOrNull { legacyId(it.id) == recentId }?.let { return it }
        }
        return songs.random()
    }

    private fun buildFromSearchIndex(
        limit: Int,
        recentIds: List<String>,
        callback: (List<MediaItem>) -> Unit
    ) {
        searchIndexRepository.getAllSongs { entries ->
            val safeEntries = entries ?: emptyList()
            if (safeEntries.isEmpty()) {
                callback(emptyList())
                return@getAllSongs
            }
            val ordered = sortByRecentOrLiked(
                safeEntries,
                recentIds,
                { entry -> tagId(entry.source, entry.itemId) ?: entry.itemId },
                { false },
                { entry -> entry.title }
            )
            val trimmed = if (limit > 0) ordered.take(limit) else ordered
            val children = trimmed.mapNotNull { toChild(it) }
            if (children.isEmpty()) {
                callback(emptyList())
                return@getAllSongs
            }
            storeSessionItemsFromChildren(children)
            callback(MappingUtil.mapMediaItems(children))
        }
    }

    private fun hasRemoteSongs(songs: List<MediaSong>): Boolean {
        if (songs.isEmpty()) return false
        return songs.any { it.source?.type != MediaSourceType.LOCAL }
    }

    private fun buildMadeForYouMix(seed: MediaSong, songs: List<MediaSong>, count: Int): List<MediaSong> {
        if (songs.isEmpty()) return emptyList()
        val seedArtistId = seed.artistId?.externalId
        val seedArtistName = seed.artist
        val filtered = songs.filter {
            when {
                seedArtistId != null && it.artistId?.externalId == seedArtistId -> true
                !seedArtistName.isNullOrBlank() && seedArtistName.equals(it.artist, ignoreCase = true) -> true
                else -> false
            }
        }
        val pool = if (filtered.isNotEmpty()) filtered else songs
        val shuffled = pool.shuffled()
        val mix = ArrayList<MediaSong>(count)
        mix.add(seed)
        for (song in shuffled) {
            if (mix.size >= count) break
            if (song == seed) continue
            mix.add(song)
        }
        return mix
    }

    private fun selectMadeForYouSeeds(
        songs: List<MediaSong>,
        recent: List<Chronology>,
        limit: Int
    ): List<MediaSong> {
        if (songs.isEmpty() || limit <= 0) return emptyList()
        val recentIds = recent.map { it.id }
        val ordered = sortByRecentOrLiked(
            songs,
            recentIds,
            { legacyId(it.id) },
            { isStarred(it.sourceItem) },
            { it.title }
        )
        val unique = LinkedHashMap<String, MediaSong>()
        for (song in ordered) {
            val key = "${song.title.lowercase()}|${song.artist?.lowercase() ?: ""}"
            if (!unique.containsKey(key)) {
                unique[key] = song
            }
            if (unique.size >= limit) break
        }
        return if (unique.isNotEmpty()) unique.values.toList() else ordered.take(limit)
    }

    private fun drawableUri(name: String): Uri? {
        val pkg = App.getContext().packageName
        return Uri.parse("android.resource://$pkg/drawable/$name")
    }

    private fun iconBytes(name: String, size: Int = 256): ByteArray? {
        val context = App.getContext()
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (resId == 0) return null
        val drawable = AppCompatResources.getDrawable(context, resId) ?: return null
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        val out = ByteArrayOutputStream()
        return if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            out.toByteArray()
        } else {
            null
        }
    }

    private fun wrapAutoArtwork(item: MediaItem): MediaItem {
        val metadata = item.mediaMetadata
        val current = metadata.artworkUri
        val base = when {
            current != null && current.toString().isNotBlank() -> current
            else -> {
                val coverArtId = metadata.extras?.getString("coverArtId")
                if (coverArtId.isNullOrBlank()) {
                    null
                } else if (coverArtId.startsWith("content://") ||
                    coverArtId.startsWith("file://") ||
                    coverArtId.startsWith("android.resource://")
                ) {
                    Uri.parse(coverArtId)
                } else {
                    CustomGlideRequest.createUrl(coverArtId, Preferences.getImageSize())?.let { Uri.parse(it) }
                }
            }
        }
        val wrapped = AutoArtworkProvider.buildCoverUri(base) ?: return item
        if (wrapped == current) return item
        val updatedMetadata = metadata.buildUpon().setArtworkUri(wrapped).build()
        return item.buildUpon().setMediaMetadata(updatedMetadata).build()
    }

    private fun toChild(entry: SearchSongLite): one.chandan.rubato.subsonic.models.Child? {
        val rawId = entry.itemId ?: return null
        val id = tagId(entry.source, rawId) ?: rawId
        val child = one.chandan.rubato.subsonic.models.Child(id)
        child.title = entry.title ?: ""
        child.artist = entry.artist ?: ""
        child.album = entry.album ?: ""
        child.albumId = tagId(entry.source, entry.albumId)
        child.artistId = tagId(entry.source, entry.artistId)
        child.coverArtId = tagId(entry.source, entry.coverArt)
        if (SearchIndexUtil.SOURCE_LOCAL == entry.source) {
            child.type = Constants.MEDIA_TYPE_LOCAL
        }
        return child
    }

    private fun tagId(source: String?, value: String?): String? {
        if (value.isNullOrBlank()) return value
        if (SearchIndexUtil.SOURCE_JELLYFIN == source) {
            return SearchIndexUtil.tagSourceId(SearchIndexUtil.SOURCE_JELLYFIN, value)
        }
        return value
    }

    fun getAlbumTracks(id: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        val matchId = normalizeExternalId(id)
        libraryRepository.loadSongs { songs ->
            val filtered = songs.filter { it.albumId?.externalId == matchId }
            val mediaItems = songsToMediaItems(filtered)
            if (mediaItems.isNotEmpty()) {
                AutoLog.event("auto_cache_hit", mapOf("section" to "album_tracks", "count" to mediaItems.size))
                future.set(libraryResult(mediaItems, mediaItems.size))
            } else {
                AutoLog.warn("auto_cache_miss", mapOf("section" to "album_tracks"))
                fallbackToNetwork(future, "album_tracks", networkCall = {
                    automotiveRepository.getAlbumTracks(id)
                })
            }
        }
        return future
    }

    fun getArtistAlbum(prefix: String, id: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        val matchId = normalizeExternalId(id)
        libraryRepository.loadAlbums { albums ->
            val filtered = albums.filter { it.artistId?.externalId == matchId }
            val items = filtered.map { albumToMediaItem(prefix, it) }
            if (items.isNotEmpty()) {
                AutoLog.event("auto_cache_hit", mapOf("section" to "artist_albums", "count" to items.size))
                future.set(libraryResult(items, items.size))
            } else {
                AutoLog.warn("auto_cache_miss", mapOf("section" to "artist_albums"))
                fallbackToNetwork(future, "artist_albums", networkCall = {
                    automotiveRepository.getArtistAlbum(prefix, id)
                })
            }
        }
        return future
    }

    fun getPlaylistSongs(id: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        AutoLog.warn("auto_cache_miss", mapOf("section" to "playlist_songs"))
        fallbackToNetwork(future, "playlist_songs", networkCall = {
            automotiveRepository.getPlaylistSongs(id)
        })
        return future
    }

    fun getMusicFolders(prefix: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return automotiveRepository.getMusicFolders(prefix)
    }

    fun getIndexes(prefix: String, id: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return automotiveRepository.getIndexes(prefix, id)
    }

    fun getDirectories(prefix: String, id: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return automotiveRepository.getDirectories(prefix, id)
    }

    fun getNewestPodcastEpisodes(count: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return automotiveRepository.getNewestPodcastEpisodes(count)
    }

    val internetRadioStations: ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>
        get() = automotiveRepository.internetRadioStations

    fun search(
        query: String,
        albumPrefix: String,
        artistPrefix: String,
        playlistPrefix: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        if (query.isBlank()) {
            future.set(LibraryResult.ofItemList(ImmutableList.of(), null))
            return future
        }

        val searchRef = AtomicReference<one.chandan.rubato.subsonic.models.SearchResult3?>()
        val playlistRef = AtomicReference<List<Playlist>?>()
        val emitted = AtomicBoolean(false)
        lateinit var searchObserver: androidx.lifecycle.Observer<one.chandan.rubato.subsonic.models.SearchResult3>
        lateinit var playlistObserver: androidx.lifecycle.Observer<List<Playlist>>

        val searchLive = libraryRepository.searchLegacy(query)
        val playlistsLive = libraryRepository.searchPlaylistsLegacy(query)

        fun emitIfReady() {
            if (emitted.get()) return
            val searchResult = searchRef.get()
            val playlists = playlistRef.get()
            if (searchResult == null || playlists == null) return

            emitted.set(true)
            searchLive.removeObserver(searchObserver)
            playlistsLive.removeObserver(playlistObserver)

            val mediaItems = LinkedHashMap<String, MediaItem>()
            val songChildren = mutableListOf<one.chandan.rubato.subsonic.models.Child>()

            searchResult.artists?.forEach { artist ->
                val id = artist?.id ?: return@forEach
                val item = MediaItem.Builder()
                    .setMediaId(artistPrefix + id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(artist.name)
                            .setArtist(artist.name)
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                            .setArtworkUri(buildArtworkUri(artist.coverArtId))
                            .build()
                    )
                    .build()
                mediaItems[item.mediaId] = item
            }

            searchResult.albums?.forEach { album ->
                val id = album?.id ?: return@forEach
                val item = MediaItem.Builder()
                    .setMediaId(albumPrefix + id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(album.name)
                            .setAlbumTitle(album.name)
                            .setArtist(album.artist)
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                            .setArtworkUri(buildArtworkUri(album.coverArtId))
                            .build()
                    )
                    .build()
                mediaItems[item.mediaId] = item
            }

            playlists.forEach { playlist ->
                val item = playlistToMediaItem(playlistPrefix, playlist)
                mediaItems[item.mediaId] = item
            }

            searchResult.songs?.forEach { song ->
                if (song == null || song.id == null) return@forEach
                songChildren.add(song)
                val item = MappingUtil.mapMediaItem(song)
                mediaItems[item.mediaId] = item
            }

            if (songChildren.isNotEmpty()) {
                storeSessionItemsFromChildren(songChildren)
            }

            val results = mediaItems.values.toList()
            if (results.isNotEmpty()) {
                AutoLog.event("auto_cache_hit", mapOf("section" to "search", "count" to results.size))
                future.set(libraryResult(results, results.size))
            } else {
                AutoLog.warn("auto_cache_miss", mapOf("section" to "search"))
                fallbackToNetwork(future, "search", networkCall = {
                    automotiveRepository.search(query, albumPrefix, artistPrefix)
                })
            }
        }

        searchObserver = androidx.lifecycle.Observer { result ->
            searchRef.set(result)
            emitIfReady()
        }
        playlistObserver = androidx.lifecycle.Observer { result ->
            playlistRef.set(result ?: emptyList())
            emitIfReady()
        }

        searchLive.observeForever(searchObserver)
        playlistsLive.observeForever(playlistObserver)
        return future
    }

    fun resolveMediaItems(mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
        val future = SettableFuture.create<List<MediaItem>>()
        runOnExecutor {
            try {
                val shuffleRequest = mediaItems.any { it.mediaId == AUTO_SHUFFLE_ALL_ID }
                if (shuffleRequest) {
                    startShuffleAllInternal(SHUFFLE_INITIAL_COUNT) { items ->
                        future.set(items)
                    }
                    return@runOnExecutor
                }
                val madeForYouSeed = mediaItems.firstOrNull {
                    it.mediaId.startsWith(AUTO_MADE_FOR_YOU_MIX_PREFIX)
                }?.mediaId?.removePrefix(AUTO_MADE_FOR_YOU_MIX_PREFIX)?.takeIf { it.isNotBlank() }
                val madeForYouRequest = madeForYouSeed != null || mediaItems.any { it.mediaId == AUTO_MADE_FOR_YOU_MIX_ID }
                if (madeForYouRequest) {
                    startMadeForYouMixInternal(madeForYouSeed) { items ->
                        future.set(items)
                    }
                    return@runOnExecutor
                }
                if (Preferences.isAutoShuffleActive()) {
                    clearShuffleState()
                }
                val resolved = mutableListOf<MediaItem>()
                for (item in mediaItems) {
                    if (item.localConfiguration?.uri != null) {
                        resolved.add(wrapAutoArtwork(item))
                        continue
                    }
                    val sessionMediaItem = sessionMediaItemDao.get(item.mediaId)
                    if (sessionMediaItem != null) {
                        val timestamp = sessionMediaItem.timestamp
                        if (timestamp == null) {
                            resolved.add(wrapAutoArtwork(sessionMediaItem.getMediaItem()))
                            continue
                        }
                        val items = sessionMediaItemDao.get(timestamp).map { it.getMediaItem() }
                        val index = items.indexOfFirst { it.mediaId == item.mediaId }
                        if (index >= 0) {
                            resolved.addAll(items.subList(index, items.size).map { wrapAutoArtwork(it) })
                        } else {
                            resolved.add(wrapAutoArtwork(sessionMediaItem.getMediaItem()))
                        }
                    } else {
                        resolved.add(wrapAutoArtwork(item))
                    }
                }
                future.set(resolved)
            } catch (ex: Exception) {
                AutoLog.error("auto_queue_resolve_failed", throwable = ex)
                future.set(mediaItems)
            }
        }
        return future
    }

    fun onShuffleAdvance(
        mediaItem: MediaItem?,
        incrementConsumed: Boolean,
        remainingQueue: Int,
        callback: (List<MediaItem>) -> Unit
    ) {
        if (mediaItem == null || !Preferences.isAutoShuffleActive()) {
            callback(emptyList())
            return
        }
        runOnExecutor {
            loadShuffleStateIfNeeded { state ->
                if (state == null) {
                    callback(emptyList())
                    return@loadShuffleStateIfNeeded
                }
                updateRecentHistory(state, mediaItem.mediaId)
                if (incrementConsumed) {
                    state.consumedSinceAppend += 1
                }
                persistShuffleState(state)
                if (state.consumedSinceAppend >= SHUFFLE_APPEND_THRESHOLD || remainingQueue <= SHUFFLE_APPEND_COUNT) {
                    val next = nextShuffleBatch(state, SHUFFLE_APPEND_COUNT)
                    state.consumedSinceAppend = 0
                    persistShuffleState(state)
                    callback(next)
                } else {
                    callback(emptyList())
                }
            }
        }
    }

    private fun startShuffleAllInternal(initialCount: Int, callback: (List<MediaItem>) -> Unit) {
        searchIndexRepository.getAllSongs { entries ->
            runOnExecutor {
                val state = ensureShuffleState(entries, forceNew = true)
                if (state == null) {
                    callback(emptyList())
                    return@runOnExecutor
                }
                val batch = nextShuffleBatch(state, initialCount)
                persistShuffleState(state)
                callback(batch)
            }
        }
    }

    private fun startMadeForYouMixInternal(seedLegacyId: String?, callback: (List<MediaItem>) -> Unit) {
        libraryRepository.loadSongs { songs ->
            runOnExecutor {
                if (songs.isNotEmpty() && hasRemoteSongs(songs)) {
                    val recent = loadRecentChronology()
                    val seed = if (!seedLegacyId.isNullOrBlank()) {
                        songs.firstOrNull { legacyId(it.id) == seedLegacyId } ?: pickMadeForYouSeed(songs, recent)
                    } else {
                        pickMadeForYouSeed(songs, recent)
                    }
                    if (seed == null) {
                        callback(emptyList())
                        return@runOnExecutor
                    }
                    val mix = buildMadeForYouMix(seed, songs, 40)
                    callback(songsToMediaItems(mix))
                } else {
                    startMadeForYouMixFromSearchIndex(seedLegacyId, callback)
                }
            }
        }
    }

    private fun buildMadeForYouTilesFromSearchIndex(
        future: SettableFuture<LibraryResult<ImmutableList<MediaItem>>>
    ) {
        searchIndexRepository.getAllSongs { entries ->
            val safeEntries = entries ?: emptyList()
            if (safeEntries.isEmpty()) {
                future.set(LibraryResult.ofItemList(ImmutableList.of(), null))
                return@getAllSongs
            }
            runOnExecutor {
                val recent = loadRecentChronology()
                val seeds = selectMadeForYouSeedsFromSearchIndex(safeEntries, recent, MADE_FOR_YOU_TILE_LIMIT)
                val items = seeds.map { entry ->
                    val title = "Mix from ${entry.title ?: ""}".trim()
                    val baseArtwork = buildArtworkUri(entry.coverArt)
                    val artworkUri = AutoArtworkProvider.buildMixUri(baseArtwork) ?: baseArtwork ?: drawableUri("ic_auto_song")
                    val seedId = entryId(entry)
                    MediaItem.Builder()
                        .setMediaId(AUTO_MADE_FOR_YOU_MIX_PREFIX + seedId)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(title.ifBlank { "Mix" })
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                .setArtworkUri(artworkUri)
                                .build()
                        )
                        .build()
                }
                AutoLog.event(
                    "auto_made_for_you_tiles",
                    mapOf(
                        "count" to items.size,
                        "source" to "search_index"
                    )
                )
                future.set(libraryResult(items, items.size))
            }
        }
    }

    private fun startMadeForYouMixFromSearchIndex(
        seedLegacyId: String?,
        callback: (List<MediaItem>) -> Unit
    ) {
        searchIndexRepository.getAllSongs { entries ->
            val safeEntries = entries ?: emptyList()
            if (safeEntries.isEmpty()) {
                callback(emptyList())
                return@getAllSongs
            }
            runOnExecutor {
                val recent = loadRecentChronology()
                val seed = if (!seedLegacyId.isNullOrBlank()) {
                    safeEntries.firstOrNull { entryId(it) == seedLegacyId || it.itemId == seedLegacyId }
                        ?: pickMadeForYouSeedFromSearchIndex(safeEntries, recent)
                } else {
                    pickMadeForYouSeedFromSearchIndex(safeEntries, recent)
                }
                if (seed == null) {
                    callback(emptyList())
                    return@runOnExecutor
                }
                val mix = buildMadeForYouMixFromSearchIndex(seed, safeEntries, 40)
                val children = mix.mapNotNull { toChild(it) }
                if (children.isNotEmpty()) {
                    storeSessionItemsFromChildren(children)
                }
                callback(MappingUtil.mapMediaItems(children))
            }
        }
    }

    private fun pickMadeForYouSeedFromSearchIndex(
        entries: List<SearchSongLite>,
        recent: List<Chronology>
    ): SearchSongLite? {
        if (entries.isEmpty()) return null
        val recentIds = recent.map { it.id }
        for (id in recentIds) {
            entries.firstOrNull { entryId(it) == id || it.itemId == id }?.let { return it }
        }
        return entries.random()
    }

    private fun selectMadeForYouSeedsFromSearchIndex(
        entries: List<SearchSongLite>,
        recent: List<Chronology>,
        limit: Int
    ): List<SearchSongLite> {
        if (entries.isEmpty() || limit <= 0) return emptyList()
        val seeds = ArrayList<SearchSongLite>(limit)
        val used = LinkedHashSet<String>()
        val recentIds = recent.map { it.id }
        for (id in recentIds) {
            val match = entries.firstOrNull { entryId(it) == id || it.itemId == id } ?: continue
            val key = entryKey(match)
            if (used.add(key)) {
                seeds.add(match)
            }
            if (seeds.size >= limit) return seeds
        }
        val remoteSources = entries.map { it.source }.filter { it != SearchIndexUtil.SOURCE_LOCAL }.distinct()
        for (source in remoteSources) {
            if (seeds.size >= limit) break
            val candidate = entries.firstOrNull { it.source == source && used.add(entryKey(it)) }
            if (candidate != null) seeds.add(candidate)
        }
        val shuffled = entries.shuffled()
        for (entry in shuffled) {
            if (seeds.size >= limit) break
            if (used.add(entryKey(entry))) {
                seeds.add(entry)
            }
        }
        return seeds
    }

    private fun buildMadeForYouMixFromSearchIndex(
        seed: SearchSongLite,
        entries: List<SearchSongLite>,
        count: Int
    ): List<SearchSongLite> {
        if (entries.isEmpty()) return emptyList()
        val seedArtistId = seed.artistId?.takeIf { it.isNotBlank() }
        val seedArtistName = seed.artist
        val filtered = entries.filter {
            when {
                seedArtistId != null && seedArtistId == it.artistId -> true
                !seedArtistName.isNullOrBlank() && seedArtistName.equals(it.artist, ignoreCase = true) -> true
                else -> false
            }
        }
        val pool = if (filtered.isNotEmpty()) filtered else entries
        val shuffled = pool.shuffled()
        val mix = ArrayList<SearchSongLite>(count)
        mix.add(seed)
        for (entry in shuffled) {
            if (mix.size >= count) break
            if (entry == seed) continue
            mix.add(entry)
        }
        return mix
    }

    private fun entryId(entry: SearchSongLite): String {
        return tagId(entry.source, entry.itemId) ?: entry.itemId ?: entry.uid
    }

    private fun entryKey(entry: SearchSongLite): String {
        val title = entry.title?.lowercase() ?: ""
        val artist = entry.artist?.lowercase() ?: ""
        return "${entryId(entry)}|$title|$artist"
    }

    private fun ensureShuffleState(entries: List<SearchSongLite>?, forceNew: Boolean): AutoShuffleState? {
        val safeEntries = entries ?: emptyList()
        if (safeEntries.isEmpty()) return null
        synchronized(shuffleLock) {
            if (!forceNew) {
                val existing = shuffleState
                if (existing != null && existing.active && shuffledEntries.isNotEmpty()) {
                    return existing
                }
                val loaded = loadShuffleStateLocked(safeEntries)
                if (loaded != null) {
                    return loaded
                }
            }
            return createShuffleStateLocked(safeEntries)
        }
    }

    private fun loadShuffleStateIfNeeded(callback: (AutoShuffleState?) -> Unit) {
        synchronized(shuffleLock) {
            val existing = shuffleState
            if (existing != null && existing.active && shuffledEntries.isNotEmpty()) {
                callback(existing)
                return
            }
        }
        searchIndexRepository.getAllSongs { entries ->
            runOnExecutor {
                callback(ensureShuffleState(entries, forceNew = false))
            }
        }
    }

    private fun loadShuffleStateLocked(entries: List<SearchSongLite>): AutoShuffleState? {
        if (!Preferences.isAutoShuffleActive()) return null
        val seed = Preferences.getAutoShuffleSeed()
        if (seed == 0L) return null
        val recent = ArrayDeque<String>(Preferences.getAutoShuffleRecentIds())
        val sessionId = Preferences.getAutoShuffleSessionId().takeIf { it != 0L } ?: seed
        val state = AutoShuffleState(
            seed = seed,
            nextIndex = Preferences.getAutoShuffleIndex(),
            consumedSinceAppend = Preferences.getAutoShuffleConsumed(),
            recentlyPlayed = recent,
            sessionId = sessionId,
            active = true
        )
        applyShuffleEntries(entries, seed)
        shuffleState = state
        return state
    }

    private fun createShuffleStateLocked(entries: List<SearchSongLite>): AutoShuffleState {
        val seed = System.currentTimeMillis()
        val state = AutoShuffleState(
            seed = seed,
            nextIndex = 0,
            consumedSinceAppend = 0,
            recentlyPlayed = ArrayDeque(),
            sessionId = seed,
            active = true
        )
        applyShuffleEntries(entries, seed)
        shuffleState = state
        persistShuffleState(state)
        return state
    }

    private fun applyShuffleEntries(entries: List<SearchSongLite>, seed: Long) {
        val shuffled = entries.toMutableList()
        shuffled.shuffle(Random(seed))
        shuffleEntries = entries
        shuffledEntries = shuffled
    }

    private fun nextShuffleBatch(state: AutoShuffleState, count: Int): List<MediaItem> {
        if (count <= 0) return emptyList()
        val entries = shuffledEntries
        if (entries.isEmpty()) return emptyList()
        val children = ArrayList<one.chandan.rubato.subsonic.models.Child>()
        var index = state.nextIndex
        while (index < entries.size && children.size < count) {
            val child = toChild(entries[index])
            index++
            if (child != null) {
                children.add(child)
            }
        }
        state.nextIndex = index
        if (state.nextIndex >= entries.size && children.size < count) {
            val newSeed = System.currentTimeMillis()
            state.seed = newSeed
            state.nextIndex = 0
            applyShuffleEntries(shuffleEntries, newSeed)
            val extra = nextShuffleBatch(state, count - children.size)
            val merged = ArrayList<MediaItem>(children.size + extra.size)
            if (children.isNotEmpty()) {
                storeSessionItemsFromChildren(children)
                merged.addAll(MappingUtil.mapMediaItems(children))
            }
            merged.addAll(extra)
            return merged
        }
        if (children.isNotEmpty()) {
            storeSessionItemsFromChildren(children)
        }
        return MappingUtil.mapMediaItems(children)
    }

    private fun updateRecentHistory(state: AutoShuffleState, mediaId: String?) {
        val id = mediaId?.takeIf { it.isNotBlank() } ?: return
        state.recentlyPlayed.remove(id)
        state.recentlyPlayed.addFirst(id)
        while (state.recentlyPlayed.size > RECENT_HISTORY_LIMIT) {
            state.recentlyPlayed.removeLast()
        }
    }

    private fun persistShuffleState(state: AutoShuffleState) {
        Preferences.setAutoShuffleActive(state.active)
        Preferences.setAutoShuffleSeed(state.seed)
        Preferences.setAutoShuffleIndex(state.nextIndex)
        Preferences.setAutoShuffleConsumed(state.consumedSinceAppend)
        Preferences.setAutoShuffleSessionId(state.sessionId)
        Preferences.setAutoShuffleRecentIds(state.recentlyPlayed.toList())
    }

    private fun clearShuffleState() {
        synchronized(shuffleLock) {
            shuffleState = null
            shuffleEntries = emptyList()
            shuffledEntries = emptyList()
        }
        Preferences.setAutoShuffleActive(false)
        Preferences.setAutoShuffleSeed(0L)
        Preferences.setAutoShuffleIndex(0)
        Preferences.setAutoShuffleConsumed(0)
        Preferences.setAutoShuffleSessionId(0L)
        Preferences.setAutoShuffleRecentIds(emptyList())
    }

    fun clearSessionCache() {
        runOnExecutor { sessionMediaItemDao.deleteAll() }
    }

    fun shutdown() {
        closed.set(true)
        executor.shutdownNow()
    }

    private fun runOnExecutor(action: () -> Unit) {
        if (closed.get()) return
        try {
            executor.execute {
                if (!closed.get()) {
                    action()
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    fun logHealthSnapshot(reason: String = "startup") {
        val remaining = AtomicInteger(5)
        var artistsCount = 0
        var albumsCount = 0
        var songsCount = 0
        var playlistsCount = 0
        var searchIndexCount = 0

        fun done() {
            if (remaining.decrementAndGet() != 0) return
            AutoLog.event(
                "auto_health",
                mapOf(
                    "reason" to reason,
                    "offline" to NetworkUtil.isOffline(),
                    "artists" to artistsCount,
                    "albums" to albumsCount,
                    "songs" to songsCount,
                    "playlists" to playlistsCount,
                    "search_index" to searchIndexCount,
                    "metadata_sync_active" to Preferences.isMetadataSyncActive(),
                    "metadata_sync_last" to Preferences.getMetadataSyncLast()
                )
            )
        }

        libraryRepository.loadArtists { artists ->
            artistsCount = artists.size
            done()
        }
        libraryRepository.loadAlbums { albums ->
            albumsCount = albums.size
            done()
        }
        libraryRepository.loadSongs { songs ->
            songsCount = songs.size
            done()
        }
        libraryRepository.loadPlaylists { playlists ->
            playlistsCount = playlists.size
            done()
        }
        searchIndexRepository.count { count ->
            searchIndexCount = count ?: 0
            done()
        }
    }

    private fun libraryResult(items: List<MediaItem>, limit: Int): LibraryResult<ImmutableList<MediaItem>> {
        if (items.isEmpty()) {
            return LibraryResult.ofItemList(ImmutableList.of(), null)
        }
        val trimmed = if (limit > 0) items.subList(0, min(limit, items.size)) else items
        return LibraryResult.ofItemList(ImmutableList.copyOf(trimmed), null)
    }

    private fun fallbackToNetwork(
        future: SettableFuture<LibraryResult<ImmutableList<MediaItem>>>,
        section: String,
        networkCall: (() -> ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>)?
    ) {
        if (NetworkUtil.isOffline() || networkCall == null) {
            future.set(LibraryResult.ofItemList(ImmutableList.of(), null))
            return
        }
        val networkFuture = networkCall.invoke()
        Futures.addCallback(
            networkFuture,
            object : FutureCallback<LibraryResult<ImmutableList<MediaItem>>> {
                override fun onSuccess(result: LibraryResult<ImmutableList<MediaItem>>?) {
                    if (result == null || result.value == null) {
                        AutoLog.warn("auto_network_empty", mapOf("section" to section))
                        future.set(LibraryResult.ofItemList(ImmutableList.of(), null))
                    } else {
                        future.set(result)
                    }
                }

                override fun onFailure(t: Throwable) {
                    AutoLog.warn("auto_network_failed", mapOf("section" to section), t)
                    future.set(LibraryResult.ofItemList(ImmutableList.of(), null))
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun albumToMediaItem(prefix: String, album: MediaAlbum): MediaItem {
        val artworkUri = buildArtworkUri(album.coverArt)
        val mediaId = prefix + legacyId(album.id)
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(album.title)
                    .setAlbumTitle(album.title)
                    .setArtist(album.artist)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .setUri("")
            .build()
    }

    private fun artistToMediaItem(prefix: String, artist: MediaArtist): MediaItem {
        val fallbackIcon = AutoArtworkProvider.buildCoverUri(drawableUri("artist_24")) ?: drawableUri("artist_24")
        val artworkUri = buildArtworkUri(artist.coverArt)
        val resolvedArtwork = artworkUri ?: fallbackIcon
        val mediaId = prefix + legacyId(artist.id)
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(artist.name)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
            .setArtworkUri(resolvedArtwork)
        if (artworkUri == null) {
            val bytes = iconBytes("artist_24")
            if (bytes != null && bytes.isNotEmpty()) {
                metadataBuilder.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        }
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadataBuilder.build())
            .setUri("")
            .build()
    }

    private fun playlistToMediaItem(prefix: String, playlist: MediaPlaylist): MediaItem {
        val artworkUri = buildArtworkUri(playlist.coverArt)
        val mediaId = prefix + legacyId(playlist.id)
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(playlist.name)
                    .setArtist(playlist.owner)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .setUri("")
            .build()
    }

    private fun playlistToMediaItem(prefix: String, playlist: Playlist): MediaItem {
        val artworkUri = buildArtworkUri(playlist.coverArtId)
        val mediaId = prefix + playlist.id
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(playlist.name)
                    .setArtist(playlist.owner)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .setUri("")
            .build()
    }

    private fun songsToMediaItems(songs: List<MediaSong>): List<MediaItem> {
        if (songs.isEmpty()) return emptyList()
        val children = songs.mapNotNull { LegacyMediaMapper.toSong(it) }
        if (children.isEmpty()) return emptyList()
        storeSessionItemsFromChildren(children)
        return MappingUtil.mapMediaItems(children)
    }

    private fun songToMediaItem(song: MediaSong): MediaItem? {
        val child = LegacyMediaMapper.toSong(song) ?: return null
        storeSessionItemsFromChildren(listOf(child))
        return MappingUtil.mapMediaItem(child)
    }

    private fun storeSessionItemsFromChronology(children: List<Chronology>) {
        if (children.isEmpty()) return
        val timestamp = System.currentTimeMillis()
        val sessionItems = children.map {
            SessionMediaItem(it).apply { this.timestamp = timestamp }
        }
        runOnExecutor { sessionMediaItemDao.insertAll(sessionItems) }
    }

    private fun storeSessionItemsFromChildren(children: List<one.chandan.rubato.subsonic.models.Child>) {
        if (children.isEmpty()) return
        val timestamp = System.currentTimeMillis()
        val sessionItems = children.map {
            SessionMediaItem(it).apply { this.timestamp = timestamp }
        }
        runOnExecutor { sessionMediaItemDao.insertAll(sessionItems) }
    }

    private fun buildArtworkUri(image: MediaImage?): Uri? {
        if (image == null) return null
        val uri = image.uri
        if (!uri.isNullOrBlank()) {
            return AutoArtworkProvider.buildCoverUri(Uri.parse(uri), Preferences.getImageSize())
        }
        val id = image.id ?: return null
        val legacy = legacyId(id)
        val url = CustomGlideRequest.createUrl(legacy, Preferences.getImageSize())
        return url?.let { AutoArtworkProvider.buildCoverUri(Uri.parse(it), Preferences.getImageSize()) }
    }

    private fun buildArtworkUri(coverArtId: String?): Uri? {
        if (coverArtId.isNullOrBlank()) return null
        val url = CustomGlideRequest.createUrl(coverArtId, Preferences.getImageSize())
        return url?.let { AutoArtworkProvider.buildCoverUri(Uri.parse(it), Preferences.getImageSize()) }
    }

    private fun normalizeExternalId(id: String): String {
        val raw = JellyfinTagUtil.toRaw(id) ?: id
        val split = raw.indexOf(':')
        return if (split >= 0 && split < raw.length - 1) raw.substring(split + 1) else raw
    }

    private fun legacyId(id: MediaId): String {
        return when (id.sourceType) {
            MediaSourceType.SUBSONIC,
            MediaSourceType.LOCAL,
            MediaSourceType.OTHER -> id.externalId
            MediaSourceType.JELLYFIN -> {
                val raw = "${id.sourceId}:${id.externalId}"
                SearchIndexUtil.tagSourceId(SearchIndexUtil.SOURCE_JELLYFIN, raw)
            }
        }
    }
}
