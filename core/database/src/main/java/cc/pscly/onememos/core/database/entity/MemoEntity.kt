package cc.pscly.onememos.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SyncStatus

@Entity(
    tableName = "memos",
    indices = [
        // uuid 仍作为业务层稳定标识（与附件关系、路由参数等），因此必须全局唯一
        Index(value = ["uuid"], unique = true),
        // 便于从服务端回拉时按远端 id 对齐，避免产生重复记录
        Index(value = ["serverId"]),

        // ----------------------------
        // 性能索引：匹配首页/Paging/Profile/同步等常用查询
        // ----------------------------
        // 主页/归档页：按状态过滤并按 createdAt 排序
        Index(value = ["serverState", "createdAt", "localId"]),
        // “最近编辑/最近创建”查询
        Index(value = ["serverState", "updatedAt"]),
        // 同步队列：找出未 SYNCED 的并按 createdAt 排序
        Index(value = ["syncStatus", "createdAt"]),
        // 登录态“只看自己”：creator + 状态 + 时间（OR 查询仍可部分受益）
        Index(value = ["creator", "serverState", "createdAt"]),
    ],
)
data class MemoEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val uuid: String,
    val serverId: String?,
    val creator: String?,
    val content: String,
    val plainPreview: String = "",
    val tagsText: String = "",
    val derivedVersion: Int = 0,
    val derivedAt: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val serverState: MemoServerState,
    val visibility: MemoVisibility,
    val pinned: Boolean,
    val syncStatus: SyncStatus,
    val lastSyncError: String?,
)
