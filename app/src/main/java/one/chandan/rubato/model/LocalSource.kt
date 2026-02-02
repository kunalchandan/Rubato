package one.chandan.rubato.model

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
@Entity(tableName = "local_source")
data class LocalSource(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "tree_uri")
    val treeUri: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "relative_path")
    val relativePath: String?,
    @ColumnInfo(name = "volume_name")
    val volumeName: String?,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long
) : Parcelable
