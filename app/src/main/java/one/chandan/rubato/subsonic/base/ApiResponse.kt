package one.chandan.rubato.subsonic.base

import androidx.annotation.Keep
import one.chandan.rubato.subsonic.models.SubsonicResponse
import com.google.gson.annotations.SerializedName

@Keep
class ApiResponse {
    @SerializedName("subsonic-response")
    lateinit var subsonicResponse: SubsonicResponse
}