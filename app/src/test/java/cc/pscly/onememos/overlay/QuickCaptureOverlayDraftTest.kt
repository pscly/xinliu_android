package cc.pscly.onememos.overlay

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import cc.pscly.onememos.ui.feature.quickcapture.draft.QuickCaptureDraft
import cc.pscly.onememos.ui.feature.quickcapture.draft.QuickCaptureDraftAttachment
import cc.pscly.onememos.ui.feature.quickcapture.draft.QuickCaptureDraftStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class QuickCaptureOverlayDraftTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        runBlocking {
            delay(1_100L)
            runCatching { QuickCaptureDraftStore(context).clearDraft() }
        }
    }

    private fun buildService(): QuickCaptureOverlayService {
        val controller = Robolectric.buildService(QuickCaptureOverlayService::class.java)
        val service = controller.get()
        return service
    }

    @Test
    fun start_withExistingDraft_bannerVisible() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()
            store.saveDraft(QuickCaptureDraft(schemaVersion = 1, updatedAt = 1L, text = "old", attachments = emptyList()))

            val service = buildService()
            service.debugRefreshDraftBannerOnStart()
            assertTrue(service.debugUiState().draftBannerVisible)
        }

    @Test
    fun overwriteConfirm_blocksWriteUntilConfirmed() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()
            store.saveDraft(QuickCaptureDraft(schemaVersion = 1, updatedAt = 1L, text = "old", attachments = emptyList()))

            val service = buildService()
            service.debugRefreshDraftBannerOnStart()
            service.debugUpdateContent(androidx.compose.ui.text.input.TextFieldValue("new"))
            assertTrue(service.debugUiState().draftOverwriteDialogVisible)

            Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
            delay(50)

            assertEquals("old", store.loadDraft()!!.text)
        }

    @Test
    fun overwriteConfirm_confirmed_allowsAutosaveToWriteNewDraft() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()
            store.saveDraft(QuickCaptureDraft(schemaVersion = 1, updatedAt = 1L, text = "old", attachments = emptyList()))

            val service = buildService()
            service.debugRefreshDraftBannerOnStart()
            service.debugUpdateContent(androidx.compose.ui.text.input.TextFieldValue("new"))
            service.debugConfirmOverwrite()

            service.debugFlushDraftNow()
            assertEquals("new", store.loadDraft()!!.text)
        }

    @Test
    fun restoreDraft_fillsTextAndAttachmentKeys() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()

            val attachmentsDir = File(context.filesDir, "quick_capture_draft_attachments").apply { mkdirs() }
            val attFile = File(attachmentsDir, "a.txt").apply { writeText("1", Charsets.UTF_8) }
            store.saveDraft(
                QuickCaptureDraft(
                    schemaVersion = 1,
                    updatedAt = 2L,
                    text = "hello",
                    attachments = listOf(QuickCaptureDraftAttachment(fileName = attFile.name, originalName = "a.txt")),
                ),
            )

            val service = buildService()
            service.debugRefreshDraftBannerOnStart()
            service.debugRestoreDraft()

            repeat(30) {
                val state = service.debugUiState()
                if (state.content.text == "hello" && state.attachments.size == 1) return@repeat
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(50)
            }

            val state = service.debugUiState()
            assertEquals("hello", state.content.text)
            assertEquals(1, state.attachments.size)
            assertEquals("a.txt", state.attachments[0].key)
            assertTrue(state.attachments[0].localUri?.contains("a.txt") == true)
        }

    @Test
    fun clearDraft_deletesJsonAndAttachmentsDir() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()
            val attachmentsDir = File(context.filesDir, "quick_capture_draft_attachments").apply { mkdirs() }
            File(attachmentsDir, "a.txt").writeText("1", Charsets.UTF_8)
            store.saveDraft(
                QuickCaptureDraft(
                    schemaVersion = 1,
                    updatedAt = 2L,
                    text = "hello",
                    attachments = listOf(QuickCaptureDraftAttachment(fileName = "a.txt")),
                ),
            )

            val service = buildService()
            service.debugRefreshDraftBannerOnStart()
            service.debugClearDraft()

            repeat(30) {
                if (store.loadDraft() == null && !attachmentsDir.exists()) return@repeat
                delay(50)
            }

            assertNull(store.loadDraft())
            assertFalse(attachmentsDir.exists())
        }

    @Test
    fun editingUuidMode_disablesDraftLogic_noDialogNoWrite() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()
            store.saveDraft(QuickCaptureDraft(schemaVersion = 1, updatedAt = 1L, text = "old", attachments = emptyList()))

            val service = buildService()
            service.debugRefreshDraftBannerOnStart()
            service.debugSetEditingUuid("uuid")

            service.debugUpdateContent(androidx.compose.ui.text.input.TextFieldValue("new"))
            assertFalse(service.debugUiState().draftOverwriteDialogVisible)

            Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
            delay(50)
            assertEquals("old", store.loadDraft()!!.text)
        }

    @Test
    fun addAttachments_copiesIntoDraftAttachmentsDir_andSavesImmediately() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()

            val service = buildService()
            service.debugRefreshDraftBannerOnStart()
            val uri = android.net.Uri.parse("content://quickcapture.test/att")
            val shadowResolver = Shadow.extract(context.contentResolver) as ShadowContentResolver
            shadowResolver.registerInputStream(uri, ByteArrayInputStream("hello".toByteArray(Charsets.UTF_8)))

            service.debugAddAttachments(uris = listOf(uri.toString()), replace = false)

            var loaded: QuickCaptureDraft? = null
            repeat(30) {
                loaded = store.loadDraft()
                if (loaded != null && loaded!!.attachments.isNotEmpty()) return@repeat
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(50)
            }

            assertNotNull(loaded)
            assertEquals(1, loaded!!.attachments.size)
            val fileName = loaded!!.attachments[0].fileName
            assertTrue(fileName.isNotBlank())

            val copied = File(context.filesDir, "quick_capture_draft_attachments/$fileName")
            assertTrue(copied.isFile)
            assertTrue(copied.length() > 0L)
        }

    @Test
    fun restoreDraft_thenOnDestroy_shouldKeepDraft() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()
            store.saveDraft(QuickCaptureDraft(schemaVersion = 1, updatedAt = 1L, text = "old", attachments = emptyList()))

            val service = buildService()
            service.debugRefreshDraftBannerOnStart()
            service.debugRestoreDraft()

            repeat(30) {
                if (service.debugUiState().content.text == "old") return@repeat
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(50)
            }

            service.debugFlushDraftNow()
            assertEquals("old", store.loadDraft()!!.text)
        }

    @Test
    fun clearDraft_thenOnDestroy_shouldStayCleared() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()
            store.saveDraft(QuickCaptureDraft(schemaVersion = 1, updatedAt = 1L, text = "old", attachments = emptyList()))

            val service = buildService()
            service.debugRefreshDraftBannerOnStart()
            service.debugClearDraft()

            repeat(30) {
                if (store.loadDraft() == null) return@repeat
                delay(50)
            }
            assertNull(store.loadDraft())

            service.debugFlushDraftNow()
            assertNull(store.loadDraft())
        }

    @Test
    fun saveSuccess_thenOnDestroy_shouldNotRecreateDraft() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()

            val attachmentsDir = File(context.filesDir, "quick_capture_draft_attachments").apply { mkdirs() }
            File(attachmentsDir, "a.txt").writeText("1", Charsets.UTF_8)
            store.saveDraft(
                QuickCaptureDraft(
                    schemaVersion = 1,
                    updatedAt = 1L,
                    text = "old",
                    attachments = listOf(QuickCaptureDraftAttachment(fileName = "a.txt")),
                ),
            )

            val service = buildService()
            service.debugRefreshDraftBannerOnStart()
            service.debugSimulateSaveSuccess()

            repeat(30) {
                if (store.loadDraft() == null && !attachmentsDir.exists()) return@repeat
                delay(50)
            }

            assertNull(store.loadDraft())
            assertFalse(attachmentsDir.exists())

            service.debugFlushDraftNow()
            assertNull(store.loadDraft())
            assertFalse(attachmentsDir.exists())
        }
}
