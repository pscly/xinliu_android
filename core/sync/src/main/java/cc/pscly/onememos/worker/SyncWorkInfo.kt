package cc.pscly.onememos.worker

import java.util.UUID

data class SyncWorkInfo(
    val id: UUID,
    val tags: Set<String>,
)
