package one.chandan.rubato.domain

data class MediaAlbum(
    val id: MediaId,
    val title: String,
    val artist: String? = null,
    val artistId: MediaId? = null,
    val year: Int? = null,
    val trackCount: Int? = null,
    val coverArt: MediaImage? = null,
    val source: MediaSourceRef? = null,
    val sourceItem: Any? = null
) {
    fun detailScore(): Int {
        return MediaDetail.score(
            title,
            artist,
            artistId?.externalId,
            year ?: 0,
            trackCount ?: 0,
            coverArt?.id?.externalId,
            coverArt?.uri
        )
    }
}
