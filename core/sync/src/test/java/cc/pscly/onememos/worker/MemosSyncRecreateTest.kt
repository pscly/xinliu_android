package cc.pscly.onememos.worker

import cc.pscly.onememos.core.database.entity.MemoEntity
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试（1.17.7 / 1.17.8）：PATCH 遇到 404（远端已删除）时应改为“重新创建”，
 * 其他错误必须原样抛出，避免把权限/服务器故障误判成“已删除”而重复建脏数据。
 *
 * 真实场景：本地存在一条“冲突副本”，其 serverId 在服务端已被删除（404）。
 * 用户一旦编辑它，syncStatus 变 DIRTY，PATCH 就会 404；旧实现直接抛错，
 * 导致这条记录在**每一轮**同步都失败，把整轮同步永久卡死。
 */
class MemosSyncRecreateTest {
    @Test
    fun notFound_shouldRecreate() {
        assertTrue(shouldRecreateAfterPatchError(404))
    }

    @Test
    fun authAndServerErrors_shouldNotRecreate() {
        assertFalse("401 不能当成删除，否则会重复创建", shouldRecreateAfterPatchError(401))
        assertFalse(shouldRecreateAfterPatchError(403))
        assertFalse(shouldRecreateAfterPatchError(400))
        assertFalse("5xx 是服务端故障，不能当成删除", shouldRecreateAfterPatchError(500))
        assertFalse(shouldRecreateAfterPatchError(503))
        assertFalse("网络类错误码 0 也不能当成删除", shouldRecreateAfterPatchError(0))
    }

    @Test
    fun applyRecreatedServerId_replacesServerIdButKeepsUuid() {
        val entity = memoEntity(serverId = "memos/dead", creator = "users/pscly")

        val updated = entity.applyRecreatedServerId(newServerId = "memos/fresh", newCreator = "users/pscly")

        // uuid 是本地业务主键，必须保持不变；serverId 必须换成新值
        assertEquals(entity.uuid, updated.uuid)
        assertEquals("memos/fresh", updated.serverId)
        assertEquals("users/pscly", updated.creator)
    }

    @Test
    fun applyRecreatedServerId_keepsLocalCreatorWhenServerOmitsIt() {
        val entity = memoEntity(serverId = "memos/dead", creator = "users/pscly")

        val updated = entity.applyRecreatedServerId(newServerId = "memos/fresh", newCreator = null)

        assertEquals("users/pscly", updated.creator)
    }

    @Test
    fun applyRecreatedServerId_ignoresBlankServerCreator() {
        val entity = memoEntity(serverId = "memos/dead", creator = "users/pscly")

        val updated = entity.applyRecreatedServerId(newServerId = "memos/fresh", newCreator = "  ")

        assertEquals("users/pscly", updated.creator)
        assertEquals("memos/fresh", updated.serverId)
    }

    @Test
    fun applyRecreatedServerId_handlesNullLocalCreator() {
        val entity = memoEntity(serverId = "memos/dead", creator = null)

        val updated = entity.applyRecreatedServerId(newServerId = "memos/fresh", newCreator = null)

        assertNull(updated.creator)
    }

    private fun memoEntity(
        serverId: String?,
        creator: String?,
    ): MemoEntity =
        MemoEntity(
            uuid = "local-uuid",
            serverId = serverId,
            creator = creator,
            content = "内容",
            createdAt = 1L,
            updatedAt = 2L,
            serverState = MemoServerState.NORMAL,
            visibility = MemoVisibility.PRIVATE,
            pinned = false,
            syncStatus = SyncStatus.DIRTY,
            lastSyncError = null,
        )
}
