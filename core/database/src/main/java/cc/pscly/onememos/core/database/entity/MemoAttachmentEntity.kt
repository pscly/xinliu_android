package cc.pscly.onememos.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memo_attachments",
    indices = [
        Index("memoUuid"),
        // cacheUri/remoteName 更新会按 (memoUuid, remoteName) 命中，避免全表扫描。
        Index(value = ["memoUuid", "remoteName"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = MemoEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["memoUuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MemoAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memoUuid: String,
    val localUri: String?,
    /** 远端图片下载后的本地缓存路径（file://...），用于“真正本地缓存/离线秒开”。 */
    val cacheUri: String? = null,
    val remoteName: String?,
    val filename: String?,
    val mimeType: String?,
    val createdAt: Long,
    // 用于前端排序；越小越靠前。默认 0，兼容旧数据。
    val sortOrder: Int = 0,
)
