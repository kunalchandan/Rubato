package one.chandan.rubato.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import one.chandan.rubato.App
import one.chandan.rubato.glide.CustomGlideRequest
import one.chandan.rubato.util.CoverArtPrefetcher
import one.chandan.rubato.util.MetadataSyncManager
import one.chandan.rubato.util.Preferences

object CoverArtPrefetchQueue {
    private const val PREF_KEY_QUEUE = "cover_art_prefetch_queue"
    private const val PREF_KEY_TOTAL = "cover_art_prefetch_total"
    private const val WORK_NAME = "cover_art_prefetch_queue"
    private const val MAX_QUEUE = 3000
    private const val MAX_ATTEMPTS = 3
    private const val THROTTLE_MS = 35L
    private const val LOG_INTERVAL = 25
    private val gson = Gson()
    private val queueType = object : TypeToken<List<Item>>() {}.type
    private val lock = Any()

    data class Item(
        val kind: String,
        val value: String,
        val resourceType: String? = null,
        val attempts: Int = 0
    ) {
        fun key(): String {
            return if (kind == KIND_URL) {
                "url:$value"
            } else {
                "id:${resourceType ?: ""}:$value"
            }
        }
    }

    @JvmStatic
    fun enqueue(context: Context?, coverArtIds: Set<String>?, coverArtUrls: Set<String>?) {
        if (context == null) return
        if (Preferences.isDataSavingMode()) {
            return
        }
        val items = buildItems(coverArtIds, coverArtUrls)
        if (items.isEmpty()) return

        synchronized(lock) {
            val prefs = App.getInstance().preferences
            val existing = loadQueue(prefs)
            val beforeSize = existing.size
            val existingTotal = prefs.getInt(PREF_KEY_TOTAL, 0)
            val doneBefore = if (existingTotal > 0) {
                (existingTotal - beforeSize).coerceAtLeast(0)
            } else {
                0
            }
            val merged = LinkedHashMap<String, Item>(beforeSize + items.size)
            for (item in existing) {
                merged[item.key()] = item
            }
            for (item in items) {
                merged.putIfAbsent(item.key(), item)
            }
            val trimmed = merged.values.take(MAX_QUEUE).toMutableList()
            val added = (trimmed.size - beforeSize).coerceAtLeast(0)
            var total = existingTotal
            val maxAllowed = doneBefore + MAX_QUEUE
            if (total <= 0 || total < beforeSize || total > maxAllowed) {
                total = doneBefore + trimmed.size
            } else if (added > 0) {
                total += added
            }
            saveQueue(prefs, trimmed, total)
            val done = (total - trimmed.size).coerceAtLeast(0)
            Preferences.setMetadataSyncCoverArtProgress(done, total)
        }

        schedule(context)
    }

    @JvmStatic
    fun processQueue(context: Context?): Int {
        if (context == null) return 0
        val prefs = App.getInstance().preferences
        val queue: List<Item>
        var total: Int

        synchronized(lock) {
            queue = loadQueue(prefs)
            total = prefs.getInt(PREF_KEY_TOTAL, 0)
            if (total <= 0) total = queue.size
            if (queue.isEmpty()) {
                saveQueue(prefs, emptyList(), 0)
                Preferences.setMetadataSyncCoverArtProgress(0, 0)
                return 0
            }
        }

        var processed = 0
        var dropped = 0
        val remaining = ArrayList<Item>()
        for (item in queue) {
            try {
                if (item.kind == KIND_URL) {
                    CoverArtPrefetcher.prefetchUrl(context, item.value)
                } else {
                    val typeName = item.resourceType ?: CustomGlideRequest.ResourceType.Album.name
                    val type = CustomGlideRequest.ResourceType.valueOf(typeName)
                    CoverArtPrefetcher.prefetch(context, item.value, type)
                }
                processed++
            } catch (ex: Exception) {
                val nextAttempts = item.attempts + 1
                if (nextAttempts < MAX_ATTEMPTS) {
                    remaining.add(item.copy(attempts = nextAttempts))
                } else {
                    dropped++
                }
            }

            if (processed % LOG_INTERVAL == 0) {
                val currentDone = (total - remaining.size - (queue.size - processed)).coerceAtLeast(0)
                Preferences.setMetadataSyncCoverArtProgress(currentDone, total)
            }

            if (THROTTLE_MS > 0) {
                try {
                    Thread.sleep(THROTTLE_MS)
                } catch (ignored: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        synchronized(lock) {
            val adjustedTotal = (total - dropped).coerceAtLeast(0)
            saveQueue(prefs, remaining, adjustedTotal)
            val done = (adjustedTotal - remaining.size).coerceAtLeast(0)
            Preferences.setMetadataSyncCoverArtProgress(done, adjustedTotal)
            if (remaining.isEmpty()) {
                Preferences.appendMetadataSyncLog(
                    "Cover art cached ($done)",
                    MetadataSyncManager.STAGE_COVER_ART,
                    true
                )
            }
            return remaining.size
        }
    }

    @JvmStatic
    fun schedule(context: Context?) {
        if (context == null) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequest.Builder(CoverArtPrefetchWorker::class.java)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun loadQueue(prefs: android.content.SharedPreferences): MutableList<Item> {
        val json = prefs.getString(PREF_KEY_QUEUE, null) ?: return mutableListOf()
        return try {
            gson.fromJson<List<Item>>(json, queueType)?.toMutableList() ?: mutableListOf()
        } catch (ex: Exception) {
            mutableListOf()
        }
    }

    private fun saveQueue(prefs: android.content.SharedPreferences, items: List<Item>, total: Int) {
        val editor = prefs.edit()
        if (items.isEmpty()) {
            editor.remove(PREF_KEY_QUEUE)
        } else {
            editor.putString(PREF_KEY_QUEUE, gson.toJson(items))
        }
        editor.putInt(PREF_KEY_TOTAL, total)
        editor.apply()
    }

    private fun buildItems(coverArtIds: Set<String>?, coverArtUrls: Set<String>?): List<Item> {
        val items = ArrayList<Item>()
        if (coverArtIds != null) {
            for (id in coverArtIds) {
                if (id.isNullOrEmpty()) continue
                items.add(Item(KIND_ID, id, CustomGlideRequest.ResourceType.Album.name))
            }
        }
        if (coverArtUrls != null) {
            for (url in coverArtUrls) {
                if (url.isNullOrEmpty()) continue
                items.add(Item(KIND_URL, url))
            }
        }
        return items
    }

    private const val KIND_ID = "id"
    private const val KIND_URL = "url"
}
