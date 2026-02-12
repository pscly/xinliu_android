package cc.pscly.onememos.ui.feature.quickcapture.draft

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class QuickCaptureDraftStoreTest {
    @Test
    fun saveDraft_atomicWriterThrows_oldDraftStillLoadable() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()

            val okStore = QuickCaptureDraftStore(context)
            okStore.clearDraft()

            val old = QuickCaptureDraft(schemaVersion = 1, updatedAt = 1L, text = "old", attachments = emptyList())
            okStore.saveDraft(old)
            assertEquals("old", okStore.loadDraft()!!.text)

            val failingStore =
                QuickCaptureDraftStore(
                    context = context,
                    atomicWriter = DraftAtomicWriter { target, bytes ->
                        val atomicFile = AtomicFile(target)
                        val out = atomicFile.startWrite()
                        out.use { it.write(bytes) }
                        throw RuntimeException("boom")
                    },
                )

            runCatching {
                failingStore.saveDraft(QuickCaptureDraft(schemaVersion = 1, updatedAt = 2L, text = "new", attachments = emptyList()))
            }

            val loaded = okStore.loadDraft()
            assertNotNull(loaded)
            assertEquals("old", loaded!!.text)
        }

    @Test
    fun loadDraft_corruptJson_returnsNull() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()

            val draftFile = File(File(context.noBackupFilesDir, "quick_capture_draft"), "draft.json")
            draftFile.parentFile?.mkdirs()
            draftFile.writeText("{not json", Charsets.UTF_8)

            assertNull(store.loadDraft())
        }

    @Test
    fun saveDraft_concurrent_doesNotCorruptJson() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val store = QuickCaptureDraftStore(context, ioDispatcher = Dispatchers.IO)
            store.clearDraft()

            val jobs =
                listOf(
                    async(Dispatchers.Default) {
                        store.saveDraft(QuickCaptureDraft(schemaVersion = 1, updatedAt = 0L, text = "a", attachments = emptyList()))
                    },
                    async(Dispatchers.Default) {
                        store.saveDraft(QuickCaptureDraft(schemaVersion = 1, updatedAt = 0L, text = "b", attachments = emptyList()))
                    },
                )
            jobs.awaitAll()

            val loaded = store.loadDraft()
            assertNotNull(loaded)
            assertTrue(loaded!!.schemaVersion == 1)
            assertTrue(loaded.updatedAt > 0L)
            assertTrue(loaded.text == "a" || loaded.text == "b")
        }

    @Test
    fun saveDraft_overwrite_cleansOrphanAttachments() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()

            val attachmentsDir = File(context.filesDir, "quick_capture_draft_attachments").apply { mkdirs() }
            val keepFile = File(attachmentsDir, "keep.txt").apply { writeText("1", Charsets.UTF_8) }
            val orphanFile = File(attachmentsDir, "orphan.txt").apply { writeText("2", Charsets.UTF_8) }

            store.saveDraft(
                QuickCaptureDraft(
                    schemaVersion = 1,
                    updatedAt = 1L,
                    text = "v1",
                    attachments =
                        listOf(
                            QuickCaptureDraftAttachment(fileName = keepFile.name),
                            QuickCaptureDraftAttachment(fileName = orphanFile.name),
                        ),
                ),
            )

            store.saveDraft(
                QuickCaptureDraft(
                    schemaVersion = 1,
                    updatedAt = 2L,
                    text = "v2",
                    attachments = listOf(QuickCaptureDraftAttachment(fileName = keepFile.name)),
                ),
            )

            assertTrue(keepFile.exists())
            assertTrue(!orphanFile.exists())
        }

    @Test
    fun clearDraft_deletesJsonAndAttachmentsDir() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()

            val attachmentsDir = File(context.filesDir, "quick_capture_draft_attachments").apply { mkdirs() }
            File(attachmentsDir, "a.txt").writeText("x", Charsets.UTF_8)
            store.saveDraft(QuickCaptureDraft(schemaVersion = 1, updatedAt = 1L, text = "x", attachments = emptyList()))

            store.clearDraft()

            val draftFile = File(File(context.noBackupFilesDir, "quick_capture_draft"), "draft.json")
            assertTrue(!draftFile.exists())
            assertTrue(!attachmentsDir.exists())
        }

    @Test
    fun copyInAttachment_copiesContentUri_andClearDraftDeletesIt() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val store = QuickCaptureDraftStore(context)
            store.clearDraft()

            val uri = Uri.parse("content://quickcapture.test/att")
            val shadowResolver = Shadow.extract(context.contentResolver) as ShadowContentResolver
            shadowResolver.registerInputStream(uri, ByteArrayInputStream("hello".toByteArray(Charsets.UTF_8)))

            val att = store.copyInAttachment(uri)
            val outFile = File(File(context.filesDir, "quick_capture_draft_attachments"), att.fileName)
            assertTrue(outFile.exists())
            assertTrue(outFile.length() > 0L)

            store.clearDraft()
            assertTrue(!outFile.exists())
        }
}
