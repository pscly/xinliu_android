package cc.pscly.onememos.core.database

import androidx.room.TypeConverter
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SyncStatus

class RoomConverters {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun fromMemoServerState(value: MemoServerState): String = value.name

    @TypeConverter
    fun toMemoServerState(value: String): MemoServerState = MemoServerState.valueOf(value)

    @TypeConverter
    fun fromMemoVisibility(value: MemoVisibility): String = value.name

    @TypeConverter
    fun toMemoVisibility(value: String): MemoVisibility = MemoVisibility.valueOf(value)
}
