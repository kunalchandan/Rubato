package one.chandan.rubato.domain

enum class MediaSourceType(val id: String) {
    SUBSONIC("subsonic"),
    JELLYFIN("jellyfin"),
    LOCAL("local"),
    OTHER("other")
}
