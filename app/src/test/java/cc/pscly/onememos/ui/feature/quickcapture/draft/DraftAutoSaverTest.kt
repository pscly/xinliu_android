package cc.pscly.onememos.ui.feature.quickcapture.draft

import android.app.Application
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DraftAutoSaverTest {
    @Test
    fun onTextChanged_debouncesAndSavesLatestText() =
        runTest {
            val saves = ArrayList<String>()
            val saver =
                DraftAutoSaver(
                    scope = backgroundScope,
                    debounceMs = 1_000L,
                    save = { text, _ -> saves += text },
                )

            saver.onTextChanged("a")
            advanceTimeBy(500)
            runCurrent()
            saver.onTextChanged("b")

            advanceTimeBy(999)
            runCurrent()
            assertEquals(0, saves.size)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf("b"), saves)
        }

    @Test
    fun flushNow_savesImmediatelyAndSuppressesPendingDebounce() =
        runTest {
            val saves = ArrayList<String>()
            val saver =
                DraftAutoSaver(
                    scope = backgroundScope,
                    debounceMs = 1_000L,
                    save = { text, _ -> saves += text },
                )

            saver.onTextChanged("x")
            advanceTimeBy(400)
            runCurrent()

            saver.flushNow()
            assertEquals(listOf("x"), saves)

            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(1, saves.size)
        }

    @Test
    fun onAttachmentsChanged_savesImmediatelyAndCancelsPendingDebounce() =
        runTest {
            data class SaveCall(val text: String, val count: Int)

            val calls = ArrayList<SaveCall>()
            val saver =
                DraftAutoSaver(
                    scope = backgroundScope,
                    debounceMs = 1_000L,
                    save = { text, attachments -> calls += SaveCall(text = text, count = attachments.size) },
                )

            saver.onTextChanged("t")
            advanceTimeBy(300)
            runCurrent()

            saver.onAttachmentsChanged(listOf(QuickCaptureDraftAttachment(fileName = "a")))
            runCurrent()
            assertEquals(1, calls.size)
            assertEquals("t", calls[0].text)
            assertEquals(1, calls[0].count)

            advanceTimeBy(2_000)
            runCurrent()
            assertTrue(calls.size == 1)
        }
}
