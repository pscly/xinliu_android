package cc.pscly.onememos.ui.feature.home

import cc.pscly.onememos.domain.model.GlobalSyncState
import cc.pscly.onememos.domain.model.SyncWorkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncBannerPolicyTest {
    @Test
    fun `鉴权失效 - 动作打开登录页 标签去登录`() {
        val state =
            GlobalSyncState(
                lastError = "鉴权失败",
                lastErrorHttpCode = 401,
            )
        val action = SyncBannerPolicy.actionFor(state)
        assertEquals(SyncBannerAction.OPEN_AUTH, action)
        assertEquals("去登录", SyncBannerPolicy.label(action))
    }

    @Test
    fun `非鉴权错误 - 动作重试 标签重试`() {
        val state =
            GlobalSyncState(
                lastError = "网络超时",
                lastErrorHttpCode = 500,
            )
        val action = SyncBannerPolicy.actionFor(state)
        assertEquals(SyncBannerAction.RETRY_SYNC, action)
        assertEquals("重试", SyncBannerPolicy.label(action))
    }

    @Test
    fun `待同步且在线且未在同步 - 动作同步 标签同步`() {
        val state =
            GlobalSyncState(
                workState = SyncWorkState.IDLE,
                pendingCount = 3,
                networkOnline = true,
            )
        val action = SyncBannerPolicy.actionFor(state)
        assertEquals(SyncBannerAction.SYNC_PENDING, action)
        assertEquals("同步", SyncBannerPolicy.label(action))
    }

    @Test
    fun `正在同步 - 即使有待同步也无动作`() {
        val state =
            GlobalSyncState(
                workState = SyncWorkState.RUNNING,
                pendingCount = 3,
                networkOnline = true,
            )
        assertEquals(SyncBannerAction.NONE, SyncBannerPolicy.actionFor(state))
    }

    @Test
    fun `离线 - 即使有待同步也无动作`() {
        val state =
            GlobalSyncState(
                workState = SyncWorkState.IDLE,
                pendingCount = 3,
                networkOnline = false,
            )
        assertEquals(SyncBannerAction.NONE, SyncBannerPolicy.actionFor(state))
    }

    @Test
    fun `仅排队中 - 无动作`() {
        val state =
            GlobalSyncState(
                workState = SyncWorkState.ENQUEUED,
                pendingCount = 0,
                networkOnline = true,
            )
        assertEquals(SyncBannerAction.NONE, SyncBannerPolicy.actionFor(state))
    }

    @Test
    fun `空闲且无待同步 - 无动作`() {
        assertEquals(SyncBannerAction.NONE, SyncBannerPolicy.actionFor(GlobalSyncState()))
    }

    @Test
    fun `动作池 - 全部动作标签非空`() {
        SyncBannerAction.entries.forEach { action ->
            assertTrue(SyncBannerPolicy.label(action).isNotBlank())
        }
    }
}
