package one.chandan.rubato.sync

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.CopyOnWriteArrayList
import one.chandan.rubato.util.OfflinePolicy
import one.chandan.rubato.util.Preferences

object SyncStateStore {
    private val state = MutableLiveData(buildState())
    private val listeners = CopyOnWriteArrayList<SyncStateListener>()

    @Volatile
    private var lastState: SyncState? = state.value

    @JvmStatic
    fun getState(): LiveData<SyncState> = state

    @JvmStatic
    fun getCurrentState(): SyncState = state.value ?: buildState()

    @JvmStatic
    fun addListener(listener: SyncStateListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    @JvmStatic
    fun removeListener(listener: SyncStateListener) {
        listeners.remove(listener)
    }

    @JvmStatic
    fun notifyChanged() {
        val updated = buildState()
        val previous = lastState
        if (previous == updated) return
        lastState = updated
        state.postValue(updated)
        for (listener in listeners) {
            listener.onStateChanged(previous, updated)
        }
    }

    private fun buildState(): SyncState {
        return SyncState(
            active = Preferences.isMetadataSyncActive(),
            stage = Preferences.getMetadataSyncStage(),
            progressCurrent = Preferences.getMetadataSyncProgressCurrent(),
            progressTotal = Preferences.getMetadataSyncProgressTotal(),
            coverArtCurrent = Preferences.getMetadataSyncCoverArtCurrent(),
            coverArtTotal = Preferences.getMetadataSyncCoverArtTotal(),
            lyricsCurrent = Preferences.getMetadataSyncLyricsCurrent(),
            lyricsTotal = Preferences.getMetadataSyncLyricsTotal(),
            startedAt = Preferences.getMetadataSyncStarted(),
            lastCompletedAt = Preferences.getMetadataSyncLast(),
            storageBytes = Preferences.getMetadataSyncStorageBytes(),
            storageUpdatedAt = Preferences.getMetadataSyncStorageUpdated(),
            logs = Preferences.getMetadataSyncLogs(),
            offline = OfflinePolicy.isOffline(),
            dataSaving = Preferences.isDataSavingMode()
        )
    }
}
