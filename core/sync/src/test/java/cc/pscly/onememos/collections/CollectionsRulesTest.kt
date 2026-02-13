package cc.pscly.onememos.collections

import cc.pscly.onememos.domain.collections.bumpClientUpdatedAtMs
import cc.pscly.onememos.domain.collections.isMoveValid
import cc.pscly.onememos.domain.collections.reorderIds
import cc.pscly.onememos.domain.collections.shouldApplyRemote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionsRulesTest {
    @Test
    fun shouldApplyRemote_localOnly_false_evenIfRemoteNewer() {
        assertFalse(shouldApplyRemote(localClientUpdatedAtMs = 1L, localOnly = true, remoteClientUpdatedAtMs = 999L))
    }

    @Test
    fun shouldApplyRemote_remoteNewer_true() {
        assertTrue(shouldApplyRemote(localClientUpdatedAtMs = 10L, localOnly = false, remoteClientUpdatedAtMs = 10L))
        assertTrue(shouldApplyRemote(localClientUpdatedAtMs = 10L, localOnly = false, remoteClientUpdatedAtMs = 11L))
    }

    @Test
    fun shouldApplyRemote_remoteOlder_false() {
        assertFalse(shouldApplyRemote(localClientUpdatedAtMs = 11L, localOnly = false, remoteClientUpdatedAtMs = 10L))
    }

    @Test
    fun bumpClientUpdatedAtMs_monotonicInNormalCase() {
        assertEquals(20L, bumpClientUpdatedAtMs(nowMs = 20L, previousClientUpdatedAtMs = 10L))
        assertEquals(21L, bumpClientUpdatedAtMs(nowMs = 20L, previousClientUpdatedAtMs = 20L))
        assertEquals(101L, bumpClientUpdatedAtMs(nowMs = 20L, previousClientUpdatedAtMs = 100L))
    }

    @Test
    fun bumpClientUpdatedAtMs_clampsIfPreviousTooFarInFuture() {
        assertEquals(300_000L, bumpClientUpdatedAtMs(nowMs = 0L, previousClientUpdatedAtMs = 999_999_999L))
    }

    @Test
    fun isMoveValid_rejectMoveToSelfOrDescendant() {
        val parentById = mapOf(
            "a" to null,
            "b" to "a",
            "c" to "b",
        )
        assertFalse(isMoveValid(parentById = parentById, movingFolderId = "a", targetParentId = "a"))
        assertFalse(isMoveValid(parentById = parentById, movingFolderId = "a", targetParentId = "c"))
        assertTrue(isMoveValid(parentById = parentById, movingFolderId = "b", targetParentId = null))
    }

    @Test
    fun isMoveValid_rejectIfTreeHasCycle() {
        val parentById = mapOf(
            "a" to "b",
            "b" to "a",
        )
        assertFalse(isMoveValid(parentById = parentById, movingFolderId = "x", targetParentId = "a"))
    }

    @Test
    fun reorderIds_movesElement() {
        assertEquals(
            listOf("a", "c", "d", "b"),
            reorderIds(ids = listOf("a", "b", "c", "d"), fromIndex = 1, toIndex = 3),
        )
    }
}
