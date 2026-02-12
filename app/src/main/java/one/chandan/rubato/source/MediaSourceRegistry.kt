package one.chandan.rubato.source

import java.util.LinkedHashMap
import one.chandan.rubato.domain.MediaSourceRef

object MediaSourceRegistry {
    private val sources = LinkedHashMap<String, MediaSourceRef>()

    @Synchronized
    fun register(source: MediaSourceRef) {
        sources[source.id] = source
    }

    @Synchronized
    fun get(id: String): MediaSourceRef? {
        return sources[id]
    }

    @Synchronized
    fun all(): List<MediaSourceRef> {
        return sources.values.toList()
    }
}
