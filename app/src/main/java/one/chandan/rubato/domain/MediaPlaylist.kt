package one.chandan.rubato.domain

data class MediaPlaylist(
    val id: MediaId,
    val name: String,
    val owner: String? = null,
    val songCount: Int? = null,
    val durationMs: Long? = null,
    val coverArt: MediaImage? = null,
    val source: MediaSourceRef? = null,
    val sourceItem: Any? = null
) {
    fun detailScore(): Int {
        return MediaDetail.score(
            name,
            owner,
            songCount ?: 0,
            durationMs ?: 0L,
            coverArt?.id?.externalId,
            coverArt?.uri
        )
    }
}
