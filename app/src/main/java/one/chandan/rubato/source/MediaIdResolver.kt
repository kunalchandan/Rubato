package one.chandan.rubato.source

import one.chandan.rubato.domain.MediaId
import one.chandan.rubato.domain.MediaSourceType
import one.chandan.rubato.jellyfin.JellyfinMediaUtil
import one.chandan.rubato.util.JellyfinTagUtil

object MediaIdResolver {
    fun subsonic(sourceId: String, rawId: String?, fallback: String): MediaId {
        val externalId = rawId?.trim().takeUnless { it.isNullOrEmpty() } ?: fallback
        return MediaId(MediaSourceType.SUBSONIC, sourceId, externalId)
    }

    fun jellyfin(defaultServerId: String, rawOrTagged: String?, fallback: String): MediaId {
        val tagged = rawOrTagged?.trim().takeUnless { it.isNullOrEmpty() }
        if (tagged != null) {
            val parsed = JellyfinMediaUtil.parseTaggedId(tagged)
            if (parsed != null) {
                return MediaId(MediaSourceType.JELLYFIN, parsed.serverId, parsed.itemId)
            }
            val raw = JellyfinTagUtil.toRaw(tagged)
            if (raw != null && raw != tagged) {
                return fromJellyfinRaw(defaultServerId, raw, fallback)
            }
            return fromJellyfinRaw(defaultServerId, tagged, fallback)
        }
        return MediaId(MediaSourceType.JELLYFIN, defaultServerId, fallback)
    }

    fun local(rawId: String?, fallback: String): MediaId {
        val id = rawId?.trim().takeUnless { it.isNullOrEmpty() } ?: fallback
        return MediaId(MediaSourceType.LOCAL, MediaSourceType.LOCAL.id, id)
    }

    private fun fromJellyfinRaw(defaultServerId: String, raw: String, fallback: String): MediaId {
        val serverId = JellyfinTagUtil.extractServerId(raw) ?: defaultServerId
        val externalId = if (raw.contains(":")) {
            raw.substring(raw.indexOf(':') + 1)
        } else {
            raw
        }
        val resolvedExternal = externalId.takeUnless { it.isBlank() } ?: fallback
        return MediaId(MediaSourceType.JELLYFIN, serverId, resolvedExternal)
    }
}
