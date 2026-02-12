package one.chandan.rubato.sync

interface SyncStateListener {
    fun onStateChanged(previous: SyncState?, current: SyncState)
}
