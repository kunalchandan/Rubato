package one.chandan.rubato.domain

data class MediaArtist(
    val id: MediaId,
    val name: String,
    val sortName: String? = null,
    val albumCount: Int? = null,
    val songCount: Int? = null,
    val coverArt: MediaImage? = null,
    val source: MediaSourceRef? = null,
    val sourceItem: Any? = null
) {
    fun detailScore(): Int {
        return MediaDetail.score(
            name,
            sortName,
            albumCount ?: 0,
            songCount ?: 0,
            coverArt?.id?.externalId,
            coverArt?.uri
        )
    }
}
