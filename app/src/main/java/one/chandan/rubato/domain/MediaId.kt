package one.chandan.rubato.domain

data class MediaId(
    val sourceType: MediaSourceType,
    val sourceId: String,
    val externalId: String
) {
    fun asCanonicalId(): String {
        return "${sourceType.id}:$sourceId:$externalId"
    }
}
