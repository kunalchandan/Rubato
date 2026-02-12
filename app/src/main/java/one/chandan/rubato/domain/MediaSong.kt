package one.chandan.rubato.domain

data class MediaSong(
    val id: MediaId,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val artistId: MediaId? = null,
    val albumId: MediaId? = null,
    val durationMs: Long? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val coverArt: MediaImage? = null,
    val source: MediaSourceRef? = null,
    val sourceItem: Any? = null
) {
    fun detailScore(): Int {
        return MediaDetail.score(
            title,
            artist,
            album,
            artistId?.externalId,
            albumId?.externalId,
            durationMs ?: 0L,
            trackNumber ?: 0,
            discNumber ?: 0,
            year ?: 0,
            coverArt?.id?.externalId,
            coverArt?.uri
        )
    }
}
