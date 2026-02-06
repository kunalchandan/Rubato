package one.chandan.rubato.service

import android.net.Uri
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.appcompat.content.res.AppCompatResources
import one.chandan.rubato.App
import one.chandan.rubato.provider.AutoArtworkProvider
import one.chandan.rubato.repository.AutoLibraryRepository
import one.chandan.rubato.util.AutoLog
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture

object MediaBrowserTree {

    private lateinit var autoLibraryRepository: AutoLibraryRepository

    private var treeNodes: MutableMap<String, MediaItemNode> = mutableMapOf()

    private var isInitialized = false

    private const val CONTENT_STYLE_SUPPORTED_KEY = "android.media.browse.CONTENT_STYLE_SUPPORTED"

    // Root
    private const val ROOT_ID = "[rootID]"

    // Root level items
    private const val RECENTLY_PLAYED_ID = "[recentlyPlayedID]"
    private const val GENRES_ID = "[genresID]"
    private const val ARTISTS_ID = "[artistsID]"
    private const val ALBUMS_ID = "[albumsID]"
    private const val SONGS_ID = "[songsID]"
    private const val PLAYLIST_ID = "[playlistID]"

    private const val GENRE_ID = "[genreID]"
    private const val ALBUM_ID = "[albumID]"
    private const val ARTIST_ID = "[artistID]"

    private class MediaItemNode(val item: MediaItem) {
        private val children: MutableList<MediaItem> = ArrayList()

        fun addChild(childID: String) {
            this.children.add(treeNodes[childID]!!.item)
        }

        fun getChildren(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val listenableFuture = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            val libraryResult = LibraryResult.ofItemList(children, null)

            listenableFuture.set(libraryResult)

            return listenableFuture
        }
    }

    private fun buildMediaItem(
        title: String,
        mediaId: String,
        isPlayable: Boolean,
        isBrowsable: Boolean,
        mediaType: @MediaMetadata.MediaType Int,
        subtitleConfigurations: List<SubtitleConfiguration> = mutableListOf(),
        album: String? = null,
        artist: String? = null,
        genre: String? = null,
        sourceUri: Uri? = null,
        imageUri: Uri? = null,
        artworkData: ByteArray? = null,
        extras: Bundle? = null
    ): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setAlbumTitle(album)
            .setTitle(title)
            .setArtist(artist)
            .setGenre(genre)
            .setIsBrowsable(isBrowsable)
            .setIsPlayable(isPlayable)
            .setArtworkUri(imageUri)
            .setMediaType(mediaType)
            .setExtras(extras)
        if (artworkData != null && artworkData.isNotEmpty()) {
            metadataBuilder.setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        val metadata = metadataBuilder.build()

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setSubtitleConfigurations(subtitleConfigurations)
            .setMediaMetadata(metadata)
            .setUri(sourceUri)
            .build()
    }

    private fun drawableUri(name: String): Uri? {
        val pkg = App.getContext().packageName
        return Uri.parse("android.resource://$pkg/drawable/$name")
    }

