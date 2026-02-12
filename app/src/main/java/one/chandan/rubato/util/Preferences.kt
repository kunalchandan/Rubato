package one.chandan.rubato.util

import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import one.chandan.rubato.App
import one.chandan.rubato.R
import one.chandan.rubato.model.HomeSector
import one.chandan.rubato.subsonic.models.OpenSubsonicExtension
import one.chandan.rubato.sync.SyncStateStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


object Preferences {
    const val THEME = "theme"
    const val METADATA_SYNC_ACTIVE = "metadata_sync_active"
    const val METADATA_SYNC_STAGE = "metadata_sync_stage"
    const val METADATA_SYNC_PROGRESS_CURRENT = "metadata_sync_progress_current"
    const val METADATA_SYNC_PROGRESS_TOTAL = "metadata_sync_progress_total"
    const val METADATA_SYNC_PROGRESS_UPDATED = "metadata_sync_progress_updated"
    const val METADATA_SYNC_COVER_ART_CURRENT = "metadata_sync_cover_art_current"
    const val METADATA_SYNC_COVER_ART_TOTAL = "metadata_sync_cover_art_total"
    const val METADATA_SYNC_LYRICS_CURRENT = "metadata_sync_lyrics_current"
    const val METADATA_SYNC_LYRICS_TOTAL = "metadata_sync_lyrics_total"
    const val METADATA_SYNC_LOGS = "metadata_sync_logs"
    const val ONBOARDING_SHOWN = "onboarding_shown"
    const val COACHMARK_ADD_SERVER_PENDING = "coachmark_add_server_pending"
    private const val SERVER = "server"
    private const val USER = "user"
    private const val PASSWORD = "password"
    private const val TOKEN = "token"
    private const val SALT = "salt"
    private const val LOW_SECURITY = "low_security"
    private const val BATTERY_OPTIMIZATION = "battery_optimization"
    private const val SERVER_ID = "server_id"
    private const val OPEN_SUBSONIC = "open_subsonic"
    private const val OPEN_SUBSONIC_EXTENSIONS = "open_subsonic_extensions"
    private const val LOCAL_ADDRESS = "local_address"
    private const val IN_USE_SERVER_ADDRESS = "in_use_server_address"
    private const val NEXT_SERVER_SWITCH = "next_server_switch"
    private const val PLAYBACK_SPEED = "playback_speed"
    private const val SKIP_SILENCE = "skip_silence"
    private const val IMAGE_CACHE_SIZE = "image_cache_size"
    private const val STREAMING_CACHE_SIZE = "streaming_cache_size"
    private const val IMAGE_SIZE = "image_size"
    private const val MAX_BITRATE_WIFI = "max_bitrate_wifi"
    private const val MAX_BITRATE_MOBILE = "max_bitrate_mobile"
    private const val AUDIO_TRANSCODE_FORMAT_WIFI = "audio_transcode_format_wifi"
    private const val AUDIO_TRANSCODE_FORMAT_MOBILE = "audio_transcode_format_mobile"
    private const val WIFI_ONLY = "wifi_only"
    private const val DATA_SAVING_MODE = "data_saving_mode"
    private const val SERVER_UNREACHABLE = "server_unreachable"
    private const val SYNC_STARRED_TRACKS_FOR_OFFLINE_USE = "sync_starred_tracks_for_offline_use"
    private const val QUEUE_SYNCING = "queue_syncing"
    private const val QUEUE_SYNCING_COUNTDOWN = "queue_syncing_countdown"
    private const val ROUNDED_CORNER = "rounded_corner"
    private const val ROUNDED_CORNER_SIZE = "rounded_corner_size"
    private const val PODCAST_SECTION_VISIBILITY = "podcast_section_visibility"
    private const val RADIO_SECTION_VISIBILITY = "radio_section_visibility"
    private const val MUSIC_DIRECTORY_SECTION_VISIBILITY = "music_directory_section_visibility"
    private const val REPLAY_GAIN_MODE = "replay_gain_mode"
    private const val AUDIO_TRANSCODE_PRIORITY = "audio_transcode_priority"
    private const val STREAMING_CACHE_STORAGE = "streaming_cache_storage"
    private const val DOWNLOAD_STORAGE = "download_storage"
    private const val DEFAULT_DOWNLOAD_VIEW_TYPE = "default_download_view_type"
    private const val AUDIO_TRANSCODE_DOWNLOAD = "audio_transcode_download"
    private const val AUDIO_TRANSCODE_DOWNLOAD_PRIORITY = "audio_transcode_download_priority"
    private const val MAX_BITRATE_DOWNLOAD = "max_bitrate_download"
    private const val AUDIO_TRANSCODE_FORMAT_DOWNLOAD = "audio_transcode_format_download"
    private const val SHARE = "share"
    private const val SCROBBLING = "scrobbling"
    private const val ESTIMATE_CONTENT_LENGTH = "estimate_content_length"
    private const val BUFFERING_STRATEGY = "buffering_strategy"
    private const val SKIP_MIN_STAR_RATING = "skip_min_star_rating"
    private const val MIN_STAR_RATING = "min_star_rating"
    private const val ALWAYS_ON_DISPLAY = "always_on_display"
    private const val AUDIO_QUALITY_PER_ITEM = "audio_quality_per_item"
    private const val HOME_SECTOR_LIST = "home_sector_list"
    private const val RATING_PER_ITEM = "rating_per_item"
    private const val NEXT_UPDATE_CHECK = "next_update_check"
    private const val CONTINUOUS_PLAY = "continuous_play"
    private const val LAST_INSTANT_MIX = "last_instant_mix"
    private const val METADATA_SYNC_LAST = "metadata_sync_last"
    private const val METADATA_SYNC_SUBSONIC_LAST = "metadata_sync_subsonic_last"
    private const val METADATA_SYNC_SUBSONIC_FULL = "metadata_sync_subsonic_full"
    private const val METADATA_SYNC_SUBSONIC_SIGNATURE = "metadata_sync_subsonic_signature"
    private const val METADATA_SYNC_JELLYFIN_LAST = "metadata_sync_jellyfin_last"
    private const val METADATA_SYNC_JELLYFIN_FULL = "metadata_sync_jellyfin_full"
    private const val METADATA_SYNC_JELLYFIN_SIGNATURE = "metadata_sync_jellyfin_signature"
    private const val METADATA_SYNC_LOCAL_LAST = "metadata_sync_local_last"
    private const val METADATA_SYNC_LOCAL_FULL = "metadata_sync_local_full"
    private const val METADATA_SYNC_LOCAL_SIGNATURE = "metadata_sync_local_signature"
    const val METADATA_SYNC_STORAGE_BYTES = "metadata_sync_storage_bytes"
    const val METADATA_SYNC_STORAGE_UPDATED = "metadata_sync_storage_updated"
    const val METADATA_SYNC_STARTED = "metadata_sync_started"
    private const val METADATA_SYNC_LOG_LIMIT = 200
    private const val LIBRARY_PLAYLIST_ORDER = "library_playlist_order"
    private const val FLASHBACK_DECADES = "flashback_decades"
    private const val QUEUE_SWIPE_LEFT_ACTION = "queue_swipe_left_action"
    private const val QUEUE_SWIPE_RIGHT_ACTION = "queue_swipe_right_action"
    private const val LOCAL_TELEMETRY_ENABLED = "local_telemetry_enabled"
    private const val LOCAL_MUSIC_ENABLED = "local_music_enabled"
    private const val AUTO_SHUFFLE_ACTIVE = "auto_shuffle_active"
    private const val AUTO_SHUFFLE_SEED = "auto_shuffle_seed"
    private const val AUTO_SHUFFLE_INDEX = "auto_shuffle_index"
    private const val AUTO_SHUFFLE_CONSUMED = "auto_shuffle_consumed"
    private const val AUTO_SHUFFLE_SESSION = "auto_shuffle_session"
    private const val AUTO_SHUFFLE_RECENT = "auto_shuffle_recent"
    private const val DOWNLOAD_EXPORT_MODE = "download_export_mode"
    private const val DOWNLOAD_EXPORT_FOLDER = "download_export_folder"
    private const val VISUALIZER_ENABLED = "visualizer_enabled"
    private const val VISUALIZER_MODE = "visualizer_mode"
    private const val VISUALIZER_BAR_COUNT = "visualizer_bar_count"
    private const val VISUALIZER_OPACITY = "visualizer_opacity"
    private const val VISUALIZER_SMOOTHING = "visualizer_smoothing"
    private const val VISUALIZER_SCALE = "visualizer_scale"
    private const val VISUALIZER_COLOR_MODE = "visualizer_color_mode"
    private const val VISUALIZER_PEAK_CAPS = "visualizer_peak_caps"
    private const val VISUALIZER_FPS = "visualizer_fps"
    private const val CUSTOM_THEME_PRIMARY = "custom_theme_primary"
    private const val CUSTOM_THEME_SECONDARY = "custom_theme_secondary"
    private const val CUSTOM_THEME_TERTIARY = "custom_theme_tertiary"
    private const val SOURCE_PREFERENCE_ORDER = "source_preference_order"
    private const val SECURE_PREFS_FILE = "secure_prefs"
    private val METADATA_SYNC_LOG_LOCK = Any()
    private val SECURE_PREFS_LOCK = Any()
    @Volatile
    private var securePrefs: SharedPreferences? = null

