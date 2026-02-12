package cc.pscly.onememos.ui.feature.quickcapture.draft

data class QuickCaptureDraft(
    val schemaVersion: Int,
    val updatedAt: Long,
    val text: String,
    val attachments: List<QuickCaptureDraftAttachment>,
)

data class QuickCaptureDraftAttachment(
    val fileName: String,
    val originalName: String? = null,
)
