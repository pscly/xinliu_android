package cc.pscly.onememos.worker

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TodoReminderAlarmReceiverTimeoutTest {
    @Test
    fun runWithTimeoutAndLog_timeout_is_swallowed() = runBlocking {
        var finished = false
        TodoReminderAlarmReceiver.runWithTimeoutAndLog(timeoutMs = 50) {
            delay(200)
            finished = true
        }

        assertFalse(finished)
    }
}