    const val QUEUE_SWIPE_ACTION_PLAY_NEXT = "play_next"
    const val QUEUE_SWIPE_ACTION_ADD_QUEUE = "add_to_queue"
    const val QUEUE_SWIPE_ACTION_NONE = "none"
    const val QUEUE_SWIPE_ACTION_LIKE = "like"

    @JvmStatic
    fun getSourcePreferenceOrder(): List<String> {
        val defaultOrder = listOf(
            SearchIndexUtil.SOURCE_LOCAL,
            SearchIndexUtil.SOURCE_SUBSONIC,
            SearchIndexUtil.SOURCE_JELLYFIN
        )
        val stored = App.getInstance().preferences.getString(SOURCE_PREFERENCE_ORDER, null)
        if (stored.isNullOrBlank()) {
            return defaultOrder
        }
        val parsed = stored.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (parsed.isEmpty()) defaultOrder else parsed
    }

    @JvmStatic
    fun setSourcePreferenceOrder(order: List<String>) {
        val cleaned = order.map { it.trim() }.filter { it.isNotEmpty() }
        val value = if (cleaned.isEmpty()) "" else cleaned.joinToString(",")
        App.getInstance().preferences.edit().putString(SOURCE_PREFERENCE_ORDER, value).apply()
    }

    private fun getSecurePrefs(): SharedPreferences? {
        val cached = securePrefs
        if (cached != null) return cached
        synchronized(SECURE_PREFS_LOCK) {
            val current = securePrefs
            if (current != null) return current
            securePrefs = try {
                val masterKey = MasterKey.Builder(App.getContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    App.getContext(),
                    SECURE_PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (ex: Exception) {
                null
            }
            return securePrefs
        }
    }

    private fun getSecret(key: String): String? {
        val secure = getSecurePrefs()
        val secureValue = secure?.getString(key, null)
        if (!secureValue.isNullOrEmpty()) {
            return secureValue
        }
        val legacy = App.getInstance().preferences.getString(key, null)
        if (!legacy.isNullOrEmpty() && secure != null) {
            secure.edit().putString(key, legacy).apply()
            App.getInstance().preferences.edit().remove(key).apply()
        }
        return legacy
    }

    private fun setSecret(key: String, value: String?) {
        val secure = getSecurePrefs()
        if (secure != null) {
            if (value == null) {
                secure.edit().remove(key).apply()
            } else {
                secure.edit().putString(key, value).apply()
            }
            App.getInstance().preferences.edit().remove(key).apply()
        } else {
            App.getInstance().preferences.edit().putString(key, value).apply()
        }
    }


    @JvmStatic
    fun getTheme(): String {
        return App.getInstance().preferences.getString(THEME, one.chandan.rubato.helper.ThemeHelper.DEFAULT_MODE)
            ?: one.chandan.rubato.helper.ThemeHelper.DEFAULT_MODE
    }

    @JvmStatic
    fun setTheme(theme: String) {
        App.getInstance().preferences.edit().putString(THEME, theme).apply()
    }

    @JvmStatic
    fun getCustomThemePrimary(): Int {
        val fallback = ContextCompat.getColor(App.getContext(), R.color.theme_custom_primary)
        return App.getInstance().preferences.getInt(CUSTOM_THEME_PRIMARY, fallback)
    }

    @JvmStatic
    fun setCustomThemePrimary(color: Int) {
        App.getInstance().preferences.edit().putInt(CUSTOM_THEME_PRIMARY, color).apply()
    }

    @JvmStatic
    fun getCustomThemeSecondary(): Int {
        val fallback = ContextCompat.getColor(App.getContext(), R.color.theme_custom_secondary)
        return App.getInstance().preferences.getInt(CUSTOM_THEME_SECONDARY, fallback)
    }

    @JvmStatic
    fun setCustomThemeSecondary(color: Int) {
        App.getInstance().preferences.edit().putInt(CUSTOM_THEME_SECONDARY, color).apply()
    }

    @JvmStatic
    fun getCustomThemeTertiary(): Int {
        val fallback = ContextCompat.getColor(App.getContext(), R.color.theme_custom_tertiary)
        return App.getInstance().preferences.getInt(CUSTOM_THEME_TERTIARY, fallback)
    }

    @JvmStatic
    fun setCustomThemeTertiary(color: Int) {
        App.getInstance().preferences.edit().putInt(CUSTOM_THEME_TERTIARY, color).apply()
    }

    @JvmStatic
    fun getServer(): String? {
        return App.getInstance().preferences.getString(SERVER, null)
    }

    @JvmStatic
    fun hasRemoteServer(): Boolean {
        val server = getServer()
        return !server.isNullOrBlank()
    }

    @JvmStatic
    fun setServer(server: String?) {
        App.getInstance().preferences.edit().putString(SERVER, server).apply()
    }

    @JvmStatic
    fun getUser(): String? {
        return App.getInstance().preferences.getString(USER, null)
    }

    @JvmStatic
    fun setUser(user: String?) {
        App.getInstance().preferences.edit().putString(USER, user).apply()
    }

    @JvmStatic
    fun getPassword(): String? {
        return getSecret(PASSWORD)
    }

    @JvmStatic
    fun setPassword(password: String?) {
        setSecret(PASSWORD, password)
    }

    @JvmStatic
    fun getToken(): String? {
        return getSecret(TOKEN)
    }

    @JvmStatic
    fun setToken(token: String?) {
        setSecret(TOKEN, token)
    }

    @JvmStatic
    fun getSalt(): String? {
        return getSecret(SALT)
    }

    @JvmStatic
    fun setSalt(salt: String?) {
        setSecret(SALT, salt)
    }

    @JvmStatic
    fun isLowScurity(): Boolean {
        return App.getInstance().preferences.getBoolean(LOW_SECURITY, false)
    }

    @JvmStatic
    fun setLowSecurity(isLowSecurity: Boolean) {
        App.getInstance().preferences.edit().putBoolean(LOW_SECURITY, isLowSecurity).apply()
    }

    @JvmStatic
    fun getServerId(): String? {
        return App.getInstance().preferences.getString(SERVER_ID, null)
    }

    @JvmStatic
    fun setServerId(serverId: String?) {
        App.getInstance().preferences.edit().putString(SERVER_ID, serverId).apply()
    }

    @JvmStatic
    fun isOpenSubsonic(): Boolean {
        return App.getInstance().preferences.getBoolean(OPEN_SUBSONIC, false)
    }

    @JvmStatic
    fun setOpenSubsonic(isOpenSubsonic: Boolean) {
        App.getInstance().preferences.edit().putBoolean(OPEN_SUBSONIC, isOpenSubsonic).apply()
    }

    @JvmStatic
    fun getOpenSubsonicExtensions(): String? {
        return App.getInstance().preferences.getString(OPEN_SUBSONIC_EXTENSIONS, null)
    }

    @JvmStatic
    fun setOpenSubsonicExtensions(extension: List<OpenSubsonicExtension>) {
        App.getInstance().preferences.edit().putString(OPEN_SUBSONIC_EXTENSIONS, Gson().toJson(extension)).apply()
    }

    @JvmStatic
    fun getLibraryPlaylistOrder(): List<String>? {
        val stored = App.getInstance().preferences.getString(LIBRARY_PLAYLIST_ORDER, null)
        if (stored.isNullOrBlank() || stored == "null") return null

        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(stored, type)
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun setLibraryPlaylistOrder(order: List<String>?) {
        if (order == null) {
            App.getInstance().preferences.edit().putString(LIBRARY_PLAYLIST_ORDER, null).apply()
            return
        }

        App.getInstance().preferences.edit().putString(LIBRARY_PLAYLIST_ORDER, Gson().toJson(order)).apply()
    }

    @JvmStatic
    fun getFlashbackDecades(): List<Int>? {
        val stored = App.getInstance().preferences.getString(FLASHBACK_DECADES, null)
        if (stored.isNullOrBlank() || stored == "null") return null

        return try {
            val type = object : TypeToken<List<Int>>() {}.type
            Gson().fromJson(stored, type)
        } catch (e: Exception) {
            null
        }
    }

    @JvmStatic
    fun setFlashbackDecades(decades: List<Int>?) {
        if (decades.isNullOrEmpty()) {
            App.getInstance().preferences.edit().putString(FLASHBACK_DECADES, null).apply()
            return
        }

        App.getInstance().preferences.edit().putString(FLASHBACK_DECADES, Gson().toJson(decades)).apply()
    }

    @JvmStatic
    fun getMetadataSyncLast(): Long {
        return App.getInstance().preferences.getLong(METADATA_SYNC_LAST, 0)
    }

    @JvmStatic
    fun setMetadataSyncLast(timestamp: Long) {
        App.getInstance().preferences.edit().putLong(METADATA_SYNC_LAST, timestamp).apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncSubsonicLast(): Long {
        return App.getInstance().preferences.getLong(METADATA_SYNC_SUBSONIC_LAST, 0)
    }

    @JvmStatic
    fun setMetadataSyncSubsonicLast(timestamp: Long) {
        App.getInstance().preferences.edit().putLong(METADATA_SYNC_SUBSONIC_LAST, timestamp).apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncSubsonicFull(): Long {
        return App.getInstance().preferences.getLong(METADATA_SYNC_SUBSONIC_FULL, 0)
    }

    @JvmStatic
    fun setMetadataSyncSubsonicFull(timestamp: Long) {
        App.getInstance().preferences.edit().putLong(METADATA_SYNC_SUBSONIC_FULL, timestamp).apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncSubsonicSignature(): String? {
        return App.getInstance().preferences.getString(METADATA_SYNC_SUBSONIC_SIGNATURE, null)
    }

    @JvmStatic
    fun setMetadataSyncSubsonicSignature(signature: String?) {
        App.getInstance().preferences.edit().putString(METADATA_SYNC_SUBSONIC_SIGNATURE, signature).apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncJellyfinLast(): Long {
        return App.getInstance().preferences.getLong(METADATA_SYNC_JELLYFIN_LAST, 0)
    }

    @JvmStatic
    fun setMetadataSyncJellyfinLast(timestamp: Long) {
        App.getInstance().preferences.edit().putLong(METADATA_SYNC_JELLYFIN_LAST, timestamp).apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncJellyfinFull(): Long {
        return App.getInstance().preferences.getLong(METADATA_SYNC_JELLYFIN_FULL, 0)
    }

    @JvmStatic
    fun setMetadataSyncJellyfinFull(timestamp: Long) {
        App.getInstance().preferences.edit().putLong(METADATA_SYNC_JELLYFIN_FULL, timestamp).apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncJellyfinSignature(): String? {
        return App.getInstance().preferences.getString(METADATA_SYNC_JELLYFIN_SIGNATURE, null)
    }

    @JvmStatic
    fun setMetadataSyncJellyfinSignature(signature: String?) {
        App.getInstance().preferences.edit().putString(METADATA_SYNC_JELLYFIN_SIGNATURE, signature).apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncLocalLast(): Long {
        return App.getInstance().preferences.getLong(METADATA_SYNC_LOCAL_LAST, 0)
    }

    @JvmStatic
    fun setMetadataSyncLocalLast(timestamp: Long) {
        App.getInstance().preferences.edit().putLong(METADATA_SYNC_LOCAL_LAST, timestamp).apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncLocalFull(): Long {
        return App.getInstance().preferences.getLong(METADATA_SYNC_LOCAL_FULL, 0)
    }

    @JvmStatic
    fun setMetadataSyncLocalFull(timestamp: Long) {
        App.getInstance().preferences.edit().putLong(METADATA_SYNC_LOCAL_FULL, timestamp).apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncLocalSignature(): String? {
        return App.getInstance().preferences.getString(METADATA_SYNC_LOCAL_SIGNATURE, null)
    }

    @JvmStatic
    fun setMetadataSyncLocalSignature(signature: String?) {
        App.getInstance().preferences.edit().putString(METADATA_SYNC_LOCAL_SIGNATURE, signature).apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncStorageBytes(): Long {
        return App.getInstance().preferences.getLong(METADATA_SYNC_STORAGE_BYTES, 0)
    }

    @JvmStatic
    fun setMetadataSyncStorageBytes(bytes: Long) {
        App.getInstance().preferences.edit().putLong(METADATA_SYNC_STORAGE_BYTES, bytes).apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncStorageUpdated(): Long {
        return App.getInstance().preferences.getLong(METADATA_SYNC_STORAGE_UPDATED, 0)
    }

    @JvmStatic
    fun setMetadataSyncStorageUpdated(timestamp: Long) {
        App.getInstance().preferences.edit().putLong(METADATA_SYNC_STORAGE_UPDATED, timestamp).apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncStarted(): Long {
        return App.getInstance().preferences.getLong(METADATA_SYNC_STARTED, 0)
    }

    @JvmStatic
    fun setMetadataSyncStarted(timestamp: Long) {
        App.getInstance().preferences.edit().putLong(METADATA_SYNC_STARTED, timestamp).apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncLogs(): List<MetadataSyncLogEntry> {
        val json = App.getInstance().preferences.getString(METADATA_SYNC_LOGS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<MetadataSyncLogEntry>>() {}.type
            Gson().fromJson<List<MetadataSyncLogEntry>>(json, type) ?: emptyList()
        } catch (ex: Exception) {
            emptyList()
        }
    }

    @JvmStatic
    fun clearMetadataSyncLogs() {
        synchronized(METADATA_SYNC_LOG_LOCK) {
            App.getInstance().preferences.edit().remove(METADATA_SYNC_LOGS).apply()
        }
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun appendMetadataSyncLog(message: String?, stage: String?, completed: Boolean) {
        if (message.isNullOrBlank()) return
        synchronized(METADATA_SYNC_LOG_LOCK) {
            val prefs = App.getInstance().preferences
            val json = prefs.getString(METADATA_SYNC_LOGS, null)
            val type = object : TypeToken<List<MetadataSyncLogEntry>>() {}.type
            val list = try {
                Gson().fromJson<List<MetadataSyncLogEntry>>(json, type)?.toMutableList() ?: mutableListOf()
            } catch (ex: Exception) {
                mutableListOf()
            }
            list.add(0, MetadataSyncLogEntry(message, stage, System.currentTimeMillis(), completed))
            if (list.size > METADATA_SYNC_LOG_LIMIT) {
                list.subList(METADATA_SYNC_LOG_LIMIT, list.size).clear()
            }
            prefs.edit().putString(METADATA_SYNC_LOGS, Gson().toJson(list)).apply()
        }
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun isOnboardingShown(): Boolean {
        return App.getInstance().preferences.getBoolean(ONBOARDING_SHOWN, false)
    }

    @JvmStatic
    fun setOnboardingShown(shown: Boolean) {
        App.getInstance().preferences.edit().putBoolean(ONBOARDING_SHOWN, shown).apply()
    }

    @JvmStatic
    fun isCoachmarkAddServerPending(): Boolean {
        return App.getInstance().preferences.getBoolean(COACHMARK_ADD_SERVER_PENDING, false)
    }

    @JvmStatic
    fun setCoachmarkAddServerPending(pending: Boolean) {
        App.getInstance().preferences.edit().putBoolean(COACHMARK_ADD_SERVER_PENDING, pending).apply()
    }

    @JvmStatic
    fun isMetadataSyncActive(): Boolean {
        return App.getInstance().preferences.getBoolean(METADATA_SYNC_ACTIVE, false)
    }

    @JvmStatic
    fun setMetadataSyncActive(active: Boolean) {
        App.getInstance().preferences.edit().putBoolean(METADATA_SYNC_ACTIVE, active).apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncStage(): String? {
        return App.getInstance().preferences.getString(METADATA_SYNC_STAGE, null)
    }

    @JvmStatic
    fun getMetadataSyncProgressCurrent(): Int {
        return App.getInstance().preferences.getInt(METADATA_SYNC_PROGRESS_CURRENT, 0)
    }

    @JvmStatic
    fun getMetadataSyncProgressTotal(): Int {
        return App.getInstance().preferences.getInt(METADATA_SYNC_PROGRESS_TOTAL, -1)
    }

    @JvmStatic
    fun getMetadataSyncProgressUpdated(): Long {
        return App.getInstance().preferences.getLong(METADATA_SYNC_PROGRESS_UPDATED, 0)
    }

    @JvmStatic
    fun setMetadataSyncProgressUpdated(timestamp: Long) {
        App.getInstance().preferences.edit()
            .putLong(METADATA_SYNC_PROGRESS_UPDATED, timestamp)
            .apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun setMetadataSyncProgress(stage: String?, current: Int, total: Int) {
        App.getInstance().preferences.edit()
            .putString(METADATA_SYNC_STAGE, stage)
            .putInt(METADATA_SYNC_PROGRESS_CURRENT, current)
            .putInt(METADATA_SYNC_PROGRESS_TOTAL, total)
            .putLong(METADATA_SYNC_PROGRESS_UPDATED, System.currentTimeMillis())
            .apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncCoverArtCurrent(): Int {
        return App.getInstance().preferences.getInt(METADATA_SYNC_COVER_ART_CURRENT, 0)
    }

    @JvmStatic
    fun getMetadataSyncCoverArtTotal(): Int {
        return App.getInstance().preferences.getInt(METADATA_SYNC_COVER_ART_TOTAL, -1)
    }

    @JvmStatic
    fun setMetadataSyncCoverArtProgress(current: Int, total: Int) {
        App.getInstance().preferences.edit()
            .putInt(METADATA_SYNC_COVER_ART_CURRENT, current)
            .putInt(METADATA_SYNC_COVER_ART_TOTAL, total)
            .putLong(METADATA_SYNC_PROGRESS_UPDATED, System.currentTimeMillis())
            .apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getMetadataSyncLyricsCurrent(): Int {
        return App.getInstance().preferences.getInt(METADATA_SYNC_LYRICS_CURRENT, 0)
    }

    @JvmStatic
    fun getMetadataSyncLyricsTotal(): Int {
        return App.getInstance().preferences.getInt(METADATA_SYNC_LYRICS_TOTAL, -1)
    }

    @JvmStatic
    fun setMetadataSyncLyricsProgress(current: Int, total: Int) {
        App.getInstance().preferences.edit()
            .putInt(METADATA_SYNC_LYRICS_CURRENT, current)
            .putInt(METADATA_SYNC_LYRICS_TOTAL, total)
            .putLong(METADATA_SYNC_PROGRESS_UPDATED, System.currentTimeMillis())
            .apply()
        SyncStateStore.notifyChanged()
    }

    @JvmStatic
    fun getQueueSwipeLeftAction(): String {
        return App.getInstance().preferences.getString(QUEUE_SWIPE_LEFT_ACTION, QUEUE_SWIPE_ACTION_PLAY_NEXT)
            ?: QUEUE_SWIPE_ACTION_PLAY_NEXT
    }

    @JvmStatic
    fun setQueueSwipeLeftAction(value: String) {
        App.getInstance().preferences.edit().putString(QUEUE_SWIPE_LEFT_ACTION, value).apply()
    }

    @JvmStatic
    fun getQueueSwipeRightAction(): String {
        return App.getInstance().preferences.getString(QUEUE_SWIPE_RIGHT_ACTION, QUEUE_SWIPE_ACTION_ADD_QUEUE)
            ?: QUEUE_SWIPE_ACTION_ADD_QUEUE
    }

    @JvmStatic
    fun setQueueSwipeRightAction(value: String) {
        App.getInstance().preferences.edit().putString(QUEUE_SWIPE_RIGHT_ACTION, value).apply()
    }

    @JvmStatic
    fun isLocalTelemetryEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(LOCAL_TELEMETRY_ENABLED, false)
    }

    @JvmStatic
    fun setLocalTelemetryEnabled(enabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(LOCAL_TELEMETRY_ENABLED, enabled).apply()
    }

    @JvmStatic
    fun isLocalMusicEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(LOCAL_MUSIC_ENABLED, false)
    }

    @JvmStatic
    fun setLocalMusicEnabled(enabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(LOCAL_MUSIC_ENABLED, enabled).apply()
    }

    @JvmStatic
    fun isAutoShuffleActive(): Boolean {
        return App.getInstance().preferences.getBoolean(AUTO_SHUFFLE_ACTIVE, false)
    }

    @JvmStatic
    fun setAutoShuffleActive(active: Boolean) {
        App.getInstance().preferences.edit().putBoolean(AUTO_SHUFFLE_ACTIVE, active).apply()
    }

    @JvmStatic
    fun getAutoShuffleSeed(): Long {
        return App.getInstance().preferences.getLong(AUTO_SHUFFLE_SEED, 0L)
    }

    @JvmStatic
    fun setAutoShuffleSeed(seed: Long) {
        App.getInstance().preferences.edit().putLong(AUTO_SHUFFLE_SEED, seed).apply()
    }

    @JvmStatic
    fun getAutoShuffleIndex(): Int {
        return App.getInstance().preferences.getInt(AUTO_SHUFFLE_INDEX, 0)
    }

    @JvmStatic
    fun setAutoShuffleIndex(index: Int) {
        App.getInstance().preferences.edit().putInt(AUTO_SHUFFLE_INDEX, index).apply()
    }

    @JvmStatic
    fun getAutoShuffleConsumed(): Int {
        return App.getInstance().preferences.getInt(AUTO_SHUFFLE_CONSUMED, 0)
    }

    @JvmStatic
    fun setAutoShuffleConsumed(consumed: Int) {
        App.getInstance().preferences.edit().putInt(AUTO_SHUFFLE_CONSUMED, consumed).apply()
    }

    @JvmStatic
    fun getAutoShuffleSessionId(): Long {
        return App.getInstance().preferences.getLong(AUTO_SHUFFLE_SESSION, 0L)
    }

    @JvmStatic
    fun setAutoShuffleSessionId(sessionId: Long) {
        App.getInstance().preferences.edit().putLong(AUTO_SHUFFLE_SESSION, sessionId).apply()
    }

    @JvmStatic
    fun getAutoShuffleRecentIds(): List<String> {
        val stored = App.getInstance().preferences.getString(AUTO_SHUFFLE_RECENT, null)
        if (stored.isNullOrBlank() || stored == "null") return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(stored, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @JvmStatic
    fun setAutoShuffleRecentIds(ids: List<String>?) {
        if (ids.isNullOrEmpty()) {
            App.getInstance().preferences.edit().putString(AUTO_SHUFFLE_RECENT, null).apply()
            return
        }
        App.getInstance().preferences.edit().putString(AUTO_SHUFFLE_RECENT, Gson().toJson(ids)).apply()
    }

    @JvmStatic
    fun isVisualizerEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(VISUALIZER_ENABLED, true)
    }

    @JvmStatic
    fun setVisualizerEnabled(enabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(VISUALIZER_ENABLED, enabled).apply()
    }

    @JvmStatic
    fun getVisualizerMode(): String {
        return App.getInstance().preferences.getString(VISUALIZER_MODE, "bars") ?: "bars"
    }

    @JvmStatic
    fun setVisualizerMode(mode: String) {
        App.getInstance().preferences.edit().putString(VISUALIZER_MODE, mode).apply()
    }

    @JvmStatic
    fun getVisualizerBarCount(): Int {
        return App.getInstance().preferences.getInt(VISUALIZER_BAR_COUNT, 48)
    }

    @JvmStatic
    fun setVisualizerBarCount(value: Int) {
        App.getInstance().preferences.edit().putInt(VISUALIZER_BAR_COUNT, value).apply()
    }

    @JvmStatic
    fun getVisualizerOpacity(): Float {
        return App.getInstance().preferences.getFloat(VISUALIZER_OPACITY, 0.55f)
    }

    @JvmStatic
    fun setVisualizerOpacity(value: Float) {
        App.getInstance().preferences.edit().putFloat(VISUALIZER_OPACITY, value).apply()
    }

    @JvmStatic
    fun getVisualizerSmoothing(): Float {
        return App.getInstance().preferences.getFloat(VISUALIZER_SMOOTHING, 0.6f)
    }

    @JvmStatic
    fun setVisualizerSmoothing(value: Float) {
        App.getInstance().preferences.edit().putFloat(VISUALIZER_SMOOTHING, value).apply()
    }

    @JvmStatic
    fun getVisualizerScale(): Float {
        return App.getInstance().preferences.getFloat(VISUALIZER_SCALE, 1.0f)
    }

    @JvmStatic
    fun setVisualizerScale(value: Float) {
        App.getInstance().preferences.edit().putFloat(VISUALIZER_SCALE, value).apply()
    }

    @JvmStatic
    fun getVisualizerColorMode(): String {
        return App.getInstance().preferences.getString(VISUALIZER_COLOR_MODE, "accent") ?: "accent"
    }

    @JvmStatic
    fun setVisualizerColorMode(mode: String) {
        App.getInstance().preferences.edit().putString(VISUALIZER_COLOR_MODE, mode).apply()
    }

    @JvmStatic
    fun isVisualizerPeakCapsEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(VISUALIZER_PEAK_CAPS, false)
    }

    @JvmStatic
    fun setVisualizerPeakCapsEnabled(enabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(VISUALIZER_PEAK_CAPS, enabled).apply()
    }

    @JvmStatic
    fun getVisualizerFps(): Int {
        return App.getInstance().preferences.getInt(VISUALIZER_FPS, 45)
    }

    @JvmStatic
    fun setVisualizerFps(value: Int) {
        App.getInstance().preferences.edit().putInt(VISUALIZER_FPS, value).apply()
    }

    @JvmStatic
    fun getDownloadExportMode(): String {
        return App.getInstance().preferences.getString(DOWNLOAD_EXPORT_MODE, "app_private") ?: "app_private"
    }

    @JvmStatic
    fun setDownloadExportMode(mode: String) {
        App.getInstance().preferences.edit().putString(DOWNLOAD_EXPORT_MODE, mode).apply()
    }

    @JvmStatic
    fun getDownloadExportFolder(): String {
        return App.getInstance().preferences.getString(DOWNLOAD_EXPORT_FOLDER, "Rubato") ?: "Rubato"
    }

    @JvmStatic
    fun setDownloadExportFolder(folder: String) {
        App.getInstance().preferences.edit().putString(DOWNLOAD_EXPORT_FOLDER, folder).apply()
    }

    @JvmStatic
    fun getLocalAddress(): String? {
        return App.getInstance().preferences.getString(LOCAL_ADDRESS, null)
    }

    @JvmStatic
    fun setLocalAddress(address: String?) {
        App.getInstance().preferences.edit().putString(LOCAL_ADDRESS, address).apply()
    }

    @JvmStatic
    fun getInUseServerAddress(): String? {
        val address = App.getInstance().preferences.getString(IN_USE_SERVER_ADDRESS, null)
            ?.takeIf { it.isNotBlank() }
            ?: getServer()
        if (address != null && address.startsWith("http://") && !isLowScurity()) {
            return "https://" + address.removePrefix("http://")
        }
        return address
    }

    @JvmStatic
    fun isInUseServerAddressLocal(): Boolean {
        return getInUseServerAddress() == getLocalAddress()
    }

    @JvmStatic
    fun switchInUseServerAddress() {
        val inUseAddress = if (getInUseServerAddress() == getServer()) getLocalAddress() else getServer()
        App.getInstance().preferences.edit().putString(IN_USE_SERVER_ADDRESS, inUseAddress).apply()
    }

    @JvmStatic
    fun isServerSwitchable(): Boolean {
        return App.getInstance().preferences.getLong(
                NEXT_SERVER_SWITCH, 0
        ) + 15000 < System.currentTimeMillis() && !getServer().isNullOrEmpty() && !getLocalAddress().isNullOrEmpty()
    }

    @JvmStatic
    fun setServerSwitchableTimer() {
        App.getInstance().preferences.edit().putLong(NEXT_SERVER_SWITCH, System.currentTimeMillis()).apply()
    }

    @JvmStatic
    fun askForOptimization(): Boolean {
        return App.getInstance().preferences.getBoolean(BATTERY_OPTIMIZATION, true)
    }

    @JvmStatic
    fun dontAskForOptimization() {
        App.getInstance().preferences.edit().putBoolean(BATTERY_OPTIMIZATION, false).apply()
    }

    @JvmStatic
    fun getPlaybackSpeed(): Float {
        return App.getInstance().preferences.getFloat(PLAYBACK_SPEED, 1f)
    }

    @JvmStatic
    fun setPlaybackSpeed(playbackSpeed: Float) {
        App.getInstance().preferences.edit().putFloat(PLAYBACK_SPEED, playbackSpeed).apply()
    }

    @JvmStatic
    fun isSkipSilenceMode(): Boolean {
        return App.getInstance().preferences.getBoolean(SKIP_SILENCE, false)
    }

    @JvmStatic
    fun setSkipSilenceMode(isSkipSilenceMode: Boolean) {
        App.getInstance().preferences.edit().putBoolean(SKIP_SILENCE, isSkipSilenceMode).apply()
    }

    @JvmStatic
    fun getImageCacheSize(): Int {
        return App.getInstance().preferences.getString(IMAGE_CACHE_SIZE, "500")!!.toInt()
    }

    @JvmStatic
    fun getImageSize(): Int {
        return App.getInstance().preferences.getString(IMAGE_SIZE, "-1")!!.toInt()
    }

    @JvmStatic
    fun getStreamingCacheSize(): Long {
        return App.getInstance().preferences.getString(STREAMING_CACHE_SIZE, "256")!!.toLong()
    }

    @JvmStatic
    fun getMaxBitrateWifi(): String {
        return App.getInstance().preferences.getString(MAX_BITRATE_WIFI, "0")!!
    }

    @JvmStatic
    fun getMaxBitrateMobile(): String {
        return App.getInstance().preferences.getString(MAX_BITRATE_MOBILE, "0")!!
    }

    @JvmStatic
    fun getAudioTranscodeFormatWifi(): String {
        return App.getInstance().preferences.getString(AUDIO_TRANSCODE_FORMAT_WIFI, "raw")!!
    }

    @JvmStatic
    fun getAudioTranscodeFormatMobile(): String {
        return App.getInstance().preferences.getString(AUDIO_TRANSCODE_FORMAT_MOBILE, "raw")!!
    }

    @JvmStatic
    fun isWifiOnly(): Boolean {
        return App.getInstance().preferences.getBoolean(WIFI_ONLY, false)
    }

    @JvmStatic
    fun isDataSavingMode(): Boolean {
        return App.getInstance().preferences.getBoolean(DATA_SAVING_MODE, false)
    }

    @JvmStatic
    fun setDataSavingMode(isDataSavingModeEnabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(DATA_SAVING_MODE, isDataSavingModeEnabled)
                .apply()
    }

    @JvmStatic
    fun isStarredSyncEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(SYNC_STARRED_TRACKS_FOR_OFFLINE_USE, false)
    }

    @JvmStatic
    fun setStarredSyncEnabled(isStarredSyncEnabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(
                SYNC_STARRED_TRACKS_FOR_OFFLINE_USE, isStarredSyncEnabled
        ).apply()
    }

    @JvmStatic
    fun showServerUnreachableDialog(): Boolean {
        return App.getInstance().preferences.getLong(
                SERVER_UNREACHABLE, 0
        ) + 86400000 < System.currentTimeMillis()
    }

    @JvmStatic
    fun setServerUnreachableDatetime() {
        App.getInstance().preferences.edit().putLong(SERVER_UNREACHABLE, System.currentTimeMillis()).apply()
    }

    @JvmStatic
    fun isSyncronizationEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(QUEUE_SYNCING, false)
    }

    @JvmStatic
    fun getSyncCountdownTimer(): Int {
        return App.getInstance().preferences.getString(QUEUE_SYNCING_COUNTDOWN, "5")!!.toInt()
    }

    @JvmStatic
    fun isCornerRoundingEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(ROUNDED_CORNER, true)
    }

    @JvmStatic
    fun getRoundedCornerSize(): Int {
        return App.getInstance().preferences.getString(ROUNDED_CORNER_SIZE, "24")!!.toInt()
    }

    @JvmStatic
    fun isPodcastSectionVisible(): Boolean {
        return App.getInstance().preferences.getBoolean(PODCAST_SECTION_VISIBILITY, true)
    }

    @JvmStatic
    fun setPodcastSectionHidden() {
        App.getInstance().preferences.edit().putBoolean(PODCAST_SECTION_VISIBILITY, false).apply()
    }

    @JvmStatic
    fun isRadioSectionVisible(): Boolean {
        return App.getInstance().preferences.getBoolean(RADIO_SECTION_VISIBILITY, true)
    }

    @JvmStatic
    fun setRadioSectionHidden() {
        App.getInstance().preferences.edit().putBoolean(RADIO_SECTION_VISIBILITY, false).apply()
    }

    @JvmStatic
    fun isMusicDirectorySectionVisible(): Boolean {
        return App.getInstance().preferences.getBoolean(MUSIC_DIRECTORY_SECTION_VISIBILITY, true)
    }

    @JvmStatic
    fun getReplayGainMode(): String? {
        return App.getInstance().preferences.getString(REPLAY_GAIN_MODE, "disabled")
    }

    @JvmStatic
    fun isServerPrioritized(): Boolean {
        return App.getInstance().preferences.getBoolean(AUDIO_TRANSCODE_PRIORITY, false)
    }

    @JvmStatic
    fun getStreamingCacheStoragePreference(): Int {
        return App.getInstance().preferences.getString(STREAMING_CACHE_STORAGE, "0")!!.toInt()
    }

    @JvmStatic
    fun setStreamingCacheStoragePreference(streamingCachePreference: Int) {
        return App.getInstance().preferences.edit().putString(
                STREAMING_CACHE_STORAGE,
                streamingCachePreference.toString()
        ).apply()
    }

    @JvmStatic
    fun getDownloadStoragePreference(): Int {
        return App.getInstance().preferences.getString(DOWNLOAD_STORAGE, "0")!!.toInt()
    }

    @JvmStatic
    fun setDownloadStoragePreference(storagePreference: Int) {
        return App.getInstance().preferences.edit().putString(
                DOWNLOAD_STORAGE,
                storagePreference.toString()
        ).apply()
    }

    @JvmStatic
    fun getDefaultDownloadViewType(): String {
        return App.getInstance().preferences.getString(
                DEFAULT_DOWNLOAD_VIEW_TYPE,
                Constants.DOWNLOAD_TYPE_TRACK
        )!!
    }

    @JvmStatic
    fun setDefaultDownloadViewType(viewType: String) {
        return App.getInstance().preferences.edit().putString(
                DEFAULT_DOWNLOAD_VIEW_TYPE,
                viewType
        ).apply()
    }

    @JvmStatic
    fun preferTranscodedDownload(): Boolean {
        return App.getInstance().preferences.getBoolean(AUDIO_TRANSCODE_DOWNLOAD, false)
    }

    @JvmStatic
    fun isServerPrioritizedInTranscodedDownload(): Boolean {
        return App.getInstance().preferences.getBoolean(AUDIO_TRANSCODE_DOWNLOAD_PRIORITY, false)
    }

    @JvmStatic
    fun getBitrateTranscodedDownload(): String {
        return App.getInstance().preferences.getString(MAX_BITRATE_DOWNLOAD, "0")!!
    }

    @JvmStatic
    fun getAudioTranscodeFormatTranscodedDownload(): String {
        return App.getInstance().preferences.getString(AUDIO_TRANSCODE_FORMAT_DOWNLOAD, "raw")!!
    }

    @JvmStatic
    fun isSharingEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(SHARE, false)
    }

    @JvmStatic
    fun isScrobblingEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(SCROBBLING, true)
    }

    @JvmStatic
    fun askForEstimateContentLength(): Boolean {
        return App.getInstance().preferences.getBoolean(ESTIMATE_CONTENT_LENGTH, false)
    }

    @JvmStatic
    fun getBufferingStrategy(): Double {
        return App.getInstance().preferences.getString(BUFFERING_STRATEGY, "1")!!.toDouble()
    }

    @JvmStatic
    fun getMinStarRatingAccepted(): Int {
        return App.getInstance().preferences.getInt(MIN_STAR_RATING, 0)
    }

    @JvmStatic
    fun isDisplayAlwaysOn(): Boolean {
        return App.getInstance().preferences.getBoolean(ALWAYS_ON_DISPLAY, false)
    }

    @JvmStatic
    fun showAudioQuality(): Boolean {
        return App.getInstance().preferences.getBoolean(AUDIO_QUALITY_PER_ITEM, false)
    }

    @JvmStatic
    fun getHomeSectorList(): String? {
        return App.getInstance().preferences.getString(HOME_SECTOR_LIST, null)
    }

    @JvmStatic
    fun setHomeSectorList(extension: List<HomeSector>?) {
        App.getInstance().preferences.edit().putString(HOME_SECTOR_LIST, Gson().toJson(extension)).apply()
    }

    @JvmStatic
    fun showItemRating(): Boolean {
        return App.getInstance().preferences.getBoolean(RATING_PER_ITEM, false)
    }

    @JvmStatic
    fun showRubatoUpdateDialog(): Boolean {
        return App.getInstance().preferences.getLong(
                NEXT_UPDATE_CHECK, 0
        ) + 86400000 < System.currentTimeMillis()
    }

    @JvmStatic
    fun setRubatoUpdateReminder() {
        App.getInstance().preferences.edit().putLong(NEXT_UPDATE_CHECK, System.currentTimeMillis()).apply()
    }

    @JvmStatic
    fun isContinuousPlayEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(CONTINUOUS_PLAY, true)
    }

    @JvmStatic
    fun setLastInstantMix() {
        App.getInstance().preferences.edit().putLong(LAST_INSTANT_MIX, System.currentTimeMillis()).apply()
    }

    @JvmStatic
    fun isInstantMixUsable(): Boolean {
        return App.getInstance().preferences.getLong(
                LAST_INSTANT_MIX, 0
        ) + 5000 < System.currentTimeMillis()
    }
}
