package one.chandan.rubato.util

import android.util.Log

object AutoLog {
    private const val TAG = "AutoDiag"

    fun event(name: String, fields: Map<String, Any?> = emptyMap()) {
        log(Log.DEBUG, name, fields, null)
    }

    fun warn(name: String, fields: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) {
        log(Log.WARN, name, fields, throwable)
    }

    fun error(name: String, fields: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) {
        log(Log.ERROR, name, fields, throwable)
    }

    private fun log(level: Int, name: String, fields: Map<String, Any?>, throwable: Throwable?) {
        val message = buildString {
            append("event=").append(name)
            fields.forEach { (key, value) ->
                append(' ')
                append(key)
                append('=')
                append(value ?: "null")
            }
        }

        when (level) {
            Log.ERROR -> if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
            Log.WARN -> if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message)
            else -> if (throwable != null) Log.d(TAG, message, throwable) else Log.d(TAG, message)
        }
    }
}
