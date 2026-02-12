package one.chandan.rubato.domain

data class MediaSourceRef(
    val id: String,
    val type: MediaSourceType,
    val displayName: String? = null
)
