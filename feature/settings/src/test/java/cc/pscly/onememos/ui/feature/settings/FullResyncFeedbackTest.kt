package cc.pscly.onememos.ui.feature.settings

import cc.pscly.onememos.domain.sync.FullResyncScheduleResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FullResyncFeedbackTest {
    @Test
    fun accepted_waitsForSchedulerAndReturnsStartedMessage() =
        runBlocking {
            val handoff = CompletableDeferred<FullResyncScheduleResult>()
            val pending = async { requestFullResyncFeedback { handoff.await() } }

            yield()
            assertFalse(pending.isCompleted)

            handoff.complete(FullResyncScheduleResult.Accepted("request-1"))
            val feedback = pending.await()

            assertEquals(FullResyncFeedback.Accepted("request-1"), feedback)
            assertEquals("已开始后台重同步", fullResyncToastMessage(feedback))
        }

    @Test
    fun duplicate_returnsDistinctNonSuccessMessage() =
        runBlocking {
            val feedback = requestFullResyncFeedback { FullResyncScheduleResult.Duplicate }
            val message = fullResyncToastMessage(feedback)

            assertEquals(FullResyncFeedback.Duplicate, feedback)
            assertEquals("已有全量重同步任务正在进行", message)
            assertNotEquals("已开始后台重同步", message)
        }

    @Test
    fun busy_returnsDistinctNonSuccessMessage() =
        runBlocking {
            val feedback = requestFullResyncFeedback { FullResyncScheduleResult.Busy }
            val message = fullResyncToastMessage(feedback)

            assertEquals(FullResyncFeedback.Busy, feedback)
            assertEquals("当前有同步任务正在进行，请稍后重试", message)
            assertNotEquals("已开始后台重同步", message)
        }

    @Test
    fun schedulerFailure_returnsFailureMessageInsteadOfSuccess() =
        runBlocking {
            val feedback =
                requestFullResyncFeedback {
                    throw IllegalStateException("WorkManager enqueue failed")
                }
            val message = fullResyncToastMessage(feedback)

            assertEquals(FullResyncFeedback.Failure, feedback)
            assertEquals("启动全量重同步失败，请稍后重试", message)
            assertNotEquals("已开始后台重同步", message)
        }
}
