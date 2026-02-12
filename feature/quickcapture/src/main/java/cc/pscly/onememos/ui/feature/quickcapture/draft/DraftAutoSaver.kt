package cc.pscly.onememos.ui.feature.quickcapture.draft

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DraftAutoSaver(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 1_000L,
    private val save: suspend (text: String, attachments: List<QuickCaptureDraftAttachment>) -> Unit,
) {
    private val lock = Any()

    private var text: String = ""
    private var attachments: List<QuickCaptureDraftAttachment> = emptyList()

    private var seq: Long = 0L
    private var pendingTextJob: Job? = null

    fun onTextChanged(text: String) {
        scheduleDebouncedSave(updatedText = text)
    }

    fun onAttachmentsChanged(attachments: List<QuickCaptureDraftAttachment>) {
        val snapshot =
            synchronized(lock) {
                this.attachments = attachments.toList()
                seq += 1
                pendingTextJob?.cancel()
                pendingTextJob = null
                DraftSnapshot(text = text, attachments = this.attachments)
            }

        scope.launch {
            runCatching { save(snapshot.text, snapshot.attachments) }
        }
    }

    suspend fun flushNow() {
        val snapshot =
            synchronized(lock) {
                seq += 1
                pendingTextJob?.cancel()
                pendingTextJob = null
                DraftSnapshot(text = text, attachments = attachments)
            }

        runCatching { save(snapshot.text, snapshot.attachments) }
    }

    fun cancel() {
        synchronized(lock) {
            seq += 1
            pendingTextJob?.cancel()
            pendingTextJob = null
        }
    }

    private fun scheduleDebouncedSave(updatedText: String) {
        val job: Job
        val expectedSeq: Long

        synchronized(lock) {
            text = updatedText
            seq += 1
            expectedSeq = seq

            pendingTextJob?.cancel()
            job =
                scope.launch {
                    delay(debounceMs)

                    val snapshot =
                        synchronized(lock) {
                            if (expectedSeq != seq) return@launch
                            pendingTextJob = null
                            DraftSnapshot(text = text, attachments = attachments)
                        }

                    runCatching { save(snapshot.text, snapshot.attachments) }
                }
            pendingTextJob = job
        }
    }

    private data class DraftSnapshot(
        val text: String,
        val attachments: List<QuickCaptureDraftAttachment>,
    )
}