    private fun iconUri(name: String): Uri? {
        val drawable = drawableUri(name) ?: return null
        return AutoArtworkProvider.buildCoverUri(drawable) ?: drawable
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
        val out = java.io.ByteArrayOutputStream()
        return if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            out.toByteArray()
        } else {
            null
        }
    }

    private fun contentStyleExtras(
        browsableStyle: Int? = null,
        playableStyle: Int? = null
    ): Bundle {
        val extras = Bundle()
        extras.putInt(
            CONTENT_STYLE_SUPPORTED_KEY,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM or
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
        )
        if (browsableStyle != null) {
            extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, browsableStyle)
        }
        if (playableStyle != null) {
            extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, playableStyle)
        }
        return extras
    }

    fun initialize(autoLibraryRepository: AutoLibraryRepository) {
        this.autoLibraryRepository = autoLibraryRepository

        if (isInitialized) return

        isInitialized = true

        // Root level

        treeNodes[ROOT_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = "Root Folder",
                    mediaId = ROOT_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        // Root level items

        treeNodes[RECENTLY_PLAYED_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = "Recent Songs",
                    mediaId = RECENTLY_PLAYED_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                    extras = contentStyleExtras(
                        browsableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    ),
                    imageUri = iconUri("music_history_24"),
                    artworkData = iconBytes("music_history_24")
                )
            )

        treeNodes[GENRES_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = "Genres",
                    mediaId = GENRES_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                    extras = contentStyleExtras(
                        browsableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                    ),
                    imageUri = iconUri("genres_24"),
                    artworkData = iconBytes("genres_24")
                )
            )

        treeNodes[ARTISTS_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = "Artists",
                    mediaId = ARTISTS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
                    extras = contentStyleExtras(
                        browsableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    ),
                    imageUri = iconUri("artist_24"),
                    artworkData = iconBytes("artist_24")
                )
            )

        treeNodes[ALBUMS_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = "Albums",
                    mediaId = ALBUMS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
                    extras = contentStyleExtras(
                        browsableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    ),
                    imageUri = iconUri("ic_auto_album"),
                    artworkData = iconBytes("ic_auto_album")
                )
            )

        treeNodes[SONGS_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = "Songs",
                    mediaId = SONGS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                    extras = contentStyleExtras(
                        browsableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                    ),
                    imageUri = iconUri("ic_auto_song"),
                    artworkData = iconBytes("ic_auto_song")
                )
            )

        treeNodes[ROOT_ID]!!.addChild(RECENTLY_PLAYED_ID)
        treeNodes[ROOT_ID]!!.addChild(GENRES_ID)
        treeNodes[ROOT_ID]!!.addChild(ALBUMS_ID)
        treeNodes[ROOT_ID]!!.addChild(ARTISTS_ID)
        treeNodes[ROOT_ID]!!.addChild(SONGS_ID)
    }

    fun getRootItem(): MediaItem {
        return treeNodes[ROOT_ID]!!.item
    }

    fun getChildren(
        id: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = when (id) {
            ROOT_ID -> treeNodes[ROOT_ID]?.getChildren()!!
            RECENTLY_PLAYED_ID -> autoLibraryRepository.getRecentlyPlayedSongs(null, 50)
            GENRES_ID -> autoLibraryRepository.getLibraryGenres(GENRE_ID, 200)
            ARTISTS_ID -> autoLibraryRepository.getLibraryArtists(ARTIST_ID, 200)
            ALBUMS_ID -> autoLibraryRepository.getLibraryAlbums(ALBUM_ID, 200)
            SONGS_ID -> autoLibraryRepository.getLibrarySongs(200)

            else -> {
                if (id.startsWith(PLAYLIST_ID)) {
                    return autoLibraryRepository.getPlaylistSongs(
                        id.removePrefix(
                            PLAYLIST_ID
                        )
                    )
                }

                if (id.startsWith(GENRE_ID)) {
                    return autoLibraryRepository.getGenreSongs(
                        id.removePrefix(
                            GENRE_ID
                        )
                    )
                }

                if (id.startsWith(ALBUM_ID)) {
                    return autoLibraryRepository.getAlbumTracks(
                        id.removePrefix(
                            ALBUM_ID
                        )
                    )
                }

                if (id.startsWith(ARTIST_ID)) {
                    return autoLibraryRepository.getArtistAlbum(
                        ALBUM_ID,
                        id.removePrefix(
                            ARTIST_ID
                        )
                    )
                }

                return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
        }
        return logChildren(id, future)
    }

    private fun logChildren(
        id: String,
        future: ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return Futures.transform(
            future,
            { result ->
                val count = result?.value?.size ?: 0
                AutoLog.event("auto_children", mapOf("id" to id, "count" to count))
                result
            },
            MoreExecutors.directExecutor()
        )
    }

    fun resolveMediaItems(mediaItems: List<MediaItem>) =
        autoLibraryRepository.resolveMediaItems(mediaItems)

    fun search(query: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return autoLibraryRepository.search(
            query = query,
            albumPrefix = ALBUM_ID,
            artistPrefix = ARTIST_ID,
            playlistPrefix = PLAYLIST_ID
        )
    }
}
