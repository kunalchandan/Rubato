package one.chandan.rubato.domain

import one.chandan.rubato.util.SearchIndexUtil
import one.chandan.rubato.util.Preferences
import java.util.LinkedHashMap

object MediaDedupeUtil {
    fun mergeArtists(vararg sources: List<MediaArtist>?): List<MediaArtist> {
        val merged = mutableListOf<MediaArtist>()
        for (source in sources) {
            if (source != null) merged.addAll(source)
        }
        return dedupeArtists(merged)
    }

    fun mergeAlbums(vararg sources: List<MediaAlbum>?): List<MediaAlbum> {
        val merged = mutableListOf<MediaAlbum>()
        for (source in sources) {
            if (source != null) merged.addAll(source)
        }
        return dedupeAlbums(merged)
    }

    fun mergeSongs(vararg sources: List<MediaSong>?): List<MediaSong> {
        val merged = mutableListOf<MediaSong>()
        for (source in sources) {
            if (source != null) merged.addAll(source)
        }
        return dedupeSongs(merged)
    }

    fun mergePlaylists(vararg sources: List<MediaPlaylist>?): List<MediaPlaylist> {
        val merged = mutableListOf<MediaPlaylist>()
        for (source in sources) {
            if (source != null) merged.addAll(source)
        }
        return dedupePlaylists(merged)
    }

    fun dedupeArtists(items: List<MediaArtist>): List<MediaArtist> {
        if (items.isEmpty()) return emptyList()
        val deduped = LinkedHashMap<String, MediaArtist>()
        for (artist in items) {
            val key = normalizeKey(artist.name)
            val existing = deduped[key]
            if (existing == null || shouldPrefer(artist, existing)) {
                deduped[key] = artist
            }
        }
        return deduped.values.toList()
    }

    fun dedupeAlbums(items: List<MediaAlbum>): List<MediaAlbum> {
        if (items.isEmpty()) return emptyList()
        val deduped = LinkedHashMap<String, MediaAlbum>()
        for (album in items) {
            val key = normalizeKey("${album.title}|${album.artist}")
            val existing = deduped[key]
            if (existing == null || shouldPrefer(album, existing)) {
                deduped[key] = album
            }
        }
        return deduped.values.toList()
    }

    fun dedupeSongs(items: List<MediaSong>): List<MediaSong> {
        if (items.isEmpty()) return emptyList()
        val deduped = LinkedHashMap<String, MediaSong>()
        for (song in items) {
            val key = normalizeKey("${song.title}|${song.artist}|${song.album}")
            val existing = deduped[key]
            if (existing == null || shouldPrefer(song, existing)) {
                deduped[key] = song
            }
        }
        return deduped.values.toList()
    }

    fun dedupePlaylists(items: List<MediaPlaylist>): List<MediaPlaylist> {
        if (items.isEmpty()) return emptyList()
        val deduped = LinkedHashMap<String, MediaPlaylist>()
        for (playlist in items) {
            val key = normalizeKey(playlist.name)
            val existing = deduped[key]
            if (existing == null || shouldPrefer(playlist, existing)) {
                deduped[key] = playlist
            }
        }
        return deduped.values.toList()
    }

    private fun normalizeKey(value: String): String {
        return SearchIndexUtil.normalize(value)
    }

    private fun shouldPrefer(candidate: MediaArtist, existing: MediaArtist): Boolean {
        val detail = candidate.detailScore().compareTo(existing.detailScore())
        if (detail != 0) return detail > 0
        return preferBySource(candidate.id.sourceType.id, existing.id.sourceType.id)
    }

    private fun shouldPrefer(candidate: MediaAlbum, existing: MediaAlbum): Boolean {
        val detail = candidate.detailScore().compareTo(existing.detailScore())
        if (detail != 0) return detail > 0
        return preferBySource(candidate.id.sourceType.id, existing.id.sourceType.id)
    }

    private fun shouldPrefer(candidate: MediaSong, existing: MediaSong): Boolean {
        val detail = candidate.detailScore().compareTo(existing.detailScore())
        if (detail != 0) return detail > 0
        return preferBySource(candidate.id.sourceType.id, existing.id.sourceType.id)
    }

    private fun shouldPrefer(candidate: MediaPlaylist, existing: MediaPlaylist): Boolean {
        val detail = candidate.detailScore().compareTo(existing.detailScore())
        if (detail != 0) return detail > 0
        return preferBySource(candidate.id.sourceType.id, existing.id.sourceType.id)
    }

    private fun preferBySource(candidateSource: String, existingSource: String): Boolean {
        val order = Preferences.getSourcePreferenceOrder()
        val candidateRank = order.indexOf(candidateSource).takeIf { it >= 0 } ?: order.size
        val existingRank = order.indexOf(existingSource).takeIf { it >= 0 } ?: order.size
        return candidateRank < existingRank
    }
}
