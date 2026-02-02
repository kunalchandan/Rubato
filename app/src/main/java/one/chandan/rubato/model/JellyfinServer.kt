package one.chandan.rubato.model

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class JellyfinServer(
    val id: String,
    val name: String,
    val address: String,
    val username: String,
    val accessToken: String,
    val userId: String,
    val libraryId: String,
    val libraryName: String,
    val timestamp: Long
) : Parcelable
