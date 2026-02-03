package cc.pscly.onememos.domain.model

/**
 * memos 服务端状态：NORMAL / ARCHIVED。
 * 与本地同步状态 [SyncStatus] 不同：SyncStatus 描述“是否已同步”，而 MemoServerState 描述“服务器上是什么状态”。
 */
enum class MemoServerState {
    NORMAL,
    ARCHIVED,
}
