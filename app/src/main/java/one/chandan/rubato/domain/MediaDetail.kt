package one.chandan.rubato.domain

object MediaDetail {
    fun score(vararg values: Any?): Int {
        var total = 0
        for (value in values) {
            when (value) {
                is String -> if (value.isNotBlank()) total += 1
                is Int -> if (value > 0) total += 1
                is Long -> if (value > 0L) total += 1
                is Float -> if (value > 0f) total += 1
                is Double -> if (value > 0.0) total += 1
                null -> {}
                else -> total += 1
            }
        }
        return total
    }
}
